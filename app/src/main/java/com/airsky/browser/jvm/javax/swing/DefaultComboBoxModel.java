package com.airsky.browser.jvm.javax.swing;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/** javax.swing.DefaultComboBoxModel の互換クラス。 */
public class DefaultComboBoxModel<E> extends AbstractListModel<E> implements MutableComboBoxModel<E> {
    private final List<E> items = new ArrayList<>();
    private Object selected;

    public DefaultComboBoxModel() {}

    public DefaultComboBoxModel(E[] items) {
        java.util.Collections.addAll(this.items, items);
        if (!this.items.isEmpty()) selected = this.items.get(0);
    }

    public DefaultComboBoxModel(Vector<E> v) {
        items.addAll(v);
        if (!items.isEmpty()) selected = items.get(0);
    }

    @Override
    public void setSelectedItem(Object o) {
        if ((selected != null && !selected.equals(o)) || (selected == null && o != null)) {
            selected = o;
            fireContentsChanged(this, -1, -1);
        }
    }

    @Override public Object getSelectedItem() { return selected; }
    @Override public int getSize() { return items.size(); }
    @Override public E getElementAt(int i) { return i >= 0 && i < items.size() ? items.get(i) : null; }
    public int getIndexOf(Object o) { return items.indexOf(o); }

    @Override
    public void addElement(E e) {
        items.add(e);
        fireIntervalAdded(this, items.size() - 1, items.size() - 1);
        if (items.size() == 1 && selected == null && e != null) setSelectedItem(e);
    }

    @Override
    public void insertElementAt(E e, int index) {
        items.add(index, e);
        fireIntervalAdded(this, index, index);
    }

    @Override
    public void removeElementAt(int index) {
        if (getElementAt(index) == selected) {
            if (index == 0) setSelectedItem(getSize() == 1 ? null : getElementAt(index + 1));
            else setSelectedItem(getElementAt(index - 1));
        }
        items.remove(index);
        fireIntervalRemoved(this, index, index);
    }

    @Override
    public void removeElement(Object o) {
        int i = items.indexOf(o);
        if (i >= 0) removeElementAt(i);
    }

    public void removeAllElements() {
        int n = items.size();
        items.clear();
        selected = null;
        if (n > 0) fireIntervalRemoved(this, 0, n - 1);
    }

    public void addAll(java.util.Collection<? extends E> c) { for (E e : c) addElement(e); }
}
