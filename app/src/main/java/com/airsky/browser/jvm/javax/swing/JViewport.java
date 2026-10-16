package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.Point;
import com.airsky.browser.jvm.java.awt.Rectangle;
import com.airsky.browser.jvm.javax.swing.event.ChangeEvent;
import com.airsky.browser.jvm.javax.swing.event.ChangeListener;

import java.util.concurrent.CopyOnWriteArrayList;

/** javax.swing.JViewport の互換クラス(中身をずらして見せる窓)。 */
public class JViewport extends JComponent {
    public static final int BLIT_SCROLL_MODE = 1;
    public static final int BACKINGSTORE_SCROLL_MODE = 2;
    public static final int SIMPLE_SCROLL_MODE = 0;

    private Component view;
    private int px, py;
    private boolean scrollToEnd;
    private final CopyOnWriteArrayList<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    public JViewport() {
        setLayout(null);
        setOpaque(true);
    }

    public Component getView() { return view; }

    public void setView(Component v) {
        if (view != null) remove(view);
        view = v;
        px = py = 0;
        if (v != null) add(v);
        revalidate();
    }

    public void setScrollMode(int m) {}
    public void addChangeListener(ChangeListener l) { if (l != null) listeners.add(l); }
    public void removeChangeListener(ChangeListener l) { listeners.remove(l); }
    public Point getViewPosition() { return new Point(px, py); }

    public void setViewPosition(Point p) {
        jvm$scrollTo(p.x, p.y);
    }

    public Dimension getExtentSize() { return getSize(); }
    public void setExtentSize(Dimension d) {}
    public Dimension getViewSize() { return view != null ? view.getSize() : new Dimension(); }
    public void setViewSize(Dimension d) { if (view != null) view.setSize(d); }
    public Rectangle getViewRect() { return new Rectangle(px, py, getWidth(), getHeight()); }

    @Override
    public void scrollRectToVisible(Rectangle r) {
        int nx = px, ny = py;
        if (r.x < nx) nx = r.x;
        else if (r.x + r.width > nx + getWidth()) nx = r.x + r.width - getWidth();
        if (r.y < ny) ny = r.y;
        else if (r.y + r.height > ny + getHeight()) ny = r.y + r.height - getHeight();
        jvm$scrollTo(nx, ny);
    }

    /** 中身の大きさを決め、位置をずらす。 */
    @Override
    public void doLayout() {
        if (view == null) return;
        Dimension pref = view.getPreferredSize();
        boolean trackW = view instanceof Scrollable && ((Scrollable) view).getScrollableTracksViewportWidth();
        boolean trackH = view instanceof Scrollable && ((Scrollable) view).getScrollableTracksViewportHeight();
        int w = trackW ? getWidth() : Math.max(pref.width, getWidth());
        int h;
        if (view instanceof JTextArea && trackW) h = Math.max(((JTextArea) view).jvm$heightForWidth(w), getHeight());
        else h = trackH ? getHeight() : Math.max(pref.height, getHeight());
        if (scrollToEnd) {
            py = h - getHeight();
            scrollToEnd = false;
        }
        px = Math.max(0, Math.min(px, w - getWidth()));
        py = Math.max(0, Math.min(py, h - getHeight()));
        view.setBounds(-px, -py, w, h);
    }

    public boolean jvm$scrollTo(int x, int y) {
        if (view == null) return false;
        int maxX = Math.max(0, view.getWidth() - getWidth()), maxY = Math.max(0, view.getHeight() - getHeight());
        int nx = Math.max(0, Math.min(x, maxX)), ny = Math.max(0, Math.min(y, maxY));
        if (nx == px && ny == py) return false;
        px = nx;
        py = ny;
        view.setLocation(-px, -py);
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(e);
        repaint();
        return true;
    }

    /** 次に配置する時に一番下までずらす(文字を足した時)。 */
    public void jvm$scrollToEndSoon() {
        scrollToEnd = true;
        invalidate();
        repaint();
    }

    @Override
    public boolean jvm$dragScroll(int dx, int dy) {
        return jvm$scrollTo(px + dx, py + dy);
    }
}
