package com.airsky.browser.jvm.java.awt.geom;

import android.graphics.Matrix;

import com.airsky.browser.jvm.java.awt.Shape;

/** java.awt.geom.AffineTransform の互換クラス。 */
public class AffineTransform implements Cloneable, java.io.Serializable {
    public static final int TYPE_IDENTITY = 0;
    public static final int TYPE_TRANSLATION = 1;
    public static final int TYPE_UNIFORM_SCALE = 2;
    public static final int TYPE_GENERAL_SCALE = 4;
    public static final int TYPE_FLIP = 64;
    public static final int TYPE_QUADRANT_ROTATION = 8;
    public static final int TYPE_GENERAL_ROTATION = 16;
    public static final int TYPE_GENERAL_TRANSFORM = 32;

    double m00 = 1, m10, m01, m11 = 1, m02, m12;

    public AffineTransform() {}
    public AffineTransform(AffineTransform t) { setTransform(t); }

    public AffineTransform(double m00, double m10, double m01, double m11, double m02, double m12) {
        setTransform(m00, m10, m01, m11, m02, m12);
    }

    public AffineTransform(float m00, float m10, float m01, float m11, float m02, float m12) {
        setTransform(m00, m10, m01, m11, m02, m12);
    }

    public AffineTransform(double[] m) {
        m00 = m[0];
        m10 = m[1];
        m01 = m[2];
        m11 = m[3];
        if (m.length > 5) {
            m02 = m[4];
            m12 = m[5];
        }
    }

    public static AffineTransform getTranslateInstance(double tx, double ty) {
        AffineTransform t = new AffineTransform();
        t.setToTranslation(tx, ty);
        return t;
    }

    public static AffineTransform getRotateInstance(double theta) {
        AffineTransform t = new AffineTransform();
        t.setToRotation(theta);
        return t;
    }

    public static AffineTransform getRotateInstance(double theta, double x, double y) {
        AffineTransform t = new AffineTransform();
        t.setToRotation(theta, x, y);
        return t;
    }

    public static AffineTransform getScaleInstance(double sx, double sy) {
        AffineTransform t = new AffineTransform();
        t.setToScale(sx, sy);
        return t;
    }

    public static AffineTransform getShearInstance(double shx, double shy) {
        AffineTransform t = new AffineTransform();
        t.setToShear(shx, shy);
        return t;
    }

    public double getScaleX() { return m00; }
    public double getScaleY() { return m11; }
    public double getShearX() { return m01; }
    public double getShearY() { return m10; }
    public double getTranslateX() { return m02; }
    public double getTranslateY() { return m12; }
    public double getDeterminant() { return m00 * m11 - m01 * m10; }

    public int getType() {
        if (isIdentity()) return TYPE_IDENTITY;
        if (m01 == 0 && m10 == 0 && m00 == 1 && m11 == 1) return TYPE_TRANSLATION;
        return TYPE_GENERAL_TRANSFORM;
    }

    public void getMatrix(double[] m) {
        m[0] = m00;
        m[1] = m10;
        m[2] = m01;
        m[3] = m11;
        if (m.length > 5) {
            m[4] = m02;
            m[5] = m12;
        }
    }

    public void translate(double tx, double ty) {
        m02 += tx * m00 + ty * m01;
        m12 += tx * m10 + ty * m11;
    }

    public void rotate(double theta) {
        double s = Math.sin(theta), c = Math.cos(theta);
        double n00 = m00 * c + m01 * s;
        double n01 = -m00 * s + m01 * c;
        double n10 = m10 * c + m11 * s;
        double n11 = -m10 * s + m11 * c;
        m00 = n00;
        m01 = n01;
        m10 = n10;
        m11 = n11;
    }

    public void rotate(double theta, double x, double y) {
        translate(x, y);
        rotate(theta);
        translate(-x, -y);
    }

    public void rotate(double vecx, double vecy, double x, double y) {
        rotate(Math.atan2(vecy, vecx), x, y);
    }

    public void quadrantRotate(int n) { rotate(n * Math.PI / 2); }
    public void quadrantRotate(int n, double x, double y) { rotate(n * Math.PI / 2, x, y); }

    public void scale(double sx, double sy) {
        m00 *= sx;
        m10 *= sx;
        m01 *= sy;
        m11 *= sy;
    }

    public void shear(double shx, double shy) {
        double n00 = m00 + m01 * shy;
        double n01 = m00 * shx + m01;
        double n10 = m10 + m11 * shy;
        double n11 = m10 * shx + m11;
        m00 = n00;
        m01 = n01;
        m10 = n10;
        m11 = n11;
    }

    public void setToIdentity() { setTransform(1, 0, 0, 1, 0, 0); }
    public void setToTranslation(double tx, double ty) { setTransform(1, 0, 0, 1, tx, ty); }

    public void setToRotation(double theta) {
        double s = Math.sin(theta), c = Math.cos(theta);
        setTransform(c, s, -s, c, 0, 0);
    }

    public void setToRotation(double theta, double x, double y) {
        setToRotation(theta);
        double s = m10, c = m00;
        m02 = x * (1.0 - c) + y * s;
        m12 = y * (1.0 - c) - x * s;
    }

    public void setToScale(double sx, double sy) { setTransform(sx, 0, 0, sy, 0, 0); }
    public void setToShear(double shx, double shy) { setTransform(1, shy, shx, 1, 0, 0); }

    public void setTransform(AffineTransform t) { setTransform(t.m00, t.m10, t.m01, t.m11, t.m02, t.m12); }

    public void setTransform(double m00, double m10, double m01, double m11, double m02, double m12) {
        this.m00 = m00;
        this.m10 = m10;
        this.m01 = m01;
        this.m11 = m11;
        this.m02 = m02;
        this.m12 = m12;
    }

