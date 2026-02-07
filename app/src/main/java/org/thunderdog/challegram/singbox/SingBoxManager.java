/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.singbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.nekohasekai.libbox.CommandServer;
import io.nekohasekai.libbox.CommandServerHandler;
import io.nekohasekai.libbox.Libbox;
import io.nekohasekai.libbox.SetupOptions;
import io.nekohasekai.libbox.SystemProxyStatus;

public class SingBoxManager implements Settings.ProxyChangeListener {
  private static final int PORT_RANGE_START = 10808;
  private static final int HEALTH_CHECK_INTERVAL_MS = 30_000;
  private static final int MAX_RESTART_ATTEMPTS = 3;
  private static final String PREFS_NAME = "singbox_crash_guard";
  private static final String KEY_RESTORING = "restoring_proxy_id";
  private static final String KEY_CRASH_COUNT = "crash_count";

  private static volatile SingBoxManager sInstance;

  private Context appContext;
  private SingBoxPlatformInterface platformInterface;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final ConcurrentHashMap<Integer, RunningInstance> runningInstances = new ConcurrentHashMap<>();
  private volatile boolean isDispatchingToTdlib;
  private volatile int startingProxyId;
  private boolean initialized;

  public static @NonNull SingBoxManager instance () {
    if (sInstance == null) {
      synchronized (SingBoxManager.class) {
        if (sInstance == null) {
          sInstance = new SingBoxManager();
        }
      }
    }
    return sInstance;
  }

  private SingBoxManager () { }

  public void initialize (@NonNull Context context) {
    if (initialized) return;
    this.appContext = context.getApplicationContext();
    this.platformInterface = new SingBoxPlatformInterface(this.appContext);
    try {
      File baseDir = new File(appContext.getFilesDir(), "sing-box");
      File workDir = new File(baseDir, "work");
      File tempDir = new File(appContext.getCacheDir(), "sing-box");
      baseDir.mkdirs();
      workDir.mkdirs();
      tempDir.mkdirs();

      SetupOptions options = new SetupOptions();
      options.setBasePath(baseDir.getAbsolutePath());
      options.setWorkingPath(workDir.getAbsolutePath());
      options.setTempPath(tempDir.getAbsolutePath());
      Libbox.setup(options);
      initialized = true;
      Log.i("SingBoxManager initialized");
    } catch (Exception e) {
      Log.e("Failed to initialize sing-box", e);
    }
  }

  public void restoreActiveInstance () {
    if (!initialized) return;

    // Crash guard: if we crashed during previous restore, don't try again
    SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    int prevRestoringId = prefs.getInt(KEY_RESTORING, 0);
    int crashCount = prefs.getInt(KEY_CRASH_COUNT, 0);
    if (prevRestoringId != 0) {
      crashCount++;
      Log.w("sing-box crashed during previous restore (proxy %d, crash count: %d), skipping restore", prevRestoringId, crashCount);
      prefs.edit().remove(KEY_RESTORING).putInt(KEY_CRASH_COUNT, crashCount).apply();
      if (crashCount >= 2) {
        Log.e("sing-box crashed too many times, disabling proxy");
        Settings.instance().disableProxy();
        prefs.edit().putInt(KEY_CRASH_COUNT, 0).apply();
      }
      return;
    }

    int proxyId = Settings.instance().getEffectiveProxyId();
    if (proxyId == Settings.PROXY_ID_NONE) return;

    Settings.Proxy proxy = Settings.instance().getProxyConfig(proxyId);
    if (proxy != null && proxy.isSingBox()) {
      startInstance(proxyId, null);
    }
  }

