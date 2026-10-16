package com.airsky.browser;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * URL バーとメニューボタンの間に置くツールバーのショートカット。
 * 既定は「共有」。長押し、または 設定 › デザイン から、Aozora の各ツール・翻訳・更新・
 * タブを追加・ブックマークに追加・ホームページ などに変えられる。
 */
public final class ToolbarShortcut {

    public static final String NONE = "none";
    public static final String SHARE = "share";
    public static final String TRANSLATE = "translate";
    public static final String RELOAD = "reload";
    public static final String NEW_TAB = "new_tab";
    public static final String BOOKMARK = "bookmark";
    public static final String HOME = "home";
    public static final String READER = "reader";

    /** ショートカットの置き場所。0 は URL バーの横(スマートフォンでも使う)、1・2 はタブレット UI のツールバー。 */
    public static final int SLOT_MAIN = 0;
    public static final int SLOT_TABLET_1 = 1;
    public static final int SLOT_TABLET_2 = 2;

    /** 1 つの選択肢。 */
    public static final class Option {
        public final String key;
        public final String label;
        @DrawableRes public final int icon;
        /** 見ているページに対して働く(スタートページでは隠す)。 */
        public final boolean needsPage;

        Option(String key, String label, @DrawableRes int icon, boolean needsPage) {
            this.key = key;
            this.label = label;
            this.icon = icon;
            this.needsPage = needsPage;
        }
    }

    /** ページの操作。 */
    public static final Option[] PAGE = {
            new Option(SHARE, Lang.t("共有"), R.drawable.ic_pm_share, true),
            new Option(TRANSLATE, Lang.t("翻訳"), R.drawable.ic_pm_translate, true),
            new Option(RELOAD, Lang.t("更新"), R.drawable.ic_pm_reload, true),
            new Option(NEW_TAB, Lang.t("タブを追加"), R.drawable.ic_ps_add_tab, false),
            new Option(BOOKMARK, Lang.t("ブックマークに追加"), R.drawable.ic_pm_bookmark, true),
            new Option(HOME, Lang.t("ホームページ"), R.drawable.ic_menu_home, false),
            new Option(READER, Lang.t("リーディングモード"), R.drawable.ic_pm_reader, true),
    };

    /** Aozora のツール(メニューの「ツール」と同じ)。 */
    public static final Option[] TOOLS = {
            new Option("note", Lang.t("メモ"), R.drawable.ic_tool_note, false),
            new Option("calc", Lang.t("電卓"), R.drawable.ic_tool_calc, false),
            new Option("qr", Lang.t("QRコード作成"), R.drawable.ic_tool_qr, false),
            new Option("qr_scan", Lang.t("QRスキャン"), R.drawable.ic_tool_qr_scan, false),
            new Option("files", Lang.t("ファイル"), R.drawable.ic_tool_folder, false),
            new Option("save_page", Lang.t("ページ保存"), R.drawable.ic_tool_save, false),
            new Option("html", Lang.t("HTML エディタ"), R.drawable.ic_tool_code, false),
            new Option("screenshot", Lang.t("スクリーンショット"), R.drawable.ic_tool_screenshot, false),
            new Option("negapoji", Lang.t("ネガポジ"), R.drawable.ic_tool_invert, false),
            new Option("text_image", Lang.t("文字を画像に"), R.drawable.ic_tool_image, false),
            new Option("image_text", Lang.t("画像を文字に"), R.drawable.ic_tool_text, false),
            new Option("grep", "grep / MD5", R.drawable.ic_tool_search_file, false),
            new Option("ior1orl", "I or 1 or l", R.drawable.ic_tool_textfields, false),
            new Option("exec", "exec", R.drawable.ic_tool_terminal, false),
            new Option("battery", Lang.t("バッテリー"), R.drawable.ic_tool_battery, false),
            new Option("java", Lang.t("Java 実行"), R.drawable.ic_tool_java, false),
    };

    private static final Option NONE_OPTION = new Option(NONE, Lang.t("表示しない"), R.drawable.ic_ps_none, false);

    private ToolbarShortcut() {}

    @NonNull
    public static String key(@NonNull Context c) {
        return key(c, SLOT_MAIN);
    }

    @NonNull
    public static String key(@NonNull Context c, int slot) {
        String def = defaultKey(slot);
        String k = BrowserPrefs.prefs(c).getString(prefKey(slot), def);
        return find(k) != null ? k : def;
    }

    public static void set(@NonNull Context c, @NonNull String key) {
        set(c, SLOT_MAIN, key);
    }

    public static void set(@NonNull Context c, int slot, @NonNull String key) {
        BrowserPrefs.put(c, prefKey(slot), key);
    }

    @NonNull
    public static Option current(@NonNull Context c) {
        return current(c, SLOT_MAIN);
    }

    @NonNull
    public static Option current(@NonNull Context c, int slot) {
        Option o = find(key(c, slot));
        return o != null ? o : PAGE[0];
    }

    @NonNull
    private static String prefKey(int slot) {
        switch (slot) {
            case SLOT_TABLET_1: return BrowserPrefs.TOOLBAR_SHORTCUT_2;
            case SLOT_TABLET_2: return BrowserPrefs.TOOLBAR_SHORTCUT_3;
            default: return BrowserPrefs.TOOLBAR_SHORTCUT;
        }
    }

