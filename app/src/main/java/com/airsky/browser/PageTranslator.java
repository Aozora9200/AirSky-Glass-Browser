package com.airsky.browser;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.TranslationsController;
import org.mozilla.geckoview.TranslationsController.RuntimeTranslation;
import org.mozilla.geckoview.TranslationsController.SessionTranslation.TranslationState;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chrome / Firefox と同じ「ページ内翻訳」。
 *
 * ページを別の URL(翻訳サイト)へ移動させずに、GeckoView の端末内翻訳エンジン
 * (Firefox Translations)でその場で翻訳する。
 * <ul>
 *   <li>ページの言語が表示言語と違えば、上部に「このページを翻訳しますか？」を出す
 *       (設定で「自動で翻訳する」にした時は尋ねずに翻訳する)。</li>
 *   <li>翻訳が終わると「ページが翻訳されました」と「元に戻す」・設定(⚙)を出す。</li>
 *   <li>設定から翻訳先/翻訳元の言語、「常に翻訳」「翻訳しない」を選べる。</li>
 *   <li>端末内翻訳が使えない場合だけ、従来どおり Google 翻訳で開く。</li>
 * </ul>
 */
final class PageTranslator {

    interface Host {
        @Nullable GeckoTabView getCurrentTab();
        boolean isInternalUrl(@Nullable String url);

        /**
         * 「〇〇を閉じました  元に戻す」と同じ帯(画面下のスナックバー)を出す。
         * 帯を出せない画面は false を返す(その時は上部の案内で知らせる)。
         */
        default boolean showUndoBar(@NonNull String text, @NonNull Runnable undo) {
            return false;
        }
    }

    private static final String PREFS = "AppPrefs";
    private static final String KEY_TARGET = "translateTargetLang";
    /** 「このサイトを翻訳しない」にしたホスト。Gecko の設定(サイトの権限)だけだと自動翻訳(常に翻訳)では守られないため、アプリでも覚える。 */
    private static final String KEY_NEVER_SITES = "translateNeverSites";

    private final Activity activity;
    private final Host host;

    /** 翻訳を指示して、まだ完了の案内を出していないタブ。 */
    @Nullable private GeckoTabView pendingTab;
    @Nullable private TopMessage progressMessage;
    /** 提案を出したタブと URL(同じページで何度も出さない)。 */
    @Nullable private GeckoTabView offeredTab;
    @Nullable private String offeredUrl;
    /** 出している「このページを翻訳しますか？」。 */
    @Nullable private TopMessage offerMessage;
    /**
     * 「元の言語で表示」を選んだタブとサイト。原文に戻すとページを読み直すことがあり、
     * 「常に翻訳」の言語だとすぐまた自動で翻訳されてしまうので、そのサイトにいる間は自動で翻訳しない。
     */
    @Nullable private GeckoTabView originalTab;
    @Nullable private String originalSite;

    PageTranslator(@NonNull Activity activity, @NonNull Host host) {
        this.activity = activity;
        this.host = host;
    }

    // ------------------------------------------------------------------
    // メニュー「翻訳」
    // ------------------------------------------------------------------

