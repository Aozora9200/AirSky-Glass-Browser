package com.airsky.browser.jvm.java.awt.font;

import com.airsky.browser.jvm.java.awt.geom.AffineTransform;

public class FontRenderContext {
    private final AffineTransform tx;
    private final boolean aa, fractional;

    protected FontRenderContext() { this(null, true, true); }

    public FontRenderContext(AffineTransform tx, boolean isAntiAliased, boolean usesFractionalMetrics) {
        this.tx = tx != null ? new AffineTransform(tx) : new AffineTransform();
        this.aa = isAntiAliased;
        this.fractional = usesFractionalMetrics;
    }

    public FontRenderContext(AffineTransform tx, Object aaHint, Object fmHint) { this(tx, true, true); }

    public boolean isTransformed() { return !tx.isIdentity(); }
    public AffineTransform getTransform() { return new AffineTransform(tx); }
    public boolean isAntiAliased() { return aa; }
    public boolean usesFractionalMetrics() { return fractional; }
}
