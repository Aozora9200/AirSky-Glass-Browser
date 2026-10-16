package com.airsky.browser;

import android.content.Context;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

/**
 * 接続したコントローラーのボタン・スティック。2 つの使い方を切り替えて使う。
 *
 * <h3>ブラウザ操作(既定)</h3>
 * ボタンをブラウザの操作にする(割り当ては {@link ControllerPrefs})。
 * <ul>
 *   <li>左スティック: 仮想マウスのカーソル(スクロールモード・仮想マウスを使っていない時はスクロール)</li>
 *   <li>右スティック: スクロール</li>
 *   <li>十字キー: 仮想マウスの間はカーソル。それ以外は普段どおり項目の移動</li>
 * </ul>
 * クリック・右クリックは仮想マウスを使っている間だけ。使っていない時はボタンをそのまま渡すので、
 * A ボタンでの決定や B ボタンでの戻るなど、Android の普段の操作がそのまま使える。
 *
 * <h3>ページ入力</h3>
 * ボタンとスティックをページ(ゲーム)へ送る。ボタンごとに「そのまま」「キーボードのキー」
 * 「別のボタン」「マウス」を選べる。ブラウザの操作は一切しないので、ゲーム中にメニューが開いたり
 * ページを戻ったりしない。
 *
 * <p>SELECT と START を同時に押すと、いつでも 2 つの使い方を切り替えられる。</p>
 */
final class GamepadInput {

    interface Host {
        /** コントローラーでブラウザを操作してよい状態か(タブ一覧を開いている間などは false)。 */
        boolean canHandleController();

        void openMenu();

        void exitFullScreen();

        void goBack();

        void goForward();

        /** ページ入力とブラウザ操作を切り替えた(案内を出す)。 */
        void onPageModeChanged(boolean pageMode);

        /** ページがマウスを固定しているなら解除する。解除したら true。 */
        boolean exitPointerLock();
    }

    private final Context context;
    private final VirtualMouse mouse;
    private final InputRouter router;
    private final Host host;
    /** 押した時に使ったキー(離した時も使って、Android の普段の動作に流さない)。 */
    private final SparseBooleanArray consumed = new SparseBooleanArray();
    /** ブラウザ操作で、離すまで待ってから実行する操作(SELECT・START は同時押しを待つ)。 */
    private final SparseIntArray deferred = new SparseIntArray();
    private int dpadX, dpadY;
    private boolean selectHeld, startHeld;
    /** SELECT + START の同時押しで切り替えた(離すまでほかの操作をしない)。 */
    private boolean comboFired;

    GamepadInput(@NonNull Context context, @NonNull VirtualMouse mouse, @NonNull InputRouter router,
                 @NonNull Host host) {
        this.context = context;
        this.mouse = mouse;
        this.router = router;
        this.host = host;
    }

    /** コントローラーから来た入力か。 */
    static boolean isController(@NonNull KeyEvent e) {
        if (isSystemKey(e.getKeyCode())) return false;
        if (KeyEvent.isGamepadButton(e.getKeyCode())) return true;
        InputDevice d = e.getDevice();
        return d != null && !d.isVirtual()
                && (d.getSources() & (InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_JOYSTICK)) != 0;
    }

