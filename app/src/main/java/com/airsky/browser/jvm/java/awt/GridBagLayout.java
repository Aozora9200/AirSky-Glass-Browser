package com.airsky.browser.jvm.java.awt;

import java.util.Hashtable;

/**
 * java.awt.GridBagLayout の互換クラス(本物より簡単な計算だが、よくある使い方はそろう)。
 */
public class GridBagLayout implements LayoutManager2, java.io.Serializable {
    protected static final int MAXGRIDSIZE = 512;
    protected static final int MINSIZE = 1;
    protected static final int PREFERREDSIZE = 2;

    protected Hashtable<Component, GridBagConstraints> comptable = new Hashtable<>();
    protected GridBagConstraints defaultConstraints = new GridBagConstraints();
    public int[] columnWidths;
    public int[] rowHeights;
    public double[] columnWeights;
    public double[] rowWeights;

    public GridBagLayout() {}

    public void setConstraints(Component comp, GridBagConstraints c) {
        comptable.put(comp, (GridBagConstraints) c.clone());
    }

    public GridBagConstraints getConstraints(Component comp) {
        return (GridBagConstraints) lookupConstraints(comp).clone();
    }

    protected GridBagConstraints lookupConstraints(Component comp) {
        GridBagConstraints c = comptable.get(comp);
        if (c == null) {
            setConstraints(comp, defaultConstraints);
            c = comptable.get(comp);
        }
        return c;
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        if (constraints instanceof GridBagConstraints) setConstraints(comp, (GridBagConstraints) constraints);
        else if (constraints != null) throw new IllegalArgumentException("cannot add to layout: constraints must be a GridBagConstraint");
    }

