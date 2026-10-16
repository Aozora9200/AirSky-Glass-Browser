package com.airsky.browser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

/**
 * 初回セットアップのページ。Pixel のセットアップと同じく、指ではめくらずボタンだけで進む
 * (利用規約に同意するまで先へ進めないようにするため)。
 * ページが切り替わる時の動きは {@link SetupActivity} が付ける。
 */
public class SetupViewPager extends ViewPager {

    public SetupViewPager(@NonNull Context context) {
        this(context, null);
    }

    public SetupViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return false;
    }
}
