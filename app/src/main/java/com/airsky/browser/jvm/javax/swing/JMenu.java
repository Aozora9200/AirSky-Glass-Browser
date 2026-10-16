package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.FontMetrics;

import java.util.ArrayList;
import java.util.List;

/** javax.swing.JMenu の互換クラス。押すと項目の一覧を出す。 */
public class JMenu extends JMenuItem {
    final List<Component> items = new ArrayList<>();
    private boolean popupVisible;

    public JMenu() { this(""); }
    public JMenu(String s) { super(s); }
    public JMenu(String s, boolean tearOff) { this(s); }
    public JMenu(Action a) { this(""); setAction(a); }

    public JMenuItem add(JMenuItem item) {
        items.add(item);
        return item;
    }

    public Component add(Component c) {
        items.add(c);
        return c;
    }

    public Component add(Component c, int index) {
        items.add(Math.max(0, Math.min(index, items.size())), c);
        return c;
    }

    public JMenuItem add(String s) { return add(new JMenuItem(s)); }
    public JMenuItem add(Action a) { return add(new JMenuItem(a)); }
    public void addSeparator() { items.add(new JSeparator()); }
    public void insert(String s, int pos) { items.add(pos, new JMenuItem(s)); }
    public JMenuItem insert(JMenuItem item, int pos) { items.add(pos, item); return item; }
    public void insertSeparator(int pos) { items.add(pos, new JSeparator()); }

    public JMenuItem getItem(int pos) {
        Component c = items.get(pos);
        return c instanceof JMenuItem ? (JMenuItem) c : null;
    }

    public int getItemCount() { return items.size(); }
    public int getMenuComponentCount() { return items.size(); }
    public Component getMenuComponent(int n) { return items.get(n); }
    public Component[] getMenuComponents() { return items.toArray(new Component[0]); }
    public void remove(JMenuItem item) { items.remove(item); }
    @Override public void remove(int pos) { items.remove(pos); }
    @Override public void remove(Component c) { items.remove(c); }
    @Override public void removeAll() { items.clear(); }
    public boolean isTopLevelMenu() { return getParent() instanceof JMenuBar; }
    public boolean isPopupMenuVisible() { return popupVisible; }
    public void setPopupMenuVisible(boolean b) { if (b) open(); }
    public JPopupMenu getPopupMenu() {
        JPopupMenu p = new JPopupMenu(getText());
        for (Component c : items) p.jvm$items().add(c);
        return p;
    }
    public void setDelay(int d) {}
    public void addMenuListener(Object l) {}

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet() || !isTopLevelMenu()) return super.getPreferredSize();
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(getText()) + 28, fm.getHeight() + 18);
    }

    @Override
    void jvm$clicked(long when, int modifiers) { open(); }

    private void open() {
        popupVisible = true;
        try {
            JPopupMenu.jvm$choose(getText(), items);
        } finally {
            popupVisible = false;
        }
    }

    boolean jvm$accelerate(KeyStroke k) {
        for (Component c : items) {
            if (c instanceof JMenu) {
                if (((JMenu) c).jvm$accelerate(k)) return true;
            } else if (c instanceof JMenuItem) {
                JMenuItem m = (JMenuItem) c;
                if (k.equals(m.getAccelerator()) && m.isEnabled()) {
                    m.doClick();
                    return true;
                }
            }
        }
        return false;
    }
}
