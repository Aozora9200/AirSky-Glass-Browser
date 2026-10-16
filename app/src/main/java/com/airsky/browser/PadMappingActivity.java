package com.airsky.browser;

import androidx.annotation.NonNull;

/**
 * 接続したコントローラーの「ページ入力」の割り当て。ボタン・スティックごとに、そのまま送るか、
 * キーボードのキー・別のボタン・マウスに置き換えるかを選ぶ。
 */
public class PadMappingActivity extends PixelSettingsActivity {

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("ページ入力の割り当て");
    }

    @Override
    protected void onBuild() {
        gap();
        switchRow(R.drawable.ic_tool_gamepad, Lang.t("コントローラーをページ入力に使う"),
                Lang.t("オンの間は、ボタンをブラウザの操作に使わずページ(ゲーム)へ送ります。"
                        + "SELECT と START の同時押しでも切り替えられます"),
                ControllerPrefs.isPageMode(this), on -> ControllerPrefs.setPageMode(this, on));

        header(Lang.t("ボタン"));
        for (int i = 0; i < VirtualGamepad.BUTTON_COUNT; i++) addRow(i);
        header(Lang.t("スティック"));
        addRow(ControllerPrefs.SOURCE_LEFT_STICK);
        addRow(ControllerPrefs.SOURCE_RIGHT_STICK);

        row(R.drawable.ic_ps_history, Lang.t("すべて「そのまま」に戻す"), null,
                () -> confirm(Lang.t("割り当てを戻しますか?"), Lang.t("ページ入力のすべての割り当てを「そのまま」に戻します。"), Lang.t("戻す"), () -> {
                    ControllerPrefs.resetPageBindings(this);
                    rebuild();
                }));
        note(Lang.t("「そのまま」はコントローラーの入力をそのままページの Gamepad API へ送ります。"
                + "キーボードで遊ぶゲームでは、ボタンをキーに置き換えてください(例: A → Space、十字キー → 矢印キー)。"));
    }

    private void addRow(int source) {
        boolean stick = source >= ControllerPrefs.SOURCE_LEFT_STICK;
        String name = ControllerPrefs.sourceName(source);
        String title = source >= VirtualGamepad.UP && source <= VirtualGamepad.RIGHT ? Lang.t("十字キー ") + name : name;
        String binding = ControllerPrefs.pageBinding(this, source);
        row(0, title, InputRouter.describe(binding), () ->
                BindingPicker.show(this, title, stick, true, binding, b -> {
                    ControllerPrefs.setPageBinding(this, source, b);
                    rebuild();
                }));
    }
}
