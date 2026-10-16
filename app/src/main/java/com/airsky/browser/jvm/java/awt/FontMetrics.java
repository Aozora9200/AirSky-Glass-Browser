package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.JvmFonts;
import com.airsky.browser.jvm.java.awt.geom.Rectangle2D;

/** java.awt.FontMetrics の互換クラス(Android の Paint で測る)。 */
public abstract class FontMetrics implements java.io.Serializable {
    protected Font font;
    private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final int ascent, descent, leading;

    protected FontMetrics(Font font) {
        this.font = font;
        JvmFonts.apply(paint, font);
        android.graphics.Paint.FontMetrics m = paint.getFontMetrics();
        ascent = (int) Math.ceil(-m.ascent);
        descent = (int) Math.ceil(m.descent);
        leading = Math.max(0, (int) Math.ceil(m.leading));
    }

    public Font getFont() { return font; }
    public int getLeading() { return leading; }
    public int getAscent() { return ascent; }
    public int getDescent() { return descent; }
    public int getHeight() { return ascent + descent + leading; }
    public int getMaxAscent() { return ascent; }
    public int getMaxDescent() { return descent; }
    @Deprecated public int getMaxDecent() { return descent; }

    public synchronized int getMaxAdvance() { return (int) Math.ceil(paint.measureText("W")); }
    public synchronized int charWidth(int ch) { return (int) Math.ceil(paint.measureText(new String(Character.toChars(ch)))); }
    public synchronized int charWidth(char ch) { return (int) Math.ceil(paint.measureText(String.valueOf(ch))); }

    public synchronized int stringWidth(String s) {
        if (s == null || s.isEmpty()) return 0;
        return (int) Math.ceil(paint.measureText(s));
    }

    public synchronized int charsWidth(char[] data, int off, int len) {
        return (int) Math.ceil(paint.measureText(data, off, len));
    }

    public int bytesWidth(byte[] data, int off, int len) {
        return stringWidth(new String(data, 0, off, len));
    }

    public int[] getWidths() {
        int[] w = new int[256];
        for (char c = 0; c < 256; c++) w[c] = charWidth(c);
        return w;
    }

    public Rectangle2D getStringBounds(String s, Graphics g) {
        return new Rectangle2D.Float(0, -ascent, stringWidth(s), getHeight());
    }

    public Rectangle2D getStringBounds(String s, int begin, int end, Graphics g) {
        return getStringBounds(s.substring(begin, end), g);
    }

    public Rectangle2D getStringBounds(char[] chars, int begin, int limit, Graphics g) {
        return getStringBounds(new String(chars, begin, limit - begin), g);
    }

    public Rectangle2D getMaxCharBounds(Graphics g) {
        return new Rectangle2D.Float(0, -ascent, getMaxAdvance(), getHeight());
    }

    public com.airsky.browser.jvm.java.awt.font.LineMetrics getLineMetrics(String s, Graphics g) {
        return new com.airsky.browser.jvm.java.awt.font.LineMetrics(this);
    }

    public boolean hasUniformLineMetrics() { return false; }

    @Override
    public String toString() {
        return getClass().getName() + "[font=" + font + "ascent=" + ascent + ", descent=" + descent + ", height=" + getHeight() + "]";
    }
}
