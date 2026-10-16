package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.java.awt.image.BufferedImage;

public abstract class GraphicsEnvironment {
    private static final GraphicsEnvironment LOCAL = new GraphicsEnvironment() {};

    protected GraphicsEnvironment() {}

    public static GraphicsEnvironment getLocalGraphicsEnvironment() { return LOCAL; }
    public static boolean isHeadless() { return false; }
    public boolean isHeadlessInstance() { return false; }
    public GraphicsDevice getDefaultScreenDevice() { return GraphicsDevice.DEFAULT; }
    public GraphicsDevice[] getScreenDevices() { return new GraphicsDevice[]{GraphicsDevice.DEFAULT}; }
    public String[] getAvailableFontFamilyNames() {
        return new String[]{Font.DIALOG, Font.DIALOG_INPUT, Font.MONOSPACED, Font.SANS_SERIF, Font.SERIF};
    }
    public String[] getAvailableFontFamilyNames(java.util.Locale l) { return getAvailableFontFamilyNames(); }
    public Font[] getAllFonts() {
        String[] n = getAvailableFontFamilyNames();
        Font[] f = new Font[n.length];
        for (int i = 0; i < n.length; i++) f[i] = new Font(n[i], Font.PLAIN, 1);
        return f;
    }
    public boolean registerFont(Font f) { return true; }
    public Graphics2D createGraphics(BufferedImage img) { return img.createGraphics(); }
    public Rectangle getMaximumWindowBounds() { return GraphicsConfiguration.DEFAULT.getBounds(); }
    public Point getCenterPoint() {
        Rectangle r = getMaximumWindowBounds();
        return new Point(r.width / 2, r.height / 2);
    }
}
