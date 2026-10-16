package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.java.awt.geom.Point2D;
import com.airsky.browser.jvm.java.awt.geom.Rectangle2D;

/** java.awt.Polygon の互換クラス。 */
public class Polygon implements Shape, java.io.Serializable {
    public int npoints;
    public int[] xpoints;
    public int[] ypoints;
    protected Rectangle bounds;

    public Polygon() {
        xpoints = new int[4];
        ypoints = new int[4];
    }

    public Polygon(int[] xs, int[] ys, int n) {
        if (n > xs.length || n > ys.length) throw new IndexOutOfBoundsException("npoints > xpoints.length || npoints > ypoints.length");
        if (n < 0) throw new NegativeArraySizeException("npoints < 0");
        npoints = n;
        xpoints = java.util.Arrays.copyOf(xs, n);
        ypoints = java.util.Arrays.copyOf(ys, n);
    }

    public void reset() {
        npoints = 0;
        bounds = null;
    }

    public void invalidate() { bounds = null; }

    public void translate(int dx, int dy) {
        for (int i = 0; i < npoints; i++) {
            xpoints[i] += dx;
            ypoints[i] += dy;
        }
        if (bounds != null) bounds.translate(dx, dy);
    }

    public void addPoint(int px, int py) {
        if (npoints >= xpoints.length) {
            int n = Math.max(npoints * 2, 4);
            xpoints = java.util.Arrays.copyOf(xpoints, n);
            ypoints = java.util.Arrays.copyOf(ypoints, n);
        }
        xpoints[npoints] = px;
        ypoints[npoints] = py;
        npoints++;
        bounds = null;
    }

    @Override
    public Rectangle getBounds() {
        if (npoints == 0) return new Rectangle();
        if (bounds == null) {
            int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE;
            for (int i = 0; i < npoints; i++) {
                x1 = Math.min(x1, xpoints[i]);
                y1 = Math.min(y1, ypoints[i]);
                x2 = Math.max(x2, xpoints[i]);
                y2 = Math.max(y2, ypoints[i]);
            }
            bounds = new Rectangle(x1, y1, x2 - x1, y2 - y1);
        }
        return bounds.getBounds();
    }

    @Deprecated public Rectangle getBoundingBox() { return getBounds(); }
    @Override public Rectangle2D getBounds2D() { return getBounds(); }
    public boolean contains(Point p) { return contains(p.x, p.y); }
    public boolean contains(int px, int py) { return contains((double) px, (double) py); }
    @Deprecated public boolean inside(int px, int py) { return contains(px, py); }

    @Override
    public boolean contains(double px, double py) {
        boolean inside = false;
        for (int i = 0, j = npoints - 1; i < npoints; j = i++) {
            if ((ypoints[i] > py) != (ypoints[j] > py)
                    && px < (double) (xpoints[j] - xpoints[i]) * (py - ypoints[i]) / (ypoints[j] - ypoints[i]) + xpoints[i]) {
                inside = !inside;
            }
        }
        return inside;
    }

    @Override public boolean contains(Point2D p) { return contains(p.getX(), p.getY()); }
    @Override public boolean intersects(double x, double y, double w, double h) { return getBounds().intersects(x, y, w, h); }
    @Override public boolean intersects(Rectangle2D r) { return intersects(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }

    @Override
    public boolean contains(double x, double y, double w, double h) {
        return contains(x, y) && contains(x + w, y) && contains(x, y + h) && contains(x + w, y + h);
    }

    @Override public boolean contains(Rectangle2D r) { return contains(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }
}
