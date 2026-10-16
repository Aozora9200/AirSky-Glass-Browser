package com.airsky.browser.jvm.java.awt.event;

import com.airsky.browser.jvm.java.awt.AWTEvent;

public class ActionEvent extends AWTEvent {
    public static final int SHIFT_MASK = 1;
    public static final int CTRL_MASK = 2;
    public static final int META_MASK = 4;
    public static final int ALT_MASK = 8;
    public static final int ACTION_FIRST = 1001;
    public static final int ACTION_LAST = 1001;
    public static final int ACTION_PERFORMED = 1001;

    private final String command;
    private final long when;
    private final int modifiers;

    public ActionEvent(Object source, int id, String command) { this(source, id, command, 0); }

    public ActionEvent(Object source, int id, String command, int modifiers) {
        this(source, id, command, System.currentTimeMillis(), modifiers);
    }

    public ActionEvent(Object source, int id, String command, long when, int modifiers) {
        super(source, id);
        this.command = command;
        this.when = when;
        this.modifiers = modifiers;
    }

    public String getActionCommand() { return command; }
    public long getWhen() { return when; }
    public int getModifiers() { return modifiers; }

    @Override
    public String paramString() { return "ACTION_PERFORMED,cmd=" + command; }
}
