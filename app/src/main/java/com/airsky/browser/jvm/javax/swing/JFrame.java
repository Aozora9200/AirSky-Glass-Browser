package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.BorderLayout;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Container;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.Frame;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.GraphicsConfiguration;
import com.airsky.browser.jvm.java.awt.LayoutManager;

/** javax.swing.JFrame の互換クラス。 */
public class JFrame extends Frame implements WindowConstants, RootPaneContainer {
    public static final int EXIT_ON_CLOSE = 3;

    private final JRootPane rootPane = new JRootPane(this);
    private Container contentPane;
    private JLayeredPane layeredPane;
    private Component glassPane;
    private JMenuBar menuBar;
    private int closeOp = HIDE_ON_CLOSE;
    private boolean internal;

    public JFrame() { this(""); }
    public JFrame(GraphicsConfiguration gc) { this(""); }
    public JFrame(String title, GraphicsConfiguration gc) { this(title); }

    public JFrame(String title) {
        super(title);
        internal = true;
        layeredPane = new JLayeredPane();
        super.addImpl(layeredPane, null, -1);
        contentPane = new JPanel(new BorderLayout());
        super.addImpl(contentPane, null, -1);
        glassPane = new JPanel();
        glassPane.setVisible(false);
        internal = false;
    }

    public static void setDefaultLookAndFeelDecorated(boolean b) {}
    public static boolean isDefaultLookAndFeelDecorated() { return false; }

    @Override
    protected void addImpl(Component comp, Object constraints, int index) {
        if (internal || contentPane == null) super.addImpl(comp, constraints, index);
        else contentPane.add(comp, constraints, index);
    }

    @Override
    public void remove(Component comp) {
        if (comp == contentPane || comp == menuBar || comp == layeredPane) super.remove(comp);
        else if (contentPane != null) contentPane.remove(comp);
    }

    @Override
    public void removeAll() {
        if (contentPane != null) contentPane.removeAll();
    }

    @Override
    public void setLayout(LayoutManager m) {
        if (contentPane != null) contentPane.setLayout(m);
        else super.setLayout(m);
    }

    @Override public JRootPane getRootPane() { return rootPane; }
    @Override public Container getContentPane() { return contentPane; }

    @Override
    public void setContentPane(Container c) {
        if (c == null) throw new IllegalArgumentException("contentPane cannot be set to null.");
        internal = true;
        if (contentPane != null) super.remove(contentPane);
        contentPane = c;
        super.addImpl(c, null, -1);
        internal = false;
        revalidate();
    }

    @Override public JLayeredPane getLayeredPane() { return layeredPane; }

    @Override
    public void setLayeredPane(JLayeredPane p) {
        internal = true;
        if (layeredPane != null) super.remove(layeredPane);
        layeredPane = p;
        if (p != null) super.addImpl(p, null, 0);
        internal = false;
    }

    @Override public Component getGlassPane() { return glassPane; }
    @Override public void setGlassPane(Component c) { glassPane = c; }
    public JMenuBar getJMenuBar() { return menuBar; }

    public void setJMenuBar(JMenuBar mb) {
        internal = true;
        if (menuBar != null) super.remove(menuBar);
        menuBar = mb;
        if (mb != null) super.addImpl(mb, null, -1);
        internal = false;
        revalidate();
    }

    public int getDefaultCloseOperation() { return closeOp; }
    public void setDefaultCloseOperation(int op) { closeOp = op; }
    public void setTransferHandler(Object h) {}

    @Override
    public void doLayout() {
        int y = 0;
        if (menuBar != null && menuBar.isVisible()) {
            int h = menuBar.getPreferredSize().height;
            menuBar.setBounds(0, 0, getWidth(), h);
            y = h;
        }
        if (contentPane != null) contentPane.setBounds(0, y, getWidth(), getHeight() - y);
        if (layeredPane != null) layeredPane.setBounds(0, 0, getWidth(), getHeight());
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        Dimension d = contentPane != null ? contentPane.getPreferredSize() : new Dimension(100, 100);
        if (menuBar != null && menuBar.isVisible()) {
            Dimension m = menuBar.getPreferredSize();
            d.width = Math.max(d.width, m.width);
            d.height += m.height;
        }
        return d;
    }

    @Override
    public void update(Graphics g) { paint(g); }

    @Override
    protected void jvm$defaultClose() {
        switch (closeOp) {
            case EXIT_ON_CLOSE: JvmRuntime.exit(0); break;
            case DISPOSE_ON_CLOSE: dispose(); break;
            case HIDE_ON_CLOSE: setVisible(false); break;
            default: break;
        }
    }
}
