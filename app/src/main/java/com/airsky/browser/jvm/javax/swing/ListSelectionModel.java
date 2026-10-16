package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.javax.swing.event.ListSelectionListener;

public interface ListSelectionModel {
    int SINGLE_SELECTION = 0;
    int SINGLE_INTERVAL_SELECTION = 1;
    int MULTIPLE_INTERVAL_SELECTION = 2;

    void setSelectionInterval(int i0, int i1);
    void addSelectionInterval(int i0, int i1);
    void removeSelectionInterval(int i0, int i1);
    int getMinSelectionIndex();
    int getMaxSelectionIndex();
    boolean isSelectedIndex(int i);
    int getAnchorSelectionIndex();
    int getLeadSelectionIndex();
    void clearSelection();
    boolean isSelectionEmpty();
    void setValueIsAdjusting(boolean b);
    boolean getValueIsAdjusting();
    void setSelectionMode(int mode);
    int getSelectionMode();
    void addListSelectionListener(ListSelectionListener l);
    void removeListSelectionListener(ListSelectionListener l);
}
