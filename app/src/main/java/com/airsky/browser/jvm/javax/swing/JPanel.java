package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.FlowLayout;
import com.airsky.browser.jvm.java.awt.LayoutManager;

/** javax.swing.JPanel の互換クラス。 */
public class JPanel extends JComponent {
    public JPanel() { this(new FlowLayout(), true); }
    public JPanel(boolean doubleBuffered) { this(new FlowLayout(), doubleBuffered); }
    public JPanel(LayoutManager layout) { this(layout, true); }

    public JPanel(LayoutManager layout, boolean doubleBuffered) {
        setLayout(layout);
        setOpaque(true);
        setBackground(JvmLook.PANEL);
    }

    @Override public String getUIClassID() { return "PanelUI"; }
}
