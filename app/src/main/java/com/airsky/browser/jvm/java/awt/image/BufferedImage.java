package com.airsky.browser.jvm.java.awt.image;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.airsky.browser.jvm.JvmGraphics;
import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Graphics2D;
import com.airsky.browser.jvm.java.awt.Image;
import com.airsky.browser.jvm.java.awt.Transparency;

/**
 * java.awt.image.BufferedImage の互換クラス(Android の Bitmap で持つ)。
 *
 * <p>getRaster() で画素の配列を渡した後は、配列を正として、描く時に Bitmap へ写す。</p>
 */
public class BufferedImage extends Image implements RenderedImage, Transparency {
    public static final int TYPE_CUSTOM = 0;
    public static final int TYPE_INT_RGB = 1;
    public static final int TYPE_INT_ARGB = 2;
    public static final int TYPE_INT_ARGB_PRE = 3;
    public static final int TYPE_INT_BGR = 4;
    public static final int TYPE_3BYTE_BGR = 5;
    public static final int TYPE_4BYTE_ABGR = 6;
    public static final int TYPE_4BYTE_ABGR_PRE = 7;
    public static final int TYPE_USHORT_565_RGB = 8;
    public static final int TYPE_USHORT_555_RGB = 9;
    public static final int TYPE_BYTE_GRAY = 10;
    public static final int TYPE_USHORT_GRAY = 11;
    public static final int TYPE_BYTE_BINARY = 12;
    public static final int TYPE_BYTE_INDEXED = 13;

    private final int type;
    private final int width, height;
    private final Bitmap bitmap;
    private int[] pixels;
    private boolean bitmapNewer;

