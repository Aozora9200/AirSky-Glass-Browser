package com.airsky.browser;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.airsky.browser.coara.GeckoScripts;
import com.airsky.browser.coara.PluginInfo;
import com.airsky.browser.coara.PluginManager;
import com.airsky.browser.coara.PluginViewOps;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Coara Browser 拡張機能(Coara のプラグイン: plugin.xml / ユーザースクリプト / ユーザースタイル)を
 * GeckoView のページで動かす。
 *
 * <ul>
 *   <li>有効なプラグインのコードと保存データを組み込み拡張の background.js へ送り、
 *       ページの読み込み開始時に動くスクリプトとして登録してもらう(実際に動かすのは coara_cs.js)</li>
 *   <li>ページで動いているプラグインからの依頼(保存・通知・コピー・新しいタブ・GM_xmlhttpRequest・
 *       メニューのコマンド)を、タブごとのネイティブのポート("aozora_plg")で受け取る</li>
 * </ul>
 *
 * <p>インストール・有効/無効・保存データは Coara のコード({@link PluginManager})をそのまま使う。</p>
 */
public final class CoaraPlugins {

    private static final String TAG = "CoaraPlugins";
    /** ページ側(coara_cs.js)とつなぐネイティブアプリ名。 */
    static final String NATIVE_APP = "aozora_plg";
    /** 保存データが変わってから、登録し直すまで待つ時間。 */
    private static final long STORE_PUSH_DELAY_MS = 800;

    private static CoaraPlugins instance;

    private final Context appContext;
    private final PluginManager manager;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** 保存データの書き込み・プラグインの組み立て(ファイルを読み書きするので画面の処理と分ける)。 */
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Map<GeckoSession, Tab> tabs = new WeakHashMap<>();
    private final Set<String> dirtyStores = new HashSet<>();

    @Nullable private WebExtension.Port background;

    public static synchronized CoaraPlugins get(@NonNull Context context) {
        if (instance == null) instance = new CoaraPlugins(context.getApplicationContext());
        return instance;
    }

    private CoaraPlugins(Context appContext) {
        this.appContext = appContext;
        this.manager = PluginManager.get(appContext);
        manager.addListener(new PluginManager.Listener() {
            @Override
            public void onPluginsChanged() {
            }

            @Override
            public void onPluginAutoDisabled(PluginInfo info, String reason) {
            }

            @Override
            public void onPluginRuntimeChanged() {
                pushAll();
            }
        });
        io.execute(this::installBundled);
    }

    // ------------------------------------------------------------------
    // 同梱の拡張機能
    // ------------------------------------------------------------------

    /** アプリに同梱している Coara Browser 拡張機能(assets/coara/bundled/*.zip)。 */
    private static final String BUNDLED_DIR = "coara/bundled";
    private static final String PREFS = "coara_plugins";
    private static final String KEY_SEEDED = "bundled_seeded";

    /**
     * 同梱の拡張機能を、まだ入れていなければ入れる(1 つにつき 1 回だけ)。
     * どれも全部のページで動くので、入れた時は無効にしておき、使うものを一覧で有効にしてもらう。
     * 自分で削除したものは、次の起動で入れ直さない。
     */
    private void installBundled() {
        android.content.SharedPreferences pref = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> seeded = new HashSet<>(pref.getStringSet(KEY_SEEDED, new HashSet<>()));
        String[] names;
        try {
            names = appContext.getAssets().list(BUNDLED_DIR);
        } catch (Exception e) {
            return;
        }
        if (names == null) return;
        boolean changed = false;
        for (String name : names) {
            if (!name.endsWith(".zip") || seeded.contains(name)) continue;
            try (java.io.InputStream in = appContext.getAssets().open(BUNDLED_DIR + "/" + name)) {
                com.airsky.browser.coara.ParsedPlugin parsed =
                        com.airsky.browser.coara.PluginInstaller.parse(in, name, null);
                if (manager.get(parsed.info.id) == null) {
                    PluginManager.InstallOutcome outcome = manager.install(parsed);
                    manager.setEnabled(outcome.info.id, false);
                }
                seeded.add(name);
                changed = true;
            } catch (Exception e) {
                Log.w(TAG, "同梱の拡張機能を入れられません: " + name, e);
            }
        }
        if (changed) pref.edit().putStringSet(KEY_SEEDED, seeded).apply();
    }

    @NonNull
    public PluginManager manager() {
        return manager;
    }

    // ------------------------------------------------------------------
    // 組み込み拡張(background.js)への登録
    // ------------------------------------------------------------------

