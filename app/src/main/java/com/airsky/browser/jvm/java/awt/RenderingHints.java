package com.airsky.browser.jvm.java.awt;

import java.util.HashMap;
import java.util.Map;

/** java.awt.RenderingHints の互換クラス(描き方の希望。大半は気にせず描く)。 */
public class RenderingHints extends HashMap<Object, Object> implements Cloneable {

    public abstract static class Key {
        private final int key;

        protected Key(int privatekey) { key = privatekey; }

        public abstract boolean isCompatibleValue(Object val);

        protected final int intKey() { return key; }

        @Override public final int hashCode() { return System.identityHashCode(this); }
        @Override public final boolean equals(Object o) { return this == o; }
    }

    private static final class K extends Key {
        private final String name;

        K(int k, String name) {
            super(k);
            this.name = name;
        }

        @Override public boolean isCompatibleValue(Object val) { return val instanceof V; }
        @Override public String toString() { return name; }
    }

    private static final class V {
        private final String name;

        V(String name) { this.name = name; }

        @Override public String toString() { return name; }
    }

    public static final Key KEY_ANTIALIASING = new K(0, "Global antialiasing enable key");
    public static final Object VALUE_ANTIALIAS_ON = new V("Antialiased rendering mode");
    public static final Object VALUE_ANTIALIAS_OFF = new V("Nonantialiased rendering mode");
    public static final Object VALUE_ANTIALIAS_DEFAULT = new V("Default antialiasing rendering mode");
    public static final Key KEY_RENDERING = new K(1, "Global rendering quality key");
    public static final Object VALUE_RENDER_SPEED = new V("Fastest rendering methods");
    public static final Object VALUE_RENDER_QUALITY = new V("Highest quality rendering methods");
    public static final Object VALUE_RENDER_DEFAULT = new V("Default rendering methods");
    public static final Key KEY_DITHERING = new K(2, "Dithering quality key");
    public static final Object VALUE_DITHER_DISABLE = new V("Nondithered rendering mode");
    public static final Object VALUE_DITHER_ENABLE = new V("Dithered rendering mode");
    public static final Object VALUE_DITHER_DEFAULT = new V("Default dithering mode");
    public static final Key KEY_TEXT_ANTIALIASING = new K(3, "Text-specific antialiasing enable key");
    public static final Object VALUE_TEXT_ANTIALIAS_ON = new V("Antialiased text mode");
    public static final Object VALUE_TEXT_ANTIALIAS_OFF = new V("Nonantialiased text mode");
    public static final Object VALUE_TEXT_ANTIALIAS_DEFAULT = new V("Default antialiasing text mode");
    public static final Object VALUE_TEXT_ANTIALIAS_GASP = new V("gasp antialiasing text mode");
    public static final Object VALUE_TEXT_ANTIALIAS_LCD_HRGB = new V("LCD HRGB antialiasing text mode");
    public static final Object VALUE_TEXT_ANTIALIAS_LCD_HBGR = new V("LCD HBGR antialiasing text mode");
    public static final Object VALUE_TEXT_ANTIALIAS_LCD_VRGB = new V("LCD VRGB antialiasing text mode");
    public static final Object VALUE_TEXT_ANTIALIAS_LCD_VBGR = new V("LCD VBGR antialiasing text mode");
    public static final Key KEY_TEXT_LCD_CONTRAST = new K(100, "Text-specific LCD contrast key");
    public static final Key KEY_FRACTIONALMETRICS = new K(4, "Fractional metrics enable key");
    public static final Object VALUE_FRACTIONALMETRICS_OFF = new V("Integer text metrics mode");
    public static final Object VALUE_FRACTIONALMETRICS_ON = new V("Fractional text metrics mode");
    public static final Object VALUE_FRACTIONALMETRICS_DEFAULT = new V("Default fractional text metrics mode");
    public static final Key KEY_INTERPOLATION = new K(5, "Image interpolation method key");
    public static final Object VALUE_INTERPOLATION_NEAREST_NEIGHBOR = new V("Nearest Neighbor image interpolation mode");
    public static final Object VALUE_INTERPOLATION_BILINEAR = new V("Bilinear image interpolation mode");
    public static final Object VALUE_INTERPOLATION_BICUBIC = new V("Bicubic image interpolation mode");
    public static final Key KEY_ALPHA_INTERPOLATION = new K(6, "Alpha blending interpolation method key");
    public static final Object VALUE_ALPHA_INTERPOLATION_SPEED = new V("Fastest alpha blending methods");
    public static final Object VALUE_ALPHA_INTERPOLATION_QUALITY = new V("Highest quality alpha blending methods");
    public static final Object VALUE_ALPHA_INTERPOLATION_DEFAULT = new V("Default alpha blending methods");
    public static final Key KEY_COLOR_RENDERING = new K(7, "Color rendering quality key");
    public static final Object VALUE_COLOR_RENDER_SPEED = new V("Fastest color rendering mode");
    public static final Object VALUE_COLOR_RENDER_QUALITY = new V("Highest quality color rendering mode");
    public static final Object VALUE_COLOR_RENDER_DEFAULT = new V("Default color rendering mode");
    public static final Key KEY_STROKE_CONTROL = new K(8, "Stroke normalization control key");
    public static final Object VALUE_STROKE_DEFAULT = new V("Default stroke normalization");
    public static final Object VALUE_STROKE_NORMALIZE = new V("Normalize strokes for consistent rendering");
    public static final Object VALUE_STROKE_PURE = new V("Pure stroke conversion for accurate paths");

    public RenderingHints(Map<Key, ?> init) {
        if (init != null) putAll(init);
    }

    public RenderingHints(Key key, Object value) {
        if (key != null) put(key, value);
    }

    public void add(RenderingHints hints) { putAll(hints); }

    @Override
    public Object clone() { return super.clone(); }
}
