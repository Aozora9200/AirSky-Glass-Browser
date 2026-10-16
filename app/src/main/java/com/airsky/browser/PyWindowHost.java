package com.airsky.browser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Python の窓の中身(一番外の PyLayout)を入れる枠。
 *
 * <p>中身が画面の幅より広い時(800 ピクセル幅の Canvas など)は、幅に収まるように全体を縮小して表示する。
 * タッチの座標は Android が縮小を戻して子へ渡すので、プログラムには元の座標が届く。
 * 高さは画面より低ければ画面いっぱいに広げ(fill / expand が効くように)、高ければ外側のスクロールで見る。</p>
 */
@SuppressLint("ViewConstructor")
final class PyWindowHost extends ViewGroup {

    private final View content;
    private float scale = 1f;
    private int contentW, contentH;

    PyWindowHost(Context c, View content) {
        super(c);
        this.content = content;
        addView(content, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availW = MeasureSpec.getSize(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int availH = MeasureSpec.getSize(heightMeasureSpec);
        // まず幅の中で、中身が求める大きさを測る
        content.measure(MeasureSpec.makeMeasureSpec(availW, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int reqW = content.getMeasuredWidth(), reqH = content.getMeasuredHeight();
        contentW = Math.max(availW, reqW);
        scale = contentW > 0 ? Math.min(1f, availW / (float) contentW) : 1f;
        int fillH = hMode == MeasureSpec.UNSPECIFIED ? 0 : Math.round(availH / scale);
        contentH = Math.max(reqH, fillH);
        content.measure(MeasureSpec.makeMeasureSpec(contentW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(contentH, MeasureSpec.EXACTLY));
        int h = Math.round(contentH * scale);
        if (hMode == MeasureSpec.EXACTLY) h = Math.max(h, availH);
        setMeasuredDimension(availW, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        content.layout(0, 0, contentW, contentH);
        content.setPivotX(0);
        content.setPivotY(0);
        content.setScaleX(scale);
        content.setScaleY(scale);
    }
}