    /** this = this × t */
    public void concatenate(AffineTransform t) {
        double n00 = m00 * t.m00 + m01 * t.m10;
        double n01 = m00 * t.m01 + m01 * t.m11;
        double n02 = m00 * t.m02 + m01 * t.m12 + m02;
        double n10 = m10 * t.m00 + m11 * t.m10;
        double n11 = m10 * t.m01 + m11 * t.m11;
        double n12 = m10 * t.m02 + m11 * t.m12 + m12;
        setTransform(n00, n10, n01, n11, n02, n12);
    }

    /** this = t × this */
    public void preConcatenate(AffineTransform t) {
        double n00 = t.m00 * m00 + t.m01 * m10;
        double n01 = t.m00 * m01 + t.m01 * m11;
        double n02 = t.m00 * m02 + t.m01 * m12 + t.m02;
        double n10 = t.m10 * m00 + t.m11 * m10;
        double n11 = t.m10 * m01 + t.m11 * m11;
        double n12 = t.m10 * m02 + t.m11 * m12 + t.m12;
        setTransform(n00, n10, n01, n11, n02, n12);
    }

    public AffineTransform createInverse() throws NoninvertibleTransformException {
        double det = getDeterminant();
        if (Math.abs(det) <= java.lang.Double.MIN_VALUE) {
            throw new NoninvertibleTransformException("Determinant is " + det);
        }
        return new AffineTransform(m11 / det, -m10 / det, -m01 / det, m00 / det,
                (m01 * m12 - m11 * m02) / det, (m10 * m02 - m00 * m12) / det);
    }

    public void invert() throws NoninvertibleTransformException { setTransform(createInverse()); }

    public Point2D transform(Point2D src, Point2D dst) {
        if (dst == null) dst = src instanceof Point2D.Float ? new Point2D.Float() : new Point2D.Double();
        double x = src.getX(), y = src.getY();
        dst.setLocation(x * m00 + y * m01 + m02, x * m10 + y * m11 + m12);
        return dst;
    }

    public void transform(double[] src, int srcOff, double[] dst, int dstOff, int n) {
        double[] tmp = java.util.Arrays.copyOfRange(src, srcOff, srcOff + n * 2);
        for (int i = 0; i < n; i++) {
            double x = tmp[i * 2], y = tmp[i * 2 + 1];
            dst[dstOff + i * 2] = x * m00 + y * m01 + m02;
            dst[dstOff + i * 2 + 1] = x * m10 + y * m11 + m12;
        }
    }

    public void transform(float[] src, int srcOff, float[] dst, int dstOff, int n) {
        float[] tmp = java.util.Arrays.copyOfRange(src, srcOff, srcOff + n * 2);
        for (int i = 0; i < n; i++) {
            double x = tmp[i * 2], y = tmp[i * 2 + 1];
            dst[dstOff + i * 2] = (float) (x * m00 + y * m01 + m02);
            dst[dstOff + i * 2 + 1] = (float) (x * m10 + y * m11 + m12);
        }
    }

    public Point2D inverseTransform(Point2D src, Point2D dst) throws NoninvertibleTransformException {
        return createInverse().transform(src, dst);
    }

    public Point2D deltaTransform(Point2D src, Point2D dst) {
        if (dst == null) dst = new Point2D.Double();
        double x = src.getX(), y = src.getY();
        dst.setLocation(x * m00 + y * m01, x * m10 + y * m11);
        return dst;
    }

    public Shape createTransformedShape(Shape s) {
        if (s == null) return null;
        return new Path2D.Double(s, this);
    }

    public boolean isIdentity() { return m00 == 1 && m11 == 1 && m01 == 0 && m10 == 0 && m02 == 0 && m12 == 0; }

    @Override
    public Object clone() {
        return new AffineTransform(this);
    }

    @Override
    public int hashCode() {
        long bits = java.lang.Double.doubleToLongBits(m00);
        bits = bits * 31 + java.lang.Double.doubleToLongBits(m01);
        bits = bits * 31 + java.lang.Double.doubleToLongBits(m02);
        bits = bits * 31 + java.lang.Double.doubleToLongBits(m10);
        bits = bits * 31 + java.lang.Double.doubleToLongBits(m11);
        bits = bits * 31 + java.lang.Double.doubleToLongBits(m12);
        return (((int) bits) ^ ((int) (bits >> 32)));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AffineTransform)) return false;
        AffineTransform t = (AffineTransform) o;
        return m00 == t.m00 && m01 == t.m01 && m02 == t.m02 && m10 == t.m10 && m11 == t.m11 && m12 == t.m12;
    }

    @Override
    public String toString() {
        return "AffineTransform[[" + m00 + ", " + m01 + ", " + m02 + "], [" + m10 + ", " + m11 + ", " + m12 + "]]";
    }

    public Matrix jvm$matrix() {
        Matrix m = new Matrix();
        m.setValues(new float[]{(float) m00, (float) m01, (float) m02, (float) m10, (float) m11, (float) m12, 0, 0, 1});
        return m;
    }

    public Rectangle2D jvm$transformBounds(double l, double t, double r, double b) {
        double[] pts = {l, t, r, t, r, b, l, b};
        transform(pts, 0, pts, 0, 4);
        double x1 = Math.min(Math.min(pts[0], pts[2]), Math.min(pts[4], pts[6]));
        double y1 = Math.min(Math.min(pts[1], pts[3]), Math.min(pts[5], pts[7]));
        double x2 = Math.max(Math.max(pts[0], pts[2]), Math.max(pts[4], pts[6]));
        double y2 = Math.max(Math.max(pts[1], pts[3]), Math.max(pts[5], pts[7]));
        return new Rectangle2D.Double(x1, y1, x2 - x1, y2 - y1);
    }
}
