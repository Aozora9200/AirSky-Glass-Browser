package com.airsky.browser;

import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.PermissionDelegate;

/**
 * サイトからの権限要求(位置情報・カメラ/マイク・通知・自動再生など)を処理する。
 * 設定の「サイトの設定」({@link SitePermissions})の既定値とサイトごとの例外に従い、
 * 「最初に確認する」の時だけダイアログで聞く。選んだ結果はそのサイトの例外として覚える。
 */
public class GeckoPermissionDelegate implements PermissionDelegate {

    private final Activity activity;
    private final int androidPermissionRequestCode;

    @Nullable private Callback pendingAndroidCallback;

    public GeckoPermissionDelegate(@NonNull Activity activity, int androidPermissionRequestCode) {
        this.activity = activity;
        this.androidPermissionRequestCode = androidPermissionRequestCode;
    }

    private boolean isUsable() {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    @Override
    public void onAndroidPermissionsRequest(@NonNull GeckoSession session, @Nullable String[] permissions,
                                            @NonNull Callback callback) {
        if (permissions == null || permissions.length == 0) {
            callback.grant();
            return;
        }
        boolean allGranted = true;
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (allGranted) {
            callback.grant();
            return;
        }
        if (pendingAndroidCallback != null) pendingAndroidCallback.reject();
        pendingAndroidCallback = callback;
        ActivityCompat.requestPermissions(activity, permissions, androidPermissionRequestCode);
    }

    /** Activity#onRequestPermissionsResult から呼ぶ。この delegate の要求なら true。 */
    public boolean onRequestPermissionsResult(int requestCode, @NonNull int[] grantResults) {
        if (requestCode != androidPermissionRequestCode) return false;
        Callback cb = pendingAndroidCallback;
        pendingAndroidCallback = null;
        if (cb == null) return true;
        boolean granted = grantResults.length > 0;
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }
        if (granted) cb.grant(); else cb.reject();
        return true;
    }

