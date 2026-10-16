package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Insets;
import com.airsky.browser.jvm.java.awt.LayoutManager;
import com.airsky.browser.jvm.javax.swing.border.LineBorder;

/** javax.swing.JScrollPane の互換クラス。ドラッグでスクロールし、右端に細いバーを出す。 */
public class JScrollPane extends JComponent implements ScrollPaneConstants {
    private JViewport viewport = new JViewport();
    private int vPolicy, hPolicy;
    private final JScrollBar vbar = new JScrollBar(JScrollBar.VERTICAL);
    private final JScrollBar hbar = new JScrollBar(JScrollBar.HORIZONTAL);
    private Component columnHeader;

    public JScrollPane() { this(null, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED); }
    public JScrollPane(Component view) { this(view, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED); }
    public JScrollPane(int vsb, int hsb) { this(null, vsb, hsb); }

    public JScrollPane(Component view, int vsb, int hsb) {
        setLayout(null);
        vPolicy = vsb;
        hPolicy = hsb;
        add(viewport);
        vbar.viewport = viewport;
        hbar.viewport = viewport;
        if (view != null) setViewportView(view);
        setBorder(new LineBorder(JvmLook.OUTLINE_VARIANT));
        setOpaque(true);
    }

    public JViewport getViewport() { return viewport; }

    public void setViewport(JViewport v) {
        remove(viewport);
        viewport = v;
        add(v);
        vbar.viewport = v;
        hbar.viewport = v;
        revalidate();
    }

    public void setViewportView(Component view) { viewport.setView(view); }
    public void setColumnHeaderView(Component view) { columnHeader = view; }
    public void setRowHeaderView(Component view) {}
    public void setCorner(String key, Component c) {}
    public int getVerticalScrollBarPolicy() { return vPolicy; }
    public void setVerticalScrollBarPolicy(int p) { vPolicy = p; }
    public int getHorizontalScrollBarPolicy() { return hPolicy; }
    public void setHorizontalScrollBarPolicy(int p) { hPolicy = p; }
    public JScrollBar getVerticalScrollBar() { return vbar; }
    public JScrollBar getHorizontalScrollBar() { return hbar; }
    public void setVerticalScrollBar(JScrollBar b) {}
    public void setHorizontalScrollBar(JScrollBar b) {}
    public void setViewportBorder(com.airsky.browser.jvm.javax.swing.border.Border b) {}
    public void setWheelScrollingEnabled(boolean b) {}

    @Override
    public void setLayout(LayoutManager m) {
        super.setLayout(null);
    }

    @Override
    public void doLayout() {
        Insets in = getInsets();
        viewport.setBounds(in.left, in.top, getWidth() - in.left - in.right, getHeight() - in.top - in.bottom);
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        Component v = viewport.getView();
        Dimension d = v == null ? new Dimension(100, 100)
                : v instanceof Scrollable ? ((Scrollable) v).getPreferredScrollableViewportSize() : v.getPreferredSize();
        Insets in = getInsets();
        return new Dimension(Math.min(d.width, 2000) + in.left + in.right, Math.min(d.height, 2000) + in.top + in.bottom);
    }

    @Override
    public Dimension getMinimumSize() { return isMinimumSizeSet() ? super.getMinimumSize() : new Dimension(40, 40); }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        Component v = viewport.getView();
        if (v == null) return;
        JvmLook.smooth(g);
        int vh = viewport.getHeight(), ch = v.getHeight();
        Insets in = getInsets();
        g.setColor(new com.airsky.browser.jvm.java.awt.Color(0, 0, 0, 70));
        if (ch > vh && vPolicy != VERTICAL_SCROLLBAR_NEVER) {
            int th = Math.max(20, vh * vh / ch);
            int ty = (vh - th) * viewport.getViewPosition().y / Math.max(1, ch - vh);
            g.fillRoundRect(getWidth() - in.right - 6, in.top + ty, 4, th, 4, 4);
        }
        int vw = viewport.getWidth(), cw = v.getWidth();
        if (cw > vw && hPolicy != HORIZONTAL_SCROLLBAR_NEVER) {
            int tw = Math.max(20, vw * vw / cw);
            int tx = (vw - tw) * viewport.getViewPosition().x / Math.max(1, cw - vw);
            g.fillRoundRect(in.left + tx, getHeight() - in.bottom - 6, tw, 4, 4, 4);
        }
    }

    @Override
    public boolean jvm$dragScroll(int dx, int dy) {
        return viewport.jvm$dragScroll(dx, dy);
    }
}
