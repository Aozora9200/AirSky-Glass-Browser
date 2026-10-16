package com.airsky.browser.jvm.java.awt.event;

import com.airsky.browser.jvm.java.awt.Component;

public class FocusEvent extends ComponentEvent {
    public static final int FOCUS_FIRST = 1004;
    public static final int FOCUS_LAST = 1005;
    public static final int FOCUS_GAINED = 1004;
    public static final int FOCUS_LOST = 1005;
    private final boolean temporary;
    private final Component opposite;

    public FocusEvent(Component source, int id) { this(source, id, false, null); }
    public FocusEvent(Component source, int id, boolean temporary) { this(source, id, temporary, null); }

    public FocusEvent(Component source, int id, boolean temporary, Component opposite) {
        super(source, id);
        this.temporary = temporary;
        this.opposite = opposite;
    }

    public boolean isTemporary() { return temporary; }
    public Component getOppositeComponent() { return opposite; }
}
