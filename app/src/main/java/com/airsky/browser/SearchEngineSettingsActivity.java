package com.airsky.browser;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 検索エンジンの選択。URL バーとスタートページの検索欄で使う検索エンジンが変わる。
 */
public class SearchEngineSettingsActivity extends PixelSettingsActivity {

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("検索エンジン");
    }

    @Override
    protected void onBuild() {
        String current = BrowserPrefs.engineKey(this);
        gap();
        for (String[] e : BrowserPrefs.ENGINES) {
            String key = e[0];
            radioRow(R.drawable.ic_ps_search, e[1], e[2], key.equals(current), () -> {
                BrowserPrefs.setEngineKey(this, key);
                rebuild();
            }).rawIcon(new LogoDrawable(logoText(key), Color.parseColor(e[5])));
        }
        note(Lang.t("URL バーに言葉を入力して検索する時と、スタートページの検索欄で使われます。"));
    }

    @NonNull
    private static String logoText(String key) {
        switch (key) {
            case "yahoo": return "Y!";
            case "bing": return "b";
            case "duckduckgo": return "D";
            case "ecosia": return "E";
            default: return "G";
        }
    }

    /** 検索エンジンの丸いロゴ(ブランドの色の丸に頭文字)。 */
    static final class LogoDrawable extends Drawable {
        private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String label;

        LogoDrawable(String label, int color) {
            this.label = label;
            circle.setColor(color);
            text.setColor(Color.WHITE);
            text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            text.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            float r = Math.min(b.width(), b.height()) / 2f;
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r, circle);
            text.setTextSize(r * (label.length() > 1 ? 0.95f : 1.15f));
            float y = b.exactCenterY() - (text.descent() + text.ascent()) / 2f;
            canvas.drawText(label, b.exactCenterX(), y, text);
        }

        @Override public void setAlpha(int alpha) { circle.setAlpha(alpha); text.setAlpha(alpha); }
        @Override public void setColorFilter(@Nullable ColorFilter cf) { circle.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
