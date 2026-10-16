package com.airsky.browser;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 設定 › 自動入力とパスワード。
 * 保存・自動入力のオン/オフと、保存した情報(パスワード・お支払い方法・住所)の管理を開く。
 */
public class AutofillSettingsActivity extends PixelSettingsActivity {

    /** 件数(Keystore の処理があるので裏で数えて、終わったら作り直す)。 */
    private final int[] counts = {-1, -1, -1};
    private int neverCount = -1;
    private boolean broken = false;
    private boolean unreadable = false;

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("自動入力とパスワード");
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCounts();
    }

    private void loadCounts() {
        CredentialVault vault = CredentialVault.get(this);
        CredentialVault.IO.execute(() -> {
            int[] c = {vault.count(CredentialVault.LOGIN), vault.count(CredentialVault.CARD),
                    vault.count(CredentialVault.ADDRESS)};
            int never = vault.neverSaveList().size();
            boolean b = vault.isBroken();
            boolean u = vault.hasUnreadable();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                unreadable = u;
                System.arraycopy(c, 0, counts, 0, 3);
                neverCount = never;
                broken = b;
                rebuild();
            });
        });
    }

    @Override
    protected void onBuild() {
        String reason = CredentialVault.unsupportedReason(this);
        boolean supported = reason == null;
        boolean master = supported && CredentialVault.isMasterOn(this);

        header(Lang.t("自動入力"));
        switchRow(R.drawable.ic_ps_lock, Lang.t("パスワードなどを保存して自動入力する"),
                supported ? Lang.t("ログイン情報・お支払い方法・住所を保存し、入力時に本人確認をして自動入力します") : reason,
                master, on -> {
                    if (on) {
                        enable();
                    } else {
                        CredentialVault.setMasterOn(this, false);
                        rebuild();
                    }
                }).setEnabled(supported);

        header(Lang.t("保存と自動入力の対象"));
        typeSwitch(CredentialVault.LOGIN, R.drawable.ic_ps_key, Lang.t("パスワード"), Lang.t("ログイン時にパスワードの保存を提案します"), master);
        typeSwitch(CredentialVault.CARD, R.drawable.ic_ps_credit_card, Lang.t("お支払い方法"),
                Lang.t("クレジットカードの保存を提案します(セキュリティ コードは保存しません)"), master);
        typeSwitch(CredentialVault.ADDRESS, R.drawable.ic_ps_location, Lang.t("住所"), Lang.t("住所の自動入力に使います"), master);

        header(Lang.t("保存した情報"));
        listRow(CredentialVault.LOGIN, R.drawable.ic_ps_key, Lang.t("保存したパスワード"));
        listRow(CredentialVault.CARD, R.drawable.ic_ps_credit_card, Lang.t("お支払い方法"));
        listRow(CredentialVault.ADDRESS, R.drawable.ic_ps_location, Lang.t("住所"));
        row(R.drawable.ic_ps_visibility_off, Lang.t("パスワードを保存しないサイト"),
                neverCount < 0 ? null : neverCount + Lang.t(" 件"),
                () -> startActivity(new Intent(this, AutofillListActivity.class)
                        .putExtra(AutofillListActivity.EXTRA_TYPE, AutofillListActivity.TYPE_NEVER))).chevron();

        header(Lang.t("管理"));
        row(R.drawable.ic_ps_delete, Lang.t("保存した情報をすべて削除"),
                Lang.t("保存したパスワード・お支払い方法・住所と暗号鍵をすべて削除します"), this::deleteAll);

        if (unreadable && !broken) {
            note(Lang.t("⚠ 端末の生体認証や画面ロックが変更されたため、読み出せなくなった情報があります。"
                    + "該当する情報を削除して、もう一度保存してください。"));
        }
        if (broken) {
            note(Lang.t("⚠ 保存した情報を読み込めませんでした。端末の画面ロックの変更などで暗号鍵が使えなくなった可能性があります。"
                    + "「保存した情報をすべて削除」を行うと、もう一度使えるようになります。"));
        }
        note(Lang.t("保存した情報は端末内で暗号化され、Android Keystore の鍵で保護されます。"
                + "パスワードやカード番号などは、表示・自動入力のたびに生体認証(または画面ロック)での本人確認が必要で、"
                + "確認するまでは伏せ字で表示されます。\n"
                + "端末に生体認証を追加・変更したり画面ロックを解除したりすると、安全のため保存した情報は読み出せなくなります。\n"
                + "シークレット タブでは保存を提案しません。"));
    }

    private void typeSwitch(int type, int icon, String title, String summary, boolean master) {
        switchRow(icon, title, summary, master && CredentialVault.isTypeOn(this, type),
                on -> CredentialVault.setTypeOn(this, type, on)).setEnabled(master);
    }

    private void listRow(int type, int icon, String title) {
        int n = counts[type];
        row(icon, title, n < 0 ? null : n + Lang.t(" 件"),
                () -> startActivity(new Intent(this, AutofillListActivity.class)
                        .putExtra(AutofillListActivity.EXTRA_TYPE, type))).chevron();
    }

    private void enable() {
        String reason = CredentialVault.unsupportedReason(this);
        if (reason != null) {
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
            rebuild();
            return;
        }
        CredentialVault vault = CredentialVault.get(this);
        CredentialVault.IO.execute(() -> {
            boolean ok = vault.prepareKeys();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (ok) {
                    CredentialVault.setMasterOn(this, true);
                } else {
                    Toast.makeText(this, Lang.t("暗号鍵を作成できないため、この端末では使用できません"), Toast.LENGTH_LONG).show();
                }
                rebuild();
            });
        });
    }

    private void deleteAll() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("保存した情報をすべて削除"))
                .setMessage(Lang.t("保存したパスワード・お支払い方法・住所がすべて削除されます。元に戻すことはできません。"))
                .setPositiveButton(Lang.t("削除"), (d, w) -> {
                    Runnable wipe = () -> CredentialVault.IO.execute(() -> {
                        CredentialVault.get(this).deleteAll();
                        runOnUiThread(() -> {
                            Toast.makeText(this, Lang.t("保存した情報を削除しました"), Toast.LENGTH_SHORT).show();
                            loadCounts();
                        });
                    });
                    if (VaultAuth.canConfirm(this)) {
                        VaultAuth.confirm(this, Lang.t("保存した情報を削除"), null, new VaultAuth.ConfirmCallback() {
                            @Override
                            public void onConfirmed() {
                                wipe.run();
                            }

                            @Override
                            public void onFailed(@NonNull CharSequence message, boolean canceled) {
                                if (!canceled) Toast.makeText(AutofillSettingsActivity.this, message,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        // 画面ロックを外した端末: 鍵はすでに無効なので、本人確認なしで消せるようにする
                        // (中身は誰も復号できない)
                        wipe.run();
                    }
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }
}
