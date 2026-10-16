package com.airsky.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 仮想コントローラー(ページの上に重ねる半透明のボタン・十字キー・スティック)。
 *
 * <p>ボタンは好きな所へ動かせて、押した時に送る入力(キーボードのキー・ゲームパッドのボタン・
 * マウスのボタン)をボタンごとに決められる。スティック・十字キーは矢印キー・WASD・ゲームパッドの
 * スティック・マウスの移動(視点)などに割り当てられる。透明度は全体で調節できる。</p>
 *
 * <p>各ボタンはページの入っている枠(contentFrame)へ直接並べる。全面を覆う透明な板にすると、
 * 1 本目の指がボタンに乗っている間は 2 本目の指もその板に取られてページへ届かなくなるため。
 * ボタンの無い所に触れた指は、そのままページ(または仮想マウスのタッチ面)へ届く。</p>
 */
final class VirtualController {

    interface Host {
        /** 入力の設定(コントローラーのタブ)を開く。 */
        void openControllerSettings();

        /** 編集を始めた/終えた。 */
        void onControllerEditingChanged(boolean editing);
    }

    // ------------------------------------------------------------------
    // 設定
    // ------------------------------------------------------------------

    static final int T_BUTTON = 0;
    static final int T_DPAD = 1;
    static final int T_STICK = 2;
    /** 設定を開く小さなボタン(1 つだけ。消せない)。 */
    static final int T_MENU = 3;

    private static final String PREFS = "virtual_controller";
    private static final String KEY_SHOWN = "shown";
    private static final String KEY_OPACITY = "opacity";
    private static final String KEY_LAYOUT = "layout";

    static final String[] PRESET_LABELS = {Lang.t("ゲームパッド"), Lang.t("矢印キー + Z / X"), Lang.t("FPS(WASD + 視点)")};

    private static SharedPreferences prefs(@NonNull Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isShown(@NonNull Context c) {
        return prefs(c).getBoolean(KEY_SHOWN, false);
    }

    static void setShown(@NonNull Context c, boolean shown) {
        prefs(c).edit().putBoolean(KEY_SHOWN, shown).apply();
    }

    /** ボタンの不透明度(0.15〜1)。 */
    static float opacity(@NonNull Context c) {
        return Math.max(0.15f, Math.min(1f, prefs(c).getFloat(KEY_OPACITY, 0.55f)));
    }

    static void setOpacity(@NonNull Context c, float v) {
        prefs(c).edit().putFloat(KEY_OPACITY, Math.max(0.15f, Math.min(1f, v))).apply();
    }

    /** 1 つのボタン・十字キー・スティック。位置は枠に対する中心の割合(0〜1)。 */
    static final class Control {
        String id;
        int type;
        float cx, cy;
        /** 大きさの倍率(0.6〜1.8)。 */
        float size = 1f;
        /** ボタンに出す文字(空なら割り当てから決める)。 */
        String label = "";
        String binding = InputRouter.NONE;

        Control(int type, float cx, float cy, String binding, String label, float size) {
            this.id = Long.toString(System.nanoTime(), 36) + Integer.toString((int) (Math.random() * 1e6), 36);
            this.type = type;
            this.cx = cx;
            this.cy = cy;
            this.binding = binding;
            this.label = label;
            this.size = size;
        }

        JSONObject toJson() throws org.json.JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("type", type);
            o.put("x", (double) cx);
            o.put("y", (double) cy);
            o.put("s", (double) size);
            o.put("label", label);
            o.put("bind", binding);
            return o;
        }

        static Control fromJson(JSONObject o) {
            Control c = new Control(o.optInt("type"), (float) o.optDouble("x", 0.5), (float) o.optDouble("y", 0.5),
                    o.optString("bind", InputRouter.NONE), o.optString("label", ""), (float) o.optDouble("s", 1));
            c.id = o.optString("id", c.id);
            return c;
        }

        /** ボタンに出す文字。 */
        String displayLabel() {
            if (label != null && !label.isEmpty()) return label;
            return InputRouter.shortLabel(binding);
        }

        /** 基本の大きさ(dp)。 */
        float baseDp() {
            switch (type) {
                case T_DPAD: return 150;
                case T_STICK: return 140;
                case T_MENU: return 40;
                default: return 64;
            }
        }
    }