    public BufferedImage(int width, int height, int imageType) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width (" + width + ") and height (" + height + ") cannot be <= 0");
        }
        this.type = imageType;
        this.width = width;
        this.height = height;
        this.bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        if (jvm$opaque()) bitmap.eraseColor(0xFF000000);
    }

    public BufferedImage(int width, int height, int imageType, Object colorModel) {
        this(width, height, imageType);
    }

    private BufferedImage(Bitmap b, int type) {
        this.type = type;
        this.width = b.getWidth();
        this.height = b.getHeight();
        this.bitmap = b.isMutable() && b.getConfig() == Bitmap.Config.ARGB_8888 ? b : b.copy(Bitmap.Config.ARGB_8888, true);
    }

    /** Android の Bitmap から作る(ImageIO など)。 */
    public static BufferedImage jvm$fromBitmap(Bitmap b) {
        return new BufferedImage(b, b.hasAlpha() ? TYPE_INT_ARGB : TYPE_INT_RGB);
    }

    public int getType() { return type; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public int getWidth(ImageObserver o) { return width; }
    @Override public int getHeight(ImageObserver o) { return height; }
    public int getMinX() { return 0; }
    public int getMinY() { return 0; }
    public boolean isAlphaPremultiplied() { return false; }
    public String[] getPropertyNames() { return null; }

    @Override
    public int getTransparency() { return jvm$opaque() ? OPAQUE : TRANSLUCENT; }

    public boolean jvm$opaque() {
        switch (type) {
            case TYPE_INT_ARGB: case TYPE_INT_ARGB_PRE: case TYPE_4BYTE_ABGR: case TYPE_4BYTE_ABGR_PRE: case TYPE_CUSTOM:
                return false;
            default:
                return true;
        }
    }

    private boolean gray() { return type == TYPE_BYTE_GRAY || type == TYPE_USHORT_GRAY || type == TYPE_BYTE_BINARY; }

    private int normalize(int argb) {
        if (gray()) {
            int g = (((argb >> 16) & 0xFF) * 77 + ((argb >> 8) & 0xFF) * 150 + (argb & 0xFF) * 29) >> 8;
            if (type == TYPE_BYTE_BINARY) g = g < 128 ? 0 : 255;
            return 0xFF000000 | (g << 16) | (g << 8) | g;
        }
        return jvm$opaque() ? argb | 0xFF000000 : argb;
    }

    @Override
    public Graphics getGraphics() { return createGraphics(); }

    public Graphics2D createGraphics() {
        syncToBitmap();
        if (pixels != null) bitmapNewer = true;
        JvmGraphics g = new JvmGraphics(new Canvas(bitmap), 1f);
        g.setColor(Color.WHITE);
        g.setBackground(Color.BLACK);
        return g;
    }

    public int getRGB(int x, int y) {
        check(x, y);
        if (pixels != null) {
            syncFromBitmap();
            return normalize(pixels[y * width + x]);
        }
        return bitmap.getPixel(x, y);
    }

    public void setRGB(int x, int y, int rgb) {
        check(x, y);
        if (pixels != null) {
            syncFromBitmap();
            pixels[y * width + x] = jvm$opaque() ? normalize(rgb) & 0xFFFFFF | (rgb & 0xFF000000) : rgb;
        } else {
            bitmap.setPixel(x, y, normalize(rgb));
        }
    }

    public int[] getRGB(int sx, int sy, int w, int h, int[] arr, int off, int scan) {
        if (arr == null) arr = new int[off + h * scan];
        if (pixels != null) {
            syncFromBitmap();
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) arr[off + j * scan + i] = normalize(pixels[(sy + j) * width + sx + i]);
            }
        } else {
            bitmap.getPixels(arr, off, scan, sx, sy, w, h);
        }
        return arr;
    }

    public void setRGB(int sx, int sy, int w, int h, int[] arr, int off, int scan) {
        if (pixels != null) {
            syncFromBitmap();
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) pixels[(sy + j) * width + sx + i] = arr[off + j * scan + i];
            }
        } else {
            int[] tmp = new int[w * h];
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) tmp[j * w + i] = normalize(arr[off + j * scan + i]);
            }
            bitmap.setPixels(tmp, 0, w, sx, sy, w, h);
        }
    }

    private void check(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new ArrayIndexOutOfBoundsException("Coordinate out of bounds!");
        }
    }

    public BufferedImage getSubimage(int x, int y, int w, int h) {
        syncToBitmap();
        Bitmap sub = Bitmap.createBitmap(bitmap, x, y, w, h);
        return new BufferedImage(sub.copy(Bitmap.Config.ARGB_8888, true), type);
    }

    public WritableRaster getRaster() {
        jvm$pixels();
        return new WritableRaster(this);
    }

    public Raster getData() { return getRaster(); }
    public WritableRaster getAlphaRaster() { return null; }

    public void setData(Raster r) {
        int[] p = jvm$pixels();
        int[] src = r.image.jvm$pixels();
        System.arraycopy(src, 0, p, 0, Math.min(p.length, src.length));
    }

    public Object getColorModel() { return null; }

    @Override
    public void flush() {}

    // ------------------------------------------------------------------
    // 互換レイヤーの内部
    // ------------------------------------------------------------------

    /** 画素の配列(なければ Bitmap から作る)。この配列が正になる。 */
    public int[] jvm$pixels() {
        if (pixels == null) {
            pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            if (jvm$opaque()) {
                for (int i = 0; i < pixels.length; i++) pixels[i] &= 0xFFFFFF;
            }
        } else {
            syncFromBitmap();
        }
        return pixels;
    }

    private void syncFromBitmap() {
        if (pixels != null && bitmapNewer) {
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            if (jvm$opaque()) {
                for (int i = 0; i < pixels.length; i++) pixels[i] &= 0xFFFFFF;
            }
            bitmapNewer = false;
        }
    }

    private void syncToBitmap() {
        if (pixels == null) return;
        syncFromBitmap();
        if (jvm$opaque() || gray()) {
            int[] tmp = new int[pixels.length];
            for (int i = 0; i < tmp.length; i++) tmp[i] = normalize(pixels[i]);
            bitmap.setPixels(tmp, 0, width, 0, 0, width, height);
        } else {
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        }
    }

    /** 描くための Bitmap(画素の配列を使っていれば写してから返す)。 */
    public Bitmap jvm$bitmap() {
        syncToBitmap();
        return bitmap;
    }

    @Override
    public String toString() {
        return "BufferedImage@" + Integer.toHexString(hashCode()) + ": type = " + type + " width = " + width + " height = " + height;
    }
}
