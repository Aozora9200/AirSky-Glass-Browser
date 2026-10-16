package com.airsky.browser.jvm.java.awt.event;

import com.airsky.browser.jvm.java.awt.Component;

public abstract class InputEvent extends ComponentEvent {
    public static final int SHIFT_MASK = 1;
    public static final int CTRL_MASK = 2;
    public static final int META_MASK = 4;
    public static final int ALT_MASK = 8;
    public static final int ALT_GRAPH_MASK = 32;
    public static final int BUTTON1_MASK = 16;
    public static final int BUTTON2_MASK = 8;
    public static final int BUTTON3_MASK = 4;
    public static final int SHIFT_DOWN_MASK = 64;
    public static final int CTRL_DOWN_MASK = 128;
    public static final int META_DOWN_MASK = 256;
    public static final int ALT_DOWN_MASK = 512;
    public static final int BUTTON1_DOWN_MASK = 1024;
    public static final int BUTTON2_DOWN_MASK = 2048;
    public static final int BUTTON3_DOWN_MASK = 4096;
    public static final int ALT_GRAPH_DOWN_MASK = 8192;

    long when;
    int modifiers;

    InputEvent(Component source, int id, long when, int modifiers) {
        super(source, id);
        this.when = when;
        this.modifiers = modifiers;
    }

    public boolean isShiftDown() { return (modifiers & (SHIFT_MASK | SHIFT_DOWN_MASK)) != 0; }
    public boolean isControlDown() { return (modifiers & (CTRL_MASK | CTRL_DOWN_MASK)) != 0; }
    public boolean isMetaDown() { return (modifiers & (META_MASK | META_DOWN_MASK)) != 0; }
    public boolean isAltDown() { return (modifiers & (ALT_MASK | ALT_DOWN_MASK)) != 0; }
    public boolean isAltGraphDown() { return (modifiers & (ALT_GRAPH_MASK | ALT_GRAPH_DOWN_MASK)) != 0; }
    public long getWhen() { return when; }
    public int getModifiers() { return modifiers & 0x3F; }
    public int getModifiersEx() { return modifiers & ~0x3F; }
    @Override public void consume() { consumed = true; }
    @Override public boolean isConsumed() { return consumed; }

    public static String getModifiersExText(int m) {
        StringBuilder sb = new StringBuilder();
        if ((m & CTRL_DOWN_MASK) != 0) sb.append("Ctrl+");
        if ((m & SHIFT_DOWN_MASK) != 0) sb.append("Shift+");
        if ((m & ALT_DOWN_MASK) != 0) sb.append("Alt+");
        if ((m & META_DOWN_MASK) != 0) sb.append("Meta+");
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        return sb.toString();
    }
}
