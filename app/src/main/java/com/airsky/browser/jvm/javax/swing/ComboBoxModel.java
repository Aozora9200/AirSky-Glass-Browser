package com.airsky.browser.jvm.javax.swing;

public interface ComboBoxModel<E> extends ListModel<E> {
    void setSelectedItem(Object item);
    Object getSelectedItem();
}
