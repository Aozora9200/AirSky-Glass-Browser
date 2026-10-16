package com.airsky.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission;
import org.mozilla.geckoview.StorageController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * サイトの権限(位置情報・カメラ・通知・JavaScript など)の既定値と、サイトごとの例外。
 * Chrome の「サイトの設定」/ Firefox の「サイトの許可設定」に当たる。
 *
 * <p>保存先は "SitePermissions"。既定値は "default.種類"、サイトごとは "site.ホスト.種類"。
 * 以前の設定(AppPrefs の javaScript・geoLocation・popupBlock と GeoPermissionStore)も引き継ぐ。</p>
 */
public final class SitePermissions {

    private static final String FILE = "SitePermissions";
    private static final String KEY_MIGRATED = "migrated_v1";
    private static final String KEY_AUTO_REVOKE = "autoRevoke";
    private static final long REVOKE_AFTER_MS = 90L * 24 * 60 * 60 * 1000;

    // 値
    public static final int ASK = 0;
    public static final int ALLOW = 1;
    public static final int BLOCK = 2;
    /** 自動再生: 音の出るものだけブロック。 */
    public static final int BLOCK_AUDIBLE = 3;
    public static final int UNSET = -1;

    // 種類
    public static final int LOCATION = 0;
    public static final int CAMERA = 1;
    public static final int MICROPHONE = 2;
    public static final int NOTIFICATIONS = 3;
    public static final int AUTOPLAY = 4;
    public static final int XR = 5;
    public static final int LOCAL_NETWORK = 6;
    public static final int DRM = 7;
    public static final int STORAGE = 8;
    public static final int JAVASCRIPT = 9;
    public static final int POPUPS = 10;

    /** サイトごとに設定できる種類(サイトの詳細に並べる順)。 */
    public static final int[] ALL = {LOCATION, CAMERA, MICROPHONE, NOTIFICATIONS, JAVASCRIPT, POPUPS,
            AUTOPLAY, DRM, STORAGE, XR, LOCAL_NETWORK};

    /** 位置情報などの「使われていなければ自動で外す」対象。 */
    private static final int[] SENSITIVE = {LOCATION, CAMERA, MICROPHONE, NOTIFICATIONS, XR, LOCAL_NETWORK};

    private SitePermissions() {}

    // ------------------------------------------------------------------
    // 種類ごとの情報
    // ------------------------------------------------------------------

    @NonNull
    public static String key(int type) {
        switch (type) {
            case LOCATION: return "location";
            case CAMERA: return "camera";
            case MICROPHONE: return "microphone";
            case NOTIFICATIONS: return "notifications";
            case AUTOPLAY: return "autoplay";
            case XR: return "xr";
            case LOCAL_NETWORK: return "localNetwork";
            case DRM: return "drm";
            case STORAGE: return "storage";
            case JAVASCRIPT: return "javascript";
            case POPUPS: return "popups";
            default: return "unknown";
        }
    }

    @NonNull
    public static String label(int type) {
        switch (type) {
            case LOCATION: return Lang.t("位置情報");
            case CAMERA: return Lang.t("カメラ");
            case MICROPHONE: return Lang.t("マイク");
            case NOTIFICATIONS: return Lang.t("通知");
            case AUTOPLAY: return Lang.t("自動再生");
            case XR: return Lang.t("バーチャル リアリティ(VR / AR)");
            case LOCAL_NETWORK: return Lang.t("ローカル ネットワーク");
            case DRM: return Lang.t("保護されたコンテンツ");
            case STORAGE: return Lang.t("デバイス上のサイトデータ");
            case JAVASCRIPT: return "JavaScript";
            case POPUPS: return Lang.t("ポップアップとリダイレクト");
            default: return "";
        }
    }

    /** 確認ダイアログで「〜を求めています」に入れる言葉。 */
    @NonNull
    public static String requestText(int type) {
        switch (type) {
            case LOCATION: return Lang.t("現在地へのアクセス");
            case CAMERA: return Lang.t("カメラの使用");
            case MICROPHONE: return Lang.t("マイクの使用");
            case NOTIFICATIONS: return Lang.t("通知の表示");
            case XR: return Lang.t("VR / AR デバイスの使用");
            case LOCAL_NETWORK: return Lang.t("ローカル ネットワーク上のデバイスへのアクセス");
            case DRM: return Lang.t("保護されたコンテンツ(DRM)の再生");
            default: return label(type);
        }
    }

