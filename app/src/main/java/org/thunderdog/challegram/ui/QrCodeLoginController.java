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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.NoScrollTextView;

public class QrCodeLoginController extends ViewController<QrCodeLoginController.Args> {

  public static class Args {
    public final TdApi.AuthorizationStateWaitOtherDeviceConfirmation state;

    public Args (TdApi.AuthorizationStateWaitOtherDeviceConfirmation state) {
      this.state = state;
    }
  }

  private ImageView qrImageView;

  public QrCodeLoginController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_qrCodeLogin;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.QrCodeLoginTitle);
  }

  @Override
  public boolean isUnauthorized () {
    return true;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected View onCreateView (Context context) {
    ScrollView scrollView = new ScrollView(context);
    scrollView.setLayoutParams(new ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    ));

    LinearLayout layout = new LinearLayout(context);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setGravity(Gravity.CENTER_HORIZONTAL);
    layout.setPadding(Screen.dp(24f), Screen.dp(24f), Screen.dp(24f), Screen.dp(24f));
    layout.setLayoutParams(new ScrollView.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    ));

    // QR code image
    int qrSize = Screen.dp(240f);
    qrImageView = new ImageView(context);
    qrImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
    LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
    qrParams.gravity = Gravity.CENTER_HORIZONTAL;
    qrParams.topMargin = Screen.dp(16f);
    qrParams.bottomMargin = Screen.dp(24f);
    qrImageView.setLayoutParams(qrParams);
    layout.addView(qrImageView);

    // Title
    NoScrollTextView titleView = new NoScrollTextView(context);
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f);
    titleView.setTextColor(Theme.textAccentColor());
    addThemeTextAccentColorListener(titleView);
    titleView.setGravity(Gravity.CENTER);
    Views.setMediumText(titleView, Lang.getString(R.string.QrCodeLoginTitle));
    LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    );
    titleParams.bottomMargin = Screen.dp(16f);
    titleView.setLayoutParams(titleParams);
    layout.addView(titleView);

    // Step 1
    addStepView(layout, "1. ", R.string.QrCodeLoginStep1);
    // Step 2
    addStepView(layout, "2. ", R.string.QrCodeLoginStep2);
    // Step 3
    addStepView(layout, "3. ", R.string.QrCodeLoginStep3);

    // "Log in by phone number" link
    NoScrollTextView phoneLink = new NoScrollTextView(context);
    phoneLink.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    phoneLink.setTextColor(Theme.getColor(ColorId.textLink));
    addThemeTextColorListener(phoneLink, ColorId.textLink);
    phoneLink.setGravity(Gravity.CENTER);
    phoneLink.setText(Lang.getString(R.string.LogInByPhoneNumber));
    phoneLink.setOnClickListener(v -> navigateBack());
    LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
    );
    linkParams.gravity = Gravity.CENTER_HORIZONTAL;
    linkParams.topMargin = Screen.dp(24f);
    phoneLink.setLayoutParams(linkParams);
    Views.setClickable(phoneLink);
    layout.addView(phoneLink);

    scrollView.addView(layout);

    // Set initial QR code
    Args args = getArgumentsStrict();
    updateQrCode(args.state.link);

    return scrollView;
  }

  private void addStepView (LinearLayout layout, String prefix, int stringRes) {
    NoScrollTextView stepView = new NoScrollTextView(context());
    stepView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    stepView.setTextColor(Theme.textDecentColor());
    addThemeTextDecentColorListener(stepView);
    stepView.setGravity(Gravity.START);
    stepView.setText(prefix + Lang.getString(stringRes));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    );
    params.bottomMargin = Screen.dp(8f);
    stepView.setLayoutParams(params);
    layout.addView(stepView);
  }

  public void updateLink (String newLink) {
    updateQrCode(newLink);
  }

  private void updateQrCode (String data) {
    if (qrImageView == null || data == null) return;
    int size = Screen.dp(240f);
    Bitmap bitmap = generateQrCode(data, size);
    if (bitmap != null) {
      qrImageView.setImageBitmap(bitmap);
    }
  }

  private static Bitmap generateQrCode (String data, int size) {
    try {
      QRCodeWriter writer = new QRCodeWriter();
      BitMatrix matrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size);
      int width = matrix.getWidth();
      int height = matrix.getHeight();
      int[] pixels = new int[width * height];
      for (int y = 0; y < height; y++) {
        int offset = y * width;
        for (int x = 0; x < width; x++) {
          pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
        }
      }
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
      return bitmap;
    } catch (WriterException e) {
      Log.e("Failed to generate QR code", e);
      return null;
    }
  }
}