    /**
     * 音量・電源などの端末のキー。AYN Thor などのゲーム機型の端末では、コントローラーと同じ入力デバイスから
     * 届くので、コントローラーのボタンとして扱うと音量を変えられなくなる。割り当てずに常に Android へ任せる。
     */
    static boolean isSystemKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_MUTE:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_BRIGHTNESS_UP:
            case KeyEvent.KEYCODE_BRIGHTNESS_DOWN:
                return true;
            default:
                return false;
        }
    }

    boolean isPageMode() {
        return ControllerPrefs.isPageMode(context);
    }

    /** ページ入力とブラウザ操作を切り替える。 */
    void setPageMode(boolean on) {
        if (isPageMode() == on) return;
        releasePageInput();
        mouse.setSticks(0, 0, 0, 0);
        mouse.setDpad(0, 0);
        dpadX = dpadY = 0;
        ControllerPrefs.setPageMode(context, on);
        host.onPageModeChanged(on);
    }

    /** ページ入力で押したままのキーなどを離す。 */
    void releasePageInput() {
        for (int i = 0; i < ControllerPrefs.SOURCE_COUNT; i++) router.releaseOwner(owner(i));
    }

    /** @return 使ったら true(Android の普段の動作には流さない) */
    boolean onKey(@NonNull KeyEvent e) {
        int code = e.getKeyCode();
        // 音量キーなどは仮想マウス・ページ入力の間も含めて使わない(前に割り当てていても無視する)
        if (isSystemKey(code)) return false;
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean controller = isController(e);

        // SELECT + START の同時押し(どちらの使い方でも)
        if (controller && (code == KeyEvent.KEYCODE_BUTTON_SELECT || code == KeyEvent.KEYCODE_BUTTON_START)) {
            if (code == KeyEvent.KEYCODE_BUTTON_SELECT) selectHeld = down;
            else startHeld = down;
            if (down && e.getRepeatCount() == 0 && selectHeld && startHeld && host.canHandleController()) {
                comboFired = true;
                deferred.clear();
                setPageMode(!isPageMode());
            }
            if (comboFired) {
                if (!selectHeld && !startHeld) comboFired = false;
                consumed.delete(code);
                return true;
            }
        }

        if (isPageMode() && controller && host.canHandleController()) {
            return onPageKey(e);
        }

        if (e.getAction() == KeyEvent.ACTION_UP && consumed.get(code)) {
            consumed.delete(code);
            int pending = deferred.get(code, -1);
            if (pending >= 0) {
                deferred.delete(code);
                run(pending);
                return true;
            }
            finishKey(code);
            return true;
        }
        if (!down || !ControllerPrefs.isEnabled(context) || !controller || !host.canHandleController()) {
            return false;
        }
        boolean first = e.getRepeatCount() == 0;
        int action = ControllerPrefs.actionFor(context, code);
        if (action < 0) {
            // 割り当てていない十字キーは、仮想マウスの間だけカーソルを動かす
            if (mouse.isActive() && isDpad(code)) {
                if (first) setDpad(code, true);
                consumed.put(code, true);
                return true;
            }
            return false;
        }
        switch (action) {
            case ControllerPrefs.CLICK:
            case ControllerPrefs.RIGHT_CLICK:
                // 仮想マウスでない時は普段の決定ボタンなどとして使う(マウスを固定したページでは固定した位置でクリック)
                if (!mouse.isActive() && !mouse.isPointerLocked()) return false;
                if (first) mouse.press(action == ControllerPrefs.CLICK
                        ? MotionEvent.BUTTON_PRIMARY : MotionEvent.BUTTON_SECONDARY);
                break;
            case ControllerPrefs.SCROLL_UP:
                mouse.scrollStep(-1); // 押し続けると続けてスクロール
                break;
            case ControllerPrefs.SCROLL_DOWN:
                mouse.scrollStep(1);
                break;
            default:
                if (!first) break;
                if (code == KeyEvent.KEYCODE_BUTTON_SELECT || code == KeyEvent.KEYCODE_BUTTON_START) {
                    // 同時押しで切り替えることがあるので、離した時に実行する
                    deferred.put(code, action);
                } else {
                    run(action);
                }
                break;
        }
        consumed.put(code, true);
        return true;
    }

    private void run(int action) {
        switch (action) {
            case ControllerPrefs.MENU:
                host.openMenu();
                break;
            case ControllerPrefs.MOUSE:
                // マウスを固定しているページでは、まず固定を解除する
                if (!host.exitPointerLock()) mouse.toggle();
                break;
            case ControllerPrefs.SCROLL_MODE:
                mouse.toggleScrollMode();
                break;
            case ControllerPrefs.ZOOM_IN:
                mouse.zoom(true);
                break;
            case ControllerPrefs.ZOOM_OUT:
                mouse.zoom(false);
                break;
            case ControllerPrefs.EXIT_FULLSCREEN:
                host.exitFullScreen();
                break;
            case ControllerPrefs.BACK:
                if (!host.exitPointerLock()) host.goBack();
                break;
            case ControllerPrefs.FORWARD:
                host.goForward();
                break;
            default:
                break;
        }
    }

    /** 押したままにする操作(クリック・十字キー)を、ボタンを離した時に終わらせる。 */
    private void finishKey(int code) {
        if (isDpad(code)) {
            setDpad(code, false);
            return;
        }
        int action = ControllerPrefs.actionFor(context, code);
        if (action == ControllerPrefs.CLICK) mouse.release(MotionEvent.BUTTON_PRIMARY);
        if (action == ControllerPrefs.RIGHT_CLICK) mouse.release(MotionEvent.BUTTON_SECONDARY);
    }

    private static boolean isDpad(int code) {
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private void setDpad(int code, boolean down) {
        int v = down ? 1 : 0;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: dpadY = down ? -1 : (dpadY < 0 ? 0 : dpadY); break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dpadY = down ? v : (dpadY > 0 ? 0 : dpadY); break;
            case KeyEvent.KEYCODE_DPAD_LEFT: dpadX = down ? -1 : (dpadX < 0 ? 0 : dpadX); break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dpadX = down ? v : (dpadX > 0 ? 0 : dpadX); break;
            default: break;
        }
        mouse.setDpad(dpadX, dpadY);
    }

    // ------------------------------------------------------------------
    // ページ入力
    // ------------------------------------------------------------------

    private final Object[] owners = new Object[ControllerPrefs.SOURCE_COUNT];

    /** 割り当ての対象ごとの目印(押したキーを離す時に使う)。 */
    private Object owner(int source) {
        if (owners[source] == null) owners[source] = "pad-" + source;
        return owners[source];
    }

    private boolean onPageKey(@NonNull KeyEvent e) {
        int code = e.getKeyCode();
        // 戻る・ホームなど端末の操作はそのまま
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_HOME) return false;
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        consumed.delete(code);
        int index = VirtualGamepad.indexOfKeyCode(code);
        if (index < 0) {
            VirtualGamepad.passKey(e);
            return true;
        }
        String binding = ControllerPrefs.pageBinding(context, index);
        String kind = InputRouter.kind(binding);
        if (InputRouter.PASS.equals(binding)) {
            VirtualGamepad.passKey(e);
        } else if ("pad".equals(kind)) {
            VirtualGamepad.passKeyAs(e, InputRouter.value(binding));
        } else if (e.getRepeatCount() == 0 || !down) {
            router.button(owner(index), binding, down);
        }
        return true;
    }

    /** ページ入力でのスティック・トリガー・ハットスイッチ。 */
    private boolean onPageMotion(@NonNull MotionEvent e) {
        mouse.setSticks(0, 0, 0, 0);
        String left = ControllerPrefs.pageBinding(context, ControllerPrefs.SOURCE_LEFT_STICK);
        String right = ControllerPrefs.pageBinding(context, ControllerPrefs.SOURCE_RIGHT_STICK);
        String l2 = ControllerPrefs.pageBinding(context, VirtualGamepad.L2);
        String r2 = ControllerPrefs.pageBinding(context, VirtualGamepad.R2);
        boolean dpadPass = true;
        for (int i = VirtualGamepad.UP; i <= VirtualGamepad.RIGHT; i++) {
            if (!InputRouter.PASS.equals(ControllerPrefs.pageBinding(context, i))) dpadPass = false;
        }
        boolean leftPass = InputRouter.PASS.equals(left);
        boolean rightPass = InputRouter.PASS.equals(right);
        boolean l2Pass = InputRouter.PASS.equals(l2) || "pad".equals(InputRouter.kind(l2));
        boolean r2Pass = InputRouter.PASS.equals(r2) || "pad".equals(InputRouter.kind(r2));

        if (!leftPass) {
            router.direction(owner(ControllerPrefs.SOURCE_LEFT_STICK), left,
                    stick(e, MotionEvent.AXIS_X), stick(e, MotionEvent.AXIS_Y));
        }
        if (!rightPass) {
            router.direction(owner(ControllerPrefs.SOURCE_RIGHT_STICK), right,
                    stick(e, MotionEvent.AXIS_Z), stick(e, MotionEvent.AXIS_RZ));
        }
        if (!l2Pass) {
            float v = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE));
            router.button(owner(VirtualGamepad.L2), v > 0.5f ? l2 : null, v > 0.5f);
        }
        if (!r2Pass) {
            float v = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS));
            router.button(owner(VirtualGamepad.R2), v > 0.5f ? r2 : null, v > 0.5f);
        }
        float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (!dpadPass) {
            hatButton(VirtualGamepad.UP, hy < -0.5f);
            hatButton(VirtualGamepad.DOWN, hy > 0.5f);
            hatButton(VirtualGamepad.LEFT, hx < -0.5f);
            hatButton(VirtualGamepad.RIGHT, hx > 0.5f);
        }

        if (leftPass && rightPass && l2Pass && r2Pass && dpadPass) {
            VirtualGamepad.passMotion(e);
        } else {
            // 置き換えた軸は 0 にしてから、残りをそのままページへ渡す
            MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
            e.getPointerCoords(0, pc);
            if (!leftPass) zero(pc, MotionEvent.AXIS_X, MotionEvent.AXIS_Y);
            if (!rightPass) zero(pc, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ);
            if (!l2Pass) zero(pc, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE);
            if (!r2Pass) zero(pc, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS);
            if (!dpadPass) zero(pc, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y);
            MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
            e.getPointerProperties(0, pp);
            MotionEvent copy = MotionEvent.obtain(e.getDownTime(), e.getEventTime(), e.getAction(), 1,
                    new MotionEvent.PointerProperties[]{pp}, new MotionEvent.PointerCoords[]{pc},
                    e.getMetaState(), e.getButtonState(), e.getXPrecision(), e.getYPrecision(),
                    e.getDeviceId(), e.getEdgeFlags(), e.getSource(), e.getFlags());
            VirtualGamepad.passMotion(copy);
            copy.recycle();
        }
        return true;
    }

    private final boolean[] hatHeld = new boolean[4];

    private void hatButton(int index, boolean down) {
        int i = index - VirtualGamepad.UP;
        if (hatHeld[i] == down) return;
        hatHeld[i] = down;
        String binding = ControllerPrefs.pageBinding(context, index);
        if (InputRouter.PASS.equals(binding)) {
            if (down) router.button(owner(index), InputRouter.pad(index), true);
            else router.button(owner(index), null, false);
        } else if ("pad".equals(InputRouter.kind(binding))) {
            // 十字キーを別のボタンに置き換える(仮想コントローラーとして送る)
            router.button(owner(index), down ? binding : null, down);
        } else {
            router.button(owner(index), down ? binding : null, down);
        }
    }

    private static void zero(@NonNull MotionEvent.PointerCoords pc, int a, int b) {
        pc.setAxisValue(a, 0f);
        pc.setAxisValue(b, 0f);
    }

    private static float stick(@NonNull MotionEvent e, int axis) {
        float v = e.getAxisValue(axis);
        InputDevice d = e.getDevice();
        float flat = 0.15f;
        if (d != null) {
            InputDevice.MotionRange r = d.getMotionRange(axis, e.getSource());
            if (r != null) flat = Math.max(flat, r.getFlat());
        }
        return Math.abs(v) < flat ? 0f : v;
    }

    // ------------------------------------------------------------------

    /** スティック・ハットスイッチ。@return 使ったら true */
    boolean onMotion(@NonNull MotionEvent e) {
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
                || e.getActionMasked() != MotionEvent.ACTION_MOVE) {
            return false;
        }
        if (isPageMode() && host.canHandleController()) return onPageMotion(e);
        if (!ControllerPrefs.isEnabled(context)) return false;
        if (!host.canHandleController()) {
            mouse.setSticks(0, 0, 0, 0);
            return false;
        }
        float lx = e.getAxisValue(MotionEvent.AXIS_X);
        float ly = e.getAxisValue(MotionEvent.AXIS_Y);
        float rx = e.getAxisValue(MotionEvent.AXIS_Z);
        float ry = e.getAxisValue(MotionEvent.AXIS_RZ);
        mouse.setSticks(lx, ly, rx, ry);

        float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (mouse.isActive()) {
            // 十字キーがハットスイッチとして来るコントローラー
            if (Math.abs(hx) > 0.5f || Math.abs(hy) > 0.5f || dpadFromHat) {
                dpadFromHat = Math.abs(hx) > 0.5f || Math.abs(hy) > 0.5f;
                dpadX = Math.round(hx);
                dpadY = Math.round(hy);
                mouse.setDpad(dpadX, dpadY);
            }
            return true;
        }
        // 仮想マウスでない時、ハットスイッチは Android が十字キーに直して項目の移動に使うので渡す
        boolean sticks = Math.abs(lx) > 0.1f || Math.abs(ly) > 0.1f || Math.abs(rx) > 0.1f || Math.abs(ry) > 0.1f;
        boolean hat = Math.abs(hx) > 0.5f || Math.abs(hy) > 0.5f;
        return !hat || sticks;
    }

    private boolean dpadFromHat;
}
