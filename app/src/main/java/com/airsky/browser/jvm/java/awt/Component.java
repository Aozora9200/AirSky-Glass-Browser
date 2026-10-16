package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.JvmFonts;
import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.event.ComponentEvent;
import com.airsky.browser.jvm.java.awt.event.ComponentListener;
import com.airsky.browser.jvm.java.awt.event.FocusEvent;
import com.airsky.browser.jvm.java.awt.event.FocusListener;
import com.airsky.browser.jvm.java.awt.event.KeyEvent;
import com.airsky.browser.jvm.java.awt.event.KeyListener;
import com.airsky.browser.jvm.java.awt.event.MouseEvent;
import com.airsky.browser.jvm.java.awt.event.MouseListener;
import com.airsky.browser.jvm.java.awt.event.MouseMotionListener;
import com.airsky.browser.jvm.java.awt.event.MouseWheelEvent;
import com.airsky.browser.jvm.java.awt.event.MouseWheelListener;
import com.airsky.browser.jvm.java.awt.image.BufferedImage;
import com.airsky.browser.jvm.java.awt.image.ImageObserver;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * java.awt.Component の互換クラス。描画はウィンドウ単位でまとめて行う(repaint はウィンドウの再描画を頼む)。
 * 名前が jvm$ で始まるメソッドは互換レイヤーの内部用。
 */
public abstract class Component implements ImageObserver, java.io.Serializable {
    public static final float TOP_ALIGNMENT = 0.0f;
    public static final float CENTER_ALIGNMENT = 0.5f;
    public static final float BOTTOM_ALIGNMENT = 1.0f;
    public static final float LEFT_ALIGNMENT = 0.0f;
    public static final float RIGHT_ALIGNMENT = 1.0f;

    int x, y, width, height;
    boolean visible = true;
    boolean enabled = true;
    boolean focusable = true;
    boolean valid;
    Color background, foreground;
    Font font;
    Cursor cursor;
    String name;
    Container parent;
    Dimension prefSize, minSize, maxSize;
    /** AWT の重いコンポーネント(Button など)。親の paint を上書きされても描くため。 */
    boolean heavyweight;
    int paintStamp;
    private final Object treeLock = new Object();

    final CopyOnWriteArrayList<MouseListener> mouseListeners = new CopyOnWriteArrayList<>();
    final CopyOnWriteArrayList<MouseMotionListener> motionListeners = new CopyOnWriteArrayList<>();
    final CopyOnWriteArrayList<MouseWheelListener> wheelListeners = new CopyOnWriteArrayList<>();
    final CopyOnWriteArrayList<KeyListener> keyListeners = new CopyOnWriteArrayList<>();
    final CopyOnWriteArrayList<FocusListener> focusListeners = new CopyOnWriteArrayList<>();
    final CopyOnWriteArrayList<ComponentListener> componentListeners = new CopyOnWriteArrayList<>();
    private PropertyChangeSupport changes;

    protected Component() {}

    public final Object getTreeLock() {
        return treeLock;
    }

    public String getName() { return name; }
    public void setName(String n) { name = n; }
    public Container getParent() { return parent; }
    public Toolkit getToolkit() { return Toolkit.getDefaultToolkit(); }

    // ------------------------------------------------------------------
    // 表示・有効
    // ------------------------------------------------------------------

    public boolean isVisible() { return visible; }

    public void setVisible(boolean b) {
        if (visible == b) return;
        visible = b;
        if (parent != null) parent.invalidate();
        fireComponent(b ? ComponentEvent.COMPONENT_SHOWN : ComponentEvent.COMPONENT_HIDDEN);
        jvm$renderWindow();
    }

    @Deprecated public void show() { setVisible(true); }
    @Deprecated public void show(boolean b) { setVisible(b); }
    @Deprecated public void hide() { setVisible(false); }

    public boolean isShowing() {
        if (!visible) return false;
        if (this instanceof Window) return ((Window) this).jvm$isShown();
        return parent != null && parent.isShowing();
    }

    public boolean isDisplayable() { return jvm$window() != null; }
    public boolean isValid() { return valid; }
    public boolean isLightweight() { return !heavyweight; }
    public boolean isOpaque() { return heavyweight; }
    public boolean isDoubleBuffered() { return true; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean b) {
        if (enabled == b) return;
        enabled = b;
        repaint();
    }

    @Deprecated public void enable() { setEnabled(true); }
    @Deprecated public void enable(boolean b) { setEnabled(b); }
    @Deprecated public void disable() { setEnabled(false); }

