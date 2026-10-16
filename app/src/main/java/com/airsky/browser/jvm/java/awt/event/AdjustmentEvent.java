package com.airsky.browser.jvm.java.awt.event;

import com.airsky.browser.jvm.java.awt.AWTEvent;
import com.airsky.browser.jvm.java.awt.Adjustable;

public class AdjustmentEvent extends AWTEvent {
    public static final int ADJUSTMENT_FIRST = 601;
    public static final int ADJUSTMENT_LAST = 601;
    public static final int ADJUSTMENT_VALUE_CHANGED = 601;
    public static final int UNIT_INCREMENT = 1;
    public static final int UNIT_DECREMENT = 2;
    public static final int BLOCK_DECREMENT = 3;
    public static final int BLOCK_INCREMENT = 4;
    public static final int TRACK = 5;
    private final int type, value;

    public AdjustmentEvent(Adjustable source, int id, int type, int value) {
        super(source, id);
        this.type = type;
        this.value = value;
    }

    public Adjustable getAdjustable() { return (Adjustable) source; }
    public int getValue() { return value; }
    public int getAdjustmentType() { return type; }
    public boolean getValueIsAdjusting() { return false; }
}
