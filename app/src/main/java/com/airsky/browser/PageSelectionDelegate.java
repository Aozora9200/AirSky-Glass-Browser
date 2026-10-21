package com.airsky.browser;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.view.MenuItem;
import android.view.textclassifier.TextClassificationManager;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextLanguage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.geckoview.BasicSelectionActionDelegate;
import org.mozilla.geckoview.GeckoSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * ページの文字を選択した時のメニュー。Gecko の標準(コピー・すべて選択など)に
 * 「翻訳」「ウェブ検索」「共有」を足す。
 * <ul>
 *   <li>翻訳: 選んだ言葉がアプリの言語と違う時だけ出す。「英語の「hello」を日本語に翻訳」を
 *       検索エンジンで調べ、ページのプレビューと同じシートで見せる。</li>
 *   <li>ウェブ検索: 選んだ言葉を新しいタブで検索する。</li>
 *   <li>共有: 選んだ言葉と、今開いているページの URL を一緒に共有する。</li>
 * </ul>
 */
final class PageSelectionDelegate extends BasicSelectionActionDelegate {

    static final String ACTION_TRANSLATE = "com.airsky.browser.TRANSLATE";
    static final String ACTION_WEB_SEARCH = "com.airsky.browser.WEB_SEARCH";
    static final String ACTION_SHARE = "com.airsky.browser.SHARE";

    /** 検索に使う文字の上限(長すぎる選択は切る)。 */
    private static final int MAX_QUERY = 200;

    /** 押された項目を MainActivity に伝える。 */
    interface Callback {
        /** 新しいタブで検索する。 */
        void onWebSearch(@NonNull String text);

        /** 翻訳の検索をプレビューで見せる。 */
        void onTranslate(@NonNull String query);

        /** 今開いているページの URL(共有に添える)。 */
        @Nullable
        String currentUrl();
    }

    private final Callback callback;
    /** 最後に調べた選択の言語(同じ文字を何度も判定しない)。 */
    @Nullable private String detectedFor;
    @Nullable private String detectedLang;

    PageSelectionDelegate(@NonNull Activity activity, @NonNull Callback callback) {
        super(activity);
        this.callback = callback;
    }

    @Override
    protected String[] getAllActions() {
        String[] base = super.getAllActions();
        List<String> all = new ArrayList<>(Arrays.asList(base));
        // ほかのアプリの項目(PROCESS_TEXT)より前に並べる
        int at = all.indexOf(ACTION_PROCESS_TEXT);
        if (at < 0) at = all.size();
        all.addAll(at, Arrays.asList(ACTION_TRANSLATE, ACTION_WEB_SEARCH, ACTION_SHARE));
        return all.toArray(new String[0]);
    }

    @Override
    protected boolean isActionAvailable(String id) {
        if (ACTION_TRANSLATE.equals(id) || ACTION_WEB_SEARCH.equals(id) || ACTION_SHARE.equals(id)) {
            String text = selectedText();
            if (text == null) return false;
            if (ACTION_TRANSLATE.equals(id)) {
                String lang = languageOf(text);
                return lang != null && !lang.equals(appLanguage());
            }
            return true;
        }
        return super.isActionAvailable(id);
    }

    @Override
    protected void prepareAction(String id, MenuItem item) {
        if (ACTION_TRANSLATE.equals(id)) {
            item.setTitle(Lang.t("翻訳"));
        } else if (ACTION_WEB_SEARCH.equals(id)) {
            item.setTitle(Lang.t("ウェブ検索"));
        } else if (ACTION_SHARE.equals(id)) {
            item.setTitle(Lang.t("共有"));
        } else {
            super.prepareAction(id, item);
        }
    }

    @Override
    protected boolean performAction(String id, MenuItem item) {
        String text = selectedText();
        if (ACTION_TRANSLATE.equals(id)) {
            if (text != null) {
                String lang = languageOf(text);
                if (lang != null) callback.onTranslate(translateQuery(text, lang));
            }
            clearSelection();
            return true;
        }
        if (ACTION_WEB_SEARCH.equals(id)) {
            if (text != null) callback.onWebSearch(text);
            clearSelection();
            return true;
        }
        if (ACTION_SHARE.equals(id)) {
            if (text != null) share(text);
            clearSelection();
            return true;
        }
        return super.performAction(id, item);
    }

