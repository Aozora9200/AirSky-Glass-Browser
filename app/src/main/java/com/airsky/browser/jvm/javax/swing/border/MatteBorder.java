package com.airsky.browser.jvm.javax.swing.border;

import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Graphics;

public class MatteBorder extends EmptyBorder {
    protected Color color;

    public MatteBorder(int top, int left, int bottom, int right, Color c) {
        super(top, left, bottom, right);
        color = c;
    }

    public MatteBorder(int top, int left, int bottom, int right, Object tileIcon) {
        this(top, left, bottom, right, Color.GRAY);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
        Color old = g.getColor();
        g.setColor(color);
        g.fillRect(x, y, w, top);
        g.fillRect(x, y + h - bottom, w, bottom);
        g.fillRect(x, y, left, h);
        g.fillRect(x + w - right, y, right, h);
        g.setColor(old);
    }

    public Color getMatteColor() { return color; }
}
