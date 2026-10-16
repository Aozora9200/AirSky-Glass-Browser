package com.airsky.browser.jvm.javax.swing;

import java.util.HashMap;
import java.util.Map;

public class ActionMap implements java.io.Serializable {
    private final Map<Object, Action> map = new HashMap<>();
    private ActionMap parent;

    public ActionMap() {}

    public void setParent(ActionMap p) { parent = p; }
    public ActionMap getParent() { return parent; }

    public void put(Object key, Action a) {
        if (key == null) return;
        if (a == null) map.remove(key);
        else map.put(key, a);
    }

    public Action get(Object key) {
        Action a = map.get(key);
        return a == null && parent != null ? parent.get(key) : a;
    }

    public void remove(Object key) { map.remove(key); }
    public void clear() { map.clear(); }
    public Object[] keys() { return map.keySet().toArray(); }
    public Object[] allKeys() { return keys(); }
    public int size() { return map.size(); }
}
