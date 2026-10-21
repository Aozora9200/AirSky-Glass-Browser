package com.airsky.browser;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Chrome / Firefox 風のタブ一覧。
 *
 * 別の Activity ではなくブラウザ画面の上に重ねるビューとして動くので、
 * タブの操作(選択・閉じる・並べ替え・新規)はその場で {@link Host} に反映される。
 * 開くときは表示中のページがカードへ縮小し、タブを選ぶとカードから画面いっぱいに拡大する。
 *
 * 上部の切り替えは「通常のタブ」「タブグループ」「シークレットタブ」。タブグループのタブは一覧では
 * 1 枚のカードにたたみ、押すとグループのタブだけを並べたパネルを開く。
 * 一覧でカードを別のカードの上に重ねて離すとグループになり、グループのパネルからタブを外へ出すと
 * グループから外れる。しばらく使っていないタブ・重複しているタブは一番上の「非アクティブなタブ」にまとめる。
 */
public class TabSwitcher {

    /** タブのデータと操作を提供する側(MainActivity)。index はタブ一覧上の並び順。 */
    public interface Host {
        int getTabCount();
        int getCurrentTabIndex();
        int getTabId(int index);
        @Nullable String getTabTitle(int index);
        @Nullable String getTabUrl(int index);
        @Nullable Bitmap getTabIcon(int index);
        /** メモリ上にあるページ画像(実寸)。無ければ null。 */
        @Nullable Bitmap getTabSnapshot(int index);
        /** 保存済みのページ画像。シークレットなど保存しない場合は null。 */
        @Nullable File getTabSnapshotFile(int index);
        /** もう一方のモード(通常 ⇔ シークレット)のタブ数。不明なら -1。 */
        int getOtherModeTabCount();
        boolean isSecret();
        /** ページを表示している領域。拡大縮小アニメーションの基準になる。 */
        View getContentView();

        void onSelectTab(int index);
        void onCloseTab(int index);
        void onNewTab();
        void onSwitchMode();
        void onMenuItem(int itemId);
        /** ドラッグでの並べ替え。order は元の index を新しい並び順に並べたもの。 */
        void onReorderTabs(@NonNull List<Integer> order);

        // ---- タブグループ(通常タブのみ) ----

        boolean supportsGroups();
        /** そのタブのグループの ID。グループに入っていなければ -1。 */
        int getTabGroupId(int index);
        @Nullable TabGroup getGroup(int groupId);
        /** 開いているグループ・閉じて保存しているグループ(新しく更新した順)。 */
        @NonNull List<TabGroup> getGroups();
        /** グループのタブの index(並び順)。 */
        @NonNull List<Integer> getTabsInGroup(int groupId);
        int countTabsInGroup(int groupId);
        @Nullable Bitmap getFaviconForUrl(@Nullable String url);
        /** タブをまとめて新しいグループにする。グループの ID を返す。 */
        int onCreateGroup(@NonNull List<Integer> indices);
        /** 新しいタブ 1 つだけの新しいグループを作る。グループの ID を返す。 */
        int onCreateGroupWithNewTab();
        /** 名前・色を変えた(保存する)。 */
        void onGroupChanged(int groupId);
        /** グループに新しいタブを開いて表示する。 */
        void onNewTabInGroup(int groupId);
        /** グループを閉じて保存する(タブは閉じる)。 */
        void onCloseGroup(int groupId);
        /** グループとそのタブを削除する。 */
        void onDeleteGroup(int groupId);
        /** グループを解除する(タブはそのまま残す)。 */
        void onUngroup(int groupId);
        /** 閉じて保存していたグループのタブを開き直す。 */
        void onOpenSavedGroup(int groupId);
        /** シークレットから、通常のタブ一覧の「タブグループ」へ。 */
        void onShowGroups();
        /** タブをグループに移す(グループの最後に並べる)。元のグループが空になれば消す。 */
        void onMoveTabsToGroup(@NonNull List<Integer> indices, int groupId);
        /** タブをグループから外す(グループのすぐ後ろに並べる)。 */
        void onRemoveTabFromGroup(int index);

        // ---- 非アクティブなタブ(通常タブのみ) ----

        /** 非アクティブなタブの index。シークレットや機能を使わない時は空。 */
        @NonNull Set<Integer> getInactiveTabIndices();
        /** 非アクティブなタブをすべて閉じる(グループは閉じて保存する)。 */
        void onCloseInactiveTabs();
    }

    /** 配色。通常は Material 3 のテーマ色、シークレットは常に暗い配色。 */
    static class Palette {
        int background, onBackground, divider;
        int card, onCard, cardSelected, onCardSelected, thumbPlaceholder;
        int search, onSearch, hint;
        int segment, segmentSelected, onSegment;
        int accent, onAccent, iconButton;
        float cardRadius;

        static Palette of(Context c, boolean secret) {
            Palette p = new Palette();
            float density = c.getResources().getDisplayMetrics().density;
            p.cardRadius = 26 * density;
            if (secret) {
                p.background = 0xFF1B1B1F;
                p.onBackground = 0xFFE3E2E6;
                p.divider = 0xFF2F2F34;
                p.card = 0xFF2B2B30;
                p.onCard = 0xFFE3E2E6;
                p.cardSelected = 0xFFA8C7FA;
                p.onCardSelected = 0xFF0B1D3A;
                p.thumbPlaceholder = 0xFF121214;
                p.search = 0xFF2B2B30;
                p.onSearch = 0xFFE3E2E6;
                p.hint = 0xFF9A9AA2;
                p.segment = 0xFF2B2B30;
                p.segmentSelected = 0xFF45454D;
                p.onSegment = 0xFFE3E2E6;
                p.accent = 0xFFA8C7FA;
                p.onAccent = 0xFF0B1D3A;
                p.iconButton = 0xFF2B2B30;
                return p;
            }
            p.background = color(c, com.google.android.material.R.attr.colorSurface, 0xFFFDFBFF);
            p.onBackground = color(c, com.google.android.material.R.attr.colorOnSurface, 0xFF1B1B1F);
            p.divider = color(c, com.google.android.material.R.attr.colorOutlineVariant, 0xFFC6C6D0);
            p.card = color(c, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFFE9E7EC);
            p.onCard = p.onBackground;
            p.cardSelected = color(c, androidx.appcompat.R.attr.colorPrimary, 0xFF445E91);
            p.onCardSelected = color(c, com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF);
            p.thumbPlaceholder = color(c, com.google.android.material.R.attr.colorSurfaceContainerLowest, 0xFFFFFFFF);
            p.search = color(c, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFFE9E7EC);
            p.onSearch = p.onBackground;
            p.hint = color(c, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF45464F);
            p.segment = color(c, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFFE9E7EC);
            p.segmentSelected = color(c, com.google.android.material.R.attr.colorSurfaceContainerLowest, 0xFFFFFFFF);
            p.onSegment = p.onBackground;
            p.accent = color(c, androidx.appcompat.R.attr.colorPrimary, 0xFF445E91);
            p.onAccent = color(c, com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF);
            p.iconButton = color(c, com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFFE3E2E6);
            return p;
        }

        private static int color(Context c, int attr, int fallback) {
            return MaterialColors.getColor(c, attr, fallback);
        }
    }

    private static final long OPEN_DURATION = 340;
    private static final long CLOSE_DURATION = 320;
    // Material 3 の emphasized に近い曲線
    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private static final int PANE_TABS = 0;
    private static final int PANE_GROUPS = 1;

    private final Activity activity;
    private final Host host;
    private final FrameLayout root;
    private final View background;
    private final View content;
    private final RecyclerView grid;
    private final GridLayoutManager layoutManager;
    private final TabSwitcherAdapter.ThumbCache thumbCache = new TabSwitcherAdapter.ThumbCache();
    private final TabSwitcherAdapter adapter;
    private final RecyclerView groupList;
    private final TabGroupListAdapter groupListAdapter;
    private final EditText searchField;
    private final ImageButton searchClear;
    private final TextView emptyView;
    private final View emptyBox;
    private final TabGroupEmptyView emptyArt;
    private final TextView tabCount;
    private final View segNormal, segGroups, segSecret;
    private Palette palette;
    private int pane = PANE_TABS;

    // グループを開いた時のパネル
    private final View groupOverlay, groupPanel, groupTitleRow, groupTitleLine, groupDot;
    private final FrameLayout groupPalette;
    private final EditText groupTitle;
    private final RecyclerView groupGrid;
    private final GridLayoutManager groupLayoutManager;
    @Nullable private TabSwitcherAdapter groupAdapter;
    private int openGroupId = -1;
    /** パネルに非アクティブなタブを並べている。 */
    private boolean inactiveOpen;
    private final View inactiveFooter, dropHint;

    private boolean showing;
    private boolean animating;
    /** Host にタブ操作を頼んでいる最中(その結果の refresh() 要求は無視する)。 */
    private boolean mutating;
    @Nullable private Animator runningAnimator;
    @Nullable private TabTransitionView transitionView;

