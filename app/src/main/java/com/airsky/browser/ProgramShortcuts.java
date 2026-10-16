package com.airsky.browser;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import java.security.SecureRandom;
import java.util.Collections;

/**
 * Java 実行・Python 実行に追加したプログラムを、ホーム画面のショートカットにする。
 *
 * <p>ショートカットは {@link ProgramShortcutActivity} を通してプログラムを始める。ほかのアプリから
 * 勝手に始められないよう、このアプリだけが知る合言葉をショートカットに入れておく。</p>
 */
final class ProgramShortcuts {

    private static final String TAG = "ProgramShortcuts";
    static final String KIND_JAVA = "java";
    static final String KIND_PYTHON = "python";
    static final String EXTRA_KIND = "program_kind";
    static final String EXTRA_ID = "program_id";
    static final String EXTRA_TOKEN = "program_token";

    private ProgramShortcuts() {}

    static boolean supported(@NonNull Context c) {
        return ShortcutManagerCompat.isRequestPinShortcutSupported(c);
    }

    static String token(@NonNull Context c) {
        SharedPreferences p = c.getSharedPreferences("program_shortcuts", Context.MODE_PRIVATE);
        String t = p.getString("token", null);
        if (t == null) {
            byte[] b = new byte[16];
            new SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            t = sb.toString();
            p.edit().putString("token", t).apply();
        }
        return t;
    }

    private static String shortcutId(String kind, String id) {
        return "program-" + kind + "-" + id;
    }

    /** ホーム画面にショートカットを置く(ランチャーの確認が出る)。 */
    static void pin(@NonNull Activity a, @NonNull String kind, @NonNull String id, @NonNull String name) {
        if (!supported(a)) {
            Toast.makeText(a, Lang.t("このホームアプリはショートカットの追加に対応していません"), Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(a, ProgramShortcutActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TOKEN, token(a));
        ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(a, shortcutId(kind, id))
                .setShortLabel(name.length() > 24 ? name.substring(0, 24) : name)
                .setLongLabel(name)
                .setIcon(IconCompat.createWithAdaptiveBitmap(icon(a, kind)))
                .setIntent(intent)
                .build();
        // プログラムを消した時に使えなくした同じ ID のショートカットが残っていると、追加を断られる
        try {
            ShortcutManagerCompat.enableShortcuts(a, Collections.singletonList(info));
        } catch (RuntimeException ignored) {
        }
        // ホームアプリが実際に置いた時だけ届く知らせ(置かれなかったのに「追加しました」と出さない)
        PendingIntent added = PendingIntent.getBroadcast(a, 0,
                new Intent(a, Added.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean ok;
        try {
            ok = ShortcutManagerCompat.requestPinShortcut(a, info, added.getIntentSender());
        } catch (RuntimeException e) {
            Log.w(TAG, "requestPinShortcut failed", e);
            ok = false;
        }
        Log.i(TAG, "requestPinShortcut " + shortcutId(kind, id) + " -> " + ok);
        Toast.makeText(a, ok ? Lang.t("ホーム画面への追加を確認してください") : Lang.t("ショートカットを追加できませんでした"),
                Toast.LENGTH_SHORT).show();
    }

    /** ホームアプリがショートカットを置き終えた知らせ。 */
    public static final class Added extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            Log.i(TAG, "pinned shortcut added");
            Toast.makeText(c, Lang.t("ホーム画面に追加しました"), Toast.LENGTH_SHORT).show();
        }
    }

    /** プログラムを消した時: ホーム画面のショートカットを使えなくする。 */
    static void removePinned(@NonNull Context c, @NonNull String kind, @NonNull String id) {
        try {
            ShortcutManagerCompat.disableShortcuts(c, Collections.singletonList(shortcutId(kind, id)),
                    Lang.t("このプログラムは削除されました"));
        } catch (RuntimeException ignored) {
        }
    }

    /** Pixel のアイコン風: 色の付いた全面の下地に、白い記号。 */
    private static Bitmap icon(Context c, String kind) {
        float d = c.getResources().getDisplayMetrics().density;
        int size = Math.round(108 * d);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        boolean python = KIND_PYTHON.equals(kind);
        int bg = ContextCompat.getColor(c, python ? R.color.ps_tone_blue_bg : R.color.ps_tone_teal_bg);
        int fg = ContextCompat.getColor(c, python ? R.color.ps_tone_blue_fg : R.color.ps_tone_teal_fg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(bg);
        canvas.drawRect(0, 0, size, size, p);
        Drawable glyph = ContextCompat.getDrawable(c, python ? R.drawable.ic_tool_python : R.drawable.ic_tool_java);
        if (glyph != null) {
            glyph = DrawableCompat.wrap(glyph.mutate());
            DrawableCompat.setTint(glyph, fg);
            int g = Math.round(46 * d), o = (size - g) / 2;
            glyph.setBounds(o, o, o + g, o + g);
            glyph.draw(canvas);
        }
        return bmp;
    }
}
