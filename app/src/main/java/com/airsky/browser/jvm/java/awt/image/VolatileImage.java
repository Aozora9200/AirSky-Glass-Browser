package com.airsky.browser.jvm.java.awt.image;

import com.airsky.browser.jvm.java.awt.GraphicsConfiguration;

/** java.awt.image.VolatileImage(内容は消えないので、いつも IMAGE_OK)。 */
public class VolatileImage extends BufferedImage {
    public static final int IMAGE_OK = 0;
    public static final int IMAGE_RESTORED = 1;
    public static final int IMAGE_INCOMPATIBLE = 2;

    public VolatileImage(int w, int h) { super(w, h, TYPE_INT_ARGB); }

    public BufferedImage getSnapshot() { return this; }
    public int validate(GraphicsConfiguration gc) { return IMAGE_OK; }
    public boolean contentsLost() { return false; }
}
