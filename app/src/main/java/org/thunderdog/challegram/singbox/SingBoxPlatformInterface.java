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
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.unsorted.Settings;

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import io.nekohasekai.libbox.ConnectionOwner;
import io.nekohasekai.libbox.InterfaceUpdateListener;
import io.nekohasekai.libbox.LocalDNSTransport;
import io.nekohasekai.libbox.NetworkInterfaceIterator;
import io.nekohasekai.libbox.Notification;
import io.nekohasekai.libbox.PlatformInterface;
import io.nekohasekai.libbox.RoutePrefixIterator;
import io.nekohasekai.libbox.StringIterator;
import io.nekohasekai.libbox.TunOptions;
import io.nekohasekai.libbox.WIFIState;

public class SingBoxPlatformInterface implements PlatformInterface {
  private final Context context;
  private final Object tunLock = new Object();
  private ParcelFileDescriptor tunInterface;

  public SingBoxPlatformInterface (Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void autoDetectInterfaceControl (int fd) throws Exception {
    // In VPN mode, protect outbound sockets from being captured by our own VPN tunnel.
    SingBoxVpnService vpnService = SingBoxVpnService.instance();
    if (vpnService != null && vpnService.protect(fd)) {
      Log.i("sing-box: autoDetectInterfaceControl fd=%d protected by VPN service", fd);
      return;
    }

    // Bridge mode fallback: bind socket to active network.
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
          Network network = cm.getActiveNetwork();
          if (network != null) {
            Log.i("sing-box: autoDetectInterfaceControl fd=%d, binding to network %s", fd, network);
            ParcelFileDescriptor pfd = ParcelFileDescriptor.adoptFd(fd);
            try {
              network.bindSocket(pfd.getFileDescriptor());
            } finally {
              // Detach without closing the original fd
              pfd.detachFd();
            }
            Log.i("sing-box: autoDetectInterfaceControl fd=%d bound successfully", fd);
          } else {
            Log.w("sing-box: autoDetectInterfaceControl fd=%d, no active network!", fd);
          }
        } else {
          Log.w("sing-box: autoDetectInterfaceControl fd=%d, no ConnectivityManager!", fd);
        }
      } else {
        Log.i("sing-box: autoDetectInterfaceControl fd=%d, API < 23, skipping bind", fd);
      }
    } catch (Exception e) {
      Log.w("sing-box: autoDetectInterfaceControl fd=%d failed: %s", fd, e.getMessage());
    }
  }

  @Override
  public void clearDNSCache () {
    // No-op in bridge mode
  }

  @Override
  public void closeDefaultInterfaceMonitor (InterfaceUpdateListener listener) throws Exception {
    // No-op in bridge mode
  }

  @Override
  public ConnectionOwner findConnectionOwner (int ipProtocol, String sourceAddress, int sourcePort,
      String destinationAddress, int destinationPort) throws Exception {
    // Must return non-null; Go code dereferences without nil check
    return new ConnectionOwner();
  }

  @Override
  public NetworkInterfaceIterator getInterfaces () throws Exception {
    return new NetworkInterfaceIterator() {
      @Override
      public boolean hasNext () {
        return false;
      }

      @Override
      public io.nekohasekai.libbox.NetworkInterface next () {
        return null;
      }
    };
  }

  @Override
  public boolean includeAllNetworks () {
    return false;
  }

  @Override
  public LocalDNSTransport localDNSTransport () {
    return null;
  }

  @Override
  public int openTun (TunOptions options) throws Exception {
    SingBoxVpnService service = SingBoxVpnService.waitForRunning(10_000);
    if (service == null) {
      throw new IllegalStateException("VPN service is not running");
    }

    VpnService.Builder builder = service.new Builder();
    builder.setSession("SingChat sing-box");
    if (options.getMTU() > 0) {
      builder.setMtu(options.getMTU());
    }

    addAddresses(builder, options.getInet4Address());
    addAddresses(builder, options.getInet6Address());

    if (options.getAutoRoute()) {
      addRoutes(builder, options.getInet4RouteAddress());
      addRoutes(builder, options.getInet6RouteAddress());
      addRoutes(builder, options.getInet4RouteRange());
      addRoutes(builder, options.getInet6RouteRange());
    }

    try {
      io.nekohasekai.libbox.StringBox dnsServerBox = options.getDNSServerAddress();
      String dnsServer = dnsServerBox != null ? dnsServerBox.getValue() : null;
      if (dnsServer != null && !dnsServer.isEmpty()) {
        builder.addDnsServer(dnsServer);
      }
    } catch (Exception e) {
      Log.w("sing-box: unable to configure DNS server for tun: %s", e.getMessage());
    }

    applyPackageRules(builder, options);

    ParcelFileDescriptor pfd = builder.establish();
    if (pfd == null) {
      throw new IllegalStateException("VpnService.Builder.establish() returned null");
    }

    synchronized (tunLock) {
      closeTunLocked();
      tunInterface = pfd;
      return tunInterface.getFd();
    }
  }

  @Override
  public WIFIState readWIFIState () {
    return null;
  }

  @Override
  public void sendNotification (Notification notification) throws Exception {
    Log.d("sing-box notification: %s", notification.getTitle());
  }

  @Override
  public void startDefaultInterfaceMonitor (InterfaceUpdateListener listener) throws Exception {
    // No-op in bridge mode
  }

  @Override
  public StringIterator systemCertificates () {
    try {
      KeyStore keyStore = KeyStore.getInstance("AndroidCAStore");
      keyStore.load(null, null);
      List<String> certs = new ArrayList<>();
      Enumeration<String> aliases = keyStore.aliases();
      while (aliases.hasMoreElements()) {
        String alias = aliases.nextElement();
        Certificate cert = keyStore.getCertificate(alias);
        if (cert instanceof X509Certificate) {
          certs.add(android.util.Base64.encodeToString(cert.getEncoded(), android.util.Base64.NO_WRAP));
        }
      }
      final List<String> finalCerts = certs;
      return new StringIterator() {
        private int index = 0;

        @Override
        public boolean hasNext () {
          return index < finalCerts.size();
        }

        @Override
        public int len () {
          return finalCerts.size();
        }

        @Override
        public String next () {
          return finalCerts.get(index++);
        }
      };
    } catch (Exception e) {
      Log.e("Failed to load system certificates", e);
      return new StringIterator() {
        @Override public boolean hasNext () { return false; }
        @Override public int len () { return 0; }
        @Override public String next () { return null; }
      };
    }
  }

  @Override
  public boolean underNetworkExtension () {
    return false;
  }

  @Override
  public boolean usePlatformAutoDetectInterfaceControl () {
    return Settings.instance().isSingBoxVpnModeEnabled();
  }

  @Override
  public boolean useProcFS () {
    return false;
  }

  public void closeTun () {
    synchronized (tunLock) {
      closeTunLocked();
    }
  }

  private void closeTunLocked () {
    if (tunInterface != null) {
      try {
        tunInterface.close();
      } catch (Exception ignored) { }
      tunInterface = null;
    }
  }

  private static void addAddresses (VpnService.Builder builder, RoutePrefixIterator iterator) {
    if (iterator == null) return;
    while (iterator.hasNext()) {
      io.nekohasekai.libbox.RoutePrefix prefix = iterator.next();
      if (prefix == null) continue;
      try {
        builder.addAddress(prefix.address(), prefix.prefix());
      } catch (Exception e) {
        Log.w("sing-box: skip tun address %s/%d: %s", prefix.address(), prefix.prefix(), e.getMessage());
      }
    }
  }

  private static void addRoutes (VpnService.Builder builder, RoutePrefixIterator iterator) {
    if (iterator == null) return;
    while (iterator.hasNext()) {
      io.nekohasekai.libbox.RoutePrefix prefix = iterator.next();
      if (prefix == null) continue;
      try {
        builder.addRoute(prefix.address(), prefix.prefix());
      } catch (Exception e) {
        Log.w("sing-box: skip tun route %s/%d: %s", prefix.address(), prefix.prefix(), e.getMessage());
      }
    }
  }

  private void applyPackageRules (VpnService.Builder builder, TunOptions options) {
    StringIterator include = options.getIncludePackage();
    boolean hasInclude = include != null && include.hasNext();
    try {
      if (hasInclude) {
        while (include.hasNext()) {
          String packageName = include.next();
          if (packageName != null && !packageName.isEmpty()) {
            builder.addAllowedApplication(packageName);
          }
        }
        return;
      }

      StringIterator exclude = options.getExcludePackage();
      if (exclude != null) {
        while (exclude.hasNext()) {
          String packageName = exclude.next();
          if (packageName != null && !packageName.isEmpty()) {
            builder.addDisallowedApplication(packageName);
          }
        }
      }
    } catch (Exception e) {
      Log.w("sing-box: apply package rule failed: %s", e.getMessage());
    }
  }
}