    @NonNull
    public static String description(int type) {
        switch (type) {
            case LOCATION: return Lang.t("サイトは通常、付近のニュースや天気など、地域に合わせた情報を表示するために位置情報を使用します。");
            case CAMERA: return Lang.t("サイトは通常、ビデオ通話などのコミュニケーション機能のためにカメラを使用します。");
            case MICROPHONE: return Lang.t("サイトは通常、ビデオ通話や音声入力などのためにマイクを使用します。");
            case NOTIFICATIONS: return Lang.t("サイトは通常、最新ニュースやチャットのメッセージを知らせるために通知を送信します。");
            case AUTOPLAY: return Lang.t("ページを開いた時に、動画や音声を自動的に再生するかどうかを選びます。");
            case XR: return Lang.t("サイトは通常、VR / AR のゲームや体験のために VR / AR デバイスとデータを使用します。");
            case LOCAL_NETWORK: return Lang.t("サイトは、プリンタやスマート家電など、同じネットワーク上のデバイスに接続することがあります。");
            case DRM: return Lang.t("サイトは、著作権で保護された動画や音楽を再生するために、デバイスの識別情報を使用することがあります。");
            case STORAGE: return Lang.t("サイトは、オフラインでも使えるように、端末にデータを保存し続けることがあります。");
            case JAVASCRIPT: return Lang.t("サイトは通常、動画やゲームなどのインタラクティブな機能のために JavaScript を使用します。");
            case POPUPS: return Lang.t("サイトによっては、広告を表示したり、望まないページに移動させたりするためにポップアップを使用します。");
            default: return "";
        }
    }

    @DrawableRes
    public static int icon(int type) {
        switch (type) {
            case LOCATION: return R.drawable.ic_ps_location;
            case CAMERA: return R.drawable.ic_ps_camera;
            case MICROPHONE: return R.drawable.ic_ps_mic;
            case NOTIFICATIONS: return R.drawable.ic_ps_bell;
            case AUTOPLAY: return R.drawable.ic_ps_volume;
            case XR: return R.drawable.ic_ps_vr;
            case LOCAL_NETWORK: return R.drawable.ic_ps_router;
            case DRM: return R.drawable.ic_ps_drm;
            case STORAGE: return R.drawable.ic_ps_storage;
            case JAVASCRIPT: return R.drawable.ic_ps_code;
            case POPUPS: return R.drawable.ic_si_open;
            default: return R.drawable.ic_ps_info;
        }
    }

    /** 既定値として選べる値。 */
    @NonNull
    public static int[] defaultOptions(int type) {
        switch (type) {
            case AUTOPLAY: return new int[]{ALLOW, BLOCK_AUDIBLE, BLOCK};
            case DRM:
            case POPUPS: return new int[]{ALLOW, ASK, BLOCK};
            case STORAGE:
            case JAVASCRIPT: return new int[]{ALLOW, BLOCK};
            default: return new int[]{ASK, BLOCK};
        }
    }

    /** サイトごとに選べる値(「最初に確認する」は既定値に戻す扱い)。 */
    @NonNull
    public static int[] siteOptions(int type) {
        if (type == AUTOPLAY) return new int[]{ALLOW, BLOCK_AUDIBLE, BLOCK};
        return new int[]{ALLOW, BLOCK};
    }

    public static int builtInDefault(int type) {
        switch (type) {
            case AUTOPLAY: return BLOCK_AUDIBLE;
            case DRM:
            case STORAGE:
            case JAVASCRIPT: return ALLOW;
            default: return ASK;
        }
    }

    @NonNull
    public static String valueLabel(int type, int value) {
        if (type == AUTOPLAY) {
            switch (value) {
                case ALLOW: return Lang.t("音声と動画を許可");
                case BLOCK: return Lang.t("音声と動画をブロック");
                default: return Lang.t("音声のみブロック");
            }
        }
        switch (value) {
            case ALLOW: return Lang.t("許可");
            case BLOCK: return Lang.t("ブロック");
            default: return Lang.t("最初に確認する");
        }
    }

    /** 一覧の説明に出す、既定値の文。 */
    @NonNull
    public static String defaultSummary(int type, int value) {
        if (type == AUTOPLAY) return valueLabel(type, value);
        switch (value) {
            case ALLOW: return Lang.t("許可されています");
            case BLOCK: return Lang.t("許可されていません");
            default: return Lang.t("最初に確認する");
        }
    }

