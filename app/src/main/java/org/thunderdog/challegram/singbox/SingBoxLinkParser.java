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

import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.Log;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class SingBoxLinkParser {

  public static @Nullable SingBoxProxyConfig parse (@NonNull String link) {
    if (link.startsWith("vless://")) {
      return parseVless(link);
    } else if (link.startsWith("ss://")) {
      return parseShadowsocks(link);
    } else if (link.startsWith("trojan://")) {
      return parseTrojan(link);
    } else if (link.startsWith("vmess://")) {
      return parseVmess(link);
    }
    return null;
  }

  private static @Nullable SingBoxProxyConfig parseVless (@NonNull String link) {
    try {
      // vless://uuid@server:port?security=tls&sni=name&type=tcp&flow=...&fp=chrome&pbk=KEY&sid=ID&alpn=h2,http/1.1#remark
      Uri uri = Uri.parse(link);
      String uuid = uri.getUserInfo();
      String server = uri.getHost();
      int port = uri.getPort();
      if (uuid == null || server == null || port <= 0) return null;

      String security = getLastQueryParam(uri, "security", "");
      String sni = getQueryParam(uri, "sni", "");
      String type = getQueryParam(uri, "type", "");
      if (type.isEmpty()) {
        type = getQueryParam(uri, "network", "tcp");
      }
      String flow = getQueryParam(uri, "flow", "");
      String path = getQueryParam(uri, "path", "");
      String serviceName = getQueryParam(uri, "serviceName", "");
      if (path.isEmpty() && !serviceName.isEmpty()) {
        path = serviceName;
      }
      String host = getQueryParam(uri, "host", "");
      String fingerprint = getQueryParam(uri, "fp", "");
      String publicKey = getQueryParam(uri, "pbk", "");
      String shortId = getQueryParam(uri, "sid", "");
      if (shortId.isEmpty()) {
        shortId = getQueryParam(uri, "shortId", "");
      }
      String alpn = getQueryParam(uri, "alpn", "");
      String remark = uri.getFragment();

      String outboundJson = SingBoxConfigBuilder.buildVlessOutbound(
        server, port, uuid, flow, security, sni, fingerprint,
        publicKey, shortId, alpn, type, path, host
      );

      SingBoxProxyConfig config = new SingBoxProxyConfig("vless", server, port, outboundJson);
      config.remark = remark;
      return config;
    } catch (Exception e) {
      Log.w("Failed to parse VLESS link", e);
      return null;
    }
  }

  private static @Nullable SingBoxProxyConfig parseShadowsocks (@NonNull String link) {
    try {
      // Format 1: ss://base64(method:password)@server:port#remark
      // Format 2: ss://base64(method:password@server:port)#remark
      String content = link.substring(5); // Remove "ss://"

      String remark = null;
      int hashIdx = content.indexOf('#');
      if (hashIdx != -1) {
        remark = Uri.decode(content.substring(hashIdx + 1));
        content = content.substring(0, hashIdx);
      }

      String server;
      int port;
      String method;
      String password;

      int atIdx = content.indexOf('@');
      if (atIdx != -1) {
        // Format 1: base64(method:password)@server:port
        String userInfo = decodeBase64(content.substring(0, atIdx));
        String hostPart = content.substring(atIdx + 1);

        int colonIdx = userInfo.indexOf(':');
        if (colonIdx == -1) return null;
        method = userInfo.substring(0, colonIdx);
        password = userInfo.substring(colonIdx + 1);

        int portColonIdx = hostPart.lastIndexOf(':');
        if (portColonIdx == -1) return null;
        server = hostPart.substring(0, portColonIdx);
        port = Integer.parseInt(hostPart.substring(portColonIdx + 1));
      } else {
        // Format 2: base64(method:password@server:port)
        String decoded = decodeBase64(content);
        int colonIdx = decoded.indexOf(':');
        if (colonIdx == -1) return null;
        method = decoded.substring(0, colonIdx);

        String rest = decoded.substring(colonIdx + 1);
        atIdx = rest.lastIndexOf('@');
        if (atIdx == -1) return null;
        password = rest.substring(0, atIdx);
        String hostPart = rest.substring(atIdx + 1);

        int portColonIdx = hostPart.lastIndexOf(':');
        if (portColonIdx == -1) return null;
        server = hostPart.substring(0, portColonIdx);
        port = Integer.parseInt(hostPart.substring(portColonIdx + 1));
      }

      String outboundJson = SingBoxConfigBuilder.buildShadowsocksOutbound(server, port, method, password);
      SingBoxProxyConfig config = new SingBoxProxyConfig("shadowsocks", server, port, outboundJson);
      config.remark = remark;
      return config;
    } catch (Exception e) {
      Log.w("Failed to parse Shadowsocks link", e);
      return null;
    }
  }

  private static @Nullable SingBoxProxyConfig parseTrojan (@NonNull String link) {
    try {
      // trojan://password@server:port?sni=name&fp=chrome&alpn=h2,http/1.1#remark
      Uri uri = Uri.parse(link);
      String password = uri.getUserInfo();
      String server = uri.getHost();
      int port = uri.getPort();
      if (password == null || server == null || port <= 0) return null;

      String sni = getQueryParam(uri, "sni", "");
      String fingerprint = getQueryParam(uri, "fp", "");
      String alpn = getQueryParam(uri, "alpn", "");
      String remark = uri.getFragment();

      String outboundJson = SingBoxConfigBuilder.buildTrojanOutbound(server, port, password, sni, fingerprint, alpn);
      SingBoxProxyConfig config = new SingBoxProxyConfig("trojan", server, port, outboundJson);
      config.remark = remark;
      return config;
    } catch (Exception e) {
      Log.w("Failed to parse Trojan link", e);
      return null;
    }
  }

  private static @Nullable SingBoxProxyConfig parseVmess (@NonNull String link) {
    try {
      // vmess://base64(json)
      String encoded = link.substring(8); // Remove "vmess://"
      String decoded = decodeBase64(encoded);
      JSONObject json = new JSONObject(decoded);

      String server = json.optString("add", "");
      int port = json.optInt("port", 0);
      String uuid = json.optString("id", "");
      int alterId = json.optInt("aid", 0);
      String security = json.optString("scy", "auto");
      String net = json.optString("net", "tcp");
      String path = json.optString("path", "");
      String host = json.optString("host", "");
      String remark = json.optString("ps", null);
      String tlsValue = json.optString("tls", "");
      String sni = json.optString("sni", "");
      String fingerprint = json.optString("fp", "");
      String alpn = json.optString("alpn", "");

      if (server.isEmpty() || port <= 0 || uuid.isEmpty()) return null;

      boolean tls = "tls".equals(tlsValue);

      String outboundJson = SingBoxConfigBuilder.buildVmessOutbound(
        server, port, uuid, alterId, security, net, path,
        tls, sni, fingerprint, alpn, host
      );
      SingBoxProxyConfig config = new SingBoxProxyConfig("vmess", server, port, outboundJson);
      config.remark = remark;
      return config;
    } catch (Exception e) {
      Log.w("Failed to parse VMess link", e);
      return null;
    }
  }

  private static @NonNull String getQueryParam (@NonNull Uri uri, @NonNull String key, @NonNull String defaultValue) {
    String value = uri.getQueryParameter(key);
    return value != null ? value : defaultValue;
  }

  private static @NonNull String getLastQueryParam (@NonNull Uri uri, @NonNull String key, @NonNull String defaultValue) {
    List<String> values = uri.getQueryParameters(key);
    return values.isEmpty() ? defaultValue : values.get(values.size() - 1);
  }

  private static @NonNull String decodeBase64 (@NonNull String input) {
    // Pad base64 if necessary
    String padded = input;
    int remainder = padded.length() % 4;
    if (remainder != 0) {
      padded = padded + "====".substring(remainder);
    }
    byte[] decoded = Base64.decode(padded, Base64.URL_SAFE | Base64.NO_WRAP);
    return new String(decoded, StandardCharsets.UTF_8);
  }
}
