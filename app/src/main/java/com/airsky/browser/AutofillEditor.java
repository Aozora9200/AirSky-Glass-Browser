package com.airsky.browser;

import android.app.Activity;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 保存した情報の追加・編集ダイアログ。
 *
 * <p>入力欄は、画面の状態の保存(回転時など)や端末の自動入力サービスに値が渡らないようにしている。
 * ダイアログはスクリーンショット・画面録画に映らない。</p>
 */
final class AutofillEditor {

    interface OnSaved {
        void onSaved(@NonNull String guid);
    }

    private AutofillEditor() {
    }

    /**
     * @param record 編集するレコード(追加なら null)
     * @param values 本人確認後に読み出した中身(追加なら null)。ダイアログを閉じると消す
     */
    static AlertDialog show(@NonNull Activity activity, int type, @Nullable CredentialVault.Record record,
                            @Nullable Map<String, String> values, @NonNull OnSaved onSaved) {
        Map<String, String> init = values != null ? values : new HashMap<>();
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 20);
        form.setPadding(pad, dp(activity, 8), pad, 0);
        Map<String, TextInputEditText> inputs = new LinkedHashMap<>();

        String title;
        if (type == CredentialVault.LOGIN) {
            title = record == null ? Lang.t("パスワードを追加") : Lang.t("パスワードを編集");
            inputs.put("origin", field(activity, form, Lang.t("サイト (例: https://example.com)"),
                    record != null ? record.origin : "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, false));
            inputs.put("username", field(activity, form, Lang.t("ユーザー名"),
                    record != null ? record.username : "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, false));
            inputs.put(CredentialVault.F_PASSWORD, field(activity, form, Lang.t("パスワード"),
                    init.get(CredentialVault.F_PASSWORD), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, true));
        } else if (type == CredentialVault.CARD) {
            title = record == null ? Lang.t("カードを追加") : Lang.t("カードを編集");
            inputs.put(CredentialVault.F_CC_NUMBER, field(activity, form, Lang.t("カード番号"),
                    init.get(CredentialVault.F_CC_NUMBER), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD, true));
            inputs.put(CredentialVault.F_CC_NAME, field(activity, form, Lang.t("名義 (例: TARO AOZORA)"),
                    init.get(CredentialVault.F_CC_NAME), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, false));
            inputs.put(CredentialVault.F_CC_EXP_MONTH, field(activity, form, Lang.t("有効期限 (月)"),
                    init.get(CredentialVault.F_CC_EXP_MONTH), InputType.TYPE_CLASS_NUMBER, false));
            inputs.put(CredentialVault.F_CC_EXP_YEAR, field(activity, form, Lang.t("有効期限 (年・4 桁)"),
                    init.get(CredentialVault.F_CC_EXP_YEAR), InputType.TYPE_CLASS_NUMBER, false));
        } else {
            title = record == null ? Lang.t("住所を追加") : Lang.t("住所を編集");
            for (String f : new String[]{CredentialVault.F_NAME, CredentialVault.F_ORGANIZATION, CredentialVault.F_POSTAL,
                    CredentialVault.F_LEVEL1, CredentialVault.F_LEVEL2, CredentialVault.F_STREET, CredentialVault.F_COUNTRY,
                    CredentialVault.F_TEL, CredentialVault.F_EMAIL}) {
                int inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
                if (CredentialVault.F_TEL.equals(f)) inputType = InputType.TYPE_CLASS_PHONE;
                if (CredentialVault.F_EMAIL.equals(f)) {
                    inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
                }
                String initial = init.get(f);
                if (CredentialVault.F_COUNTRY.equals(f) && (initial == null || initial.isEmpty()) && record == null) {
                    initial = "JP";
                }
                String hint = AutofillEntryActivity.addressLabel(f)
                        + (CredentialVault.F_COUNTRY.equals(f) ? Lang.t(" (国コード 例: JP)") : "");
                inputs.put(f, field(activity, form, hint, initial, inputType, false));
            }
            if (record != null) {
                // 編集画面に無い項目(Gecko から保存した姓・名など)は消さずに残す
                for (String f : CredentialVault.ADDRESS_FIELDS) {
                    if (!inputs.containsKey(f) && init.containsKey(f)) {
                        inputs.put(f, hiddenValue(activity, init.get(f)));
                    }
                }
            }
        }
        init.clear();

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            scroll.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES);
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton(Lang.t("保存"), null)
                .setNegativeButton(Lang.t("キャンセル"), null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Map<String, String> out = new HashMap<>();
            for (Map.Entry<String, TextInputEditText> e : inputs.entrySet()) {
                CharSequence t = e.getValue().getText();
                out.put(e.getKey(), t == null ? "" : t.toString());
            }
            String error = validate(type, out);
            if (error != null) {
                out.clear();
                Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
                return;
            }
            v.setEnabled(false);
            String guid = record != null ? record.guid : null;
            CredentialVault vault = CredentialVault.get(activity);
            CredentialVault.IO.execute(() -> {
                String saved;
                if (type == CredentialVault.LOGIN) {
                    saved = vault.saveLogin(normalizeOrigin(out.get("origin")),
                            record != null ? record.formActionOrigin : null,
                            record != null ? record.httpRealm : null,
                            out.get("username").trim(), out.get(CredentialVault.F_PASSWORD), guid);
                } else if (type == CredentialVault.CARD) {
                    saved = vault.saveCard(out, guid);
                } else {
                    if (out.containsKey(CredentialVault.F_COUNTRY)) {
                        out.put(CredentialVault.F_COUNTRY, out.get(CredentialVault.F_COUNTRY).trim().toUpperCase(Locale.ROOT));
                    }
                    saved = vault.saveAddress(out, guid);
                }
                out.clear();
                activity.runOnUiThread(() -> {
                    if (saved == null) {
                        v.setEnabled(true);
                        Toast.makeText(activity, Lang.t("保存できませんでした"), Toast.LENGTH_LONG).show();
                        return;
                    }
                    dialog.dismiss();
                    Toast.makeText(activity, Lang.t("保存しました"), Toast.LENGTH_SHORT).show();
                    onSaved.onSaved(saved);
                });
            });
        }));
        dialog.setOnDismissListener(d -> {
            // 入力欄に残った値を消す
            for (TextInputEditText e : inputs.values()) {
                if (e.getText() != null) e.getText().clear();
            }
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                    android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
        dialog.show();
        return dialog;
    }

    @Nullable
    private static String validate(int type, Map<String, String> v) {
        if (type == CredentialVault.LOGIN) {
            if (normalizeOrigin(v.get("origin")) == null) return Lang.t("サイトの URL を正しく入力してください (https://… または http://…)");
            String pw = v.get(CredentialVault.F_PASSWORD);
            if (pw == null || pw.isEmpty()) return Lang.t("パスワードを入力してください");
            return null;
        }
        if (type == CredentialVault.CARD) {
            String n = CredentialVault.digitsOnly(v.get(CredentialVault.F_CC_NUMBER));
            if (!GeckoAutofill.luhn(n)) return Lang.t("カード番号が正しくありません");
            String m = v.getOrDefault(CredentialVault.F_CC_EXP_MONTH, "").trim();
            String y = v.getOrDefault(CredentialVault.F_CC_EXP_YEAR, "").trim();
            if (!m.isEmpty()) {
                int mi = parse(m);
                if (mi < 1 || mi > 12) return Lang.t("有効期限の月は 1〜12 で入力してください");
            }
            if (!y.isEmpty()) {
                int yi = parse(y);
                if (yi < 2000 || yi > 2100) return Lang.t("有効期限の年は 4 桁で入力してください");
            }
            return null;
        }
        for (String s : v.values()) if (s != null && !s.trim().isEmpty()) return null;
        return Lang.t("住所を入力してください");
    }

    /** 入力された URL から origin(https://host[:port])を作る。正しくなければ null。 */
    @Nullable
    static String normalizeOrigin(@Nullable String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty() || s.contains(" ")) return null;
        if (!s.contains("://")) s = "https://" + s;
        try {
            Uri u = Uri.parse(s);
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            String host = u.getHost();
            if (!(scheme.equals("https") || scheme.equals("http")) || host == null || host.isEmpty()) return null;
            String origin = scheme + "://" + host.toLowerCase(Locale.ROOT);
            if (u.getPort() > 0) origin += ":" + u.getPort();
            return origin;
        } catch (Exception e) {
            return null;
        }
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static TextInputEditText field(Activity a, LinearLayout parent, String hint, @Nullable String value,
                                           int inputType, boolean secret) {
        TextInputLayout layout = new TextInputLayout(a, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        layout.setHint(hint);
        if (secret) layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        TextInputEditText et = new TextInputEditText(layout.getContext());
        et.setInputType(inputType);
        et.setSingleLine(true);
        harden(et);
        if (value != null) et.setText(value);
        layout.addView(et);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 8);
        parent.addView(layout, lp);
        return et;
    }

    /** 画面に出さずに値だけ持っておく入力欄(編集画面に無い項目を保存し直すため)。 */
    private static TextInputEditText hiddenValue(Activity a, @Nullable String value) {
        TextInputEditText et = new TextInputEditText(a);
        harden(et);
        et.setVisibility(View.GONE);
        if (value != null) et.setText(value);
        return et;
    }

    /** 値が画面の状態保存・端末の自動入力サービス・キーボードの学習に渡らないようにする。 */
    private static void harden(TextInputEditText et) {
        et.setSaveEnabled(false);
        et.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        et.setImeOptions(et.getImeOptions() | android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
    }

    private static int dp(Activity a, float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
