package com.airsky.browser;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * タブ一覧のサムネイル。
 * 高さを幅 × {@link #ASPECT} に固定し、画像は幅に合わせて上端揃えで表示する(ページの上部が見える)。
 * 角は丸く切り抜く。
 */
public class TopCropImageView extends AppCompatImageView {

    /** 高さ / 幅。タブの切り替えアニメーションもこの比率を前提にしている。 */
    public static final float ASPECT = 1.12f;

    private float cornerRadius;

    public TopCropImageView(Context context) {
        this(context, null);
    }

    public TopCropImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        cornerRadius = 20 * getResources().getDisplayMetrics().density;
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        setClipToOutline(true);
    }

    public float getCornerRadius() {
        return cornerRadius;
    }

    /** 角の丸み(タブグループのカードの小さなサムネイルは小さめにする)。 */
    public void setCornerRadius(float radius) {
        if (cornerRadius == radius) return;
        cornerRadius = radius;
        invalidateOutline();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = Math.round(w * ASPECT);
        setMeasuredDimension(w, h);
    }

    @Override
    protected boolean setFrame(int l, int t, int r, int b) {
        boolean changed = super.setFrame(l, t, r, b);
        updateMatrix();
        return changed;
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        updateMatrix();
    }

    private void updateMatrix() {
        Drawable d = getDrawable();
        int vw = getWidth();
        if (d == null || vw <= 0) return;
        int dw = d.getIntrinsicWidth();
        int dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        float vh = getHeight();
        // 幅に合わせる。画像が横長で高さが足りない場合は高さに合わせて中央を切り抜く。
        float scale = Math.max(vw / (float) dw, vh / dh);
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate((vw - dw * scale) / 2f, 0);
        setImageMatrix(m);
    }
}
