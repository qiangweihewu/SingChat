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

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Intents;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SingBoxVpnService extends VpnService {
  private static final String ACTION_START = "org.thunderdog.challegram.singbox.VPN_START";
  private static final String ACTION_STOP = "org.thunderdog.challegram.singbox.VPN_STOP";
  private static final int NOTIFICATION_ID = 0x5B04;

  private static volatile @Nullable SingBoxVpnService sInstance;
  private static volatile CountDownLatch sRunningLatch = new CountDownLatch(1);

  public static void start (Context context) {
    Intent intent = new Intent(context, SingBoxVpnService.class).setAction(ACTION_START);
    ContextCompat.startForegroundService(context, intent);
  }

  public static void stop (Context context) {
    Intent intent = new Intent(context, SingBoxVpnService.class).setAction(ACTION_STOP);
    context.stopService(intent);
  }

  public static @Nullable SingBoxVpnService instance () {
    return sInstance;
  }

  public static @Nullable SingBoxVpnService waitForRunning (long timeoutMs) {
    SingBoxVpnService instance = sInstance;
    if (instance != null) {
      return instance;
    }
    try {
      if (sRunningLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
        return sInstance;
      }
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
    return sInstance;
  }

  @Override
  public void onCreate () {
    super.onCreate();
    sInstance = this;
    sRunningLatch.countDown();
  }

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    String action = intent != null ? intent.getAction() : null;
    if (ACTION_STOP.equals(action)) {
      stopForeground(true);
      stopSelf();
      return START_NOT_STICKY;
    }
    startForegroundInternal();
    return START_STICKY;
  }

  @Override
  public void onRevoke () {
    Log.w("sing-box vpn permission revoked by system");
    stopForeground(true);
    stopSelf();
    org.thunderdog.challegram.unsorted.Settings.instance().setSingBoxVpnModeEnabled(false);
    SingBoxManager.instance().onVpnModeChangedByUser();
  }

  @Override
  public void onDestroy () {
    stopForeground(true);
    super.onDestroy();
    sInstance = null;
    sRunningLatch = new CountDownLatch(1);
  }

  private void startForegroundInternal () {
    Notification notification = buildNotification();
    startForeground(NOTIFICATION_ID, notification);
  }

  private Notification buildNotification () {
    PendingIntent contentIntent = PendingIntent.getActivity(
      this, 0, new Intent(this, MainActivity.class), Intents.mutabilityFlags(false)
    );
    return new NotificationCompat.Builder(this, U.getOtherNotificationChannel())
      .setSmallIcon(R.drawable.baseline_vpn_key_24)
      .setContentTitle(Lang.getString(R.string.SingBoxProxy))
      .setContentText(Lang.getString(R.string.ProxyVpnMode))
      .setOngoing(true)
      .setContentIntent(contentIntent)
      .build();
  }

  @Nullable
  @Override
  public IBinder onBind (Intent intent) {
    if (intent != null && VpnService.SERVICE_INTERFACE.equals(intent.getAction())) {
      return super.onBind(intent);
    }
    return null;
  }
}
