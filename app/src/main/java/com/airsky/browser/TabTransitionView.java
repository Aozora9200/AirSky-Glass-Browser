package com.airsky.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * タブ一覧を開く/閉じるときに、ページの画像を「画面全体 ⇔ カードのサムネイル」の間で
 * 拡大縮小させるためのビュー。画像は常に幅に合わせて上端揃えで描き、はみ出た下側は切り抜く
 * ({@link TopCropImageView} と同じ見え方なので、アニメーションの最後で自然に入れ替わる)。
 */
public class TabTransitionView extends View {

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final RectF tmp = new RectF();
    private final Rect src = new Rect();

    private Bitmap bitmap;
    private float radius;

    public TabTransitionView(Context context) {
        super(context);
        fillPaint.setColor(0xFFFFFFFF);
    }

    public void setBitmap(@Nullable Bitmap bitmap) {
        this.bitmap = (bitmap != null && !bitmap.isRecycled()) ? bitmap : null;
        invalidate();
    }

    /** 画像が無い時(読み込み前のタブ等)に塗る色。 */
    public void setFillColor(int color) {
        fillPaint.setColor(color);
        invalidate();
    }

    /** 位置(このビュー内の座標)と角丸の半径を更新する。 */
    public void setFrame(RectF frame, float cornerRadius) {
        rect.set(frame);
        radius = cornerRadius;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (rect.isEmpty()) return;

        canvas.save();
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawRect(rect, fillPaint);

        if (bitmap != null) {
            float scale = Math.max(rect.width() / bitmap.getWidth(), rect.height() / bitmap.getHeight());
            float drawW = bitmap.getWidth() * scale;
            float left = rect.left + (rect.width() - drawW) / 2f;
            tmp.set(left, rect.top, left + drawW, rect.top + bitmap.getHeight() * scale);
            src.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            canvas.drawBitmap(bitmap, src, tmp, bitmapPaint);
        }
        canvas.restore();
    }

    /** a → b を t (0..1) で補間した矩形を out に入れる。 */
    public static void lerp(RectF a, RectF b, float t, RectF out) {
        out.set(a.left + (b.left - a.left) * t,
                a.top + (b.top - a.top) * t,
                a.right + (b.right - a.right) * t,
                a.bottom + (b.bottom - a.bottom) * t);
    }
}
