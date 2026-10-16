package com.airsky.browser.jvm;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * プログラムの作業フォルダと、プログラムから見えるシステムプロパティ。
 *
 * <p>JAR の中の相対パス(new File("data.txt") など)は、変換の時に {@link #resolve} を通すように
 * 書き換えて、このフォルダからの位置にする。</p>
 *
 * <p>System.getProperty / System.getProperties も変換の時にここへ付け替える。Android の Java は
 * java.version に "0" を返すなど、デスクトップの Java と違う値を返すので、Java の版を調べるプログラムが
 * 止まらないように、JAR が作られた版のデスクトップ Java らしい値にそろえる。</p>
 */
public final class JvmFiles {
    private static volatile File home = new File("/");
    private static volatile Map<String, String> overrides = javaProps(8);

    private JvmFiles() {}

    public static void setHome(File dir) {
        home = dir;
    }

    public static File home() {
        return home;
    }

    /** プログラムに見せる Java の版(8, 11, 17, 21 …)。 */
    public static void setJavaFeature(int feature) {
        overrides = javaProps(feature);
    }

    private static Map<String, String> javaProps(int feature) {
        Map<String, String> m = new HashMap<>();
        boolean legacy = feature <= 8;
        String version = legacy ? "1.8.0_402" : feature + ".0.2";
        String spec = legacy ? "1.8" : String.valueOf(feature);
        m.put("java.version", version);
        m.put("java.runtime.version", legacy ? "1.8.0_402-b06" : version + "+13");
        m.put("java.specification.version", spec);
        m.put("java.vm.specification.version", spec);
        m.put("java.class.version", (44 + Math.max(feature, 8)) + ".0");
        m.put("java.vendor", "AirSky");
        m.put("java.vm.vendor", "AirSky");
        m.put("java.vm.name", "OpenJDK 64-Bit Server VM");
        m.put("java.runtime.name", "OpenJDK Runtime Environment");
        m.put("java.vm.version", version);
        m.put("java.specification.vendor", "Oracle Corporation");
        m.put("java.specification.name", "Java Platform API Specification");
        m.put("os.name", "Linux");
        m.put("java.awt.headless", "false");
        m.put("file.encoding", "UTF-8");
        m.put("sun.jnu.encoding", "UTF-8");
        m.put("sun.arch.data.model", System.getProperty("os.arch", "").contains("64") ? "64" : "32");
        return m;
    }

    public static String resolve(String path) {
        if (path == null) return null;
        if (path.startsWith("/")) return path;
        if (path.isEmpty() || path.equals(".")) return home.getPath();
        if (path.startsWith("./")) path = path.substring(2);
        return new File(home, path).getPath();
    }

    /** System.getProperty の代わり。 */
    public static String getProperty(String key) {
        if ("user.dir".equals(key) || "user.home".equals(key)) return home.getPath();
        if ("java.home".equals(key)) return new File(home, ".java").getPath();
        String v = overrides.get(key);
        return v != null ? v : System.getProperty(key);
    }

    public static String getProperty(String key, String def) {
        String v = getProperty(key);
        return v != null ? v : def;
    }

    /** System.getProperties の代わり(上の値に置き換えた写し)。 */
    public static Properties getProperties() {
        Properties p = new Properties();
        p.putAll(System.getProperties());
        p.putAll(overrides);
        p.setProperty("user.dir", home.getPath());
        p.setProperty("user.home", home.getPath());
        p.setProperty("java.home", new File(home, ".java").getPath());
        return p;
    }
}
