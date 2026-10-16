package com.airsky.browser;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.os.Build;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

/**
 * Python 実行ツールの Python 本体(python.org が配布している Android 版 CPython)。
 *
 * <p>本体は初めて使う時に python.org からダウンロードし、files/python に展開する(約 80MB)。
 * 配布物には実行ファイルが無く libpython3.x.so だけなので、アプリに同梱した小さな起動プログラム
 * (jniLibs の libairpython.so。app/src/main/cpp/airpython_launcher.c)がそれを読み込んで Python を始める。
 * 起動プログラムはアプリのネイティブライブラリの場所にあるので、Android 10 以降の
 * 「データ領域のファイルは実行できない」制限にもかからない。</p>
 *
 * <p>プログラムは Android 内蔵のシェル(/system/bin/sh)から起動し、標準入出力をつないで動かす。</p>
 */
final class PythonRuntime {

    static final String VERSION = "3.14.8";
    static final String SHORT = "3.14";
    private static final String URL_BASE = "https://www.python.org/ftp/python/" + VERSION + "/python-" + VERSION + "-";
    /** python.org の配布物の SHA-256(ダウンロードの改ざん・破損を確かめる) */
    private static final Map<String, String> SHA256 = new LinkedHashMap<>();

    static {
        SHA256.put("aarch64", "11324313957da3736f44e90257304d288864321a4dd376ed0cc35a54eacf9a33");
        SHA256.put("x86_64", "58eb3b2d76ef57e076985a0a6b093cf08906d65ea4ab78cccc6d894d4250c972");
    }

    private PythonRuntime() {}

