package com.airsky.browser;

import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

/**
 * 起動時のロック(なし / PIN / パスワード / 生体認証)と、ロックの細かい設定。
 */
public class LockSettingsActivity extends PixelSettingsActivity {

    private static final String STARTUP_PASSWORD = "startup_password";
    private static final String PREF_USE_PIN = "use_pin";

    private static final String PREF_SKIP_MINUTES =
            "password_skip_minutes";

    private static final String PREF_SKIP_MINUTES_BACKUP =
            "password_skip_minutes_backup";

    private static final int[] SKIP_MINUTES = {1, 5, 10, 15, 30, 60};

    private PasswordManager passwordManager;
    private SharedPreferences appPrefs;

    /** 今のロックで本人確認できたら、生体認証に切り替える。 */
    private final ActivityResultLauncher<Intent> confirmForBiometric =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    enrollBiometric();
                }
            });

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("パスワードの設定");
    }

    @Override
    protected void onBuild() {
        passwordManager = new PasswordManager(this);
        appPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);

        // 初回のデフォルト値
        if (!appPrefs.contains(PREF_SKIP_MINUTES)) {
            appPrefs.edit()
                    .putInt(PREF_SKIP_MINUTES, 1)
                    .putInt(PREF_SKIP_MINUTES_BACKUP, 1)
                    .apply();
        }

        boolean locked = passwordManager.isPasswordSet();
        boolean biometric = locked && passwordManager.isBiometricSet();
        boolean pin = locked && !biometric && appPrefs.getBoolean(PREF_USE_PIN, false);
        boolean password = locked && !biometric && !pin;

        header(Lang.t("ロックの種類"));
        radioRow(R.drawable.ic_ps_none, Lang.t("なし"), Lang.t("ロックしません"), !locked, () -> {
            if (!locked) return;
            confirm(Lang.t("パスワード保護機能を無効にしますか?"),
                    Lang.t("パスワードがないと、パスワード保護機能は利用できません。"),
                    Lang.t("無効にする"), () -> {
                        passwordManager.clearAll();
                        appPrefs.edit().putBoolean(PREF_USE_PIN, false).apply();
                        rebuild();
                    });
        });
        radioRow(R.drawable.ic_ps_dialpad, "PIN", pin ? Lang.t("PIN を変更する") : Lang.t("最大 8 桁の数字で解除します"), pin,
                () -> openSetting(PinSettingActivity.class, "PinSettingActivity"));
        radioRow(R.drawable.ic_ps_key, Lang.t("パスワード"), password ? Lang.t("パスワードを変更する") : Lang.t("文字・数字・記号で解除します"),
                password, () -> openSetting(PasswordSettingActivity.class, "PasswordSettingActivity"));
        boolean available = BiometricLock.isAvailable(this);
        Row bio = radioRow(R.drawable.ic_ps_fingerprint, Lang.t("生体認証"),
                available || biometric ? Lang.t("指紋や顔など、端末に登録した生体認証で解除します")
                        : BiometricLock.unavailableReason(this),
                biometric, () -> {
                    if (!biometric) chooseBiometric();
                });
        bio.setEnabled(available || biometric);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            note(Lang.t("生体認証を使用できない時は、端末の画面ロック(PIN・パターンなど)でも解除できます。"));
        }

        header(Lang.t("オプション"));
        switchRow(R.drawable.ic_ps_lock, Lang.t("起動時にロックを要求する(推奨)"),
                Lang.t("AirSky を起動した時やアプリに戻った時に、ロックの解除を求めます"),
                appPrefs.getBoolean(STARTUP_PASSWORD, true),
                on -> appPrefs.edit().putBoolean(STARTUP_PASSWORD, on).apply())
                .setEnabled(locked);

        int skip = appPrefs.getInt(PREF_SKIP_MINUTES, 1);
        int backup = appPrefs.getInt(PREF_SKIP_MINUTES_BACKUP, 1);
        int shown = skip > 0 ? skip : (backup > 0 ? backup : 1);
        switchRow(R.drawable.ic_ps_update, Lang.t("認証後の一定時間はロックをスキップする"),
                skip > 0 ? Lang.t("解除してから ") + skip + Lang.t(" 分間は、再度ロックの解除を求めません")
                        : Lang.t("毎回ロックの解除を求めます"),
                skip > 0, this::setSkipEnabled)
                .setEnabled(locked);
        row(R.drawable.ic_ps_history, Lang.t("スキップする時間"), shown + Lang.t(" 分"), this::chooseSkipTime)
                .setEnabled(locked && skip > 0);
    }

    // ------------------------------------------------------------------

    /** PIN・パスワードの設定画面を開く(ロック中なら先に今のロックを解除してもらう)。 */
    private void openSetting(Class<?> activity, String name) {
        if (passwordManager.isPasswordSet()) {
            Intent intent = new Intent(this, PasswordActivity.class);
            intent.putExtra("destination_activity", name);
            intent.putExtra("requirePasswordOnFirstLaunch", false);
            intent.putExtra("usePasswordSkip", false);
            startActivity(intent);
        } else {
            startActivity(new Intent(this, activity));
        }
    }

    private void chooseBiometric() {
        if (!BiometricLock.isAvailable(this)) {
            Toast.makeText(this, BiometricLock.unavailableReason(this), Toast.LENGTH_LONG).show();
            return;
        }
        if (passwordManager.isPasswordSet()) {
            // 今の PIN・パスワードで本人確認してから切り替える
            Intent intent = new Intent(this, PasswordActivity.class);
            intent.putExtra("requirePasswordOnFirstLaunch", false);
            intent.putExtra("usePasswordSkip", false);
            intent.putExtra("return_to_caller", true);
            confirmForBiometric.launch(intent);
        } else {
            enrollBiometric();
        }
    }

    /** 生体認証が通ることを確かめてから、生体認証のロックにする。 */
    private void enrollBiometric() {
        BiometricLock.authenticate(this, Lang.t("生体認証 を確認"), new BiometricLock.Callback() {
            @Override
            public void onSuccess() {
                passwordManager.setBiometric();
                appPrefs.edit().putBoolean(PREF_USE_PIN, false).apply();
                Toast.makeText(LockSettingsActivity.this, Lang.t("生体認証 を設定しました"), Toast.LENGTH_SHORT).show();
                rebuild();
            }

            @Override
            public void onError(@NonNull CharSequence message, boolean canceled) {
                if (!canceled) {
                    Toast.makeText(LockSettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setSkipEnabled(boolean on) {
        if (on) {
            // OFF にする前の値を戻す
            int backup = appPrefs.getInt(PREF_SKIP_MINUTES_BACKUP, 1);
            if (backup <= 0) backup = 1;
            appPrefs.edit().putInt(PREF_SKIP_MINUTES, backup).apply();
        } else {
            int current = appPrefs.getInt(PREF_SKIP_MINUTES, 1);
            SharedPreferences.Editor e = appPrefs.edit();
            if (current > 0) e.putInt(PREF_SKIP_MINUTES_BACKUP, current);
            e.putInt(PREF_SKIP_MINUTES, 0).apply();
        }
        rebuild();
    }

    private void chooseSkipTime() {
        int current = appPrefs.getInt(PREF_SKIP_MINUTES, 1);
        String[] labels = new String[SKIP_MINUTES.length];
        int checked = 0;
        for (int i = 0; i < SKIP_MINUTES.length; i++) {
            labels[i] = SKIP_MINUTES[i] + Lang.t(" 分");
            if (SKIP_MINUTES[i] == current) checked = i;
        }
        choose(Lang.t("スキップする時間"), labels, checked, i -> appPrefs.edit()
                .putInt(PREF_SKIP_MINUTES, SKIP_MINUTES[i])
                .putInt(PREF_SKIP_MINUTES_BACKUP, SKIP_MINUTES[i])
                .apply());
    }
}
