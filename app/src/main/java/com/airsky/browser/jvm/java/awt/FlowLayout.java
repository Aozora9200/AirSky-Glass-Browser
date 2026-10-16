package com.airsky.browser.jvm.java.awt;

/** java.awt.FlowLayout の互換クラス。 */
public class FlowLayout implements LayoutManager, java.io.Serializable {
    public static final int LEFT = 0;
    public static final int CENTER = 1;
    public static final int RIGHT = 2;
    public static final int LEADING = 3;
    public static final int TRAILING = 4;

    int align, hgap, vgap;
    boolean alignOnBaseline;

    public FlowLayout() { this(CENTER, 5, 5); }
    public FlowLayout(int align) { this(align, 5, 5); }

    public FlowLayout(int align, int hgap, int vgap) {
        this.align = align;
        this.hgap = hgap;
        this.vgap = vgap;
    }

    public int getAlignment() { return align; }
    public void setAlignment(int a) { align = a; }
    public int getHgap() { return hgap; }
    public void setHgap(int h) { hgap = h; }
    public int getVgap() { return vgap; }
    public void setVgap(int v) { vgap = v; }
    public void setAlignOnBaseline(boolean b) { alignOnBaseline = b; }
    public boolean getAlignOnBaseline() { return alignOnBaseline; }

    @Override public void addLayoutComponent(String name, Component comp) {}
    @Override public void removeLayoutComponent(Component comp) {}

    @Override
    public Dimension preferredLayoutSize(Container target) { return size(target, true); }

    @Override
    public Dimension minimumLayoutSize(Container target) { return size(target, false); }

    private Dimension size(Container target, boolean pref) {
        Dimension dim = new Dimension(0, 0);
        boolean first = true;
        for (Component m : target.getComponents()) {
            if (!m.isVisible()) continue;
            Dimension d = pref ? m.getPreferredSize() : m.getMinimumSize();
            dim.height = Math.max(dim.height, d.height);
            if (first) first = false;
            else dim.width += hgap;
            dim.width += d.width;
        }
        Insets in = target.getInsets();
        dim.width += in.left + in.right + hgap * 2;
        dim.height += in.top + in.bottom + vgap * 2;
        return dim;
    }

    @Override
    public void layoutContainer(Container target) {
        Insets in = target.getInsets();
        int maxwidth = target.getWidth() - (in.left + in.right + hgap * 2);
        Component[] comps = target.getComponents();
        int x = 0, y = in.top + vgap;
        int rowh = 0, start = 0;
        for (int i = 0; i < comps.length; i++) {
            Component m = comps[i];
            if (!m.isVisible()) continue;
            Dimension d = m.getPreferredSize();
            m.setSize(d.width, d.height);
            if (x == 0 || x + d.width <= maxwidth) {
                if (x > 0) x += hgap;
                x += d.width;
                rowh = Math.max(rowh, d.height);
            } else {
                moveComponents(target, in.left + hgap, y, maxwidth - x, rowh, start, i, comps);
                x = d.width;
                y += vgap + rowh;
                rowh = d.height;
                start = i;
            }
        }
        moveComponents(target, in.left + hgap, y, maxwidth - x, rowh, start, comps.length, comps);
    }

    private void moveComponents(Container target, int x, int y, int extra, int height, int start, int end, Component[] comps) {
        switch (align) {
            case CENTER: x += extra / 2; break;
            case RIGHT: case TRAILING: x += extra; break;
            default: break;
        }
        for (int i = start; i < end; i++) {
            Component m = comps[i];
            if (!m.isVisible()) continue;
            int cy = y + (height - m.getHeight()) / 2;
            m.setLocation(x, cy);
            x += m.getWidth() + hgap;
        }
    }

    @Override public String toString() { return getClass().getName() + "[hgap=" + hgap + ",vgap=" + vgap + ",align=" + align + "]"; }
}
