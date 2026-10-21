package com.airsky.browser;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.slider.Slider;

/**
 * ユーザー補助機能。ページの文字の大きさ(デフォルトのズーム)とプレビュー、ズーム・カーソル・字幕など。
 */
public class AccessibilitySettingsActivity extends PixelSettingsActivity {

    private static final int MIN = 50;
    private static final int MAX = 300;
    private static final int STEP = 10;

    private TextView previewTitle;
    private TextView previewHeading;
    private TextView previewBody;

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("ユーザー補助機能");
    }

    @Override
    protected void onBuild() {
        boolean auto = BrowserPrefs.bool(this, BrowserPrefs.FONT_AUTO, false);
        gap();
        card(buildZoomCard(auto));
        card(buildPreview());
        updatePreview(auto ? 100 : BrowserPrefs.integer(this, BrowserPrefs.FONT_SCALE, 100));
        switchRow(R.drawable.ic_ps_text, Lang.t("端末の文字サイズに合わせる"),
                Lang.t("Android の設定の文字サイズで、ページの文字の大きさを決めます"), auto, on -> {
                    BrowserPrefs.put(this, BrowserPrefs.FONT_AUTO, on);
                    BrowserPrefs.apply(this);
                    rebuild();
                });

        header(Lang.t("ズーム"));
        SharedPreferences app = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        switchRow(R.drawable.ic_ps_zoom, Lang.t("強制的にズームを有効にする"), Lang.t("ウェブサイトのズーム防止リクエストを上書きします"),
                BrowserPrefs.bool(this, BrowserPrefs.FORCE_ZOOM, false), on -> {
                    BrowserPrefs.put(this, BrowserPrefs.FORCE_ZOOM, on);
                    BrowserPrefs.apply(this);
                });
        switchRow(R.drawable.ic_ps_touch, Lang.t("ダブルタップでズーム"), Lang.t("ページをダブルタップして拡大・縮小します"),
                BrowserPrefs.bool(this, BrowserPrefs.DOUBLE_TAP_ZOOM, true), on -> {
                    BrowserPrefs.put(this, BrowserPrefs.DOUBLE_TAP_ZOOM, on);
                    BrowserPrefs.apply(this);
                });

        header(Lang.t("操作と表示"));
        switchRow(R.drawable.ic_ps_keyboard, Lang.t("キーボードが接続されているときにテキスト カーソルを使ってページ内を移動する"),
                Lang.t("カーソル ブラウジングをオンまたはオフにするには、F7 キーを使用します"),
                BrowserPrefs.bool(this, BrowserPrefs.CARET, false), on -> {
                    BrowserPrefs.put(this, BrowserPrefs.CARET, on);
                    BrowserPrefs.apply(this);
                });
        switchRow(R.drawable.ic_ps_touch, Lang.t("タップを表示"), Lang.t("画面をタップした位置を表示します"),
                app.getBoolean("touchEffect", true), on -> app.edit().putBoolean("touchEffect", on).apply());
        row(R.drawable.ic_ps_captions, Lang.t("字幕"), Lang.t("動画の字幕の表示方法(Android の設定)"), () -> {
            try {
                startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
            } catch (Exception ignored) {
            }
        }).chevron();
    }

    private View buildZoomCard(boolean auto) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(18), dp(20), dp(14));
        box.setAlpha(auto ? 0.42f : 1f);

        TextView title = text(Lang.t("デフォルトのズーム"), 18, com.google.android.material.R.attr.colorOnSurface);
        box.addView(title);
        TextView sub = text(Lang.t("アクセスしたすべてのサイトでテキストと画像を拡大または縮小して表示します"), 15,
                com.google.android.material.R.attr.colorOnSurfaceVariant);
        sub.setPadding(0, dp(2), 0, 0);
        box.addView(sub);

        int value = BrowserPrefs.integer(this, BrowserPrefs.FONT_SCALE, 100);
        TextView percent = text(value + "%", 15, com.google.android.material.R.attr.colorOnSurfaceVariant);
        percent.setGravity(Gravity.CENTER);
        percent.setPadding(0, dp(14), 0, 0);
        box.addView(percent);

        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton minus = stepButton("−");
        ImageButton plus = stepButton("+");
        Slider slider = new Slider(this);
        slider.setValueFrom(MIN);
        slider.setValueTo(MAX);
        slider.setStepSize(STEP);
        slider.setValue(Math.max(MIN, Math.min(MAX, Math.round(value / (float) STEP) * STEP)));
        slider.setLabelBehavior(com.google.android.material.slider.LabelFormatter.LABEL_GONE);
        slider.setEnabled(!auto);
        slider.addOnChangeListener((s, v, fromUser) -> {
            percent.setText(Math.round(v) + "%");
            updatePreview(Math.round(v));
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider s) {}
            @Override
            public void onStopTrackingTouch(@NonNull Slider s) {
                save(Math.round(s.getValue()));
            }
        });
        minus.setOnClickListener(v -> step(slider, -STEP));
        plus.setOnClickListener(v -> step(slider, STEP));
        minus.setEnabled(!auto);
        plus.setEnabled(!auto);
        line.addView(minus, new LinearLayout.LayoutParams(dp(44), dp(44)));
        line.addView(slider, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        line.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(44)));
        box.addView(line);
        return box;
    }

    private void step(Slider slider, int delta) {
        float v = Math.max(MIN, Math.min(MAX, slider.getValue() + delta));
        slider.setValue(v);
        save(Math.round(v));
    }

    private void save(int value) {
        BrowserPrefs.put(this, BrowserPrefs.FONT_SCALE, value);
        BrowserPrefs.apply(this);
    }

    private ImageButton stepButton(String label) {
        ImageButton b = new ImageButton(this);
        b.setImageResource("+".equals(label) ? R.drawable.ic_si_add : R.drawable.ic_ps_progress);
        b.setImageTintList(ColorStateList.valueOf(attrColor(com.google.android.material.R.attr.colorOnSurface)));
        b.setScaleType(ImageView.ScaleType.CENTER);
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        b.setBackgroundResource(tv.resourceId);
        b.setContentDescription("+".equals(label) ? Lang.t("拡大") : Lang.t("縮小"));
        return b;
    }

    private View buildPreview() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(18), dp(20), dp(20));
        box.addView(text(Lang.t("プレビュー"), 18, com.google.android.material.R.attr.colorOnSurface));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_ps_reader);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(28), dp(28));
        ilp.topMargin = dp(16);
        box.addView(icon, ilp);
        previewTitle = text(Lang.t("青空のもとで"), 18, com.google.android.material.R.attr.colorOnSurface);
        previewTitle.setPadding(0, dp(12), 0, 0);
        box.addView(previewTitle);
        previewHeading = text(Lang.t("第 1 章: 窓の向こうのブラウザ"), 16, com.google.android.material.R.attr.colorOnSurface);
        previewHeading.setPadding(0, dp(10), 0, 0);
        box.addView(previewHeading);
        previewBody = text(Lang.t("AirSky で開いたページの文字は、この大きさで表示されます。"
                + "読みやすい大きさになるまで、上のスライダーで調整してください。"), 14,
                com.google.android.material.R.attr.colorOnSurfaceVariant);
        previewBody.setPadding(0, dp(8), 0, 0);
        box.addView(previewBody);
        return box;
    }

    private void updatePreview(int percent) {
        if (previewTitle == null) return;
        float f = percent / 100f;
        previewTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18 * f);
        previewHeading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16 * f);
        previewBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14 * f);
    }

    private TextView text(String s, int sp, int colorAttr) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLineSpacing(0, 1.15f);
        t.setTextColor(attrColor(colorAttr));
        return t;
    }
}
