package com.airsky.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.LocaleList;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoPreferenceController;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chrome / Firefox と同じ働きをするブラウザの設定(プライバシー・セキュリティ・ユーザー補助・言語など)。
 * 値は AppPrefs に保存し、{@link #applyToRuntime} で GeckoView に反映する。
 */
public final class BrowserPrefs {

    private static final String TAG = "BrowserPrefs";
    static final String FILE = "AppPrefs";

    // プライバシー
    static final String DO_NOT_TRACK = "doNotTrack";
    static final String GPC = "globalPrivacyControl";
    static final String PRELOAD = "preloadPages";
    static final String TRACKING = "trackingProtection";
    // セキュリティ
    static final String SAFE_BROWSING = "safeBrowsing";
    static final String HTTPS_ONLY = "httpsOnly";
    static final String SECURE_DNS = "secureDns";
    static final String SECURE_DNS_PROVIDER = "secureDnsProvider";
    static final String JS_OPTIMIZE = "jsOptimization";
    static final String LOCK_SECRET = "lockSecretTabs";
    // ユーザー補助
    static final String FONT_SCALE = "fontScale";
    static final String FONT_AUTO = "fontAuto";
    static final String FORCE_ZOOM = "forceZoom";
    static final String DOUBLE_TAP_ZOOM = "doubleTapZoom";
    static final String CARET = "caretBrowsing";
    // 言語
    static final String TRANSLATE_OFFER = "translateOffer";
    /** ほかの言語のページを聞かずに翻訳する。既定はオフ(「翻訳しますか？」と尋ねる)。 */
    static final String TRANSLATE_AUTO = "translateAuto";
    static final String PAGE_LANGUAGE = "pageLanguage";
    // ホームページ
    static final String HOME_ENABLED = "homeButton";
    static final String HOME_CUSTOM = "homeCustom";
    static final String HOME_URL = "homeUrl";
    // デザイン
    static final String TOOLBAR_SHORTCUT = "toolbarShortcut";
    // タブレット UI のツールバーの 2 つ目・3 つ目のショートカット(既定はブックマーク・リーディングモード)
    static final String TOOLBAR_SHORTCUT_2 = "toolbarShortcut2";
    static final String TOOLBAR_SHORTCUT_3 = "toolbarShortcut3";

    static final int TRACKING_OFF = 0;
    static final int TRACKING_STANDARD = 1;
    static final int TRACKING_STRICT = 2;

    static final int HTTPS_OFF = 0;
    static final int HTTPS_SECRET = 1;
    static final int HTTPS_ALWAYS = 2;

    static final int DNS_DEFAULT = 0;
    static final int DNS_INCREASED = 1;
    static final int DNS_MAX = 2;
    static final int DNS_OFF = 3;

    static final int LANG_JA = 0;
    static final int LANG_EN = 1;
    static final int LANG_DEVICE = 2;

    /** セキュア DNS のプロバイダ(表示名, URL)。 */
    static final String[][] DNS_PROVIDERS = {
            {"Cloudflare", "https://mozilla.cloudflare-dns.com/dns-query"},
            {"Google Public DNS", "https://dns.google/dns-query"},
            {"Quad9", "https://dns.quad9.net/dns-query"},
            {"NextDNS", "https://firefox.dns.nextdns.io/"},
    };

    private BrowserPrefs() {}

    static SharedPreferences prefs(@NonNull Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean bool(Context c, String key, boolean def) {
        return prefs(c).getBoolean(key, def);
    }

    static int integer(Context c, String key, int def) {
        return prefs(c).getInt(key, def);
    }

    static void put(Context c, String key, boolean value) {
        prefs(c).edit().putBoolean(key, value).apply();
    }

    static void put(Context c, String key, int value) {
        prefs(c).edit().putInt(key, value).apply();
    }

    static void put(Context c, String key, String value) {
        prefs(c).edit().putString(key, value).apply();
    }

    // ------------------------------------------------------------------
    // 検索エンジン(スタートページの検索欄と共通)
    // ------------------------------------------------------------------

    /** 設定で選べる検索エンジン(key, 表示名, ドメイン, 検索 URL の頭, パラメータ名, ロゴの色)。 */
    static final String[][] ENGINES = {
            {"google", "Google", "google.com", "https://www.google.com/search", "q", "#4285F4"},
            {"yahoo", "Yahoo! JAPAN", "yahoo.co.jp", "https://search.yahoo.co.jp/search", "p", "#FF0033"},
            {"bing", "Microsoft Bing", "bing.com", "https://www.bing.com/search", "q", "#0C8484"},
            {"duckduckgo", "DuckDuckGo", "duckduckgo.com", "https://duckduckgo.com/", "q", "#DE5833"},
            {"ecosia", "Ecosia", "ecosia.org", "https://www.ecosia.org/search", "q", "#36A852"},
    };

    @NonNull
    static String engineKey(@NonNull Context c) {
        String key = c.getSharedPreferences("start_page", Context.MODE_PRIVATE).getString("engine", null);
        return key != null ? key : "google";
    }

    static void setEngineKey(@NonNull Context c, @NonNull String key) {
        c.getSharedPreferences("start_page", Context.MODE_PRIVATE).edit().putString("engine", key).apply();
    }

    @NonNull
    static String[] engine(@NonNull Context c) {
        String key = engineKey(c);
        for (String[] e : ENGINES) if (e[0].equals(key)) return e;
        if ("youtube".equals(key)) {
            return new String[]{"youtube", "YouTube", "youtube.com", "https://www.youtube.com/results",
                    "search_query", "#FF0000"};
        }
        return ENGINES[0];
    }

    /** URL バーに入力した言葉を、選んでいる検索エンジンの検索 URL にする。 */
    @NonNull
    static String searchUrl(@NonNull Context c, @NonNull String query) {
        String[] e = engine(c);
        try {
            return e[3] + "?" + e[4] + "=" + URLEncoder.encode(query.trim(), "UTF-8");
        } catch (Exception ex) {
            return e[3] + "?" + e[4] + "=" + query;
        }
    }

    // ------------------------------------------------------------------
    // ホームページ
    // ------------------------------------------------------------------

    static boolean homeEnabled(Context c) {
        return bool(c, HOME_ENABLED, true);
    }

    /** ホームボタン(トップページ)で開くページ。null ならスタートページ。 */
    @Nullable
    static String customHomeUrl(Context c) {
        if (!bool(c, HOME_CUSTOM, false)) return null;
        String url = prefs(c).getString(HOME_URL, "");
        if (url == null || url.trim().isEmpty()) return null;
        url = url.trim();
        if (!url.contains("://")) url = "https://" + url;
        return url;
    }

    // ------------------------------------------------------------------
    // GeckoView に反映
    // ------------------------------------------------------------------

    /** 保存してある設定をすべてランタイムに反映する(起動時と設定の変更時)。 */
    static void applyToRuntime(@NonNull Context c, @NonNull GeckoRuntime runtime) {
        GeckoRuntimeSettings s = runtime.getSettings();
        ContentBlocking.Settings cb = s.getContentBlocking();

        // トラッキング防止(Firefox の強化型トラッキング防止)
        int tracking = integer(c, TRACKING, TRACKING_STANDARD);
        switch (tracking) {
            case TRACKING_OFF:
                cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.NONE);
                cb.setAntiTracking(ContentBlocking.AntiTracking.NONE);
                cb.setStrictSocialTrackingProtection(false);
                break;
            case TRACKING_STRICT:
                cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT);
                cb.setAntiTracking(ContentBlocking.AntiTracking.STRICT);
                cb.setStrictSocialTrackingProtection(true);
                break;
            default:
                cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT);
                cb.setAntiTracking(ContentBlocking.AntiTracking.DEFAULT);
                cb.setStrictSocialTrackingProtection(false);
                break;
        }
        // セーフ ブラウジング
        cb.setSafeBrowsing(bool(c, SAFE_BROWSING, true)
                ? ContentBlocking.SafeBrowsing.DEFAULT : ContentBlocking.SafeBrowsing.NONE);

        s.setGlobalPrivacyControl(bool(c, GPC, false));

        // 常に安全な接続(HTTPS-Only モード)
        int https = integer(c, HTTPS_ONLY, HTTPS_OFF);
        s.setAllowInsecureConnections(https == HTTPS_ALWAYS ? GeckoRuntimeSettings.HTTPS_ONLY
                : https == HTTPS_SECRET ? GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE
                : GeckoRuntimeSettings.ALLOW_ALL);

        // セキュア DNS(DNS over HTTPS)
        int dns = integer(c, SECURE_DNS, DNS_DEFAULT);
        int mode;
        switch (dns) {
            case DNS_INCREASED: mode = GeckoRuntimeSettings.TRR_MODE_FIRST; break;
            case DNS_MAX: mode = GeckoRuntimeSettings.TRR_MODE_ONLY; break;
            case DNS_OFF: mode = GeckoRuntimeSettings.TRR_MODE_DISABLED; break;
            default: mode = GeckoRuntimeSettings.TRR_MODE_OFF; break;
        }
        if (mode == GeckoRuntimeSettings.TRR_MODE_FIRST || mode == GeckoRuntimeSettings.TRR_MODE_ONLY) {
            s.setTrustedRecursiveResolverUri(dnsProviderUrl(c));
        }
        s.setTrustedRecursiveResolverMode(mode);

        // ユーザー補助(文字の大きさ・ズーム)
        boolean fontAuto = bool(c, FONT_AUTO, false);
        s.setAutomaticFontSizeAdjustment(fontAuto);
        if (!fontAuto) s.setFontSizeFactor(integer(c, FONT_SCALE, 100) / 100f);
        s.setForceUserScalableEnabled(bool(c, FORCE_ZOOM, false));
        s.setDoubleTapZoomingEnabled(bool(c, DOUBLE_TAP_ZOOM, true));

        // 言語・翻訳
        s.setTranslationsOfferPopup(bool(c, TRANSLATE_OFFER, true));
        s.setLocales(pageLocales(c));

        // Gecko の内部設定で切り替えるもの
        setPref("privacy.donottrackheader.enabled", bool(c, DO_NOT_TRACK, false));
        setPref("accessibility.browsewithcaret", bool(c, CARET, false));
        boolean jit = bool(c, JS_OPTIMIZE, true);
        setPref("javascript.options.ion", jit);
        setPref("javascript.options.baselinejit", jit);
        setPref("javascript.options.wasm_optimizingjit", jit);
        boolean preload = bool(c, PRELOAD, true);
        setPref("network.prefetch-next", preload);
        setPref("network.dns.disablePrefetch", !preload);
        setIntPref("network.http.speculative-parallel-limit", preload ? 6 : 0);
    }

    @NonNull
    static String dnsProviderUrl(Context c) {
        int i = integer(c, SECURE_DNS_PROVIDER, 0);
        if (i < 0 || i >= DNS_PROVIDERS.length) i = 0;
        return DNS_PROVIDERS[i][1];
    }

    /** ウェブページの言語の既定(英語表示なら英語を優先)。 */
    static int defaultPageLanguage() {
        return Lang.isEnglish() ? LANG_EN : LANG_JA;
    }

    @NonNull
    static String[] pageLocales(Context c) {
        switch (integer(c, PAGE_LANGUAGE, defaultPageLanguage())) {
            case LANG_EN:
                return new String[]{"en-US", "en", "ja-JP", "ja"};
            case LANG_DEVICE: {
                List<String> out = new ArrayList<>();
                LocaleList list = LocaleList.getDefault();
                for (int i = 0; i < list.size(); i++) {
                    String tag = list.get(i).toLanguageTag();
                    if (!out.contains(tag)) out.add(tag);
                }
                if (out.isEmpty()) out.add(Locale.getDefault().toLanguageTag());
                return out.toArray(new String[0]);
            }
            default:
                // 英語(en-US)を入れると、Gecko は英語を「読める言語」とみなして英語のページで翻訳を提案しなくなる
                return new String[]{"ja-JP", "ja"};
        }
    }

    private static void setPref(String name, boolean value) {
        try {
            GeckoPreferenceController.setGeckoPref(name, value, GeckoPreferenceController.PREF_BRANCH_USER)
                    .accept(null, e -> Log.w(TAG, "設定を変更できません: " + name, e));
        } catch (Exception e) {
            Log.w(TAG, "設定を変更できません: " + name, e);
        }
    }

    private static void setIntPref(String name, int value) {
        try {
            GeckoPreferenceController.setGeckoPref(name, value, GeckoPreferenceController.PREF_BRANCH_USER)
                    .accept(null, e -> Log.w(TAG, "設定を変更できません: " + name, e));
        } catch (Exception e) {
            Log.w(TAG, "設定を変更できません: " + name, e);
        }
    }

    /** 設定画面から: 保存した値をすぐにランタイムへ反映する。 */
    static void apply(@NonNull Context c) {
        applyToRuntime(c, GeckoEngine.get(c).getRuntime());
    }
}
