package com.airsky.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;

/**
 * ボトムバー用のボタン。アイコンの形のまま、下向きに小さくぼかした影を付ける。
 *
 * 影はアイコンを一度ビットマップに描いて不透明な部分だけを取り出し、ぼかして少し下にずらして描く。
 * アイコン・大きさ・押した状態が変わった時だけ作り直す。
 */
public class ShadowImageButton extends AppCompatImageButton {

    /** 影を下へずらす量 / ぼかしの半径(dp)。 */
    private static final float OFFSET_DP = 1.2f;
    private static final float BLUR_DP = 1.6f;
    private static final int SHADOW_ALPHA = 0x55;

    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] blurOffset = new int[2];
    private final float offsetY;

    @Nullable private Bitmap shadow;
    private boolean shadowDirty = true;
    private boolean drawingShadowSource;
    private boolean shadowEnabled = true;

    public ShadowImageButton(Context context) {
        this(context, null);
    }

    public ShadowImageButton(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, androidx.appcompat.R.attr.imageButtonStyle);
    }

    public ShadowImageButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        offsetY = OFFSET_DP * density;
        blurPaint.setMaskFilter(new BlurMaskFilter(BLUR_DP * density, BlurMaskFilter.Blur.NORMAL));
        shadowPaint.setColor(0xFF000000);
        shadowPaint.setAlpha(SHADOW_ALPHA);
    }

    /** 影を付けるか(URL バー横のボタンは、タブレット UI の時だけ影を付ける)。 */
    public void setShadowEnabled(boolean enabled) {
        if (shadowEnabled == enabled) return;
        shadowEnabled = enabled;
        shadowDirty = true;
        invalidate();
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        shadowDirty = true;
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        shadowDirty = true;
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        shadowDirty = true;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        shadowDirty = true;
    }

    @Override
    public void invalidateDrawable(Drawable dr) {
        super.invalidateDrawable(dr);
        // アニメーションするアイコンなどで中身が変わった
        if (dr == getDrawable()) shadowDirty = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        shadowDirty = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (drawingShadowSource) {
            super.onDraw(canvas);
            return;
        }
        if (!shadowEnabled) {
            super.onDraw(canvas);
            return;
        }
        if (shadowDirty) rebuildShadow();
        if (shadow != null) {
            canvas.drawBitmap(shadow, blurOffset[0], blurOffset[1] + offsetY, shadowPaint);
        }
        super.onDraw(canvas);
    }

    private void rebuildShadow() {
        shadowDirty = false;
        if (shadow != null) {
            shadow.recycle();
            shadow = null;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0 || getDrawable() == null) return;
        Bitmap source = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(source);
        drawingShadowSource = true;
        try {
            super.onDraw(c);
        } finally {
            drawingShadowSource = false;
        }
        shadow = source.extractAlpha(blurPaint, blurOffset);
        source.recycle();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (shadow != null) {
            shadow.recycle();
            shadow = null;
        }
        shadowDirty = true;
    }
}
