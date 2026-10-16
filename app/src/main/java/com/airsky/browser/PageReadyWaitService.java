package com.airsky.browser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * 「ページの準備ができたらお知らせ」で待っている間、アプリを裏に回してもプロセスが
 * 止められないようにするためのフォアグラウンド サービス。
 *
 * 回線の監視とページの読み込み直しは PageReadyNotifier が行う。このサービスは
 * 「待っています」の通知を出してプロセスを生かしておくだけ。
 */
public class PageReadyWaitService extends Service {

    static final String CHANNEL_ID = "page_ready_wait";
    private static final int NOTIFICATION_ID = 0x0EAD0FFF;
    /** 念のため、これ以上は待たない(PageReadyNotifier の待ち時間と同じ)。 */
    private static final long MAX_ALIVE_MS = 2 * 60 * 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    static void start(Context context, int count) {
        Intent i = new Intent(context, PageReadyWaitService.class).putExtra("count", count);
        try {
            ContextCompat.startForegroundService(context, i);
        } catch (Exception ignored) {
            // 裏からは起動できない等: 通知は前面にいる間しか届かないが、動作は続ける
        }
    }

    static void stop(Context context) {
        try {
            context.stopService(new Intent(context, PageReadyWaitService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        int count = intent != null ? intent.getIntExtra("count", 1) : 1;
        Notification n = buildNotification(count);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::stopSelf, MAX_ALIVE_MS);
        // プロセスが落ちたら待っていたタブも無いので、作り直さない
        return START_NOT_STICKY;
    }

    private Notification buildNotification(int count) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, Lang.t("ページの準備を待っています"), NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = count > 1
                ? count + Lang.t(" 件のページを、インターネットにつながったら読み込みます")
                : Lang.t("インターネットにつながったらページを読み込んでお知らせします");
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle(Lang.t("ページの準備を待っています"))
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build();
    }

    @Override
    public void onTimeout(int startId) {
        // Android 15 以降: dataSync の時間切れ
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
