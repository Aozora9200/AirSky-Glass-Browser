package com.airsky.browser;

import android.content.Context;
import android.content.Intent;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 利用規約。法的情報から読む時はただ表示し、同意が必要な時({@link #requireIntent})は
 * 下に「同意しない」「同意する」を出す(規約の版が上がった後の最初の起動など)。
 */
public class TermsActivity extends PixelSettingsActivity {

    private static final String EXTRA_REQUIRE = "com.airsky.browser.extra.TERMS_REQUIRE";

    /** 法的情報などから、読むだけで開く。 */
    static Intent viewIntent(@NonNull Context c) {
        return new Intent(c, TermsActivity.class);
    }

    /** 同意してもらうために開く。同意しなければアプリを閉じる。 */
    static Intent requireIntent(@NonNull Context c) {
        return new Intent(c, TermsActivity.class).putExtra(EXTRA_REQUIRE, true);
    }

    private boolean require() {
        return getIntent().getBooleanExtra(EXTRA_REQUIRE, false);
    }

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("利用規約");
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!require()) return;
        setBackVisible(false);
        setBottomBar(buildButtons());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmDecline();
            }
        });
    }

    @Override
    protected void onBuild() {
        if (require()) {
            note(Lang.t("利用規約が新しくなりました。AirSky を引き続きご利用いただくには、内容をご確認のうえ同意してください。"));
        } else {
            gap();
        }
        card(termsText(this, attrColor(com.google.android.material.R.attr.colorOnSurface),
                attrColor(androidx.appcompat.R.attr.colorPrimary)));
        gap();
    }

    /** 規約の本文を表示する TextView(セットアップの画面でも使う)。 */
    static TextView termsText(@NonNull Context c, int textColor, int accent) {
        TextView t = new TextView(c);
        t.setText(Markdown.render(Terms.body(c), accent, 0x22808080));
        t.setTextColor(textColor);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setLineSpacing(0, 1.3f);
        t.setTextIsSelectable(true);
        int p = Math.round(20 * c.getResources().getDisplayMetrics().density);
        t.setPadding(p, p, p, p);
        return t;
    }

    private View buildButtons() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(24), dp(12), dp(24), dp(20));
        bar.setBackgroundColor(attrColor(com.google.android.material.R.attr.colorSurfaceContainer));

        LayoutInflater inflater = LayoutInflater.from(this);
        Button decline = (Button) inflater.inflate(R.layout.item_ps_text_button, bar, false);
        decline.setText(Lang.t("同意しない"));
        decline.setOnClickListener(v -> confirmDecline());
        bar.addView(decline);

        View space = new View(this);
        bar.addView(space, new LinearLayout.LayoutParams(0, 1, 1f));

        Button accept = (Button) inflater.inflate(R.layout.item_ps_filled_button, bar, false);
        accept.setText(Lang.t("同意する"));
        accept.setOnClickListener(v -> {
            Terms.accept(this);
            setResult(RESULT_OK);
            finish();
        });
        bar.addView(accept);
        return bar;
    }

    private void confirmDecline() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("利用規約に同意しない"))
                .setMessage(Lang.t("利用規約に同意いただけない場合、AirSky をご利用いただけません。アプリを終了しますか?"))
                .setPositiveButton(Lang.t("戻る"), null)
                .setNegativeButton(Lang.t("終了"), (d, w) -> finishAffinity())
                .show();
    }
}
