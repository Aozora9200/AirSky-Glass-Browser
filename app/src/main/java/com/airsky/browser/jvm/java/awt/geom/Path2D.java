package com.airsky.browser.jvm.java.awt.geom;

import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;

import com.airsky.browser.jvm.JvmShapes;
import com.airsky.browser.jvm.java.awt.Rectangle;
import com.airsky.browser.jvm.java.awt.Shape;

/** java.awt.geom.Path2D の互換クラス(Android の Path で持つ)。 */
public abstract class Path2D implements Shape, Cloneable {
    public static final int WIND_EVEN_ODD = 0;
    public static final int WIND_NON_ZERO = 1;

    Path path = new Path();
    private int rule;
    private double lastX, lastY;

    public static class Float extends Path2D implements java.io.Serializable {
        public Float() { this(WIND_NON_ZERO); }
        public Float(int rule) { super(rule); }
        public Float(int rule, int capacity) { super(rule); }
        public Float(Shape s) { this(s, null); }

        public Float(Shape s, AffineTransform at) {
            super(WIND_NON_ZERO);
            append(s, false);
            if (at != null) transform(at);
        }

        public final void moveTo(float x, float y) { moveTo((double) x, (double) y); }
        public final void lineTo(float x, float y) { lineTo((double) x, (double) y); }
        public final void quadTo(float x1, float y1, float x2, float y2) { quadTo((double) x1, y1, x2, y2); }
        public final void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            curveTo((double) x1, y1, x2, y2, x3, y3);
        }

        @Override
        public Object clone() {
            Float f = new Float(getWindingRule());
            f.path = new Path(path);
            return f;
        }
    }

    public static class Double extends Path2D implements java.io.Serializable {
        public Double() { this(WIND_NON_ZERO); }
        public Double(int rule) { super(rule); }
        public Double(int rule, int capacity) { super(rule); }
        public Double(Shape s) { this(s, null); }

        public Double(Shape s, AffineTransform at) {
            super(WIND_NON_ZERO);
            append(s, false);
            if (at != null) transform(at);
        }

        @Override
        public Object clone() {
            Double d = new Double(getWindingRule());
            d.path = new Path(path);
            return d;
        }
    }

    Path2D(int rule) { setWindingRule(rule); }

    public void moveTo(double x, double y) {
        path.moveTo((float) x, (float) y);
        lastX = x;
        lastY = y;
    }

    public void lineTo(double x, double y) {
        path.lineTo((float) x, (float) y);
        lastX = x;
        lastY = y;
    }

    public void quadTo(double x1, double y1, double x2, double y2) {
        path.quadTo((float) x1, (float) y1, (float) x2, (float) y2);
        lastX = x2;
        lastY = y2;
    }

    public void curveTo(double x1, double y1, double x2, double y2, double x3, double y3) {
        path.cubicTo((float) x1, (float) y1, (float) x2, (float) y2, (float) x3, (float) y3);
        lastX = x3;
        lastY = y3;
    }

    public void closePath() { path.close(); }
    public void reset() { path.reset(); }

    public void append(Shape s, boolean connect) {
        if (s != null) path.addPath(JvmShapes.path(s));
    }

    public Point2D getCurrentPoint() { return new Point2D.Double(lastX, lastY); }
    public int getWindingRule() { return rule; }

    public void setWindingRule(int r) {
        rule = r;
        path.setFillType(r == WIND_EVEN_ODD ? Path.FillType.EVEN_ODD : Path.FillType.WINDING);
    }

    public void transform(AffineTransform at) { path.transform(at.jvm$matrix()); }

    public Shape createTransformedShape(AffineTransform at) {
        Path2D p = (Path2D) clone();
        if (at != null) p.transform(at);
        return p;
    }

    @Override
    public Rectangle2D getBounds2D() {
        RectF r = new RectF();
        path.computeBounds(r, true);
        return new Rectangle2D.Double(r.left, r.top, r.width(), r.height());
    }

    @Override public Rectangle getBounds() { return getBounds2D().getBounds(); }

    @Override
    public boolean contains(double x, double y) {
        RectF r = new RectF();
        path.computeBounds(r, true);
        Region clip = new Region((int) Math.floor(r.left) - 1, (int) Math.floor(r.top) - 1,
                (int) Math.ceil(r.right) + 1, (int) Math.ceil(r.bottom) + 1);
        Region region = new Region();
        region.setPath(path, clip);
        return region.contains((int) x, (int) y);
    }

    @Override public boolean contains(Point2D p) { return contains(p.getX(), p.getY()); }
    @Override public boolean intersects(double x, double y, double w, double h) { return getBounds2D().intersects(x, y, w, h); }
    @Override public boolean intersects(Rectangle2D r) { return intersects(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }

    @Override
    public boolean contains(double x, double y, double w, double h) {
        return contains(x, y) && contains(x + w, y) && contains(x, y + h) && contains(x + w, y + h);
    }

    @Override public boolean contains(Rectangle2D r) { return contains(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }

    @Override
    public abstract Object clone();

    public Path jvm$path() { return new Path(path); }
}
