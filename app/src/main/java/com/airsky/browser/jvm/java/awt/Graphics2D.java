package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.java.awt.font.FontRenderContext;
import com.airsky.browser.jvm.java.awt.geom.AffineTransform;
import com.airsky.browser.jvm.java.awt.image.BufferedImage;
import com.airsky.browser.jvm.java.awt.image.BufferedImageOp;
import com.airsky.browser.jvm.java.awt.image.ImageObserver;

import java.util.Map;

/** java.awt.Graphics2D の互換クラス。 */
public abstract class Graphics2D extends Graphics {
    protected Graphics2D() {}

    public abstract void draw(Shape s);
    public abstract boolean drawImage(Image img, AffineTransform xform, ImageObserver obs);
    public abstract void drawImage(BufferedImage img, BufferedImageOp op, int x, int y);
    public abstract void drawString(String s, float x, float y);
    public abstract void drawString(java.text.AttributedCharacterIterator it, float x, float y);
    public abstract void fill(Shape s);

    public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
        return s.intersects(rect.x, rect.y, rect.width, rect.height);
    }

    public GraphicsConfiguration getDeviceConfiguration() { return GraphicsConfiguration.DEFAULT; }

    public abstract void setComposite(Composite comp);
    public abstract void setPaint(Paint paint);
    public abstract void setStroke(Stroke s);
    public abstract void setRenderingHint(RenderingHints.Key key, Object value);
    public abstract Object getRenderingHint(RenderingHints.Key key);
    public abstract void setRenderingHints(Map<?, ?> hints);
    public abstract void addRenderingHints(Map<?, ?> hints);
    public abstract RenderingHints getRenderingHints();
    public abstract void translate(double tx, double ty);
    public abstract void rotate(double theta);
    public abstract void rotate(double theta, double x, double y);
    public abstract void scale(double sx, double sy);
    public abstract void shear(double shx, double shy);
    public abstract void transform(AffineTransform tx);
    public abstract void setTransform(AffineTransform tx);
    public abstract AffineTransform getTransform();
    public abstract Paint getPaint();
    public abstract Composite getComposite();
    public abstract void setBackground(Color c);
    public abstract Color getBackground();
    public abstract Stroke getStroke();
    public abstract void clip(Shape s);
    public abstract FontRenderContext getFontRenderContext();
}
