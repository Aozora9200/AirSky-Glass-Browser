package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.Adjustable;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.event.AdjustmentEvent;
import com.airsky.browser.jvm.java.awt.event.AdjustmentListener;
import com.airsky.browser.jvm.java.awt.event.MouseEvent;

import java.util.concurrent.CopyOnWriteArrayList;

/** javax.swing.JScrollBar の互換クラス。JScrollPane のものは中身の位置とつながる。 */
public class JScrollBar extends JComponent implements Adjustable {
    private int orientation;
    private int value, extent, min, max;
    private int unit = 1, block = 10;
    JViewport viewport;
    private final CopyOnWriteArrayList<AdjustmentListener> listeners = new CopyOnWriteArrayList<>();

    public JScrollBar() { this(VERTICAL); }
    public JScrollBar(int orientation) { this(orientation, 0, 10, 0, 100); }

    public JScrollBar(int orientation, int value, int extent, int min, int max) {
        this.orientation = orientation;
        this.value = value;
        this.extent = extent;
        this.min = min;
        this.max = max;
    }

    private boolean horizontal() { return orientation == HORIZONTAL; }

    @Override public int getOrientation() { return orientation; }
    public void setOrientation(int o) { orientation = o; }

    @Override
    public int getValue() {
        if (viewport != null) return horizontal() ? viewport.getViewPosition().x : viewport.getViewPosition().y;
        return value;
    }

    @Override
    public void setValue(int v) {
        if (viewport != null) {
            com.airsky.browser.jvm.java.awt.Point p = viewport.getViewPosition();
            if (horizontal()) viewport.jvm$scrollTo(v, p.y);
            else viewport.jvm$scrollTo(p.x, v);
            return;
        }
        int nv = Math.max(min, Math.min(v, max - extent));
        if (nv == value) return;
        value = nv;
        repaint();
        AdjustmentEvent e = new AdjustmentEvent(this, AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED, AdjustmentEvent.TRACK, value);
        for (AdjustmentListener l : listeners) l.adjustmentValueChanged(e);
    }

    @Override public int getMinimum() { return viewport != null ? 0 : min; }
    @Override public void setMinimum(int m) { min = m; }

    @Override
    public int getMaximum() {
        if (viewport != null) return horizontal() ? viewport.getViewSize().width : viewport.getViewSize().height;
        return max;
    }

    @Override public void setMaximum(int m) { max = m; }

    @Override
    public int getVisibleAmount() {
        if (viewport != null) return horizontal() ? viewport.getWidth() : viewport.getHeight();
        return extent;
    }

    @Override public void setVisibleAmount(int a) { extent = a; }
    @Override public int getUnitIncrement() { return unit; }
    @Override public void setUnitIncrement(int u) { unit = u; }
    public int getUnitIncrement(int direction) { return unit; }
    @Override public int getBlockIncrement() { return block; }
    @Override public void setBlockIncrement(int b) { block = b; }
    public int getBlockIncrement(int direction) { return block; }
    public void setValues(int value, int extent, int min, int max) {
        this.extent = extent;
        this.min = min;
        this.max = max;
        setValue(value);
    }
    public boolean getValueIsAdjusting() { return false; }
    public void setValueIsAdjusting(boolean b) {}
    @Override public void addAdjustmentListener(AdjustmentListener l) { if (l != null) listeners.add(l); }
    @Override public void removeAdjustmentListener(AdjustmentListener l) { listeners.remove(l); }

    public BoundedRangeModel getModel() { return new BoundedRangeModel(this); }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        return horizontal() ? new Dimension(100, 12) : new Dimension(12, 100);
    }

    @Override
    protected void paintComponent(Graphics g) {
        JvmLook.smooth(g);
        int w = getWidth(), h = getHeight();
        g.setColor(JvmLook.OUTLINE_VARIANT);
        int range = Math.max(1, getMaximum() - getMinimum());
        if (horizontal()) {
            int tw = Math.max(16, w * getVisibleAmount() / range);
            int tx = (w - tw) * (getValue() - getMinimum()) / Math.max(1, range - getVisibleAmount());
            g.fillRoundRect(tx, 2, tw, h - 4, h - 4, h - 4);
        } else {
            int th = Math.max(16, h * getVisibleAmount() / range);
            int ty = (h - th) * (getValue() - getMinimum()) / Math.max(1, range - getVisibleAmount());
            g.fillRoundRect(2, ty, w - 4, th, w - 4, w - 4);
        }
    }

    @Override public boolean jvm$wantsMouse() { return true; }
    @Override public boolean jvm$wantsDrag() { return true; }

    @Override
    public void jvm$mouse(MouseEvent e) {
        if (e.getID() != MouseEvent.MOUSE_PRESSED && e.getID() != MouseEvent.MOUSE_DRAGGED) return;
        int range = getMaximum() - getMinimum() - getVisibleAmount();
        float f = horizontal() ? e.getX() / (float) Math.max(1, getWidth()) : e.getY() / (float) Math.max(1, getHeight());
        setValue(getMinimum() + Math.round(Math.max(0, Math.min(1, f)) * range));
    }
}
