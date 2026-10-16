package com.airsky.browser.jvm.java.awt;

/** java.awt.AlphaComposite の互換クラス。 */
public final class AlphaComposite implements Composite {
    public static final int CLEAR = 1;
    public static final int SRC = 2;
    public static final int SRC_OVER = 3;
    public static final int DST_OVER = 4;
    public static final int SRC_IN = 5;
    public static final int DST_IN = 6;
    public static final int SRC_OUT = 7;
    public static final int DST_OUT = 8;
    public static final int DST = 9;
    public static final int SRC_ATOP = 10;
    public static final int DST_ATOP = 11;
    public static final int XOR = 12;

    public static final AlphaComposite Clear = new AlphaComposite(CLEAR, 1f);
    public static final AlphaComposite Src = new AlphaComposite(SRC, 1f);
    public static final AlphaComposite SrcOver = new AlphaComposite(SRC_OVER, 1f);
    public static final AlphaComposite DstOver = new AlphaComposite(DST_OVER, 1f);
    public static final AlphaComposite SrcIn = new AlphaComposite(SRC_IN, 1f);
    public static final AlphaComposite DstIn = new AlphaComposite(DST_IN, 1f);
    public static final AlphaComposite SrcOut = new AlphaComposite(SRC_OUT, 1f);
    public static final AlphaComposite DstOut = new AlphaComposite(DST_OUT, 1f);
    public static final AlphaComposite Dst = new AlphaComposite(DST, 1f);
    public static final AlphaComposite SrcAtop = new AlphaComposite(SRC_ATOP, 1f);
    public static final AlphaComposite DstAtop = new AlphaComposite(DST_ATOP, 1f);
    public static final AlphaComposite Xor = new AlphaComposite(XOR, 1f);

    private final int rule;
    private final float alpha;

    private AlphaComposite(int rule, float alpha) {
        if (alpha < 0f || alpha > 1f) throw new IllegalArgumentException("alpha value out of range");
        this.rule = rule;
        this.alpha = alpha;
    }

    public static AlphaComposite getInstance(int rule) { return new AlphaComposite(rule, 1f); }
    public static AlphaComposite getInstance(int rule, float alpha) { return new AlphaComposite(rule, alpha); }
    public AlphaComposite derive(int rule) { return new AlphaComposite(rule, alpha); }
    public AlphaComposite derive(float alpha) { return new AlphaComposite(rule, alpha); }
    public int getRule() { return rule; }
    public float getAlpha() { return alpha; }

    @Override public int hashCode() { return Float.floatToIntBits(alpha) * 31 + rule; }

    @Override
    public boolean equals(Object o) {
        return o instanceof AlphaComposite && ((AlphaComposite) o).rule == rule && ((AlphaComposite) o).alpha == alpha;
    }
}
