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
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.unsorted.Settings;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;
import me.vkryl.core.reference.ReferenceList;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ProxySubscriptionManager {
  private static volatile ProxySubscriptionManager sInstance;

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final ReferenceList<SubscriptionChangeListener> listeners = new ReferenceList<>();
  private OkHttpClient httpClient;
  private boolean initialized;

  public static @NonNull ProxySubscriptionManager instance () {
    if (sInstance == null) {
      synchronized (ProxySubscriptionManager.class) {
        if (sInstance == null) {
          sInstance = new ProxySubscriptionManager();
        }
      }
    }
    return sInstance;
  }

  private ProxySubscriptionManager () { }

  public void initialize (@NonNull Context context) {
    if (initialized) return;
    initialized = true;
    scheduleAutoRefreshAll();
  }

  private @NonNull OkHttpClient getHttpClient () {
    if (httpClient == null) {
      synchronized (this) {
        if (httpClient == null) {
          httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
        }
      }
    }
    return httpClient;
  }

  // Unified parsed proxy result

  static class ParsedProxy {
    // For sing-box types (vless, vmess, ss, trojan)
    final @Nullable SingBoxProxyConfig singBoxConfig;
    // For TDLib types (socks5, mtproto, http)
    final @Nullable TdApi.InternalLinkTypeProxy tdlibProxy;
    final @Nullable String remark;

    ParsedProxy (@NonNull SingBoxProxyConfig singBoxConfig) {
      this.singBoxConfig = singBoxConfig;
      this.tdlibProxy = null;
      this.remark = singBoxConfig.remark;
    }

    ParsedProxy (@NonNull TdApi.InternalLinkTypeProxy tdlibProxy, @Nullable String remark) {
      this.singBoxConfig = null;
      this.tdlibProxy = tdlibProxy;
      this.remark = remark;
    }

    boolean isSingBox () {
      return singBoxConfig != null;
    }

    @NonNull String identityKey () {
      if (singBoxConfig != null) {
        return "sb:" + singBoxConfig.outboundJson.hashCode();
      } else if (tdlibProxy != null) {
        return "td:" + tdlibProxy.server + ":" + tdlibProxy.port + ":" + tdlibProxy.type.getConstructor();
      }
      return "";
    }
  }

  // Listener management

  public interface SubscriptionChangeListener {
    void onSubscriptionAdded (Settings.Subscription subscription);
    void onSubscriptionUpdated (Settings.Subscription subscription);
    void onSubscriptionRemoved (int subscriptionId);
    void onSubscriptionRefreshStarted (int subscriptionId);
    void onSubscriptionRefreshCompleted (int subscriptionId, boolean success, int added, int removed);
  }

  public void addListener (SubscriptionChangeListener listener) {
    listeners.add(listener);
  }

  public void removeListener (SubscriptionChangeListener listener) {
    listeners.remove(listener);
  }

  // Public API

  public int addSubscription (@NonNull String url, @Nullable String name, long intervalMs) {
    int subId = Settings.instance().addSubscription(url, name, intervalMs);
    Settings.Subscription sub = Settings.instance().getSubscription(subId);
    if (sub != null) {
      for (SubscriptionChangeListener listener : listeners) {
        listener.onSubscriptionAdded(sub);
      }
    }
    refreshSubscription(subId, null);
    return subId;
  }

  public void removeSubscription (int subId) {
    Settings.instance().removeSubscription(subId);
    for (SubscriptionChangeListener listener : listeners) {
      listener.onSubscriptionRemoved(subId);
    }
  }

  public void refreshSubscription (int subId, @Nullable Runnable onComplete) {
    for (SubscriptionChangeListener listener : listeners) {
      listener.onSubscriptionRefreshStarted(subId);
    }
    executor.execute(() -> {
      Settings.Subscription sub = Settings.instance().getSubscription(subId);
      if (sub == null) {
        mainHandler.post(() -> {
          for (SubscriptionChangeListener listener : listeners) {
            listener.onSubscriptionRefreshCompleted(subId, false, 0, 0);
          }
          if (onComplete != null) onComplete.run();
        });
        return;
      }

      try {
        List<ParsedProxy> parsed = fetchAndParse(sub.url);
        int[] result = reconcileProxies(subId, parsed, sub.proxyIds);
        int added = result[0];
        int removed = result[1];

        Settings.instance().updateSubscriptionLastUpdate(subId, System.currentTimeMillis());

        mainHandler.post(() -> {
          Settings.Subscription updated = Settings.instance().getSubscription(subId);
          if (updated != null) {
            for (SubscriptionChangeListener listener : listeners) {
              listener.onSubscriptionUpdated(updated);
            }
          }
          for (SubscriptionChangeListener listener : listeners) {
            listener.onSubscriptionRefreshCompleted(subId, true, added, removed);
          }
          if (onComplete != null) onComplete.run();
        });

        scheduleAutoRefresh(subId, sub.refreshIntervalMs);
      } catch (Exception e) {
        Log.e("Failed to refresh subscription %d", e, subId);
        mainHandler.post(() -> {
          for (SubscriptionChangeListener listener : listeners) {
            listener.onSubscriptionRefreshCompleted(subId, false, 0, 0);
          }
          if (onComplete != null) onComplete.run();
        });
      }
    });
  }

  public void refreshAllSubscriptions (@Nullable Runnable onComplete) {
    List<Settings.Subscription> subs = Settings.instance().getAvailableSubscriptions();
    if (subs.isEmpty()) {
      if (onComplete != null) onComplete.run();
      return;
    }
    final int[] remaining = {subs.size()};
    for (Settings.Subscription sub : subs) {
      refreshSubscription(sub.id, () -> {
        synchronized (remaining) {
          remaining[0]--;
          if (remaining[0] <= 0 && onComplete != null) {
            onComplete.run();
          }
        }
      });
    }
  }

  public @NonNull List<Settings.Subscription> getSubscriptions () {
    return Settings.instance().getAvailableSubscriptions();
  }

  // Core logic

  private @NonNull List<ParsedProxy> fetchAndParse (@NonNull String url) throws Exception {
    Request request = new Request.Builder()
      .url(url)
      .header("User-Agent", "ClashForAndroid/2.5.12")
      .build();
    Response response = getHttpClient().newCall(request).execute();
    ResponseBody body = response.body();
    if (!response.isSuccessful() || body == null) {
      throw new Exception("HTTP " + response.code());
    }

    String rawBody = body.string();
    String decoded;
    try {
      // Try base64 decode first (most subscription services use this)
      String cleaned = rawBody.trim().replace("\n", "").replace("\r", "");
      byte[] bytes = Base64.decode(cleaned, Base64.DEFAULT | Base64.NO_WRAP);
      decoded = new String(bytes, StandardCharsets.UTF_8);
    } catch (Exception e) {
      // Not base64 encoded, use as-is (some services return plain text)
      decoded = rawBody;
    }

    List<ParsedProxy> configs = new ArrayList<>();
    String[] lines = decoded.split("\n");
    for (String line : lines) {
      line = line.trim();
      if (line.isEmpty()) continue;

      // Try sing-box protocols: vless://, ss://, trojan://, vmess://
      SingBoxProxyConfig sbConfig = SingBoxLinkParser.parse(line);
      if (sbConfig != null) {
        configs.add(new ParsedProxy(sbConfig));
        continue;
      }

      // Try MTProxy / SOCKS5: tg://proxy?... or https://t.me/proxy?...
      TdApi.InternalLinkTypeProxy tdProxy = parseTelegramProxyLink(line);
      if (tdProxy != null) {
        String remark = parseTelegramProxyRemark(line);
        configs.add(new ParsedProxy(tdProxy, remark));
      }
    }
    return configs;
  }

  /**
   * Parses tg://proxy?server=...&port=...&secret=... (MTProxy)
   * and tg://socks?server=...&port=...&user=...&pass=... (SOCKS5)
   * Also handles https://t.me/proxy?... and https://t.me/socks?...
   */
  private static @Nullable TdApi.InternalLinkTypeProxy parseTelegramProxyLink (@NonNull String link) {
    try {
      Uri uri;
      if (link.startsWith("tg://")) {
        // Convert tg://proxy?... to parseable URI
        uri = Uri.parse(link);
      } else if (link.contains("t.me/proxy") || link.contains("t.me/socks")) {
        uri = Uri.parse(link);
      } else {
        return null;
      }

      String host = uri.getHost();
      String path = uri.getPath();
      boolean isMtproto;
      boolean isSocks5;

      if ("proxy".equals(host) || (path != null && path.contains("proxy"))) {
        isMtproto = true;
        isSocks5 = false;
      } else if ("socks".equals(host) || (path != null && path.contains("socks"))) {
        isMtproto = false;
        isSocks5 = true;
      } else {
        return null;
      }

      String server = uri.getQueryParameter("server");
      String portStr = uri.getQueryParameter("port");
      if (StringUtils.isEmpty(server) || StringUtils.isEmpty(portStr)) return null;

      int port;
      try {
        port = Integer.parseInt(portStr);
      } catch (NumberFormatException e) {
        return null;
      }
      if (port <= 0 || port > 65535) return null;

      TdApi.ProxyType type;
      if (isMtproto) {
        String secret = uri.getQueryParameter("secret");
        if (StringUtils.isEmpty(secret)) return null;
        type = new TdApi.ProxyTypeMtproto(secret);
      } else {
        String user = uri.getQueryParameter("user");
        String pass = uri.getQueryParameter("pass");
        type = new TdApi.ProxyTypeSocks5(
          user != null ? user : "",
          pass != null ? pass : ""
        );
      }

      return new TdApi.InternalLinkTypeProxy(server, port, type);
    } catch (Exception e) {
      return null;
    }
  }

  private static @Nullable String parseTelegramProxyRemark (@NonNull String link) {
    try {
      Uri uri = Uri.parse(link);
      // Some links use fragment as remark
      String fragment = uri.getFragment();
      if (!StringUtils.isEmpty(fragment)) return fragment;
      // Some use a "name" parameter
      String name = uri.getQueryParameter("name");
      if (!StringUtils.isEmpty(name)) return name;
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * @return int[2]: {added, removed}
   */
  private int[] reconcileProxies (int subId, @NonNull List<ParsedProxy> parsed, @Nullable int[] existingIds) {
    // Build identity keys for existing proxies
    Set<String> existingKeys = new HashSet<>();
    List<Integer> existingIdList = new ArrayList<>();
    if (existingIds != null) {
      for (int proxyId : existingIds) {
        Settings.Proxy proxy = Settings.instance().getProxyConfig(proxyId);
        if (proxy != null) {
          String key = buildExistingProxyKey(proxy);
          if (key != null) {
            existingKeys.add(key);
            existingIdList.add(proxyId);
          }
        }
      }
    }

    // Build identity keys for parsed proxies
    Set<String> parsedKeys = new HashSet<>();
    for (ParsedProxy p : parsed) {
      parsedKeys.add(p.identityKey());
    }

    // Add new proxies
    int added = 0;
    List<Integer> newProxyIds = new ArrayList<>(existingIdList);
    for (ParsedProxy p : parsed) {
      String key = p.identityKey();
      if (!existingKeys.contains(key)) {
        int proxyId;
        if (p.isSingBox()) {
          proxyId = Settings.instance().addOrUpdateSingBoxProxy(
            p.singBoxConfig.outboundType, p.singBoxConfig.outboundJson,
            p.singBoxConfig.server, p.singBoxConfig.serverPort,
            p.remark, false, Settings.PROXY_ID_NONE
          );
        } else {
          proxyId = Settings.instance().addOrUpdateProxy(
            p.tdlibProxy, p.remark, false
          );
        }
        Settings.instance().setProxySubscriptionId(proxyId, subId);
        newProxyIds.add(proxyId);
        added++;
      }
    }

    // Remove stale proxies (but skip the currently active one)
    int removed = 0;
    int effectiveProxyId = Settings.instance().getEffectiveProxyId();
    List<Integer> toRemove = new ArrayList<>();
    for (int proxyId : existingIdList) {
      Settings.Proxy proxy = Settings.instance().getProxyConfig(proxyId);
      if (proxy != null) {
        String key = buildExistingProxyKey(proxy);
        if (key != null && !parsedKeys.contains(key) && proxyId != effectiveProxyId) {
          toRemove.add(proxyId);
        }
      }
    }
    for (int proxyId : toRemove) {
      if (Settings.instance().removeProxy(proxyId)) {
        newProxyIds.remove(Integer.valueOf(proxyId));
        removed++;
      }
    }

    // Update subscription's proxy ID list
    int[] ids = new int[newProxyIds.size()];
    for (int i = 0; i < newProxyIds.size(); i++) {
      ids[i] = newProxyIds.get(i);
    }
    Settings.instance().updateSubscriptionProxyIds(subId, ids);

    return new int[]{added, removed};
  }

  private static @Nullable String buildExistingProxyKey (@NonNull Settings.Proxy proxy) {
    if (proxy.isSingBox()) {
      if (proxy.singBoxOutboundJson != null) {
        return "sb:" + proxy.singBoxOutboundJson.hashCode();
      }
      return "sb:" + proxy.singBoxServer + ":" + proxy.singBoxServerPort + ":" + proxy.singBoxOutboundType;
    } else if (proxy.proxy != null) {
      return "td:" + proxy.proxy.server + ":" + proxy.proxy.port + ":" + proxy.proxy.type.getConstructor();
    }
    return null;
  }

  // Auto-refresh scheduling

  private void scheduleAutoRefreshAll () {
    List<Settings.Subscription> subs = Settings.instance().getAvailableSubscriptions();
    long now = System.currentTimeMillis();
    for (Settings.Subscription sub : subs) {
      long nextRefresh = sub.lastUpdateTime + sub.refreshIntervalMs;
      if (nextRefresh <= now) {
        refreshSubscription(sub.id, null);
      } else {
        scheduleAutoRefresh(sub.id, nextRefresh - now);
      }
    }
  }

  private void scheduleAutoRefresh (int subId, long delayMs) {
    mainHandler.postDelayed(() -> {
      Settings.Subscription sub = Settings.instance().getSubscription(subId);
      if (sub != null) {
        refreshSubscription(subId, null);
      }
    }, delayMs);
  }
}
