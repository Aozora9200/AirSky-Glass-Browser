package com.airsky.browser.jvm.java.awt.geom;

/** java.awt.geom.Rectangle2D の互換クラス。 */
public abstract class Rectangle2D extends RectangularShape {
    public static final int OUT_LEFT = 1;
    public static final int OUT_TOP = 2;
    public static final int OUT_RIGHT = 4;
    public static final int OUT_BOTTOM = 8;

    public static class Float extends Rectangle2D implements java.io.Serializable {
        public float x, y, width, height;

        public Float() {}

        public Float(float x, float y, float w, float h) { setRect(x, y, w, h); }

        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public boolean isEmpty() { return width <= 0f || height <= 0f; }

        public void setRect(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
        }

        @Override
        public void setRect(double x, double y, double w, double h) {
            setRect((float) x, (float) y, (float) w, (float) h);
        }

        @Override public Rectangle2D getBounds2D() { return new Float(x, y, width, height); }
        @Override public String toString() { return getClass().getName() + "[x=" + x + ",y=" + y + ",w=" + width + ",h=" + height + "]"; }
    }

    public static class Double extends Rectangle2D implements java.io.Serializable {
        public double x, y, width, height;

        public Double() {}

        public Double(double x, double y, double w, double h) { setRect(x, y, w, h); }

        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public boolean isEmpty() { return width <= 0.0 || height <= 0.0; }

        @Override
        public void setRect(double x, double y, double w, double h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
        }

        @Override public Rectangle2D getBounds2D() { return new Double(x, y, width, height); }
        @Override public String toString() { return getClass().getName() + "[x=" + x + ",y=" + y + ",w=" + width + ",h=" + height + "]"; }
    }

    protected Rectangle2D() {}

    public abstract void setRect(double x, double y, double w, double h);

    public void setRect(Rectangle2D r) { setRect(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }

    @Override public void setFrame(double x, double y, double w, double h) { setRect(x, y, w, h); }

    @Override
    public Rectangle2D getBounds2D() {
        return new Double(getX(), getY(), getWidth(), getHeight());
    }

    @Override public boolean isEmpty() { return getWidth() <= 0 || getHeight() <= 0; }

    public int outcode(double x, double y) {
        int out = 0;
        if (getWidth() <= 0) out |= OUT_LEFT | OUT_RIGHT;
        else if (x < getX()) out |= OUT_LEFT;
        else if (x > getX() + getWidth()) out |= OUT_RIGHT;
        if (getHeight() <= 0) out |= OUT_TOP | OUT_BOTTOM;
        else if (y < getY()) out |= OUT_TOP;
        else if (y > getY() + getHeight()) out |= OUT_BOTTOM;
        return out;
    }

    public int outcode(Point2D p) { return outcode(p.getX(), p.getY()); }

    @Override
    public boolean contains(double x, double y) {
        double x0 = getX(), y0 = getY();
        return x >= x0 && y >= y0 && x < x0 + getWidth() && y < y0 + getHeight();
    }

    @Override
    public boolean intersects(double x, double y, double w, double h) {
        if (isEmpty() || w <= 0 || h <= 0) return false;
        double x0 = getX(), y0 = getY();
        return x + w > x0 && y + h > y0 && x < x0 + getWidth() && y < y0 + getHeight();
    }

    @Override
    public boolean contains(double x, double y, double w, double h) {
        if (isEmpty() || w <= 0 || h <= 0) return false;
        double x0 = getX(), y0 = getY();
        return x >= x0 && y >= y0 && (x + w) <= x0 + getWidth() && (y + h) <= y0 + getHeight();
    }

    public boolean intersectsLine(double x1, double y1, double x2, double y2) {
        return Line2D.linesIntersect(x1, y1, x2, y2, getMinX(), getMinY(), getMaxX(), getMinY())
                || Line2D.linesIntersect(x1, y1, x2, y2, getMaxX(), getMinY(), getMaxX(), getMaxY())
                || Line2D.linesIntersect(x1, y1, x2, y2, getMinX(), getMaxY(), getMaxX(), getMaxY())
                || Line2D.linesIntersect(x1, y1, x2, y2, getMinX(), getMinY(), getMinX(), getMaxY())
                || contains(x1, y1) || contains(x2, y2);
    }

    public boolean intersectsLine(Line2D l) { return intersectsLine(l.getX1(), l.getY1(), l.getX2(), l.getY2()); }

    public Rectangle2D createIntersection(Rectangle2D r) {
        Rectangle2D d = new Double();
        intersect(this, r, d);
        return d;
    }

    public Rectangle2D createUnion(Rectangle2D r) {
        Rectangle2D d = new Double();
        union(this, r, d);
        return d;
    }

    public static void intersect(Rectangle2D a, Rectangle2D b, Rectangle2D dest) {
        double x1 = Math.max(a.getMinX(), b.getMinX()), y1 = Math.max(a.getMinY(), b.getMinY());
        double x2 = Math.min(a.getMaxX(), b.getMaxX()), y2 = Math.min(a.getMaxY(), b.getMaxY());
        dest.setFrame(x1, y1, x2 - x1, y2 - y1);
    }

    public static void union(Rectangle2D a, Rectangle2D b, Rectangle2D dest) {
        double x1 = Math.min(a.getMinX(), b.getMinX()), y1 = Math.min(a.getMinY(), b.getMinY());
        double x2 = Math.max(a.getMaxX(), b.getMaxX()), y2 = Math.max(a.getMaxY(), b.getMaxY());
        dest.setFrameFromDiagonal(x1, y1, x2, y2);
    }

    public void add(double px, double py) {
        double x1 = Math.min(getMinX(), px), x2 = Math.max(getMaxX(), px);
        double y1 = Math.min(getMinY(), py), y2 = Math.max(getMaxY(), py);
        setRect(x1, y1, x2 - x1, y2 - y1);
    }

    public void add(Point2D p) { add(p.getX(), p.getY()); }
    public void add(Rectangle2D r) { union(this, r, this); }

    @Override
    public int hashCode() {
        long bits = java.lang.Double.doubleToLongBits(getX());
        bits += java.lang.Double.doubleToLongBits(getY()) * 37;
        bits += java.lang.Double.doubleToLongBits(getWidth()) * 43;
        bits += java.lang.Double.doubleToLongBits(getHeight()) * 47;
        return (((int) bits) ^ ((int) (bits >> 32)));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj instanceof Rectangle2D) {
            Rectangle2D r = (Rectangle2D) obj;
            return getX() == r.getX() && getY() == r.getY() && getWidth() == r.getWidth() && getHeight() == r.getHeight();
        }
        return false;
    }
}
