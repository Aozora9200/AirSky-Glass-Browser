package com.airsky.browser;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

/**
 * ツール画面の共通の見た目(Pixel の設定アプリ風)。
 *
 * <p>レイアウトに include_tool_header を入れ、本文を id が tool_content の入れ物に置いておくと、
 * 戻るボタン・大きな見出し・スクロールで小さな見出しへ切り替わる動き・開いた時にカードが順に
 * 浮かび上がる動きを付ける。</p>
 */
final class ToolChrome {

    private ToolChrome() {}

    static void setup(@NonNull Activity activity, @NonNull CharSequence title) {
        View back = activity.findViewById(R.id.tool_back);
        if (back != null) back.setOnClickListener(v -> activity.onBackPressed());
        TextView big = activity.findViewById(R.id.tool_title);
        TextView small = activity.findViewById(R.id.tool_small_title);
        if (big != null) big.setText(title);
        if (small != null) small.setText(title);

        // 本文を上へ送ったら、大きな見出しに代わって上の段に小さな見出しを出す
        View scroll = activity.findViewById(R.id.tool_scroll);
        View content = activity.findViewById(R.id.tool_content);
        if (scroll instanceof NestedScrollView && big != null && small != null && content instanceof ViewGroup) {
            // 大きな見出しは本文と一緒に流れるよう、本文の先頭へ移す
            ((ViewGroup) big.getParent()).removeView(big);
            ((ViewGroup) content).addView(big, 0, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            big.setPadding(big.getPaddingLeft() - content.getPaddingLeft(), big.getPaddingTop(),
                    big.getPaddingRight() - content.getPaddingRight(), big.getPaddingBottom());
            ((NestedScrollView) scroll).setOnScrollChangeListener(
                    (NestedScrollView.OnScrollChangeListener) (v, x, y, ox, oy) -> {
                        float p = Math.min(1f, y / (float) Math.max(1, big.getHeight()));
                        small.setAlpha(p);
                    });
        }
        playEnter(content);
        if (big != null && big.getParent() != content) {
            big.setAlpha(0f);
            big.setTranslationY(16 * activity.getResources().getDisplayMetrics().density);
            big.animate().alpha(1f).translationY(0f).setDuration(420)
                    .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
        }
    }

    /** 本文のカードを上から順に、下から浮かび上がらせる */
    static void playEnter(@Nullable View content) {
        if (!(content instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) content;
        float density = content.getResources().getDisplayMetrics().density;
        PathInterpolator emphasized = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
        int shown = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            child.setAlpha(0f);
            child.setTranslationY(28 * density);
            child.animate().alpha(1f).translationY(0f).setStartDelay(60L + 45L * shown++)
                    .setDuration(460).setInterpolator(emphasized).start();
        }
    }
}
