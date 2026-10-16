package com.airsky.browser.jvm.java.awt.geom;

/** java.awt.geom.Ellipse2D の互換クラス。 */
public abstract class Ellipse2D extends RectangularShape {

    public static class Float extends Ellipse2D implements java.io.Serializable {
        public float x, y, width, height;

        public Float() {}

        public Float(float x, float y, float w, float h) { setFrame(x, y, w, h); }

        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public boolean isEmpty() { return width <= 0f || height <= 0f; }

        public void setFrame(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
        }

        @Override public void setFrame(double x, double y, double w, double h) { setFrame((float) x, (float) y, (float) w, (float) h); }
        @Override public Rectangle2D getBounds2D() { return new Rectangle2D.Float(x, y, width, height); }
    }

    public static class Double extends Ellipse2D implements java.io.Serializable {
        public double x, y, width, height;

        public Double() {}

        public Double(double x, double y, double w, double h) { setFrame(x, y, w, h); }

        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public boolean isEmpty() { return width <= 0.0 || height <= 0.0; }

        @Override
        public void setFrame(double x, double y, double w, double h) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
        }

        @Override public Rectangle2D getBounds2D() { return new Rectangle2D.Double(x, y, width, height); }
    }

    protected Ellipse2D() {}

    @Override
    public boolean contains(double x, double y) {
        double ew = getWidth();
        if (ew <= 0.0) return false;
        double normx = (x - getX()) / ew - 0.5;
        double eh = getHeight();
        if (eh <= 0.0) return false;
        double normy = (y - getY()) / eh - 0.5;
        return (normx * normx + normy * normy) < 0.25;
    }

    @Override
    public boolean intersects(double x, double y, double w, double h) {
        if (w <= 0.0 || h <= 0.0) return false;
        double ew = getWidth(), eh = getHeight();
        if (ew <= 0.0 || eh <= 0.0) return false;
        double cx = getX() + ew / 2, cy = getY() + eh / 2;
        double nx = Math.max(x, Math.min(cx, x + w));
        double ny = Math.max(y, Math.min(cy, y + h));
        return contains(nx, ny) || new Rectangle2D.Double(x, y, w, h).contains(cx, cy);
    }

    @Override
    public boolean contains(double x, double y, double w, double h) {
        return contains(x, y) && contains(x + w, y) && contains(x, y + h) && contains(x + w, y + h);
    }
}
