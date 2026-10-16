package com.airsky.browser.jvm.java.awt;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.airsky.browser.jvm.JvmGraphics;
import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.event.WindowEvent;
import com.airsky.browser.jvm.java.awt.event.WindowFocusListener;
import com.airsky.browser.jvm.java.awt.event.WindowListener;
import com.airsky.browser.jvm.java.awt.event.WindowStateListener;
import com.airsky.browser.jvm.java.awt.image.BufferStrategy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * java.awt.Window の互換クラス。表示すると Android の画面に出る。
 * 絵はウィンドウごとに 1 枚のビットマップへ描き、描き終わったら表示用の 1 枚へ写して画面に渡す。
 */
public class Window extends Container {
    final CopyOnWriteArrayList<WindowListener> windowListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<WindowFocusListener> focusListeners2 = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<WindowStateListener> stateListeners = new CopyOnWriteArrayList<>();
    Component focusOwner;
    Window owner;
    private boolean shown;
    private boolean opened;
    private volatile boolean renderPending;
    private volatile boolean publishPending;
    private Bitmap buffer;
    private Bitmap front;
    private float renderScale = 1f;
    private final Object frontLock = new Object();
    private BufferStrategy strategy;
    private Image icon;

    public Window(Frame owner) {
        this((Window) owner);
    }

    public Window(Window owner) {
        this.owner = owner;
        this.visible = false;
        this.heavyweight = true;
        this.layout = new BorderLayout();
    }

    Window() {
        this((Window) null);
    }

    public Window getOwner() { return owner; }

    public Window[] getOwnedWindows() {
        java.util.ArrayList<Window> out = new java.util.ArrayList<>();
        for (Window w : JvmRuntime.windows()) if (w.owner == this) out.add(w);
        return out.toArray(new Window[0]);
    }

    public static Window[] getWindows() {
        return JvmRuntime.windows().toArray(new Window[0]);
    }

    public static Window[] getOwnerlessWindows() { return getWindows(); }

    @Override
    public Color getBackground() {
        Color c = super.getBackground();
        return c != null ? c : JvmLook.PANEL;
    }

    @Override
    public Font getFont() {
        return font != null ? font : JvmLook.FONT;
    }

    public void pack() {
        Dimension d = getPreferredSize();
        setSize(Math.max(d.width, 80), Math.max(d.height, 40));
        jvm$validateTree();
    }

    @Override
    public void setVisible(boolean b) {
        if (b) {
            if (width <= 0 || height <= 0) pack();
            visible = true;
            boolean first = !shown;
            shown = true;
            JvmRuntime.addWindow(this);
            if (!opened) {
                opened = true;
                processWindowEvent(new WindowEvent(this, WindowEvent.WINDOW_OPENED));
            }
            if (first) {
                processWindowEvent(new WindowEvent(this, WindowEvent.WINDOW_ACTIVATED));
                jvm$initialFocus();
            }
            jvm$requestRender();
        } else {
            visible = false;
            if (shown) {
                shown = false;
                JvmRuntime.removeWindow(this);
                processWindowEvent(new WindowEvent(this, WindowEvent.WINDOW_DEACTIVATED));
            }
        }
    }

