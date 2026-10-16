package com.airsky.browser;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.Nullable;

/**
 * タブグループが 1 つも無い時に出す、小さなアニメーション。
 * ばらばらに浮かんでいるタブが 1 か所に集まり、色の付いた枠(グループ)で囲まれる様子を繰り返す。
 * 「タブをまとめるとグループになる」ことが一目で分かるようにする。
 */
public class TabGroupEmptyView extends View {

    private static final long LOOP_MS = 4200;
    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final PathInterpolator OVERSHOOT = new PathInterpolator(0.34f, 1.56f, 0.64f, 1f);

    /** グループの色(TabGroup の色に合わせた、青・緑・ピンク)。 */
    private static final int[] TAB_ACCENTS = {0xFF7FA8F5, 0xFF6DD58C, 0xFFF3A6C8};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path framePath = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final float dp;

    private int cardColor = 0xFFE9E7EC;
    private int lineColor = 0xFF9A9AA2;
    private int accent = 0xFF445E91;

    private float t; // 0..1 のループ位置
    @Nullable private ValueAnimator loop;

    public TabGroupEmptyView(Context context) {
        this(context, null);
    }

    public TabGroupEmptyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = context.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth(2.5f * dp);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setColors(int card, int line, int accent) {
        this.cardColor = card;
        this.lineColor = line;
        this.accent = accent;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolveSize(Math.round(220 * dp), widthMeasureSpec);
        int h = resolveSize(Math.round(150 * dp), heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopLoop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateLoop();
    }

    private void updateLoop() {
        if (isAttachedToWindow() && isShown()) {
            if (loop == null) {
                loop = ValueAnimator.ofFloat(0f, 1f);
                loop.setDuration(LOOP_MS);
                loop.setRepeatCount(ValueAnimator.INFINITE);
                loop.setInterpolator(null);
                loop.addUpdateListener(a -> {
                    t = (float) a.getAnimatedValue();
                    invalidate();
                });
                loop.start();
            }
        } else {
            stopLoop();
        }
    }

    private void stopLoop() {
        if (loop != null) {
            loop.cancel();
            loop = null;
        }
    }

    /** t の [from, to] の区間を 0..1 にする。 */
    private static float seg(float t, float from, float to) {
        if (t <= from) return 0f;
        if (t >= to) return 1f;
        return (t - from) / (to - from);
    }

    private static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f + 6 * dp;

        float cardW = 46 * dp;
        float cardH = 58 * dp;
        float gap = 8 * dp;

        // 全体のフェード(最後に消えて、また最初から)
        float out = seg(t, 0.86f, 1f);
        int globalAlpha = Math.round(255 * (1f - EMPHASIZED.getInterpolation(out)));

        // 1) タブが現れる → 2) 集まる → 3) 枠が描かれる → 4) ふわふわ
        float gather = EMPHASIZED.getInterpolation(seg(t, 0.22f, 0.52f));
        float frame = EMPHASIZED.getInterpolation(seg(t, 0.46f, 0.72f));
        float bob = (float) Math.sin(t * Math.PI * 4) * 2.5f * dp * seg(t, 0.6f, 0.75f);

        // ばらばらの位置(少し傾いている)→ 並んだ位置
        float[][] scattered = {
                {cx - 70 * dp, cy - 18 * dp, -14f},
                {cx + 4 * dp, cy + 16 * dp, 9f},
                {cx + 72 * dp, cy - 22 * dp, 16f},
        };
        float totalW = cardW * 3 + gap * 2;
        float startX = cx - totalW / 2f + cardW / 2f;

        // グループの枠
        float pad = 10 * dp;
        float fl = cx - totalW / 2f - pad;
        float ft = cy - cardH / 2f - pad - 14 * dp;
        float fr = cx + totalW / 2f + pad;
        float fb = cy + cardH / 2f + pad;
        if (frame > 0f) {
            framePath.reset();
            rect.set(fl, ft + bob, fr, fb + bob);
            framePath.addRoundRect(rect, 22 * dp, 22 * dp, Path.Direction.CW);
            measure.setPath(framePath, false);
            float len = measure.getLength();
            // 枠の内側をうっすら塗る
            fill.setColor(accent);
            fill.setAlpha(Math.round(globalAlpha * 0.12f * frame));
            canvas.drawRoundRect(rect, 22 * dp, 22 * dp, fill);
            // 枠線を描いていく
            stroke.setColor(accent);
            stroke.setAlpha(globalAlpha);
            stroke.setPathEffect(new DashPathEffect(new float[]{len * frame, len}, 0));
            canvas.drawPath(framePath, stroke);
            stroke.setPathEffect(null);

            // グループ名の代わりの丸と線
            float labelIn = EMPHASIZED.getInterpolation(seg(t, 0.6f, 0.76f));
            fill.setColor(accent);
            fill.setAlpha(Math.round(globalAlpha * labelIn));
            canvas.drawCircle(fl + 18 * dp, ft + 14 * dp + bob, 5 * dp * labelIn, fill);
            rect.set(fl + 28 * dp, ft + 11 * dp + bob,
                    fl + 28 * dp + 48 * dp * labelIn, ft + 17 * dp + bob);
            canvas.drawRoundRect(rect, 3 * dp, 3 * dp, fill);
        }

        for (int i = 0; i < 3; i++) {
            float appear = seg(t, 0.02f + i * 0.05f, 0.18f + i * 0.05f);
            if (appear <= 0f) continue;
            float scale = OVERSHOOT.getInterpolation(appear);
            float x = lerp(scattered[i][0], startX + i * (cardW + gap), gather);
            float y = lerp(scattered[i][1], cy + 7 * dp, gather) + bob;
            float rot = lerp(scattered[i][2], 0f, gather);
            // 集まる前は、それぞれが少しだけ上下に揺れている
            y += (float) Math.sin((t * 6 + i) * Math.PI) * 3 * dp * (1f - gather);

            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(rot);
            canvas.scale(scale, scale);
            int a = Math.round(globalAlpha * Math.min(1f, appear * 2f));
            // カード
            rect.set(-cardW / 2f, -cardH / 2f, cardW / 2f, cardH / 2f);
            fill.setColor(cardColor);
            fill.setAlpha(a);
            canvas.drawRoundRect(rect, 10 * dp, 10 * dp, fill);
            // 上のタイトル行(ファビコン代わりの丸 + 線)
            fill.setColor(TAB_ACCENTS[i]);
            fill.setAlpha(a);
            canvas.drawCircle(-cardW / 2f + 10 * dp, -cardH / 2f + 10 * dp, 4 * dp, fill);
            fill.setColor(lineColor);
            fill.setAlpha(Math.round(a * 0.7f));
            rect.set(-cardW / 2f + 18 * dp, -cardH / 2f + 8 * dp, cardW / 2f - 8 * dp, -cardH / 2f + 12 * dp);
            canvas.drawRoundRect(rect, 2 * dp, 2 * dp, fill);
            // ページの中身(サムネイル)
            fill.setColor(TAB_ACCENTS[i]);
            fill.setAlpha(Math.round(a * 0.35f));
            rect.set(-cardW / 2f + 6 * dp, -cardH / 2f + 19 * dp, cardW / 2f - 6 * dp, cardH / 2f - 6 * dp);
            canvas.drawRoundRect(rect, 6 * dp, 6 * dp, fill);
            canvas.restore();
        }
    }
}
