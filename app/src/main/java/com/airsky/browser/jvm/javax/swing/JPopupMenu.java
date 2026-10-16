package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.Component;

import java.util.ArrayList;
import java.util.List;

/** javax.swing.JPopupMenu の互換クラス。表示すると Android の一覧で選ぶ。 */
public class JPopupMenu extends JComponent {
    private final List<Component> items = new ArrayList<>();
    private String label;
    private Component invoker;
    private boolean showing;

    public JPopupMenu() { this(null); }

    public JPopupMenu(String label) {
        this.label = label;
        setVisible(false);
    }

    List<Component> jvm$items() { return items; }

    public JMenuItem add(JMenuItem item) {
        items.add(item);
        return item;
    }

    @Override
    public Component add(Component c) {
        items.add(c);
        return c;
    }

    public JMenuItem add(String s) { return add(new JMenuItem(s)); }
    public JMenuItem add(Action a) { return add(new JMenuItem(a)); }
    public void addSeparator() { items.add(new JSeparator()); }
    public void insert(Component c, int index) { items.add(index, c); }
    @Override public void remove(Component c) { items.remove(c); }
    @Override public void remove(int i) { items.remove(i); }
    @Override public void removeAll() { items.clear(); }
    @Override public int getComponentCount() { return items.size(); }
    @Override public Component getComponent(int i) { return items.get(i); }
    @Override public Component[] getComponents() { return items.toArray(new Component[0]); }
    public String getLabel() { return label; }
    public void setLabel(String l) { label = l; }
    public Component getInvoker() { return invoker; }
    public void setInvoker(Component c) { invoker = c; }
    public void setLightWeightPopupEnabled(boolean b) {}
    public void addPopupMenuListener(Object l) {}
    public void pack() {}
    public void setLocation(int x, int y) {}
    @Override public boolean isVisible() { return showing; }

    @Override
    public void setVisible(boolean b) {
        if (b && !showing && !items.isEmpty()) show(invoker, 0, 0);
    }

    public void show(Component invoker, int x, int y) {
        this.invoker = invoker;
        Runnable open = () -> {
            showing = true;
            try {
                jvm$choose(label, items);
            } finally {
                showing = false;
            }
        };
        if (JvmRuntime.isDispatchThread()) open.run();
        else JvmRuntime.invokeLater(open);
    }

    /** 項目の一覧を出し、選ばれた項目を押す(下の階層のメニューは続けて開く)。 */
    static void jvm$choose(String title, List<Component> items) {
        List<JMenuItem> shown = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Component c : items) {
            if (!(c instanceof JMenuItem) || !c.isVisible() || !c.isEnabled()) continue;
            JMenuItem m = (JMenuItem) c;
            String t = m.getText();
            if (m instanceof JMenu) t = t + "  ›";
            else if (m.jvm$toggles()) t = (m.isSelected() ? "✓ " : "　 ") + t;
            shown.add(m);
            labels.add(t);
        }
        if (shown.isEmpty()) return;
        String[] arr = labels.toArray(new String[0]);
        int i = JvmRuntime.blocking(() -> JvmRuntime.host().showChoice(title, arr, -1), -1);
        if (i >= 0) shown.get(i).jvm$clicked(System.currentTimeMillis(), 0);
    }
}
