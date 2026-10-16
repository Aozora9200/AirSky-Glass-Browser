package com.airsky.browser.jvm.java.awt;

/** java.awt.Panel の互換クラス。 */
public class Panel extends Container {
    public Panel() { this(new FlowLayout()); }

    public Panel(LayoutManager layout) {
        this.layout = layout;
        this.heavyweight = true;
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    @Override
    public void paint(Graphics g) {
        if (background != null) {
            Color c = g.getColor();
            g.setColor(background);
            g.fillRect(0, 0, width, height);
            g.setColor(c);
        }
        super.paint(g);
    }
}
