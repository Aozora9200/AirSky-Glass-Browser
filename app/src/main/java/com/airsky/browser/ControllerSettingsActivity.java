package com.airsky.browser;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

/**
 * コントローラーの設定。ボタンの割り当て(メニュー・仮想マウス・クリックなど)と、カーソルの速さ。
 */
public class ControllerSettingsActivity extends PixelSettingsActivity {

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("コントローラー");
    }

    @Override
    protected void onBuild() {
        boolean enabled = ControllerPrefs.isEnabled(this);
        gap();
        switchRow(R.drawable.ic_tool_gamepad, Lang.t("コントローラーでブラウザを操作する"),
                Lang.t("オフにすると、ボタンとスティックはすべてページ(ゲームなど)へそのまま渡します"), enabled, on -> {
                    ControllerPrefs.setEnabled(this, on);
                    rebuild();
                });
        card(buildSpeedCard(enabled));

        header(Lang.t("ボタンの割り当て"));
        for (int i = 0; i < ControllerPrefs.COUNT; i++) {
            final int action = i;
            row(0, ControllerPrefs.LABELS[i], ControllerPrefs.SUMMARIES[i], () -> capture(action))
                    .value(ControllerPrefs.keyName(ControllerPrefs.keyFor(this, i)))
                    .setEnabled(enabled);
        }
        row(R.drawable.ic_ps_history, Lang.t("既定の割り当てに戻す"), null,
                () -> confirm(Lang.t("既定の割り当てに戻しますか?"), Lang.t("ボタンの割り当てとカーソルの速さを最初の状態に戻します。"),
                        Lang.t("戻す"), () -> {
                            ControllerPrefs.resetAssignments(this);
                            rebuild();
                        }));

        header(Lang.t("スティックと十字キー"));
        note(Lang.t("左スティック: 仮想マウスのカーソルを動かします(スクロールモードと、仮想マウスを使っていない時は"
                + "ページのスクロール)。\n右スティック: ページをスクロールします。\n"
                + "十字キー: 仮想マウスの間はカーソルを動かし、それ以外は項目を選びます。\n"
                + "クリック・右クリックのボタンは、仮想マウスを使っていない時は普段の決定ボタンとして使えます。"
                + "割り当てていない B ボタンは「戻る」になります。"));
    }

    private View buildSpeedCard(boolean enabled) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(10));
        box.setAlpha(enabled ? 1f : 0.42f);
        TextView title = new TextView(this);
        title.setText(Lang.t("カーソルとスクロールの速さ"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurface));
        box.addView(title);
        TextView value = new TextView(this);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        value.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        value.setGravity(Gravity.CENTER);
        int speed = ControllerPrefs.cursorSpeed(this);
        value.setText(speedLabel(speed));
        box.addView(value);
        Slider slider = new Slider(this);
        slider.setValueFrom(1);
        slider.setValueTo(10);
        slider.setStepSize(1);
        slider.setValue(speed);
        slider.setEnabled(enabled);
        slider.addOnChangeListener((s, v, fromUser) -> {
            value.setText(speedLabel(Math.round(v)));
            ControllerPrefs.setCursorSpeed(this, Math.round(v));
        });
        box.addView(slider);
        return box;
    }

    private static String speedLabel(int speed) {
        return speed + (speed == 5 ? Lang.t(" (標準)") : speed < 5 ? Lang.t(" (ゆっくり)") : Lang.t(" (速い)"));
    }

    /** 割り当てるボタンを押してもらう。 */
    private void capture(int action) {
        TextView msg = new TextView(this);
        msg.setText(Lang.t("「") + ControllerPrefs.LABELS[action] + Lang.t("」に割り当てるボタンを、コントローラーで押してください。\n\n"
                + "今の割り当て: ") + ControllerPrefs.keyName(ControllerPrefs.keyFor(this, action)));
        msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        msg.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        msg.setPadding(dp(24), dp(12), dp(24), 0);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("ボタンの割り当て"))
                .setView(msg)
                .setNeutralButton(Lang.t("割り当てを解除"), (d, w) -> {
                    ControllerPrefs.assign(this, action, 0);
                    rebuild();
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .create();
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && !GamepadInput.isController(event)) return false;
            if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
            if (event.getRepeatCount() != 0 || !GamepadInput.isController(event)) return false;
            int previous = ControllerPrefs.actionFor(this, keyCode);
            ControllerPrefs.assign(this, action, keyCode);
            d.dismiss();
            rebuild();
            if (previous >= 0 && previous != action) {
                android.widget.Toast.makeText(this, Lang.t("「") + ControllerPrefs.LABELS[previous]
                        + Lang.t("」の割り当てを解除しました"), android.widget.Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        dialog.show();
    }
}
