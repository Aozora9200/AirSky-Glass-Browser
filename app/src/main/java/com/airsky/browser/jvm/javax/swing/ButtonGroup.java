package com.airsky.browser.jvm.javax.swing;

import java.util.Enumeration;
import java.util.Vector;

/** javax.swing.ButtonGroup の互換クラス。 */
public class ButtonGroup implements java.io.Serializable {
    protected Vector<AbstractButton> buttons = new Vector<>();
    private AbstractButton selection;

    public ButtonGroup() {}

    public void add(AbstractButton b) {
        if (b == null) return;
        buttons.add(b);
        b.group = this;
        if (b.isSelected()) {
            if (selection == null) selection = b;
            else b.jvm$setSelectedQuietly(false);
        }
    }

    public void remove(AbstractButton b) {
        if (b == null) return;
        buttons.remove(b);
        if (b.group == this) b.group = null;
        if (selection == b) selection = null;
    }

    public void clearSelection() {
        if (selection != null) {
            AbstractButton old = selection;
            selection = null;
            old.jvm$setSelectedQuietly(false);
        }
    }

    public Enumeration<AbstractButton> getElements() { return buttons.elements(); }
    public ButtonModel getSelection() { return selection != null ? selection.getModel() : null; }

    public void setSelected(ButtonModel m, boolean b) {
        if (!b) return;
        for (AbstractButton btn : buttons) {
            if (btn.getModel() == m) btn.setSelected(true);
        }
    }

    public boolean isSelected(ButtonModel m) { return selection != null && selection.getModel() == m; }
    public int getButtonCount() { return buttons.size(); }

    void jvm$select(AbstractButton b) {
        if (selection != null && selection != b) {
            AbstractButton old = selection;
            selection = b;
            old.jvm$setSelectedQuietly(false);
        }
        selection = b;
    }

    boolean jvm$requireSelection() { return true; }
}
