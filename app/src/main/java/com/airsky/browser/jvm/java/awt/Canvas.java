package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.java.awt.image.BufferStrategy;

/** java.awt.Canvas の互換クラス。 */
public class Canvas extends Component {
    private BufferStrategy strategy;

    public Canvas() {
        heavyweight = true;
    }

    public Canvas(GraphicsConfiguration gc) { this(); }

    @Override
    public void paint(Graphics g) {
        Color bg = getBackground();
        if (bg != null) {
            g.setColor(bg);
            g.fillRect(0, 0, width, height);
        }
    }

    @Override
    public void update(Graphics g) {
        Color bg = getBackground();
        if (bg != null) {
            Color c = g.getColor();
            g.setColor(bg);
            g.fillRect(0, 0, width, height);
            g.setColor(c);
        }
        paint(g);
    }

    public void createBufferStrategy(int n) {
        strategy = new com.airsky.browser.jvm.JvmBufferStrategy(this, null);
    }

    public void createBufferStrategy(int n, BufferCapabilities caps) {
        createBufferStrategy(n);
    }

    public BufferStrategy getBufferStrategy() { return strategy; }

    @Override
    public boolean jvm$wantsKeyboardFocus() { return jvm$hasKeyListeners(); }
}
