package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Container;

/** javax.swing.JRootPane(ウィンドウの中身への入口。既定のボタンを覚える)。 */
public class JRootPane extends JComponent {
    public static final int NONE = 0;
    public static final int FRAME = 1;
    public static final int PLAIN_DIALOG = 2;

    private final RootPaneContainer owner;
    private JButton defaultButton;
    private int decoration;

    public JRootPane() { this(null); }

    JRootPane(RootPaneContainer owner) {
        this.owner = owner;
    }

    public JButton getDefaultButton() { return defaultButton; }
    public void setDefaultButton(JButton b) { defaultButton = b; }
    public Container getContentPane() { return owner != null ? owner.getContentPane() : null; }
    public void setContentPane(Container c) { if (owner != null) owner.setContentPane(c); }
    public JLayeredPane getLayeredPane() { return owner != null ? owner.getLayeredPane() : null; }
    public Component getGlassPane() { return owner != null ? owner.getGlassPane() : null; }
    public void setGlassPane(Component c) { if (owner != null) owner.setGlassPane(c); }
    public JMenuBar getJMenuBar() { return owner instanceof JFrame ? ((JFrame) owner).getJMenuBar() : null; }
    public void setJMenuBar(JMenuBar m) { if (owner instanceof JFrame) ((JFrame) owner).setJMenuBar(m); }
    public int getWindowDecorationStyle() { return decoration; }
    public void setWindowDecorationStyle(int s) { decoration = s; }
}
