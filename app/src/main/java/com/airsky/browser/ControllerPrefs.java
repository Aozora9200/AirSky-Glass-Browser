package com.airsky.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

/**
 * コントローラー操作の設定(ボタンの割り当て・カーソルの速さ)。
 *
 * <p>操作ごとに 1 つのキーコードを割り当てる。同じボタンを 2 つの操作には割り当てない
 * (新しく割り当てた操作が優先され、前の操作は「未割り当て」になる)。</p>
 */
final class ControllerPrefs {

    static final int MENU = 0;
    static final int MOUSE = 1;
    static final int CLICK = 2;
    static final int RIGHT_CLICK = 3;
    static final int SCROLL_MODE = 4;
    static final int SCROLL_UP = 5;
    static final int SCROLL_DOWN = 6;
    static final int ZOOM_IN = 7;
    static final int ZOOM_OUT = 8;
    static final int EXIT_FULLSCREEN = 9;
    static final int BACK = 10;
    static final int FORWARD = 11;
    static final int COUNT = 12;

    private static final String[] IDS = {
            "menu", "mouse", "click", "right_click", "scroll_mode", "scroll_up", "scroll_down",
            "zoom_in", "zoom_out", "exit_fullscreen", "back", "forward",
    };

    static final String[] LABELS = {
            Lang.t("メニューを開く"), Lang.t("仮想マウスモードの切り替え"), Lang.t("クリック"), Lang.t("右クリック"), Lang.t("マウスのスクロールモード"),
            Lang.t("上にスクロール"), Lang.t("下にスクロール"), Lang.t("拡大"), Lang.t("縮小"), Lang.t("全画面表示の解除"), Lang.t("戻る"), Lang.t("進む"),
    };

    static final String[] SUMMARIES = {
            Lang.t("ポップアップメニュー(Aozora メニュー)を開きます"),
            Lang.t("コントローラーで動かすマウスカーソルを出す・しまう"),
            Lang.t("カーソルの位置をクリック(押したまま動かすとドラッグ)"),
            Lang.t("カーソルの位置を右クリック(リンクのメニューなど)"),
            Lang.t("左スティックでカーソルの代わりにページをスクロールします"),
            Lang.t("ページを少し上へスクロールします"),
            Lang.t("ページを少し下へスクロールします"),
            Lang.t("ページを拡大します"),
            Lang.t("ページを縮小します"),
            Lang.t("動画などの全画面表示を終わらせます"),
            Lang.t("前のページへ戻ります"),
            Lang.t("次のページへ進みます"),
    };

    /** 既定の割り当て(AYN Thor などの一般的なコントローラーのボタン配置)。0 は未割り当て。 */
    private static final int[] DEFAULTS = {
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            0,
            0,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
    };

    private static final String PREFS = "controller";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_SPEED = "cursor_speed";

    private ControllerPrefs() {
    }

    private static SharedPreferences prefs(@NonNull Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** コントローラーのボタンでブラウザを操作するか(オフならボタンはすべてページへ渡す)。 */
    static boolean isEnabled(@NonNull Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, true);
    }

