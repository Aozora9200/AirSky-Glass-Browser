package com.airsky.browser;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;

/** データの引っ越しの画面({@link DeviceMigrationActivity})で使う、動く絵。 */
final class MigrationViews {

    private MigrationViews() {}

    /** Material 3 の「強調」の動き(減速)。 */
    static final PathInterpolator EMPHASIZED = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    static final PathInterpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);

    @ColorInt
    static int attr(@NonNull Context c, int attr) {
        return MaterialColors.getColor(c, attr, 0xFF888888);
    }

    // ------------------------------------------------------------------
    // 2 台の端末の間を点が流れる絵(トップ)
    // ------------------------------------------------------------------

    static final class HeroView extends View {
        /** 0: 両方向(トップ) / 1: 左→右(送る) / -1: 右→左(受け取る)。 */
        private int direction;
        private float phase;
        private float enter;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arc = new Path();
        private final PathMeasure measure = new PathMeasure();
        private final float[] pos = new float[2];
        private final RectF r = new RectF();
        private final float d;
        private ValueAnimator loop;
        private final int cLeft;
        private final int cRight;
        private final int cFrame;
        private final int cDot;
        private final int cDot2;

        HeroView(@NonNull Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            cLeft = attr(c, com.google.android.material.R.attr.colorPrimaryContainer);
            cRight = attr(c, com.google.android.material.R.attr.colorTertiaryContainer);
            cFrame = attr(c, com.google.android.material.R.attr.colorOnSurfaceVariant);
            cDot = attr(c, androidx.appcompat.R.attr.colorPrimary);
            cDot2 = attr(c, com.google.android.material.R.attr.colorTertiary);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(2.5f * d);
            stroke.setStrokeCap(Paint.Cap.ROUND);
        }

        void setDirection(int direction) {
            this.direction = direction;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            setMeasuredDimension(width, Math.round(190 * d));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            ValueAnimator in = ValueAnimator.ofFloat(0f, 1f);
            in.setDuration(700);
            in.setInterpolator(new OvershootInterpolator(1.2f));
            in.addUpdateListener(a -> {
                enter = (float) a.getAnimatedValue();
                invalidate();
            });
            in.start();
            loop = ValueAnimator.ofFloat(0f, 1f);
            loop.setDuration(1800);
            loop.setRepeatCount(ValueAnimator.INFINITE);
            loop.setInterpolator(new LinearInterpolator());
            loop.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
            loop.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (loop != null) loop.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float pw = 58 * d;
            float ph = 104 * d;
            float cy = h * 0.62f;
            float slide = (1f - enter) * 40 * d;
            float lx = w * 0.5f - 96 * d - slide;
            float rx = w * 0.5f + 96 * d + slide;
            int alpha = Math.round(Math.max(0f, Math.min(1f, enter)) * 255);

            // 弧(点が流れる道)
            arc.reset();
            arc.moveTo(lx, cy - ph / 2 - 4 * d);
            arc.cubicTo(lx + 30 * d, cy - ph / 2 - 70 * d, rx - 30 * d, cy - ph / 2 - 70 * d, rx, cy - ph / 2 - 4 * d);
            measure.setPath(arc, false);
            float len = measure.getLength();

            // 端末
            drawPhone(canvas, lx, cy, pw, ph, cLeft, alpha, direction >= 0 ? 1f : 0.85f);
            drawPhone(canvas, rx, cy, pw, ph, cRight, alpha, direction <= 0 ? 1f : 0.85f);

            // 流れる点
            int n = 7;
            for (int i = 0; i < n; i++) {
                float t = (phase + i / (float) n) % 1f;
                boolean back = direction < 0 || (direction == 0 && i % 2 == 1);
                float at = back ? 1f - t : t;
                measure.getPosTan(at * len, pos, null);
                float fade = (float) Math.sin(Math.PI * t);
                fill.setColor(back ? cDot2 : cDot);
                fill.setAlpha(Math.round(alpha * fade));
                canvas.drawCircle(pos[0], pos[1], (2.5f + 2.5f * fade) * d, fill);
            }
        }

        private void drawPhone(Canvas c, float cx, float cy, float pw, float ph, int screen, int alpha, float scale) {
            c.save();
            c.scale(scale, scale, cx, cy);
            r.set(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2);
            fill.setColor(screen);
            fill.setAlpha(alpha);
            c.drawRoundRect(r, 12 * d, 12 * d, fill);
            stroke.setColor(cFrame);
            stroke.setAlpha(alpha);
            c.drawRoundRect(r, 12 * d, 12 * d, stroke);
            // 画面の中の線(ページの絵)
            stroke.setAlpha(Math.round(alpha * 0.45f));
            float y = r.top + 18 * d;
            for (int i = 0; i < 4; i++) {
                float lw = i == 0 ? pw * 0.55f : pw * (0.62f - i * 0.08f);
                c.drawLine(r.left + 10 * d, y, r.left + 10 * d + lw, y, stroke);
                y += 12 * d;
            }
            fill.setColor(cFrame);
            fill.setAlpha(Math.round(alpha * 0.6f));
            c.drawCircle(cx, r.bottom - 10 * d, 3 * d, fill);
            c.restore();
        }
    }

    // ------------------------------------------------------------------
    // 広がる波(待っている間)
    // ------------------------------------------------------------------

    static final class PulseLayout extends FrameLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ValueAnimator loop;
        private float phase;
        private float minRadius;

        PulseLayout(@NonNull Context c) {
            super(c);
            setWillNotDraw(false);
            setClipChildren(false);
            paint.setColor(attr(c, androidx.appcompat.R.attr.colorPrimary));
        }

        void setMinRadius(float px) {
            minRadius = px;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            loop = ValueAnimator.ofFloat(0f, 1f);
            loop.setDuration(2400);
            loop.setRepeatCount(ValueAnimator.INFINITE);
            loop.setInterpolator(new LinearInterpolator());
            loop.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
            loop.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (loop != null) loop.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float max = Math.min(getWidth(), getHeight()) / 2f;
            float min = minRadius > 0 ? minRadius : max * 0.35f;
            for (int i = 0; i < 3; i++) {
                float t = (phase + i / 3f) % 1f;
                float eased = STANDARD.getInterpolation(t);
                paint.setAlpha(Math.round(70 * (1f - t)));
                canvas.drawCircle(cx, cy, min + (max - min) * eased, paint);
            }
        }
    }

    // ------------------------------------------------------------------
    // 進み具合の輪(終わるとチェックに変わる)
    // ------------------------------------------------------------------

    static final class RingView extends View {
        static final int SPIN = 0;
        static final int PROGRESS = 1;
        static final int DONE = 2;
        static final int ERROR = 3;

        private int mode = SPIN;
        private float shown;      // 表示中の割合(なめらかに target へ近づく)
        private float target;
        private float spin;       // 回る角度
        private float mark;       // チェック・× を描いた割合
        private float pop = 1f;   // 終わった時に少し膨らむ
        private final float d;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final Path markPath = new Path();
        private final PathMeasure pm = new PathMeasure();
        private final Path seg = new Path();
        private ValueAnimator ticker;
        private final int cPrimary;
        private final int cOk;
        private final int cOkBg;
        private final int cErr;
        private final int cErrBg;

        RingView(@NonNull Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            cPrimary = attr(c, androidx.appcompat.R.attr.colorPrimary);
            cOk = androidx.core.content.ContextCompat.getColor(c, R.color.ps_tone_green_fg);
            cOkBg = androidx.core.content.ContextCompat.getColor(c, R.color.ps_tone_green_bg);
            cErr = attr(c, androidx.appcompat.R.attr.colorError);
            cErrBg = attr(c, com.google.android.material.R.attr.colorErrorContainer);
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(8 * d);
            track.setColor(attr(c, com.google.android.material.R.attr.colorSecondaryContainer));
            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeWidth(8 * d);
            arcPaint.setStrokeCap(Paint.Cap.ROUND);
            arcPaint.setColor(cPrimary);
            markPaint.setStyle(Paint.Style.STROKE);
            markPaint.setStrokeWidth(9 * d);
            markPaint.setStrokeCap(Paint.Cap.ROUND);
            markPaint.setStrokeJoin(Paint.Join.ROUND);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(30 * d);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            text.setColor(attr(c, com.google.android.material.R.attr.colorOnSurface));
        }

        @Override
        protected void onMeasure(int w, int h) {
            int s = Math.round(168 * d);
            setMeasuredDimension(s, s);
        }

        void spin() {
            mode = SPIN;
            mark = 0;
            invalidate();
        }

        void progress(float fraction) {
            if (mode != PROGRESS) {
                mode = PROGRESS;
                shown = 0;
            }
            target = Math.max(0f, Math.min(1f, fraction));
            invalidate();
        }

        void done() {
            finish(DONE);
        }

        void error() {
            finish(ERROR);
        }

        private void finish(int m) {
            mode = m;
            target = 1f;
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(650);
            a.setInterpolator(EMPHASIZED);
            a.addUpdateListener(v -> {
                mark = (float) v.getAnimatedValue();
                invalidate();
            });
            a.start();
            ValueAnimator p = ValueAnimator.ofFloat(0.85f, 1f);
            p.setDuration(500);
            p.setInterpolator(new OvershootInterpolator(3f));
            p.addUpdateListener(v -> pop = (float) v.getAnimatedValue());
            p.start();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            ticker = ValueAnimator.ofFloat(0f, 1f);
            ticker.setDuration(1000);
            ticker.setRepeatCount(ValueAnimator.INFINITE);
            ticker.addUpdateListener(a -> {
                spin = (spin + 6f) % 360f;
                if (Math.abs(shown - target) > 0.001f) shown += (target - shown) * 0.18f;
                invalidate();
            });
            ticker.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (ticker != null) ticker.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float s = getWidth();
            float pad = 10 * d;
            oval.set(pad, pad, s - pad, s - pad);
            float cx = s / 2f;
            float cy = s / 2f;
            if (mode == DONE || mode == ERROR) {
                canvas.save();
                canvas.scale(pop, pop, cx, cy);
                disc.setColor(mode == DONE ? cOkBg : cErrBg);
                disc.setAlpha(Math.round(255 * Math.min(1f, mark * 1.6f)));
                canvas.drawCircle(cx, cy, (s / 2f - pad) * (0.6f + 0.4f * mark), disc);
                arcPaint.setColor(mode == DONE ? cOk : cErr);
                canvas.drawArc(oval, -90, 360, false, arcPaint);
                markPath.reset();
                if (mode == DONE) {
                    markPath.moveTo(cx - 30 * d, cy + 2 * d);
                    markPath.lineTo(cx - 8 * d, cy + 24 * d);
                    markPath.lineTo(cx + 34 * d, cy - 20 * d);
                } else {
                    markPath.moveTo(cx - 24 * d, cy - 24 * d);
                    markPath.lineTo(cx + 24 * d, cy + 24 * d);
                    markPath.moveTo(cx + 24 * d, cy - 24 * d);
                    markPath.lineTo(cx - 24 * d, cy + 24 * d);
                }
                markPaint.setColor(mode == DONE ? cOk : cErr);
                pm.setPath(markPath, false);
                seg.reset();
                float total = 0;
                do {
                    total += pm.getLength();
                } while (pm.nextContour());
                pm.setPath(markPath, false);
                float remain = total * mark;
                do {
                    float l = pm.getLength();
                    if (remain > 0) pm.getSegment(0, Math.min(l, remain), seg, true);
                    remain -= l;
                } while (pm.nextContour());
                canvas.drawPath(seg, markPaint);
                canvas.restore();
                return;
            }
            canvas.drawArc(oval, 0, 360, false, track);
            arcPaint.setColor(cPrimary);
            if (mode == SPIN) {
                float sweep = 60 + 200 * (float) Math.abs(Math.sin(Math.toRadians(spin)));
                canvas.drawArc(oval, spin * 2 - 90, sweep, false, arcPaint);
            } else {
                canvas.drawArc(oval, -90, Math.max(1f, 360 * shown), false, arcPaint);
                String t = Math.round(shown * 100) + "%";
                canvas.drawText(t, cx, cy - (text.descent() + text.ascent()) / 2, text);
            }
        }
    }
}
