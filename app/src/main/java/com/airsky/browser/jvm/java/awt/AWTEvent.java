package com.airsky.browser.jvm.java.awt;

import java.util.EventObject;

/** java.awt.AWTEvent の互換クラス。 */
public abstract class AWTEvent extends EventObject {
    public static final long COMPONENT_EVENT_MASK = 0x01;
    public static final long CONTAINER_EVENT_MASK = 0x02;
    public static final long FOCUS_EVENT_MASK = 0x04;
    public static final long KEY_EVENT_MASK = 0x08;
    public static final long MOUSE_EVENT_MASK = 0x10;
    public static final long MOUSE_MOTION_EVENT_MASK = 0x20;
    public static final long WINDOW_EVENT_MASK = 0x40;
    public static final long ACTION_EVENT_MASK = 0x80;
    public static final long ADJUSTMENT_EVENT_MASK = 0x100;
    public static final long ITEM_EVENT_MASK = 0x200;
    public static final long TEXT_EVENT_MASK = 0x400;
    public static final long MOUSE_WHEEL_EVENT_MASK = 0x20000;
    public static final int RESERVED_ID_MAX = 1999;

    protected int id;
    protected boolean consumed;

    public AWTEvent(Object source, int id) {
        super(source);
        this.id = id;
    }

    public int getID() { return id; }
    public void setSource(Object s) { source = s; }
    protected void consume() { consumed = true; }
    protected boolean isConsumed() { return consumed; }
    public String paramString() { return ""; }

    @Override
    public String toString() {
        return getClass().getName() + "[" + paramString() + "] on " + source;
    }
}
