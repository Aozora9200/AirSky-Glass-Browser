package com.airsky.browser;

import android.app.Activity;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 一画面/二画面の設定と、二画面に使う 2 つ目の画面の見つけ方。
 *
 * <p>二画面は AYN Thor のように画面を 2 つ持つ端末向け。アプリが開いている画面を上の画面、
 * もう 1 つの画面を下の画面として使う。</p>
 */
final class DualScreen {

    private static final String PREFS = "AppPrefs";
    private static final String KEY_DUAL = "dual_screen";

    private DualScreen() {
    }

    /** 二画面を使う設定か(2 つ目の画面が無ければ、設定していても一画面で表示する)。 */
    static boolean isEnabled(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DUAL, false);
    }

    /** 一画面/二画面をもう選んだか(初回設定で、2 つ目の画面がある端末に二画面を勧めるため)。 */
    static boolean isChosen(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_DUAL);
    }

    static void setEnabled(@NonNull Context context, boolean dual) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DUAL, dual).apply();
    }

    /** 2 つ目の画面がつながっているか(設定画面の案内用)。 */
    static boolean hasSecondScreen(@NonNull Activity activity) {
        return findSecondScreen(activity) != null;
    }

    /** 下の画面として使う、アプリが開いていない方の画面。見つからなければ null。 */
    @Nullable
    static Display findSecondScreen(@NonNull Activity activity) {
        DisplayManager dm = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return null;
        int own = ownDisplayId(activity);
        // 画面を共有するための表示先(プレゼンテーション用)として出ている画面を優先する
        for (Display d : dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (usable(d, own)) return d;
        }
        for (Display d : dm.getDisplays()) {
            if (usable(d, own) && (d.getFlags() & Display.FLAG_PRIVATE) == 0) return d;
        }
        return null;
    }

    private static boolean usable(@NonNull Display d, int own) {
        return d.getDisplayId() != own && d.isValid() && d.getState() != Display.STATE_OFF;
    }

    @SuppressWarnings("deprecation")
    private static int ownDisplayId(@NonNull Activity activity) {
        Display d = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? activity.getDisplay()
                : activity.getWindowManager().getDefaultDisplay();
        return d != null ? d.getDisplayId() : Display.DEFAULT_DISPLAY;
    }
}
