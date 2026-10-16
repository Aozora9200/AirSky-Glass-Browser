package com.airsky.browser.jvm.javax.swing.border;

import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Insets;

public class CompoundBorder extends AbstractBorder {
    protected Border outsideBorder, insideBorder;

    public CompoundBorder() {}

    public CompoundBorder(Border outside, Border inside) {
        outsideBorder = outside;
        insideBorder = inside;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
        if (outsideBorder != null) {
            outsideBorder.paintBorder(c, g, x, y, w, h);
            Insets in = outsideBorder.getBorderInsets(c);
            x += in.left;
            y += in.top;
            w -= in.left + in.right;
            h -= in.top + in.bottom;
        }
        if (insideBorder != null) insideBorder.paintBorder(c, g, x, y, w, h);
    }

    @Override
    public Insets getBorderInsets(Component c) {
        Insets r = new Insets(0, 0, 0, 0);
        for (Border b : new Border[]{outsideBorder, insideBorder}) {
            if (b == null) continue;
            Insets i = b.getBorderInsets(c);
            r.top += i.top;
            r.left += i.left;
            r.bottom += i.bottom;
            r.right += i.right;
        }
        return r;
    }

    public Border getOutsideBorder() { return outsideBorder; }
    public Border getInsideBorder() { return insideBorder; }
}