    @Nullable
    @Override
    public GeckoResult<Integer> onContentPermissionRequest(@NonNull GeckoSession session,
                                                           @NonNull ContentPermission perm) {
        String host = SitePermissions.hostOf(perm.uri);
        SitePermissions.touch(activity, host);
        switch (perm.permission) {
            case PERMISSION_GEOLOCATION:
                return decide(host, SitePermissions.LOCATION);
            case PERMISSION_DESKTOP_NOTIFICATION:
                return decide(host, SitePermissions.NOTIFICATIONS);
            case PERMISSION_XR:
                return decide(host, SitePermissions.XR);
            case PERMISSION_LOCAL_DEVICE_ACCESS:
            case PERMISSION_LOCAL_NETWORK_ACCESS:
                return decide(host, SitePermissions.LOCAL_NETWORK);
            case PERMISSION_MEDIA_KEY_SYSTEM_ACCESS:
                return decide(host, SitePermissions.DRM);
            case PERMISSION_PERSISTENT_STORAGE:
                return value(SitePermissions.effective(activity, host, SitePermissions.STORAGE) != SitePermissions.BLOCK);
            case PERMISSION_AUTOPLAY_AUDIBLE:
                // 音の出る自動再生は「音声と動画を許可」の時だけ
                return value(SitePermissions.effective(activity, host, SitePermissions.AUTOPLAY) == SitePermissions.ALLOW);
            case PERMISSION_AUTOPLAY_INAUDIBLE:
                return value(SitePermissions.effective(activity, host, SitePermissions.AUTOPLAY) != SitePermissions.BLOCK);
            case PERMISSION_STORAGE_ACCESS:
                return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW);
            default:
                return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
        }
    }

    private static GeckoResult<Integer> value(boolean allow) {
        return GeckoResult.fromValue(allow ? ContentPermission.VALUE_ALLOW : ContentPermission.VALUE_DENY);
    }

    /** 設定に従って許可/ブロックし、「最初に確認する」ならダイアログで聞く。 */
    private GeckoResult<Integer> decide(@Nullable String host, int type) {
        int v = SitePermissions.effective(activity, host, type);
        if (v == SitePermissions.ALLOW) return value(true);
        if (v == SitePermissions.BLOCK || host == null || !isUsable()) return value(false);

        GeckoResult<Integer> result = new GeckoResult<>();
        new MaterialAlertDialogBuilder(activity)
                .setIcon(SitePermissions.icon(type))
                .setTitle(SitePermissions.label(type))
                .setMessage(host + Lang.t(" が") + SitePermissions.requestText(type) + Lang.t("を求めています。"))
                .setPositiveButton(Lang.t("許可"), (d, w) -> {
                    SitePermissions.setSite(activity, host, type, SitePermissions.ALLOW);
                    result.complete(ContentPermission.VALUE_ALLOW);
                })
                .setNegativeButton(Lang.t("ブロック"), (d, w) -> {
                    SitePermissions.setSite(activity, host, type, SitePermissions.BLOCK);
                    result.complete(ContentPermission.VALUE_DENY);
                })
                .setOnCancelListener(d -> result.complete(ContentPermission.VALUE_DENY))
                .show();
        return result;
    }

    @Override
    public void onMediaPermissionRequest(@NonNull GeckoSession session, @NonNull String uri,
                                         @Nullable MediaSource[] video, @Nullable MediaSource[] audio,
                                         @NonNull MediaCallback callback) {
        if (!isUsable()) {
            callback.reject();
            return;
        }
        MediaSource v = pick(video);
        MediaSource a = pick(audio);
        String host = SitePermissions.hostOf(uri);
        SitePermissions.touch(activity, host);
        boolean screen = v != null && v.source != MediaSource.SOURCE_CAMERA;

        // ブロックしている方は渡さない(画面共有は毎回確認する)
        int cam = v == null ? SitePermissions.ALLOW
                : screen ? SitePermissions.ASK : SitePermissions.effective(activity, host, SitePermissions.CAMERA);
        int mic = a == null ? SitePermissions.ALLOW : SitePermissions.effective(activity, host, SitePermissions.MICROPHONE);
        if (cam == SitePermissions.BLOCK) v = null;
        if (mic == SitePermissions.BLOCK) a = null;
        if (v == null && a == null) {
            callback.reject();
            return;
        }
        if (cam == SitePermissions.ALLOW && mic == SitePermissions.ALLOW) {
            callback.grant(v, a);
            return;
        }

        final MediaSource video0 = v;
        final MediaSource audio0 = a;
        StringBuilder what = new StringBuilder();
        if (video0 != null) what.append(screen ? Lang.t("画面共有") : Lang.t("カメラ"));
        if (audio0 != null) what.append(what.length() > 0 ? Lang.t("とマイク") : Lang.t("マイク"));

        new MaterialAlertDialogBuilder(activity)
                .setIcon(video0 != null && !screen ? R.drawable.ic_ps_camera : R.drawable.ic_ps_mic)
                .setTitle(what + Lang.t("の使用"))
                .setMessage((host != null ? host : uri) + Lang.t(" が") + what + Lang.t("の使用を求めています。"))
                .setPositiveButton(Lang.t("許可"), (d, w) -> {
                    remember(host, video0 != null && !screen, audio0 != null, SitePermissions.ALLOW);
                    callback.grant(video0, audio0);
                })
                .setNegativeButton(Lang.t("ブロック"), (d, w) -> {
                    remember(host, video0 != null && !screen, audio0 != null, SitePermissions.BLOCK);
                    callback.reject();
                })
                .setOnCancelListener(d -> callback.reject())
                .setCancelable(false)
                .show();
    }

    private void remember(@Nullable String host, boolean camera, boolean mic, int value) {
        if (host == null) return;
        if (camera) SitePermissions.setSite(activity, host, SitePermissions.CAMERA, value);
        if (mic) SitePermissions.setSite(activity, host, SitePermissions.MICROPHONE, value);
    }

    @Nullable
    private static MediaSource pick(@Nullable MediaSource[] sources) {
        if (sources == null || sources.length == 0) return null;
        for (MediaSource s : sources) {
            if (s.source == MediaSource.SOURCE_CAMERA || s.source == MediaSource.SOURCE_MICROPHONE) return s;
        }
        return sources[0];
    }
}
