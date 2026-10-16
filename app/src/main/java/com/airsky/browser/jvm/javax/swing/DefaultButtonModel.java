package com.airsky.browser.jvm.javax.swing;

/** ボタンの状態(AbstractButton の状態をそのまま見せる)。 */
public class DefaultButtonModel implements ButtonModel {
    AbstractButton button;
    private boolean armed, selected, enabled = true, pressed, rollover;
    private String command;
    private ButtonGroup group;

    public DefaultButtonModel() {}

    @Override public boolean isArmed() { return button != null ? button.jvm$pressed() : armed; }
    @Override public boolean isSelected() { return button != null ? button.isSelected() : selected; }
    @Override public boolean isEnabled() { return button != null ? button.isEnabled() : enabled; }
    @Override public boolean isPressed() { return button != null ? button.jvm$pressed() : pressed; }
    @Override public boolean isRollover() { return button != null ? button.jvm$pressed() : rollover; }
    @Override public void setArmed(boolean b) { armed = b; }

    @Override
    public void setSelected(boolean b) {
        if (button != null) button.setSelected(b);
        else selected = b;
    }

    @Override
    public void setEnabled(boolean b) {
        if (button != null) button.setEnabled(b);
        else enabled = b;
    }

    @Override public void setPressed(boolean b) { pressed = b; }
    @Override public void setRollover(boolean b) { rollover = b; }
    @Override public void setActionCommand(String s) { command = s; }
    @Override public String getActionCommand() { return button != null ? button.getActionCommand() : command; }
    @Override public void setGroup(ButtonGroup g) { group = g; }
    public ButtonGroup getGroup() { return group; }
}
