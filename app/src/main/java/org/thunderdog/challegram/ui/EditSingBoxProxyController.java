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
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.singbox.SingBoxConfigBuilder;
import org.thunderdog.challegram.singbox.SingBoxLinkParser;
import org.thunderdog.challegram.singbox.SingBoxProxyConfig;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.FillingDecoration;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.List;

import io.nekohasekai.libbox.Libbox;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

public class EditSingBoxProxyController extends EditBaseController<EditSingBoxProxyController.Args> implements SettingsAdapter.TextChangeListener {
  public static final int MODE_VLESS = 1;
  public static final int MODE_SHADOWSOCKS = 2;
  public static final int MODE_TROJAN = 3;
  public static final int MODE_VMESS = 4;

  public static class Args {
    public int mode;
    public Settings.Proxy existingProxy;

    public Args (int mode) {
      this.mode = mode;
    }

    public Args (Settings.Proxy proxy) {
      this.existingProxy = proxy;
      if ("vless".equals(proxy.singBoxOutboundType)) {
        this.mode = MODE_VLESS;
      } else if ("shadowsocks".equals(proxy.singBoxOutboundType)) {
        this.mode = MODE_SHADOWSOCKS;
      } else if ("trojan".equals(proxy.singBoxOutboundType)) {
        this.mode = MODE_TROJAN;
      } else if ("vmess".equals(proxy.singBoxOutboundType)) {
        this.mode = MODE_VMESS;
      } else {
        this.mode = MODE_VLESS;
      }
    }
  }

