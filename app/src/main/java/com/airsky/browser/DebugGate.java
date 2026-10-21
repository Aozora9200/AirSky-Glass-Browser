package com.airsky.browser;

import android.os.SystemClock;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Debug Menu の鍵。
 *
 * <p>パスワードそのものはどこにも保存しない。入力された文字から PBKDF2(HMAC-SHA256, 20 万回)で
 * AES-256 の鍵を作り、下の暗号文(Debug Menu の画面のクラス名)を AES-GCM で復号する。
 * GCM は鍵が違うと認証に失敗するので、復号できた = パスワードが正しい。
 * ソースコードや APK から分かるのは塩・IV・暗号文だけで、パスワードもクラス名も読み取れない。</p>
 *
 * <p>総当たりを遅くするため、間違えるたびに次に試せるまでの待ち時間を延ばす。
 * 解除は同じプロセスの中で {@link #UNLOCK_MS} の間だけ有効({@link DebugActivity} が確かめる)。</p>
 */
final class DebugGate {

    private static final String SALT = "d7J76int0m/xEVu+H/2WAg==";
    private static final String IV = "5dzMWDhqgAjTSZcm";
    private static final String SEALED = "6480JRYmeJrrqSWenetgulu3RAlhjIoeVByCcMB6eYEV/M75RBC9Cy/N+SvZDPtT";
    private static final int ITERATIONS = 200_000;

    /** 解除が続く時間。 */
    private static final long UNLOCK_MS = 10 * 60_000;

    private static long unlockedAt = Long.MIN_VALUE;
    private static int failures;
    private static long retryAt;

    private DebugGate() {
    }

    /**
     * パスワードを確かめる(重いのでメインスレッドでは呼ばない)。
     *
     * @return 正しければ Debug Menu の画面のクラス名、違えば null
     */
    @Nullable
    static String unlock(@NonNull char[] password) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(password, decode(SALT), ITERATIONS, 256);
            byte[] key = f.generateSecret(spec).getEncoded();
            spec.clearPassword();
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, decode(IV)));
            String name = new String(c.doFinal(decode(SEALED)), StandardCharsets.UTF_8);
            synchronized (DebugGate.class) {
                failures = 0;
                unlockedAt = SystemClock.elapsedRealtime();
            }
            return name;
        } catch (Exception e) {
            // 認証タグが合わない(パスワードが違う)
            synchronized (DebugGate.class) {
                failures++;
                // 3 回目から 5 秒、10 秒、20 秒…(最大 5 分)待たせる
                long wait = failures < 3 ? 0 : Math.min(300_000L, 5_000L << Math.min(6, failures - 3));
                retryAt = SystemClock.elapsedRealtime() + wait;
            }
            return null;
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    /** 次に試せるまでの残り時間(ミリ秒)。0 ならすぐ試せる。 */
    static synchronized long retryWaitMs() {
        return Math.max(0, retryAt - SystemClock.elapsedRealtime());
    }

    /** 解除してから {@link #UNLOCK_MS} 以内か。 */
    static synchronized boolean isUnlocked() {
        return unlockedAt != Long.MIN_VALUE && SystemClock.elapsedRealtime() - unlockedAt < UNLOCK_MS;
    }

    private static byte[] decode(String s) {
        return Base64.decode(s, Base64.NO_WRAP);
    }
}
