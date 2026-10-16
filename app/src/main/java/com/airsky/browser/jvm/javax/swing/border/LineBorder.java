package com.airsky.browser.jvm.javax.swing.border;

import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Insets;

public class LineBorder extends AbstractBorder {
    protected int thickness;
    protected Color lineColor;
    protected boolean roundedCorners;

    public LineBorder(Color c) { this(c, 1, false); }
    public LineBorder(Color c, int thickness) { this(c, thickness, false); }

    public LineBorder(Color c, int thickness, boolean rounded) {
        this.lineColor = c;
        this.thickness = thickness;
        this.roundedCorners = rounded;
    }

    public static Border createBlackLineBorder() { return new LineBorder(Color.BLACK, 1); }
    public static Border createGrayLineBorder() { return new LineBorder(Color.GRAY, 1); }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
        Color old = g.getColor();
        g.setColor(lineColor);
        for (int i = 0; i < thickness; i++) {
            if (roundedCorners) g.drawRoundRect(x + i, y + i, w - i - i - 1, h - i - i - 1, thickness * 2, thickness * 2);
            else g.drawRect(x + i, y + i, w - i - i - 1, h - i - i - 1);
        }
        g.setColor(old);
    }

    @Override public Insets getBorderInsets(Component c) { return new Insets(thickness, thickness, thickness, thickness); }
    public Color getLineColor() { return lineColor; }
    public int getThickness() { return thickness; }
    public boolean getRoundedCorners() { return roundedCorners; }
    @Override public boolean isBorderOpaque() { return !roundedCorners; }
}
