package com.airsky.browser;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.gecko.AndroidGamepadManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * ページの Gamepad API(navigator.getGamepads())へ、コントローラーの入力を届ける。
 *
 * <ul>
 *   <li>仮想コントローラー: 画面のボタンを「AirSky 仮想コントローラー」という 1 台のゲームパッドとして
 *       ページに見せる(GeckoView の AndroidGamepadManager が実機に使っているのと同じ窓口を使う)。</li>
 *   <li>接続したコントローラー: ボタンをそのまま渡す/別のボタンに置き換えて渡す。</li>
 * </ul>
 *
 * <p>ボタンの番号は W3C の標準配置(0=A 1=B 2=X 3=Y 4=L1 5=R1 6=L2 7=R2 8=SELECT 9=START
 * 10=L3 11=R3 12〜15=十字キー 上下左右)。軸は 0,1=左スティック 2,3=右スティック。</p>
 *
 * <p>Gecko はページが Gamepad API を使い始めた時にだけ受け付けを始めるので、仮想コントローラーは
 * 受け付けが始まったのを見てから登録し直す。</p>
 */
final class VirtualGamepad {

    private static final String TAG = "VirtualGamepad";

    static final int A = 0, B = 1, X = 2, Y = 3, L1 = 4, R1 = 5, L2 = 6, R2 = 7,
            SELECT = 8, START = 9, L3 = 10, R3 = 11, UP = 12, DOWN = 13, LEFT = 14, RIGHT = 15;
    static final int BUTTON_COUNT = 16;

    static final String[] BUTTON_NAMES = {
            "A", "B", "X", "Y", "L1", "R1", "L2", "R2", "SELECT", "START", "L3", "R3",
            "↑", "↓", "←", "→",
    };

    /** 標準配置の番号に対応する Android のキーコード(実機のボタンを置き換える時に使う)。 */
    static final int[] KEYCODES = {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    };

    /** キーコードの標準配置の番号。コントローラーのボタンでなければ -1。 */
    static int indexOfKeyCode(int keyCode) {
        for (int i = 0; i < KEYCODES.length; i++) if (KEYCODES[i] == keyCode) return i;
        return -1;
    }

    private static Method addMethod;
    private static Method removeMethod;
    private static Method buttonMethod;
    private static Method axisMethod;
    private static Field startedField;
    private static boolean reflectionTried;
    private static boolean reflectionOk;

    private static synchronized boolean ensureReflection() {
        if (reflectionTried) return reflectionOk;
        reflectionTried = true;
        try {
            Class<?> c = AndroidGamepadManager.class;
            addMethod = c.getDeclaredMethod("nativeAddGamepad", String.class);
            removeMethod = c.getDeclaredMethod("nativeRemoveGamepad", byte[].class);
            buttonMethod = c.getDeclaredMethod("onButtonChange", byte[].class, int.class, boolean.class, float.class);
            axisMethod = c.getDeclaredMethod("onAxisChange", byte[].class, boolean[].class, float[].class);
            startedField = c.getDeclaredField("sStarted");
            addMethod.setAccessible(true);
            removeMethod.setAccessible(true);
            buttonMethod.setAccessible(true);
            axisMethod.setAccessible(true);
            startedField.setAccessible(true);
            reflectionOk = true;
        } catch (Throwable t) {
            Log.w(TAG, "gamepad bridge unavailable", t);
            reflectionOk = false;
        }
        return reflectionOk;
    }

