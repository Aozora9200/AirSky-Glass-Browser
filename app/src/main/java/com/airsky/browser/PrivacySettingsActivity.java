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

    /** 安全チェックから開いた時に目立たせる行。 */
    static final String FOCUS_HTTPS = "https";
    static final String FOCUS_PASSWORD = "password";

    /** 「常に安全な接続を使用する」の選択肢の並び(保存する値)。 */
    private static final int[] HTTPS_ORDER = {BrowserPrefs.HTTPS_OFF, BrowserPrefs.HTTPS_SECRET,
            BrowserPrefs.HTTPS_MAIN, BrowserPrefs.HTTPS_ALWAYS};

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
                this::openPasswordSettings).key(FOCUS_PASSWORD);
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
        row(R.drawable.ic_ps_safety, Lang.t("セーフ ブラウジング"), safe ? Lang.t("標準保護機能: オン") : Lang.t("保護なし(非推奨)"),
                () -> choose(Lang.t("セーフ ブラウジング"), new String[]{
                        Lang.t("標準保護機能\n危険なサイト・ダウンロード・拡張機能から保護します"),
                        Lang.t("保護なし(非推奨)\n危険なサイトなどから保護されません")},
                        safe ? 0 : 1, i -> {
                            BrowserPrefs.put(this, BrowserPrefs.SAFE_BROWSING, i == 0);
                            BrowserPrefs.apply(this);
                        }));
        switchRow(R.drawable.ic_si_warning, Lang.t("セキュリティ警告"), Lang.t("サイトの安全性に問題がある場合に警告を表示する"),
                app.getBoolean("securityAlert", true), on -> app.edit().putBoolean("securityAlert", on).apply());
        String[] https = {Lang.t("オフ"), Lang.t("シークレット タブのみ"), Lang.t("メインセッションのみ"), Lang.t("すべてのタブ")};
        int stored = BrowserPrefs.integer(this, BrowserPrefs.HTTPS_ONLY, BrowserPrefs.HTTPS_OFF);
        int found = 0;
        for (int k = 0; k < HTTPS_ORDER.length; k++) if (HTTPS_ORDER[k] == stored) found = k;
        final int h = found;
        row(R.drawable.ic_ps_lock, Lang.t("常に安全な接続を使用する"), https[h], () -> choose(Lang.t("常に安全な接続を使用する"),
                new String[]{Lang.t("オフ\nHTTP のサイトもそのまま開きます"),
                        Lang.t("シークレット タブのみ\nシークレット タブで HTTPS に切り替えます"),
                        Lang.t("メインセッションのみ\nシークレット タブ以外で HTTPS に切り替えます。シークレット タブでは HTTP を許可します"),
                        Lang.t("すべてのタブ\n可能な限り HTTPS で接続し、安全でないサイトを開く前に警告します")},
                h, i -> {
                    BrowserPrefs.put(this, BrowserPrefs.HTTPS_ONLY, HTTPS_ORDER[i]);
                    BrowserPrefs.apply(this);
                })).key(FOCUS_HTTPS);
        row(R.drawable.ic_ps_dns, Lang.t("セキュア DNS を使用する"), dnsSummary(), this::chooseDns);
        row(R.drawable.ic_ps_delete, Lang.t("キャッシュを消去"), Lang.t("ローカルにキャッシュしたコンテンツを消去する"), () -> {
            GeckoEngine.get(this).clearCache();
            Toast.makeText(this, Lang.t("キャッシュを削除しました"), Toast.LENGTH_SHORT).show();
        });
        row(R.drawable.ic_si_cookie, Lang.t("Cookie をすべて消去"), Lang.t("すべてのサイトの Cookie だけを消去する"), () ->
                confirm(Lang.t("Cookie をすべて消去しますか?"),
                        Lang.t("すべてのサイトの Cookie が削除され、多くのサイトからログアウトされます。\n\n"
                                + "閲覧履歴・キャッシュ・ブックマーク・保存したパスワード、"
                                + "サイトのその他のデータ(ローカル ストレージなど)は削除されません。"),
                        Lang.t("消去"), () -> GeckoEngine.get(this).clearCookies()));
    }

    // ------------------------------------------------------------------

    private String dnsSummary() {
        int dns = BrowserPrefs.integer(this, BrowserPrefs.SECURE_DNS, BrowserPrefs.DNS_DEFAULT);
        String provider = BrowserPrefs.dnsProviderName(this);
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
                chooseDnsProvider();
            } else {
                BrowserPrefs.apply(this);
            }
        });
    }

    /** プロバイダを選ぶ(それぞれの違いを説明に書く)。最後の「カスタム」は URL を入力する。 */
    private void chooseDnsProvider() {
        int n = BrowserPrefs.DNS_PROVIDERS.length;
        String[] labels = new String[n + 1];
        for (int k = 0; k < n; k++) {
            labels[k] = Lang.t(BrowserPrefs.DNS_PROVIDERS[k][0]) + "\n" + Lang.t(BrowserPrefs.DNS_PROVIDERS[k][2]);
        }
        String custom = BrowserPrefs.customDnsUrl(this);
        labels[n] = Lang.t("カスタム") + "\n" + (custom.isEmpty()
                ? Lang.t("DNS over HTTPS のサーバーの URL を自分で入力します") : custom);
        int current = BrowserPrefs.integer(this, BrowserPrefs.SECURE_DNS_PROVIDER, 0);
        int checked = current == BrowserPrefs.DNS_PROVIDER_CUSTOM ? n : Math.max(0, Math.min(n - 1, current));
        choose(Lang.t("プロバイダを選択"), labels, checked, p -> {
            if (p == n) {
                askCustomDns();
                return;
            }
            BrowserPrefs.put(this, BrowserPrefs.SECURE_DNS_PROVIDER, p);
            BrowserPrefs.apply(this);
        });
    }

    /** カスタムのプロバイダ(DoH サーバーの URL)を入力する。 */
    private void askCustomDns() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://dns.example.com/dns-query");
        input.setText(BrowserPrefs.customDnsUrl(this));
        input.setSelection(input.length());
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("カスタム プロバイダ"))
                .setMessage(Lang.t("DNS over HTTPS (DoH) に対応したサーバーの URL を入力してください"))
                .setView(box)
                .setPositiveButton(Lang.t("保存"), null)
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
        // 正しくない URL の時はダイアログを閉じずに知らせる
        d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String url = input.getText().toString().trim();
            android.net.Uri u = android.net.Uri.parse(url);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null || u.getHost().isEmpty()) {
                input.setError(Lang.t("https:// から始まる URL を入力してください"));
                return;
            }
            BrowserPrefs.put(this, BrowserPrefs.SECURE_DNS_CUSTOM, url);
            BrowserPrefs.put(this, BrowserPrefs.SECURE_DNS_PROVIDER, BrowserPrefs.DNS_PROVIDER_CUSTOM);
            BrowserPrefs.apply(this);
            d.dismiss();
            rebuild();
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
                .setMessage(Lang.t("変更を反映するには、アプリの再起動が必要です"))
                .setCancelable(false)
                .setPositiveButton(Lang.t("再起動"), (d, w) -> {
                    getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putBoolean("rebootApp", true).apply();
                    finish();
                    overridePendingTransition(0, 0);
                })
                .setNegativeButton(Lang.t("後で"), null)
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
