package com.airsky.browser.jvm.javax.swing.text;

public class BadLocationException extends Exception {
    private final int offs;

    public BadLocationException(String s, int offs) {
        super(s);
        this.offs = offs;
    }

    public int offsetRequested() { return offs; }
}
