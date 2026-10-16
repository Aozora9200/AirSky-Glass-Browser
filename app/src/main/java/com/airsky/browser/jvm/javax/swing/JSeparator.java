package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.Graphics;

/** javax.swing.JSeparator の互換クラス。 */
public class JSeparator extends JComponent implements SwingConstants {
    private int orientation;

    public JSeparator() { this(HORIZONTAL); }

    public JSeparator(int orientation) {
        this.orientation = orientation;
        setFocusable(false);
    }

    public int getOrientation() { return orientation; }
    public void setOrientation(int o) { orientation = o; revalidate(); }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        return orientation == VERTICAL ? new Dimension(9, 1) : new Dimension(1, 9);
    }

    @Override
    public Dimension getMaximumSize() {
        if (isMaximumSizeSet()) return super.getMaximumSize();
        return orientation == VERTICAL ? new Dimension(9, Short.MAX_VALUE) : new Dimension(Short.MAX_VALUE, 9);
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(isForegroundSet() ? getForeground() : JvmLook.OUTLINE_VARIANT);
        if (orientation == VERTICAL) g.fillRect(getWidth() / 2, 0, 1, getHeight());
        else g.fillRect(0, getHeight() / 2, getWidth(), 1);
    }
}
