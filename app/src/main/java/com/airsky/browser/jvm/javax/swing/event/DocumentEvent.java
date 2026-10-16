package com.airsky.browser.jvm.javax.swing.event;

import com.airsky.browser.jvm.javax.swing.text.Document;

public interface DocumentEvent {
    final class EventType {
        public static final EventType INSERT = new EventType("INSERT");
        public static final EventType REMOVE = new EventType("REMOVE");
        public static final EventType CHANGE = new EventType("CHANGE");
        private final String name;

        private EventType(String name) { this.name = name; }

        @Override public String toString() { return name; }
    }

    int getOffset();
    int getLength();
    Document getDocument();
    EventType getType();
}
