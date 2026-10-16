package com.airsky.browser;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DateFormat;
import java.util.Date;
import java.util.Map;

/**
 * 保存した 1 件の詳細。最初はすべて伏せ字で、「表示する」で本人確認をした時だけ中身を出す。
 * 表示は {@link #REVEAL_MS} 経つか、画面を離れると自動的に伏せ字に戻す。
 */
public class AutofillEntryActivity extends PixelSettingsActivity {

    static final String EXTRA_GUID = "com.airsky.browser.extra.AUTOFILL_GUID";

    private static final long REVEAL_MS = 60_000;
    private static final long CLIPBOARD_CLEAR_MS = 60_000;

    @Nullable private CredentialVault.Record record;
    /** 本人確認後に表示している中身。伏せ字に戻す時に消す。 */
    @Nullable private Map<String, String> revealed;
    private boolean loaded = false;
    @Nullable private androidx.appcompat.app.AlertDialog editor;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::hideValues;

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("保存した情報");
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // 画面読み上げなどの本来のユーザー補助ツール以外のアプリに、表示した内容を読ませない
            body.setAccessibilityDataSensitive(android.view.View.ACCESSIBILITY_DATA_SENSITIVE_YES);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 画面を離れたら必ず伏せ字に戻し、編集中の内容も閉じる
        hideValues();
        if (editor != null) {
            editor.dismiss();
            editor = null;
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(hide);
        clearRevealed();
        super.onDestroy();
    }

