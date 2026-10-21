package com.airsky.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoPreferenceController;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.StorageController;
import org.mozilla.geckoview.WebExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * プロセスで 1 つだけの GeckoRuntime と、内蔵ページ用の組み込み拡張機能を管理する。
 *
 * アプリ内のコードは従来どおり "file:///android_asset/index.html" のような URL で内蔵ページを扱い、
 * Gecko に渡す直前に拡張機能の moz-extension:// URL へ変換する({@link #toEngineUrl})。
 * 逆に Gecko から受け取った URL は {@link #toAppUrl} で元の形に戻すので、
 * 履歴・ブックマーク・タブ保存のデータ形式は WebView 版と変わらない。
 */
public final class GeckoEngine {

    private static final String TAG = "GeckoEngine";

    public static final String EXTENSION_ID = "browser@aozora.app";
    public static final String EXTENSION_LOCATION = "resource://android/assets/browser/";
    public static final String LEGACY_PREFIX = "file:///android_asset/";
    /** 英語表示の時に内蔵ページの URL に付ける印(アプリ側の URL には出さない)。 */
    private static final String LANG_SUFFIX_EN = "?hl=en";

    /** 拡張機能に同梱している内蔵ページ。これ以外の file:///android_asset/ はそのまま扱う。 */
    private static final Set<String> BUILT_IN_PAGES = new HashSet<>(Arrays.asList(
            "index.html", "index_white.html", "secret.html", "help.html",
            "error.html", "welcome.html", "index_recording.html"));

    private static GeckoEngine instance;

    private final Context appContext;
    private final GeckoRuntime runtime;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Runnable> pendingReady = new ArrayList<>();

    @Nullable private WebExtension extension;
    @Nullable private String baseUrl;
    @Nullable private WebExtension.Port backgroundPort;
    private boolean blockImages = false;

    public static synchronized GeckoEngine get(@NonNull Context context) {
        if (instance == null) {
            instance = new GeckoEngine(context.getApplicationContext());
        }
        return instance;
    }

    private GeckoEngine(Context appContext) {
        this.appContext = appContext;

        SharedPreferences setupprefs = appContext.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        boolean javaScript = setupprefs.getBoolean("javaScript", true);

        boolean debuggable = (appContext.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(javaScript)
                .consoleOutput(debuggable)
                .remoteDebuggingEnabled(debuggable)
                .aboutConfigEnabled(false)
                .locales(BrowserPrefs.pageLocales(appContext))
                // ページの言語が違う時に翻訳を提案してもらう(Firefox と同じ端末内翻訳)
                .translationsOfferPopup(true)
                // ページを開いた時にログイン情報を自動で埋めない(必ず本人確認を通す。GeckoAutofill 参照)
                .loginAutofillEnabled(false)
                .build();
        runtime = GeckoRuntime.create(appContext, settings);
        enablePasskeys();
        applyCookieSetting();
        applyColorScheme();
        // 設定のプライバシー・セキュリティ・ユーザー補助・言語などを反映
        try {
            BrowserPrefs.applyToRuntime(appContext, runtime);
            WebNotifications.install(appContext, runtime);
        } catch (Exception e) {
            Log.w(TAG, "ブラウザの設定を反映できません", e);
        }
        // 自動入力(パスワード・カード・住所)の保存庫をつなぐ
        try {
            GeckoAutofill.install(appContext, runtime);
        } catch (Exception e) {
            Log.w(TAG, "ブラウザの設定を反映できません", e);
        }

        runtime.getWebExtensionController()
                .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
                .accept(this::onExtensionReady, e -> {
                    Log.e(TAG, "組み込み拡張機能の読み込みに失敗しました", e);
                    // 拡張機能が使えなくても通常のブラウジングは続けられるようにする
                    flushReady();
                });
        // ユーザーが入れた拡張機能(アドオン)の管理を始める(インストールの確認画面・ボタンなど)
        mainHandler.post(() -> ExtensionManager.init(appContext, runtime));
    }

    /**
     * サイトのパスキー(WebAuthn)を使えるようにする。
     * GeckoView は Android 14 以降では端末の認証情報マネージャー(Google パスワード マネージャーなど)、
     * それより前は Google Play 開発者サービスの FIDO2 で処理する。パスキー(端末に保存される認証情報 =
     * resident key)は Gecko の設定で有効にしないと、セキュリティキーのような使い方しかできない。
     */
    private static void enablePasskeys() {
        String[] prefs = {
                "security.webauth.webauthn",
                "security.webauthn.webauthn_enable_android_fido2.residentkey",
                "security.webauthn.enable_json_serialization_methods",
        };
        for (String name : prefs) {
            try {
                GeckoPreferenceController.setGeckoPref(name, Boolean.TRUE,
                                GeckoPreferenceController.PREF_BRANCH_DEFAULT)
                        .accept(null, e -> Log.w(TAG, "パスキーの設定を変更できません: " + name, e));
            } catch (Exception e) {
                Log.w(TAG, "パスキーの設定を変更できません: " + name, e);
            }
        }
    }

    private void onExtensionReady(@Nullable WebExtension ext) {
        if (ext == null) {
            flushReady();
            return;
        }
        extension = ext;
        baseUrl = ext.metaData.baseUrl;
        if (baseUrl != null && !baseUrl.endsWith("/")) baseUrl = baseUrl + "/";

        ext.setMessageDelegate(new WebExtension.MessageDelegate() {
            @Override
            public void onConnect(@NonNull WebExtension.Port port) {
                backgroundPort = port;
                port.setDelegate(new WebExtension.PortDelegate() {
                    @Override
                    public void onDisconnect(@NonNull WebExtension.Port p) {
                        if (backgroundPort == p) backgroundPort = null;
                        CoaraPlugins.get(appContext).detachBackground(p);
                    }
                });
                pushSettingsToBackground();
                // Coara Browser 拡張機能をページで動くように登録してもらう
                CoaraPlugins.get(appContext).attachBackground(port);
            }
        }, "aozora_bg");

        // シークレット(プライベート)タブでも内蔵ページとブリッジを使えるようにする
        if (!ext.metaData.allowedInPrivateBrowsing) {
            runtime.getWebExtensionController()
                    .setAllowedInPrivateBrowsing(ext, true)
                    .accept(updated -> {
                        if (updated != null) extension = updated;
                        flushReady();
                    }, e -> flushReady());
        } else {
            flushReady();
        }
    }

    private void flushReady() {
        mainHandler.post(() -> {
            List<Runnable> callbacks = new ArrayList<>(pendingReady);
            pendingReady.clear();
            ready = true;
            for (Runnable r : callbacks) r.run();
        });
    }

    private boolean ready = false;


    /** 拡張機能の準備ができたら(できていれば即座に)メインスレッドで実行する。 */
    public void whenReady(@NonNull Runnable r) {
        if (ready) {
            r.run();
        } else {
            pendingReady.add(r);
        }
    }

    @NonNull
    public GeckoRuntime getRuntime() {
        return runtime;
    }

    @Nullable
    public WebExtension getExtension() {
        return extension;
    }

    /** アプリ内の URL 表現(file:///android_asset/...)を Gecko が読み込める URL に変換する。 */
    @Nullable
    public String toEngineUrl(@Nullable String url) {
        if (url == null) return null;
        if (url.startsWith(LEGACY_PREFIX) && baseUrl != null) {
            String rest = url.substring(LEGACY_PREFIX.length());
            String page = rest;
            int cut = indexOfAny(page, '?', '#');
            if (cut >= 0) page = page.substring(0, cut);
            if (BUILT_IN_PAGES.contains(page)) {
                // 英語表示の時は内蔵ページにも伝える(ページの i18n.js が英語にする)
                if (cut < 0 && Lang.isEnglish()) rest += LANG_SUFFIX_EN;
                return baseUrl + rest;
            }
        }
        return url;
    }

    /** Gecko の URL(moz-extension://...)をアプリ内の URL 表現に戻す。 */
    @Nullable
    public String toAppUrl(@Nullable String url) {
        if (url == null) return null;
        if (baseUrl != null && url.startsWith(baseUrl)) {
            String rest = url.substring(baseUrl.length());
            if (rest.endsWith(LANG_SUFFIX_EN)) rest = rest.substring(0, rest.length() - LANG_SUFFIX_EN.length());
            return LEGACY_PREFIX + rest;
        }
        return url;
    }


    private static int indexOfAny(String s, char a, char b) {
        int i = s.indexOf(a);
        int j = s.indexOf(b);
        if (i < 0) return j;
        if (j < 0) return i;
        return Math.min(i, j);
    }

    // ---- 設定 ----

    public void setBlockImages(boolean block) {
        blockImages = block;
        pushSettingsToBackground();
    }


    private void pushSettingsToBackground() {
        if (backgroundPort == null) return;
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "settings");
            msg.put("blockImages", blockImages);
            backgroundPort.postMessage(msg);
        } catch (Exception e) {
            Log.w(TAG, "設定の送信に失敗しました", e);
        }
    }

    /** 設定画面の「Cookie を受け入れる」と、サードパーティ Cookie の許可/ブロックを反映する。 */
    public void applyCookieSetting() {
        int behavior;
        if (!CookiePolicy.isCookiesAccepted(appContext)) {
            behavior = ContentBlocking.CookieBehavior.ACCEPT_NONE;
        } else if (CookiePolicy.isThirdPartyAllowed(appContext)) {
            behavior = ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS;
        } else {
            behavior = ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY;
        }
        ContentBlocking.Settings cb = runtime.getSettings().getContentBlocking();
        cb.setCookieBehavior(behavior);
        cb.setCookieBehaviorPrivateMode(behavior);
    }

    /**
     * ページの prefers-color-scheme(ダーク/ライト)を今のアプリの見た目に合わせる。
     * <ul>
     *   <li>ブラウザ設定の「ダークモード」が ON なら常にダーク</li>
     *   <li>それ以外はアプリのテーマ設定(ライト/ダーク/システムに従う)に合わせる</li>
     * </ul>
     * COLOR_SCHEME_SYSTEM に任せると、Gecko はランタイム作成時(=アプリ起動時)の端末の状態のまま
     * 切り替わらず、アプリ独自のテーマ設定も無視されるため、毎回 DARK / LIGHT を明示する。
     * 画面の作成・再開時や端末のダークモード切り替え時に呼ぶ。
     */
    /**
     * ページの配色(prefers-color-scheme)を今のテーマに合わせる。
     *
     * <p>ライト/ダークが前回から変わっていたら、サイトのキャッシュを消す。キャッシュに残った
     * 前の配色の CSS・画像のままだと、読み直しても配色が切り替わらないサイトがあるため。</p>
     *
     * @return 配色が変わった(キャッシュを消した)なら、消し終わった時に終わる GeckoResult。変わっていなければ null
     */
    @Nullable
    public GeckoResult<Void> applyColorScheme() {
        SharedPreferences pref = appContext.getSharedPreferences("AdvancedBrowserPrefs", Context.MODE_PRIVATE);
        boolean dark = pref.getBoolean("dark_mode", false) || isNightNow();
        int scheme = dark ? GeckoRuntimeSettings.COLOR_SCHEME_DARK : GeckoRuntimeSettings.COLOR_SCHEME_LIGHT;
        if (runtime.getSettings().getPreferredColorScheme() != scheme) {
            runtime.getSettings().setPreferredColorScheme(scheme);
        }
        // アプリを閉じている間に切り替わった場合も分かるよう、前回の配色は保存しておく
        int last = pref.getInt(KEY_LAST_COLOR_SCHEME, Integer.MIN_VALUE);
        if (last == scheme) return null;
        pref.edit().putInt(KEY_LAST_COLOR_SCHEME, scheme).apply();
        if (last == Integer.MIN_VALUE) return null; // 初めての起動
        return runtime.getStorageController().clearData(StorageController.ClearFlags.ALL_CACHES);
    }

    private static final String KEY_LAST_COLOR_SCHEME = "last_color_scheme";

    /** アプリが今ダーク表示か(アプリのテーマ設定 → 端末の現在の設定の順に判断)。 */
    private static boolean isNightNow() {
        int mode = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode();
        if (mode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) return true;
        if (mode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) return false;
        // 「システムに従う」: アプリのプロセスが持つ設定は古いことがあるので、端末全体の現在値を見る
        int ui = android.content.res.Resources.getSystem().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return ui == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public void setJavaScriptEnabled(boolean enabled) {
        runtime.getSettings().setJavaScriptEnabled(enabled);
    }


    /** Cookie を全て消去する。 */
    public GeckoResult<Void> clearCookies() {
        return runtime.getStorageController().clearData(StorageController.ClearFlags.COOKIES);
    }

    /** キャッシュだけを消去する。 */
    public GeckoResult<Void> clearCache() {
        return runtime.getStorageController().clearData(
                StorageController.ClearFlags.NETWORK_CACHE | StorageController.ClearFlags.IMAGE_CACHE);
    }
}
