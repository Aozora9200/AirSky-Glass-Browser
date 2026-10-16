package com.airsky.browser.jvm.java.awt.event;

import com.airsky.browser.jvm.java.awt.AWTEvent;
import com.airsky.browser.jvm.java.awt.ItemSelectable;

public class ItemEvent extends AWTEvent {
    public static final int ITEM_FIRST = 701;
    public static final int ITEM_LAST = 701;
    public static final int ITEM_STATE_CHANGED = 701;
    public static final int SELECTED = 1;
    public static final int DESELECTED = 2;
    private final Object item;
    private final int stateChange;

    public ItemEvent(ItemSelectable source, int id, Object item, int stateChange) {
        super(source, id);
        this.item = item;
        this.stateChange = stateChange;
    }

    public ItemSelectable getItemSelectable() { return (ItemSelectable) source; }
    public Object getItem() { return item; }
    public int getStateChange() { return stateChange; }
}
