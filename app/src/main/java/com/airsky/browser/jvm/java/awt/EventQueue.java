package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.JvmRuntime;

/** java.awt.EventQueue の互換クラス。 */
public class EventQueue {
    public EventQueue() {}

    public static void invokeLater(Runnable r) { JvmRuntime.invokeLater(r); }

    public static void invokeAndWait(Runnable r) throws InterruptedException, java.lang.reflect.InvocationTargetException {
        JvmRuntime.invokeAndWait(r);
    }

    public static boolean isDispatchThread() { return JvmRuntime.isDispatchThread(); }
    public static long getMostRecentEventTime() { return System.currentTimeMillis(); }
    public static AWTEvent getCurrentEvent() { return null; }
    public void postEvent(AWTEvent e) {
        Object src = e.getSource();
        if (src instanceof Component) JvmRuntime.invokeLater(() -> ((Component) src).dispatchEvent(e));
    }
}
