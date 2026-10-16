package com.airsky.browser.jvm.java.awt.event;

import com.airsky.browser.jvm.java.awt.Component;

public class MouseWheelEvent extends MouseEvent {
    public static final int WHEEL_UNIT_SCROLL = 0;
    public static final int WHEEL_BLOCK_SCROLL = 1;
    private final int scrollType, scrollAmount, wheelRotation;
    private final double precise;

    public MouseWheelEvent(Component source, int id, long when, int modifiers, int x, int y, int clickCount,
                           boolean popupTrigger, int scrollType, int scrollAmount, int wheelRotation) {
        this(source, id, when, modifiers, x, y, clickCount, popupTrigger, scrollType, scrollAmount, wheelRotation, wheelRotation);
    }

    public MouseWheelEvent(Component source, int id, long when, int modifiers, int x, int y, int clickCount,
                           boolean popupTrigger, int scrollType, int scrollAmount, int wheelRotation, double precise) {
        super(source, id, when, modifiers, x, y, clickCount, popupTrigger);
        this.scrollType = scrollType;
        this.scrollAmount = scrollAmount;
        this.wheelRotation = wheelRotation;
        this.precise = precise;
    }

    public int getScrollType() { return scrollType; }
    public int getScrollAmount() { return scrollAmount; }
    public int getWheelRotation() { return wheelRotation; }
    public double getPreciseWheelRotation() { return precise; }
    public int getUnitsToScroll() { return scrollAmount * wheelRotation; }
}
