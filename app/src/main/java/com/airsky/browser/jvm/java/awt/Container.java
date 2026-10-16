package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.java.awt.event.ContainerEvent;
import com.airsky.browser.jvm.java.awt.event.ContainerListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** java.awt.Container の互換クラス。 */
public class Container extends Component {
    final List<Component> children = new CopyOnWriteArrayList<>();
    LayoutManager layout;
    private final CopyOnWriteArrayList<ContainerListener> containerListeners = new CopyOnWriteArrayList<>();
    private static int stamp;

    public Container() {}

    public int getComponentCount() { return children.size(); }
    @Deprecated public int countComponents() { return getComponentCount(); }

    public Component getComponent(int n) {
        if (n < 0 || n >= children.size()) throw new ArrayIndexOutOfBoundsException("No such child: " + n);
        return children.get(n);
    }

    public Component[] getComponents() { return children.toArray(new Component[0]); }

    public int getComponentZOrder(Component c) { return children.indexOf(c); }

    public void setComponentZOrder(Component c, int index) {
        if (children.remove(c)) children.add(Math.max(0, Math.min(index, children.size())), c);
        repaint();
    }

    public Insets getInsets() { return new Insets(0, 0, 0, 0); }
    @Deprecated public Insets insets() { return getInsets(); }

    public Component add(Component c) {
        addImpl(c, null, -1);
        return c;
    }

    public Component add(String name, Component c) {
        addImpl(c, name, -1);
        return c;
    }

    public Component add(Component c, int index) {
        addImpl(c, null, index);
        return c;
    }

    public void add(Component c, Object constraints) {
        addImpl(c, constraints, -1);
    }

    public void add(Component c, Object constraints, int index) {
        addImpl(c, constraints, index);
    }

    protected void addImpl(Component comp, Object constraints, int index) {
        if (comp == null) throw new NullPointerException();
        if (comp instanceof Window) throw new IllegalArgumentException("adding a window to a container");
        for (Container p = this; p != null; p = p.parent) {
            if (p == comp) throw new IllegalArgumentException("adding container's parent to itself");
        }
        if (comp.parent != null) comp.parent.remove(comp);
        if (index < 0 || index > children.size()) children.add(comp);
        else children.add(index, comp);
        comp.parent = this;
        if (layout instanceof LayoutManager2) {
            ((LayoutManager2) layout).addLayoutComponent(comp, constraints);
        } else if (layout != null) {
            layout.addLayoutComponent(constraints instanceof String ? (String) constraints : null, comp);
        }
        comp.invalidate();
        invalidate();
        if (!containerListeners.isEmpty()) {
            ContainerEvent e = new ContainerEvent(this, ContainerEvent.COMPONENT_ADDED, comp);
            for (ContainerListener l : containerListeners) l.componentAdded(e);
        }
        repaint();
    }

    public void remove(int index) {
        remove(getComponent(index));
    }

    public void remove(Component comp) {
        if (comp == null || !children.remove(comp)) return;
        if (layout != null) layout.removeLayoutComponent(comp);
        comp.parent = null;
        Window w = jvm$window();
        if (w != null && w.focusOwner != null) {
            for (Component c = w.focusOwner; c != null; c = c.parent) {
                if (c == comp) {
                    w.focusOwner = null;
                    break;
                }
            }
        }
        invalidate();
        if (!containerListeners.isEmpty()) {
            ContainerEvent e = new ContainerEvent(this, ContainerEvent.COMPONENT_REMOVED, comp);
            for (ContainerListener l : containerListeners) l.componentRemoved(e);
        }
        repaint();
    }

    public void removeAll() {
        for (Component c : getComponents()) remove(c);
    }

    public boolean isAncestorOf(Component c) {
        for (Container p = c != null ? c.parent : null; p != null; p = p.parent) {
            if (p == this) return true;
        }
        return false;
    }

    public LayoutManager getLayout() { return layout; }

    public void setLayout(LayoutManager mgr) {
        layout = mgr;
        invalidate();
    }

    public void addContainerListener(ContainerListener l) { if (l != null) containerListeners.add(l); }
    public void removeContainerListener(ContainerListener l) { containerListeners.remove(l); }

    @Override
    public void doLayout() {
        if (layout != null) layout.layoutContainer(this);
    }

