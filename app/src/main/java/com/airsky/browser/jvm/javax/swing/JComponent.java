package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Container;
import com.airsky.browser.jvm.java.awt.Dimension;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Insets;
import com.airsky.browser.jvm.java.awt.Rectangle;
import com.airsky.browser.jvm.java.awt.Window;
import com.airsky.browser.jvm.java.awt.event.ActionEvent;
import com.airsky.browser.jvm.java.awt.event.ActionListener;
import com.airsky.browser.jvm.java.awt.event.KeyEvent;
import com.airsky.browser.jvm.javax.swing.border.Border;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** javax.swing.JComponent の互換クラス。 */
public abstract class JComponent extends Container implements java.io.Serializable {
    public static final int WHEN_FOCUSED = 0;
    public static final int WHEN_ANCESTOR_OF_FOCUSED_COMPONENT = 1;
    public static final int WHEN_IN_FOCUSED_WINDOW = 2;
    public static final int UNDEFINED_CONDITION = -1;
    public static final String TOOL_TIP_TEXT_KEY = "ToolTipText";

    protected com.airsky.browser.jvm.javax.swing.event.EventListenerList listenerList =
            new com.airsky.browser.jvm.javax.swing.event.EventListenerList();
    private boolean opaque;
    private Border border;
    private String toolTip;
    private Map<Object, Object> clientProperties;
    private final InputMap[] inputMaps = new InputMap[3];
    private ActionMap actionMap;
    private float alignmentX = -1, alignmentY = -1;
    private boolean inheritsPopupMenu;
    private JPopupMenu popup;

    public JComponent() {}

    @Override
    public void paint(Graphics g) {
        Graphics cg = g.create();
        try {
            paintComponent(cg);
        } finally {
            cg.dispose();
        }
        paintBorder(g);
        paintChildren(g);
    }

    @Override
    public void update(Graphics g) { paint(g); }

