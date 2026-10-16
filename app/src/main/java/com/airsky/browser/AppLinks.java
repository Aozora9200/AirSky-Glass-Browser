package com.airsky.browser;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * サイトの「アプリで開く」ボタン(X など)用。
 *
 * <p>こうしたボタンは普通の https のリンク(例: https://m.x.com/〇〇?launch_app_store=true)で、
 * Chrome ではアプリ リンクとしてインストール済みのアプリに渡される。AirSky ではページとして開くため、
 * サイトが「アプリが無い」と判断して Play ストアへ転送し、アプリがあっても Play ストアが開いていた。
 * そこで、押したリンクの転送先が Play ストアで、そのアプリがインストール済みなら、元のリンクをアプリで開く。</p>
 */
final class AppLinks {

    /** リンクを押してから Play ストアへ転送されるまで、押したリンクを覚えておく時間。 */
    static final long CANDIDATE_TTL_MS = 15_000;

    private AppLinks() {}

    /**
     * Play ストアのアプリのページ(market://details?id=…、play.google.com/store/apps/details?id=…、
     * それらを指す intent:)なら、そのアプリのパッケージ名。違えば null。
     */
    @Nullable
    static String storePackage(@Nullable String url) {
        if (url == null) return null;
        String lower = url.toLowerCase(Locale.ROOT);
        try {
            if (lower.startsWith("intent:")) {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                String fromData = storePackageOf(intent.getData());
                if (fromData != null) return fromData;
                String fallback = intent.getStringExtra("browser_fallback_url");
                return "com.android.vending".equals(intent.getPackage()) ? storePackage(fallback) : null;
            }
            return storePackageOf(Uri.parse(url));
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String storePackageOf(@Nullable Uri uri) {
        if (uri == null || uri.getScheme() == null) return null;
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
        String path = uri.getPath() != null ? uri.getPath() : "";
        boolean store = ("market".equals(scheme) && "details".equals(host))
                || (("https".equals(scheme) || "http".equals(scheme))
                && "play.google.com".equals(host) && path.startsWith("/store/apps/details"));
        if (!store) return null;
        String id;
        try {
            id = uri.getQueryParameter("id");
        } catch (UnsupportedOperationException e) {
            return null;
        }
        return id != null && id.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+") ? id : null;
    }

    /**
     * インストール済みのアプリ {@code packageName} で {@code pageUrl} を開く。そのリンクを扱えないアプリなら
     * アプリのトップを開く。アプリが無ければ false(その時は Play ストアへ進ませる)。
     */
    static boolean openInstalledApp(@NonNull Activity activity, @NonNull String pageUrl,
                                    @NonNull String packageName) {
        if (packageName.equals(activity.getPackageName())) return false;
        // パッケージの公開範囲の制限があるので、調べずに実際に起動してみる(handleCustomUrl と同じ考え方)
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(packageName);
        if (start(activity, view)) return true;
        Intent launcher = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return start(activity, launcher);
    }

    private static boolean start(@NonNull Activity activity, @NonNull Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
