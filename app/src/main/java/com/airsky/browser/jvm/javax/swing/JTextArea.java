package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.FontMetrics;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Insets;
import com.airsky.browser.jvm.javax.swing.text.BadLocationException;
import com.airsky.browser.jvm.javax.swing.text.Document;
import com.airsky.browser.jvm.javax.swing.text.JTextComponent;

import java.util.ArrayList;
import java.util.List;

/** javax.swing.JTextArea の互換クラス。 */
public class JTextArea extends JTextComponent {
    private int rows, columns;
    private boolean lineWrap, wrapWord;
    private int tabSize = 8;

    public JTextArea() { this(null, null, 0, 0); }
    public JTextArea(String text) { this(null, text, 0, 0); }
    public JTextArea(int rows, int columns) { this(null, null, rows, columns); }
    public JTextArea(String text, int rows, int columns) { this(null, text, rows, columns); }
    public JTextArea(Document doc) { this(doc, null, 0, 0); }

    public JTextArea(Document doc, String text, int rows, int columns) {
        if (doc != null) setDocument(doc);
        if (text != null) setText(text);
        this.rows = rows;
        this.columns = columns;
    }

    public void append(String s) {
        if (s == null || s.isEmpty()) return;
        try {
            getDocument().insertString(getDocument().getLength(), s, null);
        } catch (BadLocationException ignored) {
        }
    }

    public void insert(String s, int pos) {
        try {
            getDocument().insertString(pos, s, null);
        } catch (BadLocationException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public void replaceRange(String s, int start, int end) {
        try {
            getDocument().remove(start, end - start);
            if (s != null) getDocument().insertString(start, s, null);
        } catch (BadLocationException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public int getRows() { return rows; }
    public void setRows(int r) { rows = r; revalidate(); }
    public int getColumns() { return columns; }
    public void setColumns(int c) { columns = c; revalidate(); }
    public boolean getLineWrap() { return lineWrap; }
    public void setLineWrap(boolean b) { lineWrap = b; revalidate(); }
    public boolean getWrapStyleWord() { return wrapWord; }
    public void setWrapStyleWord(boolean b) { wrapWord = b; revalidate(); }
    public int getTabSize() { return tabSize; }
    public void setTabSize(int s) { tabSize = s; repaint(); }
    public int getLineCount() { return getText().split("\n", -1).length; }

    public int getLineStartOffset(int line) throws BadLocationException {
        String[] lines = getText().split("\n", -1);
        if (line < 0 || line >= lines.length) throw new BadLocationException("No such line", line);
        int off = 0;
        for (int i = 0; i < line; i++) off += lines[i].length() + 1;
        return off;
    }

    public int getLineEndOffset(int line) throws BadLocationException {
        String[] lines = getText().split("\n", -1);
        if (line < 0 || line >= lines.length) throw new BadLocationException("No such line", line);
        return getLineStartOffset(line) + lines[line].length() + (line < lines.length - 1 ? 1 : 0);
    }

    public int getLineOfOffset(int offset) throws BadLocationException {
        String t = getText();
        if (offset < 0 || offset > t.length()) throw new BadLocationException("Can't translate offset to line", offset);
        int line = 0;
        for (int i = 0; i < offset; i++) if (t.charAt(i) == '\n') line++;
        return line;
    }

    @Override protected boolean jvm$multiline() { return true; }

    /** 幅 width で折り返した時の行。 */
    List<String> jvm$wrapped(FontMetrics fm, int width) {
        List<String> out = new ArrayList<>();
        String tab = " ".repeat(Math.max(1, tabSize));
        for (String raw : getText().split("\n", -1)) {
            String line = raw.replace("\t", tab);
            if (!lineWrap || width <= 0 || fm.stringWidth(line) <= width) {
                out.add(line);
                continue;
            }
            int start = 0;
            while (start < line.length()) {
                int end = start + 1;
                while (end < line.length() && fm.stringWidth(line.substring(start, end + 1)) <= width) end++;
                if (wrapWord && end < line.length()) {
                    int sp = line.lastIndexOf(' ', end);
                    if (sp > start) end = sp + 1;
                }
                out.add(line.substring(start, end));
                start = end;
            }
        }
        return out;
    }

    /** 幅を決めた時に必要な高さ(JScrollPane が使う)。 */
    public int jvm$heightForWidth(int width) {
        FontMetrics fm = getFontMetrics(getFont());
        Insets m = getMargin();
        int lines = jvm$wrapped(fm, width - m.left - m.right).size();
        return Math.max(rows, lines) * fm.getHeight() + m.top + m.bottom;
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics fm = getFontMetrics(getFont());
        Insets m = getMargin();
        Insets b = getInsets();
        List<String> lines = jvm$wrapped(fm, -1);
        int w = 0;
        for (String l : lines) w = Math.max(w, fm.stringWidth(l));
        if (columns > 0) w = Math.max(w, columns * fm.charWidth('m'));
        if (lineWrap && columns > 0) w = columns * fm.charWidth('m');
        int h = Math.max(rows, lines.size()) * fm.getHeight();
        return new Dimension(Math.max(w, 20) + m.left + m.right + b.left + b.right,
                Math.max(h, fm.getHeight()) + m.top + m.bottom + b.top + b.bottom);
    }

    @Override public Dimension getMinimumSize() { return isMinimumSizeSet() ? super.getMinimumSize() : new Dimension(20, 20); }

    @Override public boolean getScrollableTracksViewportWidth() { return lineWrap; }

    @Override
    protected void paintComponent(Graphics g) {
        jvm$paintBox(g);
        Insets m = getMargin();
        Insets b = getInsets();
        g.setColor(jvm$textColor());
        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        int x = m.left + b.left, y = m.top + b.top + fm.getAscent();
        for (String line : jvm$wrapped(fm, getWidth() - x - m.right - b.right)) {
            g.drawString(line, x, y);
            y += fm.getHeight();
        }
    }

    @Override public String getUIClassID() { return "TextAreaUI"; }
}
