package com.airsky.browser;

import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * ホームページ(メニューの「トップページ」)。オン/オフと、開くページ(スタートページ / 好きなアドレス)。
 */
public class HomepageSettingsActivity extends PixelSettingsActivity {

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("ホームページ");
    }

    @Override
    protected void onBuild() {
        boolean enabled = BrowserPrefs.homeEnabled(this);
        gap();
        switchRow(0, enabled ? Lang.t("オン") : Lang.t("オフ"), Lang.t("メニューに「トップページ」を表示する"), enabled, on -> {
            BrowserPrefs.put(this, BrowserPrefs.HOME_ENABLED, on);
            rebuild();
        });

        header(Lang.t("このページを開く"));
        boolean custom = BrowserPrefs.bool(this, BrowserPrefs.HOME_CUSTOM, false);
        String url = BrowserPrefs.prefs(this).getString(BrowserPrefs.HOME_URL, "");
        radioRow(0, Lang.t("AirSky のスタートページ"), Lang.t("検索欄・サイト・おすすめ記事"), !custom, () -> {
            BrowserPrefs.put(this, BrowserPrefs.HOME_CUSTOM, false);
            rebuild();
        }).setEnabled(enabled);
        radioRow(0, Lang.t("カスタムのウェブアドレス"),
                url == null || url.isEmpty() ? Lang.t("タップして入力") : url, custom, this::editUrl).setEnabled(enabled);
        note(Lang.t("ホームページはシークレットタブでは使われません。"));

        header(Lang.t("スタートページ"));
        switchRow(R.drawable.ic_ps_news, Lang.t("ニュースを表示"),
                Lang.t("スタートページの下に、おすすめのニュース記事を表示します"),
                BrowserPrefs.startPageNews(this), on -> BrowserPrefs.setStartPageNews(this, on));
    }

    private void editUrl() {
        if (!BrowserPrefs.homeEnabled(this)) return;
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        input.setHint("https://example.com");
        input.setText(BrowserPrefs.prefs(this).getString(BrowserPrefs.HOME_URL, ""));
        input.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("カスタムのウェブアドレス"))
                .setView(box)
                .setPositiveButton(Lang.t("保存"), (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    BrowserPrefs.put(this, BrowserPrefs.HOME_URL, text);
                    BrowserPrefs.put(this, BrowserPrefs.HOME_CUSTOM, true);
                    rebuild();
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
        input.requestFocus();
    }
}
