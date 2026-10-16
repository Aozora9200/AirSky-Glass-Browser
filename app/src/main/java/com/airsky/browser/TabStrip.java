package com.airsky.browser;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * タブレット UI の上に並ぶタブ(パソコンのブラウザと同じ)。
 * <ul>
 *   <li>押すとそのタブへ切り替え、× で閉じる。中ボタン(ホイール)のクリックでも閉じられる。</li>
 *   <li>長押し(マウスならそのままドラッグ)で持ち上げて、左右に動かすと並びを入れ替えられる。</li>
 *   <li>+(新しいタブ)はいちばん右のタブのすぐ横に置く。タブで帯がいっぱいになったら右端に止める。</li>
 *   <li>タブを開く/閉じる時は、幅を伸び縮みさせて周りのタブと + を滑らかに動かす。</li>
 * </ul>
 */
final class TabStrip extends LinearLayout {

    interface Host {
        int tabCount();

        int currentTab();

        /** タブを見分ける番号(並べ替えても変わらない)。 */
        int tabId(int index);

        @Nullable
        String tabTitle(int index);

        @Nullable
        String tabUrl(int index);

        @Nullable
        Bitmap tabIcon(int index);

        void selectTab(int index);

        void closeTab(int index);

        /** ドラッグでタブを from から to へ動かした。 */
        void moveTab(int from, int to);

        void newTab(@NonNull View button);
    }

    private static final int TAB_MAX_DP = 220;
    private static final int TAB_MIN_DP = 112;
    /** これより狭い時は、選んでいないタブの × を隠す(押し間違えないように)。 */
    private static final int CLOSE_MIN_DP = 140;
    private static final int TAB_HEIGHT_DP = 36;
    private static final int ADD_WIDTH_DP = 40;
    /** 選んでいるタブの上の角と、ツールバーへつながる裾の丸み。 */
    private static final int CORNER_DP = 10;
    private static final long ANIM_MS = 240;
    private static final long SHIFT_MS = 170;
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private final Host host;
    private final HorizontalScrollView scroll;
    private final LinearLayout row;
    private final ImageButton add;
    private final float density;
    private final int touchSlop;
    private boolean refreshPosted;
    /** 幅を決めて並べ終わった(最初の並びはアニメーションしない)。 */
    private boolean laidOutOnce;
    private int tabWidth;
    @Nullable private ValueAnimator widthAnimator;

    // ---- ドラッグでの並べ替え ----
    @Nullable private TabView dragView;
    private int dragFrom;
    private int dragTarget;
    private float downRawX;
    private int downScrollX;
    private float lastRawX;
    /** マウスの左ボタンで押した(長押しを待たずにドラッグを始める)。 */
    private boolean mouseDown;
    /** 並べ替えを確定した時の、各タブの見た目の位置(並び直した後にそこから滑らせる)。 */
    @Nullable private Map<Integer, Float> flipFrom;
    private final Runnable autoScroll = this::autoScrollStep;

    @SuppressLint("ClickableViewAccessibility")
    TabStrip(@NonNull Context context, @NonNull Host host) {
        super(context);
        this.host = host;
        this.density = context.getResources().getDisplayMetrics().density;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setOrientation(HORIZONTAL);
        setGravity(Gravity.BOTTOM | Gravity.START);
        setPadding(dp(8), dp(4), dp(4), 0);
        setClipChildren(false);

        scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setFillViewport(false);
        scroll.setClipChildren(false);
        row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        row.setClipChildren(false);
        // 選んでいるタブの裾(左右にはみ出す丸み)を描く分だけ、端に余白をあける(余白にも描けるようにする)
        row.setPadding(dp(CORNER_DP), 0, dp(CORNER_DP), 0);
        row.setClipToPadding(false);
        scroll.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        addView(scroll, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));

