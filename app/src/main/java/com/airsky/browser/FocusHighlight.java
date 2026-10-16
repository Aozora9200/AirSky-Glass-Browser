package com.airsky.browser;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.mozilla.geckoview.GeckoView;

/**
 * コントローラーやキーボードで操作している時に、選んでいる項目をはっきり見せる枠。
 *
 * <p>画面(ウィンドウ)の一番上に、フォーカスのある部品を囲む色付きの枠を重ねる。指で触れている間
 * (タッチモード)は出さないので、普段の見た目は変わらない。各画面の部品はそのままで、
 * どの画面でも同じ見え方になる。</p>
 */
final class FocusHighlight implements ViewTreeObserver.OnGlobalFocusChangeListener,
        ViewTreeObserver.OnPreDrawListener, ViewTreeObserver.OnTouchModeChangeListener {

    /** ウィンドウに枠を付ける(付いていれば何もしない)。 */
    static void attach(@Nullable Window window) {
        if (window == null) return;
        View decor = window.getDecorView();
        if (decor.getTag(R.id.focus_highlight) != null) return;
        FocusHighlight h = new FocusHighlight(decor);
        decor.setTag(R.id.focus_highlight, h);
        ViewTreeObserver vto = decor.getViewTreeObserver();
        vto.addOnGlobalFocusChangeListener(h);
        vto.addOnPreDrawListener(h);
        vto.addOnTouchModeChangeListener(h);
        h.focused = decor.findFocus();
    }

    private final View decor;
    private final GradientDrawable ring;
    private final int pad;
    @Nullable private View focused;
    private boolean shown;
    private final Rect bounds = new Rect();
    private final int[] loc = new int[2];
    private final int[] origin = new int[2];

    private FocusHighlight(@NonNull View decor) {
        this.decor = decor;
        Context c = decor.getContext();
        float density = c.getResources().getDisplayMetrics().density;
        int primary = themeColor(c, androidx.appcompat.R.attr.colorPrimary, 0xFF1A73E8);
        pad = Math.round(3 * density);
        ring = new GradientDrawable();
        ring.setCornerRadius(12 * density);
        ring.setColor(ColorUtils.setAlphaComponent(primary, 0x38));
        ring.setStroke(Math.round(3 * density), primary);
    }

    private static int themeColor(@NonNull Context c, int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (!c.getTheme().resolveAttribute(attr, tv, true)) return fallback;
        if (tv.resourceId != 0) {
            try {
                return androidx.core.content.ContextCompat.getColor(c, tv.resourceId);
            } catch (Exception e) {
                return fallback;
            }
        }
        return tv.data;
    }

    @Override
    public void onGlobalFocusChanged(@Nullable View oldFocus, @Nullable View newFocus) {
        focused = newFocus;
        update();
    }

    @Override
    public boolean onPreDraw() {
        // スクロールやアニメーションで部品が動いても枠が付いていくよう、描くたびに位置を合わせる
        update();
        return true;
    }

    @Override
    public void onTouchModeChanged(boolean isInTouchMode) {
        update();
    }

    private void update() {
        View v = focused;
        if (v == null || decor.isInTouchMode() || !v.isAttachedToWindow() || !v.isShown()
                || v.getWidth() <= 0 || v.getHeight() <= 0 || skip(v)) {
            hide();
            return;
        }
        v.getLocationInWindow(loc);
        decor.getLocationInWindow(origin);
        int l = loc[0] - origin[0] - pad;
        int t = loc[1] - origin[1] - pad;
        int r = l + v.getWidth() + pad * 2;
        int b = t + v.getHeight() + pad * 2;
        if (!shown) {
            decor.getOverlay().add(ring);
            shown = true;
        }
        if (bounds.left != l || bounds.top != t || bounds.right != r || bounds.bottom != b) {
            bounds.set(l, t, r, b);
            ring.setBounds(bounds);
            decor.invalidate();
        }
    }

    private void hide() {
        if (!shown) return;
        shown = false;
        decor.getOverlay().remove(ring);
        bounds.setEmpty();
    }

    /** 枠を付けない部品(ページ全体など、囲んでも分かりにくい大きな物)。 */
    private boolean skip(@NonNull View v) {
        if (v instanceof GeckoView) return true;
        if (v.getWidth() <= 2 || v.getHeight() <= 2) return true; // 見えない受け口など
        int w = decor.getWidth();
        int h = decor.getHeight();
        return w > 0 && h > 0 && v.getWidth() >= w * 0.95f && v.getHeight() >= h * 0.6f;
    }
}
