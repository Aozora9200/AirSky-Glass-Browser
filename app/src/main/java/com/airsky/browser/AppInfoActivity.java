package com.airsky.browser;

import android.content.Intent;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Aozora について(Pixel の「デバイス情報」風)。
 * バージョンを 7 回タップするとイースターエッグ、ビルド番号を 7 回タップすると開発者向けの画面。
 */
public class AppInfoActivity extends PixelSettingsActivity {

    private static final int TAP_COUNT_TO_TRIGGER = 7; // 連打回数
    private static final int TAP_COUNT_TO_DEVELOP_TRIGGER = 7;

    private int tapCount = 0;
    private int tapdevelopCount = 0;
    private long lastTapTime = 0;
    private long lastTapdevelopTime = 0;
    private Toast toast;

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("AirSky Browser について");
    }

    @Override
    protected void onBuild() {
        card(hero());

        gap();
        row(R.drawable.ic_ps_phone, Lang.t("端末の状態"), Lang.t("電池やネットワークなどの情報のステータス"),
                () -> open(DeviceStatusActivity.class)).chevron();
        row(R.drawable.ic_ps_info, Lang.t("法的情報"), Lang.t("ライセンスとオープンソース ソフトウェア"),
                () -> open(LegalActivity.class)).chevron();

        header(Lang.t("ソフトウェア情報"));
        row(0, Lang.t("アプリ名"), getString(R.string.app_name), null);
        row(0, Lang.t("App バージョン"), getString(R.string.version), this::onVersionTap);
        row(0, Lang.t("ビルド番号"), getString(R.string.build), this::onBuildTap);
    }

    /** 一番上のアイコン・アプリ名・バージョン。 */
    private View hero() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(28), dp(24), dp(28));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.addView(icon, new LinearLayout.LayoutParams(dp(80), dp(80)));

        TextView name = new TextView(this);
        name.setText(R.string.app_name);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        name.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurface));
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        box.addView(name, lp);

        TextView version = new TextView(this);
        version.setText(Lang.t("バージョン ") + getString(R.string.version) + " (" + getString(R.string.build) + ")");
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        version.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = dp(4);
        box.addView(version, lp2);
        return box;
    }

    private void onVersionTap() {
        long currentTime = System.currentTimeMillis();

        // 前回タップから2秒以上経っていたらカウントをリセット
        if (currentTime - lastTapTime > 2000) {
            tapCount = 0;
        }

        tapCount++;
        lastTapTime = currentTime;

        if (tapCount >= TAP_COUNT_TO_TRIGGER) {
            tapCount = 0;
            startActivity(new Intent(this, com.airsky.browser.kitkat.PlatLogoActivity.class));
        }
    }

    private void onBuildTap() {
        long currentTime = System.currentTimeMillis();

        // 前回タップから2秒以上経っていたらカウントをリセット
        if (currentTime - lastTapdevelopTime > 2000) {
            tapdevelopCount = 0;
        }

        tapdevelopCount++;
        lastTapdevelopTime = currentTime;

        int rest = TAP_COUNT_TO_DEVELOP_TRIGGER - tapdevelopCount;
        if (rest >= 1 && rest <= 4) {
            showToast(Lang.t("デベロッパーになるまであと") + rest + Lang.t("ステップです。"));
        }

        if (tapdevelopCount >= TAP_COUNT_TO_DEVELOP_TRIGGER) {
            tapdevelopCount = 0;
            if (toast != null) {
                toast.cancel();
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(Lang.t("パスワード入力"))
                    .setMessage(Lang.t("アクセスにはパスワードが必要です"))
                    .setView(LayoutInflater.from(this).inflate(R.layout.dialog_password, null))
                    .setPositiveButton("OK", (dialog, which) -> {
                        EditText input = ((AlertDialog) dialog).findViewById(R.id.password_edit);
                        if (input != null) {
                            String enteredPassword = input.getText().toString();
                            String correctPassword = getString(R.string.password_key);

                            if (enteredPassword.equals(correctPassword)) {
                                showToast(Lang.t("これでデベロッパーになりました！"));
                                startActivity(new Intent(this, DebugActivity.class));
                            } else {
                                Toast.makeText(this, Lang.t("パスワードが違います"), Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .setNegativeButton(Lang.t("キャンセル"), null)
                    .show();
        }
    }

    private void showToast(String text) {
        if (toast != null) {
            // 既存のToastをキャンセル
            toast.cancel();
        }
        toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
    }
}
