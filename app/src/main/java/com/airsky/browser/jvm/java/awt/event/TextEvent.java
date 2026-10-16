package com.airsky.browser.jvm.java.awt.event;

import com.airsky.browser.jvm.java.awt.AWTEvent;

public class TextEvent extends AWTEvent {
    public static final int TEXT_FIRST = 900;
    public static final int TEXT_LAST = 900;
    public static final int TEXT_VALUE_CHANGED = 900;

    public TextEvent(Object source, int id) { super(source, id); }
}
