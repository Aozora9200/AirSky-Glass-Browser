package com.airsky.browser.jvm.java.awt.geom;

/** java.awt.geom.Arc2D の互換クラス。 */
public abstract class Arc2D extends RectangularShape {
    public static final int OPEN = 0;
    public static final int CHORD = 1;
    public static final int PIE = 2;

    private int type;

    public static class Float extends Arc2D implements java.io.Serializable {
        public float x, y, width, height, start, extent;

        public Float() { super(OPEN); }
        public Float(int type) { super(type); }

        public Float(float x, float y, float w, float h, float start, float extent, int type) {
            super(type);
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            this.start = start;
            this.extent = extent;
        }

        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public double getAngleStart() { return start; }
        @Override public double getAngleExtent() { return extent; }
        @Override public boolean isEmpty() { return width <= 0f || height <= 0f; }

        @Override
        public void setArc(double x, double y, double w, double h, double start, double extent, int type) {
            setArcType(type);
            this.x = (float) x;
            this.y = (float) y;
            this.width = (float) w;
            this.height = (float) h;
            this.start = (float) start;
            this.extent = (float) extent;
        }

        @Override public void setAngleStart(double a) { start = (float) a; }
        @Override public void setAngleExtent(double a) { extent = (float) a; }
        @Override public Rectangle2D getBounds2D() { return new Rectangle2D.Float(x, y, width, height); }
    }

    public static class Double extends Arc2D implements java.io.Serializable {
        public double x, y, width, height, start, extent;

        public Double() { super(OPEN); }
        public Double(int type) { super(type); }

        public Double(double x, double y, double w, double h, double start, double extent, int type) {
            super(type);
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            this.start = start;
            this.extent = extent;
        }

        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public double getAngleStart() { return start; }
        @Override public double getAngleExtent() { return extent; }
        @Override public boolean isEmpty() { return width <= 0.0 || height <= 0.0; }

        @Override
        public void setArc(double x, double y, double w, double h, double start, double extent, int type) {
            setArcType(type);
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            this.start = start;
            this.extent = extent;
        }

        @Override public void setAngleStart(double a) { start = a; }
        @Override public void setAngleExtent(double a) { extent = a; }
        @Override public Rectangle2D getBounds2D() { return new Rectangle2D.Double(x, y, width, height); }
    }

    protected Arc2D(int type) { setArcType(type); }

    public abstract double getAngleStart();
    public abstract double getAngleExtent();
    public abstract void setArc(double x, double y, double w, double h, double start, double extent, int type);
    public abstract void setAngleStart(double a);
    public abstract void setAngleExtent(double a);

    public int getArcType() { return type; }

    public void setArcType(int type) {
        if (type < OPEN || type > PIE) throw new IllegalArgumentException("invalid type for Arc: " + type);
        this.type = type;
    }

    @Override
    public void setFrame(double x, double y, double w, double h) {
        setArc(x, y, w, h, getAngleStart(), getAngleExtent(), type);
    }

    @Override public boolean contains(double x, double y) { return getBounds2D().contains(x, y); }
    @Override public boolean intersects(double x, double y, double w, double h) { return getBounds2D().intersects(x, y, w, h); }
    @Override public boolean contains(double x, double y, double w, double h) { return getBounds2D().contains(x, y, w, h); }
}
