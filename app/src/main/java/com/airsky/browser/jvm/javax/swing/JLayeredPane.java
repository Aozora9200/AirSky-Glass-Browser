package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.Component;

/** javax.swing.JLayeredPane の互換クラス(ウィンドウの中身の上に重ねて置く)。 */
public class JLayeredPane extends JComponent {
    public static final Integer DEFAULT_LAYER = 0;
    public static final Integer PALETTE_LAYER = 100;
    public static final Integer MODAL_LAYER = 200;
    public static final Integer POPUP_LAYER = 300;
    public static final Integer DRAG_LAYER = 400;
    public static final Integer FRAME_CONTENT_LAYER = -30000;
    public static final String LAYER_PROPERTY = "layeredContainerLayer";

    public JLayeredPane() {
        setLayout(null);
    }

    @Override
    protected void addImpl(Component comp, Object constraints, int index) {
        super.addImpl(comp, constraints instanceof Integer ? null : constraints, constraints instanceof Integer ? 0 : index);
    }

    public void setLayer(Component c, int layer) {}
    public void setLayer(Component c, int layer, int position) {}
    public int getLayer(Component c) { return 0; }
    public void moveToFront(Component c) { setComponentZOrder(c, 0); }
    public void moveToBack(Component c) { setComponentZOrder(c, getComponentCount() - 1); }

    /** 子に当たらない所は、下の中身へ押す操作を通す。 */
    @Override
    public Component findComponentAt(int x, int y) {
        Component c = super.findComponentAt(x, y);
        return c == this ? null : c;
    }
}
