package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.FontMetrics;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.LayoutManager;
import com.airsky.browser.jvm.java.awt.event.MouseEvent;
import com.airsky.browser.jvm.javax.swing.event.ChangeEvent;
import com.airsky.browser.jvm.javax.swing.event.ChangeListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** javax.swing.JTabbedPane の互換クラス(上にタブ、下に選んだタブの中身)。 */
public class JTabbedPane extends JComponent implements SwingConstants {
    public static final int WRAP_TAB_LAYOUT = 0;
    public static final int SCROLL_TAB_LAYOUT = 1;

    private static final class Tab {
        String title, tip;
        Icon icon;
        Component comp;
        boolean enabled = true;
    }

    private final List<Tab> tabs = new ArrayList<>();
    private int selected = -1;
    private boolean internal;
    private final CopyOnWriteArrayList<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    public JTabbedPane() { this(TOP, WRAP_TAB_LAYOUT); }
    public JTabbedPane(int placement) { this(placement, WRAP_TAB_LAYOUT); }

    public JTabbedPane(int placement, int policy) {
        super.setLayout(null);
        setOpaque(true);
        setBackground(JvmLook.PANEL);
    }

    @Override public void setLayout(LayoutManager m) {}
    public void setTabPlacement(int p) {}
    public void setTabLayoutPolicy(int p) {}
    public void addChangeListener(ChangeListener l) { if (l != null) listeners.add(l); }
    public void removeChangeListener(ChangeListener l) { listeners.remove(l); }

    private int stripHeight() { return getFontMetrics(getFont()).getHeight() + 22; }

    public void insertTab(String title, Icon icon, Component comp, String tip, int index) {
        Tab t = new Tab();
        t.title = title != null ? title : "";
        t.icon = icon;
        t.comp = comp;
        t.tip = tip;
        index = Math.max(0, Math.min(index, tabs.size()));
        tabs.add(index, t);
        if (comp != null) {
            internal = true;
            super.addImpl(comp, null, -1);
            internal = false;
        }
        if (selected < 0) setSelectedIndex(0);
        else if (index <= selected) selected++;
        revalidate();
    }

    public void addTab(String title, Component comp) { insertTab(title, null, comp, null, tabs.size()); }
    public void addTab(String title, Icon icon, Component comp) { insertTab(title, icon, comp, null, tabs.size()); }
    public void addTab(String title, Icon icon, Component comp, String tip) { insertTab(title, icon, comp, tip, tabs.size()); }

    @Override
    protected void addImpl(Component comp, Object constraints, int index) {
        if (internal) {
            super.addImpl(comp, constraints, index);
            return;
        }
        String title = constraints instanceof String ? (String) constraints : comp.getName();
        insertTab(title, constraints instanceof Icon ? (Icon) constraints : null, comp, null, index < 0 ? tabs.size() : index);
    }

    @Override
    public Component add(String title, Component comp) {
        addTab(title, comp);
        return comp;
    }

    public void removeTabAt(int i) {
        Tab t = tabs.remove(i);
        if (t.comp != null) super.remove(t.comp);
        if (selected >= tabs.size()) selected = tabs.size() - 1;
        setSelectedIndex(selected);
        revalidate();
    }

    @Override
    public void remove(Component c) {
        int i = indexOfComponent(c);
        if (i >= 0) removeTabAt(i);
        else super.remove(c);
    }

    @Override
    public void removeAll() {
        while (!tabs.isEmpty()) removeTabAt(tabs.size() - 1);
    }

    public int getTabCount() { return tabs.size(); }
    public int getTabRunCount() { return 1; }
    public int getSelectedIndex() { return selected; }

    public void setSelectedIndex(int i) {
        if (i >= tabs.size()) return;
        boolean changed = i != selected;
        selected = i;
        for (int k = 0; k < tabs.size(); k++) {
            Component c = tabs.get(k).comp;
            if (c != null) c.setVisible(k == i);
        }
        revalidate();
        if (changed) {
            ChangeEvent e = new ChangeEvent(this);
            for (ChangeListener l : listeners) l.stateChanged(e);
        }
    }

