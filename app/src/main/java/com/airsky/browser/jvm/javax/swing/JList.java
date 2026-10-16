package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.FontMetrics;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Point;
import com.airsky.browser.jvm.java.awt.Rectangle;
import com.airsky.browser.jvm.java.awt.event.MouseEvent;
import com.airsky.browser.jvm.javax.swing.event.ListDataEvent;
import com.airsky.browser.jvm.javax.swing.event.ListDataListener;
import com.airsky.browser.jvm.javax.swing.event.ListSelectionListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/** javax.swing.JList の互換クラス(項目は toString() の文字で並べる)。 */
public class JList<E> extends JComponent implements Scrollable {
    public static final int VERTICAL = 0;
    public static final int VERTICAL_WRAP = 1;
    public static final int HORIZONTAL_WRAP = 2;

    private ListModel<E> model;
    private DefaultListSelectionModel selection = new DefaultListSelectionModel();
    private int visibleRows = 8;
    private int fixedHeight = -1, fixedWidth = -1;
    private Color selBg = JvmLook.TONAL, selFg = JvmLook.ON_TONAL;
    private final ListDataListener modelListener = new ListDataListener() {
        @Override public void intervalAdded(ListDataEvent e) { revalidate(); }
        @Override public void intervalRemoved(ListDataEvent e) {
            if (selection.getMaxSelectionIndex() >= model.getSize()) selection.clearSelection();
            revalidate();
        }
        @Override public void contentsChanged(ListDataEvent e) { revalidate(); }
    };

    public JList() { this(new DefaultListModel<>()); }

    public JList(E[] data) {
        this(arrayModel(data));
    }

    public JList(Vector<? extends E> data) {
        this(vectorModel(data));
    }

    public JList(ListModel<E> model) {
        selection.source = this;
        setOpaque(true);
        setBackground(JvmLook.SURFACE);
        setModel(model);
    }

    private static <E> ListModel<E> arrayModel(E[] data) {
        DefaultListModel<E> m = new DefaultListModel<>();
        if (data != null) for (E e : data) m.addElement(e);
        return m;
    }

    private static <E> ListModel<E> vectorModel(Vector<? extends E> data) {
        DefaultListModel<E> m = new DefaultListModel<>();
        if (data != null) for (E e : data) m.addElement(e);
        return m;
    }

    public ListModel<E> getModel() { return model; }

    public void setModel(ListModel<E> m) {
        if (model != null) model.removeListDataListener(modelListener);
        model = m;
        m.addListDataListener(modelListener);
        selection.clearSelection();
        revalidate();
    }

    public void setListData(E[] data) { setModel(arrayModel(data)); }
    public void setListData(Vector<? extends E> data) { setModel(vectorModel(data)); }
    public ListSelectionModel getSelectionModel() { return selection; }
    public void setSelectionModel(ListSelectionModel m) { if (m instanceof DefaultListSelectionModel) selection = (DefaultListSelectionModel) m; }
    public void setSelectionMode(int m) { selection.setSelectionMode(m); }
    public int getSelectionMode() { return selection.getSelectionMode(); }
    public void addListSelectionListener(ListSelectionListener l) { selection.addListSelectionListener(l); }
    public void removeListSelectionListener(ListSelectionListener l) { selection.removeListSelectionListener(l); }
    public int getSelectedIndex() { return selection.getMinSelectionIndex(); }
    public int[] getSelectedIndices() { return selection.jvm$selected(); }
    public boolean isSelectedIndex(int i) { return selection.isSelectedIndex(i); }
    public boolean isSelectionEmpty() { return selection.isSelectionEmpty(); }
    public void clearSelection() { selection.clearSelection(); repaint(); }
    public boolean getValueIsAdjusting() { return false; }
    public void setValueIsAdjusting(boolean b) {}

    public void setSelectedIndex(int i) {
        if (i >= model.getSize()) return;
        if (i < 0) clearSelection();
        else selection.setSelectionInterval(i, i);
        repaint();
    }

    public void setSelectedIndices(int[] idx) {
        selection.clearSelection();
        for (int i : idx) if (i >= 0 && i < model.getSize()) selection.addSelectionInterval(i, i);
        repaint();
    }

    public void setSelectionInterval(int a, int b) { selection.setSelectionInterval(a, b); repaint(); }
    public void addSelectionInterval(int a, int b) { selection.addSelectionInterval(a, b); repaint(); }

    public E getSelectedValue() {
        int i = getSelectedIndex();
        return i >= 0 && i < model.getSize() ? model.getElementAt(i) : null;
    }

