package com.airsky.browser.jvm.java.awt;

public final class ComponentOrientation implements java.io.Serializable {
    public static final ComponentOrientation LEFT_TO_RIGHT = new ComponentOrientation(true);
    public static final ComponentOrientation RIGHT_TO_LEFT = new ComponentOrientation(false);
    public static final ComponentOrientation UNKNOWN = LEFT_TO_RIGHT;
    private final boolean ltr;

    private ComponentOrientation(boolean ltr) { this.ltr = ltr; }

    public boolean isLeftToRight() { return ltr; }
    public boolean isHorizontal() { return true; }
    public static ComponentOrientation getOrientation(java.util.Locale l) { return LEFT_TO_RIGHT; }
}
