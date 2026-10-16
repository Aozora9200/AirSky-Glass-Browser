package com.airsky.browser.jvm.javax.swing.text;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Insets;
import com.airsky.browser.jvm.java.awt.datatransfer.Clipboard;
import com.airsky.browser.jvm.java.awt.datatransfer.DataFlavor;
import com.airsky.browser.jvm.java.awt.datatransfer.StringSelection;
import com.airsky.browser.jvm.java.awt.event.KeyEvent;
import com.airsky.browser.jvm.java.awt.event.MouseEvent;
import com.airsky.browser.jvm.javax.swing.JComponent;
import com.airsky.browser.jvm.javax.swing.JViewport;
import com.airsky.browser.jvm.javax.swing.Scrollable;

/** javax.swing.text.JTextComponent の互換クラス。押すと Android の入力欄で文字を編集する。 */
public abstract class JTextComponent extends JComponent implements Scrollable {
    private PlainDocument doc = new PlainDocument();
    private boolean editable = true;
    private int caret;
    private int selStart, selEnd;
    private Insets margin = new Insets(4, 6, 4, 6);
    private Color disabledColor = JvmLook.DISABLED;
    private Color caretColor = Color.BLACK, selectionColor = JvmLook.TONAL, selectedTextColor = Color.BLACK;

    protected JTextComponent() {
        setOpaque(true);
        setBackground(JvmLook.SURFACE);
        doc.onChange = this::changed;
    }

    private void changed() {
        caret = Math.min(caret, doc.getLength());
        revalidate();
    }

    public Document getDocument() { return doc; }

    public void setDocument(Document d) {
        if (d instanceof PlainDocument) {
            doc = (PlainDocument) d;
            doc.onChange = this::changed;
            revalidate();
        }
    }

    public String getText() { return doc.jvm$text(); }

    public String getText(int offs, int len) throws BadLocationException { return doc.getText(offs, len); }

    public void setText(String t) {
        try {
            doc.remove(0, doc.getLength());
            if (t != null) doc.insertString(0, t, null);
        } catch (BadLocationException ignored) {
        }
        caret = doc.getLength();
    }

    public boolean isEditable() { return editable; }
    public void setEditable(boolean b) { editable = b; repaint(); }
    public int getCaretPosition() { return caret; }

    public void setCaretPosition(int pos) {
        if (pos < 0 || pos > doc.getLength()) throw new IllegalArgumentException("bad position: " + pos);
        caret = pos;
        if (pos == doc.getLength()) jvm$scrollToEnd();
    }

    public void moveCaretPosition(int pos) { caret = pos; }
    public Insets getMargin() { return margin; }
    public void setMargin(Insets m) { margin = m != null ? m : new Insets(0, 0, 0, 0); revalidate(); }
    public Color getDisabledTextColor() { return disabledColor; }
    public void setDisabledTextColor(Color c) { disabledColor = c; }
    public Color getCaretColor() { return caretColor; }
    public void setCaretColor(Color c) { caretColor = c; }
    public Color getSelectionColor() { return selectionColor; }
    public void setSelectionColor(Color c) { selectionColor = c; }
    public Color getSelectedTextColor() { return selectedTextColor; }
    public void setSelectedTextColor(Color c) { selectedTextColor = c; }
    public void setDragEnabled(boolean b) {}
    public void setHighlighter(Object h) {}
    public void setFocusAccelerator(char c) {}

    public void select(int start, int end) {
        selStart = Math.max(0, Math.min(start, doc.getLength()));
        selEnd = Math.max(selStart, Math.min(end, doc.getLength()));
    }

    public void selectAll() { select(0, doc.getLength()); }
    public int getSelectionStart() { return selStart; }
    public int getSelectionEnd() { return selEnd; }
    public void setSelectionStart(int s) { select(s, selEnd); }
    public void setSelectionEnd(int e) { select(selStart, e); }
    public String getSelectedText() { return selEnd > selStart ? getText().substring(selStart, selEnd) : null; }

