package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Container;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.Insets;
import com.airsky.browser.jvm.java.awt.LayoutManager2;

/** javax.swing.BoxLayout の互換クラス。 */
public class BoxLayout implements LayoutManager2, java.io.Serializable {
    public static final int X_AXIS = 0;
    public static final int Y_AXIS = 1;
    public static final int LINE_AXIS = 2;
    public static final int PAGE_AXIS = 3;

    private final Container target;
    private final int axis;

    public BoxLayout(Container target, int axis) {
        if (axis < X_AXIS || axis > PAGE_AXIS) throw new com.airsky.browser.jvm.java.awt.AWTError("Invalid axis");
        this.target = target;
        this.axis = axis;
    }

    public final Container getTarget() { return target; }
    public final int getAxis() { return axis; }
    private boolean horizontal() { return axis == X_AXIS || axis == LINE_AXIS; }

    @Override public void addLayoutComponent(String name, Component comp) {}
    @Override public void addLayoutComponent(Component comp, Object constraints) {}
    @Override public void removeLayoutComponent(Component comp) {}
    @Override public void invalidateLayout(Container target) {}
    @Override public float getLayoutAlignmentX(Container t) { return 0.5f; }
    @Override public float getLayoutAlignmentY(Container t) { return 0.5f; }

    private Dimension size(Container parent, int which) {
        int major = 0, minor = 0;
        for (Component c : parent.getComponents()) {
            if (!c.isVisible()) continue;
            Dimension d = which == 0 ? c.getMinimumSize() : which == 1 ? c.getPreferredSize() : c.getMaximumSize();
            if (horizontal()) {
                major = (int) Math.min((long) major + d.width, Integer.MAX_VALUE);
                minor = Math.max(minor, d.height);
            } else {
                major = (int) Math.min((long) major + d.height, Integer.MAX_VALUE);
                minor = Math.max(minor, d.width);
            }
        }
        Insets in = parent.getInsets();
        int w = horizontal() ? major : minor, h = horizontal() ? minor : major;
        return new Dimension((int) Math.min((long) w + in.left + in.right, Integer.MAX_VALUE),
                (int) Math.min((long) h + in.top + in.bottom, Integer.MAX_VALUE));
    }

    @Override public Dimension preferredLayoutSize(Container parent) { return size(parent, 1); }
    @Override public Dimension minimumLayoutSize(Container parent) { return size(parent, 0); }
    @Override public Dimension maximumLayoutSize(Container parent) { return size(parent, 2); }

    @Override
    public void layoutContainer(Container parent) {
        Component[] all = parent.getComponents();
        java.util.List<Component> comps = new java.util.ArrayList<>();
        for (Component c : all) if (c.isVisible()) comps.add(c);
        int n = comps.size();
        if (n == 0) return;
        Insets in = parent.getInsets();
        int availMajor = horizontal() ? parent.getWidth() - in.left - in.right : parent.getHeight() - in.top - in.bottom;
        int availMinor = horizontal() ? parent.getHeight() - in.top - in.bottom : parent.getWidth() - in.left - in.right;
        int[] size = new int[n], min = new int[n], max = new int[n];
        long total = 0;
        for (int i = 0; i < n; i++) {
            Component c = comps.get(i);
            Dimension p = c.getPreferredSize(), mn = c.getMinimumSize(), mx = c.getMaximumSize();
            size[i] = horizontal() ? p.width : p.height;
            min[i] = Math.min(size[i], horizontal() ? mn.width : mn.height);
            max[i] = Math.max(size[i], horizontal() ? mx.width : mx.height);
            total += size[i];
        }
        long extra = availMajor - total;
        if (extra > 0) {
            // 伸ばせる部品(最大の方が大きい)に余りを分ける
            for (int pass = 0; pass < 4 && extra > 0; pass++) {
                int growable = 0;
                for (int i = 0; i < n; i++) if (size[i] < max[i]) growable++;
                if (growable == 0) break;
                long share = Math.max(1, extra / growable);
                for (int i = 0; i < n && extra > 0; i++) {
                    if (size[i] >= max[i]) continue;
                    long add = Math.min(Math.min(share, extra), (long) max[i] - size[i]);
                    size[i] += (int) add;
                    extra -= add;
                }
            }
        } else if (extra < 0) {
            long shrinkable = 0;
            for (int i = 0; i < n; i++) shrinkable += size[i] - min[i];
            if (shrinkable > 0) {
                long need = Math.min(-extra, shrinkable);
                for (int i = 0; i < n; i++) size[i] -= (int) (need * (size[i] - min[i]) / shrinkable);
            }
        }
        int pos = horizontal() ? in.left : in.top;
        for (int i = 0; i < n; i++) {
            Component c = comps.get(i);
            Dimension mx = c.getMaximumSize();
            Dimension p = c.getPreferredSize();
            int cross = Math.min(availMinor, horizontal() ? Math.max(mx.height, p.height) : Math.max(mx.width, p.width));
            cross = Math.max(Math.min(cross, availMinor), 0);
            float align = horizontal() ? c.getAlignmentY() : c.getAlignmentX();
            int off = Math.round((availMinor - cross) * align);
            if (horizontal()) c.setBounds(pos, in.top + off, size[i], cross);
            else c.setBounds(in.left + off, pos, cross, size[i]);
            pos += size[i];
        }
    }
}
