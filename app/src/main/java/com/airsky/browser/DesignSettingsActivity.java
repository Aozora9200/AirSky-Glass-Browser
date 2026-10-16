package com.airsky.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * デザイン(旧「テーマ」)。上に今の見た目のプレビュー、その下でテーマ・ボタンの形・
 * URL バー横のショートカットを選ぶ。
 */
public class DesignSettingsActivity extends PixelSettingsActivity {

    static final String THEME_PREFS = "theme_prefs";
    static final String KEY_THEME = "selected_theme";
    static final String POPUP_PREFS = "popup_prefs";
    static final String KEY_POPUP = "selected_popupbutton";
    static final String ARROW_PREFS = "arrow_prefs";
    static final String KEY_ARROW = "selected_arrowbutton";

    private static final int THEME_LIGHT = 0;
    private static final int THEME_DARK = 1;
    private static final int THEME_SYSTEM = 2;

    private static final String STATE_CHANGED = "changed";

    /** 再起動しないと反映されない変更をしたか。 */
    private boolean changed = false;

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("デザイン");
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) changed = savedInstanceState.getBoolean(STATE_CHANGED, false);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(STATE_CHANGED, changed);
    }

    @Override
    protected void onBuild() {
        int theme = getSharedPreferences(THEME_PREFS, MODE_PRIVATE).getInt(KEY_THEME, THEME_SYSTEM);
        int popup = getSharedPreferences(POPUP_PREFS, MODE_PRIVATE).getInt(KEY_POPUP, 2);
        int arrow = getSharedPreferences(ARROW_PREFS, MODE_PRIVATE).getInt(KEY_ARROW, 1);

        gap();
        card(buildPreview(popup, arrow));

        header(Lang.t("テーマ"));
        card(buildThemeTiles(theme));
        SharedPreferences adv = getSharedPreferences("AdvancedBrowserPrefs", MODE_PRIVATE);
        switchRow(R.drawable.ic_ps_dark, Lang.t("ウェブページをダークモードで表示"),
                Lang.t("アプリのテーマに関係なく、ダークモードに対応したサイトを暗い配色で表示します"),
                adv.getBoolean("dark_mode", false), on -> {
                    adv.edit().putBoolean("dark_mode", on).apply();
                    GeckoEngine.get(this).applyColorScheme();
                });

        header(Lang.t("ガラス"));
        row(R.drawable.ic_ps_glass, Lang.t("ガラスのデザイン"), GlassSettingsActivity.summary(this),
                () -> startActivity(GlassSettingsActivity.intent(this))).chevron();

        header(Lang.t("画面"));
        int mode = TabletUi.mode(this);
        radioRow(R.drawable.ic_ps_single_screen, Lang.t("一画面"), Lang.t("URL バーとボトムバーを、ページと同じ画面に表示します"),
                mode == TabletUi.MODE_SINGLE, () -> setScreenMode(TabletUi.MODE_SINGLE));
        radioRow(R.drawable.ic_ps_dual_screen, Lang.t("二画面"),
                Lang.t("画面を 2 つ持つ端末(AYN Thor など)向け。上の画面にページ、下の画面にページの続きと URL バー・ボトムバーを表示します"),
                mode == TabletUi.MODE_DUAL, () -> setScreenMode(TabletUi.MODE_DUAL));
        radioRow(R.drawable.ic_ps_tablet, Lang.t("タブレット"),
                Lang.t("パソコンのブラウザのように、タブを上に並べ、URL バーとボトムバーを 1 本のツールバーにまとめます"),
                mode == TabletUi.MODE_TABLET, () -> setScreenMode(TabletUi.MODE_TABLET));
        if (mode == TabletUi.MODE_TABLET) {
            header(Lang.t("タブレットのバーの位置"));
            boolean bottom = TabletUi.barsAtBottom(this);
            radioRow(R.drawable.ic_ps_bars_top, Lang.t("上"),
                    Lang.t("タブとツールバーを画面の上に表示します。メニューやメッセージも上に出ます"),
                    !bottom, () -> setTabletBarsBottom(false));
            radioRow(R.drawable.ic_ps_bars_bottom, Lang.t("下"),
                    Lang.t("タブとツールバーを画面の下に表示します。片手で持った時にも指が届きます"),
                    bottom, () -> setTabletBarsBottom(true));
        }
        if (mode == TabletUi.MODE_DUAL && !DualScreen.hasSecondScreen(this)) {
            note(Lang.t("この端末では 2 つ目の画面が見つかりません。二画面を選んでいても、2 つ目の画面が使える時だけ二画面で表示します。"));
        }

        header(Lang.t("ツールバー"));
        ToolbarShortcut.Option sc = ToolbarShortcut.current(this);
        row(sc.icon, Lang.t("ツールバーのショートカット"), sc.label + Lang.t("(URL バーの横のボタンを長押ししても変更できます)"),
                () -> ToolbarShortcut.showPicker(this, this::rebuild)).chevron();
        if (mode == TabletUi.MODE_TABLET) {
            ToolbarShortcut.Option sc1 = ToolbarShortcut.current(this, ToolbarShortcut.SLOT_TABLET_1);
            row(sc1.icon, Lang.t("タブレットのショートカット 1"), sc1.label + Lang.t("(ボタンを長押ししても変更できます)"),
                    () -> ToolbarShortcut.showPicker(this, ToolbarShortcut.SLOT_TABLET_1, this::rebuild)).chevron();
            ToolbarShortcut.Option sc2 = ToolbarShortcut.current(this, ToolbarShortcut.SLOT_TABLET_2);
            row(sc2.icon, Lang.t("タブレットのショートカット 2"), sc2.label + Lang.t("(ボタンを長押ししても変更できます)"),
                    () -> ToolbarShortcut.showPicker(this, ToolbarShortcut.SLOT_TABLET_2, this::rebuild)).chevron();
        }

        header(Lang.t("ステータスバーの電池"));
        int battery = StatusBattery.style(this);
        String[] batteryLabels = {Lang.t("% で表示"), Lang.t("アイコンで表示"), Lang.t("アイコンと % で表示")};
        int[] batteryStyles = {StatusBattery.STYLE_PERCENT, StatusBattery.STYLE_ICON, StatusBattery.STYLE_ICON_PERCENT};
        for (int i = 0; i < batteryLabels.length; i++) {
            final int value = batteryStyles[i];
            Row r = radioRow(R.drawable.ic_ps_none, batteryLabels[i], null, battery == value, () -> {
                StatusBattery.setStyle(this, value);
                rebuild();
            });
            if (value == StatusBattery.STYLE_PERCENT) {
                r.icon.setImageResource(R.drawable.ic_ps_battery);
                r.icon.setImageTintList(ColorStateList.valueOf(attrColor(com.google.android.material.R.attr.colorOnSurface)));
            } else {
                StatusBattery.IconDrawable d = new StatusBattery.IconDrawable(this);
                d.setColors(attrColor(com.google.android.material.R.attr.colorOnSurface),
                        attrColor(androidx.appcompat.R.attr.colorError));
                d.setLevel(70, false);
                r.icon.setImageTintList(null);
                r.icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                r.icon.setImageDrawable(d);
            }
        }

        header(Lang.t("メニューボタン"));
        String[] popups = {Lang.t("ライトボタン"), Lang.t("ダークボタン"), Lang.t("テーマに合わせる")};
        int[] popupIcons = {R.mipmap.aozora1, R.mipmap.aozora, isNight() ? R.mipmap.aozora : R.mipmap.aozora1};
        for (int i = 0; i < popups.length; i++) {
            final int value = i;
            radioRow(R.drawable.ic_ps_none, popups[i], null, popup == i, () -> {
                getSharedPreferences(POPUP_PREFS, MODE_PRIVATE).edit().putInt(KEY_POPUP, value).apply();
                changed = true;
                rebuild();
            }).rawIcon(ContextCompat.getDrawable(this, popupIcons[i]));
        }

        header(Lang.t("矢印ボタン"));
        String[] arrows = {Lang.t("従来のスタイル"), Lang.t("システム UI"), Lang.t("モダンスタイル")};
        int[] arrowIcons = {R.drawable.back, R.drawable.ic_sysbar_back, R.drawable.ios_back};
        for (int i = 0; i < arrows.length; i++) {
            final int value = i;
            Row r = radioRow(R.drawable.ic_ps_none, arrows[i], null, arrow == i, () -> {
                getSharedPreferences(ARROW_PREFS, MODE_PRIVATE).edit().putInt(KEY_ARROW, value).apply();
                changed = true;
                rebuild();
            });
            r.icon.setImageResource(arrowIcons[i]);
            r.icon.setImageTintList(ColorStateList.valueOf(attrColor(com.google.android.material.R.attr.colorOnSurface)));
            if (arrowIcons[i] == R.drawable.ic_sysbar_back) {
                // システム UI の矢印は周りの余白が大きく、24dp に縮めると小さすぎるので、縮めずに真ん中を見せる
                ViewGroup.LayoutParams lp = r.icon.getLayoutParams();
                lp.width = dp(40);
                lp.height = dp(40);
                r.icon.setLayoutParams(lp);
                r.icon.setScaleType(ImageView.ScaleType.CENTER);
            }
        }
        note(Lang.t("テーマとボタンの変更は、AirSky を再起動すると反映されます。"));
    }

    /** 一画面・二画面・タブレット。タブレット UI に切り替える/やめる時は再起動が必要。 */
    private void setScreenMode(int mode) {
        boolean wasTablet = TabletUi.isEnabled(this);
        TabletUi.setMode(this, mode);
        if (wasTablet != (mode == TabletUi.MODE_TABLET)) changed = true;
        rebuild();
    }

    /** タブレット UI のバーを上/下に置く。並べ替えは起動時に行うので再起動が必要。 */
    private void setTabletBarsBottom(boolean bottom) {
        if (TabletUi.barsAtBottom(this) == bottom) return;
        TabletUi.setBarsAtBottom(this, bottom);
        changed = true;
        rebuild();
    }

    // ------------------------------------------------------------------
    // プレビュー
    // ------------------------------------------------------------------

    private View buildPreview(int popup, int arrow) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));

        int onSurface = attrColor(com.google.android.material.R.attr.colorOnSurface);
        int container = attrColor(com.google.android.material.R.attr.colorSurfaceContainerHigh);

        // URL バー
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        View fav = new View(this);
        GradientDrawable favBg = new GradientDrawable();
        favBg.setShape(GradientDrawable.OVAL);
        favBg.setColor(attrColor(androidx.appcompat.R.attr.colorPrimary));
        fav.setBackground(favBg);
        bar.addView(fav, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView url = new TextView(this);
        url.setText("aozora.app");
        url.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        url.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        url.setGravity(Gravity.CENTER_VERTICAL);
        url.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(20));
        pill.setColor(container);
        url.setBackground(pill);
        LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        urlLp.setMarginStart(dp(8));
        urlLp.setMarginEnd(dp(4));
        bar.addView(url, urlLp);
        ToolbarShortcut.Option sc = ToolbarShortcut.current(this);
        if (!ToolbarShortcut.NONE.equals(sc.key)) {
            ImageView s = icon(sc.icon, onSurface);
            GradientDrawable hl = new GradientDrawable();
            hl.setShape(GradientDrawable.OVAL);
            hl.setColor(attrColor(com.google.android.material.R.attr.colorSecondaryContainer));
            s.setBackground(hl);
            bar.addView(s, new LinearLayout.LayoutParams(dp(36), dp(36)));
        }
        bar.addView(icon(R.drawable.ic_pm_more, onSurface), new LinearLayout.LayoutParams(dp(36), dp(36)));
        box.addView(bar);

        // ページの代わり(線を何本か)
        for (int i = 0; i < 3; i++) {
            View line = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(4));
            g.setColor(container);
            line.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    i == 2 ? dp(160) : ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
            lp.topMargin = dp(i == 0 ? 18 : 8);
            box.addView(line, lp);
        }

        // 下のバー
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bottomBg = new GradientDrawable();
        bottomBg.setCornerRadius(dp(24));
        bottomBg.setColor(container);
        bottom.setBackground(bottomBg);
        int back = arrow == 0 ? R.drawable.back : arrow == 2 ? R.drawable.ios_back : R.drawable.ic_sysbar_back;
        int fwd = arrow == 0 ? R.drawable.forward : arrow == 2 ? R.drawable.ios_forward : R.drawable.ic_sysbar_forward;
        ImageView backIcon = icon(back, onSurface);
        ImageView fwdIcon = icon(fwd, onSurface);
        if (arrow == 1) {
            // システム UI の矢印は余白込みの画像なので、縮めずに真ん中を見せる
            for (ImageView iv : new ImageView[]{backIcon, fwdIcon}) {
                iv.setScaleType(ImageView.ScaleType.CENTER);
                iv.setPadding(0, 0, 0, 0);
            }
        }
        bottom.addView(backIcon, weighted());
        bottom.addView(fwdIcon, weighted());
        bottom.addView(icon(R.drawable.reload, onSurface), weighted());
        TextView tabs = new TextView(this);
        tabs.setText("3");
        tabs.setGravity(Gravity.CENTER);
        tabs.setTextColor(onSurface);
        tabs.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        GradientDrawable tabBg = new GradientDrawable();
        tabBg.setCornerRadius(dp(6));
        tabBg.setStroke(dp(2), onSurface);
        tabs.setBackground(tabBg);
        FrameLayout tabBox = new FrameLayout(this);
        tabBox.addView(tabs, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        bottom.addView(tabBox, weighted());
        int menu = popup == 0 ? R.mipmap.aozora1 : popup == 1 ? R.mipmap.aozora
                : isNight() ? R.mipmap.aozora : R.mipmap.aozora1;
        ImageView m = new ImageView(this);
        m.setImageResource(menu);
        m.setScaleType(ImageView.ScaleType.FIT_CENTER);
        m.setPadding(dp(10), dp(10), dp(10), dp(10));
        bottom.addView(m, weighted());
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        bLp.topMargin = dp(22);
        box.addView(bottom, bLp);
        return box;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private ImageView icon(@DrawableRes int res, int tint) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setPadding(dp(8), dp(8), dp(8), dp(8));
        iv.setImageTintList(ColorStateList.valueOf(tint));
        return iv;
    }

    // ------------------------------------------------------------------
    // テーマのタイル
    // ------------------------------------------------------------------

    private View buildThemeTiles(int theme) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(12), dp(16), dp(12), dp(16));
        String[] labels = {Lang.t("ライト"), Lang.t("ダーク"), Lang.t("端末に合わせる")};
        for (int i = 0; i < 3; i++) {
            row.addView(themeTile(i, labels[i], theme == i),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private View themeTile(int value, String label, boolean selected) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(dp(4), 0, dp(4), 0);

        FrameLayout swatch = new FrameLayout(this);
        int light = Color.parseColor("#F4F4F8");
        int dark = Color.parseColor("#1E1F25");
        GradientDrawable base = new GradientDrawable();
        base.setCornerRadius(dp(18));
        base.setColor(value == THEME_DARK ? dark : light);
        if (value == THEME_SYSTEM) {
            // 左半分ライト・右半分ダーク
            GradientDrawable right = new GradientDrawable();
            right.setCornerRadius(dp(18));
            right.setColor(dark);
            ClipDrawable half = new ClipDrawable(right, Gravity.END, ClipDrawable.HORIZONTAL);
            half.setLevel(5000);
            swatch.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[]{base, half}));
        } else {
            swatch.setBackground(base);
        }
        if (selected) {
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(dp(18));
            ring.setStroke(dp(3), attrColor(androidx.appcompat.R.attr.colorPrimary));
            swatch.setForeground(ring);
            ImageView check = new ImageView(this);
            check.setImageResource(R.drawable.ic_ps_check_circle);
            check.setImageTintList(ColorStateList.valueOf(attrColor(androidx.appcompat.R.attr.colorPrimary)));
            swatch.addView(check, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP | Gravity.END));
            ((FrameLayout.LayoutParams) check.getLayoutParams()).setMargins(0, dp(6), dp(6), 0);
        }
        // 小さな URL バーの線
        View mini = new View(this);
        GradientDrawable miniBg = new GradientDrawable();
        miniBg.setCornerRadius(dp(5));
        miniBg.setColor(value == THEME_DARK ? Color.parseColor("#3A3B41") : Color.parseColor("#DCDDE3"));
        mini.setBackground(miniBg);
        FrameLayout.LayoutParams miniLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10),
                Gravity.BOTTOM);
        miniLp.setMargins(dp(12), 0, dp(12), dp(14));
        swatch.addView(mini, miniLp);
        t.addView(swatch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(84)));

        TextView text = new TextView(this);
        text.setText(label);
        text.setGravity(Gravity.CENTER);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        text.setTextColor(selected ? attrColor(androidx.appcompat.R.attr.colorPrimary)
                : attrColor(com.google.android.material.R.attr.colorOnSurface));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        t.addView(text, lp);

        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        t.setForeground(ContextCompat.getDrawable(this, tv.resourceId));
        t.setContentDescription(label);
        t.setOnClickListener(v -> {
            if (selected) return;
            getSharedPreferences(THEME_PREFS, MODE_PRIVATE).edit().putInt(KEY_THEME, value).apply();
            changed = true;
            ThemeHelper.applyNightMode(this); // 設定画面はその場で切り替わる
            rebuild();
        });
        return t;
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    // ------------------------------------------------------------------

    @Override
    public void onBackPressed() {
        if (!changed) {
            super.onBackPressed();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setMessage(Lang.t("デザインの変更を反映するには再起動が必要です"))
                .setPositiveButton(Lang.t("キャンセル"), null)
                .setNegativeButton(Lang.t("再起動"), (d, w) -> {
                    getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putBoolean("rebootApp", true).apply();
                    finish();
                    overridePendingTransition(0, 0);
                })
                .setNeutralButton(Lang.t("後で"), (d, w) -> finish())
                .show();
    }

    /** 他の画面からデザインを開く。 */
    static void open(Context c) {
        c.startActivity(new android.content.Intent(c, DesignSettingsActivity.class));
    }
}
