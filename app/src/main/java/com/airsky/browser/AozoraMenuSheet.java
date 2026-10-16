package com.airsky.browser;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;

import java.util.ArrayList;
import java.util.List;

/**
 * ボトムバーの Aozora ボタンで開くメニュー(Pixel の設定パネル風)。
 *
 * <p>上によく使う画面へのタイル、下に設定などの行を並べる。
 * 開く時は、シートがばねで持ち上がり、タイルが Aozora ボタンのある右下から順に「咲く」ように広がる。
 * 下へスワイプ・外側のタップ・戻るキーで閉じる。</p>
 */
final class AozoraMenuSheet {

    /** メニューの 1 項目。 */
    static final class Item {
        @DrawableRes final int icon;
        final String label;
        final Runnable action;
        /** 押すとメニューの中で開く一覧(ツールなど)。null なら action を実行して閉じる */
        @Nullable final List<Item> children;

        Item(@DrawableRes int icon, @NonNull String label, @NonNull Runnable action) {
            this.icon = icon;
            this.label = label;
            this.action = action;
            this.children = null;
        }

        /** 押すとメニューを閉じずに、中で children の一覧へ切り替わる項目 */
        Item(@DrawableRes int icon, @NonNull String label, @NonNull List<Item> children) {
            this(icon, label, null, children);
        }

        /**
         * 中の一覧の上に、大きなカード(big)を並べる項目(コンソール: Java / Python と、その下に追加したプログラム)。
         */
        Item(@DrawableRes int icon, @NonNull String label, @Nullable List<Item> big, @NonNull List<Item> children) {
            this.icon = icon;
            this.label = label;
            this.action = () -> {};
            this.children = children;
            this.big = big;
        }

        /** 大きなカード(big)の上に並べるもの */
        @Nullable List<Item> big;
        /** 大きなカードの説明 */
        @Nullable String subtitle;
        /** 中の一覧の、小さなタイルの見出し */
        @Nullable String childrenTitle;
        /** 小さなタイルが 1 つも無い時の案内 */
        @Nullable String emptyText;
        /** タイルのアイコンの下地の色(0 なら順番に決める) */
        int toneBg, toneFg;

        Item subtitle(@NonNull String s) {
            subtitle = s;
            return this;
        }

        Item tone(int bg, int fg) {
            toneBg = bg;
            toneFg = fg;
            return this;
        }

        Item childrenTitle(@NonNull String title, @NonNull String empty) {
            childrenTitle = title;
            emptyText = empty;
            return this;
        }

        /** 長押しの動き(ツールの一覧のタイルだけ)。remove を呼ぶと一覧からこの項目を消す。 */
        @Nullable LongPress longPress;

        Item onLongPress(@NonNull LongPress l) {
            longPress = l;
            return this;
        }
    }

    interface LongPress {
        void onLongPress(@NonNull Runnable remove);
    }

    private static final int SUB_COLUMNS = 4;

    private static final int COLUMNS = 3;

