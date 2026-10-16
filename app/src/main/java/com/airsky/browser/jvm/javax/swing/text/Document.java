package com.airsky.browser.jvm.javax.swing.text;

import com.airsky.browser.jvm.javax.swing.event.DocumentListener;

public interface Document {
    String StreamDescriptionProperty = "stream";
    String TitleProperty = "title";

    int getLength();
    void addDocumentListener(DocumentListener l);
    void removeDocumentListener(DocumentListener l);
    void remove(int offs, int len) throws BadLocationException;
    void insertString(int offset, String str, AttributeSet a) throws BadLocationException;
    String getText(int offset, int length) throws BadLocationException;
    Object getProperty(Object key);
    void putProperty(Object key, Object value);
}
