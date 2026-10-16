package com.airsky.browser;

import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.LocaleList;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * アプリの表示言語(日本語 / 英語)。
 *
 * <p>画面の文字はソースに日本語で書いてあり、{@link #t(String)} を通すと英語表示の時だけ
 * assets/i18n/en.tsv の訳に置き換わる(訳の無い文字はそのまま)。レイアウト・strings.xml の文字は
 * values(英語)と values-ja(日本語)のリソースで切り替わる。</p>
 *
 * <p>言語は「端末の設定に合わせる / 日本語 / English」から選べる。端末の言語が日本語以外なら英語で表示する。
 * Android 13 以降は Android の「アプリの言語」とも連動する。</p>
 */
public final class Lang {

    private static final String TAG = "Lang";
    private static final String PREFS = "app_language";
    private static final String KEY_LANG = "lang";

    /** 端末の設定に合わせる。 */
    public static final String SYSTEM = "";
    public static final String JA = "ja";
    public static final String EN = "en";

    @Nullable private static volatile Context app;
    @Nullable private static volatile Boolean english;
    @Nullable private static volatile Map<String, String> dict;

    private Lang() {
    }

    /** アプリの起動時に呼ぶ(選んだ言語をリソースにも反映する)。 */
    static void init(@NonNull Context context) {
        app = context.getApplicationContext();
        english = null;
        if (Build.VERSION.SDK_INT < 33) {
            // Android 12 以前は AppCompat に毎回伝える(Android 13 以降は OS が覚えている)
            String lang = preference(context);
            AppCompatDelegate.setApplicationLocales(lang.isEmpty()
                    ? LocaleListCompat.getEmptyLocaleList() : LocaleListCompat.forLanguageTags(lang));
        }
    }

    /** 選んだ言語(SYSTEM / JA / EN)。 */
    @NonNull
    public static String preference(@NonNull Context c) {
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager lm = c.getSystemService(LocaleManager.class);
            if (lm != null) {
                LocaleList list = lm.getApplicationLocales();
                if (list.isEmpty()) return SYSTEM;
                return JA.equals(list.get(0).getLanguage()) ? JA : EN;
            }
        }
        String v = prefs(c).getString(KEY_LANG, SYSTEM);
        return JA.equals(v) || EN.equals(v) ? v : SYSTEM;
    }

    /** 言語を選ぶ。反映するにはアプリを再起動する。 */
    public static void setPreference(@NonNull Context c, @NonNull String lang) {
        prefs(c).edit().putString(KEY_LANG, lang).commit();
        english = null;
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager lm = c.getSystemService(LocaleManager.class);
            if (lm != null) {
                lm.setApplicationLocales(lang.isEmpty() ? LocaleList.getEmptyLocaleList()
                        : LocaleList.forLanguageTags(lang));
                return;
            }
        }
        AppCompatDelegate.setApplicationLocales(lang.isEmpty()
                ? LocaleListCompat.getEmptyLocaleList() : LocaleListCompat.forLanguageTags(lang));
    }

    private static SharedPreferences prefs(@NonNull Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 英語で表示するか。 */
    public static boolean isEnglish() {
        Boolean e = english;
        if (e != null) return e;
        Context c = app;
        String pref = c != null ? preference(c) : SYSTEM;
        boolean result;
        if (JA.equals(pref)) {
            result = false;
        } else if (EN.equals(pref)) {
            result = true;
        } else {
            result = !JA.equals(systemLanguage());
        }
        english = result;
        return result;
    }

    /** 端末の言語(アプリごとの言語の設定を除く)。 */
    @NonNull
    private static String systemLanguage() {
        LocaleList list = android.content.res.Resources.getSystem().getConfiguration().getLocales();
        Locale l = list.isEmpty() ? Locale.getDefault() : list.get(0);
        return l.getLanguage();
    }

    /** 言語の設定が変わったかもしれない(端末の設定の変更など)。 */
    static void invalidate() {
        english = null;
    }

    /** 表示用の BCP 47 の言語タグ("ja" / "en")。 */
    @NonNull
    public static String tag() {
        return isEnglish() ? EN : JA;
    }

    /** 日本語の文字を、表示する言語の文字にする。 */
    @NonNull
    public static String t(@NonNull String ja) {
        if (!isEnglish()) return ja;
        Map<String, String> d = dictionary();
        String en = d.get(ja);
        return en != null ? en : ja;
    }

    /** {@link #t(String)} してから String.format する。 */
    @NonNull
    public static String f(@NonNull String ja, Object... args) {
        return String.format(Locale.getDefault(), t(ja), args);
    }

    @NonNull
    private static Map<String, String> dictionary() {
        Map<String, String> d = dict;
        if (d != null) return d;
        synchronized (Lang.class) {
            if (dict != null) return dict;
            Map<String, String> m = new HashMap<>(4096);
            Context c = app;
            if (c != null) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(
                        c.getAssets().open("i18n/en.tsv"), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        int tab = line.indexOf('\t');
                        if (tab <= 0) continue;
                        m.put(unescape(line.substring(0, tab)), unescape(line.substring(tab + 1)));
                    }
                } catch (Exception e) {
                    Log.w(TAG, "dictionary", e);
                }
            }
            dict = m;
            return m;
        }
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    default: b.append(n); break;
                }
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }
}
