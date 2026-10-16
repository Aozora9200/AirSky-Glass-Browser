package com.airsky.browser.jvm.java.awt.image;

import com.airsky.browser.jvm.java.awt.Image;

public interface ImageObserver {
    int WIDTH = 1;
    int HEIGHT = 2;
    int PROPERTIES = 4;
    int SOMEBITS = 8;
    int FRAMEBITS = 16;
    int ALLBITS = 32;
    int ERROR = 64;
    int ABORT = 128;

    boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height);
}
