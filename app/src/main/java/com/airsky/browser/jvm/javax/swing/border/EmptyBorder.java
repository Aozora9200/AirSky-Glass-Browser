package com.airsky.browser.jvm.javax.swing.border;

import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Insets;

public class EmptyBorder extends AbstractBorder {
    protected int left, right, top, bottom;

    public EmptyBorder(int top, int left, int bottom, int right) {
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
    }

    public EmptyBorder(Insets in) { this(in.top, in.left, in.bottom, in.right); }

    @Override public Insets getBorderInsets(Component c) { return new Insets(top, left, bottom, right); }
    public Insets getBorderInsets() { return new Insets(top, left, bottom, right); }
}
