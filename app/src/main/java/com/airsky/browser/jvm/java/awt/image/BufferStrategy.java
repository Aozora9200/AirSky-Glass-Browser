package com.airsky.browser.jvm.java.awt.image;

import com.airsky.browser.jvm.java.awt.Graphics;

public abstract class BufferStrategy {
    public abstract Graphics getDrawGraphics();
    public abstract boolean contentsLost();
    public abstract boolean contentsRestored();
    public abstract void show();
    public void dispose() {}
}
