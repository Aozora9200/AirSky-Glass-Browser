package com.airsky.browser;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ページへキーボードのキーを送る(画面のキーボード・仮想コントローラー・コントローラーのボタンの置き換え)。
 *
 * <p>入力欄が無いゲームでもキーが届くように、送る前にページへフォーカスを渡す。キーには物理的な位置
 * (スキャンコード)も付けるので、KeyboardEvent.code でキーを読むゲームでも使える。</p>
 */
final class PageKeys {

    private PageKeys() {
    }

    /**
     * キーを送る相手にフォーカスを渡して返す。ページがフォーカスを持っていないとページにキーが届かない
     * (入力欄に触れていないゲームなど)。
     */
    @Nullable
    static View focus(@Nullable View target) {
        if (target instanceof GeckoTabView && !target.hasFocus()) {
            target.requestFocus();
            ((GeckoTabView) target).getGeckoSession().setFocused(true);
        }
        return target;
    }

    static void send(@NonNull View target, int action, int keyCode, int meta, long downTime, int flags) {
        long now = SystemClock.uptimeMillis();
        KeyEvent e = new KeyEvent(downTime, now, action, keyCode, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD,
                scanCodeFor(keyCode), flags, InputDevice.SOURCE_KEYBOARD);
        target.dispatchKeyEvent(e);
    }

    /** キーを押す/離す(押したままにできる。修飾キーは押している間 meta に入れる)。 */
    static void sendGameKey(@Nullable View target, int keyCode, boolean down, long downTime) {
        View t = focus(target);
        if (t == null || keyCode == 0) return;
        send(t, down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, keyCode, metaFor(keyCode, down), downTime,
                KeyEvent.FLAG_KEEP_TOUCH_MODE);
    }

