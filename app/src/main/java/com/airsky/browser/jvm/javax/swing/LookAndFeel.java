package com.airsky.browser.jvm.javax.swing;

public abstract class LookAndFeel {
    public abstract String getName();
    public abstract String getID();
    public abstract String getDescription();
    public abstract boolean isNativeLookAndFeel();
    public abstract boolean isSupportedLookAndFeel();
    public UIDefaults getDefaults() { return UIManager.getDefaults(); }
    public void initialize() {}
    public void uninitialize() {}
}
