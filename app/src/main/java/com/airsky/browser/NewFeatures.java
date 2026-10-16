package com.airsky.browser;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.color.MaterialColors;

/**
 * 新機能。Pixel の「新機能」・ヒント アプリのように、色の付いたパネルから画面がのぞく大きなカードを並べ、
 * 押すと全画面の詳細を左右にスワイプして順に見られる。
 */
public class NewFeatures extends AppCompatActivity {

    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);

    /** 新機能 1 件。画面のスクリーンショットか、アイコンのどちらかを絵にする。 */
    private static final class Feature {
        final String title, summary, detail;
        @DrawableRes final int screenshot;
        @DrawableRes final int icon;

        Feature(String title, String summary, String detail, @DrawableRes int screenshot, @DrawableRes int icon) {
            this.title = title;
            this.summary = summary;
            this.detail = detail;
            this.screenshot = screenshot;
            this.icon = icon;
        }
    }

    private static final Feature[] FEATURES = {
            new Feature(Lang.t("さらに使いやすく、便利に"),
                    Lang.t("すべてを大幅リニューアル"),
                    Lang.t("AirSky Browser は、従来の Aozora をベースにしながらも、中核部分から細部に至るまですべてをリニューアル。\n" +
                            "「使っていて楽しいブラウザ」を目指して生まれ変わりました。"),
                    R.drawable.aozora_default, 0),
            new Feature(Lang.t("豊富な拡張機能に対応"),
                    Lang.t("FireFox と同じ拡張機能を AirSkyで"),
                    Lang.t("約8万個の Firefox 拡張機能に対応しています。\n" +
                            "メニュー内の「拡張機能」から検索・追加できるほか、.xpi ファイルを直接開いてインストールすることも可能です。"),
                    R.drawable.screenshot_20261016_extended, 0),
            new Feature(Lang.t("高度なセキュリティで、より安全に"),
                    Lang.t("Aozora の脆弱性を対策してより安全になりました。"),
                    Lang.t("新たにパスワードマネージャーを搭載しました。\n" +
                            "パスワードやクレジットカード情報、住所の管理がより簡単になり、端末本体の保護機能（生体認証など）を活用してさらに厳重に保護されます。"),
                    R.drawable.screenshot_20261016_settings, 0),
            new Feature(Lang.t("スマホで自作アプリを作成＆共有"),
                    Lang.t("Java, Python のプログラムを AirSkyで。"),
                    Lang.t("スマホで作成した自作プログラムを、AirSky Browser 上で直接動かせるようになりました。\n" +
                            "Python の Flet、Tkinter、wxPython などで作成したプログラムに対応しており、CUI（文字のみ）のプログラムも正常に動作します。\n" +
                            "スマホでコーディングし、AirSky Browser に取り込むだけ。.py ファイルを共有すれば、PC でも AirSky Browser 上でも、あなたが作ったアプリをそのまま動かすことができます。"),
                    R.drawable.screenshot_20261016_python, 0),

            //new Feature(Lang.t("高度なセキュリティで、より安全に"),
            //        Lang.t("Aozora の脆弱性を対策してより安全になりました。"),
            //        Lang.t("アップデートのお知らせから「更新する」を押すと、専用の画面でダウンロードの進み具合・速度を確認しながら最新版を入手できます。"
            //                + "端末に合ったファイルを自動で選び、ダウンロードが終わるとそのままインストールに進みます。"),
            //        0, R.drawable.ic_ct_download),
    };

    private float dp;
    private int onSurface, onVariant, cardColor, primary;
    private int[] panelColors, onPanelColors;
    private final SparseArray<Bitmap> images = new SparseArray<>();

    private View detail;
    private ViewPager2 pager;
    private LinearLayout dots;
    private TextView counter;
    private Button prev, next;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_newfeatures);
        TouchEffectView.attach(getWindow());
        dp = getResources().getDisplayMetrics().density;
        resolveColors();

        LinearLayout content = findViewById(R.id.tool_content);
        content.addView(buildHero());
        for (int i = 0; i < FEATURES.length; i++) {
            if (i == 3) content.addView(sectionLabel(Lang.t("これまでの新機能")));
            content.addView(buildCard(i));
        }
        setUpDetail();

        ToolChrome.setup(this, Lang.t("新機能"));
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (detail.getVisibility() == View.VISIBLE) hideDetail();
                else close();
            }
        });
    }

    private void resolveColors() {
        onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        onVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY);
        cardColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLowest, Color.WHITE);
        primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0xFF0B57D0);
        panelColors = new int[]{
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, 0xFFD3E3FD),
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiaryContainer, 0xFFFFD8E4),
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer, 0xFFC2E7FF),
        };
        onPanelColors = new int[]{
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFF041E49),
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnTertiaryContainer, 0xFF31111D),
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer, 0xFF001D35),
        };
    }

    // ------------------------------------------------------------------
    // 一覧
    // ------------------------------------------------------------------

    /** 一番上: アプリのアイコンとバージョン。 */
    private View buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(px(8), 0, px(8), px(20));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.aozora1);
        hero.addView(icon, new LinearLayout.LayoutParams(px(56), px(56)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        TextView name = text("AirSky Browser " + version, 20, onSurface);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(name);
        texts.addView(text(Lang.t("このバージョンで追加・変わったこと"), 14, onVariant));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(px(16));
        hero.addView(texts, tlp);
        return hero;
    }

    private TextView sectionLabel(String s) {
        TextView t = text(s, 14, primary);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(px(8), px(16), px(8), px(10));
        return t;
    }

    /** 色の付いたパネル(画面がのぞく)+ 見出しと説明の大きなカード。 */
    private View buildCard(int index) {
        Feature f = FEATURES[index];
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(cardColor, px(28)));
        card.setClipToOutline(true);
        card.setForeground(getDrawable(resolveAttr(android.R.attr.selectableItemBackground)));

        card.addView(buildArt(index, false), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(196)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(px(20), px(16), px(20), px(20));
        TextView title = text(f.title, 20, onSurface);
        texts.addView(title);
        TextView summary = text(f.summary, 14, onVariant);
        summary.setPadding(0, px(4), 0, 0);
        texts.addView(summary);
        card.addView(texts);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(12);
        card.setLayoutParams(lp);
        card.setOnClickListener(v -> showDetail(index));
        return card;
    }

    /**
     * 色の付いたパネルの絵。スクリーンショットは端末の画面のように角を丸めて置く
     * (一覧では下を切って上部だけを、詳細では全体を見せる)。
     */
    private View buildArt(int index, boolean whole) {
        Feature f = FEATURES[index];
        int panel = panelColors[index % panelColors.length];
        int onPanel = onPanelColors[index % onPanelColors.length];
        FrameLayout art = new FrameLayout(this);
        art.setBackground(round(panel, whole ? px(28) : 0));
        art.setClipToOutline(true);

        if (f.screenshot != 0) {
            Bitmap bmp = image(f.screenshot);
            if (whole) {
                ImageView shot = new ImageView(this);
                shot.setImageBitmap(bmp);
                shot.setScaleType(ImageView.ScaleType.FIT_CENTER);
                shot.setAdjustViewBounds(true);
                roundCorners(shot, px(20), false);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
                lp.topMargin = px(24);
                lp.bottomMargin = px(24);
                art.addView(shot, lp);
            } else {
                TopFitImageView shot = new TopFitImageView(this);
                shot.setImageBitmap(bmp);
                roundCorners(shot, px(18), true);
                shot.setElevation(px(2));
                // 画面の幅の 6 割ほどの端末が、パネルの下からのぞく
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                lp.topMargin = px(28);
                lp.leftMargin = px(64);
                lp.rightMargin = px(64);
                art.addView(shot, lp);
            }
        } else {
            FrameLayout circle = new FrameLayout(this);
            GradientDrawable c = new GradientDrawable();
            c.setShape(GradientDrawable.OVAL);
            c.setColor(onPanel);
            c.setAlpha(28);
            circle.setBackground(c);
            ImageView icon = new ImageView(this);
            icon.setImageResource(f.icon);
            icon.setImageTintList(ColorStateList.valueOf(onPanel));
            int iconSize = whole ? px(72) : px(48);
            circle.addView(icon, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
            int size = whole ? px(168) : px(112);
            art.addView(circle, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        }
        return art;
    }

    // ------------------------------------------------------------------
    // 詳細
    // ------------------------------------------------------------------

    private void setUpDetail() {
        detail = findViewById(R.id.nfDetail);
        pager = findViewById(R.id.nfPager);
        dots = findViewById(R.id.nfDots);
        counter = findViewById(R.id.nfDetailCounter);
        prev = findViewById(R.id.nfPrev);
        next = findViewById(R.id.nfNext);
        ((ImageView) findViewById(R.id.nfDetailClose)).setImageTintList(ColorStateList.valueOf(onSurface));
        findViewById(R.id.nfDetailClose).setOnClickListener(v -> hideDetail());

        pager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                FrameLayout holder = new FrameLayout(parent.getContext());
                holder.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                return new RecyclerView.ViewHolder(holder) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                FrameLayout frame = (FrameLayout) holder.itemView;
                frame.removeAllViews();
                frame.addView(buildPage(position));
            }

            @Override
            public int getItemCount() {
                return FEATURES.length;
            }
        });
        for (int i = 0; i < FEATURES.length; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(8), px(8));
            lp.setMarginStart(px(3));
            lp.setMarginEnd(px(3));
            dots.addView(dot, lp);
        }
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updatePagerControls(position);
            }
        });
        prev.setOnClickListener(v -> {
            int p = pager.getCurrentItem();
            if (p > 0) pager.setCurrentItem(p - 1);
        });
        next.setOnClickListener(v -> {
            int p = pager.getCurrentItem();
            if (p < FEATURES.length - 1) pager.setCurrentItem(p + 1);
            else hideDetail();
        });
    }

    private View buildPage(int index) {
        Feature f = FEATURES[index];
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(16), 0, px(16), 0);
        int artHeight = Math.round(getResources().getDisplayMetrics().heightPixels * 0.46f);
        page.addView(buildArt(index, true), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, artHeight));

        ScrollView scroll = new ScrollView(this);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(px(8), px(24), px(8), px(16));
        TextView title = text(f.title, 28, onSurface);
        texts.addView(title);
        TextView body = text(f.detail, 16, onVariant);
        body.setLineSpacing(px(4), 1f);
        body.setPadding(0, px(12), 0, 0);
        texts.addView(body);
        scroll.addView(texts);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return page;
    }

    private void updatePagerControls(int position) {
        counter.setText((position + 1) + " / " + FEATURES.length);
        prev.setVisibility(position > 0 ? View.VISIBLE : View.INVISIBLE);
        next.setText(position < FEATURES.length - 1 ? Lang.t("次へ") : Lang.t("完了"));
        for (int i = 0; i < dots.getChildCount(); i++) {
            View dot = dots.getChildAt(i);
            boolean on = i == position;
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(px(4));
            d.setColor(on ? primary : (onVariant & 0x00FFFFFF) | 0x52000000);
            dot.setBackground(d);
            ViewGroup.LayoutParams lp = dot.getLayoutParams();
            lp.width = on ? px(20) : px(8);
            dot.setLayoutParams(lp);
        }
    }

    private void showDetail(int index) {
        pager.setCurrentItem(index, false);
        updatePagerControls(index);
        detail.setVisibility(View.VISIBLE);
        detail.setAlpha(0f);
        detail.setTranslationY(px(48));
        detail.animate().alpha(1f).translationY(0f).setDuration(380).setInterpolator(EMPHASIZED).start();
    }

    private void hideDetail() {
        detail.animate().alpha(0f).translationY(px(48)).setDuration(220).setInterpolator(EMPHASIZED)
                .withEndAction(() -> detail.setVisibility(View.GONE)).start();
    }

    // ------------------------------------------------------------------
    // 部品
    // ------------------------------------------------------------------

    /** 大きなスクリーンショットは縮小して読み込む(何枚も並べるため)。 */
    @Nullable
    private Bitmap image(@DrawableRes int res) {
        Bitmap cached = images.get(res);
        if (cached != null) return cached;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), res, o);
        int target = Math.max(1, getResources().getDisplayMetrics().widthPixels * 2 / 3);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= target) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inScaled = false;
        Bitmap bmp = BitmapFactory.decodeResource(getResources(), res, o);
        if (bmp != null) images.put(res, bmp);
        return bmp;
    }

    private static void roundCorners(View v, float radius, boolean topOnly) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int extra = topOnly ? Math.round(radius) : 0;
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + extra, radius);
            }
        });
        v.setClipToOutline(true);
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private int resolveAttr(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.resourceId;
    }

    private static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    private void close() {
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }

    /** 画像を幅に合わせ、上端をそろえて表示する(下ははみ出た分を切る)。 */
    private static final class TopFitImageView extends AppCompatImageView {
        TopFitImageView(Context context) {
            super(context);
            setScaleType(ScaleType.MATRIX);
        }

        @Override
        protected boolean setFrame(int l, int t, int r, int b) {
            boolean changed = super.setFrame(l, t, r, b);
            Drawable d = getDrawable();
            if (d != null && d.getIntrinsicWidth() > 0) {
                float scale = (r - l) / (float) d.getIntrinsicWidth();
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                setImageMatrix(m);
            }
            return changed;
        }
    }
}
