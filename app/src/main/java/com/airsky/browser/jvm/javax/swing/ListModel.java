package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.javax.swing.event.ListDataListener;

public interface ListModel<E> {
    int getSize();
    E getElementAt(int index);
    void addListDataListener(ListDataListener l);
    void removeListDataListener(ListDataListener l);
}
