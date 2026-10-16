package com.airsky.browser.jvm;

import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Window;
import com.airsky.browser.jvm.java.awt.image.BufferStrategy;
import com.airsky.browser.jvm.java.awt.image.BufferedImage;

/** Canvas / Window の createBufferStrategy。描いて show() すると画面に出る。 */
public final class JvmBufferStrategy extends BufferStrategy {
    private final Component component;

    public JvmBufferStrategy(Component c, Window w) {
        component = c;
    }

    @Override
    public Graphics getDrawGraphics() {
        Window w = component.jvm$window();
        Graphics g = w != null && w.jvm$isShown() ? w.jvm$strategyGraphics(component) : null;
        if (g != null) return g;
        return new BufferedImage(Math.max(1, component.getWidth()), Math.max(1, component.getHeight()),
                BufferedImage.TYPE_INT_ARGB).createGraphics();
    }

    @Override public boolean contentsLost() { return false; }
    @Override public boolean contentsRestored() { return false; }

    @Override
    public void show() {
        Window w = component.jvm$window();
        if (w != null && w.jvm$isShown()) w.jvm$publish();
    }
}
