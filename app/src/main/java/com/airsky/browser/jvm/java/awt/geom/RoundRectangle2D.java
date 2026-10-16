package com.airsky.browser.jvm.java.awt.geom;

/** java.awt.geom.RoundRectangle2D の互換クラス。 */
public abstract class RoundRectangle2D extends RectangularShape {

    public static class Float extends RoundRectangle2D implements java.io.Serializable {
        public float x, y, width, height, arcwidth, archeight;

        public Float() {}

        public Float(float x, float y, float w, float h, float aw, float ah) {
            setRoundRect(x, y, w, h, aw, ah);
        }

        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public double getArcWidth() { return arcwidth; }
        @Override public double getArcHeight() { return archeight; }
        @Override public boolean isEmpty() { return width <= 0f || height <= 0f; }

        @Override
        public void setRoundRect(double x, double y, double w, double h, double aw, double ah) {
            this.x = (float) x;
            this.y = (float) y;
            this.width = (float) w;
            this.height = (float) h;
            this.arcwidth = (float) aw;
            this.archeight = (float) ah;
        }

        @Override public Rectangle2D getBounds2D() { return new Rectangle2D.Float(x, y, width, height); }
    }

    public static class Double extends RoundRectangle2D implements java.io.Serializable {
        public double x, y, width, height, arcwidth, archeight;

        public Double() {}

        public Double(double x, double y, double w, double h, double aw, double ah) {
            setRoundRect(x, y, w, h, aw, ah);
        }

        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public double getArcWidth() { return arcwidth; }
        @Override public double getArcHeight() { return archeight; }
        @Override public boolean isEmpty() { return width <= 0.0 || height <= 0.0; }

        @Override
        public void setRoundRect(double x, double y, double w, double h, double aw, double ah) {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            this.arcwidth = aw;
            this.archeight = ah;
        }

        @Override public Rectangle2D getBounds2D() { return new Rectangle2D.Double(x, y, width, height); }
    }

    protected RoundRectangle2D() {}

    public abstract double getArcWidth();
    public abstract double getArcHeight();
    public abstract void setRoundRect(double x, double y, double w, double h, double aw, double ah);

    @Override
    public void setFrame(double x, double y, double w, double h) {
        setRoundRect(x, y, w, h, getArcWidth(), getArcHeight());
    }

    @Override
    public boolean contains(double x, double y) {
        return getBounds2D().contains(x, y);
    }

    @Override
    public boolean intersects(double x, double y, double w, double h) {
        return getBounds2D().intersects(x, y, w, h);
    }

    @Override
    public boolean contains(double x, double y, double w, double h) {
        return getBounds2D().contains(x, y, w, h);
    }
}