    public TabSwitcher(Activity activity, FrameLayout root, Host host) {
        this.activity = activity;
        this.root = root;
        this.host = host;
        this.palette = Palette.of(activity, host.isSecret());

        background = root.findViewById(R.id.tsBackground);
        content = root.findViewById(R.id.tsContent);
        grid = root.findViewById(R.id.tsGrid);
        groupList = root.findViewById(R.id.tsGroupList);
        searchField = root.findViewById(R.id.tsSearchField);
        searchClear = root.findViewById(R.id.tsSearchClear);
        emptyView = root.findViewById(R.id.tsEmpty);
        emptyBox = root.findViewById(R.id.tsEmptyBox);
        emptyArt = root.findViewById(R.id.tsEmptyArt);
        tabCount = root.findViewById(R.id.tsTabCount);
        segNormal = root.findViewById(R.id.tsSegNormal);
        segGroups = root.findViewById(R.id.tsSegGroups);
        segSecret = root.findViewById(R.id.tsSegSecret);
        groupOverlay = root.findViewById(R.id.tsGroupOverlay);
        groupPanel = root.findViewById(R.id.tsGroupPanel);
        groupTitleRow = root.findViewById(R.id.tsGroupTitleRow);
        groupTitleLine = root.findViewById(R.id.tsGroupTitleLine);
        groupDot = root.findViewById(R.id.tsGroupDot);
        groupPalette = root.findViewById(R.id.tsGroupPalette);
        groupTitle = root.findViewById(R.id.tsGroupTitle);
        groupGrid = root.findViewById(R.id.tsGroupGrid);
        inactiveFooter = root.findViewById(R.id.tsInactiveFooter);
        dropHint = root.findViewById(R.id.tsGroupDropHint);

        adapter = new TabSwitcherAdapter(host, palette, thumbCache, -1, new TabSwitcherAdapter.Callbacks() {
            @Override
            public void onCardClicked(int tabIndex) {
                if (!animating) selectTab(tabIndex, true);
            }

            @Override
            public void onCardClose(int adapterPosition) {
                if (!animating) closeAt(adapter, adapterPosition);
            }

            @Override
            public void onGroupClicked(int groupId) {
                if (!animating) openGroup(groupId);
            }

            @Override
            public void onGroupMenu(int groupId, @NonNull View anchor) {
                if (!animating) showGroupCardMenu(groupId, anchor);
            }

            @Override
            public void onInactiveClicked() {
                if (!animating) openInactivePanel();
            }
        });

        layoutManager = new GridLayoutManager(activity, 2);
        // 一番上の「非アクティブなタブ」は横いっぱいに出す
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.isInactiveHeaderAt(position) ? layoutManager.getSpanCount() : 1;
            }
        });
        grid.setLayoutManager(layoutManager);
        grid.setAdapter(adapter);
        grid.setHasFixedSize(true);
        new ItemTouchHelper(new TouchCallback(false)).attachToRecyclerView(grid);

        groupLayoutManager = new GridLayoutManager(activity, 2);
        groupGrid.setLayoutManager(groupLayoutManager);
        groupGrid.setHasFixedSize(true);
        new ItemTouchHelper(new TouchCallback(true)).attachToRecyclerView(groupGrid);

        groupListAdapter = new TabGroupListAdapter(host, palette, new TabGroupListAdapter.Callbacks() {
            @Override
            public void onGroupRowClicked(int groupId) {
                if (!animating) openFromGroupsPane(groupId);
            }

            @Override
            public void onGroupRowMenu(int groupId, @NonNull View anchor) {
                if (!animating) showGroupRowMenu(groupId, anchor);
            }
        });
        groupList.setLayoutManager(new LinearLayoutManager(activity));
        groupList.setAdapter(groupListAdapter);

        // 画面幅に合わせて列数を変える(縦: 2列 / 横・タブレット: 3〜4列)
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            float dpWidth = (r - l) / activity.getResources().getDisplayMetrics().density;
            int span = dpWidth >= 900 ? 4 : dpWidth >= 560 ? 3 : 2;
            if (span != layoutManager.getSpanCount()) layoutManager.setSpanCount(span);
            if (span != groupLayoutManager.getSpanCount()) groupLayoutManager.setSpanCount(span);
        });

        root.findViewById(R.id.tsNewTab).setOnClickListener(v -> {
            if (animating) return;
            // タブグループ一覧を見ている時の「+」は、新しいタブグループを作る
            if (pane == PANE_GROUPS && host.supportsGroups() && !host.isSecret()) {
                createGroupWithNewTab();
            } else {
                openNewTab();
            }
        });
        root.findViewById(R.id.tsMenu).setOnClickListener(this::showMenu);
        segNormal.setOnClickListener(v -> {
            if (animating) return;
            if (host.isSecret()) {
                host.onSwitchMode();
            } else {
                setPane(PANE_TABS);
            }
        });
        segGroups.setOnClickListener(v -> {
            if (animating) return;
            if (host.isSecret()) {
                host.onShowGroups();
            } else {
                setPane(PANE_GROUPS);
            }
        });
        segSecret.setOnClickListener(v -> {
            if (!host.isSecret() && !animating) host.onSwitchMode();
        });

        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                searchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (pane == PANE_GROUPS) {
                    groupListAdapter.setQuery(s.toString());
                } else {
                    adapter.setQuery(s.toString());
                }
                updateEmptyView();
            }
        });
        searchClear.setOnClickListener(v -> searchField.setText(""));

        setupGroupPanel();
        applyPalette();
    }

    // ---- 公開API ----

    public boolean isShowing() {
        return showing;
    }

    /** タブ一覧を開く。animate なら現在のページがカードへ縮小する。 */
    public void show(boolean animate) {
        if (showing) return;
        showing = true;
        cancelAnimation();

        palette = Palette.of(activity, host.isSecret());
        pane = PANE_TABS;
        closeGroupPanelNow();
        applyPalette();
        applyPane(false);
        searchField.setText("");
        adapter.reload();
        updateCount();
        updateEmptyView();

        root.setVisibility(View.VISIBLE);

        final int current = host.getCurrentTabIndex();
        final int position = adapter.positionForTab(current);
        if (position >= 0) layoutManager.scrollToPosition(position);

        if (!animate || position < 0) {
            setContentAlpha(1f, 1f);
            return;
        }

        setContentAlpha(0f, 0f);
        animating = true;

        final RectF from = contentRect();
        final TabTransitionView tv = ensureTransitionView();
        tv.setBitmap(host.getTabSnapshot(current));
        tv.setFillColor(palette.thumbPlaceholder);
        tv.setFrame(from, 0);

        whenCardLaidOut(position, holder -> {
            View target = holder != null ? adapter.transitionTargetFor(holder, current) : null;
            if (target == null) {
                // カードが見つからない時はフェードだけ
                removeTransitionView();
                fadeContent(0f, 1f, () -> animating = false);
                return;
            }
            target.setAlpha(0f);
            final RectF to = viewRect(target);
            final float endRadius = radiusOf(target);
            final RectF frame = new RectF();

            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(OPEN_DURATION);
            va.setInterpolator(EMPHASIZED);
            va.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                TabTransitionView.lerp(from, to, f, frame);
                tv.setFrame(frame, endRadius * f);
                setContentAlpha(Math.min(1f, f * 1.6f), clamp((f - 0.2f) / 0.8f));
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    target.setAlpha(1f);
                    setContentAlpha(1f, 1f);
                    removeTransitionView();
                    animating = false;
                    runningAnimator = null;
                }
            });
            runningAnimator = va;
            va.start();
        });
    }

    /** 「タブグループ」の一覧を出す(シークレットから切り替えてきた時など)。 */
    public void showGroupsPane() {
        if (!showing || !host.supportsGroups()) return;
        setPane(PANE_GROUPS);
    }

    /**
     * 通常の「タブ」の一覧を出す。シークレットから「タブ」を選んで戻ってきた時、
     * シークレットへ切り替える前にタブグループ一覧を見ていても、タブの一覧に戻す。
     */
    public void showTabsPane() {
        if (!showing) return;
        setPane(PANE_TABS);
    }

    /** タブ一覧を閉じて現在のタブに戻る。 */
    public void dismiss(boolean animate) {
        if (!showing) return;
        int current = host.getCurrentTabIndex();
        if (animate && current >= 0) {
            selectTab(current, true);
        } else {
            hideNow();
        }
    }

    /** アニメーション無しで即座に閉じる(他の画面からページを開いた時など)。 */
    public void hideNow() {
        if (!showing) return;
        cancelAnimation();
        removeTransitionView();
        hideKeyboard();
        closeGroupPanelNow();
        showing = false;
        animating = false;
        root.setVisibility(View.GONE);
        setContentAlpha(1f, 1f);
    }

    /**
     * 「すべてのタブを閉じる」の動き。カードが外側から順に、回りながら一覧の中心へ吸い込まれて消え、
     * 中心で小さな波紋が広がってから end を呼ぶ(タブを実際に閉じるのは end の中)。
     * タブ一覧を出していない時などは、すぐに end を呼ぶ。
     */
    public void playCloseAll(@NonNull Runnable end) {
        if (!showing || animating || pane != PANE_TABS || grid.getChildCount() == 0) {
            end.run();
            return;
        }
        animating = true;
        hideKeyboard();
        float density = activity.getResources().getDisplayMetrics().density;
        float cx = grid.getWidth() / 2f;
        float cy = grid.getHeight() / 2f;
        int n = grid.getChildCount();
        // 中心から遠いカードほど先に動かす
        float maxDist = 1f;
        float[] dist = new float[n];
        for (int i = 0; i < n; i++) {
            View c = grid.getChildAt(i);
            float dx = cx - (c.getLeft() + c.getWidth() / 2f);
            float dy = cy - (c.getTop() + c.getHeight() / 2f);
            dist[i] = (float) Math.hypot(dx, dy);
            maxDist = Math.max(maxDist, dist[i]);
        }
        PathInterpolator accel = new PathInterpolator(0.4f, 0f, 0.9f, 0.4f);
        long last = 0;
        for (int i = 0; i < n; i++) {
            View c = grid.getChildAt(i);
            c.animate().cancel();
            float dx = cx - (c.getLeft() + c.getWidth() / 2f);
            float dy = cy - (c.getTop() + c.getHeight() / 2f);
            long delay = Math.round(160 * (1f - dist[i] / maxDist)) + 15L * (i % 3);
            last = Math.max(last, delay);
            c.setPivotX(c.getWidth() / 2f);
            c.setPivotY(c.getHeight() / 2f);
            c.animate().translationX(dx * 0.9f).translationY(dy * 0.9f)
                    .scaleX(0.18f).scaleY(0.18f).rotation(dx >= 0 ? 14f : -14f).alpha(0f)
                    .setStartDelay(delay).setDuration(360).setInterpolator(accel).start();
        }

        // 中心の波紋
        View ripple = new View(activity);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(com.google.android.material.color.MaterialColors.getColor(root,
                androidx.appcompat.R.attr.colorPrimary));
        ripple.setBackground(circle);
        int size = Math.round(220 * density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        int[] gridLoc = new int[2];
        int[] rootLoc = new int[2];
        grid.getLocationInWindow(gridLoc);
        root.getLocationInWindow(rootLoc);
        lp.leftMargin = Math.round(gridLoc[0] - rootLoc[0] + cx - size / 2f);
        lp.topMargin = Math.round(gridLoc[1] - rootLoc[1] + cy - size / 2f);
        ripple.setAlpha(0f);
        ripple.setScaleX(0.05f);
        ripple.setScaleY(0.05f);
        root.addView(ripple, lp);
        long rippleAt = last + 260;
        ripple.animate().alpha(0.32f).scaleX(0.35f).scaleY(0.35f).setStartDelay(rippleAt).setDuration(140)
                .withEndAction(() -> ripple.animate().alpha(0f).scaleX(1.25f).scaleY(1.25f).setStartDelay(0)
                        .setDuration(420).setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                        .withEndAction(() -> root.removeView(ripple)).start())
                .start();

        root.postDelayed(() -> {
            for (int i = 0; i < grid.getChildCount(); i++) {
                View c = grid.getChildAt(i);
                c.animate().cancel();
                c.setTranslationX(0f);
                c.setTranslationY(0f);
                c.setScaleX(1f);
                c.setScaleY(1f);
                c.setRotation(0f);
                c.setAlpha(1f);
            }
            grid.setAlpha(0f);
            animating = false;
            end.run();
            grid.animate().alpha(1f).setStartDelay(120).setDuration(280).start();
        }, rippleAt + 160);
    }

    /** タブの状態が外部要因(window.close() 等)で変わった時に呼ぶ。 */
    public void refresh() {
        if (!showing || mutating || animating) return;
        reloadAll();
    }

    /** 戻るボタン。処理したら true。 */
    public boolean handleBack() {
        if (!showing) return false;
        if (animating) return true;
        if (groupPalette.getVisibility() == View.VISIBLE) {
            showGroupPalette(false);
            return true;
        }
        if (panelOpen()) {
            closeGroupPanel(true);
            return true;
        }
        if (searchField.length() > 0) {
            searchField.setText("");
            return true;
        }
        if (pane == PANE_GROUPS) {
            setPane(PANE_TABS);
            return true;
        }
        dismiss(true);
        return true;
    }

    /** テーマ変更時に色を塗り直す。 */
    public void onThemeChanged() {
        palette = Palette.of(activity, host.isSecret());
        applyPalette();
        if (showing) {
            adapter.notifyDataSetChanged();
            groupListAdapter.notifyDataSetChanged();
            if (groupAdapter != null) groupAdapter.notifyDataSetChanged();
            updateGroupPanelHeader();
        }
    }

    public void release() {
        cancelAnimation();
        adapter.release();
        if (groupAdapter != null) groupAdapter.release();
        thumbCache.release();
    }

    // ---- 表示の切り替え(通常のタブ / タブグループ) ----

    private void setPane(int newPane) {
        if (newPane == PANE_GROUPS && !host.supportsGroups()) return;
        if (pane == newPane) return;
        hideKeyboard();
        closeGroupPanel(false);
        pane = newPane;
        searchField.setText("");
        applyPane(true);
        applyPalette();
        if (pane == PANE_GROUPS) {
            groupListAdapter.reload();
        } else {
            adapter.reload();
        }
        updateEmptyView();
    }

    /**
     * @param animate 切り替えボタンで切り替えた時。出てくる一覧を、切り替えボタンの並び
     *                (タブ → タブグループ)と同じ向きからすべり込ませる。
     */
    private void applyPane(boolean animate) {
        boolean groups = pane == PANE_GROUPS;
        grid.setVisibility(groups ? View.GONE : View.VISIBLE);
        groupList.setVisibility(groups ? View.VISIBLE : View.GONE);
        searchField.setHint(groups ? Lang.t("タブグループを検索") : Lang.t("タブを検索"));
        root.findViewById(R.id.tsNewTab).setContentDescription(groups ? Lang.t("新しいタブグループ") : Lang.t("新しいタブ"));
        View shown = groups ? groupList : grid;
        shown.animate().cancel();
        if (!animate) {
            shown.setAlpha(1f);
            shown.setTranslationX(0f);
            return;
        }
        float shift = 24 * activity.getResources().getDisplayMetrics().density;
        shown.setAlpha(0f);
        // タブグループは右から、タブは左から入る
        shown.setTranslationX(groups ? shift : -shift);
        shown.animate().alpha(1f).translationX(0f).setDuration(200)
                .setInterpolator(EMPHASIZED).start();
    }

    private void reloadAll() {
        adapter.reload();
        groupListAdapter.reload();
        refreshGroupPanel();
        updateCount();
        updateEmptyView();
    }

    // ---- タブ操作 ----

    private void selectTab(int index, boolean animate) {
        if (index < 0 || index >= host.getTabCount()) return;
        hideKeyboard();
        commitGroupTitle();

        // そのタブの画像が出ている場所(開いているグループのパネル → 一覧の順に探す)
        View target = null;
        RecyclerView.ViewHolder holder = null;
        if (panelOpen() && groupAdapter != null) {
            holder = groupGrid.findViewHolderForAdapterPosition(groupAdapter.positionForTab(index));
            if (holder != null) target = groupAdapter.transitionTargetFor(holder, index);
        }
        if (target == null && !panelOpen() && pane == PANE_TABS) {
            int position = adapter.positionForTab(index);
            holder = position >= 0 ? grid.findViewHolderForAdapterPosition(position) : null;
            if (holder != null) target = adapter.transitionTargetFor(holder, index);
        }

        Bitmap bitmap = host.getTabSnapshot(index);
        if (bitmap == null) bitmap = TabSwitcherAdapter.currentBitmap(target);

        // 先にタブを切り替えておく(破棄済みタブの復元・読み込みをアニメーション中に進める)
        mutating = true;
        host.onSelectTab(index);
        mutating = false;

        if (!animate || target == null) {
            if (animate) {
                fadeContent(1f, 0f, this::hideNow);
            } else {
                hideNow();
            }
            return;
        }

        cancelAnimation();
        animating = true;

        final View source = target;
        final RectF from = viewRect(source);
        final float startRadius = radiusOf(source);
        final TabTransitionView tv = ensureTransitionView();
        tv.setBitmap(bitmap);
        tv.setFillColor(palette.thumbPlaceholder);
        tv.setFrame(from, startRadius);
        tv.setAlpha(1f);
        source.setAlpha(0f);
        final View card = holder.itemView;
        final RectF frame = new RectF();

        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(CLOSE_DURATION);
        va.setInterpolator(EMPHASIZED);
        va.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            // アドレスバーの出し入れでページ領域が動くので毎フレーム取り直す
            TabTransitionView.lerp(from, contentRect(), f, frame);
            tv.setFrame(frame, startRadius * (1f - f));
            setContentAlpha(clamp(1f - (f - 0.35f) / 0.65f), clamp(1f - f * 1.8f));
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                source.setAlpha(1f);
                card.setAlpha(1f);
                setContentAlpha(0f, 0f);
                // 実際のページが描画されるまで少しだけ画像を残してからフェードアウト
                tv.animate()
                        .alpha(0f)
                        .setStartDelay(60)
                        .setDuration(140)
                        .withEndAction(() -> {
                            runningAnimator = null;
                            hideNow();
                        })
                        .start();
            }
        });
        runningAnimator = va;
        va.start();
    }

    private void closeAt(@Nullable TabSwitcherAdapter from, int position) {
        if (from == null) return;
        int index = from.tabIndexAt(position);
        if (index < 0) {
            // グループのカードをスワイプした: グループを閉じて保存する
            int gid = from.groupIdAt(position);
            if (gid >= 0) closeGroup(gid);
            return;
        }

        if (host.getTabCount() <= 1) {
            // 最後のタブ: Host 側で新しいタブを作る(シークレットは終了する)
            mutating = true;
            host.onCloseTab(index);
            mutating = false;
            return;
        }

        boolean grouped = host.supportsGroups() && host.getTabGroupId(index) >= 0;
        mutating = true;
        host.onCloseTab(index);
        mutating = false;
        if (!showing) return;
        if (from == adapter && !grouped && !adapter.isInactiveHeaderAt(0)) {
            adapter.onTabRemoved(position);
        } else if (from == adapter && !grouped) {
            // 閉じたタブで、重複していた別のタブが非アクティブでなくなることがあるので作り直す
            adapter.reload();
        } else {
            from.onTabRemoved(position);
            if (from != adapter) adapter.reload();
        }
        groupListAdapter.reload();
        refreshGroupPanel();
        updateCount();
        updateEmptyView();
    }

    private void openNewTab() {
        hideKeyboard();
        animating = true;
        fadeContent(1f, 0f, () -> {
            hideNow();
            host.onNewTab();
        });
    }

    private void showMenu(View anchor) {
        if (animating) return;
        PopupMenu popup = new PopupMenu(activity, anchor);
        popup.getMenuInflater().inflate(host.isSecret()
                ? R.menu.menu_tabsecretlist : R.menu.menu_tablist, popup.getMenu());
        // グループが 1 つも無ければ「タブグループを一括削除」は出さない
        android.view.MenuItem deleteGroups = popup.getMenu().findItem(R.id.action_delete_groups);
        if (deleteGroups != null) {
            deleteGroups.setVisible(host.supportsGroups() && !host.getGroups().isEmpty());
        }
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_delete_groups) {
                showDeleteGroupsDialog();
            } else if (id == R.id.action_new_group) {
                createGroupWithNewTab();
            } else if (id == R.id.action_group_tabs) {
                TabGroupUi.showPickTabsDialog(activity, host, this::createGroupFrom);
            } else {
                host.onMenuItem(id);
            }
            return true;
        });
        popup.show();
    }

    // ---- タブグループ ----

    private void createGroupFrom(@NonNull List<Integer> indices) {
        mutating = true;
        int gid = host.onCreateGroup(indices);
        mutating = false;
        if (gid < 0) return;
        if (pane != PANE_TABS) setPane(PANE_TABS);
        reloadAll();
        editNewGroup(gid, false);
    }

    private void createGroupWithNewTab() {
        mutating = true;
        int gid = host.onCreateGroupWithNewTab();
        mutating = false;
        if (gid < 0) return;
        if (pane != PANE_TABS) setPane(PANE_TABS);
        reloadAll();
        editNewGroup(gid, true);
    }

    /** 作ったグループに名前と色を付けてもらう(Chrome の「新しいタブグループ」)。 */
    private void editNewGroup(int gid, boolean openAfter) {
        TabGroup g = host.getGroup(gid);
        if (g == null) return;
        TabGroupUi.showEditDialog(activity, g, host.countTabsInGroup(gid), true, (title, color) -> {
            g.title = title;
            g.color = color;
            g.touch();
            host.onGroupChanged(gid);
            if (!showing) return;
            reloadAll();
            if (openAfter) openGroup(gid);
        });
    }

    private void renameGroup(int gid) {
        TabGroup g = host.getGroup(gid);
        if (g == null) return;
        TabGroupUi.showEditDialog(activity, g, host.countTabsInGroup(gid), false, (title, color) -> {
            g.title = title;
            g.color = color;
            g.touch();
            host.onGroupChanged(gid);
            if (showing) reloadAll();
        });
    }

    private void closeGroup(int gid) {
        closeGroupPanelNow();
        mutating = true;
        host.onCloseGroup(gid);
        mutating = false;
        if (showing) reloadAll();
    }

    private void ungroup(int gid) {
        closeGroupPanelNow();
        mutating = true;
        host.onUngroup(gid);
        mutating = false;
        if (showing) reloadAll();
    }

    private void confirmDeleteGroup(int gid) {
        TabGroup g = host.getGroup(gid);
        if (g == null) return;
        int count = g.closed ? g.saved.size() : host.countTabsInGroup(gid);
        String name = g.displayTitle(count);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(Lang.t("タブグループを削除しますか？"))
                .setMessage(g.closed
                        ? Lang.t("「") + name + Lang.t("」を削除します。")
                        : Lang.t("「") + name + Lang.t("」の ") + count + Lang.t(" 個のタブを閉じて、グループを削除します。"))
                .setNegativeButton(Lang.t("キャンセル"), null)
                .setPositiveButton(Lang.t("削除"), (d, w) -> {
                    closeGroupPanelNow();
                    mutating = true;
                    host.onDeleteGroup(gid);
                    mutating = false;
                    if (showing) reloadAll();
                })
                .show();
    }

    /**
     * タブグループの一括削除。全部に印を付けた一覧を出し、選んだグループをまとめて削除する
     * (開いているグループはタブも閉じる)。
     */
    private void showDeleteGroupsDialog() {
        List<TabGroup> groups = new ArrayList<>(host.getGroups());
        if (groups.isEmpty()) return;
        String[] labels = new String[groups.size()];
        boolean[] checked = new boolean[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            TabGroup g = groups.get(i);
            int count = g.closed ? g.saved.size() : host.countTabsInGroup(g.id);
            String name = g.displayTitle(count);
            String tabs = count + Lang.t(" 個のタブ");
            // 名前がそのまま「N 個のタブ」なら、タブの数を重ねて書かない
            labels[i] = name + (g.closed
                    ? Lang.t("(閉じたグループ)")
                    : name.equals(tabs) ? "" : Lang.t("(") + tabs + ")");
            checked[i] = true;
        }
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(Lang.t("タブグループを一括削除"))
                .setMultiChoiceItems(labels, checked, (d, which, on) -> {
                    checked[which] = on;
                    updateDeleteGroupsButton((androidx.appcompat.app.AlertDialog) d, checked);
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .setPositiveButton(Lang.t("削除"), (d, w) -> {
                    List<Integer> ids = new ArrayList<>();
                    for (int i = 0; i < groups.size(); i++) if (checked[i]) ids.add(groups.get(i).id);
                    confirmDeleteGroups(ids);
                })
                .create();
        dialog.setOnShowListener(d -> updateDeleteGroupsButton(dialog, checked));
        dialog.show();
    }

    private static void updateDeleteGroupsButton(androidx.appcompat.app.AlertDialog dialog, boolean[] checked) {
        int n = 0;
        for (boolean c : checked) if (c) n++;
        android.widget.Button b = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (b == null) return;
        b.setEnabled(n > 0);
        b.setText(n > 0 ? Lang.t("削除") + " (" + n + ")" : Lang.t("削除"));
    }

    private void confirmDeleteGroups(@NonNull List<Integer> ids) {
        if (ids.isEmpty()) return;
        int tabs = 0;
        for (int gid : ids) {
            TabGroup g = host.getGroup(gid);
            if (g != null && !g.closed) tabs += host.countTabsInGroup(gid);
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(ids.size() + Lang.t(" 個のタブグループを削除しますか？"))
                .setMessage(tabs > 0
                        ? Lang.t("グループの ") + tabs + Lang.t(" 個のタブも閉じます。この操作は元に戻せません。")
                        : Lang.t("この操作は元に戻せません。"))
                .setNegativeButton(Lang.t("キャンセル"), null)
                .setPositiveButton(Lang.t("削除"), (d, w) -> {
                    closeGroupPanelNow();
                    mutating = true;
                    for (int gid : ids) {
                        if (host.getGroup(gid) != null) host.onDeleteGroup(gid);
                    }
                    mutating = false;
                    if (showing) reloadAll();
                })
                .show();
    }

    /** 「タブグループ」の一覧で押した: 閉じていれば開き直してから、グループのパネルを出す。 */
    private void openFromGroupsPane(int gid) {
        TabGroup g = host.getGroup(gid);
        if (g == null) return;
        if (g.closed) {
            mutating = true;
            host.onOpenSavedGroup(gid);
            mutating = false;
            if (!showing) return;
        }
        setPane(PANE_TABS);
        reloadAll();
        openGroup(gid);
    }

    /** 一覧のグループのカードのメニュー(閉じる・名前を変更・グループを解除・削除)。 */
    private void showGroupCardMenu(int gid, View anchor) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        Menu m = popup.getMenu();
        m.add(0, 1, 0, Lang.t("閉じる")).setIcon(R.drawable.ic_tg_close);
        m.add(0, 2, 1, Lang.t("名前を変更")).setIcon(R.drawable.ic_tg_edit);
        m.add(0, 3, 2, Lang.t("グループを解除")).setIcon(R.drawable.ic_tg_ungroup);
        m.add(0, 4, 3, Lang.t("削除")).setIcon(R.drawable.ic_tg_delete);
        forceIcons(popup);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: closeGroup(gid); break;
                case 2: renameGroup(gid); break;
                case 3: ungroup(gid); break;
                case 4: confirmDeleteGroup(gid); break;
            }
            return true;
        });
        popup.show();
    }

    /** 「タブグループ」の一覧の行のメニュー(開く・削除)。 */
    private void showGroupRowMenu(int gid, View anchor) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        Menu m = popup.getMenu();
        m.add(0, 1, 0, Lang.t("開く")).setIcon(R.drawable.ic_tg_open);
        m.add(0, 2, 1, Lang.t("名前を変更")).setIcon(R.drawable.ic_tg_edit);
        m.add(0, 3, 2, Lang.t("削除")).setIcon(R.drawable.ic_tg_delete);
        forceIcons(popup);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: openFromGroupsPane(gid); break;
                case 2: renameGroup(gid); break;
                case 3: confirmDeleteGroup(gid); break;
            }
            return true;
        });
        popup.show();
    }

    private void forceIcons(PopupMenu popup) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) popup.setForceShowIcon(true);
        for (int i = 0; i < popup.getMenu().size(); i++) {
            android.graphics.drawable.Drawable icon = popup.getMenu().getItem(i).getIcon();
            if (icon != null) {
                icon.mutate().setTint(MaterialColors.getColor(activity,
                        com.google.android.material.R.attr.colorOnSurface, 0xFF1B1B1F));
            }
        }
    }

    // ---- グループのパネル ----

    private void setupGroupPanel() {
        root.findViewById(R.id.tsGroupScrim).setOnClickListener(v -> {
            if (!animating) closeGroupPanel(true);
        });
        root.findViewById(R.id.tsGroupBack).setOnClickListener(v -> {
            if (!animating) closeGroupPanel(true);
        });
        root.findViewById(R.id.tsGroupAdd).setOnClickListener(v -> {
            if (animating || openGroupId < 0) return;
            int gid = openGroupId;
            commitGroupTitle();
            hideKeyboard();
            animating = true;
            fadeContent(1f, 0f, () -> {
                hideNow();
                host.onNewTabInGroup(gid);
            });
        });
        root.findViewById(R.id.tsGroupMenu).setOnClickListener(this::showGroupPanelMenu);
        root.findViewById(R.id.tsInactiveCloseAll).setOnClickListener(v -> {
            if (!animating) confirmCloseInactive();
        });
        groupDot.setOnClickListener(v -> showGroupPalette(true));
        groupTitle.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitGroupTitle();
                hideKeyboard();
                groupTitle.clearFocus();
                return true;
            }
            return false;
        });
        groupTitle.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commitGroupTitle();
        });
    }

    /** グループのパネル・非アクティブなタブのパネルのどちらかを開いている。 */
    private boolean panelOpen() {
        return openGroupId >= 0 || inactiveOpen;
    }

    /** パネルに並べたカードの操作。 */
    private TabSwitcherAdapter.Callbacks panelCallbacks() {
        return new TabSwitcherAdapter.Callbacks() {
            @Override
            public void onCardClicked(int tabIndex) {
                if (!animating) selectTab(tabIndex, true);
            }

            @Override
            public void onCardClose(int adapterPosition) {
                if (!animating) closeAt(groupAdapter, adapterPosition);
            }

            @Override
            public void onGroupClicked(int groupId) {
                // 非アクティブなタブのパネルのグループ: そのグループのパネルに切り替える
                if (!animating) openGroup(groupId);
            }

            @Override
            public void onGroupMenu(int groupId, @NonNull View anchor) {
                if (!animating) showGroupCardMenu(groupId, anchor);
            }
        };
    }

    /** グループのタブだけを並べたパネルを開く。 */
    private void openGroup(int gid) {
        TabGroup g = host.getGroup(gid);
        if (g == null || g.closed || host.countTabsInGroup(gid) == 0) return;
        if (pane != PANE_TABS) setPane(PANE_TABS);
        hideKeyboard();
        closeGroupPanelNow();
        openGroupId = gid;
        groupAdapter = new TabSwitcherAdapter(host, palette, thumbCache, gid, panelCallbacks());
        groupGrid.setAdapter(groupAdapter);
        groupAdapter.reload();
        int current = groupAdapter.positionForTab(host.getCurrentTabIndex());
        if (current >= 0) groupLayoutManager.scrollToPosition(current);
        showGroupPalette(false);
        applyPanelMode();
        updateGroupPanelHeader();
        animatePanelIn();
    }

    /** 非アクティブなタブ・グループを並べたパネルを開く(一番下に「すべて閉じる」)。 */
    private void openInactivePanel() {
        if (host.isSecret()) return;
        if (pane != PANE_TABS) setPane(PANE_TABS);
        hideKeyboard();
        closeGroupPanelNow();
        inactiveOpen = true;
        groupAdapter = new TabSwitcherAdapter(host, palette, thumbCache,
                TabSwitcherAdapter.FILTER_INACTIVE, panelCallbacks());
        groupGrid.setAdapter(groupAdapter);
        groupAdapter.reload();
        if (groupAdapter.getItemCount() == 0) {
            closeGroupPanelNow();
            return;
        }
        groupLayoutManager.scrollToPosition(0);
        showGroupPalette(false);
        applyPanelMode();
        updateGroupPanelHeader();
        animatePanelIn();
    }

    /** グループのパネルと非アクティブなタブのパネルで、見出しとボタンを切り替える。 */
    private void applyPanelMode() {
        boolean inactive = inactiveOpen;
        groupDot.setVisibility(inactive ? View.GONE : View.VISIBLE);
        groupTitle.setFocusable(!inactive);
        groupTitle.setFocusableInTouchMode(!inactive);
        groupTitle.setCursorVisible(!inactive);
        groupTitle.setLongClickable(!inactive);
        groupTitleLine.setVisibility(inactive ? View.INVISIBLE : View.VISIBLE);
        root.findViewById(R.id.tsGroupAdd).setVisibility(inactive ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.tsGroupMenu).setContentDescription(inactive
                ? Lang.t("非アクティブなタブのメニュー") : Lang.t("タブグループのメニュー"));
        root.findViewById(R.id.tsGroupBack).setContentDescription(inactive
                ? Lang.t("非アクティブなタブを閉じる") : Lang.t("タブグループを閉じる"));
        inactiveFooter.setVisibility(inactive ? View.VISIBLE : View.GONE);
    }

    private void animatePanelIn() {
        float d = activity.getResources().getDisplayMetrics().density;
        groupOverlay.setVisibility(View.VISIBLE);
        groupOverlay.setAlpha(0f);
        groupPanel.setScaleX(0.94f);
        groupPanel.setScaleY(0.94f);
        groupPanel.setTranslationY(24 * d);
        groupOverlay.animate().alpha(1f).setDuration(200).start();
        groupPanel.animate().scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(260).setInterpolator(EMPHASIZED).start();
    }

    private void closeGroupPanel(boolean animate) {
        if (!panelOpen()) return;
        commitGroupTitle();
        hideKeyboard();
        openGroupId = -1;
        inactiveOpen = false;
        if (!animate) {
            finishCloseGroupPanel();
        } else {
            float d = activity.getResources().getDisplayMetrics().density;
            groupPanel.animate().scaleX(0.96f).scaleY(0.96f).translationY(16 * d)
                    .setDuration(180).setInterpolator(EMPHASIZED).start();
            groupOverlay.animate().alpha(0f).setDuration(180)
                    .withEndAction(this::finishCloseGroupPanel).start();
        }
        // グループのカード(名前・タブの数)を最新にする
        adapter.reload();
        updateEmptyView();
    }

    /** アニメーション無しでパネルを片付ける。 */
    private void closeGroupPanelNow() {
        if (openGroupId >= 0) {
            commitGroupTitle();
            openGroupId = -1;
        }
        inactiveOpen = false;
        finishCloseGroupPanel();
    }

    private void finishCloseGroupPanel() {
        if (panelOpen()) return; // 閉じる途中で別のグループを開いた
        groupOverlay.animate().cancel();
        groupPanel.animate().cancel();
        groupOverlay.setVisibility(View.GONE);
        groupOverlay.setAlpha(1f);
        groupPanel.setScaleX(1f);
        groupPanel.setScaleY(1f);
        groupPanel.setTranslationY(0f);
        if (groupAdapter != null) {
            groupAdapter.release();
            groupAdapter = null;
            groupGrid.setAdapter(null);
        }
    }

    /** タブを閉じた後などに、開いているパネルを最新にする(グループが無くなったら閉じる)。 */
    private void refreshGroupPanel() {
        if (!panelOpen() || groupAdapter == null) return;
        if (inactiveOpen) {
            groupAdapter.reload();
            if (groupAdapter.getItemCount() == 0) {
                closeGroupPanel(true);
            } else {
                updateGroupPanelHeader();
            }
            return;
        }
        TabGroup g = host.getGroup(openGroupId);
        if (g == null || g.closed || host.countTabsInGroup(openGroupId) == 0) {
            closeGroupPanel(true);
            return;
        }
        groupAdapter.reload();
        updateGroupPanelHeader();
    }

    private void updateGroupPanelHeader() {
        if (inactiveOpen) {
            groupTitle.setText(Lang.t("非アクティブなタブ"));
            groupTitle.setHint("");
            return;
        }
        if (openGroupId < 0) return;
        TabGroup g = host.getGroup(openGroupId);
        if (g == null) return;
        groupDot.setBackground(TabGroupUi.dot(TabGroup.colorValue(activity, g.color, host.isSecret())));
        // 名前が無ければ「N 個のタブ」をそのまま名前のように見せる(押すと名前を入力できる)
        groupTitle.setHint(host.countTabsInGroup(openGroupId) + Lang.t(" 個のタブ"));
        if (!groupTitle.hasFocus() && !groupTitle.getText().toString().equals(g.title)) {
            groupTitle.setText(g.title);
        }
    }

    /** パネルで書き換えた名前を保存する。 */
    private void commitGroupTitle() {
        if (openGroupId < 0) return;
        TabGroup g = host.getGroup(openGroupId);
        if (g == null) return;
        String t = groupTitle.getText().toString().trim();
        if (t.equals(g.title)) return;
        g.title = t;
        g.touch();
        host.onGroupChanged(g.id);
    }

    private void showGroupPalette(boolean show) {
        if (show && openGroupId >= 0) {
            TabGroup g = host.getGroup(openGroupId);
            if (g == null) return;
            hideKeyboard();
            groupPalette.removeAllViews();
            groupPalette.addView(TabGroupUi.colorPicker(activity, g.color, palette.background, host.isSecret(),
                    idx -> {
                        g.color = idx;
                        g.touch();
                        host.onGroupChanged(g.id);
                        updateGroupPanelHeader();
                        showGroupPalette(false);
                        adapter.notifyDataSetChanged();
                    }));
        }
        groupPalette.setVisibility(show ? View.VISIBLE : View.GONE);
        groupTitleRow.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    /** 非アクティブなタブのパネルのメニュー(すべて閉じる・設定)。 */
    private void showInactivePanelMenu(View anchor) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        Menu m = popup.getMenu();
        m.add(0, 1, 0, Lang.t("すべて閉じる")).setIcon(R.drawable.ic_tg_close);
        m.add(0, 2, 1, Lang.t("非アクティブなタブの設定")).setIcon(R.drawable.ic_menu_settings);
        forceIcons(popup);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                confirmCloseInactive();
            } else {
                InactiveTabsSettingsActivity.open(activity);
            }
            return true;
        });
        popup.show();
    }

    /** 非アクティブなタブをすべて閉じる(確認してから)。 */
    private void confirmCloseInactive() {
        if (!inactiveOpen || groupAdapter == null) return;
        int n = groupAdapter.inactiveEntryCount();
        new MaterialAlertDialogBuilder(activity)
                .setTitle(Lang.t("非アクティブなタブをすべて閉じますか？"))
                .setMessage(n + Lang.t(" 個のタブとグループを閉じます。グループは「タブグループ」に保存されます。"))
                .setNegativeButton(Lang.t("キャンセル"), null)
                .setPositiveButton(Lang.t("すべて閉じる"), (d, w) -> {
                    if (!showing) return;
                    closeGroupPanel(true);
                    mutating = true;
                    host.onCloseInactiveTabs();
                    mutating = false;
                    if (showing) reloadAll();
                })
                .show();
    }

    /** パネルのメニュー(グループ名を編集・グループの色を編集・グループを閉じる・グループを削除)。 */
    private void showGroupPanelMenu(View anchor) {
        if (animating) return;
        if (inactiveOpen) {
            showInactivePanelMenu(anchor);
            return;
        }
        if (openGroupId < 0) return;
        int gid = openGroupId;
        PopupMenu popup = new PopupMenu(activity, anchor);
        Menu m = popup.getMenu();
        m.add(0, 1, 0, Lang.t("グループ名を編集")).setIcon(R.drawable.ic_tg_edit);
        m.add(0, 2, 1, Lang.t("グループの色を編集")).setIcon(R.drawable.ic_tg_color);
        m.add(0, 3, 2, Lang.t("グループを解除")).setIcon(R.drawable.ic_tg_ungroup);
        m.add(0, 4, 3, Lang.t("グループを閉じる")).setIcon(R.drawable.ic_tg_close);
        m.add(0, 5, 4, Lang.t("グループを削除")).setIcon(R.drawable.ic_tg_delete);
        forceIcons(popup);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    showGroupPalette(false);
                    groupTitle.requestFocus();
                    groupTitle.selectAll();
                    InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(groupTitle, InputMethodManager.SHOW_IMPLICIT);
                    break;
                case 2:
                    showGroupPalette(true);
                    break;
                case 3:
                    ungroup(gid);
                    break;
                case 4:
                    closeGroup(gid);
                    break;
                case 5:
                    confirmDeleteGroup(gid);
                    break;
            }
            return true;
        });
        popup.show();
    }

    // ---- 見た目 ----

    private void applyPalette() {
        Palette p = palette;
        float d = activity.getResources().getDisplayMetrics().density;

        background.setBackgroundColor(p.background);
        root.findViewById(R.id.tsDivider).setBackgroundColor(p.divider);

        ImageButton newTab = root.findViewById(R.id.tsNewTab);
        newTab.setBackground(rounded(p.accent, 16 * d));
        newTab.setImageTintList(ColorStateList.valueOf(p.onAccent));

        ImageButton menu = root.findViewById(R.id.tsMenu);
        menu.setBackground(rounded(p.iconButton, 24 * d));
        menu.setImageTintList(ColorStateList.valueOf(p.onBackground));

        root.findViewById(R.id.tsSegment).setBackground(rounded(p.segment, 24 * d));
        boolean secret = host.isSecret();
        segNormal.setBackground(!secret && pane == PANE_TABS ? rounded(p.segmentSelected, 20 * d) : null);
        segGroups.setBackground(!secret && pane == PANE_GROUPS ? rounded(p.segmentSelected, 20 * d) : null);
        segSecret.setBackground(secret ? rounded(p.segmentSelected, 20 * d) : null);

        // タブ数アイコン(角丸の四角の中に数字)
        GradientDrawable countBox = new GradientDrawable();
        countBox.setCornerRadius(6 * d);
        countBox.setStroke(Math.round(2 * d), p.onSegment);
        tabCount.setBackground(countBox);
        tabCount.setTextColor(p.onSegment);
        ((ImageView) root.findViewById(R.id.tsSecretIcon))
                .setImageTintList(ColorStateList.valueOf(p.onSegment));
        ((ImageView) root.findViewById(R.id.tsGroupsIcon))
                .setImageTintList(ColorStateList.valueOf(p.onSegment));

        root.findViewById(R.id.tsSearchBar).setBackground(rounded(p.search, 26 * d));
        searchField.setTextColor(p.onSearch);
        searchField.setHintTextColor(p.hint);
        searchClear.setImageTintList(ColorStateList.valueOf(p.hint));
        ((ImageView) root.findViewById(R.id.tsSearchIcon))
                .setImageTintList(ColorStateList.valueOf(p.hint));
        emptyView.setTextColor(p.hint);
        emptyArt.setColors(p.card, p.hint, p.accent);

        // グループのパネル
        groupPanel.setBackground(rounded(p.background, 28 * d));
        groupTitle.setTextColor(p.onBackground);
        groupTitle.setHintTextColor(p.onBackground);
        groupTitleLine.setBackgroundColor(p.divider);
        ColorStateList icon = ColorStateList.valueOf(p.onBackground);
        ((ImageButton) root.findViewById(R.id.tsGroupBack)).setImageTintList(icon);
        ((ImageButton) root.findViewById(R.id.tsGroupAdd)).setImageTintList(icon);
        ((ImageButton) root.findViewById(R.id.tsGroupMenu)).setImageTintList(icon);
        com.google.android.material.button.MaterialButton closeAll = root.findViewById(R.id.tsInactiveCloseAll);
        closeAll.setBackgroundTintList(ColorStateList.valueOf(p.iconButton));
        closeAll.setTextColor(p.onBackground);
        closeAll.setIconTint(ColorStateList.valueOf(p.onBackground));
        ((TextView) dropHint).setTextColor(p.onAccent);
        dropHint.setBackground(rounded(p.accent, 20 * d));

        adapter.setPalette(p);
        groupListAdapter.setPalette(p);
        if (groupAdapter != null) groupAdapter.setPalette(p);
    }

    private void updateCount() {
        int n = host.isSecret() ? host.getOtherModeTabCount() : host.getTabCount();
        tabCount.setText(n < 0 ? "" : n > 99 ? ":D" : String.valueOf(n));
    }

    private void updateEmptyView() {
        boolean filtering = searchField.length() > 0;
        boolean empty;
        boolean art;
        if (pane == PANE_GROUPS) {
            emptyView.setText(filtering ? Lang.t("一致するタブグループはありません")
                    : Lang.t("タブグループはありません\n\nメニューの「タブをグループにまとめる」で、"
                    + "タブに名前と色を付けてまとめられます"));
            empty = groupListAdapter.getItemCount() == 0;
            art = !filtering;
        } else {
            emptyView.setText(Lang.t("一致するタブはありません"));
            empty = adapter.getItemCount() == 0;
            art = false;
        }
        emptyArt.setVisibility(art ? View.VISIBLE : View.GONE);
        boolean wasShown = emptyBox.getVisibility() == View.VISIBLE;
        emptyBox.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty && !wasShown) {
            // ふわっと浮かび上がるように出す
            float d = emptyBox.getResources().getDisplayMetrics().density;
            emptyBox.setAlpha(0f);
            emptyBox.setTranslationY(16 * d);
            emptyBox.setScaleX(0.96f);
            emptyBox.setScaleY(0.96f);
            emptyBox.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(380).setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
                    .start();
        }
    }

    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    // ---- アニメーション補助 ----

    private interface HolderCallback {
        void onHolder(@Nullable RecyclerView.ViewHolder holder);
    }

    /**
     * position のカードがレイアウトされたら呼ぶ。画面からはみ出ていたら中央付近までスクロールしてから。
     */
    private void whenCardLaidOut(int position, HolderCallback callback) {
        grid.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            int attempts = 0;

            @Override
            public boolean onPreDraw() {
                attempts++;
                RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(position);
                if (h != null && attempts < 3 && !fullyVisible(h.itemView)) {
                    int offset = Math.max(0, (grid.getHeight() - h.itemView.getHeight()) / 2);
                    layoutManager.scrollToPositionWithOffset(position, offset);
                    return false; // 次のレイアウト後にもう一度
                }
                if (h == null && attempts < 3) {
                    return false;
                }
                grid.getViewTreeObserver().removeOnPreDrawListener(this);
                callback.onHolder(h);
                return true;
            }
        });
        grid.requestLayout();
    }

    private boolean fullyVisible(View v) {
        return v.getTop() >= 0 && v.getBottom() <= grid.getHeight();
    }

    /** 背景と、一覧(と開いているグループのパネル)の透明度。 */
    private void setContentAlpha(float backgroundAlpha, float contentAlpha) {
        background.setAlpha(backgroundAlpha);
        content.setAlpha(contentAlpha);
        groupOverlay.setAlpha(contentAlpha);
    }

    private void fadeContent(float from, float to, Runnable end) {
        cancelAnimation();
        animating = true;
        ValueAnimator va = ValueAnimator.ofFloat(from, to);
        va.setDuration(200);
        va.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            setContentAlpha(f, f);
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                runningAnimator = null;
                animating = false;
                end.run();
            }
        });
        runningAnimator = va;
        va.start();
    }

    private void cancelAnimation() {
        if (runningAnimator != null) {
            Animator a = runningAnimator;
            runningAnimator = null;
            a.removeAllListeners();
            a.cancel();
        }
        if (transitionView != null) transitionView.animate().cancel();
    }

    private TabTransitionView ensureTransitionView() {
        if (transitionView == null) {
            transitionView = new TabTransitionView(activity);
            root.addView(transitionView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        transitionView.setAlpha(1f);
        transitionView.bringToFront();
        return transitionView;
    }

    private void removeTransitionView() {
        if (transitionView != null) {
            transitionView.animate().cancel();
            root.removeView(transitionView);
            transitionView = null;
        }
    }

    /** ページ表示領域の、この一覧の座標系での位置。 */
    private RectF contentRect() {
        View c = host.getContentView();
        if (c == null || c.getWidth() == 0) {
            return new RectF(0, 0, root.getWidth(), root.getHeight());
        }
        return viewRect(c);
    }

    private RectF viewRect(View v) {
        int[] a = new int[2];
        int[] b = new int[2];
        v.getLocationInWindow(a);
        root.getLocationInWindow(b);
        float left = a[0] - b[0];
        float top = a[1] - b[1];
        return new RectF(left, top, left + v.getWidth(), top + v.getHeight());
    }

    private float radiusOf(View v) {
        if (v instanceof TopCropImageView) return ((TopCropImageView) v).getCornerRadius();
        return 14 * activity.getResources().getDisplayMetrics().density;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
        }
        searchField.clearFocus();
        groupTitle.clearFocus();
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    // ---- スワイプで閉じる / 長押しドラッグで並べ替え ----

    /** 長押しで持ち上げたカードの拡大率。 */
    private static final float DRAG_SCALE = 1.05f;

    /**
     * カードの拡大率をアニメーションで変える。RecyclerView の ItemAnimator に止められないよう、
     * ViewPropertyAnimator(view.animate())ではなく ObjectAnimator を使い、ビューごとに 1 つだけ動かす。
     */
    private static void animateCardScale(View view, float target, long duration) {
        Object running = view.getTag(R.id.tsCardScaleAnimator);
        if (running instanceof Animator) ((Animator) running).cancel();
        if (view.getScaleX() == target && view.getScaleY() == target) {
            view.setTag(R.id.tsCardScaleAnimator, null);
            return;
        }
        ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, target),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, target));
        anim.setDuration(duration);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (view.getTag(R.id.tsCardScaleAnimator) == animation) {
                    view.setTag(R.id.tsCardScaleAnimator, null);
                }
            }
        });
        view.setTag(R.id.tsCardScaleAnimator, anim);
        anim.start();
    }

    /** 重ねたカードとみなす範囲(カードの縁からこの割合より内側に、ドラッグしたカードの中心がある)。 */
    private static final float MERGE_INSET = 0.22f;
    /** 重ねてグループにする先のカードの縮小率。 */
    private static final float MERGE_TARGET_SCALE = 0.9f;

    /** ドラッグしているカードの中心が、target の中央付近に重なっているか。 */
    private static boolean overCenter(View dragged, View target) {
        float cx = dragged.getX() + dragged.getWidth() / 2f;
        float cy = dragged.getY() + dragged.getHeight() / 2f;
        float ix = target.getWidth() * MERGE_INSET;
        float iy = target.getHeight() * MERGE_INSET;
        float left = target.getX();
        float top = target.getY();
        return cx > left + ix && cx < left + target.getWidth() - ix
                && cy > top + iy && cy < top + target.getHeight() - iy;
    }

    /** タブ ID の今の index。無ければ -1。 */
    private int indexOfTabId(int id) {
        for (int i = 0; i < host.getTabCount(); i++) {
            if (host.getTabId(i) == id) return i;
        }
        return -1;
    }

    private List<Integer> indicesOf(List<Integer> ids) {
        List<Integer> list = new ArrayList<>();
        for (int id : ids) {
            int i = indexOfTabId(id);
            if (i >= 0) list.add(i);
        }
        return list;
    }

    /**
     * 一覧でカードを別のカードに重ねて離した。タブ同士なら新しいグループを作り、
     * どちらかがグループならそのグループにまとめる。
     * @param reordered 重ねる前にドラッグで並びを変えていた(先に確定する)
     */
    private void mergeCards(int fromPos, int toPos, boolean reordered) {
        int draggedGroup = adapter.groupIdAt(fromPos);
        int targetGroup = adapter.groupIdAt(toPos);
        int draggedTab = adapter.tabIndexAt(fromPos);
        int targetTab = adapter.tabIndexAt(toPos);
        if ((draggedGroup < 0 && draggedTab < 0) || (targetGroup < 0 && targetTab < 0)) return;
        if (draggedGroup >= 0 && draggedGroup == targetGroup) return;

        // 並べ替えで index が変わるので、タブ ID で覚えておく
        List<Integer> draggedIds = new ArrayList<>();
        if (draggedGroup >= 0) {
            for (int i : host.getTabsInGroup(draggedGroup)) draggedIds.add(host.getTabId(i));
        } else {
            draggedIds.add(host.getTabId(draggedTab));
        }
        int targetId = targetTab >= 0 ? host.getTabId(targetTab) : -1;

        mutating = true;
        if (reordered) {
            List<Integer> order = adapter.computeTabOrder();
            if (order != null) host.onReorderTabs(order);
        }
        if (targetGroup >= 0) {
            // グループに重ねた: ドラッグしたタブ(グループならその全部のタブ)をグループに入れる
            host.onMoveTabsToGroup(indicesOf(draggedIds), targetGroup);
        } else if (draggedGroup >= 0) {
            // グループをタブに重ねた: そのタブをグループに入れる
            List<Integer> one = new ArrayList<>();
            one.add(targetId);
            host.onMoveTabsToGroup(indicesOf(one), draggedGroup);
        } else {
            // タブ同士: 重ねた先のタブ・ドラッグしたタブの順で新しいグループにする
            List<Integer> ids = new ArrayList<>();
            ids.add(targetId);
            ids.addAll(draggedIds);
            host.onCreateGroup(indicesOf(ids));
        }
        mutating = false;
        if (showing) reloadAll();
    }

    /** グループのパネルからタブを外へドラッグして離した: グループから外して一覧に出す。 */
    private void removeFromOpenGroup(int position, boolean reordered) {
        if (groupAdapter == null) return;
        int tab = groupAdapter.tabIndexAt(position);
        if (tab < 0) return;
        int id = host.getTabId(tab);
        mutating = true;
        if (reordered) {
            List<Integer> order = groupAdapter.computeTabOrder();
            if (order != null) host.onReorderTabs(order);
        }
        int index = indexOfTabId(id);
        if (index >= 0) host.onRemoveTabFromGroup(index);
        mutating = false;
        if (showing) reloadAll();
    }

    private class TouchCallback extends ItemTouchHelper.Callback {

        /** グループのパネル用(false なら通常の一覧用)。 */
        private final boolean panel;
        /** ドラッグで並びを変えた(離した時に確定する)。 */
        private boolean moved;
        /** 重ねてグループにする先のカード(通常の一覧)。 */
        @Nullable private RecyclerView.ViewHolder mergeTarget;
        /** グループのパネルの外までドラッグしている。 */
        private boolean outside;

        TouchCallback(boolean panel) {
            this.panel = panel;
        }

        @Nullable
        private TabSwitcherAdapter target() {
            return panel ? groupAdapter : adapter;
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            TabSwitcherAdapter a = target();
            if (a == null) return 0;
            // 「非アクティブなタブ」のカードは動かさない
            if (a.isInactiveHeaderAt(vh.getBindingAdapterPosition())) return 0;
            int drag = a.isFiltering() || a.isInactiveList() ? 0
                    : ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
            int swipe = ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
            return makeMovementFlags(drag, swipe);
        }

        /** カードを重ねてグループにできるか(通常の一覧で、検索していない時)。 */
        private boolean canMerge() {
            return !panel && host.supportsGroups() && !host.isSecret() && !adapter.isFiltering();
        }

        /** グループのパネルからタブを外に出せるか。 */
        private boolean canDragOut() {
            return panel && openGroupId >= 0 && groupAdapter != null && !groupAdapter.isFiltering();
        }

        @Override
        public boolean canDropOver(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder current,
                                   @NonNull RecyclerView.ViewHolder target) {
            TabSwitcherAdapter a = target();
            if (a == null || a.isInactiveHeaderAt(target.getBindingAdapterPosition())) return false;
            // 中央に重ねている間は並べ替えない(そこで離すとグループになる。さらに進めると並べ替え)
            if (canMerge() && overCenter(current.itemView, target.itemView)) return false;
            return true;
        }

        private void setMergeTarget(@Nullable RecyclerView.ViewHolder h) {
            if (h == mergeTarget) return;
            if (mergeTarget != null) animateCardScale(mergeTarget.itemView, 1f, 150);
            mergeTarget = h;
            if (h != null) {
                animateCardScale(h.itemView, MERGE_TARGET_SCALE, 150);
                h.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            }
        }

        private void setOutside(boolean out) {
            if (out == outside) return;
            outside = out;
            dropHint.animate().cancel();
            dropHint.animate().alpha(out ? 1f : 0f).translationY(0f).setDuration(150).start();
            // パネルの地を薄くして、外に出したことが分かるようにする(ドラッグ中のカードは薄くしない)
            if (groupPanel.getBackground() != null) {
                groupPanel.getBackground().mutate().setAlpha(out ? 140 : 255);
            }
            if (out) groupPanel.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        }

        /** ドラッグしているカードの中心がグループのパネルの外にあるか。 */
        private boolean isOutsidePanel(View dragged) {
            int[] a = new int[2];
            int[] b = new int[2];
            dragged.getLocationInWindow(a);
            groupPanel.getLocationInWindow(b);
            float cx = a[0] + dragged.getWidth() * dragged.getScaleX() / 2f;
            float cy = a[1] + dragged.getHeight() * dragged.getScaleY() / 2f;
            return cx < b[0] || cx > b[0] + groupPanel.getWidth()
                    || cy < b[1] || cy > b[1] + groupPanel.getHeight();
        }

        @Override
        public long getAnimationDuration(@NonNull RecyclerView rv, int animationType, float animateDx, float animateDy) {
            // 重ねて離した・パネルの外で離した時は、元の位置へ戻る動きを見せずにすぐまとめる
            if (animationType == ItemTouchHelper.ANIMATION_TYPE_DRAG && (mergeTarget != null || outside)) {
                return 0;
            }
            return super.getAnimationDuration(rv, animationType, animateDx, animateDy);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return !animating;
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return !animating;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                              @NonNull RecyclerView.ViewHolder to) {
            TabSwitcherAdapter a = target();
            int f = from.getBindingAdapterPosition();
            int t = to.getBindingAdapterPosition();
            if (a == null || f < 0 || t < 0 || a.isFiltering()) return false;
            a.onEntryMoved(f, t);
            moved = true;
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
            int pos = vh.getBindingAdapterPosition();
            if (pos >= 0) closeAt(target(), pos);
        }

        @Override
        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder vh, int actionState) {
            super.onSelectedChanged(vh, actionState);
            if (vh != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                animateCardScale(vh.itemView, DRAG_SCALE, 150);
                vh.itemView.setElevation(8 * activity.getResources().getDisplayMetrics().density);
                if (canDragOut()) {
                    // パネルの外までカードを持ち出せるように、ドラッグ中だけはみ出して描く
                    groupGrid.setClipChildren(false);
                    ((ViewGroup) groupPanel).setClipChildren(false);
                    ((ViewGroup) groupOverlay).setClipChildren(false);
                }
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            super.clearView(rv, vh);
            vh.itemView.setElevation(0f);
            vh.itemView.setAlpha(1f);
            TabSwitcherAdapter a = target();

            RecyclerView.ViewHolder merge = mergeTarget;
            boolean out = outside;
            if (merge != null) {
                Object running = merge.itemView.getTag(R.id.tsCardScaleAnimator);
                if (running instanceof Animator) ((Animator) running).cancel();
                merge.itemView.setScaleX(1f);
                merge.itemView.setScaleY(1f);
                mergeTarget = null;
            }
            setOutside(false);
            if (panel) {
                groupGrid.setClipChildren(true);
                ((ViewGroup) groupPanel).setClipChildren(true);
                ((ViewGroup) groupOverlay).setClipChildren(true);
            }
            if (merge != null && a == adapter) {
                // 重ねて離した: グループにまとめる
                int from = vh.getBindingAdapterPosition();
                int to = merge.getBindingAdapterPosition();
                boolean reordered = moved;
                moved = false;
                vh.itemView.setScaleX(1f);
                vh.itemView.setScaleY(1f);
                if (from >= 0 && to >= 0) {
                    // 元の位置に一瞬戻って見えないよう、まとめ終わる(作り直す)まで隠しておく
                    vh.itemView.setAlpha(0f);
                    rv.post(() -> {
                        vh.itemView.setAlpha(1f);
                        if (showing) mergeCards(from, to, reordered);
                    });
                }
                return;
            }
            if (out && canDragOut()) {
                // グループのパネルの外で離した: グループから外す
                int pos = vh.getBindingAdapterPosition();
                boolean reordered = moved;
                moved = false;
                vh.itemView.setScaleX(1f);
                vh.itemView.setScaleY(1f);
                if (pos >= 0) rv.post(() -> {
                    if (showing) removeFromOpenGroup(pos, reordered);
                });
                return;
            }
            if (moved && a != null) {
                // 離した時点の並びをタブ全体の並びとして確定する
                moved = false;
                List<Integer> order = a.computeTabOrder();
                if (order != null) {
                    mutating = true;
                    host.onReorderTabs(order);
                    mutating = false;
                }
                // 並べ替え後の index で作り直す(ドロップのアニメーションが終わってから)
                rv.post(() -> {
                    if (!showing) return;
                    adapter.reload();
                    if (groupAdapter != null) groupAdapter.reload();
                });
            } else if (a != null) {
                a.refreshSelection();
            }
            // refreshSelection の変更アニメーションは itemView.animate() を cancel するので、
            // 拡大を戻すのは別の Animator で行う(以前は途中で止まって大きいまま残っていた)
            animateCardScale(vh.itemView, 1f, 200);
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                @NonNull RecyclerView.ViewHolder vh, float dX, float dY,
                                int actionState, boolean isCurrentlyActive) {
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                float w = vh.itemView.getWidth();
                vh.itemView.setAlpha(clamp(1f - Math.abs(dX) / (w * 0.9f)));
            }
            super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || !isCurrentlyActive) return;
            if (canMerge()) {
                // 中央に重なっているカードを探す(そこで離すとグループになる)
                RecyclerView.ViewHolder found = null;
                for (int i = 0; i < rv.getChildCount(); i++) {
                    View child = rv.getChildAt(i);
                    if (child == vh.itemView) continue;
                    RecyclerView.ViewHolder h = rv.getChildViewHolder(child);
                    if (h == null || adapter.isInactiveHeaderAt(h.getBindingAdapterPosition())) continue;
                    if (h.getBindingAdapterPosition() == RecyclerView.NO_POSITION) continue;
                    if (overCenter(vh.itemView, child)) {
                        found = h;
                        break;
                    }
                }
                // 同じグループには重ねられない
                if (found != null) {
                    int from = adapter.groupIdAt(vh.getBindingAdapterPosition());
                    int to = adapter.groupIdAt(found.getBindingAdapterPosition());
                    if (from >= 0 && from == to) found = null;
                }
                setMergeTarget(found);
            } else if (canDragOut()) {
                setOutside(isOutsidePanel(vh.itemView));
            }
        }
    }
}