  public void startInstance (int proxyId, @Nullable Runnable onReady) {
    if (!initialized) {
      Log.w("SingBoxManager not initialized, cannot start instance");
      return;
    }

    stopInstance(proxyId);
    startingProxyId = proxyId;

    executor.execute(() -> {
      Settings.Proxy proxy = Settings.instance().getProxyConfig(proxyId);
      if (proxy == null || !proxy.isSingBox()) return;

      int localPort = allocatePort();
      if (localPort <= 0) {
        Log.e("Failed to allocate port for sing-box proxy %d", proxyId);
        return;
      }

      // Set crash guard before calling native code
      SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
      prefs.edit().putInt(KEY_RESTORING, proxyId).apply();

      try {
        String logFilePath = new File(new File(appContext.getFilesDir(), "sing-box"), "box.log").getAbsolutePath();
        // Clear previous log file
        File logFile = new File(logFilePath);
        if (logFile.exists()) {
          logFile.delete();
        }
        String fullConfig = SingBoxConfigBuilder.buildFullConfig(localPort, proxy.singBoxOutboundJson, logFilePath);

        // Validate config
        Log.i("sing-box: validating config for proxy %d", proxyId);
        Log.i("sing-box: outbound json: %s", proxy.singBoxOutboundJson);
        Log.i("sing-box: full config: %s", fullConfig);
        Libbox.checkConfig(fullConfig);
        Log.i("sing-box: config valid, starting command server");

        // Create and start command server
        SingBoxCommandServerHandler handler = new SingBoxCommandServerHandler();
        CommandServer server = Libbox.newCommandServer(handler, platformInterface);
        server.start();
        Log.i("sing-box: command server started, loading service");
        server.startOrReloadService(fullConfig, new io.nekohasekai.libbox.OverrideOptions());
        Log.i("sing-box: service loaded, waiting for inbound to be ready");

        // Give the SOCKS5 inbound a moment to start listening
        Thread.sleep(500);

        RunningInstance instance = new RunningInstance(proxyId, localPort, server);
        runningInstances.put(proxyId, instance);

        // Verify SOCKS5 inbound is accepting connections
        verifySocks5Inbound(localPort);

        // Clear crash guard - native calls succeeded
        prefs.edit().remove(KEY_RESTORING).putInt(KEY_CRASH_COUNT, 0).apply();

        // Update the proxy's local port and re-dispatch to TDLib
        TdApi.InternalLinkTypeProxy localProxy = new TdApi.InternalLinkTypeProxy(
          "127.0.0.1", localPort, new TdApi.ProxyTypeSocks5("", "")
        );
        proxy.proxy = localProxy;

        // Re-notify listeners so TdlibManager picks up the correct port.
        // The check must happen on the main thread (inside the post()) to avoid
        // race conditions with proxy switching on the main thread.
        final int capturedProxyId = proxyId;
        final TdApi.InternalLinkTypeProxy capturedProxy = localProxy;
        isDispatchingToTdlib = true;
        mainHandler.post(() -> {
          try {
            int effectiveProxyId = Settings.instance().getEffectiveProxyId();
            Log.i("sing-box: TDLib dispatch: effectiveProxyId=%d, proxyId=%d, match=%b",
              effectiveProxyId, capturedProxyId, effectiveProxyId == capturedProxyId);
            if (effectiveProxyId != capturedProxyId) {
              Log.i("sing-box: skipping TDLib dispatch, effective proxy changed");
              return;
            }
            TdlibManager manager = TdlibManager.instance();
            int accountCount = 0;
            int dispatchCount = 0;
            for (org.thunderdog.challegram.telegram.TdlibAccount account : manager.getAllAccounts()) {
              accountCount++;
              org.thunderdog.challegram.telegram.Tdlib tdlib = account.tdlibNoWakeup();
              if (tdlib != null) {
                Log.i("sing-box: dispatching setProxy(%d) to account %d", capturedProxyId, account.id);
                tdlib.setProxy(capturedProxyId, capturedProxy);
                dispatchCount++;
              }
            }
            Log.i("sing-box: TDLib dispatch done: %d accounts, %d dispatched", accountCount, dispatchCount);
          } finally {
            isDispatchingToTdlib = false;
          }
        });

        Log.i("sing-box started for proxy %d on port %d", proxyId, localPort);

        startingProxyId = 0;

        if (onReady != null) {
          mainHandler.post(onReady);
        }

        scheduleHealthCheck(proxyId);
      } catch (Exception e) {
        Log.e("Failed to start sing-box instance for proxy %d", e, proxyId);
        startingProxyId = 0;
        // Clear crash guard on Java exception (not a native crash)
        prefs.edit().remove(KEY_RESTORING).apply();
      }
    });
  }

  public void stopInstance (int proxyId) {
    RunningInstance instance = runningInstances.remove(proxyId);
    if (instance != null) {
      executor.execute(() -> {
        try {
          instance.server.closeService();
          instance.server.close();
          Log.i("sing-box stopped for proxy %d", proxyId);
        } catch (Exception e) {
          Log.e("Failed to stop sing-box instance for proxy %d", e, proxyId);
        }
      });
    }
  }

