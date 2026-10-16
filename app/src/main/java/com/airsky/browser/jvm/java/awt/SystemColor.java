package com.airsky.browser.jvm.java.awt;

import com.airsky.browser.jvm.JvmLook;

public final class SystemColor extends Color {
    public static final SystemColor desktop = new SystemColor(0x2D4F6C);
    public static final SystemColor activeCaption = new SystemColor(0x0B57D0);
    public static final SystemColor activeCaptionText = new SystemColor(0xFFFFFF);
    public static final SystemColor window = new SystemColor(0xFFFFFF);
    public static final SystemColor windowText = new SystemColor(0x1F1F1F);
    public static final SystemColor menu = new SystemColor(JvmLook.PANEL.getRGB() & 0xFFFFFF);
    public static final SystemColor menuText = new SystemColor(0x1F1F1F);
    public static final SystemColor text = new SystemColor(0xFFFFFF);
    public static final SystemColor textText = new SystemColor(0x1F1F1F);
    public static final SystemColor control = new SystemColor(JvmLook.PANEL.getRGB() & 0xFFFFFF);
    public static final SystemColor controlText = new SystemColor(0x1F1F1F);
    public static final SystemColor controlShadow = new SystemColor(0xA0A0A0);
    public static final SystemColor controlHighlight = new SystemColor(0xFFFFFF);
    public static final SystemColor info = new SystemColor(0xFFFFE1);
    public static final SystemColor infoText = new SystemColor(0x000000);

    private SystemColor(int rgb) { super(rgb); }
}