    @NonNull
    static List<Control> preset(int which) {
        List<Control> l = new ArrayList<>();
        switch (which) {
            case 1: // 矢印キー + Z / X
                l.add(new Control(T_DPAD, 0.17f, 0.74f, InputRouter.ARROWS, "", 1f));
                l.add(new Control(T_BUTTON, 0.88f, 0.70f, InputRouter.key(android.view.KeyEvent.KEYCODE_Z), "", 1.1f));
                l.add(new Control(T_BUTTON, 0.74f, 0.80f, InputRouter.key(android.view.KeyEvent.KEYCODE_X), "", 1.1f));
                l.add(new Control(T_BUTTON, 0.88f, 0.88f, InputRouter.key(android.view.KeyEvent.KEYCODE_SPACE), "", 0.9f));
                l.add(new Control(T_BUTTON, 0.60f, 0.90f, InputRouter.key(android.view.KeyEvent.KEYCODE_ENTER), "", 0.8f));
                l.add(new Control(T_BUTTON, 0.40f, 0.90f, InputRouter.key(android.view.KeyEvent.KEYCODE_SHIFT_LEFT), "", 0.8f));
                break;
            case 2: // FPS
                l.add(new Control(T_STICK, 0.17f, 0.74f, InputRouter.WASD, "", 1f));
                l.add(new Control(T_STICK, 0.83f, 0.74f, InputRouter.MOUSE_MOVE, "", 1f));
                l.add(new Control(T_BUTTON, 0.66f, 0.86f, InputRouter.key(android.view.KeyEvent.KEYCODE_SPACE), "", 0.9f));
                l.add(new Control(T_BUTTON, 0.92f, 0.45f, InputRouter.mouse(1), "", 1f));
                l.add(new Control(T_BUTTON, 0.78f, 0.45f, InputRouter.mouse(2), "", 0.9f));
                l.add(new Control(T_BUTTON, 0.34f, 0.88f, InputRouter.key(android.view.KeyEvent.KEYCODE_SHIFT_LEFT), "", 0.8f));
                l.add(new Control(T_BUTTON, 0.08f, 0.45f, InputRouter.key(android.view.KeyEvent.KEYCODE_E), "", 0.8f));
                l.add(new Control(T_BUTTON, 0.08f, 0.32f, InputRouter.key(android.view.KeyEvent.KEYCODE_ESCAPE), "", 0.7f));
                break;
            default: // ゲームパッド
                l.add(new Control(T_DPAD, 0.17f, 0.74f, InputRouter.PAD_DPAD, "", 1f));
                l.add(new Control(T_BUTTON, 0.84f, 0.85f, InputRouter.pad(VirtualGamepad.A), "", 1f));
                l.add(new Control(T_BUTTON, 0.93f, 0.75f, InputRouter.pad(VirtualGamepad.B), "", 1f));
                l.add(new Control(T_BUTTON, 0.75f, 0.75f, InputRouter.pad(VirtualGamepad.X), "", 1f));
                l.add(new Control(T_BUTTON, 0.84f, 0.65f, InputRouter.pad(VirtualGamepad.Y), "", 1f));
                l.add(new Control(T_BUTTON, 0.08f, 0.42f, InputRouter.pad(VirtualGamepad.L1), "", 0.85f));
                l.add(new Control(T_BUTTON, 0.92f, 0.42f, InputRouter.pad(VirtualGamepad.R1), "", 0.85f));
                l.add(new Control(T_BUTTON, 0.42f, 0.92f, InputRouter.pad(VirtualGamepad.SELECT), "", 0.75f));
                l.add(new Control(T_BUTTON, 0.58f, 0.92f, InputRouter.pad(VirtualGamepad.START), "", 0.75f));
                break;
        }
        l.add(new Control(T_MENU, 0.95f, 0.06f, InputRouter.NONE, "", 1f));
        return l;
    }

