package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.JvmFonts;
import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.image.BufferedImage;
import com.airsky.browser.jvm.javax.imageio.ImageIO;

/** java.awt.Toolkit の互換クラス。 */
public abstract class Toolkit {
    private static final Toolkit DEFAULT = new Toolkit() {};
    private final EventQueue queue = new EventQueue();

    protected Toolkit() {}

    public static Toolkit getDefaultToolkit() { return DEFAULT; }

    public Dimension getScreenSize() { return new Dimension(JvmRuntime.screenWidth(), JvmRuntime.screenHeight()); }
    public int getScreenResolution() { return Math.round(160 * JvmRuntime.density() / 2f); }
    public Insets getScreenInsets(GraphicsConfiguration gc) { return new Insets(0, 0, 0, 0); }
    public void beep() { if (JvmRuntime.host() != null) JvmRuntime.host().beep(); }
    public void sync() {}
    public EventQueue getSystemEventQueue() { return queue; }
    public FontMetrics getFontMetrics(Font f) { return JvmFonts.metrics(f); }
    public String[] getFontList() { return GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(); }
    public boolean isFrameStateSupported(int state) { return true; }
    public int getMenuShortcutKeyMask() { return 2; }
    public int getMenuShortcutKeyMaskEx() { return 128; }
    public boolean getLockingKeyState(int key) { return false; }
    public com.airsky.browser.jvm.java.awt.datatransfer.Clipboard getSystemClipboard() {
        return com.airsky.browser.jvm.java.awt.datatransfer.Clipboard.SYSTEM;
    }

    public Image getImage(String file) { return load(() -> ImageIO.read(new java.io.File(file))); }
    public Image getImage(java.net.URL url) { return load(() -> ImageIO.read(url)); }
    public Image createImage(String file) { return getImage(file); }
    public Image createImage(java.net.URL url) { return getImage(url); }
    public Image createImage(byte[] data) { return load(() -> ImageIO.read(new java.io.ByteArrayInputStream(data))); }
    public Image createImage(byte[] data, int off, int len) {
        return load(() -> ImageIO.read(new java.io.ByteArrayInputStream(data, off, len)));
    }

    public boolean prepareImage(Image img, int w, int h, com.airsky.browser.jvm.java.awt.image.ImageObserver o) { return true; }
    public int checkImage(Image img, int w, int h, com.airsky.browser.jvm.java.awt.image.ImageObserver o) {
        return com.airsky.browser.jvm.java.awt.image.ImageObserver.ALLBITS;
    }

    public Cursor createCustomCursor(Image img, Point hotSpot, String name) { return new Cursor(Cursor.DEFAULT_CURSOR); }
    public Dimension getBestCursorSize(int w, int h) { return new Dimension(w, h); }

    private interface Loader {
        BufferedImage load() throws Exception;
    }

    private static Image load(Loader l) {
        try {
            BufferedImage img = l.load();
            return img != null ? img : new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        } catch (Exception e) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
    }
}