    public List<E> getSelectedValuesList() {
        List<E> out = new ArrayList<>();
        for (int i : getSelectedIndices()) if (i < model.getSize()) out.add(model.getElementAt(i));
        return out;
    }

    @Deprecated
    public Object[] getSelectedValues() { return getSelectedValuesList().toArray(); }

    public void setSelectedValue(Object o, boolean scroll) {
        for (int i = 0; i < model.getSize(); i++) {
            E e = model.getElementAt(i);
            if (e == null ? o == null : e.equals(o)) {
                setSelectedIndex(i);
                if (scroll) ensureIndexIsVisible(i);
                return;
            }
        }
        clearSelection();
    }

    public int getVisibleRowCount() { return visibleRows; }
    public void setVisibleRowCount(int n) { visibleRows = n; revalidate(); }
    public void setLayoutOrientation(int o) {}
    public void setFixedCellHeight(int h) { fixedHeight = h; revalidate(); }
    public int getFixedCellHeight() { return fixedHeight; }
    public void setFixedCellWidth(int w) { fixedWidth = w; revalidate(); }
    public void setCellRenderer(Object r) {}
    public void setPrototypeCellValue(E e) {}
    public void setDragEnabled(boolean b) {}
    public Color getSelectionBackground() { return selBg; }
    public void setSelectionBackground(Color c) { selBg = c; repaint(); }
    public Color getSelectionForeground() { return selFg; }
    public void setSelectionForeground(Color c) { selFg = c; repaint(); }

    private int rowHeight() {
        return fixedHeight > 0 ? fixedHeight : getFontMetrics(getFont()).getHeight() + 14;
    }

    public void ensureIndexIsVisible(int i) {
        scrollRectToVisible(new Rectangle(0, i * rowHeight(), getWidth(), rowHeight()));
    }

    public int locationToIndex(Point p) {
        if (model.getSize() == 0) return -1;
        return Math.max(0, Math.min(model.getSize() - 1, p.y / rowHeight()));
    }

    public Point indexToLocation(int i) { return new Point(0, i * rowHeight()); }
    public Rectangle getCellBounds(int a, int b) { return new Rectangle(0, a * rowHeight(), getWidth(), (b - a + 1) * rowHeight()); }
    public int getFirstVisibleIndex() { return model.getSize() > 0 ? 0 : -1; }
    public int getLastVisibleIndex() { return model.getSize() - 1; }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics fm = getFontMetrics(getFont());
        int w = fixedWidth > 0 ? fixedWidth : 40;
        if (fixedWidth <= 0) {
            for (int i = 0; i < model.getSize(); i++) w = Math.max(w, fm.stringWidth(String.valueOf(model.getElementAt(i))));
        }
        return new Dimension(w + 24, Math.max(1, model.getSize()) * rowHeight());
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        Dimension d = getPreferredSize();
        return new Dimension(d.width, visibleRows * rowHeight());
    }

    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return rowHeight(); }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        JvmLook.smooth(g);
        int rh = rowHeight();
        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        Rectangle clip = g.getClipBounds();
        int first = clip != null ? Math.max(0, clip.y / rh) : 0;
        int last = clip != null ? Math.min(model.getSize() - 1, (clip.y + clip.height) / rh) : model.getSize() - 1;
        for (int i = first; i <= last; i++) {
            int y = i * rh;
            boolean sel = selection.isSelectedIndex(i);
            if (sel) {
                g.setColor(selBg);
                g.fillRoundRect(4, y + 2, getWidth() - 8, rh - 4, 16, 16);
            }
            g.setColor(sel ? selFg : (isEnabled() ? getForeground() : JvmLook.DISABLED));
            JvmLook.text(g, JvmLook.ellipsize(fm, String.valueOf(model.getElementAt(i)), getWidth() - 28), 14, y, getWidth() - 28, rh, 0);
        }
    }

    @Override public boolean jvm$wantsMouse() { return true; }

    @Override
    public void jvm$mouse(MouseEvent e) {
        if (!isEnabled() || e.getID() != MouseEvent.MOUSE_PRESSED) return;
        int i = e.getY() / rowHeight();
        if (i < 0 || i >= model.getSize()) return;
        if (selection.getSelectionMode() == ListSelectionModel.MULTIPLE_INTERVAL_SELECTION && (e.isControlDown() || e.isShiftDown())) {
            if (selection.isSelectedIndex(i)) selection.removeSelectionInterval(i, i);
            else selection.addSelectionInterval(i, i);
        } else {
            selection.setSelectionInterval(i, i);
        }
        repaint();
    }
}
