package com.airsky.browser.jvm.java.awt;

/** java.awt.BasicStroke の互換クラス。 */
public class BasicStroke implements Stroke {
    public static final int JOIN_MITER = 0;
    public static final int JOIN_ROUND = 1;
    public static final int JOIN_BEVEL = 2;
    public static final int CAP_BUTT = 0;
    public static final int CAP_ROUND = 1;
    public static final int CAP_SQUARE = 2;

    private final float width;
    private final int cap, join;
    private final float miter;
    private final float[] dash;
    private final float phase;

    public BasicStroke() { this(1f, CAP_SQUARE, JOIN_MITER, 10f, null, 0f); }
    public BasicStroke(float width) { this(width, CAP_SQUARE, JOIN_MITER, 10f, null, 0f); }
    public BasicStroke(float width, int cap, int join) { this(width, cap, join, 10f, null, 0f); }
    public BasicStroke(float width, int cap, int join, float miter) { this(width, cap, join, miter, null, 0f); }

    public BasicStroke(float width, int cap, int join, float miter, float[] dash, float phase) {
        if (width < 0) throw new IllegalArgumentException("negative width");
        this.width = width;
        this.cap = cap;
        this.join = join;
        this.miter = miter;
        this.dash = dash != null ? dash.clone() : null;
        this.phase = phase;
    }

    public float getLineWidth() { return width; }
    public int getEndCap() { return cap; }
    public int getLineJoin() { return join; }
    public float getMiterLimit() { return miter; }
    public float[] getDashArray() { return dash != null ? dash.clone() : null; }
    public float getDashPhase() { return phase; }

    @Override
    public Shape createStrokedShape(Shape s) { return s; }
}
