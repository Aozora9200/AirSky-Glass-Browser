package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.BorderLayout;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Container;
import com.airsky.browser.jvm.java.awt.Frame;
import com.airsky.browser.jvm.java.awt.Window;

/** javax.swing.JWindow の互換クラス。 */
public class JWindow extends Window {
    private Container contentPane;
    private boolean internal;

    public JWindow() { this((Window) null); }
    public JWindow(Frame owner) { this((Window) owner); }

    public JWindow(Window owner) {
        super(owner);
        internal = true;
        contentPane = new JPanel(new BorderLayout());
        super.addImpl(contentPane, null, -1);
        internal = false;
    }

    @Override
    protected void addImpl(Component comp, Object constraints, int index) {
        if (internal || contentPane == null) super.addImpl(comp, constraints, index);
        else contentPane.add(comp, constraints, index);
    }

    public Container getContentPane() { return contentPane; }

    public void setContentPane(Container c) {
        internal = true;
        super.remove(contentPane);
        contentPane = c;
        super.addImpl(c, null, -1);
        internal = false;
    }

    @Override
    public void doLayout() {
        if (contentPane != null) contentPane.setBounds(0, 0, getWidth(), getHeight());
    }

    @Override
    public com.airsky.browser.jvm.java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        return contentPane.getPreferredSize();
    }
}
