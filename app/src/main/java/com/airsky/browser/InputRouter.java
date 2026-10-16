package com.airsky.browser;

import android.os.SystemClock;
import android.util.SparseIntArray;
import android.util.SparseLongArray;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * ボタン・スティックに割り当てた入力(キーボードのキー・ゲームパッドのボタン・マウス)をページへ送る。
 * 仮想コントローラーと、接続したコントローラーの「ページ入力」で使う。
 *
 * <p>割り当ては短い文字列で表す。</p>
 * <ul>
 *   <li>ボタン: {@code none}(何もしない)/ {@code pass}(コントローラーのボタンをそのまま)/
 *       {@code key:<キーコード>} / {@code pad:<標準配置の番号>} / {@code mouse:<1=左 2=右 4=中>}</li>
 *   <li>スティック・十字キー: {@code stick:0}(左スティック)/ {@code stick:1}(右スティック)/
 *       {@code keys:wasd} / {@code keys:arrows} / {@code pad:dpad}(ゲームパッドの十字キー)/
 *       {@code mousemove}(マウスの移動・視点)/ {@code scroll}(ページのスクロール)/ {@code pass} / {@code none}</li>
 * </ul>
 */
final class InputRouter {

    static final String NONE = "none";
    static final String PASS = "pass";
    static final String STICK_LEFT = "stick:0";
    static final String STICK_RIGHT = "stick:1";
    static final String WASD = "keys:wasd";
    static final String ARROWS = "keys:arrows";
    static final String PAD_DPAD = "pad:dpad";
    static final String MOUSE_MOVE = "mousemove";
    static final String SCROLL = "scroll";

    static String key(int keyCode) {
        return "key:" + keyCode;
    }

    static String pad(int index) {
        return "pad:" + index;
    }

    static String mouse(int button) {
        return "mouse:" + button;
    }

    /** 割り当ての種類("key" "pad" "mouse" など)。 */
    @NonNull
    static String kind(@Nullable String binding) {
        if (binding == null) return NONE;
        int i = binding.indexOf(':');
        return i < 0 ? binding : binding.substring(0, i);
    }

