package com.airsky.browser.jvm.javax.swing;

public class ComponentInputMap extends InputMap {
    private final JComponent c;

    public ComponentInputMap(JComponent c) { this.c = c; }

    public JComponent getComponent() { return c; }
}
