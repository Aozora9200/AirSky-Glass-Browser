package com.airsky.browser;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;

/**
 * ボトムシートの中身をスクロールできるように包む。
 *
 * <p>横画面など、画面の高さより中身が長い時に下が見切れないようにする。
 * NestedScrollView なので、いちばん上まで戻した後に下へ引くとシートごと閉じられる。</p>
 */
final class SheetScroll {

    private SheetScroll() {}

    @NonNull
    static View wrap(@NonNull View content) {
        NestedScrollView scroll = new NestedScrollView(content.getContext());
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        ViewGroup.LayoutParams lp = content.getLayoutParams();
        int width = lp != null ? lp.width : ViewGroup.LayoutParams.MATCH_PARENT;
        scroll.addView(content, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }
}
