package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Font;

public class UIDefaults extends java.util.Hashtable<Object, Object> {
    public UIDefaults() {}
    public Color getColor(Object k) { Object v = get(k); return v instanceof Color ? (Color) v : null; }
    public Font getFont(Object k) { Object v = get(k); return v instanceof Font ? (Font) v : null; }
    public int getInt(Object k) { Object v = get(k); return v instanceof Integer ? (Integer) v : 0; }
    public boolean getBoolean(Object k) { Object v = get(k); return v instanceof Boolean && (Boolean) v; }
    public String getString(Object k) { Object v = get(k); return v instanceof String ? (String) v : null; }

    @Override
    public synchronized Object put(Object key, Object value) {
        if (value == null) return remove(key);
        return super.put(key, value);
    }
}
