package com.airsky.browser.jvm;

import android.graphics.Paint;
import android.graphics.Typeface;

import com.airsky.browser.jvm.java.awt.Font;
import com.airsky.browser.jvm.java.awt.FontMetrics;

import java.util.HashMap;
import java.util.Map;

/** AWT の Font を Android の書体に当てる。 */
public final class JvmFonts {
    private static final Map<Font, FontMetrics> metrics = new HashMap<>();

    private JvmFonts() {}

    public static Typeface typeface(Font f) {
        String n = f.getName() == null ? "" : f.getName().toLowerCase(java.util.Locale.ROOT);
        Typeface base;
        if (f.jvm$typeface() != null) {
            base = f.jvm$typeface();
        } else if (n.contains("mono") || n.contains("courier") || n.contains("consol") || n.equals("dialoginput")) {
            base = Typeface.MONOSPACE;
        } else if (n.contains("serif") && !n.contains("sans") || n.contains("times") || n.contains("mincho")
                || n.contains("明朝") || n.equals("georgia")) {
            base = Typeface.SERIF;
        } else {
            base = Typeface.SANS_SERIF;
        }
        int style = Typeface.NORMAL;
        if (f.isBold() && f.isItalic()) style = Typeface.BOLD_ITALIC;
        else if (f.isBold()) style = Typeface.BOLD;
        else if (f.isItalic()) style = Typeface.ITALIC;
        return Typeface.create(base, style);
    }

    public static void apply(Paint p, Font f) {
        p.setTypeface(typeface(f));
        p.setTextSize(Math.max(1f, f.getSize2D()));
    }

    public static synchronized FontMetrics metrics(Font f) {
        FontMetrics m = metrics.get(f);
        if (m == null) {
            m = new FontMetrics(f) {};
            metrics.put(f, m);
        }
        return m;
    }
}
