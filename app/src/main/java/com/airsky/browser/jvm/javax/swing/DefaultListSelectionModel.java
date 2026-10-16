package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.javax.swing.event.ListSelectionEvent;
import com.airsky.browser.jvm.javax.swing.event.ListSelectionListener;

import java.util.BitSet;
import java.util.concurrent.CopyOnWriteArrayList;

public class DefaultListSelectionModel implements ListSelectionModel, Cloneable, java.io.Serializable {
    private final BitSet bits = new BitSet();
    private int mode = MULTIPLE_INTERVAL_SELECTION;
    private int anchor = -1, lead = -1;
    private boolean adjusting;
    private final CopyOnWriteArrayList<ListSelectionListener> listeners = new CopyOnWriteArrayList<>();
    Object source = this;

    private void fire(int a, int b) {
        ListSelectionEvent e = new ListSelectionEvent(source, Math.max(0, Math.min(a, b)), Math.max(a, b), adjusting);
        for (ListSelectionListener l : listeners) l.valueChanged(e);
    }

    @Override
    public void setSelectionInterval(int i0, int i1) {
        if (i0 < 0 || i1 < 0) return;
        if (mode == SINGLE_SELECTION) i0 = i1;
        int lo = Math.min(i0, i1), hi = Math.max(i0, i1);
        int oldMin = getMinSelectionIndex(), oldMax = getMaxSelectionIndex();
        bits.clear();
        bits.set(lo, hi + 1);
        anchor = i0;
        lead = i1;
        fire(oldMin < 0 ? lo : Math.min(oldMin, lo), Math.max(oldMax, hi));
    }

    @Override
    public void addSelectionInterval(int i0, int i1) {
        if (mode != MULTIPLE_INTERVAL_SELECTION) {
            setSelectionInterval(i0, i1);
            return;
        }
        int lo = Math.min(i0, i1), hi = Math.max(i0, i1);
        bits.set(lo, hi + 1);
        anchor = i0;
        lead = i1;
        fire(lo, hi);
    }

    @Override
    public void removeSelectionInterval(int i0, int i1) {
        int lo = Math.min(i0, i1), hi = Math.max(i0, i1);
        bits.clear(lo, hi + 1);
        fire(lo, hi);
    }

    @Override public int getMinSelectionIndex() { return bits.isEmpty() ? -1 : bits.nextSetBit(0); }
    @Override public int getMaxSelectionIndex() { return bits.isEmpty() ? -1 : bits.length() - 1; }
    @Override public boolean isSelectedIndex(int i) { return i >= 0 && bits.get(i); }
    @Override public int getAnchorSelectionIndex() { return anchor; }
    @Override public int getLeadSelectionIndex() { return lead; }

    @Override
    public void clearSelection() {
        if (bits.isEmpty()) return;
        int lo = getMinSelectionIndex(), hi = getMaxSelectionIndex();
        bits.clear();
        fire(lo, hi);
    }

    @Override public boolean isSelectionEmpty() { return bits.isEmpty(); }
    @Override public void setValueIsAdjusting(boolean b) { adjusting = b; }
    @Override public boolean getValueIsAdjusting() { return adjusting; }
    @Override public void setSelectionMode(int m) { mode = m; }
    @Override public int getSelectionMode() { return mode; }
    @Override public void addListSelectionListener(ListSelectionListener l) { if (l != null) listeners.add(l); }
    @Override public void removeListSelectionListener(ListSelectionListener l) { listeners.remove(l); }

    public int[] jvm$selected() { return bits.stream().toArray(); }
    void jvm$shift(int from, int delta) {
        BitSet copy = (BitSet) bits.clone();
        bits.clear();
        for (int i = copy.nextSetBit(0); i >= 0; i = copy.nextSetBit(i + 1)) {
            int n = i >= from ? i + delta : i;
            if (n >= 0 && (delta >= 0 || i >= from - delta || i < from)) bits.set(n);
        }
    }
}