    // ------------------------------------------------------------------
    // 読み書き
    // ------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        SharedPreferences p = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        if (!p.getBoolean(KEY_MIGRATED, false)) migrate(c, p);
        return p;
    }

    /** 以前の設定(位置情報のサイト一覧など)を引き継ぐ。 */
    private static void migrate(Context c, SharedPreferences p) {
        SharedPreferences.Editor e = p.edit();
        SharedPreferences geo = c.getSharedPreferences("GeoPermissionStore", Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> entry : geo.getAll().entrySet()) {
            if (entry.getValue() instanceof Boolean) {
                e.putInt(siteKey(entry.getKey(), LOCATION), (Boolean) entry.getValue() ? ALLOW : BLOCK);
            }
        }
        e.putBoolean(KEY_MIGRATED, true).apply();
    }

    private static String siteKey(String host, int type) {
        return "site." + host + "." + key(type);
    }

    public static int getDefault(@NonNull Context c, int type) {
        SharedPreferences app = c.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        // 以前からある設定は AppPrefs の値を正とする(他の画面・処理と共通)
        switch (type) {
            case JAVASCRIPT:
                return app.getBoolean("javaScript", true) ? ALLOW : BLOCK;
            case LOCATION:
                if (!app.getBoolean("geoLocation", true)) return BLOCK;
                break;
            case POPUPS:
                if (app.getBoolean("popupBlock", false)) return ALLOW;
                break;
        }
        int v = prefs(c).getInt("default." + key(type), UNSET);
        return v == UNSET ? builtInDefault(type) : v;
    }

    public static void setDefault(@NonNull Context c, int type, int value) {
        SharedPreferences.Editor app = c.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit();
        switch (type) {
            case JAVASCRIPT:
                app.putBoolean("javaScript", value != BLOCK).apply();
                GeckoEngine.get(c).setJavaScriptEnabled(value != BLOCK);
                break;
            case LOCATION:
                app.putBoolean("geoLocation", value != BLOCK).apply();
                break;
            case POPUPS:
                app.putBoolean("popupBlock", value == ALLOW).apply();
                break;
        }
        prefs(c).edit().putInt("default." + key(type), value).apply();
        resetGeckoPermissions(c, null);
    }

    public static int getSite(@NonNull Context c, @NonNull String host, int type) {
        return prefs(c).getInt(siteKey(host, type), UNSET);
    }

    public static void setSite(@NonNull Context c, @NonNull String host, int type, int value) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (value == UNSET) {
            e.remove(siteKey(host, type));
        } else {
            e.putInt(siteKey(host, type), value);
            e.putLong("used." + host, System.currentTimeMillis());
        }
        e.apply();
        if (type == LOCATION) syncLegacyGeo(c, host, value);
        resetGeckoPermissions(c, host);
    }

    /** サイトの例外をすべて消す(既定値に戻す)。 */
    public static void clearSite(@NonNull Context c, @NonNull String host) {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit();
        String prefix = "site." + host + ".";
        for (String k : p.getAll().keySet()) {
            if (k.startsWith(prefix)) e.remove(k);
        }
        e.remove("used." + host).apply();
        syncLegacyGeo(c, host, UNSET);
        resetGeckoPermissions(c, host);
    }

    /** そのサイトで実際に使う値(サイトの例外 → 既定値)。 */
    public static int effective(@NonNull Context c, @Nullable String host, int type) {
        if (host != null && !host.isEmpty()) {
            int v = getSite(c, host, type);
            if (v != UNSET) return v;
        }
        return getDefault(c, type);
    }

    /** サイトが権限を使った(求めた)ことを記録する。自動で外す時の目安。 */
    public static void touch(@NonNull Context c, @Nullable String host) {
        if (host == null || host.isEmpty()) return;
        SharedPreferences p = prefs(c);
        if (p.contains("used." + host) || hasAnySite(p, host)) {
            p.edit().putLong("used." + host, System.currentTimeMillis()).apply();
        }
    }

    private static boolean hasAnySite(SharedPreferences p, String host) {
        String prefix = "site." + host + ".";
        for (String k : p.getAll().keySet()) if (k.startsWith(prefix)) return true;
        return false;
    }

    /** 例外が 1 つ以上あるサイト(並べ替え済み)。 */
    @NonNull
    public static List<String> hosts(@NonNull Context c) {
        TreeSet<String> set = new TreeSet<>();
        for (String k : prefs(c).getAll().keySet()) {
            if (!k.startsWith("site.")) continue;
            String rest = k.substring(5);
            int dot = rest.lastIndexOf('.');
            if (dot > 0) set.add(rest.substring(0, dot));
        }
        return new ArrayList<>(set);
    }

    /** 種類ごとに、例外のあるサイト。 */
    @NonNull
    public static List<String> hostsWith(@NonNull Context c, int type) {
        List<String> out = new ArrayList<>();
        for (String h : hosts(c)) if (getSite(c, h, type) != UNSET) out.add(h);
        Collections.sort(out);
        return out;
    }

    // ------------------------------------------------------------------
    // 権限を自動的に削除する
    // ------------------------------------------------------------------

    public static boolean isAutoRevoke(@NonNull Context c) {
        return prefs(c).getBoolean(KEY_AUTO_REVOKE, true);
    }

    public static void setAutoRevoke(@NonNull Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_AUTO_REVOKE, on).apply();
    }

    /** 90 日以上使っていないサイトの、位置情報・カメラなどの許可を外す。外したサイトの数を返す。 */
    public static int pruneUnused(@NonNull Context c) {
        if (!isAutoRevoke(c)) return 0;
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        int count = 0;
        SharedPreferences.Editor e = p.edit();
        for (String host : hosts(c)) {
            long used = p.getLong("used." + host, 0);
            if (used == 0) {
                e.putLong("used." + host, now); // 今から数え始める
                continue;
            }
            if (now - used < REVOKE_AFTER_MS) continue;
            boolean removed = false;
            for (int type : SENSITIVE) {
                if (p.getInt(siteKey(host, type), UNSET) == ALLOW) {
                    e.remove(siteKey(host, type));
                    removed = true;
                }
            }
            if (removed) count++;
        }
        e.apply();
        return count;
    }

    /** 位置情報・カメラなどを許可しているサイトの数(安全チェック用)。 */
    public static int countSensitiveAllowed(@NonNull Context c) {
        int n = 0;
        for (String host : hosts(c)) {
            for (int type : SENSITIVE) {
                if (getSite(c, host, type) == ALLOW) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------

    private static void syncLegacyGeo(Context c, String host, int value) {
        SharedPreferences.Editor geo = c.getSharedPreferences("GeoPermissionStore", Context.MODE_PRIVATE).edit();
        if (value == ALLOW) geo.putBoolean(host, true);
        else if (value == BLOCK) geo.putBoolean(host, false);
        else geo.remove(host);
        geo.apply();
    }

    /**
     * Gecko が覚えている権限を「確認する」に戻し、次に求められた時にこの設定で判断されるようにする。
     * host が null なら、すべてのサイトの分を戻す。
     */
    static void resetGeckoPermissions(@NonNull Context c, @Nullable String host) {
        try {
            GeckoRuntime runtime = GeckoEngine.get(c).getRuntime();
            StorageController storage = runtime.getStorageController();
            if (host == null) {
                storage.getAllPermissions().accept(list -> resetAll(storage, list), e -> {});
            } else {
                for (String scheme : new String[]{"https://", "http://"}) {
                    for (String h : new String[]{host, "www." + host}) {
                        storage.getPermissions(scheme + h).accept(list -> resetAll(storage, list), e -> {});
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void resetAll(StorageController storage, @Nullable List<ContentPermission> list) {
        if (list == null) return;
        for (ContentPermission p : list) {
            if (p.permission == org.mozilla.geckoview.GeckoSession.PermissionDelegate.PERMISSION_TRACKING
                    || p.permission == org.mozilla.geckoview.GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS) continue;
            if (p.value != ContentPermission.VALUE_PROMPT) {
                storage.setPermission(p, ContentPermission.VALUE_PROMPT);
            }
        }
    }

    /** URL からホスト名(先頭の www. は除く)を取り出す。 */
    @Nullable
    public static String hostOf(@Nullable String url) {
        if (url == null) return null;
        try {
            String host = Uri.parse(url).getHost();
            if (host == null || host.isEmpty()) return null;
            host = host.toLowerCase(java.util.Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return null;
        }
    }
}