    // ------------------------------------------------------------------
    // フォーカス
    // ------------------------------------------------------------------

    public boolean isFocusable() { return focusable; }
    public void setFocusable(boolean b) { focusable = b; }
    @Deprecated public boolean isFocusTraversable() { return focusable; }
    public void setFocusTraversalKeysEnabled(boolean b) {}
    public boolean getFocusTraversalKeysEnabled() { return true; }

    public boolean isFocusOwner() {
        Window w = jvm$window();
        return w != null && w.focusOwner == this;
    }

    public boolean hasFocus() { return isFocusOwner(); }

    public void requestFocus() {
        Window w = jvm$window();
        if (w == null) return;
        Component old = w.focusOwner;
        if (old == this) return;
        w.focusOwner = this;
        if (old != null) old.dispatchEvent(new FocusEvent(old, FocusEvent.FOCUS_LOST));
        dispatchEvent(new FocusEvent(this, FocusEvent.FOCUS_GAINED));
        repaint();
        if (old != null) old.repaint();
    }

    public boolean requestFocus(boolean temporary) { requestFocus(); return true; }
    public boolean requestFocusInWindow() { requestFocus(); return true; }
    public void transferFocus() {}
    public void transferFocusBackward() {}

    // ------------------------------------------------------------------
    // 色・文字
    // ------------------------------------------------------------------

    public Color getBackground() {
        if (background != null) return background;
        return parent != null ? parent.getBackground() : null;
    }

    public void setBackground(Color c) {
        Color old = background;
        background = c;
        firePropertyChange("background", old, c);
        repaint();
    }

    public boolean isBackgroundSet() { return background != null; }

    public Color getForeground() {
        if (foreground != null) return foreground;
        return parent != null ? parent.getForeground() : JvmLook.TEXT;
    }

    public void setForeground(Color c) {
        Color old = foreground;
        foreground = c;
        firePropertyChange("foreground", old, c);
        repaint();
    }

    public boolean isForegroundSet() { return foreground != null; }

    public Font getFont() {
        if (font != null) return font;
        return parent != null ? parent.getFont() : JvmLook.FONT;
    }

    public void setFont(Font f) {
        Font old = font;
        font = f;
        firePropertyChange("font", old, f);
        invalidate();
        repaint();
    }

    public boolean isFontSet() { return font != null; }

    public FontMetrics getFontMetrics(Font f) {
        return JvmFonts.metrics(f != null ? f : JvmLook.FONT);
    }

    public void setCursor(Cursor c) { cursor = c; }
    public Cursor getCursor() { return cursor != null ? cursor : Cursor.getDefaultCursor(); }
    public boolean isCursorSet() { return cursor != null; }

    public void setLocale(java.util.Locale l) {}
    public java.util.Locale getLocale() { return java.util.Locale.getDefault(); }
    public void setIgnoreRepaint(boolean b) {}
    public boolean getIgnoreRepaint() { return false; }
    public void setComponentOrientation(ComponentOrientation o) {}
    public ComponentOrientation getComponentOrientation() { return ComponentOrientation.LEFT_TO_RIGHT; }

    // ------------------------------------------------------------------
    // 位置・大きさ
    // ------------------------------------------------------------------

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public Point getLocation() { return new Point(x, y); }
    public Point getLocation(Point rv) {
        if (rv == null) return getLocation();
        rv.x = x;
        rv.y = y;
        return rv;
    }
    @Deprecated public Point location() { return getLocation(); }

    public Point getLocationOnScreen() {
        int ax = 0, ay = 0;
        for (Component c = this; c != null; c = c.parent) {
            ax += c.x;
            ay += c.y;
        }
        return new Point(ax, ay);
    }

    public void setLocation(int x, int y) { setBounds(x, y, width, height); }
    public void setLocation(Point p) { setLocation(p.x, p.y); }
    @Deprecated public void move(int x, int y) { setLocation(x, y); }

    public Dimension getSize() { return new Dimension(width, height); }
    public Dimension getSize(Dimension rv) {
        if (rv == null) return getSize();
        rv.width = width;
        rv.height = height;
        return rv;
    }
    @Deprecated public Dimension size() { return getSize(); }

    public void setSize(int w, int h) { setBounds(x, y, w, h); }
    public void setSize(Dimension d) { setSize(d.width, d.height); }
    @Deprecated public void resize(int w, int h) { setSize(w, h); }
    @Deprecated public void resize(Dimension d) { setSize(d); }

