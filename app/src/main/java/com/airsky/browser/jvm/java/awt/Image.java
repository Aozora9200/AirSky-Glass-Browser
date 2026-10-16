package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.java.awt.image.BufferedImage;
import com.airsky.browser.jvm.java.awt.image.ImageObserver;

/** java.awt.Image の互換クラス。 */
public abstract class Image {
    public static final Object UndefinedProperty = new Object();
    public static final int SCALE_DEFAULT = 1;
    public static final int SCALE_FAST = 2;
    public static final int SCALE_SMOOTH = 4;
    public static final int SCALE_REPLICATE = 8;
    public static final int SCALE_AREA_AVERAGING = 16;

    protected Image() {}

    public abstract int getWidth(ImageObserver o);
    public abstract int getHeight(ImageObserver o);
    public abstract Graphics getGraphics();

    public Object getProperty(String name, ImageObserver o) { return UndefinedProperty; }

    public Image getScaledInstance(int w, int h, int hints) {
        int sw = getWidth(null), sh = getHeight(null);
        if (w < 0 && h < 0) {
            w = sw;
            h = sh;
        } else if (w < 0) {
            w = Math.max(1, sw * h / Math.max(1, sh));
        } else if (h < 0) {
            h = Math.max(1, sh * w / Math.max(1, sw));
        }
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics g = out.getGraphics();
        g.drawImage(this, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    public void flush() {}
    public void setAccelerationPriority(float p) {}
    public float getAccelerationPriority() { return 0.5f; }
}
