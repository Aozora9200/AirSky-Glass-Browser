package com.airsky.browser;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * ページを中に表示するシート(「ページをプレビュー」「このページについて」)用の BottomSheetDialog。
 *
 * シート全体をドラッグできるようにするとページのスクロールとぶつかるので、
 * 触り始めがページ(content)より上 ― つまみ・見出しの部分 ― の時だけドラッグで動かせるようにする。
 * 下へ引けば、ほかのシートと同じように閉じられる。
 */
class HeaderDragSheetDialog extends BottomSheetDialog {

    @Nullable private View content;
    private final int[] location = new int[2];

    HeaderDragSheetDialog(@NonNull Context context) {
        super(context);
    }

    /** このビューより上で触り始めた時だけ、シートをドラッグできる。 */
    void setDragBoundary(@NonNull View content) {
        this.content = content;
        getBehavior().setDraggable(false);
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        // BottomSheetBehavior は ACTION_DOWN の時点の draggable を見るので、その前に切り替える
        if (content != null && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            content.getLocationOnScreen(location);
            getBehavior().setDraggable(ev.getRawY() < location[1]);
        }
        return super.dispatchTouchEvent(ev);
    }
}