    public Rectangle getBounds() { return new Rectangle(x, y, width, height); }
    public Rectangle getBounds(Rectangle rv) {
        if (rv == null) return getBounds();
        rv.setBounds(x, y, width, height);
        return rv;
    }
    @Deprecated public Rectangle bounds() { return getBounds(); }

    public void setBounds(Rectangle r) { setBounds(r.x, r.y, r.width, r.height); }
    @Deprecated public void reshape(int x, int y, int w, int h) { setBounds(x, y, w, h); }

    public void setBounds(int x, int y, int w, int h) {
        w = Math.max(0, w);
        h = Math.max(0, h);
        boolean resized = this.width != w || this.height != h;
        boolean moved = this.x != x || this.y != y;
        if (!resized && !moved) return;
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
        if (resized) {
            invalidate();
            fireComponent(ComponentEvent.COMPONENT_RESIZED);
        }
        if (moved) fireComponent(ComponentEvent.COMPONENT_MOVED);
        jvm$renderWindow();
    }

    public boolean contains(int px, int py) { return px >= 0 && py >= 0 && px < width && py < height; }
    public boolean contains(Point p) { return contains(p.x, p.y); }
    @Deprecated public boolean inside(int px, int py) { return contains(px, py); }
    public Component getComponentAt(int px, int py) { return contains(px, py) ? this : null; }
    public Component getComponentAt(Point p) { return getComponentAt(p.x, p.y); }

    public Dimension getPreferredSize() {
        if (prefSize != null) return new Dimension(prefSize);
        return getMinimumSize();
    }
    @Deprecated public Dimension preferredSize() { return getPreferredSize(); }

    public void setPreferredSize(Dimension d) {
        prefSize = d == null ? null : new Dimension(d);
        invalidate();
    }

    public boolean isPreferredSizeSet() { return prefSize != null; }

    public Dimension getMinimumSize() {
        if (minSize != null) return new Dimension(minSize);
        return getSize();
    }
    @Deprecated public Dimension minimumSize() { return getMinimumSize(); }

    public void setMinimumSize(Dimension d) {
        minSize = d == null ? null : new Dimension(d);
        invalidate();
    }

    public boolean isMinimumSizeSet() { return minSize != null; }

    public Dimension getMaximumSize() {
        if (maxSize != null) return new Dimension(maxSize);
        return new Dimension(Short.MAX_VALUE, Short.MAX_VALUE);
    }

    public void setMaximumSize(Dimension d) {
        maxSize = d == null ? null : new Dimension(d);
        invalidate();
    }

    public boolean isMaximumSizeSet() { return maxSize != null; }

    public float getAlignmentX() { return CENTER_ALIGNMENT; }
    public float getAlignmentY() { return CENTER_ALIGNMENT; }
    public int getBaseline(int w, int h) { return -1; }

    // ------------------------------------------------------------------
    // 配置
    // ------------------------------------------------------------------

    public void invalidate() {
        valid = false;
        if (parent != null && parent.valid) parent.invalidate();
    }

    public void validate() { valid = true; }

    public void revalidate() {
        invalidate();
        repaint();
    }

    public void doLayout() {}
    @Deprecated public void layout() { doLayout(); }

    // ------------------------------------------------------------------
    // 描画
    // ------------------------------------------------------------------

    public void paint(Graphics g) {}

    public void update(Graphics g) { paint(g); }

    public void paintAll(Graphics g) { paint(g); }

    public void print(Graphics g) { paint(g); }

    public void printAll(Graphics g) { paintAll(g); }

    public void repaint() { jvm$renderWindow(); }
    public void repaint(long tm) { jvm$renderWindow(); }
    public void repaint(int x, int y, int w, int h) { jvm$renderWindow(); }
    public void repaint(long tm, int x, int y, int w, int h) { jvm$renderWindow(); }

    public Graphics getGraphics() {
        Window w = jvm$window();
        return w != null ? w.jvm$graphicsFor(this) : null;
    }

