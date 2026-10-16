package com.airsky.browser.jvm.java.awt.event;

import com.airsky.browser.jvm.java.awt.Component;
import com.airsky.browser.jvm.java.awt.Point;

public class MouseEvent extends InputEvent {
    public static final int MOUSE_FIRST = 500;
    public static final int MOUSE_LAST = 507;
    public static final int MOUSE_CLICKED = 500;
    public static final int MOUSE_PRESSED = 501;
    public static final int MOUSE_RELEASED = 502;
    public static final int MOUSE_MOVED = 503;
    public static final int MOUSE_ENTERED = 504;
    public static final int MOUSE_EXITED = 505;
    public static final int MOUSE_DRAGGED = 506;
    public static final int MOUSE_WHEEL = 507;
    public static final int NOBUTTON = 0;
    public static final int BUTTON1 = 1;
    public static final int BUTTON2 = 2;
    public static final int BUTTON3 = 3;

    int x, y, xAbs, yAbs, clickCount, button;
    boolean popupTrigger;

    public MouseEvent(Component source, int id, long when, int modifiers, int x, int y, int clickCount,
                      boolean popupTrigger, int button) {
        this(source, id, when, modifiers, x, y, x, y, clickCount, popupTrigger, button);
    }

    public MouseEvent(Component source, int id, long when, int modifiers, int x, int y, int clickCount,
                      boolean popupTrigger) {
        this(source, id, when, modifiers, x, y, clickCount, popupTrigger, NOBUTTON);
    }

    public MouseEvent(Component source, int id, long when, int modifiers, int x, int y, int xAbs, int yAbs,
                      int clickCount, boolean popupTrigger, int button) {
        super(source, id, when, modifiers);
        this.x = x;
        this.y = y;
        this.xAbs = xAbs;
        this.yAbs = yAbs;
        this.clickCount = clickCount;
        this.popupTrigger = popupTrigger;
        this.button = button;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public Point getPoint() { return new Point(x, y); }
    public int getXOnScreen() { return xAbs; }
    public int getYOnScreen() { return yAbs; }
    public Point getLocationOnScreen() { return new Point(xAbs, yAbs); }

    public synchronized void translatePoint(int dx, int dy) {
        x += dx;
        y += dy;
    }

    public int getClickCount() { return clickCount; }
    public int getButton() { return button; }
    public boolean isPopupTrigger() { return popupTrigger; }

    public static String getMouseModifiersText(int m) { return getModifiersExText(m); }

    @Override
    public String paramString() { return "id=" + id + ",(" + x + "," + y + "),button=" + button + ",clickCount=" + clickCount; }
}
