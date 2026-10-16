package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Font;
import com.airsky.browser.jvm.java.awt.Paint;
import com.airsky.browser.jvm.javax.swing.border.BevelBorder;
import com.airsky.browser.jvm.javax.swing.border.Border;
import com.airsky.browser.jvm.javax.swing.border.CompoundBorder;
import com.airsky.browser.jvm.javax.swing.border.EmptyBorder;
import com.airsky.browser.jvm.javax.swing.border.EtchedBorder;
import com.airsky.browser.jvm.javax.swing.border.LineBorder;
import com.airsky.browser.jvm.javax.swing.border.MatteBorder;
import com.airsky.browser.jvm.javax.swing.border.TitledBorder;

public class BorderFactory {
    private BorderFactory() {}

    public static Border createEmptyBorder() { return new EmptyBorder(0, 0, 0, 0); }
    public static Border createEmptyBorder(int t, int l, int b, int r) { return new EmptyBorder(t, l, b, r); }
    public static Border createLineBorder(Color c) { return new LineBorder(c, 1); }
    public static Border createLineBorder(Color c, int t) { return new LineBorder(c, t); }
    public static Border createLineBorder(Color c, int t, boolean rounded) { return new LineBorder(c, t, rounded); }
    public static Border createDashedBorder(Paint p) { return new LineBorder(p instanceof Color ? (Color) p : Color.GRAY); }
    public static Border createDashedBorder(Paint p, float len, float space) { return createDashedBorder(p); }
    public static Border createRaisedBevelBorder() { return new BevelBorder(BevelBorder.RAISED); }
    public static Border createLoweredBevelBorder() { return new BevelBorder(BevelBorder.LOWERED); }
    public static Border createBevelBorder(int type) { return new BevelBorder(type); }
    public static Border createBevelBorder(int type, Color h, Color s) { return new BevelBorder(type); }
    public static Border createBevelBorder(int type, Color ho, Color hi, Color so, Color si) { return new BevelBorder(type); }
    public static Border createRaisedSoftBevelBorder() { return new BevelBorder(BevelBorder.RAISED); }
    public static Border createLoweredSoftBevelBorder() { return new BevelBorder(BevelBorder.LOWERED); }
    public static Border createEtchedBorder() { return new EtchedBorder(); }
    public static Border createEtchedBorder(int type) { return new EtchedBorder(type); }
    public static Border createEtchedBorder(Color h, Color s) { return new EtchedBorder(h, s); }
    public static Border createEtchedBorder(int type, Color h, Color s) { return new EtchedBorder(type, h, s); }
    public static TitledBorder createTitledBorder(String title) { return new TitledBorder(title); }
    public static TitledBorder createTitledBorder(Border b) { return new TitledBorder(b); }
    public static TitledBorder createTitledBorder(Border b, String title) { return new TitledBorder(b, title); }
    public static TitledBorder createTitledBorder(Border b, String t, int j, int p) { return new TitledBorder(b, t, j, p); }
    public static TitledBorder createTitledBorder(Border b, String t, int j, int p, Font f) { return new TitledBorder(b, t, j, p, f); }
    public static TitledBorder createTitledBorder(Border b, String t, int j, int p, Font f, Color c) { return new TitledBorder(b, t, j, p, f, c); }
    public static CompoundBorder createCompoundBorder() { return new CompoundBorder(); }
    public static CompoundBorder createCompoundBorder(Border o, Border i) { return new CompoundBorder(o, i); }
    public static MatteBorder createMatteBorder(int t, int l, int b, int r, Color c) { return new MatteBorder(t, l, b, r, c); }
    public static MatteBorder createMatteBorder(int t, int l, int b, int r, Icon icon) { return new MatteBorder(t, l, b, r, Color.GRAY); }
}