    public Component getSelectedComponent() { return selected >= 0 ? tabs.get(selected).comp : null; }
    public void setSelectedComponent(Component c) { int i = indexOfComponent(c); if (i >= 0) setSelectedIndex(i); }
    public int indexOfComponent(Component c) { for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).comp == c) return i; return -1; }
    public int indexOfTab(String title) { for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).title.equals(title)) return i; return -1; }
    public int indexOfTab(Icon icon) { for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).icon == icon) return i; return -1; }
    public String getTitleAt(int i) { return tabs.get(i).title; }
    public void setTitleAt(int i, String t) { tabs.get(i).title = t; repaint(); }
    public Icon getIconAt(int i) { return tabs.get(i).icon; }
    public void setIconAt(int i, Icon icon) { tabs.get(i).icon = icon; repaint(); }
    public String getToolTipTextAt(int i) { return tabs.get(i).tip; }
    public void setToolTipTextAt(int i, String t) { tabs.get(i).tip = t; }
    public Component getComponentAt(int i) { return tabs.get(i).comp; }

    public void setComponentAt(int i, Component c) {
        Tab t = tabs.get(i);
        if (t.comp != null) super.remove(t.comp);
        t.comp = c;
        internal = true;
        super.addImpl(c, null, -1);
        internal = false;
        setSelectedIndex(selected);
    }

    public boolean isEnabledAt(int i) { return tabs.get(i).enabled; }
    public void setEnabledAt(int i, boolean b) { tabs.get(i).enabled = b; repaint(); }
    public void setBackgroundAt(int i, com.airsky.browser.jvm.java.awt.Color c) {}
    public void setForegroundAt(int i, com.airsky.browser.jvm.java.awt.Color c) {}
    public void setMnemonicAt(int i, int m) {}
    public void setTabComponentAt(int i, Component c) {}

    @Override
    public void doLayout() {
        int sh = stripHeight();
        for (Tab t : tabs) if (t.comp != null) t.comp.setBounds(0, sh, getWidth(), Math.max(0, getHeight() - sh));
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        int w = 0, h = 0;
        for (Tab t : tabs) {
            if (t.comp == null) continue;
            Dimension d = t.comp.getPreferredSize();
            w = Math.max(w, d.width);
            h = Math.max(h, d.height);
        }
        FontMetrics fm = getFontMetrics(getFont());
        int tw = 0;
        for (Tab t : tabs) tw += fm.stringWidth(t.title) + 32;
        return new Dimension(Math.max(w, tw), h + stripHeight());
    }

    private int[] tabX(FontMetrics fm) {
        int[] xs = new int[tabs.size() + 1];
        int total = 0;
        for (Tab t : tabs) total += fm.stringWidth(t.title) + 32;
        boolean fill = total < getWidth();
        int x = 0;
        for (int i = 0; i < tabs.size(); i++) {
            xs[i] = x;
            x += fill ? getWidth() / Math.max(1, tabs.size()) : fm.stringWidth(tabs.get(i).title) + 32;
        }
        xs[tabs.size()] = fill ? getWidth() : x;
        return xs;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        JvmLook.smooth(g);
        int sh = stripHeight();
        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        int[] xs = tabX(fm);
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            boolean sel = i == selected;
            g.setColor(!t.enabled ? JvmLook.DISABLED : sel ? JvmLook.PRIMARY : JvmLook.TEXT_VARIANT);
            JvmLook.text(g, t.title, xs[i], 0, xs[i + 1] - xs[i], sh - 3, 1);
            if (sel) {
                int tw = Math.min(fm.stringWidth(t.title) + 8, xs[i + 1] - xs[i]);
                int cx = (xs[i] + xs[i + 1]) / 2;
                g.fillRoundRect(cx - tw / 2, sh - 3, tw, 3, 3, 3);
            }
        }
        g.setColor(JvmLook.OUTLINE_VARIANT);
        g.fillRect(0, sh - 1, getWidth(), 1);
    }

    @Override public boolean jvm$wantsMouse() { return true; }

    @Override
    public void jvm$mouse(MouseEvent e) {
        if (e.getID() != MouseEvent.MOUSE_CLICKED || e.getY() > stripHeight()) return;
        int[] xs = tabX(getFontMetrics(getFont()));
        for (int i = 0; i < tabs.size(); i++) {
            if (e.getX() >= xs[i] && e.getX() < xs[i + 1] && tabs.get(i).enabled) {
                setSelectedIndex(i);
                return;
            }
        }
    }
}
