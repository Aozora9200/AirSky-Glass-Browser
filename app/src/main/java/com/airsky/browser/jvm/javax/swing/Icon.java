package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Graphics;

public interface Icon {
    void paintIcon(Component c, Graphics g, int x, int y);
    int getIconWidth();
    int getIconHeight();
}
