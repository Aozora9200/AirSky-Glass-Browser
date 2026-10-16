package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.event.MouseEvent;
import com.airsky.browser.jvm.javax.swing.event.ChangeEvent;
import com.airsky.browser.jvm.javax.swing.event.ChangeListener;

import java.util.concurrent.CopyOnWriteArrayList;

/** javax.swing.JSlider の互換クラス。つまみをドラッグして値を変える。 */
public class JSlider extends JComponent implements SwingConstants {
    private int orientation, min, max, value, extent;
    private int major, minor;
    private boolean paintTicks, paintLabels, snap, inverted, adjusting, paintTrack = true;
    private final CopyOnWriteArrayList<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    public JSlider() { this(HORIZONTAL, 0, 100, 50); }
    public JSlider(int orientation) { this(orientation, 0, 100, 50); }
    public JSlider(int min, int max) { this(HORIZONTAL, min, max, (min + max) / 2); }
    public JSlider(int min, int max, int value) { this(HORIZONTAL, min, max, value); }

    public JSlider(int orientation, int min, int max, int value) {
        this.orientation = orientation;
        this.min = min;
        this.max = max;
        this.value = Math.max(min, Math.min(value, max));
    }

    public int getValue() { return value; }

    public void setValue(int v) {
        int nv = Math.max(min, Math.min(v, max - extent));
        if (nv == value) return;
        value = nv;
        repaint();
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(e);
    }

    public int getMinimum() { return min; }
    public void setMinimum(int m) { min = m; setValue(value); repaint(); }
    public int getMaximum() { return max; }
    public void setMaximum(int m) { max = m; setValue(value); repaint(); }
    public int getExtent() { return extent; }
    public void setExtent(int e) { extent = e; }
    public int getOrientation() { return orientation; }
    public void setOrientation(int o) { orientation = o; revalidate(); }
    public void setMajorTickSpacing(int n) { major = n; }
    public int getMajorTickSpacing() { return major; }
    public void setMinorTickSpacing(int n) { minor = n; }
    public int getMinorTickSpacing() { return minor; }
    public void setPaintTicks(boolean b) { paintTicks = b; repaint(); }
    public boolean getPaintTicks() { return paintTicks; }
    public void setPaintLabels(boolean b) { paintLabels = b; repaint(); }
    public boolean getPaintLabels() { return paintLabels; }
    public void setPaintTrack(boolean b) { paintTrack = b; }
    public void setSnapToTicks(boolean b) { snap = b; }
    public boolean getSnapToTicks() { return snap; }
    public void setInverted(boolean b) { inverted = b; repaint(); }
    public boolean getInverted() { return inverted; }
    public boolean getValueIsAdjusting() { return adjusting; }
    public void setValueIsAdjusting(boolean b) { adjusting = b; }
    public void setLabelTable(java.util.Dictionary<?, ?> t) {}
    public void addChangeListener(ChangeListener l) { if (l != null) listeners.add(l); }
    public void removeChangeListener(ChangeListener l) { listeners.remove(l); }
    public ChangeListener[] getChangeListeners() { return listeners.toArray(new ChangeListener[0]); }

    private boolean vertical() { return orientation == VERTICAL; }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        int extra = (paintTicks ? 8 : 0) + (paintLabels ? getFontMetrics(getFont()).getHeight() : 0);
        return vertical() ? new Dimension(32 + extra, 200) : new Dimension(200, 32 + extra);
    }

    private float fraction() {
        float f = (value - min) / (float) Math.max(1, max - min);
        return inverted ? 1 - f : f;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        JvmLook.smooth(g);
        int w = getWidth(), h = getHeight();
        int pad = 12;
        float f = fraction();
        boolean en = isEnabled();
        if (!vertical()) {
            int cy = 16, len = w - pad * 2, tx = pad + Math.round(f * len);
            g.setColor(JvmLook.OUTLINE_VARIANT);
            g.fillRoundRect(pad, cy - 2, len, 4, 4, 4);
            g.setColor(en ? JvmLook.PRIMARY : JvmLook.DISABLED);
            g.fillRoundRect(pad, cy - 2, tx - pad, 4, 4, 4);
            g.fillOval(tx - 8, cy - 8, 16, 16);
            if (paintTicks && major > 0) {
                g.setColor(JvmLook.OUTLINE);
                for (int v = min; v <= max; v += major) {
                    int x = pad + Math.round((v - min) / (float) Math.max(1, max - min) * len);
                    g.drawLine(x, cy + 10, x, cy + 14);
                    if (paintLabels) {
                        g.setFont(getFont());
                        String s = String.valueOf(v);
                        g.drawString(s, x - g.getFontMetrics().stringWidth(s) / 2, cy + 16 + g.getFontMetrics().getAscent());
                    }
                }
            }
        } else {
            int cx = 16, len = h - pad * 2, ty = h - pad - Math.round(f * len);
            g.setColor(JvmLook.OUTLINE_VARIANT);
            g.fillRoundRect(cx - 2, pad, 4, len, 4, 4);
            g.setColor(en ? JvmLook.PRIMARY : JvmLook.DISABLED);
            g.fillRoundRect(cx - 2, ty, 4, h - pad - ty, 4, 4);
            g.fillOval(cx - 8, ty - 8, 16, 16);
        }
    }

    @Override public boolean jvm$wantsMouse() { return true; }
    @Override public boolean jvm$wantsDrag() { return true; }

    @Override
    public void jvm$mouse(MouseEvent e) {
        if (!isEnabled()) return;
        int id = e.getID();
        if (id == MouseEvent.MOUSE_RELEASED) {
            adjusting = false;
            ChangeEvent ce = new ChangeEvent(this);
            for (ChangeListener l : listeners) l.stateChanged(ce);
            return;
        }
        if (id != MouseEvent.MOUSE_PRESSED && id != MouseEvent.MOUSE_DRAGGED) return;
        adjusting = true;
        int pad = 12;
        float f = vertical() ? 1 - (e.getY() - pad) / (float) Math.max(1, getHeight() - pad * 2)
                : (e.getX() - pad) / (float) Math.max(1, getWidth() - pad * 2);
        f = Math.max(0, Math.min(1, f));
        if (inverted) f = 1 - f;
        int v = min + Math.round(f * (max - min));
        if (snap && major > 0) v = min + Math.round((v - min) / (float) major) * major;
        setValue(v);
    }
}
