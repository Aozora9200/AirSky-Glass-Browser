package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.javax.swing.event.ChangeEvent;
import com.airsky.browser.jvm.javax.swing.event.ChangeListener;

import java.util.concurrent.CopyOnWriteArrayList;

/** javax.swing.JProgressBar の互換クラス。 */
public class JProgressBar extends JComponent implements SwingConstants {
    private int orientation, min, max = 100, value;
    private boolean stringPainted, indeterminate, borderPainted = true;
    private String string;
    private final CopyOnWriteArrayList<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    public JProgressBar() { this(HORIZONTAL, 0, 100); }
    public JProgressBar(int orientation) { this(orientation, 0, 100); }
    public JProgressBar(int min, int max) { this(HORIZONTAL, min, max); }

    public JProgressBar(int orientation, int min, int max) {
        this.orientation = orientation;
        this.min = min;
        this.max = max;
        this.value = min;
    }

    public int getValue() { return value; }

    public void setValue(int v) {
        int nv = Math.max(min, Math.min(v, max));
        if (nv == value) return;
        value = nv;
        repaint();
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(e);
    }

    public int getMinimum() { return min; }
    public void setMinimum(int m) { min = m; repaint(); }
    public int getMaximum() { return max; }
    public void setMaximum(int m) { max = m; repaint(); }
    public int getOrientation() { return orientation; }
    public void setOrientation(int o) { orientation = o; revalidate(); }
    public boolean isStringPainted() { return stringPainted; }
    public void setStringPainted(boolean b) { stringPainted = b; revalidate(); }
    public String getString() { return string != null ? string : Math.round(getPercentComplete() * 100) + "%"; }
    public void setString(String s) { string = s; repaint(); }
    public boolean isIndeterminate() { return indeterminate; }
    public void setIndeterminate(boolean b) { indeterminate = b; repaint(); }
    public boolean isBorderPainted() { return borderPainted; }
    public void setBorderPainted(boolean b) { borderPainted = b; }
    public double getPercentComplete() { return (value - min) / (double) Math.max(1, max - min); }
    public void addChangeListener(ChangeListener l) { if (l != null) listeners.add(l); }
    public void removeChangeListener(ChangeListener l) { listeners.remove(l); }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        int h = stringPainted ? getFontMetrics(getFont()).getHeight() + 6 : 10;
        return orientation == VERTICAL ? new Dimension(h, 150) : new Dimension(150, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        JvmLook.smooth(g);
        int w = getWidth(), h = getHeight();
        int r = Math.min(w, h);
        g.setColor(JvmLook.TONAL);
        g.fillRoundRect(0, 0, w, h, r, r);
        g.setColor(isEnabled() ? JvmLook.PRIMARY : JvmLook.DISABLED);
        double f = indeterminate ? 0.35 : getPercentComplete();
        if (orientation == VERTICAL) {
            int fh = (int) Math.round(h * f);
            if (fh > 0) g.fillRoundRect(0, h - fh, w, fh, r, r);
        } else {
            int fw = (int) Math.round(w * f);
            if (fw > 0) g.fillRoundRect(indeterminate ? (w - fw) / 2 : 0, 0, fw, h, r, r);
        }
        if (stringPainted) {
            g.setColor(JvmLook.TEXT);
            g.setFont(getFont());
            JvmLook.text(g, getString(), 0, 0, w, h, 1);
        }
    }
}