    static void setEnabled(@NonNull Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_ENABLED, on).apply();
    }

    /** カーソル・スクロールの速さ(1〜10、5 が標準)。 */
    static int cursorSpeed(@NonNull Context c) {
        return Math.max(1, Math.min(10, prefs(c).getInt(KEY_SPEED, 5)));
    }

    static void setCursorSpeed(@NonNull Context c, int speed) {
        prefs(c).edit().putInt(KEY_SPEED, Math.max(1, Math.min(10, speed))).apply();
    }

    /** 操作に割り当てたキーコード(0 は未割り当て)。 */
    static int keyFor(@NonNull Context c, int action) {
        int key = prefs(c).getInt("key_" + IDS[action], DEFAULTS[action]);
        // 以前の版で割り当ててしまった音量キーなどは未割り当てとして扱う
        return GamepadInput.isSystemKey(key) ? 0 : key;
    }

    /** キーコードを割り当てた操作。無ければ -1。 */
    static int actionFor(@NonNull Context c, int keyCode) {
        if (keyCode == 0) return -1;
        for (int i = 0; i < COUNT; i++) {
            if (keyFor(c, i) == keyCode) return i;
        }
        return -1;
    }

    /** 割り当てる(0 で解除)。同じボタンを使っていた他の操作は未割り当てにする。 */
    static void assign(@NonNull Context c, int action, int keyCode) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (keyCode != 0) {
            for (int i = 0; i < COUNT; i++) {
                if (i != action && keyFor(c, i) == keyCode) e.putInt("key_" + IDS[i], 0);
            }
        }
        e.putInt("key_" + IDS[action], keyCode);
        e.apply();
    }

    // ------------------------------------------------------------------
    // ページ入力(コントローラーのボタンをブラウザの操作ではなくページへ送る)
    // ------------------------------------------------------------------

    private static final String KEY_PAGE_MODE = "page_mode";

    /** ページ入力の割り当ての対象: 0〜15 はボタン(標準配置の番号)、この 2 つはスティック。 */
    static final int SOURCE_LEFT_STICK = 16;
    static final int SOURCE_RIGHT_STICK = 17;
    static final int SOURCE_COUNT = 18;

    /**
     * 接続したコントローラーをページ入力に使っているか。ページ入力の間はボタンをブラウザの操作に使わず、
     * 割り当てに従ってページへ送る(SELECT + START の同時押しでいつでもブラウザ操作に戻せる)。
     */
    static boolean isPageMode(@NonNull Context c) {
        return prefs(c).getBoolean(KEY_PAGE_MODE, false);
    }

    static void setPageMode(@NonNull Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_PAGE_MODE, on).apply();
    }

    @NonNull
    static String sourceName(int source) {
        if (source == SOURCE_LEFT_STICK) return Lang.t("左スティック");
        if (source == SOURCE_RIGHT_STICK) return Lang.t("右スティック");
        return InputRouter.padName(source);
    }

    /** ページ入力での割り当て({@link InputRouter} の形式)。既定は「そのまま」。 */
    @NonNull
    static String pageBinding(@NonNull Context c, int source) {
        String v = prefs(c).getString("page_map_" + source, null);
        return v != null ? v : InputRouter.PASS;
    }

    static void setPageBinding(@NonNull Context c, int source, @NonNull String binding) {
        prefs(c).edit().putString("page_map_" + source, binding).apply();
    }

    static void resetPageBindings(@NonNull Context c) {
        SharedPreferences.Editor e = prefs(c).edit();
        for (int i = 0; i < SOURCE_COUNT; i++) e.remove("page_map_" + i);
        e.apply();
    }

    static void resetAssignments(@NonNull Context c) {
        SharedPreferences.Editor e = prefs(c).edit();
        for (String id : IDS) e.remove("key_" + id);
        e.remove(KEY_SPEED);
        e.apply();
    }

    /** ボタンの名前(設定画面に出す)。 */
    @NonNull
    static String keyName(int keyCode) {
        switch (keyCode) {
            case 0: return Lang.t("未割り当て");
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "L1";
            case KeyEvent.KEYCODE_BUTTON_R1: return "R1";
            case KeyEvent.KEYCODE_BUTTON_L2: return "L2";
            case KeyEvent.KEYCODE_BUTTON_R2: return "R2";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return Lang.t("L3 (左スティック押し込み)");
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return Lang.t("R3 (右スティック押し込み)");
            case KeyEvent.KEYCODE_BUTTON_START: return "START";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "SELECT";
            case KeyEvent.KEYCODE_BUTTON_MODE: return Lang.t("HOME (モード)");
            case KeyEvent.KEYCODE_BUTTON_C: return "C";
            case KeyEvent.KEYCODE_BUTTON_Z: return "Z";
            case KeyEvent.KEYCODE_DPAD_UP: return Lang.t("十字キー ↑");
            case KeyEvent.KEYCODE_DPAD_DOWN: return Lang.t("十字キー ↓");
            case KeyEvent.KEYCODE_DPAD_LEFT: return Lang.t("十字キー ←");
            case KeyEvent.KEYCODE_DPAD_RIGHT: return Lang.t("十字キー →");
            case KeyEvent.KEYCODE_DPAD_CENTER: return Lang.t("十字キー 決定");
            default: {
                String s = KeyEvent.keyCodeToString(keyCode);
                if (s.startsWith("KEYCODE_BUTTON_")) return Lang.t("ボタン ") + s.substring("KEYCODE_BUTTON_".length());
                if (s.startsWith("KEYCODE_")) return s.substring("KEYCODE_".length());
                return s;
            }
        }
    }
}
