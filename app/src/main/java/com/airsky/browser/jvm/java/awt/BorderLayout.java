package com.airsky.browser.jvm.java.awt;

/** java.awt.BorderLayout の互換クラス。 */
public class BorderLayout implements LayoutManager2, java.io.Serializable {
    public static final String NORTH = "North";
    public static final String SOUTH = "South";
    public static final String EAST = "East";
    public static final String WEST = "West";
    public static final String CENTER = "Center";
    public static final String BEFORE_FIRST_LINE = "First";
    public static final String AFTER_LAST_LINE = "Last";
    public static final String BEFORE_LINE_BEGINS = "Before";
    public static final String AFTER_LINE_ENDS = "After";
    public static final String PAGE_START = BEFORE_FIRST_LINE;
    public static final String PAGE_END = AFTER_LAST_LINE;
    public static final String LINE_START = BEFORE_LINE_BEGINS;
    public static final String LINE_END = AFTER_LINE_ENDS;

    int hgap, vgap;
    Component north, west, east, south, center;

    public BorderLayout() { this(0, 0); }

    public BorderLayout(int hgap, int vgap) {
        this.hgap = hgap;
        this.vgap = vgap;
    }

    public int getHgap() { return hgap; }
    public void setHgap(int h) { hgap = h; }
    public int getVgap() { return vgap; }
    public void setVgap(int v) { vgap = v; }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        if (constraints == null || constraints instanceof String) {
            addLayoutComponent((String) constraints, comp);
        } else {
            throw new IllegalArgumentException("cannot add to layout: constraint must be a string (or null)");
        }
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        if (name == null) name = CENTER;
        switch (name) {
            case CENTER: center = comp; break;
            case NORTH: case BEFORE_FIRST_LINE: north = comp; break;
            case SOUTH: case AFTER_LAST_LINE: south = comp; break;
            case EAST: case AFTER_LINE_ENDS: east = comp; break;
            case WEST: case BEFORE_LINE_BEGINS: west = comp; break;
            default: throw new IllegalArgumentException("cannot add to layout: unknown constraint: " + name);
        }
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        if (comp == center) center = null;
        else if (comp == north) north = null;
        else if (comp == south) south = null;
        else if (comp == east) east = null;
        else if (comp == west) west = null;
    }

    public Component getLayoutComponent(Object constraints) {
        if (CENTER.equals(constraints)) return center;
        if (NORTH.equals(constraints) || PAGE_START.equals(constraints)) return north;
        if (SOUTH.equals(constraints) || PAGE_END.equals(constraints)) return south;
        if (WEST.equals(constraints) || LINE_START.equals(constraints)) return west;
        if (EAST.equals(constraints) || LINE_END.equals(constraints)) return east;
        return null;
    }

    public Component getLayoutComponent(Container target, Object constraints) { return getLayoutComponent(constraints); }

    public Object getConstraints(Component comp) {
        if (comp == null) return null;
        if (comp == center) return CENTER;
        if (comp == north) return NORTH;
        if (comp == south) return SOUTH;
        if (comp == west) return WEST;
        if (comp == east) return EAST;
        return null;
    }

    private static boolean on(Component c, Container parent) {
        return c != null && c.isVisible() && c.getParent() == parent;
    }

    @Override
    public Dimension minimumLayoutSize(Container target) { return size(target, false); }

    @Override
    public Dimension preferredLayoutSize(Container target) { return size(target, true); }

    private Dimension size(Container target, boolean pref) {
        Dimension dim = new Dimension(0, 0);
        Component c;
        if (on(c = east, target)) {
            Dimension d = pref ? c.getPreferredSize() : c.getMinimumSize();
            dim.width += d.width + hgap;
            dim.height = Math.max(d.height, dim.height);
        }
        if (on(c = west, target)) {
            Dimension d = pref ? c.getPreferredSize() : c.getMinimumSize();
            dim.width += d.width + hgap;
            dim.height = Math.max(d.height, dim.height);
        }
        if (on(c = center, target)) {
            Dimension d = pref ? c.getPreferredSize() : c.getMinimumSize();
            dim.width += d.width;
            dim.height = Math.max(d.height, dim.height);
        }
        if (on(c = north, target)) {
            Dimension d = pref ? c.getPreferredSize() : c.getMinimumSize();
            dim.width = Math.max(d.width, dim.width);
            dim.height += d.height + vgap;
        }
        if (on(c = south, target)) {
            Dimension d = pref ? c.getPreferredSize() : c.getMinimumSize();
            dim.width = Math.max(d.width, dim.width);
            dim.height += d.height + vgap;
        }
        Insets in = target.getInsets();
        dim.width += in.left + in.right;
        dim.height += in.top + in.bottom;
        return dim;
    }

    @Override
    public Dimension maximumLayoutSize(Container target) { return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE); }
    @Override public float getLayoutAlignmentX(Container parent) { return 0.5f; }
    @Override public float getLayoutAlignmentY(Container parent) { return 0.5f; }
    @Override public void invalidateLayout(Container target) {}

    @Override
    public void layoutContainer(Container target) {
        Insets in = target.getInsets();
        int top = in.top, bottom = target.getHeight() - in.bottom;
        int left = in.left, right = target.getWidth() - in.right;
        Component c;
        if (on(c = north, target)) {
            Dimension d = c.getPreferredSize();
            c.setBounds(left, top, right - left, d.height);
            top += d.height + vgap;
        }
        if (on(c = south, target)) {
            Dimension d = c.getPreferredSize();
            c.setBounds(left, bottom - d.height, right - left, d.height);
            bottom -= d.height + vgap;
        }
        if (on(c = east, target)) {
            Dimension d = c.getPreferredSize();
            c.setBounds(right - d.width, top, d.width, bottom - top);
            right -= d.width + hgap;
        }
        if (on(c = west, target)) {
            Dimension d = c.getPreferredSize();
            c.setBounds(left, top, d.width, bottom - top);
            left += d.width + hgap;
        }
        if (on(c = center, target)) {
            c.setBounds(left, top, right - left, bottom - top);
        }
    }

    @Override public String toString() { return getClass().getName() + "[hgap=" + hgap + ",vgap=" + vgap + "]"; }
}
