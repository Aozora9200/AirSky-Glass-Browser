package com.airsky.browser;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Python のプログラムを動かす画面。
 *
 * <p>Python は Android 内蔵のシェル(/system/bin/sh)から別のプロセスとして起動し、標準出力・標準エラーを
 * コンソールに表示、下の入力欄から標準入力へ送る。tkinter / wx / flet の画面を作るプログラムは、
 * {@link PyGui} が「ウィンドウ」に Android の部品で表示する。画面を閉じると Python も終わる。</p>
 */
public class PyRunActivity extends AppCompatActivity implements PyGui.Host {

    static final String EXTRA_MODE = "py_mode";
    static final String EXTRA_ID = "py_id";
    static final String EXTRA_NAME = "py_name";
    static final String EXTRA_ARGS = "py_args";
    static final String MODE_RUN = "run";
    static final String MODE_REPL = "repl";
    static final String MODE_PIP = "pip";

    private static final int MAX_CONSOLE = 200_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView console, status;
    private NestedScrollView consoleScroll;
    private FrameLayout windowArea;
    private View keypad, keysButton, inputBar;
    private MaterialButtonToggleGroup tabs;
    private EditText input;
    private ImageButton stopButton;
    private LinearProgressIndicator progress;

    private PyGui gui;
    @Nullable private Process process;
    @Nullable private OutputStream stdin;
    private volatile boolean running;
    private boolean windowShownOnce;
    private boolean keypadOpen;
    private String mode = MODE_RUN;
    private String name = "Python";
    @Nullable private PyStore.Entry entry;
    private File workDir;
    private int colorErr, colorEcho, colorInfo;
    private final AtomicBoolean installCancel = new AtomicBoolean();
    @Nullable private PyGui.ValueCallback<Uri> pickCallback;

    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                PyGui.ValueCallback<Uri> cb = pickCallback;
                pickCallback = null;
                if (cb != null) cb.accept(uri);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_py_run);
        console = findViewById(R.id.pr_console);
        console.setText("", TextView.BufferType.EDITABLE);
        consoleScroll = findViewById(R.id.pr_console_scroll);
        status = findViewById(R.id.pr_status);
        windowArea = findViewById(R.id.pr_window_area);
        tabs = findViewById(R.id.pr_tabs);
        keypad = findViewById(R.id.pr_keypad);
        keysButton = findViewById(R.id.pr_keys);
        inputBar = findViewById(R.id.pr_input_bar);
        input = findViewById(R.id.pr_input);
        stopButton = findViewById(R.id.pr_stop);
        progress = findViewById(R.id.pr_progress);

        colorErr = 0xFFD93025;
        colorEcho = ToolbarShortcut.attr(this, androidx.appcompat.R.attr.colorPrimary);
        colorInfo = ToolbarShortcut.attr(this, com.google.android.material.R.attr.colorOnSurfaceVariant);

        Intent i = getIntent();
        mode = i.getStringExtra(EXTRA_MODE) != null ? i.getStringExtra(EXTRA_MODE) : MODE_RUN;
        String id = i.getStringExtra(EXTRA_ID);
        name = i.getStringExtra(EXTRA_NAME) != null ? i.getStringExtra(EXTRA_NAME) : "Python";
        if (MODE_REPL.equals(mode)) {
            workDir = new File(getFilesDir(), "python-home");
            workDir.mkdirs();
        } else {
            entry = PyStore.find(this, id);
            if (entry == null) {
                finish();
                return;
            }
            name = entry.name;
            workDir = PyStore.dir(this, entry.id);
        }
        ((TextView) findViewById(R.id.pr_title)).setText(MODE_PIP.equals(mode) ? name + Lang.t(" のパッケージ") : name);
        setStatus(Lang.t("準備しています…"));

        findViewById(R.id.pr_back).setOnClickListener(v -> onBackPressed());
        stopButton.setOnClickListener(v -> stopOrRestart());
        findViewById(R.id.pr_send).setOnClickListener(v -> send());
        input.setOnEditorActionListener((v, action, e) -> {
            if (action == EditorInfo.IME_ACTION_SEND || action == EditorInfo.IME_ACTION_DONE
                    || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN)) {
                send();
                return true;
            }
            return false;
        });
        if (MODE_REPL.equals(mode)) input.setHint(Lang.t(">>> Python のコードを入力"));
        tabs.check(R.id.pr_tab_console);
        tabs.addOnButtonCheckedListener((g, checkedId, checked) -> {
            if (checked) showPanel(checkedId == R.id.pr_tab_window);
        });
        keysButton.setOnClickListener(v -> {
            keypadOpen = !keypadOpen;
            keypad.setVisibility(keypadOpen ? View.VISIBLE : View.GONE);
        });
        buildKeypad();

        gui = new PyGui(this, this, windowArea);
        writeRunning(MODE_REPL.equals(mode) ? PyStore.REPL_ID : (entry != null ? entry.id : ""));
        ensureRuntime();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        installCancel.set(true);
        killProcess();
        if (gui != null) gui.close();
        if (isFinishing()) {
            //noinspection ResultOfMethodCallIgnored
            PyStore.runningFile(this).delete();
        }
    }

    @Override
    public void onBackPressed() {
        if (running) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(Lang.t("プログラムを終了しますか?"))
                    .setMessage(Lang.t("「") + name + Lang.t("」はまだ動いています。"))
                    .setPositiveButton(Lang.t("終了"), (d, w) -> finishProgram())
                    .setNegativeButton(Lang.t("キャンセル"), null)
                    .show();
            return;
        }
        finishProgram();
    }

    private void finishProgram() {
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down);
    }

    private void writeRunning(String id) {
        try {
            PythonRuntime.writeText(PyStore.runningFile(this), id);
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Python 本体
    // ------------------------------------------------------------------

    private void ensureRuntime() {
        if (!PythonRuntime.supported(this)) {
            setStatus(Lang.t("この端末では使えません"));
            info(Lang.t("この端末の CPU(32bit)では Python を実行できません。\n"), true);
            showReplay(false);
            return;
        }
        if (PythonRuntime.installed(this)) {
            prepareAndStart();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("Python をダウンロードしますか?"))
                .setMessage("Python " + PythonRuntime.VERSION + Lang.t(" の本体(約 23MB、展開後 約 60MB)を python.org から"
                        + "ダウンロードします。最初の 1 回だけ必要です。"))
                .setPositiveButton(Lang.t("ダウンロード"), (d, w) -> install())
                .setNegativeButton(Lang.t("キャンセル"), (d, w) -> finishProgram())
                .setCancelable(false)
                .show();
    }

    private void install() {
        progress.setVisibility(View.VISIBLE);
        info("Python " + PythonRuntime.VERSION + Lang.t(" をダウンロードしています…\n"), false);
        new Thread(() -> {
            String error = null;
            try {
                PythonRuntime.install(getApplicationContext(), (msg, pct) -> ui.post(() -> {
                    setStatus(msg);
                    if (pct >= 0) {
                        if (progress.isIndeterminate()) {
                            progress.setVisibility(View.INVISIBLE);
                            progress.setIndeterminate(false);
                            progress.setVisibility(View.VISIBLE);
                        }
                        progress.setProgressCompat(pct, true);
                    }
                }), installCancel);
            } catch (IOException e) {
                error = e.getMessage();
            }
            String err = error;
            ui.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                if (err != null) {
                    info(Lang.t("\nダウンロードできませんでした: ") + err + "\n", true);
                    setStatus(Lang.t("ダウンロードできませんでした"));
                    showReplay(true);
                    return;
                }
                info(Lang.t("Python を入れました。\n\n"), false);
                prepareAndStart();
            });
        }, "py-install").start();
    }

    private void prepareAndStart() {
        setStatus(Lang.t("準備しています…"));
        new Thread(() -> {
            String error = null;
            try {
                PythonRuntime.ensureShims(getApplicationContext());
            } catch (IOException e) {
                error = e.getMessage();
            }
            String err = error;
            ui.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (err != null) {
                    info(Lang.t("準備できませんでした: ") + err + "\n", true);
                    showReplay(true);
                    return;
                }
                start();
            });
        }, "py-prepare").start();
    }

    // ------------------------------------------------------------------
    // 実行
    // ------------------------------------------------------------------

    private void start() {
        int port;
        try {
            port = gui.start();
        } catch (IOException e) {
            port = 0;
        }
        File project = MODE_REPL.equals(mode) ? null : workDir;
        PythonRuntime.Launch l = new PythonRuntime.Launch(workDir);
        PythonRuntime.baseEnv(this, l, project);
        if (port > 0) {
            l.env.put("AIRSKY_GUI_PORT", String.valueOf(port));
            l.env.put("AIRSKY_GUI_TOKEN", gui.token);
        }
        String args = getIntent().getStringExtra(EXTRA_ARGS);
        switch (mode) {
            case MODE_REPL:
                l.env.put("PYTHON_BASIC_REPL", "1");
                l.pythonArgs.add("-u");
                l.pythonArgs.add("-i");
                l.pythonArgs.add("-q");
                info("Python " + PythonRuntime.VERSION + Lang.t(" の対話モードです。下の欄にコードを入力して送信します。\n"
                        + "終わる時は exit() を入力するか、右上の停止を押します。\n\n"), false);
                break;
            case MODE_PIP: {
                File pkgs = PythonRuntime.packagesDir(workDir);
                pkgs.mkdirs();
                l.env.put("AIRSKY_PKGS", pkgs.getPath());
                l.shellScript = "\"$AIRSKY_PYTHON\" -m pip --version >/dev/null 2>&1 || "
                        + "\"$AIRSKY_PYTHON\" -m ensurepip --default-pip || exit 1\n"
                        + "exec \"$AIRSKY_PYTHON\" -m pip install --disable-pip-version-check --no-warn-script-location "
                        + "--prefer-binary --upgrade --target \"$AIRSKY_PKGS\" \"$@\"";
                l.pythonArgs.addAll(PyStore.splitArgs(args));
                info("$ pip install " + (args != null ? args : "") + "\n", false);
                info(Lang.t("入れたパッケージは、このプログラムだけで使えます(C などで書かれた部分を含むものは使えないことがあります)。\n\n"), false);
                break;
            }
            default: {
                PyStore.Entry e = entry;
                if (e == null) return;
                File main = PyStore.mainFile(this, e);
                if (!main.isFile()) {
                    info(Lang.t("実行するファイルが見つかりません: ") + e.main + "\n", true);
                    showReplay(true);
                    return;
                }
                // .env の値を環境変数に入れる(python-dotenv を使わないプログラムでも os.environ から読める)
                Map<String, String> dotenv = PythonRuntime.readDotEnv(new File(workDir, ".env"), l.env);
                for (Map.Entry<String, String> kv : dotenv.entrySet()) {
                    if (!l.env.containsKey(kv.getKey())) l.env.put(kv.getKey(), kv.getValue());
                }
                l.env.put("AIRSKY_SCRIPT", main.getPath());
                l.pythonArgs.add("-u");
                l.pythonArgs.add(e.main);
                l.pythonArgs.addAll(PyStore.splitArgs(args));
                if (!dotenv.isEmpty()) info(Lang.t("[.env の ") + dotenv.size() + Lang.t(" 個の値を環境変数に入れました]\n"), false);
            }
        }

        try {
            Process p = PythonRuntime.processBuilder(l).start();
            process = p;
            stdin = p.getOutputStream();
            running = true;
            setStatus(MODE_PIP.equals(mode) ? Lang.t("パッケージを入れています…") : Lang.t("実行中"));
            stopButton.setImageResource(R.drawable.ic_jr_stop);
            stopButton.setContentDescription(Lang.t("停止"));
            pump(p.getInputStream(), false);
            pump(p.getErrorStream(), true);
            Thread waiter = new Thread(() -> {
                int code;
                try {
                    code = p.waitFor();
                } catch (InterruptedException e) {
                    return;
                }
                ui.postDelayed(() -> onExit(p, code), 150);
            }, "py-wait");
            waiter.setDaemon(true);
            waiter.start();
        } catch (IOException e) {
            info(Lang.t("Python を起動できませんでした: ") + e.getMessage() + "\n", true);
            setStatus(Lang.t("起動できませんでした"));
            showReplay(true);
        }
    }

    private void onExit(Process p, int code) {
        if (p != process) return;
        running = false;
        process = null;
        stdin = null;
        gui.reset();
        if (MODE_PIP.equals(mode)) {
            info(code == 0 ? Lang.t("\n[パッケージを入れました]\n") : Lang.t("\n[パッケージを入れられませんでした (終了コード ") + code + ")]\n", code != 0);
        } else if (code == 143 || code == 137) {
            info(Lang.t("\n[停止しました]\n"), true);
        } else {
            info(Lang.t("\n[プログラムが終了しました") + (code != 0 ? Lang.t(" (終了コード ") + code + ")" : "") + "]\n", code != 0);
        }
        setStatus(code == 0 ? Lang.t("終了しました") : Lang.t("終了しました (コード ") + code + ")");
        showReplay(true);
    }

    private void showReplay(boolean canReplay) {
        stopButton.setImageResource(R.drawable.ic_jr_replay);
        stopButton.setContentDescription(Lang.t("もう一度実行"));
        stopButton.setVisibility(canReplay ? View.VISIBLE : View.GONE);
        running = false;
    }

    private void killProcess() {
        Process p = process;
        if (p == null) return;
        p.destroy();
        new Thread(() -> {
            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) {
            }
            if (p.isAlive()) p.destroyForcibly();
        }, "py-kill").start();
    }

    private void stopOrRestart() {
        if (running) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(Lang.t("プログラムを停止しますか?"))
                    .setPositiveButton(Lang.t("停止"), (d, w) -> {
                        setStatus(Lang.t("停止しています…"));
                        killProcess();
                    })
                    .setNegativeButton(Lang.t("キャンセル"), null)
                    .show();
        } else {
            // もう一度実行: 同じ内容で画面を作り直す(古い画面と Python は片付く)
            Intent again = new Intent(getIntent()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(again);
            overridePendingTransition(0, 0);
        }
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    // ------------------------------------------------------------------
    // コンソール
    // ------------------------------------------------------------------

    private void pump(InputStream in, boolean err) {
        Thread t = new Thread(() -> {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
            ByteBuffer bytes = ByteBuffer.allocate(16384);
            CharBuffer chars = CharBuffer.allocate(16384);
            byte[] buf = new byte[8192];
            Ansi ansi = new Ansi(err);
            try {
                int n;
                while ((n = in.read(buf)) > 0) {
                    int off = 0;
                    while (off < n) {
                        int k = Math.min(n - off, bytes.remaining());
                        bytes.put(buf, off, k);
                        off += k;
                        bytes.flip();
                        decoder.decode(bytes, chars, false);
                        bytes.compact();
                        chars.flip();
                        if (chars.hasRemaining()) ansi.feed(chars.toString());
                        chars.clear();
                    }
                }
            } catch (IOException ignored) {
            }
        }, err ? "py-stderr" : "py-stdout");
        t.setDaemon(true);
        t.start();
    }

    /** ANSI のエスケープ(色)を解釈して、コンソールの文字にする。 */
    private final class Ansi {
        final boolean err;
        int color;
        boolean bold;
        final StringBuilder pendingEsc = new StringBuilder();

        Ansi(boolean err) {
            this.err = err;
            color = err ? colorErr : 0;
        }

        void feed(String s) {
            SpannableStringBuilder out = new SpannableStringBuilder();
            int i = 0;
            String text = pendingEsc.length() > 0 ? pendingEsc + s : s;
            pendingEsc.setLength(0);
            while (i < text.length()) {
                char ch = text.charAt(i);
                if (ch == 0x1b) {
                    int end = i + 1;
                    if (end < text.length() && text.charAt(end) == '[') {
                        end++;
                        while (end < text.length() && !Character.isLetter(text.charAt(end))) end++;
                        if (end >= text.length()) {
                            pendingEsc.append(text.substring(i));
                            break;
                        }
                        if (text.charAt(end) == 'm') sgr(text.substring(i + 2, end));
                        i = end + 1;
                        continue;
                    }
                    i = Math.min(text.length(), i + 2);
                    continue;
                }
                int start = out.length();
                int j = i;
                while (j < text.length() && text.charAt(j) != 0x1b) j++;
                out.append(text, i, j);
                if (color != 0) out.setSpan(new ForegroundColorSpan(color), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (bold) out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = j;
            }
            if (out.length() > 0) append(out);
        }

        private void sgr(String params) {
            if (params.isEmpty()) params = "0";
            for (String p : params.split(";")) {
                int v;
                try {
                    v = Integer.parseInt(p);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (v == 0) {
                    color = err ? colorErr : 0;
                    bold = false;
                } else if (v == 1) {
                    bold = true;
                } else if (v == 22) {
                    bold = false;
                } else if (v >= 30 && v <= 37) {
                    color = ANSI[v - 30];
                } else if (v >= 90 && v <= 97) {
                    color = ANSI[v - 90 + 8];
                } else if (v == 39) {
                    color = err ? colorErr : 0;
                }
            }
        }
    }

    private static final int[] ANSI = {
            0xFF5F6368, 0xFFD93025, 0xFF188038, 0xFFB06000, 0xFF1A73E8, 0xFFA142F4, 0xFF007B83, 0xFF80868B,
            0xFF80868B, 0xFFEE675C, 0xFF5BB974, 0xFFFCC934, 0xFF4285F4, 0xFFAF5CF7, 0xFF4ECDE6, 0xFFBDC1C6};

    private final SpannableStringBuilder pending = new SpannableStringBuilder();
    private boolean flushScheduled;

    private void info(String text, boolean error) {
        SpannableStringBuilder s = new SpannableStringBuilder(text);
        s.setSpan(new ForegroundColorSpan(error ? colorErr : colorInfo), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (error) s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        append(s);
    }

    private void append(CharSequence text) {
        synchronized (pending) {
            pending.append(text);
            if (!flushScheduled) {
                flushScheduled = true;
                ui.postDelayed(this::flushConsole, 40);
            }
        }
    }

    private void flushConsole() {
        CharSequence chunk;
        synchronized (pending) {
            flushScheduled = false;
            if (pending.length() == 0) return;
            chunk = new SpannableStringBuilder(pending);
            pending.clear();
            pending.clearSpans();
        }
        boolean atBottom = consoleScroll.getScrollY() + consoleScroll.getHeight() >= console.getHeight() - 40;
        Editable ed = (Editable) console.getText();
        // \r(改行なし)は行の頭に戻る: 進み具合の表示などを同じ行に上書きする
        int start = 0;
        for (int i = 0; i < chunk.length(); i++) {
            char ch = chunk.charAt(i);
            if (ch == '\r') {
                ed.append(chunk.subSequence(start, i));
                boolean crlf = i + 1 < chunk.length() && chunk.charAt(i + 1) == '\n';
                if (!crlf) {
                    int lineStart = lastIndexOf(ed, '\n') + 1;
                    ed.delete(lineStart, ed.length());
                }
                start = i + 1;
            }
        }
        ed.append(chunk.subSequence(start, chunk.length()));
        if (ed.length() > MAX_CONSOLE) ed.delete(0, ed.length() - MAX_CONSOLE * 3 / 4);
        if (atBottom) consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN));
    }

    private static int lastIndexOf(CharSequence s, char c) {
        for (int i = s.length() - 1; i >= 0; i--) if (s.charAt(i) == c) return i;
        return -1;
    }

    private void send() {
        String line = input.getText().toString();
        input.setText("");
        SpannableStringBuilder echo = new SpannableStringBuilder((MODE_REPL.equals(mode) ? "" : "> ") + line + "\n");
        echo.setSpan(new ForegroundColorSpan(colorEcho), 0, echo.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        append(echo);
        OutputStream os = stdin;
        if (os == null) return;
        new Thread(() -> {
            try {
                os.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
            } catch (IOException ignored) {
            }
        }, "py-stdin").start();
    }

    // ------------------------------------------------------------------
    // ウィンドウ(PyGui.Host)
    // ------------------------------------------------------------------

    @Override
    public void onWindowsChanged(int visibleWindows) {
        if (visibleWindows > 0) {
            tabs.setVisibility(View.VISIBLE);
            if (!windowShownOnce) {
                windowShownOnce = true;
                tabs.check(R.id.pr_tab_window);
            }
        } else if (windowShownOnce) {
            tabs.check(R.id.pr_tab_console);
            tabs.setVisibility(View.GONE);
            windowShownOnce = false;
        }
    }

    @Override
    public void pickDocument(@NonNull PyGui.ValueCallback<Uri> done) {
        pickCallback = done;
        try {
            picker.launch(new String[]{"*/*"});
        } catch (RuntimeException e) {
            pickCallback = null;
            done.accept(null);
        }
    }

    @NonNull
    @Override
    public File workDir() {
        return workDir;
    }

    private void showPanel(boolean window) {
        windowArea.setVisibility(window ? View.VISIBLE : View.GONE);
        consoleScroll.setVisibility(window ? View.GONE : View.VISIBLE);
        inputBar.setVisibility(window ? View.GONE : View.VISIBLE);
        keysButton.setVisibility(window ? View.VISIBLE : View.GONE);
        keypad.setVisibility(window && keypadOpen ? View.VISIBLE : View.GONE);
        if (window) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    // ------------------------------------------------------------------
    // キー
    // ------------------------------------------------------------------

    private void buildKeypad() {
        LinearLayout row = (LinearLayout) keypad;
        String[][] keys = {{"←", "Left", ""}, {"↑", "Up", ""}, {"↓", "Down", ""}, {"→", "Right", ""},
                {"Space", "space", " "}, {"Enter", "Return", "\r"}, {"Esc", "Escape", "\u001b"}, {"Aa", "", ""}};
        float d = getResources().getDisplayMetrics().density;
        for (String[] k : keys) {
            TextView b = new TextView(this);
            b.setText(k[0]);
            b.setGravity(Gravity.CENTER);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, k[0].length() > 2 ? 13 : 18);
            b.setTextColor(ToolbarShortcut.attr(this, com.google.android.material.R.attr.colorOnSecondaryContainer));
            b.setBackgroundResource(R.drawable.bg_jr_key);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.round(48 * d), k[0].length() > 2 ? 1.4f : 1f);
            lp.setMarginStart(Math.round(3 * d));
            lp.setMarginEnd(Math.round(3 * d));
            row.addView(b, lp);
            if (k[1].isEmpty()) {
                b.setOnClickListener(v -> typeText());
                continue;
            }
            b.setOnTouchListener((v, ev) -> {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        gui.sendKey(true, k[1], k[2], 0, new ArrayList<>());
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        gui.sendKey(false, k[1], k[2], 0, new ArrayList<>());
                        return true;
                    default:
                        return true;
                }
            });
        }
    }

    private void typeText() {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        FrameLayout box = new FrameLayout(this);
        int p = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(p, p / 2, p, 0);
        box.addView(e);
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("キーとして送る文字"))
                .setView(box)
                .setPositiveButton(Lang.t("送る"), (d, w) -> {
                    for (char c : e.getText().toString().toCharArray()) {
                        String ks = Character.isLetterOrDigit(c) ? String.valueOf(c) : PyGui.keysym(0, c);
                        if (c == ' ') ks = "space";
                        gui.sendKey(true, ks, String.valueOf(c), c, new ArrayList<>());
                        gui.sendKey(false, ks, String.valueOf(c), c, new ArrayList<>());
                    }
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
        e.requestFocus();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean windowMode = windowArea.getVisibility() == View.VISIBLE && gui != null && gui.hasWindow();
        View focus = getCurrentFocus();
        boolean editing = focus instanceof EditText;
        if (windowMode && code != KeyEvent.KEYCODE_BACK && !editing
                && code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) {
            int u = event.getUnicodeChar();
            String ch = u > 0 ? String.valueOf((char) u) : "";
            String ks = PyGui.keysym(code, u);
            List<String> mods = new ArrayList<>();
            if (event.isShiftPressed()) mods.add("Shift");
            if (event.isCtrlPressed()) mods.add("Control");
            if (event.isAltPressed()) mods.add("Alt");
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                gui.sendKey(true, ks, ch, u > 0 ? u : code, mods);
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                gui.sendKey(false, ks, ch, u > 0 ? u : code, mods);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
