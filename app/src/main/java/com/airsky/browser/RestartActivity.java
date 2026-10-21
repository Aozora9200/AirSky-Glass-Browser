package com.airsky.browser;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import androidx.annotation.NonNull;

/**
 * アプリをプロセスごと起動し直すための、画面を持たない中継。別のプロセス(:restart)で動く。
 *
 * <p>AlarmManager で後から起動する方法は、プロセスを終えた後のバックグラウンドからの起動になるので
 * Android 10 以降では止められてしまい、AirSky を開いた元のアプリ(Aozora など)の画面に戻っていた。
 * この画面は前面にいる状態で開き、元のプロセスを終わらせてから次の画面を新しいタスクで開く。</p>
 */
public class RestartActivity extends Activity {

    private static final String EXTRA_NEXT = "com.airsky.browser.extra.RESTART_NEXT";
    private static final String EXTRA_PID = "com.airsky.browser.extra.RESTART_PID";
    private static final String EXTRA_DELAY = "com.airsky.browser.extra.RESTART_DELAY";
    /**
     * 元のプロセスを終わらせるまでの待ち時間。閉じる画面がタブや設定を保存し終える
     * (onPause/onDestroy と SharedPreferences の apply が書き終える)のを待つ。
     */
    static final long DEFAULT_DELAY_MS = 600;

    /** AirSky を起動し直してランチャーから開いた時と同じ画面にする。 */
    static void restart(@NonNull Activity from) {
        restart(from, DEFAULT_DELAY_MS);
    }

    /** AirSky を起動し直してランチャーから開いた時と同じ画面にする。元のプロセスは {@code delayMs} 後に終わらせる。 */
    static void restart(@NonNull Activity from, long delayMs) {
        Intent launch = from.getPackageManager().getLaunchIntentForPackage(from.getPackageName());
        if (launch == null) launch = new Intent(from, BootingActivity.class);
        restart(from, launch, delayMs);
    }

    /** AirSky を起動し直して {@code next} を開く。 */
    static void restart(@NonNull Activity from, @NonNull Intent next) {
        restart(from, next, DEFAULT_DELAY_MS);
    }

    /** AirSky を起動し直して {@code next} を開く。元のプロセスは {@code delayMs} 後に終わらせる。 */
    static void restart(@NonNull Activity from, @NonNull Intent next, long delayMs) {
        Intent i = new Intent(from, RestartActivity.class)
                .putExtra(EXTRA_NEXT, next)
                .putExtra(EXTRA_PID, Process.myPid())
                .putExtra(EXTRA_DELAY, Math.max(0, delayMs))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        from.startActivity(i);
        from.finishAffinity();
    }

    @Override
    public void onBackPressed() {
        // 起動し直すまでの間に戻るを押されても、途中でやめない
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        long delay = getIntent().getLongExtra(EXTRA_DELAY, 0);
        if (delay > 0) {
            new Handler(Looper.getMainLooper()).postDelayed(this::relaunch, delay);
        } else {
            relaunch();
        }
    }

    private void relaunch() {
        int pid = getIntent().getIntExtra(EXTRA_PID, -1);
        if (pid > 0 && pid != Process.myPid()) Process.killProcess(pid);

        Intent next = getIntent().getParcelableExtra(EXTRA_NEXT);
        if (next != null) {
            next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(next);
        }
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        Runtime.getRuntime().exit(0);
    }
}