    /** "key:62" などの数字の部分。無ければ -1。 */
    static int value(@Nullable String binding) {
        if (binding == null) return -1;
        int i = binding.indexOf(':');
        if (i < 0) return -1;
        try {
            return Integer.parseInt(binding.substring(i + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static boolean isDirection(@Nullable String binding) {
        if (binding == null) return false;
        switch (binding) {
            case STICK_LEFT:
            case STICK_RIGHT:
            case WASD:
            case ARROWS:
            case PAD_DPAD:
            case MOUSE_MOVE:
            case SCROLL:
                return true;
            default:
                return false;
        }
    }

    /** 設定画面に出す説明。 */
    @NonNull
    static String describe(@Nullable String binding) {
        if (binding == null || NONE.equals(binding)) return Lang.t("割り当てなし");
        switch (binding) {
            case PASS: return Lang.t("そのまま(コントローラーの入力)");
            case STICK_LEFT: return Lang.t("ゲームパッドの左スティック");
            case STICK_RIGHT: return Lang.t("ゲームパッドの右スティック");
            case WASD: return Lang.t("キー W A S D");
            case ARROWS: return Lang.t("矢印キー");
            case PAD_DPAD: return Lang.t("ゲームパッドの十字キー");
            case MOUSE_MOVE: return Lang.t("マウスの移動(視点)");
            case SCROLL: return Lang.t("ページのスクロール");
            default: break;
        }
        int v = value(binding);
        switch (kind(binding)) {
            case "key": return Lang.t("キー ") + PageKeys.name(v);
            case "pad": return Lang.t("ゲームパッド ") + padName(v);
            case "mouse": return mouseName(v);
            default: return binding;
        }
    }

    /** 仮想コントローラーのボタンに出す短い文字。 */
    @NonNull
    static String shortLabel(@Nullable String binding) {
        if (binding == null) return "";
        int v = value(binding);
        switch (kind(binding)) {
            case "key": return PageKeys.name(v);
            case "pad": return padName(v);
            case "mouse": return v == 2 ? "R" : v == 4 ? "M" : "L";
            default: return "";
        }
    }

    @NonNull
    static String padName(int index) {
        return index >= 0 && index < VirtualGamepad.BUTTON_NAMES.length ? VirtualGamepad.BUTTON_NAMES[index] : "?";
    }

    @NonNull
    static String mouseName(int button) {
        switch (button) {
            case 2: return Lang.t("右クリック");
            case 4: return Lang.t("中クリック");
            default: return Lang.t("左クリック");
        }
    }

    private static int mouseButton(int v) {
        switch (v) {
            case 2: return MotionEvent.BUTTON_SECONDARY;
            case 4: return MotionEvent.BUTTON_TERTIARY;
            default: return MotionEvent.BUTTON_PRIMARY;
        }
    }

    // ------------------------------------------------------------------

    interface Host {
        /** キーを送る相手(表示中のタブなど)。 */
        @Nullable
        View keyTarget();
    }

    private final Host host;
    private final VirtualMouse mouse;
    private final VirtualGamepad pad;

    /** 押しているキー(同じキーを 2 つのボタンで押しても、両方離すまで押したままにする)。 */
    private final SparseIntArray keyCount = new SparseIntArray();
    private final SparseLongArray keyDownTime = new SparseLongArray();
    private final SparseIntArray padCount = new SparseIntArray();
    private final SparseIntArray mouseCount = new SparseIntArray();
    /** ボタンごとの押した時の割り当て(押している間に割り当てが変わっても、同じものを離す)。 */
    private final Map<Object, String> pressedButtons = new HashMap<>();
    /** スティックごとの今の割り当てと、押しているキー・向き。 */
    private final Map<Object, DirState> directions = new HashMap<>();

    private static final class DirState {
        String binding;
        float x, y;
        /** 押している向き(上下左右のビット)。 */
        int held;
    }

    InputRouter(@NonNull Host host, @NonNull VirtualMouse mouse, @NonNull VirtualGamepad pad) {
        this.host = host;
        this.mouse = mouse;
        this.pad = pad;
    }

    // ---- ボタン ----

    void button(@NonNull Object owner, @Nullable String binding, boolean down) {
        if (down) {
            if (pressedButtons.containsKey(owner)) return;
            if (binding == null || NONE.equals(binding) || PASS.equals(binding)) return;
            pressedButtons.put(owner, binding);
            apply(binding, true);
        } else {
            String b = pressedButtons.remove(owner);
            if (b != null) apply(b, false);
        }
    }

    private void apply(@NonNull String binding, boolean down) {
        int v = value(binding);
        switch (kind(binding)) {
            case "key":
                key(v, down);
                break;
            case "pad":
                padButton(v, down);
                break;
            case "mouse":
                mouseButtonRef(v, down);
                break;
            default:
                break;
        }
    }

    private void key(int code, boolean down) {
        if (code <= 0) return;
        int n = keyCount.get(code) + (down ? 1 : -1);
        if (n < 0) n = 0;
        int before = keyCount.get(code);
        keyCount.put(code, n);
        if (down && before == 0) {
            long t = SystemClock.uptimeMillis();
            keyDownTime.put(code, t);
            PageKeys.sendGameKey(host.keyTarget(), code, true, t);
        } else if (!down && before > 0 && n == 0) {
            PageKeys.sendGameKey(host.keyTarget(), code, false, keyDownTime.get(code, SystemClock.uptimeMillis()));
        }
    }

    private void padButton(int index, boolean down) {
        if (index < 0 || index >= VirtualGamepad.BUTTON_COUNT) return;
        int before = padCount.get(index);
        int n = Math.max(0, before + (down ? 1 : -1));
        padCount.put(index, n);
        if ((before == 0) != (n == 0)) pad.setButton(index, n > 0);
    }

    private void mouseButtonRef(int v, boolean down) {
        int before = mouseCount.get(v);
        int n = Math.max(0, before + (down ? 1 : -1));
        mouseCount.put(v, n);
        if (before == 0 && n > 0) mouse.press(mouseButton(v));
        if (before > 0 && n == 0) mouse.release(mouseButton(v));
    }

    // ---- スティック・十字キー ----

    private static final int UP = 1, DOWN = 2, LEFT = 4, RIGHT = 8;
    private static final float KEY_THRESHOLD = 0.45f;

    /** スティック・十字キーの傾き(-1〜1、右・下が正)。 */
    void direction(@NonNull Object owner, @Nullable String binding, float x, float y) {
        DirState s = directions.get(owner);
        if (s == null) {
            s = new DirState();
            directions.put(owner, s);
        }
        if (s.binding != null && !s.binding.equals(binding)) {
            String old = s.binding;
            applyDirection(owner, s, old, 0, 0);
        }
        s.binding = binding;
        applyDirection(owner, s, binding, x, y);
    }

    private void applyDirection(@NonNull Object owner, @NonNull DirState s, @Nullable String binding, float x, float y) {
        s.x = x;
        s.y = y;
        if (binding == null) return;
        switch (binding) {
            case STICK_LEFT:
                pad.setStick(0, x, y);
                break;
            case STICK_RIGHT:
                pad.setStick(1, x, y);
                break;
            case WASD:
            case ARROWS:
            case PAD_DPAD:
                setHeld(s, binding, heldBits(x, y));
                break;
            case MOUSE_MOVE:
                updateAux(true);
                break;
            case SCROLL:
                updateAux(false);
                break;
            default:
                break;
        }
    }

    private static int heldBits(float x, float y) {
        int bits = 0;
        if (y < -KEY_THRESHOLD) bits |= UP;
        if (y > KEY_THRESHOLD) bits |= DOWN;
        if (x < -KEY_THRESHOLD) bits |= LEFT;
        if (x > KEY_THRESHOLD) bits |= RIGHT;
        return bits;
    }

    private void setHeld(@NonNull DirState s, @NonNull String binding, int bits) {
        int changed = s.held ^ bits;
        if (changed == 0) return;
        int[] dirs = {UP, DOWN, LEFT, RIGHT};
        for (int i = 0; i < dirs.length; i++) {
            int d = dirs[i];
            if ((changed & d) == 0) continue;
            boolean down = (bits & d) != 0;
            switch (binding) {
                case WASD:
                    key(new int[]{KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_D}[i], down);
                    break;
                case ARROWS:
                    key(new int[]{KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT}[i], down);
                    break;
                case PAD_DPAD:
                    padButton(new int[]{VirtualGamepad.UP, VirtualGamepad.DOWN, VirtualGamepad.LEFT,
                            VirtualGamepad.RIGHT}[i], down);
                    break;
                default:
                    break;
            }
        }
        s.held = bits;
    }

    /** マウス移動・スクロールに割り当てたスティックの合計を仮想マウスへ渡す。 */
    private void updateAux(boolean move) {
        float sx = 0, sy = 0;
        String want = move ? MOUSE_MOVE : SCROLL;
        for (DirState s : directions.values()) {
            if (want.equals(s.binding)) {
                sx += s.x;
                sy += s.y;
            }
        }
        sx = Math.max(-1f, Math.min(1f, sx));
        sy = Math.max(-1f, Math.min(1f, sy));
        if (move) mouse.setAuxMove(sx, sy); else mouse.setAuxScroll(sx, sy);
    }

    /** そのボタン・スティックの入力を全部離す。 */
    void releaseOwner(@NonNull Object owner) {
        button(owner, null, false);
        DirState s = directions.get(owner);
        if (s != null) direction(owner, s.binding, 0, 0);
    }

    /** 押したままの入力を全部離す(画面を離れる・仮想コントローラーを閉じる時)。 */
    void releaseAll() {
        for (Object owner : new java.util.ArrayList<>(pressedButtons.keySet())) button(owner, null, false);
        for (Map.Entry<Object, DirState> e : new java.util.ArrayList<>(directions.entrySet())) {
            direction(e.getKey(), e.getValue().binding, 0, 0);
        }
    }
}
