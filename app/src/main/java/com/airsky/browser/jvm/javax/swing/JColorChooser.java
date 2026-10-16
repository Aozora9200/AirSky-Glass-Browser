package com.airsky.browser.jvm.javax.swing;

import com.airsky.browser.Lang;

import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Component;

/** javax.swing.JColorChooser(よく使う色から選ぶ)。 */
public class JColorChooser extends JComponent {
    private static final String[] NAMES = {Lang.t("黒"), Lang.t("白"), Lang.t("灰色"), Lang.t("赤"), Lang.t("オレンジ"), Lang.t("黄"), Lang.t("緑"), Lang.t("シアン"), Lang.t("青"), Lang.t("紫"), Lang.t("ピンク"), Lang.t("茶")};
    private static final Color[] COLORS = {Color.BLACK, Color.WHITE, Color.GRAY, Color.RED, Color.ORANGE, Color.YELLOW,
            Color.GREEN, Color.CYAN, Color.BLUE, new Color(0x80, 0x00, 0x80), Color.PINK, new Color(0x8B, 0x45, 0x13)};

    private Color color = Color.WHITE;

    public JColorChooser() {}
    public JColorChooser(Color c) { color = c; }

    public Color getColor() { return color; }
    public void setColor(Color c) { color = c; }

    public static Color showDialog(Component parent, String title, Color initial) {
        int i = JvmRuntime.blocking(() -> JvmRuntime.host().showChoice(title, NAMES, -1), -1);
        return i >= 0 ? COLORS[i] : null;
    }
}
