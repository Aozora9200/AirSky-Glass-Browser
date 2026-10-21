package com.airsky.browser.coara;

public interface PluginViewOps {
    void registerCommand(String pluginId, String cmdId, String title);

    void unregisterCommand(String pluginId, String cmdId);

    void openTab(String url);

    void notifyUser(String title, String text);

    boolean copyText(String text);
}
