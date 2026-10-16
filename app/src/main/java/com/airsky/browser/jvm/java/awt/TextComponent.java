package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.event.KeyEvent;
import com.airsky.browser.jvm.java.awt.event.MouseEvent;
import com.airsky.browser.jvm.java.awt.event.TextEvent;
import com.airsky.browser.jvm.java.awt.event.TextListener;

import java.util.concurrent.CopyOnWriteArrayList;

/** java.awt.TextComponent の互換クラス。押すと Android の入力欄で文字を編集する。 */
public class TextComponent extends Component {
    String text = "";
    boolean editable = true;
    final CopyOnWriteArrayList<TextListener> textListeners = new CopyOnWriteArrayList<>();

    TextComponent(String text) {
        this.text = text != null ? text : "";
        this.heavyweight = true;
    }

    public String getText() { return text; }

    public void setText(String t) {
        text = t != null ? t : "";
        TextEvent e = new TextEvent(this, TextEvent.TEXT_VALUE_CHANGED);
        for (TextListener l : textListeners) l.textValueChanged(e);
        repaint();
    }

    public boolean isEditable() { return editable; }
    public void setEditable(boolean b) { editable = b; repaint(); }
    public String getSelectedText() { return ""; }
    public void selectAll() {}
    public void select(int s, int e) {}
    public void setCaretPosition(int p) {}
    public int getCaretPosition() { return text.length(); }
    public void addTextListener(TextListener l) { if (l != null) textListeners.add(l); }
    public void removeTextListener(TextListener l) { textListeners.remove(l); }

    boolean multiline() { return false; }

    void onEnter() {}

    @Override
    public boolean jvm$wantsMouse() { return true; }

    @Override
    public boolean jvm$wantsKeyboardFocus() { return true; }

    @Override
    public void jvm$mouse(MouseEvent e) {
        if (e.getID() != MouseEvent.MOUSE_CLICKED || !enabled || !editable) return;
        requestFocus();
        String result = JvmRuntime.blocking(() -> JvmRuntime.host().showInput(null, null, text, multiline(), false), null);
        if (result != null) {
            setText(result);
            if (!multiline()) onEnter();
        }
    }

    @Override
    public void jvm$key(KeyEvent e) {
        if (!editable || !enabled) return;
        if (e.getID() == KeyEvent.KEY_PRESSED) {
            if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && !text.isEmpty()) {
                setText(text.substring(0, text.length() - 1));
            } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                if (multiline()) setText(text + "\n");
                else onEnter();
            }
        } else if (e.getID() == KeyEvent.KEY_TYPED) {
            char c = e.getKeyChar();
            if (c >= ' ' && c != 127 && c != KeyEvent.CHAR_UNDEFINED) setText(text + c);
        }
    }

    void paintField(Graphics g, boolean wrap) {
        JvmLook.smooth(g);
        g.setColor(background != null ? background : JvmLook.SURFACE);
        g.fillRoundRect(0, 0, width - 1, height - 1, 8, 8);
        g.setColor(isFocusOwner() ? JvmLook.PRIMARY : JvmLook.OUTLINE_VARIANT);
        g.drawRoundRect(0, 0, width - 1, height - 1, 8, 8);
        g.setColor(enabled ? (foreground != null ? foreground : JvmLook.TEXT) : JvmLook.DISABLED);
        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        if (!wrap) {
            JvmLook.text(g, JvmLook.ellipsize(fm, text, width - 12), 6, 0, width - 12, height, 0);
        } else {
            int y = 4 + fm.getAscent();
            for (String line : text.split("\n", -1)) {
                g.drawString(line, 6, y);
                y += fm.getHeight();
                if (y > height + fm.getHeight()) break;
            }
        }
    }
}
