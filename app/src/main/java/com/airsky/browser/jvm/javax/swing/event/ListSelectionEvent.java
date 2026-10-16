package com.airsky.browser.jvm.javax.swing.event;

public class ListSelectionEvent extends java.util.EventObject {
    private final int first, last;
    private final boolean adjusting;

    public ListSelectionEvent(Object source, int first, int last, boolean isAdjusting) {
        super(source);
        this.first = first;
        this.last = last;
        this.adjusting = isAdjusting;
    }

    public int getFirstIndex() { return first; }
    public int getLastIndex() { return last; }
    public boolean getValueIsAdjusting() { return adjusting; }
}
