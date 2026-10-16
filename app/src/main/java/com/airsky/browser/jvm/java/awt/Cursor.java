package com.airsky.browser.jvm.java.awt;

public class Cursor implements java.io.Serializable {
    public static final int DEFAULT_CURSOR = 0;
    public static final int CROSSHAIR_CURSOR = 1;
    public static final int TEXT_CURSOR = 2;
    public static final int WAIT_CURSOR = 3;
    public static final int SW_RESIZE_CURSOR = 4;
    public static final int SE_RESIZE_CURSOR = 5;
    public static final int NW_RESIZE_CURSOR = 6;
    public static final int NE_RESIZE_CURSOR = 7;
    public static final int N_RESIZE_CURSOR = 8;
    public static final int S_RESIZE_CURSOR = 9;
    public static final int W_RESIZE_CURSOR = 10;
    public static final int E_RESIZE_CURSOR = 11;
    public static final int HAND_CURSOR = 12;
    public static final int MOVE_CURSOR = 13;
    public static final int CUSTOM_CURSOR = -1;
    private final int type;
    private final String name;

    public Cursor(int type) {
        this.type = type;
        this.name = "Cursor " + type;
    }

    protected Cursor(String name) {
        this.type = CUSTOM_CURSOR;
        this.name = name;
    }

    public static Cursor getPredefinedCursor(int type) { return new Cursor(type); }
    public static Cursor getDefaultCursor() { return new Cursor(DEFAULT_CURSOR); }
    public static Cursor getSystemCustomCursor(String name) { return new Cursor(name); }
    public int getType() { return type; }
    public String getName() { return name; }
}
