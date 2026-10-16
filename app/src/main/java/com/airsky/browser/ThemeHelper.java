package com.airsky.browser;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * 設定画面の「テーマ」(ライト / ダーク / システム) を Material3 DayNight に反映する。
 */
public final class ThemeHelper {

    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME = "selected_theme";
    private static final int THEME_LIGHT = 0;
    private static final int THEME_DARK = 1;

    private ThemeHelper() {}

    public static void applyNightMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int theme = prefs.getInt(KEY_THEME, 2);
        int mode;
        switch (theme) {
            case THEME_LIGHT:
                mode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case THEME_DARK:
                mode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            default:
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
