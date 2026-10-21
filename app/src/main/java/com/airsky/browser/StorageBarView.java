package com.airsky.browser;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

/**
 * ストレージ管理の画面の上に出す、Pixel の「ストレージ」風の横長の帯。
 * 内訳ごとに色を分けて左からつなげて描き、値が決まると左から順に伸びる。
 * 区切りの間は少しあけ、帯の両端だけ丸くする。
 */
public class StorageBarView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint seg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final float density;

    private float[] fractions = new float[0];
    private int[] colors = new int[0];
    private float progress = 1f;
    @Nullable private ValueAnimator anim;

    public StorageBarView(Context context) {
        this(context, null);
    }

    public StorageBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
    }

    public void setTrackColor(@ColorInt int color) {
        track.setColor(color);
        invalidate();
    }

    /**
     * @param fractions 帯全体に対する各内訳の割合(合計は 1 以下)
     * @param animate   左から伸ばすか(初めて値が決まった時だけ)
     */
    public void setSegments(float[] fractions, int[] colors, boolean animate) {
        this.fractions = fractions.clone();
        this.colors = colors.clone();
        if (anim != null) anim.cancel();
        if (!animate) {
            progress = 1f;
            invalidate();
            return;
        }
        progress = 0f;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1100);
        anim.setStartDelay(120);
        // Material の「強調」の動き(素早く出て、ゆっくり止まる)
        anim.setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f));
        anim.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = resolveSize(Math.round(20 * density), heightMeasureSpec);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float r = h / 2f;
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRect(rect, track);

        // 全体を progress の所まで描く。内訳は左から順に現れる
        float gap = 2 * density;
        float min = 3 * density;
        float limit = w * progress;
        // 区切りの隙間と、小さすぎて見えない内訳に足す分を引いてから割り振り、帯からはみ出さないようにする
        int shown = 0;
        float sum = 0, padded = 0;
        for (float f : fractions) {
            if (f <= 0) continue;
            shown++;
            sum += f;
        }
        float avail = Math.max(0, w - gap * Math.max(0, shown - 1));
        for (float f : fractions) if (f > 0 && avail * f < min) padded += min - avail * f;
        float scale = sum > 0 && avail > padded ? (avail - padded) / avail : 1f;
        float x = 0;
        for (int i = 0; i < fractions.length; i++) {
            if (fractions[i] <= 0) continue;
            float len = Math.max(avail * fractions[i] * scale, min);
            float end = Math.min(x + len, limit);
            if (end > x) {
                seg.setColor(colors[i]);
                // 区切りの角を少しだけ丸くする
                float sr = 2 * density;
                rect.set(x, 0, end, h);
                canvas.drawRoundRect(rect, sr, sr, seg);
            }
            x += len + gap;
            if (x >= limit) break;
        }
        canvas.restore();
    }
}
