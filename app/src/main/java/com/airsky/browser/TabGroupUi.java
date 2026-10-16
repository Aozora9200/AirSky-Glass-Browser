package com.airsky.browser;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** タブグループの色の選択・名前の入力ダイアログなど、グループの画面部品。 */
final class TabGroupUi {

    interface ColorListener {
        void onColor(int colorIndex);
    }

    private TabGroupUi() {}

    /** グループの色の丸。 */
    static Drawable dot(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /**
     * 9 色の選択(5 色 + 4 色の 2 段)。選んでいる色は角丸の四角に縁取りを付ける(Chrome と同じ)。
     * @param background 縁取りの内側の色(置く場所の背景色)
     */
    static View colorPicker(@NonNull Context context, int selected, int background, boolean forceDark,
                            @NonNull ColorListener listener) {
        float d = context.getResources().getDisplayMetrics().density;
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        int size = Math.round(48 * d);
        int margin = Math.round(7 * d);
        int[] rows = {5, 4};
        int index = 0;
        View[] swatches = new View[TabGroup.colorCount()];
        int[] current = {selected};
        for (int count : rows) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int i = 0; i < count && index < swatches.length; i++, index++) {
                final int colorIndex = index;
                View swatch = new View(context);
                swatch.setContentDescription(TabGroup.COLOR_NAMES[colorIndex]);
                swatch.setOnClickListener(v -> {
                    current[0] = colorIndex;
                    for (int k = 0; k < swatches.length; k++) {
                        swatches[k].setBackground(swatch(context, k, k == colorIndex, background, forceDark));
                        swatches[k].setSelected(k == colorIndex);
                    }
                    listener.onColor(colorIndex);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMargins(margin, margin, margin, margin);
                row.addView(swatch, lp);
                swatches[colorIndex] = swatch;
            }
            column.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        for (int k = 0; k < swatches.length; k++) {
            swatches[k].setBackground(swatch(context, k, k == current[0], background, forceDark));
            swatches[k].setSelected(k == current[0]);
        }
        return column;
    }

    private static Drawable swatch(Context context, int colorIndex, boolean selected, int background,
                                   boolean forceDark) {
        int color = TabGroup.colorValue(context, colorIndex, forceDark);
        if (!selected) return dot(color);
        float d = context.getResources().getDisplayMetrics().density;
        GradientDrawable outer = new GradientDrawable();
        outer.setCornerRadius(14 * d);
        outer.setColor(color);
        GradientDrawable inner = new GradientDrawable();
        inner.setCornerRadius(11 * d);
        inner.setColor(color);
        inner.setStroke(Math.round(3 * d), background);
        LayerDrawable layer = new LayerDrawable(new Drawable[]{outer, inner});
        int inset = Math.round(3 * d);
        layer.setLayerInset(1, inset, inset, inset, inset);
        return layer;
    }

    interface EditListener {
        void onDone(@NonNull String title, int colorIndex);
    }

    /**
     * 新しいグループ(「新しいタブグループ」)・名前の変更(「タブグループの名前を変更」)のダイアログ。
     * 名前を空のままにすると「N 個のタブ」と表示する。
     */
    static void showEditDialog(@NonNull Activity activity, @NonNull TabGroup group, int tabCount,
                               boolean isNew, @NonNull EditListener listener) {
        Context c = activity;
        float d = c.getResources().getDisplayMetrics().density;
        int pad = Math.round(24 * d);
        int primary = MaterialColors.getColor(c, androidx.appcompat.R.attr.colorPrimary, 0xFF445E91);
        int onVariant = MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF45464F);
        int surface = MaterialColors.getColor(c, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFFF3F3F6);

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, Math.round(8 * d), pad, 0);

        if (isNew) {
            TextView desc = new TextView(c);
            desc.setText(Lang.t("タブグループは自動的に保存され、閉じた後もタブ一覧の「タブグループ」から開き直せます"));
            desc.setTextColor(onVariant);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Math.round(16 * d);
            body.addView(desc, lp);
        }

        TextView label = new TextView(c);
        label.setText(Lang.t("タイトル"));
        label.setTextColor(primary);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        body.addView(label);

        String defaultTitle = tabCount + Lang.t(" 個のタブ");
        EditText field = new EditText(c);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setText(group.displayTitle(tabCount));
        field.setSelectAllOnFocus(true);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        body.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int[] color = {group.color};
        View picker = colorPicker(c, group.color, surface, false, idx -> color[0] = idx);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = Math.round(20 * d);
        body.addView(picker, plp);

        Runnable commit = () -> {
            String text = field.getText().toString().trim();
            // 既定の「N 個のタブ」のままなら名前は付けない(タブ数が変わったら表示も変わる)
            listener.onDone(text.equals(defaultTitle) ? "" : text, color[0]);
        };
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(c)
                .setTitle(isNew ? Lang.t("新しいタブグループ") : Lang.t("タブグループの名前を変更"))
                .setView(body);
        if (isNew) {
            b.setPositiveButton(Lang.t("完了"), null);
        } else {
            b.setNegativeButton(Lang.t("キャンセル"), null);
            b.setPositiveButton(Lang.t("続行"), (di, w) -> commit.run());
        }
        AlertDialog dialog = b.create();
        dialog.setOnDismissListener(di -> {
            // 閉じた後にキーボードが残らないようにする
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                    c.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
            // 新しいグループはもう作ってあるので、閉じ方にかかわらず入力内容を使う
            if (isNew) commit.run();
        });
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        field.requestFocus();
    }

