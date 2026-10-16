package com.airsky.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * AirSky Browser から別の端末の AirSky Browser へのデータの引っ越し(データの入れ物)。
 *
 * <p>引っ越しファイル(.airsky)は ZIP で、次のものを入れる。</p>
 * <ul>
 *   <li>{@code manifest.json}: 形式・送った端末・件数・送った端末のアプリのフォルダの場所</li>
 *   <li>{@code prefs/名前.json}: 設定(SharedPreferences)。タブ一覧・非アクティブなタブ・タブグループ・履歴も
 *       WebViewTabs などの設定に入っている。値の型ごと JSON にする</li>
 *   <li>{@code databases/}: ブックマーク・保存したページの一覧など</li>
 *   <li>{@code files/}: タブごとのページ遷移(tab_state_*.json)・タブの画像・保存したページ・メモなど</li>
 *   <li>{@code ext/PageScreenshots/}: 保存したページのスクリーンショット</li>
 * </ul>
 *
 * <p>壁紙・Python・Java の環境は大きく、端末ごとに入れ直すものなので入れない(画面で別に移すよう案内する)。
 * パスワード・自動入力(端末の鍵で暗号化している)・アプリのロック・初回起動の印も、受け取る端末のものを残す。</p>
 *
 * <p>受け取った側は、使っている最中のデータを書き換えないように、ファイルをいったん
 * {@link #stage 置いておき}、アプリを起動し直してすぐ({@link AozoraApplication#onCreate} の最初)に
 * {@link #applyPending} で入れ替える。</p>
 */
final class DeviceMigration {

    private static final String TAG = "DeviceMigration";
    static final String FORMAT = "airsky-device-migration";
    static final int FORMAT_VERSION = 1;
    static final String EXTENSION = ".airsky";

    /** 移さない設定のファイル(受け取る端末のものを残す)。 */
    private static final Set<String> KEEP_PREFS = new HashSet<>(Arrays.asList(
            "password_prefs",   // アプリのロックのパスワード(端末の鍵で暗号化)
            "autofill_prefs",   // パスワード マネージャー(端末の鍵で暗号化)
            "py_tool",          // Python のアプリ一覧
            "jar_tool"          // Java のアプリ一覧
    ));
    /** 移さない設定(ライブラリが使うもの)の名前の始まり。 */
    private static final String[] KEEP_PREFS_PREFIX = {
            "com.google", "androidx", "WebViewChromium", "org.mozilla", "com.android", "_has_set_default_values"
    };
    /** AppPrefs のうち、受け取る端末のものを残す設定(ロック・初回起動など)。 */
    private static final Set<String> KEEP_APP_KEYS = new HashSet<>(Arrays.asList(
            "startup_password", "requirePasswordOnFirstLaunch", "usePasswordSkip",
            "password_skip_minutes", "password_skip_minutes_backup", "password_skip_until",
            "password_lock_until", "password_lock_minutes", "password_failed_count",
            BrowserPrefs.LOCK_SECRET, "use_biometric",
            "isFirstRun_v2", "isFirstMov", "isFirstLaunch", "termsAcceptedVersion",
            "rebootApp", AozoraTransfer.KEY_DONE
    ));
    /** files/ の中で移さないもの(一番上の名前)。壁紙・Python・Java・エンジンのプロファイルなど。 */
    private static final Set<String> KEEP_FILES = new HashSet<>(Arrays.asList(
            "mozilla", "python", "python-home", "python-shims", "python.installing", "pyapps", "py_running",
            "jars", "jvm", "java", "jvm_running",
            "start_page_wallpaper.jpg", "start_page_wallpaper.jpg.tmp",
            "profileInstalled", "profileinstaller_profileWrittenFor_lastUpdateTime.dat",
            "PersistedInstallation", "generatefid.lock"
    ));
    /** 移さないデータベースの名前の始まり(ライブラリが使うもの)。 */
    private static final String[] KEEP_DB_PREFIX = {"androidx", "google", "com.google", "firebase", "webview"};

    /** 受け取ったファイルを置いておく場所(files/ を入れ替えても消えない no_backup の中)。 */
    private static final String STAGE_DIR = "device_migration";
    private static final String STAGE_FILE = "pending.airsky";
    private static final String STAGE_READY = "ready";
    private static final String RESULT_FILE = "result.json";

    /** 読み込む大きさの上限(壊れたファイルで端末を埋めないように)。 */
    static final long MAX_SIZE = 4L * 1024 * 1024 * 1024;

    private DeviceMigration() {}

    /** 進み具合。 */
    interface Progress {
        void onProgress(long done, long total);
    }

    /** 引っ越しの中身の件数など(manifest.json に入れ、受け取る側の確認画面に出す)。 */
    static final class Summary {
        String device = "";
        String appVersion = "";
        long created;
        int tabs;
        int inactiveTabs;
        int tabGroups;
        int history;
        int bookmarks;
        int savedPages;
        int settings;
        int tabStates;
        int notes;
        /** 引っ越しファイルの大きさ(バイト)。 */
        long bytes;

        @NonNull
        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("device", device);
                o.put("appVersion", appVersion);
                o.put("created", created);
                o.put("tabs", tabs);
                o.put("inactiveTabs", inactiveTabs);
                o.put("tabGroups", tabGroups);
                o.put("history", history);
                o.put("bookmarks", bookmarks);
                o.put("savedPages", savedPages);
                o.put("settings", settings);
                o.put("tabStates", tabStates);
                o.put("notes", notes);
                o.put("bytes", bytes);
            } catch (Exception ignored) {
            }
            return o;
        }

        @NonNull
        static Summary fromJson(@Nullable JSONObject o) {
            Summary s = new Summary();
            if (o == null) return s;
            s.device = o.optString("device", "");
            s.appVersion = o.optString("appVersion", "");
            s.created = o.optLong("created");
            s.tabs = o.optInt("tabs");
            s.inactiveTabs = o.optInt("inactiveTabs");
            s.tabGroups = o.optInt("tabGroups");
            s.history = o.optInt("history");
            s.bookmarks = o.optInt("bookmarks");
            s.savedPages = o.optInt("savedPages");
            s.settings = o.optInt("settings");
            s.tabStates = o.optInt("tabStates");
            s.notes = o.optInt("notes");
            s.bytes = o.optLong("bytes");
            return s;
        }
    }

    static final class MigrationException extends Exception {
        MigrationException(@NonNull String message) {
            super(message);
        }

        MigrationException(@NonNull String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    /** この端末の名前(「Pixel 9」など)。 */
    @NonNull
    static String deviceName() {
        String model = Build.MODEL != null ? Build.MODEL : "Android";
        String maker = Build.MANUFACTURER != null ? Build.MANUFACTURER : "";
        if (!maker.isEmpty() && !model.toLowerCase(java.util.Locale.ROOT).startsWith(maker.toLowerCase(java.util.Locale.ROOT))) {
            maker = Character.toUpperCase(maker.charAt(0)) + maker.substring(1);
            return maker + " " + model;
        }
        return model;
    }

    // ------------------------------------------------------------------
    // 書き出し
    // ------------------------------------------------------------------

    /** 今のデータの件数(書き出す前に画面に出す)。 */
    @NonNull
    static Summary count(@NonNull Context context) {
        Summary s = new Summary();
        s.device = deviceName();
        s.appVersion = appVersion(context);
        s.created = System.currentTimeMillis();
        SharedPreferences web = context.getSharedPreferences("WebViewTabs", Context.MODE_PRIVATE);
        JSONArray tabs = jsonArray(web.getString("saved_tabs", null));
        s.tabs = tabs.length();
        int days = InactiveTabs.days(context);
        if (days > 0) {
            long limit = System.currentTimeMillis() - days * InactiveTabs.DAY_MS;
            for (int i = 0; i < tabs.length(); i++) {
                JSONObject t = tabs.optJSONObject(i);
                if (t != null && t.optLong("used", Long.MAX_VALUE) < limit) s.inactiveTabs++;
            }
        }
        s.tabGroups = jsonArray(web.getString("tab_groups", null)).length();
        s.history = jsonArray(web.getString("history", null)).length();
        s.bookmarks = countRows(context, "bookmarks_v2.db", "SELECT COUNT(*) FROM nodes WHERE folder = 0");
        s.savedPages = countRows(context, "saved_pages.db", "SELECT COUNT(*) FROM pages");
        for (String name : prefNames(context)) {
            s.settings += context.getSharedPreferences(name, Context.MODE_PRIVATE).getAll().size();
        }
        File[] files = context.getFilesDir().listFiles();
        if (files != null) {
            for (File f : files) if (f.getName().startsWith("tab_state_")) s.tabStates++;
        }
        File notes = new File(new File(context.getFilesDir(), "notes"), "notes.json");
        if (notes.isFile()) {
            try {
                String text = readText(notes);
                Object v = new org.json.JSONTokener(text).nextValue();
                if (v instanceof JSONArray) s.notes = ((JSONArray) v).length();
                else if (v instanceof JSONObject) s.notes = Math.max(0, jsonArray(((JSONObject) v).optString("notes")).length());
            } catch (Exception ignored) {
            }
        }
        return s;
    }

    /**
     * データを引っ越しファイルに書き出す。
     *
     * @return 書き出した中身の件数(bytes にファイルの大きさが入る)
     */
    @NonNull
    static Summary export(@NonNull Context context, @NonNull File out, @Nullable Progress progress)
            throws IOException {
        Summary summary = count(context);
        File filesDir = context.getFilesDir();

        // 入れるファイルを先に集めて、全体の大きさを出す(進み具合のため)
        Map<String, File> entries = new LinkedHashMap<>();
        File dbDir = context.getDatabasePath("x").getParentFile();
        if (dbDir != null) {
            checkpointDatabases(dbDir);
            File[] dbs = dbDir.listFiles();
            if (dbs != null) {
                for (File f : dbs) {
                    String n = f.getName();
                    if (!f.isFile() || !n.endsWith(".db") || isKeptDatabase(n)) continue;
                    entries.put("databases/" + n, f);
                }
            }
        }
        File[] top = filesDir.listFiles();
        if (top != null) {
            for (File f : top) {
                if (KEEP_FILES.contains(f.getName()) || f.getName().endsWith(".tmp")) continue;
                collect(f, "files/" + f.getName(), entries);
            }
        }
        File shots = screenshotDir(context);
        if (shots != null && shots.isDirectory()) collect(shots, "ext/PageScreenshots", entries);

        long total = 0;
        for (File f : entries.values()) total += f.length();
        long done = 0;

        JSONObject manifest = new JSONObject();
        try {
            manifest.put("format", FORMAT);
            manifest.put("version", FORMAT_VERSION);
            manifest.put("summary", summary.toJson());
            JSONObject paths = new JSONObject();
            paths.put("data", context.getDataDir().getAbsolutePath());
            paths.put("files", filesDir.getAbsolutePath());
            if (shots != null) paths.put("screenshots", shots.getAbsolutePath());
            File ext = context.getExternalFilesDir(null);
            if (ext != null) paths.put("external", ext.getAbsolutePath());
            manifest.put("paths", paths);
        } catch (Exception ignored) {
        }

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            putText(zip, "manifest.json", manifest.toString());
            // 設定はメモリ上の今の値を書く(apply() でまだファイルに書かれていない分も入る)
            for (String name : prefNames(context)) {
                Map<String, ?> all = context.getSharedPreferences(name, Context.MODE_PRIVATE).getAll();
                putText(zip, "prefs/" + name + ".json", prefsToJson(name, all).toString());
            }
            byte[] buf = new byte[64 * 1024];
            for (Map.Entry<String, File> e : entries.entrySet()) {
                File f = e.getValue();
                if (!f.isFile()) continue; // 書き出している間に消えた
                zip.putNextEntry(new ZipEntry(e.getKey()));
                try (InputStream in = new FileInputStream(f)) {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        zip.write(buf, 0, n);
                        done += n;
                        if (progress != null) progress.onProgress(Math.min(done, total), total);
                    }
                } catch (IOException ex) {
                    // 読めないファイル(書き換え中など)は飛ばす
                    Log.w(TAG, "skip " + e.getKey(), ex);
                }
                zip.closeEntry();
            }
        }
        summary.bytes = out.length();
        if (progress != null) progress.onProgress(total, total);
        return summary;
    }

    /** 移す設定のファイルの名前(拡張子なし)。 */
    @NonNull
    private static List<String> prefNames(@NonNull Context context) {
        List<String> names = new ArrayList<>();
        File[] files = new File(context.getDataDir(), "shared_prefs").listFiles();
        if (files == null) return names;
        for (File f : files) {
            String n = f.getName();
            if (!n.endsWith(".xml")) continue;
            n = n.substring(0, n.length() - 4);
            if (isKeptPrefs(n)) continue;
            names.add(n);
        }
        return names;
    }

    private static boolean isKeptPrefs(@NonNull String name) {
        if (KEEP_PREFS.contains(name)) return true;
        for (String p : KEEP_PREFS_PREFIX) if (name.startsWith(p)) return true;
        return false;
    }

    private static boolean isKeptDatabase(@NonNull String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String p : KEEP_DB_PREFIX) if (lower.startsWith(p)) return true;
        return false;
    }

    @NonNull
    private static JSONObject prefsToJson(@NonNull String name, @NonNull Map<String, ?> all) {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            if ("AppPrefs".equals(name) && KEEP_APP_KEYS.contains(e.getKey())) continue;
            Object v = e.getValue();
            try {
                JSONObject typed = new JSONObject();
                if (v instanceof String) {
                    typed.put("t", "s").put("v", v);
                } else if (v instanceof Boolean) {
                    typed.put("t", "b").put("v", v);
                } else if (v instanceof Integer) {
                    typed.put("t", "i").put("v", v);
                } else if (v instanceof Long) {
                    typed.put("t", "l").put("v", v);
                } else if (v instanceof Float) {
                    typed.put("t", "f").put("v", String.valueOf(v));
                } else if (v instanceof Set) {
                    JSONArray a = new JSONArray();
                    for (Object s : (Set<?>) v) a.put(String.valueOf(s));
                    typed.put("t", "set").put("v", a);
                } else {
                    continue;
                }
                o.put(e.getKey(), typed);
            } catch (Exception ignored) {
            }
        }
        return o;
    }

    /** WAL の中身をデータベースのファイルに書き戻す(.db だけをコピーしても最新になるように)。 */
    private static void checkpointDatabases(@NonNull File dbDir) {
        File[] dbs = dbDir.listFiles();
        if (dbs == null) return;
        for (File f : dbs) {
            if (!f.getName().endsWith(".db") || !new File(f.getPath() + "-wal").exists()) continue;
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(f.getPath(), null, SQLiteDatabase.OPEN_READWRITE)) {
                try (Cursor c = db.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
                    c.moveToFirst();
                }
            } catch (Exception e) {
                Log.w(TAG, "checkpoint " + f.getName(), e);
            }
        }
    }

    private static void collect(@NonNull File f, @NonNull String path, @NonNull Map<String, File> out) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children == null) return;
            for (File c : children) collect(c, path + "/" + c.getName(), out);
        } else if (f.isFile()) {
            out.put(path, f);
        }
    }

    @Nullable
    private static File screenshotDir(@NonNull Context context) {
        File pics = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return pics != null ? new File(pics, "PageScreenshots") : null;
    }

    // ------------------------------------------------------------------
    // 受け取った引っ越しファイルを調べる
    // ------------------------------------------------------------------

    /** 引っ越しファイルの中身を確かめて、件数を返す。AirSky の引っ越しファイルでなければ例外。 */
    @NonNull
    static Summary inspect(@NonNull File file) throws MigrationException {
        if (file.length() > MAX_SIZE) throw new MigrationException(Lang.t("引っ越しファイルが大きすぎます。"));
        try (ZipFile zip = new ZipFile(file)) {
            JSONObject manifest = readManifest(zip);
            Summary s = Summary.fromJson(manifest.optJSONObject("summary"));
            s.bytes = file.length();
            return s;
        } catch (MigrationException e) {
            throw e;
        } catch (Exception e) {
            throw new MigrationException(Lang.t("引っ越しファイルを読み込めませんでした。ファイルが壊れている可能性があります。"), e);
        }
    }

    @NonNull
    private static JSONObject readManifest(@NonNull ZipFile zip) throws Exception {
        ZipEntry m = zip.getEntry("manifest.json");
        if (m == null) throw new MigrationException(Lang.t("AirSky Browser の引っ越しファイルではありません。"));
        JSONObject manifest;
        try (InputStream in = zip.getInputStream(m)) {
            manifest = new JSONObject(new String(readAll(in, 1024 * 1024), StandardCharsets.UTF_8));
        }
        if (!FORMAT.equals(manifest.optString("format"))) {
            throw new MigrationException(Lang.t("AirSky Browser の引っ越しファイルではありません。"));
        }
        if (manifest.optInt("version") > FORMAT_VERSION) {
            throw new MigrationException(Lang.t("新しい版の AirSky Browser で作られた引っ越しファイルです。この端末の AirSky Browser を更新してから、もう一度お試しください。"));
        }
        return manifest;
    }

    // ------------------------------------------------------------------
    // 受け取った引っ越しファイルを反映する
    // ------------------------------------------------------------------

    @NonNull
    private static File stageDir(@NonNull Context context) {
        return new File(context.getNoBackupFilesDir(), STAGE_DIR);
    }

    /**
     * 確かめた引っ越しファイルを置いておき、次の起動で反映するように印を付ける。
     * この後でアプリを起動し直す。
     */
    static void stage(@NonNull Context context, @NonNull File received) throws IOException {
        File dir = stageDir(context);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File target = new File(dir, STAGE_FILE);
        new File(dir, RESULT_FILE).delete();
        if (!received.renameTo(target)) {
            try (InputStream in = new FileInputStream(received); OutputStream os = new FileOutputStream(target)) {
                copy(in, os);
            }
            //noinspection ResultOfMethodCallIgnored
            received.delete();
        }
        try (FileOutputStream os = new FileOutputStream(new File(dir, STAGE_READY))) {
            os.write('1');
            os.getFD().sync();
        }
    }

    /**
     * 置いておいた引っ越しファイルがあれば反映する。アプリの起動直後(まだ設定やデータベースを
     * 開いていない時)に呼ぶ。結果は {@link #takeResult} で受け取る。
     */
    static void applyPending(@NonNull Context context) {
        File dir = stageDir(context);
        File ready = new File(dir, STAGE_READY);
        File file = new File(dir, STAGE_FILE);
        if (!ready.isFile()) return;
        //noinspection ResultOfMethodCallIgnored
        ready.delete(); // 失敗して落ちても、起動のたびにやり直さないように先に消す
        JSONObject result = new JSONObject();
        try {
            Summary s = apply(context, file);
            result.put("ok", true);
            result.put("summary", s.toJson());
        } catch (Throwable e) {
            Log.e(TAG, "apply failed", e);
            try {
                result.put("ok", false);
                result.put("error", e instanceof MigrationException ? e.getMessage()
                        : Lang.t("データを反映できませんでした。") + " (" + e.getClass().getSimpleName() + ")");
            } catch (Exception ignored) {
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        try (OutputStream os = new FileOutputStream(new File(dir, RESULT_FILE))) {
            os.write(result.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    /** 前の起動で反映した結果(無ければ null)。読んだら消す。 */
    @Nullable
    static JSONObject takeResult(@NonNull Context context) {
        File f = new File(stageDir(context), RESULT_FILE);
        if (!f.isFile()) return null;
        try {
            return new JSONObject(readText(f));
        } catch (Exception e) {
            return null;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    @NonNull
    private static Summary apply(@NonNull Context context, @NonNull File file) throws Exception {
        if (!file.isFile()) throw new MigrationException(Lang.t("引っ越しファイルが見つかりません。"));
        try (ZipFile zip = new ZipFile(file)) {
            JSONObject manifest = readManifest(zip);
            Summary summary = Summary.fromJson(manifest.optJSONObject("summary"));
            JSONObject paths = manifest.optJSONObject("paths");

            // 送った端末のフォルダの場所 → この端末の場所(保存したページの場所などが入っている)
            Map<String, String> rewrite = new LinkedHashMap<>();
            if (paths != null) {
                File shots = screenshotDir(context);
                addRewrite(rewrite, paths.optString("screenshots"), shots != null ? shots.getAbsolutePath() : null);
                File ext = context.getExternalFilesDir(null);
                addRewrite(rewrite, paths.optString("external"), ext != null ? ext.getAbsolutePath() : null);
                addRewrite(rewrite, paths.optString("files"), context.getFilesDir().getAbsolutePath());
                addRewrite(rewrite, paths.optString("data"), context.getDataDir().getAbsolutePath());
            }

            // 1. 今のデータ(移す種類のもの)を消す
            File filesDir = context.getFilesDir();
            File[] top = filesDir.listFiles();
            if (top != null) {
                for (File f : top) if (!KEEP_FILES.contains(f.getName())) deleteRecursively(f);
            }
            File dbDir = context.getDatabasePath("x").getParentFile();
            if (dbDir != null) {
                File[] dbs = dbDir.listFiles();
                if (dbs != null) {
                    for (File f : dbs) if (!isKeptDatabase(f.getName())) deleteRecursively(f);
                }
                //noinspection ResultOfMethodCallIgnored
                dbDir.mkdirs();
            }
            File shots = screenshotDir(context);
            if (shots != null) deleteRecursively(shots);

            // 2. ファイルを取り出す
            Set<String> seenPrefs = new HashSet<>();
            byte[] buf = new byte[64 * 1024];
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (name.contains("..") || name.startsWith("/") || name.contains("\\")) continue;
                File target = null;
                if (name.startsWith("files/")) {
                    String rel = name.substring(6);
                    String first = rel.contains("/") ? rel.substring(0, rel.indexOf('/')) : rel;
                    if (KEEP_FILES.contains(first)) continue;
                    target = new File(filesDir, rel);
                } else if (name.startsWith("databases/") && dbDir != null) {
                    String rel = name.substring(10);
                    if (rel.contains("/") || isKeptDatabase(rel)) continue;
                    target = new File(dbDir, rel);
                } else if (name.startsWith("ext/PageScreenshots/") && shots != null) {
                    target = new File(shots, name.substring("ext/PageScreenshots/".length()));
                } else if (name.startsWith("prefs/") && name.endsWith(".json")) {
                    String prefName = name.substring(6, name.length() - 5);
                    if (prefName.contains("/") || isKeptPrefs(prefName)) continue;
                    try (InputStream in = zip.getInputStream(e)) {
                        String json = new String(readAll(in, 64L * 1024 * 1024), StandardCharsets.UTF_8);
                        applyPrefs(context, prefName, new JSONObject(rewriteText(json, rewrite, true)));
                    }
                    seenPrefs.add(prefName);
                    continue;
                }
                if (target == null) continue;
                File parent = target.getParentFile();
                if (parent != null) //noinspection ResultOfMethodCallIgnored
                    parent.mkdirs();
                try (InputStream in = zip.getInputStream(e); OutputStream os = new FileOutputStream(target)) {
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
            }

            // 3. 送った端末に無かった設定は空にする(引っ越し前の値が残らないように)
            File[] prefs = new File(context.getDataDir(), "shared_prefs").listFiles();
            if (prefs != null) {
                for (File f : prefs) {
                    String n = f.getName();
                    if (!n.endsWith(".xml")) continue;
                    n = n.substring(0, n.length() - 4);
                    if (seenPrefs.contains(n) || isKeptPrefs(n)) continue;
                    context.getSharedPreferences(n, Context.MODE_PRIVATE).edit().clear().commit();
                }
            }

            // 4. データベースの中のフォルダの場所を、この端末の場所に直す
            if (dbDir != null && !rewrite.isEmpty()) {
                File[] dbs = dbDir.listFiles();
                if (dbs != null) {
                    for (File f : dbs) {
                        if (f.getName().endsWith(".db") && !isKeptDatabase(f.getName())) rewriteDatabase(f, rewrite);
                    }
                }
            }
            return summary;
        }
    }

    private static void addRewrite(@NonNull Map<String, String> map, @Nullable String from, @Nullable String to) {
        if (from == null || from.isEmpty() || to == null || from.equals(to)) return;
        map.put(from, to);
        // /data/user/0/... と /data/data/... の 2 通りで書かれていることがある
        if (from.startsWith("/data/user/0/")) map.put("/data/data/" + from.substring(13), to);
        else if (from.startsWith("/data/data/")) map.put("/data/user/0/" + from.substring(11), to);
    }

    @NonNull
    private static String rewriteText(@NonNull String text, @NonNull Map<String, String> rewrite, boolean jsonEscaped) {
        for (Map.Entry<String, String> e : rewrite.entrySet()) {
            String from = e.getKey();
            String to = e.getValue();
            text = text.replace(from, to);
            // JSON の中では / が \/ になっていることがある(設定の中の JSON 文字列)
            if (jsonEscaped) {
                text = text.replace(from.replace("/", "\\/"), to.replace("/", "\\/"));
                text = text.replace(from.replace("/", "\\\\\\/"), to.replace("/", "\\\\\\/"));
            }
        }
        return text;
    }

    private static void applyPrefs(@NonNull Context context, @NonNull String name, @NonNull JSONObject values) {
        SharedPreferences prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit();
        Map<String, ?> keep = null;
        if ("AppPrefs".equals(name)) {
            keep = new LinkedHashMap<>(prefs.getAll());
            keep.keySet().retainAll(KEEP_APP_KEYS);
        }
        ed.clear();
        Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (keep != null && KEEP_APP_KEYS.contains(k)) continue;
            JSONObject typed = values.optJSONObject(k);
            if (typed == null) continue;
            try {
                switch (typed.optString("t")) {
                    case "s": ed.putString(k, typed.getString("v")); break;
                    case "b": ed.putBoolean(k, typed.getBoolean("v")); break;
                    case "i": ed.putInt(k, typed.getInt("v")); break;
                    case "l": ed.putLong(k, typed.getLong("v")); break;
                    case "f": ed.putFloat(k, Float.parseFloat(typed.getString("v"))); break;
                    case "set": {
                        JSONArray a = typed.getJSONArray("v");
                        Set<String> set = new HashSet<>();
                        for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
                        ed.putStringSet(k, set);
                        break;
                    }
                    default: break;
                }
            } catch (Exception ignored) {
            }
        }
        if (keep != null) {
            for (Map.Entry<String, ?> e : keep.entrySet()) {
                Object v = e.getValue();
                if (v instanceof Boolean) ed.putBoolean(e.getKey(), (Boolean) v);
                else if (v instanceof Integer) ed.putInt(e.getKey(), (Integer) v);
                else if (v instanceof Long) ed.putLong(e.getKey(), (Long) v);
                else if (v instanceof Float) ed.putFloat(e.getKey(), (Float) v);
                else if (v instanceof String) ed.putString(e.getKey(), (String) v);
            }
        }
        ed.commit();
    }

    /** データベースの文字の列にある、送った端末のフォルダの場所を書き換える。 */
    private static void rewriteDatabase(@NonNull File file, @NonNull Map<String, String> rewrite) {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            List<String> tables = new ArrayList<>();
            try (Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' "
                    + "AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'", null)) {
                while (c.moveToNext()) tables.add(c.getString(0));
            }
            for (String t : tables) {
                List<String> cols = new ArrayList<>();
                try (Cursor c = db.rawQuery("PRAGMA table_info(\"" + t.replace("\"", "\"\"") + "\")", null)) {
                    while (c.moveToNext()) {
                        String type = c.getString(2);
                        if (type == null || type.isEmpty() || type.toUpperCase(java.util.Locale.ROOT).contains("TEXT")) {
                            cols.add(c.getString(1));
                        }
                    }
                }
                for (String col : cols) {
                    String q = "\"" + col.replace("\"", "\"\"") + "\"";
                    for (Map.Entry<String, String> e : rewrite.entrySet()) {
                        db.execSQL("UPDATE \"" + t.replace("\"", "\"\"") + "\" SET " + q + " = REPLACE(" + q + ", ?, ?) "
                                + "WHERE instr(" + q + ", ?) > 0", new Object[]{e.getKey(), e.getValue(), e.getKey()});
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "rewrite " + file.getName(), e);
        }
    }

    // ------------------------------------------------------------------

    @NonNull
    static String appVersion(@NonNull Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private static int countRows(@NonNull Context context, @NonNull String db, @NonNull String sql) {
        File f = context.getDatabasePath(db);
        if (!f.isFile()) return 0;
        try (SQLiteDatabase d = SQLiteDatabase.openDatabase(f.getPath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor c = d.rawQuery(sql, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @NonNull
    private static JSONArray jsonArray(@Nullable String json) {
        if (json == null || json.isEmpty()) return new JSONArray();
        try {
            return new JSONArray(json);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void putText(@NonNull ZipOutputStream zip, @NonNull String name, @NonNull String text)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    @NonNull
    private static byte[] readAll(@NonNull InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        long total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > limit) throw new IOException("too large");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    @NonNull
    static String readText(@NonNull File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return new String(readAll(in, 64L * 1024 * 1024), StandardCharsets.UTF_8);
        }
    }

    static void copy(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    static void deleteRecursively(@NonNull File f) {
        File[] children = f.isDirectory() ? f.listFiles() : null;
        if (children != null) for (File c : children) deleteRecursively(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
