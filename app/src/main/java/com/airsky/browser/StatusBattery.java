package com.airsky.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ステータスバーの電池の表示。「%」「アイコン」「アイコンと %」から選ぶ(デザインの設定)。
 */
final class StatusBattery {

    static final String KEY_STYLE = "battery_style";

    static final int STYLE_PERCENT = 0;
    static final int STYLE_ICON = 1;
    static final int STYLE_ICON_PERCENT = 2;

    /** 残量がこれ以下で充電していない時は、アイコンを赤くする。 */
    static final int LOW_LEVEL = 20;

    private StatusBattery() {
    }

    static int style(@NonNull Context c) {
        int s = c.getSharedPreferences(DesignSettingsActivity.THEME_PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_STYLE, STYLE_PERCENT);
        return s >= STYLE_PERCENT && s <= STYLE_ICON_PERCENT ? s : STYLE_PERCENT;
    }

    static void setStyle(@NonNull Context c, int style) {
        c.getSharedPreferences(DesignSettingsActivity.THEME_PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_STYLE, style).apply();
    }

    /**
     * Pixel 風の横向きの電池。薄い本体の上に残量の分だけ塗り、充電中は稲妻を抜く。
     */
    static final class IconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint boltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF body = new RectF();
        private final RectF fill = new RectF();
        private final RectF cap = new RectF();
        private final Path bolt = new Path();
        private final float density;
        private int color = 0xFFFFFFFF;
        private int lowColor = 0xFFE53935;
        private int level = 100;
        private boolean charging;
        private int alpha = 255;

        IconDrawable(@NonNull Context c) {
            density = c.getResources().getDisplayMetrics().density;
            boltPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.XOR));
        }

        void setColors(int color, int lowColor) {
            this.color = color;
            this.lowColor = lowColor;
            invalidateSelf();
        }

        void setLevel(int level, boolean charging) {
            this.level = Math.max(0, Math.min(level, 100));
            this.charging = charging;
            invalidateSelf();
        }

        @Override
        public int getIntrinsicWidth() {
            return Math.round(22 * density);
        }

        @Override
        public int getIntrinsicHeight() {
            return Math.round(12 * density);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            // 22x12 の比で真ん中に置く
            float w = Math.min(b.width(), b.height() * 22f / 12f);
            float h = w * 12f / 22f;
            float left = b.left + (b.width() - w) / 2f;
            float top = b.top + (b.height() - h) / 2f;
            float u = w / 22f;
            body.set(left, top, left + 20 * u, top + h);
            cap.set(body.right + 0.6f * u, top + h * 0.3f, left + w, top + h * 0.7f);
            float r = 3.2f * u;

            int saved = canvas.saveLayer(b.left, b.top, b.right, b.bottom, null);
            boolean low = level <= LOW_LEVEL && !charging;
            int main = low ? lowColor : color;

            // 本体(薄く)と端子
            paint.setColor(color);
            paint.setAlpha(Math.round(alpha * 0.3f));
            canvas.drawRoundRect(body, r, r, paint);
            paint.setAlpha(Math.round(alpha * (level >= 100 ? 1f : 0.3f)));
            canvas.drawRoundRect(cap, u, u, paint);

            // 残量
            if (level > 0) {
                paint.setColor(main);
                paint.setAlpha(alpha);
                fill.set(body.left, body.top, body.left + body.width() * level / 100f, body.bottom);
                canvas.save();
                canvas.clipRect(fill);
                canvas.drawRoundRect(body, r, r, paint);
                canvas.restore();
            }

            // 充電中の稲妻(塗った所は抜き、薄い所は濃く見える)
            if (charging) {
                float cx = body.centerX();
                float cy = body.centerY();
                bolt.reset();
                bolt.moveTo(cx + 1.2f * u, cy - 5.0f * u);
                bolt.lineTo(cx - 3.2f * u, cy + 0.8f * u);
                bolt.lineTo(cx - 0.2f * u, cy + 0.8f * u);
                bolt.lineTo(cx - 1.2f * u, cy + 5.0f * u);
                bolt.lineTo(cx + 3.2f * u, cy - 0.8f * u);
                bolt.lineTo(cx + 0.2f * u, cy - 0.8f * u);
                bolt.close();
                boltPaint.setColor(color);
                boltPaint.setAlpha(alpha);
                canvas.drawPath(bolt, boltPaint);
            }
            canvas.restoreToCount(saved);
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
