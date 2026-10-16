package com.airsky.browser.jvm.java.awt;

public class GraphicsDevice {
    public static final GraphicsDevice DEFAULT = new GraphicsDevice();

    protected GraphicsDevice() {}

    public GraphicsConfiguration getDefaultConfiguration() { return GraphicsConfiguration.DEFAULT; }
    public GraphicsConfiguration[] getConfigurations() { return new GraphicsConfiguration[]{GraphicsConfiguration.DEFAULT}; }
    public boolean isFullScreenSupported() { return false; }

    public void setFullScreenWindow(Window w) {
        if (w != null) {
            w.setSize(com.airsky.browser.jvm.JvmRuntime.screenWidth(), com.airsky.browser.jvm.JvmRuntime.screenHeight());
            w.setVisible(true);
        }
    }

    public Window getFullScreenWindow() { return null; }
    public String getIDstring() { return "screen0"; }
    public DisplayMode getDisplayMode() {
        return new DisplayMode(com.airsky.browser.jvm.JvmRuntime.screenWidth(),
                com.airsky.browser.jvm.JvmRuntime.screenHeight(), 32, 60);
    }
}