    @Override public void addLayoutComponent(String name, Component comp) {}
    @Override public void removeLayoutComponent(Component comp) { comptable.remove(comp); }
    @Override public Dimension preferredLayoutSize(Container parent) { return info(parent, true).size(parent); }
    @Override public Dimension minimumLayoutSize(Container parent) { return info(parent, false).size(parent); }
    @Override public Dimension maximumLayoutSize(Container target) { return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE); }
    @Override public float getLayoutAlignmentX(Container parent) { return 0.5f; }
    @Override public float getLayoutAlignmentY(Container parent) { return 0.5f; }
    @Override public void invalidateLayout(Container target) {}

    public int[][] getLayoutDimensions() { return new int[2][0]; }
    public double[][] getLayoutWeights() { return new double[2][0]; }

    /** 位置を決めた部品 1 つ。 */
    private static final class Cell {
        Component comp;
        GridBagConstraints c;
        int x, y, w, h;
        Dimension size;
    }

    private final class Info {
        Cell[] cells;
        int cols, rows;
        int[] colW, rowH;
        double[] colWt, rowWt;

        Dimension size(Container parent) {
            Insets in = parent.getInsets();
            int w = 0, h = 0;
            for (int v : colW) w += v;
            for (int v : rowH) h += v;
            return new Dimension(w + in.left + in.right, h + in.top + in.bottom);
        }
    }

    private Info info(Container parent, boolean pref) {
        Info info = new Info();
        java.util.List<Cell> cells = new java.util.ArrayList<>();
        int curX = 0, curY = 0;
        int[] rowNext = new int[MAXGRIDSIZE];
        int[] colNext = new int[MAXGRIDSIZE];
        int maxX = 0, maxY = 0;
        for (Component comp : parent.getComponents()) {
            if (!comp.isVisible()) continue;
            GridBagConstraints c = lookupConstraints(comp);
            Cell cell = new Cell();
            cell.comp = comp;
            cell.c = c;
            int w = c.gridwidth > 0 ? c.gridwidth : 1;
            int h = c.gridheight > 0 ? c.gridheight : 1;
            int x, y;
            if (c.gridx >= 0 && c.gridy >= 0) {
                x = c.gridx;
                y = c.gridy;
            } else if (c.gridx >= 0) {
                x = c.gridx;
                y = colNext[Math.min(x, MAXGRIDSIZE - 1)];
            } else if (c.gridy >= 0) {
                y = c.gridy;
                x = rowNext[Math.min(y, MAXGRIDSIZE - 1)];
            } else {
                x = curX;
                y = curY;
            }
            cell.x = x;
            cell.y = y;
            cell.w = w;
            cell.h = h;
            for (int i = x; i < Math.min(x + w, MAXGRIDSIZE); i++) colNext[i] = Math.max(colNext[i], y + h);
            for (int i = y; i < Math.min(y + h, MAXGRIDSIZE); i++) rowNext[i] = Math.max(rowNext[i], x + w);
            if (c.gridwidth == GridBagConstraints.REMAINDER) {
                curX = 0;
                curY = y + h;
            } else {
                curX = x + w;
                curY = y;
            }
            maxX = Math.max(maxX, x + w);
            maxY = Math.max(maxY, y + h);
            Dimension d = pref ? comp.getPreferredSize() : comp.getMinimumSize();
            cell.size = new Dimension(d.width + c.ipadx + c.insets.left + c.insets.right,
                    d.height + c.ipady + c.insets.top + c.insets.bottom);
            cells.add(cell);
        }
        if (columnWidths != null) maxX = Math.max(maxX, columnWidths.length);
        if (rowHeights != null) maxY = Math.max(maxY, rowHeights.length);
        // REMAINDER は最後の列・行まで広げる
        for (Cell cell : cells) {
            if (cell.c.gridwidth == GridBagConstraints.REMAINDER) cell.w = Math.max(1, maxX - cell.x);
            if (cell.c.gridheight == GridBagConstraints.REMAINDER) cell.h = Math.max(1, maxY - cell.y);
        }
        info.cols = maxX;
        info.rows = maxY;
        info.colW = new int[maxX];
        info.rowH = new int[maxY];
        info.colWt = new double[maxX];
        info.rowWt = new double[maxY];
        if (columnWidths != null) for (int i = 0; i < Math.min(columnWidths.length, maxX); i++) info.colW[i] = columnWidths[i];
        if (rowHeights != null) for (int i = 0; i < Math.min(rowHeights.length, maxY); i++) info.rowH[i] = rowHeights[i];
        // 1 マスの部品から先に大きさを決め、またがる部品は足りない分を均等に足す
        for (int pass = 0; pass < 2; pass++) {
            for (Cell cell : cells) {
                boolean single = cell.w == 1;
                if (single == (pass == 0)) {
                    int have = 0;
                    for (int i = cell.x; i < cell.x + cell.w; i++) have += info.colW[i];
                    int need = cell.size.width - have;
                    if (need > 0) {
                        for (int i = cell.x; i < cell.x + cell.w; i++) {
                            info.colW[i] += need / cell.w + (i == cell.x + cell.w - 1 ? need % cell.w : 0);
                        }
                    }
                    double wsum = 0;
                    for (int i = cell.x; i < cell.x + cell.w; i++) wsum += info.colWt[i];
                    if (cell.c.weightx > wsum) info.colWt[cell.x + cell.w - 1] += cell.c.weightx - wsum;
                }
                single = cell.h == 1;
                if (single == (pass == 0)) {
                    int have = 0;
                    for (int i = cell.y; i < cell.y + cell.h; i++) have += info.rowH[i];
                    int need = cell.size.height - have;
                    if (need > 0) {
                        for (int i = cell.y; i < cell.y + cell.h; i++) {
                            info.rowH[i] += need / cell.h + (i == cell.y + cell.h - 1 ? need % cell.h : 0);
                        }
                    }
                    double wsum = 0;
                    for (int i = cell.y; i < cell.y + cell.h; i++) wsum += info.rowWt[i];
                    if (cell.c.weighty > wsum) info.rowWt[cell.y + cell.h - 1] += cell.c.weighty - wsum;
                }
            }
        }
        if (columnWeights != null) for (int i = 0; i < Math.min(columnWeights.length, maxX); i++) info.colWt[i] += columnWeights[i];
        if (rowWeights != null) for (int i = 0; i < Math.min(rowWeights.length, maxY); i++) info.rowWt[i] += rowWeights[i];
        info.cells = cells.toArray(new Cell[0]);
        return info;
    }

    private static int[] distribute(int[] sizes, double[] weights, int available, int[] origin) {
        int total = 0;
        for (int s : sizes) total += s;
        int extra = available - total;
        int[] out = sizes.clone();
        double wsum = 0;
        for (double w : weights) wsum += w;
        if (extra != 0 && wsum > 0) {
            int used = 0;
            int last = -1;
            for (int i = 0; i < out.length; i++) {
                if (weights[i] > 0) {
                    int add = (int) (extra * weights[i] / wsum);
                    out[i] = Math.max(0, out[i] + add);
                    used += add;
                    last = i;
                }
            }
            if (last >= 0) out[last] = Math.max(0, out[last] + extra - used);
            origin[0] = 0;
        } else if (extra < 0 && total > 0) {
            int used = 0;
            for (int i = 0; i < out.length; i++) {
                out[i] = sizes[i] * available / total;
                used += out[i];
            }
            if (out.length > 0) out[out.length - 1] += available - used;
            origin[0] = 0;
        } else {
            origin[0] = extra / 2;
        }
        return out;
    }

    @Override
    public void layoutContainer(Container parent) {
        Info info = info(parent, true);
        if (info.cells.length == 0) return;
        Insets in = parent.getInsets();
        int availW = parent.getWidth() - in.left - in.right;
        int availH = parent.getHeight() - in.top - in.bottom;
        Dimension pref = info.size(parent);
        if (pref.width - in.left - in.right > availW || pref.height - in.top - in.bottom > availH) {
            Info min = info(parent, false);
            if (pref.width - in.left - in.right > availW) info.colW = min.colW;
            if (pref.height - in.top - in.bottom > availH) info.rowH = min.rowH;
        }
        int[] ox = new int[1], oy = new int[1];
        int[] colW = distribute(info.colW, info.colWt, availW, ox);
        int[] rowH = distribute(info.rowH, info.rowWt, availH, oy);
        int[] colX = new int[colW.length + 1];
        int[] rowY = new int[rowH.length + 1];
        colX[0] = in.left + ox[0];
        for (int i = 0; i < colW.length; i++) colX[i + 1] = colX[i] + colW[i];
        rowY[0] = in.top + oy[0];
        for (int i = 0; i < rowH.length; i++) rowY[i + 1] = rowY[i] + rowH[i];
        for (Cell cell : info.cells) {
            GridBagConstraints c = cell.c;
            int x = colX[cell.x] + c.insets.left;
            int y = rowY[cell.y] + c.insets.top;
            int w = colX[Math.min(cell.x + cell.w, colW.length)] - colX[cell.x] - c.insets.left - c.insets.right;
            int h = rowY[Math.min(cell.y + cell.h, rowH.length)] - rowY[cell.y] - c.insets.top - c.insets.bottom;
            Dimension d = cell.comp.getPreferredSize();
            int cw = d.width + c.ipadx, ch = d.height + c.ipady;
            if (c.fill == GridBagConstraints.BOTH || c.fill == GridBagConstraints.HORIZONTAL || cw > w) cw = w;
            if (c.fill == GridBagConstraints.BOTH || c.fill == GridBagConstraints.VERTICAL || ch > h) ch = h;
            int dx = w - cw, dy = h - ch;
            int ax, ay;
            switch (c.anchor) {
                case GridBagConstraints.NORTH: case GridBagConstraints.PAGE_START: ax = dx / 2; ay = 0; break;
                case GridBagConstraints.NORTHEAST: case GridBagConstraints.FIRST_LINE_END: ax = dx; ay = 0; break;
                case GridBagConstraints.EAST: case GridBagConstraints.LINE_END: ax = dx; ay = dy / 2; break;
                case GridBagConstraints.SOUTHEAST: case GridBagConstraints.LAST_LINE_END: ax = dx; ay = dy; break;
                case GridBagConstraints.SOUTH: case GridBagConstraints.PAGE_END: ax = dx / 2; ay = dy; break;
                case GridBagConstraints.SOUTHWEST: case GridBagConstraints.LAST_LINE_START: ax = 0; ay = dy; break;
                case GridBagConstraints.WEST: case GridBagConstraints.LINE_START: ax = 0; ay = dy / 2; break;
                case GridBagConstraints.NORTHWEST: case GridBagConstraints.FIRST_LINE_START: ax = 0; ay = 0; break;
                default: ax = dx / 2; ay = dy / 2; break;
            }
            cell.comp.setBounds(x + ax, y + ay, Math.max(0, cw), Math.max(0, ch));
        }
    }
}
