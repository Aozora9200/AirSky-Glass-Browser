package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.java.awt.geom.Point2D;
import com.airsky.browser.jvm.java.awt.geom.Rectangle2D;

public interface Shape {
    Rectangle getBounds();
    Rectangle2D getBounds2D();
    boolean contains(double x, double y);
    boolean contains(Point2D p);
    boolean intersects(double x, double y, double w, double h);
    boolean intersects(Rectangle2D r);
    boolean contains(double x, double y, double w, double h);
    boolean contains(Rectangle2D r);
}
