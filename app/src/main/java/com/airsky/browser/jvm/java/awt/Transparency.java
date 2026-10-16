package com.airsky.browser.jvm.java.awt;

public interface Transparency {
    int OPAQUE = 1;
    int BITMASK = 2;
    int TRANSLUCENT = 3;

    int getTransparency();
}
