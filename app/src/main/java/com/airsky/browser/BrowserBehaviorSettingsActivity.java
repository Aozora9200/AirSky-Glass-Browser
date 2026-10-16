package com.airsky.browser;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

/**
 * ブラウザの動作(以前の「ブラウザ設定」)。
 * すべてをデスクトップモードにする / CT3UA / ガラケーUA(どれか 1 つ)、バックグラウンド再生、URL スキーム。
 */
public class BrowserBehaviorSettingsActivity extends PixelSettingsActivity {

    private static final String PREF_NAME = "AdvancedBrowserPrefs";
    private static final String[] UA_KEYS = {"deskua_enabled", "ct3ua_enabled", "ua_enabled"};

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("ブラウザの動作");
    }

    /** ユーザーエージェントの設定を 1 つだけ有効にする(null なら全部オフ)。 */
    static void setUserAgentMode(@NonNull Context c, @Nullable String key) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        for (String k : UA_KEYS) e.putBoolean(k, k.equals(key));
        e.apply();
    }

    @Override
    protected void onBuild() {
        SharedPreferences ua = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        header(Lang.t("ページの表示"));
        uaRow(ua, R.drawable.ic_ps_desktop, Lang.t("すべてをデスクトップモードにする"),
                Lang.t("すべてのサイトで PC 版の表示をリクエストする"), "deskua_enabled");
        uaRow(ua, R.drawable.ic_ps_tablet, "CT3UA", Lang.t("チャレンジパッドのユーザーエージェントを有効化する"), "ct3ua_enabled");
        uaRow(ua, R.drawable.ic_ps_phone, Lang.t("ガラケーUA"), Lang.t("ガラケーのユーザーエージェントをリクエストする"), "ua_enabled");
        note(Lang.t("ページごとに切り替える場合は、URL バーの横のメニュー › PC 版サイト を使います。"));

        header(Lang.t("再生とアプリ"));
        SharedPreferences def = PreferenceManager.getDefaultSharedPreferences(this);
        switchRow(R.drawable.ic_ps_headphones, Lang.t("バックグラウンド再生"),
                Lang.t("アプリを閉じたり画面を消したりしても、動画や音声の再生を続ける(通知から操作できます)"),
                MediaPlaybackService.isEnabled(this),
                on -> def.edit().putBoolean(MediaPlaybackService.PREF_BACKGROUND_PLAYBACK, on).apply());
        SharedPreferences app = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        switchRow(R.drawable.ic_ps_link, Lang.t("URL スキーム"),
                Lang.t("サイトからアプリストアやアプリを起動する URL スキーム機能を有効化する"),
                app.getBoolean("intentJump", true), on -> app.edit().putBoolean("intentJump", on).apply());
    }

    private void uaRow(SharedPreferences ua, int icon, String title, String summary, String key) {
        switchRow(icon, title, summary, ua.getBoolean(key, false), on -> {
            if (on) {
                setUserAgentMode(this, key);
            } else {
                ua.edit().putBoolean(key, false).apply();
            }
            rebuild(); // ほかの UA のスイッチを切る
        });
    }
}
