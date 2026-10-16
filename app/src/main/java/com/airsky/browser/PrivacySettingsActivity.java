package com.airsky.browser;

import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.mozilla.geckoview.StorageController;

/**
 * プライバシーとセキュリティ(Chrome / Firefox と同じ項目に、Aozora のロックなどを加えたもの)。
 */
public class PrivacySettingsActivity extends PixelSettingsActivity {

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("プライバシーとセキュリティ");
    }

    @Override
    protected void onBuild() {
        header(Lang.t("プライバシー"));
        row(R.drawable.ic_ps_history, Lang.t("閲覧履歴データを削除"), Lang.t("履歴、Cookie、サイトデータ、キャッシュを削除します"),
                this::showClearData);
        boolean cookies = CookiePolicy.isCookiesAccepted(this);
        switchRow(R.drawable.ic_si_cookie, Lang.t("Cookie を受け入れる"), Lang.t("サイトによる Cookie の保存と読み取りを許可する"),
                cookies, on -> {
                    BrowserPrefs.put(this, "acceptCookies", on);
                    GeckoEngine.get(this).applyCookieSetting();
                    rebuild();
                });
        row(R.drawable.ic_si_cookie, Lang.t("サードパーティ Cookie"),
                CookiePolicy.isThirdPartyAllowed(this) ? Lang.t("サードパーティの Cookie が許可されています")
                        : Lang.t("サードパーティの Cookie がブロックされています"),
                () -> CookieSettingsActivity.open(this)).setEnabled(cookies);
        String[] tracking = {Lang.t("オフ"), Lang.t("標準(おすすめ)"), Lang.t("強力")};
        int t = BrowserPrefs.integer(this, BrowserPrefs.TRACKING, BrowserPrefs.TRACKING_STANDARD);
        row(R.drawable.ic_si_shield, Lang.t("トラッキング防止"), tracking[t], () -> choose(Lang.t("トラッキング防止"),
                new String[]{Lang.t("オフ\nトラッカーをブロックしません"),
                        Lang.t("標準(おすすめ)\nソーシャルメディアや広告のトラッカーをブロックします。ページは通常どおり表示されます"),
                        Lang.t("強力\nより多くのトラッカーやフィンガープリント採取をブロックします。一部のサイトが動作しない場合があります")},
                t, i -> {
                    BrowserPrefs.put(this, BrowserPrefs.TRACKING, i);
                    BrowserPrefs.apply(this);
                }));
        switchRow(R.drawable.ic_ps_privacy, Lang.t("「Do Not Track」リクエストを送信する"),
                Lang.t("閲覧時に、トラッキングしないよう求めるリクエストをサイトに送信します"),
                BrowserPrefs.bool(this, BrowserPrefs.DO_NOT_TRACK, false), on -> {
                    BrowserPrefs.put(this, BrowserPrefs.DO_NOT_TRACK, on);
                    BrowserPrefs.apply(this);
                });
        switchRow(R.drawable.ic_ps_privacy, Lang.t("グローバル プライバシー コントロール"),
                Lang.t("個人データを販売・共有しないようサイトに伝えます"),
                BrowserPrefs.bool(this, BrowserPrefs.GPC, false), on -> {
                    BrowserPrefs.put(this, BrowserPrefs.GPC, on);
                    BrowserPrefs.apply(this);
                });
        boolean preload = BrowserPrefs.bool(this, BrowserPrefs.PRELOAD, true);
        switchRow(R.drawable.ic_ps_bolt, Lang.t("ページをプリロードする"),
                preload ? Lang.t("標準プリロード: リンク先を先読みして、ページをすばやく開きます") : Lang.t("プリロードしません"),
                preload, on -> {
                    BrowserPrefs.put(this, BrowserPrefs.PRELOAD, on);
                    BrowserPrefs.apply(this);
                    rebuild();
                });

        header(Lang.t("ロック"));
        KeyguardManager km = getSystemService(KeyguardManager.class);
        boolean secure = km != null && km.isDeviceSecure();
        switchRow(R.drawable.ic_ts_incognito, Lang.t("アプリを離れたときにシークレット タブをロックする"),
                secure ? Lang.t("画面ロックを使用して、開いているシークレット タブを表示します")
                        : Lang.t("端末に画面ロックが設定されていないため使用できません"),
                secure && BrowserPrefs.bool(this, BrowserPrefs.LOCK_SECRET, false),
                on -> BrowserPrefs.put(this, BrowserPrefs.LOCK_SECRET, on)).setEnabled(secure);
        PasswordManager pm = new PasswordManager(this);
        row(R.drawable.ic_ps_lock, Lang.t("パスワードの設定"),
                pm.isPasswordSet() ? Lang.t("起動時にパスワードを求めます") : Lang.t("パスワードを設定し、起動時に入力を求めるようにできます"),
                this::openPasswordSettings);
        SharedPreferences app = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        switchRow(R.drawable.ic_ps_visibility_off, Lang.t("プレビューを非表示"),
                Lang.t("外部ディスプレイやタスク一覧から、アプリの内容が閲覧できないように遮断します"),
                app.getBoolean("useSecretSecurity", false), on -> {
                    app.edit().putBoolean("useSecretSecurity", on).apply();
                    if (on) {
                        new MaterialAlertDialogBuilder(this)
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setCancelable(false)
                                .setTitle(Lang.t("情報"))
                                .setMessage(Lang.t("スクリーンショットや画面録画を行う場合は、こちらの機能を無効にしてください。\nまた、パスワード同様ブラウザ以外の「ツール」等では機能しません。"))
                                .setPositiveButton("OK", (d, w) -> rebootDialog())
                                .show();
                    } else {
                        rebootDialog();
                    }
                });

        header(Lang.t("セキュリティ"));
        boolean safe = BrowserPrefs.bool(this, BrowserPrefs.SAFE_BROWSING, true);
        row(R.drawable.ic_ps_safety, Lang.t("セーフ ブラウジング"), safe ? Lang.t("標準保護機能: オン") : Lang.t("保護なし(おすすめしません)"),
                () -> choose(Lang.t("セーフ ブラウジング"), new String[]{
                        Lang.t("標準保護機能\n危険なサイト・ダウンロード・拡張機能から保護します"),
                        Lang.t("保護なし(おすすめしません)\n危険なサイトなどから保護されません")},
                        safe ? 0 : 1, i -> {
                            BrowserPrefs.put(this, BrowserPrefs.SAFE_BROWSING, i == 0);
                            BrowserPrefs.apply(this);
                        }));
        switchRow(R.drawable.ic_si_warning, Lang.t("セキュリティ警告"), Lang.t("サイトの安全性に問題がある場合に警告を表示する"),
                app.getBoolean("securityAlert", true), on -> app.edit().putBoolean("securityAlert", on).apply());
        String[] https = {Lang.t("オフ"), Lang.t("シークレット タブのみ"), Lang.t("すべてのタブ")};
        int h = BrowserPrefs.integer(this, BrowserPrefs.HTTPS_ONLY, BrowserPrefs.HTTPS_OFF);
        row(R.drawable.ic_ps_lock, Lang.t("常に安全な接続を使用する"), https[h], () -> choose(Lang.t("常に安全な接続を使用する"),
                new String[]{Lang.t("オフ"), Lang.t("シークレット タブのみ\nシークレット タブで HTTPS に切り替えます"),
                        Lang.t("すべてのタブ\n可能な限り HTTPS で接続し、安全でないサイトを開く前に警告します")},
                h, i -> {
                    BrowserPrefs.put(this, BrowserPrefs.HTTPS_ONLY, i);
                    BrowserPrefs.apply(this);
                }));
        row(R.drawable.ic_ps_dns, Lang.t("セキュア DNS を使用する"), dnsSummary(), this::chooseDns);
        row(R.drawable.ic_ps_delete, Lang.t("キャッシュを消去"), Lang.t("ローカルにキャッシュしたコンテンツを消去する"), () -> {
            GeckoEngine.get(this).clearCache();
            Toast.makeText(this, Lang.t("キャッシュを削除しました"), Toast.LENGTH_SHORT).show();
        });
        row(R.drawable.ic_si_cookie, Lang.t("Cookie をすべて消去"), Lang.t("すべてのブラウザ Cookie を消去する"), () ->
                confirm(Lang.t("AirSky ブラウザ内のすべてのサイトデータが削除されます"), Lang.t("Cookie をすべて消去します\n本当によろしいですか?"),
                        Lang.t("消去"), () -> GeckoEngine.get(this).clearCookies()));
    }

    // ------------------------------------------------------------------

    private String dnsSummary() {
        int dns = BrowserPrefs.integer(this, BrowserPrefs.SECURE_DNS, BrowserPrefs.DNS_DEFAULT);
        String provider = BrowserPrefs.DNS_PROVIDERS[Math.max(0, Math.min(BrowserPrefs.DNS_PROVIDERS.length - 1,
                BrowserPrefs.integer(this, BrowserPrefs.SECURE_DNS_PROVIDER, 0)))][0];
        switch (dns) {
            case BrowserPrefs.DNS_INCREASED: return Lang.t("強化(") + provider + ")";
            case BrowserPrefs.DNS_MAX: return Lang.t("最大(") + provider + ")";
            case BrowserPrefs.DNS_OFF: return Lang.t("オフ");
            default: return Lang.t("自動設定");
        }
    }

    private void chooseDns() {
        int dns = BrowserPrefs.integer(this, BrowserPrefs.SECURE_DNS, BrowserPrefs.DNS_DEFAULT);
        choose(Lang.t("セキュア DNS を使用する"), new String[]{
                Lang.t("自動設定\n使用できる場合はセキュア DNS を使い、使えない時は通常の DNS を使います"),
                Lang.t("強化\n指定したプロバイダのセキュア DNS を優先して使います"),
                Lang.t("最大\n常にセキュア DNS を使います。使えない時はサイトに接続しません"),
                Lang.t("オフ\n通常の DNS を使います")}, dns, i -> {
            BrowserPrefs.put(this, BrowserPrefs.SECURE_DNS, i);
            if (i == BrowserPrefs.DNS_INCREASED || i == BrowserPrefs.DNS_MAX) {
                String[] names = new String[BrowserPrefs.DNS_PROVIDERS.length];
                for (int k = 0; k < names.length; k++) names[k] = BrowserPrefs.DNS_PROVIDERS[k][0];
                choose(Lang.t("プロバイダを選択"), names, BrowserPrefs.integer(this, BrowserPrefs.SECURE_DNS_PROVIDER, 0),
                        p -> {
                            BrowserPrefs.put(this, BrowserPrefs.SECURE_DNS_PROVIDER, p);
                            BrowserPrefs.apply(this);
                        });
            } else {
                BrowserPrefs.apply(this);
            }
        });
    }

    /** 閲覧履歴データを削除(消すものを選ぶ)。 */
    private void showClearData() {
        String[] items = {Lang.t("閲覧履歴"), Lang.t("Cookie とサイトデータ"), Lang.t("キャッシュされた画像とファイル")};
        boolean[] checked = {true, true, true};
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("閲覧履歴データを削除"))
                .setMultiChoiceItems(items, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(Lang.t("データを削除"), (d, w) -> {
                    if (checked[0]) {
                        getSharedPreferences("WebViewTabs", MODE_PRIVATE).edit().putString("history", "[]").apply();
                    }
                    StorageController storage = GeckoEngine.get(this).getRuntime().getStorageController();
                    long flags = 0;
                    if (checked[1]) flags |= StorageController.ClearFlags.SITE_DATA;
                    if (checked[2]) flags |= StorageController.ClearFlags.ALL_CACHES;
                    if (flags != 0) storage.clearData(flags);
                    Toast.makeText(this, Lang.t("閲覧履歴データを削除しました"), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    private void openPasswordSettings() {
        PasswordManager pm = new PasswordManager(this);
        if (pm.isPasswordSet()) {
            Intent intent = new Intent(this, PasswordActivity.class);
            intent.putExtra("destination_activity", "LockSettingsActivity");
            intent.putExtra("requirePasswordOnFirstLaunch", false);
            intent.putExtra("usePasswordSkip", false);
            startActivity(intent);
        } else {
            startActivity(new Intent(this, LockSettingsActivity.class));
        }
    }

    private void rebootDialog() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(Lang.t("変更を反映するには再起動が必要です"))
                .setCancelable(false)
                .setPositiveButton(Lang.t("キャンセル"), null)
                .setNegativeButton(Lang.t("再起動"), (d, w) -> {
                    getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putBoolean("rebootApp", true).apply();
                    finish();
                    overridePendingTransition(0, 0);
                })
                .setNeutralButton(Lang.t("後で"), null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getSharedPreferences("AppPrefs", MODE_PRIVATE).getBoolean("rebootApp", false)) {
            finish();
            overridePendingTransition(0, 0);
        }
    }
}
