package com.airsky.browser.jvm.java.awt.geom;

import com.airsky.browser.jvm.java.awt.Rectangle;
import com.airsky.browser.jvm.java.awt.Shape;

/** java.awt.geom.RectangularShape の互換クラス。 */
public abstract class RectangularShape implements Shape, Cloneable {
    protected RectangularShape() {}

    public abstract double getX();
    public abstract double getY();
    public abstract double getWidth();
    public abstract double getHeight();
    public abstract boolean isEmpty();
    public abstract void setFrame(double x, double y, double w, double h);

    public double getMinX() { return getX(); }
    public double getMinY() { return getY(); }
    public double getMaxX() { return getX() + getWidth(); }
    public double getMaxY() { return getY() + getHeight(); }
    public double getCenterX() { return getX() + getWidth() / 2.0; }
    public double getCenterY() { return getY() + getHeight() / 2.0; }
    public Rectangle2D getFrame() { return new Rectangle2D.Double(getX(), getY(), getWidth(), getHeight()); }
    public void setFrame(Point2D loc, com.airsky.browser.jvm.java.awt.Dimension size) { setFrame(loc.getX(), loc.getY(), size.getWidth(), size.getHeight()); }
    public void setFrame(Rectangle2D r) { setFrame(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }

    public void setFrameFromDiagonal(double x1, double y1, double x2, double y2) {
        setFrame(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
    }

    public void setFrameFromCenter(double cx, double cy, double cornerX, double cornerY) {
        double hw = Math.abs(cornerX - cx), hh = Math.abs(cornerY - cy);
        setFrame(cx - hw, cy - hh, hw * 2, hh * 2);
    }

    @Override public boolean contains(Point2D p) { return contains(p.getX(), p.getY()); }
    @Override public boolean intersects(Rectangle2D r) { return intersects(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }
    @Override public boolean contains(Rectangle2D r) { return contains(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }

    @Override
    public Rectangle getBounds() {
        double w = getWidth(), h = getHeight();
        if (w < 0 || h < 0) return new Rectangle();
        double x = Math.floor(getX()), y = Math.floor(getY());
        double x2 = Math.ceil(getX() + w), y2 = Math.ceil(getY() + h);
        return new Rectangle((int) x, (int) y, (int) (x2 - x), (int) (y2 - y));
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }
}
