package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.java.awt.event.AdjustmentListener;

public interface Adjustable {
    int HORIZONTAL = 0;
    int VERTICAL = 1;
    int NO_ORIENTATION = 2;

    int getOrientation();
    void setMinimum(int min);
    int getMinimum();
    void setMaximum(int max);
    int getMaximum();
    void setUnitIncrement(int u);
    int getUnitIncrement();
    void setBlockIncrement(int b);
    int getBlockIncrement();
    void setVisibleAmount(int v);
    int getVisibleAmount();
    void setValue(int v);
    int getValue();
    void addAdjustmentListener(AdjustmentListener l);
    void removeAdjustmentListener(AdjustmentListener l);
}
