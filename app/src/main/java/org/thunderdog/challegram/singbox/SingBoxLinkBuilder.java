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

import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.thunderdog.challegram.Log;

import java.nio.charset.StandardCharsets;

public class SingBoxLinkBuilder {

  public static @Nullable String buildShareLink (@NonNull String outboundType,
      @NonNull String outboundJson, @Nullable String remark) {
    try {
      JSONObject json = new JSONObject(outboundJson);
      switch (outboundType) {
        case "vless":
          return buildVlessLink(json, remark);
        case "shadowsocks":
          return buildShadowsocksLink(json, remark);
        case "trojan":
          return buildTrojanLink(json, remark);
        case "vmess":
          return buildVmessLink(json, remark);
        default:
          return null;
      }
    } catch (Exception e) {
      Log.e("Failed to build share link", e);
      return null;
    }
  }

  private static @NonNull String buildVlessLink (@NonNull JSONObject json, @Nullable String remark) {
    String uuid = json.optString("uuid", "");
    String server = json.optString("server", "");
    int port = json.optInt("server_port", 0);
    String flow = json.optString("flow", "");

    Uri.Builder builder = new Uri.Builder();
    builder.scheme("vless");
    builder.encodedAuthority(Uri.encode(uuid) + "@" + server + ":" + port);

    // TLS
    JSONObject tls = json.optJSONObject("tls");
    if (tls != null && tls.optBoolean("enabled", false)) {
      builder.appendQueryParameter("security", "tls");
      String sni = tls.optString("server_name", "");
      if (!sni.isEmpty()) {
        builder.appendQueryParameter("sni", sni);
      }
    }

    if (!flow.isEmpty()) {
      builder.appendQueryParameter("flow", flow);
    }

    // Transport
    JSONObject transport = json.optJSONObject("transport");
    if (transport != null) {
      String type = transport.optString("type", "tcp");
      builder.appendQueryParameter("type", type);
      String path = transport.optString("path", "");
      String serviceName = transport.optString("service_name", "");
      if (!path.isEmpty()) {
        builder.appendQueryParameter("path", path);
      } else if (!serviceName.isEmpty()) {
        builder.appendQueryParameter("path", serviceName);
      }
    } else {
      builder.appendQueryParameter("type", "tcp");
    }

    if (remark != null && !remark.isEmpty()) {
      builder.fragment(remark);
    }

    return builder.build().toString();
  }

  private static @NonNull String buildShadowsocksLink (@NonNull JSONObject json, @Nullable String remark) {
    String server = json.optString("server", "");
    int port = json.optInt("server_port", 0);
    String method = json.optString("method", "");
    String password = json.optString("password", "");

    // Format: ss://base64(method:password)@server:port#remark
    String userInfo = method + ":" + password;
    String encoded = Base64.encodeToString(userInfo.getBytes(StandardCharsets.UTF_8),
      Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);

    StringBuilder sb = new StringBuilder("ss://");
    sb.append(encoded).append("@").append(server).append(":").append(port);
    if (remark != null && !remark.isEmpty()) {
      sb.append("#").append(Uri.encode(remark));
    }
    return sb.toString();
  }

  private static @NonNull String buildTrojanLink (@NonNull JSONObject json, @Nullable String remark) {
    String server = json.optString("server", "");
    int port = json.optInt("server_port", 0);
    String password = json.optString("password", "");

    Uri.Builder builder = new Uri.Builder();
    builder.scheme("trojan");
    builder.encodedAuthority(Uri.encode(password) + "@" + server + ":" + port);

    JSONObject tls = json.optJSONObject("tls");
    if (tls != null) {
      String sni = tls.optString("server_name", "");
      if (!sni.isEmpty()) {
        builder.appendQueryParameter("sni", sni);
      }
    }

    if (remark != null && !remark.isEmpty()) {
      builder.fragment(remark);
    }

    return builder.build().toString();
  }

  private static @NonNull String buildVmessLink (@NonNull JSONObject json, @Nullable String remark) {
    // vmess://base64(json)
    JSONObject vmessJson = new JSONObject();
    try {
      vmessJson.put("v", "2");
      vmessJson.put("ps", remark != null ? remark : "");
      vmessJson.put("add", json.optString("server", ""));
      vmessJson.put("port", json.optInt("server_port", 0));
      vmessJson.put("id", json.optString("uuid", ""));
      vmessJson.put("aid", json.optInt("alter_id", 0));
      vmessJson.put("scy", json.optString("security", "auto"));

      JSONObject transport = json.optJSONObject("transport");
      if (transport != null) {
        vmessJson.put("net", transport.optString("type", "tcp"));
        String path = transport.optString("path", "");
        String serviceName = transport.optString("service_name", "");
        vmessJson.put("path", !path.isEmpty() ? path : serviceName);
      } else {
        vmessJson.put("net", "tcp");
      }

      JSONObject tls = json.optJSONObject("tls");
      vmessJson.put("tls", tls != null && tls.optBoolean("enabled", false) ? "tls" : "");
      if (tls != null) {
        vmessJson.put("sni", tls.optString("server_name", ""));
      }
    } catch (Exception e) {
      Log.e("Failed to build VMess JSON", e);
    }

    String encoded = Base64.encodeToString(vmessJson.toString().getBytes(StandardCharsets.UTF_8),
      Base64.NO_WRAP | Base64.NO_PADDING);
    return "vmess://" + encoded;
  }
}
