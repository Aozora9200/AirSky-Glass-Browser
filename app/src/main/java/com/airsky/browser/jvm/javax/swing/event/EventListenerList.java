package com.airsky.browser.jvm.javax.swing.event;

import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;

/** javax.swing.event.EventListenerList の互換クラス。 */
public class EventListenerList implements java.io.Serializable {
    private final List<Object> list = new ArrayList<>();

    public synchronized Object[] getListenerList() { return list.toArray(); }

    public synchronized int getListenerCount() { return list.size() / 2; }

    public synchronized int getListenerCount(Class<?> t) {
        int n = 0;
        for (int i = 0; i < list.size(); i += 2) if (list.get(i) == t) n++;
        return n;
    }

    @SuppressWarnings("unchecked")
    public synchronized <T extends EventListener> T[] getListeners(Class<T> t) {
        List<T> out = new ArrayList<>();
        for (int i = list.size() - 2; i >= 0; i -= 2) if (list.get(i) == t) out.add((T) list.get(i + 1));
        T[] arr = (T[]) java.lang.reflect.Array.newInstance(t, out.size());
        return out.toArray(arr);
    }

    public synchronized <T extends EventListener> void add(Class<T> t, T l) {
        if (l == null) return;
        list.add(t);
        list.add(l);
    }

    public synchronized <T extends EventListener> void remove(Class<T> t, T l) {
        for (int i = list.size() - 2; i >= 0; i -= 2) {
            if (list.get(i) == t && list.get(i + 1).equals(l)) {
                list.remove(i + 1);
                list.remove(i);
                return;
            }
        }
    }
}