  public void ensureRunningForPing (int proxyId, @NonNull Runnable callback) {
    RunningInstance instance = runningInstances.get(proxyId);
    if (instance != null) {
      Log.i("ensureRunningForPing: proxy %d already running on port %d, calling callback immediately", proxyId, instance.localPort);
      callback.run();
      return;
    }

    // Instance is already being started, wait for it
    if (startingProxyId == proxyId) {
      Log.i("ensureRunningForPing: proxy %d is starting, waiting on executor", proxyId);
      executor.execute(() -> {
        RunningInstance started = runningInstances.get(proxyId);
        if (started != null) {
          Log.i("ensureRunningForPing: proxy %d started on port %d, posting callback", proxyId, started.localPort);
          mainHandler.post(callback);
        } else {
          Log.w("ensureRunningForPing: proxy %d start failed, no instance found", proxyId);
        }
      });
      return;
    }

    // Stop all temporary instances (not the active proxy) before starting a new one
    // to prevent memory exhaustion from multiple concurrent Go runtimes
    int activeProxyId = Settings.instance().getEffectiveProxyId();
    for (Integer existingId : runningInstances.keySet()) {
      if (existingId != activeProxyId) {
        Log.i("ensureRunningForPing: stopping temporary instance for proxy %d before starting %d", existingId, proxyId);
        stopInstance(existingId);
      }
    }

    Log.i("ensureRunningForPing: proxy %d not running, starting temporary instance", proxyId);
    startInstance(proxyId, () -> {
      callback.run();
      // Stop after a delay to allow ping to complete
      mainHandler.postDelayed(() -> {
        int currentProxyId = Settings.instance().getEffectiveProxyId();
        if (currentProxyId != proxyId) {
          Log.i("ensureRunningForPing: stopping temporary instance for proxy %d (current=%d)", proxyId, currentProxyId);
          stopInstance(proxyId);
        }
      }, 10_000);
    });
  }

  public int getLocalPort (int proxyId) {
    RunningInstance instance = runningInstances.get(proxyId);
    return instance != null ? instance.localPort : 0;
  }