    /** 選んでいる文字(空白をまとめ、長すぎる分は切る)。パスワード欄や空の選択は null。 */
    @Nullable
    private String selectedText() {
        GeckoSession.SelectionActionDelegate.Selection s = mSelection;
        if (s == null || s.text == null
                || (s.flags & GeckoSession.SelectionActionDelegate.FLAG_IS_PASSWORD) != 0
                || (s.flags & GeckoSession.SelectionActionDelegate.FLAG_IS_COLLAPSED) != 0) {
            return null;
        }
        String t = s.text.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return null;
        return t.length() > MAX_QUERY ? t.substring(0, MAX_QUERY) : t;
    }

    private void share(@NonNull String text) {
        String url = callback.currentUrl();
        String body = "「" + text + "」";
        if (url != null && !url.isEmpty()) body += "\n" + url;
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, body);
        try {
            mActivity.startActivity(Intent.createChooser(send, Lang.t("共有")));
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // 言語
    // ------------------------------------------------------------------

    /** アプリの表示言語(ja / en)。 */
    @NonNull
    private static String appLanguage() {
        return Lang.isEnglish() ? "en" : "ja";
    }

    /** 「英語の「hello」を日本語に翻訳」(英語表示なら「Translate "こんにちは" from Japanese to English」)。 */
    @NonNull
    static String translateQuery(@NonNull String text, @NonNull String fromLang) {
        Locale ui = Lang.isEnglish() ? Locale.ENGLISH : Locale.JAPANESE;
        String from = new Locale(fromLang).getDisplayLanguage(ui);
        String to = new Locale(appLanguage()).getDisplayLanguage(ui);
        if (Lang.isEnglish()) return "Translate \"" + text + "\" from " + from + " to " + to;
        return from + "の「" + text + "」を" + to + "に翻訳";
    }

    /** 選んだ文字の言語(ISO 639 の 2 文字)。分からなければ null。 */
    @Nullable
    private String languageOf(@NonNull String text) {
        if (text.equals(detectedFor)) return detectedLang;
        detectedFor = text;
        detectedLang = detect(text);
        return detectedLang;
    }

    @Nullable
    private String detect(@NonNull String text) {
        // まず文字の種類で決める(かなが 1 つでもあれば日本語、ハングルなら韓国語など)
        boolean han = false, latin = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Character.UnicodeScript sc = Character.UnicodeScript.of(cp);
            switch (sc) {
                case HIRAGANA:
                case KATAKANA:
                    return "ja";
                case HANGUL:
                    return "ko";
                case CYRILLIC:
                    return "ru";
                case ARABIC:
                    return "ar";
                case THAI:
                    return "th";
                case GREEK:
                    return "el";
                case HEBREW:
                    return "he";
                case DEVANAGARI:
                    return "hi";
                case HAN:
                    han = true;
                    break;
                case LATIN:
                    latin = true;
                    break;
                default:
                    break;
            }
        }
        // 漢字だけの言葉は日本語と中国語を見分けられないので、日本語表示の時は日本語とみなす
        if (han) return Lang.isEnglish() ? "zh" : "ja";
        if (!latin) return null;
        // ラテン文字は英語・フランス語などを端末の言語判定で見分ける(使えなければ英語とみなす)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                TextClassificationManager tcm = mActivity.getSystemService(TextClassificationManager.class);
                TextClassifier tc = tcm != null ? tcm.getTextClassifier() : null;
                if (tc != null) {
                    TextLanguage result = tc.detectLanguage(new TextLanguage.Request.Builder(text).build());
                    if (result.getLocaleHypothesisCount() > 0) {
                        android.icu.util.ULocale top = result.getLocale(0);
                        if (result.getConfidenceScore(top) >= 0.5f && !top.getLanguage().isEmpty()) {
                            return top.getLanguage();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return "en";
    }
}
