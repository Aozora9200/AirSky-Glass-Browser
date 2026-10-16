package com.airsky.browser.jvm.javax.swing.border;

import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Insets;

public class BevelBorder extends AbstractBorder {
    public static final int RAISED = 0;
    public static final int LOWERED = 1;
    protected int bevelType;

    public BevelBorder(int type) { bevelType = type; }
    public BevelBorder(int type, Color highlight, Color shadow) { bevelType = type; }
    public BevelBorder(int type, Color ho, Color hi, Color so, Color si) { bevelType = type; }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
        Color old = g.getColor();
        g.setColor(new Color(0xC4, 0xC7, 0xC5));
        g.draw3DRect(x, y, w - 1, h - 1, bevelType == RAISED);
        g.setColor(old);
    }

    @Override public Insets getBorderInsets(Component c) { return new Insets(2, 2, 2, 2); }
    public int getBevelType() { return bevelType; }
}
