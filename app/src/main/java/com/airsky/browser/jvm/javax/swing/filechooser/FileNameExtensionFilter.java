package com.airsky.browser.jvm.javax.swing.filechooser;

import java.io.File;
import java.util.Locale;

public final class FileNameExtensionFilter extends FileFilter {
    private final String description;
    private final String[] extensions;

    public FileNameExtensionFilter(String description, String... extensions) {
        if (extensions == null || extensions.length == 0) throw new IllegalArgumentException("Extensions must be non-null and not empty");
        this.description = description;
        this.extensions = extensions.clone();
    }

    @Override
    public boolean accept(File f) {
        if (f == null) return false;
        if (f.isDirectory()) return true;
        String n = f.getName().toLowerCase(Locale.ROOT);
        for (String e : extensions) if (n.endsWith("." + e.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    @Override public String getDescription() { return description; }
    public String[] getExtensions() { return extensions.clone(); }
}
