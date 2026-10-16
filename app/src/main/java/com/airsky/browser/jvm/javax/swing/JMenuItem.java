package com.airsky.browser.jvm.javax.swing;

/** javax.swing.JMenuItem の互換クラス。 */
public class JMenuItem extends AbstractButton {
    private KeyStroke accelerator;

    public JMenuItem() { this(null, null); }
    public JMenuItem(Icon icon) { this(null, icon); }
    public JMenuItem(String text) { this(text, null); }
    public JMenuItem(Action a) { this(null, null); setAction(a); }

    public JMenuItem(String text, Icon icon) {
        setText(text);
        setIcon(icon);
        setHorizontalAlignment(LEADING);
    }

    public JMenuItem(String text, int mnemonic) {
        this(text, null);
        setMnemonic(mnemonic);
    }

    public KeyStroke getAccelerator() { return accelerator; }
    public void setAccelerator(KeyStroke k) { accelerator = k; }
    public void setArmed(boolean b) {}
    public boolean isArmed() { return false; }

    @Override
    public void setAction(Action a) {
        super.setAction(a);
        if (a != null && a.getValue(Action.ACCELERATOR_KEY) instanceof KeyStroke) {
            accelerator = (KeyStroke) a.getValue(Action.ACCELERATOR_KEY);
        }
    }

    @Override int jvm$style() { return STYLE_MENU; }
}