    public void dispose() {
        boolean wasOpen = opened;
        visible = false;
        if (shown) {
            shown = false;
            JvmRuntime.removeWindow(this);
        }
        for (Window w : getOwnedWindows()) w.dispose();
        opened = false;
        if (wasOpen) processWindowEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSED));
    }

    public void toFront() {
        if (shown) JvmRuntime.addWindow(this);
    }

    public void toBack() {}
    public boolean isActive() { return shown && JvmRuntime.topWindow() == this; }
    public boolean isFocused() { return isActive(); }
    public boolean isFocusableWindow() { return true; }
    public void setFocusableWindowState(boolean b) {}
    public boolean isAlwaysOnTop() { return false; }
    public void setAlwaysOnTop(boolean b) {}
    public void setLocationRelativeTo(Component c) {}
    public void setLocationByPlatform(boolean b) {}
    public void setAutoRequestFocus(boolean b) {}
    public void setOpacity(float f) {}
    public float getOpacity() { return 1f; }
    public void setShape(Shape s) {}
    public void setIconImage(Image img) { icon = img; }
    public void setIconImages(List<? extends Image> list) { if (list != null && !list.isEmpty()) icon = list.get(0); }
    public List<Image> getIconImages() {
        java.util.ArrayList<Image> l = new java.util.ArrayList<>();
        if (icon != null) l.add(icon);
        return l;
    }
    public void setType(Object type) {}
    public boolean isShowing() { return shown && visible; }

    public Component getFocusOwner() { return focusOwner; }
    public Component getMostRecentFocusOwner() { return focusOwner; }

    public void addWindowListener(WindowListener l) { if (l != null) windowListeners.add(l); }
    public void removeWindowListener(WindowListener l) { windowListeners.remove(l); }
    public WindowListener[] getWindowListeners() { return windowListeners.toArray(new WindowListener[0]); }
    public void addWindowFocusListener(WindowFocusListener l) { if (l != null) focusListeners2.add(l); }
    public void removeWindowFocusListener(WindowFocusListener l) { focusListeners2.remove(l); }
    public void addWindowStateListener(WindowStateListener l) { if (l != null) stateListeners.add(l); }
    public void removeWindowStateListener(WindowStateListener l) { stateListeners.remove(l); }

    @Override
    protected void processEvent(AWTEvent e) {
        if (e instanceof WindowEvent) processWindowEvent((WindowEvent) e);
        else super.processEvent(e);
    }

    protected void processWindowEvent(WindowEvent e) {
        for (WindowListener l : windowListeners) {
            switch (e.getID()) {
                case WindowEvent.WINDOW_OPENED: l.windowOpened(e); break;
                case WindowEvent.WINDOW_CLOSING: l.windowClosing(e); break;
                case WindowEvent.WINDOW_CLOSED: l.windowClosed(e); break;
                case WindowEvent.WINDOW_ICONIFIED: l.windowIconified(e); break;
                case WindowEvent.WINDOW_DEICONIFIED: l.windowDeiconified(e); break;
                case WindowEvent.WINDOW_ACTIVATED: l.windowActivated(e); break;
                case WindowEvent.WINDOW_DEACTIVATED: l.windowDeactivated(e); break;
                default: break;
            }
        }
    }

    public void createBufferStrategy(int n) {
        strategy = new com.airsky.browser.jvm.JvmBufferStrategy(this, this);
    }

    public void createBufferStrategy(int n, BufferCapabilities caps) {
        createBufferStrategy(n);
    }

    public BufferStrategy getBufferStrategy() { return strategy; }

    // ------------------------------------------------------------------
    // 互換レイヤーの内部
    // ------------------------------------------------------------------

    public boolean jvm$isShown() { return shown; }

    /** 表示中の題名(Frame / Dialog が返す)。 */
    public String jvm$title() { return ""; }

    /** 画面の × で閉じられた。 */
    public void jvm$closeRequested() {
        processWindowEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        jvm$defaultClose();
    }

    /** × の後の既定の動き(JFrame などが上書きする)。AWT のウィンドウは何もしない。 */
    protected void jvm$defaultClose() {}

    /** System.exit で、何も知らせずに隠す。 */
    public void jvm$forceHide() {
        shown = false;
        visible = false;
    }

    void jvm$initialFocus() {
        if (focusOwner != null) return;
        Component c = jvm$find(x -> x != this && x.focusable && x.enabled && x.jvm$wantsKeyboardFocus());
        if (c != null) focusOwner = c;
    }

    public void jvm$requestRender() {
        if (!shown || renderPending) return;
        renderPending = true;
        JvmRuntime.invokeLater(this::jvm$render);
    }

    /** 全体を描き直して画面に出す(EDT で呼ぶ)。 */
    public void jvm$render() {
        renderPending = false;
        if (!shown) return;
        if (width <= 0 || height <= 0) pack();
        jvm$validateTree();
        ensureBuffer();
        JvmGraphics g = new JvmGraphics(new Canvas(buffer), renderScale);
        Color bg = getBackground();
        g.setBackground(bg);
        g.setColor(bg);
        g.fillRect(0, 0, width, height);
        g.setFont(getFont());
        Color fg = getForeground();
        g.setColor(fg != null ? fg : Color.BLACK);
        int stamp = Container.jvm$nextStamp();
        paintStamp = stamp;
        update(g);
        paintLeftovers(g, this, 0, 0, stamp);
        g.dispose();
        publish();
    }

    /** 親の paint が上書きされて描かれなかった AWT の部品(Button など)を描く。 */
    private void paintLeftovers(JvmGraphics g, Container c, int ox, int oy, int stamp) {
        for (Component child : c.children) {
            if (!child.visible) continue;
            if (child.heavyweight && child.paintStamp != stamp) {
                Graphics cg = g.create();
                cg.translate(ox, oy);
                Container.jvm$paintChild(cg, child);
                cg.dispose();
            } else if (child instanceof Container) {
                paintLeftovers(g, (Container) child, ox + child.x, oy + child.y, stamp);
            }
        }
    }

    private void ensureBuffer() {
        float s = JvmRuntime.scaleFor(width, height);
        int bw = Math.max(1, (int) Math.ceil(width * s));
        int bh = Math.max(1, (int) Math.ceil(height * s));
        if (buffer == null || buffer.getWidth() != bw || buffer.getHeight() != bh) {
            buffer = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        }
        renderScale = s;
    }

    /** 描いた絵を表示用へ写して、画面に知らせる。 */
    public void jvm$publish() {
        publish();
    }

    private void publish() {
        publishPending = false;
        Bitmap b = buffer;
        if (b == null) return;
        synchronized (frontLock) {
            if (front == null || front.getWidth() != b.getWidth() || front.getHeight() != b.getHeight()) {
                front = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
            }
            new Canvas(front).drawBitmap(b, 0, 0, null);
        }
        JvmRuntime.present(this);
    }

    private void publishSoon() {
        if (publishPending) return;
        publishPending = true;
        JvmRuntime.timers.schedule(this::publish, 16, TimeUnit.MILLISECONDS);
    }

    /** 画面が使う表示用の絵。使う間は jvm$frontLock() で同期する。 */
    public Bitmap jvm$front() { return front; }
    public Object jvm$frontLock() { return frontLock; }
    public float jvm$renderScale() { return renderScale; }

    /** getGraphics(): ウィンドウの絵へ直接描く。描いたものは少し後に画面へ出る。 */
    public Graphics jvm$graphicsFor(Component c) {
        if (!shown) return null;
        if (buffer == null) {
            if (width <= 0 || height <= 0) return null;
            ensureBuffer();
        }
        JvmGraphics g = new JvmGraphics(new Canvas(buffer), renderScale);
        Point p = jvm$offsetOf(c);
        g.translate(p.x, p.y);
        g.clipRect(0, 0, c.width, c.height);
        g.setFont(c.getFont());
        Color fg = c.getForeground();
        g.setColor(fg != null ? fg : Color.BLACK);
        Color bg = c.getBackground();
        if (bg != null) g.setBackground(bg);
        g.jvm$setOnDispose(this::publishSoon);
        publishSoon();
        return g;
    }

    /** BufferStrategy 用: 自動で画面に出さない Graphics。 */
    public Graphics jvm$strategyGraphics(Component c) {
        if (buffer == null || width * renderScale > buffer.getWidth() + 1) {
            if (width <= 0 || height <= 0) pack();
            ensureBuffer();
        }
        JvmGraphics g = new JvmGraphics(new Canvas(buffer), renderScale);
        Point p = jvm$offsetOf(c);
        g.translate(p.x, p.y);
        g.clipRect(0, 0, c.width, c.height);
        g.setFont(c.getFont());
        Color bg = c.getBackground();
        if (bg != null) g.setBackground(bg);
        return g;
    }

    private Point jvm$offsetOf(Component c) {
        int ox = 0, oy = 0;
        for (Component p = c; p != null && p != this; p = p.parent) {
            ox += p.x;
            oy += p.y;
        }
        return new Point(ox, oy);
    }

    public Component jvm$focusOwner() { return focusOwner; }
}
