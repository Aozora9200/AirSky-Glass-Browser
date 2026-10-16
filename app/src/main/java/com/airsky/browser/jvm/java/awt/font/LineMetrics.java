package com.airsky.browser.jvm.java.awt.font;

import com.airsky.browser.jvm.java.awt.FontMetrics;

public class LineMetrics {
    private final FontMetrics fm;

    public LineMetrics(FontMetrics fm) { this.fm = fm; }

    public int getNumChars() { return 0; }
    public float getAscent() { return fm.getAscent(); }
    public float getDescent() { return fm.getDescent(); }
    public float getLeading() { return fm.getLeading(); }
    public float getHeight() { return fm.getHeight(); }
    public int getBaselineIndex() { return 0; }
    public float[] getBaselineOffsets() { return new float[]{0, 0, 0}; }
    public float getStrikethroughOffset() { return -fm.getAscent() / 3f; }
    public float getStrikethroughThickness() { return 1f; }
    public float getUnderlineOffset() { return 1f; }
    public float getUnderlineThickness() { return 1f; }
}
