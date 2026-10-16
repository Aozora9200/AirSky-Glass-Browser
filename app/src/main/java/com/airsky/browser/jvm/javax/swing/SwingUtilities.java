package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Container;
import com.airsky.browser.jvm.java.awt.FontMetrics;
import com.airsky.browser.jvm.java.awt.Point;
import com.airsky.browser.jvm.java.awt.Rectangle;
import com.airsky.browser.jvm.java.awt.Window;
import com.airsky.browser.jvm.java.awt.event.InputEvent;
import com.airsky.browser.jvm.java.awt.event.MouseEvent;

/** javax.swing.SwingUtilities の互換クラス。 */
public class SwingUtilities implements SwingConstants {
    private SwingUtilities() {}

    public static void invokeLater(Runnable r) { JvmRuntime.invokeLater(r); }

    public static void invokeAndWait(Runnable r) throws InterruptedException, java.lang.reflect.InvocationTargetException {
        JvmRuntime.invokeAndWait(r);
    }

    public static boolean isEventDispatchThread() { return JvmRuntime.isDispatchThread(); }
    public static Window getWindowAncestor(Component c) { return c != null ? (c instanceof Window ? ((Window) c).getOwner() : c.jvm$window()) : null; }
    public static Window windowForComponent(Component c) { return c != null ? c.jvm$window() : null; }
    public static Component getRoot(Component c) { return c != null ? c.jvm$window() : null; }
    public static JRootPane getRootPane(Component c) { return c instanceof JComponent ? ((JComponent) c).getRootPane() : null; }

    public static Container getAncestorOfClass(Class<?> cls, Component c) {
        if (c == null || cls == null) return null;
        for (Container p = c.getParent(); p != null; p = p.getParent()) if (cls.isInstance(p)) return p;
        return null;
    }

    public static Component getAncestorNamed(String name, Component c) {
        if (c == null || name == null) return null;
        for (Container p = c.getParent(); p != null; p = p.getParent()) if (name.equals(p.getName())) return p;
        return null;
    }

    public static boolean isDescendingFrom(Component a, Component b) {
        if (a == b) return true;
        for (Container p = a != null ? a.getParent() : null; p != null; p = p.getParent()) if (p == b) return true;
        return false;
    }

    public static Component getDeepestComponentAt(Component parent, int x, int y) {
        if (parent instanceof Container) return ((Container) parent).findComponentAt(x, y);
        return parent != null && parent.contains(x, y) ? parent : null;
    }

    public static boolean isLeftMouseButton(MouseEvent e) {
        return e.getButton() == MouseEvent.BUTTON1 || (e.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0
                || (e.getModifiers() & InputEvent.BUTTON1_MASK) != 0;
    }

    public static boolean isRightMouseButton(MouseEvent e) { return e.getButton() == MouseEvent.BUTTON3; }
    public static boolean isMiddleMouseButton(MouseEvent e) { return e.getButton() == MouseEvent.BUTTON2; }

    public static Point convertPoint(Component src, Point p, Component dst) {
        return convertPoint(src, p.x, p.y, dst);
    }

    public static Point convertPoint(Component src, int x, int y, Component dst) {
        Point a = src != null ? src.getLocationOnScreen() : new Point();
        Point b = dst != null ? dst.getLocationOnScreen() : new Point();
        return new Point(x + a.x - b.x, y + a.y - b.y);
    }

    public static Rectangle convertRectangle(Component src, Rectangle r, Component dst) {
        Point p = convertPoint(src, r.x, r.y, dst);
        return new Rectangle(p.x, p.y, r.width, r.height);
    }

    public static MouseEvent convertMouseEvent(Component src, MouseEvent e, Component dst) {
        Point p = convertPoint(src, e.getX(), e.getY(), dst);
        return new MouseEvent(dst, e.getID(), e.getWhen(), e.getModifiers() | e.getModifiersEx(), p.x, p.y,
                e.getXOnScreen(), e.getYOnScreen(), e.getClickCount(), e.isPopupTrigger(), e.getButton());
    }

    public static void convertPointToScreen(Point p, Component c) {
        Point a = c.getLocationOnScreen();
        p.translate(a.x, a.y);
    }

    public static void convertPointFromScreen(Point p, Component c) {
        Point a = c.getLocationOnScreen();
        p.translate(-a.x, -a.y);
    }

    public static void updateComponentTreeUI(Component c) {
        if (c != null) c.repaint();
    }

    public static int computeStringWidth(FontMetrics fm, String s) { return fm.stringWidth(s); }

    public static Rectangle calculateInnerArea(JComponent c, Rectangle r) {
        com.airsky.browser.jvm.java.awt.Insets in = c.getInsets();
        Rectangle out = r != null ? r : new Rectangle();
        out.setBounds(in.left, in.top, c.getWidth() - in.left - in.right, c.getHeight() - in.top - in.bottom);
        return out;
    }

    public static boolean notifyAction(Action a, KeyStroke k, com.airsky.browser.jvm.java.awt.event.KeyEvent e, Object sender, int modifiers) {
        if (a == null || !a.isEnabled()) return false;
        a.actionPerformed(new com.airsky.browser.jvm.java.awt.event.ActionEvent(sender,
                com.airsky.browser.jvm.java.awt.event.ActionEvent.ACTION_PERFORMED, null, modifiers));
        return true;
    }
}
