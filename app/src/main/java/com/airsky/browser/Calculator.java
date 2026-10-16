package com.airsky.browser;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.ViewCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.javia.arity.Symbols;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 電卓(Pixel の電卓アプリ風)。
 *
 * <ul>
 *   <li>丸いボタンを 4 列 × 5 段に並べる。押している間は角の丸い四角に形が変わる。</li>
 *   <li>入力中の式の下に、途中の計算結果を薄く表示する。= で結果が大きく上がってくる。</li>
 *   <li>左の ⌃⌄ で関数(√ π ^ ! sin cos tan ln log など)の段を出し入れする。</li>
 *   <li>左上の時計のボタンで計算の履歴。押すと結果を式に入れる。</li>
 * </ul>
 * 計算そのものは Arity(org.javia.arity)で行う。
 */
public class Calculator extends AppCompatActivity {

    /** 以前の電卓(CalcLogic 等)が参照する定数。 */
    static final int BASIC_PANEL = 0;
    static final int ADVANCED_PANEL = 1;
    /** 以前の電卓のボタン(ColorButton)が参照する。新しい画面では使わない。 */
    final CalcEventListener mListener = new CalcEventListener();

    static void log(String message) {
    }

    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final String PREFS = "pixel_calculator";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_EXPR = "expr";
    private static final String KEY_DEG = "deg";
    private static final int MAX_HISTORY = 100;

    // 表示用の記号
    private static final String MUL = "×", DIV = "÷", MINUS = "−", PLUS = "+";

    /** 色(ダーク/ライトで切り替える)。 */
    private int bg, onBg, onBgVariant, digitBg, onDigit, opBg, onOp, clearBg, onClear, equalBg, onEqual, sciBg, onSci;
    /** ボタンを載せる台の色、= のグラデーションの終わりの色、数字ボタンのふち、途中の結果の色。 */
    private int deckBg, equalBgEnd, keyStroke, accent;

    private float dp;
    /** 表示欄(式と結果)の高さ(px)。 */
    private int displayHeight;
    private TextView prevView, exprView, resultView;
    private HorizontalScrollView exprScroll;
    private LinearLayout sciPanel;
    /** ボタンを載せる台。縦向きでは下のナビゲーションバーの裏まで伸ばす。 */
    private LinearLayout deck;
    private ImageButton expandButton;
    private TextView degButton, invButton;
    private final List<TextView> invTargets = new ArrayList<>();

    /** 入力中の式(表示用の記号のまま)。 */
    private final StringBuilder expr = new StringBuilder();
    /** = を押した直後(次に数字を打つと新しい計算を始める)。 */
    private boolean justEvaluated;
    private boolean degrees;
    private boolean inverse;
    private boolean sciOpen;
    /** 横向きの画面(関数の段を常に左に出す、横専用の並び)。 */
    private boolean landscape;

    private final Symbols symbols = new Symbols();
    private final List<String[]> history = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dp = getResources().getDisplayMetrics().density;
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        resolvePalette();
        defineDegreeFunctions();
        loadState();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat ic = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ic.setAppearanceLightStatusBars(!isDark());
        ic.setAppearanceLightNavigationBars(!isDark());

