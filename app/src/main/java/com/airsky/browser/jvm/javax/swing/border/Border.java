package com.airsky.browser.jvm.javax.swing.border;

import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Insets;

public interface Border {
    void paintBorder(Component c, Graphics g, int x, int y, int width, int height);
    Insets getBorderInsets(Component c);
    boolean isBorderOpaque();
}
