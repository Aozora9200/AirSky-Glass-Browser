package com.airsky.browser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

/**
 * 画面の下に常に出しておくキーボード(ツールの「入力」のキーボード)。
 *
 * <p>Android のキーボードは入力欄に触れた時にしか出ないので、キー操作で遊ぶゲームや、入力欄を使わずに
 * キーを読むタイピングゲーム(寿司打など)では使えない。このキーボードは押したキーをそのままページへ
 * キー入力として送るので、入力欄が無くても使える。仮想マウスと組み合わせれば、コントローラーと
 * 画面のキーボードだけでパソコン向けのサイトを操作できる。</p>
 *
 * <ul>
 *   <li>ページ入力モード: 通常(離した時に 1 文字入力)/ ゲーム(押している間キーを押したまま、
 *       矢印キーなど付き)/ タイピング(触れた瞬間にローマ字のキーを送る)</li>
 *   <li>入力文字種: 通常(数字・英字・記号)/ 数字だけ / 英語だけ</li>
 * </ul>
 */
final class SoftKeyboard extends LinearLayout {

    // ------------------------------------------------------------------
    // 設定
    // ------------------------------------------------------------------

    static final int MODE_NORMAL = 0;
    static final int MODE_GAME = 1;
    static final int MODE_TYPING = 2;

    static final int TYPE_NORMAL = 0;
    static final int TYPE_NUMBER = 1;
    static final int TYPE_ENGLISH = 2;

    private static final String PREFS = "AppPrefs";
    private static final String KEY_SHOWN = "soft_kb_shown";
    private static final String KEY_MODE = "soft_kb_mode";
    private static final String KEY_TYPE = "soft_kb_type";

    static final String[] MODE_LABELS = {Lang.t("通常"), Lang.t("ゲーム"), Lang.t("タイピング")};
    static final String[] TYPE_LABELS = {Lang.t("通常"), Lang.t("数字だけ"), Lang.t("英語だけ")};

