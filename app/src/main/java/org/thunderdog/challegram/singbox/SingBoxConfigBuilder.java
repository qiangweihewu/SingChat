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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SingBoxConfigBuilder {

  public static @NonNull String buildVlessOutbound (@NonNull String server, int port,
      @NonNull String uuid, @NonNull String flow, @NonNull String security, @NonNull String sni,
      @NonNull String fingerprint, @NonNull String publicKey, @NonNull String shortId,
      @NonNull String alpn, @NonNull String transport, @NonNull String transportPath,
      @NonNull String transportHost) throws JSONException {
    JSONObject outbound = new JSONObject();
    outbound.put("type", "vless");
    outbound.put("tag", "proxy");
    outbound.put("server", server);
    outbound.put("server_port", port);
    outbound.put("uuid", uuid);
    if (!flow.isEmpty()) {
      outbound.put("flow", flow);
    }
    boolean tls = "tls".equals(security) || "reality".equals(security);
    if (tls) {
      JSONObject tlsObj = new JSONObject();
      tlsObj.put("enabled", true);
      if (!sni.isEmpty()) {
        tlsObj.put("server_name", sni);
      }
      if (!alpn.isEmpty()) {
        JSONArray alpnArray = new JSONArray();
        for (String a : alpn.split(",")) {
          String trimmed = a.trim();
          if (!trimmed.isEmpty()) {
            alpnArray.put(trimmed);
          }
        }
        if (alpnArray.length() > 0) {
          tlsObj.put("alpn", alpnArray);
        }
      }
      if (!fingerprint.isEmpty()) {
        JSONObject utls = new JSONObject();
        utls.put("enabled", true);
        utls.put("fingerprint", fingerprint);
        tlsObj.put("utls", utls);
      }
      if ("reality".equals(security)) {
        JSONObject reality = new JSONObject();
        reality.put("enabled", true);
        if (!publicKey.isEmpty()) {
          reality.put("public_key", publicKey);
        }
        if (!shortId.isEmpty()) {
          reality.put("short_id", shortId);
        }
        tlsObj.put("reality", reality);
      }
      outbound.put("tls", tlsObj);
    }
    if (!transport.isEmpty() && !"tcp".equals(transport)) {
      JSONObject transportObj = new JSONObject();
      transportObj.put("type", transport);
      if ("ws".equals(transport)) {
        if (!transportPath.isEmpty()) {
          transportObj.put("path", transportPath);
        }
        if (!transportHost.isEmpty()) {
          JSONObject headers = new JSONObject();
          headers.put("Host", transportHost);
          transportObj.put("headers", headers);
        }
      } else if ("grpc".equals(transport)) {
        if (!transportPath.isEmpty()) {
          transportObj.put("service_name", transportPath);
        }
      } else if ("http".equals(transport) || "h2".equals(transport)) {
        transportObj.put("type", "http");
        if (!transportPath.isEmpty()) {
          transportObj.put("path", transportPath);
        }
        if (!transportHost.isEmpty()) {
          transportObj.put("host", new JSONArray().put(transportHost));
        }
      } else if ("httpupgrade".equals(transport)) {
        if (!transportPath.isEmpty()) {
          transportObj.put("path", transportPath);
        }
        if (!transportHost.isEmpty()) {
          transportObj.put("host", transportHost);
        }
      }
      outbound.put("transport", transportObj);
    }
    return outbound.toString();
  }

  public static @NonNull String buildShadowsocksOutbound (@NonNull String server, int port,
      @NonNull String method, @NonNull String password) throws JSONException {
    JSONObject outbound = new JSONObject();
    outbound.put("type", "shadowsocks");
    outbound.put("tag", "proxy");
    outbound.put("server", server);
    outbound.put("server_port", port);
    outbound.put("method", method);
    outbound.put("password", password);
    return outbound.toString();
  }

  public static @NonNull String buildTrojanOutbound (@NonNull String server, int port,
      @NonNull String password, @NonNull String sni, @NonNull String fingerprint,
      @NonNull String alpn) throws JSONException {
    JSONObject outbound = new JSONObject();
    outbound.put("type", "trojan");
    outbound.put("tag", "proxy");
    outbound.put("server", server);
    outbound.put("server_port", port);
    outbound.put("password", password);
    JSONObject tlsObj = new JSONObject();
    tlsObj.put("enabled", true);
    if (!sni.isEmpty()) {
      tlsObj.put("server_name", sni);
    }
    if (!alpn.isEmpty()) {
      JSONArray alpnArray = new JSONArray();
      for (String a : alpn.split(",")) {
        String trimmed = a.trim();
        if (!trimmed.isEmpty()) {
          alpnArray.put(trimmed);
        }
      }
      if (alpnArray.length() > 0) {
        tlsObj.put("alpn", alpnArray);
      }
    }
    if (!fingerprint.isEmpty()) {
      JSONObject utls = new JSONObject();
      utls.put("enabled", true);
      utls.put("fingerprint", fingerprint);
      tlsObj.put("utls", utls);
    }
    outbound.put("tls", tlsObj);
    return outbound.toString();
  }

  public static @NonNull String buildVmessOutbound (@NonNull String server, int port,
      @NonNull String uuid, int alterId, @NonNull String security,
      @NonNull String transport, @NonNull String transportPath,
      boolean tls, @NonNull String sni, @NonNull String fingerprint,
      @NonNull String alpn, @NonNull String transportHost) throws JSONException {
    JSONObject outbound = new JSONObject();
    outbound.put("type", "vmess");
    outbound.put("tag", "proxy");
    outbound.put("server", server);
    outbound.put("server_port", port);
    outbound.put("uuid", uuid);
    outbound.put("alter_id", alterId);
    if (!security.isEmpty()) {
      outbound.put("security", security);
    }
    if (tls) {
      JSONObject tlsObj = new JSONObject();
      tlsObj.put("enabled", true);
      if (!sni.isEmpty()) {
        tlsObj.put("server_name", sni);
      }
      if (!alpn.isEmpty()) {
        JSONArray alpnArray = new JSONArray();
        for (String a : alpn.split(",")) {
          String trimmed = a.trim();
          if (!trimmed.isEmpty()) {
            alpnArray.put(trimmed);
          }
        }
        if (alpnArray.length() > 0) {
          tlsObj.put("alpn", alpnArray);
        }
      }
      if (!fingerprint.isEmpty()) {
        JSONObject utls = new JSONObject();
        utls.put("enabled", true);
        utls.put("fingerprint", fingerprint);
        tlsObj.put("utls", utls);
      }
      outbound.put("tls", tlsObj);
    }
    if (!transport.isEmpty() && !"tcp".equals(transport)) {
      JSONObject transportObj = new JSONObject();
      transportObj.put("type", transport);
      if ("ws".equals(transport)) {
        if (!transportPath.isEmpty()) {
          transportObj.put("path", transportPath);
        }
        if (!transportHost.isEmpty()) {
          JSONObject headers = new JSONObject();
          headers.put("Host", transportHost);
          transportObj.put("headers", headers);
        }
      } else if ("grpc".equals(transport)) {
        if (!transportPath.isEmpty()) {
          transportObj.put("service_name", transportPath);
        }
      } else if ("http".equals(transport) || "h2".equals(transport)) {
        transportObj.put("type", "http");
        if (!transportPath.isEmpty()) {
          transportObj.put("path", transportPath);
        }
        if (!transportHost.isEmpty()) {
          transportObj.put("host", new JSONArray().put(transportHost));
        }
      } else if ("httpupgrade".equals(transport)) {
        if (!transportPath.isEmpty()) {
          transportObj.put("path", transportPath);
        }
        if (!transportHost.isEmpty()) {
          transportObj.put("host", transportHost);
        }
      }
      outbound.put("transport", transportObj);
    }
    return outbound.toString();
  }

  public static @NonNull String buildFullConfig (int localPort, @NonNull String outboundJson, @NonNull String logFilePath) throws JSONException {
    JSONObject config = new JSONObject();

    // Log
    JSONObject log = new JSONObject();
    log.put("level", "debug");
    log.put("timestamp", true);
    if (!logFilePath.isEmpty()) {
      log.put("output", logFilePath);
    }
    config.put("log", log);

    // DNS: use plain UDP DNS for reliability
    JSONObject dns = new JSONObject();
    JSONArray dnsServers = new JSONArray();
    // Primary: through proxy for resolving destinations
    JSONObject dnsProxy = new JSONObject();
    dnsProxy.put("tag", "dns-proxy");
    dnsProxy.put("address", "8.8.8.8");
    dnsProxy.put("detour", "proxy");
    dnsServers.put(dnsProxy);
    // Fallback: direct for resolving proxy server hostname
    JSONObject dnsDirect = new JSONObject();
    dnsDirect.put("tag", "dns-direct");
    dnsDirect.put("address", "local");
    dnsDirect.put("detour", "direct");
    dnsServers.put(dnsDirect);
    dns.put("servers", dnsServers);
    // DNS rules: resolve proxy server's hostname via direct DNS
    JSONArray dnsRules = new JSONArray();
    JSONObject dnsRule = new JSONObject();
    dnsRule.put("outbound", new JSONArray().put("proxy"));
    dnsRule.put("server", "dns-direct");
    dnsRules.put(dnsRule);
    dns.put("rules", dnsRules);
    config.put("dns", dns);

    // Inbound: local SOCKS5
    JSONObject inbound = new JSONObject();
    inbound.put("type", "socks");
    inbound.put("tag", "socks-in");
    inbound.put("listen", "127.0.0.1");
    inbound.put("listen_port", localPort);
    config.put("inbounds", new JSONArray().put(inbound));

    // Outbound: user's proxy + direct + dns
    JSONObject outbound = new JSONObject(outboundJson);
    JSONObject directOutbound = new JSONObject();
    directOutbound.put("type", "direct");
    directOutbound.put("tag", "direct");
    JSONObject dnsOutbound = new JSONObject();
    dnsOutbound.put("type", "dns");
    dnsOutbound.put("tag", "dns-out");
    config.put("outbounds", new JSONArray().put(outbound).put(directOutbound).put(dnsOutbound));

    // Route
    JSONObject route = new JSONObject();
    // Note: do NOT set auto_detect_interface in bridge mode (non-VPN).
    // It causes "no available network interface" errors on Android.
    // Regular sockets use the system default route, which is correct for bridge mode.
    JSONArray routeRules = new JSONArray();
    // DNS hijack rule: route DNS queries to dns-out
    JSONObject dnsRouteRule = new JSONObject();
    dnsRouteRule.put("protocol", "dns");
    dnsRouteRule.put("outbound", "dns-out");
    routeRules.put(dnsRouteRule);
    route.put("rules", routeRules);
    config.put("route", route);

    return config.toString();
  }
}
