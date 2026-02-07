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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SingBoxProxyConfig {
  public final @NonNull String outboundType;
  public final @NonNull String server;
  public final int serverPort;
  public final @NonNull String outboundJson;
  public int localPort;
  public @Nullable String remark;

  public SingBoxProxyConfig (@NonNull String outboundType, @NonNull String server, int serverPort,
      @NonNull String outboundJson) {
    this.outboundType = outboundType;
    this.server = server;
    this.serverPort = serverPort;
    this.outboundJson = outboundJson;
  }
}