    /** この端末の CPU に合う配布物の名前。対応していなければ null(32bit の端末など)。 */
    @Nullable
    static String arch() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) return "aarch64";
            if ("x86_64".equals(abi)) return "x86_64";
        }
        return null;
    }

    static File home(Context c) {
        return new File(c.getFilesDir(), "python");
    }

    static File libpython(Context c) {
        return new File(home(c), "lib/libpython" + SHORT + ".so");
    }

    private static File marker(Context c) {
        return new File(home(c), "airsky-installed.txt");
    }

    /** 起動プログラム(アプリに同梱。実行できる場所にある)。 */
    static File launcher(Context c) {
        return new File(c.getApplicationInfo().nativeLibraryDir, "libairpython.so");
    }

    /** この端末で Python を使えるか(CPU が対応していて、起動プログラムが入っている)。 */
    static boolean supported(Context c) {
        return arch() != null && launcher(c).isFile();
    }

    static boolean installed(Context c) {
        if (!libpython(c).isFile()) return false;
        return VERSION.equals(readText(marker(c)).trim());
    }

    /** 本体が使っている容量(バイト)。 */
    static long installedSize(Context c) {
        return treeSize(home(c));
    }

    static long treeSize(File f) {
        if (f.isFile()) return f.length();
        long n = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) n += treeSize(k);
        return n;
    }

    static void uninstall(Context c) {
        deleteTree(home(c));
        deleteTree(shimsRoot(c));
    }

    // ------------------------------------------------------------------
    // ダウンロードと展開
    // ------------------------------------------------------------------

    interface Progress {
        void onProgress(@NonNull String message, int percent);
    }

    /**
     * python.org から本体をダウンロードして展開する。時間がかかるので UI スレッド以外で呼ぶ。
     * 途中で cancel が true になったら止める(展開途中のものは消す)。
     */
    static void install(@NonNull Context c, @NonNull Progress progress, @NonNull AtomicBoolean cancel)
            throws IOException {
        String arch = arch();
        if (arch == null) throw new IOException(Lang.t("この端末の CPU(32bit)には対応していません"));
        File tmp = new File(c.getCacheDir(), "python-" + VERSION + "-" + arch + ".tar.gz");
        String url = URL_BASE + arch + "-linux-android.tar.gz";
        try {
            download(url, tmp, progress, cancel);
            progress.onProgress(Lang.t("ファイルを確かめています…"), -1);
            String sum = sha256(tmp);
            if (!sum.equalsIgnoreCase(SHA256.get(arch))) {
                throw new IOException(Lang.t("ダウンロードしたファイルが壊れています。もう一度お試しください"));
            }
            File dest = home(c);
            File staging = new File(c.getFilesDir(), "python.installing");
            deleteTree(staging);
            extract(tmp, staging, progress, cancel);
            if (cancel.get()) throw new IOException(Lang.t("中止しました"));
            writeText(new File(staging, "airsky-installed.txt"), VERSION);
            deleteTree(dest);
            if (!staging.renameTo(dest)) throw new IOException(Lang.t("展開したファイルを移動できません"));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            deleteTree(new File(c.getFilesDir(), "python.installing"));
        }
    }

    private static void download(String url, File out, Progress progress, AtomicBoolean cancel) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "AirSkyBrowser/" + BuildConfig.VERSION_NAME + " (Python installer)");
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException(Lang.t("ダウンロードできませんでした (HTTP ") + code + ")");
            long total = conn.getContentLengthLong();
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int n, last = -1;
                while ((n = in.read(buf)) > 0) {
                    if (cancel.get()) throw new IOException(Lang.t("中止しました"));
                    os.write(buf, 0, n);
                    done += n;
                    int pct = total > 0 ? (int) (done * 100 / total) : -1;
                    if (pct != last) {
                        last = pct;
                        progress.onProgress(Lang.t("ダウンロードしています… ")
                                + (done / (1024 * 1024)) + " / " + Math.max(1, total / (1024 * 1024)) + " MB", pct);
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String sha256(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** 展開しない(Android では使わない・大きい)部分 */
    private static boolean skip(String rel) {
        String lib = "lib/python" + SHORT + "/";
        return rel.startsWith(lib + "test/") || rel.startsWith(lib + "idlelib/")
                || rel.startsWith(lib + "turtledemo/") || rel.startsWith(lib + "tkinter/")
                || rel.startsWith("lib/pkgconfig/") || rel.contains("/__pycache__/");
    }

    /** tar.gz の prefix/lib 以下を dest に展開する(ustar / GNU の長い名前 / pax に対応)。 */
    private static void extract(File tgz, File dest, Progress progress, AtomicBoolean cancel) throws IOException {
        long total = tgz.length();
        String destPath = dest.getCanonicalPath() + File.separator;
        try (FileInputStream raw = new FileInputStream(tgz);
             InputStream in = new BufferedInputStream(new GZIPInputStream(new BufferedInputStream(raw), 64 * 1024))) {
            byte[] header = new byte[512];
            String longName = null;
            String paxPath = null;
            int lastPct = -1;
            while (true) {
                if (cancel.get()) return;
                if (!readFully(in, header)) break;
                boolean empty = true;
                for (byte b : header) {
                    if (b != 0) {
                        empty = false;
                        break;
                    }
                }
                if (empty) break;
                String name = cstr(header, 0, 100);
                String prefix = cstr(header, 345, 155);
                if (!prefix.isEmpty() && "ustar".equals(cstr(header, 257, 5))) name = prefix + "/" + name;
                long size = octal(header, 124, 12);
                char type = (char) header[156];
                String link = cstr(header, 157, 100);
                if (type == 'L') {
                    longName = new String(readBytes(in, size), StandardCharsets.UTF_8).trim().replace("\0", "");
                    skipPad(in, size);
                    continue;
                }
                if (type == 'x' || type == 'g') {
                    String pax = new String(readBytes(in, size), StandardCharsets.UTF_8);
                    skipPad(in, size);
                    if (type == 'x') {
                        for (String line : pax.split("\n")) {
                            int sp = line.indexOf(' ');
                            if (sp > 0 && line.substring(sp + 1).startsWith("path=")) {
                                paxPath = line.substring(sp + 6);
                            }
                        }
                    }
                    continue;
                }
                if (longName != null) name = longName;
                if (paxPath != null) name = paxPath;
                longName = null;
                paxPath = null;

                String rel = name.startsWith("./") ? name.substring(2) : name;
                boolean wanted = rel.startsWith("prefix/lib/");
                rel = wanted ? rel.substring("prefix/".length()) : rel;
                if (wanted && skip(rel)) wanted = false;
                File out = new File(dest, rel);
                if (wanted && !(out.getCanonicalPath() + (type == '5' ? File.separator : "")).startsWith(destPath)) {
                    wanted = false; // 外へ出るパスは無視する
                }

                if (type == '5') {
                    if (wanted) out.mkdirs();
                } else if (type == '2') {
                    if (wanted) {
                        File parent = out.getParentFile();
                        if (parent != null) parent.mkdirs();
                        try {
                            Os.symlink(link, out.getPath());
                        } catch (Exception e) {
                            // シンボリックリンクを作れない時は、後で中身を写す
                            pendingLinks.add(new String[]{out.getPath(), link});
                        }
                    }
                } else if (type == '0' || type == 0 || type == '7') {
                    if (wanted) {
                        File parent = out.getParentFile();
                        if (parent != null) parent.mkdirs();
                        try (OutputStream os = new FileOutputStream(out)) {
                            copyN(in, os, size);
                        }
                        skipPad(in, size);
                    } else {
                        skipN(in, size + pad(size));
                    }
                } else {
                    skipN(in, size + pad(size));
                }
                int pct = (int) Math.min(99, raw.getChannel().position() * 100 / Math.max(1, total));
                if (pct != lastPct) {
                    lastPct = pct;
                    progress.onProgress(Lang.t("展開しています…"), pct);
                }
            }
        }
        for (String[] l : pendingLinks) {
            File target = new File(new File(l[0]).getParentFile(), l[1]);
            if (target.isFile()) {
                try (InputStream i = new FileInputStream(target); OutputStream o = new FileOutputStream(l[0])) {
                    copyN(i, o, target.length());
                }
            }
        }
        pendingLinks.clear();
    }

    private static final List<String[]> pendingLinks = new ArrayList<>();

    private static boolean readFully(InputStream in, byte[] b) throws IOException {
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) return false;
            off += n;
        }
        return true;
    }

    private static byte[] readBytes(InputStream in, long size) throws IOException {
        byte[] b = new byte[(int) size];
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) throw new IOException(Lang.t("tar が途中で終わっています"));
            off += n;
        }
        return b;
    }

    private static void copyN(InputStream in, OutputStream os, long size) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long left = size;
        while (left > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (n < 0) throw new IOException(Lang.t("tar が途中で終わっています"));
            os.write(buf, 0, n);
            left -= n;
        }
    }

    private static long pad(long size) {
        return (512 - (size % 512)) % 512;
    }

    private static void skipPad(InputStream in, long size) throws IOException {
        skipN(in, pad(size));
    }

    private static void skipN(InputStream in, long n) throws IOException {
        while (n > 0) {
            long s = in.skip(n);
            if (s <= 0) {
                if (in.read() < 0) return;
                s = 1;
            }
            n -= s;
        }
    }

    private static String cstr(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    private static long octal(byte[] b, int off, int len) {
        if ((b[off] & 0x80) != 0) {
            // 大きなファイルの base-256 表記
            long v = 0;
            for (int i = off + 1; i < off + len; i++) v = (v << 8) | (b[i] & 0xFF);
            return v;
        }
        String s = cstr(b, off, len).trim();
        if (s.isEmpty()) return 0;
        return Long.parseLong(s, 8);
    }

    // ------------------------------------------------------------------
    // 互換モジュール(tkinter / wx / flet / dotenv)
    // ------------------------------------------------------------------

    static File shimsRoot(Context c) {
        return new File(c.getFilesDir(), "python-shims");
    }

    static File guiShims(Context c) {
        return new File(shimsRoot(c), "gui");
    }

    static File extraShims(Context c) {
        return new File(shimsRoot(c), "extra");
    }

    /** アプリに入っている互換モジュールを files/python-shims へ写す(アプリを更新した時だけ)。 */
    static synchronized void ensureShims(@NonNull Context c) throws IOException {
        long stamp;
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            stamp = pi.lastUpdateTime;
        } catch (Exception e) {
            stamp = 0;
        }
        File mark = new File(shimsRoot(c), "stamp.txt");
        if (String.valueOf(stamp).equals(readText(mark).trim()) && guiShims(c).isDirectory()) return;
        deleteTree(shimsRoot(c));
        copyAssetTree(c.getAssets(), "python", shimsRoot(c));
        writeCertBundle(certBundle(c));
        writeText(mark, String.valueOf(stamp));
    }

    static File certBundle(Context c) {
        return new File(shimsRoot(c), "cacert.pem");
    }

    /** 端末の証明書のフォルダ(Android 14 以降は APEX の中)。 */
    private static File systemCertDir() {
        File apex = new File("/apex/com.android.conscrypt/cacerts");
        return apex.isDirectory() ? apex : new File("/system/etc/security/cacerts");
    }

    /**
     * 端末の証明書を 1 つの PEM ファイルにまとめる。Android の証明書フォルダはファイル名が古い OpenSSL の
     * ハッシュなので、Python(OpenSSL 3)の SSL_CERT_DIR からは見つけられない。
     */
    private static void writeCertBundle(File out) {
        File[] certs = systemCertDir().listFiles();
        if (certs == null) return;
        StringBuilder sb = new StringBuilder();
        for (File f : certs) {
            String text = readText(f);
            int a = text.indexOf("-----BEGIN CERTIFICATE-----");
            int b = text.indexOf("-----END CERTIFICATE-----");
            if (a >= 0 && b > a) sb.append(text, a, b + 25).append('\n');
        }
        try {
            writeText(out, sb.toString());
        } catch (IOException ignored) {
        }
    }

    private static void copyAssetTree(AssetManager am, String path, File dest) throws IOException {
        String[] kids = am.list(path);
        if (kids == null || kids.length == 0) {
            File parent = dest.getParentFile();
            if (parent != null) parent.mkdirs();
            try (InputStream in = am.open(path); OutputStream os = new FileOutputStream(dest)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            return;
        }
        dest.mkdirs();
        for (String k : kids) copyAssetTree(am, path + "/" + k, new File(dest, k));
    }

    // ------------------------------------------------------------------
    // 起動
    // ------------------------------------------------------------------

    /** 起動の設定(環境変数・作業フォルダ・引数)。 */
    static final class Launch {
        @NonNull final File workDir;
        @NonNull final List<String> pythonArgs = new ArrayList<>();
        /** sh -c で実行する文(null なら exec "$AIRSKY_PYTHON" "$@")。 */
        @Nullable String shellScript;
        @NonNull final Map<String, String> env = new LinkedHashMap<>();

        Launch(@NonNull File workDir) {
            this.workDir = workDir;
        }
    }

    /** Python の基本の環境変数を用意する。 */
    static void baseEnv(@NonNull Context c, @NonNull Launch l, @Nullable File projectDir) {
        Map<String, String> env = l.env;
        File home = home(c);
        env.put("PYTHONHOME", home.getPath());
        env.put("AIRSKY_LIBPYTHON", libpython(c).getPath());
        env.put("AIRSKY_PYTHON", launcher(c).getPath());
        env.put("LD_LIBRARY_PATH", new File(home, "lib").getPath());
        env.put("PYTHONUNBUFFERED", "1");
        env.put("PYTHONUTF8", "1");
        env.put("PYTHONIOENCODING", "utf-8");
        env.put("PYTHONNOUSERSITE", "1");
        env.put("TERM", "dumb");
        env.put("COLUMNS", "60");
        env.put("LINES", "40");
        env.put("LANG", "ja_JP.UTF-8");
        File tmp = new File(c.getCacheDir(), "pytmp");
        tmp.mkdirs();
        env.put("TMPDIR", tmp.getPath());
        // 証明書(https 通信で使う)。端末の証明書を 1 つの PEM にまとめたもの(ensureShims で作る)
        File bundle = certBundle(c);
        if (bundle.isFile()) env.put("SSL_CERT_FILE", bundle.getPath());
        env.put("SSL_CERT_DIR", systemCertDir().getPath());

        List<String> path = new ArrayList<>();
        path.add(guiShims(c).getPath());
        if (projectDir != null) {
            path.add(packagesDir(projectDir).getPath());
            path.addAll(venvSitePackages(projectDir));
            File home2 = new File(projectDir, ".airsky/home");
            home2.mkdirs();
            env.put("HOME", home2.getPath());
        } else {
            File h = new File(c.getFilesDir(), "python-home");
            h.mkdirs();
            env.put("HOME", h.getPath());
        }
        path.add(extraShims(c).getPath());
        env.put("PYTHONPATH", android.text.TextUtils.join(":", path));
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        env.put("AIRSKY_SCREEN_W", String.valueOf(Math.round(dm.widthPixels / dm.density)));
        env.put("AIRSKY_SCREEN_H", String.valueOf(Math.round(dm.heightPixels / dm.density)));
    }

    /** pip で入れたパッケージの置き場所(プログラムごと)。 */
    static File packagesDir(File projectDir) {
        return new File(projectDir, ".airsky/site-packages");
    }

    /** プロジェクトに入っている仮想環境(venv / .venv / env)の site-packages。 */
    static List<String> venvSitePackages(File projectDir) {
        List<String> out = new ArrayList<>();
        for (String name : new String[]{"venv", ".venv", "env", ".env.d", "virtualenv"}) {
            File v = new File(projectDir, name);
            if (!v.isDirectory()) continue;
            File win = new File(v, "Lib/site-packages");
            if (win.isDirectory()) out.add(win.getPath());
            File lib = new File(v, "lib");
            File[] kids = lib.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    File sp = new File(k, "site-packages");
                    if (k.getName().startsWith("python") && sp.isDirectory()) out.add(sp.getPath());
                }
            }
        }
        return out;
    }

    /** プロジェクトの .env を読む(python-dotenv と同じ書き方。値は環境変数に入れる)。 */
    static Map<String, String> readDotEnv(File file, Map<String, String> base) {
        Map<String, String> out = new LinkedHashMap<>();
        String text = readText(file);
        if (text.isEmpty()) return out;
        String[] lines = text.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("export ")) line = line.substring(7).trim();
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            if (!key.matches("[A-Za-z_][A-Za-z0-9_.\\-]*")) continue;
            String val = line.substring(eq + 1).trim();
            boolean literal = false;
            if (val.startsWith("\"") || val.startsWith("'")) {
                char q = val.charAt(0);
                StringBuilder sb = new StringBuilder(val.substring(1));
                while (sb.indexOf(String.valueOf(q)) < 0 && i + 1 < lines.length) {
                    sb.append('\n').append(lines[++i]);
                }
                int end = sb.indexOf(String.valueOf(q));
                val = end >= 0 ? sb.substring(0, end) : sb.toString();
                if (q == '"') val = val.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"");
                literal = q == '\'';
            } else {
                int hash = val.indexOf(" #");
                if (hash >= 0) val = val.substring(0, hash).trim();
            }
            if (!literal) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?\\}").matcher(val);
                StringBuffer sb = new StringBuffer();
                while (m.find()) {
                    String v = out.get(m.group(1));
                    if (v == null) v = base.get(m.group(1));
                    if (v == null) v = System.getenv(m.group(1));
                    if (v == null || v.isEmpty()) v = m.group(2) != null ? m.group(2) : "";
                    m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(v));
                }
                m.appendTail(sb);
                val = sb.toString();
            }
            out.put(key, val);
        }
        return out;
    }

    /** /system/bin/sh で Python を始める ProcessBuilder。 */
    static ProcessBuilder processBuilder(@NonNull Launch l) {
        List<String> cmd = new ArrayList<>();
        cmd.add("/system/bin/sh");
        cmd.add("-c");
        cmd.add(l.shellScript != null ? l.shellScript : "exec \"$AIRSKY_PYTHON\" \"$@\"");
        cmd.add("python");
        cmd.addAll(l.pythonArgs);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(l.workDir);
        Map<String, String> env = pb.environment();
        env.putAll(l.env);
        return pb;
    }

    // ------------------------------------------------------------------

    static String readText(File f) {
        if (!f.isFile()) return "";
        try (InputStream in = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    static void writeText(File f, String text) throws IOException {
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    static void deleteTree(File f) {
        if (f == null) return;
        try {
            // シンボリックリンクはたどらずにリンクだけ消す
            if (android.system.Os.lstat(f.getPath()) != null
                    && android.system.OsConstants.S_ISLNK(android.system.Os.lstat(f.getPath()).st_mode)) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                return;
            }
        } catch (Exception ignored) {
        }
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
