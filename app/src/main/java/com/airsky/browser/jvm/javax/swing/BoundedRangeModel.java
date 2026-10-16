package com.airsky.browser.jvm.javax.swing;

/** スクロールバーの値の入れ物(JScrollBar をそのまま見せる)。 */
public class BoundedRangeModel {
    private final JScrollBar bar;

    BoundedRangeModel(JScrollBar bar) { this.bar = bar; }

    public int getValue() { return bar.getValue(); }
    public void setValue(int v) { bar.setValue(v); }
    public int getMinimum() { return bar.getMinimum(); }
    public int getMaximum() { return bar.getMaximum(); }
    public int getExtent() { return bar.getVisibleAmount(); }
    public boolean getValueIsAdjusting() { return false; }
    public void setValueIsAdjusting(boolean b) {}
    public void addChangeListener(com.airsky.browser.jvm.javax.swing.event.ChangeListener l) {}
}
