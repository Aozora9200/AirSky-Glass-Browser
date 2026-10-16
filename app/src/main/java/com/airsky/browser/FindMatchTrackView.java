package com.airsky.browser;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

/**
 * ページ内検索バーの下に出す「何件中どこにいるか」のトラック。
 * 一致した数だけ目盛りを並べ、今の一致には色の付いた粒を置く。次/前へ動かすと粒が弾むように移動する。
 * 一致が多い時は目盛りをやめて、1 本の線の上を粒が動く。
 */
public class FindMatchTrackView extends View {

    /** これより多い時は目盛りを描かない。 */
    private static final int MAX_TICKS = 48;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;

    private int total;
    /** 粒の今の位置(0〜1)。 */
    private float position;
    /** 粒の大きさ(出てくる時に 0 から膨らむ)。 */
    private float dotScale;
    @Nullable private ValueAnimator moveAnim, scaleAnim;

    public FindMatchTrackView(Context context) {
        this(context, null);
    }

    public FindMatchTrackView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        trackPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainerHighest, 0x33888888));
        tickPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOutline, 0xFF888888));
        dotPaint.setColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0xFF415F91));
    }

    /** 一致の数と今の位置(0 始まり)。一致が無ければ粒を消す。 */
    public void setMatches(int current, int total) {
        boolean hadMatches = this.total > 0;
        this.total = total;
        float target = total > 1 ? (float) current / (total - 1) : 0.5f;
        if (total <= 0) {
            animateScale(0f);
        } else if (!hadMatches) {
            // 初めて見つかった: その場所で膨らませる
            cancel(moveAnim);
            position = target;
            animateScale(1f);
        } else {
            animateScale(1f);
            animateTo(target);
        }
        invalidate();
    }

    private void animateTo(float target) {
        cancel(moveAnim);
        // 端から端へ回り込む時(最後 → 最初)は、速めに動かす
        long duration = Math.abs(target - position) > 0.9f ? 260 : 360;
        moveAnim = ValueAnimator.ofFloat(position, target);
        moveAnim.setDuration(duration);
        moveAnim.setInterpolator(new OvershootInterpolator(1.6f));
        moveAnim.addUpdateListener(a -> {
            position = (float) a.getAnimatedValue();
            invalidate();
        });
        moveAnim.start();
    }

    private void animateScale(float target) {
        if (scaleAnim != null && scaleAnim.isRunning()) scaleAnim.cancel();
        if (dotScale == target) return;
        scaleAnim = ValueAnimator.ofFloat(dotScale, target);
        scaleAnim.setDuration(target > dotScale ? 320 : 160);
        if (target > dotScale) scaleAnim.setInterpolator(new OvershootInterpolator(2.2f));
        scaleAnim.addUpdateListener(a -> {
            dotScale = (float) a.getAnimatedValue();
            invalidate();
        });
        scaleAnim.start();
    }

    private static void cancel(@Nullable ValueAnimator a) {
        if (a != null && a.isRunning()) a.cancel();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancel(moveAnim);
        cancel(scaleAnim);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth() - getPaddingLeft() - getPaddingRight();
        float cy = getHeight() / 2f;
        float left = getPaddingLeft();
        float dotR = 4.5f * density;
        // 粒が端からはみ出さないよう、粒の幅の半分だけ内側を動かす
        float half = dotR * 1.6f;
        float start = left + half;
        float span = Math.max(0f, w - half * 2);

        float trackH = 4 * density;
        rect.set(left, cy - trackH / 2, left + w, cy + trackH / 2);
        canvas.drawRoundRect(rect, trackH / 2, trackH / 2, trackPaint);

        if (total > 1 && total <= MAX_TICKS) {
            float tickR = 1.5f * density;
            for (int i = 0; i < total; i++) {
                float x = start + span * i / (total - 1);
                canvas.drawCircle(x, cy, tickR, tickPaint);
            }
        }

        if (dotScale > 0f) {
            float x = start + span * position;
            float r = dotR * dotScale;
            // 粒は横に少し長い角丸にして、動いている感じを出す
            rect.set(x - r * 1.6f, cy - r, x + r * 1.6f, cy + r);
            canvas.drawRoundRect(rect, r, r, dotPaint);
        }
    }
}
