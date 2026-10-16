package com.airsky.browser;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.Autocomplete;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.PromptDelegate;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Web ページからのダイアログ要求(alert / confirm / prompt / select / ファイル選択 / Basic 認証 など)を
 * Material 3 のダイアログで表示する。WebChromeClient の onJsAlert 等の置き換え。
 */
public class GeckoPromptDelegate implements PromptDelegate {

    private static final String PAGE_MESSAGE_TITLE = Lang.t("このページからのメッセージ");

    private final Activity activity;
    private final int fileChooserRequestCode;
    private final Runnable beforeFileChooser;

    @Nullable private FilePrompt pendingFilePrompt;
    @Nullable private GeckoResult<PromptResponse> pendingFileResult;
    /** フォームの再送信を断った・ページから移動しなかった時の処理(スワイプ更新の読み込み表示を戻す等) */
    @Nullable private Runnable onRepostDenied;

    /**
     * @param beforeFileChooser ファイル選択画面を開く直前に呼ぶ処理(復帰時のロック画面を出さない等)
     */
    public GeckoPromptDelegate(@NonNull Activity activity, int fileChooserRequestCode,
                               @Nullable Runnable beforeFileChooser) {
        this.activity = activity;
        this.fileChooserRequestCode = fileChooserRequestCode;
        this.beforeFileChooser = beforeFileChooser;
    }

    public void setOnRepostDenied(@Nullable Runnable r) {
        this.onRepostDenied = r;
    }

    private void repostDenied(GeckoResult<PromptResponse> result, RepostConfirmPrompt prompt) {
        navigationDenied(result, prompt.confirm(AllowOrDeny.DENY));
    }

    /**
     * 読み込みを取りやめた(再送信を断った・「留まる」を選んだ)。読み込みが始まらないので、
     * スワイプ更新のくるくるや読み込み表示が残らないように知らせる。
     */
    private void navigationDenied(GeckoResult<PromptResponse> result, PromptResponse response) {
        complete(result, response);
        if (onRepostDenied != null) onRepostDenied.run();
    }