    public void replaceSelection(String s) {
        try {
            if (selEnd > selStart) doc.remove(selStart, selEnd - selStart);
            int at = selEnd > selStart ? selStart : caret;
            if (s != null) doc.insertString(Math.min(at, doc.getLength()), s, null);
            selStart = selEnd = 0;
        } catch (BadLocationException ignored) {
        }
    }

    public void copy() {
        String s = getSelectedText();
        Clipboard.SYSTEM.setContents(new StringSelection(s != null ? s : getText()), null);
    }

    public void cut() {
        copy();
        if (editable) replaceSelection("");
    }

    public void paste() {
        if (!editable) return;
        try {
            Object s = Clipboard.SYSTEM.getData(DataFlavor.stringFlavor);
            if (s != null) replaceSelection(String.valueOf(s));
        } catch (Exception ignored) {
        }
    }

    public void addCaretListener(Object l) {}

    /** 1 行の欄か(違えば複数行)。 */
    protected boolean jvm$multiline() { return false; }

    /** 1 行の欄で入力を終えた(Enter)。 */
    protected void jvm$enter() {}

    protected boolean jvm$password() { return false; }

    void jvm$scrollToEnd() {
        for (Component p = getParent(); p != null; p = p.getParent()) {
            if (p instanceof JViewport) {
                ((JViewport) p).jvm$scrollToEndSoon();
                return;
            }
        }
    }

    @Override public boolean jvm$wantsMouse() { return true; }
    @Override public boolean jvm$wantsKeyboardFocus() { return true; }

    @Override
    public void jvm$mouse(MouseEvent e) {
        if (e.getID() != MouseEvent.MOUSE_CLICKED || !isEnabled()) return;
        requestFocus();
        if (!editable) return;
        String result = JvmRuntime.blocking(() -> JvmRuntime.host().showInput(null, null, getText(), jvm$multiline(), jvm$password()), null);
        if (result != null) {
            setText(result);
            if (!jvm$multiline()) jvm$enter();
        }
    }

    @Override
    public void jvm$key(KeyEvent e) {
        if (!editable || !isEnabled()) return;
        String t = getText();
        if (e.getID() == KeyEvent.KEY_PRESSED) {
            if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && !t.isEmpty()) {
                setText(t.substring(0, t.length() - 1));
            } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                if (jvm$multiline()) setText(t + "\n");
                else jvm$enter();
            }
        } else if (e.getID() == KeyEvent.KEY_TYPED) {
            char c = e.getKeyChar();
            if (c >= ' ' && c != 127 && c != KeyEvent.CHAR_UNDEFINED) setText(t + c);
        }
    }

    /** 枠と背景を描く(中の文字は各クラスが描く)。 */
    protected void jvm$paintBox(Graphics g) {
        JvmLook.smooth(g);
        int w = getWidth(), h = getHeight();
        if (isOpaque()) {
            g.setColor(isBackgroundSet() ? getBackground() : JvmLook.SURFACE);
            if (getBorder() == null) g.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
            else g.fillRect(0, 0, w, h);
        }
        if (getBorder() == null && !(getParent() instanceof JViewport)) {
            g.setColor(isFocusOwner() && editable ? JvmLook.PRIMARY : JvmLook.OUTLINE_VARIANT);
            g.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
        }
    }

    protected Color jvm$textColor() {
        if (!isEnabled()) return disabledColor;
        Color fg = getForeground();
        return fg != null ? fg : JvmLook.TEXT;
    }

    @Override public com.airsky.browser.jvm.java.awt.Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(com.airsky.browser.jvm.java.awt.Rectangle r, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(com.airsky.browser.jvm.java.awt.Rectangle r, int o, int d) { return 64; }
    @Override public boolean getScrollableTracksViewportWidth() { return false; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
