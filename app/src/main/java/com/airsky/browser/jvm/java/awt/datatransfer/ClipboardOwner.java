package com.airsky.browser.jvm.java.awt.datatransfer;

public interface ClipboardOwner {
    void lostOwnership(Clipboard clipboard, Transferable contents);
}
