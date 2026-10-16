package com.airsky.browser;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import org.mozilla.geckoview.PanZoomController;

/**
 * 仮想マウス(画面に出すマウスカーソル)。
 *
 * <p>ページの上にカーソルを出し、コントローラーのスティック・十字キー、または画面へのタッチ
 * (リモートデスクトップのようなトラックパッド操作)で動かす。クリック・右クリック・ホイールは
 * 本物のマウスと同じイベントとしてページへ送るので、マウス前提のサイト(カーソルを合わせると開く
 * メニュー、ドラッグ、右クリックメニュー)もそのまま操作できる。</p>
 *
 * <p>タッチ操作には 2 つの方式がある。</p>
 * <ul>
 *   <li>トラックパッド: 1 本指でカーソルを動かす。タップでクリック、2 本指タップで右クリック、
 *       2 本指で動かすとスクロール、2 本指を広げる・狭めると拡大縮小、3 本指タップで中クリック。
 *       長押しかタップしてすぐ押し直すとドラッグ。</li>
 *   <li>ダイレクト: 触れた所へカーソルが移る。タップでその場所をクリック、指で動かすとドラッグ、
 *       長押しで右クリック。2 本指の操作はトラックパッドと同じ。</li>
 * </ul>
 *
 * <p>ページがポインターロック(マウスの固定)を求めた時は、カーソルを隠して動きだけをページへ送る
 * (FPS のように視点を動かすゲーム用)。3 本指タップでロックを解除できる。</p>
 *
 * <p>二画面の時はページが上下の画面につながっているので、カーソルも下の画面まで動かせる
 * (下の画面のカーソルは {@link DualScreenController} が描く)。</p>
 *
 * <p>座標はすべて「表示中のタブのビューから見た位置」。二画面の下の画面の部分は、タブの高さより
 * 下の位置になる(下の画面に触れた時と同じ扱い)。</p>
 */
final class VirtualMouse implements Choreographer.FrameCallback {

    interface Host {
        @Nullable
        GeckoTabView currentTab();

        /** タブの上に重ねたネイティブのスタートページを出しているか(出ていれば、そちらをタップで操作する)。 */
        boolean isStartPageShowing();

        /** スタートページの置き場(タブと同じ位置・大きさ)。 */
        @NonNull
        ViewGroup nativePageArea();

        /** 二画面表示(使っていなければ null)。 */
        @Nullable
        DualScreenController dualScreen();

        /** 仮想マウスを使い始めた/やめた・スクロールモードが変わった(案内を出す)。 */
        void onMouseStateChanged(boolean active, boolean scrollMode);

        /** ポインターロック中のマウスの動き(CSS px)をページへ送る。 */
        void sendLockedMove(float dx, float dy);

        /** ポインターロックを解除する(3 本指タップ)。 */
        void exitPointerLock();

        /** 仮想マウスの設定(入力のシートのマウスのタブ)を開く。 */
        void openMouseSettings();
    }

    // ------------------------------------------------------------------
    // 設定
    // ------------------------------------------------------------------

    static final int STYLE_TRACKPAD = 0;
    static final int STYLE_DIRECT = 1;

    private static final String PREFS = "virtual_mouse";
    private static final String KEY_STYLE = "touch_style";
    private static final String KEY_TOUCH_SPEED = "touch_speed";
    private static final String KEY_LOCK_SPEED = "lock_speed";
    /** カーソルを隠している時の速さの上限(ふつうの速さの上限 10 の 2 倍)。 */
    static final int MAX_LOCK_SPEED = 20;
    private static final String KEY_COLOR = "cursor_color";
    private static final String KEY_SIZE = "cursor_size";

    static final String[] STYLE_LABELS = {Lang.t("トラックパッド"), Lang.t("ダイレクト")};
    static final String[] COLOR_LABELS = {Lang.t("ホワイト"), Lang.t("ブラック"), Lang.t("アクセント"), Lang.t("グリーン"), Lang.t("イエロー"), Lang.t("ピンク"), Lang.t("ブルー")};
    /** カーソルの塗り(アクセントはテーマの色を使うので 0)。 */
    private static final int[] COLOR_FILLS = {0xFFFFFFFF, 0xFF1F1F1F, 0, 0xFF6DD58C, 0xFFFDD663, 0xFFF8B5D0, 0xFFA8C7FA};
    static final String[] SIZE_LABELS = {Lang.t("小"), Lang.t("中"), Lang.t("大"), Lang.t("特大")};
    private static final float[] SIZE_SCALES = {0.85f, 1f, 1.3f, 1.65f};

