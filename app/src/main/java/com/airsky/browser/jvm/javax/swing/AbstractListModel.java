package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.javax.swing.event.ListDataEvent;
import com.airsky.browser.jvm.javax.swing.event.ListDataListener;

import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractListModel<E> implements ListModel<E>, java.io.Serializable {
    private final CopyOnWriteArrayList<ListDataListener> listeners = new CopyOnWriteArrayList<>();

    @Override public void addListDataListener(ListDataListener l) { if (l != null) listeners.add(l); }
    @Override public void removeListDataListener(ListDataListener l) { listeners.remove(l); }
    public ListDataListener[] getListDataListeners() { return listeners.toArray(new ListDataListener[0]); }

    protected void fireContentsChanged(Object source, int i0, int i1) {
        ListDataEvent e = new ListDataEvent(source, ListDataEvent.CONTENTS_CHANGED, i0, i1);
        for (ListDataListener l : listeners) l.contentsChanged(e);
    }

    protected void fireIntervalAdded(Object source, int i0, int i1) {
        ListDataEvent e = new ListDataEvent(source, ListDataEvent.INTERVAL_ADDED, i0, i1);
        for (ListDataListener l : listeners) l.intervalAdded(e);
    }

    protected void fireIntervalRemoved(Object source, int i0, int i1) {
        ListDataEvent e = new ListDataEvent(source, ListDataEvent.INTERVAL_REMOVED, i0, i1);
        for (ListDataListener l : listeners) l.intervalRemoved(e);
    }
}
