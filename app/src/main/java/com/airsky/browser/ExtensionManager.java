package com.airsky.browser;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ユーザーが入れた拡張機能(Firefox のアドオン)をまとめて管理する。
 *
 * <ul>
 *   <li>インストール(Firefox のアドオンストア・.xpi ファイル)と、その時の権限の確認画面</li>
 *   <li>有効/無効・シークレットでの実行・削除</li>
 *   <li>ツールバーボタン(browser_action / page_action)の状態と、ポップアップの表示</li>
 *   <li>拡張機能からのタブ操作(新しいタブ・タブの更新・閉じる・設定ページ)</li>
 * </ul>
 *
 * <p>内蔵ページ用の組み込み拡張機能({@link GeckoEngine#EXTENSION_ID})は一覧に出さない。</p>
 */
public final class ExtensionManager {

    private static final String TAG = "ExtensionManager";

    /** Firefox for Android 向けのアドオンストア。 */
    public static final String AMO_URL = "https://addons.mozilla.org/ja/android/";

    /** 一覧・状態が変わった時の通知先。 */
    public interface Listener {
        void onExtensionsChanged();
    }

    /** 拡張機能からのタブ操作を受け持つ画面(ブラウザ本体)。 */
    public interface TabHost {
        /** 新しいタブを作り、まだ開いていない GeckoSession を返す。 */
        @Nullable GeckoSession onExtensionNewTab(@NonNull WebExtension ext, @Nullable String url, boolean active);
        /** URL を新しいタブで開く(拡張機能の設定ページなど)。 */
        void onExtensionOpenUrl(@NonNull String url);
        /** そのセッションのタブを前面に出す。 */
        boolean onExtensionSelectTab(@NonNull GeckoSession session);
        /** そのセッションのタブで URL を開く。 */
        boolean onExtensionLoadInTab(@NonNull GeckoSession session, @NonNull String url);
        /** そのセッションのタブを閉じる。 */
        boolean onExtensionCloseTab(@NonNull GeckoSession session);
        /** 今前面にあるタブのセッション。 */
        @Nullable GeckoSession currentSession();
    }

    private static ExtensionManager instance;

    private final Context appContext;
    private final GeckoRuntime runtime;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    /** 入っている拡張機能(組み込みを除く)。id → 拡張機能。入れた順。 */
    private final Map<String, WebExtension> installed = new LinkedHashMap<>();
    /** 拡張機能全体のツールバーボタンの状態。 */
    private final Map<String, WebExtension.Action> defaultActions = new HashMap<>();
    /** タブ(セッション)ごとに上書きされたボタンの状態(バッジの数など)。 */
    private final Map<GeckoSession, Map<String, WebExtension.Action>> sessionActions = new WeakHashMap<>();
    /** 拡張機能の操作を受け付けるために見張っているタブ。 */
    private final Set<GeckoSession> sessions = Collections.newSetFromMap(new WeakHashMap<>());
    /** インストール中(ストアのおすすめで + を押した等)の拡張機能のダウンロード URL。 */
    private final Set<String> installing = Collections.synchronizedSet(new java.util.HashSet<>());

    @Nullable private WeakReference<Activity> topActivity;
    @Nullable private WeakReference<TabHost> tabHost;
    private boolean loaded;

    static ExtensionManager get(@NonNull Context context) {
        // ランタイムを作ると init() が呼ばれる(GeckoEngine の中から)
        GeckoRuntime runtime = GeckoEngine.get(context).getRuntime();
        synchronized (ExtensionManager.class) {
            if (instance == null) {
                instance = new ExtensionManager(context.getApplicationContext(), runtime);
            }
            return instance;
        }
    }

    /** GeckoRuntime が作られた後に一度だけ呼ぶ。 */
    static synchronized void init(@NonNull Context context, @NonNull GeckoRuntime runtime) {
        if (instance != null) return;
        instance = new ExtensionManager(context.getApplicationContext(), runtime);
    }

    private ExtensionManager(Context appContext, GeckoRuntime runtime) {
        this.appContext = appContext;
        this.runtime = runtime;
        trackActivities();
        WebExtensionController controller = runtime.getWebExtensionController();
        controller.setPromptDelegate(new InstallPrompts());
        controller.setAddonManagerDelegate(new WebExtensionController.AddonManagerDelegate() {
            @Override
            public void onInstalled(@NonNull WebExtension ext) {
                if (!isUserExtension(ext)) return;
                main.post(() -> {
                    // ストアのページの「Firefox へ追加」など、Gecko が自分で始めたインストール。
                    // install() から入れた分は、その結果のほうでお知らせを出す
                    boolean isNew = !installed.containsKey(ext.id);
                    boolean ours = !installing.isEmpty();
                    installed.put(ext.id, ext);
                    attach(ext);
                    notifyChanged();
                    Activity a = currentActivity();
                    if (isNew && !ours && a != null) ExtensionInstallSheet.showInstalled(a, ext);
                });
            }
            @Override public void onReady(@NonNull WebExtension ext) { put(ext); }
            @Override public void onEnabled(@NonNull WebExtension ext) { put(ext); }
            @Override public void onDisabled(@NonNull WebExtension ext) { put(ext); }
            @Override public void onOptionalPermissionsChanged(@NonNull WebExtension ext) { put(ext); }

            @Override
            public void onUninstalled(@NonNull WebExtension ext) {
                main.post(() -> {
                    installed.remove(ext.id);
                    defaultActions.remove(ext.id);
                    for (Map<String, WebExtension.Action> m : sessionActions.values()) m.remove(ext.id);
                    notifyChanged();
                });
            }
        });
        refresh();
    }

    // ------------------------------------------------------------------
    // 一覧
    // ------------------------------------------------------------------

    /** Gecko から入っている拡張機能を読み直す。 */
    public void refresh() {
        runtime.getWebExtensionController().list().accept(list -> main.post(() -> {
            installed.clear();
            if (list != null) {
                for (WebExtension ext : list) {
                    if (isUserExtension(ext)) {
                        installed.put(ext.id, ext);
                        attach(ext);
                    }
                }
            }
            loaded = true;
            notifyChanged();
        }), e -> Log.w(TAG, "拡張機能の一覧を読めませんでした", e));
    }

    public boolean isLoaded() {
        return loaded;
    }

    @NonNull
    public List<WebExtension> getInstalled() {
        return new ArrayList<>(installed.values());
    }

    @Nullable
    public WebExtension find(@Nullable String id) {
        return id == null ? null : installed.get(id);
    }

    /** そのアドオンが入っているか(おすすめの一覧で + を出すかどうか)。 */
    public boolean isInstalled(@Nullable String id) {
        return id != null && installed.containsKey(id);
    }

    public boolean isInstalling(@Nullable String downloadUrl) {
        return downloadUrl != null && installing.contains(downloadUrl);
    }

    public void addListener(@NonNull Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(@NonNull Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged() {
        for (Listener l : listeners) l.onExtensionsChanged();
    }

    private static boolean isUserExtension(@Nullable WebExtension ext) {
        return ext != null && !ext.isBuiltIn && !GeckoEngine.EXTENSION_ID.equals(ext.id);
    }

    private void put(@Nullable WebExtension ext) {
        if (!isUserExtension(ext)) return;
        main.post(() -> {
            installed.put(ext.id, ext);
            attach(ext);
            notifyChanged();
        });
    }

    public static boolean isEnabled(@NonNull WebExtension ext) {
        return ext.metaData.enabled;
    }

    // ------------------------------------------------------------------
    // インストール・削除・有効/無効
    // ------------------------------------------------------------------

    /**
     * URL(https の .xpi・file://)から入れる。権限の確認画面は {@link InstallPrompts} が出す。
     * @param method WebExtensionController.INSTALLATION_METHOD_*(分からなければ null)
     */
    public GeckoResult<WebExtension> install(@NonNull String uri, @Nullable String method) {
        installing.add(uri);
        notifyChanged();
        GeckoResult<WebExtension> result = method != null
                ? runtime.getWebExtensionController().install(uri, method)
                : runtime.getWebExtensionController().install(uri);
        result.accept(ext -> main.post(() -> {
            installing.remove(uri);
            if (ext != null && isUserExtension(ext)) {
                installed.put(ext.id, ext);
                attach(ext);
                Activity a = currentActivity();
                if (a != null) ExtensionInstallSheet.showInstalled(a, ext);
            }
            notifyChanged();
        }), e -> main.post(() -> {
            installing.remove(uri);
            notifyChanged();
            showInstallError(e);
        }));
        return result;
    }

    /**
     * ファイルアプリ等から渡された .xpi(content:// を含む)を入れる。
     * Gecko は content:// を読めないので、アプリのキャッシュへ写してから file:// で渡す。
     */
    public void installFromUri(@NonNull Uri uri) {
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
            install(uri.toString(), null);
            return;
        }
        if ("file".equalsIgnoreCase(scheme)) {
            install(uri.toString(), WebExtensionController.INSTALLATION_METHOD_FROM_FILE);
            return;
        }
        Toast.makeText(appContext, Lang.t("拡張機能を読み込んでいます…"), Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            File dir = new File(appContext.getCacheDir(), "xpi");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File out = new File(dir, "install_" + System.currentTimeMillis() + ".xpi");
            try (InputStream in = appContext.getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(out)) {
                if (in == null) throw new java.io.IOException("open failed");
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            } catch (Exception e) {
                Log.w(TAG, "xpi を読めませんでした", e);
                main.post(() -> Toast.makeText(appContext, Lang.t("ファイルを読み込めませんでした"), Toast.LENGTH_LONG).show());
                return;
            }
            main.post(() -> install(Uri.fromFile(out).toString(), WebExtensionController.INSTALLATION_METHOD_FROM_FILE)
                    .accept(ext -> out.delete(), e -> out.delete()));
        });
    }

    private void showInstallError(@Nullable Throwable e) {
        String msg;
        if (e instanceof WebExtension.InstallException) {
            WebExtension.InstallException ie = (WebExtension.InstallException) e;
            switch (ie.code) {
                case WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED:
                    return; // 自分でキャンセルした
                case WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE:
                    msg = Lang.t("ネットワークに接続できないため、ダウンロードできませんでした。");
                    break;
                case WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE:
                case WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_HASH:
                    msg = Lang.t("ファイルが壊れているため、インストールできませんでした。");
                    break;
                case WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED:
                    msg = Lang.t("この拡張機能は Mozilla の署名がないため、インストールできません。");
                    break;
                case WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE:
                case WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_VERSION:
                    msg = Lang.t("この拡張機能は AirSky に対応していません。");
                    break;
                case WebExtension.InstallException.ErrorCodes.ERROR_UNSUPPORTED_ADDON_TYPE:
                case WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_TYPE:
                    msg = Lang.t("この種類のアドオンはインストールできません。");
                    break;
                case WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED:
                case WebExtension.InstallException.ErrorCodes.ERROR_SOFT_BLOCKED:
                    msg = Lang.t("この拡張機能は安全上の理由でブロックされています。");
                    break;
                default:
                    msg = Lang.t("拡張機能をインストールできませんでした。");
            }
            if (ie.extensionName != null && !ie.extensionName.isEmpty()) {
                msg = ie.extensionName + "\n" + msg;
            }
        } else {
            msg = Lang.t("拡張機能をインストールできませんでした。");
        }
        Activity a = currentActivity();
        if (a != null) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(a)
                    .setTitle(Lang.t("インストールに失敗しました"))
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show();
        } else {
            Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show();
        }
    }

    public GeckoResult<WebExtension> setEnabled(@NonNull WebExtension ext, boolean enabled) {
        WebExtensionController c = runtime.getWebExtensionController();
        GeckoResult<WebExtension> r = enabled
                ? c.enable(ext, WebExtensionController.EnableSource.USER)
                : c.disable(ext, WebExtensionController.EnableSource.USER);
        r.accept(this::put, e -> main.post(() ->
                Toast.makeText(appContext, Lang.t("切り替えられませんでした"), Toast.LENGTH_SHORT).show()));
        return r;
    }

    public GeckoResult<WebExtension> setAllowedInPrivate(@NonNull WebExtension ext, boolean allowed) {
        GeckoResult<WebExtension> r = runtime.getWebExtensionController().setAllowedInPrivateBrowsing(ext, allowed);
        r.accept(this::put, e -> main.post(() ->
                Toast.makeText(appContext, Lang.t("切り替えられませんでした"), Toast.LENGTH_SHORT).show()));
        return r;
    }

    public GeckoResult<Void> uninstall(@NonNull WebExtension ext) {
        GeckoResult<Void> r = runtime.getWebExtensionController().uninstall(ext);
        r.accept(v -> main.post(() -> {
            installed.remove(ext.id);
            defaultActions.remove(ext.id);
            notifyChanged();
        }), e -> main.post(() ->
                Toast.makeText(appContext, Lang.t("削除できませんでした"), Toast.LENGTH_SHORT).show()));
        return r;
    }

    /** 任意の権限(optional_permissions)を許可/取り消す。 */
    public GeckoResult<WebExtension> setOptionalPermission(@NonNull WebExtension ext, @NonNull String permission,
                                                           boolean origin, boolean granted) {
        String[] perms = origin ? new String[0] : new String[]{permission};
        String[] origins = origin ? new String[]{permission} : new String[0];
        WebExtensionController c = runtime.getWebExtensionController();
        GeckoResult<WebExtension> r = granted
                ? c.addOptionalPermissions(ext.id, perms, origins, new String[0])
                : c.removeOptionalPermissions(ext.id, perms, origins, new String[0]);
        r.accept(this::put, e -> main.post(() ->
                Toast.makeText(appContext, Lang.t("権限を変更できませんでした"), Toast.LENGTH_SHORT).show()));
        return r;
    }

    public GeckoResult<WebExtension> update(@NonNull WebExtension ext) {
        GeckoResult<WebExtension> r = runtime.getWebExtensionController().update(ext);
        r.accept(updated -> {
            if (updated != null) put(updated);
        }, e -> { });
        return r;
    }

    /** 拡張機能の設定ページを開く(タブで開く指定でも、無い場合でもアプリのタブで開く)。 */
    public boolean openOptionsPage(@NonNull WebExtension ext) {
        String url = ext.metaData.optionsPageUrl;
        if (url == null || url.isEmpty()) return false;
        TabHost host = host();
        if (host != null) {
            host.onExtensionOpenUrl(url);
            return true;
        }
        // ブラウザの画面が無い(設定から開いた等): ブラウザを起動して開く
        Activity a = currentActivity();
        Context c = a != null ? a : appContext;
        android.content.Intent i = new android.content.Intent(c, MainActivity.class)
                .putExtra("url", url)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (a == null) i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
        return true;
    }

    // ------------------------------------------------------------------
    // ツールバーボタン・タブ
    // ------------------------------------------------------------------

    public void setTabHost(@Nullable TabHost host) {
        tabHost = host == null ? null : new WeakReference<>(host);
    }

    /** host が今の受け持ちなら外す。 */
    public void clearTabHost(@NonNull TabHost host) {
        if (host() == host) tabHost = null;
    }

    @Nullable
    private TabHost host() {
        return tabHost != null ? tabHost.get() : null;
    }

    /** 新しく作ったタブを、拡張機能のボタン状態・タブ操作の対象にする。 */
    public void attachSession(@NonNull GeckoSession session) {
        sessions.add(session);
        for (WebExtension ext : installed.values()) attachToSession(ext, session);
    }

    /** タブが前面に来た/裏へ回った(拡張機能の tabs.query({active:true}) に使われる)。 */
    public void setTabActive(@NonNull GeckoSession session, boolean active) {
        try {
            runtime.getWebExtensionController().setTabActive(session, active);
        } catch (Exception ignored) {
        }
    }

    /**
     * 今のタブで見えるボタンの状態。拡張機能がボタンを持たないなら null。
     */
    @Nullable
    public WebExtension.Action actionFor(@NonNull WebExtension ext, @Nullable GeckoSession session) {
        WebExtension.Action def = defaultActions.get(ext.id);
        if (session != null) {
            Map<String, WebExtension.Action> m = sessionActions.get(session);
            WebExtension.Action own = m != null ? m.get(ext.id) : null;
            if (own != null) return def != null ? own.withDefault(def) : own;
        }
        return def;
    }

    /** 今前面にあるタブのセッション(ブラウザの画面が無ければ null)。 */
    @Nullable
    public GeckoSession currentSession() {
        TabHost host = host();
        return host != null ? host.currentSession() : null;
    }

    /** ボタンを押す(ポップアップがあれば ActionDelegate.onTogglePopup が呼ばれる)。 */
    public boolean clickAction(@NonNull WebExtension ext) {
        TabHost host = host();
        WebExtension.Action a = actionFor(ext, host != null ? host.currentSession() : null);
        if (a == null) return false;
        a.click();
        return true;
    }

    private void attach(@NonNull WebExtension ext) {
        ext.setActionDelegate(new ActionHandler(null));
        ext.setTabDelegate(new WebExtension.TabDelegate() {
            @Nullable
            @Override
            public GeckoResult<GeckoSession> onNewTab(@NonNull WebExtension source,
                                                      @NonNull WebExtension.CreateTabDetails details) {
                TabHost host = host();
                if (host == null) return null;
                boolean active = details.active == null || details.active;
                GeckoSession s = host.onExtensionNewTab(source, details.url, active);
                return s != null ? GeckoResult.fromValue(s) : null;
            }

            @Override
            public void onOpenOptionsPage(@NonNull WebExtension source) {
                main.post(() -> openOptionsPage(source));
            }
        });
        for (GeckoSession s : new ArrayList<>(sessions)) attachToSession(ext, s);
    }

    private void attachToSession(@NonNull WebExtension ext, @NonNull GeckoSession session) {
        try {
            WebExtension.SessionController c = session.getWebExtensionController();
            c.setActionDelegate(ext, new ActionHandler(session));
            c.setTabDelegate(ext, new WebExtension.SessionTabDelegate() {
                @NonNull
                @Override
                public GeckoResult<AllowOrDeny> onCloseTab(@Nullable WebExtension source, @NonNull GeckoSession s) {
                    TabHost host = host();
                    return GeckoResult.fromValue(host != null && host.onExtensionCloseTab(s)
                            ? AllowOrDeny.ALLOW : AllowOrDeny.DENY);
                }

                @NonNull
                @Override
                public GeckoResult<AllowOrDeny> onUpdateTab(@NonNull WebExtension source, @NonNull GeckoSession s,
                                                            @NonNull WebExtension.UpdateTabDetails details) {
                    TabHost host = host();
                    if (host == null) return GeckoResult.fromValue(AllowOrDeny.DENY);
                    if (Boolean.TRUE.equals(details.active)) host.onExtensionSelectTab(s);
                    if (details.url != null) host.onExtensionLoadInTab(s, details.url);
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "タブへの登録に失敗しました", e);
        }
    }

    /** ボタンの状態の変化とポップアップを受け取る。session が null なら拡張機能全体の既定。 */
    private final class ActionHandler implements WebExtension.ActionDelegate {
        @Nullable private final WeakReference<GeckoSession> session;

        ActionHandler(@Nullable GeckoSession session) {
            this.session = session == null ? null : new WeakReference<>(session);
        }

        private void store(@NonNull WebExtension ext, @Nullable GeckoSession s, @NonNull WebExtension.Action action) {
            main.post(() -> {
                GeckoSession target = session != null ? session.get() : s;
                if (target == null) {
                    defaultActions.put(ext.id, action);
                } else {
                    Map<String, WebExtension.Action> m = sessionActions.get(target);
                    if (m == null) {
                        m = new HashMap<>();
                        sessionActions.put(target, m);
                    }
                    m.put(ext.id, action);
                }
                notifyChanged();
            });
        }

        @Override
        public void onBrowserAction(@NonNull WebExtension ext, @Nullable GeckoSession s, @NonNull WebExtension.Action action) {
            store(ext, s, action);
        }

        @Override
        public void onPageAction(@NonNull WebExtension ext, @Nullable GeckoSession s, @NonNull WebExtension.Action action) {
            store(ext, s, action);
        }

        @Nullable
        @Override
        public GeckoResult<GeckoSession> onTogglePopup(@NonNull WebExtension ext, @NonNull WebExtension.Action action) {
            return openPopup(ext, action);
        }

        @Nullable
        @Override
        public GeckoResult<GeckoSession> onOpenPopup(@NonNull WebExtension ext, @NonNull WebExtension.Action action) {
            return openPopup(ext, action);
        }
    }

    /** 拡張機能のポップアップ(uBlock Origin のパネルなど)を下から出るシートに表示する。 */
    @Nullable
    private GeckoResult<GeckoSession> openPopup(@NonNull WebExtension ext, @NonNull WebExtension.Action action) {
        Activity a = currentActivity();
        if (a == null || a.isFinishing()) return null;
        GeckoSession popup = new GeckoSession(new GeckoSessionSettings.Builder()
                .usePrivateMode(false)
                .build());
        popup.open(runtime);
        main.post(() -> ExtensionPopupSheet.show(a, ext, action, popup));
        return GeckoResult.fromValue(popup);
    }

    // ------------------------------------------------------------------
    // インストール時の確認
    // ------------------------------------------------------------------

    private final class InstallPrompts implements WebExtensionController.PromptDelegate {
        @Nullable
        @Override
        public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
                @NonNull WebExtension ext, @NonNull String[] permissions, @NonNull String[] origins,
                @NonNull String[] dataCollectionPermissions) {
            GeckoResult<WebExtension.PermissionPromptResponse> result = new GeckoResult<>();
            main.post(() -> {
                Activity a = currentActivity();
                if (a == null || a.isFinishing()) {
                    result.complete(new WebExtension.PermissionPromptResponse(false, false, false));
                    return;
                }
                ExtensionInstallSheet.showPrompt(a, ext, permissions, origins, dataCollectionPermissions,
                        (accepted, privateMode, technicalData) -> result.complete(
                                new WebExtension.PermissionPromptResponse(accepted, privateMode, technicalData)));
            });
            return result;
        }

        @Nullable
        @Override
        public GeckoResult<AllowOrDeny> onUpdatePrompt(@NonNull WebExtension ext, @NonNull String[] newPermissions,
                                                       @NonNull String[] newOrigins, @NonNull String[] newData) {
            return askPermissions(ext, Lang.t("「") + ExtensionUi.name(ext) + Lang.t("」の更新に新しい権限が必要です"),
                    newPermissions, newOrigins, Lang.t("更新"));
        }

        @Nullable
        @Override
        public GeckoResult<AllowOrDeny> onOptionalPrompt(@NonNull WebExtension ext, @NonNull String[] permissions,
                                                         @NonNull String[] origins, @NonNull String[] data) {
            return askPermissions(ext, Lang.t("「") + ExtensionUi.name(ext) + Lang.t("」が追加の権限を求めています"),
                    permissions, origins, Lang.t("許可"));
        }

        private GeckoResult<AllowOrDeny> askPermissions(WebExtension ext, String title, String[] perms,
                                                        String[] origins, String positive) {
            GeckoResult<AllowOrDeny> result = new GeckoResult<>();
            main.post(() -> {
                Activity a = currentActivity();
                if (a == null || a.isFinishing()) {
                    result.complete(AllowOrDeny.DENY);
                    return;
                }
                List<String> lines = ExtensionUi.describePermissions(perms, origins);
                StringBuilder sb = new StringBuilder();
                for (String l : lines) sb.append(Lang.t("・")).append(l).append('\n');
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(a)
                        .setTitle(title)
                        .setMessage(sb.length() > 0 ? sb.toString().trim() : null)
                        .setPositiveButton(positive, (d, w) -> result.complete(AllowOrDeny.ALLOW))
                        .setNegativeButton(Lang.t("キャンセル"), (d, w) -> result.complete(AllowOrDeny.DENY))
                        .setOnCancelListener(d -> result.complete(AllowOrDeny.DENY))
                        .show();
            });
            return result;
        }
    }

    // ------------------------------------------------------------------
    // 前面の画面
    // ------------------------------------------------------------------

    @Nullable
    Activity currentActivity() {
        Activity a = topActivity != null ? topActivity.get() : null;
        return a != null && !a.isFinishing() && !a.isDestroyed() ? a : null;
    }

    private void trackActivities() {
        if (!(appContext instanceof Application)) return;
        ((Application) appContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { topActivity = new WeakReference<>(a); }
            @Override public void onActivityStarted(@NonNull Activity a) { }
            @Override public void onActivityResumed(@NonNull Activity a) { topActivity = new WeakReference<>(a); }
            @Override public void onActivityPaused(@NonNull Activity a) { }
            @Override public void onActivityStopped(@NonNull Activity a) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }

            @Override
            public void onActivityDestroyed(@NonNull Activity a) {
                if (topActivity != null && topActivity.get() == a) topActivity = null;
            }
        });
    }
}
