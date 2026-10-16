package com.airsky.browser.jvm.java.awt;

/** java.awt.MediaTracker(画像はいつも読み込み済みなので待たない)。 */
public class MediaTracker implements java.io.Serializable {
    public static final int LOADING = 1;
    public static final int ABORTED = 2;
    public static final int ERRORED = 4;
    public static final int COMPLETE = 8;

    public MediaTracker(Component c) {}

    public void addImage(Image img, int id) {}
    public void addImage(Image img, int id, int w, int h) {}
    public void removeImage(Image img) {}
    public void waitForAll() throws InterruptedException {}
    public boolean waitForAll(long ms) throws InterruptedException { return true; }
    public void waitForID(int id) throws InterruptedException {}
    public boolean waitForID(int id, long ms) throws InterruptedException { return true; }
    public boolean checkAll() { return true; }
    public boolean checkAll(boolean load) { return true; }
    public boolean checkID(int id) { return true; }
    public boolean checkID(int id, boolean load) { return true; }
    public boolean isErrorAny() { return false; }
    public boolean isErrorID(int id) { return false; }
    public int statusAll(boolean load) { return COMPLETE; }
    public int statusID(int id, boolean load) { return COMPLETE; }
}
