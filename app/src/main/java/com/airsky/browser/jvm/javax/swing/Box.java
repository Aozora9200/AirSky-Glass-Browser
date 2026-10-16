package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.AWTError;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.LayoutManager;

/** javax.swing.Box の互換クラス。 */
public class Box extends JComponent {

    public static class Filler extends JComponent {
        private Dimension min, pref, max;

        public Filler(Dimension min, Dimension pref, Dimension max) {
            this.min = min;
            this.pref = pref;
            this.max = max;
            setFocusable(false);
        }

        public void changeShape(Dimension min, Dimension pref, Dimension max) {
            this.min = min;
            this.pref = pref;
            this.max = max;
            revalidate();
        }

        @Override public Dimension getMinimumSize() { return new Dimension(min); }
        @Override public Dimension getPreferredSize() { return new Dimension(pref); }
        @Override public Dimension getMaximumSize() { return new Dimension(max); }
    }

    public Box(int axis) {
        super.setLayout(new BoxLayout(this, axis));
    }

    @Override
    public void setLayout(LayoutManager l) {
        throw new AWTError("Illegal request");
    }

    public static Box createHorizontalBox() { return new Box(BoxLayout.X_AXIS); }
    public static Box createVerticalBox() { return new Box(BoxLayout.Y_AXIS); }

    public static Component createRigidArea(Dimension d) { return new Filler(d, d, d); }

    public static Component createHorizontalStrut(int w) {
        return new Filler(new Dimension(w, 0), new Dimension(w, 0), new Dimension(w, Short.MAX_VALUE));
    }

    public static Component createVerticalStrut(int h) {
        return new Filler(new Dimension(0, h), new Dimension(0, h), new Dimension(Short.MAX_VALUE, h));
    }

    public static Component createGlue() {
        return new Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
    }

    public static Component createHorizontalGlue() {
        return new Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(Short.MAX_VALUE, 0));
    }

    public static Component createVerticalGlue() {
        return new Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(0, Short.MAX_VALUE));
    }
}
