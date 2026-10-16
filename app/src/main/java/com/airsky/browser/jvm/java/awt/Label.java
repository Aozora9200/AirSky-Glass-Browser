package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.JvmLook;

/** java.awt.Label の互換クラス。 */
public class Label extends Component {
    public static final int LEFT = 0;
    public static final int CENTER = 1;
    public static final int RIGHT = 2;

    private String text;
    private int alignment;

    public Label() { this("", LEFT); }
    public Label(String text) { this(text, LEFT); }

    public Label(String text, int alignment) {
        this.text = text != null ? text : "";
        this.alignment = alignment;
        this.heavyweight = true;
        this.focusable = false;
    }

    public String getText() { return text; }

    public void setText(String t) {
        text = t != null ? t : "";
        invalidate();
        repaint();
    }

    public int getAlignment() { return alignment; }
    public void setAlignment(int a) { alignment = a; repaint(); }

    @Override
    public Dimension getPreferredSize() {
        if (prefSize != null) return new Dimension(prefSize);
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(text) + 8, fm.getHeight() + 6);
    }

    @Override
    public Dimension getMinimumSize() {
        return minSize != null ? new Dimension(minSize) : getPreferredSize();
    }

    @Override
    public void paint(Graphics g) {
        if (background != null) {
            g.setColor(background);
            g.fillRect(0, 0, width, height);
        }
        g.setColor(enabled ? getForeground() : JvmLook.DISABLED);
        g.setFont(getFont());
        JvmLook.text(g, text, 4, 0, width - 8, height, alignment);
    }
}
