package com.airsky.browser;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.airsky.browser.jvm.JvmExit;
import com.airsky.browser.jvm.JvmFiles;
import com.airsky.browser.jvm.JvmInput;
import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.Window;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JAR 実行ツールの実行画面。専用のプロセス(:jvm)で 1 つのプログラムを動かす。
 *
 * <p>コンソールには System.out / System.err を表示し、下の入力欄から System.in へ送る。
 * プログラムが AWT / Swing のウィンドウを出すと「ウィンドウ」に切り替わり、タッチでマウス、
 * キーのボタン(または物理キーボード)でキーの操作ができる。画面を閉じるとプロセスごと終わる。</p>
 */
public class JarRunActivity extends AppCompatActivity implements JvmRuntime.Host {

    static final String EXTRA_ID = "jar_id";
    static final String EXTRA_NAME = "jar_name";
    static final String EXTRA_ARGS = "jar_args";

    private static final int MAX_CONSOLE = 200_000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView console, status, windowTitle;
    private NestedScrollView consoleScroll;
    private View windowPanel, keypad, keysButton;
    private JvmWindowView windowView;
    private MaterialButtonToggleGroup tabs;
    private EditText input;
    private ImageButton stopButton;

    private final SpannableStringBuilder pending = new SpannableStringBuilder();
    private boolean flushScheduled;
    private final Stdin stdin = new Stdin();
    private volatile boolean running;
    private volatile boolean mainDone;
    private boolean windowShownOnce;
    private boolean keypadOpen;
    private String jarName = "Java";
    private int colorErr, colorEcho, colorInfo;
    private ToneGenerator tone;
    private final Runnable recheck = this::checkFinished;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jar_run);
        console = findViewById(R.id.jr_console);
        consoleScroll = findViewById(R.id.jr_console_scroll);
        status = findViewById(R.id.jr_status);
        windowPanel = findViewById(R.id.jr_window_panel);
        windowView = findViewById(R.id.jr_window);
        windowTitle = findViewById(R.id.jr_window_title);
        tabs = findViewById(R.id.jr_tabs);
        keypad = findViewById(R.id.jr_keypad);
        keysButton = findViewById(R.id.jr_keys);
        input = findViewById(R.id.jr_input);
        stopButton = findViewById(R.id.jr_stop);

        colorErr = 0xFFD93025;
        colorEcho = ToolbarShortcut.attr(this, androidx.appcompat.R.attr.colorPrimary);
        colorInfo = ToolbarShortcut.attr(this, com.google.android.material.R.attr.colorOnSurfaceVariant);

        String id = getIntent().getStringExtra(EXTRA_ID);
        JarStore.Entry entry = JarStore.find(this, id);
        if (entry == null) {
            finish();
            return;
        }
        jarName = entry.name;
        ((TextView) findViewById(R.id.jr_title)).setText(jarName);
        setStatus(Lang.t("準備しています…"));

        findViewById(R.id.jr_back).setOnClickListener(v -> onBackPressed());
        stopButton.setOnClickListener(v -> stopOrRestart());
        findViewById(R.id.jr_send).setOnClickListener(v -> send());
        input.setOnEditorActionListener((v, action, e) -> {
            if (action == EditorInfo.IME_ACTION_SEND || action == EditorInfo.IME_ACTION_DONE
                    || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN)) {
                send();
                return true;
            }
            return false;
        });
        tabs.check(R.id.jr_tab_console);
        tabs.addOnButtonCheckedListener((g, checkedId, checked) -> {
            if (checked) showPanel(checkedId == R.id.jr_tab_window);
        });
        findViewById(R.id.jr_window_close).setOnClickListener(v -> {
            Window w = JvmRuntime.topWindow();
            if (w != null) JvmRuntime.invokeLater(w::jvm$closeRequested);
        });
        keysButton.setOnClickListener(v -> {
            keypadOpen = !keypadOpen;
            keypad.setVisibility(keypadOpen ? View.VISIBLE : View.GONE);
        });
        buildKeypad();

        writeRunning(entry.id);
        start(entry, getIntent().getStringExtra(EXTRA_ARGS));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 実行中に同じ画面がもう一度開かれた(前に出すだけ)
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            JarStore.runningFile(this).delete();
            // プログラムが作ったスレッドも含めて、プロセスごと終わらせる
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    @Override
    public void onBackPressed() {
        if (running && !mainDone || running && !JvmRuntime.windows().isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(Lang.t("プログラムを終了しますか?"))
                    .setMessage(Lang.t("「") + jarName + Lang.t("」はまだ動いています。"))
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
        try (OutputStream out = new FileOutputStream(JarStore.runningFile(this))) {
            out.write(id.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------
    // 実行
    // ------------------------------------------------------------------

    private void start(JarStore.Entry entry, @Nullable String args) {
        running = true;
        ThreadGroup group = new ThreadGroup("program");
        JvmRuntime.install(this, group);
        File home = JarStore.homeDir(this, entry);
        if (!home.isDirectory()) home.mkdirs();
        JvmFiles.setHome(home);

        PrintStream out = new PrintStream(new ConsoleOut(false), true);
        PrintStream err = new PrintStream(new ConsoleOut(true), true);
        try {
            out = new PrintStream(new ConsoleOut(false), true, "UTF-8");
            err = new PrintStream(new ConsoleOut(true), true, "UTF-8");
        } catch (java.io.UnsupportedEncodingException ignored) {
        }
        System.setOut(out);
        System.setErr(err);
        System.setIn(stdin);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            if (e instanceof JvmExit || JvmRuntime.hasExited()) return;
            System.err.print("Exception in thread \"" + t.getName() + "\" ");
            e.printStackTrace();
            ui.post(this::checkFinished);
        });

        Thread main = new Thread(group, () -> run(entry, args), "main", 64L << 20);
        main.start();
    }

    private void run(JarStore.Entry entry, @Nullable String args) {
        try {
            JarCompiler.Result r = JarCompiler.compile(JarStore.jarFile(this, entry.id),
                    JarStore.cacheDir(this, entry.id), msg -> ui.post(() -> setStatus(msg)));
            String mainClass = r.manifestMain;
            if (mainClass == null) {
                if (r.mains.isEmpty()) throw new IOException(Lang.t("main メソッドのあるクラスが見つかりません"));
                if (r.mains.size() == 1) {
                    mainClass = r.mains.get(0);
                } else {
                    String[] items = r.mains.toArray(new String[0]);
                    int i = showChoice(Lang.t("実行するクラス"), items, -1);
                    if (i < 0) {
                        ui.post(this::finishProgram);
                        return;
                    }
                    mainClass = items[i];
                }
            }
            JvmFiles.setJavaFeature(r.javaFeature);
            ui.post(() -> setStatus(Lang.t("実行中")));
            ClassLoader loader = new JvmClassLoader(r.dex.getPath(), getClassLoader());
            Thread.currentThread().setContextClassLoader(loader);
            Class<?> cls = Class.forName(mainClass, true, loader);
            Method m = cls.getMethod("main", String[].class);
            if (!Modifier.isStatic(m.getModifiers())) throw new IOException(mainClass + Lang.t(".main が static ではありません"));
            m.setAccessible(true);
            m.invoke(null, (Object) splitArgs(args));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (!(cause instanceof JvmExit) && cause != null) {
                System.err.print("Exception in thread \"main\" ");
                cause.printStackTrace();
            }
        } catch (JvmExit ignored) {
        } catch (Throwable e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            info(Lang.t("\n実行できませんでした: ") + msg + "\n", true);
            if (e instanceof LinkageError || e instanceof ClassNotFoundException) {
                info(Lang.t("このプログラムが使う機能の一部(JavaFX など)には対応していません。\n"), false);
            }
        } finally {
            mainDone = true;
            ui.post(this::checkFinished);
        }
    }

    private static String[] splitArgs(@Nullable String args) {
        if (args == null || args.trim().isEmpty()) return new String[0];
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quote = false, any = false;
        for (char c : args.trim().toCharArray()) {
            if (c == '"') {
                quote = !quote;
                any = true;
            } else if (Character.isWhitespace(c) && !quote) {
                if (any) out.add(cur.toString());
                cur.setLength(0);
                any = false;
            } else {
                cur.append(c);
                any = true;
            }
        }
        if (any) out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    /** プログラムが終わったか(main が終わり、ウィンドウも残っていない)を見て表示を変える。 */
    private void checkFinished() {
        if (!running) return;
        if (!JvmRuntime.hasExited() && mainDone && !JvmRuntime.isIdle()) {
            // invokeLater で頼まれた処理(ウィンドウを出すなど)がまだ残っている
            ui.removeCallbacks(recheck);
            ui.postDelayed(recheck, 300);
            return;
        }
        if (JvmRuntime.hasExited() || mainDone && JvmRuntime.windows().isEmpty() && !hasProgramThreads()) {
            running = false;
            if (!JvmRuntime.hasExited()) info(Lang.t("\n[プログラムが終了しました]\n"), false);
            setStatus(Lang.t("終了しました"));
            stopButton.setImageResource(R.drawable.ic_jr_replay);
            stopButton.setContentDescription(Lang.t("もう一度実行"));
        }
    }

    /** プログラムが作った(デーモンでない)スレッドがまだ動いているか。 */
    private boolean hasProgramThreads() {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive() && !t.isDaemon() && t.getThreadGroup() != null
                    && "program".equals(t.getThreadGroup().getName()) && !"main".equals(t.getName())) {
                return true;
            }
        }
        return false;
    }

    private void stopOrRestart() {
        if (running) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(Lang.t("プログラムを停止しますか?"))
                    .setPositiveButton(Lang.t("停止"), (d, w) -> {
                        running = false;
                        info(Lang.t("\n[停止しました]\n"), true);
                        setStatus(Lang.t("停止しました"));
                        JvmRuntime.invokeLater(() -> JvmRuntime.exit(143));
                        stopButton.setImageResource(R.drawable.ic_jr_replay);
                        stopButton.setContentDescription(Lang.t("もう一度実行"));
                    })
                    .setNegativeButton(Lang.t("キャンセル"), null)
                    .show();
        } else {
            // もう一度実行: ブラウザのプロセスの JAR ツールから、新しいプロセスで始め直してもらう
            startActivity(new Intent(this, JarToolActivity.class)
                    .putExtra(JarToolActivity.EXTRA_RUN, getIntent().getStringExtra(EXTRA_ID))
                    .putExtra(JarToolActivity.EXTRA_RUN_ARGS, getIntent().getStringExtra(EXTRA_ARGS))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        }
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    // ------------------------------------------------------------------
    // コンソール
    // ------------------------------------------------------------------

    private void info(String text, boolean error) {
        append(text, error ? colorErr : colorInfo, error);
    }

    private void append(CharSequence text, int color, boolean bold) {
        synchronized (pending) {
            int start = pending.length();
            pending.append(text);
            if (color != 0) {
                pending.setSpan(new ForegroundColorSpan(color), start, pending.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (bold) pending.setSpan(new StyleSpan(Typeface.BOLD), start, pending.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!flushScheduled) {
                flushScheduled = true;
                ui.postDelayed(this::flushConsole, 50);
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
        console.append(chunk);
        CharSequence all = console.getText();
        if (all.length() > MAX_CONSOLE) {
            console.setText(new SpannableStringBuilder(all, all.length() - MAX_CONSOLE * 3 / 4, all.length()));
        }
        if (atBottom) consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN));
    }

    /** System.out / System.err の行き先。UTF-8 として少しずつ文字にする。 */
    private final class ConsoleOut extends OutputStream {
        private final boolean err;
        private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
        private final ByteBuffer bytes = ByteBuffer.allocate(8192);
        private final CharBuffer chars = CharBuffer.allocate(8192);

        ConsoleOut(boolean err) {
            this.err = err;
        }

        @Override
        public synchronized void write(int b) {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            if (JvmRuntime.hasExited() && !err) return;
            while (len > 0) {
                int n = Math.min(len, bytes.remaining());
                bytes.put(b, off, n);
                off += n;
                len -= n;
                bytes.flip();
                decoder.decode(bytes, chars, false);
                bytes.compact();
                chars.flip();
                if (chars.hasRemaining()) append(chars.toString(), err ? colorErr : 0, false);
                chars.clear();
            }
        }
    }

    /** System.in。入力欄から送った行が入る。 */
    private final class Stdin extends InputStream {
        private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
        private byte[] current;
        private int pos;
        private boolean eof;

        void offer(String line) {
            queue.add((line + "\n").getBytes(StandardCharsets.UTF_8));
        }

        void close0() {
            queue.add(new byte[0]);
        }

        private boolean fill() {
            if (eof) return false;
            while (current == null || pos >= current.length) {
                byte[] next = queue.poll();
                if (next == null) {
                    ui.post(() -> {
                        if (running) setStatus(Lang.t("入力を待っています"));
                        input.requestFocus();
                    });
                    try {
                        next = queue.take();
                    } catch (InterruptedException e) {
                        return false;
                    }
                    ui.post(() -> {
                        if (running) setStatus(Lang.t("実行中"));
                    });
                }
                if (next.length == 0) {
                    eof = true;
                    return false;
                }
                current = next;
                pos = 0;
            }
            return true;
        }

        @Override
        public synchronized int read() {
            if (!fill()) return -1;
            return current[pos++] & 0xFF;
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            if (len == 0) return 0;
            if (!fill()) return -1;
            int n = Math.min(len, current.length - pos);
            System.arraycopy(current, pos, b, off, n);
            pos += n;
            return n;
        }

        @Override
        public synchronized int available() {
            return current != null ? current.length - pos : 0;
        }
    }

    private void send() {
        String line = input.getText().toString();
        input.setText("");
        append("> " + line + "\n", colorEcho, false);
        stdin.offer(line);
    }

    // ------------------------------------------------------------------
    // ウィンドウ
    // ------------------------------------------------------------------

    private void showPanel(boolean window) {
        windowPanel.setVisibility(window ? View.VISIBLE : View.GONE);
        consoleScroll.setVisibility(window ? View.GONE : View.VISIBLE);
        findViewById(R.id.jr_input_bar).setVisibility(window ? View.GONE : View.VISIBLE);
        keysButton.setVisibility(window ? View.VISIBLE : View.GONE);
        keypad.setVisibility(window && keypadOpen ? View.VISIBLE : View.GONE);
        if (window) {
            hideKeyboard();
            windowView.requestFocus();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    @Override
    public void windowsChanged() {
        ui.post(() -> {
            Window top = JvmRuntime.topWindow();
            if (top != null) {
                tabs.setVisibility(View.VISIBLE);
                String t = top.jvm$title();
                windowTitle.setText(t != null && !t.isEmpty() ? t : jarName);
                if (!windowShownOnce) {
                    windowShownOnce = true;
                    tabs.check(R.id.jr_tab_window);
                }
            } else if (windowShownOnce) {
                tabs.check(R.id.jr_tab_console);
                windowTitle.setText("");
                checkFinished();
            }
            windowView.invalidate();
        });
    }

    @Override
    public void present(Window w) {
        if (w == JvmRuntime.topWindow()) windowView.postInvalidateOnAnimation();
    }

    // ------------------------------------------------------------------
    // キー
    // ------------------------------------------------------------------

    private void buildKeypad() {
        LinearLayout row = (LinearLayout) keypad;
        Object[][] keys = {
                {"←", com.airsky.browser.jvm.java.awt.event.KeyEvent.VK_LEFT, (char) 0xFFFF},
                {"↑", com.airsky.browser.jvm.java.awt.event.KeyEvent.VK_UP, (char) 0xFFFF},
                {"↓", com.airsky.browser.jvm.java.awt.event.KeyEvent.VK_DOWN, (char) 0xFFFF},
                {"→", com.airsky.browser.jvm.java.awt.event.KeyEvent.VK_RIGHT, (char) 0xFFFF},
                {"Space", com.airsky.browser.jvm.java.awt.event.KeyEvent.VK_SPACE, ' '},
                {"Enter", com.airsky.browser.jvm.java.awt.event.KeyEvent.VK_ENTER, '\n'},
                {"Esc", com.airsky.browser.jvm.java.awt.event.KeyEvent.VK_ESCAPE, (char) 27},
                {"Aa", -1, (char) 0},
        };
        float d = getResources().getDisplayMetrics().density;
        for (Object[] k : keys) {
            TextView b = new TextView(this);
            b.setText((String) k[0]);
            b.setGravity(Gravity.CENTER);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, ((String) k[0]).length() > 2 ? 13 : 18);
            b.setTextColor(ToolbarShortcut.attr(this, com.google.android.material.R.attr.colorOnSecondaryContainer));
            b.setBackgroundResource(R.drawable.bg_jr_key);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.round(48 * d), ((String) k[0]).length() > 2 ? 1.4f : 1f);
            lp.setMarginStart(Math.round(3 * d));
            lp.setMarginEnd(Math.round(3 * d));
            row.addView(b, lp);
            int code = (Integer) k[1];
            char ch = (Character) k[2];
            if (code < 0) {
                b.setOnClickListener(v -> typeText());
                continue;
            }
            b.setOnTouchListener((v, ev) -> {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        key(com.airsky.browser.jvm.java.awt.event.KeyEvent.KEY_PRESSED, code, ch, 0);
                        if (ch != 0xFFFF) key(com.airsky.browser.jvm.java.awt.event.KeyEvent.KEY_TYPED, 0, ch, 0);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        key(com.airsky.browser.jvm.java.awt.event.KeyEvent.KEY_RELEASED, code, ch, 0);
                        return true;
                    default:
                        return true;
                }
            });
        }
    }

    /** 文字を打つ(入力した文字を 1 文字ずつキーとして送る)。 */
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
                        int code = com.airsky.browser.jvm.java.awt.event.KeyEvent.getExtendedKeyCodeForChar(c);
                        key(com.airsky.browser.jvm.java.awt.event.KeyEvent.KEY_PRESSED, code, c, 0);
                        key(com.airsky.browser.jvm.java.awt.event.KeyEvent.KEY_TYPED, 0, c, 0);
                        key(com.airsky.browser.jvm.java.awt.event.KeyEvent.KEY_RELEASED, code, c, 0);
                    }
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
        e.requestFocus();
    }

    private void key(int id, int code, char ch, int modifiers) {
        Window w = JvmRuntime.topWindow();
        if (w == null) return;
        JvmRuntime.invokeLater(() -> JvmInput.key(w, id, code, ch, modifiers));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean windowMode = windowPanel.getVisibility() == View.VISIBLE && JvmRuntime.topWindow() != null;
        if (windowMode && code != KeyEvent.KEYCODE_BACK && !GamepadInput.isSystemKey(code) && !input.hasFocus()) {
            int vk = vk(code);
            int u = event.getUnicodeChar();
            char ch = u > 0 ? (char) u : (char) 0xFFFF;
            int mods = 0;
            if (event.isShiftPressed()) mods |= 1 | 64;
            if (event.isCtrlPressed()) mods |= 2 | 128;
            if (event.isAltPressed()) mods |= 8 | 512;
            if (event.isMetaPressed()) mods |= 4 | 256;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                key(com.airsky.browser.jvm.java.awt.event.KeyEvent.KEY_PRESSED, vk, ch, mods);
                if (ch != 0xFFFF) key(com.airsky.browser.jvm.java.awt.event.KeyEvent.KEY_TYPED, 0, ch, mods);
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                key(com.airsky.browser.jvm.java.awt.event.KeyEvent.KEY_RELEASED, vk, ch, mods);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static int vk(int code) {
        if (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z) return 'A' + (code - KeyEvent.KEYCODE_A);
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) return '0' + (code - KeyEvent.KEYCODE_0);
        if (code >= KeyEvent.KEYCODE_F1 && code <= KeyEvent.KEYCODE_F12) return 0x70 + (code - KeyEvent.KEYCODE_F1);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return 0x25;
            case KeyEvent.KEYCODE_DPAD_UP: return 0x26;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 0x27;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 0x28;
            case KeyEvent.KEYCODE_SPACE: return 0x20;
            case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_DPAD_CENTER: return '\n';
            case KeyEvent.KEYCODE_ESCAPE: return 0x1B;
            case KeyEvent.KEYCODE_DEL: return '\b';
            case KeyEvent.KEYCODE_FORWARD_DEL: return 0x7F;
            case KeyEvent.KEYCODE_TAB: return '\t';
            case KeyEvent.KEYCODE_SHIFT_LEFT: case KeyEvent.KEYCODE_SHIFT_RIGHT: return 0x10;
            case KeyEvent.KEYCODE_CTRL_LEFT: case KeyEvent.KEYCODE_CTRL_RIGHT: return 0x11;
            case KeyEvent.KEYCODE_ALT_LEFT: case KeyEvent.KEYCODE_ALT_RIGHT: return 0x12;
            case KeyEvent.KEYCODE_MOVE_HOME: return 0x24;
            case KeyEvent.KEYCODE_MOVE_END: return 0x23;
            case KeyEvent.KEYCODE_PAGE_UP: return 0x21;
            case KeyEvent.KEYCODE_PAGE_DOWN: return 0x22;
            case KeyEvent.KEYCODE_COMMA: return 0x2C;
            case KeyEvent.KEYCODE_MINUS: return 0x2D;
            case KeyEvent.KEYCODE_PERIOD: return 0x2E;
            case KeyEvent.KEYCODE_SLASH: return 0x2F;
            case KeyEvent.KEYCODE_SEMICOLON: return 0x3B;
            case KeyEvent.KEYCODE_EQUALS: return 0x3D;
            default: return 0;
        }
    }

    // ------------------------------------------------------------------
    // JvmRuntime.Host: ダイアログ(プログラムのスレッドから呼ばれ、答えが出るまで待つ)
    // ------------------------------------------------------------------

    @Override
    public Context context() {
        return this;
    }

    private <T> T onUi(java.util.function.Consumer<java.util.function.Consumer<T>> show, T fallback) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>(fallback);
        ui.post(() -> {
            if (isFinishing() || isDestroyed()) {
                latch.countDown();
                return;
            }
            show.accept(v -> {
                result.set(v);
                latch.countDown();
            });
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            return fallback;
        }
        return result.get();
    }

    private static String defaultTitle(int type) {
        switch (type) {
            case 0: return Lang.t("エラー");
            case 2: return Lang.t("警告");
            case 3: return Lang.t("確認");
            default: return Lang.t("メッセージ");
        }
    }

    @Override
    public int showDialog(String title, String message, int messageType, String[] buttons) {
        Integer r = onUi(done -> {
            AtomicInteger chosen = new AtomicInteger(-1);
            MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this)
                    .setTitle(title != null && !title.isEmpty() ? title : defaultTitle(messageType))
                    .setMessage(message);
            if (buttons.length <= 3) {
                if (buttons.length > 0) b.setPositiveButton(buttons[0], (d, w) -> chosen.set(0));
                if (buttons.length > 1) b.setNegativeButton(buttons[1], (d, w) -> chosen.set(1));
                if (buttons.length > 2) b.setNeutralButton(buttons[2], (d, w) -> chosen.set(2));
            } else {
                b.setItems(buttons, (d, w) -> chosen.set(w));
            }
            b.setOnDismissListener(d -> done.accept(chosen.get()));
            b.show();
        }, -1);
        return r != null ? r : -1;
    }

    @Override
    public String showInput(String title, String message, String initial, boolean multiline, boolean password) {
        return onUi(done -> {
            AtomicReference<String> value = new AtomicReference<>(null);
            EditText e = new EditText(this);
            e.setText(initial != null ? initial : "");
            e.setSelectAllOnFocus(!multiline);
            if (password) {
                e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            } else if (multiline) {
                e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                e.setMinLines(4);
                e.setMaxLines(10);
                e.setGravity(Gravity.TOP | Gravity.START);
            } else {
                e.setSingleLine(true);
            }
            FrameLayout box = new FrameLayout(this);
            int p = Math.round(20 * getResources().getDisplayMetrics().density);
            box.setPadding(p, p / 2, p, 0);
            box.addView(e, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this)
                    .setTitle(title != null && !title.isEmpty() ? title : (multiline ? Lang.t("テキストを編集") : Lang.t("入力")))
                    .setView(box)
                    .setPositiveButton("OK", (d, w) -> value.set(e.getText().toString()))
                    .setNegativeButton(Lang.t("キャンセル"), null);
            if (message != null && !message.isEmpty()) b.setMessage(message);
            androidx.appcompat.app.AlertDialog dialog = b.create();
            dialog.setOnDismissListener(d -> done.accept(value.get()));
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            }
            dialog.show();
            e.requestFocus();
        }, null);
    }

    @Override
    public int showChoice(String title, String[] items, int checked) {
        Integer r = onUi(done -> {
            AtomicInteger chosen = new AtomicInteger(-1);
            MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this);
            if (title != null && !title.isEmpty()) b.setTitle(title);
            if (checked >= 0) {
                b.setSingleChoiceItems(items, checked, (d, w) -> {
                    chosen.set(w);
                    d.dismiss();
                });
            } else {
                b.setItems(items, (d, w) -> chosen.set(w));
            }
            b.setOnDismissListener(d -> done.accept(chosen.get()));
            b.show();
        }, -1);
        return r != null ? r : -1;
    }

    @Override
    public void beep() {
        ui.post(() -> {
            try {
                if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70);
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
            } catch (RuntimeException ignored) {
            }
        });
    }

    @Override
    public void onExit(int status) {
        ui.post(() -> {
            if (status != 143) info(Lang.t("\n[プログラムが終了しました (終了コード ") + status + ")]\n", false);
            running = false;
            setStatus(Lang.t("終了しました"));
            stopButton.setImageResource(R.drawable.ic_jr_replay);
            stopButton.setContentDescription(Lang.t("もう一度実行"));
            stdin.close0();
        });
    }
}