  private int allocatePort () {
    // Try using libbox's port finder first
    try {
      return Libbox.availablePort(PORT_RANGE_START);
    } catch (Exception e) {
      // Fallback to manual port allocation
    }
    // Fallback: let OS assign
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      Log.e("Failed to allocate port", e);
      return -1;
    }
  }

  private void scheduleHealthCheck (int proxyId) {
    mainHandler.postDelayed(() -> {
      RunningInstance instance = runningInstances.get(proxyId);
      if (instance == null) return;

      executor.execute(() -> {
        boolean healthy = checkHealth(instance.localPort);
        if (!healthy) {
          instance.restartCount++;
          if (instance.restartCount <= MAX_RESTART_ATTEMPTS) {
            Log.w("sing-box health check failed for proxy %d, restarting (attempt %d)", proxyId, instance.restartCount);
            startInstance(proxyId, null);
          } else {
            Log.e("sing-box health check failed for proxy %d, max restarts reached", proxyId);
            stopInstance(proxyId);
          }
        } else {
          scheduleHealthCheck(proxyId);
        }
      });
    }, HEALTH_CHECK_INTERVAL_MS);
  }

  private boolean checkHealth (int port) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("127.0.0.1", port), 3000);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private void verifySocks5Inbound (int port) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
      socket.setSoTimeout(10000);
      java.io.OutputStream out = socket.getOutputStream();
      java.io.InputStream in = socket.getInputStream();
      // SOCKS5 greeting: version=5, 1 auth method, method=0 (no auth)
      out.write(new byte[]{0x05, 0x01, 0x00});
      out.flush();
      byte[] response = new byte[2];
      int read = in.read(response);
      if (read == 2 && response[0] == 0x05 && response[1] == 0x00) {
        Log.i("sing-box: SOCKS5 auth OK on port %d", port);
      } else {
        Log.e("sing-box: SOCKS5 auth failed on port %d: read=%d, data=%s",
          port, read, read > 0 ? java.util.Arrays.toString(response) : "none");
        return;
      }

      // Now try SOCKS5 CONNECT to Telegram DC2: 149.154.167.50:443
      // Format: VER(1) CMD(1) RSV(1) ATYP(1) ADDR(4) PORT(2)
      byte[] connectReq = new byte[]{
        0x05, 0x01, 0x00, 0x01, // ver=5, cmd=CONNECT, rsv=0, atyp=IPv4
        (byte) 149, (byte) 154, (byte) 167, 50, // 149.154.167.50
        0x01, (byte) 0xBB // port 443
      };
      Log.i("sing-box: sending SOCKS5 CONNECT to 149.154.167.50:443 through port %d", port);
      out.write(connectReq);
      out.flush();

      byte[] connectResp = new byte[10];
      int connectRead = in.read(connectResp);
      if (connectRead >= 2) {
        int rep = connectResp[1] & 0xFF;
        String repStr;
        switch (rep) {
          case 0: repStr = "succeeded"; break;
          case 1: repStr = "general SOCKS server failure"; break;
          case 2: repStr = "connection not allowed by ruleset"; break;
          case 3: repStr = "network unreachable"; break;
          case 4: repStr = "host unreachable"; break;
          case 5: repStr = "connection refused"; break;
          case 6: repStr = "TTL expired"; break;
          case 7: repStr = "command not supported"; break;
          case 8: repStr = "address type not supported"; break;
          default: repStr = "unknown(" + rep + ")"; break;
        }
        Log.i("sing-box: SOCKS5 CONNECT response: rep=%d (%s), read=%d bytes", rep, repStr, connectRead);

        if (rep == 0) {
          // Connection succeeded! Try reading a few bytes to see what the Telegram DC responds
          Log.i("sing-box: SOCKS5 CONNECT succeeded! Outbound proxy chain works.");
        } else {
          Log.e("sing-box: SOCKS5 CONNECT FAILED: %s. Outbound proxy not working!", repStr);
        }
      } else {
        Log.e("sing-box: SOCKS5 CONNECT got no response (read=%d)", connectRead);
      }
    } catch (java.net.SocketTimeoutException e) {
      Log.w("sing-box: SOCKS5 CONNECT timed out on port %d (outbound may be slow or blocked)", port);
    } catch (Exception e) {
      Log.e("sing-box: SOCKS5 verification failed on port %d: %s", e, port, e.getMessage());
    }

    // Read sing-box log file if it exists
    readSingBoxLogFile();
  }

  private void readSingBoxLogFile () {
    try {
      File logFile = new File(new File(appContext.getFilesDir(), "sing-box"), "box.log");
      if (logFile.exists()) {
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(logFile));
        StringBuilder sb = new StringBuilder();
        String line;
        int lineCount = 0;
        while ((line = reader.readLine()) != null && lineCount < 50) {
          sb.append(line).append('\n');
          lineCount++;
        }
        reader.close();
        if (sb.length() > 0) {
          Log.i("sing-box log file (%d lines):\n%s", lineCount, sb.toString());
        }
      } else {
        Log.i("sing-box: no log file found at %s", logFile.getAbsolutePath());
      }
    } catch (Exception e) {
      Log.w("sing-box: failed to read log file: %s", e.getMessage());
    }
  }

  // Settings.ProxyChangeListener

  @Override
  public void onProxyConfigurationChanged (int proxyId, @Nullable TdApi.InternalLinkTypeProxy proxy,
      @Nullable String description, boolean isCurrent, boolean isNewAdd) {
    if (isDispatchingToTdlib) return;
    if (isCurrent) {
      Settings.Proxy proxyConfig = Settings.instance().getProxyConfig(proxyId);
      if (proxyConfig != null && proxyConfig.isSingBox()) {
        if (runningInstances.containsKey(proxyId) || startingProxyId == proxyId) return;
        // Stop other sing-box instances before starting the new one
        stopAllInstances();
        startInstance(proxyId, null);
      } else {
        stopAllInstances();
      }
    }
  }

  @Override
  public void onProxyAvailabilityChanged (boolean isAvailable) {
    if (!isAvailable) {
      stopAllInstances();
    }
  }

  @Override
  public void onProxyAdded (Settings.Proxy proxy, boolean isCurrent) {
    if (isDispatchingToTdlib) return;
    if (isCurrent && proxy.isSingBox()) {
      if (runningInstances.containsKey(proxy.id) || startingProxyId == proxy.id) return;
      // Stop other sing-box instances before starting the new one
      stopAllInstances();
      startInstance(proxy.id, null);
    }
  }

  private void stopAllInstances () {
    for (Integer proxyId : runningInstances.keySet()) {
      stopInstance(proxyId);
    }
  }

  private static class RunningInstance {
    final int proxyId;
    final int localPort;
    final CommandServer server;
    int restartCount;

    RunningInstance (int proxyId, int localPort, CommandServer server) {
      this.proxyId = proxyId;
      this.localPort = localPort;
      this.server = server;
    }
  }

  private static class SingBoxCommandServerHandler implements CommandServerHandler {
    @Override
    public void serviceStop () throws Exception {
      Log.i("sing-box service stopped");
    }

    @Override
    public void serviceReload () throws Exception {
      Log.i("sing-box service reload requested");
    }

    @Override
    public SystemProxyStatus getSystemProxyStatus () throws Exception {
      return null;
    }

    @Override
    public void setSystemProxyEnabled (boolean enabled) throws Exception {
      // Not used in bridge mode
    }

    @Override
    public void writeDebugMessage (String message) {
      Log.i("sing-box: %s", message);
    }
  }
}
