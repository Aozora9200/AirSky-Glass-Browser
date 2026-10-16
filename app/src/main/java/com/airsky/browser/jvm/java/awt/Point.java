package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.java.awt.geom.Point2D;

/** java.awt.Point の互換クラス。 */
public class Point extends Point2D implements java.io.Serializable {
    public int x;
    public int y;

    public Point() {}
    public Point(Point p) { this(p.x, p.y); }

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override public double getX() { return x; }
    @Override public double getY() { return y; }
    public Point getLocation() { return new Point(x, y); }
    public void setLocation(Point p) { setLocation(p.x, p.y); }

    public void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void setLocation(double x, double y) {
        this.x = (int) Math.floor(x + 0.5);
        this.y = (int) Math.floor(y + 0.5);
    }

    public void move(int x, int y) { setLocation(x, y); }

    public void translate(int dx, int dy) {
        x += dx;
        y += dy;
    }

    @Override public boolean equals(Object o) {
        if (o instanceof Point) return ((Point) o).x == x && ((Point) o).y == y;
        return super.equals(o);
    }

    @Override public int hashCode() { return super.hashCode(); }
    @Override public String toString() { return getClass().getName() + "[x=" + x + ",y=" + y + "]"; }
}
