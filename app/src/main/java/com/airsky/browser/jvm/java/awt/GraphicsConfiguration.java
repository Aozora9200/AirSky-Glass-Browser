package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.image.BufferedImage;

public class GraphicsConfiguration {
    public static final GraphicsConfiguration DEFAULT = new GraphicsConfiguration();

    protected GraphicsConfiguration() {}

    public Rectangle getBounds() { return new Rectangle(0, 0, JvmRuntime.screenWidth(), JvmRuntime.screenHeight()); }
    public BufferedImage createCompatibleImage(int w, int h) { return new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB); }

    public BufferedImage createCompatibleImage(int w, int h, int transparency) {
        return new BufferedImage(w, h, transparency == Transparency.OPAQUE
                ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
    }

    public GraphicsDevice getDevice() { return GraphicsDevice.DEFAULT; }
}
