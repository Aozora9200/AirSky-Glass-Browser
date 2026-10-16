package com.airsky.browser.jvm.javax.swing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

/** javax.swing.DefaultListModel の互換クラス。 */
public class DefaultListModel<E> extends AbstractListModel<E> {
    private final List<E> list = new ArrayList<>();

    @Override public synchronized int getSize() { return list.size(); }
    @Override public synchronized E getElementAt(int i) { return list.get(i); }
    public synchronized int size() { return list.size(); }
    public synchronized boolean isEmpty() { return list.isEmpty(); }
    public synchronized E get(int i) { return list.get(i); }
    public synchronized E elementAt(int i) { return list.get(i); }
    public synchronized E firstElement() { return list.get(0); }
    public synchronized E lastElement() { return list.get(list.size() - 1); }
    public synchronized boolean contains(Object o) { return list.contains(o); }
    public synchronized int indexOf(Object o) { return list.indexOf(o); }
    public synchronized int lastIndexOf(Object o) { return list.lastIndexOf(o); }
    public synchronized Object[] toArray() { return list.toArray(); }
    public Enumeration<E> elements() { return java.util.Collections.enumeration(new ArrayList<>(list)); }
    public void copyInto(Object[] arr) { list.toArray(arr); }
    public void ensureCapacity(int n) {}
    public void trimToSize() {}
    public int capacity() { return list.size(); }

    public void addElement(E e) {
        int i;
        synchronized (this) {
            list.add(e);
            i = list.size() - 1;
        }
        fireIntervalAdded(this, i, i);
    }

    public void add(int index, E e) {
        synchronized (this) {
            list.add(index, e);
        }
        fireIntervalAdded(this, index, index);
    }

    public void addAll(Collection<? extends E> c) {
        int start;
        synchronized (this) {
            start = list.size();
            list.addAll(c);
        }
        if (!c.isEmpty()) fireIntervalAdded(this, start, start + c.size() - 1);
    }

    public void insertElementAt(E e, int index) { add(index, e); }

    public E set(int index, E e) {
        E old;
        synchronized (this) {
            old = list.set(index, e);
        }
        fireContentsChanged(this, index, index);
        return old;
    }

    public void setElementAt(E e, int index) { set(index, e); }

    public E remove(int index) {
        E old;
        synchronized (this) {
            old = list.remove(index);
        }
        fireIntervalRemoved(this, index, index);
        return old;
    }

    public void removeElementAt(int index) { remove(index); }

    public boolean removeElement(Object o) {
        int i = indexOf(o);
        if (i < 0) return false;
        remove(i);
        return true;
    }

    public void removeRange(int from, int to) {
        synchronized (this) {
            for (int i = to; i >= from; i--) list.remove(i);
        }
        fireIntervalRemoved(this, from, to);
    }

    public void clear() {
        int n;
        synchronized (this) {
            n = list.size();
            list.clear();
        }
        if (n > 0) fireIntervalRemoved(this, 0, n - 1);
    }

    public void removeAllElements() { clear(); }

    @Override public synchronized String toString() { return list.toString(); }
}
