package com.airsky.browser;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * タブレット UI の設定(設定 › デザイン › 画面、初回設定の「画面」)。
 *
 * <p>タブレット UI では、パソコンのブラウザのようにタブを上に並べ、URL バーとボトムバーを
 * 1 本のツールバーにまとめる。二画面とは同時に使えない(どちらかを選ぶと、もう片方はオフになる)。</p>
 */
final class TabletUi {

    private static final String PREFS = "AppPrefs";
    private static final String KEY_TABLET = "tablet_ui";
    /** タブレット UI のバー(タブ・ツールバー)を画面の下に出す。既定は上。 */
    private static final String KEY_BARS_BOTTOM = "tablet_bars_bottom";

    private TabletUi() {
    }

    static boolean barsAtBottom(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BARS_BOTTOM, false);
    }

    static void setBarsAtBottom(@NonNull Context context, boolean bottom) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_BARS_BOTTOM, bottom).apply();
    }

    /**
     * タブレット UI でバーを上に出している。この時はメニュー(シート)や「タブを閉じました」などの
     * メッセージも、指を伸ばさなくてよいよう画面の上(ツールバーのすぐ下)に出す。
     */
    static boolean barsOnTop(@NonNull Context context) {
        return isEnabled(context) && !barsAtBottom(context);
    }

    static boolean isEnabled(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TABLET, false);
    }

    static void setEnabled(@NonNull Context context, boolean tablet) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_TABLET, tablet).apply();
    }

    /** 画面の使い方。初回設定と設定画面の 3 つの選択肢。 */
    static final int MODE_SINGLE = 0;
    static final int MODE_DUAL = 1;
    static final int MODE_TABLET = 2;

    static int mode(@NonNull Context context) {
        if (isEnabled(context)) return MODE_TABLET;
        return DualScreen.isEnabled(context) ? MODE_DUAL : MODE_SINGLE;
    }

    static void setMode(@NonNull Context context, int mode) {
        setEnabled(context, mode == MODE_TABLET);
        DualScreen.setEnabled(context, mode == MODE_DUAL);
    }
}
