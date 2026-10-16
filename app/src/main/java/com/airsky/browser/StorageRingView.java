package com.airsky.browser;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

/**
 * ダウンロード画面の上のカードに出す、端末の容量の輪。
 * 外側の輪は端末全体の使用量、内側の細い輪のうち濃い所が Aozora でダウンロードしたファイルの量。
 * 値が決まると 0 から伸びるように描く。
 */
public class StorageRingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint used = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float density;

    private float usedFraction, mineFraction;
    private float progress;
    @Nullable private ValueAnimator anim;

    public StorageRingView(Context context) {
        this(context, null);
    }

    public StorageRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        for (Paint p : new Paint[]{track, used, mine}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        track.setStrokeWidth(8 * density);
        used.setStrokeWidth(8 * density);
        mine.setStrokeWidth(8 * density);
    }

    public void setColors(@ColorInt int trackColor, @ColorInt int usedColor, @ColorInt int mineColor) {
        track.setColor(trackColor);
        used.setColor(usedColor);
        mine.setColor(mineColor);
        invalidate();
    }

    /** @param usedFraction 端末の使用率 @param mineFraction そのうち Aozora のダウンロードの割合(端末全体に対して) */
    public void setValues(float usedFraction, float mineFraction) {
        boolean first = this.usedFraction == 0f && this.mineFraction == 0f;
        this.usedFraction = clamp(usedFraction);
        this.mineFraction = Math.min(this.usedFraction, clamp(mineFraction));
        if (!first) {
            invalidate();
            return;
        }
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(900);
        anim.setStartDelay(150);
        anim.setInterpolator(new DecelerateInterpolator(2.2f));
        anim.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float stroke = used.getStrokeWidth();
        float size = Math.min(getWidth(), getHeight());
        float inset = stroke / 2f + 1;
        float left = (getWidth() - size) / 2f + inset;
        float top = (getHeight() - size) / 2f + inset;
        oval.set(left, top, left + size - inset * 2, top + size - inset * 2);
        canvas.drawArc(oval, 0, 360, false, track);
        float sweepUsed = 360f * usedFraction * progress;
        if (sweepUsed > 0.5f) canvas.drawArc(oval, -90, sweepUsed, false, used);
        // ダウンロードした分は、使用量の始まりに重ねて別の色で描く(少なくても見えるよう最低 6°)
        if (mineFraction > 0f) {
            float sweepMine = Math.max(6f, 360f * mineFraction) * progress;
            canvas.drawArc(oval, -90, Math.min(sweepMine, Math.max(sweepUsed, 6f)), false, mine);
        }
    }
}
