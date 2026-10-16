package com.airsky.browser.jvm;

import android.content.Context;

import com.airsky.browser.jvm.java.awt.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * JAR のプログラムと、それを表示する Android の画面(JarRunActivity)をつなぐ。
 *
 * <p>AWT / Swing のイベントスレッド(EDT)もここで回す。EDT は Android のメインスレッドとは別なので、
 * プログラムが EDT を止めても(モーダルなダイアログなど)画面は固まらない。
 * モーダルな待ちの間は {@link #pumpUntil} がイベントを処理し続ける。</p>
 */
public final class JvmRuntime {

    /** プログラムを表示する側(Activity)。show 系は呼んだスレッドを結果が出るまで止める。 */
    public interface Host {
        Context context();

        /** 表示するウィンドウの一覧が変わった。 */
        void windowsChanged();

        /** ウィンドウの絵が新しくなった。 */
        void present(Window w);

        /** メッセージ・確認のダイアログ。押したボタンの番号(閉じたら -1)。 */
        int showDialog(String title, String message, int messageType, String[] buttons);

        /** 文字の入力。キャンセルで null。 */
        String showInput(String title, String message, String initial, boolean multiline, boolean password);

        /** 一覧から 1 つ選ぶ。キャンセルで -1。 */
        int showChoice(String title, String[] items, int checked);

        void beep();

        /** プログラムが System.exit した。 */
        void onExit(int status);
    }

    private static volatile Host host;
    private static final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private static volatile Thread edt;
    private static final List<Window> windows = new CopyOnWriteArrayList<>();
    private static volatile boolean exited;
    private static volatile boolean busy;
    private static volatile int viewW, viewH;
    private static volatile float density = 2f;
    private static ThreadGroup group;

    /** javax.swing.Timer などの時間待ち。 */
    public static final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Swing-Timer");
        t.setDaemon(true);
        return t;
    });

    private JvmRuntime() {}

    public static void install(Host h, ThreadGroup programGroup) {
        host = h;
        group = programGroup;
    }

    public static Host host() {
        return host;
    }

    public static boolean hasExited() {
        return exited;
    }

    // ------------------------------------------------------------------
    // イベントスレッド
    // ------------------------------------------------------------------

    public static void invokeLater(Runnable r) {
        if (r == null || exited) return;
        ensureEdt();
        queue.add(r);
    }

    /** EDT に処理が残っていないか(プログラムが終わったかを見るため)。 */
    public static boolean isIdle() {
        return edt == null || (!busy && queue.isEmpty());
    }

    public static boolean isDispatchThread() {
        return Thread.currentThread() == edt;
    }

    /** EDT で実行して終わるまで待つ。EDT から呼ばれたらその場で実行する。 */
    public static void invokeAndWait(Runnable r) throws InterruptedException, java.lang.reflect.InvocationTargetException {
        if (isDispatchThread()) {
            r.run();
            return;
        }
        final Throwable[] error = new Throwable[1];
        final boolean[] done = new boolean[1];
        invokeLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                error[0] = t;
            } finally {
                synchronized (done) {
                    done[0] = true;
                    done.notifyAll();
                }
            }
        });
        synchronized (done) {
            while (!done[0] && !exited) done.wait(50);
        }
        if (error[0] instanceof JvmExit) throw (JvmExit) error[0];
        if (error[0] != null) throw new java.lang.reflect.InvocationTargetException(error[0]);
    }

    private static synchronized void ensureEdt() {
        if (edt != null) return;
        Runnable loop = () -> {
            while (true) {
                try {
                    Runnable r = queue.take();
                    busy = true;
                    try {
                        runSafely(r);
                    } finally {
                        busy = false;
                    }
                } catch (InterruptedException ignored) {
                }
            }
        };
        Thread t = group != null ? new Thread(group, loop, "AWT-EventQueue-0", 16L << 20)
                : new Thread(null, loop, "AWT-EventQueue-0", 16L << 20);
        t.setDaemon(true);
        edt = t;
        t.start();
    }

    static void runSafely(Runnable r) {
        try {
            r.run();
        } catch (JvmExit ignored) {
        } catch (Throwable t) {
            Thread th = Thread.currentThread();
            Thread.UncaughtExceptionHandler h = Thread.getDefaultUncaughtExceptionHandler();
            if (h != null) h.uncaughtException(th, t);
            else t.printStackTrace();
        }
    }

    /**
     * done が true になるまで待つ。EDT で呼ばれた時は、待つ間もイベントを処理し続ける
     * (モーダルなダイアログの間もボタンや再描画が動くように)。
     */
    public static void pumpUntil(BooleanSupplier done) {
        if (isDispatchThread()) {
            while (!done.getAsBoolean() && !exited) {
                try {
                    Runnable r = queue.poll(15, TimeUnit.MILLISECONDS);
                    if (r != null) runSafely(r);
                } catch (InterruptedException e) {
                    return;
                }
            }
        } else {
            Object lock = new Object();
            synchronized (lock) {
                while (!done.getAsBoolean() && !exited) {
                    try {
                        lock.wait(30);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * Host の止まる呼び出しをする。EDT からなら別のスレッドで呼び、待つ間イベントを処理する。
     */
    public static <T> T blocking(Callable<T> call, T fallback) {
        if (host == null || exited) return fallback;
        try {
            if (!isDispatchThread()) return call.call();
            FutureTask<T> f = new FutureTask<>(call);
            Thread t = new Thread(f, "AWT-Modal");
            t.setDaemon(true);
            t.start();
            pumpUntil(f::isDone);
            return f.isDone() ? f.get() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------
    // ウィンドウ
    // ------------------------------------------------------------------

    public static void addWindow(Window w) {
        windows.remove(w);
        windows.add(w);
        Host h = host;
        if (h != null) h.windowsChanged();
    }

    public static void removeWindow(Window w) {
        if (windows.remove(w)) {
            Host h = host;
            if (h != null) h.windowsChanged();
        }
    }

    public static List<Window> windows() {
        return new ArrayList<>(windows);
    }

    public static Window topWindow() {
        List<Window> list = windows();
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    public static void present(Window w) {
        Host h = host;
        if (h != null) h.present(w);
    }

    // ------------------------------------------------------------------
    // 画面の大きさ
    // ------------------------------------------------------------------

    /** ウィンドウを表示できる場所の大きさ(端末のピクセル)。 */
    public static void setViewport(int width, int height, float d) {
        boolean changed = width != viewW || height != viewH;
        viewW = width;
        viewH = height;
        density = d;
        if (changed) {
            for (Window w : windows()) w.repaint();
        }
    }

    public static float density() {
        return density;
    }

    /** AWT の座標 1 を端末の何ピクセルで描くか(ウィンドウを表示場所いっぱいに合わせる)。 */
    public static float scaleFor(int width, int height) {
        if (viewW <= 0 || viewH <= 0 || width <= 0 || height <= 0) return density;
        float s = Math.min(viewW / (float) width, viewH / (float) height);
        return Math.max(0.25f, Math.min(s, density * 2.5f));
    }

    /** Toolkit.getScreenSize の大きさ(dp 相当)。 */
    public static int screenWidth() {
        return viewW > 0 ? Math.round(viewW / density) : 400;
    }

    public static int screenHeight() {
        return viewH > 0 ? Math.round(viewH / density) : 700;
    }

    // ------------------------------------------------------------------
    // 終了
    // ------------------------------------------------------------------

    public static void exit(int status) {
        if (!exited) {
            exited = true;
            for (Window w : windows()) w.jvm$forceHide();
            windows.clear();
            Host h = host;
            if (h != null) {
                h.windowsChanged();
                h.onExit(status);
            }
        }
        throw new JvmExit(status);
    }
}
