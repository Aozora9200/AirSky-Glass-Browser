package com.airsky.browser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

/**
 * ページの動画・音声をアプリの外(ホーム画面・画面オフ)でも再生し続けるためのサービス。
 *
 * <p>GeckoView の MediaSession(ページのメディア)を、Android の MediaSession と
 * メディア通知(再生/一時停止/停止)につなぐ。再生している間はフォアグラウンドサービスとして
 * プロセスを生かしておき、ロック画面・イヤホンのボタンからも操作できるようにする。</p>
 */
public class MediaPlaybackService extends Service {

    private static final String TAG = "MediaPlayback";
    private static final String CHANNEL_ID = "media_playback";
    private static final int NOTIFICATION_ID = 0x4D45; // "ME"

    private static final String ACTION_UPDATE = "com.airsky.browser.media.UPDATE";
    private static final String ACTION_PLAY = "com.airsky.browser.media.PLAY";
    private static final String ACTION_PAUSE = "com.airsky.browser.media.PAUSE";
    private static final String ACTION_STOP = "com.airsky.browser.media.STOP";

    /** 設定「バックグラウンド再生」のキー(既定の SharedPreferences)。 */
    public static final String PREF_BACKGROUND_PLAYBACK = "background_playback";

    // ------------------------------------------------------------------
    // タブ側(GeckoTabView)から呼ぶ窓口
    // ------------------------------------------------------------------

    /** 今メディア通知に出しているタブ。 */
    @Nullable private static GeckoTabView current;
    @Nullable private static String title;
    @Nullable private static String artist;
    @Nullable private static Bitmap artwork;
    private static boolean playing;

    public static boolean isEnabled(@NonNull Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c).getBoolean(PREF_BACKGROUND_PLAYBACK, true);
    }

    /** タブのメディアが再生/一時停止された。 */
    static void onMediaState(@NonNull GeckoTabView tab, boolean isPlaying) {
        if (tab.isPrivateMode() && !isPlaying && current != tab) return;
        Context c = tab.getContext().getApplicationContext();
        if (!isEnabled(c)) {
            stopFor(tab);
            return;
        }
        if (current != tab) {
            if (!isPlaying) return; // 他のタブの一時停止は関係ない
            current = tab;
            title = null;
            artist = null;
            artwork = null;
        }
        playing = isPlaying;
        send(c, ACTION_UPDATE);
    }

    /** タブのメディアの情報(曲名・アーティスト・画像)が変わった。 */
    static void onMetadata(@NonNull GeckoTabView tab, @Nullable String t, @Nullable String a, @Nullable Bitmap art) {
        if (current != tab) return;
        title = t;
        artist = a;
        if (art != null) artwork = art;
        send(tab.getContext().getApplicationContext(), ACTION_UPDATE);
    }

    /** タブのメディアが終わった / タブが閉じられた。 */
    static void stopFor(@NonNull GeckoTabView tab) {
        if (current != tab) return;
        current = null;
        playing = false;
        artwork = null;
        Context c = tab.getContext().getApplicationContext();
        c.stopService(new Intent(c, MediaPlaybackService.class));
    }

    private static void send(Context c, String action) {
        Intent i = new Intent(c, MediaPlaybackService.class).setAction(action);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (RuntimeException e) {
            // アプリが裏にある時は Android 12 以降フォアグラウンドサービスを始められない
            Log.w(TAG, "メディア通知を開始できません", e);
        }
    }

    // ------------------------------------------------------------------
    // サービス本体
    // ------------------------------------------------------------------

    private final Handler main = new Handler(Looper.getMainLooper());
    @Nullable private MediaSession mediaSession;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaSession = new MediaSession(this, "AozoraMedia");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                control(ACTION_PLAY);
            }

            @Override
            public void onPause() {
                control(ACTION_PAUSE);
            }

            @Override
            public void onStop() {
                control(ACTION_STOP);
            }
        });
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        // startForegroundService の後は必ず startForeground を呼ぶ
        startInForeground();
        String action = intent != null ? intent.getAction() : null;
        if (current == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_PLAY.equals(action) || ACTION_PAUSE.equals(action) || ACTION_STOP.equals(action)) {
            control(action);
        }
        return START_NOT_STICKY;
    }

    private void control(String action) {
        GeckoTabView tab = current;
        if (tab == null) {
            stopSelf();
            return;
        }
        main.post(() -> {
            if (ACTION_PLAY.equals(action)) {
                tab.playMedia();
                playing = true;
            } else if (ACTION_PAUSE.equals(action)) {
                tab.pauseMedia();
                playing = false;
            } else {
                tab.stopMedia();
                stopFor(tab);
                return;
            }
            startInForeground();
        });
    }

    private void startInForeground() {
        Notification n = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
            // 一時停止中は通知を残したままフォアグラウンドをやめる(スワイプで消せるように)
            if (!playing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "フォアグラウンドにできません", e);
        }
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null
                && nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, Lang.t("メディアの再生"),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(Lang.t("バックグラウンドで再生中の動画や音声を操作します"));
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }

        String shownTitle = !TextUtils.isEmpty(title) ? title
                : current != null && !TextUtils.isEmpty(current.getTitle()) ? current.getTitle() : Lang.t("メディアを再生中");
        String shownText = !TextUtils.isEmpty(artist) ? artist : hostOf(current);

        if (mediaSession != null) {
            MediaMetadata.Builder meta = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, shownTitle)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, shownText);
            if (artwork != null) meta.putBitmap(MediaMetadata.METADATA_KEY_ART, artwork);
            mediaSession.setMetadata(meta.build());
            mediaSession.setPlaybackState(new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                            | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP)
                    .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                            PlaybackState.PLAYBACK_POSITION_UNKNOWN, playing ? 1f : 0f)
                    .build());
        }

        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_media)
                .setContentTitle(shownTitle)
                .setContentText(shownText)
                .setContentIntent(content)
                .setDeleteIntent(action(ACTION_STOP, 3))
                .setOngoing(playing)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT);
        if (artwork != null) b.setLargeIcon(artwork);
        if (playing) {
            b.addAction(new Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    Lang.t("一時停止"), action(ACTION_PAUSE, 1)).build());
        } else {
            b.addAction(new Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_play),
                    Lang.t("再生"), action(ACTION_PLAY, 1)).build());
        }
        b.addAction(new Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_close),
                Lang.t("停止"), action(ACTION_STOP, 2)).build());
        Notification.MediaStyle style = new Notification.MediaStyle().setShowActionsInCompactView(0, 1);
        if (mediaSession != null) style.setMediaSession(mediaSession.getSessionToken());
        b.setStyle(style);
        return b.build();
    }

    private PendingIntent action(String action, int requestCode) {
        Intent i = new Intent(this, MediaPlaybackService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @NonNull
    private static String hostOf(@Nullable GeckoTabView tab) {
        if (tab == null) return "AirSky";
        try {
            String host = android.net.Uri.parse(tab.getUrl()).getHost();
            if (host == null) return "AirSky";
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return "AirSky";
        }
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        super.onDestroy();
    }
}