    private static int metaFor(int keyCode, boolean down) {
        if (!down) return 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT: return KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_RIGHT_ON;
            case KeyEvent.KEYCODE_CTRL_LEFT: return KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            case KeyEvent.KEYCODE_CTRL_RIGHT: return KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_RIGHT_ON;
            case KeyEvent.KEYCODE_ALT_LEFT: return KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
            case KeyEvent.KEYCODE_ALT_RIGHT: return KeyEvent.META_ALT_ON | KeyEvent.META_ALT_RIGHT_ON;
            default: return 0;
        }
    }

    /**
     * キーの物理的な位置(Linux のスキャンコード)。Gecko はこれから KeyboardEvent.code ("KeyA" など)を
     * 決めるので、code でキーを読むゲーム(Unity 製など)のために付ける。
     */
    static int scanCodeFor(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_9) return 2 + (keyCode - KeyEvent.KEYCODE_1);
        if (keyCode >= KeyEvent.KEYCODE_F1 && keyCode <= KeyEvent.KEYCODE_F10) return 59 + (keyCode - KeyEvent.KEYCODE_F1);
        switch (keyCode) {
            case KeyEvent.KEYCODE_0: return 11;
            case KeyEvent.KEYCODE_Q: return 16;
            case KeyEvent.KEYCODE_W: return 17;
            case KeyEvent.KEYCODE_E: return 18;
            case KeyEvent.KEYCODE_R: return 19;
            case KeyEvent.KEYCODE_T: return 20;
            case KeyEvent.KEYCODE_Y: return 21;
            case KeyEvent.KEYCODE_U: return 22;
            case KeyEvent.KEYCODE_I: return 23;
            case KeyEvent.KEYCODE_O: return 24;
            case KeyEvent.KEYCODE_P: return 25;
            case KeyEvent.KEYCODE_A: return 30;
            case KeyEvent.KEYCODE_S: return 31;
            case KeyEvent.KEYCODE_D: return 32;
            case KeyEvent.KEYCODE_F: return 33;
            case KeyEvent.KEYCODE_G: return 34;
            case KeyEvent.KEYCODE_H: return 35;
            case KeyEvent.KEYCODE_J: return 36;
            case KeyEvent.KEYCODE_K: return 37;
            case KeyEvent.KEYCODE_L: return 38;
            case KeyEvent.KEYCODE_Z: return 44;
            case KeyEvent.KEYCODE_X: return 45;
            case KeyEvent.KEYCODE_C: return 46;
            case KeyEvent.KEYCODE_V: return 47;
            case KeyEvent.KEYCODE_B: return 48;
            case KeyEvent.KEYCODE_N: return 49;
            case KeyEvent.KEYCODE_M: return 50;
            case KeyEvent.KEYCODE_ESCAPE: return 1;
            case KeyEvent.KEYCODE_MINUS: return 12;
            case KeyEvent.KEYCODE_EQUALS: return 13;
            case KeyEvent.KEYCODE_DEL: return 14;
            case KeyEvent.KEYCODE_TAB: return 15;
            case KeyEvent.KEYCODE_LEFT_BRACKET: return 26;
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return 27;
            case KeyEvent.KEYCODE_ENTER: return 28;
            case KeyEvent.KEYCODE_CTRL_LEFT: return 29;
            case KeyEvent.KEYCODE_SEMICOLON: return 39;
            case KeyEvent.KEYCODE_APOSTROPHE: return 40;
            case KeyEvent.KEYCODE_GRAVE: return 41;
            case KeyEvent.KEYCODE_SHIFT_LEFT: return 42;
            case KeyEvent.KEYCODE_BACKSLASH: return 43;
            case KeyEvent.KEYCODE_COMMA: return 51;
            case KeyEvent.KEYCODE_PERIOD: return 52;
            case KeyEvent.KEYCODE_SLASH: return 53;
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return 54;
            case KeyEvent.KEYCODE_ALT_LEFT: return 56;
            case KeyEvent.KEYCODE_SPACE: return 57;
            case KeyEvent.KEYCODE_F11: return 87;
            case KeyEvent.KEYCODE_F12: return 88;
            case KeyEvent.KEYCODE_CTRL_RIGHT: return 97;
            case KeyEvent.KEYCODE_ALT_RIGHT: return 100;
            case KeyEvent.KEYCODE_MOVE_HOME: return 102;
            case KeyEvent.KEYCODE_DPAD_UP: return 103;
            case KeyEvent.KEYCODE_PAGE_UP: return 104;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 105;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 106;
            case KeyEvent.KEYCODE_MOVE_END: return 107;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 108;
            case KeyEvent.KEYCODE_PAGE_DOWN: return 109;
            case KeyEvent.KEYCODE_INSERT: return 110;
            case KeyEvent.KEYCODE_FORWARD_DEL: return 111;
            default: return 0;
        }
    }

    // ------------------------------------------------------------------
    // ボタンに割り当てられるキーの一覧(設定画面用)
    // ------------------------------------------------------------------

    /** 割り当てに選べるキー。 */
    static final int[] CHOOSABLE;

    static {
        int[] special = {
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_TAB,
                KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_DEL,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        };
        int[] tail = {
                KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_LEFT_BRACKET,
                KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE,
                KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_GRAVE,
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END,
        };
        int n = special.length + 26 + 10 + 12 + tail.length;
        int[] all = new int[n];
        int i = 0;
        for (int k : special) all[i++] = k;
        for (int k = KeyEvent.KEYCODE_A; k <= KeyEvent.KEYCODE_Z; k++) all[i++] = k;
        for (int k = KeyEvent.KEYCODE_0; k <= KeyEvent.KEYCODE_9; k++) all[i++] = k;
        for (int k = KeyEvent.KEYCODE_F1; k <= KeyEvent.KEYCODE_F12; k++) all[i++] = k;
        for (int k : tail) all[i++] = k;
        CHOOSABLE = all;
    }

    /** キーの表示名(ボタンの上にも出すので短く)。 */
    @NonNull
    static String name(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            return String.valueOf((char) ('A' + keyCode - KeyEvent.KEYCODE_A));
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return String.valueOf((char) ('0' + keyCode - KeyEvent.KEYCODE_0));
        }
        if (keyCode >= KeyEvent.KEYCODE_F1 && keyCode <= KeyEvent.KEYCODE_F12) {
            return "F" + (1 + keyCode - KeyEvent.KEYCODE_F1);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_SPACE: return "Space";
            case KeyEvent.KEYCODE_ENTER: return "Enter";
            case KeyEvent.KEYCODE_ESCAPE: return "Esc";
            case KeyEvent.KEYCODE_TAB: return "Tab";
            case KeyEvent.KEYCODE_SHIFT_LEFT: return "Shift";
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return "Shift R";
            case KeyEvent.KEYCODE_CTRL_LEFT: return "Ctrl";
            case KeyEvent.KEYCODE_CTRL_RIGHT: return "Ctrl R";
            case KeyEvent.KEYCODE_ALT_LEFT: return "Alt";
            case KeyEvent.KEYCODE_ALT_RIGHT: return "Alt R";
            case KeyEvent.KEYCODE_DEL: return "Backspace";
            case KeyEvent.KEYCODE_FORWARD_DEL: return "Delete";
            case KeyEvent.KEYCODE_DPAD_UP: return "↑";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "↓";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "←";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "→";
            case KeyEvent.KEYCODE_MINUS: return "-";
            case KeyEvent.KEYCODE_EQUALS: return "=";
            case KeyEvent.KEYCODE_LEFT_BRACKET: return "[";
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return "]";
            case KeyEvent.KEYCODE_SEMICOLON: return ";";
            case KeyEvent.KEYCODE_APOSTROPHE: return "'";
            case KeyEvent.KEYCODE_COMMA: return ",";
            case KeyEvent.KEYCODE_PERIOD: return ".";
            case KeyEvent.KEYCODE_SLASH: return "/";
            case KeyEvent.KEYCODE_GRAVE: return "`";
            case KeyEvent.KEYCODE_BACKSLASH: return "\\";
            case KeyEvent.KEYCODE_PAGE_UP: return "PgUp";
            case KeyEvent.KEYCODE_PAGE_DOWN: return "PgDn";
            case KeyEvent.KEYCODE_MOVE_HOME: return "Home";
            case KeyEvent.KEYCODE_MOVE_END: return "End";
            case KeyEvent.KEYCODE_INSERT: return "Insert";
            default: {
                String s = KeyEvent.keyCodeToString(keyCode);
                return s.startsWith("KEYCODE_") ? s.substring("KEYCODE_".length()) : s;
            }
        }
    }
}
