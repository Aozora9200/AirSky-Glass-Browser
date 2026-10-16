package com.airsky.browser.jvm.javax.swing.border;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Font;
import com.airsky.browser.jvm.java.awt.FontMetrics;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Insets;

/** 題名付きの枠(枠の左上に題名を描く)。 */
public class TitledBorder extends AbstractBorder {
    public static final int DEFAULT_POSITION = 0;
    public static final int ABOVE_TOP = 1;
    public static final int TOP = 2;
    public static final int BELOW_TOP = 3;
    public static final int ABOVE_BOTTOM = 4;
    public static final int BOTTOM = 5;
    public static final int BELOW_BOTTOM = 6;
    public static final int DEFAULT_JUSTIFICATION = 0;
    public static final int LEFT = 1;
    public static final int CENTER = 2;
    public static final int RIGHT = 3;
    public static final int LEADING = 4;
    public static final int TRAILING = 5;

    protected String title;
    protected Border border;
    protected int titleJustification, titlePosition;
    protected Font titleFont;
    protected Color titleColor;

    public TitledBorder(String title) { this(null, title, LEADING, DEFAULT_POSITION, null, null); }
    public TitledBorder(Border border) { this(border, "", LEADING, DEFAULT_POSITION, null, null); }
    public TitledBorder(Border border, String title) { this(border, title, LEADING, DEFAULT_POSITION, null, null); }
    public TitledBorder(Border border, String title, int j, int p) { this(border, title, j, p, null, null); }
    public TitledBorder(Border border, String title, int j, int p, Font f) { this(border, title, j, p, f, null); }

    public TitledBorder(Border border, String title, int j, int p, Font f, Color c) {
        this.border = border;
        this.title = title;
        this.titleJustification = j;
        this.titlePosition = p;
        this.titleFont = f;
        this.titleColor = c;
    }

    public String getTitle() { return title; }
    public void setTitle(String t) { title = t; }
    public Border getBorder() { return border; }
    public void setBorder(Border b) { border = b; }
    public void setTitleJustification(int j) { titleJustification = j; }
    public void setTitlePosition(int p) { titlePosition = p; }
    public void setTitleFont(Font f) { titleFont = f; }
    public Font getTitleFont() { return titleFont; }
    public void setTitleColor(Color c) { titleColor = c; }
    public Color getTitleColor() { return titleColor; }

    private Font font(Component c) {
        return titleFont != null ? titleFont : c.getFont().deriveFont(Font.BOLD);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
        FontMetrics fm = c.getFontMetrics(font(c));
        int top = y + fm.getHeight() / 2;
        Color old = g.getColor();
        if (border != null) {
            border.paintBorder(c, g, x, top, w, h - (top - y));
        } else {
            g.setColor(JvmLook.OUTLINE_VARIANT);
            g.drawRoundRect(x, top, w - 1, h - (top - y) - 1, 12, 12);
        }
        if (title != null && !title.isEmpty()) {
            int tw = fm.stringWidth(title);
            int tx = titleJustification == CENTER ? x + (w - tw) / 2
                    : (titleJustification == RIGHT || titleJustification == TRAILING) ? x + w - tw - 12 : x + 12;
            Color bg = c.getBackground();
            g.setColor(bg != null ? bg : JvmLook.PANEL);
            g.fillRect(tx - 4, y, tw + 8, fm.getHeight());
            g.setColor(titleColor != null ? titleColor : JvmLook.PRIMARY);
            g.setFont(font(c));
            g.drawString(title, tx, y + fm.getAscent());
        }
        g.setColor(old);
    }

    @Override
    public Insets getBorderInsets(Component c) {
        FontMetrics fm = c.getFontMetrics(font(c));
        Insets in = border != null ? border.getBorderInsets(c) : new Insets(1, 1, 1, 1);
        return new Insets(fm.getHeight() + in.top + 2, in.left + 6, in.bottom + 6, in.right + 6);
    }
}
