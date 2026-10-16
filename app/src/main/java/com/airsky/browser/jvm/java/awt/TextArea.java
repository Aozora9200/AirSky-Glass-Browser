package com.airsky.browser.jvm.java.awt;

/** java.awt.TextArea の互換クラス。 */
public class TextArea extends TextComponent {
    public static final int SCROLLBARS_BOTH = 0;
    public static final int SCROLLBARS_VERTICAL_ONLY = 1;
    public static final int SCROLLBARS_HORIZONTAL_ONLY = 2;
    public static final int SCROLLBARS_NONE = 3;

    private int rows, columns;

    public TextArea() { this("", 0, 0); }
    public TextArea(String text) { this(text, 0, 0); }
    public TextArea(int rows, int columns) { this("", rows, columns); }
    public TextArea(String text, int rows, int columns) { this(text, rows, columns, SCROLLBARS_BOTH); }

    public TextArea(String text, int rows, int columns, int scrollbars) {
        super(text);
        this.rows = rows;
        this.columns = columns;
    }

    public void append(String s) { setText(getText() + (s != null ? s : "")); }
    @Deprecated public void appendText(String s) { append(s); }

    public void insert(String s, int pos) {
        String t = getText();
        pos = Math.max(0, Math.min(pos, t.length()));
        setText(t.substring(0, pos) + s + t.substring(pos));
    }

    public void replaceRange(String s, int start, int end) {
        String t = getText();
        setText(t.substring(0, start) + s + t.substring(end));
    }

    public int getRows() { return rows; }
    public void setRows(int r) { rows = r; invalidate(); }
    public int getColumns() { return columns; }
    public void setColumns(int c) { columns = c; invalidate(); }

    @Override
    boolean multiline() { return true; }

    @Override
    public Dimension getPreferredSize() {
        if (prefSize != null) return new Dimension(prefSize);
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(Math.max(columns, 20) * fm.charWidth('m') + 14, Math.max(rows, 4) * fm.getHeight() + 10);
    }

    @Override
    public Dimension getMinimumSize() { return minSize != null ? new Dimension(minSize) : getPreferredSize(); }

    @Override
    public void paint(Graphics g) {
        paintField(g, true);
    }
}
