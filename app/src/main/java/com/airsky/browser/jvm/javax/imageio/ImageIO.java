package com.airsky.browser.jvm.javax.imageio;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.airsky.browser.jvm.java.awt.image.BufferedImage;
import com.airsky.browser.jvm.java.awt.image.RenderedImage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Locale;

/** javax.imageio.ImageIO の互換クラス(PNG / JPEG / GIF / BMP / WebP を読み、PNG / JPEG / WebP を書く)。 */
public final class ImageIO {
    private ImageIO() {}

    private static BufferedImage decode(InputStream in) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inMutable = true;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        o.inScaled = false;
        Bitmap b = BitmapFactory.decodeStream(in, null, o);
        return b != null ? BufferedImage.jvm$fromBitmap(b) : null;
    }

    public static BufferedImage read(File f) throws IOException {
        if (f == null) throw new IllegalArgumentException("input == null!");
        if (!f.canRead()) throw new IIOException("Can't read input file!");
        try (InputStream in = new java.io.FileInputStream(f)) {
            return decode(new java.io.BufferedInputStream(in));
        }
    }

    public static BufferedImage read(InputStream in) throws IOException {
        if (in == null) throw new IllegalArgumentException("input == null!");
        return decode(in);
    }

    public static BufferedImage read(URL url) throws IOException {
        if (url == null) throw new IllegalArgumentException("input == null!");
        try (InputStream in = url.openStream()) {
            return decode(new java.io.BufferedInputStream(in));
        } catch (IOException e) {
            throw new IIOException("Can't get input stream from URL!", e);
        }
    }

    private static Bitmap.CompressFormat format(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("png")) return Bitmap.CompressFormat.PNG;
        if (n.equals("jpg") || n.equals("jpeg")) return Bitmap.CompressFormat.JPEG;
        if (n.equals("webp")) return Bitmap.CompressFormat.WEBP;
        return null;
    }

    public static boolean write(RenderedImage img, String formatName, OutputStream out) throws IOException {
        if (img == null || formatName == null || out == null) throw new IllegalArgumentException("null argument");
        Bitmap.CompressFormat f = format(formatName);
        if (f == null || !(img instanceof BufferedImage)) return false;
        return ((BufferedImage) img).jvm$bitmap().compress(f, 92, out);
    }

    public static boolean write(RenderedImage img, String formatName, File file) throws IOException {
        if (file == null) throw new IllegalArgumentException("output == null!");
        try (OutputStream out = new FileOutputStream(file)) {
            return write(img, formatName, out);
        }
    }

    public static String[] getReaderFormatNames() { return new String[]{"png", "jpg", "jpeg", "gif", "bmp", "webp"}; }
    public static String[] getWriterFormatNames() { return new String[]{"png", "jpg", "jpeg", "webp"}; }
    public static String[] getReaderFileSuffixes() { return getReaderFormatNames(); }
    public static String[] getWriterFileSuffixes() { return getWriterFormatNames(); }
    public static void setUseCache(boolean b) {}
    public static boolean getUseCache() { return false; }
}