    /** Gecko がゲームパッドの受け付けをしているか(ページが Gamepad API を使っているか)。 */
    private static boolean geckoListening() {
        try {
            return ensureReflection() && startedField.getBoolean(null);
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 仮想コントローラー(1 台)
    // ------------------------------------------------------------------

    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private byte[] handle;
    private boolean enabled;
    private final boolean[] buttons = new boolean[BUTTON_COUNT];
    private final float[] axes = new float[4];

    private boolean watching;

    private final Runnable watch = new Runnable() {
        @Override
        public void run() {
            boolean listening = geckoListening();
            if (listening && handle == null && enabled) {
                register();
            } else if (!listening && handle != null) {
                handle = null; // Gecko 側で片付けられた(次に使い始めた時に登録し直す)
            }
            if (enabled || handle != null) {
                handler.postDelayed(this, 1000);
            } else {
                watching = false;
            }
        }
    };

    private void startWatching() {
        if (watching) return;
        watching = true;
        handler.post(watch);
    }

    /** 入力を送る前に、Gecko が受け付けていれば登録しておく。 */
    private void ensureRegistered() {
        if (handle == null && geckoListening()) register();
        if (handle != null) startWatching();
    }

    /** 仮想コントローラーを出している間 true(ページが Gamepad API を使い始めたらすぐ見えるようにする)。 */
    void setEnabled(boolean on) {
        if (enabled == on) return;
        enabled = on;
        if (on) {
            startWatching();
        } else {
            for (int i = 0; i < BUTTON_COUNT; i++) buttons[i] = false;
            for (int i = 0; i < 4; i++) axes[i] = 0f;
            unregister();
        }
    }

    private void register() {
        if (!ensureReflection()) return;
        try {
            handle = (byte[]) addMethod.invoke(null, "AirSky Virtual Controller (STANDARD GAMEPAD)");
            // 押したままのボタンと傾けたままのスティックを伝え直す
            for (int i = 0; i < BUTTON_COUNT; i++) if (buttons[i]) sendButton(i, true);
            sendAxes();
        } catch (Throwable t) {
            Log.w(TAG, "add gamepad failed", t);
            handle = null;
        }
    }

    private void unregister() {
        byte[] h = handle;
        handle = null;
        if (h == null || !geckoListening()) return;
        try {
            removeMethod.invoke(null, (Object) h);
        } catch (Throwable t) {
            Log.w(TAG, "remove gamepad failed", t);
        }
    }

    /** ボタンを押す・離す(標準配置の番号)。 */
    void setButton(int index, boolean pressed) {
        if (index < 0 || index >= BUTTON_COUNT) return;
        if (buttons[index] == pressed) return;
        buttons[index] = pressed;
        ensureRegistered();
        sendButton(index, pressed);
    }

    /** スティック(0=左 1=右)の傾き(-1〜1、下・右が正)。 */
    void setStick(int stick, float x, float y) {
        int i = stick == 0 ? 0 : 2;
        if (axes[i] == x && axes[i + 1] == y) return;
        axes[i] = x;
        axes[i + 1] = y;
        ensureRegistered();
        sendAxes();
    }

    private void sendButton(int index, boolean pressed) {
        byte[] h = handle;
        if (h == null) return;
        try {
            buttonMethod.invoke(null, h, index, pressed, pressed ? 1f : 0f);
        } catch (Throwable t) {
            Log.w(TAG, "button failed", t);
        }
    }

    private void sendAxes() {
        byte[] h = handle;
        if (h == null) return;
        try {
            axisMethod.invoke(null, h, new boolean[]{true, true, true, true}, axes.clone());
        } catch (Throwable t) {
            Log.w(TAG, "axis failed", t);
        }
    }

    // ------------------------------------------------------------------
    // 接続したコントローラー
    // ------------------------------------------------------------------

    /** 実機のボタンを、そのまま Gamepad API へ渡す。 */
    static void passKey(@NonNull KeyEvent e) {
        try {
            AndroidGamepadManager.handleKeyEvent(e);
        } catch (Throwable ignored) {
        }
    }

    /** 実機のスティック・トリガー・ハットスイッチを、そのまま Gamepad API へ渡す。 */
    static void passMotion(@NonNull MotionEvent e) {
        try {
            AndroidGamepadManager.handleMotionEvent(e);
        } catch (Throwable ignored) {
        }
    }

    /** 実機のボタンを、同じコントローラーの別のボタン(標準配置の番号)として渡す。 */
    static void passKeyAs(@NonNull KeyEvent e, int index) {
        if (index < 0 || index >= KEYCODES.length) return;
        long now = SystemClock.uptimeMillis();
        KeyEvent remapped = new KeyEvent(e.getDownTime(), now, e.getAction(), KEYCODES[index], 0, 0,
                e.getDeviceId(), 0, 0, e.getSource() != 0 ? e.getSource() : InputDevice.SOURCE_GAMEPAD);
        passKey(remapped);
    }
}
