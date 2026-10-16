package com.airsky.browser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

/**
 * ツールの「入力」。キーボード・マウス・コントローラーの設定を 1 枚のシートのタブにまとめる。
 *
 * <ul>
 *   <li>キーボード: 画面の下に出したままにするキーボード({@link SoftKeyboard})</li>
 *   <li>マウス: タッチで操作する仮想マウス({@link VirtualMouse})。トラックパッド/ダイレクト、カーソルの見た目</li>
 *   <li>コントローラー: 画面に重ねる仮想コントローラー({@link VirtualController})と、
 *       接続したコントローラーの使い方(ブラウザ操作/ページ入力)</li>
 * </ul>
 */
final class InputSheet {

    static final int TAB_KEYBOARD = 0;
    static final int TAB_MOUSE = 1;
    static final int TAB_CONTROLLER = 2;

    private static final String PREFS = "input_sheet";
    private static final String KEY_TAB = "tab";

    interface Host {
        /** キーボードの設定を反映する。 */
        void applySoftKeyboard();

        @NonNull
        VirtualMouse virtualMouse();

        @NonNull
        VirtualController virtualController();

        @NonNull
        GamepadInput gamepadInput();
    }

    private InputSheet() {
    }

    /** 前に開いていたタブで開く。 */
    static void show(@NonNull Activity activity, @NonNull Host host) {
        show(activity, host, activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TAB, TAB_KEYBOARD));
    }

    static void show(@NonNull Activity activity, @NonNull Host host, int tab) {
        BottomSheetDialog dialog = DualScreenController.newBottomSheet(activity);
        Context c = dialog.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Math.round(20 * d), Math.round(8 * d), Math.round(20 * d), Math.round(24 * d));

        TextView title = new TextView(c);
        title.setText(Lang.t("入力"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTextColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface));
        root.addView(title);

        String[] tabs = {Lang.t("キーボード"), Lang.t("マウス"), Lang.t("コントローラー")};
        int[] icons = {R.drawable.ic_tool_keyboard, R.drawable.ic_tool_mouse, R.drawable.ic_tool_gamepad};
        MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(c);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        int[] ids = new int[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            MaterialButton b = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setText(tabs[i]);
            // 英語では文字が長くなるので、アイコンは文字に収まる時だけ付ける
            if (!Lang.isEnglish()) {
                b.setIconResource(icons[i]);
                b.setIconSize(Math.round(18 * d));
                b.setIconPadding(Math.round(6 * d));
                b.setIconTint(ColorStateList.valueOf(
                        MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurfaceVariant)));
            }
            b.setPadding(Math.round(4 * d), b.getPaddingTop(), Math.round(4 * d), b.getPaddingBottom());
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            b.setMaxLines(1);
            ids[i] = View.generateViewId();
            b.setId(ids[i]);
            group.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = Math.round(12 * d);
        root.addView(group, glp);

        FrameLayout content = new FrameLayout(c);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Runnable[] showTab = new Runnable[1];
        int[] current = {Math.max(0, Math.min(2, tab))};
        showTab[0] = () -> {
            content.removeAllViews();
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(0, Math.round(12 * d), 0, 0);
            switch (current[0]) {
                case TAB_MOUSE:
                    buildMouse(activity, dialog, box, host, showTab[0]);
                    break;
                case TAB_CONTROLLER:
                    buildController(activity, dialog, box, host, showTab[0]);
                    break;
                default:
                    SoftKeyboard.buildSettings(box, host::applySoftKeyboard);
                    break;
            }
            content.addView(box);
        };
        group.check(ids[current[0]]);
        group.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == id && current[0] != i) {
                    current[0] = i;
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_TAB, i).apply();
                    showTab[0].run();
                }
            }
        });
        showTab[0].run();

        dialog.setContentView(SheetScroll.wrap(root));
        dialog.show();
    }

    // ------------------------------------------------------------------
    // マウス
    // ------------------------------------------------------------------

    private static void buildMouse(@NonNull Activity activity, @NonNull BottomSheetDialog dialog,
                                   @NonNull LinearLayout box, @NonNull Host host, @NonNull Runnable refresh) {
        Context c = box.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        VirtualMouse mouse = host.virtualMouse();

        box.addView(note(c, Lang.t("画面にマウスカーソルを出し、リモートデスクトップのようにタッチで操作します。"
                + "マウス前提のサイト(カーソルを合わせると開くメニュー、右クリック、ドラッグ)も使えます。")));

        MaterialSwitch on = new MaterialSwitch(c);
        on.setText(Lang.t("仮想マウスを使う"));
        on.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        on.setChecked(mouse.isTouchMode());
        on.setOnCheckedChangeListener((b, checked) -> {
            mouse.setActive(checked, true);
            if (checked) dialog.dismiss();
        });
        box.addView(on, matchWrap());

        box.addView(SoftKeyboard.sectionLabel(c, Lang.t("タッチ操作"), d));
        box.addView(SoftKeyboard.toggleGroup(c, VirtualMouse.STYLE_LABELS, VirtualMouse.touchStyle(c), i -> {
            VirtualMouse.setTouchStyle(c, i);
            refresh.run();
        }));
        box.addView(gestureList(c, VirtualMouse.touchStyle(c)));

        box.addView(SoftKeyboard.sectionLabel(c, Lang.t("タッチの速さ"), d));
        Slider speed = new Slider(c);
        speed.setValueFrom(1);
        speed.setValueTo(10);
        speed.setStepSize(1);
        speed.setValue(VirtualMouse.touchSpeed(c));
        speed.addOnChangeListener((s, v, fromUser) -> VirtualMouse.setTouchSpeed(c, Math.round(v)));
        box.addView(speed, matchWrap());

        box.addView(SoftKeyboard.sectionLabel(c, Lang.t("カーソルの色"), d));
        box.addView(colorRow(c, mouse));
        box.addView(SoftKeyboard.sectionLabel(c, Lang.t("カーソルの大きさ"), d));
        box.addView(SoftKeyboard.toggleGroup(c, VirtualMouse.SIZE_LABELS, VirtualMouse.cursorSize(c), i -> {
            VirtualMouse.setCursorSize(c, i);
            mouse.reloadStyle();
        }));

        box.addView(SoftKeyboard.sectionLabel(c, Lang.t("マウスを固定するページ"), d));
        box.addView(note(c, Lang.t("3D ゲームなど、マウスを固定して視点を動かすページでは、クリックするとカーソルが隠れ、"
                + "指やマウスの動きがそのまま視点の動きになります。Esc キー・戻る操作・3 本指でタップのどれかで解除できます。")));

        box.addView(SoftKeyboard.sectionLabel(c, Lang.t("カーソルを隠している時の速さ"), d));
        box.addView(note(c, Lang.t("タッチ・マウス・コントローラーで視点を動かす速さです。ふつうのカーソルの最大の 2 倍まで速くできます。")));
        Slider lockSpeed = new Slider(c);
        lockSpeed.setValueFrom(1);
        lockSpeed.setValueTo(VirtualMouse.MAX_LOCK_SPEED);
        lockSpeed.setStepSize(1);
        lockSpeed.setValue(VirtualMouse.lockSpeed(c));
        lockSpeed.setLabelFormatter(v -> {
            int n = Math.round(v);
            return n + (n == 5 ? Lang.t(" (標準)") : n == VirtualMouse.MAX_LOCK_SPEED ? Lang.t(" (最大)")
                    : n > 10 ? Lang.t(" (とても速い)") : "");
        });
        lockSpeed.addOnChangeListener((sl, v, fromUser) -> VirtualMouse.setLockSpeed(c, Math.round(v)));
        box.addView(lockSpeed, matchWrap());
    }

    private static View gestureList(@NonNull Context c, int style) {
        String text = style == VirtualMouse.STYLE_DIRECT
                ? Lang.t("・タップ: その場所をクリック\n・指で動かす: ドラッグ\n・長押し: 右クリック\n"
                + "・2 本指で動かす: スクロール\n・2 本指でタップ: 右クリック\n・2 本指を広げる/狭める: 拡大・縮小")
                : Lang.t("・1 本指で動かす: カーソルを動かす\n・タップ: クリック(2 回でダブルクリック)\n"
                + "・2 本指でタップ: 右クリック\n・2 本指で動かす: スクロール\n・2 本指を広げる/狭める: 拡大・縮小\n"
                + "・長押し、またはタップしてすぐ押し直して動かす: ドラッグ\n・3 本指でタップ: 中クリック");
        TextView t = note(c, text);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private static View colorRow(@NonNull Context c, @NonNull VirtualMouse mouse) {
        float d = c.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] colors = {0xFFFFFFFF, 0xFF1F1F1F,
                MaterialColors.getColor(c, androidx.appcompat.R.attr.colorPrimary, 0xFF0B57D0),
                0xFF6DD58C, 0xFFFDD663, 0xFFF8B5D0, 0xFFA8C7FA};
        int selected = VirtualMouse.cursorColor(c);
        int outline = MaterialColors.getColor(c, com.google.android.material.R.attr.colorOutline, 0xFF747775);
        int ring = MaterialColors.getColor(c, androidx.appcompat.R.attr.colorPrimary, 0xFF0B57D0);
        View[] swatches = new View[colors.length];
        for (int i = 0; i < colors.length; i++) {
            final int index = i;
            View v = new View(c);
            swatches[i] = v;
            v.setContentDescription(VirtualMouse.COLOR_LABELS[i]);
            v.setTooltipText(VirtualMouse.COLOR_LABELS[i]);
            v.setOnClickListener(x -> {
                VirtualMouse.setCursorColor(c, index);
                mouse.reloadStyle();
                for (int j = 0; j < swatches.length; j++) {
                    swatches[j].setBackground(swatch(colors[j], j == index, outline, ring, d));
                }
            });
            v.setBackground(swatch(colors[i], i == selected, outline, ring, d));
            int size = Math.round(36 * d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(Math.round(10 * d));
            row.addView(v, lp);
        }
        return row;
    }

    private static GradientDrawable swatch(int color, boolean selected, int outline, int ring, float d) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        if (selected) {
            g.setStroke(Math.round(3 * d), ring);
        } else {
            g.setStroke(Math.round(1 * d), outline);
        }
        return g;
    }

    // ------------------------------------------------------------------
    // コントローラー
    // ------------------------------------------------------------------

    private static void buildController(@NonNull Activity activity, @NonNull BottomSheetDialog dialog,
                                        @NonNull LinearLayout box, @NonNull Host host, @NonNull Runnable refresh) {
        Context c = box.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        VirtualController vc = host.virtualController();
        GamepadInput gamepad = host.gamepadInput();

        // ---- 仮想コントローラー ----
        box.addView(heading(c, Lang.t("仮想コントローラー")));
        box.addView(note(c, Lang.t("ページの上に半透明のボタンを重ねます。ボタンは好きな所に動かせて、"
                + "キーボードのキー・ゲームパッドのボタン(A B X Y など)・マウスのボタンを割り当てられます。")));
        MaterialSwitch show = new MaterialSwitch(c);
        show.setText(Lang.t("仮想コントローラーを表示"));
        show.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        show.setChecked(VirtualController.isShown(c));
        show.setOnCheckedChangeListener((b, on) -> {
            VirtualController.setShown(c, on);
            vc.apply();
        });
        box.addView(show, matchWrap());

        LinearLayout buttons = new LinearLayout(c);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton edit = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        edit.setText(Lang.t("配置と割り当てを編集"));
        edit.setOnClickListener(v -> {
            dialog.dismiss();
            vc.setEditing(true);
        });
        buttons.addView(edit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton opacity = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        opacity.setText(Lang.t("不透明度 ") + Math.round(VirtualController.opacity(c) * 100) + "%");
        opacity.setOnClickListener(v -> VirtualController.showOpacityDialog(activity, () -> {
            vc.applyOpacity();
            opacity.setText(Lang.t("不透明度 ") + Math.round(VirtualController.opacity(c) * 100) + "%");
        }));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.setMarginStart(Math.round(8 * d));
        buttons.addView(opacity, olp);
        LinearLayout.LayoutParams blp = matchWrap();
        blp.topMargin = Math.round(8 * d);
        box.addView(buttons, blp);

        // ---- 接続したコントローラー ----
        box.addView(heading(c, Lang.t("接続したコントローラー")));
        box.addView(SoftKeyboard.toggleGroup(c, new String[]{Lang.t("ブラウザ操作"), Lang.t("ページ入力")},
                gamepad.isPageMode() ? 1 : 0, i -> {
                    gamepad.setPageMode(i == 1);
                    refresh.run();
                }));
        box.addView(note(c, gamepad.isPageMode()
                ? Lang.t("ボタンとスティックをページ(ゲーム)へ送ります。ブラウザのメニューや戻るは動きません。"
                + "ボタンごとに、そのまま送る・キーボードのキーにする・別のボタンにすることができます。")
                : Lang.t("ボタンでメニュー・仮想マウス・戻る/進むなどのブラウザの操作をします。")));
        box.addView(note(c, Lang.t("SELECT と START を同時に押すと、いつでもブラウザ操作とページ入力を切り替えられます。")));

        LinearLayout links = new LinearLayout(c);
        links.setOrientation(LinearLayout.VERTICAL);
        MaterialButton pageMap = new MaterialButton(c, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        pageMap.setText(Lang.t("ページ入力の割り当て"));
        pageMap.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        pageMap.setOnClickListener(v -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, PadMappingActivity.class));
        });
        links.addView(pageMap, matchWrap());
        MaterialButton browserMap = new MaterialButton(c, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        browserMap.setText(Lang.t("ブラウザ操作の割り当てとカーソルの速さ"));
        browserMap.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        browserMap.setOnClickListener(v -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, ControllerSettingsActivity.class));
        });
        links.addView(browserMap, matchWrap());
        box.addView(links, matchWrap());
    }

    // ------------------------------------------------------------------

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static TextView note(@NonNull Context c, @NonNull CharSequence text) {
        float d = c.getResources().getDisplayMetrics().density;
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF444746));
        t.setPadding(0, Math.round(4 * d), 0, Math.round(8 * d));
        return t;
    }

    private static TextView heading(@NonNull Context c, @NonNull CharSequence text) {
        float d = c.getResources().getDisplayMetrics().density;
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurface, 0xFF1F1F1F));
        t.setPadding(0, Math.round(16 * d), 0, Math.round(2 * d));
        return t;
    }
}
