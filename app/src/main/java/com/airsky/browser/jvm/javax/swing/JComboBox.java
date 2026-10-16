package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.FontMetrics;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.ItemSelectable;
import com.airsky.browser.jvm.java.awt.event.ActionEvent;
import com.airsky.browser.jvm.java.awt.event.ActionListener;
import com.airsky.browser.jvm.java.awt.event.ItemEvent;
import com.airsky.browser.jvm.java.awt.event.ItemListener;
import com.airsky.browser.jvm.java.awt.event.MouseEvent;

import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;

/** javax.swing.JComboBox の互換クラス。押すと一覧から選ぶ。 */
public class JComboBox<E> extends JComponent implements ItemSelectable {
    protected ComboBoxModel<E> dataModel;
    private boolean editable;
    private String command = "comboBoxChanged";
    private int maxRows = 8;
    private final CopyOnWriteArrayList<ActionListener> actionListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ItemListener> itemListeners = new CopyOnWriteArrayList<>();

    public JComboBox() { this(new DefaultComboBoxModel<>()); }
    public JComboBox(E[] items) { this(new DefaultComboBoxModel<>(items)); }
    public JComboBox(Vector<E> items) { this(new DefaultComboBoxModel<>(items)); }

    public JComboBox(ComboBoxModel<E> model) {
        dataModel = model;
    }

    public ComboBoxModel<E> getModel() { return dataModel; }
    public void setModel(ComboBoxModel<E> m) { dataModel = m; revalidate(); }
    public void setEditable(boolean b) { editable = b; }
    public boolean isEditable() { return editable; }
    public void setMaximumRowCount(int n) { maxRows = n; }
    public int getMaximumRowCount() { return maxRows; }
    public void setRenderer(Object r) {}
    public void setEditor(Object e) {}
    public void setPrototypeDisplayValue(E e) {}
    public void setLightWeightPopupEnabled(boolean b) {}
    public void setActionCommand(String c) { command = c; }
    public String getActionCommand() { return command; }
    public void addActionListener(ActionListener l) { if (l != null) actionListeners.add(l); }
    public void removeActionListener(ActionListener l) { actionListeners.remove(l); }
    public ActionListener[] getActionListeners() { return actionListeners.toArray(new ActionListener[0]); }
    public void addItemListener(ItemListener l) { if (l != null) itemListeners.add(l); }
    public void removeItemListener(ItemListener l) { itemListeners.remove(l); }
    public void showPopup() { open(); }
    public void hidePopup() {}
    public void setPopupVisible(boolean b) { if (b) open(); }

    @SuppressWarnings("unchecked")
    private MutableComboBoxModel<E> mutable() {
        if (!(dataModel instanceof MutableComboBoxModel)) throw new RuntimeException("Cannot use this method with a non-Mutable data model.");
        return (MutableComboBoxModel<E>) dataModel;
    }

    public void addItem(E item) { mutable().addElement(item); revalidate(); }
    public void insertItemAt(E item, int index) { mutable().insertElementAt(item, index); revalidate(); }
    public void removeItem(Object item) { mutable().removeElement(item); revalidate(); }
    public void removeItemAt(int index) { mutable().removeElementAt(index); revalidate(); }

    public void removeAllItems() {
        if (dataModel instanceof DefaultComboBoxModel) ((DefaultComboBoxModel<E>) dataModel).removeAllElements();
        else for (int i = dataModel.getSize() - 1; i >= 0; i--) mutable().removeElementAt(i);
        revalidate();
    }

    public int getItemCount() { return dataModel.getSize(); }
    public E getItemAt(int i) { return dataModel.getElementAt(i); }
    public Object getSelectedItem() { return dataModel.getSelectedItem(); }

    public void setSelectedItem(Object o) {
        Object old = dataModel.getSelectedItem();
        if (old == null ? o == null : old.equals(o)) return;
        dataModel.setSelectedItem(o);
        repaint();
        if (old != null) fireItem(old, ItemEvent.DESELECTED);
        if (o != null) fireItem(o, ItemEvent.SELECTED);
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, command);
        for (ActionListener l : actionListeners) l.actionPerformed(e);
    }

    private void fireItem(Object item, int state) {
        ItemEvent e = new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, item, state);
        for (ItemListener l : itemListeners) l.itemStateChanged(e);
    }

    public int getSelectedIndex() {
        Object s = dataModel.getSelectedItem();
        for (int i = 0; i < dataModel.getSize(); i++) {
            Object e = dataModel.getElementAt(i);
            if (e != null && e.equals(s)) return i;
        }
        return -1;
    }

    public void setSelectedIndex(int i) {
        if (i < -1 || i >= dataModel.getSize()) throw new IllegalArgumentException("setSelectedIndex: " + i + " out of bounds");
        setSelectedItem(i == -1 ? null : dataModel.getElementAt(i));
    }

    @Override
    public Object[] getSelectedObjects() {
        Object s = getSelectedItem();
        return s == null ? new Object[0] : new Object[]{s};
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics fm = getFontMetrics(getFont());
        int w = 40;
        for (int i = 0; i < dataModel.getSize(); i++) w = Math.max(w, fm.stringWidth(String.valueOf(dataModel.getElementAt(i))));
        return new Dimension(w + 40, fm.getHeight() + 14);
    }

    @Override public Dimension getMaximumSize() { return isMaximumSizeSet() ? super.getMaximumSize() : new Dimension(Short.MAX_VALUE, getPreferredSize().height); }

    @Override
    protected void paintComponent(Graphics g) {
        JvmLook.smooth(g);
        int w = getWidth(), h = getHeight();
        g.setColor(isBackgroundSet() ? getBackground() : JvmLook.SURFACE);
        g.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
        g.setColor(JvmLook.OUTLINE_VARIANT);
        g.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
        g.setColor(isEnabled() ? getForeground() : JvmLook.DISABLED);
        g.setFont(getFont());
        Object s = getSelectedItem();
        FontMetrics fm = g.getFontMetrics();
        JvmLook.text(g, JvmLook.ellipsize(fm, s != null ? String.valueOf(s) : "", w - 36), 8, 0, w - 36, h, 0);
        int cx = w - 15, cy = h / 2;
        g.fillPolygon(new int[]{cx - 5, cx + 5, cx}, new int[]{cy - 2, cy - 2, cy + 4}, 3);
    }

    @Override public boolean jvm$wantsMouse() { return true; }

    @Override
    public void jvm$mouse(MouseEvent e) {
        if (e.getID() == MouseEvent.MOUSE_CLICKED && isEnabled()) open();
    }

    private void open() {
        int n = dataModel.getSize();
        if (editable) {
            Object cur = getSelectedItem();
            String r = JvmRuntime.blocking(() -> JvmRuntime.host().showInput(null, null, cur != null ? String.valueOf(cur) : "", false, false), null);
            if (r != null) setSelectedItem(r);
            return;
        }
        if (n == 0) return;
        String[] items = new String[n];
        for (int i = 0; i < n; i++) items[i] = String.valueOf(dataModel.getElementAt(i));
        int sel = getSelectedIndex();
        int chosen = JvmRuntime.blocking(() -> JvmRuntime.host().showChoice(null, items, sel), -1);
        if (chosen >= 0) setSelectedIndex(chosen);
    }
}
