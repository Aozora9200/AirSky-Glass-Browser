package com.airsky.browser;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * サイトの設定。権限(位置情報・カメラなど)とコンテンツ(Cookie・JavaScript・ポップアップなど)の
 * 既定値を並べ、押すとサイトごとの例外を管理する画面を開く。
 */
public class SiteSettingsActivity extends PixelSettingsActivity {

    private static final String ADV = "AdvancedBrowserPrefs";

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("サイトの設定");
    }

    @Override
    protected void onBuild() {
        gap();
        int count = SitePermissions.hosts(this).size();
        row(R.drawable.ic_ps_list, Lang.t("すべてのサイト"), count == 0 ? Lang.t("例外を設定したサイトはありません") : count + Lang.t(" 件のサイト"),
                () -> SitePermissionActivity.open(this, -1));

        header(Lang.t("権限"));
        for (int type : new int[]{SitePermissions.LOCATION, SitePermissions.CAMERA, SitePermissions.MICROPHONE,
                SitePermissions.NOTIFICATIONS, SitePermissions.XR, SitePermissions.LOCAL_NETWORK}) {
            permissionRow(type);
        }

        header(Lang.t("コンテンツ"));
        row(R.drawable.ic_si_cookie, Lang.t("サードパーティ Cookie"),
                CookiePolicy.isThirdPartyAllowed(this) ? Lang.t("サードパーティの Cookie が許可されています")
                        : Lang.t("サードパーティの Cookie がブロックされています"),
                () -> CookieSettingsActivity.open(this));
        permissionRow(SitePermissions.JAVASCRIPT);
        permissionRow(SitePermissions.POPUPS);
        permissionRow(SitePermissions.AUTOPLAY);
        permissionRow(SitePermissions.DRM);
        permissionRow(SitePermissions.STORAGE);

        SharedPreferences adv = getSharedPreferences(ADV, MODE_PRIVATE);
        boolean blockImages = adv.getBoolean("img_block_enabled", false);
        switchRow(R.drawable.ic_ps_image, Lang.t("画像"), blockImages ? Lang.t("画像を表示しません(データ使用量を節約)") : Lang.t("すべての画像を表示します"),
                !blockImages, on -> {
                    adv.edit().putBoolean("img_block_enabled", !on).apply();
                    GeckoEngine.get(this).setBlockImages(!on);
                    rebuild();
                });
        boolean jit = BrowserPrefs.bool(this, BrowserPrefs.JS_OPTIMIZE, true);
        switchRow(R.drawable.ic_ps_speed, Lang.t("JavaScript の最適化とセキュリティ"),
                jit ? Lang.t("サイトは速くなりますが、安全性は低下します") : Lang.t("サイトは遅くなりますが、安全性が高まります"),
                jit, on -> {
                    BrowserPrefs.put(this, BrowserPrefs.JS_OPTIMIZE, on);
                    BrowserPrefs.apply(this);
                    rebuild();
                });
        boolean desktop = adv.getBoolean("deskua_enabled", false);
        switchRow(R.drawable.ic_ps_desktop, Lang.t("PC 版サイト"), desktop ? Lang.t("すべてのサイトをデスクトップモードで表示します") : Lang.t("オフ"),
                desktop, on -> {
                    BrowserBehaviorSettingsActivity.setUserAgentMode(this, on ? "deskua_enabled" : null);
                    rebuild();
                });

        gap();
        switchRow(0, Lang.t("権限を自動的に削除する"),
                Lang.t("データ保護のため、最近(90 日以上)アクセスしていないサイトから位置情報やカメラなどの権限を自動的に削除します"),
                SitePermissions.isAutoRevoke(this), on -> SitePermissions.setAutoRevoke(this, on));
    }

    private void permissionRow(int type) {
        row(SitePermissions.icon(type), SitePermissions.label(type),
                SitePermissions.defaultSummary(type, SitePermissions.getDefault(this, type)),
                () -> SitePermissionActivity.open(this, type));
    }
}
