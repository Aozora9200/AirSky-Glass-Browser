package com.airsky.browser;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * 安全チェック。Aozora のバージョン・セーフ ブラウジング・サイトの権限・通知・ロックなどをまとめて確認する。
 * 問題がある項目は押すと直せる画面を開く。
 */
public class SafetyCheckActivity extends PixelSettingsActivity {

    private static final String RELEASE_API = "https://api.github.com/repos/Aozora9200/Aozora-Browser/releases/latest";

    /** null = 確認中、"" = 確認できなかった、それ以外 = 最新のバージョン。 */
    @Nullable private String latestVersion;
    private boolean checking = false;

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("安全チェック");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (latestVersion == null && !checking) checkUpdate();
    }

    @Override
    protected void onBuild() {
        note(Lang.t("AirSky の設定が安全かどうかを確認します。注意が必要な項目は、押すと設定を変更できます。"));

        header(Lang.t("安全に関する概要"));
        // バージョン
        String current = currentVersion();
        if (latestVersion == null) {
            status(R.drawable.ic_ps_update, Lang.t("アップデートを確認しています…"), "AirSky Browser " + current, null, null);
        } else if (latestVersion.isEmpty()) {
            status(R.drawable.ic_si_warning, Lang.t("アップデートを確認できませんでした"), Lang.t("インターネットに接続して、もう一度お試しください"),
                    false, this::recheck);
        } else if (latestVersion.equals(current)) {
            status(R.drawable.ic_ps_check_circle, Lang.t("AirSky は最新の状態です"), Lang.t("バージョン ") + current, true, null);
        } else {
            status(R.drawable.ic_si_warning, Lang.t("アップデートがあります"), Lang.t("最新バージョン ") + latestVersion + Lang.t(" に更新できます"),
                    false, () -> startActivity(UpdateActivity.intent(this, latestVersion)));
        }
        // セーフ ブラウジング
        boolean safe = BrowserPrefs.bool(this, BrowserPrefs.SAFE_BROWSING, true);
        status(safe ? R.drawable.ic_ps_check_circle : R.drawable.ic_si_warning,
                safe ? Lang.t("セーフ ブラウジングはオンになっています") : Lang.t("セーフ ブラウジングがオフになっています"),
                safe ? Lang.t("危険なサイトやダウンロードから保護されています") : Lang.t("押すとオンにします"), safe, safe ? null : () -> {
                    BrowserPrefs.put(this, BrowserPrefs.SAFE_BROWSING, true);
                    BrowserPrefs.apply(this);
                    rebuild();
                });
        // 安全な接続
        boolean https = BrowserPrefs.integer(this, BrowserPrefs.HTTPS_ONLY, BrowserPrefs.HTTPS_OFF) == BrowserPrefs.HTTPS_ALWAYS;
        status(https ? R.drawable.ic_ps_check_circle : R.drawable.ic_ps_info,
                https ? Lang.t("常に安全な接続を使用しています") : Lang.t("安全でない接続を許可しています"),
                https ? Lang.t("可能な限り HTTPS で接続します") : Lang.t("押すと「常に安全な接続を使用する」を設定できます"),
                https ? Boolean.TRUE : null, https ? null : () -> open(PrivacySettingsActivity.class));
        // 権限
        int sites = SitePermissions.countSensitiveAllowed(this);
        status(R.drawable.ic_ps_check_circle,
                sites == 0 ? Lang.t("権限に問題は見受けられません") : sites + Lang.t(" 件のサイトに位置情報・カメラなどを許可しています"),
                sites == 0 ? Lang.t("位置情報やカメラなどを許可しているサイトはありません") : Lang.t("押すと確認・変更できます"),
                true, () -> SitePermissionActivity.open(this, -1));
        // 通知
        boolean notify = NotificationManagerCompat.from(this).areNotificationsEnabled();
        status(notify ? R.drawable.ic_ps_check_circle : R.drawable.ic_ps_info,
                notify ? Lang.t("通知に問題は見受けられません") : Lang.t("AirSky の通知がオフになっています"),
                notify ? Lang.t("ダウンロードやサイトの通知を受け取れます") : Lang.t("ダウンロードの完了などが通知されません"),
                notify ? Boolean.TRUE : null, () -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                    } catch (Exception ignored) {
                    }
                });
        // ロック
        boolean locked = new PasswordManager(this).isPasswordSet();
        status(locked ? R.drawable.ic_ps_check_circle : R.drawable.ic_ps_info,
                locked ? Lang.t("AirSky はパスワードで保護されています") : Lang.t("AirSky にパスワードが設定されていません"),
                locked ? Lang.t("起動時にパスワードを求めます") : Lang.t("端末を共有する場合は、パスワードの設定をおすすめします"),
                locked ? Boolean.TRUE : null, () -> open(PrivacySettingsActivity.class));

        gap();
        row(R.drawable.ic_ps_refresh, Lang.t("今すぐ確認"), null, this::recheck);
        header(Lang.t("AirSky のユーザー保護の仕組み"));
        note(Lang.t("AirSky は GeckoView を使い、セーフ ブラウジング・トラッキング防止・HTTPS への切り替え・"
                + "サイトごとの権限によって、危険なサイトや追跡からあなたを守ります。"));
    }

    /** 1 項目。ok: true=問題なし(緑) / false=注意(オレンジ) / null=おすすめ(色なし)。 */
    private void status(int icon, String title, String summary, @Nullable Boolean ok, @Nullable Runnable action) {
        Row r = row(icon, title, summary, action);
        int color = ok == null ? attrColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
                : ContextCompat.getColor(this, ok ? R.color.ps_ok : R.color.ps_warn);
        r.icon.setImageTintList(ColorStateList.valueOf(color));
        if (action != null) r.chevron();
    }

    private void recheck() {
        latestVersion = null;
        rebuild();
        checkUpdate();
    }

    private String currentVersion() {
        try {
            PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
            return p.versionName != null ? p.versionName : "";
        } catch (Exception e) {
            return "";
        }
    }

    private void checkUpdate() {
        checking = true;
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String v = "";
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                try (InputStream in = conn.getInputStream()) {
                    Scanner sc = new Scanner(in).useDelimiter("\\A");
                    v = new JSONObject(sc.hasNext() ? sc.next() : "{}").optString("tag_name", "");
                }
                conn.disconnect();
            } catch (Exception ignored) {
            }
            String result = v;
            main.post(() -> {
                checking = false;
                if (isFinishing() || isDestroyed()) return;
                latestVersion = result;
                rebuild();
            });
        }).start();
    }
}
