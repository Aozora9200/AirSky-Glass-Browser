package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Font;

/** javax.swing.UIManager の互換クラス(見た目はいつも AirSky の Pixel 風)。 */
public class UIManager implements java.io.Serializable {

    public static class LookAndFeelInfo {
        private final String name, className;

        public LookAndFeelInfo(String name, String className) {
            this.name = name;
            this.className = className;
        }

        public String getName() { return name; }
        public String getClassName() { return className; }
        @Override public String toString() { return name + " " + className; }
    }

    private static final UIDefaults defaults = new UIDefaults();
    private static final LookAndFeel LAF = new LookAndFeel() {
        @Override public String getName() { return "Metal"; }
        @Override public String getID() { return "Metal"; }
        @Override public String getDescription() { return "AirSky Pixel"; }
        @Override public boolean isNativeLookAndFeel() { return true; }
        @Override public boolean isSupportedLookAndFeel() { return true; }
    };

    private UIManager() {}

    public static LookAndFeelInfo[] getInstalledLookAndFeels() {
        return new LookAndFeelInfo[]{
                new LookAndFeelInfo("Metal", "javax.swing.plaf.metal.MetalLookAndFeel"),
                new LookAndFeelInfo("Nimbus", "javax.swing.plaf.nimbus.NimbusLookAndFeel"),
        };
    }

    public static LookAndFeel getLookAndFeel() { return LAF; }

    public static void setLookAndFeel(String className) throws ClassNotFoundException, InstantiationException,
            IllegalAccessException, UnsupportedLookAndFeelException {}

    public static void setLookAndFeel(LookAndFeel laf) throws UnsupportedLookAndFeelException {}

    public static String getSystemLookAndFeelClassName() { return "javax.swing.plaf.metal.MetalLookAndFeel"; }
    public static String getCrossPlatformLookAndFeelClassName() { return "javax.swing.plaf.metal.MetalLookAndFeel"; }
    public static UIDefaults getDefaults() { return defaults; }
    public static UIDefaults getLookAndFeelDefaults() { return defaults; }
    public static Object get(Object key) { return defaults.get(key); }
    public static Object put(Object key, Object value) { return defaults.put(key, value); }
    public static Color getColor(Object key) { return defaults.getColor(key); }
    public static Font getFont(Object key) { return defaults.getFont(key); }
    public static int getInt(Object key) { return defaults.getInt(key); }
    public static boolean getBoolean(Object key) { return defaults.getBoolean(key); }
    public static String getString(Object key) { return defaults.getString(key); }
    public static void installLookAndFeel(String name, String className) {}
}
