package com.airsky.browser;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Python 実行ツールに追加したプログラムの一覧。
 *
 * <p>プログラムは 1 つずつ専用のフォルダ(files/pyapps/&lt;id&gt;)に写して持つ。このフォルダが実行時の
 * 作業フォルダになるので、プログラムが作るデータベース(SQLite)・.env・設定ファイルなどもここに残る。
 * .py ファイル 1 つ、プロジェクトの zip、フォルダごとの追加に対応する。</p>
 */
final class PyStore {

    private static final String PREFS = "py_tool";
    private static final String KEY = "entries";
    static final String REPL_ID = "__repl__";

    static final class Entry {
        final String id;
        String name;
        /** 実行するファイル(作業フォルダからの相対パス) */
        String main = "main.py";
        long size;
        long added;
        boolean shortcut;
        String args = "";

        Entry(String id) {
            this.id = id;
        }
    }

    private PyStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    static List<Entry> list(@NonNull Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry(o.getString("id"));
                e.name = o.optString("name", "Python");
                e.main = o.optString("main", "main.py");
                e.size = o.optLong("size");
                e.added = o.optLong("added");
                e.shortcut = o.optBoolean("shortcut");
                e.args = o.optString("args", "");
                if (dir(c, e.id).isDirectory()) out.add(e);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    @Nullable
    static Entry find(@NonNull Context c, @Nullable String id) {
        for (Entry e : list(c)) if (e.id.equals(id)) return e;
        return null;
    }

    private static void save(Context c, List<Entry> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Entry e : list) {
                arr.put(new JSONObject()
                        .put("id", e.id)
                        .put("name", e.name)
                        .put("main", e.main)
                        .put("size", e.size)
                        .put("added", e.added)
                        .put("shortcut", e.shortcut)
                        .put("args", e.args));
            }
        } catch (JSONException ignored) {
        }
        prefs(c).edit().putString(KEY, arr.toString()).apply();
    }

    static void update(@NonNull Context c, @NonNull Entry changed) {
        List<Entry> list = list(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(changed.id)) list.set(i, changed);
        }
        save(c, list);
    }

    static void setShortcut(@NonNull Context c, @NonNull String id, boolean on) {
        Entry e = find(c, id);
        if (e == null) return;
        e.shortcut = on;
        update(c, e);
    }

    static void remove(@NonNull Context c, @NonNull String id) {
        List<Entry> keep = new ArrayList<>();
        for (Entry e : list(c)) if (!e.id.equals(id)) keep.add(e);
        save(c, keep);
        PythonRuntime.deleteTree(dir(c, id));
        ProgramShortcuts.removePinned(c, ProgramShortcuts.KIND_PYTHON, id);
    }

    /** プログラムのフォルダ(実行時の作業フォルダ)。 */
    static File dir(Context c, String id) {
        return new File(new File(c.getFilesDir(), "pyapps"), id);
    }

    static File mainFile(Context c, Entry e) {
        return new File(dir(c, e.id), e.main);
    }

    // ------------------------------------------------------------------
    // 追加
    // ------------------------------------------------------------------

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** .py / .pyw ファイル、または zip のプロジェクトを追加する。UI スレッド以外で呼ぶ。 */
    @NonNull
    static Entry addFile(@NonNull Context c, @NonNull Uri uri) throws IOException {
        ContentResolver cr = c.getContentResolver();
        String name = displayName(cr, uri);
        if (name == null || name.isEmpty()) name = "program.py";
        String lower = name.toLowerCase(Locale.ROOT);
        Entry e = new Entry(newId());
        e.added = System.currentTimeMillis();
        File dir = dir(c, e.id);
        dir.mkdirs();
        try {
            if (lower.endsWith(".zip")) {
                try (InputStream in = cr.openInputStream(uri)) {
                    if (in == null) throw new IOException(Lang.t("ファイルを開けません"));
                    unzip(in, dir);
                }
                flattenSingleFolder(dir);
                e.name = name.substring(0, name.length() - 4);
                String main = guessMain(dir);
                if (main == null) throw new IOException(Lang.t("zip の中に Python のファイル(.py)が見つかりません"));
                e.main = main;
            } else {
                if (!lower.endsWith(".py") && !lower.endsWith(".pyw")) {
                    name = name + ".py";
                }
                File out = new File(dir, safeName(name));
                try (InputStream in = cr.openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
                    if (in == null) throw new IOException(Lang.t("ファイルを開けません"));
                    copy(in, os);
                }
                e.main = out.getName();
                e.name = stripExt(name);
            }
        } catch (IOException ex) {
            PythonRuntime.deleteTree(dir);
            throw ex;
        }
        e.size = PythonRuntime.treeSize(dir);
        List<Entry> list = list(c);
        list.add(0, e);
        save(c, list);
        return e;
    }

    /** フォルダ(SAF のツリー)ごと追加する。 */
    @NonNull
    static Entry addFolder(@NonNull Context c, @NonNull Uri tree) throws IOException {
        Entry e = new Entry(newId());
        e.added = System.currentTimeMillis();
        File dir = dir(c, e.id);
        dir.mkdirs();
        try {
            String docId = DocumentsContract.getTreeDocumentId(tree);
            Uri root = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
            String name = queryName(c.getContentResolver(), root);
            e.name = name != null && !name.isEmpty() ? name : "Python";
            copyTree(c.getContentResolver(), tree, docId, dir, 0);
            String main = guessMain(dir);
            if (main == null) throw new IOException(Lang.t("フォルダの中に Python のファイル(.py)が見つかりません"));
            e.main = main;
        } catch (IOException | RuntimeException ex) {
            PythonRuntime.deleteTree(dir);
            throw ex instanceof IOException ? (IOException) ex : new IOException(ex.getMessage());
        }
        e.size = PythonRuntime.treeSize(dir);
        List<Entry> list = list(c);
        list.add(0, e);
        save(c, list);
        return e;
    }

    private static final List<String> SKIP_DIRS = Arrays.asList(".git", "__pycache__", ".idea", ".vscode",
            "node_modules", ".mypy_cache", ".pytest_cache");

    private static void copyTree(ContentResolver cr, Uri tree, String docId, File dest, int depth) throws IOException {
        if (depth > 12) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        try (Cursor cur = cr.query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (cur == null) return;
            while (cur.moveToNext()) {
                String id = cur.getString(0);
                String name = safeName(cur.getString(1));
                String mime = cur.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (SKIP_DIRS.contains(name)) continue;
                    File sub = new File(dest, name);
                    sub.mkdirs();
                    copyTree(cr, tree, id, sub, depth + 1);
                } else {
                    Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    try (InputStream in = cr.openInputStream(doc);
                         OutputStream os = new FileOutputStream(new File(dest, name))) {
                        if (in != null) copy(in, os);
                    }
                }
            }
        }
    }

    /** 作業フォルダへファイルを足す(データファイルや .env など)。 */
    static void importInto(@NonNull Context c, @NonNull File dir, @NonNull Uri uri) throws IOException {
        ContentResolver cr = c.getContentResolver();
        String name = displayName(cr, uri);
        if (name == null || name.isEmpty()) name = "file";
        File out = uniqueFile(dir, safeName(name));
        try (InputStream in = cr.openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IOException(Lang.t("ファイルを開けません"));
            copy(in, os);
        }
    }

    static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = stripExt(name);
        String ext = name.substring(base.length());
        for (int i = 2; ; i++) {
            f = new File(dir, base + " (" + i + ")" + ext);
            if (!f.exists()) return f;
        }
    }

    /** 作業フォルダを zip にして書き出す。 */
    static void exportZip(@NonNull Context c, @NonNull File dir, @NonNull Uri out) throws IOException {
        try (OutputStream os = c.getContentResolver().openOutputStream(out);
             ZipOutputStream zip = new ZipOutputStream(os)) {
            if (os == null) throw new IOException(Lang.t("書き出し先を開けません"));
            zipDir(zip, dir, "");
        }
    }

    private static void zipDir(ZipOutputStream zip, File dir, String prefix) throws IOException {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            String name = prefix + k.getName();
            if (k.isDirectory()) {
                if (k.getName().equals("__pycache__") || name.equals(".airsky/site-packages")) continue;
                zip.putNextEntry(new ZipEntry(name + "/"));
                zip.closeEntry();
                zipDir(zip, k, name + "/");
            } else {
                zip.putNextEntry(new ZipEntry(name));
                try (InputStream in = new FileInputStream(k)) {
                    copy(in, zip);
                }
                zip.closeEntry();
            }
        }
    }

    // ------------------------------------------------------------------

    /** 実行するファイルを推測する(__main__.py → main.py → app.py → … → 一番上の .py)。 */
    @Nullable
    static String guessMain(File dir) {
        for (String n : new String[]{"__main__.py", "main.py", "app.py", "run.py", "start.py", "game.py",
                "gui.py", "index.py", "manage.py"}) {
            if (new File(dir, n).isFile()) return n;
        }
        List<String> top = listPy(dir, "", 0);
        if (top.isEmpty()) return null;
        // 一番上の階層のものを優先
        for (String s : top) if (!s.contains("/")) return s;
        return top.get(0);
    }

    /** フォルダの中の .py ファイル(相対パス)。 */
    static List<String> listPy(File dir, String prefix, int depth) {
        List<String> out = new ArrayList<>();
        File[] kids = dir.listFiles();
        if (kids == null || depth > 4) return out;
        Arrays.sort(kids);
        for (File k : kids) {
            if (k.isDirectory()) {
                if (SKIP_DIRS.contains(k.getName()) || k.getName().startsWith(".") || k.getName().equals("venv")
                        || k.getName().equals("site-packages")) continue;
                out.addAll(listPy(k, prefix + k.getName() + "/", depth + 1));
            } else if (k.getName().endsWith(".py") || k.getName().endsWith(".pyw")) {
                out.add(prefix + k.getName());
            }
        }
        return out;
    }

    private static void unzip(InputStream in, File dest) throws IOException {
        String base = dest.getCanonicalPath() + File.separator;
        try (ZipInputStream z = new ZipInputStream(in)) {
            ZipEntry ze;
            while ((ze = z.getNextEntry()) != null) {
                String name = ze.getName().replace('\\', '/');
                if (name.startsWith("__MACOSX/") || name.contains("/__pycache__/")) continue;
                File out = new File(dest, name);
                if (!out.getCanonicalPath().startsWith(base) && !(out.getCanonicalPath() + File.separator).equals(base)) {
                    continue; // zip の外へ書くパスは無視する
                }
                if (ze.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new FileOutputStream(out)) {
                        copy(z, os);
                    }
                }
            }
        }
    }

    /** zip の中身が 1 つのフォルダだけなら、その中身を一番上へ出す。 */
    private static void flattenSingleFolder(File dir) {
        File[] kids = dir.listFiles();
        if (kids == null || kids.length != 1 || !kids[0].isDirectory()) return;
        File only = kids[0];
        File[] inner = only.listFiles();
        if (inner == null) return;
        for (File f : inner) {
            //noinspection ResultOfMethodCallIgnored
            f.renameTo(new File(dir, f.getName()));
        }
        //noinspection ResultOfMethodCallIgnored
        only.delete();
    }

    static void copy(InputStream in, OutputStream os) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
    }

    static String safeName(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_").trim();
        if (s.isEmpty() || s.equals(".") || s.equals("..")) s = "file";
        return s;
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Nullable
    static String displayName(ContentResolver cr, Uri uri) {
        String n = queryName(cr, uri);
        if (n != null) return n;
        String last = uri.getLastPathSegment();
        return last != null ? last.substring(last.lastIndexOf('/') + 1) : null;
    }

    @Nullable
    private static String queryName(ContentResolver cr, Uri uri) {
        try (Cursor cur = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 実行
    // ------------------------------------------------------------------

    /** 実行中のプログラムの印(PyRunActivity が書き、終わると消す)。 */
    static File runningFile(Context c) {
        return new File(c.getFilesDir(), "py_running");
    }

    /**
     * プログラムを実行する。同じプログラムが動いていれば前に出す。別のプログラムが動いていれば、
     * その画面を閉じて(Python も終わらせて)から始める。
     */
    static void launch(@NonNull Activity a, @NonNull Entry e, @Nullable String args) {
        start(a, PyRunActivity.MODE_RUN, e.id, e.name, args != null ? args : e.args, args == null);
    }

    /** 対話モード(REPL)。 */
    static void launchRepl(@NonNull Activity a) {
        start(a, PyRunActivity.MODE_REPL, REPL_ID, Lang.t("Python シェル"), "", true);
    }

    /** requirements.txt や指定したパッケージを pip で入れる。 */
    static void launchPip(@NonNull Activity a, @NonNull Entry e, @NonNull String packages) {
        start(a, PyRunActivity.MODE_PIP, e.id, e.name, packages, false);
    }

    private static void start(Activity a, String mode, String id, String name, String args, boolean front) {
        if (front && id.equals(readRunning(a))) {
            ActivityManager am = (ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.AppTask t : am.getAppTasks()) {
                Intent base = t.getTaskInfo().baseIntent;
                if (base != null && base.getComponent() != null
                        && PyRunActivity.class.getName().equals(base.getComponent().getClassName())) {
                    t.moveToFront();
                    return;
                }
            }
        }
        Intent i = new Intent(a, PyRunActivity.class)
                .putExtra(PyRunActivity.EXTRA_MODE, mode)
                .putExtra(PyRunActivity.EXTRA_ID, id)
                .putExtra(PyRunActivity.EXTRA_NAME, name)
                .putExtra(PyRunActivity.EXTRA_ARGS, args)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
    }

    @Nullable
    static String readRunning(Context c) {
        String s = PythonRuntime.readText(runningFile(c)).trim();
        return s.isEmpty() ? null : s;
    }

    /** 引数の文字列を分ける(ダブルクォートでくくると空白を含められる)。 */
    static List<String> splitArgs(@Nullable String args) {
        List<String> out = new ArrayList<>();
        if (args == null || args.trim().isEmpty()) return out;
        StringBuilder cur = new StringBuilder();
        boolean quote = false, any = false;
        for (char ch : args.trim().toCharArray()) {
            if (ch == '"') {
                quote = !quote;
                any = true;
            } else if (Character.isWhitespace(ch) && !quote) {
                if (any) out.add(cur.toString());
                cur.setLength(0);
                any = false;
            } else {
                cur.append(ch);
                any = true;
            }
        }
        if (any) out.add(cur.toString());
        return out;
    }
}
