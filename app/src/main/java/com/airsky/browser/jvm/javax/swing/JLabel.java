package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.FontMetrics;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Insets;

/** javax.swing.JLabel の互換クラス(<html> の文字は改行だけ反映する)。 */
public class JLabel extends JComponent implements SwingConstants {
    private String text;
    private Icon icon, disabledIcon;
    private int hAlign, vAlign = CENTER, hTextPos = TRAILING, vTextPos = CENTER;
    private int gap = 4;
    private Component labelFor;
    private int mnemonic;

    public JLabel() { this("", null, LEADING); }
    public JLabel(String text) { this(text, null, LEADING); }
    public JLabel(String text, int hAlign) { this(text, null, hAlign); }
    public JLabel(Icon icon) { this(null, icon, CENTER); }
    public JLabel(Icon icon, int hAlign) { this(null, icon, hAlign); }

    public JLabel(String text, Icon icon, int hAlign) {
        this.text = text;
        this.icon = icon;
        this.hAlign = hAlign;
        setFocusable(false);
    }

    public String getText() { return text; }

    public void setText(String t) {
        String old = text;
        text = t;
        firePropertyChange("text", old, t);
        revalidate();
    }

    public Icon getIcon() { return icon; }
    public void setIcon(Icon i) { icon = i; revalidate(); }
    public Icon getDisabledIcon() { return disabledIcon; }
    public void setDisabledIcon(Icon i) { disabledIcon = i; }
    public int getHorizontalAlignment() { return hAlign; }
    public void setHorizontalAlignment(int a) { hAlign = a; repaint(); }
    public int getVerticalAlignment() { return vAlign; }
    public void setVerticalAlignment(int a) { vAlign = a; repaint(); }
    public int getHorizontalTextPosition() { return hTextPos; }
    public void setHorizontalTextPosition(int p) { hTextPos = p; repaint(); }
    public int getVerticalTextPosition() { return vTextPos; }
    public void setVerticalTextPosition(int p) { vTextPos = p; repaint(); }
    public int getIconTextGap() { return gap; }
    public void setIconTextGap(int g) { gap = g; revalidate(); }
    public Component getLabelFor() { return labelFor; }
    public void setLabelFor(Component c) { labelFor = c; }
    public void setDisplayedMnemonic(int k) { mnemonic = k; }
    public void setDisplayedMnemonic(char c) { mnemonic = c; }
    public int getDisplayedMnemonic() { return mnemonic; }
    public void setDisplayedMnemonicIndex(int i) {}
    @Override public String getUIClassID() { return "LabelUI"; }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics fm = getFontMetrics(getFont());
        String[] lines = JvmLook.lines(text);
        int tw = 0;
        for (String l : lines) tw = Math.max(tw, fm.stringWidth(l));
        int th = (lines.length == 0 || (lines.length == 1 && lines[0].isEmpty())) ? 0 : lines.length * fm.getHeight();
        int iw = icon != null ? icon.getIconWidth() : 0, ih = icon != null ? icon.getIconHeight() : 0;
        int w, h;
        if (vTextPos != CENTER && icon != null && tw > 0) {
            w = Math.max(iw, tw);
            h = ih + gap + th;
        } else {
            w = iw + tw + (iw > 0 && tw > 0 ? gap : 0);
            h = Math.max(ih, th);
        }
        Insets in = getInsets();
        if (h == 0) h = fm.getHeight();
        return new Dimension(w + in.left + in.right, h + in.top + in.bottom);
    }

    @Override public Dimension getMinimumSize() { return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize(); }
    @Override public Dimension getMaximumSize() { return isMaximumSizeSet() ? super.getMaximumSize() : getPreferredSize(); }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        JvmLook.smooth(g);
        Insets in = getInsets();
        int x0 = in.left, y0 = in.top, w = getWidth() - in.left - in.right, h = getHeight() - in.top - in.bottom;
        Dimension p = getPreferredSize();
        int cw = p.width - in.left - in.right, ch = p.height - in.top - in.bottom;
        int x;
        if (hAlign == CENTER) x = x0 + (w - cw) / 2;
        else if (hAlign == RIGHT || hAlign == TRAILING) x = x0 + w - cw;
        else x = x0;
        int y;
        if (vAlign == TOP) y = y0;
        else if (vAlign == BOTTOM) y = y0 + h - ch;
        else y = y0 + (h - ch) / 2;
        String[] lines = JvmLook.lines(text);
        FontMetrics fm = g.getFontMetrics(getFont());
        int tx = x;
        Icon ic = isEnabled() || disabledIcon == null ? icon : disabledIcon;
        if (ic != null) {
            if (vTextPos != CENTER && lines.length > 0 && !lines[0].isEmpty()) {
                int iy = vTextPos == BOTTOM ? y : y + ch - ic.getIconHeight();
                ic.paintIcon(this, g, x + (cw - ic.getIconWidth()) / 2, iy);
                y = vTextPos == BOTTOM ? y + ic.getIconHeight() + gap : y;
                ch -= ic.getIconHeight() + gap;
            } else if (hTextPos == LEFT || hTextPos == LEADING) {
                ic.paintIcon(this, g, x + cw - ic.getIconWidth(), y + (ch - ic.getIconHeight()) / 2);
            } else {
                ic.paintIcon(this, g, x, y + (ch - ic.getIconHeight()) / 2);
                tx = x + ic.getIconWidth() + gap;
            }
        }
        Color fg = getForeground();
        g.setColor(isEnabled() ? (fg != null ? fg : JvmLook.TEXT) : JvmLook.DISABLED);
        g.setFont(getFont());
        int ty = y + (ch - lines.length * fm.getHeight()) / 2 + fm.getAscent();
        for (String l : lines) {
            int lx = tx;
            if (lines.length > 1 && hAlign == CENTER) lx = x0 + (w - fm.stringWidth(l)) / 2;
            g.drawString(l, lx, ty);
            ty += fm.getHeight();
        }
    }
}
