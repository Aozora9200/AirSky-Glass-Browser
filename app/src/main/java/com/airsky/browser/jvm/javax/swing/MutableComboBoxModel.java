package com.airsky.browser.jvm.javax.swing;

public interface MutableComboBoxModel<E> extends ComboBoxModel<E> {
    void addElement(E item);
    void removeElement(Object obj);
    void insertElementAt(E item, int index);
    void removeElementAt(int index);
}
