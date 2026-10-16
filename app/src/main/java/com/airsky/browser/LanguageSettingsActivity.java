package com.airsky.browser;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;

/**
 * 言語。アプリの表示言語(日本語 / English)、ウェブページに伝える言語、翻訳の提案。
 */
public class LanguageSettingsActivity extends PixelSettingsActivity {

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("言語");
    }

    @Override
    protected void onBuild() {
        // アプリの表示言語。言語名はどちらの言語の時もその言語自身の名前で出す
        header(Lang.t("AirSky の言語") + (Lang.isEnglish() ? " / 言語" : " / Language"));
        String current = Lang.preference(this);
        String[][] appLangs = {
                {Lang.SYSTEM, Lang.t("端末の設定に合わせる"), Lang.t("端末の言語が日本語なら日本語、それ以外は英語で表示します")},
                {Lang.JA, "日本語", "Japanese"},
                {Lang.EN, "English", "英語"},
        };
        for (String[] l : appLangs) {
            radioRow(0, l[1], l[2], current.equals(l[0]), () -> {
                if (current.equals(l[0])) return;
                confirm(Lang.t("言語を切り替えますか?"), Lang.t("AirSky を再起動して表示言語を切り替えます。開いているタブはそのまま残ります。"),
                        Lang.t("再起動"), () -> switchLanguage(l[0]));
            });
        }
        if (Build.VERSION.SDK_INT >= 33) {
            row(R.drawable.ic_ps_language, Lang.t("Android のアプリの言語設定"), Lang.t("Android の設定でも変更できます"), () -> {
                try {
                    startActivity(new Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                            .setData(Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {
                }
            }).chevron();
        }

        header(Lang.t("ウェブページの言語"));
        int lang = BrowserPrefs.integer(this, BrowserPrefs.PAGE_LANGUAGE, BrowserPrefs.defaultPageLanguage());
        String[][] options = {
                {Lang.t("日本語を優先"), Lang.t("日本語 → 英語 の順にページを表示するようサイトに伝えます")},
                {Lang.t("英語を優先"), Lang.t("英語 → 日本語 の順にページを表示するようサイトに伝えます")},
                {Lang.t("端末の言語に合わせる"), Lang.t("Android で設定している言語の順に伝えます")},
        };
        for (int i = 0; i < options.length; i++) {
            final int value = i;
            radioRow(0, options[i][0], options[i][1], lang == i, () -> {
                BrowserPrefs.put(this, BrowserPrefs.PAGE_LANGUAGE, value);
                BrowserPrefs.apply(this);
                rebuild();
            });
        }

        header(Lang.t("翻訳"));
        boolean auto = BrowserPrefs.bool(this, BrowserPrefs.TRANSLATE_AUTO, false);
        radioRow(0, Lang.t("翻訳するか尋ねる"),
                Lang.t("ほかの言語のページを開いた時に「このページを翻訳しますか？」と尋ねます"),
                !auto, () -> {
                    BrowserPrefs.put(this, BrowserPrefs.TRANSLATE_AUTO, false);
                    rebuild();
                });
        radioRow(0, Lang.t("自動で翻訳する"),
                Lang.t("ほかの言語のページを開いたら、尋ねずにすぐ翻訳します(端末内で翻訳します)"),
                auto, () -> {
                    BrowserPrefs.put(this, BrowserPrefs.TRANSLATE_AUTO, true);
                    rebuild();
                });
        gap();
        switchRow(R.drawable.ic_pm_translate, Lang.t("ページの翻訳を提案する"),
                Lang.t("ほかの言語のページを開いた時に、日本語への翻訳を提案します(端末内で翻訳します)"),
                BrowserPrefs.bool(this, BrowserPrefs.TRANSLATE_OFFER, true), on -> {
                    BrowserPrefs.put(this, BrowserPrefs.TRANSLATE_OFFER, on);
                    BrowserPrefs.apply(this);
                }).setEnabled(!auto);
        note(Lang.t("ページはいつでも メニュー › 翻訳 から日本語に翻訳できます。"));
    }

    /**
     * 表示言語を切り替えてアプリを起動し直す。画面の文字の一部は起動時に一度だけ読むので、
     * 開いている画面を作り直すだけでなくプロセスごと起動し直す(タブは終了時に保存される)。
     */
    private void switchLanguage(@NonNull String lang) {
        Lang.setPreference(this, lang);
        Intent restart = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (restart == null) return;
        restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 0, restart,
                android.app.PendingIntent.FLAG_CANCEL_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        if (am != null) am.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 1600, pi);
        finishAffinity();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> System.exit(0), 1200);
    }
}
