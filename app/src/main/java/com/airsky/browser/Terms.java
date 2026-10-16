package com.airsky.browser;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 利用規約(assets/terms.md)と、ユーザーが同意したかどうか。
 *
 * <p>規約の中身を変えて改めて同意してもらいたい時は {@link #VERSION} を上げる。
 * 同意済みの版より新しければ、次の起動時にもう一度同意の画面を出す。</p>
 */
final class Terms {

    /** 今の利用規約の版。 */
    static final int VERSION = 1;

    private static final String PREFS = "AppPrefs";
    private static final String KEY_ACCEPTED = "termsAcceptedVersion";
    private static final String ASSET = "terms.md";

    private Terms() {}

    static boolean isAccepted(@NonNull Context c) {
        return prefs(c).getInt(KEY_ACCEPTED, 0) >= VERSION;
    }

    static void accept(@NonNull Context c) {
        prefs(c).edit().putInt(KEY_ACCEPTED, VERSION).apply();
    }

    /** 規約の文章(Markdown)。読めなければ空文字。 */
    @NonNull
    static String load(@NonNull Context c) {
        try (InputStream in = c.getAssets().open(ASSET)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return "";
        }
    }

    /** 1 行目の見出し(# Aozora 利用規約)は画面の見出しと重なるので除いた本文。 */
    @NonNull
    static String body(@NonNull Context c) {
        String text = load(c);
        if (text.startsWith("# ")) {
            int nl = text.indexOf('\n');
            text = nl >= 0 ? text.substring(nl + 1).trim() : "";
        }
        return text;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