    private static SharedPreferences prefs(@NonNull Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int touchStyle(@NonNull Context c) {
        return prefs(c).getInt(KEY_STYLE, STYLE_TRACKPAD) == STYLE_DIRECT ? STYLE_DIRECT : STYLE_TRACKPAD;
    }

    static void setTouchStyle(@NonNull Context c, int style) {
        prefs(c).edit().putInt(KEY_STYLE, style).apply();
    }

    /** タッチ操作の速さ(1〜10、5 が標準)。 */
    static int touchSpeed(@NonNull Context c) {
        return Math.max(1, Math.min(10, prefs(c).getInt(KEY_TOUCH_SPEED, 5)));
    }

    static void setTouchSpeed(@NonNull Context c, int speed) {
        prefs(c).edit().putInt(KEY_TOUCH_SPEED, Math.max(1, Math.min(10, speed))).apply();
    }

    /**
     * マウスを固定して(カーソルを隠して)視点を動かしている時の速さ(1〜20、5 が標準)。
     * タッチ・マウス・コントローラーのどれで動かしても、この速さを使う。
     */
    static int lockSpeed(@NonNull Context c) {
        return Math.max(1, Math.min(MAX_LOCK_SPEED, prefs(c).getInt(KEY_LOCK_SPEED, 5)));
    }

    static void setLockSpeed(@NonNull Context c, int speed) {
        prefs(c).edit().putInt(KEY_LOCK_SPEED, Math.max(1, Math.min(MAX_LOCK_SPEED, speed))).apply();
    }

    static int cursorColor(@NonNull Context c) {
        int v = prefs(c).getInt(KEY_COLOR, 0);
        return v < 0 || v >= COLOR_FILLS.length ? 0 : v;
    }

    static void setCursorColor(@NonNull Context c, int color) {
        prefs(c).edit().putInt(KEY_COLOR, color).apply();
    }

    static int cursorSize(@NonNull Context c) {
        int v = prefs(c).getInt(KEY_SIZE, 1);
        return v < 0 || v >= SIZE_SCALES.length ? 1 : v;
    }

    static void setCursorSize(@NonNull Context c, int size) {
        prefs(c).edit().putInt(KEY_SIZE, size).apply();
    }

    // ------------------------------------------------------------------

    private static final float DEAD_ZONE = 0.18f;

    private final Context context;
    private final Host host;
    private final CursorView topCursor;
    private final TouchLayer touchLayer;
    private final float density;

    private boolean active;
    /** 画面へのタッチでカーソルを動かす(ツールから使い始めた時)。コントローラーから使い始めた時は false。 */
    private boolean touchMode;
    private boolean scrollMode;
    private float x = -1;
    private float y = -1;
    /** 押しているボタン(MotionEvent.BUTTON_*)。 */
    private int buttons;
    private long downTime;

    /** ページがマウスを固定している(ポインターロック)。カーソルは隠し、動きだけを送る。 */
    private boolean locked;
    private float lockX, lockY;

    // スティック・十字キーの入力
    private float leftX, leftY, rightX, rightY;
    private int dpadX, dpadY;
    private long dpadSince;
    // 仮想コントローラーなど、ほかの入力からの「カーソル移動」「スクロール」(-1〜1)
    private float auxMoveX, auxMoveY, auxScrollX, auxScrollY;
    // 2 本指スクロールの慣性(px/秒)
    private float flingX, flingY;
    private boolean frameScheduled;
    private long lastFrameNanos;
    /** カーソルを動かしたのにまだページへ伝えていない。 */
    private boolean moved;

    VirtualMouse(@NonNull Context context, @NonNull Host host, @NonNull ViewGroup overlayParent) {
        this.context = context;
        this.host = host;
        this.density = context.getResources().getDisplayMetrics().density;
        touchLayer = new TouchLayer(context);
        touchLayer.setVisibility(View.GONE);
        overlayParent.addView(touchLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        topCursor = new CursorView(context);
        overlayParent.addView(topCursor, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        chip = buildChip(context);
        chip.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM | android.view.Gravity.START);
        int m = Math.round(12 * density);
        lp.setMargins(m, m, m, m);
        overlayParent.addView(chip, lp);
    }

    /** タッチで操作している間、左下に出す小さな札(設定を開く・仮想マウスをやめる)。 */
    private final View chip;

    private View buildChip(@NonNull Context c) {
        android.widget.LinearLayout box = new android.widget.LinearLayout(c);
        box.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        box.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(20 * density);
        bg.setColor(0xB3202124);
        box.setBackground(bg);
        box.setAlpha(0.85f);
        int pad = Math.round(2 * density);
        box.setPadding(pad, pad, pad, pad);
        box.addView(chipButton(c, R.drawable.ic_tool_mouse, Lang.t("仮想マウスの設定"), v -> host.openMouseSettings()));
        box.addView(chipButton(c, R.drawable.ic_close, Lang.t("仮想マウスをやめる"), v -> setActive(false)));
        return box;
    }

    private View chipButton(@NonNull Context c, int icon, @NonNull String description, View.OnClickListener l) {
        android.widget.ImageButton b = new android.widget.ImageButton(c);
        b.setImageResource(icon);
        b.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        android.util.TypedValue tv = new android.util.TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        b.setBackgroundResource(tv.resourceId);
        b.setContentDescription(description);
        b.setTooltipText(description);
        b.setOnClickListener(l);
        int size = Math.round(36 * density);
        b.setLayoutParams(new android.widget.LinearLayout.LayoutParams(size, size));
        b.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        return b;
    }

    boolean isActive() {
        return active;
    }

    boolean isTouchMode() {
        return active && touchMode;
    }

    boolean isScrollMode() {
        return scrollMode;
    }

    boolean isPointerLocked() {
        return locked;
    }

    /** コントローラーから切り替える(タッチ操作はページへそのまま渡す)。 */
    void toggle() {
        setActive(!active, false);
    }

    void setActive(boolean on) {
        setActive(on, touchMode);
    }

    /**
     * @param touch 画面へのタッチでカーソルを操作する(ツール・入力シートから使う時)
     */
    void setActive(boolean on, boolean touch) {
        boolean touchChanged = on && touch != touchMode;
        if (active == on && !touchChanged) return;
        if (!on) releaseButtons();
        boolean was = active;
        active = on;
        if (on) touchMode = touch;
        if (on && !was) {
            GeckoTabView tab = host.currentTab();
            if (x < 0 && tab != null) {
                x = tab.getWidth() / 2f;
                y = tab.getHeight() / 2f;
            }
            clampCursor();
            moved = true;
        } else if (!on) {
            scrollMode = false;
            touchLayer.reset();
        }
        touchLayer.setVisibility(active && touchMode ? View.VISIBLE : View.GONE);
        chip.setVisibility(active && touchMode ? View.VISIBLE : View.GONE);
        chip.bringToFront();
        topCursor.applyStyle();
        updateCursorViews();
        if (was != on) host.onMouseStateChanged(active, scrollMode);
        scheduleFrame();
    }

    void toggleScrollMode() {
        if (!active) setActive(true, false);
        scrollMode = !scrollMode;
        updateCursorViews();
        host.onMouseStateChanged(active, scrollMode);
    }

    /** カーソルの色・大きさの設定を読み直す。 */
    void reloadStyle() {
        topCursor.applyStyle();
        DualScreenController dual = host.dualScreen();
        if (dual != null) dual.setBottomCursor(active && !locked, x, y, scrollMode);
    }

    /** 速さの設定(1〜10、5 が標準)。 */
    private float speedFactor() {
        return ControllerPrefs.cursorSpeed(context) / 5f;
    }

    // ------------------------------------------------------------------
    // ポインターロック
    // ------------------------------------------------------------------

    /**
     * ページがマウスを固定した/解除した。
     *
     * @param px 固定した位置(仮想マウスを使っていない時。実際のマウスの最後の位置)。分からなければ負の値
     */
    void setPointerLocked(boolean on, float px, float py) {
        if (locked == on) return;
        if (!on) releaseButtons();
        locked = on;
        if (on) {
            GeckoTabView tab = host.currentTab();
            if (active) {
                lockX = x;
                lockY = y;
            } else if (px >= 0 && py >= 0) {
                lockX = px;
                lockY = py;
            } else if (tab != null) {
                lockX = tab.getWidth() / 2f;
                lockY = tab.getHeight() / 2f;
            }
            flingX = flingY = 0;
        }
        updateCursorViews();
    }

    /** クリック・ホイールを送る位置。 */
    private float pointX() {
        return locked ? lockX : x;
    }

    private float pointY() {
        return locked ? lockY : y;
    }

    // ------------------------------------------------------------------
    // コントローラー・ほかの入力
    // ------------------------------------------------------------------

    /** スティックの傾き(-1〜1)。左はカーソル(スクロールモードではスクロール)、右はスクロール。 */
    void setSticks(float lx, float ly, float rx, float ry) {
        leftX = dead(lx);
        leftY = dead(ly);
        rightX = dead(rx);
        rightY = dead(ry);
        scheduleFrame();
    }

    /** 十字キー(仮想マウスの間だけカーソルを動かす)。押していない向きは 0。 */
    void setDpad(int dx, int dy) {
        if ((dx != 0 || dy != 0) && dpadX == 0 && dpadY == 0) dpadSince = SystemClock.uptimeMillis();
        dpadX = dx;
        dpadY = dy;
        scheduleFrame();
    }

    /** 仮想コントローラーのスティックなどでカーソルを動かす(-1〜1。ロック中は視点の動きになる)。 */
    void setAuxMove(float ax, float ay) {
        auxMoveX = ax;
        auxMoveY = ay;
        scheduleFrame();
    }

    /** 仮想コントローラーのスティックなどでスクロールする(-1〜1)。 */
    void setAuxScroll(float ax, float ay) {
        auxScrollX = ax;
        auxScrollY = ay;
        scheduleFrame();
    }

    private static float dead(float v) {
        if (Math.abs(v) < DEAD_ZONE) return 0f;
        // 遊びの分を差し引いて 0〜1 に直す
        return Math.signum(v) * (Math.abs(v) - DEAD_ZONE) / (1f - DEAD_ZONE);
    }

    private boolean hasContinuousInput() {
        return leftX != 0 || leftY != 0 || rightX != 0 || rightY != 0 || dpadX != 0 || dpadY != 0
                || auxMoveX != 0 || auxMoveY != 0 || auxScrollX != 0 || auxScrollY != 0
                || flingX != 0 || flingY != 0;
    }

    private void scheduleFrame() {
        if (frameScheduled) return;
        if (!hasContinuousInput() && !moved) return;
        frameScheduled = true;
        lastFrameNanos = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        frameScheduled = false;
        float dt = lastFrameNanos == 0 ? 1f / 60f : Math.min(0.05f, (frameTimeNanos - lastFrameNanos) / 1e9f);
        lastFrameNanos = frameTimeNanos;

        float scrollX = 0f;
        float scrollY = 0f;
        // 右スティックはいつでもスクロール
        scrollX += curve(rightX) * scrollSpeed() * dt;
        scrollY += curve(rightY) * scrollSpeed() * dt;
        scrollX += curve(auxScrollX) * scrollSpeed() * dt;
        scrollY += curve(auxScrollY) * scrollSpeed() * dt;

        // 2 本指スクロールの慣性
        if (flingX != 0 || flingY != 0) {
            scrollX += flingX * dt;
            scrollY += flingY * dt;
            float decay = (float) Math.pow(0.04, dt); // 1 秒で 4% まで落ちる
            flingX *= decay;
            flingY *= decay;
            if (Math.abs(flingX) < 40 * density && Math.abs(flingY) < 40 * density) {
                flingX = 0;
                flingY = 0;
            }
        }

        float speed = 1100f * density * (locked ? lockSpeed(context) / 5f : speedFactor());
        if (locked) {
            // 固定中: 左スティック・仮想スティックは視点(マウスの動き)
            float dx = (curve(leftX) + curve(auxMoveX)) * speed * dt * 0.6f;
            float dy = (curve(leftY) + curve(auxMoveY)) * speed * dt * 0.6f;
            if (dx != 0 || dy != 0) host.sendLockedMove(dx / density, dy / density);
        } else if (active && !scrollMode) {
            float dx = (curve(leftX) + curve(auxMoveX)) * speed * dt;
            float dy = (curve(leftY) + curve(auxMoveY)) * speed * dt;
            if (dpadX != 0 || dpadY != 0) {
                // 十字キーは押し続けるほど速くする(細かく合わせやすく、遠くへも行ける)
                float held = (SystemClock.uptimeMillis() - dpadSince) / 1000f;
                float s = (220f + Math.min(1f, held / 0.8f) * 700f) * density * speedFactor();
                dx += dpadX * s * dt;
                dy += dpadY * s * dt;
            }
            if (dx != 0 || dy != 0) {
                float ox = x, oy = y;
                x += dx;
                y += dy;
                clampCursor();
                // 端に当たってもまだ押している分は、その向きへページをスクロールする
                float overY = (oy + dy) - y;
                float overX = (ox + dx) - x;
                if (Math.abs(overY) > 0.5f) scrollY += overY * 1.5f;
                if (Math.abs(overX) > 0.5f) scrollX += overX * 1.5f;
                moved = true;
            }
        } else {
            // 仮想マウスを使っていない時・スクロールモードの時は左スティックもスクロール
            scrollX += (curve(leftX) + curve(auxMoveX)) * scrollSpeed() * dt;
            scrollY += (curve(leftY) + curve(auxMoveY)) * scrollSpeed() * dt;
            if (!active && (dpadX != 0 || dpadY != 0)) {
                scrollY += dpadY * scrollSpeed() * 0.6f * dt;
            }
        }

        if (moved) {
            moved = false;
            updateCursorViews();
            if (active && !locked) sendMove();
        }
        if (scrollX != 0 || scrollY != 0) scrollPixels(scrollX, scrollY);

        if (hasContinuousInput()) {
            frameScheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    /** 小さく傾けた時はゆっくり、大きく傾けると速く。 */
    private static float curve(float v) {
        return v * Math.abs(v);
    }

    private float scrollSpeed() {
        return 2600f * density * speedFactor();
    }

    // ------------------------------------------------------------------
    // カーソルを動かす(タッチ)
    // ------------------------------------------------------------------

    /** カーソルを相対的に動かす(px)。ロック中はページへ動きとして送る。 */
    void moveBy(float dx, float dy) {
        if (locked) {
            host.sendLockedMove(dx / density, dy / density);
            return;
        }
        if (!active) return;
        x += dx;
        y += dy;
        clampCursor();
        updateCursorViews();
        sendMove();
    }

    /** カーソルをその位置へ動かす(ダイレクト操作)。 */
    void moveTo(float nx, float ny) {
        if (!active || locked) return;
        x = nx;
        y = ny;
        clampCursor();
        updateCursorViews();
        sendMove();
    }

    // ------------------------------------------------------------------
    // クリック・スクロール・ズーム
    // ------------------------------------------------------------------

    /** マウスのボタンを押す(MotionEvent.BUTTON_PRIMARY / BUTTON_SECONDARY / BUTTON_TERTIARY)。 */
    void press(int button) {
        if (!active && !locked) return;
        if ((buttons & button) != 0) return;
        boolean first = buttons == 0;
        buttons |= button;
        topCursor.setPressed(true);
        DualScreenController dual = host.dualScreen();
        if (dual != null) dual.setBottomCursorPressed(true);
        if (host.isStartPageShowing() && !locked) {
            if (first && button == MotionEvent.BUTTON_PRIMARY) sendNativeTouch(MotionEvent.ACTION_DOWN);
            return;
        }
        if (first) {
            downTime = SystemClock.uptimeMillis();
            sendMouse(MotionEvent.ACTION_DOWN, buttons);
        } else {
            sendMouse(MotionEvent.ACTION_MOVE, buttons);
        }
    }

    /** マウスのボタンを離す。 */
    void release(int button) {
        if ((buttons & button) == 0) return;
        buttons &= ~button;
        if (buttons == 0) {
            topCursor.setPressed(false);
            DualScreenController dual = host.dualScreen();
            if (dual != null) dual.setBottomCursorPressed(false);
        }
        if (host.isStartPageShowing() && !locked) {
            if (button == MotionEvent.BUTTON_PRIMARY) sendNativeTouch(MotionEvent.ACTION_UP);
            return;
        }
        // 離したボタンの状態(残りのボタン)で送ると、ページには離したボタンの mouseup が届く
        sendMouse(buttons == 0 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE, buttons);
    }

    void click(int button) {
        press(button);
        release(button);
    }

    boolean isPressed(int button) {
        return (buttons & button) != 0;
    }

    void releaseButtons() {
        if ((buttons & MotionEvent.BUTTON_TERTIARY) != 0) release(MotionEvent.BUTTON_TERTIARY);
        if ((buttons & MotionEvent.BUTTON_SECONDARY) != 0) release(MotionEvent.BUTTON_SECONDARY);
        if ((buttons & MotionEvent.BUTTON_PRIMARY) != 0) release(MotionEvent.BUTTON_PRIMARY);
        buttons = 0;
    }

    /** 押したまま・傾けたままの入力を全部やめる(画面を離れる時)。 */
    void releaseAll() {
        releaseButtons();
        setSticks(0, 0, 0, 0);
        setDpad(0, 0);
        setAuxMove(0, 0);
        setAuxScroll(0, 0);
        flingX = flingY = 0;
        touchLayer.reset();
    }

    /** ボタンでのスクロール(1 回分)。正の値で下へ。 */
    void scrollStep(int direction) {
        GeckoTabView tab = host.currentTab();
        if (tab == null) return;
        scrollPixels(0, direction * tab.getHeight() * 0.35f);
    }

    /** ページを拡大・縮小する。 */
    void zoom(boolean in) {
        GeckoTabView tab = host.currentTab();
        if (tab != null) tab.zoomBy(in ? 1.25f : 0.8f);
    }

    /** カーソルの位置(仮想マウスを使っていない時は画面の真ん中)で、ホイールのスクロールを送る。 */
    void scrollPixels(float dxPx, float dyPx) {
        GeckoTabView tab = host.currentTab();
        if (tab == null || tab.getWidth() <= 0 || host.isStartPageShowing()) return;
        PanZoomController pzc = tab.getPanZoomController();
        float factor = pzc.getScrollFactor();
        if (factor <= 0) factor = 64f;
        float px = active || locked ? pointX() : tab.getWidth() / 2f;
        float py = active || locked ? pointY() : tab.getHeight() / 2f;
        MotionEvent.PointerProperties[] props = {mouseProps()};
        MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
        pc.x = px;
        pc.y = py;
        // ホイールは上が正(下へスクロールすると負)
        pc.setAxisValue(MotionEvent.AXIS_VSCROLL, -dyPx / factor);
        pc.setAxisValue(MotionEvent.AXIS_HSCROLL, dxPx / factor);
        long now = SystemClock.uptimeMillis();
        dispatch(tab, MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, props,
                new MotionEvent.PointerCoords[]{pc}, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0), true);
    }

    private void sendMove() {
        if (host.isStartPageShowing()) {
            if ((buttons & MotionEvent.BUTTON_PRIMARY) != 0) sendNativeTouch(MotionEvent.ACTION_MOVE);
            return;
        }
        if (buttons != 0) {
            sendMouse(MotionEvent.ACTION_MOVE, buttons);
        } else {
            sendMouse(MotionEvent.ACTION_HOVER_MOVE, 0);
        }
    }

    private static MotionEvent.PointerProperties mouseProps() {
        MotionEvent.PointerProperties p = new MotionEvent.PointerProperties();
        p.id = 0;
        p.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        return p;
    }

    private void sendMouse(int action, int buttonState) {
        GeckoTabView tab = host.currentTab();
        if (tab == null) return;
        MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
        pc.x = pointX();
        pc.y = pointY();
        pc.pressure = buttonState != 0 ? 1f : 0f;
        long now = SystemClock.uptimeMillis();
        long down = action == MotionEvent.ACTION_HOVER_MOVE ? now : downTime;
        MotionEvent e = MotionEvent.obtain(down, now, action, 1, new MotionEvent.PointerProperties[]{mouseProps()},
                new MotionEvent.PointerCoords[]{pc}, 0, buttonState, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        dispatch(tab, e, action == MotionEvent.ACTION_HOVER_MOVE);
    }

    /**
     * タブへ渡す。Gecko はイベントの画面上の位置と、ビューの中の位置の差からビューの位置を求めるので、
     * 画面上の位置で作ってからビューの中の位置へずらす(下の画面に触れた時と同じ)。
     */
    private void dispatch(@NonNull GeckoTabView tab, @NonNull MotionEvent e, boolean generic) {
        int[] origin = new int[2];
        tab.getLocationOnScreen(origin);
        MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
        e.getPointerCoords(0, pc);
        pc.x += origin[0];
        pc.y += origin[1];
        MotionEvent local = MotionEvent.obtain(e.getDownTime(), e.getEventTime(), e.getAction(), 1,
                new MotionEvent.PointerProperties[]{mouseProps()}, new MotionEvent.PointerCoords[]{pc},
                e.getMetaState(), e.getButtonState(), 1f, 1f, 0, 0, e.getSource(), 0);
        local.offsetLocation(-origin[0], -origin[1]);
        e.recycle();
        try {
            PanZoomController pzc = tab.getPanZoomController();
            if (generic) {
                pzc.onMotionEvent(local);
            } else {
                pzc.onTouchEvent(local);
            }
        } catch (Exception ignored) {
        } finally {
            local.recycle();
        }
    }

    /** 指 2 本のタッチをそのままタブへ渡す(ピンチでの拡大縮小)。 */
    private void dispatchFingers(int action, long down, @NonNull float[] xs, @NonNull float[] ys, int count) {
        GeckoTabView tab = host.currentTab();
        if (tab == null) return;
        int[] origin = new int[2];
        tab.getLocationOnScreen(origin);
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = i;
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = xs[i] + origin[0];
            coords[i].y = ys[i] + origin[1];
            coords[i].pressure = 1f;
            coords[i].size = 1f;
        }
        MotionEvent e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, count, props, coords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        e.offsetLocation(-origin[0], -origin[1]);
        try {
            tab.getPanZoomController().onTouchEvent(e);
        } catch (Exception ignored) {
        } finally {
            e.recycle();
        }
    }

    /** スタートページ(ネイティブの画面)を指でタップしたのと同じ操作を送る。 */
    private void sendNativeTouch(int action) {
        ViewGroup area = host.nativePageArea();
        GeckoTabView tab = host.currentTab();
        float topHeight = tab != null ? tab.getHeight() : area.getHeight();
        if (y > topHeight) return; // 下の画面はスタートページの続きの壁紙だけ
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) downTime = now;
        MotionEvent e = MotionEvent.obtain(downTime, now, action, x, y, 0);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        area.dispatchTouchEvent(e);
        e.recycle();
    }

    // ------------------------------------------------------------------
    // カーソルの位置と表示
    // ------------------------------------------------------------------

    /** カーソルを動かせる範囲(二画面の時は下の画面の分まで)に収める。 */
    private void clampCursor() {
        GeckoTabView tab = host.currentTab();
        if (tab == null || tab.getWidth() <= 0) return;
        float maxY = tab.getHeight();
        DualScreenController dual = host.dualScreen();
        if (dual != null) maxY += dual.bottomPageHeight();
        x = Math.max(0, Math.min(tab.getWidth() - 1, x));
        y = Math.max(0, Math.min(maxY - 1, y));
    }

    private void updateCursorViews() {
        boolean shown = active && !locked;
        topCursor.set(shown, x, y, scrollMode);
        DualScreenController dual = host.dualScreen();
        if (dual != null) dual.setBottomCursor(shown, x, y, scrollMode);
    }

    /** タブが替わった・画面の大きさが変わった時に、カーソルを範囲内へ戻す。 */
    void onLayoutChanged() {
        if (!active) return;
        clampCursor();
        updateCursorViews();
    }

    // ------------------------------------------------------------------
    // タッチ操作(トラックパッド・ダイレクト)
    // ------------------------------------------------------------------

    /** ページの上に重ねる透明な面。仮想マウスをタッチで使う間だけ出す。 */
    private final class TouchLayer extends View {
        private static final int G_NONE = 0;
        /** 1 本指を置いた(まだ動かしていない)。 */
        private static final int G_ONE = 1;
        /** 1 本指でカーソルを動かしている。 */
        private static final int G_MOVE = 2;
        /** 左ボタンを押したまま動かしている(ドラッグ)。 */
        private static final int G_DRAG = 3;
        /** 2 本指を置いた(まだ動かしていない)。 */
        private static final int G_TWO = 4;
        private static final int G_SCROLL = 5;
        private static final int G_PINCH = 6;
        private static final int G_THREE = 7;
        /** この操作は終わり。指を全部離すまで何もしない。 */
        private static final int G_DONE = 8;

        private static final long TAP_TIMEOUT = 260;
        private static final long LONG_PRESS = 450;
        private static final long TAP_DRAG_WINDOW = 280;

        private final int slop;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private int gesture = G_NONE;
        private long gestureStart;
        private float downX, downY, lastX, lastY;
        private long lastMoveTime;
        private float cx0, cy0, span0, lastCx, lastCy;
        private float velX, velY;
        private long lastTapUp;
        private float lastTapX, lastTapY;
        private boolean tapDrag;
        private long pinchDown;
        private final float[] px = new float[2];
        private final float[] py = new float[2];

        private final Runnable longPress = new Runnable() {
            @Override
            public void run() {
                if (gesture != G_ONE) return;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (style() == STYLE_DIRECT && !locked) {
                    // ダイレクト: 長押しで右クリック
                    click(MotionEvent.BUTTON_SECONDARY);
                    gesture = G_DONE;
                } else {
                    // トラックパッド: 長押しで左ボタンを押したまま(そのまま動かすとドラッグ)
                    press(MotionEvent.BUTTON_PRIMARY);
                    gesture = G_DRAG;
                }
            }
        };

        TouchLayer(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        private int style() {
            return touchStyle(getContext());
        }

        void reset() {
            handler.removeCallbacks(longPress);
            if (gesture == G_PINCH) endPinch();
            if (gesture == G_DRAG) release(MotionEvent.BUTTON_PRIMARY);
            gesture = G_NONE;
        }

        /** 指の動き(px)を、速く動かすほど大きく効くようにする(トラックパッドの加速)。 */
        private float gain(float dist, long dtMs) {
            if (locked) return 1.1f * lockSpeed(getContext()) / 5f; // 視点操作は一定の速さの方が狙いやすい
            float speed = touchSpeed(getContext()) / 5f;
            float v = dtMs > 0 ? dist / density / dtMs : 0f; // dp/ms
            float accel = Math.max(0f, Math.min(2.2f, (v - 0.25f) * 1.4f));
            return speed * (1.0f + accel);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                // スタートページ(ネイティブの画面)はそのまま指で操作する
                if (!active || !touchMode || (host.isStartPageShowing() && !locked)) return false;
            }
            long now = SystemClock.uptimeMillis();
            switch (action) {
                case MotionEvent.ACTION_DOWN: {
                    flingX = flingY = 0;
                    gesture = G_ONE;
                    gestureStart = now;
                    downX = lastX = ev.getX();
                    downY = lastY = ev.getY();
                    lastMoveTime = now;
                    tapDrag = !locked && style() == STYLE_TRACKPAD && now - lastTapUp < TAP_DRAG_WINDOW
                            && Math.hypot(downX - lastTapX, downY - lastTapY) < 60 * density;
                    if (style() == STYLE_DIRECT && !locked) moveTo(downX, downY);
                    handler.postDelayed(longPress, LONG_PRESS);
                    return true;
                }
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int count = ev.getPointerCount();
                    handler.removeCallbacks(longPress);
                    if (count == 2 && (gesture == G_ONE || gesture == G_MOVE)) {
                        gesture = G_TWO;
                        gestureStart = now;
                        cx0 = lastCx = centroidX(ev);
                        cy0 = lastCy = centroidY(ev);
                        span0 = span(ev);
                    } else if (count == 3 && gesture == G_TWO) {
                        gesture = G_THREE;
                    } else if (gesture == G_PINCH || gesture == G_SCROLL || gesture == G_TWO) {
                        // 3 本目以降の指は使わない
                    }
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    onMove(ev, now);
                    return true;
                case MotionEvent.ACTION_POINTER_UP: {
                    int remaining = ev.getPointerCount() - 1;
                    switch (gesture) {
                        case G_TWO:
                            if (now - gestureStart < TAP_TIMEOUT + 80) {
                                // 2 本指タップ: 右クリック
                                click(MotionEvent.BUTTON_SECONDARY);
                                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                            }
                            gesture = G_DONE;
                            break;
                        case G_THREE:
                            if (remaining <= 2) {
                                if (now - gestureStart < TAP_TIMEOUT + 160) {
                                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                                    if (locked) {
                                        host.exitPointerLock();
                                    } else {
                                        click(MotionEvent.BUTTON_TERTIARY);
                                    }
                                }
                                gesture = G_DONE;
                            }
                            break;
                        case G_SCROLL:
                            // 指を離した時の速さで少し滑らせる
                            flingX = -velX;
                            flingY = -velY;
                            scheduleFrame();
                            gesture = G_DONE;
                            break;
                        case G_PINCH:
                            endPinch();
                            gesture = G_DONE;
                            break;
                        default:
                            break;
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    handler.removeCallbacks(longPress);
                    if (gesture == G_ONE) {
                        if (now - gestureStart < LONG_PRESS) {
                            click(MotionEvent.BUTTON_PRIMARY);
                            lastTapUp = now;
                            lastTapX = ev.getX();
                            lastTapY = ev.getY();
                        }
                    } else if (gesture == G_DRAG) {
                        release(MotionEvent.BUTTON_PRIMARY);
                    } else if (gesture == G_PINCH) {
                        endPinch();
                    }
                    gesture = G_NONE;
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    reset();
                    return true;
                default:
                    return true;
            }
        }

        private void onMove(MotionEvent ev, long now) {
            switch (gesture) {
                case G_ONE: {
                    float dist = (float) Math.hypot(ev.getX() - downX, ev.getY() - downY);
                    if (dist < slop) return;
                    handler.removeCallbacks(longPress);
                    if (style() == STYLE_DIRECT && !locked) {
                        // ダイレクト: 指で動かすとドラッグ(触れた所でボタンを押す)
                        press(MotionEvent.BUTTON_PRIMARY);
                        gesture = G_DRAG;
                    } else if (tapDrag) {
                        press(MotionEvent.BUTTON_PRIMARY);
                        gesture = G_DRAG;
                    } else {
                        gesture = G_MOVE;
                    }
                    moveFinger(ev, now);
                    break;
                }
                case G_MOVE:
                case G_DRAG:
                    moveFinger(ev, now);
                    break;
                case G_TWO: {
                    if (ev.getPointerCount() < 2) return;
                    float cx = centroidX(ev), cy = centroidY(ev);
                    float sp = span(ev);
                    if (Math.abs(sp - span0) > slop * 2.2f && !locked) {
                        gesture = G_PINCH;
                        startPinch(ev);
                    } else if (Math.hypot(cx - cx0, cy - cy0) > slop) {
                        gesture = G_SCROLL;
                        lastCx = cx;
                        lastCy = cy;
                        lastMoveTime = now;
                        velX = velY = 0;
                    }
                    break;
                }
                case G_SCROLL: {
                    if (ev.getPointerCount() < 2) return;
                    float cx = centroidX(ev), cy = centroidY(ev);
                    float dx = cx - lastCx, dy = cy - lastCy;
                    long dt = Math.max(1, now - lastMoveTime);
                    lastCx = cx;
                    lastCy = cy;
                    lastMoveTime = now;
                    // 指と同じ向きにページが動く(指を上へ動かすと下へスクロール)
                    scrollPixels(-dx, -dy);
                    float ivx = dx / dt * 1000f, ivy = dy / dt * 1000f;
                    velX = velX * 0.6f + ivx * 0.4f;
                    velY = velY * 0.6f + ivy * 0.4f;
                    break;
                }
                case G_PINCH:
                    if (ev.getPointerCount() >= 2) {
                        copyFingers(ev);
                        dispatchFingers(MotionEvent.ACTION_MOVE, pinchDown, px, py, 2);
                    }
                    break;
                default:
                    break;
            }
        }

        private void moveFinger(MotionEvent ev, long now) {
            float nx = ev.getX(), ny = ev.getY();
            if (style() == STYLE_DIRECT && !locked) {
                moveTo(nx, ny);
            } else {
                float dx = nx - lastX, dy = ny - lastY;
                float g = gain((float) Math.hypot(dx, dy), now - lastMoveTime);
                moveBy(dx * g, dy * g);
            }
            lastX = nx;
            lastY = ny;
            lastMoveTime = now;
        }

        private void startPinch(MotionEvent ev) {
            pinchDown = SystemClock.uptimeMillis();
            copyFingers(ev);
            dispatchFingers(MotionEvent.ACTION_DOWN, pinchDown, px, py, 1);
            dispatchFingers(MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    pinchDown, px, py, 2);
        }

        private void endPinch() {
            dispatchFingers(MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    pinchDown, px, py, 2);
            dispatchFingers(MotionEvent.ACTION_UP, pinchDown, px, py, 1);
        }

        private void copyFingers(MotionEvent ev) {
            for (int i = 0; i < 2 && i < ev.getPointerCount(); i++) {
                px[i] = ev.getX(i);
                py[i] = ev.getY(i);
            }
        }

        private float centroidX(MotionEvent ev) {
            float s = 0;
            int n = Math.min(2, ev.getPointerCount());
            for (int i = 0; i < n; i++) s += ev.getX(i);
            return s / n;
        }

        private float centroidY(MotionEvent ev) {
            float s = 0;
            int n = Math.min(2, ev.getPointerCount());
            for (int i = 0; i < n; i++) s += ev.getY(i);
            return s / n;
        }

        private float span(MotionEvent ev) {
            if (ev.getPointerCount() < 2) return 0f;
            return (float) Math.hypot(ev.getX(0) - ev.getX(1), ev.getY(0) - ev.getY(1));
        }
    }

    // ------------------------------------------------------------------

    /**
     * カーソルの絵(Pixel 風)。角の丸い矢印に細い縁取りと柔らかい影。押している間はテーマの色の波紋を出す。
     * 色と大きさは設定で選べる。タッチは下へ通す。
     */
    static final class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint chevron = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private final Path chevrons = new Path();
        private final float density;
        private final int accent;
        private final int accentContainer;
        private final int onAccentContainer;
        private float scale = 1f;
        private boolean shown;
        private boolean scrollMode;
        private boolean pressed;
        private float cx, cy;
        /** 押した時の波紋(0〜1)。 */
        private float ripple;
        @Nullable private ValueAnimator rippleAnim;

        CursorView(@NonNull Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setWillNotDraw(false);
            accent = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0xFF0B57D0);
            accentContainer = MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorPrimaryContainer, 0xFFD3E3FD);
            onAccentContainer = MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFF041E49);
            fill.setStyle(Paint.Style.FILL);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            ring.setStyle(Paint.Style.FILL);
            pill.setStyle(Paint.Style.FILL);
            chevron.setStyle(Paint.Style.STROKE);
            chevron.setStrokeCap(Paint.Cap.ROUND);
            chevron.setStrokeJoin(Paint.Join.ROUND);
            applyStyle();
        }

        /** 設定(色・大きさ)を読み直す。 */
        void applyStyle() {
            Context c = getContext();
            int colorIndex = cursorColor(c);
            scale = SIZE_SCALES[cursorSize(c)];
            int fillColor = COLOR_FILLS[colorIndex];
            if (fillColor == 0) fillColor = accent;
            fill.setColor(fillColor);
            // 暗い塗りには白、明るい塗りには濃い灰色の縁取り
            boolean darkFill = luminance(fillColor) < 0.4f;
            stroke.setColor(darkFill ? 0xFFFFFFFF : 0xFF1F1F1F);
            float d = density * scale;
            stroke.setStrokeWidth(1.4f * d);
            CornerPathEffect round = new CornerPathEffect(2.2f * d);
            fill.setPathEffect(round);
            stroke.setPathEffect(round);
            fill.setShadowLayer(3f * d, 0, 1.2f * d, 0x55000000);
            chevron.setStrokeWidth(2f * d);
            chevron.setColor(onAccentContainer);
            pill.setColor(accentContainer);
            pill.setShadowLayer(3f * d, 0, 1.2f * d, 0x44000000);

            // 先端が (0, 0) の矢印。Pixel のポインタに近い、やや細身でしっぽの短い形
            arrow.reset();
            arrow.moveTo(0, 0);
            arrow.lineTo(0, 18.5f * d);
            arrow.lineTo(4.6f * d, 14.2f * d);
            arrow.lineTo(7.6f * d, 21f * d);
            arrow.lineTo(10.8f * d, 19.6f * d);
            arrow.lineTo(7.8f * d, 12.9f * d);
            arrow.lineTo(13.4f * d, 12.9f * d);
            arrow.close();

            // スクロールモードの上下の山形
            chevrons.reset();
            float w = 4.5f * d, h = 3f * d, gap = 4.5f * d;
            chevrons.moveTo(-w, -gap + h / 2f);
            chevrons.lineTo(0, -gap - h / 2f);
            chevrons.lineTo(w, -gap + h / 2f);
            chevrons.moveTo(-w, gap - h / 2f);
            chevrons.lineTo(0, gap + h / 2f);
            chevrons.lineTo(w, gap - h / 2f);
            invalidate();
        }

        private static float luminance(int c) {
            return (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
        }

        void set(boolean shown, float x, float y, boolean scrollMode) {
            if (this.shown == shown && cx == x && cy == y && this.scrollMode == scrollMode) return;
            this.shown = shown;
            this.cx = x;
            this.cy = y;
            this.scrollMode = scrollMode;
            invalidate();
        }

        @Override
        public void setPressed(boolean pressed) {
            if (this.pressed == pressed) return;
            this.pressed = pressed;
            if (rippleAnim != null) rippleAnim.cancel();
            rippleAnim = ValueAnimator.ofFloat(ripple, pressed ? 1f : 0f);
            rippleAnim.setDuration(pressed ? 160 : 260);
            rippleAnim.setInterpolator(new DecelerateInterpolator());
            rippleAnim.addUpdateListener(a -> {
                ripple = (float) a.getAnimatedValue();
                invalidate();
            });
            rippleAnim.start();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (!shown) return;
            float d = density * scale;
            if (cx < -40 * d || cy < -40 * d || cx > getWidth() + 40 * d || cy > getHeight() + 40 * d) {
                return;
            }
            canvas.save();
            canvas.translate(cx, cy);
            if (ripple > 0.01f) {
                // 押している間: 先端を中心にテーマの色の輪が広がる
                ring.setColor(accent);
                ring.setAlpha(Math.round(70 * ripple));
                canvas.drawCircle(0, 0, (6f + 10f * ripple) * d, ring);
            }
            if (scrollMode) {
                float r = 11f * d;
                canvas.drawRoundRect(-r * 0.78f, -r * 1.25f, r * 0.78f, r * 1.25f, r, r, pill);
                canvas.drawPath(chevrons, chevron);
            } else {
                canvas.drawPath(arrow, fill);
                canvas.drawPath(arrow, stroke);
            }
            canvas.restore();
        }
    }
}