    @NonNull
    private static String defaultKey(int slot) {
        switch (slot) {
            case SLOT_TABLET_1: return BOOKMARK;
            case SLOT_TABLET_2: return READER;
            default: return SHARE;
        }
    }

    @Nullable
    public static Option find(@Nullable String key) {
        if (NONE.equals(key)) return NONE_OPTION;
        for (Option o : PAGE) if (o.key.equals(key)) return o;
        for (Option o : TOOLS) if (o.key.equals(key)) return o;
        return null;
    }

    // ------------------------------------------------------------------
    // 選ぶシート
    // ------------------------------------------------------------------

    /** ショートカットを選ぶシートを出す。選んだら保存して onChanged を呼ぶ。 */
    public static void showPicker(@NonNull Context context, @Nullable Runnable onChanged) {
        showPicker(context, SLOT_MAIN, onChanged);
    }

    /** slot のショートカットを選ぶシートを出す。 */
    public static void showPicker(@NonNull Context context, int slot, @Nullable Runnable onChanged) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        if (context instanceof android.app.Activity) TopSheet.adapt((android.app.Activity) context, dialog);
        Context c = dialog.getContext();
        float d = c.getResources().getDisplayMetrics().density;

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(d, 16), px(d, 8), px(d, 16), px(d, 24));

        TextView title = new TextView(c);
        title.setText(Lang.t("ツールバーのショートカット"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTextColor(attr(c, com.google.android.material.R.attr.colorOnSurface));
        title.setPadding(px(d, 8), px(d, 8), px(d, 8), px(d, 4));
        box.addView(title);
        TextView sub = new TextView(c);
        sub.setText(Lang.t("URL バーの横に置くボタンを選びます。ボタンを長押ししても変更できます。"));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setTextColor(attr(c, com.google.android.material.R.attr.colorOnSurfaceVariant));
        sub.setPadding(px(d, 8), 0, px(d, 8), px(d, 8));
        box.addView(sub);

        String current = key(c, slot);
        Runnable[] dismiss = new Runnable[1];
        addSection(c, box, Lang.t("ページ"), PAGE, slot, current, dismiss, onChanged);
        addSection(c, box, Lang.t("AirSky のツール"), TOOLS, slot, current, dismiss, onChanged);
        addSection(c, box, Lang.t("その他"), new Option[]{NONE_OPTION}, slot, current, dismiss, onChanged);

        ScrollView scroll = new ScrollView(c);
        scroll.addView(box);
        dialog.setContentView(scroll);
        dismiss[0] = dialog::dismiss;
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.show();
    }

    private static void addSection(Context c, LinearLayout box, String label, Option[] options, int slot, String current,
                                   Runnable[] dismiss, @Nullable Runnable onChanged) {
        float d = c.getResources().getDisplayMetrics().density;
        TextView h = new TextView(c);
        h.setText(label);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
        h.setTextColor(attr(c, androidx.appcompat.R.attr.colorPrimary));
        h.setPadding(px(d, 8), px(d, 18), px(d, 8), px(d, 8));
        box.addView(h);

        final int perRow = 4;
        LinearLayout row = null;
        for (int i = 0; i < options.length; i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            Option o = options[i];
            row.addView(tile(c, o, o.key.equals(current), v -> {
                set(c, slot, o.key);
                if (dismiss[0] != null) dismiss[0].run();
                if (onChanged != null) onChanged.run();
            }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        // 最後の行の余りを埋めて、タイルの幅をそろえる
        if (row != null) {
            for (int i = options.length % perRow; i != 0 && i < perRow; i++) {
                row.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
    }

    private static View tile(Context c, Option o, boolean selected, View.OnClickListener l) {
        float d = c.getResources().getDisplayMetrics().density;
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(px(d, 4), px(d, 10), px(d, 4), px(d, 10));
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        t.setBackgroundResource(tv.resourceId);
        t.setOnClickListener(l);
        t.setContentDescription(o.label);

        ImageView icon = new ImageView(c);
        icon.setImageResource(o.icon);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(d, 18));
        int fg;
        if (selected) {
            bg.setColor(attr(c, androidx.appcompat.R.attr.colorPrimary));
            fg = attr(c, com.google.android.material.R.attr.colorOnPrimary);
        } else {
            bg.setColor(attr(c, com.google.android.material.R.attr.colorSecondaryContainer));
            fg = attr(c, com.google.android.material.R.attr.colorOnSecondaryContainer);
        }
        icon.setBackground(bg);
        icon.setImageTintList(ColorStateList.valueOf(fg));
        t.addView(icon, new LinearLayout.LayoutParams(px(d, 56), px(d, 56)));

        TextView label = new TextView(c);
        label.setText(o.label);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTextColor(attr(c, com.google.android.material.R.attr.colorOnSurface));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(d, 6);
        t.addView(label, lp);
        return t;
    }

    private static int px(float density, int dp) {
        return Math.round(dp * density);
    }

    static int attr(Context c, int attr) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(attr, tv, true);
        if (tv.resourceId != 0) {
            try {
                return ContextCompat.getColor(c, tv.resourceId);
            } catch (Exception ignored) {
            }
        }
        return tv.data;
    }
}