        add = new ImageButton(context);
        add.setImageResource(R.drawable.ic_ts_add);
        add.setImageTintList(ColorStateList.valueOf(color(com.google.android.material.R.attr.colorOnSurfaceVariant)));
        add.setBackgroundResource(R.drawable.ripple_circle_small);
        add.setContentDescription(Lang.t("新しいタブ"));
        add.setTooltipText(Lang.t("新しいタブ"));
        add.setOnClickListener(host::newTab);
        LayoutParams addLp = new LayoutParams(dp(ADD_WIDTH_DP), dp(TAB_HEIGHT_DP));
        addLp.gravity = Gravity.BOTTOM;
        addView(add, addLp);
    }

    /** タブの数・題名・アイコン・選択中のタブ・並びが変わった。続けて呼ばれても 1 回にまとめる。 */
    void refresh() {
        if (refreshPosted) return;
        refreshPosted = true;
        post(() -> {
            refreshPosted = false;
            rebuild(laidOutOnce);
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == oldw) return;
        // 画面の回転などで幅が変わった: タブの幅を決め直す(ここはアニメーションしない)
        post(() -> rebuild(false));
    }

    // ------------------------------------------------------------------
    // 並べる
    // ------------------------------------------------------------------

    private void rebuild(boolean animate) {
        if (dragView != null) {
            // ドラッグ中は並びを変えない(題名や選択の表示だけ合わせる)
            bindAll();
            return;
        }
        int count = host.tabCount();

        // 今あるタブの View をタブの番号で引けるようにする(閉じかけのものは除く)
        Map<Integer, TabView> existing = new HashMap<>();
        for (int i = 0; i < row.getChildCount(); i++) {
            TabView v = (TabView) row.getChildAt(i);
            if (!v.removing) existing.put(v.tabId, v);
        }
        List<TabView> tabsInOrder = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = host.tabId(i);
            TabView v = existing.remove(id);
            if (v == null) {
                v = new TabView(getContext(), id);
                v.setLayoutParams(new LayoutParams(animate ? 0 : Math.max(1, tabWidth), dp(TAB_HEIGHT_DP)));
                if (animate) v.setAlpha(0f);
            }
            tabsInOrder.add(v);
        }
        // 無くなったタブは元の場所で幅を縮めながら消す
        for (TabView v : existing.values()) {
            v.removing = true;
            if (!animate) v.setVisibility(GONE);
        }

        List<TabView> children = new ArrayList<>(tabsInOrder);
        for (int i = 0; i < row.getChildCount(); i++) {
            TabView v = (TabView) row.getChildAt(i);
            if (v.removing) children.add(Math.min(i, children.size()), v);
        }
        boolean sameOrder = children.size() == row.getChildCount();
        for (int i = 0; sameOrder && i < children.size(); i++) {
            if (row.getChildAt(i) != children.get(i)) sameOrder = false;
        }
        if (!sameOrder) {
            row.removeAllViews();
            for (TabView v : children) row.addView(v);
        }

        tabWidth = tabWidth(count);
        bindAll();
        if (flipFrom != null) {
            playReorder(flipFrom);
            flipFrom = null;
        }
        animateWidths(animate);
        laidOutOnce = getWidth() > 0;

        int current = host.currentTab();
        if (current >= 0 && current < count) {
            // 幅のアニメーションが終わった後の位置で、選んでいるタブが見えるようにスクロールする
            int left = row.getPaddingLeft() + current * tabWidth;
            int right = left + tabWidth;
            scroll.post(() -> {
                int x = scroll.getScrollX();
                int w = scroll.getWidth();
                if (w <= 0) return;
                if (left - dp(CORNER_DP) < x) scroll.smoothScrollTo(left - dp(CORNER_DP), 0);
                else if (right + dp(CORNER_DP) > x + w) scroll.smoothScrollTo(right + dp(CORNER_DP) - w, 0);
            });
        }
    }

    /** 題名・アイコン・選択の表示を今のタブに合わせる。 */
    private void bindAll() {
        int count = host.tabCount();
        int current = host.currentTab();
        Map<Integer, Integer> index = new HashMap<>();
        for (int i = 0; i < count; i++) index.put(host.tabId(i), i);
        int n = row.getChildCount();
        for (int c = 0; c < n; c++) {
            TabView v = (TabView) row.getChildAt(c);
            Integer i = index.get(v.tabId);
            if (i == null || v.removing) continue;
            // 区切り線は、選んでいるタブの両隣と右端には出さない
            boolean nextSelected = i + 1 == current;
            boolean divider = i != current && !nextSelected && i < count - 1 && dragView == null;
            bind(v, i, i == current, divider);
        }
    }

    /** タブが少ない時は広く、多い時は最小幅まで縮める(それ以上はスクロール)。 */
    private int tabWidth(int count) {
        int avail = availableWidth() - row.getPaddingLeft() - row.getPaddingRight();
        int w = count > 0 ? avail / count : dp(TAB_MAX_DP);
        return Math.max(dp(TAB_MIN_DP), Math.min(dp(TAB_MAX_DP), w));
    }

    /** タブを並べられる幅(+ の分を除く)。 */
    private int availableWidth() {
        int w = getWidth();
        if (w <= 0) w = getResources().getDisplayMetrics().widthPixels;
        return Math.max(0, w - getPaddingLeft() - getPaddingRight() - dp(ADD_WIDTH_DP));
    }

    /**
     * タブの幅を目標まで伸び縮みさせる。並び全体の幅に合わせてスクロール枠の幅も変えるので、
     * + は右端のタブにくっついて動き、帯がいっぱいになったら右端で止まる。
     */
    private void animateWidths(boolean animate) {
        if (widthAnimator != null) widthAnimator.cancel();
        widthAnimator = null;
        int n = row.getChildCount();
        TabView[] views = new TabView[n];
        int[] from = new int[n];
        int[] to = new int[n];
        float[] alphaFrom = new float[n];
        boolean changed = false;
        for (int i = 0; i < n; i++) {
            TabView v = (TabView) row.getChildAt(i);
            views[i] = v;
            from[i] = v.getLayoutParams().width;
            to[i] = v.removing ? 0 : tabWidth;
            alphaFrom[i] = v.getAlpha();
            if (from[i] != to[i] || alphaFrom[i] != (v.removing ? 0f : 1f)) changed = true;
        }
        if (!animate || !changed) {
            for (int i = 0; i < n; i++) {
                setTabWidth(views[i], to[i]);
                views[i].setAlpha(views[i].removing ? 0f : 1f);
            }
            removeClosedTabs();
            updateScrollWidth();
            return;
        }
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(ANIM_MS);
        va.setInterpolator(EASE);
        va.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            for (int i = 0; i < n; i++) {
                TabView v = views[i];
                setTabWidth(v, Math.round(from[i] + (to[i] - from[i]) * f));
                float target = v.removing ? 0f : 1f;
                // 開いたタブは少し遅れて浮かび上がり、閉じたタブは先に薄くなる
                float af = v.removing ? Math.min(1f, f * 1.6f) : Math.max(0f, (f - 0.25f) / 0.75f);
                v.setAlpha(alphaFrom[i] + (target - alphaFrom[i]) * af);
            }
            updateScrollWidth();
        });
        va.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (widthAnimator == animation) widthAnimator = null;
                if (canceled) return;
                removeClosedTabs();
                updateScrollWidth();
            }
        });
        widthAnimator = va;
        va.start();
    }

    private void setTabWidth(@NonNull TabView v, int width) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp.width == width) return;
        lp.width = width;
        v.setLayoutParams(lp);
    }

    private void removeClosedTabs() {
        for (int i = row.getChildCount() - 1; i >= 0; i--) {
            TabView v = (TabView) row.getChildAt(i);
            if (v.removing) row.removeViewAt(i);
        }
    }

    /** スクロール枠をタブの並びの幅にする(入りきらない時は + の手前まで)。 */
    private void updateScrollWidth() {
        int content = row.getPaddingLeft() + row.getPaddingRight();
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v.getVisibility() != GONE) content += Math.max(0, v.getLayoutParams().width);
        }
        int w = Math.min(content, availableWidth());
        ViewGroup.LayoutParams lp = scroll.getLayoutParams();
        if (lp.width != w) {
            lp.width = w;
            scroll.setLayoutParams(lp);
        }
    }

    // ------------------------------------------------------------------
    // タブ 1 枚
    // ------------------------------------------------------------------

    private void bind(@NonNull TabView tab, int index, boolean selected, boolean divider) {
        String t = host.tabTitle(index);
        String url = host.tabUrl(index);
        if (t == null || t.isEmpty()) t = url != null && !url.isEmpty() ? url : Lang.t("新しいタブ");
        tab.title.setText(t);
        tab.setContentDescription(t);
        tab.setTooltipText(t);
        Bitmap bmp = host.tabIcon(index);
        int onSurface = color(com.google.android.material.R.attr.colorOnSurface);
        int onVariant = color(com.google.android.material.R.attr.colorOnSurfaceVariant);
        if (bmp != null) {
            tab.icon.setImageBitmap(bmp);
            tab.icon.setImageTintList(null);
        } else {
            tab.icon.setImageResource(R.drawable.ic_ts_globe);
            tab.icon.setImageTintList(ColorStateList.valueOf(onVariant));
        }
        tab.title.setTextColor(selected ? onSurface : onVariant);
        tab.title.setTypeface(selected ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
        tab.close.setImageTintList(ColorStateList.valueOf(onVariant));
        // 狭いタブでは、選んでいるタブにだけ × を出す
        boolean showClose = selected || tabWidth >= dp(CLOSE_MIN_DP);
        tab.close.setVisibility(showClose ? VISIBLE : GONE);
        tab.setSelected(selected);
        if (tab.selectedTab != selected || tab.divider != divider) {
            tab.selectedTab = selected;
            tab.divider = divider;
            tab.invalidate();
        }
        // 選んでいるタブは手前に(裾の丸みが隣のタブの上に重なるように)
        tab.setZ(selected ? 1f : 0f);
    }

    private int indexOfId(int id) {
        int n = host.tabCount();
        for (int i = 0; i < n; i++) if (host.tabId(i) == id) return i;
        return -1;
    }

    /** タブ 1 枚。選んでいる時はツールバーと同じ色の、上の角が丸く裾が広がった形を描く。 */
    @SuppressLint("ViewConstructor")
    private final class TabView extends LinearLayout {
        final int tabId;
        final ImageView icon;
        final TextView title;
        final ImageButton close;
        boolean removing;
        boolean selectedTab;
        boolean divider;
        private final Path path = new Path();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

        @SuppressLint("ClickableViewAccessibility")
        TabView(@NonNull Context c, int tabId) {
            super(c);
            this.tabId = tabId;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(12), 0, dp(4), 0);
            setWillNotDraw(false);
            setClipChildren(false);
            setClipToPadding(false);
            fill.setColor(color(com.google.android.material.R.attr.colorSurfaceContainerHighest));
            line.setColor(color(com.google.android.material.R.attr.colorOutlineVariant));
            setForeground(hoverBackground());

            icon = new ImageView(c);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(icon, new LayoutParams(dp(16), dp(16)));

            title = new TextView(c);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            LayoutParams tlp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.setMarginStart(dp(8));
            addView(title, tlp);

            close = new ImageButton(c);
            close.setImageResource(R.drawable.ic_ts_close);
            close.setScaleType(ImageView.ScaleType.FIT_CENTER);
            close.setPadding(dp(6), dp(6), dp(6), dp(6));
            close.setBackgroundResource(R.drawable.ripple_circle_small);
            close.setContentDescription(Lang.t("タブを閉じる"));
            close.setTooltipText(Lang.t("タブを閉じる"));
            addView(close, new LayoutParams(dp(28), dp(28)));

            setOnClickListener(v -> {
                int i = indexOfId(tabId);
                if (i >= 0) host.selectTab(i);
            });
            close.setOnClickListener(v -> {
                int i = indexOfId(tabId);
                if (i >= 0) host.closeTab(i);
            });
            setOnLongClickListener(v -> {
                if (removing || dragView != null) return false;
                beginTabDrag(this, true);
                return true;
            });
            setOnTouchListener(TabStrip.this::onTabTouch);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (selectedTab && w > 0) {
                float r = dp(CORNER_DP);
                float rr = Math.min(r, w / 2f);
                path.reset();
                // 左の裾 → 左上の角 → 右上の角 → 右の裾(ツールバーへなめらかにつながる)
                path.moveTo(-r, h);
                path.quadTo(0, h, 0, h - r);
                path.lineTo(0, rr);
                path.quadTo(0, 0, rr, 0);
                path.lineTo(w - rr, 0);
                path.quadTo(w, 0, w, rr);
                path.lineTo(w, h - r);
                path.quadTo(w, h, w + r, h);
                path.close();
                canvas.drawPath(path, fill);
            } else if (divider && w > 0) {
                float lw = Math.max(1f, density);
                canvas.drawRect(w - lw, h * 0.3f, w, h * 0.7f, line);
            }
            super.onDraw(canvas);
        }

        /** マウスを乗せた時・押した時の丸い背景(タブの形より少し内側)。 */
        private android.graphics.drawable.Drawable hoverBackground() {
            GradientDrawable mask = new GradientDrawable();
            mask.setCornerRadius(dp(8));
            mask.setColor(0xFFFFFFFF);
            InsetDrawable inset = new InsetDrawable(mask, dp(3), dp(4), dp(3), dp(4));
            TypedValue tv = new TypedValue();
            getContext().getTheme().resolveAttribute(android.R.attr.colorControlHighlight, tv, true);
            int highlight = tv.resourceId != 0 ? getContext().getColor(tv.resourceId) : tv.data;
            return new RippleDrawable(ColorStateList.valueOf(highlight), null, inset);
        }
    }

    // ------------------------------------------------------------------
    // ドラッグで並べ替える
    // ------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private boolean onTabTouch(View view, MotionEvent e) {
        TabView v = (TabView) view;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // マウスの中ボタンで閉じる(パソコンのブラウザと同じ)
                if ((e.getButtonState() & MotionEvent.BUTTON_TERTIARY) != 0) {
                    int i = indexOfId(v.tabId);
                    if (i >= 0) host.closeTab(i);
                    return true;
                }
                downRawX = lastRawX = e.getRawX();
                downScrollX = scroll.getScrollX();
                mouseDown = e.isFromSource(InputDevice.SOURCE_MOUSE)
                        && (e.getButtonState() & MotionEvent.BUTTON_PRIMARY) != 0;
                // マウスで押したまま動かしたら、帯のスクロールではなくタブを動かす(スクロールはホイールで)
                if (mouseDown) scroll.requestDisallowInterceptTouchEvent(true);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (dragView == v) {
                    onDragMove(e.getRawX());
                    return true;
                }
                if (mouseDown && dragView == null && !v.removing && Math.abs(e.getRawX() - downRawX) > touchSlop) {
                    v.cancelLongPress();
                    beginTabDrag(v, false);
                    onDragMove(e.getRawX());
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mouseDown = false;
                if (dragView == v) {
                    // 離した所で並びを確定する(クリックとしては扱わない)
                    endDrag();
                    return true;
                }
                return false;
        }
        return false;
    }

    private void beginTabDrag(@NonNull TabView v, boolean haptic) {
        // 開閉のアニメーションの途中なら終わらせてから(全部のタブが同じ幅になっている前提で位置を計算する)
        if (widthAnimator != null) widthAnimator.end();
        int index = row.indexOfChild(v);
        if (index < 0 || v.removing) return;
        dragView = v;
        dragFrom = dragTarget = index;
        scroll.requestDisallowInterceptTouchEvent(true);
        if (haptic) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        // 持ち上げる
        v.animate().scaleX(1.04f).scaleY(1.04f).translationZ(dp(6)).setDuration(150).setInterpolator(EASE).start();
        int i = indexOfId(v.tabId);
        if (i >= 0 && i != host.currentTab()) host.selectTab(i);
        bindAll();
    }

    private void onDragMove(float rawX) {
        TabView v = dragView;
        if (v == null) return;
        lastRawX = rawX;
        int n = row.getChildCount();
        int w = Math.max(1, tabWidth);
        float dx = rawX - downRawX + (scroll.getScrollX() - downScrollX);
        // 並びの外へは出さない
        float min = row.getPaddingLeft() - v.getLeft();
        float max = row.getPaddingLeft() + (n - 1) * w - v.getLeft();
        dx = Math.max(min, Math.min(max, dx));
        v.setTranslationX(dx);
        int target = Math.round((v.getLeft() + dx - row.getPaddingLeft()) / (float) w);
        target = Math.max(0, Math.min(n - 1, target));
        if (target != dragTarget) {
            dragTarget = target;
            shiftNeighbors(w);
        }
        // 端まで持っていったら帯をスクロールする
        removeCallbacks(autoScroll);
        if (autoScrollSpeed() != 0) postOnAnimation(autoScroll);
    }

    /** ドラッグしているタブの行き先をあけるように、間のタブを 1 枚分ずらす。 */
    private void shiftNeighbors(int w) {
        for (int i = 0; i < row.getChildCount(); i++) {
            if (i == dragFrom) continue;
            float shift = 0;
            if (dragFrom < dragTarget && i > dragFrom && i <= dragTarget) shift = -w;
            else if (dragTarget < dragFrom && i >= dragTarget && i < dragFrom) shift = w;
            View c = row.getChildAt(i);
            if (c.getTranslationX() != shift) {
                c.animate().translationX(shift).setDuration(SHIFT_MS).setInterpolator(EASE).start();
            }
        }
    }

    private int autoScrollSpeed() {
        if (dragView == null || row.getWidth() <= scroll.getWidth()) return 0;
        int[] loc = new int[2];
        scroll.getLocationOnScreen(loc);
        float x = lastRawX - loc[0];
        int edge = dp(40);
        if (x < edge && scroll.canScrollHorizontally(-1)) return -Math.round((edge - x) / 3f) - 2;
        if (x > scroll.getWidth() - edge && scroll.canScrollHorizontally(1)) {
            return Math.round((x - (scroll.getWidth() - edge)) / 3f) + 2;
        }
        return 0;
    }

    private void autoScrollStep() {
        int speed = autoScrollSpeed();
        if (speed == 0) return;
        scroll.scrollBy(speed, 0);
        onDragMove(lastRawX);
    }

    private void endDrag() {
        TabView v = dragView;
        if (v == null) return;
        removeCallbacks(autoScroll);
        int from = dragFrom;
        int to = dragTarget;
        dragView = null;
        v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(180).setInterpolator(EASE).start();
        if (from == to) {
            for (int i = 0; i < row.getChildCount(); i++) {
                row.getChildAt(i).animate().translationX(0f).setDuration(SHIFT_MS).setInterpolator(EASE).start();
            }
            bindAll();
            return;
        }
        // 今見えている位置を覚えておき、並び直した後でそこから滑らせる
        Map<Integer, Float> visual = new HashMap<>();
        for (int i = 0; i < row.getChildCount(); i++) {
            TabView c = (TabView) row.getChildAt(i);
            visual.put(c.tabId, c.getLeft() + c.getTranslationX());
        }
        flipFrom = visual;
        host.moveTab(from, to);
        refresh();
    }

    /** 並び直したタブを、前に見えていた位置から新しい位置へ滑らせる。 */
    private void playReorder(@NonNull Map<Integer, Float> visual) {
        int left = row.getPaddingLeft();
        for (int i = 0; i < row.getChildCount(); i++) {
            TabView c = (TabView) row.getChildAt(i);
            Float old = visual.get(c.tabId);
            int width = Math.max(0, c.getLayoutParams().width);
            if (old != null) {
                c.setTranslationX(old - left);
                c.animate().translationX(0f).setDuration(SHIFT_MS).setInterpolator(EASE).start();
            }
            left += width;
        }
    }

    private int color(int attr) {
        return MaterialColors.getColor(this, attr, android.graphics.Color.GRAY);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
