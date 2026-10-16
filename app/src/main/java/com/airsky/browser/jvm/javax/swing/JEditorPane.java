package com.airsky.browser.jvm.javax.swing;

/** javax.swing.JEditorPane(書式は使わず、文字だけ表示する)。 */
public class JEditorPane extends JTextArea {
    private String contentType = "text/plain";

    public JEditorPane() {
        setLineWrap(true);
        setWrapStyleWord(true);
    }

    public JEditorPane(String type, String text) {
        this();
        setContentType(type);
        setText(text);
    }

    public void setContentType(String t) { contentType = t; }
    public String getContentType() { return contentType; }

    @Override
    public void setText(String t) {
        super.setText(contentType != null && contentType.contains("html") ? String.join("\n", com.airsky.browser.jvm.JvmLook.lines("<html>" + t)) : t);
    }

    public void setPage(String url) {}
    public void setPage(java.net.URL url) {}
    public void addHyperlinkListener(Object l) {}
}
