package com.airsky.browser;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 「ページの準備ができたらお知らせ」(Chrome のオフライン時の機能と同じ考え方)。
 *
 * インターネットに接続できずにエラーページになったタブを覚えておき、端末がインターネットに
 * つながった(Android が通信できると確認した)らそのタブを裏で読み込み直す。読み込みに成功したら
 * 通知と画面上部の案内で知らせ、通知を押すとそのタブを開く。
 *
 * エラーページのボタンは "aozora-action:notify-ready?t=トークン" へのリンクで、トークンは
 * エラーページを作る時に発行したものだけを受け付ける(普通のサイトからは呼べない)。
 *
 * <p>アプリを使っている最中(前面)にオンラインへ戻った時は、お知らせと読み込み直しの予約を取り消す。
 * 戻った直後にユーザーが別のページへ移ると、遅れて届いた「準備ができました」で開けなかったページが
 * また勝手に開かれてしまうため(画面を見ているなら、自分で開き直せる)。
 * 待っているタブで別のページへ移った時も同じく取り消す。</p>
 */
final class PageReadyNotifier {

    interface Host {
        /** タブ ID のタブ(破棄済みなら読み込み直す)。閉じられていれば null。 */
        @Nullable GeckoTabView tabForId(int tabId);
        int tabIdOf(@NonNull GeckoTabView tab);
        boolean isSecret();
        /** 画面が前面に出ているか(出ていれば通知の代わりに上部の案内を出す)。 */
        boolean isInForeground();
    }

    static final String SCHEME = "aozora-action:";
    static final String EXTRA_TAB_ID = "com.airsky.browser.extra.READY_TAB_ID";
    private static final String CHANNEL_ID = "page_ready";
    /** 待つのはこの時間まで(Chrome と同じく、いつまでも待たない)。 */
    private static final long MAX_WAIT_MS = 2 * 60 * 60 * 1000L;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 0x0D2;

    private static final class Watch {
        int tabId;
        String url;
        long since;
        /** つながったので読み込み直している最中。 */
        boolean reloading;
        /** アプリが裏にあるので、読み込みのためだけにタブを動かしている。 */
        boolean wokeInBackground;
    }

    /** 裏で読み込み直す時、この時間で終わらなければ諦めて次の接続を待つ。 */
    private static final long BACKGROUND_LOAD_TIMEOUT_MS = 60 * 1000L;

    private final Activity activity;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Watch> watches = new ArrayList<>();
    /** エラーページに埋め込んだトークン → 失敗した URL。 */
    private final Map<String, String> tokens = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    @Nullable private ConnectivityManager.NetworkCallback callback;
    /** 最後に調べた時、通信できるネットワークにつながっていたか(確認前も含む)。 */
    private boolean connected;
    /** 最後に調べた時、Android がインターネットに通信できると確認済みだったか。 */
    private boolean validated;

    PageReadyNotifier(@NonNull Activity activity, @NonNull Host host) {
        this.activity = activity;
        this.host = host;
    }

    /** エラーページのボタン用のリンク。 */
    @NonNull
    String createLink(@NonNull String failingUrl) {
        byte[] b = new byte[16];
        random.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        String token = sb.toString();
        tokens.put(token, failingUrl);
        return SCHEME + "notify-ready?t=" + token;
    }

    /** "aozora-action:" のリンクを処理する。自分のリンクなら true。 */
    boolean handleUrl(@NonNull GeckoTabView tab, @NonNull String url) {
        if (!url.startsWith(SCHEME)) return false;
        int q = url.indexOf("?t=");
        String token = q >= 0 ? url.substring(q + 3) : null;
        String failingUrl = token != null ? tokens.remove(token) : null;
        if (failingUrl == null) return true; // 発行していないトークンは無視する
        watch(tab, failingUrl);
        return true;
    }

    private void watch(@NonNull GeckoTabView tab, @NonNull String url) {
        int id = host.tabIdOf(tab);
        if (id < 0) return;
        for (Iterator<Watch> it = watches.iterator(); it.hasNext(); ) {
            if (it.next().tabId == id) it.remove();
        }
        Watch w = new Watch();
        w.tabId = id;
        w.url = url;
        w.since = SystemClock.elapsedRealtime();
        watches.add(w);
        requestNotificationPermission();
        Toast.makeText(activity, Lang.t("ページの準備ができたらお知らせします"), Toast.LENGTH_SHORT).show();
        registerCallback();
        updateWaitService();
    }

