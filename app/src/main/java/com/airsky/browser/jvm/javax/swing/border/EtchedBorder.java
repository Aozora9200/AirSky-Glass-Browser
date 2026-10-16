package com.airsky.browser.jvm.javax.swing.border;

import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Insets;

public class EtchedBorder extends AbstractBorder {
    public static final int RAISED = 0;
    public static final int LOWERED = 1;
    protected int etchType;
    protected Color highlight, shadow;

    public EtchedBorder() { this(LOWERED, null, null); }
    public EtchedBorder(int type) { this(type, null, null); }
    public EtchedBorder(Color h, Color s) { this(LOWERED, h, s); }

    public EtchedBorder(int type, Color h, Color s) {
        etchType = type;
        highlight = h;
        shadow = s;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
        Color old = g.getColor();
        g.setColor(shadow != null ? shadow : new Color(0xC4, 0xC7, 0xC5));
        g.drawRoundRect(x, y, w - 1, h - 1, 8, 8);
        g.setColor(old);
    }

    @Override public Insets getBorderInsets(Component c) { return new Insets(2, 2, 2, 2); }
}
