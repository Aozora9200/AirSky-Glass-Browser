package com.airsky.browser.jvm.javax.swing;

public class JCheckBoxMenuItem extends JMenuItem {
    public JCheckBoxMenuItem() { this(null, null, false); }
    public JCheckBoxMenuItem(Icon icon) { this(null, icon, false); }
    public JCheckBoxMenuItem(String text) { this(text, null, false); }
    public JCheckBoxMenuItem(Action a) { this(null, null, false); setAction(a); }
    public JCheckBoxMenuItem(String text, Icon icon) { this(text, icon, false); }
    public JCheckBoxMenuItem(String text, boolean b) { this(text, null, b); }

    public JCheckBoxMenuItem(String text, Icon icon, boolean b) {
        super(text, icon);
        jvm$setSelectedQuietly(b);
    }

    public boolean getState() { return isSelected(); }
    public void setState(boolean b) { setSelected(b); }
    @Override boolean jvm$toggles() { return true; }
}
