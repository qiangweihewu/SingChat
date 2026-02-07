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

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyLatencyMonitor implements Settings.ProxyChangeListener {
  private static final long PING_INTERVAL_MS = 30_000;
  private static final long LATENCY_THRESHOLD_MS = 500;
  private static final int MAX_CONSECUTIVE_FAILURES = 3;

  private static volatile ProxyLatencyMonitor sInstance;

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private volatile boolean monitoring;
  private int consecutiveFailures;
  private boolean switchInProgress;

  public static @NonNull ProxyLatencyMonitor instance () {
    if (sInstance == null) {
      synchronized (ProxyLatencyMonitor.class) {
        if (sInstance == null) {
          sInstance = new ProxyLatencyMonitor();
        }
      }
    }
    return sInstance;
  }

  private ProxyLatencyMonitor () { }

  public void startMonitoring () {
    if (monitoring) return;
    monitoring = true;
    consecutiveFailures = 0;
    Log.i("ProxyLatencyMonitor: starting");
    scheduleNextCheck();
  }

  public void stopMonitoring () {
    if (!monitoring) return;
    monitoring = false;
    mainHandler.removeCallbacksAndMessages(null);
    Log.i("ProxyLatencyMonitor: stopped");
  }

  private void scheduleNextCheck () {
    if (!monitoring) return;
    mainHandler.postDelayed(this::performLatencyCheck, PING_INTERVAL_MS);
  }

  private void performLatencyCheck () {
    if (!monitoring) return;

    int proxyId = Settings.instance().getEffectiveProxyId();
    if (proxyId == Settings.PROXY_ID_NONE) {
      stopMonitoring();
      return;
    }

    Settings.Proxy proxy = Settings.instance().getProxyConfig(proxyId);
    if (proxy == null) {
      stopMonitoring();
      return;
    }

    Tdlib tdlib = TdlibManager.instance().currentNoWakeup();
    if (tdlib == null) {
      scheduleNextCheck();
      return;
    }

    tdlib.pingProxy(proxy, pingMs -> mainHandler.post(() -> {
      if (!monitoring) return;

      if (pingMs < 0 || pingMs > LATENCY_THRESHOLD_MS) {
        consecutiveFailures++;
        Log.i("ProxyLatencyMonitor: failure #%d (pingMs=%d)", consecutiveFailures, pingMs);
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
          triggerSmartSwitch();
          return;
        }
      } else {
        consecutiveFailures = 0;
      }

      scheduleNextCheck();
    }));
  }

  private void triggerSmartSwitch () {
    if (switchInProgress) return;
    switchInProgress = true;
    Log.i("ProxyLatencyMonitor: triggering smart switch");

    List<Settings.Proxy> proxies = Settings.instance().getAvailableProxies();
    if (proxies.isEmpty()) {
      switchInProgress = false;
      scheduleNextCheck();
      return;
    }

    Tdlib tdlib = TdlibManager.instance().currentNoWakeup();
    if (tdlib == null) {
      switchInProgress = false;
      scheduleNextCheck();
      return;
    }

    int currentProxyId = Settings.instance().getEffectiveProxyId();
    AtomicInteger remaining = new AtomicInteger(proxies.size());

    for (Settings.Proxy proxy : proxies) {
      tdlib.pingProxy(proxy, pingMs -> mainHandler.post(() -> {
        if (remaining.decrementAndGet() <= 0) {
          // All pings complete, find the best
          long bestPing = Long.MAX_VALUE;
          int bestId = Settings.PROXY_ID_NONE;

          for (Settings.Proxy p : proxies) {
            if (p.hasPong() && p.pingMs < bestPing) {
              bestPing = p.pingMs;
              bestId = p.id;
            }
          }

          if (bestId != Settings.PROXY_ID_NONE && bestId != currentProxyId) {
            Log.i("ProxyLatencyMonitor: switching from proxy %d to %d (ping %dms)", currentProxyId, bestId, bestPing);
            Settings.instance().enableProxy(bestId);
          }

          consecutiveFailures = 0;
          switchInProgress = false;
          scheduleNextCheck();
        }
      }));
    }
  }

  // Settings.ProxyChangeListener

  @Override
  public void onProxyConfigurationChanged (int proxyId, @Nullable TdApi.InternalLinkTypeProxy proxy,
      @Nullable String description, boolean isCurrent, boolean isNewAdd) {
    if (isCurrent) {
      consecutiveFailures = 0;
    }
  }

  @Override
  public void onProxyAvailabilityChanged (boolean isAvailable) {
    if (!isAvailable) {
      stopMonitoring();
    }
  }

  @Override
  public void onProxyAdded (Settings.Proxy proxy, boolean isCurrent) {
    // no-op
  }
}