    /** 組み込み拡張のバックグラウンドとつながった(起動時・拡張の再読み込み時)。 */
    void attachBackground(@NonNull WebExtension.Port port) {
        background = port;
        pushAll();
    }

    void detachBackground(@NonNull WebExtension.Port port) {
        if (background == port) background = null;
    }

    /** 有効なプラグインを全部送り直す(インストール・有効/無効・削除の後)。 */
    public void pushAll() {
        io.execute(() -> {
            try {
                JSONArray plugins = GeckoScripts.buildAll(appContext);
                JSONObject stores = new JSONObject();
                JSONObject versions = new JSONObject();
                long v = System.currentTimeMillis();
                for (int i = 0; i < plugins.length(); i++) {
                    String id = plugins.getJSONObject(i).getString("id");
                    stores.put(id, GeckoScripts.storeOf(appContext, id));
                    versions.put(id, v);
                }
                JSONObject msg = new JSONObject();
                msg.put("type", "coaraPlugins");
                msg.put("plugins", plugins);
                msg.put("stores", stores);
                msg.put("versions", versions);
                main.post(() -> postBackground(msg));
            } catch (Exception e) {
                Log.w(TAG, "プラグインを登録できません", e);
            }
        });
    }

    /** 保存データが変わった。少し待ってまとめて登録し直す(次に開くページから新しい値になる)。 */
    private void storeChanged(@NonNull String id) {
        main.post(() -> {
            boolean first;
            synchronized (dirtyStores) {
                first = dirtyStores.isEmpty();
                dirtyStores.add(id);
            }
            if (first) main.postDelayed(this::pushStores, STORE_PUSH_DELAY_MS);
        });
    }

    private void pushStores() {
        List<String> ids;
        synchronized (dirtyStores) {
            ids = new ArrayList<>(dirtyStores);
            dirtyStores.clear();
        }
        io.execute(() -> {
            for (String id : ids) {
                try {
                    JSONObject msg = new JSONObject();
                    msg.put("type", "coaraStore");
                    msg.put("id", id);
                    msg.put("v", System.currentTimeMillis());
                    msg.put("store", GeckoScripts.storeOf(appContext, id));
                    main.post(() -> postBackground(msg));
                } catch (Exception e) {
                    Log.w(TAG, "保存データを送れません", e);
                }
            }
        });
    }

    /** 設定画面から保存データを消した時など。 */
    public void onStoreCleared(@NonNull String id) {
        storeChanged(id);
    }

    private void postBackground(@NonNull JSONObject msg) {
        WebExtension.Port port = background;
        if (port == null) return;
        try {
            port.postMessage(msg);
        } catch (Exception e) {
            Log.w(TAG, "組み込み拡張へ送れません", e);
        }
    }

    // ------------------------------------------------------------------
    // タブ
    // ------------------------------------------------------------------

    /** タブ(GeckoSession)ごとの受け口を作る。GeckoTabView から呼ぶ。 */
    @NonNull
    Tab tabFor(@NonNull GeckoSession session) {
        synchronized (tabs) {
            Tab t = tabs.get(session);
            if (t == null) {
                t = new Tab();
                tabs.put(session, t);
            }
            return t;
        }
    }

    @Nullable
    private Tab findTab(@Nullable GeckoSession session) {
        if (session == null) return null;
        synchronized (tabs) {
            return tabs.get(session);
        }
    }

    /** メニューのコマンド(プラグインの GM_registerMenuCommand)。 */
    public static final class Command {
        public final String pluginId;
        public final String commandId;
        public final String title;

        Command(String pluginId, String commandId, String title) {
            this.pluginId = pluginId;
            this.commandId = commandId;
            this.title = title;
        }
    }

    /** そのタブで今使えるコマンド(無効にしたプラグインの分は除く)。 */
    @NonNull
    public List<Command> commandsFor(@Nullable GeckoSession session) {
        List<Command> out = new ArrayList<>();
        Tab t = findTab(session);
        if (t == null) return out;
        synchronized (t.commands) {
            for (Map.Entry<String, String[]> e : t.commands.entrySet()) {
                PluginInfo p = manager.get(e.getValue()[0]);
                if (p != null && p.enabled) out.add(new Command(p.id, e.getKey(), e.getValue()[1]));
            }
        }
        return out;
    }

    /** コマンドを実行する(ページの上のフレームのプラグインへ送る)。 */
    public boolean runCommand(@Nullable GeckoSession session, @NonNull String commandId) {
        Tab t = findTab(session);
        if (t == null || t.topPort == null) return false;
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "cmd");
            msg.put("cid", commandId);
            t.topPort.postMessage(msg);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 1 つのタブで動いているプラグインの受け口。 */
    final class Tab implements PluginViewOps {
        /** コマンド id → {プラグイン id, 表示名}。ページを移るたびに空にする。 */
        final Map<String, String[]> commands = new LinkedHashMap<>();
        @Nullable WebExtension.Port topPort;

