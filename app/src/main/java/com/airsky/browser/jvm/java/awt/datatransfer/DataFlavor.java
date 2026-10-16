package com.airsky.browser.jvm.java.awt.datatransfer;

public class DataFlavor {
    public static final DataFlavor stringFlavor = new DataFlavor("application/x-java-serialized-object; class=java.lang.String", "Unicode String");
    public static final DataFlavor imageFlavor = new DataFlavor("image/x-java-image; class=java.awt.Image", "Image");
    public static final DataFlavor javaFileListFlavor = new DataFlavor("application/x-java-file-list; class=java.util.List", null);
    public static final DataFlavor allHtmlFlavor = new DataFlavor("text/html; document=all; class=java.lang.String", "HTML");
    @Deprecated public static final DataFlavor plainTextFlavor = new DataFlavor("text/plain; charset=unicode; class=java.io.InputStream", "Plain Text");

    private final String mime;
    private final String name;

    public DataFlavor() { this("application/octet-stream", null); }

    public DataFlavor(String mimeType, String humanPresentableName) {
        this.mime = mimeType;
        this.name = humanPresentableName;
    }

    public DataFlavor(String mimeType) { this(mimeType, null); }
    public String getMimeType() { return mime; }
    public String getHumanPresentableName() { return name; }
    @Override public boolean equals(Object o) { return o instanceof DataFlavor && ((DataFlavor) o).mime.equals(mime); }
    public boolean equals(DataFlavor f) { return f != null && f.mime.equals(mime); }
    @Override public int hashCode() { return mime.hashCode(); }
}
