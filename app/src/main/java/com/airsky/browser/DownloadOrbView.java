package com.airsky.browser;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * 簡易ブラウザのダウンロード画面の丸い進行表示。
 *
 * 外周のリングが進み具合、中央に種類のアイコン。サイズが分からない間は弧が回り続け、
 * 終わるとリングが満ちて中央のアイコンがチェックに替わる。周りには空気のような輪がゆっくり広がる。
 */
public class DownloadOrbView extends View {

    public static final int STATE_RUNNING = 0;
    public static final int STATE_DONE = 1;
    public static final int STATE_FAILED = 2;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripple = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private int accent = 0xFF1C74B5;
    private int errorColor = 0xFFC0392B;
    @Nullable private Drawable icon;
    @Nullable private Drawable doneIcon;
    private int state = STATE_RUNNING;
    /** 0〜1。負ならサイズ不明(回り続ける)。 */
    private float target = -1f;
    private float shown;
    /** 終わった時のチェックの出方(0〜1)。 */
    private float pop = 1f;
    private float spin;
    private float wave;

    private final ValueAnimator ticker = ValueAnimator.ofFloat(0f, 1f);

    public DownloadOrbView(Context context) {
        this(context, null);
    }

    public DownloadOrbView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(6 * d);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(6 * d);
        arc.setStrokeCap(Paint.Cap.ROUND);
        ripple.setStyle(Paint.Style.STROKE);
        ripple.setStrokeWidth(1.5f * d);
        doneIcon = ContextCompat.getDrawable(context, R.drawable.ic_dl_check);

        ticker.setDuration(2400);
        ticker.setRepeatCount(ValueAnimator.INFINITE);
        ticker.setInterpolator(new LinearInterpolator());
        ticker.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            wave = f;
            spin = f * 720f;
            // 表示中の進み具合を目標へなめらかに寄せる
            if (target >= 0f) shown += (target - shown) * 0.18f;
            invalidate();
        });
        applyColors();
    }

    public void setColors(int accent, int error) {
        this.accent = accent;
        this.errorColor = error;
        applyColors();
    }

    public void setIcon(@DrawableRes int res) {
        icon = ContextCompat.getDrawable(getContext(), res);
        invalidate();
    }

    /** @param fraction 0〜1。サイズが分からなければ負の値。 */
    public void setProgress(float fraction) {
        target = fraction < 0f ? -1f : Math.min(1f, fraction);
    }

    public void setState(int newState) {
        if (state == newState) return;
        state = newState;
        applyColors();
        if (newState == STATE_DONE) {
            target = 1f;
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(520);
            a.setStartDelay(120);
            a.setInterpolator(new OvershootInterpolator(2.2f));
            a.addUpdateListener(v -> {
                pop = (float) v.getAnimatedValue();
                invalidate();
            });
            pop = 0f;
            a.start();
        } else if (newState == STATE_FAILED) {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(300);
            a.setInterpolator(new DecelerateInterpolator());
            a.addUpdateListener(v -> invalidate());
            a.start();
        }
    }

    private void applyColors() {
        int color = state == STATE_FAILED ? errorColor : accent;
        track.setColor(withAlpha(color, 0.16f));
        arc.setColor(color);
        disc.setColor(withAlpha(color, state == STATE_DONE ? 0.95f : 0.12f));
        ripple.setColor(color);
        invalidate();
    }

    private static int withAlpha(int color, float alpha) {
        return (Math.round(alpha * 255) << 24) | (color & 0x00FFFFFF);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ticker.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        ticker.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float outer = Math.min(cx, cy);
        float ringR = outer * 0.62f;
        float stroke = arc.getStrokeWidth();

        // 空気の輪(実行中だけ、2 本がずれて外へ広がって消える)
        if (state == STATE_RUNNING) {
            for (int i = 0; i < 2; i++) {
                float t = (wave + i * 0.5f) % 1f;
                float r = ringR + stroke + (outer - ringR - stroke) * t;
                ripple.setAlpha(Math.round(90 * (1f - t)));
                canvas.drawCircle(cx, cy, r, ripple);
            }
        }

        oval.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
        canvas.drawCircle(cx, cy, ringR, track);
        if (state == STATE_RUNNING && target < 0f) {
            canvas.drawArc(oval, spin - 90f, 70f, false, arc);
        } else {
            canvas.drawArc(oval, -90f, Math.max(360f * shown, 1f), false, arc);
        }

        // 中央の円とアイコン
        float discR = ringR - stroke * 2.2f;
        if (state == STATE_DONE) {
            canvas.drawCircle(cx, cy, discR * Math.max(0f, pop), disc);
        } else {
            canvas.drawCircle(cx, cy, discR, disc);
        }
        Drawable d = state == STATE_DONE ? doneIcon : icon;
        if (d != null) {
            float scale = state == STATE_DONE ? Math.max(0f, pop) : 1f;
            int half = Math.round(discR * 0.52f * scale);
            d.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
            d.setTint(state == STATE_DONE ? 0xFFFFFFFF : (state == STATE_FAILED ? errorColor : accent));
            d.draw(canvas);
        }
    }
}
