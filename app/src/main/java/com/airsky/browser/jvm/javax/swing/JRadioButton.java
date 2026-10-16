package com.airsky.browser.jvm.javax.swing;

/** javax.swing.JRadioButton の互換クラス。 */
public class JRadioButton extends JToggleButton {
    public JRadioButton() { this(null, null, false); }
    public JRadioButton(Icon icon) { this(null, icon, false); }
    public JRadioButton(Icon icon, boolean selected) { this(null, icon, selected); }
    public JRadioButton(String text) { this(text, null, false); }
    public JRadioButton(Action a) { this(null, null, false); setAction(a); }
    public JRadioButton(String text, boolean selected) { this(text, null, selected); }
    public JRadioButton(String text, Icon icon) { this(text, icon, false); }

    public JRadioButton(String text, Icon icon, boolean selected) {
        super(text, icon, selected);
        setHorizontalAlignment(LEADING);
    }

    @Override int jvm$style() { return STYLE_RADIO; }
}
