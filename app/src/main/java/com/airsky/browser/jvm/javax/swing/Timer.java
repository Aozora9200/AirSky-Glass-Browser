package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.event.ActionEvent;
import com.airsky.browser.jvm.java.awt.event.ActionListener;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** javax.swing.Timer の互換クラス。時間が来たら EDT でアクションを呼ぶ(処理が遅れたら重ねない)。 */
public class Timer implements java.io.Serializable {
    private final CopyOnWriteArrayList<ActionListener> listeners = new CopyOnWriteArrayList<>();
    private int delay, initialDelay;
    private boolean repeats = true, coalesce = true;
    private String command;
    private ScheduledFuture<?> future;
    private final AtomicBoolean pending = new AtomicBoolean();
    private static boolean logTimers;

    public Timer(int delay, ActionListener l) {
        this.delay = delay;
        this.initialDelay = delay;
        if (l != null) listeners.add(l);
    }

    public void addActionListener(ActionListener l) { if (l != null) listeners.add(l); }
    public void removeActionListener(ActionListener l) { listeners.remove(l); }
    public ActionListener[] getActionListeners() { return listeners.toArray(new ActionListener[0]); }
    public static void setLogTimers(boolean b) { logTimers = b; }
    public static boolean getLogTimers() { return logTimers; }
    public int getDelay() { return delay; }
    public int getInitialDelay() { return initialDelay; }
    public void setInitialDelay(int d) { initialDelay = d; }
    public boolean isRepeats() { return repeats; }
    public void setRepeats(boolean b) { repeats = b; }
    public boolean isCoalesce() { return coalesce; }
    public void setCoalesce(boolean b) { coalesce = b; }
    public String getActionCommand() { return command; }
    public void setActionCommand(String c) { command = c; }

    public void setDelay(int d) {
        delay = d;
        if (isRunning()) restart();
    }

    public synchronized boolean isRunning() { return future != null && !future.isDone(); }

    public synchronized void start() {
        if (isRunning()) return;
        Runnable tick = () -> {
            if (coalesce && !pending.compareAndSet(false, true)) return;
            JvmRuntime.invokeLater(this::fire);
        };
        if (repeats) {
            future = JvmRuntime.timers.scheduleAtFixedRate(tick, Math.max(0, initialDelay), Math.max(1, delay), TimeUnit.MILLISECONDS);
        } else {
            future = JvmRuntime.timers.schedule(tick, Math.max(0, initialDelay), TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void stop() {
        if (future != null) future.cancel(false);
        future = null;
        pending.set(false);
    }

    public synchronized void restart() {
        stop();
        start();
    }

    private void fire() {
        pending.set(false);
        synchronized (this) {
            if (future == null) return;
        }
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, command, System.currentTimeMillis(), 0);
        for (ActionListener l : listeners) l.actionPerformed(e);
    }
}
