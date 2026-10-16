package com.airsky.browser.jvm;

import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Window;
import com.airsky.browser.jvm.java.awt.event.InputEvent;
import com.airsky.browser.jvm.java.awt.event.KeyEvent;
import com.airsky.browser.jvm.java.awt.event.MouseEvent;

/**
 * 画面のタッチとキーを、AWT のマウス・キーのイベントにして部品へ届ける(EDT で呼ぶ)。
 * タッチはマウスの左ボタンとして扱う。少し動いただけならクリックとみなす。
 */
public final class JvmInput {
    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int MOVE = 2;
    public static final int CANCEL = 3;
    private static final int SLOP = 6;

    private static Component target;
    private static int downX, downY, lastX, lastY;
    private static boolean dragging;
    private static long lastClickTime;
    private static int lastClickX, lastClickY, clickCount;

    private JvmInput() {}

    public static void touch(Window w, int action, int x, int y) {
        long now = System.currentTimeMillis();
        switch (action) {
            case DOWN: {
                target = pick(w, x, y);
                downX = lastX = x;
                downY = lastY = y;
                dragging = false;
                if (target.isFocusable() && target.jvm$wantsKeyboardFocus()) target.requestFocus();
                boolean again = now - lastClickTime < 400 && Math.abs(x - lastClickX) < 20 && Math.abs(y - lastClickY) < 20;
                clickCount = again ? clickCount + 1 : 1;
                send(MouseEvent.MOUSE_ENTERED, x, y, now, 0);
                send(MouseEvent.MOUSE_PRESSED, x, y, now, InputEvent.BUTTON1_MASK | InputEvent.BUTTON1_DOWN_MASK);
                break;
            }
            case MOVE: {
                if (target == null) return;
                if (!dragging && (Math.abs(x - downX) > SLOP || Math.abs(y - downY) > SLOP)) dragging = true;
                if (target.jvm$wantsDrag()) {
                    send(MouseEvent.MOUSE_DRAGGED, x, y, now, InputEvent.BUTTON1_MASK | InputEvent.BUTTON1_DOWN_MASK);
                    send(MouseEvent.MOUSE_MOVED, x, y, now, 0);
                } else if (dragging) {
                    int dx = lastX - x, dy = lastY - y;
                    for (Component c = target; c != null; c = c.jvm$parent()) {
                        if (c.jvm$dragScroll(dx, dy)) break;
                    }
                }
                lastX = x;
                lastY = y;
                break;
            }
            case UP:
            case CANCEL: {
                if (target == null) return;
                send(MouseEvent.MOUSE_RELEASED, x, y, now, InputEvent.BUTTON1_MASK);
                if (action == UP && !dragging) {
                    send(MouseEvent.MOUSE_CLICKED, x, y, now, InputEvent.BUTTON1_MASK);
                    lastClickTime = now;
                    lastClickX = x;
                    lastClickY = y;
                }
                send(MouseEvent.MOUSE_EXITED, x, y, now, 0);
                target = null;
                break;
            }
            default:
                break;
        }
    }

    private static Component pick(Window w, int x, int y) {
        Component deepest = w.findComponentAt(x, y);
        if (deepest == null) return w;
        for (Component c = deepest; c != null; c = c.jvm$parent()) {
            if (c.jvm$hasMouseHandling()) return c;
        }
        return deepest;
    }

    private static void send(int id, int x, int y, long when, int modifiers) {
        Component t = target;
        if (t == null || !t.isEnabled()) return;
        int ox = 0, oy = 0;
        for (Component c = t; c != null && !(c instanceof Window); c = c.jvm$parent()) {
            ox += c.getX();
            oy += c.getY();
        }
        MouseEvent e = new MouseEvent(t, id, when, modifiers, x - ox, y - oy, x, y, clickCount, false, MouseEvent.BUTTON1);
        t.jvm$mouse(e);
        t.dispatchEvent(e);
    }

    /** キー。フォーカスのある部品へ、受け取る部品がなければキーのリスナーを持つ部品へ届ける。 */
    public static void key(Window w, int id, int keyCode, char ch, int modifiers) {
        Component t = w.jvm$focusOwner();
        if (t == null || !t.isShowing() || (!t.jvm$hasKeyListeners() && !t.jvm$wantsKeyboardFocus())) {
            Component f = w.jvm$find(Component::jvm$hasKeyListeners);
            if (f != null) t = f;
        }
        if (t == null) t = w;
        KeyEvent e = new KeyEvent(t, id, System.currentTimeMillis(), modifiers, keyCode, ch);
        t.jvm$key(e);
        t.dispatchEvent(e);
        if (e.isConsumed() || id == KeyEvent.KEY_TYPED) return;
        for (Component c = t; c != null; c = c.jvm$parent()) {
            if (c.jvm$binding(e, c == t ? 0 : 1)) return;
        }
        if (w.jvm$find(c -> c.jvm$binding(e, 2)) != null) return;
        // フォーカスのある部品がなければ、WHEN_FOCUSED の割り当てを持つ部品で受ける
        if (w.jvm$focusOwner() == null || !w.jvm$focusOwner().jvm$wantsKeyboardFocus()) {
            w.jvm$find(c -> c.jvm$binding(e, 0));
        }
    }
}
