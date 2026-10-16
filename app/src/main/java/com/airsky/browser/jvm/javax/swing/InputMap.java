package com.airsky.browser.jvm.javax.swing;

import java.util.HashMap;
import java.util.Map;

public class InputMap implements java.io.Serializable {
    private final Map<KeyStroke, Object> map = new HashMap<>();
    private InputMap parent;

    public InputMap() {}

    public void setParent(InputMap p) { parent = p; }
    public InputMap getParent() { return parent; }

    public void put(KeyStroke k, Object key) {
        if (k == null) return;
        if (key == null) map.remove(k);
        else map.put(k, key);
    }

    public Object get(KeyStroke k) {
        Object v = map.get(k);
        return v == null && parent != null ? parent.get(k) : v;
    }

    public void remove(KeyStroke k) { map.remove(k); }
    public void clear() { map.clear(); }
    public KeyStroke[] keys() { return map.keySet().toArray(new KeyStroke[0]); }
    public KeyStroke[] allKeys() { return keys(); }
    public int size() { return map.size(); }
}