    private void load() {
        String guid = getIntent().getStringExtra(EXTRA_GUID);
        CredentialVault vault = CredentialVault.get(this);
        CredentialVault.IO.execute(() -> {
            CredentialVault.Record r = vault.find(guid);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (r == null) {
                    finish();
                    return;
                }
                record = r;
                loaded = true;
                rebuild();
            });
        });
    }

    @Override
    protected void onBuild() {
        CredentialVault.Record r = record;
        if (!loaded || r == null) return;
        boolean shown = revealed != null;

        if (r.type == CredentialVault.LOGIN) {
            header(Lang.t("ログイン情報"));
            row(R.drawable.ic_ps_link, Lang.t("サイト"), r.origin, null);
            valueRow(R.drawable.ic_ext_person, Lang.t("ユーザー名"),
                    shown ? r.username : Mask.username(r.username), shown ? r.username : null);
            valueRow(R.drawable.ic_ps_key, Lang.t("パスワード"), shown ? v(CredentialVault.F_PASSWORD) : Mask.hidden(),
                    shown ? v(CredentialVault.F_PASSWORD) : null);
        } else if (r.type == CredentialVault.CARD) {
            header(Lang.t("カード"));
            String number = v(CredentialVault.F_CC_NUMBER);
            valueRow(R.drawable.ic_ps_credit_card, Lang.t("カード番号"), shown ? Mask.groupedNumber(number) : Mask.card(r),
                    shown ? number : null);
            if (r.fields.contains(CredentialVault.F_CC_NAME)) {
                valueRow(R.drawable.ic_ext_person, Lang.t("名義"), shown ? v(CredentialVault.F_CC_NAME) : "•••••",
                        shown ? v(CredentialVault.F_CC_NAME) : null);
            }
            String exp = shown ? expiry() : "••/••";
            row(R.drawable.ic_ps_history, Lang.t("有効期限"), exp, null);
        } else {
            header(Lang.t("住所"));
            for (String f : CredentialVault.ADDRESS_FIELDS) {
                if (!r.fields.contains(f)) continue;
                String value = v(f);
                String masked = CredentialVault.F_COUNTRY.equals(f) ? r.country : "•••••";
                valueRow(0, addressLabel(f), shown ? value : masked, shown ? value : null);
            }
        }

        gap();
        if (shown) {
            row(R.drawable.ic_ps_visibility_off, Lang.t("伏せ字に戻す"), Lang.t("1 分後や画面を離れた時にも自動的に戻ります"),
                    this::hideValues);
        } else {
            row(R.drawable.ic_ps_fingerprint, Lang.t("表示する"), Lang.t("本人確認をして内容を表示します"), this::reveal);
        }
        row(R.drawable.ic_omni_edit, Lang.t("編集"), shown ? null : Lang.t("本人確認が必要です"), this::edit);
        row(R.drawable.ic_ps_delete, Lang.t("削除"), null, this::delete);

        note(shown ? Lang.t("項目を押すとコピーします(クリップボードは 1 分後に消去します)。")
                : Lang.t("内容は本人確認をするまで伏せ字で表示されます。"));
        if (r.created > 0) {
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
            String info = Lang.t("保存: ") + df.format(new Date(r.created));
            if (r.lastUsed > 0) info += Lang.t("\n最後に使用: ") + df.format(new Date(r.lastUsed));
            note(info);
        }
    }

    private void valueRow(int icon, String title, String display, @Nullable String copyValue) {
        row(icon, title, display, copyValue == null || copyValue.isEmpty() ? null
                : () -> copySensitive(this, title, copyValue));
    }

    private String v(String key) {
        if (revealed == null) return "";
        String s = revealed.get(key);
        return s == null ? "" : s;
    }

    private String expiry() {
        String m = v(CredentialVault.F_CC_EXP_MONTH);
        String y = v(CredentialVault.F_CC_EXP_YEAR);
        if (m.isEmpty() && y.isEmpty()) return Lang.t("未設定");
        if (m.length() == 1) m = "0" + m;
        return m + "/" + y;
    }

    static String addressLabel(String f) {
        switch (f) {
            case CredentialVault.F_NAME: return Lang.t("氏名");
            case CredentialVault.F_GIVEN_NAME: return Lang.t("名");
            case CredentialVault.F_ADDITIONAL_NAME: return Lang.t("ミドルネーム");
            case CredentialVault.F_FAMILY_NAME: return Lang.t("姓");
            case CredentialVault.F_ORGANIZATION: return Lang.t("会社名");
            case CredentialVault.F_POSTAL: return Lang.t("郵便番号");
            case CredentialVault.F_LEVEL1: return Lang.t("都道府県");
            case CredentialVault.F_LEVEL2: return Lang.t("市区町村");
            case CredentialVault.F_LEVEL3: return Lang.t("地区");
            case CredentialVault.F_STREET: return Lang.t("番地・建物名");
            case CredentialVault.F_COUNTRY: return Lang.t("国");
            case CredentialVault.F_TEL: return Lang.t("電話番号");
            case CredentialVault.F_EMAIL: return Lang.t("メールアドレス");
            default: return f;
        }
    }

    // ------------------------------------------------------------------

    private interface WithValues {
        void run(Map<String, String> values);
    }

    /** 本人確認をして中身を読み出す(表示中なら確認しなおさない)。 */
    private void withValues(String title, WithValues action) {
        CredentialVault.Record r = record;
        if (r == null) return;
        if (revealed != null) {
            action.run(revealed);
            return;
        }
        VaultAuth.unseal(this, r, title, siteOf(r), new VaultAuth.UnsealCallback() {
            @Override
            public void onUnsealed(@NonNull Map<String, String> values) {
                action.run(values);
            }

            @Override
            public void onFailed(@NonNull CharSequence message, boolean canceled) {
                if (!canceled) Toast.makeText(AutofillEntryActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void reveal() {
        withValues(Lang.t("保存した情報を表示"), values -> {
            revealed = values;
            handler.removeCallbacks(hide);
            handler.postDelayed(hide, REVEAL_MS);
            rebuild();
        });
    }

    private void hideValues() {
        handler.removeCallbacks(hide);
        if (revealed == null) return;
        clearRevealed();
        if (!isDestroyed()) rebuild();
    }

    private void clearRevealed() {
        if (revealed != null) revealed.clear();
        revealed = null;
    }

    private void edit() {
        CredentialVault.Record r = record;
        if (r == null) return;
        withValues(Lang.t("保存した情報を編集"), values -> {
            // 編集画面には複製を渡す(表示の自動消去と別に管理する)
            editor = AutofillEditor.show(this, r.type, r, new java.util.HashMap<>(values), guid -> {
                hideValues();
                load();
            });
            if (values != revealed) values.clear();
        });
    }

    private void delete() {
        CredentialVault.Record r = record;
        if (r == null) return;
        confirm(Lang.t("削除"), Lang.t("この情報を削除しますか?元に戻すことはできません。"), Lang.t("削除"), () ->
                VaultAuth.confirm(this, Lang.t("保存した情報を削除"), siteOf(r), new VaultAuth.ConfirmCallback() {
                    @Override
                    public void onConfirmed() {
                        CredentialVault.IO.execute(() -> {
                            boolean ok = CredentialVault.get(AutofillEntryActivity.this).delete(r.guid);
                            runOnUiThread(() -> {
                                Toast.makeText(AutofillEntryActivity.this, ok ? Lang.t("削除しました") : Lang.t("削除できませんでした"),
                                        Toast.LENGTH_SHORT).show();
                                if (ok) finish();
                            });
                        });
                    }

                    @Override
                    public void onFailed(@NonNull CharSequence message, boolean canceled) {
                        if (!canceled) Toast.makeText(AutofillEntryActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    @Nullable
    private static String siteOf(CredentialVault.Record r) {
        return r.type == CredentialVault.LOGIN ? r.host() : null;
    }

    // ------------------------------------------------------------------

    /**
     * 秘密の値をコピーする。キーボードのクリップボード履歴などに出ないよう「機密」の印を付け、
     * 一定時間後に(まだ同じ内容なら)クリップボードを消す。
     */
    static void copySensitive(Context context, String title, String value) {
        Context app = context.getApplicationContext();
        ClipboardManager cm = app.getSystemService(ClipboardManager.class);
        if (cm == null) return;
        String label = "Aozora:" + Long.toHexString(new java.security.SecureRandom().nextLong());
        ClipData clip = ClipData.newPlainText(label, value);
        PersistableBundle extras = new PersistableBundle();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
        } else {
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
        }
        clip.getDescription().setExtras(extras);
        cm.setPrimaryClip(clip);
        // Android 13 以降はシステムがコピーした内容を表示するので、自前のメッセージは出さない
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, title + Lang.t(" をコピーしました"), Toast.LENGTH_SHORT).show();
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                ClipDescription d = cm.getPrimaryClipDescription();
                if (d != null && label.contentEquals(d.getLabel() != null ? d.getLabel() : "")
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cm.clearPrimaryClip();
                }
            } catch (Exception ignored) {
                // 裏に回っていてクリップボードを読めない時など
            }
        }, CLIPBOARD_CLEAR_MS);
    }
}
