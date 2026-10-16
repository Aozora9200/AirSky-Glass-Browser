package com.airsky.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * ボトムバーのタブ数ボタン用。{@link ShadowImageButton} と同じく、枠と数字の形のまま
 * 下向きに小さくぼかした影を付ける(他のボタンと見た目をそろえる)。
 */
public class ShadowTextView extends AppCompatTextView {

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

    public ShadowTextView(Context context) {
        this(context, null);
    }

    public ShadowTextView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, android.R.attr.textViewStyle);
    }

    public ShadowTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        offsetY = OFFSET_DP * density;
        blurPaint.setMaskFilter(new BlurMaskFilter(BLUR_DP * density, BlurMaskFilter.Blur.NORMAL));
        shadowPaint.setColor(0xFF000000);
        shadowPaint.setAlpha(SHADOW_ALPHA);
        // ぼかした影が枠の外にはみ出るので、親で切らない
        setClipToOutline(false);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        shadowDirty = true;
    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        shadowDirty = true;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        shadowDirty = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        shadowDirty = true;
    }

    @Override
    public void draw(Canvas canvas) {
        if (drawingShadowSource) {
            super.draw(canvas);
            return;
        }
        if (shadowDirty) rebuildShadow();
        if (shadow != null) {
            canvas.drawBitmap(shadow, blurOffset[0], blurOffset[1] + offsetY, shadowPaint);
        }
        super.draw(canvas);
    }

    private void rebuildShadow() {
        shadowDirty = false;
        if (shadow != null) {
            shadow.recycle();
            shadow = null;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        Bitmap source = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(source);
        drawingShadowSource = true;
        try {
            // 背景(枠)と数字の両方を影の元にする。押した時の波紋(foreground)は影にしない
            if (getBackground() != null) {
                getBackground().setBounds(0, 0, w, h);
                getBackground().draw(c);
            }
            onDraw(c);
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