    @Override
    public void invalidate() {
        if (layout instanceof LayoutManager2) ((LayoutManager2) layout).invalidateLayout(this);
        super.invalidate();
    }

    @Override
    public void validate() {
        if (!valid) {
            doLayout();
            valid = true;
        }
        for (Component c : children) {
            if (c instanceof Container) c.validate();
            else c.valid = true;
        }
    }

    /** 配置を必ずやり直す(描く前に呼ぶ)。 */
    public void jvm$validateTree() {
        valid = false;
        doLayout();
        valid = true;
        for (Component c : children) {
            if (c instanceof Container) ((Container) c).jvm$validateTree();
            else c.valid = true;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (prefSize != null) return new Dimension(prefSize);
        if (layout != null) return layout.preferredLayoutSize(this);
        return super.getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        if (minSize != null) return new Dimension(minSize);
        if (layout != null) return layout.minimumLayoutSize(this);
        return super.getMinimumSize();
    }

    @Override
    public Dimension getMaximumSize() {
        if (maxSize != null) return new Dimension(maxSize);
        if (layout instanceof LayoutManager2) return ((LayoutManager2) layout).maximumLayoutSize(this);
        return super.getMaximumSize();
    }

    @Override
    public float getAlignmentX() {
        if (layout instanceof LayoutManager2) return ((LayoutManager2) layout).getLayoutAlignmentX(this);
        return super.getAlignmentX();
    }

    @Override
    public float getAlignmentY() {
        if (layout instanceof LayoutManager2) return ((LayoutManager2) layout).getLayoutAlignmentY(this);
        return super.getAlignmentY();
    }

    @Override
    public void paint(Graphics g) {
        paintComponents(g);
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    /** 子を描く。0 番目が一番手前なので、後ろから描く。 */
    public void paintComponents(Graphics g) {
        for (int i = children.size() - 1; i >= 0; i--) {
            jvm$paintChild(g, children.get(i));
        }
    }

    public void printComponents(Graphics g) { paintComponents(g); }

    /** 子 1 つを、その位置へずらして切り抜いた Graphics で描く。 */
    public static void jvm$paintChild(Graphics g, Component c) {
        if (!c.visible || c.width <= 0 || c.height <= 0) return;
        Rectangle clip = g.getClipBounds();
        if (clip != null && !clip.intersects(new Rectangle(c.x, c.y, c.width, c.height))) {
            c.paintStamp = stamp;
            return;
        }
        Graphics cg = g.create();
        try {
            cg.translate(c.x, c.y);
            cg.clipRect(0, 0, c.width, c.height);
            cg.setFont(c.getFont());
            Color fg = c.getForeground();
            cg.setColor(fg != null ? fg : Color.BLACK);
            if (cg instanceof Graphics2D) {
                Color bg = c.getBackground();
                if (bg != null) ((Graphics2D) cg).setBackground(bg);
            }
            c.paintStamp = stamp;
            if (c.heavyweight && !(c instanceof Container)) c.update(cg);
            else c.paint(cg);
        } finally {
            cg.dispose();
        }
    }

    public static int jvm$nextStamp() { return ++stamp; }
    public static int jvm$stamp() { return stamp; }

    @Override
    public Component getComponentAt(int px, int py) {
        if (!contains(px, py)) return null;
        for (Component c : children) {
            if (c.visible && c.getBounds().contains(px, py)) return c;
        }
        return this;
    }

    public Component findComponentAt(int px, int py) {
        if (!visible || !contains(px, py)) return null;
        for (Component c : children) {
            if (!c.visible) continue;
            int cx = px - c.x, cy = py - c.y;
            if (c instanceof Container) {
                Component f = ((Container) c).findComponentAt(cx, cy);
                if (f != null) return f;
            } else if (c.contains(cx, cy)) {
                return c;
            }
        }
        return this;
    }

    public Component findComponentAt(Point p) { return findComponentAt(p.x, p.y); }

    /** 子から順に、条件に合う最初の部品を探す(自分を含む)。 */
    public Component jvm$find(java.util.function.Predicate<Component> test) {
        if (test.test(this)) return this;
        for (Component c : children) {
            if (!c.visible) continue;
            if (c instanceof Container) {
                Component f = ((Container) c).jvm$find(test);
                if (f != null) return f;
            } else if (test.test(c)) {
                return c;
            }
        }
        return null;
    }

    public java.util.List<Component> jvm$children() { return children; }
}
