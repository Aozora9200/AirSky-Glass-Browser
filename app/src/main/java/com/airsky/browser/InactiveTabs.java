package com.airsky.browser;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * 非アクティブなタブ(Chrome のタブの整理と同じ)の設定。
 *
 * <p>しばらく使っていないタブ・グループと、同じページを開いている重複したタブ・グループを、
 * タブ一覧の一番上の「非アクティブなタブ」にまとめる。まとめたタブは 3 か月使わなければ自動的に閉じる。</p>
 */
final class InactiveTabs {

    private static final String PREFS = "inactive_tabs";
    private static final String KEY_DAYS = "days";
    private static final String KEY_DUPLICATES = "archive_duplicates";
    private static final String KEY_AUTO_CLOSE = "auto_close";

    /** 選べる期間(日)。0 は「使用しない」。 */
    static final int[] DAY_OPTIONS = {0, 7, 14, 21, 28};
    static final int DEFAULT_DAYS = 28;

    static final long DAY_MS = 24L * 60 * 60 * 1000;
    /** 非アクティブなタブを自動的に閉じるまでの期間(3 か月)。 */
    static final long AUTO_CLOSE_MS = 90L * DAY_MS;

    private InactiveTabs() {}

    private static SharedPreferences prefs(@NonNull Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 非アクティブにするまでの日数。0 なら機能を使わない。 */
    static int days(@NonNull Context c) {
        return prefs(c).getInt(KEY_DAYS, DEFAULT_DAYS);
    }

    static void setDays(@NonNull Context c, int days) {
        prefs(c).edit().putInt(KEY_DAYS, days).apply();
    }

    static boolean isEnabled(@NonNull Context c) {
        return days(c) > 0;
    }

    /** 重複しているタブ・グループも非アクティブにする。 */
    static boolean archiveDuplicates(@NonNull Context c) {
        return isEnabled(c) && prefs(c).getBoolean(KEY_DUPLICATES, true);
    }

    static boolean archiveDuplicatesSetting(@NonNull Context c) {
        return prefs(c).getBoolean(KEY_DUPLICATES, true);
    }

    static void setArchiveDuplicates(@NonNull Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_DUPLICATES, on).apply();
    }

    /** 非アクティブなタブを 3 か月後に自動的に閉じる。 */
    static boolean autoClose(@NonNull Context c) {
        return isEnabled(c) && prefs(c).getBoolean(KEY_AUTO_CLOSE, true);
    }

    static boolean autoCloseSetting(@NonNull Context c) {
        return prefs(c).getBoolean(KEY_AUTO_CLOSE, true);
    }

    static void setAutoClose(@NonNull Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_AUTO_CLOSE, on).apply();
    }

    /** 非アクティブにするまでの時間(ミリ秒)。使わないなら Long.MAX_VALUE。 */
    static long thresholdMs(@NonNull Context c) {
        int d = days(c);
        return d > 0 ? d * DAY_MS : Long.MAX_VALUE;
    }

    @NonNull
    static String label(int days) {
        return days <= 0 ? Lang.t("使用しない") : days + Lang.t(" 日間経過後");
    }

    /** 設定画面の入口に出す今の設定。 */
    @NonNull
    static String summary(@NonNull Context c) {
        int d = days(c);
        if (d <= 0) return Lang.t("使用しない");
        return d + Lang.t(" 日間使用していないタブ") + (archiveDuplicates(c) ? Lang.t("と重複しているタブ") : "")
                + Lang.t("を非アクティブにします");
    }
}