    protected void paintComponent(Graphics g) {
        if (isOpaque()) {
            Color bg = getBackground();
            if (bg != null) {
                g.setColor(bg);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            g.setColor(getForeground());
        }
    }

    protected void paintBorder(Graphics g) {
        if (border != null) border.paintBorder(this, g, 0, 0, getWidth(), getHeight());
    }

    protected void paintChildren(Graphics g) {
        List<Component> kids = jvm$children();
        for (int i = kids.size() - 1; i >= 0; i--) Container.jvm$paintChild(g, kids.get(i));
    }

    public void paintImmediately(int x, int y, int w, int h) { repaint(); }
    public void paintImmediately(Rectangle r) { repaint(); }
    public void repaint(Rectangle r) { repaint(); }

    @Override public boolean isOpaque() { return opaque; }

    public void setOpaque(boolean b) {
        opaque = b;
        repaint();
    }

    public Border getBorder() { return border; }

    public void setBorder(Border b) {
        border = b;
        invalidate();
        repaint();
    }

    @Override
    public Insets getInsets() {
        return border != null ? border.getBorderInsets(this) : new Insets(0, 0, 0, 0);
    }

    public Insets getInsets(Insets in) {
        Insets i = getInsets();
        if (in == null) return i;
        in.set(i.top, i.left, i.bottom, i.right);
        return in;
    }

    public void setToolTipText(String t) { toolTip = t; }
    public String getToolTipText() { return toolTip; }
    public String getToolTipText(com.airsky.browser.jvm.java.awt.event.MouseEvent e) { return toolTip; }

    public final void putClientProperty(Object key, Object value) {
        if (clientProperties == null) clientProperties = new HashMap<>();
        if (value == null) clientProperties.remove(key);
        else clientProperties.put(key, value);
    }

    public final Object getClientProperty(Object key) {
        return clientProperties != null ? clientProperties.get(key) : null;
    }

    public void setDoubleBuffered(boolean b) {}
    public void setRequestFocusEnabled(boolean b) {}
    public boolean isRequestFocusEnabled() { return true; }
    public void setVerifyInputWhenFocusTarget(boolean b) {}
    public void setAutoscrolls(boolean b) {}
    public boolean getAutoscrolls() { return false; }
    public void setDebugGraphicsOptions(int o) {}
    public void setInheritsPopupMenu(boolean b) { inheritsPopupMenu = b; }
    public boolean getInheritsPopupMenu() { return inheritsPopupMenu; }
    public void setComponentPopupMenu(JPopupMenu p) { popup = p; }
    public JPopupMenu getComponentPopupMenu() { return popup; }
    public void setTransferHandler(Object h) {}
    public void updateUI() {}
    public String getUIClassID() { return "ComponentUI"; }
    public void setInputVerifier(Object v) {}
    public void grabFocus() { requestFocus(); }

    @Override
    public void revalidate() {
        invalidate();
        repaint();
    }

    public Rectangle getVisibleRect() { return new Rectangle(0, 0, getWidth(), getHeight()); }

    public void scrollRectToVisible(Rectangle r) {
        Container p = getParent();
        if (p instanceof JViewport) ((JViewport) p).scrollRectToVisible(new Rectangle(r.x + getX(), r.y + getY(), r.width, r.height));
        else if (p instanceof JComponent) ((JComponent) p).scrollRectToVisible(new Rectangle(r.x + getX(), r.y + getY(), r.width, r.height));
    }

    public void setAlignmentX(float a) { alignmentX = a; }
    public void setAlignmentY(float a) { alignmentY = a; }
    @Override public float getAlignmentX() { return alignmentX >= 0 ? alignmentX : super.getAlignmentX(); }
    @Override public float getAlignmentY() { return alignmentY >= 0 ? alignmentY : super.getAlignmentY(); }

    public Container getTopLevelAncestor() { return jvm$window(); }

    public JRootPane getRootPane() {
        Window w = jvm$window();
        if (w instanceof RootPaneContainer) return ((RootPaneContainer) w).getRootPane();
        return null;
    }

    // ------------------------------------------------------------------
    // キーの割り当て
    // ------------------------------------------------------------------

    public final InputMap getInputMap() { return getInputMap(WHEN_FOCUSED); }

    public final InputMap getInputMap(int condition) {
        if (condition < 0 || condition > 2) throw new IllegalArgumentException("condition must be one of JComponent.WHEN_*");
        if (inputMaps[condition] == null) inputMaps[condition] = new InputMap();
        return inputMaps[condition];
    }

    public final void setInputMap(int condition, InputMap map) { inputMaps[condition] = map; }

    public final ActionMap getActionMap() {
        if (actionMap == null) actionMap = new ActionMap();
        return actionMap;
    }

    public final void setActionMap(ActionMap am) { actionMap = am; }

    public void registerKeyboardAction(ActionListener l, KeyStroke k, int condition) {
        registerKeyboardAction(l, null, k, condition);
    }

    public void registerKeyboardAction(ActionListener l, String command, KeyStroke k, int condition) {
        Object key = new Object();
        getInputMap(condition).put(k, key);
        getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                l.actionPerformed(new ActionEvent(e.getSource(), e.getID(), command, e.getWhen(), e.getModifiers()));
            }
        });
    }

    public void unregisterKeyboardAction(KeyStroke k) {
        for (InputMap m : inputMaps) if (m != null) m.remove(k);
    }

    public void resetKeyboardActions() {
        for (InputMap m : inputMaps) if (m != null) m.clear();
        if (actionMap != null) actionMap.clear();
    }

    @Override
    public boolean jvm$binding(KeyEvent e, int condition) {
        InputMap map = inputMaps[condition];
        if (map == null || actionMap == null) return false;
        Object key = map.get(KeyStroke.getKeyStrokeForEvent(e));
        if (key == null) return false;
        Action a = actionMap.get(key);
        if (a == null || !a.isEnabled()) return false;
        a.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, key instanceof String ? (String) key : null,
                e.getWhen(), e.getModifiers()));
        return true;
    }
}
