package com.airsky.browser;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.radiobutton.MaterialRadioButton;

/**
 * 非アクティブなタブの設定(Pixel の設定風)。
 * 期間(使用しない / 7・14・21・28 日間経過後)と、重複するタブのアーカイブ・3 か月後に自動で閉じるを切り替える。
 */
public class InactiveTabsSettingsActivity extends AppCompatActivity {

    private final MaterialRadioButton[] radios = new MaterialRadioButton[InactiveTabs.DAY_OPTIONS.length];
    private MaterialSwitch duplicatesSwitch;
    private MaterialSwitch autoCloseSwitch;

    public static void open(@NonNull Context context) {
        context.startActivity(new Intent(context, InactiveTabsSettingsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inactive_tabs_settings);
        TouchEffectView.attach(getWindow());
        findViewById(R.id.it_back).setOnClickListener(v -> finish());

        // 大きな見出しが隠れたら、上のバーに小さな見出しを出す(Pixel の設定と同じ)
        View bigTitle = findViewById(R.id.it_title);
        View barTitle = findViewById(R.id.it_bar_title);
        ((NestedScrollView) findViewById(R.id.it_scroll)).setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener) (v, x, y, ox, oy) -> {
                    boolean hidden = y > bigTitle.getBottom() - bigTitle.getHeight() / 3;
                    float target = hidden ? 1f : 0f;
                    if (barTitle.getTag() == null || (float) barTitle.getTag() != target) {
                        barTitle.setTag(target);
                        barTitle.animate().alpha(target).setDuration(150).start();
                    }
                });

        buildPeriods();
        duplicatesSwitch = findViewById(R.id.it_duplicates_switch);
        autoCloseSwitch = findViewById(R.id.it_auto_close_switch);
        findViewById(R.id.it_duplicates).setOnClickListener(v -> {
            if (!InactiveTabs.isEnabled(this)) return;
            InactiveTabs.setArchiveDuplicates(this, !InactiveTabs.archiveDuplicatesSetting(this));
            refresh(true);
        });
        findViewById(R.id.it_auto_close).setOnClickListener(v -> {
            if (!InactiveTabs.isEnabled(this)) return;
            InactiveTabs.setAutoClose(this, !InactiveTabs.autoCloseSetting(this));
            refresh(true);
        });
        refresh(false);
    }

    /** 期間の選択肢を、角の丸いカードをつなげた形で並べる。 */
    private void buildPeriods() {
        LinearLayout box = findViewById(R.id.it_periods);
        LayoutInflater inflater = LayoutInflater.from(this);
        int n = InactiveTabs.DAY_OPTIONS.length;
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < n; i++) {
            int days = InactiveTabs.DAY_OPTIONS[i];
            View row = inflater.inflate(R.layout.item_inactive_period, box, false);
            row.setBackgroundResource(i == 0 ? R.drawable.bg_cs_card_top
                    : i == n - 1 ? R.drawable.bg_cs_card_bottom : R.drawable.bg_it_card_middle);
            if (i > 0) {
                ((ViewGroup.MarginLayoutParams) row.getLayoutParams()).topMargin = Math.round(3 * d);
            }
            ((TextView) row.findViewById(R.id.ip_title)).setText(InactiveTabs.label(days));
            TextView summary = row.findViewById(R.id.ip_summary);
            if (days <= 0) {
                summary.setText(Lang.t("タブを非アクティブにしません"));
            } else if (days == InactiveTabs.DEFAULT_DAYS) {
                summary.setText(days + Lang.t(" 日間使用していないタブとグループを移動します(おすすめ)"));
            } else {
                summary.setText(days + Lang.t(" 日間使用していないタブとグループを移動します"));
            }
            radios[i] = row.findViewById(R.id.ip_radio);
            row.setOnClickListener(v -> {
                if (InactiveTabs.days(this) == days) return;
                InactiveTabs.setDays(this, days);
                refresh(true);
            });
            box.addView(row);
        }
    }

    private void refresh(boolean animate) {
        int days = InactiveTabs.days(this);
        for (int i = 0; i < radios.length; i++) {
            radios[i].setChecked(InactiveTabs.DAY_OPTIONS[i] == days);
        }
        boolean enabled = days > 0;
        duplicatesSwitch.setChecked(enabled && InactiveTabs.archiveDuplicatesSetting(this));
        autoCloseSwitch.setChecked(enabled && InactiveTabs.autoCloseSetting(this));
        setRowEnabled(findViewById(R.id.it_duplicates), duplicatesSwitch, enabled, animate);
        setRowEnabled(findViewById(R.id.it_auto_close), autoCloseSwitch, enabled, animate);

        // 機能を切った時はアイコンも少し沈ませる
        View hero = findViewById(R.id.it_hero);
        float target = enabled ? 1f : 0.55f;
        if (animate) {
            hero.animate().alpha(target).scaleX(enabled ? 1f : 0.92f).scaleY(enabled ? 1f : 0.92f)
                    .setDuration(260).setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f)).start();
        } else {
            hero.setAlpha(target);
            hero.setScaleX(enabled ? 1f : 0.92f);
            hero.setScaleY(enabled ? 1f : 0.92f);
        }
    }

    private static void setRowEnabled(View row, MaterialSwitch sw, boolean enabled, boolean animate) {
        row.setEnabled(enabled);
        sw.setEnabled(enabled);
        float alpha = enabled ? 1f : 0.45f;
        if (animate) {
            row.animate().alpha(alpha).setDuration(200).start();
        } else {
            row.setAlpha(alpha);
        }
    }
}
