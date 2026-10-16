package com.airsky.browser.jvm.java.awt;

/** java.awt.Dimension の互換クラス。 */
public class Dimension implements Cloneable, java.io.Serializable {
    public int width;
    public int height;

    public Dimension() {}
    public Dimension(Dimension d) { this(d.width, d.height); }

    public Dimension(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public double getWidth() { return width; }
    public double getHeight() { return height; }

    public void setSize(double w, double h) {
        width = (int) Math.ceil(w);
        height = (int) Math.ceil(h);
    }

    public Dimension getSize() { return new Dimension(width, height); }
    public void setSize(Dimension d) { setSize(d.width, d.height); }

    public void setSize(int w, int h) {
        width = w;
        height = h;
    }

    @Override public boolean equals(Object o) {
        return o instanceof Dimension && ((Dimension) o).width == width && ((Dimension) o).height == height;
    }

    @Override public int hashCode() { return width * 31 + height; }
    @Override public String toString() { return getClass().getName() + "[width=" + width + ",height=" + height + "]"; }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }
}
