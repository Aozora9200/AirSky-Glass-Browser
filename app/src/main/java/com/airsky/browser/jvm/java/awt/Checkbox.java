package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.event.ItemEvent;
import com.airsky.browser.jvm.java.awt.event.ItemListener;
import com.airsky.browser.jvm.java.awt.event.MouseEvent;

import java.util.concurrent.CopyOnWriteArrayList;

/** java.awt.Checkbox の互換クラス(CheckboxGroup に入れるとラジオボタン)。 */
public class Checkbox extends Component implements ItemSelectable {
    private String label;
    private boolean state;
    private CheckboxGroup group;
    private final CopyOnWriteArrayList<ItemListener> itemListeners = new CopyOnWriteArrayList<>();

    public Checkbox() { this("", false, null); }
    public Checkbox(String label) { this(label, false, null); }
    public Checkbox(String label, boolean state) { this(label, state, null); }
    public Checkbox(String label, CheckboxGroup group, boolean state) { this(label, state, group); }

    public Checkbox(String label, boolean state, CheckboxGroup group) {
        this.label = label != null ? label : "";
        this.group = group;
        this.heavyweight = true;
        setState(state);
    }

    public String getLabel() { return label; }
    public void setLabel(String l) { label = l != null ? l : ""; invalidate(); repaint(); }
    public boolean getState() { return state; }

    public void setState(boolean s) {
        state = s;
        if (s && group != null) group.setSelectedCheckbox(this);
        repaint();
    }

    void jvm$setStateOnly(boolean s) { state = s; repaint(); }

    public CheckboxGroup getCheckboxGroup() { return group; }
    public void setCheckboxGroup(CheckboxGroup g) { group = g; }
    public void addItemListener(ItemListener l) { if (l != null) itemListeners.add(l); }
    public void removeItemListener(ItemListener l) { itemListeners.remove(l); }

    @Override
    public Object[] getSelectedObjects() { return state ? new Object[]{label} : null; }

    @Override
    public Dimension getPreferredSize() {
        if (prefSize != null) return new Dimension(prefSize);
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(label) + 30, Math.max(fm.getHeight() + 8, 22));
    }

    @Override
    public Dimension getMinimumSize() { return minSize != null ? new Dimension(minSize) : getPreferredSize(); }

    @Override
    public void paint(Graphics g) {
        JvmLook.smooth(g);
        int s = 16, top = (height - s) / 2;
        if (group != null) {
            g.setColor(state ? JvmLook.PRIMARY : JvmLook.OUTLINE);
            g.drawOval(3, top, s, s);
            if (state) g.fillOval(7, top + 4, s - 8, s - 8);
        } else if (state) {
            g.setColor(JvmLook.PRIMARY);
            g.fillRoundRect(3, top, s, s, 5, 5);
            g.setColor(Color.WHITE);
            g.drawLine(6, top + 8, 9, top + 11);
            g.drawLine(9, top + 11, 15, top + 5);
        } else {
            g.setColor(JvmLook.OUTLINE);
            g.drawRoundRect(3, top, s, s, 5, 5);
        }
        g.setColor(enabled ? getForeground() : JvmLook.DISABLED);
        g.setFont(getFont());
        JvmLook.text(g, label, 26, 0, width - 26, height, 0);
    }

    @Override
    public boolean jvm$wantsMouse() { return true; }

    @Override
    public void jvm$mouse(MouseEvent e) {
        if (!enabled || e.getID() != MouseEvent.MOUSE_CLICKED) return;
        if (group != null && state) return;
        setState(!state);
        ItemEvent ie = new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, label,
                state ? ItemEvent.SELECTED : ItemEvent.DESELECTED);
        for (ItemListener l : itemListeners) l.itemStateChanged(ie);
    }
}
