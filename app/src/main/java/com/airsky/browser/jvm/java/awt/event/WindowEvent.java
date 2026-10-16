package com.airsky.browser.jvm.java.awt.event;

import com.airsky.browser.jvm.java.awt.Window;

public class WindowEvent extends ComponentEvent {
    public static final int WINDOW_FIRST = 200;
    public static final int WINDOW_OPENED = 200;
    public static final int WINDOW_CLOSING = 201;
    public static final int WINDOW_CLOSED = 202;
    public static final int WINDOW_ICONIFIED = 203;
    public static final int WINDOW_DEICONIFIED = 204;
    public static final int WINDOW_ACTIVATED = 205;
    public static final int WINDOW_DEACTIVATED = 206;
    public static final int WINDOW_GAINED_FOCUS = 207;
    public static final int WINDOW_LOST_FOCUS = 208;
    public static final int WINDOW_STATE_CHANGED = 209;
    public static final int WINDOW_LAST = 209;
    private final Window opposite;
    private final int oldState, newState;

    public WindowEvent(Window source, int id) { this(source, id, null, 0, 0); }
    public WindowEvent(Window source, int id, Window opposite) { this(source, id, opposite, 0, 0); }
    public WindowEvent(Window source, int id, int oldState, int newState) { this(source, id, null, oldState, newState); }

    public WindowEvent(Window source, int id, Window opposite, int oldState, int newState) {
        super(source, id);
        this.opposite = opposite;
        this.oldState = oldState;
        this.newState = newState;
    }

    public Window getWindow() { return source instanceof Window ? (Window) source : null; }
    public Window getOppositeWindow() { return opposite; }
    public int getOldState() { return oldState; }
    public int getNewState() { return newState; }
}
