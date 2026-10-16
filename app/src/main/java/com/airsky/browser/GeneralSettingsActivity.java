package com.airsky.browser;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * 全般(Aozora 独自のアプリ設定。以前の「ブラウザ設定 › 全般」)。
 */
public class GeneralSettingsActivity extends PixelSettingsActivity {

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("全般");
    }

    @Override
    protected void onBuild() {
        SharedPreferences p = getSharedPreferences("AppPrefs", MODE_PRIVATE);

        header(Lang.t("ページ"));
        toggle(p, R.drawable.ic_ps_refresh, Lang.t("スワイプで再読み込み"), Lang.t("ページの最上部で上から下にスワイプし、再読み込みする"),
                "isSwipeReload", true);
        toggle(p, R.drawable.ic_ps_progress, Lang.t("プログレスバーのアニメーション"), Lang.t("プログレスバー進行時のアニメーションを有効化する"),
                "ProgressBarAnimation", true);
        toggle(p, R.drawable.ic_ps_hidebar, Lang.t("下部メニューバーを自動的に隠す"), Lang.t("スクロール時に下部メニューバーを自動的に隠す"),
                "hidebottom", true);
        toggle(p, R.drawable.ic_ps_vibration, Lang.t("振動"), Lang.t("サイト推移時に端末を少々振動させる"), "Vibrate", true);

        header(Lang.t("起動"));
        boolean boot = p.getBoolean("bootScreen", false);
        switchRow(R.drawable.ic_ps_play, Lang.t("起動画面"), Lang.t("アプリ起動時に起動画面を表示する"), boot, on -> {
            p.edit().putBoolean("bootScreen", on).apply();
            rebuild();
        });
        toggle(p, R.drawable.ic_ps_music, Lang.t("起動音"), Lang.t("アプリ起動時に起動音を再生する"), "bootSound", false).setEnabled(boot);
        switchRow(R.drawable.ic_ps_update, Lang.t("アップデートのお知らせ"),
                Lang.t("新しいバージョンがある場合、起動時のダイアログと通知でお知らせする"),
                p.getBoolean(UpdateChecker.KEY_ENABLED, true), on -> {
                    p.edit().putBoolean(UpdateChecker.KEY_ENABLED, on).apply();
                    UpdateChecker.schedule(this); // オフなら 1 日 1 回の確認も止める
                    if (on) requestNotificationPermission();
                });

        header(Lang.t("お知らせ"));
        toggle(p, R.drawable.ic_ps_battery, Lang.t("バッテリー残量低下の通知"),
                Lang.t("充電されていない状態でバッテリー残量が20%を切った場合、残量低下の通知を表示する"), "batteryalert", true);
        note(Lang.t("ズームボタンの表示とタップの表示は「ユーザー補助機能」に移動しました。"));
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 0x0A13);
        }
    }

    private Row toggle(SharedPreferences p, int icon, String title, String summary, String key, boolean def) {
        return switchRow(icon, title, summary, p.getBoolean(key, def), on -> p.edit().putBoolean(key, on).apply());
    }
}