  public EditSingBoxProxyController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.SingBoxProxy);
  }

  @Override
  public int getId () {
    return R.id.controller_proxySingBox;
  }

  private SettingsAdapter adapter;

  // Link import field
  private ListItem linkField;

  // Common fields
  private ListItem serverField, portField;

  // VLESS fields
  private ListItem uuidField, flowField, tlsField, sniField, transportField, pathField;

  // Shadowsocks fields
  private ListItem methodField, passwordField;

  // VMess fields
  private ListItem alterIdField, securityField;

  @Override
  protected int getRecyclerBackgroundColorId () {
    return ColorId.background;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    final int mode = getArgumentsStrict().mode;
    final Settings.Proxy existingProxy = getArgumentsStrict().existingProxy;

    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        final int itemId = item.getId();
        if (itemId == R.id.edit_singbox_port || itemId == R.id.edit_singbox_alterid) {
          editText.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
          editText.getEditText().setIsPassword(false);
        } else if (itemId == R.id.edit_singbox_password) {
          editText.getEditText().setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
          editText.getEditText().setIsPassword(true);
        } else if (itemId == R.id.edit_singbox_server) {
          editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
          editText.getEditText().setIsPassword(false);
        } else if (itemId == R.id.edit_singbox_link) {
          editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
          editText.getEditText().setIsPassword(false);
        } else {
          editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT);
          editText.getEditText().setIsPassword(false);
        }
      }
    };
    adapter.setLockFocusOn(this, existingProxy == null);
    adapter.setTextChangeListener(this);

    List<ListItem> items = new ArrayList<>();
    int fillStart, fillCount;

    // Link import section
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.SingBoxProxyLink));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(linkField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_link, 0, R.string.SingBoxProxyLinkHint)
      .setStringValue("")
      .setInputFilters(new InputFilter[]{ new InputFilter.LengthFilter(4096) }));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Connection section
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.Connection));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    fillStart = items.size();
    items.add(serverField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_server, 0, R.string.UseProxyServer)
      .setStringValue(existingProxy != null ? existingProxy.singBoxServer : "")
      .setInputFilters(new InputFilter[]{ new InputFilter.LengthFilter(255) }));
    items.add(portField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_port, 0, R.string.UseProxyPort)
      .setStringValue(existingProxy != null ? String.valueOf(existingProxy.singBoxServerPort) : "")
      .setInputFilters(new InputFilter[]{ new InputFilter.LengthFilter(5) }));

    switch (mode) {
      case MODE_VLESS: {
        items.add(uuidField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_uuid, 0, R.string.SingBoxProxyUUID)
          .setStringValue(""));
        items.add(flowField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_flow, 0, R.string.SingBoxProxyFlow)
          .setStringValue(""));
        items.add(sniField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_sni, 0, R.string.SingBoxProxySni)
          .setStringValue(""));
        items.add(transportField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_transport, 0, R.string.SingBoxProxyTransport)
          .setStringValue("tcp"));
        items.add(pathField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_path, 0, R.string.SingBoxProxyPath)
          .setStringValue(""));
        fillCount = 7;
        break;
      }
      case MODE_SHADOWSOCKS: {
        items.add(methodField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_method, 0, R.string.SingBoxProxyMethod)
          .setStringValue("aes-256-gcm"));
        items.add(passwordField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_password, 0, R.string.ProxyPasswordHint)
          .setStringValue(""));
        fillCount = 4;
        break;
      }
      case MODE_TROJAN: {
        items.add(passwordField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_password, 0, R.string.ProxyPasswordHint)
          .setStringValue(""));
        items.add(sniField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_sni, 0, R.string.SingBoxProxySni)
          .setStringValue(""));
        fillCount = 4;
        break;
      }
      case MODE_VMESS: {
        items.add(uuidField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_uuid, 0, R.string.SingBoxProxyUUID)
          .setStringValue(""));
        items.add(alterIdField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_alterid, 0, R.string.SingBoxProxyAlterID)
          .setStringValue("0"));
        items.add(securityField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_security, 0, R.string.SingBoxProxySecurity)
          .setStringValue("auto"));
        items.add(transportField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_transport, 0, R.string.SingBoxProxyTransport)
          .setStringValue("tcp"));
        items.add(pathField = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_singbox_path, 0, R.string.SingBoxProxyPath)
          .setStringValue(""));
        fillCount = 7;
        break;
      }
      default:
        throw new IllegalStateException();
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);

    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        outRect.bottom = 0;
      }
    });
    recyclerView.addItemDecoration(new FillingDecoration(recyclerView, this)
      .addRange(2, 3) // link field
      .addRange(fillStart, fillStart + fillCount));
    recyclerView.setAdapter(adapter);

    setDoneVisible(false);
    setDoneIcon(R.drawable.baseline_check_24);
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v) {
    if (id == R.id.edit_singbox_link) {
      tryImportLink(item.getStringValue().trim());
    }
    checkDoneVisibility(id);
  }

  private void tryImportLink (String link) {
    if (link.isEmpty()) return;

    SingBoxProxyConfig config = SingBoxLinkParser.parse(link);
    if (config == null) return;

    // Fill form fields from parsed link
    if (serverField != null) {
      serverField.setStringValue(config.server);
      adapter.updateEditTextById(R.id.edit_singbox_server, true, false);
    }
    if (portField != null) {
      portField.setStringValue(String.valueOf(config.serverPort));
      adapter.updateEditTextById(R.id.edit_singbox_port, true, false);
    }
  }

  private void checkDoneVisibility (int id) {
    if (id != 0) {
      adapter.updateEditTextById(id, false, false);
    }
    final String server = serverField.getStringValue().trim();
    final String port = portField.getStringValue().trim();
    setDoneVisible(!server.isEmpty() && !port.isEmpty());
  }

  @Override
  protected boolean onDoneClick () {
    final String server = serverField.getStringValue().trim();
    final String portRaw = portField.getStringValue().trim();
    final String port = StringUtils.isNumeric(portRaw) ? portRaw : "";

    if (server.isEmpty()) {
      adapter.updateEditTextById(R.id.edit_singbox_server, false, true);
      return false;
    }
    if (port.isEmpty()) {
      adapter.updateEditTextById(R.id.edit_singbox_port, false, true);
      return false;
    }

    final int portNum = StringUtils.parseInt(port);
    final int mode = getArgumentsStrict().mode;

    // First check if we have a link to import directly
    String linkText = linkField != null ? linkField.getStringValue().trim() : "";
    if (!linkText.isEmpty()) {
      SingBoxProxyConfig config = SingBoxLinkParser.parse(linkText);
      if (config != null) {
        saveSingBoxProxy(config.outboundType, config.outboundJson, config.server, config.serverPort, config.remark);
        return true;
      }
    }

    // Build from form fields
    try {
      String outboundJson;
      String outboundType;

      switch (mode) {
        case MODE_VLESS: {
          String uuid = uuidField.getStringValue().trim();
          String flow = flowField != null ? flowField.getStringValue().trim() : "";
          String sni = sniField != null ? sniField.getStringValue().trim() : "";
          String transport = transportField != null ? transportField.getStringValue().trim() : "tcp";
          String path = pathField != null ? pathField.getStringValue().trim() : "";
          String security = !sni.isEmpty() ? "tls" : "";
          outboundJson = SingBoxConfigBuilder.buildVlessOutbound(server, portNum, uuid, flow, security, sni, "", "", "", "", transport, path, "");
          outboundType = "vless";
          break;
        }
        case MODE_SHADOWSOCKS: {
          String method = methodField.getStringValue().trim();
          String password = passwordField.getStringValue().trim();
          outboundJson = SingBoxConfigBuilder.buildShadowsocksOutbound(server, portNum, method, password);
          outboundType = "shadowsocks";
          break;
        }
        case MODE_TROJAN: {
          String password = passwordField.getStringValue().trim();
          String sni = sniField != null ? sniField.getStringValue().trim() : "";
          outboundJson = SingBoxConfigBuilder.buildTrojanOutbound(server, portNum, password, sni, "", "");
          outboundType = "trojan";
          break;
        }
        case MODE_VMESS: {
          String uuid = uuidField.getStringValue().trim();
          int alterId = StringUtils.parseInt(alterIdField != null ? alterIdField.getStringValue().trim() : "0");
          String security = securityField != null ? securityField.getStringValue().trim() : "auto";
          String transport = transportField != null ? transportField.getStringValue().trim() : "tcp";
          String path = pathField != null ? pathField.getStringValue().trim() : "";
          outboundJson = SingBoxConfigBuilder.buildVmessOutbound(server, portNum, uuid, alterId, security, transport, path, false, "", "", "", "");
          outboundType = "vmess";
          break;
        }
        default:
          throw new IllegalStateException();
      }

      saveSingBoxProxy(outboundType, outboundJson, server, portNum, null);
      return true;
    } catch (Exception e) {
      context.tooltipManager()
        .builder(getDoneButton())
        .icon(R.drawable.baseline_warning_24)
        .show(tdlib, Lang.getString(R.string.SingBoxProxyConfigError))
        .hideDelayed();
      return false;
    }
  }

  private void saveSingBoxProxy (@NonNull String outboundType, @NonNull String outboundJson,
      @NonNull String server, int serverPort, String remark) {
    setInProgress(true);

    // Validate config by building full config and checking with libbox
    try {
      String fullConfig = SingBoxConfigBuilder.buildFullConfig(10808, outboundJson, "");
      Libbox.checkConfig(fullConfig);
    } catch (Exception e) {
      setInProgress(false);
      context.tooltipManager()
        .builder(getDoneButton())
        .icon(R.drawable.baseline_warning_24)
        .show(tdlib, Lang.getString(R.string.SingBoxProxyConfigError) + ": " + e.getMessage())
        .hideDelayed();
      return;
    }

    int existingProxyId = getArgumentsStrict().existingProxy != null
      ? getArgumentsStrict().existingProxy.id
      : Settings.PROXY_ID_NONE;

    Settings.instance().addOrUpdateSingBoxProxy(
      outboundType, outboundJson, server, serverPort,
      remark, true, existingProxyId
    );

    setInProgress(false);

    if (navigationController != null) {
      ViewController<?> c = navigationController.getPreviousStackItem();
      if (c != null && c.getId() != R.id.controller_proxyList) {
        navigationController.getStack().insertBack(new SettingsProxyController(context, tdlib));
      }
    }
    navigateBack();
  }
}
