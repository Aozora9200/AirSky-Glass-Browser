package com.airsky.browser;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

/**
 * 丸い色見本を横に並べて 1 色を選ばせる(Pixel の壁紙とスタイル風)。
 * 最後の「+」で、色相・彩度・明るさのスライダーと 16 進数で好きな色を作れる。
 */
final class ColorSwatches {

    /** 「同じ色」など、色ではない選択肢を表す値 */
    static final int NONE = 0;
    /** 透明(NONE と区別するため白の透明にする) */
    static final int TRANSPARENT = 0x00FFFFFF;

    interface Listener {
        void onPick(@ColorInt int color);
    }

    private ColorSwatches() {}

    /**
     * @param noneLabel NONE を選べるようにする時の名前(例: 「同じ」)。null なら出さない
     */
    static void build(@NonNull LinearLayout row, @NonNull int[] colors, @ColorInt int selected,
                      @Nullable String noneLabel, @NonNull Listener listener) {
        row.removeAllViews();
        Context ctx = row.getContext();
        if (noneLabel != null) addSwatch(row, NONE, noneLabel, selected == NONE, listener);
        boolean found = selected == NONE && noneLabel != null;
        for (int c : colors) {
            boolean on = c == selected;
            found |= on;
            addSwatch(row, c, null, on, listener);
        }
        // 自分で作った色: 選ばれている色が見本に無ければ、その色を「+」の場所に出す
        boolean preset = found;
        View custom = addSwatch(row, preset ? Color.TRANSPARENT : selected, "+", !preset, null);
        custom.setOnClickListener(v -> pick(ctx, preset ? Color.BLACK : selected, color -> {
            build(row, colors, color, noneLabel, listener);
            listener.onPick(color);
        }));
        custom.setContentDescription(Lang.t("好きな色を作る"));
    }