        final WebExtension.MessageDelegate delegate = new WebExtension.MessageDelegate() {
            @Override
            public void onConnect(@NonNull WebExtension.Port port) {
                port.setDelegate(new WebExtension.PortDelegate() {
                    @Override
                    public void onPortMessage(@NonNull Object message, @NonNull WebExtension.Port p) {
                        if (message instanceof JSONObject) handle((JSONObject) message, p);
                    }

                    @Override
                    public void onDisconnect(@NonNull WebExtension.Port p) {
                        if (topPort == p) topPort = null;
                    }
                });
            }
        };

        private void handle(@NonNull JSONObject m, @NonNull WebExtension.Port port) {
            String type = m.optString("type");
            if ("hello".equals(type)) {
                if (m.optBoolean("top")) {
                    // 新しいページ: 前のページのコマンドは使えない
                    topPort = port;
                    synchronized (commands) {
                        commands.clear();
                    }
                }
                return;
            }
            String id = m.optString("id");
            if (id.isEmpty()) return;
            if ("xhr".equals(type)) {
                startXhr(id, m.optString("key"), m.optString("rid"), m.optString("opts"), port);
                return;
            }
            if (!"op".equals(type)) return;
            String op = m.optString("op");
            String a = m.optString("a", "");
            String b = m.optString("b", "");
            String c = m.optString("c", "");
            io.execute(() -> {
                try {
                    manager.handleBridge(this, id, op, a, b, c);
                } catch (Throwable t) {
                    Log.w(TAG, "プラグインの処理に失敗しました: " + op, t);
                }
                if ("sset".equals(op) || "sdel".equals(op) || "sclear".equals(op)) storeChanged(id);
            });
        }

        private void startXhr(String id, String key, String rid, String opts, WebExtension.Port port) {
            io.execute(() -> {
                boolean started = manager.xhrStartAsync(id, rid, opts, result -> main.post(() -> {
                    JSONObject res;
                    try {
                        res = new JSONObject(result);
                    } catch (Exception e) {
                        res = new JSONObject();
                    }
                    sendXhr(port, key, rid, res);
                }));
                if (!started) {
                    main.post(() -> {
                        JSONObject res = new JSONObject();
                        try {
                            res.put("error", "request rejected (permission/limit)");
                        } catch (Exception ignored) {
                        }
                        sendXhr(port, key, rid, res);
                    });
                }
            });
        }

        private void sendXhr(WebExtension.Port port, String key, String rid, JSONObject res) {
            try {
                JSONObject msg = new JSONObject();
                msg.put("type", "xhr");
                msg.put("key", key);
                msg.put("rid", rid);
                msg.put("res", res);
                port.postMessage(msg);
            } catch (Exception ignored) {
                // ページを離れた後に終わった
            }
        }

        // ---- PluginViewOps(PluginManager.handleBridge から呼ばれる) ----

        @Override
        public void registerCommand(String pluginId, String cmdId, String title) {
            synchronized (commands) {
                if (commands.size() >= 32 && !commands.containsKey(cmdId)) return;
                commands.put(cmdId, new String[]{pluginId, title});
            }
        }

        @Override
        public void unregisterCommand(String pluginId, String cmdId) {
            synchronized (commands) {
                String[] cur = commands.get(cmdId);
                if (cur != null && cur[0].equals(pluginId)) commands.remove(cmdId);
            }
        }

        @Override
        public void openTab(String url) {
            main.post(() -> {
                Activity a = ExtensionManager.get(appContext).currentActivity();
                Context c = a != null ? a : appContext;
                Intent i = new Intent(c, MainActivity.class)
                        .putExtra("url", url)
                        .putExtra(MainActivity.EXTRA_URL_IN_NEW_TAB, true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                if (a == null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            });
        }

        @Override
        public void notifyUser(String title, String text) {
            main.post(() -> {
                String shown = text == null || text.isEmpty() ? title : title + ": " + text;
                Toast.makeText(appContext, shown, Toast.LENGTH_LONG).show();
            });
        }

        @Override
        public boolean copyText(String text) {
            main.post(() -> {
                try {
                    ClipboardManager cm = (ClipboardManager) appContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("plugin", text));
                } catch (Exception ignored) {
                }
            });
            return true;
        }
    }
}
