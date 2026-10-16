package com.airsky.browser.jvm.javax.swing.plaf;

import com.airsky.browser.jvm.java.awt.Color;

public class ColorUIResource extends Color {
    public ColorUIResource(int r, int g, int b) { super(r, g, b); }
    public ColorUIResource(int rgb) { super(rgb); }
    public ColorUIResource(float r, float g, float b) { super(r, g, b); }
    public ColorUIResource(Color c) { super(c.getRGB(), true); }
}
