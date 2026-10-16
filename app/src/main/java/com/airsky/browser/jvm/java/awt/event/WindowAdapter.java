package com.airsky.browser.jvm.java.awt.event;

public abstract class WindowAdapter implements WindowListener, WindowStateListener, WindowFocusListener {
    @Override public void windowOpened(WindowEvent e) {}
    @Override public void windowClosing(WindowEvent e) {}
    @Override public void windowClosed(WindowEvent e) {}
    @Override public void windowIconified(WindowEvent e) {}
    @Override public void windowDeiconified(WindowEvent e) {}
    @Override public void windowActivated(WindowEvent e) {}
    @Override public void windowDeactivated(WindowEvent e) {}
    @Override public void windowStateChanged(WindowEvent e) {}
    @Override public void windowGainedFocus(WindowEvent e) {}
    @Override public void windowLostFocus(WindowEvent e) {}
}
