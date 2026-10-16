package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.java.awt.image.ImageObserver;

/** java.awt.Graphics の互換クラス。実体は JvmGraphics。 */
public abstract class Graphics {
    protected Graphics() {}

    public abstract Graphics create();

    public Graphics create(int x, int y, int w, int h) {
        Graphics g = create();
        if (g == null) return null;
        g.translate(x, y);
        g.clipRect(0, 0, w, h);
        return g;
    }

    public abstract void translate(int x, int y);
    public abstract Color getColor();
    public abstract void setColor(Color c);
    public abstract void setPaintMode();
    public abstract void setXORMode(Color c);
    public abstract Font getFont();
    public abstract void setFont(Font f);

    public FontMetrics getFontMetrics() { return getFontMetrics(getFont()); }

    public abstract FontMetrics getFontMetrics(Font f);
    public abstract Rectangle getClipBounds();
    public abstract void clipRect(int x, int y, int w, int h);
    public abstract void setClip(int x, int y, int w, int h);
    public abstract Shape getClip();
    public abstract void setClip(Shape s);
    public abstract void copyArea(int x, int y, int w, int h, int dx, int dy);
    public abstract void drawLine(int x1, int y1, int x2, int y2);
    public abstract void fillRect(int x, int y, int w, int h);

    public void drawRect(int x, int y, int w, int h) {
        if (w < 0 || h < 0) return;
        drawLine(x, y, x + w, y);
        drawLine(x + w, y, x + w, y + h);
        drawLine(x + w, y + h, x, y + h);
        drawLine(x, y + h, x, y);
    }

    public abstract void clearRect(int x, int y, int w, int h);
    public abstract void drawRoundRect(int x, int y, int w, int h, int aw, int ah);
    public abstract void fillRoundRect(int x, int y, int w, int h, int aw, int ah);

    public void draw3DRect(int x, int y, int w, int h, boolean raised) {
        Color c = getColor();
        Color brighter = c.brighter(), darker = c.darker();
        setColor(raised ? brighter : darker);
        drawLine(x, y, x, y + h);
        drawLine(x + 1, y, x + w - 1, y);
        setColor(raised ? darker : brighter);
        drawLine(x + 1, y + h, x + w, y + h);
        drawLine(x + w, y, x + w, y + h - 1);
        setColor(c);
    }

    public void fill3DRect(int x, int y, int w, int h, boolean raised) {
        Color c = getColor();
        Color brighter = c.brighter(), darker = c.darker();
        if (!raised) setColor(darker);
        fillRect(x + 1, y + 1, w - 2, h - 2);
        setColor(raised ? brighter : darker);
        drawLine(x, y, x, y + h - 1);
        drawLine(x + 1, y, x + w - 2, y);
        setColor(raised ? darker : brighter);
        drawLine(x + 1, y + h - 1, x + w - 1, y + h - 1);
        drawLine(x + w - 1, y, x + w - 1, y + h - 2);
        setColor(c);
    }

    public abstract void drawOval(int x, int y, int w, int h);
    public abstract void fillOval(int x, int y, int w, int h);
    public abstract void drawArc(int x, int y, int w, int h, int start, int arc);
    public abstract void fillArc(int x, int y, int w, int h, int start, int arc);
    public abstract void drawPolyline(int[] xs, int[] ys, int n);
    public abstract void drawPolygon(int[] xs, int[] ys, int n);

    public void drawPolygon(Polygon p) { drawPolygon(p.xpoints, p.ypoints, p.npoints); }

    public abstract void fillPolygon(int[] xs, int[] ys, int n);

    public void fillPolygon(Polygon p) { fillPolygon(p.xpoints, p.ypoints, p.npoints); }

    public abstract void drawString(String s, int x, int y);
    public abstract void drawString(java.text.AttributedCharacterIterator it, int x, int y);

    public void drawChars(char[] data, int off, int len, int x, int y) { drawString(new String(data, off, len), x, y); }

    public void drawBytes(byte[] data, int off, int len, int x, int y) {
        drawString(new String(data, 0, off, len), x, y);
    }

    public abstract boolean drawImage(Image img, int x, int y, ImageObserver o);
    public abstract boolean drawImage(Image img, int x, int y, int w, int h, ImageObserver o);
    public abstract boolean drawImage(Image img, int x, int y, Color bg, ImageObserver o);
    public abstract boolean drawImage(Image img, int x, int y, int w, int h, Color bg, ImageObserver o);
    public abstract boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, ImageObserver o);
    public abstract boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, Color bg, ImageObserver o);
    public abstract void dispose();

    @Deprecated public Rectangle getClipRect() { return getClipBounds(); }

    public boolean hitClip(int x, int y, int w, int h) {
        Rectangle c = getClipBounds();
        return c == null || c.intersects(new Rectangle(x, y, w, h));
    }

    public Rectangle getClipBounds(Rectangle r) {
        Rectangle c = getClipBounds();
        if (c != null && r != null) {
            r.setBounds(c);
            return r;
        }
        return c != null ? c : r;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[font=" + getFont() + ",color=" + getColor() + "]";
    }
}
