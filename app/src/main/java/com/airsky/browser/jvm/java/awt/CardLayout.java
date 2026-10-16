package com.airsky.browser.jvm.java.awt;

import java.util.ArrayList;
import java.util.List;

/** java.awt.CardLayout の互換クラス。 */
public class CardLayout implements LayoutManager2, java.io.Serializable {
    private final List<Object[]> cards = new ArrayList<>();
    private int current;
    int hgap, vgap;

    public CardLayout() { this(0, 0); }

    public CardLayout(int hgap, int vgap) {
        this.hgap = hgap;
        this.vgap = vgap;
    }

    public int getHgap() { return hgap; }
    public void setHgap(int h) { hgap = h; }
    public int getVgap() { return vgap; }
    public void setVgap(int v) { vgap = v; }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        addLayoutComponent(constraints instanceof String ? (String) constraints : String.valueOf(cards.size()), comp);
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i)[0].equals(name)) {
                cards.get(i)[1] = comp;
                return;
            }
        }
        cards.add(new Object[]{name != null ? name : String.valueOf(cards.size()), comp});
        comp.setVisible(cards.size() == 1);
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i)[1] == comp) {
                cards.remove(i);
                if (current >= cards.size()) current = Math.max(0, cards.size() - 1);
                return;
            }
        }
    }

    private void showIndex(Container parent, int i) {
        if (cards.isEmpty()) return;
        current = (i + cards.size()) % cards.size();
        for (int k = 0; k < cards.size(); k++) ((Component) cards.get(k)[1]).setVisible(k == current);
        parent.invalidate();
        parent.repaint();
    }

    public void first(Container parent) { showIndex(parent, 0); }
    public void last(Container parent) { showIndex(parent, cards.size() - 1); }
    public void next(Container parent) { showIndex(parent, current + 1); }
    public void previous(Container parent) { showIndex(parent, current - 1); }

    public void show(Container parent, String name) {
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i)[0].equals(name)) {
                showIndex(parent, i);
                return;
            }
        }
    }

    @Override public Dimension preferredLayoutSize(Container parent) { return size(parent, true); }
    @Override public Dimension minimumLayoutSize(Container parent) { return size(parent, false); }

    private Dimension size(Container parent, boolean pref) {
        int w = 0, h = 0;
        for (Component c : parent.getComponents()) {
            Dimension d = pref ? c.getPreferredSize() : c.getMinimumSize();
            w = Math.max(w, d.width);
            h = Math.max(h, d.height);
        }
        Insets in = parent.getInsets();
        return new Dimension(in.left + in.right + w + hgap * 2, in.top + in.bottom + h + vgap * 2);
    }

    @Override public Dimension maximumLayoutSize(Container target) { return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE); }
    @Override public float getLayoutAlignmentX(Container parent) { return 0.5f; }
    @Override public float getLayoutAlignmentY(Container parent) { return 0.5f; }
    @Override public void invalidateLayout(Container target) {}

    @Override
    public void layoutContainer(Container parent) {
        Insets in = parent.getInsets();
        for (Component c : parent.getComponents()) {
            c.setBounds(hgap + in.left, vgap + in.top, parent.getWidth() - (hgap * 2 + in.left + in.right),
                    parent.getHeight() - (vgap * 2 + in.top + in.bottom));
        }
    }
}
