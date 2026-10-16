package com.airsky.browser;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 保存したパスワード / お支払い方法 / 住所 / 保存しないサイト の一覧。
 * 中身はすべて伏せ字で出し、押すと詳細(本人確認で表示)を開く。
 */
public class AutofillListActivity extends PixelSettingsActivity {

    static final String EXTRA_TYPE = "com.airsky.browser.extra.AUTOFILL_TYPE";
    /** 「パスワードを保存しないサイト」の一覧。 */
    static final int TYPE_NEVER = 3;

    private int type;
    @Nullable private List<CredentialVault.Record> records;
    @Nullable private List<String> never;

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        switch (getIntent().getIntExtra(EXTRA_TYPE, CredentialVault.LOGIN)) {
            case CredentialVault.CARD: return Lang.t("お支払い方法");
            case CredentialVault.ADDRESS: return Lang.t("住所");
            case TYPE_NEVER: return Lang.t("パスワードを保存しないサイト");
            default: return Lang.t("保存したパスワード");
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        type = getIntent().getIntExtra(EXTRA_TYPE, CredentialVault.LOGIN);
        if (type < CredentialVault.LOGIN || type > TYPE_NEVER) type = CredentialVault.LOGIN;
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        if (type != TYPE_NEVER) {
            setAction(R.drawable.ic_si_add, Lang.t("追加"), v -> AutofillEditor.show(this, type, null, null, guid -> load()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        CredentialVault vault = CredentialVault.get(this);
        CredentialVault.IO.execute(() -> {
            List<CredentialVault.Record> r = type == TYPE_NEVER ? null : vault.list(type);
            List<String> n = type == TYPE_NEVER ? vault.neverSaveList() : null;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                records = r;
                never = n;
                rebuild();
            });
        });
    }

    @Override
    protected void onBuild() {
        if (type == TYPE_NEVER) {
            buildNever();
            return;
        }
        if (records == null) return;
        if (!CredentialVault.isActive(this, type)) {
            note(Lang.t("自動入力はオフになっています。設定の「自動入力とパスワード」でオンにできます。"));
        }
        if (records.isEmpty()) {
            note(type == CredentialVault.LOGIN ? Lang.t("保存したパスワードはありません。サイトにログインすると保存を提案します。")
                    : type == CredentialVault.CARD ? Lang.t("保存したお支払い方法はありません。")
                    : Lang.t("保存した住所はありません。右上の「+」から追加できます。"));
            return;
        }
        header(records.size() + Lang.t(" 件"));
        for (CredentialVault.Record r : records) {
            Row row;
            if (type == CredentialVault.LOGIN) {
                row = row(R.drawable.ic_ps_key, r.host(), Mask.username(r.username), () -> openEntry(r));
            } else if (type == CredentialVault.CARD) {
                row = row(R.drawable.ic_ps_credit_card, Mask.card(r), Lang.t("名義 •••••  有効期限 ••/••"), () -> openEntry(r));
            } else {
                row = row(R.drawable.ic_ps_location, Mask.address(r), Lang.t("〒•••-••••  ") + Mask.hidden(), () -> openEntry(r));
            }
            row.chevron();
        }
        note(Lang.t("内容を表示するには本人確認が必要です。"));
    }

    private void buildNever() {
        if (never == null) return;
        if (never.isEmpty()) {
            note(Lang.t("パスワードの保存を提案しないサイトはありません。"));
            return;
        }
        header(never.size() + Lang.t(" 件"));
        List<String> copy = new ArrayList<>(never);
        for (String origin : copy) {
            row(R.drawable.ic_ps_visibility_off, CredentialVault.hostOf(origin), origin, () ->
                    confirm(Lang.t("リストから削除"), CredentialVault.hostOf(origin) + Lang.t(" でパスワードの保存を提案するようにします。"),
                            Lang.t("削除"), () -> CredentialVault.IO.execute(() -> {
                                boolean ok = CredentialVault.get(this).removeNeverSave(origin);
                                runOnUiThread(() -> {
                                    if (!ok) Toast.makeText(this, Lang.t("削除できませんでした"), Toast.LENGTH_SHORT).show();
                                    load();
                                });
                            })));
        }
        note(Lang.t("サイトを押すと、このリストから削除します。"));
    }

    private void openEntry(CredentialVault.Record r) {
        startActivity(new Intent(this, AutofillEntryActivity.class)
                .putExtra(AutofillEntryActivity.EXTRA_GUID, r.guid));
    }
}
