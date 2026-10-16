package com.airsky.browser.jvm.javax.swing.filechooser;

public abstract class FileFilter {
    public abstract boolean accept(java.io.File f);
    public abstract String getDescription();
}
