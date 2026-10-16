package com.airsky.browser.jvm.java.awt.geom;

import com.airsky.browser.jvm.java.awt.Rectangle;
import com.airsky.browser.jvm.java.awt.Shape;

/** java.awt.geom.Line2D の互換クラス。 */
public abstract class Line2D implements Shape, Cloneable {

    public static class Float extends Line2D implements java.io.Serializable {
        public float x1, y1, x2, y2;

        public Float() {}

        public Float(float x1, float y1, float x2, float y2) { setLine(x1, y1, x2, y2); }
        public Float(Point2D p1, Point2D p2) { setLine(p1, p2); }

        @Override public double getX1() { return x1; }
        @Override public double getY1() { return y1; }
        @Override public double getX2() { return x2; }
        @Override public double getY2() { return y2; }
        @Override public Point2D getP1() { return new Point2D.Float(x1, y1); }
        @Override public Point2D getP2() { return new Point2D.Float(x2, y2); }

        @Override
        public void setLine(double x1, double y1, double x2, double y2) {
            this.x1 = (float) x1;
            this.y1 = (float) y1;
            this.x2 = (float) x2;
            this.y2 = (float) y2;
        }

        public void setLine(float x1, float y1, float x2, float y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        @Override
        public Rectangle2D getBounds2D() {
            return new Rectangle2D.Float(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
        }
    }

    public static class Double extends Line2D implements java.io.Serializable {
        public double x1, y1, x2, y2;

        public Double() {}

        public Double(double x1, double y1, double x2, double y2) { setLine(x1, y1, x2, y2); }
        public Double(Point2D p1, Point2D p2) { setLine(p1, p2); }

        @Override public double getX1() { return x1; }
        @Override public double getY1() { return y1; }
        @Override public double getX2() { return x2; }
        @Override public double getY2() { return y2; }
        @Override public Point2D getP1() { return new Point2D.Double(x1, y1); }
        @Override public Point2D getP2() { return new Point2D.Double(x2, y2); }

        @Override
        public void setLine(double x1, double y1, double x2, double y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        @Override
        public Rectangle2D getBounds2D() {
            return new Rectangle2D.Double(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
        }
    }

    protected Line2D() {}

    public abstract double getX1();
    public abstract double getY1();
    public abstract double getX2();
    public abstract double getY2();
    public abstract Point2D getP1();
    public abstract Point2D getP2();
    public abstract void setLine(double x1, double y1, double x2, double y2);

    public void setLine(Point2D p1, Point2D p2) { setLine(p1.getX(), p1.getY(), p2.getX(), p2.getY()); }
    public void setLine(Line2D l) { setLine(l.getX1(), l.getY1(), l.getX2(), l.getY2()); }

    public static int relativeCCW(double x1, double y1, double x2, double y2, double px, double py) {
        x2 -= x1;
        y2 -= y1;
        px -= x1;
        py -= y1;
        double ccw = px * y2 - py * x2;
        if (ccw == 0.0) {
            ccw = px * x2 + py * y2;
            if (ccw > 0.0) {
                px -= x2;
                py -= y2;
                ccw = px * x2 + py * y2;
                if (ccw < 0.0) ccw = 0.0;
            }
        }
        return (ccw < 0.0) ? -1 : ((ccw > 0.0) ? 1 : 0);
    }

    public int relativeCCW(double px, double py) { return relativeCCW(getX1(), getY1(), getX2(), getY2(), px, py); }

    public static boolean linesIntersect(double x1, double y1, double x2, double y2,
                                         double x3, double y3, double x4, double y4) {
        return ((relativeCCW(x1, y1, x2, y2, x3, y3) * relativeCCW(x1, y1, x2, y2, x4, y4) <= 0)
                && (relativeCCW(x3, y3, x4, y4, x1, y1) * relativeCCW(x3, y3, x4, y4, x2, y2) <= 0));
    }

    public boolean intersectsLine(double x1, double y1, double x2, double y2) {
        return linesIntersect(x1, y1, x2, y2, getX1(), getY1(), getX2(), getY2());
    }

    public boolean intersectsLine(Line2D l) { return intersectsLine(l.getX1(), l.getY1(), l.getX2(), l.getY2()); }

    public static double ptSegDistSq(double x1, double y1, double x2, double y2, double px, double py) {
        x2 -= x1;
        y2 -= y1;
        px -= x1;
        py -= y1;
        double dotprod = px * x2 + py * y2;
        double projlenSq;
        if (dotprod <= 0.0) {
            projlenSq = 0.0;
        } else {
            px = x2 - px;
            py = y2 - py;
            dotprod = px * x2 + py * y2;
            projlenSq = dotprod <= 0.0 ? 0.0 : dotprod * dotprod / (x2 * x2 + y2 * y2);
        }
        double lenSq = px * px + py * py - projlenSq;
        return Math.max(0, lenSq);
    }

    public static double ptSegDist(double x1, double y1, double x2, double y2, double px, double py) {
        return Math.sqrt(ptSegDistSq(x1, y1, x2, y2, px, py));
    }

    public double ptSegDist(double px, double py) { return ptSegDist(getX1(), getY1(), getX2(), getY2(), px, py); }
    public double ptSegDist(Point2D p) { return ptSegDist(p.getX(), p.getY()); }

    public static double ptLineDistSq(double x1, double y1, double x2, double y2, double px, double py) {
        x2 -= x1;
        y2 -= y1;
        px -= x1;
        py -= y1;
        double dotprod = px * x2 + py * y2;
        double projlenSq = dotprod * dotprod / (x2 * x2 + y2 * y2);
        return Math.max(0, px * px + py * py - projlenSq);
    }

    public static double ptLineDist(double x1, double y1, double x2, double y2, double px, double py) {
        return Math.sqrt(ptLineDistSq(x1, y1, x2, y2, px, py));
    }

    public double ptLineDist(double px, double py) { return ptLineDist(getX1(), getY1(), getX2(), getY2(), px, py); }

    @Override public Rectangle getBounds() { return getBounds2D().getBounds(); }
    @Override public boolean contains(double x, double y) { return false; }
    @Override public boolean contains(Point2D p) { return false; }
    @Override public boolean intersects(double x, double y, double w, double h) {
        return new Rectangle2D.Double(x, y, w, h).intersectsLine(getX1(), getY1(), getX2(), getY2());
    }
    @Override public boolean intersects(Rectangle2D r) { return intersects(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }
    @Override public boolean contains(double x, double y, double w, double h) { return false; }
    @Override public boolean contains(Rectangle2D r) { return false; }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }
}
