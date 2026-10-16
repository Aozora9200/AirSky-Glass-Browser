package com.airsky.browser.jvm.javax.swing;

/** javax.swing.JButton の互換クラス。 */
public class JButton extends AbstractButton {
    private boolean defaultCapable = true;

    public JButton() { this(null, null); }
    public JButton(String text) { this(text, null); }
    public JButton(Icon icon) { this(null, icon); }
    public JButton(Action a) { this(null, null); setAction(a); }

    public JButton(String text, Icon icon) {
        setText(text);
        setIcon(icon);
    }

    public boolean isDefaultButton() {
        JRootPane r = getRootPane();
        return r != null && r.getDefaultButton() == this;
    }

    public boolean isDefaultCapable() { return defaultCapable; }
    public void setDefaultCapable(boolean b) { defaultCapable = b; }
    @Override public String getUIClassID() { return "ButtonUI"; }
}
