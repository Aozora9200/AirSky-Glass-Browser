package com.airsky.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.airsky.browser.jvm.JvmInput;
import com.airsky.browser.jvm.JvmRuntime;
import com.airsky.browser.jvm.java.awt.Window;

/**
 * JAR のプログラムのウィンドウ(一番手前のもの)を表示する。
 * ウィンドウの絵は表示場所に合わせた倍率で描かれているので、真ん中に置いて角を丸く切り抜くだけ。
 * タッチはウィンドウの座標に直して、マウスの操作として EDT へ渡す。
 */
public class JvmWindowView extends View {

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private final Path clip = new Path();
    @Nullable private Window touching;

    public JvmWindowView(Context c) {
        this(c, null);
    }

    public JvmWindowView(Context c, @Nullable AttributeSet attrs) {
        super(c, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        JvmRuntime.setViewport(w, h, getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Window w = JvmRuntime.topWindow();
        if (w == null) return;
        synchronized (w.jvm$frontLock()) {
            Bitmap b = w.jvm$front();
            if (b == null || b.isRecycled()) return;
            float s = Math.min(1f, Math.min(getWidth() / (float) b.getWidth(), getHeight() / (float) b.getHeight()));
            float bw = b.getWidth() * s, bh = b.getHeight() * s;
            float left = (getWidth() - bw) / 2f, top = (getHeight() - bh) / 2f;
            dst.set(left, top, left + bw, top + bh);
            float r = 18 * getResources().getDisplayMetrics().density;
            clip.reset();
            clip.addRoundRect(dst, r, r, Path.Direction.CW);
            int sc = canvas.save();
            canvas.clipPath(clip);
            canvas.drawBitmap(b, null, dst, paint);
            canvas.restoreToCount(sc);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touching = JvmRuntime.topWindow();
                action = JvmInput.DOWN;
                requestFocus();
                break;
            case MotionEvent.ACTION_MOVE: action = JvmInput.MOVE; break;
            case MotionEvent.ACTION_UP: action = JvmInput.UP; break;
            case MotionEvent.ACTION_CANCEL: action = JvmInput.CANCEL; break;
            default: return true;
        }
        Window w = touching;
        if (w == null || dst.width() <= 0) return true;
        float fx = (ev.getX() - dst.left) / dst.width() * w.getWidth();
        float fy = (ev.getY() - dst.top) / dst.height() * w.getHeight();
        int x = Math.round(fx), y = Math.round(fy);
        JvmRuntime.invokeLater(() -> JvmInput.touch(w, action, x, y));
        if (action == JvmInput.UP || action == JvmInput.CANCEL) touching = null;
        return true;
    }
}
