package com.airsky.browser;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;

/**
 * 端末の生体認証(指紋・顔など)で Aozora のロックを解除する。
 *
 * <p>Android 11 以降は、生体認証が通らない時に端末の画面ロック(PIN・パターンなど)でも解除できる。
 * Android 9 より前の端末では使えない。</p>
 */
final class BiometricLock {

    /** 認証の結果。 */
    interface Callback {
        void onSuccess();

        /** キャンセル・失敗回数超過など。message は画面に出す文。 */
        void onError(@NonNull CharSequence message, boolean canceled);
    }

    private BiometricLock() {
    }

    /** この端末で生体認証が使えるか(登録済みか)。 */
    static boolean isAvailable(Context c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager bm = c.getSystemService(BiometricManager.class);
            return bm != null && bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    == BiometricManager.BIOMETRIC_SUCCESS;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            BiometricManager bm = c.getSystemService(BiometricManager.class);
            return bm != null && bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return hasFingerprint(c);
        }
        return false;
    }

    /** 使えない理由(設定画面の説明用)。 */
    static String unavailableReason(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return Lang.t("この Android のバージョンでは使用できません");
        }
        return Lang.t("端末に指紋や顔などの生体認証が登録されていないため使用できません");
    }

    /** ロック解除に使えるものが残っているか(生体認証か、Android 11 以降なら端末の画面ロック)。 */
    static boolean canUnlock(Context c) {
        if (isAvailable(c)) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            KeyguardManager km = c.getSystemService(KeyguardManager.class);
            return km != null && km.isDeviceSecure();
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static boolean hasFingerprint(Context c) {
        if (!c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) return false;
        FingerprintManager fm = c.getSystemService(FingerprintManager.class);
        try {
            return fm != null && fm.isHardwareDetected() && fm.hasEnrolledFingerprints();
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * 生体認証のダイアログを出す。
     *
     * @return 途中で取り消すための signal(出せなかった時は null で、callback.onError が呼ばれる)
     */
    static CancellationSignal authenticate(@NonNull Activity activity, @NonNull CharSequence title,
                                           @NonNull Callback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            callback.onError(unavailableReason(activity), false);
            return null;
        }
        BiometricPrompt.Builder b = new BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(Lang.t("AirSky のロックを解除"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 生体認証が通らない時は端末の画面ロックでも解除できる
            b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            b.setNegativeButton(Lang.t("キャンセル"), activity.getMainExecutor(),
                    (d, w) -> callback.onError(Lang.t("認証がキャンセルされました"), true));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            b.setConfirmationRequired(false);
        }
        CancellationSignal signal = new CancellationSignal();
        try {
            b.build().authenticate(signal, activity.getMainExecutor(),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            callback.onSuccess();
                        }

                        @Override
                        public void onAuthenticationError(int code, CharSequence message) {
                            boolean canceled = code == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                                    || code == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED;
                            callback.onError(message != null ? message : Lang.t("認証できませんでした"), canceled);
                        }
                    });
        } catch (RuntimeException e) {
            // 生体認証も画面ロックもない(登録を消した)など
            callback.onError(Lang.t("生体認証を使用できません"), false);
            return null;
        }
        return signal;
    }
}
