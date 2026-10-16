package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Container;

public interface RootPaneContainer {
    JRootPane getRootPane();
    void setContentPane(Container contentPane);
    Container getContentPane();
    void setLayeredPane(JLayeredPane layeredPane);
    JLayeredPane getLayeredPane();
    void setGlassPane(Component glassPane);
    Component getGlassPane();
}
