package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.FlowLayout;

/** javax.swing.JToolBar の互換クラス。 */
public class JToolBar extends JComponent implements SwingConstants {
    private int orientation;

    public JToolBar() { this(null, HORIZONTAL); }
    public JToolBar(int orientation) { this(null, orientation); }
    public JToolBar(String name) { this(name, HORIZONTAL); }

    public JToolBar(String name, int orientation) {
        setName(name);
        this.orientation = orientation;
        if (orientation == VERTICAL) setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        else setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
        setOpaque(true);
    }

    public JButton add(Action a) {
        JButton b = new JButton(a);
        add(b);
        return b;
    }

    public void addSeparator() { add(new JSeparator(orientation == VERTICAL ? HORIZONTAL : VERTICAL)); }
    public void addSeparator(Dimension d) { add(Box.createRigidArea(d)); }
    public void setFloatable(boolean b) {}
    public boolean isFloatable() { return false; }
    public void setRollover(boolean b) {}
    public void setBorderPainted(boolean b) {}
    public int getOrientation() { return orientation; }
    public void setOrientation(int o) { orientation = o; }
    public void setMargin(com.airsky.browser.jvm.java.awt.Insets m) {}
}