    public Image createImage(int w, int h) {
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics g = img.getGraphics();
        Color bg = getBackground();
        g.setColor(bg != null ? bg : Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    public com.airsky.browser.jvm.java.awt.image.VolatileImage createVolatileImage(int w, int h) {
        return new com.airsky.browser.jvm.java.awt.image.VolatileImage(Math.max(1, w), Math.max(1, h));
    }

    public boolean prepareImage(Image img, ImageObserver o) { return true; }
    public int checkImage(Image img, ImageObserver o) { return ImageObserver.ALLBITS; }

    @Override
    public boolean imageUpdate(Image img, int flags, int x, int y, int w, int h) {
        if ((flags & (ALLBITS | FRAMEBITS)) != 0) repaint();
        return (flags & (ALLBITS | ABORT)) == 0;
    }

    public GraphicsConfiguration getGraphicsConfiguration() { return GraphicsConfiguration.DEFAULT; }

    // ------------------------------------------------------------------
    // イベント
    // ------------------------------------------------------------------

    public synchronized void addMouseListener(MouseListener l) { if (l != null) mouseListeners.add(l); }
    public synchronized void removeMouseListener(MouseListener l) { mouseListeners.remove(l); }
    public MouseListener[] getMouseListeners() { return mouseListeners.toArray(new MouseListener[0]); }
    public synchronized void addMouseMotionListener(MouseMotionListener l) { if (l != null) motionListeners.add(l); }
    public synchronized void removeMouseMotionListener(MouseMotionListener l) { motionListeners.remove(l); }
    public MouseMotionListener[] getMouseMotionListeners() { return motionListeners.toArray(new MouseMotionListener[0]); }
    public synchronized void addMouseWheelListener(MouseWheelListener l) { if (l != null) wheelListeners.add(l); }
    public synchronized void removeMouseWheelListener(MouseWheelListener l) { wheelListeners.remove(l); }
    public MouseWheelListener[] getMouseWheelListeners() { return wheelListeners.toArray(new MouseWheelListener[0]); }
    public synchronized void addKeyListener(KeyListener l) { if (l != null) keyListeners.add(l); }
    public synchronized void removeKeyListener(KeyListener l) { keyListeners.remove(l); }
    public KeyListener[] getKeyListeners() { return keyListeners.toArray(new KeyListener[0]); }
    public synchronized void addFocusListener(FocusListener l) { if (l != null) focusListeners.add(l); }
    public synchronized void removeFocusListener(FocusListener l) { focusListeners.remove(l); }
    public FocusListener[] getFocusListeners() { return focusListeners.toArray(new FocusListener[0]); }
    public synchronized void addComponentListener(ComponentListener l) { if (l != null) componentListeners.add(l); }
    public synchronized void removeComponentListener(ComponentListener l) { componentListeners.remove(l); }
    public ComponentListener[] getComponentListeners() { return componentListeners.toArray(new ComponentListener[0]); }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        if (l == null) return;
        if (changes == null) changes = new PropertyChangeSupport(this);
        changes.addPropertyChangeListener(l);
    }

    public void addPropertyChangeListener(String property, PropertyChangeListener l) {
        if (l == null) return;
        if (changes == null) changes = new PropertyChangeSupport(this);
        changes.addPropertyChangeListener(property, l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        if (changes != null) changes.removePropertyChangeListener(l);
    }

    public void removePropertyChangeListener(String property, PropertyChangeListener l) {
        if (changes != null) changes.removePropertyChangeListener(property, l);
    }

    protected void firePropertyChange(String property, Object oldValue, Object newValue) {
        if (changes != null) changes.firePropertyChange(property, oldValue, newValue);
    }

    protected void firePropertyChange(String property, boolean oldValue, boolean newValue) {
        if (changes != null) changes.firePropertyChange(property, oldValue, newValue);
    }

    protected void firePropertyChange(String property, int oldValue, int newValue) {
        if (changes != null) changes.firePropertyChange(property, oldValue, newValue);
    }

    protected final void enableEvents(long mask) {}
    protected final void disableEvents(long mask) {}

    public final void dispatchEvent(AWTEvent e) {
        processEvent(e);
    }

    protected void processEvent(AWTEvent e) {
        if (e instanceof MouseWheelEvent) {
            processMouseWheelEvent((MouseWheelEvent) e);
        } else if (e instanceof MouseEvent) {
            int id = e.getID();
            if (id == MouseEvent.MOUSE_DRAGGED || id == MouseEvent.MOUSE_MOVED) {
                processMouseMotionEvent((MouseEvent) e);
            } else {
                processMouseEvent((MouseEvent) e);
            }
        } else if (e instanceof KeyEvent) {
            processKeyEvent((KeyEvent) e);
        } else if (e instanceof FocusEvent) {
            processFocusEvent((FocusEvent) e);
        } else if (e instanceof ComponentEvent) {
            processComponentEvent((ComponentEvent) e);
        }
    }

    protected void processMouseEvent(MouseEvent e) {
        for (MouseListener l : mouseListeners) {
            switch (e.getID()) {
                case MouseEvent.MOUSE_PRESSED: l.mousePressed(e); break;
                case MouseEvent.MOUSE_RELEASED: l.mouseReleased(e); break;
                case MouseEvent.MOUSE_CLICKED: l.mouseClicked(e); break;
                case MouseEvent.MOUSE_ENTERED: l.mouseEntered(e); break;
                case MouseEvent.MOUSE_EXITED: l.mouseExited(e); break;
                default: break;
            }
        }
    }

    protected void processMouseMotionEvent(MouseEvent e) {
        for (MouseMotionListener l : motionListeners) {
            if (e.getID() == MouseEvent.MOUSE_DRAGGED) l.mouseDragged(e);
            else l.mouseMoved(e);
        }
    }

    protected void processMouseWheelEvent(MouseWheelEvent e) {
        for (MouseWheelListener l : wheelListeners) l.mouseWheelMoved(e);
    }

    protected void processKeyEvent(KeyEvent e) {
        for (KeyListener l : keyListeners) {
            switch (e.getID()) {
                case KeyEvent.KEY_PRESSED: l.keyPressed(e); break;
                case KeyEvent.KEY_RELEASED: l.keyReleased(e); break;
                case KeyEvent.KEY_TYPED: l.keyTyped(e); break;
                default: break;
            }
        }
    }

    protected void processFocusEvent(FocusEvent e) {
        for (FocusListener l : focusListeners) {
            if (e.getID() == FocusEvent.FOCUS_GAINED) l.focusGained(e);
            else l.focusLost(e);
        }
    }

    protected void processComponentEvent(ComponentEvent e) {
        for (ComponentListener l : componentListeners) {
            switch (e.getID()) {
                case ComponentEvent.COMPONENT_RESIZED: l.componentResized(e); break;
                case ComponentEvent.COMPONENT_MOVED: l.componentMoved(e); break;
                case ComponentEvent.COMPONENT_SHOWN: l.componentShown(e); break;
                case ComponentEvent.COMPONENT_HIDDEN: l.componentHidden(e); break;
                default: break;
            }
        }
    }

    private void fireComponent(int id) {
        if (!componentListeners.isEmpty()) dispatchEvent(new ComponentEvent(this, id));
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" + (name != null ? name : "") + "," + x + "," + y + "," + width + "x" + height + "]";
    }

    // ------------------------------------------------------------------
    // 互換レイヤーの内部
    // ------------------------------------------------------------------

    /** 部品が自分で受ける押す操作(ボタンなど)。リスナーより先に呼ばれる。 */
    public void jvm$mouse(MouseEvent e) {}

    /** 部品が自分で受けるキー操作(文字欄など)。 */
    public void jvm$key(KeyEvent e) {}

    /** 押す操作を自分で受ける部品か。 */
    public boolean jvm$wantsMouse() { return false; }

    /** ドラッグを自分で受けるか(受けないならスクロールに使う)。 */
    public boolean jvm$wantsDrag() { return !motionListeners.isEmpty(); }

    /** ドラッグでスクロールする(JScrollPane)。動かしたら true。 */
    public boolean jvm$dragScroll(int dx, int dy) { return false; }

    /** キーの割り当て(Swing の InputMap)。 */
    public boolean jvm$binding(KeyEvent e, int condition) { return false; }

    public boolean jvm$hasMouseHandling() {
        return !mouseListeners.isEmpty() || !motionListeners.isEmpty() || jvm$wantsMouse();
    }

    public boolean jvm$hasKeyListeners() { return !keyListeners.isEmpty(); }

    /** 押した時にキーボードのフォーカスを受けるか(文字欄・キーのリスナーを持つ部品)。 */
    public boolean jvm$wantsKeyboardFocus() { return jvm$hasKeyListeners(); }

    public Window jvm$window() {
        Component c = this;
        while (c != null && !(c instanceof Window)) c = c.parent;
        return (Window) c;
    }

    void jvm$renderWindow() {
        Window w = jvm$window();
        if (w != null) w.jvm$requestRender();
    }

    public int jvm$paintStamp() { return paintStamp; }
    public void jvm$setPaintStamp(int s) { paintStamp = s; }
    public boolean jvm$isHeavyweight() { return heavyweight; }
    public Component jvm$parent() { return parent; }
}
