package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.event.ItemEvent;
import com.airsky.browser.jvm.java.awt.event.ItemListener;
import com.airsky.browser.jvm.java.awt.event.MouseEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** java.awt.Choice の互換クラス。押すと一覧から選ぶ。 */
public class Choice extends Component implements ItemSelectable {
    private final List<String> items = new ArrayList<>();
    private int selected = -1;
    private final CopyOnWriteArrayList<ItemListener> itemListeners = new CopyOnWriteArrayList<>();

    public Choice() { heavyweight = true; }

    public void add(String item) {
        items.add(item);
        if (selected < 0) selected = 0;
        invalidate();
        repaint();
    }

    public void addItem(String item) { add(item); }
    public void insert(String item, int index) { items.add(index, item); if (selected < 0) selected = 0; repaint(); }
    public void remove(String item) { items.remove(item); if (selected >= items.size()) selected = items.size() - 1; repaint(); }
    public void remove(int i) { items.remove(i); if (selected >= items.size()) selected = items.size() - 1; repaint(); }
    public void removeAll() { items.clear(); selected = -1; repaint(); }
    public int getItemCount() { return items.size(); }
    public String getItem(int i) { return items.get(i); }
    public int getSelectedIndex() { return selected; }
    public String getSelectedItem() { return selected >= 0 ? items.get(selected) : null; }
    public void select(int i) { selected = i; repaint(); }
    public void select(String s) { int i = items.indexOf(s); if (i >= 0) select(i); }
    public void addItemListener(ItemListener l) { if (l != null) itemListeners.add(l); }
    public void removeItemListener(ItemListener l) { itemListeners.remove(l); }

    @Override
    public Object[] getSelectedObjects() { return selected >= 0 ? new Object[]{items.get(selected)} : null; }

    @Override
    public Dimension getPreferredSize() {
        if (prefSize != null) return new Dimension(prefSize);
        FontMetrics fm = getFontMetrics(getFont());
        int w = 40;
        for (String s : items) w = Math.max(w, fm.stringWidth(s));
        return new Dimension(w + 36, fm.getHeight() + 12);
    }

    @Override
    public Dimension getMinimumSize() { return minSize != null ? new Dimension(minSize) : getPreferredSize(); }

    @Override
    public void paint(Graphics g) {
        JvmLook.smooth(g);
        g.setColor(JvmLook.SURFACE);
        g.fillRoundRect(0, 0, width - 1, height - 1, 8, 8);
        g.setColor(JvmLook.OUTLINE_VARIANT);
        g.drawRoundRect(0, 0, width - 1, height - 1, 8, 8);
        g.setColor(enabled ? getForeground() : JvmLook.DISABLED);
        g.setFont(getFont());
        JvmLook.text(g, getSelectedItem(), 8, 0, width - 30, height, 0);
        int cx = width - 14, cy = height / 2;
        g.fillPolygon(new int[]{cx - 5, cx + 5, cx}, new int[]{cy - 2, cy - 2, cy + 4}, 3);
    }

    @Override
    public boolean jvm$wantsMouse() { return true; }

    @Override
    public void jvm$mouse(MouseEvent e) {
        if (e.getID() != MouseEvent.MOUSE_CLICKED || !enabled || items.isEmpty()) return;
        String[] arr = items.toArray(new String[0]);
        int chosen = JvmRuntime.blocking(() -> JvmRuntime.host().showChoice(null, arr, selected), -1);
        if (chosen >= 0 && chosen != selected) {
            selected = chosen;
            repaint();
            ItemEvent ie = new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, items.get(chosen), ItemEvent.SELECTED);
            for (ItemListener l : itemListeners) l.itemStateChanged(ie);
        }
    }
}
