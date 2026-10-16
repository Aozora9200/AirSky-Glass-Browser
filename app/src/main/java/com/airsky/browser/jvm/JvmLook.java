package com.airsky.browser.jvm;

import com.airsky.browser.Lang;

import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Font;
import com.airsky.browser.jvm.java.awt.FontMetrics;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Graphics2D;
import com.airsky.browser.jvm.java.awt.RenderingHints;

/** 互換レイヤーの部品の見た目(Pixel 風の明るい配色)。 */
public final class JvmLook {
    private JvmLook() {}

    public static final Color PANEL = new Color(0xF3, 0xF4, 0xF9);
    public static final Color SURFACE = new Color(0xFF, 0xFF, 0xFF);
    public static final Color TEXT = new Color(0x1F, 0x1F, 0x1F);
    public static final Color TEXT_VARIANT = new Color(0x44, 0x47, 0x46);
    public static final Color DISABLED = new Color(0x9A, 0x9C, 0xA0);
    public static final Color PRIMARY = new Color(0x0B, 0x57, 0xD0);
    public static final Color ON_PRIMARY = Color.WHITE;
    public static final Color TONAL = new Color(0xD3, 0xE3, 0xFD);
    public static final Color ON_TONAL = new Color(0x04, 0x1E, 0x49);
    public static final Color OUTLINE = new Color(0x74, 0x77, 0x75);
    public static final Color OUTLINE_VARIANT = new Color(0xC4, 0xC7, 0xC5);
    public static final Color PRESSED = new Color(0xA8, 0xC7, 0xFA);
    public static final Font FONT = new Font(Font.DIALOG, Font.PLAIN, 12);
    public static final Font BUTTON_FONT = new Font(Font.DIALOG, Font.BOLD, 12);

    public static void smooth(Graphics g) {
        if (g instanceof Graphics2D) {
            ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
    }

    /** 文字を左・中央・右にそろえて、縦は真ん中に描く。align: 0=左 1=中央 2=右。 */
    public static void text(Graphics g, String s, int x, int y, int w, int h, int align) {
        if (s == null || s.isEmpty()) return;
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(s);
        int tx = align == 1 ? x + (w - tw) / 2 : align == 2 ? x + w - tw : x;
        int ty = y + (h - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(s, tx, ty);
    }

    /** 表示する行。"<html>" で始まる文字は <br> などで改行し、タグを外す。 */
    public static String[] lines(String s) {
        if (s == null) return new String[]{""};
        if (!s.regionMatches(true, 0, "<html>", 0, 6)) return new String[]{s};
        String t = s.replaceAll("(?i)<br\\s*/?>|</p>|</div>|<p[^>]*>|</h[1-6]>|</li>", "\n")
                .replaceAll("(?i)<li[^>]*>", Lang.t("・"))
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
        t = t.replaceAll("\n{3,}", "\n\n").trim();
        return t.split("\n", -1);
    }

    /** 1 行で表示する文字(<html> はタグを外してつなぐ)。 */
    public static String plain(String s) {
        if (s == null) return "";
        if (!s.regionMatches(true, 0, "<html>", 0, 6)) return s;
        return String.join(" ", lines(s));
    }

    public static String ellipsize(FontMetrics fm, String s, int width) {
        if (s == null) return "";
        if (fm.stringWidth(s) <= width) return s;
        String dots = "…";
        int end = s.length();
        while (end > 0 && fm.stringWidth(s.substring(0, end) + dots) > width) end--;
        return s.substring(0, end) + dots;
    }
}
