package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.FontMetrics;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Insets;
import com.airsky.browser.jvm.java.awt.event.ActionEvent;
import com.airsky.browser.jvm.java.awt.event.ActionListener;
import com.airsky.browser.jvm.javax.swing.text.Document;
import com.airsky.browser.jvm.javax.swing.text.JTextComponent;

import java.util.concurrent.CopyOnWriteArrayList;

/** javax.swing.JTextField の互換クラス。 */
public class JTextField extends JTextComponent implements SwingConstants {
    public static final String notifyAction = "notify-field-accept";

    private int columns;
    private int hAlign = LEADING;
    private String command;
    private Action action;
    private final CopyOnWriteArrayList<ActionListener> actionListeners = new CopyOnWriteArrayList<>();

    public JTextField() { this(null, null, 0); }
    public JTextField(String text) { this(null, text, 0); }
    public JTextField(int columns) { this(null, null, columns); }
    public JTextField(String text, int columns) { this(null, text, columns); }

    public JTextField(Document doc, String text, int columns) {
        if (doc != null) setDocument(doc);
        if (text != null) setText(text);
        this.columns = columns;
    }

    public int getColumns() { return columns; }
    public void setColumns(int c) { columns = c; revalidate(); }
    public int getHorizontalAlignment() { return hAlign; }
    public void setHorizontalAlignment(int a) { hAlign = a; repaint(); }
    public void setActionCommand(String c) { command = c; }
    public void addActionListener(ActionListener l) { if (l != null) actionListeners.add(l); }
    public void removeActionListener(ActionListener l) { actionListeners.remove(l); }
    public ActionListener[] getActionListeners() { return actionListeners.toArray(new ActionListener[0]); }
    public Action getAction() { return action; }

    public void setAction(Action a) {
        if (action != null) actionListeners.remove(action);
        action = a;
        if (a != null) actionListeners.add(a);
    }

    public void postActionEvent() { fireActionPerformed(); }

    protected void fireActionPerformed() {
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, command != null ? command : getText());
        for (ActionListener l : actionListeners) l.actionPerformed(e);
        JRootPane root = getRootPane();
        if (actionListeners.isEmpty() && root != null && root.getDefaultButton() != null) root.getDefaultButton().doClick();
    }

    @Override
    protected void jvm$enter() { fireActionPerformed(); }

    protected String jvm$displayText() { return getText(); }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics fm = getFontMetrics(getFont());
        Insets m = getMargin();
        Insets b = getInsets();
        int w = columns > 0 ? columns * fm.charWidth('m') : Math.max(fm.stringWidth(jvm$displayText()), 8);
        return new Dimension(w + m.left + m.right + b.left + b.right + 4, fm.getHeight() + m.top + m.bottom + b.top + b.bottom + 6);
    }

    @Override
    public Dimension getMaximumSize() {
        if (isMaximumSizeSet()) return super.getMaximumSize();
        return new Dimension(Short.MAX_VALUE, Short.MAX_VALUE);
    }

    @Override public Dimension getMinimumSize() { return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize(); }

    @Override
    protected void paintComponent(Graphics g) {
        jvm$paintBox(g);
        Insets m = getMargin();
        Insets b = getInsets();
        int x = m.left + b.left + 2, w = getWidth() - x - m.right - b.right - 2;
        g.setColor(jvm$textColor());
        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        String t = JvmLook.ellipsize(fm, jvm$displayText(), w);
        int align = hAlign == CENTER ? 1 : (hAlign == RIGHT || hAlign == TRAILING) ? 2 : 0;
        JvmLook.text(g, t, x, 0, w, getHeight(), align);
    }

    @Override public String getUIClassID() { return "TextFieldUI"; }
}
