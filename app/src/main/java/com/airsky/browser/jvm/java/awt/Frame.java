package com.airsky.browser.jvm.java.awt;

/** java.awt.Frame の互換クラス。 */
public class Frame extends Window {
    public static final int NORMAL = 0;
    public static final int ICONIFIED = 1;
    public static final int MAXIMIZED_HORIZ = 2;
    public static final int MAXIMIZED_VERT = 4;
    public static final int MAXIMIZED_BOTH = 6;
    @Deprecated public static final int DEFAULT_CURSOR = 0;
    @Deprecated public static final int HAND_CURSOR = 12;

    private String title = "";
    private boolean resizable = true;
    private boolean undecorated;
    private int state;
    private MenuBar menuBar;

    public Frame() { this(""); }

    public Frame(String title) {
        super((Window) null);
        this.title = title != null ? title : "";
        this.background = null;
    }

    public Frame(GraphicsConfiguration gc) { this(""); }
    public Frame(String title, GraphicsConfiguration gc) { this(title); }

    public String getTitle() { return title; }

    public void setTitle(String t) {
        title = t != null ? t : "";
        if (jvm$isShown()) com.airsky.browser.jvm.JvmRuntime.addWindow(this);
    }

    public boolean isResizable() { return resizable; }
    public void setResizable(boolean b) { resizable = b; }
    public boolean isUndecorated() { return undecorated; }
    public void setUndecorated(boolean b) { undecorated = b; }
    public int getExtendedState() { return state; }

    public void setExtendedState(int s) {
        state = s;
        if ((s & MAXIMIZED_BOTH) == MAXIMIZED_BOTH) {
            setSize(com.airsky.browser.jvm.JvmRuntime.screenWidth(), com.airsky.browser.jvm.JvmRuntime.screenHeight());
        }
    }

    public int getState() { return state & ICONIFIED; }
    public void setState(int s) { state = s; }
    public void setMaximizedBounds(Rectangle r) {}
    public Image getIconImage() { java.util.List<Image> l = getIconImages(); return l.isEmpty() ? null : l.get(0); }
    public MenuBar getMenuBar() { return menuBar; }
    public void setMenuBar(MenuBar mb) { menuBar = mb; }

    public static Frame[] getFrames() {
        java.util.ArrayList<Frame> out = new java.util.ArrayList<>();
        for (Window w : com.airsky.browser.jvm.JvmRuntime.windows()) if (w instanceof Frame) out.add((Frame) w);
        return out.toArray(new Frame[0]);
    }

    @Override
    public String jvm$title() { return title; }
}