    private final Activity activity;
    private final BottomSheetDialog dialog;
    private final View sheet;
    private final View scrim;
    private boolean closing;
    /** ツール一覧などの中の一覧を開いている */
    private boolean inSubPage;
    private boolean switching;
    private final OnBackPressedCallback subPageBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            closeSubPage();
        }
    };

    private AozoraMenuSheet(Activity activity, boolean secret, List<Item> tiles, List<Item> rows,
                            boolean extensions) {
        this.activity = activity;
        // 二画面の時は、ボトムバーのある下の画面に出す
        dialog = DualScreenController.newBottomSheet(activity);
        sheet = LayoutInflater.from(dialog.getContext()).inflate(R.layout.sheet_aozora_menu, null);
        dialog.setContentView(SheetScroll.wrap(sheet)); // 横画面でも下までスクロールできるように
        dialog.setCanceledOnTouchOutside(true);
        // 外側のタップ・戻るキーでも、シートを下へ滑らせてから閉じる
        dialog.setDismissWithAnimation(true);

        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setDraggable(true);
        behavior.setSkipCollapsed(true); // 下へスワイプしたら途中で止まらずに閉じる
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        // 画面を暗くするのはウィンドウではなく自前の幕で行い、シートの位置に合わせて濃さを変える
        Window window = dialog.getWindow();
        scrim = dialog.findViewById(com.google.android.material.R.id.touch_outside);
        if (window != null && scrim != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setWindowAnimations(0);
            scrim.setBackgroundColor(Color.argb(110, 0, 0, 0));
            scrim.setAlpha(0f);
        }
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {}

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // 下へ引くほど幕を薄くする(-1 で完全に隠れる)
                if (scrim != null && !closing) scrim.setAlpha(Math.max(0f, Math.min(1f, 1f + slideOffset)));
            }
        });

        bindHeader(secret);
        bindTiles(tiles);
        bindRows(rows);
        if (extensions) bindExtensions(secret);
        dialog.setOnShowListener(d -> playEnter());
        // 中の一覧を開いている時は、戻るでメニュー本体へ戻る
        dialog.getOnBackPressedDispatcher().addCallback(subPageBack);
        sheet.findViewById(R.id.am_back).setOnClickListener(v -> closeSubPage());
    }

    static BottomSheetDialog show(@NonNull Activity activity, boolean secret,
                                  @NonNull List<Item> tiles, @NonNull List<Item> rows) {
        return show(activity, secret, tiles, rows, false);
    }

    /** @param extensions 「拡張機能」の項目(入っている拡張機能の一覧と管理画面への入口)を出す */
    static BottomSheetDialog show(@NonNull Activity activity, boolean secret,
                                  @NonNull List<Item> tiles, @NonNull List<Item> rows, boolean extensions) {
        AozoraMenuSheet s = new AozoraMenuSheet(activity, secret, tiles, rows, extensions);
        s.dialog.show();
        return s.dialog;
    }

    // ------------------------------------------------------------------
    // 拡張機能
    // ------------------------------------------------------------------

    private static final String PREF_EXT_EXPANDED = "menu_extensions_expanded";

    private void bindExtensions(boolean secret) {
        LinearLayout box = sheet.findViewById(R.id.am_extensions);
        box.setVisibility(View.VISIBLE);
        // 開閉の動きは箱の中だけにする。親(シート)まで動かすと、BottomSheetBehavior が位置をずらす前の
        // 一時的な位置(画面の一番上)へ向けてシートが動き、メニューが上へ飛んでしまう
        android.animation.LayoutTransition transition = box.getLayoutTransition();
        if (transition != null) transition.setAnimateParentHierarchy(false);
        ExtensionManager manager = ExtensionManager.get(activity);
        // シートを開いている間にバッジの数などが変わったら描き直す
        ExtensionManager.Listener listener = () -> {
            if (dialog.isShowing() && !closing) fillExtensions(box, manager, secret);
        };
        manager.addListener(listener);
        // ダイアログの dismiss リスナーは呼び出し側が使うので、ウィンドウから外れた時に後片付けする
        box.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(@NonNull View v) {}

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                manager.removeListener(listener);
            }
        });
        fillExtensions(box, manager, secret);
    }

    private void fillExtensions(LinearLayout box, ExtensionManager manager, boolean secret) {
        box.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(sheet.getContext());
        List<WebExtension> list = new ArrayList<>();
        for (WebExtension e : manager.getInstalled()) {
            if (!ExtensionManager.isEnabled(e)) continue;
            if (secret && !e.metaData.allowedInPrivateBrowsing) continue;
            list.add(e);
        }
        SharedPreferences pref = activity.getSharedPreferences("AppPrefs", Activity.MODE_PRIVATE);
        boolean expanded = pref.getBoolean(PREF_EXT_EXPANDED, true) && !list.isEmpty();
        GeckoSession session = manager.currentSession();
        int onVariant = MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnSurfaceVariant);

        View header = inflater.inflate(R.layout.item_menu_extension_header, box, false);
        ((ImageView) header.findViewById(R.id.mxIcon)).setImageTintList(ColorStateList.valueOf(onVariant));
        TextView summary = header.findViewById(R.id.mxSummary);
        View chip = header.findViewById(R.id.mxChip);
        if (list.isEmpty()) {
            summary.setText(secret ? Lang.t("シークレットで使える拡張機能はありません") : Lang.t("広告ブロックなどの機能を追加できます"));
            chip.setVisibility(View.GONE);
            header.setOnClickListener(v -> choose(v, new Item(0, "", this::openManager)));
        } else {
            StringBuilder names = new StringBuilder();
            for (WebExtension e : list) {
                if (names.length() > 0) names.append(Lang.t("、"));
                names.append(ExtensionUi.name(e));
                WebExtension.Action a = manager.actionFor(e, session);
                if (a != null && a.badgeText != null && !a.badgeText.isEmpty()) {
                    names.append(" (").append(a.badgeText).append(')');
                }
            }
            summary.setText(names);
            ((TextView) header.findViewById(R.id.mxCount)).setText(String.valueOf(list.size()));
            View arrow = header.findViewById(R.id.mxArrow);
            arrow.setRotation(expanded ? 180f : 0f);
            header.setOnClickListener(v -> {
                boolean now = !pref.getBoolean(PREF_EXT_EXPANDED, true);
                pref.edit().putBoolean(PREF_EXT_EXPANDED, now).apply();
                arrow.animate().rotation(now ? 180f : 0f).setDuration(320)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
                animateExtensionHeader(header, now);
                List<View> targets = new ArrayList<>();
                for (int i = 1; i < box.getChildCount(); i++) {
                    View c = box.getChildAt(i);
                    if (Boolean.TRUE.equals(c.getTag(R.id.ext_icon_tag))) targets.add(c);
                }
                if (now) expandRows(targets);
                else collapseRows(targets);
            });
        }
        box.addView(header);

        int iconPx = Math.round(24 * sheet.getResources().getDisplayMetrics().density);
        for (WebExtension e : list) {
            View row = inflater.inflate(R.layout.item_menu_extension, box, false);
            row.setTag(R.id.ext_icon_tag, Boolean.TRUE); // 開閉する行の印
            row.setVisibility(expanded ? View.VISIBLE : View.GONE);
            ExtensionUi.loadIcon(row.findViewById(R.id.miIcon), e, iconPx);
            WebExtension.Action action = manager.actionFor(e, session);
            String label = action != null && action.title != null && !action.title.isEmpty()
                    ? action.title : ExtensionUi.name(e);
            ((TextView) row.findViewById(R.id.miName)).setText(label);
            TextView badge = row.findViewById(R.id.miBadge);
            if (action != null && action.badgeText != null && !action.badgeText.isEmpty()) {
                badge.setText(action.badgeText);
                badge.setVisibility(View.VISIBLE);
                if (action.badgeBackgroundColor != null) {
                    badge.setBackgroundTintList(ColorStateList.valueOf(action.badgeBackgroundColor));
                }
                if (action.badgeTextColor != null) badge.setTextColor(action.badgeTextColor);
            }
            boolean usable = action == null || action.enabled == null || action.enabled;
            View main = row.findViewById(R.id.miMain);
            main.setAlpha(usable ? 1f : 0.5f);
            main.setOnClickListener(v -> {
                if (action != null && usable) {
                    // ツールバーボタンを押したのと同じ(ポップアップがあれば下から出る)
                    choose(v, new Item(0, "", () -> manager.clickAction(e)));
                } else {
                    choose(v, new Item(0, "", () -> openDetail(e)));
                }
            });
            row.findViewById(R.id.miSettings).setOnClickListener(v -> choose(v, new Item(0, "", () -> openDetail(e))));
            box.addView(row);
        }

        if (!list.isEmpty()) {
            View manage = inflater.inflate(R.layout.item_aozora_menu_row, box, false);
            ImageView mi = manage.findViewById(R.id.am_row_icon);
            mi.setImageResource(R.drawable.ic_ext_store);
            mi.setImageTintList(ColorStateList.valueOf(onVariant));
            ((TextView) manage.findViewById(R.id.am_row_label)).setText(Lang.t("拡張機能を管理"));
            manage.setOnClickListener(v -> choose(v, new Item(0, "", this::openManager)));
            box.addView(manage);
        }
    }

    /** 「拡張機能」の行を押した時: パズルのアイコンがくるっと回り、件数のチップが弾む */
    private void animateExtensionHeader(View header, boolean expand) {
        View icon = header.findViewById(R.id.mxIcon);
        icon.animate().cancel();
        icon.setRotation(0f);
        icon.animate().rotation(expand ? 360f : -360f).setDuration(520)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .withEndAction(() -> icon.setRotation(0f)).start();
        View chip = header.findViewById(R.id.mxChip);
        chip.animate().cancel();
        chip.animate().scaleX(1.12f).scaleY(1.12f).setDuration(110).withEndAction(() ->
                chip.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start()).start();
        View summary = header.findViewById(R.id.mxSummary);
        summary.animate().cancel();
        summary.setAlpha(0.3f);
        summary.animate().alpha(1f).setDuration(260).start();
    }

    /** 行を上から順に、高さを伸ばしながらすべり込ませる */
    private void expandRows(List<View> rows) {
        if (rows.isEmpty()) return;
        float density = sheet.getResources().getDisplayMetrics().density;
        int width = ((View) rows.get(0).getParent()).getWidth();
        PathInterpolator emphasized = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
        for (int i = 0; i < rows.size(); i++) {
            View row = rows.get(i);
            row.animate().cancel();
            cancelHeightAnim(row);
            row.setVisibility(View.VISIBLE);
            row.measure(View.MeasureSpec.makeMeasureSpec(Math.max(width, 1), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int target = row.getMeasuredHeight();
            long delay = 35L * i;
            animateHeight(row, 0, target, delay, 300, emphasized, null);
            row.setAlpha(0f);
            row.setTranslationY(-10 * density);
            row.setScaleX(0.96f);
            row.animate().alpha(1f).translationY(0f).scaleX(1f).setStartDelay(delay + 60).setDuration(320)
                    .setInterpolator(emphasized).start();
            View icon = row.findViewById(R.id.miIcon);
            if (icon != null) {
                icon.animate().cancel();
                icon.setScaleX(0.4f);
                icon.setScaleY(0.4f);
                icon.animate().scaleX(1f).scaleY(1f).setStartDelay(delay + 120).setDuration(360)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.6f)).start();
            }
        }
    }

    /** 行を下から順に、薄くしながら高さを縮めて畳む */
    private void collapseRows(List<View> rows) {
        PathInterpolator accel = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
        int n = rows.size();
        for (int i = 0; i < n; i++) {
            View row = rows.get(n - 1 - i);
            if (row.getVisibility() != View.VISIBLE) continue;
            row.animate().cancel();
            cancelHeightAnim(row);
            long delay = 25L * i;
            row.animate().alpha(0f).scaleX(0.96f).setStartDelay(delay).setDuration(160)
                    .setInterpolator(accel).start();
            animateHeight(row, row.getHeight(), 0, delay + 40, 220, new PathInterpolator(0.2f, 0f, 0f, 1f), () -> {
                row.setVisibility(View.GONE);
                row.setAlpha(1f);
                row.setScaleX(1f);
            });
        }
    }

    private static void cancelHeightAnim(View v) {
        Object running = v.getTag(R.id.am_height_anim);
        if (running instanceof android.animation.Animator) ((android.animation.Animator) running).cancel();
    }

    /** 高さを from から to へ動かし、終わったら wrap_content に戻す */
    private static void animateHeight(View v, int from, int to, long delay, long duration,
                                      android.animation.TimeInterpolator interpolator,
                                      @Nullable Runnable end) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        lp.height = from;
        v.setLayoutParams(lp);
        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(from, to);
        anim.setStartDelay(delay);
        anim.setDuration(duration);
        anim.setInterpolator(interpolator);
        anim.addUpdateListener(a -> {
            lp.height = (int) a.getAnimatedValue();
            v.setLayoutParams(lp);
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (v.getTag(R.id.am_height_anim) == animation) v.setTag(R.id.am_height_anim, null);
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                v.setLayoutParams(lp);
                if (!canceled && end != null) end.run();
            }
        });
        v.setTag(R.id.am_height_anim, anim);
        anim.start();
    }

    private void openManager() {
        activity.startActivity(new Intent(activity, ExtensionsActivity.class));
        activity.overridePendingTransition(R.anim.ext_in, R.anim.ext_out);
    }

    private void openDetail(WebExtension e) {
        activity.startActivity(new Intent(activity, ExtensionDetailActivity.class)
                .putExtra(ExtensionDetailActivity.EXTRA_ID, e.id));
        activity.overridePendingTransition(R.anim.ext_in, R.anim.ext_out);
    }

    // ------------------------------------------------------------------
    // 中身
    // ------------------------------------------------------------------

    private void bindHeader(boolean secret) {
        TextView chip = sheet.findViewById(R.id.am_secret);
        chip.setVisibility(secret ? View.VISIBLE : View.GONE);
    }

    private void bindTiles(List<Item> tiles) {
        LinearLayout container = sheet.findViewById(R.id.am_tiles);
        LayoutInflater inflater = LayoutInflater.from(sheet.getContext());
        // タイルごとにアイコンの下地の色を変える(Pixel の壁紙の色から作られたテーマの 3 色を順に使う)
        int[][] palette = {
                {MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorPrimaryContainer),
                        MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnPrimaryContainer)},
                {MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorSecondaryContainer),
                        MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnSecondaryContainer)},
                {MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorTertiaryContainer),
                        MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnTertiaryContainer)},
        };
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(sheet.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                container.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            Item item = tiles.get(i);
            View tile = inflater.inflate(R.layout.item_aozora_menu_tile, row, false);
            int[] colors = palette[i % palette.length];
            View iconBg = tile.findViewById(R.id.am_tile_icon_bg);
            iconBg.setBackgroundTintList(ColorStateList.valueOf(colors[0]));
            ImageView icon = tile.findViewById(R.id.am_tile_icon);
            icon.setImageResource(item.icon);
            icon.setImageTintList(ColorStateList.valueOf(colors[1]));
            ((TextView) tile.findViewById(R.id.am_tile_label)).setText(item.label);
            tile.setContentDescription(item.label);
            tile.setOnClickListener(v -> choose(v, item));
            row.addView(tile);
        }
        // 最後の行が埋まらない時は、空きを置いてタイルの幅をそろえる
        if (row != null) {
            int rest = (COLUMNS - tiles.size() % COLUMNS) % COLUMNS;
            for (int i = 0; i < rest; i++) {
                View space = new View(sheet.getContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
                row.addView(space, lp);
            }
        }
    }

    private void bindRows(List<Item> rows) {
        LinearLayout container = sheet.findViewById(R.id.am_rows);
        container.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(sheet.getContext());
        int onVariant = MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnSurfaceVariant);
        for (Item item : rows) {
            View row = inflater.inflate(R.layout.item_aozora_menu_row, container, false);
            ImageView icon = row.findViewById(R.id.am_row_icon);
            icon.setImageResource(item.icon);
            icon.setImageTintList(ColorStateList.valueOf(onVariant));
            ((TextView) row.findViewById(R.id.am_row_label)).setText(item.label);
            row.setOnClickListener(v -> choose(v, item));
            container.addView(row);
        }
    }

    /** 押した項目を少し沈ませてから閉じ、画面を開く。 */
    private void choose(View v, Item item) {
        if (closing || switching) return;
        if (item.children != null) {
            v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
                openSubPage(item);
            }).start();
            return;
        }
        closing = true;
        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).withEndAction(() -> {
            dialog.dismiss();
            item.action.run();
        }).start();
    }

    // ------------------------------------------------------------------
    // 中の一覧(ツール)
    // ------------------------------------------------------------------

    /** 中の一覧で、開く時に順に咲かせるタイル(と、その波の順番) */
    private final List<View> subTiles = new ArrayList<>();
    private final List<Integer> subWaves = new ArrayList<>();
    @Nullable private Item subParent;

    private void openSubPage(Item parent) {
        if (inSubPage || switching || parent.children == null) return;
        LinearLayout page = sheet.findViewById(R.id.am_tools_page);
        subParent = parent;
        buildSubPage(page, parent.children);
        inSubPage = true;
        subPageBack.setEnabled(true);
        switchPage(sheet.findViewById(R.id.am_main_page), page, true, parent.label);
    }

    private void closeSubPage() {
        if (!inSubPage || switching) return;
        inSubPage = false;
        subPageBack.setEnabled(false);
        switchPage(sheet.findViewById(R.id.am_tools_page), sheet.findViewById(R.id.am_main_page), false, "AirSky");
    }

    /** ツールを 4 列のタイルで並べる(Pixel のクイック設定風: 丸いアイコンの下に名前) */
    private void buildSubPage(LinearLayout page, List<Item> items) {
        page.removeAllViews();
        subTiles.clear();
        subWaves.clear();
        float density = sheet.getResources().getDisplayMetrics().density;
        LayoutInflater inflater = LayoutInflater.from(sheet.getContext());
        Item parent = subParent;
        if (parent != null && parent.big != null) buildBigCards(page, parent.big, density);
        if (parent != null && parent.childrenTitle != null) {
            TextView head = new TextView(sheet.getContext());
            head.setText(parent.childrenTitle);
            head.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            head.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            head.setTextColor(MaterialColors.getColor(sheet, androidx.appcompat.R.attr.colorPrimary));
            int p = Math.round(20 * density);
            head.setPadding(p, Math.round(18 * density), p, Math.round(6 * density));
            page.addView(head);
            if (items.isEmpty() && parent.emptyText != null) {
                TextView empty = new TextView(sheet.getContext());
                empty.setText(parent.emptyText);
                empty.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
                empty.setTextColor(MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnSurfaceVariant));
                empty.setBackgroundResource(R.drawable.bg_page_menu_group);
                empty.setPadding(p, Math.round(18 * density), p, Math.round(18 * density));
                LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                elp.leftMargin = elp.rightMargin = Math.round(4 * density);
                page.addView(empty, elp);
                subTiles.add(empty);
                subWaves.add(2);
                return;
            }
        }
        int[][] palette = {
                {MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorPrimaryContainer),
                        MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnPrimaryContainer)},
                {MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorSecondaryContainer),
                        MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnSecondaryContainer)},
                {MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorTertiaryContainer),
                        MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnTertiaryContainer)},
        };
        LinearLayout grid = new LinearLayout(sheet.getContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setBackgroundResource(R.drawable.bg_page_menu_group);
        int pad = Math.round(6 * density);
        grid.setPadding(pad, pad * 2, pad, pad);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = Math.round(4 * density);
        glp.leftMargin = Math.round(4 * density);
        glp.rightMargin = glp.leftMargin;
        page.addView(grid, glp);
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % SUB_COLUMNS == 0) {
                row = new LinearLayout(sheet.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                grid.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            Item item = items.get(i);
            View tile = inflater.inflate(R.layout.item_aozora_menu_tool, row, false);
            int[] colors = palette[(i / SUB_COLUMNS + i) % palette.length];
            if (item.toneBg != 0) {
                colors = new int[]{androidx.core.content.ContextCompat.getColor(activity, item.toneBg),
                        androidx.core.content.ContextCompat.getColor(activity, item.toneFg)};
            }
            subTiles.add(tile);
            subWaves.add(i / SUB_COLUMNS + i % SUB_COLUMNS + (subParent != null && subParent.big != null ? 2 : 0));
            View iconBg = tile.findViewById(R.id.am_tool_icon_bg);
            iconBg.setBackgroundTintList(ColorStateList.valueOf(colors[0]));
            ImageView icon = tile.findViewById(R.id.am_tool_icon);
            icon.setImageResource(item.icon);
            icon.setImageTintList(ColorStateList.valueOf(colors[1]));
            ((TextView) tile.findViewById(R.id.am_tool_label)).setText(item.label);
            tile.setContentDescription(item.label);
            tile.setOnClickListener(v -> choose(v, item));
            if (item.longPress != null) {
                tile.setOnLongClickListener(v -> {
                    if (closing || switching) return false;
                    item.longPress.onLongPress(() -> removeSubPageItem(page, items, item));
                    return true;
                });
            }
            row.addView(tile);
        }
        if (row != null) {
            int rest = (SUB_COLUMNS - items.size() % SUB_COLUMNS) % SUB_COLUMNS;
            for (int i = 0; i < rest; i++) {
                row.addView(new View(sheet.getContext()), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
    }

    /** ツールの一覧から 1 つ消して並べ直し、シートの高さをなめらかに合わせる。 */
    private void removeSubPageItem(LinearLayout page, List<Item> items, Item item) {
        if (!items.remove(item) || !inSubPage) return;
        FrameLayout pages = sheet.findViewById(R.id.am_pages);
        int startH = pages.getHeight();
        buildSubPage(page, items);
        page.measure(View.MeasureSpec.makeMeasureSpec(pages.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        cancelHeightAnim(pages);
        animateHeight(pages, startH, page.getMeasuredHeight(), 0, 300,
                new PathInterpolator(0.05f, 0.7f, 0.1f, 1f), null);
    }

    /**
     * メニュー本体とツール一覧を入れ替える。
     * 出ていく側は横へずれながら薄くなり、入る側はタイルが波のように順に咲く。
     * 2 つを入れた枠の高さを動かして、シートの高さもなめらかに変える。
     */
    private void switchPage(View from, View to, boolean forward, String title) {
        switching = true;
        float density = sheet.getResources().getDisplayMetrics().density;
        FrameLayout pages = sheet.findViewById(R.id.am_pages);
        int startH = pages.getHeight();
        to.setVisibility(View.VISIBLE);
        to.measure(View.MeasureSpec.makeMeasureSpec(pages.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int endH = to.getMeasuredHeight();
        PathInterpolator emphasized = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
        float shift = 48 * density * (forward ? 1 : -1);

        from.animate().cancel();
        from.animate().alpha(0f).translationX(-shift).scaleX(0.94f).scaleY(0.94f).setStartDelay(0)
                .setDuration(200).setInterpolator(new PathInterpolator(0.3f, 0f, 0.8f, 0.15f))
                .withEndAction(() -> {
                    from.setVisibility(View.GONE);
                    from.setAlpha(1f);
                    from.setTranslationX(0f);
                    from.setScaleX(1f);
                    from.setScaleY(1f);
                }).start();

        to.animate().cancel();
        to.setAlpha(0f);
        to.setTranslationX(shift);
        to.animate().alpha(1f).translationX(0f).setStartDelay(90).setDuration(420)
                .setInterpolator(emphasized).start();
        if (forward) playSubPageTiles((ViewGroup) to, density);
        else playMainReturn();

        cancelHeightAnim(pages);
        animateHeight(pages, startH, endH, 0, 380, emphasized, null);
        pages.postDelayed(() -> switching = false, 420);

        // 見出し: ロゴと戻る矢印を回しながら入れ替え、題名を差し替える
        View logo = sheet.findViewById(R.id.am_logo);
        View back = sheet.findViewById(R.id.am_back);
        View showIcon = forward ? back : logo;
        View hideIcon = forward ? logo : back;
        hideIcon.animate().cancel();
        hideIcon.animate().rotation(forward ? 90f : -90f).scaleX(0.3f).scaleY(0.3f).alpha(0f)
                .setStartDelay(0).setDuration(150).withEndAction(() -> {
                    hideIcon.setVisibility(View.GONE);
                    hideIcon.setRotation(0f);
                    hideIcon.setScaleX(1f);
                    hideIcon.setScaleY(1f);
                    hideIcon.setAlpha(1f);
                    showIcon.setVisibility(View.VISIBLE);
                    showIcon.setRotation(forward ? -90f : 90f);
                    showIcon.setScaleX(0.3f);
                    showIcon.setScaleY(0.3f);
                    showIcon.setAlpha(0f);
                    showIcon.animate().rotation(0f).scaleX(1f).scaleY(1f).alpha(1f).setDuration(320)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f)).start();
                }).start();
        TextView titleView = sheet.findViewById(R.id.am_title);
        titleView.animate().cancel();
        titleView.animate().alpha(0f).translationY(-8 * density).setStartDelay(0).setDuration(130).withEndAction(() -> {
            titleView.setText(title);
            titleView.setTranslationY(8 * density);
            titleView.animate().alpha(1f).translationY(0f).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }).start();
    }

    /** ツールのタイル: 左上から斜めの波で、小さく回りながら咲く(コンソールの大きなカードは下からせり上がる) */
    private void playSubPageTiles(ViewGroup page, float density) {
        PathInterpolator emphasized = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
        for (int i = 0; i < subTiles.size(); i++) {
            View tile = subTiles.get(i);
            int wave = subWaves.get(i);
            boolean big = Boolean.TRUE.equals(tile.getTag(R.id.am_big_card));
            tile.setAlpha(0f);
            if (big) {
                tile.setTranslationY(36 * density);
                tile.setScaleX(0.92f);
                tile.setScaleY(0.92f);
                tile.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                        .setStartDelay(100L + 70L * wave).setDuration(520).setInterpolator(emphasized).start();
            } else {
                tile.setScaleX(0.5f);
                tile.setScaleY(0.5f);
                tile.setTranslationY(20 * density);
                tile.setRotation(-8f);
                tile.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).rotation(0f)
                        .setStartDelay(120L + 38L * wave).setDuration(440)
                        .setInterpolator(emphasized).start();
            }
            View icon = tile.findViewById(big ? R.id.am_big_icon : R.id.am_tool_icon);
            if (icon == null) continue;
            icon.setScaleX(0f);
            icon.setScaleY(0f);
            icon.setRotation(-45f);
            icon.animate().scaleX(1f).scaleY(1f).rotation(0f).setStartDelay((big ? 260L : 220L) + 38L * wave)
                    .setDuration(big ? 460 : 380)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2.4f)).start();
        }
    }

    /**
     * コンソールの大きなカード(Java / Python)。2 つを横に並べる。
     * Pixel のクイック設定の大きなタイルのように、角の大きく丸いカードに、丸いアイコン・名前・説明を入れる。
     */
    private void buildBigCards(LinearLayout page, List<Item> big, float density) {
        LinearLayout row = new LinearLayout(sheet.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        int gap = Math.round(6 * density);
        int[][] palette = {
                {MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorTertiaryContainer),
                        MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnTertiaryContainer)},
                {MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorPrimaryContainer),
                        MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnPrimaryContainer)},
        };
        for (int i = 0; i < big.size(); i++) {
            Item item = big.get(i);
            int[] colors = palette[i % palette.length];
            LinearLayout card = new LinearLayout(sheet.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setClickable(true);
            card.setFocusable(true);
            int pad = Math.round(18 * density);
            card.setPadding(pad, pad, pad, pad);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(colors[0]);
            bg.setCornerRadius(28 * density);
            card.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf((colors[1] & 0x00ffffff) | 0x22000000), bg, null));
            card.setTag(R.id.am_big_card, Boolean.TRUE);

            FrameLayout iconBox = new FrameLayout(sheet.getContext());
            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(colors[1]);
            iconBox.setBackground(circle);
            ImageView icon = new ImageView(sheet.getContext());
            icon.setId(R.id.am_big_icon);
            icon.setImageResource(item.icon);
            icon.setImageTintList(ColorStateList.valueOf(colors[0]));
            int is = Math.round(26 * density);
            iconBox.addView(icon, new FrameLayout.LayoutParams(is, is, android.view.Gravity.CENTER));
            int box = Math.round(52 * density);
            card.addView(iconBox, new LinearLayout.LayoutParams(box, box));

            TextView label = new TextView(sheet.getContext());
            label.setText(item.label);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22);
            label.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            label.setTextColor(colors[1]);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = Math.round(22 * density);
            card.addView(label, llp);
            if (item.subtitle != null) {
                TextView sub = new TextView(sheet.getContext());
                sub.setText(item.subtitle);
                sub.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
                sub.setTextColor((colors[1] & 0x00ffffff) | 0xC0000000);
                card.addView(sub);
            }
            card.setContentDescription(item.label);
            card.setOnClickListener(v -> choose(v, item));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = gap;
            lp.rightMargin = gap;
            row.addView(card, lp);
            subTiles.add(card);
            subWaves.add(i);
        }
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Math.round(4 * density);
        page.addView(row, rlp);
    }

    /** メニュー本体へ戻る時: タイルを軽く順に戻す */
    private void playMainReturn() {
        PathInterpolator emphasized = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
        LinearLayout tiles = sheet.findViewById(R.id.am_tiles);
        int i = 0;
        for (int r = 0; r < tiles.getChildCount(); r++) {
            ViewGroup row = (ViewGroup) tiles.getChildAt(r);
            for (int c = 0; c < row.getChildCount(); c++) {
                View tile = row.getChildAt(c);
                if (!tile.isClickable()) continue;
                tile.setAlpha(0f);
                tile.setScaleX(0.85f);
                tile.setScaleY(0.85f);
                tile.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(100L + 30L * i++)
                        .setDuration(320).setInterpolator(emphasized).start();
            }
        }
    }

    // ------------------------------------------------------------------
    // 開く時の動き
    // ------------------------------------------------------------------

    private void playEnter() {
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        float density = sheet.getResources().getDisplayMetrics().density;

        if (scrim != null) scrim.animate().alpha(1f).setDuration(220).start();

        // シート: 画面の下からばねで持ち上がり、少し行き過ぎてから止まる
        if (bottomSheet != null) {
            bottomSheet.setTranslationY(bottomSheet.getHeight() > 0 ? bottomSheet.getHeight() : 600 * density);
            SpringAnimation lift = new SpringAnimation(bottomSheet, DynamicAnimation.TRANSLATION_Y, 0f);
            lift.getSpring().setStiffness(SpringForce.STIFFNESS_LOW * 1.6f)
                    .setDampingRatio(0.78f);
            lift.start();
        }

        // 見出し: ロゴがくるっと回って現れる
        View logo = sheet.findViewById(R.id.am_logo);
        logo.setRotation(-120f);
        logo.setScaleX(0.3f);
        logo.setScaleY(0.3f);
        logo.animate().rotation(0f).scaleX(1f).scaleY(1f).setStartDelay(80).setDuration(520)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f)).start();
        View title = sheet.findViewById(R.id.am_title);
        title.setAlpha(0f);
        title.setTranslationX(-12 * density);
        title.animate().alpha(1f).translationX(0f).setStartDelay(140).setDuration(320)
                .setInterpolator(new DecelerateInterpolator(2f)).start();

        // タイル: Aozora ボタンのある右下に近いものから順に、小さな所から咲くように広がる
        LinearLayout tiles = sheet.findViewById(R.id.am_tiles);
        int rowsCount = tiles.getChildCount();
        PathInterpolator emphasized = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
        for (int r = 0; r < rowsCount; r++) {
            ViewGroup row = (ViewGroup) tiles.getChildAt(r);
            for (int c = 0; c < row.getChildCount(); c++) {
                View tile = row.getChildAt(c);
                if (!tile.isClickable()) continue; // 空き
                // 右下からの距離(行・列の差)で遅らせる
                int wave = (rowsCount - 1 - r) + (COLUMNS - 1 - c);
                tile.setAlpha(0f);
                tile.setScaleX(0.6f);
                tile.setScaleY(0.6f);
                tile.setTranslationX(18 * density);
                tile.setTranslationY(26 * density);
                tile.setRotation(6f);
                tile.animate().alpha(1f).scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).rotation(0f)
                        .setStartDelay(90L + 45L * wave).setDuration(460)
                        .setInterpolator(emphasized).start();
                View icon = tile.findViewById(R.id.am_tile_icon);
                icon.setScaleX(0f);
                icon.setScaleY(0f);
                icon.animate().scaleX(1f).scaleY(1f).setStartDelay(200L + 45L * wave).setDuration(380)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.4f)).start();
            }
        }

        // 拡張機能: タイルの後に、行と一緒にすべり込む
        View extensions = sheet.findViewById(R.id.am_extensions);
        if (extensions.getVisibility() == View.VISIBLE) {
            extensions.setAlpha(0f);
            extensions.setTranslationY(24 * density);
            extensions.animate().alpha(1f).translationY(0f).setStartDelay(200).setDuration(380)
                    .setInterpolator(emphasized).start();
        }

        // 行: タイルの後に、下から順にすべり込む
        LinearLayout rows = sheet.findViewById(R.id.am_rows);
        rows.setAlpha(0f);
        rows.setTranslationY(24 * density);
        rows.animate().alpha(1f).translationY(0f).setStartDelay(220).setDuration(380)
                .setInterpolator(emphasized).start();
        for (int i = 0; i < rows.getChildCount(); i++) {
            View child = rows.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationX(20 * density);
            child.animate().alpha(1f).translationX(0f).setStartDelay(280L + 40L * i).setDuration(300)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }
}