    private boolean isUsable() {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    private MaterialAlertDialogBuilder builder(@Nullable String title) {
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(activity);
        b.setTitle(title != null && !title.isEmpty() ? title : PAGE_MESSAGE_TITLE);
        return b;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAlertPrompt(@NonNull GeckoSession session, @NonNull AlertPrompt prompt) {
        if (!isUsable()) return GeckoResult.fromValue(prompt.dismiss());
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        builder(null)
                .setMessage(prompt.message)
                .setPositiveButton(android.R.string.ok, (d, w) -> complete(result, prompt.dismiss()))
                .setOnCancelListener(d -> complete(result, prompt.dismiss()))
                .show();
        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onButtonPrompt(@NonNull GeckoSession session, @NonNull ButtonPrompt prompt) {
        if (!isUsable()) return GeckoResult.fromValue(prompt.dismiss());
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        builder(prompt.title)
                .setMessage(prompt.message)
                .setPositiveButton("OK", (d, w) -> complete(result, prompt.confirm(ButtonPrompt.Type.POSITIVE)))
                .setNegativeButton(Lang.t("キャンセル"), (d, w) -> complete(result, prompt.confirm(ButtonPrompt.Type.NEGATIVE)))
                .setOnCancelListener(d -> complete(result, prompt.dismiss()))
                .show();
        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onTextPrompt(@NonNull GeckoSession session, @NonNull TextPrompt prompt) {
        if (!isUsable()) return GeckoResult.fromValue(prompt.dismiss());
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        EditText input = new EditText(activity);
        input.setText(prompt.defaultValue);
        input.setSingleLine(true);
        builder(prompt.title)
                .setMessage(prompt.message)
                .setView(padded(input))
                .setPositiveButton("OK", (d, w) -> complete(result, prompt.confirm(input.getText().toString())))
                .setNegativeButton(Lang.t("キャンセル"), (d, w) -> complete(result, prompt.dismiss()))
                .setOnCancelListener(d -> complete(result, prompt.dismiss()))
                .show();
        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAuthPrompt(@NonNull GeckoSession session, @NonNull AuthPrompt prompt) {
        if (!isUsable()) return GeckoResult.fromValue(prompt.dismiss());
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        boolean onlyPassword = (prompt.authOptions.flags & AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0;

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        EditText user = new EditText(activity);
        user.setHint(Lang.t("ユーザー名"));
        user.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        if (prompt.authOptions.username != null) user.setText(prompt.authOptions.username);
        EditText pass = new EditText(activity);
        pass.setHint(Lang.t("パスワード"));
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (!onlyPassword) layout.addView(user);
        layout.addView(pass);

        AlertDialog dialog = builder(Lang.t("認証情報を入力"))
                .setMessage(prompt.message)
                .setView(padded(layout))
                .setPositiveButton(Lang.t("ログイン"), (d, w) -> {
                    String p = pass.getText().toString();
                    if (onlyPassword) {
                        complete(result, prompt.confirm(p));
                    } else {
                        complete(result, prompt.confirm(user.getText().toString().trim(), p));
                    }
                })
                .setNegativeButton(Lang.t("キャンセル"), (d, w) -> complete(result, prompt.dismiss()))
                .create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onChoicePrompt(@NonNull GeckoSession session, @NonNull ChoicePrompt prompt) {
        if (!isUsable()) return GeckoResult.fromValue(prompt.dismiss());
        GeckoResult<PromptResponse> result = new GeckoResult<>();

        // optgroup を展開して 1 列に並べる
        List<ChoicePrompt.Choice> flat = new ArrayList<>();
        flatten(prompt.choices, flat);
        List<ChoicePrompt.Choice> selectable = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (ChoicePrompt.Choice c : flat) {
            if (c.separator || c.items != null) continue;
            selectable.add(c);
            labels.add(c.disabled ? c.label + Lang.t("（選択不可）") : c.label);
        }
        String[] items = labels.toArray(new String[0]);

        if (prompt.type == ChoicePrompt.Type.MULTIPLE) {
            boolean[] checked = new boolean[selectable.size()];
            for (int i = 0; i < selectable.size(); i++) checked[i] = selectable.get(i).selected;
            builder(prompt.title)
                    .setMultiChoiceItems(items, checked, (d, which, isChecked) -> {
                        if (selectable.get(which).disabled) {
                            ((AlertDialog) d).getListView().setItemChecked(which, !isChecked);
                            checked[which] = !isChecked;
                        } else {
                            checked[which] = isChecked;
                        }
                    })
                    .setPositiveButton("OK", (d, w) -> {
                        List<ChoicePrompt.Choice> chosen = new ArrayList<>();
                        for (int i = 0; i < checked.length; i++) if (checked[i]) chosen.add(selectable.get(i));
                        complete(result, prompt.confirm(chosen.toArray(new ChoicePrompt.Choice[0])));
                    })
                    .setNegativeButton(Lang.t("キャンセル"), (d, w) -> complete(result, prompt.dismiss()))
                    .setOnCancelListener(d -> complete(result, prompt.dismiss()))
                    .show();
        } else {
            int selected = -1;
            for (int i = 0; i < selectable.size(); i++) if (selectable.get(i).selected) selected = i;
            builder(prompt.title)
                    .setSingleChoiceItems(items, selected, (d, which) -> {
                        ChoicePrompt.Choice c = selectable.get(which);
                        if (c.disabled) return;
                        complete(result, prompt.confirm(c));
                        d.dismiss();
                    })
                    .setOnCancelListener(d -> complete(result, prompt.dismiss()))
                    .show();
        }
        return result;
    }

    private static void flatten(@Nullable ChoicePrompt.Choice[] choices, List<ChoicePrompt.Choice> out) {
        if (choices == null) return;
        for (ChoicePrompt.Choice c : choices) {
            out.add(c);
            if (c.items != null) flatten(c.items, out);
        }
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onColorPrompt(@NonNull GeckoSession session, @NonNull ColorPrompt prompt) {
        if (!isUsable()) return GeckoResult.fromValue(prompt.dismiss());
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        EditText input = new EditText(activity);
        input.setText(prompt.defaultValue != null ? prompt.defaultValue : "#000000");
        input.setSingleLine(true);
        builder(Lang.t("色を入力 (#RRGGBB)"))
                .setView(padded(input))
                .setPositiveButton("OK", (d, w) -> {
                    String v = input.getText().toString().trim();
                    try {
                        Color.parseColor(v);
                        complete(result, prompt.confirm(v.toLowerCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(activity, Lang.t("色の形式が正しくありません"), Toast.LENGTH_SHORT).show();
                        complete(result, prompt.dismiss());
                    }
                })
                .setNegativeButton(Lang.t("キャンセル"), (d, w) -> complete(result, prompt.dismiss()))
                .setOnCancelListener(d -> complete(result, prompt.dismiss()))
                .show();
        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onDateTimePrompt(@NonNull GeckoSession session, @NonNull DateTimePrompt prompt) {
        if (!isUsable()) return GeckoResult.fromValue(prompt.dismiss());
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        Calendar cal = Calendar.getInstance();
        int type = prompt.type;

        if (type == DateTimePrompt.Type.TIME) {
            int h = cal.get(Calendar.HOUR_OF_DAY), m = cal.get(Calendar.MINUTE);
            if (prompt.defaultValue != null && prompt.defaultValue.matches("\\d{2}:\\d{2}.*")) {
                h = Integer.parseInt(prompt.defaultValue.substring(0, 2));
                m = Integer.parseInt(prompt.defaultValue.substring(3, 5));
            }
            TimePickerDialog dlg = new TimePickerDialog(activity, (v, hour, minute) ->
                    complete(result, prompt.confirm(String.format(Locale.ROOT, "%02d:%02d", hour, minute))),
                    h, m, true);
            dlg.setOnCancelListener(d -> complete(result, prompt.dismiss()));
            dlg.show();
            return result;
        }

        int y = cal.get(Calendar.YEAR), mo = cal.get(Calendar.MONTH), d0 = cal.get(Calendar.DAY_OF_MONTH);
        if (prompt.defaultValue != null && prompt.defaultValue.matches("\\d{4}-\\d{2}.*")) {
            y = Integer.parseInt(prompt.defaultValue.substring(0, 4));
            mo = Integer.parseInt(prompt.defaultValue.substring(5, 7)) - 1;
            if (prompt.defaultValue.length() >= 10 && type == DateTimePrompt.Type.DATE) {
                d0 = Integer.parseInt(prompt.defaultValue.substring(8, 10));
            }
        }
        DatePickerDialog dlg = new DatePickerDialog(activity, (v, year, month, day) -> {
            String value;
            if (type == DateTimePrompt.Type.MONTH) {
                value = String.format(Locale.ROOT, "%04d-%02d", year, month + 1);
            } else if (type == DateTimePrompt.Type.WEEK) {
                Calendar c = Calendar.getInstance();
                c.set(year, month, day);
                c.setMinimalDaysInFirstWeek(4);
                c.setFirstDayOfWeek(Calendar.MONDAY);
                value = String.format(Locale.ROOT, "%04d-W%02d", year, c.get(Calendar.WEEK_OF_YEAR));
            } else if (type == DateTimePrompt.Type.DATETIME_LOCAL) {
                value = String.format(Locale.ROOT, "%04d-%02d-%02dT00:00", year, month + 1, day);
            } else {
                value = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day);
            }
            complete(result, prompt.confirm(value));
        }, y, mo, d0);
        dlg.setOnCancelListener(d -> complete(result, prompt.dismiss()));
        dlg.show();
        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onPopupPrompt(@NonNull GeckoSession session, @NonNull PopupPrompt prompt) {
        // 設定の「サイトの設定 › ポップアップとリダイレクト」(既定値とサイトごとの例外)に従う
        String host = pageUrlResolver != null ? SitePermissions.hostOf(pageUrlResolver.urlOf(session)) : null;
        int v = SitePermissions.effective(activity, host, SitePermissions.POPUPS);
        if (v == SitePermissions.ALLOW) return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW));
        if (v == SitePermissions.BLOCK || !isUsable()) {
            if (isUsable()) Toast.makeText(activity, Lang.t("ポップアップをブロックしました"), Toast.LENGTH_SHORT).show();
            return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY));
        }
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        MaterialAlertDialogBuilder b = builder(Lang.t("ポップアップ"))
                .setMessage(Lang.t("このページがポップアップを開こうとしています。\n") +
                        (prompt.targetUri != null ? prompt.targetUri : ""))
                .setPositiveButton(Lang.t("許可"), (d, w) -> complete(result, prompt.confirm(AllowOrDeny.ALLOW)))
                .setNegativeButton(Lang.t("ブロック"), (d, w) -> complete(result, prompt.confirm(AllowOrDeny.DENY)))
                .setOnCancelListener(d -> complete(result, prompt.confirm(AllowOrDeny.DENY)));
        if (host != null) {
            b.setNeutralButton(Lang.t("常に許可"), (d, w) -> {
                SitePermissions.setSite(activity, host, SitePermissions.POPUPS, SitePermissions.ALLOW);
                complete(result, prompt.confirm(AllowOrDeny.ALLOW));
            });
        }
        b.show();
        return result;
    }

    /** ポップアップを開こうとしたページの URL を調べる(サイトごとの設定に使う)。 */
    public interface PageUrlResolver {
        @Nullable
        String urlOf(@NonNull GeckoSession session);
    }

    @Nullable private PageUrlResolver pageUrlResolver;

    public void setPageUrlResolver(@Nullable PageUrlResolver r) {
        pageUrlResolver = r;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onBeforeUnloadPrompt(@NonNull GeckoSession session, @NonNull BeforeUnloadPrompt prompt) {
        if (!isUsable()) return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW));
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        builder(Lang.t("このページから移動しますか？"))
                .setMessage(Lang.t("入力した内容が保存されない可能性があります。"))
                .setPositiveButton(Lang.t("移動する"), (d, w) -> complete(result, prompt.confirm(AllowOrDeny.ALLOW)))
                .setNegativeButton(Lang.t("留まる"), (d, w) -> navigationDenied(result, prompt.confirm(AllowOrDeny.DENY)))
                .setOnCancelListener(d -> navigationDenied(result, prompt.confirm(AllowOrDeny.DENY)))
                .show();
        return result;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onRepostConfirmPrompt(@NonNull GeckoSession session, @NonNull RepostConfirmPrompt prompt) {
        if (!isUsable()) {
            if (onRepostDenied != null) onRepostDenied.run();
            return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY));
        }
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        builder(Lang.t("フォームを再送信しますか？"))
                .setMessage(Lang.t("このページを再読み込みすると、入力内容がもう一度送信されます。"))
                .setPositiveButton(Lang.t("再送信"), (d, w) -> complete(result, prompt.confirm(AllowOrDeny.ALLOW)))
                .setNegativeButton(Lang.t("キャンセル"), (d, w) -> repostDenied(result, prompt))
                .setOnCancelListener(d -> repostDenied(result, prompt))
                .show();
        return result;
    }

    // ---- 自動入力(パスワード・カード・住所) ----
    // 保存した情報は本人確認をしてから入力する。詳しくは GeckoAutofill を参照。

    @Nullable
    private String pageUrlOf(@NonNull GeckoSession session) {
        return pageUrlResolver != null ? pageUrlResolver.urlOf(session) : null;
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onLoginSelect(@NonNull GeckoSession session,
            @NonNull AutocompleteRequest<Autocomplete.LoginSelectOption> request) {
        return GeckoAutofill.onLoginSelect(activity, session, request, pageUrlOf(session));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onLoginSave(@NonNull GeckoSession session,
            @NonNull AutocompleteRequest<Autocomplete.LoginSaveOption> request) {
        return GeckoAutofill.onLoginSave(activity, session, request);
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onCreditCardSelect(@NonNull GeckoSession session,
            @NonNull AutocompleteRequest<Autocomplete.CreditCardSelectOption> request) {
        return GeckoAutofill.onCreditCardSelect(activity, session, request, pageUrlOf(session));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onCreditCardSave(@NonNull GeckoSession session,
            @NonNull AutocompleteRequest<Autocomplete.CreditCardSaveOption> request) {
        return GeckoAutofill.onCreditCardSave(activity, session, request);
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAddressSelect(@NonNull GeckoSession session,
            @NonNull AutocompleteRequest<Autocomplete.AddressSelectOption> request) {
        return GeckoAutofill.onAddressSelect(activity, session, request, pageUrlOf(session));
    }

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onAddressSave(@NonNull GeckoSession session,
            @NonNull AutocompleteRequest<Autocomplete.AddressSaveOption> request) {
        return GeckoAutofill.onAddressSave(activity, session, request);
    }

    // ---- ファイル選択 ----

    @Nullable
    @Override
    public GeckoResult<PromptResponse> onFilePrompt(@NonNull GeckoSession session, @NonNull FilePrompt prompt) {
        if (!isUsable()) return GeckoResult.fromValue(prompt.dismiss());
        // 前回の選択が残っていたら閉じておく
        if (pendingFilePrompt != null && pendingFileResult != null) {
            complete(pendingFileResult, pendingFilePrompt.dismiss());
        }

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String[] mimes = prompt.mimeTypes;
        if (mimes != null && mimes.length == 1 && mimes[0] != null && !mimes[0].isEmpty()) {
            intent.setType(mimes[0]);
        } else {
            intent.setType("*/*");
            if (mimes != null && mimes.length > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
        }
        if (prompt.type == FilePrompt.Type.MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        GeckoResult<PromptResponse> result = new GeckoResult<>();
        pendingFilePrompt = prompt;
        pendingFileResult = result;
        if (beforeFileChooser != null) beforeFileChooser.run();
        try {
            activity.startActivityForResult(Intent.createChooser(intent, Lang.t("ファイルを選択")), fileChooserRequestCode);
        } catch (Exception e) {
            pendingFilePrompt = null;
            pendingFileResult = null;
            return GeckoResult.fromValue(prompt.dismiss());
        }
        return result;
    }

    /** Activity#onActivityResult から呼ぶ。ファイル選択の結果なら true。 */
    public boolean onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode != fileChooserRequestCode) return false;
        FilePrompt prompt = pendingFilePrompt;
        GeckoResult<PromptResponse> result = pendingFileResult;
        pendingFilePrompt = null;
        pendingFileResult = null;
        if (prompt == null || result == null) return true;

        if (resultCode != Activity.RESULT_OK || data == null) {
            complete(result, prompt.dismiss());
            return true;
        }
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null) uris.add(u);
            }
        }
        if (uris.isEmpty() && data.getData() != null) uris.add(data.getData());

        try {
            if (uris.isEmpty()) {
                complete(result, prompt.dismiss());
            } else if (prompt.type == FilePrompt.Type.MULTIPLE) {
                complete(result, prompt.confirm(activity, uris.toArray(new Uri[0])));
            } else {
                complete(result, prompt.confirm(activity, uris.get(0)));
            }
        } catch (Exception e) {
            Toast.makeText(activity, Lang.t("ファイルを読み込めませんでした"), Toast.LENGTH_SHORT).show();
            complete(result, prompt.dismiss());
        }
        return true;
    }

    // ----

    private static void complete(GeckoResult<PromptResponse> result, PromptResponse response) {
        try {
            result.complete(response);
        } catch (IllegalStateException ignored) {
            // すでに応答済み(二重タップなど)
        }
    }

    private View padded(View child) {
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(child, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return wrap;
    }
}
