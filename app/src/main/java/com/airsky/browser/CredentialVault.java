package com.airsky.browser;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.StrongBoxUnavailableException;
import android.util.AtomicFile;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * 自動入力用のパスワード・お支払い方法(クレジットカード)・住所の保存庫。
 *
 * <h3>暗号化の仕組み</h3>
 * <ul>
 *   <li><b>本体(パスワード・カード番号・住所など)</b> は、レコードごとに作る使い捨ての AES-256 鍵で
 *   AES-GCM 暗号化し、その AES 鍵を Android Keystore の RSA 鍵(公開鍵)で包んで保存する。
 *   RSA の秘密鍵は端末のセキュアハードウェアから取り出せず、<b>1 回使うたびに生体認証(または端末の画面ロック)
 *   が必要</b>({@link #newUnsealCipher} → {@link VaultAuth} → {@link #unseal})。
 *   公開鍵での暗号化には認証が要らないので、サイトでの保存は認証なしでできるが、読み出しは必ず本人確認を通る。</li>
 *   <li><b>一覧に出す情報(サイト・ユーザー名・カード下 4 桁など)</b> は、認証不要の Keystore の AES-GCM 鍵で
 *   保存庫ファイルごと暗号化する。画面には伏せ字にして出す({@link Mask})。</li>
 *   <li>同じパスワード・カードかどうかは、Keystore の HMAC 鍵で作った値で比べる(本体を復号しない)。</li>
 *   <li>ファイルは no_backup フォルダに置き、バックアップや端末間の移行には含めない。</li>
 *   <li>端末の生体認証の登録や画面ロックが変わると RSA 鍵は無効になり、本体は二度と復号できない(仕様)。</li>
 * </ul>
 *
 * <p>メソッドはどのスレッドからでも呼べる(中で同期する)。Keystore の処理は遅いことがあるので、
 * 画面から使う時は {@link #IO} で実行する。</p>
 */
final class CredentialVault {

    private static final String TAG = "CredentialVault";

    static final int LOGIN = 0;
    static final int CARD = 1;
    static final int ADDRESS = 2;

    /** 保存庫の処理用(Keystore・ファイル)。1 本で順番に実行する。 */
    static final java.util.concurrent.ExecutorService IO =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "aozora-vault");
                t.setDaemon(true);
                return t;
            });

    // ---- 設定(オン・オフ) ----

    private static final String PREFS = "autofill_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String[] TYPE_KEYS = {"logins", "cards", "addresses"};

    // ---- Keystore ----

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS_META = "aozora_vault_meta_v1";
    private static final String ALIAS_MAC = "aozora_vault_mac_v1";
    private static final String ALIAS_SEAL = "aozora_vault_seal_v1";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    /** Keystore の RSA-OAEP は MGF1 に SHA-1 しか使えない端末があるため、暗号化・復号とも明示する。 */
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";

    // ---- ファイル ----

    private static final String FILE_NAME = "autofill_vault.bin";
    private static final byte[] MAGIC = {'A', 'Z', 'V', '1'};
    private static final byte[] FILE_AAD = "aozora-autofill-vault-v1".getBytes(StandardCharsets.UTF_8);
    /** 異常に大きいファイルは読まない。 */
    private static final int MAX_FILE_BYTES = 32 * 1024 * 1024;
    /** 保存できる件数と値の長さの上限(ページから巨大な値を保存させられて保存庫が壊れないように)。 */
    private static final int MAX_RECORDS = 2000;
    private static final int MAX_PASSWORD = 4096;
    private static final int MAX_USERNAME = 1024;
    private static final int MAX_ORIGIN = 2048;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static CredentialVault instance;

    private final Context app;
    private final AtomicFile file;

    /** 読み込んだ一覧(本体は暗号化されたまま)。null = まだ読んでいない。 */
    @Nullable private List<Record> records;
    private final Set<String> neverSave = new LinkedHashSet<>();
    /** ファイルはあるのに読めない(鍵が消えた等)。上書きして消さないよう、保存を止める。 */
    private boolean broken = false;

    static synchronized CredentialVault get(@NonNull Context c) {
        if (instance == null) instance = new CredentialVault(c.getApplicationContext());
        return instance;
    }

    private CredentialVault(Context app) {
        this.app = app;
        this.file = new AtomicFile(new File(app.getNoBackupFilesDir(), FILE_NAME));
    }

    // ==================================================================
    // 設定
    // ==================================================================

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 「自動入力とパスワード」全体のスイッチ。既定はオフ。 */
    static boolean isMasterOn(Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, false);
    }

    static void setMasterOn(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_ENABLED, on).apply();
    }

    /** 種類ごとのスイッチ(全体がオンの時だけ意味を持つ)。既定はオン。 */
    static boolean isTypeOn(Context c, int type) {
        return prefs(c).getBoolean(TYPE_KEYS[type], true);
    }

    static void setTypeOn(Context c, int type, boolean on) {
        prefs(c).edit().putBoolean(TYPE_KEYS[type], on).apply();
    }

    /** 保存・自動入力をしてよいか(全体・種類のスイッチがオンで、端末が対応している)。 */
    static boolean isActive(Context c, int type) {
        return isMasterOn(c) && isTypeOn(c, type) && isSupported(c);
    }

    /**
     * この端末で使えるか。本体の復号に「1 回ごとの本人確認」が必要な鍵を使うので、
     * Android 9 以降で、強力な生体認証(Android 11 以降なら画面ロックでも可)が使えることが条件。
     */
    static boolean isSupported(Context c) {
        return unsupportedReason(c) == null;
    }

    @Nullable
    static String unsupportedReason(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return Lang.t("Android 9 以降で使用できます");
        }
        KeyguardManager km = c.getSystemService(KeyguardManager.class);
        if (km == null || !km.isDeviceSecure()) {
            return Lang.t("端末に画面ロック(PIN・パターン・パスワード)が設定されていないため使用できません");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager bm = c.getSystemService(BiometricManager.class);
            int r = bm == null ? -1 : bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            if (r != BiometricManager.BIOMETRIC_SUCCESS) return Lang.t("端末の本人確認を使用できないため使用できません");
            return null;
        }
        if (!VaultAuth.hasStrongBiometric(c)) {
            return Lang.t("端末に指紋が登録されていないため使用できません");
        }
        return null;
    }

    // ==================================================================
    // レコード
    // ==================================================================

    /** 保存した 1 件。本体(パスワードなど)は {@link #sealed} に暗号化されたまま入っている。 */
    static final class Record {
        final int type;
        final String guid;

        // ログイン
        String origin = "";
        @Nullable String formActionOrigin;
        @Nullable String httpRealm;
        String username = "";
        /** パスワードの HMAC(変わったかどうかの判定用)。 */
        String passwordMac = "";

        // カード
        String brand = "";
        String last4 = "";
        /** カード番号の HMAC(同じカードの判定用)。 */
        String numberMac = "";

        // 住所
        String nameInitial = "";
        String country = "";

        // カード・住所: 中身全体の HMAC(まったく同じ内容かどうかの判定用)
        String contentMac = "";
        /** 空でない項目の名前(Gecko に渡すダミーを、本物と同じ形にするため)。 */
        Set<String> fields = new LinkedHashSet<>();

        long created;
        long modified;
        long lastUsed;
        int timesUsed;

        /** 項目ごとの HMAC(フォームの値が保存済みと同じかを、復号せずに比べるため)。 */
        Map<String, String> fieldMacs = new HashMap<>();
        /** 本体を包んだ RSA 鍵の識別子(鍵が作り直された後の古いレコードを見分ける)。 */
        String keyId = "";

        /** 暗号化した本体。 */
        byte[] wrappedKey = new byte[0];
        byte[] iv = new byte[0];
        byte[] ciphertext = new byte[0];

        Record(int type, String guid) {
            this.type = type;
            this.guid = guid;
        }

        /** ログインのサイト(ホスト名)。 */
        String host() {
            return hostOf(origin);
        }

        Record copy() {
            Record r = new Record(type, guid);
            r.origin = origin;
            r.formActionOrigin = formActionOrigin;
            r.httpRealm = httpRealm;
            r.username = username;
            r.passwordMac = passwordMac;
            r.brand = brand;
            r.last4 = last4;
            r.numberMac = numberMac;
            r.nameInitial = nameInitial;
            r.country = country;
            r.contentMac = contentMac;
            r.fields = new LinkedHashSet<>(fields);
            r.fieldMacs = new HashMap<>(fieldMacs);
            r.keyId = keyId;
            r.created = created;
            r.modified = modified;
            r.lastUsed = lastUsed;
            r.timesUsed = timesUsed;
            r.wrappedKey = wrappedKey.clone();
            r.iv = iv.clone();
            r.ciphertext = ciphertext.clone();
            return r;
        }
    }

    // ---- 本体の項目名 ----

    static final String F_PASSWORD = "password";

    static final String F_CC_NAME = "name";
    static final String F_CC_NUMBER = "number";
    static final String F_CC_EXP_MONTH = "expMonth";
    static final String F_CC_EXP_YEAR = "expYear";

    static final String F_NAME = "name";
    static final String F_GIVEN_NAME = "givenName";
    static final String F_ADDITIONAL_NAME = "additionalName";
    static final String F_FAMILY_NAME = "familyName";
    static final String F_ORGANIZATION = "organization";
    static final String F_STREET = "streetAddress";
    static final String F_LEVEL1 = "addressLevel1";
    static final String F_LEVEL2 = "addressLevel2";
    static final String F_LEVEL3 = "addressLevel3";
    static final String F_POSTAL = "postalCode";
    static final String F_COUNTRY = "country";
    static final String F_TEL = "tel";
    static final String F_EMAIL = "email";

    static final String[] ADDRESS_FIELDS = {F_NAME, F_GIVEN_NAME, F_ADDITIONAL_NAME, F_FAMILY_NAME,
            F_ORGANIZATION, F_POSTAL, F_LEVEL1, F_LEVEL2, F_LEVEL3, F_STREET, F_COUNTRY, F_TEL, F_EMAIL};

    // ==================================================================
    // 読み出し(本体は暗号化されたまま)
    // ==================================================================

    /** 保存庫が読めない状態か(鍵が消えた等)。 */
    synchronized boolean isBroken() {
        ensureLoaded();
        return broken;
    }

    /** 種類ごとの一覧(新しく使った順)。コピーを返す。 */
    synchronized List<Record> list(int type) {
        ensureLoaded();
        List<Record> out = new ArrayList<>();
        for (Record r : records) if (r.type == type) out.add(r.copy());
        Collections.sort(out, (a, b) -> {
            long x = Math.max(a.lastUsed, a.modified);
            long y = Math.max(b.lastUsed, b.modified);
            if (type == LOGIN && x == y) return a.host().compareTo(b.host());
            return Long.compare(y, x);
        });
        return out;
    }

    synchronized int count(int type) {
        ensureLoaded();
        int n = 0;
        for (Record r : records) if (r.type == type) n++;
        return n;
    }

    @Nullable
    synchronized Record find(@Nullable String guid) {
        if (guid == null) return null;
        ensureLoaded();
        for (Record r : records) if (r.guid.equals(guid)) return r.copy();
        return null;
    }

    /**
     * ドメイン(eTLD+1)に属するログイン。domain が null ならすべて。
     * 細かい一致(スキーム・サブドメイン)は Gecko 側でもう一度絞り込まれる。
     */
    synchronized List<Record> loginsForDomain(@Nullable String domain) {
        ensureLoaded();
        List<Record> out = new ArrayList<>();
        String d = domain == null ? null : domain.toLowerCase(Locale.ROOT);
        for (Record r : records) {
            if (r.type != LOGIN) continue;
            String host = r.host();
            if (d == null || host.equals(d) || host.endsWith("." + d)) out.add(r.copy());
        }
        return out;
    }

    /** 同じサイト(origin)・同じユーザー名のログイン。 */
    @Nullable
    synchronized Record findLogin(@NonNull String origin, @NonNull String username, @Nullable String httpRealm) {
        ensureLoaded();
        for (Record r : records) {
            if (r.type == LOGIN && r.origin.equals(origin) && r.username.equals(username)
                    && equalsNullable(r.httpRealm, httpRealm)) {
                return r.copy();
            }
        }
        return null;
    }

    /** 保存したパスワードと同じか(本体を復号せずに比べる)。 */
    boolean passwordMatches(@NonNull Record r, @NonNull String password) {
        try {
            return MessageDigest.isEqual(Base64.decode(r.passwordMac, Base64.NO_WRAP),
                    mac("pw|" + r.guid + "|" + password));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * values のすべての項目が、保存済みの r と同じ値か(本体を復号せずに比べる)。
     * フォームに無い項目は比べない。
     */
    boolean fieldsMatch(@NonNull Record r, @NonNull Map<String, String> values) {
        try {
            for (Map.Entry<String, String> e : values.entrySet()) {
                String stored = r.fieldMacs.get(e.getKey());
                if (stored == null || !MessageDigest.isEqual(Base64.decode(stored, Base64.NO_WRAP),
                        fieldMac(r.guid, e.getKey(), e.getValue()))) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 同じ番号のカード。 */
    @Nullable
    synchronized Record findCardByNumber(@NonNull String number) {
        ensureLoaded();
        String m;
        try {
            m = b64(mac("cc|" + digitsOnly(number)));
        } catch (Exception e) {
            return null;
        }
        for (Record r : records) if (r.type == CARD && r.numberMac.equals(m)) return r.copy();
        return null;
    }

    /** まったく同じ内容の住所・カード。 */
    @Nullable
    synchronized Record findSameContent(int type, @NonNull Map<String, String> values) {
        ensureLoaded();
        String m;
        try {
            m = b64(contentMac(type, values));
        } catch (Exception e) {
            return null;
        }
        for (Record r : records) if (r.type == type && r.contentMac.equals(m)) return r.copy();
        return null;
    }

    // ---- 保存しないサイト ----

    synchronized boolean isNeverSave(@Nullable String origin) {
        if (origin == null) return false;
        ensureLoaded();
        return neverSave.contains(origin);
    }

    synchronized List<String> neverSaveList() {
        ensureLoaded();
        return new ArrayList<>(neverSave);
    }

    synchronized boolean addNeverSave(@NonNull String origin) {
        ensureLoaded();
        if (broken || origin.length() > MAX_ORIGIN || neverSave.size() >= MAX_RECORDS) return false;
        neverSave.add(origin);
        return persist();
    }

    synchronized boolean removeNeverSave(@NonNull String origin) {
        ensureLoaded();
        if (broken) return false;
        neverSave.remove(origin);
        return persist();
    }

    // ==================================================================
    // 保存
    // ==================================================================

    /**
     * ログインを保存する(guid が null なら、同じサイト・ユーザー名のものを更新するか新しく作る)。
     *
     * @return 保存した guid。失敗したら null
     */
    @Nullable
    synchronized String saveLogin(@NonNull String origin, @Nullable String formActionOrigin,
                                  @Nullable String httpRealm, @NonNull String username,
                                  @NonNull String password, @Nullable String guid) {
        ensureLoaded();
        if (broken || origin.isEmpty() || password.isEmpty() || password.length() > MAX_PASSWORD
                || username.length() > MAX_USERNAME || origin.length() > MAX_ORIGIN
                || (formActionOrigin != null && formActionOrigin.length() > MAX_ORIGIN)
                || (httpRealm != null && httpRealm.length() > MAX_ORIGIN)) {
            return null;
        }
        try {
            Record old = null;
            if (guid != null) old = findInternal(guid);
            if (old == null) {
                for (Record r : records) {
                    if (r.type == LOGIN && r.origin.equals(origin) && r.username.equals(username)
                            && equalsNullable(r.httpRealm, httpRealm)) {
                        old = r;
                        break;
                    }
                }
            }
            Record r = new Record(LOGIN, old != null ? old.guid : newGuid());
            r.origin = origin;
            r.formActionOrigin = formActionOrigin;
            r.httpRealm = httpRealm;
            r.username = username;
            r.passwordMac = b64(mac("pw|" + r.guid + "|" + password));
            Map<String, String> secret = new HashMap<>();
            secret.put(F_PASSWORD, password);
            seal(r, secret);
            return commit(r, old) ? r.guid : null;
        } catch (Exception e) {
            Log.w(TAG, "ログインを保存できません: " + e.getClass().getSimpleName());
            return null;
        }
    }

    /** カードを保存する(guid が null なら、同じ番号のカードを更新するか新しく作る)。 */
    @Nullable
    synchronized String saveCard(@NonNull Map<String, String> values, @Nullable String guid) {
        ensureLoaded();
        Map<String, String> v = clean(values, new String[]{F_CC_NAME, F_CC_NUMBER, F_CC_EXP_MONTH, F_CC_EXP_YEAR});
        String number = digitsOnly(v.getOrDefault(F_CC_NUMBER, ""));
        if (broken || number.length() < 12 || number.length() > 19) return null;
        v.put(F_CC_NUMBER, number);
        try {
            String numberMac = b64(mac("cc|" + number));
            Record old = guid != null ? findInternal(guid) : null;
            if (old == null) {
                for (Record r : records) {
                    if (r.type == CARD && r.numberMac.equals(numberMac)) {
                        old = r;
                        break;
                    }
                }
            }
            Record r = new Record(CARD, old != null ? old.guid : newGuid());
            r.numberMac = numberMac;
            r.brand = Mask.cardBrand(number);
            r.last4 = number.substring(number.length() - 4);
            r.contentMac = b64(contentMac(CARD, v));
            r.fields = new LinkedHashSet<>(v.keySet());
            seal(r, v);
            return commit(r, old) ? r.guid : null;
        } catch (Exception e) {
            Log.w(TAG, "カードを保存できません: " + e.getClass().getSimpleName());
            return null;
        }
    }

    /** 住所を保存する(guid が null なら新しく作る。まったく同じ内容なら何もしない)。 */
    @Nullable
    synchronized String saveAddress(@NonNull Map<String, String> values, @Nullable String guid) {
        ensureLoaded();
        Map<String, String> v = clean(values, ADDRESS_FIELDS);
        if (broken || v.isEmpty()) return null;
        try {
            String content = b64(contentMac(ADDRESS, v));
            Record old = guid != null ? findInternal(guid) : null;
            if (old == null) {
                for (Record r : records) {
                    if (r.type == ADDRESS && r.contentMac.equals(content)) return r.guid;
                }
            }
            Record r = new Record(ADDRESS, old != null ? old.guid : newGuid());
            String name = v.get(F_NAME);
            if (name == null) {
                name = (v.getOrDefault(F_FAMILY_NAME, "") + v.getOrDefault(F_GIVEN_NAME, "")).trim();
            }
            r.nameInitial = name.isEmpty() ? "" : name.substring(0, name.offsetByCodePoints(0, 1));
            r.country = v.getOrDefault(F_COUNTRY, "").toUpperCase(Locale.ROOT);
            r.contentMac = content;
            r.fields = new LinkedHashSet<>(v.keySet());
            seal(r, v);
            return commit(r, old) ? r.guid : null;
        } catch (Exception e) {
            Log.w(TAG, "住所を保存できません: " + e.getClass().getSimpleName());
            return null;
        }
    }

    /** 自動入力に使った記録。 */
    synchronized void markUsed(@Nullable String guid) {
        if (guid == null) return;
        ensureLoaded();
        Record r = findInternal(guid);
        if (r == null || broken) return;
        r.lastUsed = System.currentTimeMillis();
        r.timesUsed++;
        persist();
    }

    synchronized boolean delete(@NonNull String guid) {
        ensureLoaded();
        if (broken) return false;
        Record r = findInternal(guid);
        if (r == null) return true;
        records.remove(r);
        r.wrappedKey = new byte[0];
        r.ciphertext = new byte[0];
        return persist();
    }

    /** 保存した情報と鍵をすべて消す(読めなくなった保存庫も消す)。 */
    synchronized void deleteAll() {
        records = new ArrayList<>();
        neverSave.clear();
        broken = false;
        file.delete();
        try {
            KeyStore ks = keyStore();
            for (String alias : new String[]{ALIAS_META, ALIAS_MAC, ALIAS_SEAL}) {
                if (ks.containsAlias(alias)) ks.deleteEntry(alias);
            }
        } catch (Exception e) {
            Log.w(TAG, "鍵を削除できません: " + e.getClass().getSimpleName());
        }
    }

    /** 鍵を用意する(スイッチをオンにする時に呼んで、使えるかを確かめる)。 */
    synchronized boolean prepareKeys() {
        try {
            metaKey();
            macKey();
            sealPublicKey();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "鍵を作成できません: " + e.getClass().getSimpleName());
            return false;
        }
    }

    // ==================================================================
    // 本体の復号(本人確認が必要)
    // ==================================================================

    /** 鍵が無効になった(生体認証の登録や画面ロックが変わった)。 */
    static final class KeyInvalidatedException extends Exception {
        KeyInvalidatedException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * 本体を復号するための Cipher を用意する。これを {@link VaultAuth} に渡して本人確認を通すと、
     * 1 回だけ {@link #unseal} に使える。
     */
    /** このレコードは、今の鍵で読み出せるか(鍵が作り直される前に保存したものは読めない)。 */
    boolean isReadable(@NonNull Record r) {
        try {
            KeyStore ks = keyStore();
            Certificate cert = ks.getCertificate(ALIAS_SEAL);
            return cert != null && keyIdOf(cert.getPublicKey()).equals(r.keyId);
        } catch (Exception e) {
            return false;
        }
    }

    /** 鍵が無効になって読み出せないレコードがあるか。 */
    synchronized boolean hasUnreadable() {
        ensureLoaded();
        for (Record r : records) if (!isReadable(r)) return true;
        return false;
    }

    @NonNull
    Cipher newUnsealCipher() throws KeyInvalidatedException, Exception {
        KeyStore ks = keyStore();
        if (!ks.containsAlias(ALIAS_SEAL)) throw new KeyInvalidatedException(null);
        PrivateKey key = (PrivateKey) ks.getKey(ALIAS_SEAL, null);
        if (key == null) throw new KeyInvalidatedException(null);
        Cipher c = Cipher.getInstance(RSA_TRANSFORMATION);
        try {
            c.init(Cipher.DECRYPT_MODE, key, OAEP);
        } catch (android.security.keystore.KeyPermanentlyInvalidatedException e) {
            throw new KeyInvalidatedException(e);
        }
        return c;
    }

    /**
     * 本人確認を通った Cipher で 1 件の本体を復号する。Cipher は 1 回しか使えない。
     */
    @NonNull
    Map<String, String> unseal(@NonNull Record r, @NonNull Cipher authenticated) throws Exception {
        byte[] keyBytes = authenticated.doFinal(r.wrappedKey);
        if (keyBytes.length != 32) {
            Arrays.fill(keyBytes, (byte) 0);
            throw new IllegalStateException("key");
        }
        byte[] plain = null;
        try {
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
            Cipher c = Cipher.getInstance(AES_TRANSFORMATION);
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, r.iv));
            c.updateAAD(recordAad(r));
            plain = c.doFinal(r.ciphertext);
            JSONObject o = new JSONObject(new String(plain, StandardCharsets.UTF_8));
            Map<String, String> out = new HashMap<>();
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                out.put(k, o.optString(k, ""));
            }
            return out;
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
            if (plain != null) Arrays.fill(plain, (byte) 0);
        }
    }

    // ==================================================================
    // 内部
    // ==================================================================

    @Nullable
    private Record findInternal(String guid) {
        for (Record r : records) if (r.guid.equals(guid)) return r;
        return null;
    }

    private boolean commit(Record r, @Nullable Record old) {
        if (old == null && records.size() >= MAX_RECORDS) return false;
        long now = System.currentTimeMillis();
        r.created = old != null ? old.created : now;
        r.modified = now;
        r.lastUsed = old != null ? old.lastUsed : 0;
        r.timesUsed = old != null ? old.timesUsed : 0;
        List<Record> backup = new ArrayList<>(records);
        if (old != null) records.remove(old);
        records.add(r);
        if (persist()) return true;
        records = backup;
        return false;
    }

    /** 本体を使い捨ての AES 鍵で暗号化し、その鍵を RSA 公開鍵で包む。 */
    private void seal(Record r, Map<String, String> secret) throws Exception {
        if (r.type != LOGIN) {
            r.fieldMacs = new HashMap<>();
            for (Map.Entry<String, String> e : secret.entrySet()) {
                r.fieldMacs.put(e.getKey(), b64(fieldMac(r.guid, e.getKey(), e.getValue())));
            }
        }
        JSONObject o = new JSONObject();
        for (Map.Entry<String, String> e : secret.entrySet()) o.put(e.getKey(), e.getValue());
        byte[] plain = o.toString().getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = new byte[32];
        RANDOM.nextBytes(keyBytes);
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance(AES_TRANSFORMATION);
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
            c.updateAAD(recordAad(r));
            r.ciphertext = c.doFinal(plain);
            r.iv = iv;
            PublicKey pub = sealPublicKey();
            Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION);
            rsa.init(Cipher.ENCRYPT_MODE, pub, OAEP);
            r.wrappedKey = rsa.doFinal(keyBytes);
            r.keyId = keyIdOf(pub);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
            Arrays.fill(plain, (byte) 0);
        }
    }

    /** 暗号文を別のレコードへ付け替えられないよう、種類と guid を認証付きデータにする。 */
    private static byte[] recordAad(Record r) {
        return ("aozora-vault-record|" + r.type + "|" + r.guid).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] contentMac(int type, Map<String, String> values) throws Exception {
        StringBuilder sb = new StringBuilder("content|").append(type);
        List<String> keys = new ArrayList<>(values.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            sb.append('|').append(k).append('=').append(values.get(k).replace("|", "||"));
        }
        return mac(sb.toString());
    }

    private byte[] fieldMac(String guid, String field, String value) throws Exception {
        return mac("field|" + guid + "|" + field + "|" + value);
    }

    private static String keyIdOf(PublicKey pub) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(pub.getEncoded());
        return b64(Arrays.copyOf(h, 12));
    }

    private byte[] mac(String s) throws Exception {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(macKey());
        return m.doFinal(s.getBytes(StandardCharsets.UTF_8));
    }

    // ---- ファイルの読み書き ----

    private void ensureLoaded() {
        // 読めなかった時は、次に使う時にもう一度読んでみる(一時的な Keystore のエラーなど)
        if (records != null && !broken) return;
        records = new ArrayList<>();
        neverSave.clear();
        broken = false;
        try {
            byte[] data = readAll();
            if (data == null) return; // まだ何も保存していない
            if (data.length < MAGIC.length + 12 + 16 || !Arrays.equals(Arrays.copyOf(data, 4), MAGIC)) {
                throw new IllegalStateException("format");
            }
            byte[] iv = Arrays.copyOfRange(data, 4, 16);
            Cipher c = Cipher.getInstance(AES_TRANSFORMATION);
            c.init(Cipher.DECRYPT_MODE, existingMetaKey(), new GCMParameterSpec(128, iv));
            c.updateAAD(FILE_AAD);
            byte[] plain = c.doFinal(data, 16, data.length - 16);
            JSONObject root = new JSONObject(new String(plain, StandardCharsets.UTF_8));
            Arrays.fill(plain, (byte) 0);
            JSONArray arr = root.optJSONArray("records");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    Record r = fromJson(arr.getJSONObject(i));
                    if (r != null) records.add(r);
                }
            }
            JSONArray never = root.optJSONArray("never");
            if (never != null) for (int i = 0; i < never.length(); i++) neverSave.add(never.getString(i));
        } catch (Exception e) {
            Log.w(TAG, "保存庫を読み込めません: " + e.getClass().getSimpleName());
            records = new ArrayList<>();
            neverSave.clear();
            broken = true;
        }
    }

    @Nullable
    private byte[] readAll() throws Exception {
        InputStream opened;
        try {
            opened = file.openRead();
        } catch (java.io.FileNotFoundException e) {
            return null;
        }
        try (InputStream in = opened) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_FILE_BYTES) throw new IllegalStateException("too large");
            }
            return out.toByteArray();
        }
    }

    private boolean persist() {
        FileOutputStream out = null;
        byte[] plain = null;
        try {
            JSONObject root = new JSONObject();
            root.put("v", 1);
            JSONArray arr = new JSONArray();
            for (Record r : records) arr.put(toJson(r));
            root.put("records", arr);
            root.put("never", new JSONArray(neverSave));
            plain = root.toString().getBytes(StandardCharsets.UTF_8);
            Cipher c = Cipher.getInstance(AES_TRANSFORMATION);
            c.init(Cipher.ENCRYPT_MODE, metaKey());
            byte[] iv = c.getIV();
            if (iv == null || iv.length != 12) throw new IllegalStateException("iv");
            c.updateAAD(FILE_AAD);
            byte[] ct = c.doFinal(plain);
            out = file.startWrite();
            out.write(MAGIC);
            out.write(iv);
            out.write(ct);
            file.finishWrite(out);
            return true;
        } catch (Exception e) {
            if (out != null) file.failWrite(out);
            Log.w(TAG, "保存庫を書き込めません: " + e.getClass().getSimpleName());
            return false;
        } finally {
            if (plain != null) Arrays.fill(plain, (byte) 0);
        }
    }

    private static JSONObject toJson(Record r) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("t", r.type);
        o.put("g", r.guid);
        o.put("o", r.origin);
        if (r.formActionOrigin != null) o.put("fa", r.formActionOrigin);
        if (r.httpRealm != null) o.put("hr", r.httpRealm);
        o.put("u", r.username);
        o.put("pm", r.passwordMac);
        o.put("b", r.brand);
        o.put("l4", r.last4);
        o.put("nm", r.numberMac);
        o.put("ni", r.nameInitial);
        o.put("c", r.country);
        o.put("cm", r.contentMac);
        o.put("f", new JSONArray(r.fields));
        o.put("fm", new JSONObject(r.fieldMacs));
        o.put("kid", r.keyId);
        o.put("cr", r.created);
        o.put("md", r.modified);
        o.put("lu", r.lastUsed);
        o.put("tu", r.timesUsed);
        o.put("k", b64(r.wrappedKey));
        o.put("iv", b64(r.iv));
        o.put("ct", b64(r.ciphertext));
        return o;
    }

    @Nullable
    private static Record fromJson(JSONObject o) {
        int type = o.optInt("t", -1);
        String guid = o.optString("g", "");
        if (type < LOGIN || type > ADDRESS || guid.isEmpty()) return null;
        Record r = new Record(type, guid);
        r.origin = o.optString("o", "");
        r.formActionOrigin = o.has("fa") ? o.optString("fa") : null;
        r.httpRealm = o.has("hr") ? o.optString("hr") : null;
        r.username = o.optString("u", "");
        r.passwordMac = o.optString("pm", "");
        r.brand = o.optString("b", "");
        r.last4 = o.optString("l4", "");
        r.numberMac = o.optString("nm", "");
        r.nameInitial = o.optString("ni", "");
        r.country = o.optString("c", "");
        r.contentMac = o.optString("cm", "");
        JSONArray f = o.optJSONArray("f");
        if (f != null) for (int i = 0; i < f.length(); i++) r.fields.add(f.optString(i));
        JSONObject fm = o.optJSONObject("fm");
        if (fm != null) {
            for (java.util.Iterator<String> it = fm.keys(); it.hasNext(); ) {
                String k = it.next();
                r.fieldMacs.put(k, fm.optString(k, ""));
            }
        }
        r.keyId = o.optString("kid", "");
        r.created = o.optLong("cr");
        r.modified = o.optLong("md");
        r.lastUsed = o.optLong("lu");
        r.timesUsed = o.optInt("tu");
        r.wrappedKey = Base64.decode(o.optString("k", ""), Base64.NO_WRAP);
        r.iv = Base64.decode(o.optString("iv", ""), Base64.NO_WRAP);
        r.ciphertext = Base64.decode(o.optString("ct", ""), Base64.NO_WRAP);
        return r;
    }

    // ---- 鍵 ----

    private static KeyStore keyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        return ks;
    }

    /** 既にある一覧用の鍵(無ければ作らない = 読めない保存庫を新しい鍵で上書きしない)。 */
    private SecretKey existingMetaKey() throws Exception {
        SecretKey k = (SecretKey) keyStore().getKey(ALIAS_META, null);
        if (k == null) throw new IllegalStateException("no key");
        return k;
    }

    private SecretKey metaKey() throws Exception {
        KeyStore ks = keyStore();
        SecretKey k = (SecretKey) ks.getKey(ALIAS_META, null);
        if (k != null) return k;
        return generateSecret(ALIAS_META, KeyProperties.KEY_ALGORITHM_AES,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT, true);
    }

    private SecretKey macKey() throws Exception {
        KeyStore ks = keyStore();
        SecretKey k = (SecretKey) ks.getKey(ALIAS_MAC, null);
        if (k != null) return k;
        return generateSecret(ALIAS_MAC, KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KeyProperties.PURPOSE_SIGN, false);
    }

    /** AES / HMAC の鍵を作る。使えれば StrongBox(専用の安全なチップ)に作る。 */
    private static SecretKey generateSecret(String alias, String algorithm, int purposes, boolean aes) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean strongBox = attempt == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
            if (attempt == 0 && !strongBox) continue;
            KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(alias, purposes);
            if (aes) {
                b.setKeySize(256)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true);
            }
            if (strongBox) b.setIsStrongBoxBacked(true);
            try {
                KeyGenerator g = KeyGenerator.getInstance(algorithm, KEYSTORE);
                g.init(b.build());
                return g.generateKey();
            } catch (Exception e) {
                if (!strongBox) throw e;
                // StrongBox が無い・対応していない → 通常のセキュアハードウェア(TEE)で作り直す
                if (!(e instanceof StrongBoxUnavailableException)) {
                    try {
                        keyStore().deleteEntry(alias);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        throw new IllegalStateException("unreachable");
    }

    /** 本体を包む RSA 鍵の公開鍵(無ければ作る)。 */
    private PublicKey sealPublicKey() throws Exception {
        KeyStore ks = keyStore();
        if (ks.containsAlias(ALIAS_SEAL)) {
            // 生体認証の登録変更などで秘密鍵が無効になっていたら、新しい鍵を作る
            // (無効な鍵の公開鍵で包むと、誰にも読み出せないデータになってしまう)
            try {
                newUnsealCipher();
            } catch (KeyInvalidatedException e) {
                ks.deleteEntry(ALIAS_SEAL);
            }
        }
        if (!ks.containsAlias(ALIAS_SEAL)) generateSealKey();
        Certificate cert = ks.getCertificate(ALIAS_SEAL);
        if (cert == null) throw new IllegalStateException("no seal key");
        // Keystore の公開鍵のまま使うと Keystore 側の制約(認証)が掛かる端末があるため、ソフトウェアの鍵に移す
        PublicKey pub = cert.getPublicKey();
        return KeyFactory.getInstance(pub.getAlgorithm()).generatePublic(new X509EncodedKeySpec(pub.getEncoded()));
    }

    private static void generateSealKey() throws Exception {
        Exception last = null;
        for (int size : new int[]{3072, 2048}) {
            KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(ALIAS_SEAL,
                    KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_ENCRYPT)
                    .setKeySize(size)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setUserAuthenticationRequired(true)
                    // 生体認証の登録が変わったら鍵を無効にする(他人の指紋を追加されても読めない)
                    .setInvalidatedByBiometricEnrollment(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // 有効時間 0 = 復号のたびに本人確認が必要
                b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG
                        | KeyProperties.AUTH_DEVICE_CREDENTIAL);
            }
            try {
                KeyPairGenerator g = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE);
                g.initialize(b.build());
                g.generateKeyPair();
                return;
            } catch (Exception e) {
                last = e;
                try {
                    keyStore().deleteEntry(ALIAS_SEAL);
                } catch (Exception ignored) {
                }
            }
        }
        throw last;
    }

    // ---- 小物 ----

    private static Map<String, String> clean(Map<String, String> values, String[] allowed) {
        Map<String, String> out = new HashMap<>();
        for (String k : allowed) {
            String v = values.get(k);
            if (v == null) continue;
            v = v.trim();
            if (v.isEmpty()) continue;
            // 異常に長い値は切り詰める(ページから受け取る値のため)
            if (v.length() > 1000) v = v.substring(0, 1000);
            out.put(k, v);
        }
        return out;
    }

    static String digitsOnly(@Nullable String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') sb.append(ch);
        }
        return sb.toString();
    }

    /** URL・origin からホスト名(小文字)。 */
    static String hostOf(@Nullable String origin) {
        if (origin == null) return "";
        try {
            String h = android.net.Uri.parse(origin).getHost();
            return h == null ? origin : h.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return origin;
        }
    }

    private static boolean equalsNullable(@Nullable String a, @Nullable String b) {
        if (a == null || a.isEmpty()) return b == null || b.isEmpty();
        return a.equals(b);
    }

    private static String newGuid() {
        return "{" + UUID.randomUUID() + "}";
    }

    private static String b64(byte[] b) {
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }
}
