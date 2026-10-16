package com.airsky.browser;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public class AozoraApplication extends Application implements Application.ActivityLifecycleCallbacks {

    private int activityReferences = 0;
    private boolean isActivityChangingConfigurations = false;
    private boolean appWentToBackground = false;

    @Override
    public void onCreate() {
        super.onCreate();
        // 別の端末から受け取った引っ越しのデータを、設定やデータベースを開く前に反映する
        // (エンジンの子プロセスなど、別のプロセスでは何もしない)
        if (isMainProcess()) DeviceMigration.applyPending(this);
        Lang.init(this);
        ThemeHelper.applyNightMode(this);
        registerActivityLifecycleCallbacks(this);
    }

    private boolean isMainProcess() {
        String name = null;
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            name = Application.getProcessName();
        } else {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null && am.getRunningAppProcesses() != null) {
                int pid = android.os.Process.myPid();
                for (android.app.ActivityManager.RunningAppProcessInfo p : am.getRunningAppProcesses()) {
                    if (p.pid == pid) name = p.processName;
                }
            }
        }
        return name == null || name.equals(getPackageName());
    }

    @Override
    public void onConfigurationChanged(@androidx.annotation.NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 端末の言語・アプリの言語が変わったかもしれない
        Lang.invalidate();
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (++activityReferences == 1 && !isActivityChangingConfigurations) {
            // 本当にバックグラウンドから復帰した
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        isActivityChangingConfigurations = activity.isChangingConfigurations();
        if (--activityReferences == 0 && !isActivityChangingConfigurations) {
            // アプリ全体が裏に回った（ホーム/他アプリ切替/画面ロック等）
            appWentToBackground = true;
        }
    }

    public boolean isAppWentToBackground() {
        return appWentToBackground;
    }

    public void clearAppWentToBackground() {
        appWentToBackground = false;
    }

    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override
    public void onActivityResumed(Activity activity) {
        // コントローラー・キーボードで選んでいる項目を、どの画面でも枠で見やすくする
        FocusHighlight.attach(activity.getWindow());
    }

    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}