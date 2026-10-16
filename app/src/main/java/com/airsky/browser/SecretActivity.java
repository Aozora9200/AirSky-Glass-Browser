package com.airsky.browser;

import android.app.Activity;
import android.app.Application;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import org.mozilla.geckoview.GeckoRuntime;

import java.util.UUID;

/**
 * シークレットモード。
 *
 * 画面・操作は通常のブラウザ({@link MainActivity})と共通で、次の点だけが異なる。
 * <ul>
 *   <li>Chrome のシークレットと同じく、この画面を開くたびに専用の一時プロファイルを新しく作る。
 *       GeckoView のプライベートブラウジング(Cookie・キャッシュ・サイトデータはメモリ上にだけ置く)に加え、
 *       画面ごとに固有の区画(contextId)を使うので、通常タブとも前回のシークレットとも何も共有しない。
 *       画面を閉じるとその区画のデータを消去する。</li>
 *   <li>閲覧履歴・タブ・サムネイルを端末に保存しない</li>
 *   <li>スタートページ・メニュー・タブ一覧がシークレット用</li>
 *   <li>テーマ設定に関係なく常にダーク表示。アドレスバーにシークレットのマークを出す</li>
 * </ul>
 */
public class SecretActivity extends MainActivity {

    /** このシークレット画面だけの一時プロファイル(区画)の ID。 */
    private final String contextId = "aozora-secret-" + UUID.randomUUID();

    @Override
    protected void attachBaseContext(Context newBase) {
        // Chrome と同じく、シークレットはアプリのテーマ設定に関係なく常にダーク表示。
        // 画面の Context を作る前に指定する(onCreate で指定すると、Android 9 以前などで
        // 通常の画面と共有している Resources が書き換わり、通常の画面までダークになっていた)
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.attachBaseContext(newBase);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Chrome のシークレットと同じく、スクリーンショット・画面録画・画面共有(キャスト)・
        // 最近のアプリ一覧のサムネイルに画面が映らないようにする
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false);
        registerLifecycle();
        super.onCreate(savedInstanceState);
    }

    // ------------------------------------------------------------------
    // アプリを離れたときにシークレット タブをロックする(設定 › プライバシーとセキュリティ)
    // ------------------------------------------------------------------

    private static final int REQUEST_SECRET_UNLOCK = 7301;

    /** 表示中の Aozora の画面。空になったらアプリを離れた。 */
    private static final java.util.Set<Activity> startedActivities =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static boolean lifecycleRegistered = false;

    private boolean lockPending = false;
    private boolean authenticating = false;
    @Nullable private View lockCover;

    private void registerLifecycle() {
        if (lifecycleRegistered) return;
        lifecycleRegistered = true;
        getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) { startedActivities.add(a); }
            @Override public void onActivityStopped(Activity a) { startedActivities.remove(a); }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (lockPending && !authenticating) showSecretLock();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (authenticating || isChangingConfigurations()) return;
        // ほかのアプリやホーム画面に移った時だけロックする(Aozora の中の画面を開いた時はそのまま)
        if (startedActivities.isEmpty() && BrowserPrefs.bool(this, BrowserPrefs.LOCK_SECRET, false) && isDeviceSecure()) {
            lockPending = true;
            showSecretLock(); // 戻ってきた時に一瞬でも中身が見えないよう、先に覆っておく
        }
    }

    private boolean isDeviceSecure() {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        return km != null && km.isDeviceSecure();
    }

    private void showSecretLock() {
        if (lockCover != null) return;
        FrameLayout cover = new FrameLayout(this);
        cover.setClickable(true);
        cover.setFocusable(true);
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true);
        cover.setBackgroundColor(tv.data);
        cover.setElevation(100f);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        float d = getResources().getDisplayMetrics().density;
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_ts_incognito);
        box.addView(icon, new LinearLayout.LayoutParams(Math.round(64 * d), Math.round(64 * d)));
        TextView title = new TextView(this);
        title.setText(Lang.t("シークレット タブはロックされています"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Math.round(20 * d), 0, Math.round(8 * d));
        box.addView(title);
        TextView sub = new TextView(this);
        sub.setText(Lang.t("開いているシークレット タブを表示するには、画面ロックを解除してください"));
        sub.setGravity(Gravity.CENTER);
        sub.setAlpha(0.75f);
        box.addView(sub);
        MaterialButton unlock = new MaterialButton(this);
        unlock.setText(Lang.t("ロックを解除"));
        unlock.setOnClickListener(v -> requestUnlock());
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.topMargin = Math.round(28 * d);
        box.addView(unlock, ulp);
        MaterialButton close = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        close.setText(Lang.t("シークレット タブをすべて閉じる"));
        close.setOnClickListener(v -> finish());
        box.addView(close);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.leftMargin = lp.rightMargin = Math.round(32 * d);
        cover.addView(box, lp);
        ((ViewGroup) getWindow().getDecorView()).addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        lockCover = cover;
    }

    @SuppressWarnings("deprecation")
    private void requestUnlock() {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        Intent intent = km != null ? km.createConfirmDeviceCredentialIntent(Lang.t("シークレット タブのロックを解除"), null) : null;
        if (intent == null) {
            unlocked();
            return;
        }
        authenticating = true;
        startActivityForResult(intent, REQUEST_SECRET_UNLOCK);
    }

    private void unlocked() {
        lockPending = false;
        if (lockCover != null) {
            View c = lockCover;
            lockCover = null;
            c.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                if (c.getParent() instanceof ViewGroup) ((ViewGroup) c.getParent()).removeView(c);
            }).start();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SECRET_UNLOCK) {
            authenticating = false;
            if (resultCode == RESULT_OK) unlocked();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected boolean isSecretMode() {
        return true;
    }

    @Nullable
    @Override
    protected String sessionContextId() {
        return contextId;
    }

    @Override
    protected boolean useNativeStartPage() {
        // シークレットタブのスタートページは従来の HTML(secret.html)のまま
        return false;
    }

    @Override
    protected String homeUrl() {
        return "file:///android_asset/secret.html";
    }

    @Override
    protected String homeSiteName() {
        return Lang.t("シークレットタブ");
    }

    @Override
    protected boolean isHomeUrl(@Nullable String url) {
        return url != null && url.equals("file:///android_asset/secret.html");
    }

    @Override
    protected void onDestroy() {
        // 先にタブ(セッション)を閉じてから、この区画に残ったデータを消す
        super.onDestroy();
        try {
            GeckoRuntime runtime = GeckoEngine.get(this).getRuntime();
            if (runtime != null) {
                runtime.getStorageController().clearDataForSessionContext(contextId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
