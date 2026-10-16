package com.airsky.browser;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * ボタン・スティックに割り当てる入力を選ぶダイアログ({@link InputRouter} の形式で返す)。
 * 仮想コントローラーの編集と、接続したコントローラーのページ入力の割り当てで使う。
 */
final class BindingPicker {

    interface Callback {
        void onPicked(@NonNull String binding);
    }

    private BindingPicker() {
    }

    /**
     * @param direction スティック・十字キー用(向きのある入力)
     * @param allowPass 「そのまま(コントローラーの入力)」を選べるようにする(接続したコントローラー用)
     */
    static void show(@NonNull Context c, @NonNull CharSequence title, boolean direction, boolean allowPass,
                     @Nullable String current, @NonNull Callback callback) {
        if (direction) {
            showDirection(c, title, allowPass, current, callback);
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (allowPass) {
            labels.add(Lang.t("そのまま(コントローラーのボタン)"));
            actions.add(() -> callback.onPicked(InputRouter.PASS));
        }
        labels.add(Lang.t("キーボードのキー…"));
        actions.add(() -> pickKey(c, title, current, callback));
        labels.add(Lang.t("ゲームパッドのボタン…"));
        actions.add(() -> pickPad(c, title, current, callback));
        labels.add(Lang.t("マウスのボタン…"));
        actions.add(() -> pickMouse(c, title, current, callback));
        labels.add(Lang.t("割り当てなし"));
        actions.add(() -> callback.onPicked(InputRouter.NONE));
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setItems(labels.toArray(new CharSequence[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    private static void showDirection(@NonNull Context c, @NonNull CharSequence title, boolean allowPass,
                                      @Nullable String current, @NonNull Callback callback) {
        List<String> values = new ArrayList<>();
        if (allowPass) values.add(InputRouter.PASS);
        values.add(InputRouter.ARROWS);
        values.add(InputRouter.WASD);
        values.add(InputRouter.PAD_DPAD);
        values.add(InputRouter.STICK_LEFT);
        values.add(InputRouter.STICK_RIGHT);
        values.add(InputRouter.MOUSE_MOVE);
        values.add(InputRouter.SCROLL);
        values.add(InputRouter.NONE);
        CharSequence[] labels = new CharSequence[values.size()];
        int checked = -1;
        for (int i = 0; i < values.size(); i++) {
            labels[i] = InputRouter.describe(values.get(i));
            if (values.get(i).equals(current)) checked = i;
        }
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    callback.onPicked(values.get(which));
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    private static void pickKey(@NonNull Context c, @NonNull CharSequence title, @Nullable String current,
                                @NonNull Callback callback) {
        int[] keys = PageKeys.CHOOSABLE;
        CharSequence[] labels = new CharSequence[keys.length];
        int checked = -1;
        for (int i = 0; i < keys.length; i++) {
            labels[i] = PageKeys.name(keys[i]);
            if (InputRouter.key(keys[i]).equals(current)) checked = i;
        }
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    callback.onPicked(InputRouter.key(keys[which]));
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    private static void pickPad(@NonNull Context c, @NonNull CharSequence title, @Nullable String current,
                                @NonNull Callback callback) {
        CharSequence[] labels = new CharSequence[VirtualGamepad.BUTTON_COUNT];
        int checked = -1;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = i >= VirtualGamepad.UP ? Lang.t("十字キー ") + VirtualGamepad.BUTTON_NAMES[i] : VirtualGamepad.BUTTON_NAMES[i];
            if (InputRouter.pad(i).equals(current)) checked = i;
        }
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    callback.onPicked(InputRouter.pad(which));
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    private static void pickMouse(@NonNull Context c, @NonNull CharSequence title, @Nullable String current,
                                  @NonNull Callback callback) {
        int[] buttons = {1, 2, 4};
        CharSequence[] labels = new CharSequence[buttons.length];
        int checked = -1;
        for (int i = 0; i < buttons.length; i++) {
            labels[i] = InputRouter.mouseName(buttons[i]);
            if (InputRouter.mouse(buttons[i]).equals(current)) checked = i;
        }
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    callback.onPicked(InputRouter.mouse(buttons[which]));
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }
}