    /** メニューの「翻訳」。翻訳済みなら案内(元に戻す・設定)を出し直す。 */
    void translateCurrentPage() {
        GeckoTabView tab = host.getCurrentTab();
        if (tab == null || tab.getUrl() == null || tab.getUrl().isEmpty() || host.isInternalUrl(tab.getUrl())) {
            Toast.makeText(activity, Lang.t("翻訳するページが見つかりません"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (tab.isTranslated()) {
            showTranslatedMessage(tab);
            return;
        }
        // メニューから選んだ時は「このサイトを翻訳しない」でも翻訳する(自分で選んだので)
        if (tab == originalTab) originalTab = null;
        String from = detectedLanguage(tab);
        String to = targetLanguage();
        if (from != null && sameLanguage(from, to)) {
            // 既に翻訳先の言語のページ: 翻訳元を選んでもらう
            chooseSourceLanguage(tab);
            return;
        }
        translate(tab, from, to);
    }

    private void translate(@NonNull GeckoTabView tab, @Nullable String from, @NonNull String to) {
        translate(tab, from, to, false);
    }

    /** @param auto 「常に翻訳」による自動の翻訳(始める直前にも「翻訳しない」ページでないかを確かめる) */
    private void translate(@NonNull GeckoTabView tab, @Nullable String from, @NonNull String to, boolean auto) {
        RuntimeTranslation.isTranslationsEngineSupported().accept(supported -> {
            if (auto && isSiteExcluded(tab)) return;
            if (!Boolean.TRUE.equals(supported) || from == null) {
                if (Boolean.TRUE.equals(supported)) {
                    // 言語が検出できなかった: 翻訳元を選んでもらう
                    chooseSourceLanguage(tab);
                } else {
                    translateWithWebService(tab, to);
                }
                return;
            }
            GeckoResult<Void> result = tab.translate(from, to);
            if (result == null) {
                translateWithWebService(tab, to);
                return;
            }
            pendingTab = tab;
            showProgress(from, to);
            result.accept(v -> {
                // 翻訳の開始が受け付けられた。完了の案内は状態の通知で出す
                TranslationState st = tab.getTranslationState();
                if (st != null) onStateChanged(tab, st);
            }, e -> onTranslateFailed(tab, to, e));
        }, e -> translateWithWebService(tab, to));
    }

    // ------------------------------------------------------------------
    // GeckoTabView からの通知
    // ------------------------------------------------------------------

    void onStateChanged(@NonNull GeckoTabView tab, @NonNull TranslationState state) {
        if (tab != pendingTab) {
            offerFromDetectedLanguage(tab, state);
            return;
        }
        if (state.error != null && !state.error.isEmpty()) {
            onTranslateFailed(tab, targetLanguage(), null);
            return;
        }
        if (state.requestedTranslationPair != null && Boolean.TRUE.equals(state.isEngineReady)) {
            pendingTab = null;
            hideProgress();
            if (tab == host.getCurrentTab()) showTranslatedBar(tab);
        }
    }

    /**
     * ページの言語が表示言語と違う: 翻訳を提案する(同じページでは 1 回だけ)。
     * 設定で「自動で翻訳する」にしている時は、尋ねずに翻訳する。
     */
    void onOffered(@NonNull GeckoTabView tab) {
        if (tab != host.getCurrentTab() || tab.isTranslated() || pendingTab == tab) return;
        String url = tab.getUrl();
        if (url == null || host.isInternalUrl(url) || isSiteExcluded(tab)) return;
        if (tab == offeredTab && url.equals(offeredUrl)) return;
        offeredTab = tab;
        offeredUrl = url;
        String from = detectedLanguage(tab);
        String to = targetLanguage();
        if (from != null && sameLanguage(from, to)) return;
        if (isAutoTranslate() && from != null) {
            translate(tab, from, to, true);
            return;
        }
        if (!BrowserPrefs.bool(activity, BrowserPrefs.TRANSLATE_OFFER, true)) return;
        showOffer(tab, from, to);
    }

    /** 「このページを翻訳しますか？」を出す。 */
    private void showOffer(@NonNull GeckoTabView tab, @Nullable String from, @NonNull String to) {
        dismissOffer();
        offerMessage = TopMessage.make(activity, R.drawable.ic_msg_translate, Lang.t("このページを翻訳しますか？"))
                .setSubtitle(languageName(from, Lang.t("検出された言語")) + " → " + languageName(to, Lang.t("日本語")))
                .setSecondaryIcon(R.drawable.ic_msg_settings, Lang.t("翻訳の設定"), () -> showSettings(tab))
                .setAction(Lang.t("翻訳"), () -> {
                    // 提案の後に言語の検出が終わっていることもあるので、押した時点の値を使う
                    String detected = detectedLanguage(tab);
                    translate(tab, detected != null ? detected : from, to);
                })
                .setDuration(TopMessage.DURATION_LONG);
        offerMessage.show();
    }

    private void dismissOffer() {
        if (offerMessage != null) {
            offerMessage.dismiss();
            offerMessage = null;
        }
    }

    /**
     * Gecko の提案(onOfferTranslate)が来なくても、検出したページの言語が翻訳先と違えば提案する。
     * Gecko は「表示言語・Accept-Language・端末の言語」のどれかに入っている言語を読めるとみなして提案しないため、
     * 端末の言語に英語が入っていると英語のページで何も聞いてこなかった。
     * 「この言語を翻訳しない」「このサイトを翻訳しない」は Gecko の設定をそのまま守る。
     */
    private void offerFromDetectedLanguage(@NonNull GeckoTabView tab, @NonNull TranslationState state) {
        if (state.requestedTranslationPair != null || state.detectedLanguages == null) return;
        if (!BrowserPrefs.bool(activity, BrowserPrefs.TRANSLATE_OFFER, true)) return;
        String doc = state.detectedLanguages.docLangTag;
        if (doc == null || doc.isEmpty() || !state.detectedLanguages.isDocLangTagSupported) return;
        if (sameLanguage(doc, targetLanguage())) return;
        // 設定の「ページの言語」に入っている言語(英語を選んだ時の英語など)は読めるので聞かない
        for (String tag : BrowserPrefs.pageLocales(activity)) {
            if (sameLanguage(doc, tag)) return;
        }
        String url = tab.getUrl();
        if (url == null || host.isInternalUrl(url) || (tab == offeredTab && url.equals(offeredUrl))) return;
        if (isSiteExcluded(tab)) return;
        RuntimeTranslation.getLanguageSetting(doc).accept(setting -> {
            if (RuntimeTranslation.NEVER.equals(setting) || RuntimeTranslation.ALWAYS.equals(setting)) return;
            GeckoResult<Boolean> never = tab.getNeverTranslateSite();
            if (never == null) {
                onOffered(tab);
                return;
            }
            never.accept(n -> {
                if (!Boolean.TRUE.equals(n) && url.equals(tab.getUrl())) onOffered(tab);
            }, e -> onOffered(tab));
        }, e -> onOffered(tab));
    }

    /**
     * Gecko が「このページは翻訳の対象」と知らせてきた(表示言語と違う言語のページを開くたびに来る)。
     * 既定では「翻訳しますか？」と尋ねる。聞かずに翻訳するのは、設定で「自動で翻訳する」にした時と、
     * 翻訳の設定(⚙)でその言語を「常に翻訳」にした時だけ。
     */
    void onExpected(@NonNull GeckoTabView tab) {
        if (tab.isTranslated() || pendingTab == tab || isSiteExcluded(tab)) return;
        String from = detectedLanguage(tab);
        if (from == null) return;
        if (isAutoTranslate()) {
            translateExpected(tab, from);
            return;
        }
        RuntimeTranslation.getLanguageSetting(from).accept(setting -> {
            if (RuntimeTranslation.ALWAYS.equals(setting)) translateExpected(tab, from);
            else if (!RuntimeTranslation.NEVER.equals(setting)) onOffered(tab);
        }, e -> onOffered(tab));
    }

    /** 聞かずに翻訳する(このサイトを翻訳しない設定なら何もしない)。 */
    private void translateExpected(@NonNull GeckoTabView tab, @NonNull String from) {
        if (tab.isTranslated() || pendingTab == tab || isSiteExcluded(tab)) return;
        GeckoResult<Boolean> never = tab.getNeverTranslateSite();
        if (never == null) {
            translate(tab, from, targetLanguage(), true);
            return;
        }
        String url = tab.getUrl();
        never.accept(n -> {
            if (Boolean.TRUE.equals(n) || isSiteExcluded(tab)) return;
            if (url != null && !url.equals(tab.getUrl())) return;
            translate(tab, from, targetLanguage(), true);
        }, e -> translate(tab, from, targetLanguage(), true));
    }

    /**
     * ページが「翻訳しないで」と書いている(&lt;meta name="google" content="notranslate"&gt; や
     * &lt;html translate="no"&gt;)と分かった。自動で翻訳していたら元に戻す。
     */
    void onNoTranslatePage(@NonNull GeckoTabView tab) {
        if (tab == offeredTab) dismissOffer();
        if (pendingTab == tab || tab.isTranslated()) restore(tab);
    }

    // ------------------------------------------------------------------
    // 「このサイトを翻訳しない」
    // ------------------------------------------------------------------

    /** 翻訳の提案・自動翻訳をしないページか(このサイトを翻訳しない / ページが翻訳を断っている)。 */
    private boolean isSiteExcluded(@NonNull GeckoTabView tab) {
        if (tab.isNoTranslatePage()) return true;
        String h = siteOf(tab.getUrl());
        if (h != null && tab == originalTab && h.equals(originalSite)) return true;
        return h != null && neverSites().contains(h);
    }

    @NonNull
    private java.util.Set<String> neverSites() {
        return prefs().getStringSet(KEY_NEVER_SITES, java.util.Collections.emptySet());
    }

    private void setSiteExcluded(@NonNull GeckoTabView tab, boolean never) {
        String h = siteOf(tab.getUrl());
        if (h == null) return;
        java.util.Set<String> set = new java.util.HashSet<>(neverSites());
        if (never) set.add(h); else set.remove(h);
        prefs().edit().putStringSet(KEY_NEVER_SITES, set).apply();
        // Gecko 側の設定(提案の判定に使われる)も合わせる
        GeckoResult<Void> r = tab.setNeverTranslateSite(never);
        if (r != null) r.accept(v -> {}, e -> {});
    }

    /** "https://www.example.com/a" → "www.example.com"。 */
    @Nullable
    private static String siteOf(@Nullable String url) {
        if (url == null) return null;
        try {
            String h = android.net.Uri.parse(url).getHost();
            return h == null || h.isEmpty() ? null : h.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 表示
    // ------------------------------------------------------------------

    private void showProgress(@NonNull String from, @NonNull String to) {
        hideProgress();
        progressMessage = TopMessage.make(activity, R.drawable.ic_msg_translate, Lang.t("ページを翻訳しています..."))
                .setSubtitle(languageName(from, Lang.t("検出された言語")) + Lang.t("から") + languageName(to, Lang.t("日本語")))
                .setDuration(0);
        progressMessage.show();
    }

    private void hideProgress() {
        if (progressMessage != null) {
            progressMessage.dismiss();
            progressMessage = null;
        }
    }

    /**
     * 翻訳が終わった: 「ページを翻訳しました  元に戻す」の帯を出す(タブを閉じた時と同じ帯)。
     * 元に戻すと原文に戻し、「このページを翻訳しますか？」をもう一度出す。
     */
    private void showTranslatedBar(@NonNull GeckoTabView tab) {
        TranslationState st = tab.getTranslationState();
        String from = st != null && st.requestedTranslationPair != null
                ? st.requestedTranslationPair.fromLanguage : null;
        String to = st != null && st.requestedTranslationPair != null
                ? st.requestedTranslationPair.toLanguage : targetLanguage();
        boolean shown = host.showUndoBar(Lang.t("ページを翻訳しました"), () -> {
            if (!tab.isTranslated() && pendingTab != tab) return;
            showOriginal(tab);
            if (tab == host.getCurrentTab()) {
                String detected = detectedLanguage(tab);
                showOffer(tab, detected != null ? detected : from, to);
            }
        });
        if (!shown) showTranslatedMessage(tab);
    }

    /** 「ページが翻訳されました / 〇〇から日本語  ⚙ | 元に戻す」 */
    private void showTranslatedMessage(@NonNull GeckoTabView tab) {
        TranslationState st = tab.getTranslationState();
        String from = st != null && st.requestedTranslationPair != null
                ? st.requestedTranslationPair.fromLanguage : null;
        String to = st != null && st.requestedTranslationPair != null
                ? st.requestedTranslationPair.toLanguage : targetLanguage();
        TopMessage.make(activity, R.drawable.ic_msg_translate, Lang.t("ページが翻訳されました"))
                .setSubtitle(languageName(from, Lang.t("検出された言語")) + Lang.t("から") + languageName(to, Lang.t("日本語")))
                .setSecondaryIcon(R.drawable.ic_msg_settings, Lang.t("翻訳の設定"), () -> showSettings(tab))
                .setAction(Lang.t("元に戻す"), () -> showOriginal(tab))
                .setDuration(TopMessage.DURATION_LONG)
                .show();
    }

    /** 「元の言語で表示」(翻訳をやめて原文に戻す)。翻訳していなければ何もしない。 */
    void showOriginal() {
        GeckoTabView tab = host.getCurrentTab();
        if (tab != null && (tab.isTranslated() || pendingTab == tab)) showOriginal(tab);
    }

    /** ユーザーが原文に戻した。このサイトにいる間は自動で翻訳しない。 */
    private void showOriginal(@NonNull GeckoTabView tab) {
        originalTab = tab;
        originalSite = siteOf(tab.getUrl());
        restore(tab);
    }

    private void restore(@NonNull GeckoTabView tab) {
        pendingTab = null;
        hideProgress();
        GeckoResult<Void> r = tab.restoreOriginalPage();
        if (r == null) {
            tab.reload();
            return;
        }
        r.accept(null, e -> tab.reload());
    }

    private void onTranslateFailed(@NonNull GeckoTabView tab, @NonNull String to, @Nullable Throwable e) {
        if (pendingTab != tab) return;
        pendingTab = null;
        hideProgress();
        String reason = Lang.t("ページを翻訳できませんでした");
        if (e instanceof TranslationsController.TranslationsException) {
            int code = ((TranslationsController.TranslationsException) e).code;
            if (code == TranslationsController.TranslationsException.ERROR_MODEL_COULD_NOT_DOWNLOAD
                    || code == TranslationsController.TranslationsException.ERROR_MODEL_DOWNLOAD_REQUIRED) {
                reason = Lang.t("翻訳データをダウンロードできませんでした");
            } else if (code == TranslationsController.TranslationsException.ERROR_LANGUAGE_NOT_SUPPORTED) {
                reason = Lang.t("この言語の翻訳には対応していません");
            }
        }
        TopMessage.make(activity, R.drawable.ic_msg_translate, reason)
                .setSubtitle(Lang.t("Google 翻訳で開くこともできます"))
                .setAction(Lang.t("Google 翻訳"), () -> translateWithWebService(tab, to))
                .setDuration(TopMessage.DURATION_LONG)
                .show();
    }

    // ------------------------------------------------------------------
    // 設定(⚙)
    // ------------------------------------------------------------------

    /** 今のタブの翻訳の設定(メニューの「翻訳」を長押し)。 */
    void showSettings() {
        GeckoTabView tab = host.getCurrentTab();
        if (tab == null || tab.getUrl() == null || host.isInternalUrl(tab.getUrl())) return;
        showSettings(tab);
    }

    private void showSettings(@NonNull GeckoTabView tab) {
        String from = detectedLanguage(tab);
        TranslationState st = tab.getTranslationState();
        if (st != null && st.requestedTranslationPair != null) from = st.requestedTranslationPair.fromLanguage;
        final String source = from;
        String fromName = languageName(source, Lang.t("この言語"));

        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (tab.isTranslated() || pendingTab == tab) {
            labels.add(Lang.t("元の言語で表示"));
            actions.add(() -> showOriginal(tab));
        }
        labels.add(Lang.t("翻訳先の言語を変更（現在: ") + languageName(targetLanguage(), Lang.t("日本語")) + Lang.t("）"));
        actions.add(() -> chooseTargetLanguage(tab));
        labels.add(Lang.t("翻訳元の言語を変更（現在: ") + fromName + Lang.t("）"));
        actions.add(() -> chooseSourceLanguage(tab));
        if (source != null) {
            labels.add(fromName + Lang.t("のページを常に翻訳"));
            actions.add(() -> setLanguageSetting(source, RuntimeTranslation.ALWAYS,
                    fromName + Lang.t("のページを常に翻訳します")));
            labels.add(fromName + Lang.t("のページを翻訳しない"));
            actions.add(() -> setLanguageSetting(source, RuntimeTranslation.NEVER,
                    fromName + Lang.t("のページは翻訳を提案しません")));
        }
        String site = siteOf(tab.getUrl());
        if (site != null && neverSites().contains(site)) {
            labels.add(Lang.t("このサイトの翻訳を再開する"));
            actions.add(() -> {
                setSiteExcluded(tab, false);
                Toast.makeText(activity, Lang.t("このサイトでも翻訳を提案します"), Toast.LENGTH_SHORT).show();
            });
        } else if (site != null) {
            labels.add(Lang.t("このサイトを翻訳しない"));
            actions.add(() -> {
                setSiteExcluded(tab, true);
                Toast.makeText(activity, Lang.t("このサイトは翻訳を提案しません"), Toast.LENGTH_SHORT).show();
                // 翻訳中・翻訳済みなら原文に戻す。案内も消す
                if (tab.isTranslated() || pendingTab == tab) restore(tab);
                else dismissOffer();
            });
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle(Lang.t("翻訳の設定"))
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton(Lang.t("閉じる"), null)
                .show();
    }

    private void setLanguageSetting(@NonNull String lang, @NonNull String setting, @NonNull String done) {
        RuntimeTranslation.setLanguageSettings(lang, setting).accept(
                v -> Toast.makeText(activity, done, Toast.LENGTH_SHORT).show(),
                e -> Toast.makeText(activity, Lang.t("設定を変更できませんでした"), Toast.LENGTH_SHORT).show());
    }

    private void chooseTargetLanguage(@NonNull GeckoTabView tab) {
        chooseLanguage(Lang.t("翻訳先の言語"), false, lang -> {
            prefs().edit().putString(KEY_TARGET, lang).apply();
            String from = currentSource(tab);
            if (from != null) translate(tab, from, lang);
        });
    }

    private void chooseSourceLanguage(@NonNull GeckoTabView tab) {
        chooseLanguage(Lang.t("翻訳元の言語"), true, lang -> translate(tab, lang, targetLanguage()));
    }

    private interface LanguageCallback {
        void onLanguage(@NonNull String code);
    }

    private void chooseLanguage(@NonNull String title, boolean source, @NonNull LanguageCallback cb) {
        RuntimeTranslation.listSupportedLanguages().accept(support -> {
            List<TranslationsController.Language> langs = support == null ? null
                    : (source ? support.fromLanguages : support.toLanguages);
            if (langs == null || langs.isEmpty()) {
                Toast.makeText(activity, Lang.t("翻訳に対応した言語を取得できませんでした"), Toast.LENGTH_SHORT).show();
                return;
            }
            String[] names = new String[langs.size()];
            for (int i = 0; i < langs.size(); i++) {
                TranslationsController.Language l = langs.get(i);
                names[i] = languageName(l.code, l.localizedDisplayName != null ? l.localizedDisplayName : l.code);
            }
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(title)
                    .setItems(names, (d, which) -> cb.onLanguage(langs.get(which).code))
                    .setNegativeButton(Lang.t("キャンセル"), null)
                    .show();
        }, e -> Toast.makeText(activity, Lang.t("翻訳に対応した言語を取得できませんでした"), Toast.LENGTH_SHORT).show());
    }

    // ------------------------------------------------------------------
    // 端末内翻訳が使えない場合
    // ------------------------------------------------------------------

    private void translateWithWebService(@NonNull GeckoTabView tab, @NonNull String to) {
        pendingTab = null;
        hideProgress();
        String url = tab.getUrl();
        if (url == null || url.isEmpty()) return;
        try {
            tab.loadUrl("https://translate.google.com/translate?sl=auto&tl=" + to + "&hl=" + to
                    + "&u=" + URLEncoder.encode(url, "UTF-8"));
        } catch (Exception e) {
            Toast.makeText(activity, Lang.t("翻訳中にエラーが発生しました"), Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------
    // ユーティリティ
    // ------------------------------------------------------------------

    private SharedPreferences prefs() {
        return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 設定「自動で翻訳する」(既定はオフ = 尋ねる)。 */
    private boolean isAutoTranslate() {
        return BrowserPrefs.bool(activity, BrowserPrefs.TRANSLATE_AUTO, false);
    }

    @NonNull
    private String targetLanguage() {
        return prefs().getString(KEY_TARGET, Lang.tag());
    }

    @Nullable
    private static String detectedLanguage(@NonNull GeckoTabView tab) {
        TranslationState st = tab.getTranslationState();
        if (st == null || st.detectedLanguages == null) return null;
        String doc = st.detectedLanguages.docLangTag;
        return doc == null || doc.isEmpty() ? null : doc;
    }

    @Nullable
    private static String currentSource(@NonNull GeckoTabView tab) {
        TranslationState st = tab.getTranslationState();
        if (st != null && st.requestedTranslationPair != null) return st.requestedTranslationPair.fromLanguage;
        return detectedLanguage(tab);
    }

    private static boolean sameLanguage(@NonNull String a, @NonNull String b) {
        return Locale.forLanguageTag(a).getLanguage().equals(Locale.forLanguageTag(b).getLanguage());
    }

    /** "en" → "英語"。分からなければ fallback。 */
    @NonNull
    private static String languageName(@Nullable String tag, @NonNull String fallback) {
        if (tag == null || tag.isEmpty()) return fallback;
        String name = Locale.forLanguageTag(tag).getDisplayName(Locale.forLanguageTag(Lang.tag()));
        return name == null || name.isEmpty() || name.equalsIgnoreCase(tag) ? fallback : name;
    }
}
