package com.airsky.browser.jvm.java.awt.geom;

import com.airsky.browser.jvm.java.awt.Shape;

public final class GeneralPath extends Path2D.Float {
    public GeneralPath() { super(WIND_NON_ZERO); }
    public GeneralPath(int rule) { super(rule); }
    public GeneralPath(int rule, int initialCapacity) { super(rule); }
    public GeneralPath(Shape s) { super(s); }

    @Override
    public Object clone() {
        GeneralPath g = new GeneralPath(getWindingRule());
        g.path = new android.graphics.Path(path);
        return g;
    }
}
