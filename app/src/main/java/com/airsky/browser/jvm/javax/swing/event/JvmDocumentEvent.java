package com.airsky.browser.jvm.javax.swing.event;

import com.airsky.browser.jvm.javax.swing.text.Document;

public final class JvmDocumentEvent implements DocumentEvent {
    private final Document doc;
    private final EventType type;
    private final int offset, length;

    public JvmDocumentEvent(Document doc, EventType type, int offset, int length) {
        this.doc = doc;
        this.type = type;
        this.offset = offset;
        this.length = length;
    }

    @Override public int getOffset() { return offset; }
    @Override public int getLength() { return length; }
    @Override public Document getDocument() { return doc; }
    @Override public EventType getType() { return type; }
}
