package com.airsky.browser;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * JAR 実行ツールに追加した JAR の一覧。JAR はアプリの中(files/jars)へ写して持つ。
 * 「ツールに追加」した JAR は、Aozora メニューのツール一覧にショートカットとして並ぶ。
 */
final class JarStore {

    private static final String PREFS = "jar_tool";
    private static final String KEY = "entries";

    static final class Entry {
        final String id;
        String name;
        long size;
        long added;
        boolean shortcut;
        String args = "";

        Entry(String id) {
            this.id = id;
        }
    }

    private JarStore() {}

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
                e.name = o.optString("name", "Java");
                e.size = o.optLong("size");
                e.added = o.optLong("added");
                e.shortcut = o.optBoolean("shortcut");
                e.args = o.optString("args", "");
                if (jarFile(c, e.id).isFile()) out.add(e);
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
        List<Entry> list = list(c);
        List<Entry> keep = new ArrayList<>();
        for (Entry e : list) if (!e.id.equals(id)) keep.add(e);
        save(c, keep);
        File jar = jarFile(c, id);
        jar.delete();
        deleteTree(cacheDir(c, id));
        ProgramShortcuts.removePinned(c, ProgramShortcuts.KIND_JAVA, id);
    }

    static File jarFile(Context c, String id) {
        return new File(new File(c.getFilesDir(), "jars"), id + ".jar");
    }

    /** 変換した DEX などを置くフォルダ。 */
    static File cacheDir(Context c, String id) {
        return new File(new File(c.getFilesDir(), "jvm"), id);
    }

    /** プログラムの作業フォルダ(相対パスのファイルはここに置かれる)。 */
    static File homeDir(Context c, Entry e) {
        File base = c.getExternalFilesDir("java");
        if (base == null) base = new File(c.getFilesDir(), "java");
        String safe = e.name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return new File(base, safe.isEmpty() ? e.id : safe);
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.setWritable(true);
        f.delete();
    }

    /** 端末のファイル(SAF の Uri)から JAR を写して追加する。時間がかかるので UI スレッド以外で呼ぶ。 */
    @NonNull
    static Entry add(@NonNull Context c, @NonNull Uri uri) throws IOException {
        ContentResolver cr = c.getContentResolver();
        String name = displayName(cr, uri);
        if (name == null || name.isEmpty()) name = "Java";
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) name = name.substring(0, name.length() - 4);
        Entry e = new Entry(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        e.name = name;
        e.added = System.currentTimeMillis();
        File out = jarFile(c, e.id);
        File dir = out.getParentFile();
        if (dir != null && !dir.isDirectory()) dir.mkdirs();
        try (InputStream in = cr.openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IOException(Lang.t("ファイルを開けません"));
            byte[] buf = new byte[64 * 1024];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                total += n;
            }
            e.size = total;
        }
        if (!looksLikeJar(out)) {
            out.delete();
            throw new IOException(Lang.t("JAR ファイルではないか、Java のクラスが入っていません"));
        }
        List<Entry> list = list(c);
        list.add(0, e);
        save(c, list);
        return e;
    }

    private static boolean looksLikeJar(File f) {
        try (ZipInputStream z = new ZipInputStream(new java.io.FileInputStream(f))) {
            ZipEntry ze;
            while ((ze = z.getNextEntry()) != null) {
                if (ze.getName().endsWith(".class")) return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    @Nullable
    private static String displayName(ContentResolver cr, Uri uri) {
        try (Cursor cur = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last != null ? last.substring(last.lastIndexOf('/') + 1) : null;
    }

    // ------------------------------------------------------------------
    // 実行
    // ------------------------------------------------------------------

    private static final String JVM_PROCESS_SUFFIX = ":jvm";

    /** 実行中の JAR の印(:jvm プロセスが書き、終わると消す)。 */
    static File runningFile(Context c) {
        return new File(c.getFilesDir(), "jvm_running");
    }

    /**
     * JAR を実行する。同じ JAR が動いていれば前に出し、別の JAR が動いていれば終わらせてから始める
     * (プログラムは 1 つずつ専用のプロセスで動かす)。
     */
    static void launch(@NonNull Activity a, @NonNull Entry e, @Nullable String args) {
        ActivityManager am = (ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE);
        int pid = jvmPid(a, am);
        if (pid != 0 && args == null && e.id.equals(readRunning(a))) {
            for (ActivityManager.AppTask t : am.getAppTasks()) {
                Intent base = t.getTaskInfo().baseIntent;
                if (base != null && base.getComponent() != null
                        && JarRunActivity.class.getName().equals(base.getComponent().getClassName())) {
                    t.moveToFront();
                    return;
                }
            }
        }
        if (pid != 0) android.os.Process.killProcess(pid);
        Intent i = new Intent(a, JarRunActivity.class)
                .putExtra(JarRunActivity.EXTRA_ID, e.id)
                .putExtra(JarRunActivity.EXTRA_NAME, e.name)
                .putExtra(JarRunActivity.EXTRA_ARGS, args != null ? args : e.args)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Runnable start = () -> {
            a.startActivity(i);
            a.overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
        };
        // 終わらせたプロセスが片付くのを少し待ってから始める
        if (pid != 0) new Handler(Looper.getMainLooper()).postDelayed(start, 350);
        else start.run();
    }

    private static int jvmPid(Context c, ActivityManager am) {
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return 0;
        String name = c.getPackageName() + JVM_PROCESS_SUFFIX;
        for (ActivityManager.RunningAppProcessInfo p : procs) {
            if (name.equals(p.processName)) return p.pid;
        }
        return 0;
    }

    @Nullable
    private static String readRunning(Context c) {
        File f = runningFile(c);
        if (!f.isFile()) return null;
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[64];
            int n = in.read(b);
            return n > 0 ? new String(b, 0, n, java.nio.charset.StandardCharsets.UTF_8).trim() : null;
        } catch (IOException ex) {
            return null;
        }
    }
}
