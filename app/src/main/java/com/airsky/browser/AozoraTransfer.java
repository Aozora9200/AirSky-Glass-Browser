package com.airsky.browser;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Aozora(前のブラウザ)から AirSky へのデータの引っ越し。
 *
 * <p>Aozora は「ダウンロード/Aozora/.Aozora-西暦.az」(隠しファイル)にデータを書き出してから、
 * Intent の extra {@code Aozora_to_AirSky = true} を付けて AirSky を起動する。.az の中身は ZIP で、
 * Aozora のアプリ領域の {@code shared_prefs/*.xml} と {@code databases/*.db} をそのまま入れたもの
 * (詳しくは docs/aozora-transfer-format.md)。タブの戻る/進む履歴は {@code tab_history.json} に、
 * 保存したページのファイルは {@code saved_pages/} に入っている。</p>
 *
 * <p>AirSky は Aozora から作り直したブラウザなので、設定の名前の多くは同じ。ここでは AirSky でも
 * 同じ意味で使っている設定・ブックマーク・履歴・タブ・ダウンロード履歴・位置情報の許可だけを移し、
 * パスワード(アプリのロック)や初回起動のフラグなどは移さない。</p>
 */
final class AozoraTransfer {

    /** Aozora が AirSky を起動する時に付ける extra。 */
    static final String EXTRA_FROM_AOZORA = "Aozora_to_AirSky";
    /** 引っ越しを済ませた(もう一度聞かない)。 */
    static final String KEY_DONE = "aozoraTransferDone";

    static final String MANIFEST_FORMAT = "aozora-airsky-transfer";
    private static final Pattern FILE_NAME = Pattern.compile("^\\.Aozora-(\\d{4,8})\\.az$");
    /** 読み込む大きさの上限(壊れたファイルで端末を埋めないように)。 */
    private static final long MAX_ENTRY_BYTES = 64L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 256L * 1024 * 1024;
    /** 保存したページのファイルの合計の上限(超えた分は移さないだけで、引っ越しは続ける)。 */
    private static final long MAX_SAVED_PAGE_BYTES = 512L * 1024 * 1024;
    /** Gecko のセッション状態で、ページを開いた主体を「システム」にする値。 */
    private static final String SYSTEM_PRINCIPAL = "{\"3\":{}}";

    /** 移す AppPrefs の設定(AirSky でも同じ意味・同じ型で使っているもの)。 */
    private static final String[] APP_PREF_KEYS = {
            "ProgressBarAnimation", "Vibrate", "acceptCookies", "batteryalert", "bootScreen", "bootSound",
            "geoLocation", "hidebottom", "intentJump", "isSwipeReload", "javaScript", "popupBlock",
            "securityAlert", "touchEffect", "updateDialog", "useSecretSecurity", "zoomButton",
    };
    /** 移す AdvancedBrowserPrefs の設定(ダウンロード履歴は別に扱う)。 */
    private static final String[] ADVANCED_PREF_KEYS = {
            "dark_mode", "deskua_enabled", "ua_enabled", "ct3ua_enabled", "img_block_enabled",
            "js_enabled", "zoom_enabled", "basic_auth",
    };

    /** 引っ越しの結果(何をいくつ移したか)。 */
    static final class Result {
        int settings;
        int bookmarks;
        int history;
        int tabs;
        int downloads;
        int sites;
        int savedPages;

        int total() {
            return settings + bookmarks + history + tabs + downloads + sites + savedPages;
        }
    }

    /** 引っ越しに失敗した理由(画面に出す)。 */
    static final class TransferException extends Exception {
        /** 失敗する前に、既存のデータを消していた(入れ替えの引っ越し)。 */
        boolean dataCleared;

        TransferException(@NonNull String message) {
            super(message);
        }

        TransferException(@NonNull String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    private AozoraTransfer() {
    }

    // ------------------------------------------------------------------
    // ファイルを探す
    // ------------------------------------------------------------------

    /** 「ダウンロード/Aozora」。 */
    @NonNull
    static File folder() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Aozora");
    }

    /** いちばん新しい .Aozora-西暦.az。無い・読めない時は null。 */
    @Nullable
    static File findFile() {
        File[] files = folder().listFiles();
        if (files == null) return null;
        File best = null;
        long bestYear = -1;
        for (File f : files) {
            Matcher m = FILE_NAME.matcher(f.getName());
            if (!f.isFile() || !m.matches()) continue;
            long year = Long.parseLong(m.group(1));
            if (best == null || year > bestYear || (year == bestYear && f.lastModified() > best.lastModified())) {
                best = f;
                bestYear = year;
            }
        }
        return best;
    }

    /** ファイル名が .Aozora-西暦.az の形か(ファイルを選んでもらった時の確認)。 */
    static boolean looksLikeTransferFile(@Nullable String name) {
        return name != null && FILE_NAME.matcher(name).matches();
    }

    // ------------------------------------------------------------------
    // 読み込んで反映する
    // ------------------------------------------------------------------

    /** .az(ZIP)を読み、AirSky の設定・ブックマークなどに反映する。 */
    @NonNull
    static Result importFrom(@NonNull Context context, @NonNull InputStream in) throws TransferException {
        return importFrom(context, in, false);
    }

    /**
     * .az(ZIP)を読み、AirSky に反映する。
     *
     * @param replaceExisting 既存のデータをすべて消してから反映する(初回起動でない時の引っ越し)。
     *                        消すのは、ファイルを読み終えて中身を確かめた後だけ(壊れたファイルでデータを失わない)
     */
    @NonNull
    static Result importFrom(@NonNull Context context, @NonNull InputStream in, boolean replaceExisting)
            throws TransferException {
        Archive a = readArchive(context, in);
        try {
            if (a.prefs.isEmpty() && a.databases.isEmpty()) {
                throw new TransferException(Lang.t("Aozora のデータが入っていません。"));
            }
            if (!replaceExisting) return apply(context, a);
            clearExistingData(context);
            try {
                return apply(context, a);
            } catch (TransferException e) {
                e.dataCleared = true;
                throw e;
            }
        } finally {
            for (File f : a.databases.values()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
            for (File f : a.savedPageFiles.values()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    /** ZIP の中身。設定は名前 → 値、データベースは名前 → 一時ファイル。 */
    private static final class Archive {
        final Map<String, Map<String, Object>> prefs = new HashMap<>();
        final Map<String, File> databases = new HashMap<>();
        /** 保存したページのファイル名 → 一時ファイル。 */
        final Map<String, File> savedPageFiles = new HashMap<>();
        /** タブの id(Aozora の番号)→ 戻る/進む履歴。 */
        @Nullable JSONObject tabHistory;
        @Nullable JSONObject manifest;
    }

    @NonNull
    private static Archive readArchive(@NonNull Context context, @NonNull InputStream in) throws TransferException {
        Archive a = new Archive();
        File tmpDir = new File(context.getCacheDir(), "aozora_transfer");
        //noinspection ResultOfMethodCallIgnored
        tmpDir.mkdirs();
        long total = 0;
        long savedPageBytes = 0;
        boolean anyEntry = false;
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                anyEntry = true;
                if (e.isDirectory()) continue;
                String name = e.getName().replace('\\', '/');
                String base = name.substring(name.lastIndexOf('/') + 1);
                String parent = parentFolder(name);
                if ("manifest.json".equals(name)) {
                    byte[] data = readLimited(zip);
                    total += data.length;
                    try {
                        a.manifest = new JSONObject(new String(data, StandardCharsets.UTF_8));
                    } catch (Exception ex) {
                        throw new TransferException(Lang.t("引っ越しファイルの情報が読めません。"), ex);
                    }
                } else if ("shared_prefs".equals(parent) && base.endsWith(".xml")) {
                    byte[] data = readLimited(zip);
                    total += data.length;
                    String prefName = base.substring(0, base.length() - 4);
                    a.prefs.put(prefName, parsePrefs(new String(data, StandardCharsets.UTF_8)));
                } else if ("databases".equals(parent) && isDatabaseName(base)) {
                    // 名前はこちらで決めた形だけを使う(ZIP の中のパスでは書き出さない)
                    File out = new File(tmpDir, "db_" + base);
                    total += copyLimited(zip, out);
                    a.databases.put(base, out);
                } else if ("tab_history.json".equals(name)) {
                    byte[] data = readLimited(zip);
                    total += data.length;
                    try {
                        a.tabHistory = new JSONObject(new String(data, StandardCharsets.UTF_8));
                    } catch (Exception ex) {
                        // 読めなければ、タブは URL だけで引っ越す
                    }
                } else if ("saved_pages".equals(parent) && isSavedPageFileName(base)
                        && savedPageBytes < MAX_SAVED_PAGE_BYTES) {
                    File out = new File(tmpDir, "sp_" + base);
                    long size = copyOrSkip(zip, out);
                    if (size >= 0) {
                        savedPageBytes += size;
                        a.savedPageFiles.put(base, out);
                    }
                }
                if (total > MAX_TOTAL_BYTES) throw new TransferException(Lang.t("引っ越しファイルが大きすぎます。"));
            }
        } catch (TransferException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new TransferException(Lang.t("引っ越しファイルを読み込めませんでした。"), ex);
        }
        if (!anyEntry) throw new TransferException(Lang.t("引っ越しファイルの形式が正しくありません。"));
        if (a.manifest != null) {
            String format = a.manifest.optString("format", MANIFEST_FORMAT);
            if (!MANIFEST_FORMAT.equals(format)) {
                throw new TransferException(Lang.t("引っ越しファイルの形式が正しくありません。"));
            }
        }
        return a;
    }

    /** "x/shared_prefs/AppPrefs.xml" → "shared_prefs"(アプリ領域ごと入れた ZIP でも読めるように)。 */
    @Nullable
    private static String parentFolder(@NonNull String path) {
        int slash = path.lastIndexOf('/');
        if (slash <= 0) return null;
        String dir = path.substring(0, slash);
        return dir.substring(dir.lastIndexOf('/') + 1);
    }

    private static boolean isDatabaseName(@NonNull String name) {
        return name.matches("[A-Za-z0-9_.-]+\\.db");
    }

    /** 保存したページ(.mht / .html)とスクリーンショットの画像。 */
    private static boolean isSavedPageFileName(@NonNull String name) {
        return name.matches("[A-Za-z0-9_.-]+\\.(mht|mhtml|html|png|jpg|jpeg|webp)");
    }

    /** 1 ファイルの上限を超えたら、途中まで書いたファイルを消して -1 を返す(引っ越しは止めない)。 */
    private static long copyOrSkip(@NonNull InputStream in, @NonNull File out) throws IOException {
        try {
            return copyLimited(in, out);
        } catch (TransferException e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return -1;
        }
    }

    @NonNull
    private static byte[] readLimited(@NonNull InputStream in) throws IOException, TransferException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        long size = 0;
        while ((n = in.read(buf)) > 0) {
            size += n;
            if (size > MAX_ENTRY_BYTES) throw new TransferException(Lang.t("引っ越しファイルが大きすぎます。"));
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static long copyLimited(@NonNull InputStream in, @NonNull File out) throws IOException, TransferException {
        long size = 0;
        try (FileOutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                size += n;
                if (size > MAX_ENTRY_BYTES) throw new TransferException(Lang.t("引っ越しファイルが大きすぎます。"));
                os.write(buf, 0, n);
            }
        }
        return size;
    }

    /** SharedPreferences の XML(&lt;map&gt;&lt;string name=".."&gt;..&lt;/string&gt;...)を読む。 */
    @NonNull
    static Map<String, Object> parsePrefs(@NonNull String xml) throws TransferException {
        Map<String, Object> map = new LinkedHashMap<>();
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new StringReader(xml));
            int ev;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev != XmlPullParser.START_TAG || p.getDepth() != 2) continue;
                String tag = p.getName();
                String name = p.getAttributeValue(null, "name");
                if (name == null) continue;
                String value = p.getAttributeValue(null, "value");
                switch (tag) {
                    case "string":
                        map.put(name, p.nextText());
                        break;
                    case "boolean":
                        map.put(name, Boolean.parseBoolean(value));
                        break;
                    case "int":
                        if (value != null) map.put(name, Integer.parseInt(value));
                        break;
                    case "long":
                        if (value != null) map.put(name, Long.parseLong(value));
                        break;
                    case "float":
                        if (value != null) map.put(name, Float.parseFloat(value));
                        break;
                    case "set": {
                        Set<String> set = new HashSet<>();
                        int depth = p.getDepth();
                        while (!((ev = p.next()) == XmlPullParser.END_TAG && p.getDepth() == depth)) {
                            if (ev == XmlPullParser.END_DOCUMENT) break;
                            if (ev == XmlPullParser.START_TAG && "string".equals(p.getName())) set.add(p.nextText());
                        }
                        map.put(name, set);
                        break;
                    }
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            throw new TransferException(Lang.t("Aozora の設定を読み込めませんでした。"), e);
        }
        return map;
    }

    @NonNull
    private static Result apply(@NonNull Context context, @NonNull Archive a) throws TransferException {
        Result r = new Result();
        try {
            // ---- 設定 ----
            r.settings += copyKeys(a.prefs.get("AppPrefs"), context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE),
                    APP_PREF_KEYS);
            r.settings += copyKeys(a.prefs.get("AdvancedBrowserPrefs"),
                    context.getSharedPreferences("AdvancedBrowserPrefs", Context.MODE_PRIVATE), ADVANCED_PREF_KEYS);
            r.settings += copyKeys(a.prefs.get(DesignSettingsActivity.THEME_PREFS),
                    context.getSharedPreferences(DesignSettingsActivity.THEME_PREFS, Context.MODE_PRIVATE),
                    new String[]{"selected_theme"});
            r.settings += copyKeys(a.prefs.get(DesignSettingsActivity.POPUP_PREFS),
                    context.getSharedPreferences(DesignSettingsActivity.POPUP_PREFS, Context.MODE_PRIVATE),
                    new String[]{"selected_popupbutton"});
            r.settings += copyKeys(a.prefs.get(DesignSettingsActivity.ARROW_PREFS),
                    context.getSharedPreferences(DesignSettingsActivity.ARROW_PREFS, Context.MODE_PRIVATE),
                    new String[]{"selected_arrowbutton"});

            // ---- 位置情報の許可(AirSky の SitePermissions が起動時に取り込む) ----
            Map<String, Object> geo = a.prefs.get("GeoPermissionStore");
            if (geo != null && !geo.isEmpty()) {
                SharedPreferences.Editor ed = context.getSharedPreferences("GeoPermissionStore", Context.MODE_PRIVATE).edit();
                for (Map.Entry<String, Object> e : geo.entrySet()) {
                    if (put(ed, e.getKey(), e.getValue())) r.sites++;
                }
                ed.commit();
            }

            Map<String, Object> tabsPrefs = a.prefs.get("WebViewTabs");
            SharedPreferences web = context.getSharedPreferences("WebViewTabs", Context.MODE_PRIVATE);

            // ---- ブックマーク(一覧のブックマークと、画像付きで保存したブックマーク) ----
            r.bookmarks += importBookmarks(context, tabsPrefs, a.databases.get("saved_bm.db"));

            // ---- 履歴 ----
            r.history += importHistory(web, tabsPrefs, a.databases.get("history.db"));

            // ---- タブ(戻る/進む履歴も) ----
            r.tabs += importTabs(context, web, tabsPrefs, a.tabHistory);

            // ---- 保存したページ ----
            r.savedPages += importSavedPages(context, a.databases.get("saved_pages.db"), a.savedPageFiles);

            // ---- ダウンロード履歴 ----
            Map<String, Object> adv = a.prefs.get("AdvancedBrowserPrefs");
            Object dl = adv != null ? adv.get("download_history") : null;
            if (dl instanceof String) r.downloads += importDownloads(context, (String) dl);
        } catch (TransferException e) {
            throw e;
        } catch (Exception e) {
            throw new TransferException(Lang.t("データの反映中にエラーが発生しました。"), e);
        }
        if (r.total() == 0) throw new TransferException(Lang.t("引っ越せるデータが見つかりませんでした。"));
        return r;
    }

    private static int copyKeys(@Nullable Map<String, Object> from, @NonNull SharedPreferences to,
                                @NonNull String[] keys) {
        if (from == null) return 0;
        SharedPreferences.Editor ed = to.edit();
        int n = 0;
        for (String k : keys) {
            if (from.containsKey(k) && put(ed, k, from.get(k))) n++;
        }
        ed.commit();
        return n;
    }

    @SuppressWarnings("unchecked")
    private static boolean put(@NonNull SharedPreferences.Editor ed, @NonNull String key, @Nullable Object v) {
        if (v instanceof Boolean) ed.putBoolean(key, (Boolean) v);
        else if (v instanceof Integer) ed.putInt(key, (Integer) v);
        else if (v instanceof Long) ed.putLong(key, (Long) v);
        else if (v instanceof Float) ed.putFloat(key, (Float) v);
        else if (v instanceof String) ed.putString(key, (String) v);
        else if (v instanceof Set) ed.putStringSet(key, (Set<String>) v);
        else return false;
        return true;
    }

    private static int importBookmarks(@NonNull Context context, @Nullable Map<String, Object> tabsPrefs,
                                       @Nullable File savedDb) throws Exception {
        // URL → 題名(並びは Aozora と同じ、古い順)
        Map<String, String> list = new LinkedHashMap<>();
        if (savedDb != null) {
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(savedDb.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                 Cursor c = db.rawQuery("SELECT url, title FROM pages ORDER BY id", null)) {
                while (c.moveToNext()) addLink(list, c.getString(0), c.getString(1));
            } catch (Exception ignored) {
                // 画像付きのブックマークが読めなくても、ほかは移す
            }
        }
        Object json = tabsPrefs != null ? tabsPrefs.get("bookmarks") : null;
        if (json instanceof String) {
            JSONArray arr = new JSONArray((String) json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) addLink(list, o.optString("url"), o.optString("title"));
            }
        }
        if (list.isEmpty()) return 0;
        BookmarkStore store = BookmarkStore.get(context);
        long time = System.currentTimeMillis() - list.size() * 1000L;
        int n = 0;
        for (Map.Entry<String, String> e : list.entrySet()) {
            time += 1000L;
            if (store.findBookmark(e.getKey()) != null) continue;
            String title = e.getValue().isEmpty() ? e.getKey() : e.getValue();
            // 一覧での並び(新しい順)が Aozora と同じになるよう、少しずつ時刻をずらす
            if (store.addBookmark(BookmarkStore.ROOT_BOOKMARKS, title, e.getKey(), null, time) > 0) n++;
        }
        return n;
    }

    private static void addLink(@NonNull Map<String, String> list, @Nullable String url, @Nullable String title) {
        if (!isWebUrl(url) || list.containsKey(url)) return;
        list.put(url, title != null ? title : "");
    }

    /** Aozora の内蔵ページ(file:///android_asset/...)などは移さない。 */
    private static boolean isWebUrl(@Nullable String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase(java.util.Locale.ROOT);
        return u.startsWith("http://") || u.startsWith("https://");
    }

    private static int importHistory(@NonNull SharedPreferences web, @Nullable Map<String, Object> tabsPrefs,
                                     @Nullable File historyDb) throws Exception {
        // 訪れた時刻は history.db にだけあるので、URL で引けるようにしておく
        Map<String, Long> times = new HashMap<>();
        Map<String, String> dbTitles = new LinkedHashMap<>();
        if (historyDb != null) {
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(historyDb.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                 Cursor c = db.rawQuery("SELECT url, title, timestamp FROM history ORDER BY timestamp", null)) {
                while (c.moveToNext()) {
                    String url = c.getString(0);
                    if (!isWebUrl(url)) continue;
                    times.put(url, c.getLong(2));
                    dbTitles.put(url, c.getString(1) != null ? c.getString(1) : "");
                }
            } catch (Exception ignored) {
            }
        }
        JSONArray out = new JSONArray(web.getString("history", "[]"));
        Set<String> have = new HashSet<>();
        for (int i = 0; i < out.length(); i++) {
            JSONObject o = out.optJSONObject(i);
            if (o != null) have.add(o.optString("url") + "\n" + o.optLong("time", 0));
        }
        int n = 0;
        Object json = tabsPrefs != null ? tabsPrefs.get("history") : null;
        if (json instanceof String) {
            JSONArray arr = new JSONArray((String) json);
            // history.db には URL ごとに最後に訪れた時刻しか無い。同じページを何度か訪れていると
            // 前の訪問にも最後の時刻が付くので、新しい方から見て時刻が逆転しないように揃える
            long[] visitTimes = new long[arr.length()];
            long later = 0;
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.optJSONObject(i);
                Long t = o != null ? times.get(o.optString("url")) : null;
                long time = t != null ? t : later;
                if (later > 0 && (time <= 0 || time > later)) time = later;
                visitTimes[i] = time;
                if (time > 0) later = time;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null || !isWebUrl(o.optString("url"))) continue;
                String url = o.optString("url");
                long time = visitTimes[i];
                // 同じページへの何度もの訪問は残す(AirSky に元からある履歴とだけ重複を除く)
                if (have.contains(url + "\n" + time)) continue;
                JSONObject item = new JSONObject();
                item.put("title", o.optString("title", url));
                item.put("url", url);
                if (time > 0) item.put("time", time);
                out.put(item);
                n++;
            }
        } else {
            // 一覧が無い時はデータベースの履歴を使う(古い順)
            for (Map.Entry<String, String> e : dbTitles.entrySet()) {
                long time = times.get(e.getKey());
                if (!have.add(e.getKey() + "\n" + time)) continue;
                JSONObject item = new JSONObject();
                item.put("title", e.getValue().isEmpty() ? e.getKey() : e.getValue());
                item.put("url", e.getKey());
                if (time > 0) item.put("time", time);
                out.put(item);
                n++;
            }
        }
        if (n > 0) web.edit().putString("history", out.toString()).commit();
        return n;
    }

    private static int importTabs(@NonNull Context context, @NonNull SharedPreferences web,
                                  @Nullable Map<String, Object> tabsPrefs, @Nullable JSONObject history)
            throws Exception {
        Object json = tabsPrefs != null ? tabsPrefs.get("saved_tabs") : null;
        if (!(json instanceof String)) return 0;
        JSONArray existing = new JSONArray(web.getString("saved_tabs", "[]"));
        int nextId = 0;
        for (int i = 0; i < existing.length(); i++) {
            JSONObject o = existing.optJSONObject(i);
            if (o != null) nextId = Math.max(nextId, o.optInt("id", -1) + 1);
        }
        Object cur = tabsPrefs.get("current_tab_id");
        int currentOld = cur instanceof Integer ? (Integer) cur : -1;
        int currentNew = -1;
        JSONArray arr = new JSONArray((String) json);
        int n = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || !isWebUrl(o.optString("url"))) continue;
            // タブの番号は AirSky で付け直す。戻る/進む履歴は Gecko の形にして tab_state_<id>.json に書く
            // (無ければ URL から開き直す)
            JSONObject tab = new JSONObject();
            tab.put("id", nextId);
            tab.put("url", o.optString("url"));
            tab.put("title", o.optString("title", ""));
            existing.put(tab);
            JSONObject h = history != null ? history.optJSONObject(String.valueOf(o.optInt("id", -1))) : null;
            String state = h != null ? geckoState(h) : null;
            if (state != null) writeText(new File(context.getFilesDir(), "tab_state_" + nextId + ".json"), state);
            if (o.optInt("id", -2) == currentOld || currentNew < 0) currentNew = nextId;
            nextId++;
            n++;
        }
        if (n == 0) return 0;
        web.edit().putString("saved_tabs", existing.toString()).putInt("current_tab_id", currentNew).commit();
        return n;
    }

    /**
     * Aozora の戻る/進む履歴({"index", "entries": [{"url", "title"}]})を、GeckoSession.SessionState の
     * JSON にする。入れる履歴が無ければ null。
     */
    @Nullable
    static String geckoState(@NonNull JSONObject h) throws Exception {
        JSONArray from = h.optJSONArray("entries");
        if (from == null) return null;
        int current = h.optInt("index", from.length() - 1);
        JSONArray entries = new JSONArray();
        int index = -1;
        for (int i = 0; i < from.length(); i++) {
            JSONObject o = from.optJSONObject(i);
            String url = o != null ? o.optString("url") : null;
            if (!isWebUrl(url)) continue;
            if (i <= current || index < 0) index = entries.length();
            JSONObject e = new JSONObject();
            e.put("url", url);
            e.put("title", o.optString("title", ""));
            e.put("cacheKey", 0);
            e.put("ID", entries.length() + 1);
            e.put("docIdentifier", entries.length() + 1);
            e.put("triggeringPrincipal_base64", SYSTEM_PRINCIPAL);
            e.put("resultPrincipalURI", JSONObject.NULL);
            // 操作のあった履歴にしておく(無いと「戻る」で飛ばされる)
            e.put("hasUserInteraction", true);
            e.put("persist", true);
            entries.put(e);
        }
        if (entries.length() == 0) return null;
        JSONObject history = new JSONObject();
        history.put("entries", entries);
        history.put("index", index + 1); // Gecko は 1 から数える
        history.put("requestedIndex", 0);
        history.put("fromIdx", -1);
        return new JSONObject().put("history", history).toString();
    }

    /**
     * 保存したページ(saved_pages.db と、そのファイル)を移す。Aozora のページ(.mht)は GeckoView では
     * 開けないので、画像などを埋め込んだ HTML に変えてから AirSky の保存ページに加える。
     */
    private static int importSavedPages(@NonNull Context context, @Nullable File db,
                                        @NonNull Map<String, File> files) {
        if (db == null) return 0;
        File shotDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (shotDir != null) shotDir = new File(shotDir, "PageScreenshots");
        int n = 0;
        SavedPageDatabase helper = new SavedPageDatabase(context);
        try (SQLiteDatabase src = SQLiteDatabase.openDatabase(db.getPath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor c = src.rawQuery("SELECT url, title, screenshot_path, date_saved FROM pages ORDER BY id", null)) {
            SQLiteDatabase dst = helper.getWritableDatabase();
            long stamp = System.currentTimeMillis();
            while (c.moveToNext()) {
                String url = c.getString(0);
                String pageUrl;
                if (isWebUrl(url)) {
                    pageUrl = url;
                } else if (url != null && url.startsWith("/")) {
                    File from = files.get(new File(url).getName());
                    if (from == null) continue;
                    File to = new File(context.getFilesDir(), "page_" + stamp + "_" + n + ".html");
                    if (!copySavedPage(from, to)) continue;
                    pageUrl = to.getAbsolutePath();
                } else {
                    continue;
                }
                String shot = c.getString(2);
                String shotPath = "";
                File shotFrom = shot != null ? files.get(new File(shot).getName()) : null;
                if (shotFrom != null && shotDir != null && (shotDir.isDirectory() || shotDir.mkdirs())) {
                    File to = new File(shotDir, "screenshot_" + stamp + "_" + n + extension(shotFrom.getName()));
                    if (copyFile(shotFrom, to)) shotPath = to.getAbsolutePath();
                }
                String title = c.getString(1);
                ContentValues values = new ContentValues();
                values.put("url", pageUrl);
                values.put("title", title != null && !title.isEmpty() ? title : pageUrl);
                values.put("screenshot_path", shotPath);
                values.put("date_saved", c.getString(3));
                if (dst.insert("pages", null, values) >= 0) n++;
            }
        } catch (Exception ignored) {
            // 保存したページが読めなくても、ほかは移す
        } finally {
            helper.close();
        }
        return n;
    }

    /** .mht は HTML に変えて、.html はそのまま書く。 */
    private static boolean copySavedPage(@NonNull File from, @NonNull File to) {
        String name = from.getName().toLowerCase(java.util.Locale.ROOT);
        if (!name.endsWith(".mht") && !name.endsWith(".mhtml")) return copyFile(from, to);
        try {
            byte[] mht = new byte[(int) from.length()];
            try (FileInputStream in = new FileInputStream(from)) {
                int off = 0;
                int r;
                while (off < mht.length && (r = in.read(mht, off, mht.length - off)) > 0) off += r;
            }
            byte[] html = MhtConverter.toHtml(mht);
            if (html == null) return false;
            try (FileOutputStream out = new FileOutputStream(to)) {
                out.write(html);
            }
            return true;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            //noinspection ResultOfMethodCallIgnored
            to.delete();
            return false;
        }
    }

    private static boolean copyFile(@NonNull File from, @NonNull File to) {
        try (FileInputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            return true;
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            to.delete();
            return false;
        }
    }

    @NonNull
    private static String extension(@NonNull String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    private static void writeText(@NonNull File file, @NonNull String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static int importDownloads(@NonNull Context context, @NonNull String json) throws Exception {
        JSONArray from = new JSONArray(json);
        if (from.length() == 0) return 0;
        SharedPreferences p = context.getSharedPreferences("AdvancedBrowserPrefs", Context.MODE_PRIVATE);
        JSONArray to = new JSONArray(p.getString("download_history", "[]"));
        Set<String> have = new HashSet<>();
        for (int i = 0; i < to.length(); i++) {
            JSONObject o = to.optJSONObject(i);
            if (o != null) have.add(o.optString("filePath"));
        }
        int n = 0;
        for (int i = 0; i < from.length(); i++) {
            JSONObject o = from.optJSONObject(i);
            if (o == null || o.optString("filePath").isEmpty() || !have.add(o.optString("filePath"))) continue;
            to.put(o);
            n++;
        }
        if (n > 0) p.edit().putString("download_history", to.toString()).commit();
        return n;
    }

    // ------------------------------------------------------------------
    // 入れ替えの引っ越し: 既存のデータを消す
    // ------------------------------------------------------------------

    /**
     * 既存のデータ(設定・データベース・ファイル・キャッシュ)を消す。読み込んだ引っ越しファイルの一時ファイル
     * (キャッシュの aozora_transfer)だけは残す。
     *
     * <p>このプロセスで開いている設定は、ファイルを消してもメモリに前の値が残っていて、次の保存で
     * 書き戻されてしまう。先に中身を空にしてから消す。ブックマークのデータベースも閉じておく。</p>
     */
    private static void clearExistingData(@NonNull Context context) {
        BookmarkStore.closeInstance();
        File data = context.getDataDir();
        File[] prefs = new File(data, "shared_prefs").listFiles();
        if (prefs != null) {
            for (File f : prefs) {
                String name = f.getName();
                if (!name.endsWith(".xml")) continue;
                context.getSharedPreferences(name.substring(0, name.length() - 4), Context.MODE_PRIVATE)
                        .edit().clear().commit();
            }
        }
        File keep = new File(context.getCacheDir(), "aozora_transfer");
        File[] children = data.listFiles();
        if (children != null) {
            for (File f : children) {
                if ("lib".equals(f.getName())) continue;
                if (f.equals(context.getCacheDir())) {
                    File[] cache = f.listFiles();
                    if (cache != null) for (File c : cache) if (!c.equals(keep)) deleteRecursively(c);
                    continue;
                }
                deleteRecursively(f);
            }
        }
        // 最初の動画はもう見ているので、次の起動で流し直さない
        context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit()
                .putBoolean("isFirstMov", false).commit();
    }

    // ------------------------------------------------------------------
    // 失敗した時: すべてのデータを消す
    // ------------------------------------------------------------------

    /**
     * アプリのデータ(設定・データベース・ファイル・キャッシュ)をすべて消す。途中まで反映したデータを
     * 残さないため。消した後は、最初の動画をもう一度流さないよう isFirstMov だけ戻す。
     */
    static void wipeAllData(@NonNull Context context) {
        BookmarkStore.closeInstance();
        File data = context.getDataDir();
        File[] children = data.listFiles();
        if (children != null) {
            for (File f : children) {
                // ネイティブライブラリ(lib)はアプリの一部なので残す
                if ("lib".equals(f.getName())) continue;
                deleteRecursively(f);
            }
        }
        context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit()
                .clear().putBoolean("isFirstMov", false).commit();
    }

    private static void deleteRecursively(@NonNull File f) {
        File[] children = f.isDirectory() ? f.listFiles() : null;
        if (children != null) for (File c : children) deleteRecursively(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