    private static SharedPreferences prefs(@NonNull Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isShown(@NonNull Context c) {
        return prefs(c).getBoolean(KEY_SHOWN, false);
    }

    static void setShown(@NonNull Context c, boolean shown) {
        prefs(c).edit().putBoolean(KEY_SHOWN, shown).apply();
    }

    static int mode(@NonNull Context c) {
        return clamp(prefs(c).getInt(KEY_MODE, MODE_NORMAL));
    }

    static int type(@NonNull Context c) {
        return clamp(prefs(c).getInt(KEY_TYPE, TYPE_NORMAL));
    }

    private static int clamp(int v) {
        return v < 0 || v > 2 ? 0 : v;
    }

    /** キーボードを置く画面。 */
    interface Host {
        /** 押したキーを送る相手(アドレスバーの入力中はアドレスバー、それ以外は表示中のタブ)。 */
        @Nullable
        View keyTarget();

        /** 表示・モードが変わった(閉じるボタンなど)。 */
        void onSoftKeyboardChanged();
    }

    /**
     * キーボードの設定(常時表示・ページ入力モード・入力文字種)を box に並べる。入力のシート
     * ({@link InputSheet})のキーボードのタブで使う。
     */
    static void buildSettings(@NonNull LinearLayout box, @NonNull Runnable onChanged) {
        Context c = box.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        TextView note = new TextView(c);
        note.setText(Lang.t("画面の下にキーボードを出したままにします。入力欄が無いゲームやタイピングサイト"
                + "(寿司打など)でも、押したキーがページに届きます。"));
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        note.setTextColor(MaterialColors.getColor(box, com.google.android.material.R.attr.colorOnSurfaceVariant));
        note.setPadding(0, Math.round(4 * d), 0, Math.round(12 * d));
        box.addView(note);

        MaterialSwitch shown = new MaterialSwitch(c);
        shown.setText(Lang.t("キーボードを表示"));
        shown.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        shown.setChecked(isShown(c));
        shown.setOnCheckedChangeListener((b, on) -> {
            setShown(c, on);
            onChanged.run();
        });
        box.addView(shown, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        box.addView(sectionLabel(c, Lang.t("ページ入力モード"), d));
        box.addView(toggleGroup(c, MODE_LABELS, mode(c), i -> {
            prefs(c).edit().putInt(KEY_MODE, i).apply();
            onChanged.run();
        }));
        TextView modeNote = new TextView(c);
        modeNote.setText(Lang.t("通常: 指を離した時に 1 文字入力  ゲーム: 押している間キーを押したまま(矢印キー付き)"
                + "  タイピング: 触れた瞬間にローマ字のキーを送る"));
        modeNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        modeNote.setTextColor(MaterialColors.getColor(box, com.google.android.material.R.attr.colorOnSurfaceVariant));
        modeNote.setPadding(0, Math.round(6 * d), 0, 0);
        box.addView(modeNote);

        box.addView(sectionLabel(c, Lang.t("入力文字種"), d));
        box.addView(toggleGroup(c, TYPE_LABELS, type(c), i -> {
            prefs(c).edit().putInt(KEY_TYPE, i).apply();
            onChanged.run();
        }));
    }

    interface IntListener {
        void onSelected(int index);
    }

    static TextView sectionLabel(@NonNull Context c, String text, float d) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(MaterialColors.getColor(c, androidx.appcompat.R.attr.colorPrimary, 0xFF1A73E8));
        t.setPadding(0, Math.round(18 * d), 0, Math.round(6 * d));
        return t;
    }

    static View toggleGroup(@NonNull Context c, String[] labels, int checked, IntListener l) {
        MaterialButtonToggleGroup g = new MaterialButtonToggleGroup(c);
        g.setSingleSelection(true);
        g.setSelectionRequired(true);
        int[] ids = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            MaterialButton b = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setText(labels[i]);
            ids[i] = View.generateViewId();
            b.setId(ids[i]);
            g.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        g.check(ids[Math.max(0, Math.min(checked, ids.length - 1))]);
        g.addOnButtonCheckedListener((group, id, isChecked) -> {
            if (!isChecked) return;
            for (int i = 0; i < ids.length; i++) if (ids[i] == id) l.onSelected(i);
        });
        return g;
    }

    // ------------------------------------------------------------------
    // パネル(上の帯 + キー)
    // ------------------------------------------------------------------

    private final Host host;
    private final TextView modeChip;
    private final TextView typeChip;
    private final Keys keys;

    SoftKeyboard(@NonNull Context context, @NonNull Host host) {
        super(context);
        this.host = host;
        setOrientation(VERTICAL);
        setClickable(true); // 下のページへタッチを通さない
        setFocusable(false);
        setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer));
        int pad = dp(4);
        setPadding(pad, 0, pad, pad);

