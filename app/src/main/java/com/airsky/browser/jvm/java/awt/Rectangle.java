package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.java.awt.geom.Rectangle2D;

/** java.awt.Rectangle の互換クラス。 */
public class Rectangle extends Rectangle2D implements Shape, java.io.Serializable {
    public int x, y, width, height;

    public Rectangle() {}
    public Rectangle(Rectangle r) { this(r.x, r.y, r.width, r.height); }

    public Rectangle(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public Rectangle(int width, int height) { this(0, 0, width, height); }
    public Rectangle(Point p, Dimension d) { this(p.x, p.y, d.width, d.height); }
    public Rectangle(Point p) { this(p.x, p.y, 0, 0); }
    public Rectangle(Dimension d) { this(0, 0, d.width, d.height); }

    @Override public double getX() { return x; }
    @Override public double getY() { return y; }
    @Override public double getWidth() { return width; }
    @Override public double getHeight() { return height; }
    @Override public Rectangle getBounds() { return new Rectangle(x, y, width, height); }
    @Override public Rectangle2D getBounds2D() { return new Rectangle(x, y, width, height); }

    public void setBounds(Rectangle r) { setBounds(r.x, r.y, r.width, r.height); }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Deprecated public void reshape(int x, int y, int w, int h) { setBounds(x, y, w, h); }

    @Override
    public void setRect(double x, double y, double w, double h) {
        int nx = (int) Math.floor(x), ny = (int) Math.floor(y);
        int nw = (int) Math.ceil(x + w) - nx, nh = (int) Math.ceil(y + h) - ny;
        setBounds(nx, ny, nw, nh);
    }

    public Point getLocation() { return new Point(x, y); }
    public void setLocation(Point p) { setLocation(p.x, p.y); }

    public void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Deprecated public void move(int x, int y) { setLocation(x, y); }

    public void translate(int dx, int dy) {
        x += dx;
        y += dy;
    }

    public Dimension getSize() { return new Dimension(width, height); }
    public void setSize(Dimension d) { setSize(d.width, d.height); }

    public void setSize(int w, int h) {
        width = w;
        height = h;
    }

    @Deprecated public void resize(int w, int h) { setSize(w, h); }

    public boolean contains(Point p) { return contains(p.x, p.y); }

    public boolean contains(int px, int py) {
        return width > 0 && height > 0 && px >= x && py >= y && px < x + width && py < y + height;
    }

    public boolean contains(Rectangle r) { return contains(r.x, r.y, r.width, r.height); }

    public boolean contains(int X, int Y, int W, int H) {
        return width > 0 && height > 0 && W >= 0 && H >= 0 && X >= x && Y >= y
                && X + W <= x + width && Y + H <= y + height;
    }

    @Deprecated public boolean inside(int px, int py) { return contains(px, py); }

    public boolean intersects(Rectangle r) {
        return r.width > 0 && r.height > 0 && width > 0 && height > 0
                && r.x < x + width && r.x + r.width > x && r.y < y + height && r.y + r.height > y;
    }

    public Rectangle intersection(Rectangle r) {
        int x1 = Math.max(x, r.x), y1 = Math.max(y, r.y);
        int x2 = Math.min(x + width, r.x + r.width), y2 = Math.min(y + height, r.y + r.height);
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    public Rectangle union(Rectangle r) {
        int x1 = Math.min(x, r.x), y1 = Math.min(y, r.y);
        int x2 = Math.max(x + width, r.x + r.width), y2 = Math.max(y + height, r.y + r.height);
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    public void add(int px, int py) {
        int x1 = Math.min(x, px), y1 = Math.min(y, py);
        int x2 = Math.max(x + width, px), y2 = Math.max(y + height, py);
        setBounds(x1, y1, x2 - x1, y2 - y1);
    }

    public void add(Point p) { add(p.x, p.y); }

    public void add(Rectangle r) {
        Rectangle u = union(r);
        setBounds(u);
    }

    public void grow(int h, int v) {
        x -= h;
        y -= v;
        width += 2 * h;
        height += 2 * v;
    }

    @Override public boolean isEmpty() { return width <= 0 || height <= 0; }

    @Override
    public Rectangle2D createIntersection(Rectangle2D r) {
        if (r instanceof Rectangle) return intersection((Rectangle) r);
        return super.createIntersection(r);
    }

    @Override
    public Rectangle2D createUnion(Rectangle2D r) {
        if (r instanceof Rectangle) return union((Rectangle) r);
        return super.createUnion(r);
    }

    @Override public boolean equals(Object o) {
        if (o instanceof Rectangle) {
            Rectangle r = (Rectangle) o;
            return r.x == x && r.y == y && r.width == width && r.height == height;
        }
        return super.equals(o);
    }

    @Override public int hashCode() { return super.hashCode(); }

    @Override public String toString() {
        return getClass().getName() + "[x=" + x + ",y=" + y + ",width=" + width + ",height=" + height + "]";
    }
}