    @NonNull
    static List<Control> loadLayout(@NonNull Context c) {
        String json = prefs(c).getString(KEY_LAYOUT, null);
        List<Control> l = new ArrayList<>();
        if (json != null) {
            try {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) l.add(Control.fromJson(a.getJSONObject(i)));
            } catch (Exception ignored) {
                l.clear();
            }
        }
        if (l.isEmpty()) l = preset(0);
        boolean hasMenu = false;
        for (Control ctl : l) if (ctl.type == T_MENU) hasMenu = true;
        if (!hasMenu) l.add(new Control(T_MENU, 0.95f, 0.06f, InputRouter.NONE, "", 1f));
        return l;
    }

    static void saveLayout(@NonNull Context c, @NonNull List<Control> layout) {
        JSONArray a = new JSONArray();
        try {
            for (Control ctl : layout) a.put(ctl.toJson());
        } catch (Exception ignored) {
            return;
        }
        prefs(c).edit().putString(KEY_LAYOUT, a.toString()).apply();
    }

    // ------------------------------------------------------------------

    private final Activity activity;
    private final FrameLayout parent;
    private final InputRouter router;
    private final VirtualGamepad pad;
    private final Host host;
    private final float density;
    private final List<Control> layout = new ArrayList<>();
    private final List<ControlView> views = new ArrayList<>();
    private boolean shown;
    private boolean editing;
    @Nullable private View scrim;
    @Nullable private View toolbar;

    VirtualController(@NonNull Activity activity, @NonNull FrameLayout parent, @NonNull InputRouter router,
                      @NonNull VirtualGamepad pad, @NonNull Host host) {
        this.activity = activity;
        this.parent = parent;
        this.router = router;
        this.pad = pad;
        this.host = host;
        this.density = activity.getResources().getDisplayMetrics().density;
        parent.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) positionAll();
        });
    }

    boolean isShown() {
        return shown;
    }

    boolean isEditing() {
        return editing;
    }

    /** 設定に合わせて出す・しまう。 */
    void apply() {
        boolean want = isShown(activity);
        if (want == shown) {
            if (shown) applyOpacity();
            return;
        }
        shown = want;
        pad.setEnabled(want);
        if (want) {
            rebuild();
        } else {
            setEditing(false);
            removeViews();
        }
    }

    void applyOpacity() {
        float a = opacity(activity);
        for (ControlView v : views) v.setAlpha(editing ? Math.max(0.85f, a) : a);
    }

    void releaseAll() {
        for (ControlView v : views) v.cancel();
    }

    private void removeViews() {
        releaseAll();
        for (ControlView v : views) parent.removeView(v);
        views.clear();
    }

    private void rebuild() {
        removeViews();
        layout.clear();
        layout.addAll(loadLayout(activity));
        for (Control c : layout) addView(c);
        positionAll();
        applyOpacity();
    }

    private void addView(@NonNull Control c) {
        ControlView v = new ControlView(activity, c);
        int px = Math.round(c.baseDp() * c.size * density);
        parent.addView(v, new FrameLayout.LayoutParams(px, px, Gravity.TOP | Gravity.START));
        views.add(v);
        if (toolbar != null) toolbar.bringToFront();
    }

    private void positionAll() {
        int w = parent.getWidth(), h = parent.getHeight();
        if (w <= 0 || h <= 0) {
            parent.post(this::positionAll);
            return;
        }
        for (ControlView v : views) position(v, w, h);
    }

    private void position(@NonNull ControlView v, int w, int h) {
        Control c = v.control;
        int px = Math.round(c.baseDp() * c.size * density);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp.width != px) {
            lp.width = px;
            lp.height = px;
            v.setLayoutParams(lp);
        }
        float x = c.cx * w - px / 2f;
        float y = c.cy * h - px / 2f;
        v.setX(Math.max(0, Math.min(w - px, x)));
        v.setY(Math.max(0, Math.min(h - px, y)));
    }

    private void save() {
        saveLayout(activity, layout);
    }

    // ------------------------------------------------------------------
    // 編集
    // ------------------------------------------------------------------

    void setEditing(boolean on) {
        if (editing == on) return;
        if (on && !shown) {
            setShown(activity, true);
            apply();
        }
        editing = on;
        releaseAll();
        if (on) {
            View s = new View(activity);
            s.setBackgroundColor(0x99000000);
            s.setClickable(true);
            int index = views.isEmpty() ? parent.getChildCount() : parent.indexOfChild(views.get(0));
            parent.addView(s, index, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            scrim = s;
            toolbar = buildToolbar();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.topMargin = Math.round(12 * density);
            parent.addView(toolbar, lp);
        } else {
            if (scrim != null) parent.removeView(scrim);
            if (toolbar != null) parent.removeView(toolbar);
            scrim = null;
            toolbar = null;
            save();
        }
        for (ControlView v : views) v.invalidate();
        applyOpacity();
        host.onControllerEditingChanged(on);
    }

    private View buildToolbar() {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(6 * density);
        bar.setPadding(pad, pad, pad, pad);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(28 * density);
        bg.setColor(MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurfaceContainerHigh,
                0xFFECEFF1));
        bar.setBackground(bg);
        bar.setElevation(6 * density);
        bar.addView(toolButton(Lang.t("追加"), R.drawable.ic_add, v -> addControl()));
        bar.addView(toolButton(Lang.t("プリセット"), 0, v -> choosePreset()));
        bar.addView(toolButton(Lang.t("透明度"), 0, v -> chooseOpacity()));
        MaterialButton done = new MaterialButton(activity);
        done.setText(Lang.t("完了"));
        done.setOnClickListener(v -> setEditing(false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(Math.round(4 * density));
        bar.addView(done, lp);
        return bar;
    }

    private MaterialButton toolButton(String text, int icon, View.OnClickListener l) {
        MaterialButton b = new MaterialButton(activity, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        b.setText(text);
        if (icon != 0) {
            b.setIconResource(icon);
            b.setIconPadding(Math.round(4 * density));
        }
        b.setOnClickListener(l);
        return b;
    }

    private void addControl() {
        CharSequence[] labels = {Lang.t("ボタン"), Lang.t("十字キー"), Lang.t("スティック")};
        new MaterialAlertDialogBuilder(activity)
                .setTitle(Lang.t("追加する部品"))
                .setItems(labels, (d, which) -> {
                    Control c;
                    if (which == 1) {
                        c = new Control(T_DPAD, 0.5f, 0.5f, InputRouter.ARROWS, "", 1f);
                    } else if (which == 2) {
                        c = new Control(T_STICK, 0.5f, 0.5f, InputRouter.STICK_LEFT, "", 1f);
                    } else {
                        c = new Control(T_BUTTON, 0.5f, 0.5f, InputRouter.key(android.view.KeyEvent.KEYCODE_SPACE), "", 1f);
                    }
                    layout.add(c);
                    addView(c);
                    positionAll();
                    applyOpacity();
                    save();
                    edit(c);
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    private void choosePreset() {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(Lang.t("プリセットから配置"))
                .setItems(PRESET_LABELS, (d, which) -> new MaterialAlertDialogBuilder(activity)
                        .setTitle(Lang.t("配置を置き換えますか?"))
                        .setMessage(Lang.t("今のボタンの配置と割り当ては「") + PRESET_LABELS[which] + Lang.t("」に置き換わります。"))
                        .setPositiveButton(Lang.t("置き換える"), (d2, w2) -> {
                            saveLayout(activity, preset(which));
                            rebuild();
                        })
                        .setNegativeButton(Lang.t("キャンセル"), null)
                        .show())
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    /** 透明度を選ぶ(入力の設定からも使う)。 */
    void chooseOpacity() {
        showOpacityDialog(activity, this::applyOpacity);
    }

    static void showOpacityDialog(@NonNull Activity activity, @NonNull Runnable onChanged) {
        float d = activity.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Math.round(24 * d), Math.round(8 * d), Math.round(24 * d), 0);
        TextView value = new TextView(activity);
        value.setGravity(Gravity.CENTER);
        float current = opacity(activity);
        value.setText(Math.round(current * 100) + "%");
        box.addView(value);
        Slider slider = new Slider(activity);
        slider.setValueFrom(15);
        slider.setValueTo(100);
        slider.setStepSize(5);
        slider.setValue(Math.round(current * 20) * 5f);
        slider.addOnChangeListener((s, v, fromUser) -> {
            value.setText(Math.round(v) + "%");
            setOpacity(activity, v / 100f);
            onChanged.run();
        });
        box.addView(slider);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(Lang.t("ボタンの不透明度"))
                .setView(box)
                .setPositiveButton("OK", null)
                .show();
    }

    /** 部品の設定(割り当て・文字・大きさ・削除)。 */
    private void edit(@NonNull Control c) {
        if (c.type == T_MENU) return;
        // ボタンは上の画面にあるので、二画面でも上の画面に出す
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        Context ctx = activity;
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Math.round(20 * density);
        box.setPadding(p, Math.round(8 * density), p, Math.round(24 * density));

        TextView title = new TextView(ctx);
        title.setText(c.type == T_DPAD ? Lang.t("十字キー") : c.type == T_STICK ? Lang.t("スティック") : Lang.t("ボタン"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTextColor(MaterialColors.getColor(box, com.google.android.material.R.attr.colorOnSurface));
        box.addView(title);

        boolean direction = c.type != T_BUTTON;
        MaterialButton binding = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        binding.setText(Lang.t("入力: ") + InputRouter.describe(c.binding));
        binding.setOnClickListener(v -> BindingPicker.show(ctx, Lang.t("入力の割り当て"), direction, false, c.binding, b -> {
            c.binding = b;
            binding.setText(Lang.t("入力: ") + InputRouter.describe(b));
            refreshView(c);
            save();
        }));
        LinearLayout.LayoutParams full = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        full.topMargin = Math.round(16 * density);
        box.addView(binding, full);

        if (c.type == T_BUTTON) {
            EditText label = new EditText(ctx);
            label.setHint(Lang.t("ボタンの文字(空なら割り当てから)"));
            label.setSingleLine(true);
            label.setInputType(InputType.TYPE_CLASS_TEXT);
            label.setText(c.label);
            label.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int st, int co, int af) {
                }

                @Override
                public void onTextChanged(CharSequence s, int st, int be, int co) {
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    c.label = s.toString().trim();
                    refreshView(c);
                    save();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(8 * density);
            box.addView(label, lp);
        }

        TextView sizeLabel = new TextView(ctx);
        sizeLabel.setText(Lang.t("大きさ"));
        sizeLabel.setPadding(0, Math.round(16 * density), 0, 0);
        sizeLabel.setTextColor(MaterialColors.getColor(box, com.google.android.material.R.attr.colorOnSurfaceVariant));
        box.addView(sizeLabel);
        Slider size = new Slider(ctx);
        size.setValueFrom(60);
        size.setValueTo(180);
        size.setStepSize(10);
        size.setValue(Math.max(60, Math.min(180, Math.round(c.size * 10) * 10f)));
        size.addOnChangeListener((s, v, fromUser) -> {
            c.size = v / 100f;
            positionAll();
            save();
        });
        box.addView(size);

        MaterialButton delete = new MaterialButton(ctx, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        delete.setText(Lang.t("この部品を削除"));
        delete.setTextColor(MaterialColors.getColor(box, androidx.appcompat.R.attr.colorError, 0xFFB3261E));
        delete.setOnClickListener(v -> {
            dialog.dismiss();
            for (int i = 0; i < views.size(); i++) {
                if (views.get(i).control == c) {
                    parent.removeView(views.get(i));
                    views.remove(i);
                    break;
                }
            }
            layout.remove(c);
            save();
        });
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.topMargin = Math.round(8 * density);
        box.addView(delete, dl);

        dialog.setContentView(SheetScroll.wrap(box));
        dialog.show();
    }

    private void refreshView(@NonNull Control c) {
        for (ControlView v : views) if (v.control == c) v.invalidate();
    }

    // ------------------------------------------------------------------
    // 部品の絵とタッチ
    // ------------------------------------------------------------------

    private final class ControlView extends View {
        final Control control;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int slop;
        private final int accent;
        private final int onAccent;
        private int pointerId = -1;
        private boolean pressed;
        /** 十字キー・スティックの今の向き(-1〜1)。 */
        private float vx, vy;
        // 編集中のドラッグ
        private float downRawX, downRawY, startX, startY;
        private boolean dragging;

        ControlView(Context context, Control control) {
            super(context);
            this.control = control;
            slop = ViewConfiguration.get(context).getScaledTouchSlop();
            accent = 0xFFA8C7FA;
            onAccent = 0xFF062E6F;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1.5f * density);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            dash.setStyle(Paint.Style.STROKE);
            dash.setStrokeWidth(1.5f * density);
            dash.setColor(0xFFFFFFFF);
            dash.setPathEffect(new DashPathEffect(new float[]{6 * density, 4 * density}, 0));
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setContentDescription(control.type == T_MENU ? Lang.t("仮想コントローラーの設定") : control.displayLabel());
            setHapticFeedbackEnabled(true);
        }

        void cancel() {
            if (pointerId >= 0 || pressed || vx != 0 || vy != 0) {
                pointerId = -1;
                pressed = false;
                vx = vy = 0;
                router.releaseOwner(this);
                invalidate();
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float r = Math.min(w, h) / 2f - 2 * density;
            float cx = w / 2f, cy = h / 2f;
            fill.setColor(0xB3202124);
            stroke.setColor(0x80FFFFFF);
            switch (control.type) {
                case T_DPAD:
                    drawDpad(canvas, cx, cy, r);
                    break;
                case T_STICK:
                    drawStick(canvas, cx, cy, r);
                    break;
                case T_MENU:
                    drawMenu(canvas, cx, cy, r);
                    break;
                default:
                    drawButton(canvas, cx, cy, r);
                    break;
            }
            if (editing) {
                rect.set(density, density, w - density, h - density);
                canvas.drawRoundRect(rect, 10 * density, 10 * density, dash);
            }
        }

        private void drawButton(Canvas canvas, float cx, float cy, float r) {
            if (pressed) fill.setColor(accent);
            canvas.drawCircle(cx, cy, r, fill);
            canvas.drawCircle(cx, cy, r, stroke);
            String label = control.displayLabel();
            text.setColor(pressed ? onAccent : 0xFFFFFFFF);
            float size = label.length() <= 1 ? r * 0.8f : label.length() <= 3 ? r * 0.55f : r * 0.36f;
            text.setTextSize(size);
            canvas.drawText(label, cx, cy - (text.descent() + text.ascent()) / 2f, text);
        }

        private void drawDpad(Canvas canvas, float cx, float cy, float r) {
            float arm = r * 0.36f; // 腕の半分の太さ
            float rr = arm * 0.5f;
            // 縦・横の腕
            rect.set(cx - arm, cy - r, cx + arm, cy + r);
            canvas.drawRoundRect(rect, rr, rr, fill);
            rect.set(cx - r, cy - arm, cx + r, cy + arm);
            canvas.drawRoundRect(rect, rr, rr, fill);
            // 押している向き
            Paint hi = text;
            hi.setColor(accent);
            if (vy < 0) canvas.drawRoundRect(cx - arm, cy - r, cx + arm, cy - arm, rr, rr, hi);
            if (vy > 0) canvas.drawRoundRect(cx - arm, cy + arm, cx + arm, cy + r, rr, rr, hi);
            if (vx < 0) canvas.drawRoundRect(cx - r, cy - arm, cx - arm, cy + arm, rr, rr, hi);
            if (vx > 0) canvas.drawRoundRect(cx + arm, cy - arm, cx + r, cy + arm, rr, rr, hi);
            // 腕の先の三角
            Paint tri = new Paint(Paint.ANTI_ALIAS_FLAG);
            tri.setColor(0xCCFFFFFF);
            float t = arm * 0.55f, o = r * 0.72f;
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(cx, cy - o - t * 0.6f); p.lineTo(cx - t, cy - o + t * 0.6f); p.lineTo(cx + t, cy - o + t * 0.6f); p.close();
            p.moveTo(cx, cy + o + t * 0.6f); p.lineTo(cx - t, cy + o - t * 0.6f); p.lineTo(cx + t, cy + o - t * 0.6f); p.close();
            p.moveTo(cx - o - t * 0.6f, cy); p.lineTo(cx - o + t * 0.6f, cy - t); p.lineTo(cx - o + t * 0.6f, cy + t); p.close();
            p.moveTo(cx + o + t * 0.6f, cy); p.lineTo(cx + o - t * 0.6f, cy - t); p.lineTo(cx + o - t * 0.6f, cy + t); p.close();
            canvas.drawPath(p, tri);
        }

        private void drawStick(Canvas canvas, float cx, float cy, float r) {
            Paint base = fill;
            base.setColor(0x80202124);
            canvas.drawCircle(cx, cy, r, base);
            canvas.drawCircle(cx, cy, r, stroke);
            float kr = r * 0.42f;
            float range = r - kr;
            float kx = cx + vx * range, ky = cy + vy * range;
            Paint knob = text;
            knob.setColor(pointerId >= 0 ? accent : 0xE6303134);
            canvas.drawCircle(kx, ky, kr, knob);
            stroke.setColor(0xB3FFFFFF);
            canvas.drawCircle(kx, ky, kr, stroke);
            String label = InputRouter.MOUSE_MOVE.equals(control.binding) ? "◎"
                    : InputRouter.WASD.equals(control.binding) ? "WASD" : "";
            if (!label.isEmpty()) {
                text.setColor(pointerId >= 0 ? onAccent : 0xFFFFFFFF);
                text.setTextSize(kr * (label.length() > 1 ? 0.45f : 0.9f));
                canvas.drawText(label, kx, ky - (text.descent() + text.ascent()) / 2f, text);
            }
        }

        private void drawMenu(Canvas canvas, float cx, float cy, float r) {
            canvas.drawCircle(cx, cy, r, fill);
            android.graphics.drawable.Drawable icon = ContextCompat.getDrawable(getContext(), R.drawable.ic_tool_gamepad);
            if (icon != null) {
                icon = icon.mutate();
                icon.setTintList(ColorStateList.valueOf(0xFFFFFFFF));
                int s = Math.round(r * 1.1f);
                icon.setBounds(Math.round(cx - s / 2f), Math.round(cy - s / 2f), Math.round(cx + s / 2f), Math.round(cy + s / 2f));
                icon.draw(canvas);
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (editing) return onEditTouch(ev);
            int action = ev.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN: {
                    pointerId = ev.getPointerId(0);
                    if (control.type == T_MENU) {
                        pressed = true;
                        invalidate();
                        return true;
                    }
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    onPointer(ev.getX(0), ev.getY(0));
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    int i = ev.findPointerIndex(pointerId);
                    if (i >= 0 && control.type != T_MENU) onPointer(ev.getX(i), ev.getY(i));
                    return true;
                }
                case MotionEvent.ACTION_POINTER_UP:
                    if (ev.getPointerId(ev.getActionIndex()) != pointerId) return true;
                    // 押していた指を離した
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    boolean menu = control.type == T_MENU && pressed && action == MotionEvent.ACTION_UP;
                    pointerId = -1;
                    pressed = false;
                    vx = vy = 0;
                    router.releaseOwner(this);
                    invalidate();
                    if (menu) host.openControllerSettings();
                    return true;
                }
                default:
                    return true;
            }
        }

        private void onPointer(float x, float y) {
            float w = getWidth(), h = getHeight();
            float r = Math.min(w, h) / 2f;
            float dx = (x - w / 2f) / r, dy = (y - h / 2f) / r;
            switch (control.type) {
                case T_DPAD: {
                    // 8 方向。真ん中付近は何も押さない
                    float nx = Math.abs(dx) > 0.28f && Math.abs(dx) > Math.abs(dy) * 0.45f ? Math.signum(dx) : 0;
                    float ny = Math.abs(dy) > 0.28f && Math.abs(dy) > Math.abs(dx) * 0.45f ? Math.signum(dy) : 0;
                    if (nx != vx || ny != vy) {
                        if ((nx != 0 || ny != 0) && (vx != nx || vy != ny)) {
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        }
                        vx = nx;
                        vy = ny;
                        router.direction(this, control.binding, vx, vy);
                        invalidate();
                    }
                    break;
                }
                case T_STICK: {
                    float len = (float) Math.hypot(dx, dy);
                    float range = 1f - 0.42f;
                    float nx = dx / range, ny = dy / range;
                    float nl = len / range;
                    if (nl > 1f) {
                        nx /= nl;
                        ny /= nl;
                    }
                    vx = nx;
                    vy = ny;
                    router.direction(this, control.binding, vx, vy);
                    invalidate();
                    break;
                }
                default:
                    if (!pressed) {
                        pressed = true;
                        router.button(this, control.binding, true);
                        invalidate();
                    }
                    break;
            }
        }

        private boolean onEditTouch(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = ev.getRawX();
                    downRawY = ev.getRawY();
                    startX = getX();
                    startY = getY();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = ev.getRawX() - downRawX, dy = ev.getRawY() - downRawY;
                    if (!dragging && Math.hypot(dx, dy) > slop) dragging = true;
                    if (dragging) {
                        float nx = Math.max(0, Math.min(parent.getWidth() - getWidth(), startX + dx));
                        float ny = Math.max(0, Math.min(parent.getHeight() - getHeight(), startY + dy));
                        setX(nx);
                        setY(ny);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
                            control.cx = (getX() + getWidth() / 2f) / parent.getWidth();
                            control.cy = (getY() + getHeight() / 2f) / parent.getHeight();
                        }
                        save();
                    } else {
                        edit(control);
                    }
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        setX(startX);
                        setY(startY);
                    }
                    dragging = false;
                    return true;
                default:
                    return true;
            }
        }
    }
}
