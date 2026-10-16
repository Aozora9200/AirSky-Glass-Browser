package com.airsky.browser.jvm.java.awt;

public final class DisplayMode {
    public static final int REFRESH_RATE_UNKNOWN = 0;
    public static final int BIT_DEPTH_MULTI = -1;
    private final int w, h, depth, rate;

    public DisplayMode(int w, int h, int depth, int rate) {
        this.w = w;
        this.h = h;
        this.depth = depth;
        this.rate = rate;
    }

    public int getWidth() { return w; }
    public int getHeight() { return h; }
    public int getBitDepth() { return depth; }
    public int getRefreshRate() { return rate; }
}
