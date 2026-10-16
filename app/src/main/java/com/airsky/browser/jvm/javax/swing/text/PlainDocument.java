package com.airsky.browser.jvm.javax.swing.text;

import com.airsky.browser.jvm.javax.swing.event.DocumentEvent;
import com.airsky.browser.jvm.javax.swing.event.DocumentListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** 文字欄の中身。変わると DocumentListener に知らせる。 */
public class PlainDocument implements Document {
    private final StringBuilder content = new StringBuilder();
    private final CopyOnWriteArrayList<DocumentListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Object, Object> props = new HashMap<>();
    Runnable onChange;

    public PlainDocument() {}

    @Override public synchronized int getLength() { return content.length(); }
    @Override public void addDocumentListener(DocumentListener l) { if (l != null) listeners.add(l); }
    @Override public void removeDocumentListener(DocumentListener l) { listeners.remove(l); }
    public DocumentListener[] getDocumentListeners() { return listeners.toArray(new DocumentListener[0]); }

    @Override
    public void remove(int offs, int len) throws BadLocationException {
        if (len <= 0) return;
        synchronized (this) {
            if (offs < 0 || offs + len > content.length()) throw new BadLocationException("Invalid remove", offs);
            content.delete(offs, offs + len);
        }
        fire(DocumentEvent.EventType.REMOVE, offs, len);
    }

    @Override
    public void insertString(int offset, String str, AttributeSet a) throws BadLocationException {
        if (str == null || str.isEmpty()) return;
        synchronized (this) {
            if (offset < 0 || offset > content.length()) throw new BadLocationException("Invalid insert", offset);
            content.insert(offset, str);
        }
        fire(DocumentEvent.EventType.INSERT, offset, str.length());
    }

    @Override
    public synchronized String getText(int offset, int length) throws BadLocationException {
        if (offset < 0 || length < 0 || offset + length > content.length()) throw new BadLocationException("Invalid range", offset);
        return content.substring(offset, offset + length);
    }

    @Override public Object getProperty(Object key) { return props.get(key); }
    @Override public void putProperty(Object key, Object value) { props.put(key, value); }

    public synchronized String jvm$text() { return content.toString(); }

    private void fire(DocumentEvent.EventType type, int offset, int length) {
        if (onChange != null) onChange.run();
        DocumentEvent e = new com.airsky.browser.jvm.javax.swing.event.JvmDocumentEvent(this, type, offset, length);
        for (DocumentListener l : listeners) {
            if (type == DocumentEvent.EventType.INSERT) l.insertUpdate(e);
            else l.removeUpdate(e);
        }
    }
}
