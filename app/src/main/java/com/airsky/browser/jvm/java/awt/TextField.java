package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.java.awt.event.ActionEvent;
import com.airsky.browser.jvm.java.awt.event.ActionListener;

import java.util.concurrent.CopyOnWriteArrayList;

/** java.awt.TextField の互換クラス。 */
public class TextField extends TextComponent {
    private int columns;
    private char echo;
    private final CopyOnWriteArrayList<ActionListener> actionListeners = new CopyOnWriteArrayList<>();

    public TextField() { this("", 0); }
    public TextField(String text) { this(text, 0); }
    public TextField(int columns) { this("", columns); }

    public TextField(String text, int columns) {
        super(text);
        this.columns = columns;
    }

    public int getColumns() { return columns; }
    public void setColumns(int c) { columns = c; invalidate(); }
    public void setEchoChar(char c) { echo = c; repaint(); }
    public char getEchoChar() { return echo; }
    public boolean echoCharIsSet() { return echo != 0; }
    public void addActionListener(ActionListener l) { if (l != null) actionListeners.add(l); }
    public void removeActionListener(ActionListener l) { actionListeners.remove(l); }

    @Override
    void onEnter() {
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, text);
        for (ActionListener l : actionListeners) l.actionPerformed(e);
    }

    @Override
    public Dimension getPreferredSize() {
        if (prefSize != null) return new Dimension(prefSize);
        FontMetrics fm = getFontMetrics(getFont());
        int w = columns > 0 ? columns * fm.charWidth('m') : Math.max(fm.stringWidth(text), 60);
        return new Dimension(w + 14, fm.getHeight() + 12);
    }

    @Override
    public Dimension getMinimumSize() { return minSize != null ? new Dimension(minSize) : getPreferredSize(); }

    @Override
    public void paint(Graphics g) {
        if (echo != 0) {
            String saved = text;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < saved.length(); i++) sb.append(echo);
            text = sb.toString();
            paintField(g, false);
            text = saved;
        } else {
            paintField(g, false);
        }
    }
}
