package com.airsky.browser.jvm.javax.swing;

public class JRadioButtonMenuItem extends JMenuItem {
    public JRadioButtonMenuItem() { this(null, null, false); }
    public JRadioButtonMenuItem(Icon icon) { this(null, icon, false); }
    public JRadioButtonMenuItem(String text) { this(text, null, false); }
    public JRadioButtonMenuItem(Action a) { this(null, null, false); setAction(a); }
    public JRadioButtonMenuItem(String text, Icon icon) { this(text, icon, false); }
    public JRadioButtonMenuItem(String text, boolean b) { this(text, null, b); }

    public JRadioButtonMenuItem(String text, Icon icon, boolean b) {
        super(text, icon);
        jvm$setSelectedQuietly(b);
    }

    @Override boolean jvm$toggles() { return true; }

    @Override
    void jvm$clicked(long when, int modifiers) {
        if (group != null) {
            if (!isSelected()) setSelected(true);
            fireActionPerformed(new com.airsky.browser.jvm.java.awt.event.ActionEvent(this,
                    com.airsky.browser.jvm.java.awt.event.ActionEvent.ACTION_PERFORMED, getActionCommand(), when, modifiers));
        } else {
            super.jvm$clicked(when, modifiers);
        }
    }
}
