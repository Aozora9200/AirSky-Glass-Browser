package com.airsky.browser.jvm;

import android.graphics.Path;
import android.graphics.RectF;

import com.airsky.browser.jvm.java.awt.Polygon;
import com.airsky.browser.jvm.java.awt.Shape;
import com.airsky.browser.jvm.java.awt.geom.Arc2D;
import com.airsky.browser.jvm.java.awt.geom.Ellipse2D;
import com.airsky.browser.jvm.java.awt.geom.Line2D;
import com.airsky.browser.jvm.java.awt.geom.Path2D;
import com.airsky.browser.jvm.java.awt.geom.Rectangle2D;
import com.airsky.browser.jvm.java.awt.geom.RoundRectangle2D;

/** AWT の Shape を Android の Path にする。 */
public final class JvmShapes {
    private JvmShapes() {}

    public static Path path(Shape s) {
        Path p = new Path();
        if (s instanceof Path2D) {
            return ((Path2D) s).jvm$path();
        } else if (s instanceof RoundRectangle2D) {
            RoundRectangle2D r = (RoundRectangle2D) s;
            p.addRoundRect(new RectF((float) r.getX(), (float) r.getY(), (float) (r.getX() + r.getWidth()),
                    (float) (r.getY() + r.getHeight())), (float) r.getArcWidth() / 2f, (float) r.getArcHeight() / 2f,
                    Path.Direction.CW);
        } else if (s instanceof Rectangle2D) {
            Rectangle2D r = (Rectangle2D) s;
            p.addRect((float) r.getX(), (float) r.getY(), (float) (r.getX() + r.getWidth()),
                    (float) (r.getY() + r.getHeight()), Path.Direction.CW);
        } else if (s instanceof Ellipse2D) {
            Ellipse2D e = (Ellipse2D) s;
            p.addOval(new RectF((float) e.getX(), (float) e.getY(), (float) (e.getX() + e.getWidth()),
                    (float) (e.getY() + e.getHeight())), Path.Direction.CW);
        } else if (s instanceof Arc2D) {
            Arc2D a = (Arc2D) s;
            RectF r = new RectF((float) a.getX(), (float) a.getY(), (float) (a.getX() + a.getWidth()),
                    (float) (a.getY() + a.getHeight()));
            if (a.getArcType() == Arc2D.PIE) {
                p.moveTo(r.centerX(), r.centerY());
                p.arcTo(r, (float) -a.getAngleStart(), (float) -a.getAngleExtent());
                p.close();
            } else {
                p.addArc(r, (float) -a.getAngleStart(), (float) -a.getAngleExtent());
                if (a.getArcType() == Arc2D.CHORD) p.close();
            }
        } else if (s instanceof Line2D) {
            Line2D l = (Line2D) s;
            p.moveTo((float) l.getX1(), (float) l.getY1());
            p.lineTo((float) l.getX2(), (float) l.getY2());
        } else if (s instanceof Polygon) {
            Polygon g = (Polygon) s;
            if (g.npoints > 0) {
                p.moveTo(g.xpoints[0], g.ypoints[0]);
                for (int i = 1; i < g.npoints; i++) p.lineTo(g.xpoints[i], g.ypoints[i]);
                p.close();
            }
            p.setFillType(Path.FillType.EVEN_ODD);
        } else {
            Rectangle2D r = s.getBounds2D();
            if (r != null) {
                p.addRect((float) r.getX(), (float) r.getY(), (float) (r.getX() + r.getWidth()),
                        (float) (r.getY() + r.getHeight()), Path.Direction.CW);
            }
        }
        return p;
    }
}
