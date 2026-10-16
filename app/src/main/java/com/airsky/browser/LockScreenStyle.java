package com.airsky.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

/**
 * パスワード・PIN 画面(壁紙の上に半透明の幕を重ねた画面)の文字を読みやすくする。
 *
 * <p>幕は暗い(黒)か明るい(白)かのどちらかなので、文字はその反対の色にして薄い影を付ける。
 * テーマの色(colorOnPrimary など)に任せると、幕と同じような色になって読めないことがあった。</p>
 */
final class LockScreenStyle {

    private static final int LIGHT_TEXT = 0xFFFFFFFF;
    private static final int DARK_TEXT = 0xFF111317;

    private LockScreenStyle() {
    }

    /** 幕が暗いか(各画面の applyBackTheme と同じ判定)。 */
    static boolean isDarkOverlay(Context c) {
        SharedPreferences prefs = c.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE);
        int theme = prefs.getInt("selected_theme", 2);
        if (theme == 0) return false;
        if (theme == 1) return true;
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    static int textColor(boolean dark) {
        return dark ? LIGHT_TEXT : DARK_TEXT;
    }

    /** root の中の文字をすべて読みやすい色にする。 */
    static void apply(View root, boolean dark) {
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                apply(g.getChildAt(i), dark);
            }
        } else if (root instanceof TextView) {
            style((TextView) root, dark);
        }
    }

    /** 1 つの文字の色と影を整える(あとから作る PIN の表示にも使う)。 */
    static void style(TextView t, boolean dark) {
        int color = textColor(dark);
        t.setTextColor(color);
        t.setShadowLayer(6f, 0f, 1f, dark ? 0x99000000 : 0x99FFFFFF);
        if (t instanceof EditText) {
            t.setHintTextColor((color & 0x00FFFFFF) | 0xA6000000);
            t.setBackgroundTintList(ColorStateList.valueOf(color));
        } else if (t instanceof CompoundButton) {
            ((CompoundButton) t).setButtonTintList(ColorStateList.valueOf(color));
        } else if (t.getId() == R.id.logtext || t.getId() == R.id.setpassword_msg) {
            // 一番上の案内文は少し太くする
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
    }
}
