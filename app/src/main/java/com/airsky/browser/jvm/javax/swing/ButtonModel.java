package com.airsky.browser.jvm.javax.swing;

public interface ButtonModel {
    boolean isArmed();
    boolean isSelected();
    boolean isEnabled();
    boolean isPressed();
    boolean isRollover();
    void setArmed(boolean b);
    void setSelected(boolean b);
    void setEnabled(boolean b);
    void setPressed(boolean b);
    void setRollover(boolean b);
    void setActionCommand(String s);
    String getActionCommand();
    void setGroup(ButtonGroup group);
}
