package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.event.InputEvent;
import com.airsky.browser.jvm.java.awt.event.KeyEvent;

/** javax.swing.KeyStroke の互換クラス。 */
public final class KeyStroke implements java.io.Serializable {
    private static final int MASK = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
            | InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;

    private final char keyChar;
    private final int keyCode, modifiers;
    private final boolean onRelease;

    private KeyStroke(char keyChar, int keyCode, int modifiers, boolean onRelease) {
        this.keyChar = keyChar;
        this.keyCode = keyCode;
        this.modifiers = normalize(modifiers);
        this.onRelease = onRelease;
    }

    private static int normalize(int m) {
        int r = m & MASK;
        if ((m & InputEvent.SHIFT_MASK) != 0) r |= InputEvent.SHIFT_DOWN_MASK;
        if ((m & InputEvent.CTRL_MASK) != 0) r |= InputEvent.CTRL_DOWN_MASK;
        if ((m & InputEvent.META_MASK) != 0) r |= InputEvent.META_DOWN_MASK;
        if ((m & InputEvent.ALT_MASK) != 0) r |= InputEvent.ALT_DOWN_MASK;
        return r;
    }

    public static KeyStroke getKeyStroke(char c) { return new KeyStroke(c, KeyEvent.VK_UNDEFINED, 0, false); }
    public static KeyStroke getKeyStroke(Character c, int modifiers) { return new KeyStroke(c, KeyEvent.VK_UNDEFINED, modifiers, false); }
    public static KeyStroke getKeyStroke(int keyCode, int modifiers) { return new KeyStroke(KeyEvent.CHAR_UNDEFINED, keyCode, modifiers, false); }
    public static KeyStroke getKeyStroke(int keyCode, int modifiers, boolean onRelease) {
        return new KeyStroke(KeyEvent.CHAR_UNDEFINED, keyCode, modifiers, onRelease);
    }

    public static KeyStroke getKeyStrokeForEvent(KeyEvent e) {
        if (e.getID() == KeyEvent.KEY_TYPED) return new KeyStroke(e.getKeyChar(), KeyEvent.VK_UNDEFINED, e.getModifiersEx() | e.getModifiers(), false);
        return new KeyStroke(KeyEvent.CHAR_UNDEFINED, e.getKeyCode(), e.getModifiersEx() | e.getModifiers(),
                e.getID() == KeyEvent.KEY_RELEASED);
    }

    public static KeyStroke getKeyStroke(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        String[] t = s.trim().split("\\s+");
        int mods = 0;
        boolean released = false, typed = false;
        for (int i = 0; i < t.length; i++) {
            String w = t[i];
            switch (w) {
                case "shift": mods |= InputEvent.SHIFT_DOWN_MASK; continue;
                case "control": case "ctrl": mods |= InputEvent.CTRL_DOWN_MASK; continue;
                case "meta": mods |= InputEvent.META_DOWN_MASK; continue;
                case "alt": mods |= InputEvent.ALT_DOWN_MASK; continue;
                case "altGraph": mods |= InputEvent.ALT_GRAPH_DOWN_MASK; continue;
                case "pressed": continue;
                case "released": released = true; continue;
                case "typed": typed = true; continue;
                default: break;
            }
            if (i != t.length - 1) return null;
            if (typed) return w.length() == 1 ? new KeyStroke(w.charAt(0), KeyEvent.VK_UNDEFINED, mods, false) : null;
            try {
                int code = KeyEvent.class.getField("VK_" + w).getInt(null);
                return new KeyStroke(KeyEvent.CHAR_UNDEFINED, code, mods, released);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public char getKeyChar() { return keyChar; }
    public int getKeyCode() { return keyCode; }
    public int getModifiers() { return modifiers; }
    public boolean isOnKeyRelease() { return onRelease; }

    public int getKeyEventType() {
        if (keyCode == KeyEvent.VK_UNDEFINED) return KeyEvent.KEY_TYPED;
        return onRelease ? KeyEvent.KEY_RELEASED : KeyEvent.KEY_PRESSED;
    }

    @Override
    public int hashCode() { return (keyChar + 1) * 2 * (keyCode + 1) * (modifiers + 1) + (onRelease ? 1 : 2); }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof KeyStroke)) return false;
        KeyStroke k = (KeyStroke) o;
        return k.keyChar == keyChar && k.keyCode == keyCode && k.modifiers == modifiers && k.onRelease == onRelease;
    }

    @Override
    public String toString() {
        return (keyCode == KeyEvent.VK_UNDEFINED ? "typed " + keyChar : (onRelease ? "released " : "pressed ") + KeyEvent.getKeyText(keyCode));
    }
}
