package com.airsky.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission;
import org.mozilla.geckoview.StorageController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * サードパーティ Cookie の許可/ブロックと、サイトごとの例外を管理する。
 *
 * 例外は「そのサイトを開いている間はサードパーティ Cookie を許可する」もので、アプリの設定に
 * ホスト名を保存しておき、タブがそのサイトを開いた時に Gecko のトラッキング保護の例外
 * (PERMISSION_TRACKING = ALLOW)として反映する。Gecko の権限はページを開いた時にしか
 * 受け取れないため、URL を入力して追加した例外も次にそのサイトを開いた時に効く。
 */
public final class CookiePolicy {

    private static final String PREFS = "AppPrefs";
    private static final String KEY_ACCEPT = "acceptCookies";
    private static final String KEY_THIRD_PARTY = "thirdPartyCookies";
    private static final String KEY_EXCEPTIONS = "thirdPartyCookieExceptions";

    private CookiePolicy() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Cookie そのものを受け入れるか(設定 > セキュリティ)。 */
    public static boolean isCookiesAccepted(@NonNull Context c) {
        return prefs(c).getBoolean(KEY_ACCEPT, true);
    }

    public static boolean isThirdPartyAllowed(@NonNull Context c) {
        return prefs(c).getBoolean(KEY_THIRD_PARTY, true);
    }

    public static void setThirdPartyAllowed(@NonNull Context c, boolean allowed) {
        prefs(c).edit().putBoolean(KEY_THIRD_PARTY, allowed).apply();
        GeckoEngine.get(c).applyCookieSetting();
    }

    @NonNull
    public static List<String> getExceptions(@NonNull Context c) {
        List<String> list = new ArrayList<>(prefs(c).getStringSet(KEY_EXCEPTIONS, Collections.emptySet()));
        Collections.sort(list);
        return list;
    }

    public static boolean isException(@NonNull Context c, @Nullable String host) {
        if (host == null) return false;
        Set<String> set = prefs(c).getStringSet(KEY_EXCEPTIONS, Collections.emptySet());
        String h = host.toLowerCase(Locale.ROOT);
        // 「example.com」を登録したら「www.example.com」等のサブドメインにも効かせる
        for (String e : set) {
            if (h.equals(e) || h.endsWith("." + e)) return true;
        }
        return false;
    }

    public static void addException(@NonNull Context c, @NonNull String host) {
        Set<String> set = new HashSet<>(prefs(c).getStringSet(KEY_EXCEPTIONS, Collections.emptySet()));
        set.add(host.toLowerCase(Locale.ROOT));
        prefs(c).edit().putStringSet(KEY_EXCEPTIONS, set).apply();
    }

    /** 例外を外し、Gecko に反映済みの許可も取り消す。 */
    public static void removeException(@NonNull Context c, @NonNull String host) {
        Set<String> set = new HashSet<>(prefs(c).getStringSet(KEY_EXCEPTIONS, Collections.emptySet()));
        final String h = host.toLowerCase(Locale.ROOT);
        set.remove(h);
        prefs(c).edit().putStringSet(KEY_EXCEPTIONS, set).apply();

        StorageController storage = GeckoEngine.get(c).getRuntime().getStorageController();
        storage.getAllPermissions().accept(perms -> {
            if (perms == null) return;
            for (ContentPermission p : perms) {
                if (p.permission != PermissionDelegate.PERMISSION_TRACKING
                        || p.value != ContentPermission.VALUE_ALLOW) continue;
                String ph = Uri.parse(p.uri).getHost();
                if (ph != null && (ph.equals(h) || ph.endsWith("." + h))) {
                    storage.setPermission(p, ContentPermission.VALUE_DENY);
                }
            }
        });
    }

    /** そのホストに効いている例外(親ドメインで登録したものも含む)をすべて外す。 */
    public static void removeExceptionsFor(@NonNull Context c, @NonNull String host) {
        String h = host.toLowerCase(Locale.ROOT);
        for (String e : getExceptions(c)) {
            if (h.equals(e) || h.endsWith("." + e)) removeException(c, e);
        }
    }

    /**
     * 入力された URL・ホスト名から登録用のホスト名を取り出す。
     * 「https://www.example.com/path」「www.example.com」「[*.]example.com」などを受け付ける。
     */
    @Nullable
    public static String normalizeHost(@Nullable String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("[*.]")) s = s.substring(4);
        if (s.startsWith("*.")) s = s.substring(2);
        if (s.isEmpty()) return null;
        if (!s.contains("://")) s = "https://" + s;
        String host = Uri.parse(s).getHost();
        if (host == null || host.isEmpty() || host.startsWith(".") || host.endsWith(".")) return null;
        // localhost 以外はドット付きのホスト名だけ受け付ける
        if (!host.contains(".") && !host.equals("localhost")) return null;
        if (!host.matches("[a-z0-9.\\-\\[\\]:]+")) return null;
        return host;
    }

    /** そのホストのサイトでサードパーティ Cookie の例外を効かせるべきか。 */
    static boolean wantsException(@NonNull Context c, @Nullable String host) {
        return isCookiesAccepted(c) && !isThirdPartyAllowed(c) && isException(c, host);
    }

    /**
     * タブが開いたページの権限を、例外の設定と合わせる。
     * @return 権限を変えたので読み直しが必要なら true
     */
    static boolean syncTrackingPermission(@NonNull Context c, @Nullable String host,
                                          @Nullable ContentPermission tracking) {
        if (tracking == null || host == null) return false;
        boolean want = wantsException(c, host);
        boolean has = tracking.value == ContentPermission.VALUE_ALLOW;
        if (want == has) return false;
        GeckoEngine.get(c).getRuntime().getStorageController().setPermission(
                tracking, want ? ContentPermission.VALUE_ALLOW : ContentPermission.VALUE_DENY);
        return true;
    }

    /** そのサイト(ホスト)の Cookie とサイトデータを消去する。 */
    @NonNull
    public static GeckoResult<Void> clearSiteData(@NonNull Context c, @NonNull String host) {
        return GeckoEngine.get(c).getRuntime().getStorageController().clearDataFromHost(host,
                StorageController.ClearFlags.COOKIES
                        | StorageController.ClearFlags.DOM_STORAGES
                        | StorageController.ClearFlags.AUTH_SESSIONS);
    }
}
