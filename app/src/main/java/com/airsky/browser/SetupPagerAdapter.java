package com.airsky.browser;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.airbnb.lottie.LottieAnimationView;

/** 初回セットアップの各ページを作り、ページの中の設定を保存する。 */
public class SetupPagerAdapter extends PagerAdapter {
    private final Activity activity;
    private final int[] layouts;
    private final SparseArray<View> views = new SparseArray<>();
    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME = "selected_theme";
    private static final String KEY_UA_ENABLED = "ua_enabled";
    private static final String KEY_DESKUA_ENABLED = "deskua_enabled";
    private static final String PREF_NAME = "AdvancedBrowserPrefs";

    private static final int THEME_LIGHT = 0;
    private static final int THEME_DARK = 1;
    private static final int THEME_SYSTEM = 2;

    private ImageButton themeButton;

    public SetupPagerAdapter(Activity activity, int[] layouts) {
        this.activity = activity;
        this.layouts = layouts;
    }

    @Override
    public int getCount() {
        return layouts.length;
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    /** 作ってあるページ(画面外に出て破棄されていれば null)。 */
    @Nullable
    View viewAt(int position) {
        return views.get(position);
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        int layout = layouts[position];
        View view = LayoutInflater.from(activity).inflate(layout, container, false);
        if (layout == R.layout.setup_page1) {
            bindWelcome(view);
        } else if (layout == R.layout.setup_page_terms) {
            bindTerms(view);
        } else if (layout == R.layout.setup_page2) {
            bindTheme(view);
        } else if (layout == R.layout.setup_page3) {
            bindCustomize(view);
        } else if (layout == R.layout.setup_page_screen) {
            bindScreenMode(view);
        } else if (layout == R.layout.setup_page4) {
            bindDefaultBrowser(view);
        }
        // 画面を作り直した直後(テーマの変更など)に表示中のページなら、イラストをすぐ動かす
        LottieAnimationView lottie = view.findViewById(R.id.setup_lottie);
        if (lottie != null && container instanceof ViewPager
                && ((ViewPager) container).getCurrentItem() == position) {
            lottie.playAnimation();
        }
        container.addView(view);
        views.put(position, view);
        return view;
    }

    // ------------------------------------------------------------------
    // 各ページ
    // ------------------------------------------------------------------

    private void bindWelcome(View view) {
        TextView version = view.findViewById(R.id.setup_version);
        try {
            String name = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
            version.setText(Lang.t("バージョン ") + name);
        } catch (PackageManager.NameNotFoundException e) {
            version.setVisibility(View.GONE);
        }
    }

    private void bindTerms(View view) {
        FrameLayout box = view.findViewById(R.id.setup_terms_container);
        box.addView(TermsActivity.termsText(activity,
                ContextCompat.getColor(activity, R.color.setup_on_card),
                ContextCompat.getColor(activity, R.color.setup_primary)));
    }

    private void bindTheme(View view) {
        CheckBox progressBarAnimation = view.findViewById(R.id.setup_checkbox_progressBar);
        themeButton = view.findViewById(R.id.theme_example_image);
        SharedPreferences appPrefs = activity.getSharedPreferences("AppPrefs", MODE_PRIVATE);
        progressBarAnimation.setChecked(appPrefs.getBoolean("ProgressBarAnimation", true));
        progressBarAnimation.setOnCheckedChangeListener((b, on) ->
                appPrefs.edit().putBoolean("ProgressBarAnimation", on).apply());

        // 暗いカードの上でも読めるよう、閉じている時・開いた一覧とも明るい文字の行を使う
        Spinner spinner = view.findViewById(R.id.setup_styleList);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                activity, R.array.theme_options, R.layout.item_setup_spinner);
        adapter.setDropDownViewResource(R.layout.item_setup_spinner_dropdown);
        spinner.setAdapter(adapter);
        applySavedTheme();
        spinner.setSelection(savedTheme(), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                if (position == savedTheme()) return;
                activity.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(KEY_THEME, position).apply();
                applySavedTheme();
                // ライト/ダークが変わる時は、この画面も作り直されてすぐ反映される
                ThemeHelper.applyNightMode(activity);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        Button helpProgressButton = view.findViewById(R.id.setup_help_progress);
        helpProgressButton.setOnClickListener(v -> {
            activity.startActivity(new Intent(activity, HelpProgress.class));
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
    }

    private void bindCustomize(View view) {
        // 壁紙はスタートページと同じもの。プレビューにも同じ壁紙を出す
        AppWallpaper.apply(view.findViewById(R.id.background_example_image), null);
        CheckBox desktopcheck = view.findViewById(R.id.setup_checkbox_desktop);
        CheckBox uacheck = view.findViewById(R.id.setup_checkbox_ua);
        CheckBox bottomcheck = view.findViewById(R.id.setup_checkbox_bottom);
        CheckBox fixedUrlCheck = view.findViewById(R.id.setup_checkbox_fixed_url);
        CheckBox swipecheck = view.findViewById(R.id.setup_checkbox_swipe);
        CheckBox updatecheck = view.findViewById(R.id.setup_checkbox_update);
        SharedPreferences browserPrefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences prefs = activity.getSharedPreferences("AppPrefs", MODE_PRIVATE);

        swipecheck.setChecked(prefs.getBoolean("isSwipeReload", true));
        bottomcheck.setChecked(prefs.getBoolean("hidebottom", true));
        // アドレスバーを固定(既定はオフ)。固定するとスクロールでバーが隠れないので、下部バーの自動非表示も効かない
        boolean fixedUrl = BrowserPrefs.isUrlBarFixed(activity);
        fixedUrlCheck.setChecked(fixedUrl);
        bottomcheck.setEnabled(!fixedUrl);
        updatecheck.setChecked(prefs.getBoolean(UpdateChecker.KEY_ENABLED, true));
        boolean desk = browserPrefs.getBoolean(KEY_DESKUA_ENABLED, false);
        desktopcheck.setChecked(desk);
        uacheck.setChecked(!desk && browserPrefs.getBoolean(KEY_UA_ENABLED, false));

        swipecheck.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean("isSwipeReload", on).apply());
        bottomcheck.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean("hidebottom", on).apply());
        fixedUrlCheck.setOnCheckedChangeListener((b, on) -> {
            BrowserPrefs.setUrlBarFixed(activity, on);
            bottomcheck.setEnabled(!on);
        });
        updatecheck.setOnCheckedChangeListener((b, on) ->
                prefs.edit().putBoolean(UpdateChecker.KEY_ENABLED, on).apply());
        // デスクトップモードとガラケーの UA はどちらか一方だけ
        desktopcheck.setOnCheckedChangeListener((b, on) -> {
            if (on) uacheck.setChecked(false);
            browserPrefs.edit().putBoolean(KEY_DESKUA_ENABLED, on).apply();
        });
        uacheck.setOnCheckedChangeListener((b, on) -> {
            if (on) desktopcheck.setChecked(false);
            browserPrefs.edit().putBoolean(KEY_UA_ENABLED, on).apply();
        });

        Button helpButton = view.findViewById(R.id.setup_help_ua);
        helpButton.setOnClickListener(v -> {
            activity.startActivity(new Intent(activity, HelpActivity.class));
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
    }

    /** 一画面/二画面。2 つ目の画面がつながっている端末では、まだ選んでいなければ二画面を勧める。 */
    private void bindScreenMode(View view) {
        View single = view.findViewById(R.id.setup_screen_single);
        View dual = view.findViewById(R.id.setup_screen_dual);
        View tablet = view.findViewById(R.id.setup_screen_tablet);
        CompoundButton singleRadio = view.findViewById(R.id.setup_radio_single);
        CompoundButton dualRadio = view.findViewById(R.id.setup_radio_dual);
        CompoundButton tabletRadio = view.findViewById(R.id.setup_radio_tablet);
        tabletRadio.setText(Lang.t("タブレット"));
        ((TextView) view.findViewById(R.id.setup_tablet_desc)).setText(
                Lang.t("タブを上に並べ、URL バーとボトムバーを 1 本のツールバーにまとめます(パソコンのブラウザと同じ)"));
        TextView note = view.findViewById(R.id.setup_screen_note);

        boolean second = DualScreen.hasSecondScreen(activity);
        if (second && !DualScreen.isChosen(activity)) DualScreen.setEnabled(activity, true);
        // 大きい画面(タブレット)で、まだ選んでいなければタブレット UI を勧める
        if (!second && !DualScreen.isChosen(activity)
                && activity.getResources().getConfiguration().smallestScreenWidthDp >= 600) {
            TabletUi.setMode(activity, TabletUi.MODE_TABLET);
        }
        if (!second) {
            note.setText(Lang.t("この端末では 2 つ目の画面が見つかりませんでした。二画面を選んだ場合は、"
                    + "2 つ目の画面が使える時だけ二画面で表示します。あとから「設定」→「デザイン」→「画面」で変更できます。"));
        }

        Runnable refresh = () -> {
            int mode = TabletUi.mode(activity);
            singleRadio.setChecked(mode == TabletUi.MODE_SINGLE);
            dualRadio.setChecked(mode == TabletUi.MODE_DUAL);
            tabletRadio.setChecked(mode == TabletUi.MODE_TABLET);
            applySelectedOutline(single, mode == TabletUi.MODE_SINGLE);
            applySelectedOutline(dual, mode == TabletUi.MODE_DUAL);
            applySelectedOutline(tablet, mode == TabletUi.MODE_TABLET);
        };
        single.setOnClickListener(v -> {
            TabletUi.setMode(activity, TabletUi.MODE_SINGLE);
            refresh.run();
        });
        dual.setOnClickListener(v -> {
            TabletUi.setMode(activity, TabletUi.MODE_DUAL);
            refresh.run();
        });
        tablet.setOnClickListener(v -> {
            TabletUi.setMode(activity, TabletUi.MODE_TABLET);
            refresh.run();
        });
        refresh.run();
    }

    /** 選んでいるカードを枠で囲む。 */
    private void applySelectedOutline(View card, boolean selected) {
        if (!selected) {
            card.setForeground(ContextCompat.getDrawable(activity, outValue(android.R.attr.selectableItemBackground)));
            return;
        }
        float density = activity.getResources().getDisplayMetrics().density;
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(28 * density);
        outline.setStroke(Math.round(2 * density), ContextCompat.getColor(activity, R.color.setup_primary));
        android.graphics.drawable.Drawable ripple = ContextCompat.getDrawable(activity,
                outValue(android.R.attr.selectableItemBackground));
        card.setForeground(ripple != null ? new LayerDrawable(new android.graphics.drawable.Drawable[]{outline, ripple})
                : outline);
    }

    private int outValue(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(attr, tv, true);
        return tv.resourceId;
    }

    private void bindDefaultBrowser(View view) {
        Button defaultButton = view.findViewById(R.id.setup_default);
        defaultButton.setOnClickListener(v -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
            try {
                activity.startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                Toast.makeText(activity, Lang.t("この端末では設定画面を開けませんでした"), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------------------------------------------------------
    // テーマ
    // ------------------------------------------------------------------

    private int savedTheme() {
        return activity.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_THEME, THEME_SYSTEM);
    }

    private void applySavedTheme() {
        if (themeButton == null) return;
        switch (savedTheme()) {
            case THEME_LIGHT:
                themeButton.setImageResource(R.drawable.aozora_white);
                themeButton.setOnClickListener(v -> Toast.makeText(activity,
                        Lang.t("デバイス側でのダークモードの有無に関わらず、ライトモードにします."), Toast.LENGTH_SHORT).show());
                break;
            case THEME_DARK:
                themeButton.setImageResource(R.drawable.aozora_dark);
                themeButton.setOnClickListener(v -> Toast.makeText(activity,
                        Lang.t("デバイス側でのダークモードの有無に関わらず、ダークモードにします."), Toast.LENGTH_SHORT).show());
                break;
            case THEME_SYSTEM:
            default:
                themeButton.setImageResource(R.drawable.aozora_default);
                themeButton.setOnClickListener(v -> Toast.makeText(activity,
                        Lang.t("デバイス側でのダークモードの設定に合わせて、自動的に変更されます."), Toast.LENGTH_SHORT).show());
                break;
        }
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View) object);
        views.remove(position);
    }
}
