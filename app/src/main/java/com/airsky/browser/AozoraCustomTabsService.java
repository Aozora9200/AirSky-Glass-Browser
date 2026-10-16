package com.airsky.browser;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsService;
import androidx.browser.customtabs.CustomTabsSessionToken;

import java.util.List;

/**
 * 他のアプリに「Aozora は簡易ブラウザ(Custom Tabs)を提供できる」と知らせるサービス。
 *
 * アプリは既定のブラウザがこのサービスを持っているかを確かめてから、Custom Tabs の Intent で
 * ページを開く。Intent は {@link MainActivity} が受け取り、{@link CustomTabActivity} へ渡す。
 */
public class AozoraCustomTabsService extends CustomTabsService {

    @Override
    protected boolean warmup(long flags) {
        // エンジンを先に起動しておくと、開いた時の表示が速くなる
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                GeckoEngine.get(getApplicationContext());
            } catch (RuntimeException ignored) {}
        });
        return true;
    }

    @Override
    protected boolean newSession(@NonNull CustomTabsSessionToken sessionToken) {
        return true;
    }

    @Override
    protected boolean mayLaunchUrl(@NonNull CustomTabsSessionToken sessionToken, @Nullable Uri url,
                                   @Nullable Bundle extras, @Nullable List<Bundle> otherLikelyBundles) {
        return true;
    }

    @Nullable
    @Override
    protected Bundle extraCommand(@NonNull String commandName, @Nullable Bundle args) {
        return null;
    }

    @Override
    protected boolean updateVisuals(@NonNull CustomTabsSessionToken sessionToken, @Nullable Bundle bundle) {
        return false;
    }

    @Override
    protected boolean requestPostMessageChannel(@NonNull CustomTabsSessionToken sessionToken, @NonNull Uri postMessageOrigin) {
        return false;
    }

    @Override
    protected int postMessage(@NonNull CustomTabsSessionToken sessionToken, @NonNull String message, @Nullable Bundle extras) {
        return RESULT_FAILURE_DISALLOWED;
    }

    @Override
    protected boolean validateRelationship(@NonNull CustomTabsSessionToken sessionToken, int relation,
                                           @NonNull Uri origin, @Nullable Bundle extras) {
        return false;
    }

    @Override
    protected boolean receiveFile(@NonNull CustomTabsSessionToken sessionToken, @NonNull Uri uri,
                                  int purpose, @Nullable Bundle extras) {
        return false;
    }

    @Override
    protected boolean cleanUpSession(@NonNull CustomTabsSessionToken sessionToken) {
        return true;
    }
}
