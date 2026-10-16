package com.airsky.browser;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebNotification;
import org.mozilla.geckoview.WebNotificationDelegate;

/**
 * サイトからの通知(Web Notifications)を Android の通知として表示する。
 * どのサイトが通知を出せるかは 設定 › サイトの設定 › 通知 で決まる。
 */
final class WebNotifications implements WebNotificationDelegate {

    private static final String CHANNEL_ID = "web_notifications";

    private final Context appContext;

    private WebNotifications(Context appContext) {
        this.appContext = appContext;
    }

    static void install(@NonNull Context context, @NonNull GeckoRuntime runtime) {
        runtime.setWebNotificationDelegate(new WebNotifications(context.getApplicationContext()));
    }

    @Override
    public void onShowNotification(@NonNull WebNotification n) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(appContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ensureChannel();
        String site = SitePermissions.hostOf(n.origin != null ? n.origin : n.source);
        Intent open = new Intent(Intent.ACTION_VIEW,
                Uri.parse(n.source != null ? n.source : (n.origin != null ? n.origin : "about:blank")),
                appContext, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(appContext, id(n), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_web)
                .setContentTitle(n.title != null ? n.title : site)
                .setContentText(n.text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(n.text))
                .setSubText(site)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setSilent(n.silent);
        try {
            NotificationManagerCompat.from(appContext).notify(id(n), b.build());
        } catch (SecurityException ignored) {
        }
    }

    @Override
    public void onCloseNotification(@NonNull WebNotification n) {
        NotificationManagerCompat.from(appContext).cancel(id(n));
    }

    private static int id(WebNotification n) {
        return ("web:" + n.tag).hashCode();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = appContext.getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, Lang.t("サイトからの通知"),
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
    }
}
