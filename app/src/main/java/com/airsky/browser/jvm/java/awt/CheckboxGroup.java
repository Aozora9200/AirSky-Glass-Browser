package com.airsky.browser.jvm.java.awt;

/** java.awt.CheckboxGroup の互換クラス。 */
public class CheckboxGroup implements java.io.Serializable {
    private Checkbox selected;

    public Checkbox getSelectedCheckbox() { return selected; }

    public void setSelectedCheckbox(Checkbox box) {
        if (selected != null && selected != box) selected.jvm$setStateOnly(false);
        selected = box;
        if (box != null && !box.getState()) box.jvm$setStateOnly(true);
    }
}