    private static View addSwatch(LinearLayout row, @ColorInt int color, @Nullable String label,
                                  boolean selected, @Nullable Listener listener) {
        Context ctx = row.getContext();
        float d = ctx.getResources().getDisplayMetrics().density;
        int size = Math.round(44 * d);
        FrameLayout box = new FrameLayout(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.rightMargin = Math.round(10 * d);
        box.setLayoutParams(lp);

        int outline = MaterialColors.getColor(row, com.google.android.material.R.attr.colorOutlineVariant);
        int primary = MaterialColors.getColor(row, androidx.appcompat.R.attr.colorPrimary);
        int onSurface = MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurface);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        boolean isPlus = "+".equals(label) && color == Color.TRANSPARENT;
        if (isPlus) {
            bg.setGradientType(GradientDrawable.SWEEP_GRADIENT);
            bg.setColors(new int[]{0xFFFF5252, 0xFFFFD740, 0xFF69F0AE, 0xFF40C4FF, 0xFF7C4DFF, 0xFFFF4081, 0xFFFF5252});
        } else if (label != null && !"+".equals(label) || color == TRANSPARENT) {
            bg.setColor(Color.TRANSPARENT);
        } else {
            bg.setColor(color);
        }
        bg.setStroke(Math.round((selected ? 3 : 1) * d), selected ? primary : outline);
        box.setBackground(bg);

        if (label != null && !"+".equals(label)) {
            TextView t = new TextView(ctx);
            t.setText(label);
            t.setTextSize(11);
            t.setTextColor(onSurface);
            t.setGravity(Gravity.CENTER);
            box.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (color == TRANSPARENT) {
            TextView t = new TextView(ctx);
            t.setText(Lang.t("透明"));
            t.setTextSize(11);
            t.setTextColor(onSurface);
            t.setGravity(Gravity.CENTER);
            box.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (isPlus) {
            TextView t = new TextView(ctx);
            t.setText("+");
            t.setTextSize(22);
            t.setTextColor(Color.WHITE);
            t.setGravity(Gravity.CENTER);
            box.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (selected && !isPlus && label == null && color != TRANSPARENT) {
            ImageView check = new ImageView(ctx);
            check.setImageResource(R.drawable.ic_ext_check);
            check.setImageTintList(ColorStateList.valueOf(
                    QrRenderer.contrast(color, Color.WHITE) > 2.2 ? Color.WHITE : Color.BLACK));
            int pad = Math.round(11 * d);
            check.setPadding(pad, pad, pad, pad);
            box.addView(check, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            check.setScaleX(0f);
            check.setScaleY(0f);
            check.animate().scaleX(1f).scaleY(1f).setDuration(260)
                    .setInterpolator(new OvershootInterpolator(2.5f)).start();
        }
        box.setClickable(true);
        box.setFocusable(true);
        if (listener != null) {
            box.setOnClickListener(v -> {
                v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction(() ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                                .setInterpolator(new OvershootInterpolator(3f)).start()).start();
                ViewGroup parent = (ViewGroup) v.getParent();
                // 選択の枠を付け替える(作り直すと横スクロール位置が戻るので、見た目だけ変える)
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    if (child.getBackground() instanceof GradientDrawable) {
                        ((GradientDrawable) child.getBackground()).setStroke(Math.round((child == v ? 3 : 1) * d),
                                child == v ? primary : outline);
                    }
                    if (child instanceof FrameLayout) {
                        FrameLayout f = (FrameLayout) child;
                        for (int k = f.getChildCount() - 1; k >= 0; k--) {
                            if (f.getChildAt(k) instanceof ImageView) f.removeViewAt(k);
                        }
                    }
                }
                if (label == null && color != TRANSPARENT) {
                    ImageView check = new ImageView(ctx);
                    check.setImageResource(R.drawable.ic_ext_check);
                    check.setImageTintList(ColorStateList.valueOf(
                            QrRenderer.contrast(color, Color.WHITE) > 2.2 ? Color.WHITE : Color.BLACK));
                    int pad = Math.round(11 * d);
                    check.setPadding(pad, pad, pad, pad);
                    ((FrameLayout) v).addView(check, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    check.setScaleX(0f);
                    check.setScaleY(0f);
                    check.animate().scaleX(1f).scaleY(1f).setDuration(260)
                            .setInterpolator(new OvershootInterpolator(2.5f)).start();
                }
                listener.onPick(color);
            });
        }
        row.addView(box);
        return box;
    }

    /** 色相・彩度・明るさのスライダーと 16 進数の入力で色を作るダイアログ */
    static void pick(@NonNull Context ctx, @ColorInt int initial, @NonNull Listener listener) {
        float d = ctx.getResources().getDisplayMetrics().density;
        float[] hsv = new float[3];
        Color.colorToHSV(initial, hsv);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * d);
        root.setPadding(pad, Math.round(8 * d), pad, 0);

        View preview = new View(ctx);
        GradientDrawable pv = new GradientDrawable();
        pv.setCornerRadius(20 * d);
        pv.setColor(initial);
        preview.setBackground(pv);
        root.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(72 * d)));

        Slider hue = slider(ctx, root, Lang.t("色相"), 0, 360, hsv[0]);
        Slider sat = slider(ctx, root, Lang.t("鮮やかさ"), 0, 100, hsv[1] * 100);
        Slider val = slider(ctx, root, Lang.t("明るさ"), 0, 100, hsv[2] * 100);

        TextInputLayout hexLayout = new TextInputLayout(ctx, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        hexLayout.setHint(Lang.t("16 進数 (#RRGGBB)"));
        TextInputEditText hex = new TextInputEditText(hexLayout.getContext());
        hex.setSingleLine(true);
        hex.setText(String.format(Locale.ROOT, "#%06X", initial & 0xFFFFFF));
        hexLayout.addView(hex);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Math.round(12 * d);
        root.addView(hexLayout, hlp);

        int[] current = {initial | 0xFF000000};
        boolean[] updating = {false};
        Runnable fromSliders = () -> {
            if (updating[0]) return;
            current[0] = Color.HSVToColor(new float[]{hue.getValue(), sat.getValue() / 100f, val.getValue() / 100f});
            pv.setColor(current[0]);
            updating[0] = true;
            hex.setText(String.format(Locale.ROOT, "#%06X", current[0] & 0xFFFFFF));
            updating[0] = false;
            tintSliders(current[0], hue, sat, val);
        };
        hue.addOnChangeListener((s, v, u) -> fromSliders.run());
        sat.addOnChangeListener((s, v, u) -> fromSliders.run());
        val.addOnChangeListener((s, v, u) -> fromSliders.run());
        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (updating[0]) return;
                String t = s.toString().trim();
                if (!t.startsWith("#")) t = "#" + t;
                if (t.length() != 7) return;
                try {
                    int c = Color.parseColor(t);
                    current[0] = c;
                    pv.setColor(c);
                    float[] h = new float[3];
                    Color.colorToHSV(c, h);
                    updating[0] = true;
                    hue.setValue(Math.round(h[0]));
                    sat.setValue(Math.round(h[1] * 100));
                    val.setValue(Math.round(h[2] * 100));
                    updating[0] = false;
                    tintSliders(c, hue, sat, val);
                } catch (IllegalArgumentException ignored) {
                }
            }
        });
        tintSliders(current[0], hue, sat, val);

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(Lang.t("色を作る"))
                .setView(root)
                .setPositiveButton(Lang.t("決定"), (dlg, w) -> listener.onPick(current[0]))
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    private static Slider slider(Context ctx, LinearLayout root, String label, float from, float to, float value) {
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Math.round(12 * ctx.getResources().getDisplayMetrics().density);
        root.addView(t, tlp);
        Slider s = new Slider(ctx);
        s.setValueFrom(from);
        s.setValueTo(to);
        s.setStepSize(1f);
        s.setValue(Math.max(from, Math.min(to, Math.round(value))));
        root.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    private static void tintSliders(@ColorInt int c, Slider... sliders) {
        ColorStateList csl = ColorStateList.valueOf(c);
        for (Slider s : sliders) {
            s.setTrackActiveTintList(csl);
            s.setThumbTintList(csl);
        }
    }
}