    interface PickListener {
        void onPicked(@NonNull java.util.List<Integer> indices);
    }

    /** 「タブをグループにまとめる」: まとめるタブを選ぶ。 */
    static void showPickTabsDialog(@NonNull Activity activity, @NonNull TabSwitcher.Host host,
                                   @NonNull PickListener listener) {
        int count = host.getTabCount();
        if (count == 0) return;
        String[] labels = new String[count];
        boolean[] checked = new boolean[count];
        for (int i = 0; i < count; i++) {
            String title = host.getTabTitle(i);
            if (title == null || title.isEmpty()) title = host.getTabUrl(i);
            if (title == null || title.isEmpty()) title = Lang.t("新しいタブ");
            int gid = host.getTabGroupId(i);
            TabGroup g = gid >= 0 ? host.getGroup(gid) : null;
            labels[i] = g != null ? title + "(" + g.displayTitle(host.countTabsInGroup(gid)) + ")" : title;
            checked[i] = i == host.getCurrentTabIndex();
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(Lang.t("グループにまとめるタブを選択"))
                .setMultiChoiceItems(labels, checked, (di, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(Lang.t("キャンセル"), null)
                .setPositiveButton(Lang.t("グループを作成"), (di, w) -> {
                    java.util.List<Integer> picked = new java.util.ArrayList<>();
                    for (int i = 0; i < count; i++) if (checked[i]) picked.add(i);
                    if (!picked.isEmpty()) listener.onPicked(picked);
                })
                .create();
        dialog.show();
    }

    /** 「1 年前に更新」など。 */
    @NonNull
    static String updatedText(long updated) {
        long now = System.currentTimeMillis();
        if (updated <= 0) return "";
        long diff = Math.max(0, now - updated);
        long min = 60_000L, hour = 60 * min, day = 24 * hour;
        String ago;
        if (diff < min) {
            return Lang.t("たった今更新");
        } else if (diff < hour) {
            ago = (diff / min) + Lang.t(" 分前");
        } else if (diff < day) {
            ago = (diff / hour) + Lang.t(" 時間前");
        } else if (diff < 30 * day) {
            ago = (diff / day) + Lang.t(" 日前");
        } else if (diff < 365 * day) {
            ago = (diff / (30 * day)) + Lang.t(" か月前");
        } else {
            ago = (diff / (365 * day)) + Lang.t(" 年前");
        }
        return ago + Lang.t("に更新");
    }

    @Nullable
    static Activity activityOf(@NonNull View v) {
        Context c = v.getContext();
        while (c instanceof android.content.ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }
}
