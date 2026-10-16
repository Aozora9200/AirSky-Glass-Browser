package com.airsky.browser;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.mozilla.geckoview.Autocomplete;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.AutocompleteRequest;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GeckoView の自動入力(ログイン・カード・住所)を {@link CredentialVault} につなぐ。
 *
 * <h3>本物の値は本人確認の後にしか Gecko へ渡さない</h3>
 * <p>Gecko はフォームを見つけるたびに保存した情報を問い合わせてくる({@link Storage})。
 * ここでは<b>中身を伏せたダミー</b>(パスワード・カード番号・住所などは偽の値)だけを返す。
 * ユーザーが入力欄をタップして候補を選ぶと Gecko から選択の依頼が来る({@link #onLoginSelect} など)ので、
 * そこで本人確認をしてから本物の値を復号し、選ばれた候補として返す。Gecko はその値でフォームを埋める。</p>
 *
 * <p>ページを開いただけで自動的に埋める Gecko の機能(signon.autofillForms)は切っている
 * ({@link #install})。ダミーが入力されたり、本人確認なしで入力されたりしないようにするため。</p>
 */
final class GeckoAutofill {

    private GeckoAutofill() {
    }

    /** ダミーのパスワード。レコードごとに別の値にする(Gecko が同じログインとしてまとめないように)。 */
    private static final Map<String, String> PLACEHOLDER_PASSWORDS = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    static void install(@NonNull Context context, @NonNull GeckoRuntime runtime) {
        // ページを開いた時に自動で埋めない(必ず候補を選ぶ → 本人確認の流れにする)
        runtime.getSettings().setLoginAutofillEnabled(false);
        runtime.setAutocompleteStorageDelegate(new Storage(context.getApplicationContext()));
    }

    // ==================================================================
    // 保存した情報の問い合わせ(ダミーだけを返す)
    // ==================================================================

    private static final class Storage implements Autocomplete.StorageDelegate {
        private final Context app;
        private final Handler main = new Handler(Looper.getMainLooper());

        Storage(Context app) {
            this.app = app;
        }

        private <T> GeckoResult<T> async(java.util.concurrent.Callable<T> work, T empty) {
            GeckoResult<T> result = new GeckoResult<>();
            CredentialVault.IO.execute(() -> {
                T value;
                try {
                    value = work.call();
                } catch (Exception e) {
                    value = empty;
                }
                T v = value;
                main.post(() -> result.complete(v));
            });
            return result;
        }

        @Nullable
        @Override
        public GeckoResult<Autocomplete.LoginEntry[]> onLoginFetch(@NonNull String domain) {
            return fetchLogins(domain);
        }

        @Nullable
        @Override
        public GeckoResult<Autocomplete.LoginEntry[]> onLoginFetch() {
            return fetchLogins(null);
        }

        private GeckoResult<Autocomplete.LoginEntry[]> fetchLogins(@Nullable String domain) {
            if (!CredentialVault.isActive(app, CredentialVault.LOGIN)) return null;
            return async(() -> {
                List<Autocomplete.LoginEntry> out = new ArrayList<>();
                for (CredentialVault.Record r : CredentialVault.get(app).loginsForDomain(domain)) {
                    out.add(placeholderLogin(r));
                }
                return out.toArray(new Autocomplete.LoginEntry[0]);
            }, new Autocomplete.LoginEntry[0]);
        }

        @Nullable
        @Override
        public GeckoResult<Autocomplete.CreditCard[]> onCreditCardFetch() {
            if (!CredentialVault.isActive(app, CredentialVault.CARD)) return null;
            return async(() -> {
                List<Autocomplete.CreditCard> out = new ArrayList<>();
                for (CredentialVault.Record r : CredentialVault.get(app).list(CredentialVault.CARD)) {
                    out.add(placeholderCard(r));
                }
                return out.toArray(new Autocomplete.CreditCard[0]);
            }, new Autocomplete.CreditCard[0]);
        }

        @Nullable
        @Override
        public GeckoResult<Autocomplete.Address[]> onAddressFetch() {
            if (!CredentialVault.isActive(app, CredentialVault.ADDRESS)) return null;
            return async(() -> {
                List<Autocomplete.Address> out = new ArrayList<>();
                for (CredentialVault.Record r : CredentialVault.get(app).list(CredentialVault.ADDRESS)) {
                    out.add(placeholderAddress(r));
                }
                return out.toArray(new Autocomplete.Address[0]);
            }, new Autocomplete.Address[0]);
        }

        // 保存は、ユーザーが保存ダイアログで「保存」を押した時に GeckoAutofill 側で行う。
        // Gecko から直接来る保存(生成したパスワードの仮保存など)は受け付けない。
        @Override
        public void onLoginSave(@NonNull Autocomplete.LoginEntry login) {
        }

        @Override
        public void onCreditCardSave(@NonNull Autocomplete.CreditCard creditCard) {
        }

        @Override
        public void onAddressSave(@NonNull Autocomplete.Address address) {
        }

        @Override
        public void onLoginUsed(@NonNull Autocomplete.LoginEntry login, int usedFields) {
            String guid = login.guid;
            if (guid == null) return;
            CredentialVault.IO.execute(() -> CredentialVault.get(app).markUsed(guid));
        }
    }

    private static Autocomplete.LoginEntry placeholderLogin(CredentialVault.Record r) {
        String pw = PLACEHOLDER_PASSWORDS.get(r.guid);
        if (pw == null) {
            byte[] b = new byte[18];
            RANDOM.nextBytes(b);
            pw = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE);
            PLACEHOLDER_PASSWORDS.put(r.guid, pw);
        }
        return new Autocomplete.LoginEntry.Builder()
                .guid(r.guid)
                .origin(r.origin)
                .formActionOrigin(r.formActionOrigin)
                .httpRealm(r.httpRealm)
                .username(Mask.username(r.username))
                .password(pw)
                .build();
    }

    private static final String PH = "••••";

    private static Autocomplete.CreditCard placeholderCard(CredentialVault.Record r) {
        Autocomplete.CreditCard.Builder b = new Autocomplete.CreditCard.Builder()
                .guid(r.guid)
                // 下 4 桁だけ本物(候補の表示用)。それ以外は 0
                .number("000000000000" + (r.last4.length() == 4 ? r.last4 : "0000"));
        if (r.fields.contains(CredentialVault.F_CC_NAME)) b.name(PH);
        if (r.fields.contains(CredentialVault.F_CC_EXP_MONTH)) b.expirationMonth("1");
        if (r.fields.contains(CredentialVault.F_CC_EXP_YEAR)) b.expirationYear("2099");
        return b.build();
    }

    private static Autocomplete.Address placeholderAddress(CredentialVault.Record r) {
        Autocomplete.Address.Builder b = new Autocomplete.Address.Builder().guid(r.guid);
        boolean anyName = r.fields.contains(CredentialVault.F_NAME) || r.fields.contains(CredentialVault.F_GIVEN_NAME)
                || r.fields.contains(CredentialVault.F_FAMILY_NAME);
        if (anyName) b.name(PH);
        if (r.fields.contains(CredentialVault.F_GIVEN_NAME)) b.givenName(PH);
        if (r.fields.contains(CredentialVault.F_ADDITIONAL_NAME)) b.additionalName(PH);
        if (r.fields.contains(CredentialVault.F_FAMILY_NAME)) b.familyName(PH);
        if (r.fields.contains(CredentialVault.F_ORGANIZATION)) b.organization(PH);
        if (r.fields.contains(CredentialVault.F_STREET)) b.streetAddress(PH);
        if (r.fields.contains(CredentialVault.F_LEVEL1)) b.addressLevel1(PH);
        if (r.fields.contains(CredentialVault.F_LEVEL2)) b.addressLevel2(PH);
        if (r.fields.contains(CredentialVault.F_LEVEL3)) b.addressLevel3(PH);
        if (r.fields.contains(CredentialVault.F_POSTAL)) b.postalCode("0000000");
        // 国は Gecko が「この国の住所に対応しているか」の判定に使うので本物を渡す(秘密ではない)
        if (!r.country.isEmpty()) b.country(r.country);
        if (r.fields.contains(CredentialVault.F_TEL)) b.tel("0000000000");
        if (r.fields.contains(CredentialVault.F_EMAIL)) b.email("hidden@example.invalid");
        return b.build();
    }

    // ==================================================================
    // 候補の選択(本人確認 → 本物の値を返す)
    // ==================================================================

    static GeckoResult<PromptResponse> onLoginSelect(@NonNull Activity activity, @NonNull GeckoSession session,
                                                     @NonNull AutocompleteRequest<Autocomplete.LoginSelectOption> request,
                                                     @Nullable String pageUrl) {
        if (!CredentialVault.isActive(activity, CredentialVault.LOGIN) || !usable(activity)) {
            return GeckoResult.fromValue(request.dismiss());
        }
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        CredentialVault vault = CredentialVault.get(activity);
        CredentialVault.IO.execute(() -> {
            List<Autocomplete.LoginSelectOption> options = new ArrayList<>();
            List<CredentialVault.Record> recs = new ArrayList<>();
            boolean insecure = false;
            for (Autocomplete.LoginSelectOption o : request.options) {
                if (o == null || o.value == null) continue;
                if ((o.hint & Autocomplete.SelectOption.Hint.INSECURE_FORM) != 0) insecure = true;
                if ((o.hint & Autocomplete.SelectOption.Hint.FIREFOX_RELAY) != 0) continue;
                if ((o.hint & Autocomplete.SelectOption.Hint.GENERATED) != 0) {
                    options.add(o);
                    recs.add(null);
                    continue;
                }
                CredentialVault.Record r = vault.find(o.value.guid);
                // Gecko がこのページ用に選んだ候補で、保存したサイトとも一致するものだけ
                if (r == null || r.type != CredentialVault.LOGIN || !r.origin.equals(o.value.origin)) continue;
                options.add(o);
                recs.add(r);
            }
            boolean insecureForm = insecure;
            activity.runOnUiThread(() -> {
                if (options.isEmpty() || !usable(activity)) {
                    complete(result, request.dismiss());
                    return;
                }
                showLoginChooser(activity, request, result, options, recs, pageUrl, insecureForm);
            });
        });
        return result;
    }

    private static void showLoginChooser(Activity activity, AutocompleteRequest<Autocomplete.LoginSelectOption> request,
                                         GeckoResult<PromptResponse> result, List<Autocomplete.LoginSelectOption> options,
                                         List<CredentialVault.Record> recs, @Nullable String pageUrl, boolean insecure) {
        String host = siteLabel(pageUrl, recs);
        String[] labels = new String[options.size()];
        for (int i = 0; i < labels.length; i++) {
            CredentialVault.Record r = recs.get(i);
            // 開いているページと違うサイト(同じドメインの別のサブドメインなど)で保存したものは目立たせる
            String site = r == null || r.host().equals(host) ? (r == null ? "" : r.host()) : Lang.t("⚠ 別のサイト: ") + r.host();
            labels[i] = r == null ? Lang.t("安全なパスワードを生成して使用")
                    : Mask.username(r.username) + "\n" + site + "  " + Mask.hidden();
        }
        // AlertDialog は本文(setMessage)と一覧(setItems)を同時に出せないので、警告は見出しに入れる
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(activity)
                .setTitle(insecure ? Lang.t("⚠ 安全でない接続です\n") + host + Lang.t(" のパスワード")
                        : host + Lang.t(" のパスワード"))
                .setItems(labels, (d, which) -> {
                    Autocomplete.LoginSelectOption o = options.get(which);
                    CredentialVault.Record r = recs.get(which);
                    if (r == null) {
                        // Gecko が作ったパスワード(保存済みの情報ではないので本人確認は不要)
                        complete(result, request.confirm(o));
                        return;
                    }
                    VaultAuth.unseal(activity, r, Lang.t("パスワードを入力"), host, new VaultAuth.UnsealCallback() {
                        @Override
                        public void onUnsealed(@NonNull Map<String, String> values) {
                            String pw = values.get(CredentialVault.F_PASSWORD);
                            values.clear();
                            if (TextUtils.isEmpty(pw)) {
                                complete(result, request.dismiss());
                                return;
                            }
                            Autocomplete.LoginEntry real = new Autocomplete.LoginEntry.Builder()
                                    .guid(r.guid)
                                    .origin(o.value.origin)
                                    .formActionOrigin(o.value.formActionOrigin)
                                    .httpRealm(o.value.httpRealm)
                                    .username(r.username)
                                    .password(pw)
                                    .build();
                            complete(result, request.confirm(new Autocomplete.LoginSelectOption(real, o.hint)));
                            CredentialVault.IO.execute(() -> CredentialVault.get(activity).markUsed(r.guid));
                        }

                        @Override
                        public void onFailed(@NonNull CharSequence message, boolean canceled) {
                            complete(result, request.dismiss());
                            if (!canceled) toast(activity, message);
                        }
                    });
                })
                .setNegativeButton(Lang.t("キャンセル"), (d, w) -> complete(result, request.dismiss()))
                .setOnCancelListener(d -> complete(result, request.dismiss()));
        showSecure(b);
    }

    static GeckoResult<PromptResponse> onCreditCardSelect(@NonNull Activity activity, @NonNull GeckoSession session,
                                                          @NonNull AutocompleteRequest<Autocomplete.CreditCardSelectOption> request,
                                                          @Nullable String pageUrl) {
        if (!CredentialVault.isActive(activity, CredentialVault.CARD) || !usable(activity)) {
            return GeckoResult.fromValue(request.dismiss());
        }
        for (Autocomplete.CreditCardSelectOption o : request.options) {
            if (o != null && (o.hint & Autocomplete.SelectOption.Hint.INSECURE_FORM) != 0) {
                // 暗号化されていないページにはカード情報を入力しない
                toast(activity, Lang.t("安全でない接続のページには、カード情報を自動入力できません"));
                return GeckoResult.fromValue(request.dismiss());
            }
        }
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        chooseRecords(activity, request.options, CredentialVault.CARD, (options, recs) -> {
            String[] labels = new String[recs.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = Mask.card(recs.get(i)) + Lang.t("\n有効期限 ••/••");
            showSecure(new MaterialAlertDialogBuilder(activity)
                    .setTitle(Lang.t("保存したカードを使用"))
                    .setItems(labels, (d, which) -> {
                        CredentialVault.Record r = recs.get(which);
                        VaultAuth.unseal(activity, r, Lang.t("カード情報を入力"), siteLabel(pageUrl, null),
                                new VaultAuth.UnsealCallback() {
                                    @Override
                                    public void onUnsealed(@NonNull Map<String, String> v) {
                                        Autocomplete.CreditCard real = new Autocomplete.CreditCard.Builder()
                                                .guid(r.guid)
                                                .name(v.getOrDefault(CredentialVault.F_CC_NAME, ""))
                                                .number(v.getOrDefault(CredentialVault.F_CC_NUMBER, ""))
                                                .expirationMonth(v.getOrDefault(CredentialVault.F_CC_EXP_MONTH, ""))
                                                .expirationYear(v.getOrDefault(CredentialVault.F_CC_EXP_YEAR, ""))
                                                .build();
                                        v.clear();
                                        complete(result, request.confirm(new Autocomplete.CreditCardSelectOption(real)));
                                        CredentialVault.IO.execute(() -> CredentialVault.get(activity).markUsed(r.guid));
                                    }

                                    @Override
                                    public void onFailed(@NonNull CharSequence message, boolean canceled) {
                                        complete(result, request.dismiss());
                                        if (!canceled) toast(activity, message);
                                    }
                                });
                    })
                    .setNegativeButton(Lang.t("キャンセル"), (d, w) -> complete(result, request.dismiss()))
                    .setOnCancelListener(d -> complete(result, request.dismiss())));
        }, () -> complete(result, request.dismiss()));
        return result;
    }

    static GeckoResult<PromptResponse> onAddressSelect(@NonNull Activity activity, @NonNull GeckoSession session,
                                                       @NonNull AutocompleteRequest<Autocomplete.AddressSelectOption> request,
                                                       @Nullable String pageUrl) {
        if (!CredentialVault.isActive(activity, CredentialVault.ADDRESS) || !usable(activity)) {
            return GeckoResult.fromValue(request.dismiss());
        }
        boolean insecure = false;
        for (Autocomplete.AddressSelectOption o : request.options) {
            if (o != null && (o.hint & Autocomplete.SelectOption.Hint.INSECURE_FORM) != 0) insecure = true;
        }
        boolean insecureForm = insecure;
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        chooseRecords(activity, request.options, CredentialVault.ADDRESS, (options, recs) -> {
            String[] labels = new String[recs.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = Mask.address(recs.get(i)) + Lang.t("\n〒•••-•••• ") + Mask.hidden();
            MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(activity)
                    .setTitle(insecureForm ? Lang.t("⚠ 安全でない接続です\n保存した住所を使用") : Lang.t("保存した住所を使用"))
                    .setItems(labels, (d, which) -> {
                        CredentialVault.Record r = recs.get(which);
                        VaultAuth.unseal(activity, r, Lang.t("住所を入力"), siteLabel(pageUrl, null),
                                new VaultAuth.UnsealCallback() {
                                    @Override
                                    public void onUnsealed(@NonNull Map<String, String> v) {
                                        Autocomplete.Address real = new Autocomplete.Address.Builder()
                                                .guid(r.guid)
                                                .name(v.getOrDefault(CredentialVault.F_NAME, ""))
                                                .givenName(v.getOrDefault(CredentialVault.F_GIVEN_NAME, ""))
                                                .additionalName(v.getOrDefault(CredentialVault.F_ADDITIONAL_NAME, ""))
                                                .familyName(v.getOrDefault(CredentialVault.F_FAMILY_NAME, ""))
                                                .organization(v.getOrDefault(CredentialVault.F_ORGANIZATION, ""))
                                                .streetAddress(v.getOrDefault(CredentialVault.F_STREET, ""))
                                                .addressLevel1(v.getOrDefault(CredentialVault.F_LEVEL1, ""))
                                                .addressLevel2(v.getOrDefault(CredentialVault.F_LEVEL2, ""))
                                                .addressLevel3(v.getOrDefault(CredentialVault.F_LEVEL3, ""))
                                                .postalCode(v.getOrDefault(CredentialVault.F_POSTAL, ""))
                                                .country(v.getOrDefault(CredentialVault.F_COUNTRY, ""))
                                                .tel(v.getOrDefault(CredentialVault.F_TEL, ""))
                                                .email(v.getOrDefault(CredentialVault.F_EMAIL, ""))
                                                .build();
                                        v.clear();
                                        complete(result, request.confirm(new Autocomplete.AddressSelectOption(real)));
                                        CredentialVault.IO.execute(() -> CredentialVault.get(activity).markUsed(r.guid));
                                    }

                                    @Override
                                    public void onFailed(@NonNull CharSequence message, boolean canceled) {
                                        complete(result, request.dismiss());
                                        if (!canceled) toast(activity, message);
                                    }
                                });
                    })
                    .setNegativeButton(Lang.t("キャンセル"), (d, w) -> complete(result, request.dismiss()))
                    .setOnCancelListener(d -> complete(result, request.dismiss()));
            showSecure(b);
        }, () -> complete(result, request.dismiss()));
        return result;
    }

    private interface Chosen<O> {
        void show(List<O> options, List<CredentialVault.Record> recs);
    }

    /** Gecko の候補(ダミー)を、保存庫のレコードに結び付ける。 */
    private static <O extends Autocomplete.Option<?>> void chooseRecords(Activity activity, O[] in, int type,
                                                                         Chosen<O> show, Runnable none) {
        CredentialVault vault = CredentialVault.get(activity);
        CredentialVault.IO.execute(() -> {
            List<O> options = new ArrayList<>();
            List<CredentialVault.Record> recs = new ArrayList<>();
            List<String> seen = new ArrayList<>();
            for (O o : in) {
                if (o == null || o.value == null) continue;
                String guid = guidOf(o.value);
                if (guid == null || seen.contains(guid)) continue;
                CredentialVault.Record r = vault.find(guid);
                if (r == null || r.type != type) continue;
                seen.add(guid);
                options.add(o);
                recs.add(r);
            }
            activity.runOnUiThread(() -> {
                if (recs.isEmpty() || !usable(activity)) {
                    none.run();
                } else {
                    show.show(options, recs);
                }
            });
        });
    }

    @Nullable
    private static String guidOf(Object v) {
        if (v instanceof Autocomplete.CreditCard) return ((Autocomplete.CreditCard) v).guid;
        if (v instanceof Autocomplete.Address) return ((Autocomplete.Address) v).guid;
        if (v instanceof Autocomplete.LoginEntry) return ((Autocomplete.LoginEntry) v).guid;
        return null;
    }

    // ==================================================================
    // 保存の確認
    // ==================================================================

    static GeckoResult<PromptResponse> onLoginSave(@NonNull Activity activity, @NonNull GeckoSession session,
                                                   @NonNull AutocompleteRequest<Autocomplete.LoginSaveOption> request) {
        if (!CredentialVault.isActive(activity, CredentialVault.LOGIN) || !usable(activity) || isPrivate(session)
                || request.options.length == 0 || request.options[0] == null || request.options[0].value == null) {
            return GeckoResult.fromValue(request.dismiss());
        }
        Autocomplete.LoginSaveOption option = request.options[0];
        Autocomplete.LoginEntry entry = option.value;
        String origin = entry.origin;
        String password = entry.password;
        String typedUsername = entry.username != null ? entry.username : "";
        if (TextUtils.isEmpty(origin) || TextUtils.isEmpty(password) || PLACEHOLDER_PASSWORDS.containsValue(password)) {
            // ダミーのパスワードは保存しない
            return GeckoResult.fromValue(request.dismiss());
        }
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        CredentialVault vault = CredentialVault.get(activity);
        CredentialVault.IO.execute(() -> {
            if (vault.isNeverSave(origin) || vault.isBroken()) {
                activity.runOnUiThread(() -> complete(result, request.dismiss()));
                return;
            }
            // パスワード変更のフォームなどでは、Gecko は保存済みのログイン(ユーザー名は伏せ字のダミー)を元に
            // 依頼してくるので、guid から本物のユーザー名に戻す
            CredentialVault.Record byGuid = vault.find(entry.guid);
            String username = typedUsername;
            if (byGuid != null && byGuid.type == CredentialVault.LOGIN && byGuid.origin.equals(origin)
                    && (typedUsername.isEmpty() || typedUsername.equals(Mask.username(byGuid.username)))) {
                username = byGuid.username;
            }
            String user = username;
            CredentialVault.Record existing = vault.findLogin(origin, user, entry.httpRealm);
            if (existing != null && vault.passwordMatches(existing, password)) {
                // 保存済みのパスワードでログインしただけ
                vault.markUsed(existing.guid);
                activity.runOnUiThread(() -> complete(result, request.dismiss()));
                return;
            }
            activity.runOnUiThread(() -> {
                if (!usable(activity)) {
                    complete(result, request.dismiss());
                    return;
                }
                String host = CredentialVault.hostOf(origin);
                String message = host + Lang.t("\nユーザー名: ") + Mask.username(user)
                        + Lang.t("\nパスワード: ") + Mask.hidden()
                        + Lang.t("\n\nパスワードは暗号化して保存され、使用する時に本人確認が必要です。");
                showSecure(new MaterialAlertDialogBuilder(activity)
                        .setTitle(existing != null ? Lang.t("パスワードを更新しますか？") : Lang.t("パスワードを保存しますか？"))
                        .setMessage(message)
                        .setPositiveButton(existing != null ? Lang.t("更新") : Lang.t("保存"), (d, w) -> {
                            complete(result, request.dismiss());
                            CredentialVault.IO.execute(() -> {
                                String guid = vault.saveLogin(origin, entry.formActionOrigin, entry.httpRealm,
                                        user, password, existing != null ? existing.guid : null);
                                activity.runOnUiThread(() -> toast(activity,
                                        guid != null ? Lang.t("パスワードを保存しました") : Lang.t("パスワードを保存できませんでした")));
                            });
                        })
                        .setNegativeButton(Lang.t("今回はしない"), (d, w) -> complete(result, request.dismiss()))
                        .setNeutralButton(Lang.t("このサイトでは保存しない"), (d, w) -> {
                            complete(result, request.dismiss());
                            CredentialVault.IO.execute(() -> vault.addNeverSave(origin));
                        })
                        .setOnCancelListener(d -> complete(result, request.dismiss())));
            });
        });
        return result;
    }

    static GeckoResult<PromptResponse> onCreditCardSave(@NonNull Activity activity, @NonNull GeckoSession session,
                                                        @NonNull AutocompleteRequest<Autocomplete.CreditCardSaveOption> request) {
        if (!CredentialVault.isActive(activity, CredentialVault.CARD) || !usable(activity) || isPrivate(session)
                || request.options.length == 0 || request.options[0] == null || request.options[0].value == null) {
            return GeckoResult.fromValue(request.dismiss());
        }
        Autocomplete.CreditCard card = request.options[0].value;
        Map<String, String> v = new HashMap<>();
        put(v, CredentialVault.F_CC_NAME, card.name);
        put(v, CredentialVault.F_CC_NUMBER, CredentialVault.digitsOnly(card.number));
        put(v, CredentialVault.F_CC_EXP_MONTH, card.expirationMonth);
        put(v, CredentialVault.F_CC_EXP_YEAR, card.expirationYear);
        // 更新の時、Gecko は保存済みのカード(ダミー)とフォームの値を混ぜてくるので、ダミーの値は取り除く
        java.util.Set<String> dropped = dropCardPlaceholders(v);
        String number = v.getOrDefault(CredentialVault.F_CC_NUMBER, "");
        if (!luhn(number)) {
            v.clear();
            return GeckoResult.fromValue(request.dismiss());
        }

        GeckoResult<PromptResponse> result = new GeckoResult<>();
        CredentialVault vault = CredentialVault.get(activity);
        CredentialVault.IO.execute(() -> {
            if (vault.isBroken() || vault.findSameContent(CredentialVault.CARD, v) != null) {
                v.clear();
                activity.runOnUiThread(() -> complete(result, request.dismiss()));
                return;
            }
            CredentialVault.Record existing = vault.findCardByNumber(number);
            if (existing != null && vault.fieldsMatch(existing, v)) {
                // 保存済みのカードで支払っただけ(フォームに無い項目は比べない)
                v.clear();
                activity.runOnUiThread(() -> complete(result, request.dismiss()));
                return;
            }
            activity.runOnUiThread(() -> {
                if (!usable(activity)) {
                    v.clear();
                    complete(result, request.dismiss());
                    return;
                }
                String label = Mask.cardBrand(number);
                String message = (label.isEmpty() ? Lang.t("カード") : label) + " •••• " + number.substring(number.length() - 4)
                        + Lang.t("\n\nカード情報は暗号化して保存され、使用する時に本人確認が必要です。"
                        + "\nセキュリティ コードは保存しません。");
                showSecure(new MaterialAlertDialogBuilder(activity)
                        .setTitle(existing != null ? Lang.t("カード情報を更新しますか？") : Lang.t("カードを保存しますか？"))
                        .setMessage(message)
                        .setPositiveButton(existing != null ? Lang.t("更新") : Lang.t("保存"), (d, w) -> {
                            complete(result, request.dismiss());
                            saveMerged(activity, existing, v, dropped, Lang.t("カード情報を更新"),
                                    values -> vault.saveCard(values, existing != null ? existing.guid : null),
                                    Lang.t("カードを保存しました"), Lang.t("カードを保存できませんでした"));
                        })
                        .setNegativeButton(Lang.t("保存しない"), (d, w) -> {
                            v.clear();
                            complete(result, request.dismiss());
                        })
                        .setOnCancelListener(d -> {
                            v.clear();
                            complete(result, request.dismiss());
                        }));
            });
        });
        return result;
    }

    static GeckoResult<PromptResponse> onAddressSave(@NonNull Activity activity, @NonNull GeckoSession session,
                                                     @NonNull AutocompleteRequest<Autocomplete.AddressSaveOption> request) {
        if (!CredentialVault.isActive(activity, CredentialVault.ADDRESS) || !usable(activity) || isPrivate(session)
                || request.options.length == 0 || request.options[0] == null || request.options[0].value == null) {
            return GeckoResult.fromValue(request.dismiss());
        }
        Autocomplete.Address a = request.options[0].value;
        Map<String, String> v = addressValues(a);
        java.util.Set<String> dropped = dropAddressPlaceholders(v);
        if (v.isEmpty()) return GeckoResult.fromValue(request.dismiss());
        // 既存の住所を書き換える依頼(guid 付き)なら、その住所を更新する
        String targetGuid = a.guid;

        GeckoResult<PromptResponse> result = new GeckoResult<>();
        CredentialVault vault = CredentialVault.get(activity);
        CredentialVault.IO.execute(() -> {
            if (vault.isBroken() || vault.findSameContent(CredentialVault.ADDRESS, v) != null) {
                v.clear();
                activity.runOnUiThread(() -> complete(result, request.dismiss()));
                return;
            }
            CredentialVault.Record existing = vault.find(targetGuid);
            boolean update = existing != null && existing.type == CredentialVault.ADDRESS;
            if (update && vault.fieldsMatch(existing, v)) {
                v.clear();
                activity.runOnUiThread(() -> complete(result, request.dismiss()));
                return;
            }
            activity.runOnUiThread(() -> {
                if (!usable(activity)) {
                    v.clear();
                    complete(result, request.dismiss());
                    return;
                }
                showSecure(new MaterialAlertDialogBuilder(activity)
                        .setTitle(update ? Lang.t("住所を更新しますか？") : Lang.t("住所を保存しますか？"))
                        .setMessage(Lang.t("入力した住所を暗号化して保存します。使用する時に本人確認が必要です。"))
                        .setPositiveButton(update ? Lang.t("更新") : Lang.t("保存"), (d, w) -> {
                            complete(result, request.dismiss());
                            saveMerged(activity, update ? existing : null, v, dropped, Lang.t("住所を更新"),
                                    values -> vault.saveAddress(values, update ? targetGuid : null),
                                    Lang.t("住所を保存しました"), Lang.t("住所を保存できませんでした"));
                        })
                        .setNegativeButton(Lang.t("保存しない"), (d, w) -> {
                            v.clear();
                            complete(result, request.dismiss());
                        })
                        .setOnCancelListener(d -> {
                            v.clear();
                            complete(result, request.dismiss());
                        }));
            });
        });
        return result;
    }

    static Map<String, String> addressValues(Autocomplete.Address a) {
        Map<String, String> v = new HashMap<>();
        put(v, CredentialVault.F_NAME, a.name);
        put(v, CredentialVault.F_GIVEN_NAME, a.givenName);
        put(v, CredentialVault.F_ADDITIONAL_NAME, a.additionalName);
        put(v, CredentialVault.F_FAMILY_NAME, a.familyName);
        put(v, CredentialVault.F_ORGANIZATION, a.organization);
        put(v, CredentialVault.F_STREET, a.streetAddress);
        put(v, CredentialVault.F_LEVEL1, a.addressLevel1);
        put(v, CredentialVault.F_LEVEL2, a.addressLevel2);
        put(v, CredentialVault.F_LEVEL3, a.addressLevel3);
        put(v, CredentialVault.F_POSTAL, a.postalCode);
        put(v, CredentialVault.F_COUNTRY, a.country);
        put(v, CredentialVault.F_TEL, a.tel);
        put(v, CredentialVault.F_EMAIL, a.email);
        return v;
    }

    // ---- ダミーの値の取り除きと、保存済みの値との組み合わせ ----

    private static java.util.Set<String> dropCardPlaceholders(Map<String, String> v) {
        java.util.Set<String> dropped = new java.util.HashSet<>();
        if (PH.equals(v.get(CredentialVault.F_CC_NAME))) {
            v.remove(CredentialVault.F_CC_NAME);
            dropped.add(CredentialVault.F_CC_NAME);
        }
        String m = v.get(CredentialVault.F_CC_EXP_MONTH);
        String y = v.get(CredentialVault.F_CC_EXP_YEAR);
        if (m != null && y != null && parseInt(m) == 1 && "2099".equals(y)) {
            v.remove(CredentialVault.F_CC_EXP_MONTH);
            v.remove(CredentialVault.F_CC_EXP_YEAR);
            dropped.add(CredentialVault.F_CC_EXP_MONTH);
            dropped.add(CredentialVault.F_CC_EXP_YEAR);
        }
        String n = v.get(CredentialVault.F_CC_NUMBER);
        if (n != null && n.startsWith("000000000000")) {
            v.remove(CredentialVault.F_CC_NUMBER);
        }
        return dropped;
    }

    private static java.util.Set<String> dropAddressPlaceholders(Map<String, String> v) {
        java.util.Set<String> dropped = new java.util.HashSet<>();
        for (String k : new ArrayList<>(v.keySet())) {
            String val = v.get(k);
            boolean ph = PH.equals(val)
                    || (CredentialVault.F_POSTAL.equals(k) && "0000000".equals(val))
                    || (CredentialVault.F_TEL.equals(k) && "0000000000".equals(val))
                    || (CredentialVault.F_EMAIL.equals(k) && "hidden@example.invalid".equals(val));
            if (ph) {
                v.remove(k);
                dropped.add(k);
            }
        }
        return dropped;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 保存する。保存済みの情報の更新で、フォームに無い項目(ダミーとして取り除いたものを含む)がある時は、
     * 本人確認をして保存済みの本物の値で補ってから保存する(ダミーや空の値で本物を消さないため)。
     */
    private static void saveMerged(Activity activity, @Nullable CredentialVault.Record existing,
                                   Map<String, String> values, java.util.Set<String> dropped, String authTitle,
                                   java.util.function.Function<Map<String, String>, String> saver,
                                   String ok, String ng) {
        Runnable save = () -> CredentialVault.IO.execute(() -> {
            String guid = saver.apply(values);
            values.clear();
            activity.runOnUiThread(() -> toast(activity, guid != null ? ok : ng));
        });
        // フォームに無かった項目(ダミーとして取り除いたものを含む)は、保存済みの値を残す
        java.util.Set<String> missing = new java.util.HashSet<>(dropped);
        if (existing != null) {
            for (String f : existing.fields) if (!values.containsKey(f)) missing.add(f);
        }
        if (existing == null || missing.isEmpty()) {
            save.run();
            return;
        }
        VaultAuth.unseal(activity, existing, authTitle, null, new VaultAuth.UnsealCallback() {
            @Override
            public void onUnsealed(@NonNull Map<String, String> old) {
                for (String k : missing) {
                    String o = old.get(k);
                    if (o != null && !values.containsKey(k)) values.put(k, o);
                }
                old.clear();
                save.run();
            }

            @Override
            public void onFailed(@NonNull CharSequence message, boolean canceled) {
                values.clear();
                toast(activity, canceled ? Lang.t("保存を取り消しました") : message);
            }
        });
    }

    // ==================================================================

    /** カード番号の検査(Luhn)。 */
    static boolean luhn(@Nullable String digits) {
        if (digits == null || digits.length() < 12 || digits.length() > 19) return false;
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (d < 0 || d > 9) return false;
            if (dbl) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    private static void put(Map<String, String> m, String k, @Nullable String v) {
        if (v != null && !v.trim().isEmpty()) m.put(k, v.trim());
    }

    private static boolean isPrivate(GeckoSession session) {
        // シークレット タブでは保存を提案しない(閲覧の記録を残さないため)
        try {
            return session.getSettings().getUsePrivateMode();
        } catch (Exception e) {
            return true;
        }
    }

    private static String siteLabel(@Nullable String pageUrl, @Nullable List<CredentialVault.Record> recs) {
        String host = CredentialVault.hostOf(pageUrl);
        if (!host.isEmpty() && pageUrl != null && !pageUrl.startsWith("file:")) return host;
        if (recs != null) {
            for (CredentialVault.Record r : recs) if (r != null) return r.host();
        }
        return "AirSky";
    }

    private static boolean usable(Activity a) {
        return !a.isFinishing() && !a.isDestroyed();
    }

    /** 画面の録画・スクリーンショット・最近のアプリ一覧に映らないダイアログ。 */
    static AlertDialog showSecure(MaterialAlertDialogBuilder b) {
        AlertDialog d = b.create();
        if (d.getWindow() != null) {
            d.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
        d.show();
        return d;
    }

    private static void toast(Activity a, CharSequence message) {
        if (usable(a)) Toast.makeText(a, message, Toast.LENGTH_LONG).show();
    }

    private static void complete(GeckoResult<PromptResponse> result, PromptResponse response) {
        try {
            result.complete(response);
        } catch (IllegalStateException ignored) {
            // すでに応答済み
        }
    }
}
