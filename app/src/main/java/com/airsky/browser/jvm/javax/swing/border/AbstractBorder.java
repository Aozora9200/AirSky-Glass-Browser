package com.airsky.browser.jvm.javax.swing.border;

import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Insets;

public abstract class AbstractBorder implements Border, java.io.Serializable {
    @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {}
    @Override public Insets getBorderInsets(Component c) { return new Insets(0, 0, 0, 0); }

    public Insets getBorderInsets(Component c, Insets in) {
        Insets i = getBorderInsets(c);
        in.set(i.top, i.left, i.bottom, i.right);
        return in;
    }

    @Override public boolean isBorderOpaque() { return false; }
}