        View root = landscape ? buildLandscapeUi() : buildUi();
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            if (landscape) {
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                // 台はナビゲーションバーの裏まで伸ばし、ボタンだけ上に逃がす
                v.setPadding(bars.left, bars.top, bars.right, 0);
                deck.setPadding(deck.getPaddingLeft(), deck.getPaddingTop(), deck.getPaddingRight(), bars.bottom);
            }
            return WindowInsetsCompat.CONSUMED;
        });
        TouchEffectView.attach(getWindow());
        refresh(false);
        if (savedInstanceState == null) playEnter();
    }

    // ------------------------------------------------------------------
    // 色
    // ------------------------------------------------------------------

    private boolean isDark() {
        int ui = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return ui == Configuration.UI_MODE_NIGHT_YES;
    }

    private void resolvePalette() {
        // 青を基調にした AirSky の配色。ボタンは「台」の上に載せる
        if (isDark()) {
            bg = 0xFF0B1220;
            onBg = 0xFFE6EEFB;
            onBgVariant = 0xFF8A9BB8;
            deckBg = 0xFF111C2E;
            digitBg = 0xFF1A2940;
            onDigit = 0xFFE6EEFB;
            keyStroke = 0xFF24385A;
            opBg = 0xFF1E3D6E;
            onOp = 0xFFA9CBFF;
            clearBg = 0xFF2F6DB5;
            onClear = 0xFFFFFFFF;
            equalBg = 0xFF4C9BFF;
            equalBgEnd = 0xFF2A62E8;
            onEqual = 0xFFFFFFFF;
            sciBg = 0xFF152238;
            onSci = 0xFF9FB9DE;
            accent = 0xFF6FB1FF;
        } else {
            bg = 0xFFF3F7FD;
            onBg = 0xFF0E1E36;
            onBgVariant = 0xFF64748F;
            deckBg = 0xFFE3ECF8;
            digitBg = 0xFFFFFFFF;
            onDigit = 0xFF0E1E36;
            keyStroke = 0xFFD3E0F2;
            opBg = 0xFFC9DDFB;
            onOp = 0xFF0B4CA8;
            clearBg = 0xFF7FB2F5;
            onClear = 0xFF06264F;
            equalBg = 0xFF2F80FF;
            equalBgEnd = 0xFF1450D0;
            onEqual = 0xFFFFFFFF;
            sciBg = 0xFFD3E1F5;
            onSci = 0xFF234573;
            accent = 0xFF1F6FE5;
        }
    }

    // ------------------------------------------------------------------
    // 画面
    // ------------------------------------------------------------------

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        // 上の段: 履歴 / メニュー
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(px(12), px(8), px(8), 0);
        ImageButton historyButton = iconButton(R.drawable.ic_omni_history, Lang.t("履歴"), onBgVariant);
        historyButton.setOnClickListener(v -> showHistory());
        top.addView(historyButton, new LinearLayout.LayoutParams(px(48), px(48)));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        ImageButton more = iconButton(R.drawable.ic_pm_more, Lang.t("その他"), onBgVariant);
        more.setOnClickListener(this::showMenu);
        top.addView(more, new LinearLayout.LayoutParams(px(48), px(48)));
        root.addView(top);

        root.addView(buildDisplay(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ボタンの台(上の角だけ丸い)
        deck = new LinearLayout(this);
        deck.setOrientation(LinearLayout.VERTICAL);
        deck.setBackground(deckBackground(false));
        deck.setPadding(0, px(6), 0, 0);

        // ⌃⌄(関数の段の出し入れ)
        LinearLayout toggleRow = new LinearLayout(this);
        toggleRow.setPadding(px(16), 0, px(16), 0);
        expandButton = iconButton(R.drawable.ic_calc_expand, Lang.t("関数を表示"), accent);
        expandButton.setOnClickListener(v -> toggleScientific());
        toggleRow.addView(expandButton, new LinearLayout.LayoutParams(px(48), px(44)));
        deck.addView(toggleRow);

        // 関数の段
        sciPanel = new LinearLayout(this);
        sciPanel.setOrientation(LinearLayout.VERTICAL);
        sciPanel.setPadding(px(8), 0, px(8), px(6));
        sciPanel.setVisibility(sciOpen ? View.VISIBLE : View.GONE);
        buildScientific(sciPanel);
        deck.addView(sciPanel, matchWrap());

        // ボタン
        deck.addView(buildKeypad(), matchWrap());
        root.addView(deck, matchWrap());
        return root;
    }

    /**
     * 横向きの画面。上に表示(左右に履歴・メニュー)、下の左に関数 3 列 × 5 段、右に数字と演算子 4 列 × 5 段。
     * ボタンは画面の高さに合わせて伸び縮みするので、どの横長の画面でもはみ出さない。
     */
    private View buildLandscapeUi() {
        boolean large = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        // 上の段: 履歴 / 表示 / メニュー
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(px(12), px(4), px(8), 0);
        ImageButton historyButton = iconButton(R.drawable.ic_omni_history, Lang.t("履歴"), onBgVariant);
        historyButton.setOnClickListener(v -> showHistory());
        top.addView(historyButton, new LinearLayout.LayoutParams(px(48), px(48)));
        top.addView(buildDisplay(), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ImageButton more = iconButton(R.drawable.ic_pm_more, Lang.t("その他"), onBgVariant);
        more.setOnClickListener(this::showMenu);
        top.addView(more, new LinearLayout.LayoutParams(px(48), px(48)));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, large ? 0.36f : 0.3f));

        // 下の段: 関数 | ボタン
        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.HORIZONTAL);
        keys.setBackground(deckBackground(true));
        keys.setPadding(px(10), px(8), px(10), px(8));
        deck = keys;

        sciPanel = new LinearLayout(this);
        sciPanel.setOrientation(LinearLayout.VERTICAL);
        String[][] sciRows = {
                {"INV", "DEG", "π"},
                {"√", "^", "!"},
                {"sin", "cos", "tan"},
                {"ln", "log", "e"},
                {"(", ")", "x²"},
        };
        buildScientificRows(sciPanel, sciRows, large ? 20 : 16, true);
        keys.addView(sciPanel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f));

        keys.addView(new View(this), new LinearLayout.LayoutParams(px(10), 1));

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        for (String[] row : KEYPAD_ROWS) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (String key : row) {
                View b = keyButton(key, large ? 30 : 22);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                lp.setMargins(px(4), px(3), px(4), px(3));
                line.addView(b, lp);
            }
            pad.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        keys.addView(pad, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 4f));
        LinearLayout.LayoutParams keysLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, large ? 0.64f : 0.7f);
        keysLp.setMargins(px(8), px(4), px(8), px(8));
        root.addView(keys, keysLp);
        return root;
    }

    /** ボタンの台の背景。floating のときは四隅とも丸い(横向き)、そうでなければ上の角だけ丸い。 */
    private GradientDrawable deckBackground(boolean floating) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(deckBg);
        float r = px(28);
        d.setCornerRadii(floating
                ? new float[]{r, r, r, r, r, r, r, r}
                : new float[]{r, r, r, r, 0, 0, 0, 0});
        return d;
    }

    /** 表示を小さめにするか(関数の段を出している時と、横向きの時)。 */
    private boolean compactDisplay() {
        return sciOpen || landscape;
    }

    /** 表示: 前の式(小) / 式(大) / 途中の結果。 */
    private View buildDisplay() {
        LinearLayout display = new LinearLayout(this);
        display.setOrientation(LinearLayout.VERTICAL);
        display.setGravity(Gravity.BOTTOM | Gravity.END);
        display.setPadding(px(24), 0, px(24), px(4));
        prevView = text("", compactDisplay() ? 18 : 22, onBgVariant);
        prevView.setGravity(Gravity.END);
        prevView.setSingleLine(true);
        prevView.setEllipsize(TextUtils.TruncateAt.START);
        display.addView(prevView, matchWrap());

        exprScroll = new HorizontalScrollView(this);
        exprScroll.setHorizontalScrollBarEnabled(false);
        exprScroll.setFillViewport(true);
        exprView = text("", 72, onBg);
        exprView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        exprView.setSingleLine(true);
        exprView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        exprView.setOnLongClickListener(v -> {
            copyToClipboard(exprView.getText().toString());
            return true;
        });
        exprScroll.addView(exprView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END));
        display.addView(exprScroll, matchWrap());

        resultView = text("", compactDisplay() ? 26 : 34, accent);
        resultView.setGravity(Gravity.END);
        resultView.setSingleLine(true);
        resultView.setEllipsize(TextUtils.TruncateAt.END);
        display.addView(resultView, matchWrap());
        display.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != displayHeight) {
                displayHeight = b - t;
                v.post(this::fitExpressionText);
            }
        });
        return display;
    }

    private static final String[][] KEYPAD_ROWS = {
            {"AC", "( )", "%", DIV},
            {"7", "8", "9", MUL},
            {"4", "5", "6", MINUS},
            {"1", "2", "3", PLUS},
            {"0", ".", "⌫", "="},
    };

    private View buildKeypad() {
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(px(6), 0, px(6), px(10));
        for (String[] row : KEYPAD_ROWS) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (String key : row) {
                View b = keyButton(key, 34);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                lp.setMargins(px(5), px(5), px(5), px(5));
                line.addView(b, lp);
            }
            // ボタンは少し横長の角丸の四角にする(幅に合わせて高さを決める)
            line.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
                int cell = (r - l) / 4;
                int h = Math.round(cell * 0.84f);
                ViewGroup.LayoutParams p = v.getLayoutParams();
                if (p.height != h) {
                    p.height = h;
                    v.post(() -> v.setLayoutParams(p));
                }
            });
            pad.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(84)));
        }
        return pad;
    }

    private View keyButton(String key, float size) {
        int fill, ink;
        switch (key) {
            case "AC":
                fill = clearBg; ink = onClear; size = size * 30 / 34;
                break;
            case "( )": case "%": case DIV: case MUL: case MINUS: case PLUS:
                fill = opBg; ink = onOp;
                break;
            case "=":
                fill = equalBg; ink = onEqual;
                break;
            default:
                fill = digitBg; ink = onDigit;
        }
        View view;
        if ("⌫".equals(key)) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(R.drawable.ic_calc_backspace);
            icon.setImageTintList(ColorStateList.valueOf(ink));
            icon.setScaleType(ImageView.ScaleType.CENTER);
            icon.setContentDescription(Lang.t("1 文字消す"));
            FrameLayout f = new FrameLayout(this);
            f.addView(icon, new FrameLayout.LayoutParams(px(size), px(size), Gravity.CENTER));
            view = f;
        } else {
            TextView t = text(key, size, ink);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            if ("=".equals(key)) t.setContentDescription(Lang.t("計算する"));
            view = t;
        }
        MorphBackground shape = "=".equals(key)
                ? new MorphBackground(equalBg, equalBgEnd, 0)
                : new MorphBackground(fill, 0, fill == digitBg ? keyStroke : 0);
        view.setBackground(shape);
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    shape.press(v, true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    shape.press(v, false);
                    break;
            }
            return false;
        });
        view.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            onKey(key);
        });
        if ("⌫".equals(key)) {
            view.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                clearAll(true);
                return true;
            });
        }
        return view;
    }

    private void buildScientific(LinearLayout panel) {
        String[][] rows = {
                {"√", "π", "^", "!"},
                {"DEG", "sin", "cos", "tan"},
                {"INV", "e", "ln", "log"},
        };
        buildScientificRows(panel, rows, 20, false);
    }

    /** 関数のボタンを並べる。fill のときは段が panel の高さを等分する(横向きの画面)。 */
    private void buildScientificRows(LinearLayout panel, String[][] rows, float textSp, boolean fill) {
        for (String[] row : rows) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (String key : row) {
                TextView t = text(key, textSp, onSci);
                t.setGravity(Gravity.CENTER);
                GradientDrawable d = new GradientDrawable();
                d.setColor(sciBg);
                d.setCornerRadius(px(14));
                t.setBackground(d);
                t.setForeground(getDrawable(resolve(android.R.attr.selectableItemBackground)));
                t.setClipToOutline(true);
                t.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    onSciKey((TextView) v);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        fill ? ViewGroup.LayoutParams.MATCH_PARENT : px(46), 1f);
                int mh = px(fill ? 4 : 5), mv = px(fill ? 3 : 4);
                lp.setMargins(mh, mv, mh, mv);
                line.addView(t, lp);
                if ("DEG".equals(key)) degButton = t;
                if ("INV".equals(key)) invButton = t;
                if (key.equals("√") || key.equals("sin") || key.equals("cos") || key.equals("tan")
                        || key.equals("ln") || key.equals("log")) {
                    t.setTag(key);
                    invTargets.add(t);
                }
            }
            panel.addView(line, fill
                    ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                    : matchWrap());
        }
        updateSciLabels();
    }

    private void updateSciLabels() {
        if (degButton != null) degButton.setText(degrees ? "DEG" : "RAD");
        if (invButton != null) {
            GradientDrawable d = (GradientDrawable) invButton.getBackground();
            d.setColor(inverse ? opBg : sciBg);
        }
        for (TextView t : invTargets) {
            String base = (String) t.getTag();
            t.setText(inverse ? inverseLabel(base) : base);
        }
    }

    private static String inverseLabel(String base) {
        switch (base) {
            case "√": return "x²";
            case "sin": return "sin⁻¹";
            case "cos": return "cos⁻¹";
            case "tan": return "tan⁻¹";
            case "ln": return "eˣ";
            case "log": return "10ˣ";
            default: return base;
        }
    }

    private void toggleScientific() {
        sciOpen = !sciOpen;
        fitExpressionText();
        prevView.setVisibility(prevView.length() > 0 && !sciOpen ? View.VISIBLE : View.GONE);
        prevView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sciOpen ? 18 : 22);
        resultView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sciOpen ? 26 : 34);
        expandButton.animate().rotation(sciOpen ? 180f : 0f).setDuration(300).setInterpolator(EMPHASIZED).start();
        sciPanel.measure(View.MeasureSpec.makeMeasureSpec(((View) sciPanel.getParent()).getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int full = sciPanel.getMeasuredHeight();
        int from = sciOpen ? 0 : sciPanel.getHeight();
        int to = sciOpen ? full : 0;
        sciPanel.setVisibility(View.VISIBLE);
        ValueAnimator va = ValueAnimator.ofInt(from, to);
        va.setDuration(320);
        va.setInterpolator(EMPHASIZED);
        va.addUpdateListener(a -> {
            ViewGroup.LayoutParams lp = sciPanel.getLayoutParams();
            lp.height = (int) a.getAnimatedValue();
            sciPanel.setLayoutParams(lp);
            sciPanel.setAlpha(full > 0 ? (float) lp.height / full : 1f);
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                ViewGroup.LayoutParams lp = sciPanel.getLayoutParams();
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                sciPanel.setLayoutParams(lp);
                sciPanel.setVisibility(sciOpen ? View.VISIBLE : View.GONE);
            }
        });
        va.start();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("sci_open", sciOpen).apply();
    }

    // ------------------------------------------------------------------
    // 入力
    // ------------------------------------------------------------------

    private void onKey(String key) {
        switch (key) {
            case "AC":
                clearAll(true);
                return;
            case "⌫":
                if (justEvaluated) {
                    justEvaluated = false;
                } else if (expr.length() > 0) {
                    deleteLast();
                }
                refresh(true);
                return;
            case "=":
                evaluateAndCommit();
                return;
            case "( )":
                startFromResultIfNeeded(false);
                insert(shouldOpenParen() ? "(" : ")");
                return;
            case "%":
                startFromResultIfNeeded(true);
                if (expr.length() > 0 && (Character.isDigit(lastChar()) || lastChar() == ')')) insert("%");
                return;
            case ".":
                startFromResultIfNeeded(false);
                if (!currentNumberHasDot()) {
                    if (expr.length() == 0 || !Character.isDigit(lastChar())) insert("0");
                    insert(".");
                }
                return;
            case PLUS: case MINUS: case MUL: case DIV:
                startFromResultIfNeeded(true);
                insertOperator(key);
                return;
            default:
                startFromResultIfNeeded(false);
                // 「50%」「(1+2)」「3!」の後に数字を打ったら掛け算にする
                if (expr.length() > 0) {
                    char last = lastChar();
                    if (last == '%' || last == ')' || last == '!' || last == 'π' || last == 'e') insert(MUL);
                }
                insert(key);
        }
    }

    private void onSciKey(TextView v) {
        String label = v.getText().toString();
        switch (label) {
            case "DEG":
            case "RAD":
                degrees = !degrees;
                updateSciLabels();
                refresh(true);
                return;
            case "INV":
                inverse = !inverse;
                updateSciLabels();
                return;
            case "π": case "e":
                startFromResultIfNeeded(false);
                insert(label);
                break;
            case "√":
                startFromResultIfNeeded(false);
                insert("√");
                break;
            case "(":
                startFromResultIfNeeded(false);
                insert("(");
                break;
            case ")":
                startFromResultIfNeeded(true);
                if (expr.length() > 0) insert(")");
                break;
            case "^": case "!":
                startFromResultIfNeeded(true);
                if (expr.length() > 0) insert(label);
                break;
            case "x²":
                startFromResultIfNeeded(true);
                if (expr.length() > 0) insert("^2");
                break;
            case "eˣ":
                startFromResultIfNeeded(false);
                insert("e^(");
                break;
            case "10ˣ":
                startFromResultIfNeeded(false);
                insert("10^(");
                break;
            default:
                // sin, cos, tan, sin⁻¹, ln, log など
                startFromResultIfNeeded(false);
                insert(label + "(");
        }
        if (inverse) {
            inverse = false;
            updateSciLabels();
        }
    }

    /**
     * = の直後の入力。演算子なら結果に続けて計算、数字なら新しい計算を始める。
     */
    private void startFromResultIfNeeded(boolean continueWithResult) {
        if (!justEvaluated) return;
        justEvaluated = false;
        if (!continueWithResult) {
            prevView.setText("");
            expr.setLength(0);
        }
    }

    private void insert(String s) {
        expr.append(s);
        refresh(true);
    }

    private void insertOperator(String op) {
        if (expr.length() == 0) {
            // 先頭はマイナスだけ打てる
            if (MINUS.equals(op)) insert(op);
            return;
        }
        char last = lastChar();
        if (isOperatorChar(last)) {
            // 演算子を続けて押したら置き換える(× と ÷ の後のマイナスは負の数として残す)
            if (MINUS.equals(op) && (last == '×' || last == '÷')) {
                insert(op);
                return;
            }
            expr.setLength(expr.length() - 1);
            if (expr.length() > 0 && isOperatorChar(lastChar())) expr.setLength(expr.length() - 1);
            if (expr.length() == 0) {
                refresh(true);
                return;
            }
        } else if (last == '(') {
            if (MINUS.equals(op)) insert(op);
            return;
        }
        insert(op);
    }

    private void deleteLast() {
        // sin( などの関数名はまとめて消す
        String[] words = {"sin⁻¹(", "cos⁻¹(", "tan⁻¹(", "sin(", "cos(", "tan(", "ln(", "log("};
        String s = expr.toString();
        for (String w : words) {
            if (s.endsWith(w)) {
                expr.setLength(expr.length() - w.length());
                return;
            }
        }
        expr.setLength(expr.length() - 1);
    }

    private boolean shouldOpenParen() {
        if (expr.length() == 0) return true;
        int open = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') open++;
            else if (c == ')') open--;
        }
        char last = lastChar();
        if (open <= 0) return true;
        return isOperatorChar(last) || last == '(' || last == '^' || last == '√';
    }

    private boolean currentNumberHasDot() {
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == '.') return true;
            if (!Character.isDigit(c)) return false;
        }
        return false;
    }

    private char lastChar() {
        return expr.charAt(expr.length() - 1);
    }

    private static boolean isOperatorChar(char c) {
        return c == '+' || c == '−' || c == '×' || c == '÷';
    }

    private void clearAll(boolean animate) {
        boolean had = expr.length() > 0;
        expr.setLength(0);
        justEvaluated = false;
        if (animate && had) {
            // Pixel と同じく、表示がふわっと消える
            exprView.animate().alpha(0f).translationY(-px(24)).setDuration(160).withEndAction(() -> {
                exprView.setTranslationY(0f);
                exprView.setAlpha(1f);
                prevView.setText("");
                refresh(false);
            }).start();
        } else {
            prevView.setText("");
            refresh(false);
        }
    }

    // ------------------------------------------------------------------
    // 計算
    // ------------------------------------------------------------------

    private void evaluateAndCommit() {
        if (expr.length() == 0 || justEvaluated) return;
        String input = expr.toString();
        String result = evaluate(input);
        if (result == null) {
            resultView.setText(Lang.t("形式エラー"));
            resultView.setTextColor(0xFFF2B8B5);
            exprView.animate().translationX(px(10)).setDuration(60).withEndAction(() ->
                    exprView.animate().translationX(-px(8)).setDuration(60).withEndAction(() ->
                            exprView.animate().translationX(0).setDuration(80).start()).start()).start();
            return;
        }
        addHistory(input, result);
        prevView.setText(groupDigits(input) + " =");
        prevView.setVisibility(compactDisplay() ? View.GONE : View.VISIBLE);
        expr.setLength(0);
        expr.append(result);
        justEvaluated = true;

        // 途中結果が大きくなって上がってくる
        float scale = resultView.getTextSize() / exprView.getTextSize();
        exprView.setPivotX(exprView.getWidth());
        exprView.setPivotY(exprView.getHeight());
        refresh(false);
        exprView.setScaleX(scale);
        exprView.setScaleY(scale);
        exprView.setTranslationY(px(40));
        exprView.setAlpha(0.6f);
        exprView.animate().scaleX(1f).scaleY(1f).translationY(0f).alpha(1f)
                .setDuration(380).setInterpolator(EMPHASIZED).start();
        prevView.setAlpha(0f);
        prevView.animate().alpha(1f).setDuration(300).start();
    }

    /** 式を計算して表示用の文字列にする。計算できなければ null。 */
    @Nullable
    private String evaluate(String input) {
        if (input.isEmpty()) return null;
        String s = input;
        // 最後の演算子は無視、開いたままの括弧は閉じる
        while (!s.isEmpty() && (isOperatorChar(s.charAt(s.length() - 1)) || s.charAt(s.length() - 1) == '(')) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) return null;
        int open = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '(') open++;
            else if (s.charAt(i) == ')') open--;
        }
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < open; i++) sb.append(')');
        String math = toArity(sb.toString());
        try {
            double v = symbols.eval(math);
            if (Double.isNaN(v)) return null;
            return format(v);
        } catch (Exception | StackOverflowError e) {
            return null;
        }
    }

    private String toArity(String s) {
        String d = degrees ? "d" : "";
        // 逆関数は先に目印へ置き換えてから戻す(sin( の置き換えで asin( が崩れないように)
        return s.replace("sin⁻¹(", "(")
                .replace("cos⁻¹(", "(")
                .replace("tan⁻¹(", "(")
                .replace("sin(", d + "sin(")
                .replace("cos(", d + "cos(")
                .replace("tan(", d + "tan(")
                .replace("(", d + "asin(")
                .replace("(", d + "acos(")
                .replace("(", d + "atan(")
                .replace("log(", "lg(")
                .replace("E", "*10^")
                .replace("π", "pi")
                .replace("√", "sqrt")
                .replace("×", "*")
                .replace("÷", "/")
                .replace("−", "-")
                .replace("%", "/100")
                .replace(",", "");
    }

    /** 角度を度で扱う三角関数を用意する。 */
    private void defineDegreeFunctions() {
        String[] defs = {
                "dsin(x)=sin(x*pi/180)", "dcos(x)=cos(x*pi/180)", "dtan(x)=tan(x*pi/180)",
                "dasin(x)=asin(x)*180/pi", "dacos(x)=acos(x)*180/pi", "datan(x)=atan(x)*180/pi",
        };
        for (String def : defs) {
            try {
                symbols.define(symbols.compileWithName(def));
            } catch (Exception ignored) {
            }
        }
    }

    /** 計算結果を表示用にする(12 桁まで、余分な 0 は消す、大きい/小さい数は指数表記)。 */
    private static String format(double v) {
        if (Double.isInfinite(v)) return v > 0 ? "∞" : MINUS + "∞";
        if (v == 0) return "0";
        double abs = Math.abs(v);
        String out;
        if (abs >= 1e15 || abs < 1e-9) {
            String e = String.format(Locale.US, "%.9E", v);
            String mant = e.substring(0, e.indexOf('E'));
            String exp = e.substring(e.indexOf('E') + 1);
            mant = new BigDecimal(mant).stripTrailingZeros().toPlainString();
            out = mant + "E" + Integer.parseInt(exp);
        } else {
            BigDecimal bd = new BigDecimal(v).round(new java.math.MathContext(12));
            out = bd.stripTrailingZeros().toPlainString();
        }
        return out.replace('-', '−');
    }

    // ------------------------------------------------------------------
    // 表示
    // ------------------------------------------------------------------

    private void refresh(boolean preview) {
        // 前の式は、ある時だけ・表示欄に余裕がある時(関数の段を閉じている時)だけ出す
        prevView.setVisibility(prevView.length() > 0 && !compactDisplay() ? View.VISIBLE : View.GONE);
        String shown = groupDigits(expr.toString());
        exprView.setText(shown);
        fitExpressionText();
        exprScroll.post(() -> exprScroll.fullScroll(View.FOCUS_RIGHT));

        // 途中の結果: 演算子や関数を含む式の時だけ出す
        resultView.setTextColor(accent);
        String s = expr.toString();
        boolean hasOp = false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isOperatorChar(c) || c == '%' || c == '^' || c == '!' || c == '(' || c == '√') {
                hasOp = true;
                break;
            }
        }
        if (!justEvaluated && (hasOp || s.startsWith("√") || s.startsWith("π") || s.startsWith("e"))) {
            String r = evaluate(s);
            resultView.setText(r != null && !r.equals(s) ? groupDigits(r) : "");
        } else {
            resultView.setText("");
        }
        saveExpression();
    }

    /** 式が長くなったら文字を小さくする(72sp → 40sp)。 */
    private void fitExpressionText() {
        int len = exprView.getText().length();
        float size;
        if (len <= 8) size = 72;
        else if (len <= 11) size = 58;
        else if (len <= 14) size = 48;
        else size = 40;
        // 関数の段を出している間は表示が狭いので小さめにする
        if (compactDisplay()) size = Math.min(size, 46);
        // 表示欄の高さに、式と途中の結果の両方が収まる大きさにする
        if (displayHeight > 0) {
            float resultSp = compactDisplay() ? 26 : 34;
            float fitSp = (displayHeight / dp - resultSp * 1.25f - 8) / 1.25f;
            size = Math.max(28, Math.min(size, fitSp));
        }
        exprView.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    }

    /** 式の中の数字に 3 桁ごとのカンマを入れる(表示だけ)。 */
    private static String groupDigits(String s) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                int j = i;
                while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
                String run = s.substring(i, j);
                boolean afterDot = i > 0 && s.charAt(i - 1) == '.';
                boolean exponent = i > 0 && s.charAt(i - 1) == 'E' || (i > 1 && s.charAt(i - 1) == '−' && s.charAt(i - 2) == 'E');
                if (afterDot || exponent || run.length() <= 3) {
                    out.append(run);
                } else {
                    int first = run.length() % 3;
                    if (first > 0) out.append(run, 0, first);
                    for (int k = first; k < run.length(); k += 3) {
                        if (k > 0) out.append(',');
                        out.append(run, k, k + 3);
                    }
                }
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private void playEnter() {
        View root = findViewById(android.R.id.content);
        root.post(() -> {
            List<View> keys = new ArrayList<>();
            collectKeys(root, keys);
            int[] loc = new int[2];
            for (View k : keys) {
                k.getLocationInWindow(loc);
                k.setAlpha(0f);
                k.setScaleX(0.7f);
                k.setScaleY(0.7f);
                long delay = Math.max(0, loc[1] - root.getHeight() / 3) / 7 + loc[0] / 24;
                k.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(Math.min(delay, 300))
                        .setDuration(420).setInterpolator(EMPHASIZED).start();
            }
        });
    }

    private static void collectKeys(View v, List<View> out) {
        if (v.getBackground() instanceof MorphBackground) {
            out.add(v);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectKeys(g.getChildAt(i), out);
        }
    }

    // ------------------------------------------------------------------
    // 履歴・メニュー
    // ------------------------------------------------------------------

    private void showHistory() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(20), px(12), px(20), px(24));
        TextView title = text(Lang.t("履歴"), 22, onBg);
        title.setPadding(px(4), px(8), px(4), px(12));
        root.addView(title);
        if (history.isEmpty()) {
            TextView empty = text(Lang.t("まだ計算の履歴はありません"), 15, onBgVariant);
            empty.setPadding(px(4), px(24), px(4), px(32));
            empty.setGravity(Gravity.CENTER);
            root.addView(empty, matchWrap());
        } else {
            for (int i = history.size() - 1; i >= 0; i--) {
                String[] h = history.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setGravity(Gravity.END);
                row.setPadding(px(16), px(12), px(16), px(12));
                GradientDrawable d = new GradientDrawable();
                d.setColor(deckBg);
                d.setCornerRadius(px(20));
                row.setBackground(d);
                row.setForeground(getDrawable(resolve(android.R.attr.selectableItemBackground)));
                row.setClipToOutline(true);
                TextView e = text(groupDigits(h[0]), 15, onBgVariant);
                e.setGravity(Gravity.END);
                row.addView(e, matchWrap());
                TextView r = text("= " + groupDigits(h[1]), 24, onBg);
                r.setGravity(Gravity.END);
                row.addView(r, matchWrap());
                row.setOnClickListener(v -> {
                    // 結果を今の式に入れる
                    startFromResultIfNeeded(false);
                    if (expr.length() > 0 && !isOperatorChar(lastChar()) && lastChar() != '(') insert(MUL);
                    insert(h[1].replace("∞", ""));
                    dialog.dismiss();
                });
                row.setOnLongClickListener(v -> {
                    copyToClipboard(h[1]);
                    return true;
                });
                LinearLayout.LayoutParams lp = matchWrap();
                lp.bottomMargin = px(6);
                root.addView(row, lp);
            }
        }
        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(this);
        scroll.addView(root);
        dialog.setContentView(scroll);
        dialog.show();
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, Lang.t("履歴を消去"));
        menu.getMenu().add(0, 2, 1, degrees ? Lang.t("角度の単位: 度 (DEG)") : Lang.t("角度の単位: ラジアン (RAD)"));
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                history.clear();
                saveHistory();
                Toast.makeText(this, Lang.t("履歴を消去しました"), Toast.LENGTH_SHORT).show();
            } else if (item.getItemId() == 2) {
                degrees = !degrees;
                updateSciLabels();
                refresh(true);
            }
            return true;
        });
        menu.show();
    }

    private void copyToClipboard(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || s.isEmpty()) return;
        cm.setPrimaryClip(ClipData.newPlainText(Lang.t("計算"), s.replace(",", "").replace('−', '-')));
        if (android.os.Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, Lang.t("コピーしました"), Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------
    // 保存
    // ------------------------------------------------------------------

    private void loadState() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        expr.append(p.getString(KEY_EXPR, ""));
        degrees = p.getBoolean(KEY_DEG, false);
        sciOpen = p.getBoolean("sci_open", false);
        try {
            JSONArray a = new JSONArray(p.getString(KEY_HISTORY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                history.add(new String[]{o.getString("e"), o.getString("r")});
            }
        } catch (Exception ignored) {
        }
    }

    private void saveExpression() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_EXPR, expr.toString())
                .putBoolean(KEY_DEG, degrees)
                .apply();
    }

    private void addHistory(String e, String r) {
        history.add(new String[]{e, r});
        while (history.size() > MAX_HISTORY) history.remove(0);
        saveHistory();
    }

    private void saveHistory() {
        JSONArray a = new JSONArray();
        try {
            for (String[] h : history) a.put(new JSONObject().put("e", h[0]).put("r", h[1]));
        } catch (Exception ignored) {
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_HISTORY, a.toString()).apply();
    }

    // ------------------------------------------------------------------
    // 部品
    // ------------------------------------------------------------------

    /**
     * ボタンの背景。普段は角の丸い四角で、押している間は角がさらに丸くなって(カプセル形)色が少し変わる。
     * baseEnd が 0 でなければ斜めのグラデーション、stroke が 0 でなければ細いふちを付ける。
     */
    private final class MorphBackground extends GradientDrawable {
        private final int base, baseEnd;
        @Nullable private ValueAnimator anim;
        private float progress; // 0 = 普段の形, 1 = 押した形

        MorphBackground(int color, int colorEnd, int stroke) {
            base = color;
            baseEnd = colorEnd;
            setOrientation(Orientation.TL_BR);
            if (stroke != 0) setStroke(Math.max(1, px(1)), stroke);
            applyColor(0f);
        }

        private void applyColor(float f) {
            int with = isDark() ? Color.WHITE : 0xFF0B3D91;
            if (baseEnd != 0) {
                setColors(new int[]{blend(base, with, f), blend(baseEnd, with, f)});
            } else {
                setColor(blend(base, with, f));
            }
        }

        @Override
        protected void onBoundsChange(android.graphics.Rect bounds) {
            super.onBoundsChange(bounds);
            apply();
        }

        void press(View v, boolean down) {
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(progress, down ? 1f : 0f);
            anim.setDuration(down ? 120 : 320);
            anim.setInterpolator(EMPHASIZED);
            anim.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                apply();
                v.invalidate();
            });
            anim.start();
        }

        private void apply() {
            float h = Math.min(getBounds().width(), getBounds().height());
            if (h <= 0) return;
            float rest = h * 0.32f;
            float pressed = h / 2f;
            setCornerRadius(rest + (pressed - rest) * progress);
            applyColor(0.14f * progress);
        }
    }

    private static int blend(int c, int with, float f) {
        int r = Math.round(Color.red(c) + (Color.red(with) - Color.red(c)) * f);
        int g = Math.round(Color.green(c) + (Color.green(with) - Color.green(c)) * f);
        int b = Math.round(Color.blue(c) + (Color.blue(with) - Color.blue(c)) * f);
        return Color.argb(Color.alpha(c), r, g, b);
    }

    private ImageButton iconButton(@DrawableRes int res, String desc, int tint) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setImageTintList(ColorStateList.valueOf(tint));
        b.setBackgroundResource(resolve(android.R.attr.selectableItemBackgroundBorderless));
        b.setContentDescription(desc);
        return b;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int resolve(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.resourceId;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }
}
