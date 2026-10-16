package com.airsky.browser.jvm.javax.swing;

/** javax.swing.JToggleButton の互換クラス。 */
public class JToggleButton extends AbstractButton {
    public JToggleButton() { this(null, null, false); }
    public JToggleButton(Icon icon) { this(null, icon, false); }
    public JToggleButton(Icon icon, boolean selected) { this(null, icon, selected); }
    public JToggleButton(String text) { this(text, null, false); }
    public JToggleButton(String text, boolean selected) { this(text, null, selected); }
    public JToggleButton(Action a) { this(null, null, false); setAction(a); }
    public JToggleButton(String text, Icon icon) { this(text, icon, false); }

    public JToggleButton(String text, Icon icon, boolean selected) {
        setText(text);
        setIcon(icon);
        jvm$setSelectedQuietly(selected);
    }

    @Override boolean jvm$toggles() { return true; }
}
