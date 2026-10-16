package com.airsky.browser.jvm.javax.swing;

/** javax.swing.JCheckBox の互換クラス。 */
public class JCheckBox extends JToggleButton {
    public JCheckBox() { this(null, null, false); }
    public JCheckBox(Icon icon) { this(null, icon, false); }
    public JCheckBox(Icon icon, boolean selected) { this(null, icon, selected); }
    public JCheckBox(String text) { this(text, null, false); }
    public JCheckBox(Action a) { this(null, null, false); setAction(a); }
    public JCheckBox(String text, boolean selected) { this(text, null, selected); }
    public JCheckBox(String text, Icon icon) { this(text, icon, false); }

    public JCheckBox(String text, Icon icon, boolean selected) {
        super(text, icon, selected);
        setHorizontalAlignment(LEADING);
    }

    public void setBorderPaintedFlat(boolean b) {}
    @Override int jvm$style() { return STYLE_CHECK; }
}
