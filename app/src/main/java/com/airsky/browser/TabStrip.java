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
 *   <li>長押し(マウスならそのままドラッグ)で持ち上げて、左右に動かすと並びを入れ替えられる。
 *       グループの中へ落とすとそのグループに入り、グループの外へ出すとグループから外れる。</li>
 *   <li>グループは先頭に名前のチップを置き、グループのタブの下に色の線を引く。チップを押すとグループのメニュー、
 *       長押しで持ち上げるとグループごと並びを入れ替えられる。</li>
 *   <li>長押し(動かさずに離す)・右クリックで、タブのメニュー(グループに追加など)を出す。</li>
 *   <li>+(新しいタブ)はいちばん右のタブのすぐ横に置く。タブで帯がいっぱいになったら右端に止める。
 *       右端のボタンでタブとグループの一覧を開く。</li>
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

        void newTab(@NonNull View button);

        /** タブのグループ。グループに入っていなければ null。 */
        @Nullable
        TabGroup tabGroup(int index);

        /** グループのタブの数(名前が無い時の「N 個のタブ」に使う)。 */
        int groupTabCount(int groupId);

        /** 並べ替えた(order は元の index を新しい並びに並べたもの)。グループごと動かした時。 */
        void reorderTabs(@NonNull List<Integer> order);

        /**
         * タブをドラッグして落とした。並べ替えたうえで、タブ(tabId)をグループ groupId に入れる
         * (-1 ならグループから外す)。
         */
        void dropTab(@NonNull List<Integer> order, int tabId, int groupId);

        /** タブのメニュー(長押し・右クリック)。 */
        void showTabContextMenu(@NonNull View anchor, int index);

        /** グループのメニュー(チップを押した時)。 */
        void showGroupMenu(@NonNull View anchor, int groupId);

        /** タブとグループの一覧を開く。 */
        void showAllTabs(@NonNull View button);
    }

    private static final int TAB_MAX_DP = 220;
    private static final int TAB_MIN_DP = 112;
    /** これより狭い時は、選んでいないタブの × を隠す(押し間違えないように)。 */
    private static final int CLOSE_MIN_DP = 140;
    private static final int TAB_HEIGHT_DP = 36;
    private static final int ADD_WIDTH_DP = 40;
    /** 右端の「タブとグループの一覧」ボタンの幅。 */
    private static final int ALL_WIDTH_DP = 44;
    /** グループのチップの最大幅。 */
    private static final int CHIP_MAX_DP = 120;
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

    // ---- ドラッグ(タブ 1 枚、またはグループのチップとそのタブ) ----
    /** 持ち上げている View(タブならそのタブ、グループならチップとそのタブ)。空ならドラッグしていない。 */
    private final List<View> dragBlock = new ArrayList<>();
    /** 押した View(長押し・マウスのドラッグの起点)。 */
    @Nullable private View dragHandle;
    /** グループごと動かしている。 */
    private boolean dragGroup;
    /** ドラッグを始めた時の、子それぞれの左端(並べ直しの計算に使う)。 */
    private final Map<View, Integer> baseLeft = new HashMap<>();
    /** 周りの子をずらしている量(同じ量なら動かし直さない)。 */
    private final Map<View, Float> shiftTarget = new HashMap<>();
    /** 落とした時の並び(タブの ID)と、入るグループ。 */
    private final List<Integer> dropOrder = new ArrayList<>();
    private int dropGroupId = -1;
    /** ドラッグ中のタブが、持ち上げる前に入っていたグループ。 */
    private int dragOriginalGroup = -1;
    private float downRawX;
    private int downScrollX;
    private float lastRawX;
    /** マウスの左ボタンで押した(長押しを待たずにドラッグを始める)。 */
    private boolean mouseDown;
    /** 長押しで持ち上げた(動かさずに離したらメニューを出す)。 */
    private boolean dragByLongPress;
    /** 持ち上げてから指を動かした。 */
    private boolean dragMoved;
    /** 並べ替えを確定した時の、各子の見た目の位置(並び直した後にそこから滑らせる)。 */
    @Nullable private Map<View, Float> flipFrom;
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
        // スクロール枠を自分の幅で切る(切らないと、タブが多い時にはみ出したタブが + や一覧のボタンの上に描かれる)
        setClipChildren(true);

        scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setFillViewport(false);
        scroll.setClipChildren(true);
        // 続きがある端は薄くして、スクロールできることが分かるようにする
        scroll.setHorizontalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(28));
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

        // 右端: タブとグループの一覧(タブボタンの代わり)
        addView(new View(context), new LayoutParams(0, 1, 1f));
        ImageButton all = new ImageButton(context);
        all.setImageResource(R.drawable.ic_tg_groups);
        all.setImageTintList(ColorStateList.valueOf(color(com.google.android.material.R.attr.colorOnSurfaceVariant)));
        all.setBackgroundResource(R.drawable.ripple_circle_small);
        all.setContentDescription(Lang.t("タブとグループの一覧"));
        all.setTooltipText(Lang.t("タブとグループの一覧"));
        all.setOnClickListener(host::showAllTabs);
        LayoutParams allLp = new LayoutParams(dp(ALL_WIDTH_DP), dp(TAB_HEIGHT_DP));
        allLp.gravity = Gravity.BOTTOM;
        addView(all, allLp);
    }

    /** タブの数・題名・アイコン・選択中のタブ・並び・グループが変わった。続けて呼ばれても 1 回にまとめる。 */
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
        if (!dragBlock.isEmpty()) {
            // ドラッグ中は並びを変えない(題名や選択の表示だけ合わせる)
            bindAll();
            return;
        }
        int count = host.tabCount();

        // 今あるタブ・チップの View を引けるようにする(閉じかけのタブは除く)
        Map<Integer, TabView> existing = new HashMap<>();
        Map<Integer, GroupChip> chips = new HashMap<>();
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            if (c instanceof TabView && !((TabView) c).removing) existing.put(((TabView) c).tabId, (TabView) c);
            else if (c instanceof GroupChip) chips.put(((GroupChip) c).groupId, (GroupChip) c);
        }
        // 並び: グループが始まる所にチップ、続けてタブ
        List<View> ordered = new ArrayList<>(count + 4);
        int prevGroup = -1;
        for (int i = 0; i < count; i++) {
            TabGroup g = host.tabGroup(i);
            int gid = g != null ? g.id : -1;
            if (gid >= 0 && gid != prevGroup) {
                GroupChip chip = chips.remove(gid);
                if (chip == null) chip = new GroupChip(getContext(), gid);
                chip.setAlpha(1f);
                bindChip(chip, g);
                ordered.add(chip);
            }
            prevGroup = gid;
            int id = host.tabId(i);
            TabView v = existing.remove(id);
            if (v == null) {
                v = new TabView(getContext(), id);
                v.setLayoutParams(new LayoutParams(animate ? 0 : Math.max(1, tabWidth), dp(TAB_HEIGHT_DP)));
                if (animate) v.setAlpha(0f);
            }
            ordered.add(v);
        }
        // 無くなったタブは元の場所で幅を縮めながら消す
        for (TabView v : existing.values()) {
            v.removing = true;
            if (!animate) v.setVisibility(GONE);
        }

        List<View> children = new ArrayList<>(ordered);
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            if (c instanceof TabView && ((TabView) c).removing) children.add(Math.min(i, children.size()), c);
        }
        boolean sameOrder = children.size() == row.getChildCount();
        for (int i = 0; sameOrder && i < children.size(); i++) {
            if (row.getChildAt(i) != children.get(i)) sameOrder = false;
        }
        if (!sameOrder) {
            row.removeAllViews();
            for (View v : children) row.addView(v);
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
            int currentId = host.tabId(current);
            int left = row.getPaddingLeft();
            for (int i = 0; i < row.getChildCount(); i++) {
                View c = row.getChildAt(i);
                if (c instanceof TabView && ((TabView) c).tabId == currentId) break;
                left += targetWidth(c);
            }
            int l = left;
            int right = left + tabWidth;
            scroll.post(() -> {
                int x = scroll.getScrollX();
                int w = scroll.getWidth();
                if (w <= 0) return;
                if (l - dp(CORNER_DP) < x) scroll.smoothScrollTo(l - dp(CORNER_DP), 0);
                else if (right + dp(CORNER_DP) > x + w) scroll.smoothScrollTo(right + dp(CORNER_DP) - w, 0);
            });
        }
    }

    /** 題名・アイコン・選択・グループの表示を今のタブに合わせる。 */
    private void bindAll() {
        int count = host.tabCount();
        int current = host.currentTab();
        Map<Integer, Integer> index = new HashMap<>();
        for (int i = 0; i < count; i++) index.put(host.tabId(i), i);
        boolean dragging = !dragBlock.isEmpty();
        for (int c = 0; c < row.getChildCount(); c++) {
            View child = row.getChildAt(c);
            if (!(child instanceof TabView)) continue;
            TabView v = (TabView) child;
            Integer i = index.get(v.tabId);
            if (i == null || v.removing) continue;
            // 区切り線は、選んでいるタブの両隣・右端・グループの境目には出さない
            View next = c + 1 < row.getChildCount() ? row.getChildAt(c + 1) : null;
            boolean nextSelected = i + 1 == current;
            boolean divider = i != current && !nextSelected && i < count - 1 && !dragging
                    && !(next instanceof GroupChip);
            bind(v, i, i == current, divider);
        }
    }

    /** タブが少ない時は広く、多い時は最小幅まで縮める(それ以上はスクロール)。チップの幅は先に引く。 */
    private int tabWidth(int count) {
        int avail = availableWidth() - row.getPaddingLeft() - row.getPaddingRight();
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            if (c instanceof GroupChip) avail -= targetWidth(c);
        }
        int w = count > 0 ? avail / count : dp(TAB_MAX_DP);
        return Math.max(dp(TAB_MIN_DP), Math.min(dp(TAB_MAX_DP), w));
    }

    /** タブを並べられる幅(+ と一覧のボタンの分を除く)。 */
    private int availableWidth() {
        int w = getWidth();
        if (w <= 0) w = getResources().getDisplayMetrics().widthPixels;
        return Math.max(0, w - getPaddingLeft() - getPaddingRight() - dp(ADD_WIDTH_DP) - dp(ALL_WIDTH_DP));
    }

    /** アニメーションが終わった時の幅(チップは文字に合わせた幅、閉じたタブは 0)。 */
    private int targetWidth(@NonNull View c) {
        if (c instanceof GroupChip) return Math.max(0, c.getLayoutParams().width);
        TabView t = (TabView) c;
        if (t.removing || t.getVisibility() == GONE) return 0;
        return tabWidth;
    }

    /**
     * タブの幅を目標まで伸び縮みさせる。並び全体の幅に合わせてスクロール枠の幅も変えるので、
     * + は右端のタブにくっついて動き、帯がいっぱいになったら右端で止まる。
     */
    private void animateWidths(boolean animate) {
        if (widthAnimator != null) widthAnimator.cancel();
        widthAnimator = null;
        List<TabView> views = new ArrayList<>();
        for (int i = 0; i < row.getChildCount(); i++) {
            if (row.getChildAt(i) instanceof TabView) views.add((TabView) row.getChildAt(i));
        }
        int n = views.size();
        int[] from = new int[n];
        int[] to = new int[n];
        float[] alphaFrom = new float[n];
        boolean changed = false;
        for (int i = 0; i < n; i++) {
            TabView v = views.get(i);
            from[i] = v.getLayoutParams().width;
            to[i] = v.removing ? 0 : tabWidth;
            alphaFrom[i] = v.getAlpha();
            if (from[i] != to[i] || alphaFrom[i] != (v.removing ? 0f : 1f)) changed = true;
        }
        if (!animate || !changed) {
            for (int i = 0; i < n; i++) {
                setTabWidth(views.get(i), to[i]);
                views.get(i).setAlpha(views.get(i).removing ? 0f : 1f);
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
                TabView v = views.get(i);
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
            View v = row.getChildAt(i);
            if (v instanceof TabView && ((TabView) v).removing) row.removeViewAt(i);
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
    // タブ 1 枚・グループのチップ
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
        // グループのタブは下にグループの色の線(ドラッグ中のタブは入る先のグループの色にする)
        if (!(dragBlock.contains(tab) && !dragGroup)) {
            TabGroup g = host.tabGroup(index);
            tab.setGroupColor(g != null ? TabGroup.colorValue(getContext(), g.color, false) : 0);
        }
    }

    /** チップの名前と色。幅は文字に合わせて決める(長い名前は省略)。 */
    private void bindChip(@NonNull GroupChip chip, @NonNull TabGroup g) {
        int color = TabGroup.colorValue(getContext(), g.color, false);
        chip.color = color;
        // 名前が無いグループは「N 個のタブ」ではなく、タブの数だけを出す(読み上げは「N 個のタブ」のまま)
        int tabs = host.groupTabCount(g.id);
        boolean named = g.title != null && !g.title.trim().isEmpty();
        chip.label.setText(named ? g.displayTitle(tabs) : String.valueOf(tabs));
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(8));
        pill.setColor(color);
        chip.label.setBackground(pill);
        // 明るい色には黒、暗い色には白の文字
        double lum = androidx.core.graphics.ColorUtils.calculateLuminance(color);
        chip.label.setTextColor(lum > 0.5 ? 0xFF1F1F1F : 0xFFFFFFFF);
        chip.setContentDescription(Lang.t("タブグループ: ") + g.displayTitle(tabs));
        chip.setTooltipText(g.displayTitle(tabs));
        chip.label.measure(MeasureSpec.makeMeasureSpec(dp(CHIP_MAX_DP), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int w = chip.label.getMeasuredWidth() + chip.getPaddingLeft() + chip.getPaddingRight();
        ViewGroup.LayoutParams lp = chip.getLayoutParams();
        if (lp == null) {
            chip.setLayoutParams(new LayoutParams(w, dp(TAB_HEIGHT_DP)));
        } else if (lp.width != w) {
            lp.width = w;
            chip.setLayoutParams(lp);
        }
        chip.invalidate();
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
        /** グループの色(グループに入っていなければ 0)。 */
        int groupColor;
        private final Paint groupPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
            setForeground(roundRipple(dp(3), dp(4)));

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
                if (removing || !dragBlock.isEmpty()) return false;
                beginDrag(this, true);
                return true;
            });
            setOnTouchListener(TabStrip.this::onItemTouch);
        }

        void setGroupColor(int c) {
            if (groupColor == c) return;
            groupColor = c;
            groupPaint.setColor(c);
            invalidate();
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
            // グループのタブは下端にグループの色の線を引く(隣のタブとつながって 1 本に見えるよう横いっぱい)
            if (groupColor != 0 && w > 0) {
                canvas.drawRect(0, h - dp(3), w, h, groupPaint);
            }
        }
    }

    /** グループの先頭に置く、名前のチップ。押すとグループのメニュー、長押しでグループごと動かす。 */
    @SuppressLint("ViewConstructor")
    private final class GroupChip extends FrameLayout {
        final int groupId;
        final TextView label;
        int color;
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @SuppressLint("ClickableViewAccessibility")
        GroupChip(@NonNull Context c, int groupId) {
            super(c);
            this.groupId = groupId;
            setPadding(dp(6), 0, dp(6), 0);
            setWillNotDraw(false);
            setForeground(roundRipple(dp(2), dp(4)));
            label = new TextView(c);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setMaxWidth(dp(CHIP_MAX_DP) - dp(12));
            label.setPadding(dp(10), dp(3), dp(10), dp(3));
            addView(label, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
            setOnClickListener(v -> host.showGroupMenu(v, groupId));
            setOnLongClickListener(v -> {
                if (!dragBlock.isEmpty()) return false;
                beginDrag(this, true);
                return true;
            });
            setOnTouchListener(TabStrip.this::onItemTouch);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            // グループの線はチップのまん中から始めて、続くタブの線とつなげる
            if (color != 0 && getWidth() > 0) {
                linePaint.setColor(color);
                canvas.drawRect(getWidth() / 2f, getHeight() - dp(3), getWidth(), getHeight(), linePaint);
            }
        }
    }

    /** マウスを乗せた時・押した時の丸い背景(形より少し内側)。 */
    private android.graphics.drawable.Drawable roundRipple(int insetX, int insetY) {
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(8));
        mask.setColor(0xFFFFFFFF);
        InsetDrawable inset = new InsetDrawable(mask, insetX, insetY, insetX, insetY);
        TypedValue tv = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.colorControlHighlight, tv, true);
        int highlight = tv.resourceId != 0 ? getContext().getColor(tv.resourceId) : tv.data;
        return new RippleDrawable(ColorStateList.valueOf(highlight), null, inset);
    }

    // ------------------------------------------------------------------
    // ドラッグで並べ替える(グループへの出し入れ・グループごとの移動も)
    // ------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private boolean onItemTouch(View view, MotionEvent e) {
        boolean isTab = view instanceof TabView;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (isTab && (e.getButtonState() & MotionEvent.BUTTON_TERTIARY) != 0) {
                    // マウスの中ボタンで閉じる(パソコンのブラウザと同じ)
                    int i = indexOfId(((TabView) view).tabId);
                    if (i >= 0) host.closeTab(i);
                    return true;
                }
                if ((e.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
                    // 右クリックでメニュー
                    if (isTab) {
                        int i = indexOfId(((TabView) view).tabId);
                        if (i >= 0) host.showTabContextMenu(view, i);
                    } else {
                        host.showGroupMenu(view, ((GroupChip) view).groupId);
                    }
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
                if (dragHandle == view) {
                    onDragMove(e.getRawX());
                    return true;
                }
                boolean removing = isTab && ((TabView) view).removing;
                if (mouseDown && dragBlock.isEmpty() && !removing && Math.abs(e.getRawX() - downRawX) > touchSlop) {
                    view.cancelLongPress();
                    beginDrag(view, false);
                    onDragMove(e.getRawX());
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mouseDown = false;
                if (dragHandle == view) {
                    // 離した所で並びを確定する(クリックとしては扱わない)
                    endDrag();
                    return true;
                }
                return false;
        }
        return false;
    }

    /** タブ(またはチップならグループごと)を持ち上げる。 */
    private void beginDrag(@NonNull View v, boolean haptic) {
        // 開閉のアニメーションの途中なら終わらせてから(幅が決まっている前提で位置を計算する)
        if (widthAnimator != null) widthAnimator.end();
        int at = row.indexOfChild(v);
        if (at < 0) return;
        dragBlock.clear();
        dragGroup = v instanceof GroupChip;
        dragBlock.add(v);
        dragOriginalGroup = -1;
        if (dragGroup) {
            // チップに続く、同じグループのタブもまとめて持ち上げる
            int gid = ((GroupChip) v).groupId;
            for (int i = at + 1; i < row.getChildCount(); i++) {
                View c = row.getChildAt(i);
                if (!(c instanceof TabView) || groupOf((TabView) c) != gid) break;
                dragBlock.add(c);
            }
        } else {
            dragOriginalGroup = groupOf((TabView) v);
        }
        dropGroupId = dragOriginalGroup;
        dragHandle = v;
        dragByLongPress = haptic;
        dragMoved = false;
        // 並べ直しの計算に使う、今の左端(幅から計算する。レイアウトがまだ追いついていないことがあるため)
        baseLeft.clear();
        shiftTarget.clear();
        int left = row.getPaddingLeft();
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            baseLeft.put(c, left);
            left += targetWidth(c);
        }
        dropOrder.clear();
        scroll.requestDisallowInterceptTouchEvent(true);
        if (haptic) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        // 持ち上げる
        for (View c : dragBlock) {
            c.animate().scaleX(1.04f).scaleY(1.04f).translationZ(dp(6)).setDuration(150).setInterpolator(EASE).start();
        }
        if (!dragGroup) {
            int i = indexOfId(((TabView) v).tabId);
            if (i >= 0 && i != host.currentTab()) host.selectTab(i);
        }
        bindAll();
    }

    private int blockLeft() {
        Integer l = baseLeft.get(dragBlock.get(0));
        return l != null ? l : 0;
    }

    private int blockWidth() {
        int w = 0;
        for (View c : dragBlock) w += targetWidth(c);
        return w;
    }

    private void onDragMove(float rawX) {
        if (dragBlock.isEmpty()) return;
        lastRawX = rawX;
        if (Math.abs(rawX - downRawX) > touchSlop) dragMoved = true;
        float dx = rawX - downRawX + (scroll.getScrollX() - downScrollX);
        int bLeft = blockLeft();
        int bWidth = blockWidth();
        int total = row.getPaddingLeft();
        for (int i = 0; i < row.getChildCount(); i++) total += targetWidth(row.getChildAt(i));
        // 並びの外へは出さない
        float min = row.getPaddingLeft() - bLeft;
        float max = total - bWidth - bLeft;
        dx = Math.max(min, Math.min(max, dx));
        for (View c : dragBlock) c.setTranslationX(dx);
        layoutAround(bLeft + dx + bWidth / 2f);
        // 端まで持っていったら帯をスクロールする
        removeCallbacks(autoScroll);
        if (autoScrollSpeed() != 0) postOnAnimation(autoScroll);
    }

    /**
     * 持ち上げているものの中心が center にある時の並びを決め、周りのタブとチップをそこへ滑らせる。
     * タブ 1 枚の時は入るグループも決める(グループのタブの間・チップのすぐ後ろなら、そのグループに入る)。
     */
    private void layoutAround(float center) {
        // 持ち上げているもの以外の並び(閉じかけのタブは除く)
        List<View> others = new ArrayList<>();
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            if (dragBlock.contains(c)) continue;
            if (c instanceof TabView && ((TabView) c).removing) continue;
            others.add(c);
        }
        // タブ 1 枚だけのグループから持ち出した時は、そのチップを並びから外す(落とすまで薄くしておく)
        if (!dragGroup && dragOriginalGroup >= 0 && countTabsOf(others, dragOriginalGroup) == 0) {
            for (int i = others.size() - 1; i >= 0; i--) {
                View c = others.get(i);
                if (c instanceof GroupChip && ((GroupChip) c).groupId == dragOriginalGroup) {
                    c.setAlpha(0.3f);
                    others.remove(i);
                }
            }
        }

        // 単位に分ける(グループを動かす時は、ほかのグループもチップとタブで 1 つ)
        List<List<View>> units = new ArrayList<>();
        for (int i = 0; i < others.size(); i++) {
            View c = others.get(i);
            List<View> u = new ArrayList<>();
            u.add(c);
            if (dragGroup && c instanceof GroupChip) {
                int gid = ((GroupChip) c).groupId;
                while (i + 1 < others.size() && others.get(i + 1) instanceof TabView
                        && groupOf((TabView) others.get(i + 1)) == gid) {
                    u.add(others.get(++i));
                }
            }
            units.add(u);
        }
        // ほかのものだけを並べた時の位置で、中心がどこに入るかを決める
        int[] uLeft = new int[units.size()];
        int[] uWidth = new int[units.size()];
        int left = row.getPaddingLeft();
        for (int k = 0; k < units.size(); k++) {
            uLeft[k] = left;
            int w = 0;
            for (View c : units.get(k)) w += targetWidth(c);
            uWidth[k] = w;
            left += w;
        }
        int insert = 0;
        while (insert < units.size() && uLeft[insert] + uWidth[insert] / 2f < center) insert++;

        // 入るグループ(タブ 1 枚の時だけ)
        int join = -1;
        if (!dragGroup && insert > 0) {
            View prev = units.get(insert - 1).get(0);
            View next = insert < units.size() ? units.get(insert).get(0) : null;
            int prevGroup = prev instanceof GroupChip ? ((GroupChip) prev).groupId : groupOf((TabView) prev);
            int nextGroup = next instanceof TabView ? groupOf((TabView) next) : -1;
            if (prev instanceof GroupChip || (prevGroup >= 0 && prevGroup == nextGroup)) {
                join = prevGroup;
            } else if (prevGroup >= 0) {
                // グループの最後のタブのすぐ後ろ: そのタブの右端より左ならグループに入り、右なら外に出る
                join = center < uLeft[insert - 1] + uWidth[insert - 1] ? prevGroup : -1;
            }
        }

        // 新しい並び
        List<View> seq = new ArrayList<>();
        for (int k = 0; k < units.size(); k++) {
            if (k == insert) seq.addAll(dragBlock);
            seq.addAll(units.get(k));
        }
        if (insert >= units.size()) seq.addAll(dragBlock);

        // 周りを新しい位置へ滑らせる
        left = row.getPaddingLeft();
        for (View c : seq) {
            if (!dragBlock.contains(c)) {
                Integer base = baseLeft.get(c);
                float shift = base != null ? left - base : 0;
                Float last = shiftTarget.get(c);
                if (last == null || last != shift) {
                    shiftTarget.put(c, shift);
                    c.animate().translationX(shift).setDuration(SHIFT_MS).setInterpolator(EASE).start();
                }
            }
            left += targetWidth(c);
        }

        // 落とした時の並び(タブの ID)
        dropOrder.clear();
        for (View c : seq) if (c instanceof TabView) dropOrder.add(((TabView) c).tabId);
        if (!dragGroup && join != dropGroupId) {
            dropGroupId = join;
            // 入る先のグループの色の線で、グループに入る/外れることが分かるようにする
            ((TabView) dragBlock.get(0)).setGroupColor(join >= 0 ? colorOfGroup(join) : 0);
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    private int groupOf(@NonNull TabView t) {
        int i = indexOfId(t.tabId);
        TabGroup g = i >= 0 ? host.tabGroup(i) : null;
        return g != null ? g.id : -1;
    }

    private int countTabsOf(@NonNull List<View> views, int groupId) {
        int n = 0;
        for (View c : views) if (c instanceof TabView && groupOf((TabView) c) == groupId) n++;
        return n;
    }

    private int colorOfGroup(int groupId) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            if (c instanceof GroupChip && ((GroupChip) c).groupId == groupId) return ((GroupChip) c).color;
        }
        return 0;
    }

    private int autoScrollSpeed() {
        if (dragBlock.isEmpty() || row.getWidth() <= scroll.getWidth()) return 0;
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
        if (dragBlock.isEmpty()) return;
        removeCallbacks(autoScroll);
        List<View> block = new ArrayList<>(dragBlock);
        View handle = dragHandle;
        boolean group = dragGroup;
        int join = dropGroupId;
        int original = dragOriginalGroup;
        List<Integer> orderIds = new ArrayList<>(dropOrder);
        dragBlock.clear();
        dragHandle = null;
        shiftTarget.clear();
        for (View c : block) {
            c.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(180).setInterpolator(EASE).start();
        }
        // 今の並び(タブの ID)
        int n = host.tabCount();
        List<Integer> currentIds = new ArrayList<>(n);
        for (int i = 0; i < n; i++) currentIds.add(host.tabId(i));
        boolean moved = orderIds.size() == n && !orderIds.equals(currentIds);
        boolean regrouped = !group && join != original;
        if (!moved && !regrouped) {
            // 何も変わらなかった: 元の位置へ戻す
            for (int i = 0; i < row.getChildCount(); i++) {
                View c = row.getChildAt(i);
                if (c instanceof GroupChip) c.setAlpha(1f);
                c.animate().translationX(0f).setDuration(SHIFT_MS).setInterpolator(EASE).start();
            }
            bindAll();
            // 長押ししただけ(動かさなかった): メニューを出す
            if (dragByLongPress && !dragMoved && handle != null) {
                if (handle instanceof TabView) {
                    int i = indexOfId(((TabView) handle).tabId);
                    if (i >= 0) host.showTabContextMenu(handle, i);
                } else {
                    host.showGroupMenu(handle, ((GroupChip) handle).groupId);
                }
            }
            return;
        }
        // 今見えている位置を覚えておき、並び直した後でそこから滑らせる
        Map<View, Float> visual = new HashMap<>();
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            Integer base = baseLeft.get(c);
            visual.put(c, (base != null ? base : c.getLeft()) + c.getTranslationX());
        }
        flipFrom = visual;
        List<Integer> order = new ArrayList<>(n);
        if (moved) {
            for (int id : orderIds) order.add(currentIds.indexOf(id));
        } else {
            for (int i = 0; i < n; i++) order.add(i);
        }
        if (group) {
            host.reorderTabs(order);
        } else {
            host.dropTab(order, ((TabView) block.get(0)).tabId, join);
        }
        refresh();
    }

    /** 並び直した子を、前に見えていた位置から新しい位置へ滑らせる。 */
    private void playReorder(@NonNull Map<View, Float> visual) {
        int left = row.getPaddingLeft();
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            Float old = visual.get(c);
            c.animate().cancel();
            if (old != null) {
                c.setTranslationX(old - left);
                c.animate().translationX(0f).setDuration(SHIFT_MS).setInterpolator(EASE).start();
            } else {
                c.setTranslationX(0f);
            }
            left += targetWidth(c);
        }
    }

    private int color(int attr) {
        return MaterialColors.getColor(this, attr, android.graphics.Color.GRAY);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
