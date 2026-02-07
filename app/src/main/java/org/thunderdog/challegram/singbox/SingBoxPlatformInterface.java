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
import android.os.Build;
import android.os.ParcelFileDescriptor;

import org.thunderdog.challegram.Log;

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
import io.nekohasekai.libbox.StringIterator;
import io.nekohasekai.libbox.TunOptions;
import io.nekohasekai.libbox.WIFIState;

public class SingBoxPlatformInterface implements PlatformInterface {
  private final Context context;

  public SingBoxPlatformInterface (Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void autoDetectInterfaceControl (int fd) throws Exception {
    // Bind socket to default network so outbound connections use the correct interface.
    // In VPN mode this prevents routing loops; in bridge mode it ensures proper routing.
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
    throw new UnsupportedOperationException("TUN not supported in bridge mode");
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
    return false; // Bridge mode: use system default routing, no interface binding needed
  }

  @Override
  public boolean useProcFS () {
    return false;
  }
}
