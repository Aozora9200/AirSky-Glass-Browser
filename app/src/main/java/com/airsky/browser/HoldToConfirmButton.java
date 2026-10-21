package com.airsky.browser;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

/**
 * 押し続けると左から色が満ちていき、最後まで満ちた時だけ実行するボタン。
 * 途中で指を離すと元に戻る。間違えて押しただけでは実行されないので、取り消せない操作に使う。
 */
public class HoldToConfirmButton extends View {

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textOnFill = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final float density;

    private String label = "";
    private long holdMs = 2500;
    private float progress;
    @Nullable private ValueAnimator anim;
    @Nullable private Runnable onConfirm;
    private boolean done;

    public HoldToConfirmButton(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        text.setTextSize(15 * getResources().getDisplayMetrics().scaledDensity);
        text.setFakeBoldText(true);
        text.setTextAlign(Paint.Align.CENTER);
        textOnFill.set(text);
        setClickable(true);
        setFocusable(true);
    }

    public void setColors(@ColorInt int container, @ColorInt int onContainer,
                          @ColorInt int fillColor, @ColorInt int onFill) {
        bg.setColor(container);
        text.setColor(onContainer);
        fill.setColor(fillColor);
        textOnFill.setColor(onFill);
        invalidate();
    }

    public void setLabel(String label) {
        this.label = label;
        setContentDescription(label);
        invalidate();
    }

    public void setHoldMs(long ms) {
        holdMs = ms;
    }

    public void setOnConfirm(@Nullable Runnable r) {
        onConfirm = r;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = resolveSize(Math.round(56 * density), heightMeasureSpec);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled() || done) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                startHold();
                return true;
            case MotionEvent.ACTION_MOVE:
                // ボタンの外に指がずれたらやめる
                if (e.getX() < 0 || e.getY() < 0 || e.getX() > getWidth() || e.getY() > getHeight()) release();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                release();
                return true;
        }
        return false;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setLongClickable(true);
    }

    /** ユーザー補助(TalkBack など)からは長押しの操作で実行する。 */
    @Override
    public boolean performLongClick() {
        if (done) return false;
        finish();
        return true;
    }

    private void startHold() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        animateTo(1f, Math.round(holdMs * (1f - progress)), new LinearInterpolator());
    }

    private void release() {
        if (done || anim == null) return;
        if (progress >= 1f) return;
        animateTo(0f, Math.round(350 * progress) + 1, new DecelerateInterpolator());
    }

    private void animateTo(float target, long duration, android.animation.TimeInterpolator interp) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(progress, target);
        anim.setDuration(duration);
        anim.setInterpolator(interp);
        anim.addUpdateListener(a -> {
            float before = progress;
            progress = (float) a.getAnimatedValue();
            // 4 分の 1 ごとに軽く振動させて、進んでいることを伝える
            if (target == 1f && (int) (before * 4) != (int) (progress * 4) && progress < 1f) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            invalidate();
            if (target == 1f && progress >= 1f) finish();
        });
        anim.start();
    }

    private void finish() {
        if (done) return;
        done = true;
        progress = 1f;
        invalidate();
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (onConfirm != null) onConfirm.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
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
        canvas.drawRect(rect, bg);
        float ty = h / 2f - (text.descent() + text.ascent()) / 2f;
        canvas.drawText(label, w / 2f, ty, text);
        // 満ちた所は色を変え、文字も満ちた色の上で読める色にする
        float fw = w * progress;
        if (fw > 0) {
            canvas.clipRect(0, 0, fw, h);
            canvas.drawRect(0, 0, fw, h, fill);
            canvas.drawText(label, w / 2f, ty, textOnFill);
        }
        canvas.restore();
    }
}
