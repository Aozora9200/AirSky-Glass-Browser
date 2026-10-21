package com.airsky.browser;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.radiobutton.MaterialRadioButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Pixel の設定アプリ風の設定画面の土台。
 *
 * <p>大きな見出し(スクロールすると上のバーに小さく移る)の下に、角の丸いカードをつなげた
 * グループを並べる。グループの最初と最後だけ角を大きく、間は小さくして 1 つのまとまりに見せる。
 * 各画面は {@link #onBuild()} で {@link #header}・{@link #row}・{@link #switchRow} などを呼んで項目を作る。
 * 別の画面から戻った時は作り直すので、説明(現在の値)はいつも最新になる。</p>
 */
public abstract class PixelSettingsActivity extends AppCompatActivity {

    /** スイッチの切り替え。 */
    public interface BoolListener {
        void onChanged(boolean checked);
    }

    /** 選択肢の選択。 */
    public interface IntListener {
        void onSelected(int index);
    }

    /** 開いた時にスクロールして目立たせる項目({@link Row#key} で付けた名前)。 */
    public static final String EXTRA_FOCUS = "ps_focus";
    /** 開いた時にスクロールして目立たせる項目の見出し(設定の検索結果から開く時)。 */
    public static final String EXTRA_FOCUS_TITLE = "ps_focus_title";

    protected LinearLayout body;
    protected NestedScrollView scroll;
    private TextView bigTitle;
    private TextView barTitle;

    /** 今作っているグループの行(区切り = 見出し・説明・余白で閉じる)。 */
    private final List<View> group = new ArrayList<>();
    /** 名前を付けた行(EXTRA_FOCUS で探す)。 */
    private final java.util.Map<String, View> keyed = new java.util.HashMap<>();
    @Nullable private String pendingFocus;
    @Nullable private String pendingFocusTitle;
    /** 作った行と見出し(見出しの文字で探す)。 */
    private final List<android.util.Pair<String, View>> titled = new ArrayList<>();
    private final List<android.util.Pair<String, View>> headed = new ArrayList<>();

    @NonNull
    protected abstract CharSequence pageTitle();

    /** 項目を作る。作り直す時も呼ばれる。 */
    protected abstract void onBuild();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pixel_settings);
        TouchEffectView.attach(getWindow());
        body = findViewById(R.id.ps_body);
        scroll = findViewById(R.id.ps_scroll);
        bigTitle = findViewById(R.id.ps_title);
        barTitle = findViewById(R.id.ps_bar_title);
        findViewById(R.id.ps_back).setOnClickListener(v -> onBackPressed());

        pendingFocus = getIntent().getStringExtra(EXTRA_FOCUS);
        pendingFocusTitle = getIntent().getStringExtra(EXTRA_FOCUS_TITLE);
        CharSequence title = pageTitle();
        bigTitle.setText(title);
        barTitle.setText(title);
        // 大きな見出しが隠れたら、上のバーに小さな見出しを出す
        scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, x, y, ox, oy) -> {
            boolean hidden = y > bigTitle.getBottom() - bigTitle.getHeight() / 3;
            float target = hidden ? 1f : 0f;
            if (barTitle.getTag() == null || (float) barTitle.getTag() != target) {
                barTitle.setTag(target);
                barTitle.animate().alpha(target).setDuration(150).start();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        launching = false;
        rebuild();
    }

    // ------------------------------------------------------------------
    // 二度押しの対策
    // ------------------------------------------------------------------

    /** 項目を続けて押した時に、2 回目を無視する間隔。 */
    private static final long CLICK_GUARD_MS = 600;
    private long lastClickAt;
    /** 別の画面を開いている途中(戻ってくるまで、次の画面は開かない)。 */
    private boolean launching;

    /**
     * 行を素早く 2 回押した(ダブルタップ)なら true。2 回目の処理を飛ばす。
     * これが無いと、同じ設定画面やダイアログが 2 枚重なって開いてしまう。
     */
    protected boolean isDoubleClick() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastClickAt < CLICK_GUARD_MS) return true;
        lastClickAt = now;
        return false;
    }

    /**
     * 設定画面から開く画面は 1 枚だけにする。startActivity もここを通るので、
     * どの行・ボタンから開いても、戻ってくるまでに開こうとした 2 枚目は無視する。
     */
    @Override
    @SuppressWarnings("deprecation")
    public void startActivityForResult(@NonNull android.content.Intent intent, int requestCode,
                                       @Nullable Bundle options) {
        if (launching) return;
        launching = true;
        try {
            super.startActivityForResult(intent, requestCode, options);
        } catch (RuntimeException e) {
            launching = false;
            throw e;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        launching = false;
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** 項目をすべて作り直す(スクロール位置はそのまま)。 */
    protected void rebuild() {
        int y = scroll.getScrollY();
        body.removeAllViews();
        group.clear();
        keyed.clear();
        titled.clear();
        headed.clear();
        onBuild();
        closeGroup();
        View focus = pendingFocus != null ? keyed.get(pendingFocus) : null;
        if (focus == null && pendingFocusTitle != null) focus = findByTitle(pendingFocusTitle);
        pendingFocus = null;
        pendingFocusTitle = null;
        if (focus != null) {
            View target = focus;
            scroll.post(() -> focusRow(target));
        } else {
            scroll.post(() -> scroll.scrollTo(0, y));
        }
    }

    /**
     * 見出しの文字で行を探す。検索の索引の名前は画面の見出しと少し違うこと
     * (「セキュア DNS」と「セキュア DNS を使用する」、末尾の「(HTTPS)」など)があるので、ゆるく比べる。
     */
    @Nullable
    private View findByTitle(String query) {
        String q = normalizeTitle(query);
        if (q.isEmpty()) return null;
        // 行を先に、見出し(header)はあとに探す
        List<android.util.Pair<String, View>> all = new ArrayList<>(titled);
        all.addAll(headed);
        for (android.util.Pair<String, View> t : all) if (normalizeTitle(t.first).equals(q)) return t.second;
        for (android.util.Pair<String, View> t : all) if (normalizeTitle(t.first).contains(q)) return t.second;
        for (android.util.Pair<String, View> t : all) {
            String n = normalizeTitle(t.first);
            if (n.length() >= 2 && q.contains(n)) return t.second;
        }
        return null;
    }

    /** 括弧の中・空白・かぎ括弧を除いて小文字にする。 */
    private static String normalizeTitle(@Nullable String s) {
        if (s == null) return "";
        return s.replaceAll("[(（][^)）]*[)）]", "").replaceAll("[\\s「」]", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    /** 行が画面のまん中あたりに来るようにスクロールし、2 回点滅させて「ここ」と分かるようにする。 */
    private void focusRow(View row) {
        int top = 0;
        for (View v = row; v != null && v != scroll; v = (View) v.getParent()) top += v.getTop();
        int target = Math.max(0, top - (scroll.getHeight() - row.getHeight()) / 2);
        scroll.smoothScrollTo(0, target);
        if (target > bigTitle.getBottom() - bigTitle.getHeight() / 3) {
            barTitle.setTag(1f);
            barTitle.animate().alpha(1f).setDuration(150).start();
        }
        GradientDrawable glow = new GradientDrawable();
        glow.setCornerRadius(dp(26));
        glow.setColor(attrColor(androidx.appcompat.R.attr.colorPrimary));
        glow.setAlpha(0);
        row.setForeground(glow);
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofInt(0, 70, 0, 70, 0);
        a.setStartDelay(350);
        a.setDuration(1400);
        a.addUpdateListener(an -> glow.setAlpha((int) an.getAnimatedValue()));
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                row.setForeground(null);
            }
        });
        a.start();
        row.requestFocus();
    }

    /**
     * 画面の一番下に固定するバー(初回セットアップの「次へ」など)。項目はその上でスクロールする。
     */
    protected void setBottomBar(View bar) {
        LinearLayout root = findViewById(R.id.ps_root);
        LinearLayout.LayoutParams sp = (LinearLayout.LayoutParams) scroll.getLayoutParams();
        sp.height = 0;
        sp.weight = 1f;
        scroll.setLayoutParams(sp);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /**
     * 上のバーのすぐ下に、スクロールしない View を固定する(デザインのプレビューなど)。
     * 大きな見出しは隠し、見出しは上のバーに出したままにする。
     */
    protected void setPinnedHeader(View pinned) {
        LinearLayout root = findViewById(R.id.ps_root);
        root.addView(pinned, root.indexOfChild(findViewById(R.id.ps_bar)) + 1,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        bigTitle.setVisibility(View.GONE);
        scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) null);
        barTitle.animate().cancel();
        barTitle.setAlpha(1f);
        barTitle.setTag(1f);
    }

    /** 左上の戻るボタンを出すか(同意が必要な画面などでは隠す)。 */
    protected void setBackVisible(boolean visible) {
        findViewById(R.id.ps_back).setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    /** 右上のボタン(ヘルプなど)。 */
    protected void setAction(@DrawableRes int icon, String description, View.OnClickListener l) {
        ImageButton b = findViewById(R.id.ps_action);
        b.setImageResource(icon);
        b.setImageTintList(ColorStateList.valueOf(attrColor(com.google.android.material.R.attr.colorOnSurfaceVariant)));
        b.setContentDescription(description);
        b.setOnClickListener(l);
        b.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------
    // 項目を作る
    // ------------------------------------------------------------------

    /** グループの見出し(色付きの小さな文字)。 */
    protected TextView header(CharSequence text) {
        closeGroup();
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(attrColor(androidx.appcompat.R.attr.colorPrimary));
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(dp(20), dp(28), dp(20), dp(10));
        body.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        headed.add(new android.util.Pair<>(text.toString(), t));
        return t;
    }

    /** グループの間の余白(見出しなしで区切る)。 */
    protected void gap() {
        closeGroup();
        View v = new View(this);
        body.addView(v, new LinearLayout.LayoutParams(1, dp(20)));
    }

    /** カードの外の説明文。 */
    protected TextView note(CharSequence text) {
        closeGroup();
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setLineSpacing(0, 1.2f);
        t.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        t.setPadding(dp(20), dp(12), dp(20), dp(12));
        body.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    /** 押すと何かを開く行。 */
    protected Row row(@DrawableRes int icon, CharSequence title, @Nullable CharSequence summary,
                      @Nullable Runnable onClick) {
        Row r = newRow(icon, title, summary);
        if (onClick != null) {
            r.view.setOnClickListener(v -> {
                if (!isDoubleClick()) onClick.run();
            });
        } else {
            r.view.setClickable(false);
        }
        return r;
    }

    /** スイッチの行。行のどこを押しても切り替わる。 */
    protected Row switchRow(@DrawableRes int icon, CharSequence title, @Nullable CharSequence summary,
                            boolean checked, BoolListener listener) {
        Row r = newRow(icon, title, summary);
        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setChecked(checked);
        sw.setClickable(false);
        sw.setFocusable(false);
        sw.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        r.setEnd(sw);
        r.toggle = sw;
        r.view.setOnClickListener(v -> {
            boolean now = !sw.isChecked();
            sw.setChecked(now);
            listener.onChanged(now);
        });
        return r;
    }

    /** ラジオボタンの行。アイコンがある時は右端、ない時は左端に丸を出す。 */
    protected Row radioRow(@DrawableRes int icon, CharSequence title, @Nullable CharSequence summary,
                           boolean checked, Runnable onClick) {
        Row r = newRow(icon, title, summary);
        MaterialRadioButton rb = new MaterialRadioButton(this);
        rb.setChecked(checked);
        rb.setClickable(false);
        rb.setFocusable(false);
        rb.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        rb.setMinWidth(0);
        rb.setMinimumWidth(0);
        if (icon != 0) {
            r.setEnd(rb);
        } else {
            FrameLayout start = r.view.findViewById(R.id.ps_start);
            start.addView(rb);
            start.setVisibility(View.VISIBLE);
            ((LinearLayout.LayoutParams) start.getLayoutParams()).setMarginEnd(dp(12));
        }
        r.toggle = rb;
        r.view.setOnClickListener(v -> {
            if (!isDoubleClick()) onClick.run();
        });
        return r;
    }

    /** カードの中に好きな View を置く。 */
    protected View card(View content) {
        FrameLayout frame = new FrameLayout(this);
        frame.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addToGroup(frame);
        return frame;
    }

    /** カードの外に好きな View を置く。 */
    protected void plain(View content) {
        closeGroup();
        body.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private Row newRow(@DrawableRes int icon, CharSequence title, @Nullable CharSequence summary) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_ps_row, body, false);
        Row r = new Row(v);
        r.title.setText(title);
        titled.add(new android.util.Pair<>(title.toString(), v));
        r.setSummary(summary);
        if (icon != 0) {
            r.icon.setImageResource(icon);
            r.icon.setImageTintList(ColorStateList.valueOf(
                    attrColor(com.google.android.material.R.attr.colorOnSurfaceVariant)));
        } else {
            v.findViewById(R.id.ps_icon_box).setVisibility(View.GONE);
        }
        addToGroup(v);
        return r;
    }

    private void addToGroup(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!group.isEmpty()) lp.topMargin = dp(2);
        body.addView(v, lp);
        group.add(v);
    }

    /** グループを閉じて、最初と最後の角を大きくする。 */
    private void closeGroup() {
        int n = group.size();
        for (int i = 0; i < n; i++) {
            View v = group.get(i);
            v.setBackground(cardBackground(i == 0, i == n - 1));
            v.setClipToOutline(true);
        }
        group.clear();
    }

    private Drawable cardBackground(boolean first, boolean last) {
        float big = dp(26);
        float small = dp(5);
        float t = first ? big : small;
        float b = last ? big : small;
        float[] radii = {t, t, t, t, b, b, b, b};
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadii(radii);
        shape.setColor(ContextCompat.getColor(this, R.color.ps_card_bg));
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadii(radii);
        mask.setColor(0xFFFFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(
                attrColor(androidx.appcompat.R.attr.colorControlHighlight)), shape, mask);
    }

    // ------------------------------------------------------------------
    // 便利な部品
    // ------------------------------------------------------------------

    /**
     * 1 つ選ぶダイアログ。選択肢が「見出し\n説明」の時は、見出しを太字に、説明を小さな薄い文字にして段落を分ける。
     */
    protected void choose(CharSequence title, CharSequence[] labels, int checked, IntListener listener) {
        CharSequence[] styled = new CharSequence[labels.length];
        for (int i = 0; i < labels.length; i++) styled[i] = styleChoice(labels[i]);
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setSingleChoiceItems(styled, checked, (d, which) -> {
                    d.dismiss();
                    listener.onSelected(which);
                    rebuild();
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    /** 選択肢の見出し(1 行目)を太字に、説明(2 行目から)を小さく薄くする。 */
    protected CharSequence styleChoice(CharSequence label) {
        String s = label.toString();
        int nl = s.indexOf('\n');
        int end = nl >= 0 ? nl : s.length();
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(s);
        b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, end,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (nl >= 0) {
            int start = nl + 1;
            b.setSpan(new android.text.style.RelativeSizeSpan(0.86f), start, s.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new android.text.style.ForegroundColorSpan(
                            attrColor(com.google.android.material.R.attr.colorOnSurfaceVariant)), start, s.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            // 見出しと説明の間を少しあける
            b.setSpan(new android.text.style.LineHeightSpan() {
                @Override
                public void chooseHeight(CharSequence text, int st, int en, int spanstartv, int lineHeight,
                                         android.graphics.Paint.FontMetricsInt fm) {
                    if (st == start) {
                        fm.ascent -= dp(4);
                        fm.top -= dp(4);
                    }
                }
            }, start, s.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return b;
    }

    /** 確認してから実行する。 */
    protected void confirm(CharSequence title, CharSequence message, CharSequence ok, Runnable action) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(ok, (d, w) -> action.run())
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    protected void open(Class<?> activity) {
        startActivity(new Intent(this, activity));
    }

    protected int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    protected int attrColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        if (tv.resourceId != 0) return ContextCompat.getColor(this, tv.resourceId);
        return tv.data;
    }

    // ------------------------------------------------------------------

    /** 作った 1 行。説明や有効/無効をあとから変えられる。 */
    public class Row {
        public final View view;
        public final TextView title;
        public final TextView summary;
        public final ImageView icon;
        @Nullable public android.widget.CompoundButton toggle;

        Row(View view) {
            this.view = view;
            this.title = view.findViewById(R.id.ps_row_title);
            this.summary = view.findViewById(R.id.ps_row_summary);
            this.icon = view.findViewById(R.id.ps_icon);
        }

        /** 名前を付ける。EXTRA_FOCUS にこの名前を入れて開くと、この行までスクロールして目立たせる。 */
        public Row key(String name) {
            keyed.put(name, view);
            return this;
        }

        public Row setSummary(@Nullable CharSequence text) {
            summary.setText(text);
            summary.setVisibility(text == null || text.length() == 0 ? View.GONE : View.VISIBLE);
            return this;
        }

        public Row setEnabled(boolean enabled) {
            view.setEnabled(enabled);
            view.setAlpha(enabled ? 1f : 0.42f);
            return this;
        }

        /** トップの一覧のように、アイコンを色付きの丸に入れる。 */
        public Row tone(@ColorRes int bg, @ColorRes int fg) {
            View box = view.findViewById(R.id.ps_icon_box);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(ContextCompat.getColor(PixelSettingsActivity.this, bg));
            box.setBackground(circle);
            icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(PixelSettingsActivity.this, fg)));
            ((LinearLayout.LayoutParams) box.getLayoutParams()).setMarginEnd(dp(20));
            return this;
        }

        /** アイコンを色付けしないで出す(ファビコンや検索エンジンのロゴ)。 */
        public Row rawIcon(Drawable d) {
            icon.setImageTintList(null);
            icon.setImageDrawable(d);
            ViewGroup.LayoutParams lp = icon.getLayoutParams();
            lp.width = dp(32);
            lp.height = dp(32);
            icon.setLayoutParams(lp);
            return this;
        }

        /** 右端に値の文字を出す。 */
        public Row value(CharSequence text) {
            TextView t = new TextView(PixelSettingsActivity.this);
            t.setText(text);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t.setTextColor(attrColor(androidx.appcompat.R.attr.colorPrimary));
            t.setGravity(Gravity.END);
            setEnd(t);
            return this;
        }

        /** 右端に「>」を出す(別の画面に進む行)。 */
        public Row chevron() {
            ImageView iv = new ImageView(PixelSettingsActivity.this);
            iv.setImageResource(R.drawable.ic_si_chevron);
            iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            setEnd(iv);
            return this;
        }

        void setEnd(View v) {
            FrameLayout end = view.findViewById(R.id.ps_end);
            end.removeAllViews();
            end.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            end.setVisibility(View.VISIBLE);
        }
    }

    /** 設定画面を開き、名前を付けた行までスクロールして目立たせる。 */
    static void openFocused(Context c, Class<?> activity, String key) {
        c.startActivity(new Intent(c, activity).putExtra(EXTRA_FOCUS, key));
    }

    /** Context から Activity を開くための小さな補助。 */
    static void start(Context c, Class<?> activity) {
        c.startActivity(new Intent(c, activity));
    }
}
