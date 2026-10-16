package com.airsky.browser.jvm.javax.swing;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashMap;
import java.util.Map;

/** javax.swing.AbstractAction の互換クラス。 */
public abstract class AbstractAction implements Action, Cloneable, java.io.Serializable {
    protected boolean enabled = true;
    private final Map<String, Object> values = new HashMap<>();
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);

    public AbstractAction() {}
    public AbstractAction(String name) { putValue(NAME, name); }

    public AbstractAction(String name, Icon icon) {
        putValue(NAME, name);
        putValue(SMALL_ICON, icon);
    }

    @Override public Object getValue(String key) { return values.get(key); }

    @Override
    public void putValue(String key, Object value) {
        Object old = values.put(key, value);
        changes.firePropertyChange(key, old, value);
    }

    @Override public boolean isEnabled() { return enabled; }

    @Override
    public void setEnabled(boolean b) {
        boolean old = enabled;
        enabled = b;
        changes.firePropertyChange("enabled", old, b);
    }

    public Object[] getKeys() { return values.keySet().toArray(); }
    @Override public void addPropertyChangeListener(PropertyChangeListener l) { changes.addPropertyChangeListener(l); }
    @Override public void removePropertyChangeListener(PropertyChangeListener l) { changes.removePropertyChangeListener(l); }
    public PropertyChangeListener[] getPropertyChangeListeners() { return changes.getPropertyChangeListeners(); }

    protected void firePropertyChange(String name, Object o, Object n) { changes.firePropertyChange(name, o, n); }

    @Override
    protected Object clone() throws CloneNotSupportedException { return super.clone(); }
}
