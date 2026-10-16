package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.java.awt.geom.Point2D;

/** java.awt.GradientPaint の互換クラス。 */
public class GradientPaint implements Paint {
    private final Point2D.Float p1, p2;
    private final Color c1, c2;
    private final boolean cyclic;

    public GradientPaint(float x1, float y1, Color c1, float x2, float y2, Color c2) {
        this(x1, y1, c1, x2, y2, c2, false);
    }

    public GradientPaint(float x1, float y1, Color c1, float x2, float y2, Color c2, boolean cyclic) {
        this.p1 = new Point2D.Float(x1, y1);
        this.p2 = new Point2D.Float(x2, y2);
        this.c1 = c1;
        this.c2 = c2;
        this.cyclic = cyclic;
    }

    public GradientPaint(Point2D a, Color c1, Point2D b, Color c2) {
        this((float) a.getX(), (float) a.getY(), c1, (float) b.getX(), (float) b.getY(), c2, false);
    }

    public GradientPaint(Point2D a, Color c1, Point2D b, Color c2, boolean cyclic) {
        this((float) a.getX(), (float) a.getY(), c1, (float) b.getX(), (float) b.getY(), c2, cyclic);
    }

    public Point2D getPoint1() { return new Point2D.Float(p1.x, p1.y); }
    public Point2D getPoint2() { return new Point2D.Float(p2.x, p2.y); }
    public Color getColor1() { return c1; }
    public Color getColor2() { return c2; }
    public boolean isCyclic() { return cyclic; }

    @Override
    public int getTransparency() {
        return (c1.getAlpha() & c2.getAlpha()) == 0xFF ? OPAQUE : TRANSLUCENT;
    }
}
