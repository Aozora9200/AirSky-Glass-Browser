package com.airsky.browser;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * アプリのアップデートの確認と通知。
 *
 * <p>GitHub の最新リリースのタグ(v4.1.0 など)と今のバージョンを数字で比べ、新しい時だけ知らせる
 * (同じ・古いタグでは知らせない)。アプリを開いた時は MainActivity がダイアログを出し、
 * 開いていない間は 1 日 1 回 {@link UpdateCheckJobService} が確認して通知を出す。
 * 同じバージョンの通知は 1 回だけ。設定「更新情報を表示」(updateDialog)をオフにすると両方止まる。</p>
 */
final class UpdateChecker {

    static final String RELEASE_API = "https://api.github.com/repos/Aozora9200/AirSky-Glass-Browser/releases/latest";

    private static final String PREFS = "AppPrefs";
    static final String KEY_ENABLED = "updateDialog";
    private static final String KEY_NOTIFIED = "updateNotifiedVersion";
    private static final String CHANNEL_ID = "app_update";
    private static final int NOTIFICATION_ID = 0x0A11;
    private static final int JOB_ID = 0x0A12;

    private UpdateChecker() {}

    /** 最新リリースの情報。 */
    static final class Release {
        final String version;
        final String notes;

        Release(String version, String notes) {
            this.version = version;
            this.notes = notes;
        }
    }

    static boolean isEnabled(@NonNull Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, true);
    }

    /** 最新リリースを取得する。取れなければ null。 */
    @WorkerThread
    @Nullable
    static Release fetchLatest() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(RELEASE_API).openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            if (conn.getResponseCode() / 100 != 2) return null;
            String result;
            try (InputStream in = conn.getInputStream(); Scanner scanner = new Scanner(in, "UTF-8")) {
                scanner.useDelimiter("\\A");
                result = scanner.hasNext() ? scanner.next() : "";
            }
            JSONObject json = new JSONObject(result);
            String tag = json.optString("tag_name", "");
            if (tag.isEmpty()) return null;
            return new Release(tag, json.optString("body", Lang.t("変更履歴はありません。")));
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** [release] が今入っているバージョンより新しいか。 */
    static boolean isNewer(@NonNull Context c, @NonNull Release release) {
        try {
            String current = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return compareVersions(release.version, current) > 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * バージョンを数字の並びとして比べる。"v4.1.0" と "4.1" のような書き方の違いは無視する
     * (先頭の v、足りない桁は 0、数字以外の後ろの部分は見ない)。
     */
    static int compareVersions(@Nullable String a, @Nullable String b) {
        int[] x = parse(a);
        int[] y = parse(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0;
            int q = i < y.length ? y[i] : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int[] parse(@Nullable String v) {
        if (v == null) return new int[0];
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        String[] parts = s.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            int end = 0;
            while (end < p.length() && Character.isDigit(p.charAt(end))) end++;
            if (end == 0) {
                // 4.1-beta のように数字が途切れたら、そこまでで比べる
                int[] cut = new int[i];
                System.arraycopy(out, 0, cut, 0, i);
                return cut;
            }
            try {
                out[i] = Integer.parseInt(p.substring(0, end));
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 通知
    // ------------------------------------------------------------------

    /** まだ知らせていないバージョンなら通知を出す。出したら true。 */
    static boolean notifyIfNew(@NonNull Context c, @NonNull Release release) {
        SharedPreferences p = prefs(c);
        if (release.version.equals(p.getString(KEY_NOTIFIED, null))) return false;
        if (!canNotify(c)) return false;
        ensureChannel(c);
        Intent open = UpdateActivity.intent(c, release.version).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, NOTIFICATION_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String notes = release.notes == null ? "" : release.notes.trim();
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_ps_update)
                .setContentTitle(Lang.t("AirSky のアップデートがあります"))
                .setContentText(Lang.t("バージョン ") + release.version + Lang.t(" が利用可能です。タップして更新します"))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        Lang.t("バージョン ") + release.version + Lang.t(" が利用可能です。タップして更新します")
                                + (notes.isEmpty() ? "" : "\n\n" + Markdown.strip(notes))))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        try {
            NotificationManagerCompat.from(c).notify(NOTIFICATION_ID, b.build());
        } catch (SecurityException e) {
            return false;
        }
        p.edit().putString(KEY_NOTIFIED, release.version).apply();
        return true;
    }

    /** アプリ内でアップデートを始めたら、残っている通知は消す。 */
    static void cancelNotification(@NonNull Context c) {
        NotificationManagerCompat.from(c).cancel(NOTIFICATION_ID);
    }

    static boolean canNotify(@NonNull Context c) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(c).areNotificationsEnabled();
    }

    private static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, Lang.t("アプリのアップデート"),
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription(Lang.t("AirSky の新しいバージョンが公開された時にお知らせします"));
        nm.createNotificationChannel(ch);
    }

    // ------------------------------------------------------------------
    // 定期的な確認
    // ------------------------------------------------------------------

    /** 1 日 1 回の確認を予約する(予約済みなら何もしない)。設定がオフなら予約を消す。 */
    static void schedule(@NonNull Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        if (!isEnabled(c)) {
            js.cancel(JOB_ID);
            return;
        }
        if (js.getPendingJob(JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, UpdateCheckJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(TimeUnit.DAYS.toMillis(1))
                .build();
        try {
            js.schedule(job);
        } catch (RuntimeException ignored) { // 予約の上限など。次に開いた時にまた試す
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
