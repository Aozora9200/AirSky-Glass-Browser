package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.font.FontRenderContext;
import com.airsky.browser.jvm.java.awt.geom.Rectangle2D;

/** java.awt.Font の互換クラス。Font.createFont で読んだ書体はそのまま使う。 */
public class Font implements java.io.Serializable {
    public static final String DIALOG = "Dialog";
    public static final String DIALOG_INPUT = "DialogInput";
    public static final String SANS_SERIF = "SansSerif";
    public static final String SERIF = "Serif";
    public static final String MONOSPACED = "Monospaced";
    public static final int PLAIN = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int ROMAN_BASELINE = 0;
    public static final int CENTER_BASELINE = 1;
    public static final int HANGING_BASELINE = 2;
    public static final int TRUETYPE_FONT = 0;
    public static final int TYPE1_FONT = 1;
    public static final int LAYOUT_LEFT_TO_RIGHT = 0;
    public static final int LAYOUT_RIGHT_TO_LEFT = 1;
    public static final int LAYOUT_NO_START_CONTEXT = 2;
    public static final int LAYOUT_NO_LIMIT_CONTEXT = 4;

    protected String name;
    protected int style;
    protected int size;
    protected float pointSize;
    private android.graphics.Typeface custom;

    public Font(String name, int style, int size) {
        this.name = name != null ? name : DIALOG;
        this.style = (style & ~0x03) == 0 ? style : 0;
        this.size = size;
        this.pointSize = size;
    }

    private Font(String name, int style, float size, android.graphics.Typeface custom) {
        this.name = name;
        this.style = style;
        this.size = (int) (size + 0.5f);
        this.pointSize = size;
        this.custom = custom;
    }

    protected Font(Font f) {
        this(f.name, f.style, f.pointSize, f.custom);
    }

    public Font(java.util.Map<?, ?> attributes) {
        this(DIALOG, PLAIN, 12);
    }

    public String getName() { return name; }
    public String getFontName() { return name; }
    public String getFontName(java.util.Locale l) { return name; }
    public String getFamily() { return name; }
    public String getFamily(java.util.Locale l) { return name; }
    public String getPSName() { return name; }
    public int getStyle() { return style; }
    public int getSize() { return size; }
    public float getSize2D() { return pointSize; }
    public boolean isPlain() { return style == 0; }
    public boolean isBold() { return (style & BOLD) != 0; }
    public boolean isItalic() { return (style & ITALIC) != 0; }
    public boolean isTransformed() { return false; }
    public int getNumGlyphs() { return 65536; }
    public boolean canDisplay(char c) { return true; }
    public boolean canDisplay(int cp) { return true; }
    public int canDisplayUpTo(String s) { return -1; }

    public Font deriveFont(float size) { return new Font(name, style, size, custom); }
    public Font deriveFont(int style) { return new Font(name, style, pointSize, custom); }
    public Font deriveFont(int style, float size) { return new Font(name, style, size, custom); }
    public Font deriveFont(com.airsky.browser.jvm.java.awt.geom.AffineTransform t) { return this; }
    public Font deriveFont(java.util.Map<?, ?> attributes) { return this; }

    public Rectangle2D getStringBounds(String s, FontRenderContext frc) {
        FontMetrics fm = com.airsky.browser.jvm.JvmFonts.metrics(this);
        return new Rectangle2D.Float(0, -fm.getAscent(), fm.stringWidth(s), fm.getHeight());
    }

    public Rectangle2D getStringBounds(char[] chars, int begin, int limit, FontRenderContext frc) {
        return getStringBounds(new String(chars, begin, limit - begin), frc);
    }

    public Rectangle2D getMaxCharBounds(FontRenderContext frc) {
        FontMetrics fm = com.airsky.browser.jvm.JvmFonts.metrics(this);
        return new Rectangle2D.Float(0, -fm.getAscent(), fm.getMaxAdvance(), fm.getHeight());
    }

    public com.airsky.browser.jvm.java.awt.font.LineMetrics getLineMetrics(String s, FontRenderContext frc) {
        return new com.airsky.browser.jvm.java.awt.font.LineMetrics(com.airsky.browser.jvm.JvmFonts.metrics(this));
    }

    public static Font decode(String str) {
        if (str == null) return new Font(DIALOG, PLAIN, 12);
        String[] parts = str.split("[- ]");
        String n = parts[0];
        int st = PLAIN, sz = 12;
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].toLowerCase(java.util.Locale.ROOT);
            if (p.equals("bold")) st |= BOLD;
            else if (p.equals("italic")) st |= ITALIC;
            else if (p.equals("bolditalic")) st = BOLD | ITALIC;
            else {
                try {
                    sz = Integer.parseInt(p);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return new Font(n, st, sz);
    }

    public static Font getFont(String nm) { return null; }
    public static Font getFont(String nm, Font font) { return font; }

    public static Font createFont(int format, java.io.InputStream in) throws FontFormatException, java.io.IOException {
        java.io.File dir = JvmRuntime.host() != null ? JvmRuntime.host().context().getCacheDir()
                : new java.io.File(System.getProperty("java.io.tmpdir", "/data/local/tmp"));
        java.io.File f = java.io.File.createTempFile("font", ".ttf", dir);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return createFont(format, f);
    }

    public static Font createFont(int format, java.io.File file) throws FontFormatException, java.io.IOException {
        try {
            android.graphics.Typeface t = android.graphics.Typeface.createFromFile(file);
            return new Font(file.getName(), PLAIN, 1f, t);
        } catch (RuntimeException e) {
            throw new FontFormatException(e.getMessage());
        }
    }

    public android.graphics.Typeface jvm$typeface() { return custom; }

    @Override
    public int hashCode() {
        return name.hashCode() ^ style ^ Float.floatToIntBits(pointSize) ^ (custom != null ? custom.hashCode() : 0);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Font)) return false;
        Font f = (Font) o;
        return f.name.equals(name) && f.style == style && f.pointSize == pointSize && f.custom == custom;
    }

    @Override
    public String toString() {
        String s = isBold() ? (isItalic() ? "bolditalic" : "bold") : (isItalic() ? "italic" : "plain");
        return getClass().getName() + "[family=" + name + ",name=" + name + ",style=" + s + ",size=" + size + "]";
    }
}
