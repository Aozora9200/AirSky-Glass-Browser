package com.airsky.browser.jvm.java.awt;

/** java.awt.GridLayout の互換クラス。 */
public class GridLayout implements LayoutManager, java.io.Serializable {
    int hgap, vgap, rows, cols;

    public GridLayout() { this(1, 0, 0, 0); }
    public GridLayout(int rows, int cols) { this(rows, cols, 0, 0); }

    public GridLayout(int rows, int cols, int hgap, int vgap) {
        if (rows == 0 && cols == 0) throw new IllegalArgumentException("rows and cols cannot both be zero");
        this.rows = rows;
        this.cols = cols;
        this.hgap = hgap;
        this.vgap = vgap;
    }

    public int getRows() { return rows; }
    public void setRows(int r) { rows = r; }
    public int getColumns() { return cols; }
    public void setColumns(int c) { cols = c; }
    public int getHgap() { return hgap; }
    public void setHgap(int h) { hgap = h; }
    public int getVgap() { return vgap; }
    public void setVgap(int v) { vgap = v; }

    @Override public void addLayoutComponent(String name, Component comp) {}
    @Override public void removeLayoutComponent(Component comp) {}

    private int[] grid(Container parent) {
        int n = parent.getComponentCount();
        int r = rows, c = cols;
        if (r > 0) c = (n + r - 1) / r;
        else r = (n + c - 1) / c;
        return new int[]{Math.max(r, 1), Math.max(c, 1)};
    }

    @Override public Dimension preferredLayoutSize(Container parent) { return size(parent, true); }
    @Override public Dimension minimumLayoutSize(Container parent) { return size(parent, false); }

    private Dimension size(Container parent, boolean pref) {
        int[] g = grid(parent);
        int w = 0, h = 0;
        for (Component c : parent.getComponents()) {
            Dimension d = pref ? c.getPreferredSize() : c.getMinimumSize();
            w = Math.max(w, d.width);
            h = Math.max(h, d.height);
        }
        Insets in = parent.getInsets();
        return new Dimension(in.left + in.right + g[1] * w + (g[1] - 1) * hgap,
                in.top + in.bottom + g[0] * h + (g[0] - 1) * vgap);
    }

    @Override
    public void layoutContainer(Container parent) {
        Component[] comps = parent.getComponents();
        if (comps.length == 0) return;
        int[] g = grid(parent);
        int nrows = g[0], ncols = g[1];
        Insets in = parent.getInsets();
        int totalGapsWidth = (ncols - 1) * hgap;
        int widthWOInsets = parent.getWidth() - (in.left + in.right);
        int widthOnComponent = (widthWOInsets - totalGapsWidth) / ncols;
        int extraWidthAvailable = (widthWOInsets - (widthOnComponent * ncols + totalGapsWidth)) / 2;
        int totalGapsHeight = (nrows - 1) * vgap;
        int heightWOInsets = parent.getHeight() - (in.top + in.bottom);
        int heightOnComponent = (heightWOInsets - totalGapsHeight) / nrows;
        int extraHeightAvailable = (heightWOInsets - (heightOnComponent * nrows + totalGapsHeight)) / 2;
        for (int c = 0, x = in.left + extraWidthAvailable; c < ncols; c++, x += widthOnComponent + hgap) {
            for (int r = 0, y = in.top + extraHeightAvailable; r < nrows; r++, y += heightOnComponent + vgap) {
                int i = r * ncols + c;
                if (i < comps.length) comps[i].setBounds(x, y, widthOnComponent, heightOnComponent);
            }
        }
    }

    @Override public String toString() {
        return getClass().getName() + "[hgap=" + hgap + ",vgap=" + vgap + ",rows=" + rows + ",cols=" + cols + "]";
    }
}
