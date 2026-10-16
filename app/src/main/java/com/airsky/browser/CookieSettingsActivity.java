package com.airsky.browser;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;

/**
 * サードパーティ Cookie の許可/ブロックと例外のサイトの設定画面。
 * EXTRA_DETAIL を付けて開くと、各選択肢の説明ページになる。
 */
public class CookieSettingsActivity extends AppCompatActivity {

    static final String EXTRA_DETAIL = "detail";
    private static final String DETAIL_ALLOW = "allow";
    private static final String DETAIL_BLOCK = "block";

    private MaterialRadioButton allowRadio;
    private MaterialRadioButton blockRadio;
    private ViewGroup exceptionList;

    public static void open(@NonNull Context context) {
        context.startActivity(new Intent(context, CookieSettingsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cookie_settings);
        TouchEffectView.attach(getWindow());
        findViewById(R.id.cs_back).setOnClickListener(v -> finish());

        String detail = getIntent().getStringExtra(EXTRA_DETAIL);
        TextView title = findViewById(R.id.cs_title);
        if (detail != null) {
            title.setText("Cookie");
            findViewById(R.id.cs_main).setVisibility(View.GONE);
            findViewById(R.id.cs_detail).setVisibility(View.VISIBLE);
            bindDetail(DETAIL_ALLOW.equals(detail));
            return;
        }

        title.setText(Lang.t("サードパーティ Cookie"));
        allowRadio = findViewById(R.id.cs_allow_radio);
        blockRadio = findViewById(R.id.cs_block_radio);
        exceptionList = findViewById(R.id.cs_exceptions);
        findViewById(R.id.cs_allow).setOnClickListener(v -> setThirdPartyAllowed(true));
        findViewById(R.id.cs_block).setOnClickListener(v -> setThirdPartyAllowed(false));
        findViewById(R.id.cs_allow_detail).setOnClickListener(v -> openDetail(DETAIL_ALLOW));
        findViewById(R.id.cs_block_detail).setOnClickListener(v -> openDetail(DETAIL_BLOCK));
        findViewById(R.id.cs_add).setOnClickListener(v -> showAddDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allowRadio != null) refresh();
    }

    private void openDetail(String which) {
        startActivity(new Intent(this, CookieSettingsActivity.class).putExtra(EXTRA_DETAIL, which));
    }

    // ------------------------------------------------------------------
    // 許可/ブロックと例外
    // ------------------------------------------------------------------

    private void refresh() {
        boolean allowed = CookiePolicy.isThirdPartyAllowed(this);
        allowRadio.setChecked(allowed);
        blockRadio.setChecked(!allowed);
        findViewById(R.id.cs_disabled_note)
                .setVisibility(CookiePolicy.isCookiesAccepted(this) ? View.GONE : View.VISIBLE);

        exceptionList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String host : CookiePolicy.getExceptions(this)) {
            View row = inflater.inflate(R.layout.item_cookie_exception, exceptionList, false);
            ((TextView) row.findViewById(R.id.ce_host)).setText(host);
            row.findViewById(R.id.ce_remove).setOnClickListener(v -> {
                CookiePolicy.removeException(this, host);
                refresh();
            });
            exceptionList.addView(row);
        }
    }

    private void setThirdPartyAllowed(boolean allowed) {
        if (CookiePolicy.isThirdPartyAllowed(this) == allowed) return;
        CookiePolicy.setThirdPartyAllowed(this, allowed);
        refresh();
    }

    private void showAddDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_cookie_exception_add, null);
        EditText input = view.findViewById(R.id.ce_input);
        TextView error = view.findViewById(R.id.ce_error);
        MaterialButton add = view.findViewById(R.id.ce_add);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setView(view).create();
        view.findViewById(R.id.ce_cancel).setOnClickListener(v -> dialog.dismiss());
        Runnable submit = () -> {
            String host = CookiePolicy.normalizeHost(input.getText().toString());
            if (host == null) return;
            CookiePolicy.addException(this, host);
            dialog.dismiss();
            refresh();
        };
        add.setOnClickListener(v -> submit.run());
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                boolean empty = s.toString().trim().isEmpty();
                boolean ok = CookiePolicy.normalizeHost(s.toString()) != null;
                add.setEnabled(ok);
                error.setVisibility(empty || ok ? View.INVISIBLE : View.VISIBLE);
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.run();
                return true;
            }
            return false;
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        input.requestFocus();
    }

    // ------------------------------------------------------------------
    // 説明ページ
    // ------------------------------------------------------------------

    private void bindDetail(boolean allow) {
        ((TextView) findViewById(R.id.cs_detail_heading)).setText(allow
                ? Lang.t("サードパーティの Cookie を許可する:")
                : Lang.t("サードパーティの Cookie をブロックする:"));
        ViewGroup rows = findViewById(R.id.cs_detail_rows);
        if (allow) {
            addDetailRow(rows, R.drawable.ic_si_cookie,
                    Lang.t("サイトはサードパーティ Cookie を使用して、コンテンツや広告をカスタマイズしたり、"
                            + "他のサイトでのあなたのアクティビティを把握したりできます"));
            addDetailRow(rows, R.drawable.ic_si_web,
                    Lang.t("サードパーティ Cookie を使用するサイト機能は想定どおりに動作します"));
            addDetailRow(rows, R.drawable.ic_si_shield,
                    Lang.t("既知のトラッカーによる Cookie は引き続きブロックされます"));
        } else {
            addDetailRow(rows, R.drawable.ic_si_block,
                    Lang.t("例外として許可しない限り、サイトはサードパーティ Cookie を使用してコンテンツや広告を"
                            + "カスタマイズしたり、他のサイトでのあなたのアクティビティを把握したりすることはできません"));
            addDetailRow(rows, R.drawable.ic_si_cookie,
                    Lang.t("サイトは Cookie を使用して自サイトでの閲覧アクティビティを参照できます"));
            addDetailRow(rows, R.drawable.ic_si_broken,
                    Lang.t("サードパーティ Cookie を使用するサイト機能が動作しない可能性があります"));
        }
    }

    private void addDetailRow(ViewGroup parent, @DrawableRes int icon, String text) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_cookie_detail_row, parent, false);
        ((ImageView) row.findViewById(R.id.cd_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.cd_text)).setText(text);
        parent.addView(row);
    }
}
