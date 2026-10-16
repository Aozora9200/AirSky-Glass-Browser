package com.airsky.browser;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 設定のトップ(Pixel の設定風)。
 * 上に「設定項目を検索」、その下に 基本設定 / 詳細設定 / Aozora / 情報 のグループを並べる。
 * トップの項目だけ、アイコンを色付きの丸に入れて見分けやすくしている。
 */
public class SettingsActivity extends PixelSettingsActivity {

    private EditText search;
    private String query = "";

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("設定");
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setAction(R.drawable.ic_menu_help, Lang.t("ヘルプ"), v -> open(HelpCenterActivity.class));

        findViewById(R.id.ps_search_box).setVisibility(View.VISIBLE);
        search = findViewById(R.id.ps_search);
        View clear = findViewById(R.id.ps_search_clear);
        clear.setOnClickListener(v -> search.setText(""));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                clear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                rebuild();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences setupprefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (setupprefs.getBoolean("rebootApp", false)) {
            finish();
            overridePendingTransition(0, 0);
        }
        // 使っていないサイトの権限を自動的に外す
        SitePermissions.pruneUnused(this);
    }

    @Override
    protected void onBuild() {
        if (!query.isEmpty()) {
            buildSearchResults();
            return;
        }

        header(Lang.t("基本設定"));
        String[] engine = BrowserPrefs.engine(this);
        row(R.drawable.ic_ps_search, Lang.t("検索エンジン"), engine[1], () -> open(SearchEngineSettingsActivity.class))
                .tone(R.color.ps_tone_blue_bg, R.color.ps_tone_blue_fg);
        row(R.drawable.ic_ps_privacy, Lang.t("プライバシーとセキュリティ"), Lang.t("Cookie、トラッキング防止、安全な接続"),
                () -> open(PrivacySettingsActivity.class))
                .tone(R.color.ps_tone_green_bg, R.color.ps_tone_green_fg);
        row(R.drawable.ic_ps_key, Lang.t("自動入力とパスワード"), autofillSummary(),
                () -> open(AutofillSettingsActivity.class))
                .tone(R.color.ps_tone_purple_bg, R.color.ps_tone_purple_fg);
        row(R.drawable.ic_ps_safety, Lang.t("安全チェック"), Lang.t("AirSky の安全性をまとめて確認"),
                () -> open(SafetyCheckActivity.class))
                .tone(R.color.ps_tone_green_bg, R.color.ps_tone_green_fg);
        row(R.drawable.ic_ps_browser, Lang.t("デフォルトのブラウザ"), Lang.t("リンクを AirSky で開く"), this::openDefaultApps)
                .tone(R.color.ps_tone_teal_bg, R.color.ps_tone_teal_fg);

        header(Lang.t("詳細設定"));
        row(R.drawable.ic_ps_tab, Lang.t("タブとタブグループ"), InactiveTabs.summary(this),
                () -> InactiveTabsSettingsActivity.open(this))
                .tone(R.color.ps_tone_blue_bg, R.color.ps_tone_blue_fg);
        row(R.drawable.ic_ps_home, Lang.t("ホームページ"), BrowserPrefs.homeEnabled(this) ? Lang.t("オン") : Lang.t("オフ"),
                () -> open(HomepageSettingsActivity.class))
                .tone(R.color.ps_tone_orange_bg, R.color.ps_tone_orange_fg);
        row(R.drawable.ic_ps_bell, Lang.t("通知"), Lang.t("AirSky からの通知を管理"), this::openNotificationSettings)
                .tone(R.color.ps_tone_red_bg, R.color.ps_tone_red_fg);
        row(R.drawable.ic_ps_palette, Lang.t("デザイン"), Lang.t("テーマ、ボタン、ツールバーのショートカット"),
                () -> openForResult(DesignSettingsActivity.class))
                .tone(R.color.ps_tone_pink_bg, R.color.ps_tone_pink_fg);
        row(R.drawable.ic_ps_accessibility, Lang.t("ユーザー補助機能"), Lang.t("文字の大きさ、ズーム、字幕"),
                () -> open(AccessibilitySettingsActivity.class))
                .tone(R.color.ps_tone_purple_bg, R.color.ps_tone_purple_fg);
        row(R.drawable.ic_ps_tune, Lang.t("サイトの設定"), Lang.t("位置情報、カメラ、JavaScript などの権限"),
                () -> open(SiteSettingsActivity.class))
                .tone(R.color.ps_tone_teal_bg, R.color.ps_tone_teal_fg);
        row(R.drawable.ic_ps_language, Lang.t("言語"), Lang.t("ウェブページの言語と翻訳"), () -> open(LanguageSettingsActivity.class))
                .tone(R.color.ps_tone_blue_bg, R.color.ps_tone_blue_fg);
        row(R.drawable.ic_ps_download, Lang.t("ダウンロード"), Lang.t("保存先と履歴"), () -> open(DownloadSettingsActivity.class))
                .tone(R.color.ps_tone_green_bg, R.color.ps_tone_green_fg);

        header("AirSky");
        row(R.drawable.ic_ps_tune, Lang.t("全般"), Lang.t("スワイプで再読み込み、起動画面、振動など"),
                () -> open(GeneralSettingsActivity.class))
                .tone(R.color.ps_tone_gray_bg, R.color.ps_tone_gray_fg);
        row(R.drawable.ic_ps_desktop, Lang.t("ブラウザの動作"), Lang.t("デスクトップモード、ユーザーエージェント、バックグラウンド再生"),
                () -> open(BrowserBehaviorSettingsActivity.class))
                .tone(R.color.ps_tone_gray_bg, R.color.ps_tone_gray_fg);
        row(R.drawable.ic_tool_gamepad, Lang.t("コントローラー"), Lang.t("ボタンの割り当て、仮想マウス、カーソルの速さ"),
                () -> open(ControllerSettingsActivity.class))
                .tone(R.color.ps_tone_gray_bg, R.color.ps_tone_gray_fg);
        row(R.drawable.ic_ps_data, Lang.t("データ使用状況"), null, () -> open(DataUsage.class))
                .tone(R.color.ps_tone_gray_bg, R.color.ps_tone_gray_fg);
        row(R.drawable.ic_ps_swap, Lang.t("データの引っ越し"), Lang.t("別の端末の AirSky Browser にデータを移す"),
                () -> open(DeviceMigrationActivity.class))
                .tone(R.color.ps_tone_teal_bg, R.color.ps_tone_teal_fg);
        row(R.drawable.ic_ps_storage, Lang.t("ストレージ"), Lang.t("アプリの情報を開く"), this::openAppDetails)
                .tone(R.color.ps_tone_gray_bg, R.color.ps_tone_gray_fg);
        row(R.drawable.ic_ps_history, Lang.t("初期設定にリセット"), Lang.t("設定をデフォルト値に戻す"), this::confirmReset)
                .tone(R.color.ps_tone_gray_bg, R.color.ps_tone_gray_fg);

        header(Lang.t("情報"));
        row(R.drawable.ic_ps_phone, Lang.t("AirSky Browser について"), getString(R.string.version) + " (" + getString(R.string.build) + ")",
                () -> open(AppInfoActivity.class))
                .tone(R.color.ps_tone_blue_bg, R.color.ps_tone_blue_fg);
    }

    // ------------------------------------------------------------------
    // 検索
    // ------------------------------------------------------------------

    /** 検索できる設定項目(見出し, 場所, 開く画面)。 */
    private static final Object[][] INDEX = {
            {Lang.t("検索エンジン"), Lang.t("基本設定"), SearchEngineSettingsActivity.class},
            {Lang.t("プライバシーとセキュリティ"), Lang.t("基本設定"), PrivacySettingsActivity.class},
            {Lang.t("閲覧履歴データを削除"), Lang.t("プライバシーとセキュリティ"), PrivacySettingsActivity.class},
            {Lang.t("Cookie を受け入れる"), Lang.t("プライバシーとセキュリティ"), PrivacySettingsActivity.class},
            {Lang.t("サードパーティ Cookie"), Lang.t("プライバシーとセキュリティ"), CookieSettingsActivity.class},
            {Lang.t("トラッキング防止"), Lang.t("プライバシーとセキュリティ"), PrivacySettingsActivity.class},
            {"Do Not Track", Lang.t("プライバシーとセキュリティ"), PrivacySettingsActivity.class},
            {Lang.t("グローバル プライバシー コントロール"), Lang.t("プライバシーとセキュリティ"), PrivacySettingsActivity.class},
            {Lang.t("ページをプリロードする"), Lang.t("プライバシーとセキュリティ"), PrivacySettingsActivity.class},
            {Lang.t("シークレット タブをロック"), Lang.t("プライバシーとセキュリティ"), PrivacySettingsActivity.class},
            {Lang.t("パスワードの設定"), Lang.t("プライバシーとセキュリティ"), PrivacySettingsActivity.class},
            {Lang.t("プレビューを非表示"), Lang.t("プライバシーとセキュリティ"), PrivacySettingsActivity.class},
            {Lang.t("セーフ ブラウジング"), Lang.t("プライバシーとセキュリティ"), PrivacySettingsActivity.class},
            {Lang.t("セキュリティ警告"), Lang.t("プライバシーとセキュリティ"), PrivacySettingsActivity.class},
            {Lang.t("常に安全な接続を使用する (HTTPS)"), Lang.t("プライバシーとセキュリティ"), PrivacySettingsActivity.class},
            {Lang.t("セキュア DNS"), Lang.t("プライバシーとセキュリティ"), PrivacySettingsActivity.class},
            {Lang.t("キャッシュを消去"), Lang.t("プライバシーとセキュリティ"), PrivacySettingsActivity.class},
            {Lang.t("安全チェック"), Lang.t("基本設定"), SafetyCheckActivity.class},
            {Lang.t("自動入力とパスワード"), Lang.t("基本設定"), AutofillSettingsActivity.class},
            {Lang.t("保存したパスワード"), Lang.t("自動入力とパスワード"), AutofillSettingsActivity.class},
            {Lang.t("パスワード マネージャー"), Lang.t("自動入力とパスワード"), AutofillSettingsActivity.class},
            {Lang.t("お支払い方法 (クレジットカード)"), Lang.t("自動入力とパスワード"), AutofillSettingsActivity.class},
            {Lang.t("住所"), Lang.t("自動入力とパスワード"), AutofillSettingsActivity.class},
            {Lang.t("非アクティブなタブ"), Lang.t("タブとタブグループ"), InactiveTabsSettingsActivity.class},
            {Lang.t("ホームページ"), Lang.t("詳細設定"), HomepageSettingsActivity.class},
            {Lang.t("デザイン"), Lang.t("詳細設定"), DesignSettingsActivity.class},
            {Lang.t("テーマ (ライト / ダーク)"), Lang.t("デザイン"), DesignSettingsActivity.class},
            {Lang.t("メニューボタン"), Lang.t("デザイン"), DesignSettingsActivity.class},
            {Lang.t("矢印ボタン"), Lang.t("デザイン"), DesignSettingsActivity.class},
            {Lang.t("ツールバーのショートカット"), Lang.t("デザイン"), DesignSettingsActivity.class},
            {Lang.t("ウェブページをダークモードで表示"), Lang.t("デザイン"), DesignSettingsActivity.class},
            {Lang.t("ユーザー補助機能"), Lang.t("詳細設定"), AccessibilitySettingsActivity.class},
            {Lang.t("コントローラー"), "AirSky", ControllerSettingsActivity.class},
            {Lang.t("ボタンの割り当て"), Lang.t("コントローラー"), ControllerSettingsActivity.class},
            {Lang.t("仮想マウス"), Lang.t("コントローラー"), ControllerSettingsActivity.class},
            {Lang.t("デフォルトのズーム (文字の大きさ)"), Lang.t("ユーザー補助機能"), AccessibilitySettingsActivity.class},
            {Lang.t("強制的にズームを有効にする"), Lang.t("ユーザー補助機能"), AccessibilitySettingsActivity.class},
            {Lang.t("ズームボタンの表示"), Lang.t("ユーザー補助機能"), AccessibilitySettingsActivity.class},
            {Lang.t("字幕"), Lang.t("ユーザー補助機能"), AccessibilitySettingsActivity.class},
            {Lang.t("タップを表示"), Lang.t("ユーザー補助機能"), AccessibilitySettingsActivity.class},
            {Lang.t("テキスト カーソル"), Lang.t("ユーザー補助機能"), AccessibilitySettingsActivity.class},
            {Lang.t("サイトの設定"), Lang.t("詳細設定"), SiteSettingsActivity.class},
            {Lang.t("すべてのサイト"), Lang.t("サイトの設定"), SitePermissionActivity.class, -1},
            {Lang.t("位置情報"), Lang.t("サイトの設定"), SitePermissionActivity.class, SitePermissions.LOCATION},
            {Lang.t("カメラ"), Lang.t("サイトの設定"), SitePermissionActivity.class, SitePermissions.CAMERA},
            {Lang.t("マイク"), Lang.t("サイトの設定"), SitePermissionActivity.class, SitePermissions.MICROPHONE},
            {Lang.t("通知 (サイト)"), Lang.t("サイトの設定"), SitePermissionActivity.class, SitePermissions.NOTIFICATIONS},
            {"JavaScript", Lang.t("サイトの設定"), SitePermissionActivity.class, SitePermissions.JAVASCRIPT},
            {Lang.t("ポップアップとリダイレクト"), Lang.t("サイトの設定"), SitePermissionActivity.class, SitePermissions.POPUPS},
            {Lang.t("自動再生 (音声)"), Lang.t("サイトの設定"), SitePermissionActivity.class, SitePermissions.AUTOPLAY},
            {Lang.t("保護されたコンテンツ"), Lang.t("サイトの設定"), SitePermissionActivity.class, SitePermissions.DRM},
            {Lang.t("画像を表示"), Lang.t("サイトの設定"), SiteSettingsActivity.class},
            {Lang.t("PC 版サイト"), Lang.t("サイトの設定"), SiteSettingsActivity.class},
            {Lang.t("言語"), Lang.t("詳細設定"), LanguageSettingsActivity.class},
            {Lang.t("翻訳"), Lang.t("言語"), LanguageSettingsActivity.class},
            {Lang.t("ダウンロード"), Lang.t("詳細設定"), DownloadSettingsActivity.class},
            {Lang.t("全般"), "AirSky", GeneralSettingsActivity.class},
            {Lang.t("スワイプで再読み込み"), Lang.t("全般"), GeneralSettingsActivity.class},
            {Lang.t("起動画面・起動音"), Lang.t("全般"), GeneralSettingsActivity.class},
            {Lang.t("振動"), Lang.t("全般"), GeneralSettingsActivity.class},
            {Lang.t("バッテリー残量低下の通知"), Lang.t("全般"), GeneralSettingsActivity.class},
            {Lang.t("下部メニューバーを自動的に隠す"), Lang.t("全般"), GeneralSettingsActivity.class},
            {Lang.t("更新情報を表示"), Lang.t("全般"), GeneralSettingsActivity.class},
            {Lang.t("すべてをデスクトップモードにする"), Lang.t("ブラウザの動作"), BrowserBehaviorSettingsActivity.class},
            {Lang.t("CT3UA / ガラケーUA"), Lang.t("ブラウザの動作"), BrowserBehaviorSettingsActivity.class},
            {Lang.t("バックグラウンド再生"), Lang.t("ブラウザの動作"), BrowserBehaviorSettingsActivity.class},
            {Lang.t("URL スキーム"), Lang.t("ブラウザの動作"), BrowserBehaviorSettingsActivity.class},
            {Lang.t("データ使用状況"), "AirSky", DataUsage.class},
            {Lang.t("データの引っ越し"), "AirSky", DeviceMigrationActivity.class},
            {Lang.t("Bluetooth・QR コード・Quick Share で引っ越し"), Lang.t("データの引っ越し"), DeviceMigrationActivity.class},
            {Lang.t("AirSky Browser について"), Lang.t("情報"), AppInfoActivity.class},
    };

    private void buildSearchResults() {
        String q = query.toLowerCase(Locale.ROOT);
        List<Object[]> hits = new ArrayList<>();
        for (Object[] e : INDEX) {
            String title = ((String) e[0]).toLowerCase(Locale.ROOT);
            String where = ((String) e[1]).toLowerCase(Locale.ROOT);
            if (title.contains(q) || where.contains(q)) hits.add(e);
        }
        if (hits.isEmpty()) {
            note(Lang.t("「") + query + Lang.t("」に一致する設定は見つかりませんでした"));
            return;
        }
        header(Lang.t("検索結果"));
        for (Object[] e : hits) {
            Class<?> target = (Class<?>) e[2];
            row(0, (String) e[0], (String) e[1], () -> {
                if (target == DesignSettingsActivity.class) {
                    openForResult(target);
                } else if (e.length > 3) {
                    SitePermissionActivity.open(this, (Integer) e[3]);
                } else {
                    open(target);
                }
            });
        }
    }

    // ------------------------------------------------------------------

    private String autofillSummary() {
        if (!CredentialVault.isSupported(this)) return Lang.t("この端末では使用できません");
        return CredentialVault.isMasterOn(this) ? Lang.t("パスワード、お支払い方法、住所(本人確認で自動入力)") : Lang.t("オフ");
    }

    private void openForResult(Class<?> activity) {
        startActivityForResult(new Intent(this, activity), 1001);
    }

    private void openDefaultApps() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openAppDetails() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void openNotificationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void confirmReset() {
        confirm(Lang.t("初期設定にリセット"), Lang.t("設定をデフォルト値に戻しますか?\n(ブックマーク・履歴・タブは消えません)"), Lang.t("リセット"), () -> {
            SharedPreferences.Editor editor = getSharedPreferences("AppPrefs", MODE_PRIVATE).edit();
            editor.putBoolean("javaScript", true);
            editor.putBoolean("popupBlock", false);
            editor.putBoolean("intentJump", true);
            editor.putBoolean("ProgressBarAnimation", true);
            editor.putBoolean("hidebottom", true);
            editor.putBoolean("batteryalert", true);
            editor.putBoolean("zoomButton", true);
            editor.putBoolean("bootScreen", true);
            editor.putBoolean("bootSound", true);
            editor.putBoolean("touchEffect", true);
            editor.putBoolean("securityAlert", true);
            editor.putBoolean("acceptCookies", true);
            editor.putBoolean("geoLocation", true);
            editor.putBoolean("isSwipeReload", true);
            for (String k : new String[]{BrowserPrefs.DO_NOT_TRACK, BrowserPrefs.GPC, BrowserPrefs.PRELOAD,
                    BrowserPrefs.TRACKING, BrowserPrefs.SAFE_BROWSING, BrowserPrefs.HTTPS_ONLY,
                    BrowserPrefs.SECURE_DNS, BrowserPrefs.SECURE_DNS_PROVIDER, BrowserPrefs.JS_OPTIMIZE,
                    BrowserPrefs.LOCK_SECRET, BrowserPrefs.FONT_SCALE, BrowserPrefs.FONT_AUTO,
                    BrowserPrefs.FORCE_ZOOM, BrowserPrefs.DOUBLE_TAP_ZOOM, BrowserPrefs.CARET,
                    BrowserPrefs.TRANSLATE_OFFER, BrowserPrefs.TRANSLATE_AUTO, BrowserPrefs.PAGE_LANGUAGE, BrowserPrefs.HOME_ENABLED,
                    BrowserPrefs.HOME_CUSTOM, BrowserPrefs.HOME_URL, BrowserPrefs.TOOLBAR_SHORTCUT}) {
                editor.remove(k);
            }
            editor.apply();
            getSharedPreferences("AdvancedBrowserPrefs", MODE_PRIVATE).edit()
                    .putBoolean("img_block_enabled", false)
                    .putBoolean("deskua_enabled", false)
                    .putBoolean("ua_enabled", false)
                    .putBoolean("ct3ua_enabled", false)
                    .apply();
            BrowserPrefs.apply(this);
            android.widget.Toast.makeText(this, Lang.t("設定をリセットしました"), android.widget.Toast.LENGTH_SHORT).show();
            rebuild();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // デザインの「再起動」: このまま閉じてブラウザ側で再起動する
        if (getSharedPreferences("AppPrefs", MODE_PRIVATE).getBoolean("rebootApp", false)) {
            finish();
            overridePendingTransition(0, 0);
        }
    }

    @Override
    public void onBackPressed() {
        if (!query.isEmpty()) {
            search.setText("");
            return;
        }
        Intent result = new Intent();
        result.putExtra("action", "recreate");
        setResult(RESULT_OK, result);
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }
}