        LinearLayout strip = new LinearLayout(context);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        modeChip = chip();
        modeChip.setOnClickListener(v -> {
            prefs(getContext()).edit().putInt(KEY_MODE, (mode(getContext()) + 1) % 3).apply();
            refresh();
            host.onSoftKeyboardChanged();
        });
        typeChip = chip();
        typeChip.setOnClickListener(v -> {
            prefs(getContext()).edit().putInt(KEY_TYPE, (type(getContext()) + 1) % 3).apply();
            refresh();
            host.onSoftKeyboardChanged();
        });
        strip.addView(modeChip);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.setMarginStart(dp(6));
        strip.addView(typeChip, gap);
        strip.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));
        ImageButton ime = stripButton(R.drawable.ic_tool_keyboard, Lang.t("日本語入力(端末のキーボード)"));
        ime.setOnClickListener(v -> showSystemIme());
        strip.addView(ime);
        ImageButton close = stripButton(R.drawable.ic_close, Lang.t("キーボードを閉じる"));
        close.setOnClickListener(v -> {
            releaseAll();
            setShown(getContext(), false);
            host.onSoftKeyboardChanged();
        });
        strip.addView(close);
        addView(strip, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        keys = new Keys(context);
        addView(keys, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        refresh();
    }

    /** 設定(モード・文字種)を読み直してキーを並べ直す。 */
    void refresh() {
        int m = mode(getContext());
        int t = type(getContext());
        modeChip.setText(Lang.t("モード: ") + MODE_LABELS[m]);
        typeChip.setText(Lang.t("文字: ") + TYPE_LABELS[t]);
        keys.setLayout(m, t);
    }

    /** 押したままのキーを全部離す(閉じる・画面を離れる時)。 */
    void releaseAll() {
        keys.releaseAll();
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }

    private TextView chip() {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(5), dp(12), dp(5));
        t.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer));
        t.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(
                MaterialColors.getColor(this, androidx.appcompat.R.attr.colorControlHighlight)), bg, null));
        t.setFocusable(false);
        return t;
    }

    private ImageButton stripButton(int icon, String description) {
        ImageButton b = new ImageButton(getContext());
        b.setImageResource(icon);
        b.setImageTintList(ColorStateList.valueOf(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)));
        TypedValue tv = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        b.setBackgroundResource(tv.resourceId);
        b.setContentDescription(description);
        b.setTooltipText(description);
        b.setFocusable(false);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(36)));
        return b;
    }

    /** 端末のキーボード(日本語入力など)を出す。ページの入力欄に入力中の時だけ使える。 */
    private void showSystemIme() {
        View target = host.keyTarget();
        if (target == null) return;
        // ページの入力の窓口が別の画面にある時(二画面の下の画面)はそちらへ出す
        if (target instanceof GeckoTabView) {
            View v = ((GeckoTabView) target).getGeckoSession().getTextInput().getView();
            if (v != null) target = v;
        }
        InputMethodManager imm = (InputMethodManager) target.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            GeckoTabView.allowSoftInputOnce = true;
            target.requestFocus();
            imm.showSoftInput(target, 0);
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------
    // キーをページへ送る
    // ------------------------------------------------------------------

    private static final int META_NONE = 0;

    /**
     * キーを送る相手。ページがフォーカスを持っていないとページにキーが届かない(入力欄に触れていない
     * ゲームなど)ので、ページへフォーカスを渡してから返す。
     */
    @Nullable
    private View focusedTarget() {
        return PageKeys.focus(host.keyTarget());
    }

    private void sendKey(int action, int keyCode, int meta, long downTime) {
        View target = focusedTarget();
        if (target == null) return;
        PageKeys.send(target, action, keyCode, meta, downTime, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);
    }

    /** キーを 1 回押して離す。 */
    private void tapKey(int keyCode, int meta) {
        long t = SystemClock.uptimeMillis();
        sendKey(KeyEvent.ACTION_DOWN, keyCode, meta, t);
        sendKey(KeyEvent.ACTION_UP, keyCode, meta, t);
    }

    /** 文字を入力する。キーで打てる文字はキーとして、打てない文字はそのまま文字として送る。 */
    private void typeText(@NonNull String text) {
        View target = focusedTarget();
        if (target == null) return;
        KeyEvent[] events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray());
        if (events != null) {
            for (KeyEvent e : events) {
                long now = SystemClock.uptimeMillis();
                target.dispatchKeyEvent(new KeyEvent(now, now, e.getAction(), e.getKeyCode(), 0, e.getMetaState(),
                        KeyCharacterMap.VIRTUAL_KEYBOARD, PageKeys.scanCodeFor(e.getKeyCode()),
                        e.getFlags() | KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE,
                        InputDevice.SOURCE_KEYBOARD));
            }
        } else {
            target.dispatchKeyEvent(new KeyEvent(SystemClock.uptimeMillis(), text,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, KeyEvent.FLAG_SOFT_KEYBOARD));
        }
    }

    // ------------------------------------------------------------------
    // キーの並び
    // ------------------------------------------------------------------

    private static final int K_CHAR = 0;
    private static final int K_SHIFT = 1;
    private static final int K_SYMBOLS = 2;
    /** キーコードをそのまま送るキー(Enter・矢印・Esc など)。 */
    private static final int K_CODE = 3;
    private static final int K_CTRL = 4;

    private static final class Key {
        final String label;
        /** 入力する文字(K_CHAR)。 */
        final String text;
        final int kind;
        final int code;
        final float weight;
        final boolean special;
        final RectF rect = new RectF();

        Key(String label, String text, int kind, int code, float weight, boolean special) {
            this.label = label;
            this.text = text;
            this.kind = kind;
            this.code = code;
            this.weight = weight;
            this.special = special;
        }

        boolean repeats() {
            return kind == K_CODE && (code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_DPAD_LEFT
                    || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_UP
                    || code == KeyEvent.KEYCODE_DPAD_DOWN);
        }
    }

    private static Key ch(String s) {
        return new Key(s, s, K_CHAR, codeFor(s), 1f, false);
    }

    private static Key ch(String s, float w) {
        return new Key(s, s, K_CHAR, codeFor(s), w, false);
    }

    private static Key code(String label, int code, float w) {
        return new Key(label, "", K_CODE, code, w, true);
    }

    private static Key space(float w) {
        return new Key("space", " ", K_CHAR, KeyEvent.KEYCODE_SPACE, w, false);
    }

    /** 文字に対応するキーコード(英数字と一部の記号)。無ければ 0。 */
    private static int codeFor(String s) {
        if (s.length() != 1) return 0;
        char c = Character.toLowerCase(s.charAt(0));
        if (c >= 'a' && c <= 'z') return KeyEvent.KEYCODE_A + (c - 'a');
        if (c >= '0' && c <= '9') return KeyEvent.KEYCODE_0 + (c - '0');
        switch (c) {
            case ' ': return KeyEvent.KEYCODE_SPACE;
            case '-': return KeyEvent.KEYCODE_MINUS;
            case ',': return KeyEvent.KEYCODE_COMMA;
            case '.': return KeyEvent.KEYCODE_PERIOD;
            case '/': return KeyEvent.KEYCODE_SLASH;
            case ';': return KeyEvent.KEYCODE_SEMICOLON;
            case '\'': return KeyEvent.KEYCODE_APOSTROPHE;
            case '[': return KeyEvent.KEYCODE_LEFT_BRACKET;
            case ']': return KeyEvent.KEYCODE_RIGHT_BRACKET;
            case '=': return KeyEvent.KEYCODE_EQUALS;
            case '`': return KeyEvent.KEYCODE_GRAVE;
            case '\\': return KeyEvent.KEYCODE_BACKSLASH;
            default: return 0;
        }
    }

    private static List<Key> row(String chars) {
        List<Key> r = new ArrayList<>();
        for (int i = 0; i < chars.length(); i++) r.add(ch(String.valueOf(chars.charAt(i))));
        return r;
    }

    private static List<List<Key>> buildLayout(int mode, int type, boolean symbols) {
        List<List<Key>> rows = new ArrayList<>();
        Key bs = code("⌫", KeyEvent.KEYCODE_DEL, 1.5f);
        Key enter = code("⏎", KeyEvent.KEYCODE_ENTER, 1.5f);
        if (type == TYPE_NUMBER) {
            List<Key> r1 = row("123");
            r1.add(code("⌫", KeyEvent.KEYCODE_DEL, 1f));
            List<Key> r2 = row("456");
            r2.add(ch("-"));
            List<Key> r3 = row("789");
            r3.add(code("⏎", KeyEvent.KEYCODE_ENTER, 1f));
            List<Key> r4 = new ArrayList<>();
            r4.add(ch("."));
            r4.add(ch("0"));
            r4.add(ch(","));
            r4.add(space(1f));
            rows.add(r1);
            rows.add(r2);
            rows.add(r3);
            rows.add(r4);
            if (mode == MODE_GAME) rows.add(arrowRow(false));
            return rows;
        }
        if (symbols && mode != MODE_GAME) {
            rows.add(row("!@#$%^&*()"));
            rows.add(row("~`|\\=+_[]{}"));
            List<Key> r3 = row(";:'\"<>?/");
            r3.add(bs);
            rows.add(r3);
            List<Key> r4 = new ArrayList<>();
            r4.add(new Key("ABC", "", K_SYMBOLS, 0, 1.5f, true));
            r4.add(ch(","));
            r4.add(space(5f));
            r4.add(ch("."));
            r4.add(enter);
            rows.add(r4);
            return rows;
        }
        if (type == TYPE_NORMAL) rows.add(row("1234567890"));
        rows.add(row("qwertyuiop"));
        List<Key> r2 = row("asdfghjkl");
        r2.add(ch("-"));
        rows.add(r2);
        List<Key> r3 = new ArrayList<>();
        r3.add(new Key("⇧", "", K_SHIFT, KeyEvent.KEYCODE_SHIFT_LEFT, 1.5f, true));
        r3.addAll(row("zxcvbnm"));
        r3.add(bs);
        rows.add(r3);
        List<Key> r4 = new ArrayList<>();
        if (mode == MODE_GAME) {
            r4.add(code("Esc", KeyEvent.KEYCODE_ESCAPE, 1.2f));
            r4.add(code("Tab", KeyEvent.KEYCODE_TAB, 1.2f));
            r4.add(new Key("Ctrl", "", K_CTRL, KeyEvent.KEYCODE_CTRL_LEFT, 1.3f, true));
            r4.add(space(4f));
            r4.add(code("⏎", KeyEvent.KEYCODE_ENTER, 1.3f));
            rows.add(r4);
            rows.add(arrowRow(true));
            return rows;
        }
        if (type == TYPE_NORMAL) {
            r4.add(new Key(Lang.t("記号"), "", K_SYMBOLS, 0, 1.5f, true));
        } else {
            r4.add(ch("'", 1.5f));
        }
        r4.add(ch(","));
        r4.add(space(5f));
        r4.add(ch("."));
        r4.add(enter);
        rows.add(r4);
        return rows;
    }

    /** ゲーム用の矢印キーの段。 */
    private static List<Key> arrowRow(boolean wide) {
        List<Key> r = new ArrayList<>();
        float w = wide ? 2.5f : 1f;
        r.add(code("←", KeyEvent.KEYCODE_DPAD_LEFT, w));
        r.add(code("↑", KeyEvent.KEYCODE_DPAD_UP, w));
        r.add(code("↓", KeyEvent.KEYCODE_DPAD_DOWN, w));
        r.add(code("→", KeyEvent.KEYCODE_DPAD_RIGHT, w));
        return r;
    }

    // ------------------------------------------------------------------
    // キーを描いて、触れた指ごとに押す・離す
    // ------------------------------------------------------------------

    private final class Keys extends View {
        private List<List<Key>> rows = new ArrayList<>();
        private int mode;
        private int type;
        private boolean symbols;
        /** 次の 1 文字だけ大文字(通常・タイピング)/ 押している間 Shift(ゲーム)。 */
        private boolean shift;
        private boolean capsLock;
        private long lastShiftTap;
        private boolean ctrl;
        /** 触れている指ごとの押しているキー。 */
        private final SparseArray<Key> pressed = new SparseArray<>();
        /** ゲームモードで押したままのキーの押した時刻(離す時に同じ時刻を使う)。 */
        private final SparseArray<Long> downTimes = new SparseArray<>();
        private final Handler handler = new Handler(Looper.getMainLooper());
        @Nullable private Key repeating;

        private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int keyColor;
        private final int specialColor;
        private final int pressedColor;
        private final int activeColor;
        private final int textColor;
        private final int activeTextColor;

        private final Runnable repeat = new Runnable() {
            @Override
            public void run() {
                Key k = repeating;
                if (k == null) return;
                tapKey(k.code, META_NONE);
                handler.postDelayed(this, 50);
            }
        };

        Keys(Context context) {
            super(context);
            setFocusable(false);
            keyColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest);
            specialColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer);
            pressedColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer);
            activeColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
            textColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface);
            activeTextColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        void setLayout(int mode, int type) {
            releaseAll();
            this.mode = mode;
            this.type = type;
            if (type != TYPE_NORMAL) symbols = false;
            shift = false;
            capsLock = false;
            rows = buildLayout(mode, type, symbols);
            requestLayout();
            invalidate();
        }

        private int rowHeight() {
            return dp(type == TYPE_NUMBER ? 46 : 42);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(w, rows.size() * rowHeight());
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            layoutKeys();
        }

        private void layoutKeys() {
            int w = getWidth();
            if (w <= 0) return;
            float max = 0;
            for (List<Key> r : rows) {
                float sum = 0;
                for (Key k : r) sum += k.weight;
                max = Math.max(max, sum);
            }
            if (max <= 0) return;
            float unit = w / max;
            float gap = dp(3);
            int rh = rowHeight();
            for (int i = 0; i < rows.size(); i++) {
                List<Key> r = rows.get(i);
                float sum = 0;
                for (Key k : r) sum += k.weight;
                float x = (w - sum * unit) / 2f; // 短い段は真ん中に寄せる
                float y = i * rh;
                for (Key k : r) {
                    float kw = k.weight * unit;
                    k.rect.set(x + gap / 2f, y + gap / 2f, x + kw - gap / 2f, y + rh - gap / 2f);
                    x += kw;
                }
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (rows.isEmpty()) return;
            if (rows.get(0).get(0).rect.isEmpty()) layoutKeys();
            float radius = dp(8);
            for (List<Key> r : rows) {
                for (Key k : r) {
                    boolean down = isPressed(k);
                    boolean active = (k.kind == K_SHIFT && (shift || capsLock)) || (k.kind == K_CTRL && ctrl);
                    keyPaint.setColor(down ? pressedColor : active ? activeColor : k.special ? specialColor : keyColor);
                    canvas.drawRoundRect(k.rect, radius, radius, keyPaint);
                    String label = label(k);
                    textPaint.setColor(active && !down ? activeTextColor : textColor);
                    float size = label.length() > 2 ? 13 : 18;
                    textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size,
                            getResources().getDisplayMetrics()));
                    float ty = k.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;
                    canvas.drawText(label, k.rect.centerX(), ty, textPaint);
                }
            }
        }

        private String label(Key k) {
            if (k.kind == K_CHAR && (shift || capsLock) && mode != MODE_GAME && k.text.length() == 1
                    && Character.isLetter(k.text.charAt(0))) {
                return k.text.toUpperCase(java.util.Locale.ROOT);
            }
            if (k.kind == K_SHIFT && capsLock) return "⇪";
            return k.label;
        }

        private boolean isPressed(Key k) {
            for (int i = 0; i < pressed.size(); i++) if (pressed.valueAt(i) == k) return true;
            return false;
        }

        @Nullable
        private Key keyAt(float x, float y) {
            for (List<Key> r : rows) {
                for (Key k : r) {
                    if (y >= k.rect.top - dp(2) && y <= k.rect.bottom + dp(2)
                            && x >= k.rect.left - dp(2) && x <= k.rect.right + dp(2)) {
                        return k;
                    }
                }
            }
            return null;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            int action = ev.getActionMasked();
            int index = ev.getActionIndex();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int id = ev.getPointerId(index);
                    Key k = keyAt(ev.getX(index), ev.getY(index));
                    if (k != null) {
                        pressed.put(id, k);
                        onPress(id, k);
                        invalidate();
                    }
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < ev.getPointerCount(); i++) {
                        int id = ev.getPointerId(i);
                        Key old = pressed.get(id);
                        Key now = keyAt(ev.getX(i), ev.getY(i));
                        if (old == null || now == null || now == old) continue;
                        // 指を滑らせて隣のキーへ移った。ゲームは押し直し、それ以外は離す時のキーを変える
                        if (mode == MODE_GAME) {
                            onRelease(id, old, false);
                            pressed.put(id, now);
                            onPress(id, now);
                        } else if (mode == MODE_NORMAL) {
                            stopRepeat();
                            pressed.put(id, now);
                        }
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL: {
                    boolean cancel = action == MotionEvent.ACTION_CANCEL;
                    if (cancel || action == MotionEvent.ACTION_UP) {
                        for (int i = pressed.size() - 1; i >= 0; i--) {
                            onRelease(pressed.keyAt(i), pressed.valueAt(i), cancel);
                        }
                        pressed.clear();
                    } else {
                        int id = ev.getPointerId(index);
                        Key k = pressed.get(id);
                        if (k != null) {
                            pressed.remove(id);
                            onRelease(id, k, false);
                        }
                    }
                    invalidate();
                    return true;
                }
                default:
                    return true;
            }
        }

        /** 指を置いた。 */
        private void onPress(int pointer, Key k) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (mode == MODE_GAME) {
                long t = SystemClock.uptimeMillis();
                downTimes.put(pointer, t);
                if (k.kind == K_SHIFT) shift = true;
                if (k.kind == K_CTRL) ctrl = true;
                int code = gameCode(k);
                if (code != 0) sendKey(KeyEvent.ACTION_DOWN, code, gameMeta(k), t);
                return;
            }
            if (mode == MODE_TYPING && k.kind == K_CHAR) {
                // 寿司打などは押した瞬間のキーを読むので、触れた時にすぐ送る
                commitChar(k);
                return;
            }
            if (k.repeats()) {
                tapKey(k.code, META_NONE);
                repeating = k;
                handler.postDelayed(repeat, 400);
            }
        }

        /** 指を離した。 */
        private void onRelease(int pointer, Key k, boolean cancel) {
            if (mode == MODE_GAME) {
                Long t = downTimes.get(pointer);
                downTimes.remove(pointer);
                if (k.kind == K_SHIFT) shift = false;
                if (k.kind == K_CTRL) ctrl = false;
                int code = gameCode(k);
                if (code != 0) {
                    sendKey(KeyEvent.ACTION_UP, code, gameMeta(k), t != null ? t : SystemClock.uptimeMillis());
                }
                return;
            }
            if (k == repeating) {
                stopRepeat();
                return;
            }
            if (cancel) return;
            switch (k.kind) {
                case K_SHIFT: {
                    long now = SystemClock.uptimeMillis();
                    if (capsLock) {
                        capsLock = false;
                        shift = false;
                    } else if (shift && now - lastShiftTap < 350) {
                        capsLock = true; // 2 回続けて押すと大文字のまま
                    } else {
                        shift = !shift;
                    }
                    lastShiftTap = now;
                    invalidate();
                    break;
                }
                case K_SYMBOLS:
                    symbols = !symbols;
                    rows = buildLayout(mode, type, symbols);
                    requestLayout();
                    invalidate();
                    break;
                case K_CODE:
                    tapKey(k.code, META_NONE);
                    break;
                case K_CHAR:
                    if (mode != MODE_TYPING) commitChar(k);
                    break;
                default:
                    break;
            }
        }

        /** 1 文字入力する(通常・タイピング)。 */
        private void commitChar(Key k) {
            boolean upper = (shift || capsLock) && k.text.length() == 1 && Character.isLetter(k.text.charAt(0));
            if (k.code != 0 && (upper || !needsShift(k.text))) {
                tapKey(k.code, upper ? (KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON) : META_NONE);
            } else {
                typeText(k.text);
            }
            if (shift && !capsLock) {
                shift = false;
                invalidate();
            }
        }

        private boolean needsShift(String s) {
            return s.length() == 1 && Character.isUpperCase(s.charAt(0));
        }

        private int gameCode(Key k) {
            if (k.kind == K_SYMBOLS) return 0;
            return k.code;
        }

        private int gameMeta(Key k) {
            int meta = 0;
            if (shift && k.kind != K_SHIFT) meta |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
            if (ctrl && k.kind != K_CTRL) meta |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            return meta;
        }

        private void stopRepeat() {
            repeating = null;
            handler.removeCallbacks(repeat);
        }

        void releaseAll() {
            stopRepeat();
            if (mode == MODE_GAME) {
                for (int i = pressed.size() - 1; i >= 0; i--) onRelease(pressed.keyAt(i), pressed.valueAt(i), true);
            }
            pressed.clear();
            downTimes.clear();
            ctrl = false;
            if (mode == MODE_GAME) shift = false;
            invalidate();
        }
    }
}
