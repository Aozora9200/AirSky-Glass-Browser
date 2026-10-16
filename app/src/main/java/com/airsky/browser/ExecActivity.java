package com.airsky.browser;

import androidx.appcompat.app.AppCompatActivity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EXEC: Android 内部の本物のシェルを使うコンソール。
 *
 * <p>ProcessBuilder で sh(ルート化端末で「root」を選ぶと su)を 1 つ起動し、画面を閉じるまで使い続ける。
 * 入力した行はそのままシェルの標準入力へ送るので、cd・export・関数・変数などはシェルが覚えている
 * (コマンドごとに別のシェルを立ち上げていた以前の方式とは違う)。</p>
 *
 * <p>端末(PTY)ではないので、シェルからプロンプトは出ない。コマンドの後ろに目印を出す printf を
 * 続けて送り、その目印で「コマンドが終わった・終了コード・今のフォルダ」を知る。
 * 実行中に入力した行は、動いているコマンドの標準入力になる(read や cat などへの入力)。</p>
 */
public class ExecActivity extends AppCompatActivity {

    private static final int BG = 0xFF111318;
    private static final int BAR = 0xFF111318;

    /** コマンドの終わりの目印。普通の出力には現れない制御文字で囲む。 */
    private static final String MARK = "\u001eAZEXEC";
    private static final String MARK_END = "\u001f";
    /**
     * シェルへ送る printf の書式の中の目印。制御文字は 8 進数(\036 \037)で書くので、cat などが
     * 送った行をそのまま出力しても、目印として読まれない。
     */
    private static final String SH_MARK = "\\036AZEXEC";
    private static final String SH_MARK_END = "\\037";
    /** su のパスとして探す場所。 */
    private static final String[] SU_PATHS = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/system/sbin/su", "/vendor/bin/su", "/debug_ramdisk/su", "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su", "/data/local/xbin/su", "/data/local/bin/su",
    };

    private WebView webView;
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
    /** シェルの起動・入力の書き込み・^C は、送った順に 1 つずつ行う。 */
    private final ExecutorService shellExecutor = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();

    /** 今使っているシェル。 */
    private Shell shell;
    private volatile boolean closing;

    /** 1 つのシェル(sh または su)のプロセスと入出力。 */
    private final class Shell {
        final Process process;
        final OutputStream stdin;
        final boolean root;
        /** シェル自身の PID(^C で子プロセスを止めるのに使う)。 */
        volatile int pid = -1;
        volatile boolean busy;
        volatile boolean alive = true;
        /** 実行中のコマンドの番号(終わりの目印に入れ、古い目印を無視するため)。 */
        volatile int seq;
        /** ^C を押した回数(2 回目以降は強制終了)。 */
        int interrupts;

        Shell(Process process, boolean root) {
            this.process = process;
            this.stdin = process.getOutputStream();
            this.root = root;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.exec);
        TouchEffectView.attach(getWindow());
        // ターミナルの見た目に合わせて、上下のバーも黒くする(タイトルは画面内に出す)
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        getWindow().setStatusBarColor(BAR);
        getWindow().setNavigationBarColor(BAR);
        androidx.core.view.WindowInsetsControllerCompat insets =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insets.setAppearanceLightStatusBars(false);
        insets.setAppearanceLightNavigationBars(false);

        webView = findViewById(R.id.webview);
        webView.setBackgroundColor(BG);
        ((android.view.View) webView.getParent()).setBackgroundColor(BG);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(view.getContext())
                        .setTitle(Lang.t("このページからのメッセージ"))
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (DialogInterface dialog, int which) -> result.confirm())
                        .setCancelable(false)
                        .create()
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(view.getContext())
                        .setTitle(Lang.t("このページからのメッセージ"))
                        .setMessage(message)
                        .setPositiveButton("OK", (DialogInterface dialog, int which) -> result.confirm())
                        .setNegativeButton(Lang.t("キャンセル"), (DialogInterface dialog, int which) -> result.cancel())
                        .create()
                        .show();
                return true;
            }
        });
        webView.addJavascriptInterface(new JSInterface(), "Android");
        webView.loadUrl("file:///android_asset/exec.html" + (Lang.isEnglish() ? "?hl=en" : ""));
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            close();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        close();
    }

    private void close() {
        closing = true;
        finish(); // 前の画面に戻る
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }

    @Override
    protected void onDestroy() {
        closing = true;
        Shell s;
        synchronized (lock) {
            s = shell;
            shell = null;
        }
        if (s != null) destroyShell(s);
        ioExecutor.shutdownNow();
        shellExecutor.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // 画面(exec.html)から呼ばれる
    // ------------------------------------------------------------------

    public class JSInterface {
        /** 画面の準備ができた: 普通のシェルを立ち上げる。 */
        @JavascriptInterface
        public void start() {
            shellExecutor.execute(() -> startShell(false));
        }

        /** 入力した 1 行。待機中ならコマンド、実行中なら動いているコマンドへの入力として送る。 */
        @JavascriptInterface
        public void sendLine(String line) {
            shellExecutor.execute(() -> writeLine(line == null ? "" : line));
        }

        /** ^C: 実行中のコマンドを止める。 */
        @JavascriptInterface
        public void interrupt() {
            shellExecutor.execute(ExecActivity.this::interruptCommand);
        }

        /** su を使える(ルート化されている)らしいか。 */
        @JavascriptInterface
        public boolean isRootAvailable() {
            return findSu() != null;
        }

        /** root のシェルに切り替える(su がルート権限を求める画面を出す)。 */
        @JavascriptInterface
        public void startRoot() {
            shellExecutor.execute(() -> startShell(true));
        }

        /** 普通のシェルに戻す。 */
        @JavascriptInterface
        public void startUser() {
            shellExecutor.execute(() -> startShell(false));
        }

        /** ホームフォルダ(プロンプトでは ~ と表示する)。 */
        @JavascriptInterface
        public String getHome() {
            return getFilesDir().getAbsolutePath();
        }

        @JavascriptInterface
        public void exit() {
            runOnUiThread(ExecActivity.this::close);
        }
    }

    // ------------------------------------------------------------------
    // シェル
    // ------------------------------------------------------------------

    private void startShell(boolean root) {
        if (closing) return;
        String su = root ? findSu() : null;
        if (root && su == null) {
            js(Lang.t("appendOutput('ERROR: su が見つかりません。この端末はルート化されていないようです')"));
            return;
        }
        Shell old;
        synchronized (lock) {
            old = shell;
            shell = null;
        }
        if (old != null) destroyShell(old);

        File home = getFilesDir();
        ProcessBuilder pb = root ? new ProcessBuilder(su) : new ProcessBuilder("/system/bin/sh");
        pb.directory(home);
        Map<String, String> env = pb.environment();
        env.put("HOME", home.getAbsolutePath());
        env.put("TMPDIR", getCacheDir().getAbsolutePath());
        env.put("TERM", "dumb");
        env.put("PAGER", "cat");
        env.put("COLUMNS", "80");

        final Shell s;
        try {
            s = new Shell(pb.start(), root);
        } catch (IOException e) {
            js(Lang.t("appendOutput('ERROR: シェルを起動できません: ") + escapeForJS(e.getMessage()) + "')");
            if (root) startShell(false);
            return;
        }
        synchronized (lock) {
            shell = s;
            s.busy = true;
            s.seq = 1;
        }
        js("onShellStarting(" + root + ")");
        ioExecutor.execute(() -> pump(s, s.process.getInputStream(), false));
        ioExecutor.execute(() -> pump(s, s.process.getErrorStream(), true));
        ioExecutor.execute(() -> waitForExit(s));

        // 最初に、シェルの PID・ユーザー・今のフォルダを目印付きで返してもらう。
        // su は許可の画面を出すので、ここで返事が来るまで待つことになる
        writeRaw(s, "PS1=; PS2=; " +
                "printf '" + SH_MARK + "pid:%s" + SH_MARK_END + "\\n' \"$$\"; " +
                "printf '" + SH_MARK + "user:%s" + SH_MARK_END + "\\n' \"$(id -un 2>/dev/null || whoami 2>/dev/null)\"; " +
                doneMarker(s.seq) + "\n");
    }

    /** コマンドの後ろに送る、コマンドの番号・終了コード・今のフォルダを出す目印。 */
    private static String doneMarker(int seq) {
        return "printf '" + SH_MARK + "done:" + seq + ":%s:%s" + SH_MARK_END + "\\n' \"$?\" \"$PWD\"";
    }

    private void writeLine(String line) {
        Shell s;
        synchronized (lock) {
            s = shell;
        }
        if (s == null || !s.alive) {
            js(Lang.t("appendOutput('ERROR: シェルが動いていません')"));
            js("onCommandFinished(1, null)");
            return;
        }
        if (s.busy) {
            // 実行中のコマンド(read・cat など)への入力
            writeRaw(s, line + "\n");
            return;
        }
        s.busy = true;
        s.interrupts = 0;
        s.seq++;
        // コマンドと終わりの目印を 1 行にして送る(eval で包む)。シェルは 1 行を読んでから実行するので、
        // 標準入力を読むコマンド(cat など)に目印を読まれてしまうことがない
        writeRaw(s, "eval " + shellQuote(line) + "; " + doneMarker(s.seq) + "\n");
    }

    /** シェルの 1 つの引数としてそのまま渡せるように ' で囲む。 */
    private static String shellQuote(String text) {
        return "'" + text.replace("'", "'\\''") + "'";
    }

    private void writeRaw(Shell s, String text) {
        try {
            s.stdin.write(text.getBytes(StandardCharsets.UTF_8));
            s.stdin.flush();
        } catch (IOException e) {
            // シェルが終わっている: waitForExit() が知らせる
        }
    }

    /**
     * 出力を読んで画面へ流す。改行の無い出力(プロンプトなど)も、続きが来なければそのまま出す。
     * 標準出力に現れた目印は画面に出さず、状態の知らせとして扱う。
     */
    private void pump(Shell s, InputStream in, boolean stderr) {
        StringBuilder pending = new StringBuilder();
        char[] buf = new char[4096];
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            int n;
            while ((n = reader.read(buf)) != -1) {
                pending.append(buf, 0, n);
                int nl;
                while ((nl = pending.indexOf("\n")) >= 0) {
                    String line = pending.substring(0, nl);
                    pending.delete(0, nl + 1);
                    handleLine(s, line, stderr, true);
                }
                // 続きがすぐに来ない改行無しの出力は今出す(目印の途中かもしれないものは待つ)
                if (pending.length() > 0 && !reader.ready() && pending.indexOf("\u001e") < 0) {
                    handleLine(s, pending.toString(), stderr, false);
                    pending.setLength(0);
                }
            }
        } catch (IOException e) {
            // シェルを止めた時など
        }
        if (pending.length() > 0) handleLine(s, pending.toString(), stderr, false);
    }

    private void handleLine(Shell s, String line, boolean stderr, boolean newline) {
        int m = line.indexOf(MARK);
        if (m >= 0) {
            int end = line.indexOf(MARK_END, m);
            if (end > m) {
                if (m > 0) emit(line.substring(0, m), stderr, true);
                handleMarker(s, line.substring(m + MARK.length(), end));
                String rest = line.substring(end + MARK_END.length());
                if (!rest.isEmpty()) emit(rest, stderr, newline);
                return;
            }
        }
        emit(line, stderr, newline);
    }

    private void handleMarker(Shell s, String body) {
        if (body.startsWith("pid:")) {
            try {
                s.pid = Integer.parseInt(body.substring(4).trim());
            } catch (NumberFormatException ignored) {
            }
        } else if (body.startsWith("user:")) {
            js("onShellReady(" + s.root + ", '" + escapeForJS(body.substring(5).trim()) + "')");
        } else if (body.startsWith("done:")) {
            // done:番号:終了コード:フォルダ
            String[] parts = body.substring(5).split(":", 3);
            int seq = -1;
            int code = 0;
            try {
                seq = Integer.parseInt(parts[0]);
                if (parts.length > 1) code = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
            String cwd = parts.length > 2 ? parts[2] : "";
            // ^C の時に送り直した目印などで、もう終わったコマンドの分は無視する
            if (!s.busy || seq != s.seq) return;
            s.busy = false;
            s.interrupts = 0;
            js("onCommandFinished(" + code + ", '" + escapeForJS(cwd) + "')");
        }
    }

    /** 1 行(newline=false なら改行無しの途中)を画面へ出す。 */
    private void emit(String text, boolean stderr, boolean newline) {
        js("appendChunk('" + escapeForJS(text) + "', " + stderr + ", " + newline + ")");
    }

    private void waitForExit(Shell s) {
        int code;
        try {
            code = s.process.waitFor();
        } catch (InterruptedException e) {
            return;
        }
        s.alive = false;
        boolean current;
        synchronized (lock) {
            current = shell == s;
            if (current) shell = null;
        }
        if (!current || closing) return;
        if (s.root && s.pid < 0) {
            // 許可されなかった(su がすぐに終わった)
            js(Lang.t("appendOutput('ERROR: ルート権限を得られませんでした(終了コード ") + code + ")')");
        } else {
            js(Lang.t("appendOutput('INFO: シェルが終了しました(終了コード ") + code + ")')");
        }
        // root を抜けた・シェルが終わった時は、普通のシェルを立ち上げ直す
        js("onShellExited(" + s.root + ")");
        try {
            shellExecutor.execute(() -> startShell(false));
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // 画面を閉じた後
        }
    }

    /**
     * ^C。端末ではないので Ctrl+C の信号はシェルに届かない。代わりにシェルの子プロセス
     * (= 実行中のコマンド)へ SIGINT を送る。2 回目は SIGKILL で止める。
     */
    private void interruptCommand() {
        Shell s;
        synchronized (lock) {
            s = shell;
        }
        if (s == null || !s.busy) {
            js(Lang.t("showToast('実行中のコマンドはありません')"));
            return;
        }
        s.interrupts++;
        String signal = s.interrupts >= 2 ? "KILL" : "INT";
        if (s.pid > 0) {
            // toybox の pkill は名前のパターンが必須なので、何にでも当てはまる "." を付ける
            String cmd = "pkill -" + signal + " -P " + s.pid + " .";
            try {
                Process p = s.root
                        ? new ProcessBuilder(findSuOr("su"), "-c", cmd).start()
                        : new ProcessBuilder("/system/bin/sh", "-c", cmd).start();
                p.waitFor();
            } catch (Exception ignored) {
            }
        }
        if (s.interrupts >= 3 || s.pid <= 0) {
            // それでも止まらなければ、シェルごと立ち上げ直す
            js(Lang.t("appendOutput('INFO: 止まらないため、シェルを立ち上げ直します')"));
            synchronized (lock) {
                if (shell == s) shell = null;
            }
            destroyShell(s);
            js("onCommandFinished(130, null)");
            startShell(s.root);
            return;
        }
        js("appendOutput('INFO: ^C" + (signal.equals("KILL") ? Lang.t(" (強制終了)") : "") + "')");
    }

    private void destroyShell(Shell s) {
        s.alive = false;
        try {
            s.stdin.write("exit\n".getBytes(StandardCharsets.UTF_8));
            s.stdin.flush();
        } catch (IOException ignored) {
        }
        try {
            s.stdin.close();
        } catch (IOException ignored) {
        }
        s.process.destroy();
    }

    private static String findSuOr(String fallback) {
        String su = findSu();
        return su != null ? su : fallback;
    }

    /** su の場所。見つからなければ null。 */
    private static String findSu() {
        for (String path : SU_PATHS) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) return path;
        }
        String envPath = System.getenv("PATH");
        if (envPath != null) {
            for (String dir : envPath.split(":")) {
                if (dir.isEmpty()) continue;
                File f = new File(dir, "su");
                if (f.exists() && f.canExecute()) return f.getAbsolutePath();
            }
        }
        return null;
    }

    /** 画面へ送る JavaScript。出力が多い時に 1 行ずつ送ると重いので、少しまとめてから送る。 */
    private final StringBuilder jsQueue = new StringBuilder();
    private boolean jsFlushPosted;
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable jsFlush = () -> {
        String script;
        synchronized (jsQueue) {
            script = jsQueue.toString();
            jsQueue.setLength(0);
            jsFlushPosted = false;
        }
        if (!script.isEmpty() && !isDestroyed()) webView.evaluateJavascript(script, null);
    };

    private void js(String script) {
        synchronized (jsQueue) {
            jsQueue.append(script).append(";\n");
            if (jsFlushPosted) return;
            jsFlushPosted = true;
        }
        uiHandler.postDelayed(jsFlush, 16);
    }

    private static String escapeForJS(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                case ' ': sb.append("\\u2028"); break;
                case ' ': sb.append("\\u2029"); break;
                default:
                    if (c < 0x20 && c != '\t') {
                        // 制御文字(エスケープシーケンスなど)は表示しない
                        if (c == 0x1b) sb.append("\\u001b");
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
