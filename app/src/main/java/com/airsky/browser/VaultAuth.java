package com.airsky.browser;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import javax.crypto.Cipher;

/**
 * 保存した情報を使う前の本人確認(生体認証。Android 11 以降は端末の画面ロックでも可)。
 *
 * <p>{@link #unseal} は保存庫の鍵(Keystore の RSA 秘密鍵)に結び付いた CryptoObject で認証するので、
 * 認証が通らない限り本体はハードウェア側で復号できない(アプリのコードを書き換えても飛ばせない)。</p>
 */
final class VaultAuth {

    private VaultAuth() {
    }

    /** 復号の結果。どちらもメインスレッドで呼ばれる。 */
    interface UnsealCallback {
        void onUnsealed(@NonNull Map<String, String> values);

        /**
         * @param canceled ユーザーが取り消した(メッセージを出さなくてよい)
         */
        void onFailed(@NonNull CharSequence message, boolean canceled);
    }

    /** 本人確認だけ(削除など、復号しない操作の前)。 */
    interface ConfirmCallback {
        void onConfirmed();

        default void onFailed(@NonNull CharSequence message, boolean canceled) {
        }
    }

    static final String KEY_INVALIDATED_MESSAGE =
            Lang.t("端末の生体認証や画面ロックが変更されたため、保存した情報を読み出せなくなりました。"
                    + "設定の「自動入力とパスワード」から保存した情報を削除して、もう一度保存してください。");

    /**
     * 本人確認をして、1 件の本体を復号する。
     */
    static void unseal(@NonNull Activity activity, @NonNull CredentialVault.Record record,
                       @NonNull CharSequence title, @Nullable CharSequence subtitle,
                       @NonNull UnsealCallback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        CredentialVault vault = CredentialVault.get(activity);
        if (!CredentialVault.isSupported(activity)) {
            String reason = CredentialVault.unsupportedReason(activity);
            callback.onFailed(reason != null ? reason : Lang.t("本人確認を使用できません"), false);
            return;
        }
        if (!vault.isReadable(record)) {
            callback.onFailed(KEY_INVALIDATED_MESSAGE, false);
            return;
        }
        Cipher cipher;
        try {
            cipher = vault.newUnsealCipher();
        } catch (CredentialVault.KeyInvalidatedException e) {
            callback.onFailed(KEY_INVALIDATED_MESSAGE, false);
            return;
        } catch (Exception e) {
            callback.onFailed(Lang.t("保存した情報を読み出せません"), false);
            return;
        }
        prompt(activity, title, subtitle, cipher, new Result() {
            @Override
            public void onSuccess(@Nullable BiometricPrompt.CryptoObject crypto) {
                Cipher authed = crypto != null ? crypto.getCipher() : null;
                if (authed == null) {
                    callback.onFailed(Lang.t("本人確認に失敗しました"), false);
                    return;
                }
                CredentialVault.IO.execute(() -> {
                    try {
                        Map<String, String> values = vault.unseal(record, authed);
                        main.post(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) {
                                // 画面が閉じた: 中身は捨てて、取り消しとして返す(待っている側を必ず終わらせる)
                                values.clear();
                                callback.onFailed(Lang.t("キャンセルしました"), true);
                                return;
                            }
                            callback.onUnsealed(values);
                        });
                    } catch (Exception e) {
                        main.post(() -> callback.onFailed(Lang.t("保存した情報を読み出せません"), false));
                    }
                });
            }

            @Override
            public void onError(@NonNull CharSequence message, boolean canceled) {
                callback.onFailed(message, canceled);
            }
        });
    }

    /** 本人確認だけをする(復号はしない)。 */
    static void confirm(@NonNull Activity activity, @NonNull CharSequence title, @Nullable CharSequence subtitle,
                        @NonNull ConfirmCallback callback) {
        if (!canConfirm(activity)) {
            callback.onFailed(Lang.t("本人確認を使用できません"), false);
            return;
        }
        prompt(activity, title, subtitle, null, new Result() {
            @Override
            public void onSuccess(@Nullable BiometricPrompt.CryptoObject crypto) {
                callback.onConfirmed();
            }

            @Override
            public void onError(@NonNull CharSequence message, boolean canceled) {
                callback.onFailed(message, canceled);
            }
        });
    }

    /** 本人確認(復号を伴わないもの)ができるか。鍵が無効になった後の削除でも使う。 */
    static boolean canConfirm(Context c) {
        return CredentialVault.isSupported(c);
    }

    /** Android 9・10 用: 強力な生体認証(指紋)が登録されているか。 */
    @SuppressWarnings("deprecation")
    static boolean hasStrongBiometric(Context c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager bm = c.getSystemService(BiometricManager.class);
            return bm != null && bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    == BiometricManager.BIOMETRIC_SUCCESS;
        }
        if (!c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) return false;
        FingerprintManager fm = c.getSystemService(FingerprintManager.class);
        try {
            return fm != null && fm.isHardwareDetected() && fm.hasEnrolledFingerprints();
        } catch (SecurityException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------

    private interface Result {
        void onSuccess(@Nullable BiometricPrompt.CryptoObject crypto);

        void onError(@NonNull CharSequence message, boolean canceled);
    }

    private static void prompt(@NonNull Activity activity, @NonNull CharSequence title,
                               @Nullable CharSequence subtitle, @Nullable Cipher cipher, @NonNull Result result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || activity.isFinishing() || activity.isDestroyed()) {
            result.onError(Lang.t("本人確認を使用できません"), false);
            return;
        }
        // 二重に結果を返さない
        boolean[] done = {false};
        Result once = new Result() {
            @Override
            public void onSuccess(@Nullable BiometricPrompt.CryptoObject crypto) {
                if (done[0]) return;
                done[0] = true;
                result.onSuccess(crypto);
            }

            @Override
            public void onError(@NonNull CharSequence message, boolean canceled) {
                if (done[0]) return;
                done[0] = true;
                result.onError(message, canceled);
            }
        };

        BiometricPrompt.Builder b = new BiometricPrompt.Builder(activity).setTitle(title);
        if (subtitle != null) b.setSubtitle(subtitle);
        b.setDescription(Lang.t("保存した情報を使用するには本人確認が必要です"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            b.setNegativeButton(Lang.t("キャンセル"), activity.getMainExecutor(),
                    (d, w) -> once.onError(Lang.t("キャンセルしました"), true));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 顔認証などで確認ボタンを押さずに通らないようにする(誤って使われないように)
            b.setConfirmationRequired(true);
        }
        BiometricPrompt.AuthenticationCallback cb = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                once.onSuccess(r.getCryptoObject());
            }

            @Override
            public void onAuthenticationError(int code, CharSequence message) {
                boolean canceled = code == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                        || code == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                        || code == 13 /* BIOMETRIC_ERROR_NEGATIVE_BUTTON(androidx と同じ値) */;
                once.onError(message != null ? message : Lang.t("本人確認に失敗しました"), canceled);
            }
        };
        CancellationSignal signal = new CancellationSignal();
        try {
            BiometricPrompt p = b.build();
            if (cipher != null) {
                p.authenticate(new BiometricPrompt.CryptoObject(cipher), signal, activity.getMainExecutor(), cb);
            } else {
                p.authenticate(signal, activity.getMainExecutor(), cb);
            }
        } catch (RuntimeException e) {
            once.onError(Lang.t("本人確認を使用できません"), false);
        }
    }
}
