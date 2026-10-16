package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.event.ActionEvent;
import com.airsky.browser.jvm.java.awt.event.ActionListener;
import com.airsky.browser.jvm.java.awt.event.MouseEvent;

import java.util.concurrent.CopyOnWriteArrayList;

/** java.awt.Button の互換クラス。 */
public class Button extends Component {
    private String label;
    private String command;
    private boolean pressed;
    private final CopyOnWriteArrayList<ActionListener> actionListeners = new CopyOnWriteArrayList<>();

    public Button() { this(""); }

    public Button(String label) {
        this.label = label != null ? label : "";
        this.heavyweight = true;
    }

    public String getLabel() { return label; }

    public void setLabel(String l) {
        label = l != null ? l : "";
        invalidate();
        repaint();
    }

    public void setActionCommand(String c) { command = c; }
    public String getActionCommand() { return command != null ? command : label; }
    public void addActionListener(ActionListener l) { if (l != null) actionListeners.add(l); }
    public void removeActionListener(ActionListener l) { actionListeners.remove(l); }
    public ActionListener[] getActionListeners() { return actionListeners.toArray(new ActionListener[0]); }

    protected void processActionEvent(ActionEvent e) {
        for (ActionListener l : actionListeners) l.actionPerformed(e);
    }

    @Override
    protected void processEvent(AWTEvent e) {
        if (e instanceof ActionEvent) processActionEvent((ActionEvent) e);
        else super.processEvent(e);
    }

    @Override
    public Dimension getPreferredSize() {
        if (prefSize != null) return new Dimension(prefSize);
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(label) + 28, fm.getHeight() + 14);
    }

    @Override
    public Dimension getMinimumSize() {
        return minSize != null ? new Dimension(minSize) : getPreferredSize();
    }

    @Override
    public void paint(Graphics g) {
        JvmLook.smooth(g);
        Color bg = background != null ? background : JvmLook.TONAL;
        if (pressed) bg = JvmLook.PRESSED;
        if (!enabled) bg = new Color(0xE3, 0xE3, 0xE3);
        g.setColor(bg);
        int r = Math.min(height, 40);
        g.fillRoundRect(1, 1, width - 2, height - 2, r, r);
        g.setColor(enabled ? (foreground != null ? foreground : JvmLook.ON_TONAL) : JvmLook.DISABLED);
        g.setFont(getFont());
        JvmLook.text(g, label, 0, 0, width, height, 1);
    }

    @Override
    public boolean jvm$wantsMouse() { return true; }

    @Override
    public void jvm$mouse(MouseEvent e) {
        if (!enabled) return;
        switch (e.getID()) {
            case MouseEvent.MOUSE_PRESSED: pressed = true; repaint(); break;
            case MouseEvent.MOUSE_RELEASED: pressed = false; repaint(); break;
            case MouseEvent.MOUSE_CLICKED:
                processActionEvent(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, getActionCommand(),
                        e.getWhen(), e.getModifiers()));
                break;
            default: break;
        }
    }
}