    /**
     * 待っているタブがある間はフォアグラウンド サービスを動かし、アプリを裏に回しても
     * 回線の監視と読み込み直しが止められないようにする(通知がアプリの外でも届くように)。
     */
    private void updateWaitService() {
        Context ctx = activity.getApplicationContext();
        if (watches.isEmpty()) {
            PageReadyWaitService.stop(ctx);
        } else {
            PageReadyWaitService.start(ctx, watches.size());
        }
    }

    // ------------------------------------------------------------------
    // 回線の監視
    // ------------------------------------------------------------------

    private void registerCallback() {
        if (callback != null) return;
        ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        connected = isConnectedNow(cm, false);
        validated = isConnectedNow(cm, true);
        // オフラインではない(サーバー側の問題など)なら、これまでどおりすぐに読み込み直してみる
        if (validated) handler.post(this::onOnline);
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                handler.post(PageReadyNotifier.this::onNetworkChanged);
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
                handler.post(PageReadyNotifier.this::onNetworkChanged);
            }

            @Override
            public void onLost(@NonNull Network network) {
                handler.post(PageReadyNotifier.this::onNetworkChanged);
            }
        };
        NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        try {
            cm.registerNetworkCallback(req, callback);
        } catch (Exception e) {
            callback = null;
        }
    }

    private void unregisterCallback() {
        if (callback == null) return;
        ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            if (cm != null) cm.unregisterNetworkCallback(callback);
        } catch (Exception ignored) {
        }
        callback = null;
    }

    /** 今のネットワーク(既定のネットワーク)でインターネットにつながっているか。 */
    private static boolean isConnectedNow(@NonNull ConnectivityManager cm, boolean requireValidated) {
        try {
            Network active = cm.getActiveNetwork();
            NetworkCapabilities caps = active != null ? cm.getNetworkCapabilities(active) : null;
            if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false;
            return !requireValidated || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ネットワークの状態が変わった。オフライン → オンラインに変わった時だけ動く。
     * 前面ではお知らせと読み込み直しの予約を取り消し、裏にいる時は(通信を確認できてから)読み込み直す。
     */
    private void onNetworkChanged() {
        if (callback == null) return;
        ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        boolean nowConnected = isConnectedNow(cm, false);
        boolean nowValidated = isConnectedNow(cm, true);
        boolean becameConnected = nowConnected && !connected;
        boolean becameValidated = nowValidated && !validated;
        connected = nowConnected;
        validated = nowValidated;
        if (!becameConnected && !becameValidated) return;
        if (host.isInForeground()) {
            cancelAll();
        } else if (becameValidated) {
            onOnline();
        }
    }

    /** 待っているタブをすべて取り消す(お知らせも読み込み直しもしない)。 */
    private void cancelAll() {
        if (watches.isEmpty()) return;
        for (Watch w : watches) sleepIfWoken(w);
        watches.clear();
        unregisterCallback();
        updateWaitService();
        Toast.makeText(activity, Lang.t("インターネットに再接続しました"), Toast.LENGTH_SHORT).show();
    }

    /** インターネットに通信できるようになった: 待っているタブを読み込み直す。 */
    private void onOnline() {
        long now = SystemClock.elapsedRealtime();
        for (Iterator<Watch> it = watches.iterator(); it.hasNext(); ) {
            Watch w = it.next();
            if (now - w.since > MAX_WAIT_MS) {
                it.remove();
                continue;
            }
            if (w.reloading) continue;
            GeckoTabView tab = host.tabForId(w.tabId);
            if (tab == null || tab.isDestroyed()) {
                it.remove();
                continue;
            }
            w.reloading = true;
            if (!host.isInForeground()) {
                // アプリが裏にあるとタブは止まっているので、読み込みの間だけ動かす
                tab.activateForBackgroundLoad();
                w.wokeInBackground = true;
                final Watch target = w;
                handler.postDelayed(() -> {
                    if (!watches.contains(target) || !target.reloading) return;
                    target.reloading = false;
                    sleepIfWoken(target);
                }, BACKGROUND_LOAD_TIMEOUT_MS);
            }
            tab.loadUrl(w.url);
        }
        if (watches.isEmpty()) unregisterCallback();
        updateWaitService();
    }

    /** 裏で読み込むためだけに動かしたタブを、アプリが裏のままなら止め直す。 */
    private void sleepIfWoken(@NonNull Watch w) {
        if (!w.wokeInBackground) return;
        w.wokeInBackground = false;
        if (host.isInForeground()) return;
        GeckoTabView tab = host.tabForId(w.tabId);
        if (tab != null && !tab.isDestroyed()) tab.onPause();
    }

    // ------------------------------------------------------------------
    // タブからの通知
    // ------------------------------------------------------------------

    /**
     * 読み込みが始まった。待っているタブでユーザーが別のページへ移ったなら、そのタブの予約を取り消す
     * (後から開けなかったページが勝手に開かれないように)。
     */
    void onPageStarted(@NonNull GeckoTabView tab, @Nullable String url) {
        Watch w = find(tab);
        if (w == null || w.reloading) return;
        if (url == null || url.startsWith("data:") || url.equals(w.url)) return;
        watches.remove(w);
        if (watches.isEmpty()) unregisterCallback();
        updateWaitService();
    }

    /** 読み込みが終わった。待っていたタブなら知らせる。 */
    void onPageFinished(@NonNull GeckoTabView tab, @Nullable String url, boolean success) {
        Watch w = find(tab);
        if (w == null || !w.reloading) return;
        if (!success || url == null || url.startsWith("data:")) {
            // まだ読めない(つながったが通信できなかった等): 次に回線が変わるまで待つ
            w.reloading = false;
            sleepIfWoken(w);
            return;
        }
        watches.remove(w);
        if (watches.isEmpty()) unregisterCallback();
        notifyReady(w.tabId, tab);
        sleepIfWoken(w);
        updateWaitService();
    }

    /** 読み込みに失敗した(エラーページを出す)。待っていたタブなら次の接続を待つ。 */
    void onLoadError(@NonNull GeckoTabView tab) {
        Watch w = find(tab);
        if (w != null) {
            w.reloading = false;
            sleepIfWoken(w);
        }
    }

    /** タブが閉じられた。 */
    void onTabClosed(int tabId) {
        for (Iterator<Watch> it = watches.iterator(); it.hasNext(); ) {
            if (it.next().tabId == tabId) it.remove();
        }
        if (watches.isEmpty()) unregisterCallback();
        updateWaitService();
    }

    /** アプリが前面に戻った: 裏で動かしていた印は不要になる(タブは通常どおり動く)。 */
    void onAppForeground() {
        for (Watch w : watches) w.wokeInBackground = false;
    }

    void release() {
        boolean hadWatches = !watches.isEmpty();
        watches.clear();
        tokens.clear();
        unregisterCallback();
        if (hadWatches) PageReadyWaitService.stop(activity.getApplicationContext());
        handler.removeCallbacksAndMessages(null);
    }

    @Nullable
    private Watch find(@NonNull GeckoTabView tab) {
        int id = host.tabIdOf(tab);
        for (Watch w : watches) if (w.tabId == id) return w;
        return null;
    }

    // ------------------------------------------------------------------
    // お知らせ
    // ------------------------------------------------------------------

    private void notifyReady(int tabId, @NonNull GeckoTabView tab) {
        String title = tab.getTitle();
        if (title == null || title.isEmpty()) title = tab.getUrl();
        // シークレットでは通知にページ名を出さない(ロック画面などから見えてしまうため)
        String shown = host.isSecret() ? Lang.t("シークレット タブのページ") : title;

        if (host.isInForeground()) {
            TopMessage.make(activity, R.drawable.ic_msg_download, Lang.t("ページの準備ができました"))
                    .setSubtitle(shown)
                    .setAction(Lang.t("開く"), () -> activity.startActivity(openTabIntent(tabId)))
                    .setDuration(TopMessage.DURATION_LONG)
                    .show();
            return;
        }
        if (!canNotify()) return;
        Context ctx = activity.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID, Lang.t("ページの準備ができたらお知らせ"), NotificationManager.IMPORTANCE_HIGH));
            }
        }
        PendingIntent pi = PendingIntent.getActivity(ctx, tabId, openTabIntent(tabId),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(Lang.t("ページの準備ができました"))
                .setContentText(shown)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(host.isSecret()
                        ? NotificationCompat.VISIBILITY_SECRET : NotificationCompat.VISIBILITY_PRIVATE);
        try {
            //noinspection MissingPermission
            NotificationManagerCompat.from(ctx).notify(0x0EAD0000 + tabId, b.build());
        } catch (SecurityException ignored) {
        }
    }

    private Intent openTabIntent(int tabId) {
        return new Intent(activity, activity.getClass())
                .putExtra(EXTRA_TAB_ID, tabId)
                // CLEAR_TOP だと上にあるシークレット画面を閉じてしまうので、前面へ移すだけにする
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    private boolean canNotify() {
        return Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !canNotify()) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
        }
    }
}
