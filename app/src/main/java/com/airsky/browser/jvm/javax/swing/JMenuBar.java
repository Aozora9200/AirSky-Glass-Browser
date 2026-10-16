package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.JvmLook;
import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.FlowLayout;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.event.KeyEvent;

/** javax.swing.JMenuBar の互換クラス。メニューを押すと一覧が開く。 */
public class JMenuBar extends JComponent {
    public JMenuBar() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        setOpaque(true);
        setBackground(new Color(0xE9, 0xEB, 0xF2));
    }

    public JMenu add(JMenu m) {
        super.add(m);
        return m;
    }

    public JMenu getMenu(int i) {
        Component c = getComponent(i);
        return c instanceof JMenu ? (JMenu) c : null;
    }

    public int getMenuCount() { return getComponentCount(); }
    public void setHelpMenu(JMenu m) { add(m); }
    public void setBorderPainted(boolean b) {}
    public void setMargin(com.airsky.browser.jvm.java.awt.Insets m) {}
    public boolean isSelected() { return false; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(JvmLook.OUTLINE_VARIANT);
        g.fillRect(0, getHeight() - 1, getWidth(), 1);
    }

    /** 押されたキーがメニュー項目のショートカットなら、その項目を押す。 */
    @Override
    public boolean jvm$binding(KeyEvent e, int condition) {
        if (condition != 2 || e.getID() != KeyEvent.KEY_PRESSED) return false;
        KeyStroke k = KeyStroke.getKeyStrokeForEvent(e);
        for (Component c : getComponents()) {
            if (c instanceof JMenu && ((JMenu) c).jvm$accelerate(k)) return true;
        }
        return false;
    }
}
