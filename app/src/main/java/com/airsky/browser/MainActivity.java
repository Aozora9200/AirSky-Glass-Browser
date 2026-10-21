package com.airsky.browser;

import android.annotation.SuppressLint;

import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import androidx.appcompat.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.http.SslCertificate;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Environment;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import android.os.Vibrator;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.LruCache;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import androidx.annotation.Nullable;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.TranslationsController;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.RecyclerView;

import android.content.pm.PackageInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class MainActivity extends AppCompatActivity implements HistoryAdapter.HistoryListener,
        GeckoTabView.WindowInsetsHost, DualScreenController.Owner, TopSheet.Host, GeckoPromptDelegate.Host {

    private final List<HistoryItem> historyItems = new ArrayList<>();
    private final Map<String, Bitmap> faviconCaches = new HashMap<>();

    @Override
    public void onHistoryItemClick(String url) {
        getCurrentTab().loadUrl(url);
    }

    @Override
    public void onHistoryItemNewTab(String url) {
        addNewTab(url);
    }

    @Override
    public void onHistoryDeleted() {
        saveHistory();
    }

    @Override
    public Bitmap getFavicon(String url) {
        return faviconCaches.get(url);
    }

    @Override
    public void copyToClipboard(String text) {
        copyLink(text);
    }

    @Override
    public void onSaveBookmark(String url, String title) {
        saveBookmark();
    }

    @Override
    public void onShareUrl(String url) {
        if (url != null) {
            ShareSheet.show(this, url, null, tabSwitcherHost.getFaviconForUrl(url), false);
        }
    }
    // パスワードマネージャー。起動時にパスワードを要求する
    private PasswordManager passwordManager;

    private Vibrator vib;

    private Toast toast;
    private AutoCompleteTextView urlEditText;
    private GeckoTabView webView; // WebViewをクラス変数として定義
    private LinearLayout action_Bar, Bar, StatusBar;
    private GlassBottomBar bottomBar;
    /** タブレット UI(上にタブ、URL バーとボトムバーを 1 本にまとめたツールバー)。起動時に決まる。 */
    private boolean tabletUi;
    /** タブレット UI のバー(タブ・ツールバー)を画面の下に出す(設定 › デザイン)。起動時に決まる。 */
    private boolean tabletBarsBottom;
    @Nullable private TabStrip tabStrip;
    /** タブレット UI のツールバーが狭い(縦向きのスマートフォンなど)ので、ショートカットを減らしている段階。0 = 全部出す。 */
    private int tabletCompactLevel;
    private TextView tabCountTextView, batteryStatus, sitename;
    private ImageView connectionStatus, wifi, forwardView, backView;
    /** ステータスバーの電池のアイコン(デザインの設定で % とどちらを出すか選ぶ)。 */
    private ImageView batteryIcon;
    private StatusBattery.IconDrawable batteryDrawable;
    private int lastBatteryPct = -1;
    private boolean lastBatteryCharging;
    private SavedPageDatabase dbH;
    private ImageButton backButton, forwardButton, bmbutton, reloadButton, hideBottomButton, showBottomButton, donttouch, popupbutton;
    private FrameLayout webViewContainer;
    private ProgressDialog progressDialog, progressResetDialog;
    private com.google.android.material.progressindicator.LinearProgressIndicator progressBar;
    private ProgressBar pageloading;
    private static final String PREF_NAME = "AdvancedBrowserPrefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private boolean darkModeEnabled = false;
    private static final String KEY_BASIC_AUTH = "basic_auth";
    private static final String TAG = "MainActivity";
    private static final String APPEND_STR = " AirSkyBrowser/" + BuildConfig.VERSION_NAME;
    private static final String KEY_ZOOM_ENABLED = "zoom_enabled";
    private static final String KEY_CT3UA_ENABLED = "ct3ua_enabled";
    private static final String KEY_JS_ENABLED = "js_enabled";
    // 1001 はタブ一覧・設定画面の requestCode と重なるため、ファイル選択は別の値にする
    private static final int FILE_CHOOSER_REQUEST_CODE = 1101;
    private static final int REQUEST_GECKO_PERMISSIONS = 3001;
    private static final int REQUEST_STORAGE_PERMISSION = 3002;

    private static final String DOCOMO_UA = "DoCoMo/2.0 SH902i(c100;TB)";
    private static final String CT3_UA = "Mozilla/5.0 (Linux; Android 7.0; TAB-A03-BR3 Build/02.05.000; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/83.0.4103.106 Safari/537.36";

    private FrameLayout fullscreenContainer;
    // ページが全画面表示(動画など)中かどうか
    private boolean isFullScreen = false;

    private GeckoEngine geckoEngine;
    private GeckoPromptDelegate promptDelegate;

    @Nullable
    @Override
    public GeckoPromptDelegate getPromptDelegate() {
        return promptDelegate;
    }
    private GeckoPermissionDelegate permissionDelegate;
    // ストレージ権限(Android 9 以前)の許可待ちで保留している保存処理
    private Runnable pendingStorageAction;


    private SavedPageAdapter adapter;

    public ArrayList<GeckoTabView> tabs = new ArrayList<>();
    // tabs とインデックス整合。GeckoTabView が破棄されてもタブIDを保持する。
    private final ArrayList<Integer> tabIds = new ArrayList<>();
    private static final int MAX_LOADED_TABS = 3;
    // 先頭が最新使用タブ。要素は tabs のインデックス。
    private final LinkedList<Integer> tabLru = new LinkedList<>();
    private final Handler tabUnloadHandler = new Handler(Looper.getMainLooper());
    private static final long TAB_UNLOAD_DELAY_MS = 20_000;
    private final Runnable tabUnloadRunnable = this::unloadColdTabs;
    private ArrayList<TabInfo> tabInfos = new ArrayList<>();
    private int currentTabIndex = 0;
    private SharedPreferences prefs, pref;
    private int currentHistoryIndex = -1;
    private int totalMatches = 0;
    private int currentMatchIndex = 0;
    private final Map<GeckoTabView, Bitmap> webViewFavicons = new HashMap<>();
    private boolean uaEnabled = false;
    private boolean deskuaEnabled = false;
    private boolean ct3uaEnabled = false;
    private boolean jsEnabled = false;
    private boolean imgBlockEnabled = false;
    //private boolean wentToBackground = false; ←アプリ復帰時のパスワード要求するか判定で試行錯誤で入れた没データ
    private boolean authenticated = false;
    private boolean batteryReceiverRegistered = false;

    private static final String KEY_TABS = "saved_tabs";
    private static final String KEY_CURRENT_TAB_ID = "current_tab_id";
    private static final String KEY_BOOKMARKS = "bookmarks";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_UA_ENABLED = "ua_enabled";
    private static final String KEY_DESKUA_ENABLED = "deskua_enabled";
    private static final String KEY_IMG_BLOCK_ENABLED = "img_block_enabled";
    private final Map<GeckoTabView, String> originalUserAgents = new HashMap<>();
    private int nextTabId = 0;
    private boolean isBackNavigation = false;
    private static final int MAX_HISTORY_SIZE = 100;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LruCache<String, Bitmap> faviconCache;
    private UrlBarSuggestionsView urlBarSuggestions;
    private ImageButton faviconImageView;
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors());
    private final ArrayList<GeckoTabView> webViews = new ArrayList<>();
    private View findInPageBarView;
    private EditText etFindQuery;
    private TextView tvFindCount;
    private ImageButton btnFindPrev, btnFindNext, btnFindClose;


    private final List<Bookmark> bookmarks = new ArrayList<>();
    private boolean basicAuthEnabled = false;
    private boolean zoomEnabled = false;
    private AlertDialog dialog;
    // 選択されたURLとタイプを保持
    private String selectedUrl;
    private int selectedType;
    private static final int HIT_ANCHOR = 1;
    private static final int HIT_IMAGE = 2;
    private static final int HIT_IMAGE_ANCHOR = 3;

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/Aozora9200/AirSky-Glass-Browser/releases/latest";
    private boolean isLoading = false; // ページ読み込み中かどうか
    private boolean isNewTab = false;
    private boolean skipLockOnce = false;
    /** ネイティブのスタートページ。シークレットモードでは使わない(null)。 */
    @Nullable private StartPageView startPageView;
    /** 最後に撮ったスタートページのサムネイル(裏にあるスタートページのタブ用)。 */
    @Nullable private Bitmap startPageSnapshot;
    private static final int REQUEST_START_WALLPAPER = 2101;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private BroadcastReceiver batteryReceiver;

    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;

    private ColorFilter invertFilter, grayFilter;

    private static final String PREFS_NAME = "theme_prefs";
    private static final String POPUP_PREFS_NAME = "popup_prefs";
    private static final String ARROW_PREFS_NAME = "arrow_prefs";
    private static final String KEY_THEME = "selected_theme";
    private static final String KEY_POPUP = "selected_popupbutton";
    private static final String KEY_ARROW = "selected_arrowbutton";

    private static final int THEME_LIGHT = 0;
    private static final int THEME_DARK = 1;
    private static final int THEME_SYSTEM = 2;

    private static final int POPUPBUTTON_LIGHT = 0;
    private static final int POPUPBUTTON_DARK = 1;
    private static final int POPUPBUTTON_SYSTEM = 2;

    private static final int ARROWBUTTON_OLD = 0;
    private static final int ARROWBUTTON_NEW = 1;
    private static final int ARROWBUTTON_IOS = 2;


    // タブ一覧(ブラウザ画面の上に重ねて表示する)
    private TabSwitcher tabSwitcher;
    private PageTranslator pageTranslator;
    private PageReadyNotifier pageReadyNotifier;
    private View snackbarHost;

    /** 閉じたタブを「元に戻す」で復元するための情報(Chrome と同じく 10 秒間)。 */
    private static final class ClosedTab {
        int index;
        int id;
        @Nullable String url;
        @Nullable String title;
        @Nullable String state;
        @Nullable Bitmap snapshot;
        boolean wasCurrent;
        /** 最後のタブだったので、代わりに新しいタブを開いた。 */
        boolean replacedByNewTab;
        /** 入っていたグループ(無ければ -1)。 */
        int groupId = -1;
        /** このタブを閉じてグループが空になり、消したグループ(元に戻す時に戻す)。 */
        @Nullable TabGroup removedGroup;
    }

    private static final int UNDO_CLOSE_TAB_MS = 10_000;
    @Nullable private ClosedTab lastClosedTab;
    static final String EXTRA_SHOW_TAB_SWITCHER = "showTabSwitcher";
    /** タブ一覧を「タブグループ」の表示で開く(シークレットのタブ一覧から切り替えた時)。 */
    static final String EXTRA_SHOW_TAB_GROUPS = "showTabGroups";

    /** タブグループ(通常のタブのみ。シークレットでは null)。 */
    @Nullable private TabGroup.Store tabGroups;
    /** タブ ID → そのタブのグループの ID。 */
    private final Map<Integer, Integer> tabGroupOf = new HashMap<>();
    /** タブ ID → 最後に表示した時刻(非アクティブなタブの判定用)。 */
    private final Map<Integer, Long> tabLastUsed = new HashMap<>();
    /** 次に開く新しいタブを入れるグループ(グループのパネルの「+」)。 */
    private int pendingNewTabGroupId = -1;
    static final String EXTRA_NEW_TAB = "newTab";
    /** "url" で渡した URL を、今のタブではなく新しいタブで開く(拡張機能の画面から)。 */
    static final String EXTRA_URL_IN_NEW_TAB = "urlInNewTab";

    private final Map<GeckoTabView, Bitmap> tabSnapshots = new HashMap<>();

    // アドレスバー/ボトムバーのスクロール連動(Firefox / Chrome 方式)
    private DynamicToolbar dynamicToolbar;
    /** 二画面表示(画面を 2 つ持つ端末)。使わない画面では null。 */
    @Nullable private DualScreenController dualScreen;
    /** 画面の下に出したままにするキーボード(ツールの「キーボード」)と、その置き場。 */
    @Nullable private SoftKeyboard softKeyboard;
    private FrameLayout softKeyboardSlot;
    /** コントローラーで動かす仮想マウスと、コントローラーのボタンの割り当て。 */
    private VirtualMouse virtualMouse;
    private GamepadInput gamepadInput;
    private VirtualGamepad virtualGamepad;
    private InputRouter inputRouter;
    private VirtualController virtualController;
    /** 実際のマウスの最後の位置(表示中のタブから見た位置。マウスを固定した位置に使う)。 */
    private float physicalMouseX = -1, physicalMouseY = -1;
    /** マウスの固定(ポインターロック)を実際のマウスで行っている(Android のポインターキャプチャ中)。 */
    @Nullable private GeckoTabView pointerCaptureTab;
    /** 一画面の時のプログレスバーの位置(二画面から戻す時用)。 */
    private int singleScreenProgressTop = -1;

    private boolean nohideurl = false;
    private boolean networkCallbackRegistered = false;
    private boolean loadTabnoHideurl = false;

    private static final int REQUEST_HISTORY = 1002;
    private boolean noUpdateUrl = false;

    private long lastBackPressedTime = 0; // 最後にBackキーが押された時間

    private boolean dialogShown = false; // 重複表示防止


    /** URL バー横のボタンで開く総合メニュー。 */
    @Nullable private com.google.android.material.bottomsheet.BottomSheetDialog pageMenuDialog;

    /** タブのビューを外している間は、アドレスバーにフォーカスが移っても入力画面を出さない。 */
    private boolean suppressUrlBarFocus;

    private FrameLayout effectLayer; // エフェクト用レイヤー

    private SharedPreferences geoPrefs;

    private static final String STARTUP_PASSWORD = "startup_password";

    private static final int REQUEST_UNLOCK = 2001;
    private static final String STATE_UNLOCKED_NONCE = "unlockedProcessNonce";
    /** 起動時にパスワード画面へ回して、この画面は閉じる途中。 */
    private boolean redirectedToLock = false;
    private boolean lockShowing = false;


    public static class HistoryItem implements java.io.Serializable {
        private static final long serialVersionUID = 2L;
        private final String title;
        private final String url;
        /** 見た時刻(ミリ秒)。時刻を記録していなかった古い履歴は 0。 */
        private final long time;
        public HistoryItem(String title, String url) {
            this(title, url, 0L);
        }
        public HistoryItem(String title, String url, long time) {
            this.title = title;
            this.url = url;
            this.time = time;
        }
        public String getTitle() { return title; }
        public String getUrl() { return url; }
        public long getTime() { return time; }
    }

    public static class Bookmark {
        private final String title;
        private final String url;
        public Bookmark(String title, String url) {
            this.title = title;
            this.url = url;
        }
        public String getTitle() { return title; }
        public String getUrl() { return url; }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 他のアプリのサインインなどで開かれた簡易ブラウザ(Custom Tabs)は、専用の画面で開く
        if (CustomTabActivity.isCustomTabIntent(getIntent())) {
            openCustomTab(getIntent());
            redirectedToLock = true; // この画面では何も作らず、何も保存しない
            finish();
            return;
        }
        passwordManager = new PasswordManager(this);

        SharedPreferences setupprefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstRun = setupprefs.getBoolean("isFirstRun_v2", true);

        if (isFirstRun && !isSecretMode()) {
            // 初回起動 → セットアップ画面へ
            Intent intent = new Intent(this, SetupActivity.class);
            startActivity(intent);
            finish(); // MainActivityを閉じてセットアップから開始
        } else if (!isSecretMode() && !Terms.isAccepted(this)) {
            // セットアップを済ませた後で利用規約ができた(または新しくなった)。同意してもらってから使う
            startActivity(TermsActivity.requireIntent(this));
        }
        boolean usePassWordonStartup = setupprefs.getBoolean(STARTUP_PASSWORD, true);
        boolean useSecretSecurity = setupprefs.getBoolean("useSecretSecurity", false);

        if (useSecretSecurity) {
            //おお
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
            );
        }
        // 解除済みかどうかは、パスワード画面が発行した 1 回限りの合言葉でだけ判断する。
        // (以前の authenticated=true は外部アプリからも付けられるので無視する)
        authenticated = AuthTicket.consume(getIntent());
        // 回転・テーマ変更などで同じプロセス内で画面が作り直された場合は、解除済みを引き継ぐ。
        // プロセスが終了した後の復元(別のプロセス)では引き継がず、パスワードを求める。
        boolean recreatedUnlocked = savedInstanceState != null
                && AuthTicket.PROCESS_NONCE.equals(savedInstanceState.getString(STATE_UNLOCKED_NONCE));

        if (passwordManager.isPasswordSet() && !authenticated && !recreatedUnlocked && !isSecretMode()) {

            if (usePassWordonStartup) {
                Intent intent = new Intent(this, PasswordActivity.class);

                intent.putExtra(
                        "destination_activity",
                        "MainActivity"
                );

                intent.putExtra(
                        "requirePasswordOnFirstLaunch",
                        true
                );

                intent.putExtra(
                        "usePasswordSkip",
                        true
                );

                startActivity(intent);

                finish();
                // ロック画面へ回したので、この画面ではこれ以上何もしない
                // (以前は続けてタブの読み込み・外部から渡された URL の読み込み・権限の要求まで行っていた)
                redirectedToLock = true;
                return;
            }

        }


        setContentView(R.layout.activity_main);
        TouchEffectView.attach(getWindow());
        effectLayer = findViewById(R.id.effectLayer);
        urlEditText = findViewById(R.id.urlEditText);
        backButton = findViewById(R.id.backButton);
        forwardButton = findViewById(R.id.forwardButton);
        webViewContainer = findViewById(R.id.webViewContainer);
        setupStartPage();
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        faviconImageView = (ImageButton) findViewById(R.id.favicon);
        progressBar = findViewById(R.id.progressBar);
        tabCountTextView = (TextView) findViewById(R.id.tabCountTextView);
        tabSwitcher = new TabSwitcher(this, findViewById(R.id.tabSwitcherRoot), tabSwitcherHost);
        snackbarHost = findViewById(R.id.snackbarHost);
        effectLayer.getViewTreeObserver().addOnPreDrawListener(() -> {
            updateSnackbarHost();
            return true;
        });
        pageReadyNotifier = new PageReadyNotifier(this, new PageReadyNotifier.Host() {
            @Nullable
            @Override
            public GeckoTabView tabForId(int tabId) {
                int index = tabIds.indexOf(tabId);
                return index >= 0 ? loadTabIfNeeded(index) : null;
            }

            @Override
            public int tabIdOf(@NonNull GeckoTabView tab) {
                int index = tabs.indexOf(tab);
                return index >= 0 && index < tabIds.size() ? tabIds.get(index) : -1;
            }

            @Override
            public boolean isSecret() {
                return isSecretMode();
            }

            @Override
            public boolean isInForeground() {
                return getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED);
            }
        });
        pageTranslator = new PageTranslator(this, new PageTranslator.Host() {
            @Nullable
            @Override
            public GeckoTabView getCurrentTab() {
                return MainActivity.this.getCurrentTab();
            }

            @Override
            public boolean isInternalUrl(@Nullable String url) {
                return MainActivity.this.isInternalUrl(url);
            }

            @Override
            public boolean showUndoBar(@NonNull String text, @NonNull Runnable undo) {
                return MainActivity.this.showUndoBar(text, undo);
            }
        });
        // ImageButton tabButton = findViewById(R.id.action_tab);
        reloadButton = findViewById(R.id.action_reload);
        ImageButton bmbutton = findViewById(R.id.action_bookmark);
        popupbutton = findViewById(R.id.action_popup);
        connectionStatus = findViewById(R.id.network);
        batteryStatus = findViewById(R.id.batteryStatus);
        batteryIcon = findViewById(R.id.batteryIcon);
        batteryDrawable = new StatusBattery.IconDrawable(this);
        batteryIcon.setImageDrawable(batteryDrawable);
        StatusBar = findViewById(R.id.statusbar);
        pageloading = findViewById(R.id.pageloading);
        sitename = findViewById(R.id.sitename);
        hideBottomButton = findViewById(R.id.hidebottom);
        showBottomButton = findViewById(R.id.showbottom);
        donttouch = findViewById(R.id.donttouch);
        forwardView = findViewById(R.id.forwardView);
        backView = findViewById(R.id.backView);
        vib = (Vibrator)getSystemService(VIBRATOR_SERVICE);

        applyMenuButtonStyle();
        applyArrowButtonStyle();

        showBottomButton.setVisibility(View.GONE);
        donttouch.setVisibility(View.GONE);

        geoPrefs = getSharedPreferences("GeoPermissionStore", MODE_PRIVATE);

        wifi = findViewById(R.id.wifi);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            // API 24 以上のときだけ実行する処理
            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    runOnUiThread(() -> updateConnectionStatus());
                }

                @Override
                public void onLost(Network network) {
                    runOnUiThread(() -> showDisconnected());
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                    runOnUiThread(() -> updateConnectionStatus());
                }
            };
        } else {
            // API23以下（Android 6.0以下）のときの処理
            wifi.setVisibility(View.GONE);
            connectionStatus.setVisibility(View.GONE);
        }

        // 色反転フィルタを準備
        ColorMatrix colorMatrix_Invert = new ColorMatrix(new float[] {
                -1,  0,  0,  0, 255, // R
                0, -1,  0,  0, 255, // G
                0,  0, -1,  0, 255, // B
                0,  0,  0,  1,   0  // A
        });
        ColorMatrix colorMatrix_Gray75 = new ColorMatrix(new float[] {
                0, 0, 0, 0, 107, // R
                0, 0, 0, 0, 107, // G
                0, 0, 0, 0, 107, // B
                0, 0, 0, 1,   0  // A
        });
        invertFilter = new ColorMatrixColorFilter(colorMatrix_Invert);
        grayFilter = new ColorMatrixColorFilter(colorMatrix_Gray75);

        applyBarColors(); // 起動時に反映
        if (isSecretMode()) showSecretUrlBarIcon();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 123);
            }
        }

        dbH = new SavedPageDatabase(this);

        // ProgressResetDialog の初期設定
        progressResetDialog = new ProgressDialog(this);
        progressResetDialog.setMessage("Erasing...");
        progressResetDialog.setCancelable(false);
        prefs = getSharedPreferences("WebViewTabs", MODE_PRIVATE);
        pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        darkModeEnabled = pref.getBoolean(KEY_DARK_MODE, false);
        basicAuthEnabled = pref.getBoolean(KEY_BASIC_AUTH, false);
        zoomEnabled = pref.getBoolean(KEY_ZOOM_ENABLED, false);
        jsEnabled = pref.getBoolean(KEY_JS_ENABLED, false);
        imgBlockEnabled = pref.getBoolean(KEY_IMG_BLOCK_ENABLED, false);
        uaEnabled = pref.getBoolean(KEY_UA_ENABLED, false);
        deskuaEnabled = pref.getBoolean(KEY_DESKUA_ENABLED, false);
        ct3uaEnabled = pref.getBoolean(KEY_CT3UA_ENABLED, false);

        prefs = getSharedPreferences("WebViewTabs", MODE_PRIVATE);

        fullscreenContainer = findViewById(R.id.fullscreenContainer);
        bottomBar = findViewById(R.id.bottomBar);
        action_Bar = findViewById(R.id.action_Bar);
        tabletUi = TabletUi.isEnabled(this);
        tabletBarsBottom = tabletUi && TabletUi.barsAtBottom(this);
        if (tabletUi) applyTabletLayout();
        setupDynamicToolbar();
        setupUrlBarSuggestions();
        setupImeInsets();
        setupSoftKeyboardSlot();
        setupDualScreen();
        setupController();
        applySoftKeyboard();

        // GeckoView のランタイムと、ページからのダイアログ・権限要求の処理
        geckoEngine = GeckoEngine.get(this);
        applyPageColorScheme(); // ページの配色を今のテーマに合わせる
        geckoEngine.setBlockImages(imgBlockEnabled);
        promptDelegate = new GeckoPromptDelegate(this, FILE_CHOOSER_REQUEST_CODE, () -> skipLockOnce = true);
        // 再送信を断ると読み込みが始まらないので、スワイプ更新のくるくると読み込み表示をここで戻す
        promptDelegate.setPageUrlResolver(session -> {
            for (GeckoTabView t : tabs) {
                if (t != null && t.getGeckoSession() == session) return t.getUrl();
            }
            return null;
        });
        promptDelegate.setOnRepostDenied(() -> {
            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) swipeRefreshLayout.setRefreshing(false);
            if (isLoading) {
                isLoading = false;
                reloadButton.setImageResource(R.drawable.reload);
                hideProgressBarDelayed();
                pageloading.setVisibility(View.INVISIBLE);
            }
        });
        permissionDelegate = new GeckoPermissionDelegate(this, REQUEST_GECKO_PERMISSIONS);


        backButton.setOnClickListener(v -> goBack());
        forwardButton.setOnClickListener(v -> goForward());

        basicAuthEnabled = true;
        zoomEnabled = true;

        // インテントで渡された URL を取得
        String url = getIntent().getStringExtra("url");

        // ボタンにクリックイベントを設定
        //tabButton.setOnClickListener(v ->
        //        showTabMenu()
        //);

        View.OnClickListener openTabs = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GeckoTabView webView = getCurrentTab();
                // サムネイルに文字選択やメニューが写らないよう、撮る前に解除する
                if (clearTextSelection()) {
                    webViewContainer.postDelayed(() -> captureTabSnapshot(webView, () -> showTabMenu()), 150);
                } else {
                    captureTabSnapshot(webView, () -> showTabMenu());
                }
            }
        };
        // 数字の上も周りの余白も、ボタン全体で受けて丸い波紋を出す(ページに触ったことにしない)
        findViewById(R.id.tabCountButton).setOnClickListener(openTabs);

        hideBottomButton.setOnClickListener(v ->
                hideBottomBar()
        );

        showBottomButton.setOnClickListener(v ->
                showBottomBar()
        );

        // バッテリー残量監視
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int batteryPct = (int) ((level / (float) scale) * 100);

                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                String chargeStatus;
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    chargeStatus = "+";
                } else if (status == BatteryManager.BATTERY_STATUS_FULL) {
                    chargeStatus = "+";
                } else {
                    chargeStatus = "";
                }

                if (level >= 0 && scale > 0) {
                    //充電状態をチェック
                    boolean isCharging =
                            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                    status == BatteryManager.BATTERY_STATUS_FULL;

                    //20％以下 & 未充電時のみダイアログ表示
                    if (batteryPct <= 20 && !isCharging && !dialogShown) {
                        SharedPreferences setupprefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                        boolean batteryalert = setupprefs.getBoolean("batteryalert", true);
                        if (batteryalert) {
                            showBatteryLowDialog(batteryPct);
                        }
                        dialogShown = true;
                    } else if (batteryPct > 20 || isCharging) {
                        //条件から外れたら再度出せるようにリセット
                        dialogShown = false;
                    }
                }

                if (level >= 0 && scale > 0) {
                    lastBatteryPct = batteryPct;
                    lastBatteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                            || status == BatteryManager.BATTERY_STATUS_FULL;
                }
                batteryStatus.setText(chargeStatus + batteryPct + "%");
                applyBatteryStyle();
            }
        };

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            // API 24 以上のときだけ実行する処理
            // モバイル電波強度リスナー
            phoneStateListener = new PhoneStateListener() {
                @Override
                public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                    super.onSignalStrengthsChanged(signalStrength);
                    int level = signalStrength.getLevel(); // 0〜4
                    updateSignalIcon(level, false);
                }
            };
        }

        pageloading.setVisibility(View.INVISIBLE);

        reloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GeckoTabView current = getCurrentTab();
                if (current == null) return;
                if (isLoading) {
                    current.stopLoading(); // 読み込み中止
                    isLoading = false;
                    reloadButton.setImageResource(R.drawable.reload);
                } else {
                    // 矢印の向きに 1 回転させてから読み込み中(×)の表示に変わる
                    reloadButton.animate().cancel();
                    reloadButton.setRotation(0f);
                    reloadButton.animate().rotation(360f).setDuration(420)
                            .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
                            .withEndAction(() -> reloadButton.setRotation(0f))
                            .start();
                    current.reload(); // リロード
                }
            }
        });

        bmbutton.setOnClickListener(v -> showPageMenu());

        // URL バーの横のショートカット(既定は共有)。長押しで別の機能に変えられる
        ImageButton shortcutButton = findViewById(R.id.action_shortcut);
        shortcutButton.setOnClickListener(v -> runToolbarShortcut(v));
        shortcutButton.setOnLongClickListener(v -> {
            ToolbarShortcut.showPicker(this, this::updateToolbarShortcut);
            return true;
        });
        updateToolbarShortcut();

        popupbutton.setOnClickListener(v ->
                showBottomMenu(v)
        );

        faviconImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSiteDialog();
            }
        });

        // URL入力でエンターを押したら現在のタブでページを開く
        urlEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                loadUrlInCurrentTab(urlEditText.getText().toString());
                closeKeyboard();
                urlEditText.clearFocus();
                return true;
            }
            return false;
        });

        urlEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && suppressUrlBarFocus) return;
                if (hasFocus) {
                    urlEditText.selectAll();
                    showUrlBarHistory();
                    urlBarSuggestions.setShowing(true);
                } else {
                    urlBarSuggestions.setShowing(false);
                }
                faviconImageView.setVisibility(hasFocus ? View.GONE : View.VISIBLE);
                updateUrlBarStartPadding();
            }
        });


        int maxMemory = (int)(Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        faviconCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        swipeRefreshLayout.setOnChildScrollUpCallback(new SwipeRefreshLayout.OnChildScrollUpCallback() {
            @Override
            public boolean canChildScrollUp(SwipeRefreshLayout parent, @Nullable View child) {
                GeckoTabView current = getCurrentTab();
                if (isShowingStartPage(current)) return startPageView.canScrollUp();
                return (current != null && current.getPageScrollY() > 0);
            }
        });
        // 指を動かし始めてから、ページがそのタッチを使うか(地図・中の枠のスクロール・ピンチなど)を確かめる
        ((BrowserSwipeRefreshLayout) swipeRefreshLayout).setPullGate(() -> {
            GeckoTabView current = getCurrentTab();
            if (current == null || isInputOverlayActive()) return false;
            if (isShowingStartPage(current)) return !startPageView.canScrollUp();
            return current.canPullToRefresh();
        });
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                GeckoTabView current = getCurrentTab();
                if (isShowingStartPage(current)) {
                    // スタートページはページを読み直さず、おすすめ記事と壁紙を新しくする
                    startPageView.refresh(() -> swipeRefreshLayout.setRefreshing(false));
                    return;
                }
                if (current != null) current.reload();
            }
        });

        boolean isSwipeReload = setupprefs.getBoolean("isSwipeReload", true);
        swipeRefreshLayout.setEnabled(isSwipeReload);

        urlEditText.addTextChangedListener(new TextWatcher() {
            private Timer timer = new Timer();
            private final long DELAY = 100; // 入力後に待つミリ秒

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(final Editable s) {
                final String query = s.toString();
                // ページ読み込みでアドレスバーに URL を入れた時などは候補を取りに行かない
                // (閲覧中の URL を検索候補サーバーへ送らない)
                if (!urlEditText.hasFocus()) return;
                timer.cancel();
                // 全部消したら、入力前と同じく検索履歴を出す
                if (query.trim().isEmpty()) {
                    showUrlBarHistory();
                    return;
                }
                // 入力内容はすぐに先頭へ出す(通信を待たない・オフラインでも出る)
                urlBarSuggestions.showQuery(query, clipboardUrl(), matchVisitedPages(query));
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        fetchSuggestions(query);
                    }
                }, DELAY);
            }
        });

        boolean isFirstLaunch = pref.getBoolean("isFirstLaunch", true);

        // タブを復元(シークレットモードでは前回のタブを残さない)
        nextTabId = firstTabId();
        if (!isSecretMode()) {
            if (supportsTabGroups()) tabGroups = new TabGroup.Store(prefs);
            loadTabsState();
        }
        // リンクを開くために作られた画面(シークレット タブ・新しいウィンドウで開く)は、空のタブを作らない
        if (tabs.isEmpty() && !isOpeningLink()) {
            if (isFirstLaunch) {
                newStartPage();

                // 初回起動済みを保存
                SharedPreferences.Editor editor = pref.edit();
                editor.putBoolean("isFirstLaunch", false);
                editor.apply();
            } else {
                newStartPage();
            }
        }

        updateTabCount();

        // 更新の確認はアプリ起動時に 1 回だけ。シークレットへの切り替えや画面の作り直しでは出さない
        boolean isUpdate = setupprefs.getBoolean("updateDialog", true);
        if (isUpdate && !isSecretMode() && !updateCheckedThisProcess) {
            updateCheckedThisProcess = true;
            new CheckUpdateTask().execute();
        }
        // アプリを開いていない間も、1 日 1 回確認して通知で知らせる(設定がオフなら予約を消す)
        UpdateChecker.schedule(this);

        loadBookmarks();
        loadHistory();
        if (!historyItems.isEmpty()) {
            currentHistoryIndex = historyItems.size() - 1;
        }
        initializePersistentFavicons();
        switchToTab(currentTabIndex);
        // 3 か月使っていない非アクティブなタブを閉じる
        webViewContainer.post(this::closeExpiredInactiveTabs);
        if (getIntent().getBooleanExtra(EXTRA_SHOW_TAB_SWITCHER, false)) {
            // 通常/シークレットの切り替えで開かれた: 最初からタブ一覧を出す
            boolean groups = getIntent().getBooleanExtra(EXTRA_SHOW_TAB_GROUPS, false);
            getIntent().removeExtra(EXTRA_SHOW_TAB_SWITCHER);
            getIntent().removeExtra(EXTRA_SHOW_TAB_GROUPS);
            webViewContainer.post(() -> {
                showTabSwitcherNow();
                if (groups) tabSwitcher.showGroupsPane();
                else tabSwitcher.showTabsPane();
            });
        }
        Intent intent = getIntent();
        String action = intent.getAction();
        Uri uri = intent.getData();

        if (Intent.ACTION_VIEW.equals(action) && uri != null) {
            // 外部アプリから渡されたリンクは新しいタブで開く
            addNewTab(uri.toString());
        } else {
            return;
        }

        //if (uri != null) {
        //    if ("file".equals(uri.getScheme()) || "content".equals(uri.getScheme())) {
        //        addNewTab(uri.toString()); // WebViewが自動で読み込み
        //    } else {
        //        Toast.makeText(this, "対応していない URI 形式です。", Toast.LENGTH_SHORT).show();
        //    }
        //}
        if (url != null && !url.isEmpty()) {
            // URL が渡された場合は GeckoTabView で開く
            load(url);
        } else {
            return;
        }

    }


    private void showBatteryLowDialog(int batteryPct) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.battery_low, null);

        TextView percent = view.findViewById(R.id.level_percent);
        percent.setText(Lang.t("残り") + batteryPct + "%");

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("充電してください"))
                .setView(view)
                .setPositiveButton("OK", null)
                .setCancelable(true)
                .show();
    }

    private void startPage() {
        // 設定 › ホームページ でカスタムのアドレスを選んでいればそこを開く(シークレットは常にスタートページ)
        String custom = isSecretMode() ? null : BrowserPrefs.customHomeUrl(this);
        load(custom != null ? custom : homeUrl());
    }

    private void newStartPage() {
        addNewTab(homeUrl());
    }

    /**
     * ユーザーが「新しいタブ」を開いた。開いたことが分かるようにアニメーションを付ける。
     * @param origin 押されたボタン。そこから新しいページが広がる(null なら画面下から)
     */
    private void openNewTabAnimated(@Nullable View origin) {
        // ボタンの位置はタブ一覧を閉じる前(=見えている間)に測っておく
        int[] originCenter = null;
        if (origin != null && origin.getWidth() > 0) {
            int[] loc = new int[2];
            origin.getLocationInWindow(loc);
            originCenter = new int[]{loc[0] + origin.getWidth() / 2, loc[1] + origin.getHeight() / 2};
        }
        newStartPage();
        animateNewTab(originCenter);
    }

    /**
     * 新しいタブのページを、押したボタンの位置から広げる(ボタンが無ければ画面下からせり上げる)。
     * あわせてタブ数のアイコンを弾ませる。
     * Gecko のページ(シークレット)は SurfaceView が拡大縮小・透明度に追従しないので、スライドだけにする。
     */
    private void animateNewTab(@Nullable int[] originInWindow) {
        float density = getResources().getDisplayMetrics().density;
        android.view.animation.Interpolator emphasized =
                new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
        boolean nativePage = startPageView != null && startPageView.getVisibility() == View.VISIBLE;
        View target = nativePage ? startPageView : findViewById(R.id.contentFrame);
        if (target != null) {
            target.animate().cancel();
            if (nativePage) {
                if (originInWindow != null && target.getWidth() > 0) {
                    int[] loc = new int[2];
                    target.getLocationInWindow(loc);
                    target.setPivotX(originInWindow[0] - loc[0]);
                    target.setPivotY(originInWindow[1] - loc[1]);
                    target.setScaleX(0.4f);
                    target.setScaleY(0.4f);
                    target.setTranslationY(0f);
                } else {
                    target.setPivotX(target.getWidth() / 2f);
                    target.setPivotY(target.getHeight());
                    target.setScaleX(0.9f);
                    target.setScaleY(0.9f);
                    target.setTranslationY(72 * density);
                }
                target.setAlpha(0f);
            } else {
                target.setTranslationY(72 * density);
            }
            // 広がる途中は、まだ描かれていない Gecko の面(灰色)ではなく画面の背景色を見せる
            View backing = nativePage ? findViewById(R.id.startPageContainer) : null;
            if (backing != null) {
                android.util.TypedValue tv = new android.util.TypedValue();
                getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true);
                backing.setBackgroundColor(tv.data);
            }
            target.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(420)
                    .setInterpolator(emphasized)
                    .withEndAction(() -> {
                        target.setTranslationY(0f);
                        target.setAlpha(1f);
                        target.setScaleX(1f);
                        target.setScaleY(1f);
                        if (backing != null) backing.setBackground(null);
                    })
                    .start();
        }
        View counter = findViewById(R.id.tabCountTextView);
        if (counter != null) {
            counter.animate().cancel();
            counter.setScaleX(1.35f);
            counter.setScaleY(1.35f);
            counter.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(120)
                    .setDuration(320)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f))
                    .start();
        }
    }

    // ------------------------------------------------------------------
    // タブレット UI
    // ------------------------------------------------------------------

    /**
     * タブレット UI: パソコンのブラウザのように、いちばん上にタブを並べ、その下の 1 本のツールバーに
     * 左から ショートカット・戻る・進む・再読み込み・URL バー・ショートカット 2 つ(既定はブックマーク・
     * リーディングモード)・タブ・ポップアップメニュー(A)・メニュー を並べる。ボトムバーは使わない。
     * ボタンはボトムバーにあったものをそのまま移すので、押した時の処理は一画面と同じ。
     * 設定でバーを下にした時は、タブとツールバーをまとめて画面の下に置く(上はステータスバーだけ)。
     */
    private void applyTabletLayout() {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout row = (LinearLayout) action_Bar.getChildAt(0);
        View urlFrame = findViewById(R.id.urlBarFrame);
        ImageButton shortcut = findViewById(R.id.action_shortcut);
        ImageButton menu = findViewById(R.id.action_bookmark);
        View tabButton = findViewById(R.id.tabCountButton);
        // タブレット UI ではショートカットとメニューのボタンにも、ほかのボタンと同じ影を付ける
        if (shortcut instanceof ShadowImageButton) ((ShadowImageButton) shortcut).setShadowEnabled(true);
        if (menu instanceof ShadowImageButton) ((ShadowImageButton) menu).setShadowEnabled(true);

        ImageButton slot1 = newTabletToolbarButton(R.id.action_shortcut_tablet1, ToolbarShortcut.SLOT_TABLET_1);
        ImageButton slot2 = newTabletToolbarButton(R.id.action_shortcut_tablet2, ToolbarShortcut.SLOT_TABLET_2);

        // タブボタンは置かない(タブの切り替え・グループはツールバーの上のタブの帯で行う)
        View[] order = {shortcut, backButton, backView, forwardButton, forwardView, reloadButton, urlFrame,
                slot1, slot2, popupbutton, menu};
        for (View v : order) {
            ViewGroup parent = (ViewGroup) v.getParent();
            if (parent != null) parent.removeView(v);
        }
        if (tabButton != null) tabButton.setVisibility(View.GONE);
        row.removeAllViews();
        int h = Math.round(40 * density);
        int w = Math.round(44 * density);
        for (View v : order) {
            LinearLayout.LayoutParams lp;
            if (v == urlFrame) {
                lp = new LinearLayout.LayoutParams(0, h, 1f);
                lp.setMarginStart(Math.round(6 * density));
                lp.setMarginEnd(Math.round(6 * density));
            } else {
                lp = new LinearLayout.LayoutParams(w, h);
            }
            lp.gravity = android.view.Gravity.CENTER_VERTICAL;
            row.addView(v, lp);
        }
        row.setPadding(Math.round(4 * density), 0, Math.round(4 * density), 0);
        LinearLayout.LayoutParams rowLp = (LinearLayout.LayoutParams) row.getLayoutParams();
        rowLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        rowLp.weight = 0;
        rowLp.height = Math.round(48 * density);
        rowLp.gravity = android.view.Gravity.CENTER_VERTICAL;
        row.setLayoutParams(rowLp);
        // ボトムバー用の大きい A のボタンを、ツールバーに合う大きさにする(ほかのボタンより少し大きめ)
        popupbutton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Math.round(3 * density);
        popupbutton.setPadding(pad, pad, pad, pad);

        // タブを並べる帯をツールバーの上に入れ、ツールバーを縦に 2 段にする
        tabStrip = new TabStrip(this, tabStripHost);
        tabStrip.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorSurfaceContainer, android.graphics.Color.BLACK));
        action_Bar.setOrientation(LinearLayout.VERTICAL);
        action_Bar.addView(tabStrip, 0, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(40 * density)));
        action_Bar.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorSurfaceContainerHighest, android.graphics.Color.BLACK));
        FrameLayout.LayoutParams barLp = (FrameLayout.LayoutParams) action_Bar.getLayoutParams();
        barLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        FrameLayout.LayoutParams plp = (FrameLayout.LayoutParams) progressBar.getLayoutParams();
        if (tabletBarsBottom) {
            // バーを下に: タブの帯とツールバーを画面の下端に置き、プログレスバーはステータスバーのすぐ下
            barLp.gravity = android.view.Gravity.BOTTOM;
            barLp.topMargin = 0;
            plp.topMargin = Math.round(24 * density);
        } else {
            // プログレスバーはツールバーの下端に合わせる(タブの帯の分だけ下げる)
            plp.topMargin += Math.round(40 * density);
        }
        action_Bar.setLayoutParams(barLp);
        progressBar.setLayoutParams(plp);
        // ページとの境目に細い線を引く(上に置く時は下端、下に置く時は上端)。影(elevation)にすると
        // 同じ枠に重ねているタブ一覧などより手前に出てしまうので使わない
        int toolbarColor = com.google.android.material.color.MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorSurfaceContainerHighest, android.graphics.Color.BLACK);
        int lineColor = com.google.android.material.color.MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorOutlineVariant, android.graphics.Color.GRAY);
        android.graphics.drawable.LayerDrawable barBg = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{new android.graphics.drawable.ColorDrawable(lineColor),
                        new android.graphics.drawable.ColorDrawable(toolbarColor)});
        int hair = Math.max(1, Math.round(density));
        if (tabletBarsBottom) {
            // 下に置く時は、いちばん上にあるタブの帯の上端に線を引く
            int stripColor = com.google.android.material.color.MaterialColors.getColor(
                    this, com.google.android.material.R.attr.colorSurfaceContainer, android.graphics.Color.BLACK);
            android.graphics.drawable.LayerDrawable stripBg = new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{new android.graphics.drawable.ColorDrawable(lineColor),
                            new android.graphics.drawable.ColorDrawable(stripColor)});
            stripBg.setLayerInset(1, 0, hair, 0, 0);
            tabStrip.setBackground(stripBg);
            action_Bar.setBackgroundColor(toolbarColor);
        } else {
            barBg.setLayerInset(1, 0, 0, 0, hair);
            action_Bar.setBackground(barBg);
        }

        bottomBar.setVisibility(View.GONE);
        hideBottomButton.setVisibility(View.GONE);
        showBottomButton.setVisibility(View.GONE);
        // 幅が足りない時(縦向きなど)は URL バーが潰れないよう、ショートカットから順に隠す
        row.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l == or - ol) return;
            int widthDp = Math.round((r - l) / density);
            int level = widthDp >= 640 ? 0 : widthDp >= 520 ? 1 : 2;
            if (level != tabletCompactLevel) {
                tabletCompactLevel = level;
                v.post(this::updateToolbarShortcut);
            }
        });
        refreshTabStrip();
    }

    @NonNull
    private ImageButton newTabletToolbarButton(int id, int slot) {
        ImageButton b = new ImageButton(this);
        b.setId(id);
        b.setBackgroundResource(R.drawable.ripple_circle_small);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setImageTintList(android.content.res.ColorStateList.valueOf(com.google.android.material.color.MaterialColors
                .getColor(this, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.GRAY)));
        b.setOnClickListener(v -> runToolbarShortcut(v, ToolbarShortcut.key(this, slot)));
        b.setOnLongClickListener(v -> {
            ToolbarShortcut.showPicker(this, slot, this::updateToolbarShortcut);
            return true;
        });
        return b;
    }

    /** タブレット UI で上から出すシートの上端 = ツールバーの下端(画面の上からの px)。 */
    @Override
    public int topSheetAnchorY() {
        int[] loc = new int[2];
        action_Bar.getLocationOnScreen(loc);
        return Math.max(0, loc[1] + action_Bar.getHeight());
    }

    /** 上のタブの並びを今のタブに合わせる(タブレット UI の時だけ)。 */
    private void refreshTabStrip() {
        if (tabStrip == null) return;
        ensureGroupsContiguous();
        tabStrip.refresh();
    }

    /**
     * グループのタブが離れて並んでいたら、最初のタブの位置に寄せる。
     * 離れていると、タブの帯に同じグループのチップが 2 つ出てしまう(以前の並べ替えで離れたものも直す)。
     */
    private void ensureGroupsContiguous() {
        if (tabGroups == null) return;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        java.util.Set<Integer> broken = new java.util.HashSet<>();
        Integer prev = null;
        for (int i = 0; i < tabIds.size(); i++) {
            Integer g = tabGroupOf.get(tabIds.get(i));
            if (g != null && !g.equals(prev)) {
                if (!seen.add(g)) broken.add(g);
            }
            prev = g;
        }
        if (broken.isEmpty()) return;
        for (int gid : broken) gatherGroup(gid);
        saveTabsState();
    }

    private final TabStrip.Host tabStripHost = new TabStrip.Host() {
        @Override
        public int tabCount() {
            return tabs.size();
        }

        @Override
        public int currentTab() {
            return currentTabIndex;
        }

        @Nullable
        @Override
        public String tabTitle(int index) {
            GeckoTabView tab = index < tabs.size() ? tabs.get(index) : null;
            String url = tabUrl(index);
            if (url != null && isHomeUrl(url)) return homeSiteName();
            String title = tab != null ? tab.getTitle() : null;
            if ((title == null || title.isEmpty()) && index < tabInfos.size()) title = tabInfos.get(index).getTitle();
            return title;
        }

        @Nullable
        @Override
        public String tabUrl(int index) {
            GeckoTabView tab = index < tabs.size() ? tabs.get(index) : null;
            String url = tab != null ? tab.getUrl() : null;
            if ((url == null || url.isEmpty()) && index < tabInfos.size()) url = tabInfos.get(index).getUrl();
            return url;
        }

        @Nullable
        @Override
        public Bitmap tabIcon(int index) {
            String url = tabUrl(index);
            if (url == null || isInternalUrl(url)) return null;
            Bitmap own = index < tabInfos.size() ? tabInfos.get(index).getIcon() : null;
            return own != null ? own : tabSwitcherHost.getFaviconForUrl(url);
        }

        @Override
        public int tabId(int index) {
            return index >= 0 && index < tabIds.size() ? tabIds.get(index) : -1;
        }

        @Override
        public void reorderTabs(@NonNull List<Integer> order) {
            applyTabOrder(order);
            saveTabsState();
            refreshTabStrip();
        }

        @Override
        public void dropTab(@NonNull List<Integer> order, int tabId, int groupId) {
            applyTabOrder(order);
            // 落とした場所のグループに入れる(-1 ならグループから外す)。元のグループが空になれば消す
            Integer old = tabGroupOf.get(tabId);
            TabGroup target = groupId >= 0 && tabGroups != null ? tabGroups.get(groupId) : null;
            if (target != null && !target.closed) {
                tabGroupOf.put(tabId, groupId);
                target.touch();
            } else {
                tabGroupOf.remove(tabId);
            }
            Integer now = tabGroupOf.get(tabId);
            if (old != null && !old.equals(now)) removeGroupIfEmpty(old);
            saveTabsState();
            if (tabGroups != null) tabGroups.save();
            refreshTabStrip();
        }

        @Override
        public void selectTab(int index) {
            if (index == currentTabIndex) return;
            clearUrlBarFocus();
            closeKeyboard();
            switchToTab(index);
        }

        @Override
        public void closeTab(int index) {
            MainActivity.this.closeTab(index, true);
        }

        @Override
        public void newTab(@NonNull View button) {
            openNewTabAnimated(button);
        }

        @Nullable
        @Override
        public TabGroup tabGroup(int index) {
            if (tabGroups == null || index < 0 || index >= tabIds.size()) return null;
            Integer gid = tabGroupOf.get(tabIds.get(index));
            TabGroup g = gid != null ? tabGroups.get(gid) : null;
            return g != null && !g.closed ? g : null;
        }

        @Override
        public int groupTabCount(int groupId) {
            return tabsInGroup(groupId).size();
        }

        @Override
        public void showTabContextMenu(@NonNull View anchor, int index) {
            showTabStripTabMenu(anchor, index);
        }

        @Override
        public void showGroupMenu(@NonNull View anchor, int groupId) {
            showTabStripGroupMenu(anchor, groupId);
        }

        @Override
        public void showAllTabs(@NonNull View button) {
            clearUrlBarFocus();
            closeKeyboard();
            captureTabSnapshot(getCurrentTab(), () -> showTabMenu());
        }
    };

    /**
     * タブの帯でタブを長押し・右クリックした時のメニュー。
     * タブレットではタブボタンを置かないので、グループの作成・追加・解除はここから行う。
     */
    private void showTabStripTabMenu(@NonNull View anchor, int index) {
        if (index < 0 || index >= tabs.size()) return;
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
        android.view.Menu m = menu.getMenu();
        final int newGroup = 1, removeFromGroup = 2, close = 3, closeOthers = 4, groupBase = 100;
        Integer current = tabGroupOf.get(tabIds.get(index));
        if (tabGroups != null) {
            m.add(0, newGroup, 0, Lang.t("新しいグループにタブを追加"));
            // 開いているほかのグループへ
            List<TabGroup> others = new ArrayList<>();
            for (TabGroup g : tabGroups.all()) {
                if (!g.closed && (current == null || g.id != current) && !tabsInGroup(g.id).isEmpty()) others.add(g);
            }
            if (!others.isEmpty()) {
                android.view.SubMenu sub = m.addSubMenu(0, 0, 1, Lang.t("グループにタブを追加"));
                for (TabGroup g : others) {
                    android.view.MenuItem item = sub.add(0, groupBase + g.id, 0, g.displayTitle(tabsInGroup(g.id).size()));
                    item.setIcon(TabGroupUi.dot(TabGroup.colorValue(this, g.color, isSecretMode())));
                }
            }
            if (current != null) m.add(0, removeFromGroup, 2, Lang.t("グループから削除"));
        }
        m.add(0, close, 3, Lang.t("タブを閉じる"));
        if (tabs.size() > 1) m.add(0, closeOthers, 4, Lang.t("他のタブを閉じる"));
        final int tabId = tabIds.get(index);
        menu.setOnMenuItemClickListener(item -> {
            int i = tabIds.indexOf(tabId);
            if (i < 0) return true;
            int id = item.getItemId();
            if (id == newGroup) {
                List<Integer> one = new ArrayList<>();
                one.add(i);
                int gid = createTabGroup(one);
                TabGroup g = tabGroups != null ? tabGroups.get(gid) : null;
                if (g != null) {
                    TabGroupUi.showEditDialog(this, g, tabsInGroup(gid).size(), true, (title, color) -> {
                        g.title = title;
                        g.color = color;
                        g.touch();
                        tabGroups.save();
                        refreshTabStrip();
                    });
                }
            } else if (id >= groupBase) {
                List<Integer> one = new ArrayList<>();
                one.add(i);
                moveTabsToGroup(one, id - groupBase);
            } else if (id == removeFromGroup) {
                removeTabFromGroup(i);
            } else if (id == close) {
                closeTab(i, true);
            } else if (id == closeOthers) {
                for (int k = tabs.size() - 1; k >= 0; k--) {
                    if (tabIds.get(k) != tabId) closeTab(k, false);
                }
            }
            refreshTabStrip();
            return true;
        });
        menu.show();
    }

    /** タブの帯でグループ名のラベルを押した時のメニュー。 */
    private void showTabStripGroupMenu(@NonNull View anchor, int groupId) {
        if (tabGroups == null) return;
        TabGroup g = tabGroups.get(groupId);
        if (g == null) return;
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
        android.view.Menu m = menu.getMenu();
        final int newTab = 1, edit = 2, ungroup = 3, closeGroup = 4, delete = 5;
        m.add(0, newTab, 0, Lang.t("グループに新しいタブを追加"));
        m.add(0, edit, 1, Lang.t("名前と色を変更"));
        m.add(0, ungroup, 2, Lang.t("グループを解除"));
        m.add(0, closeGroup, 3, Lang.t("グループを閉じる"));
        m.add(0, delete, 4, Lang.t("グループを削除"));
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == newTab) {
                tabSwitcherHost.onNewTabInGroup(groupId);
            } else if (id == edit) {
                TabGroupUi.showEditDialog(this, g, tabsInGroup(groupId).size(), false, (title, color) -> {
                    g.title = title;
                    g.color = color;
                    g.touch();
                    tabGroups.save();
                    refreshTabStrip();
                });
            } else if (id == ungroup) {
                tabSwitcherHost.onUngroup(groupId);
            } else if (id == closeGroup) {
                // 閉じたグループはタブとグループの一覧の「タブグループ」から開き直せる
                closeTabGroup(groupId);
            } else if (id == delete) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(Lang.t("グループを削除しますか?"))
                        .setMessage(Lang.t("グループのタブもすべて閉じられ、グループは保存されません。"))
                        .setPositiveButton(Lang.t("削除"), (d, w) -> {
                            deleteTabGroup(groupId);
                            refreshTabStrip();
                        })
                        .setNegativeButton(Lang.t("キャンセル"), null)
                        .show();
            }
            refreshTabStrip();
            return true;
        });
        menu.show();
    }

    /** アドレスバー/ボトムバーのスクロール連動を準備する。 */
    private void setupDynamicToolbar() {
        // タブレット UI でバーを下に置く時は、タブとツールバーをボトムバーと同じように下へ隠す
        dynamicToolbar = new DynamicToolbar(
                tabletBarsBottom ? new View[]{StatusBar, progressBar} : new View[]{StatusBar, action_Bar, progressBar},
                tabletBarsBottom ? action_Bar : bottomBar,
                swipeRefreshLayout,
                new DynamicToolbar.Listener() {
                    @Override
                    public void onToolbarFullyHidden(boolean hidden) {
                    }

                    @Override
                    public void onBottomBarHidden(boolean hidden) {
                        // ボトムバーが隠れている間は、出すためのボタンを表示する
                        // (タブレット UI は上へスクロールすればバーが戻るので出さない)
                        boolean visible = hidden && !isFullScreen && !tabletUi;
                        if (visible == (showBottomButton.getVisibility() == View.VISIBLE)) return;
                        showBottomButton.startAnimation(AnimationUtils.loadAnimation(MainActivity.this,
                                visible ? R.anim.fade : R.anim.fadeout));
                        showBottomButton.setVisibility(visible ? View.VISIBLE : View.GONE);
                    }
                });
        dynamicToolbar.setHideBottomOnScroll(
                getSharedPreferences("AppPrefs", MODE_PRIVATE).getBoolean("hidebottom", true));
        dynamicToolbar.setUrlBarFixed(BrowserPrefs.isUrlBarFixed(this));
        // 下に置いたツールバーには URL バーがあるので、キーボードが出たらその上へ持ち上げる
        dynamicToolbar.setBottomFollowsIme(tabletBarsBottom);

        // バーの高さはレイアウト後に確定する(画面回転などで変わった時も取り直す)
        View.OnLayoutChangeListener heights = (v, l, t, r, b, ol, ot, or, ob) -> updateToolbarHeights();
        action_Bar.addOnLayoutChangeListener(heights);
        progressBar.addOnLayoutChangeListener(heights);
        bottomBar.addOnLayoutChangeListener(heights);
    }

    private void updateToolbarHeights() {
        if (dynamicToolbar == null || isFullScreen) return;
        if (isDualScreenActive()) {
            // 二画面: 上の画面にはステータスバーとプログレスバーだけ。URL バーとボトムバーは下の画面
            int top = Math.max(StatusBar.getBottom(), progressBar.getBottom());
            if (top > 0) dynamicToolbar.setHeights(top, 0);
            return;
        }
        // translationY を含まないレイアウト上の位置で測る
        int top = Math.max(StatusBar.getBottom(), Math.max(action_Bar.getBottom(), progressBar.getBottom()));
        if (tabletBarsBottom) {
            // タブレット UI でバーを下に: 上はステータスバーとプログレスバーだけ、下はタブとツールバー
            int above = Math.max(StatusBar.getBottom(), progressBar.getBottom());
            int below = action_Bar.getHeight();
            if (above > 0 && below > 0) dynamicToolbar.setHeights(above, below);
            return;
        }
        if (tabletUi) {
            // タブレット UI はボトムバーが無い(ボタンは上のツールバー)
            if (top > 0) dynamicToolbar.setHeights(top, 0);
            return;
        }
        int bottom = bottomBar.getHeight();
        if (top > 0 && bottom > 0) dynamicToolbar.setHeights(top, bottom);
    }

    private void checkHideUrlBar() {
        if (!nohideurl) {
            hideUrlBar();
        }
    }

    private void hideBottomBar() {
        dynamicToolbar.setBottomManuallyHidden(true);
    }

    private void showBottomBar() {
        // 手動で隠した場合はボトムバーだけ戻す。スクロールで隠れている場合はバー全体を出す。
        dynamicToolbar.setBottomManuallyHidden(false);
        dynamicToolbar.show(true);
    }

    private void hideUrlBar() {
        dynamicToolbar.hide(true);
    }

    private void showUrlBar() {
        if (dynamicToolbar != null) dynamicToolbar.show(true);
    }

    // ------------------------------------------------------------------
    // 二画面
    // ------------------------------------------------------------------

    /** 二画面表示を使える画面か(シークレット・新しいウィンドウも含む)。 */
    protected boolean supportsDualScreen() {
        return true;
    }

    @Nullable
    @Override
    public DualScreenController dualScreenController() {
        return isDualScreenActive() ? dualScreen : null;
    }

    private boolean isDualScreenActive() {
        return dualScreen != null && dualScreen.isActive();
    }

    private void setupDualScreen() {
        if (!supportsDualScreen()) return;
        dualScreen = new DualScreenController(this, new DualScreenController.Host() {
            @Nullable
            @Override
            public GeckoTabView currentTab() {
                return getCurrentTab();
            }

            @Override
            public boolean isStartPageShowing() {
                return startPageView != null && startPageView.getVisibility() == View.VISIBLE;
            }

            @Override
            public void onDualScreenChanged(boolean active) {
                applyDualScreenLayout(active);
            }

            @Override
            public void onBackFromSecondScreen() {
                onBackPressed();
            }

            @Override
            public boolean isSecondScreenLocked() {
                // タブ一覧を開いている間は、下の画面のバーやページを触れないようにする
                return tabSwitcher != null && tabSwitcher.isShowing();
            }

            @Override
            public boolean onSecondScreenKey(@NonNull KeyEvent event) {
                return gamepadInput != null && gamepadInput.onKey(event);
            }

            @Override
            public boolean onSecondScreenMotion(@NonNull MotionEvent event) {
                return gamepadInput != null && gamepadInput.onMotion(event);
            }
        }, findViewById(R.id.contentFrame), new View[]{action_Bar, bottomBar, softKeyboardSlot}, urlBarSuggestions);
        // 表示中のタブ・スタートページが変わったら、二画面の描画先もそれに合わせる
        effectLayer.getViewTreeObserver().addOnPreDrawListener(() -> {
            if (dualScreen != null) dualScreen.sync();
            return true;
        });
    }

    /**
     * 上の画面のバーの並びを一画面/二画面に合わせる。
     * 二画面: 上端にステータスバー、そのすぐ下にプログレスバー。URL バーとボトムバーは下の画面へ移っている。
     */
    private void applyDualScreenLayout(boolean dual) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progressBar.getLayoutParams();
        if (dual) {
            if (singleScreenProgressTop < 0) singleScreenProgressTop = lp.topMargin;
            lp.topMargin = StatusBar.getLayoutParams().height;
        } else if (singleScreenProgressTop >= 0) {
            lp.topMargin = singleScreenProgressTop;
            singleScreenProgressTop = -1;
        }
        progressBar.setLayoutParams(lp);
        // バーは上下の画面に分かれて常に出ているので、ボトムバーを隠すボタンは使わない
        hideBottomButton.setVisibility(dual ? View.GONE : View.VISIBLE);
        showBottomButton.setVisibility(View.GONE);
        if (urlBarSuggestions != null && !dual) {
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) urlBarSuggestions.getLayoutParams();
            p.topMargin = action_Bar.getBottom();
            urlBarSuggestions.setLayoutParams(p);
        }
        dynamicToolbar.setPinned(dual);
        updateToolbarHeights();
        progressBar.post(this::updateToolbarHeights);
        updateBottomGapColor();
    }

    /** URL バー・ボトムバーは二画面の間は下の画面にあるので、そちらも探す。 */
    @Override
    public <T extends View> T findViewById(int id) {
        T v = super.findViewById(id);
        if (v == null && dualScreen != null) v = dualScreen.findViewById(id);
        return v;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (dualScreen != null) dualScreen.onStart();
    }

    // ------------------------------------------------------------------
    // 常時表示のキーボード・コントローラー・仮想マウス
    // ------------------------------------------------------------------

    /**
     * キーボードの置き場を、ブラウザ画面の下(ボトムバーのさらに下)に作る。ページの表示領域はその分
     * 縮むので、キーボードがページを隠さない。二画面の時は URL バー・ボトムバーと一緒に下の画面へ移る。
     */
    private void setupSoftKeyboardSlot() {
        softKeyboardSlot = new FrameLayout(this);
        softKeyboardSlot.setVisibility(View.GONE);
        ViewGroup parent = (ViewGroup) effectLayer.getParent();
        if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) effectLayer.getLayoutParams();
            lp.height = 0;
            lp.weight = 1f;
            effectLayer.setLayoutParams(lp);
            parent.addView(softKeyboardSlot, parent.indexOfChild(effectLayer) + 1,
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            effectLayer.addView(softKeyboardSlot, lp);
        }
    }

    /** 設定に合わせてキーボードを出す・しまう・並べ直す。 */
    private void applySoftKeyboard() {
        if (softKeyboardSlot == null) return;
        boolean show = SoftKeyboard.isShown(this);
        GeckoTabView.suppressSoftInput = show;
        if (show) {
            if (softKeyboard == null) {
                softKeyboard = new SoftKeyboard(this, new SoftKeyboard.Host() {
                    @Nullable
                    @Override
                    public View keyTarget() {
                        // アドレスバーに入力中ならアドレスバーへ、それ以外は表示中のページへ
                        if (urlEditText != null && urlEditText.hasFocus()) return urlEditText;
                        return getCurrentTab();
                    }

                    @Override
                    public void onSoftKeyboardChanged() {
                        applySoftKeyboard();
                    }
                });
                softKeyboardSlot.addView(softKeyboard, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                softKeyboard.refresh();
            }
            softKeyboardSlot.setVisibility(View.VISIBLE);
        } else {
            if (softKeyboard != null) softKeyboard.releaseAll();
            softKeyboardSlot.setVisibility(View.GONE);
        }
    }

    private void setupController() {
        FrameLayout contentFrame = findViewById(R.id.contentFrame);
        virtualMouse = new VirtualMouse(this, new VirtualMouse.Host() {
            @Nullable
            @Override
            public GeckoTabView currentTab() {
                return getCurrentTab();
            }

            @Override
            public boolean isStartPageShowing() {
                return startPageView != null && startPageView.getVisibility() == View.VISIBLE;
            }

            @NonNull
            @Override
            public ViewGroup nativePageArea() {
                return findViewById(R.id.startPageContainer);
            }

            @Nullable
            @Override
            public DualScreenController dualScreen() {
                return isDualScreenActive() ? dualScreen : null;
            }

            @Override
            public void onMouseStateChanged(boolean active, boolean scrollMode) {
                String title = !active ? Lang.t("仮想マウスをオフにしました")
                        : scrollMode ? Lang.t("スクロールモード: 左スティックでスクロールします") : Lang.t("仮想マウスをオンにしました");
                TopMessage m = TopMessage.make(MainActivity.this,
                        virtualMouse != null && virtualMouse.isTouchMode() ? R.drawable.ic_tool_mouse
                                : R.drawable.ic_tool_gamepad, title)
                        .setDuration(1800);
                if (active && virtualMouse != null && virtualMouse.isTouchMode()) {
                    m.setSubtitle(VirtualMouse.touchStyle(MainActivity.this) == VirtualMouse.STYLE_DIRECT
                            ? Lang.t("タップでクリック・長押しで右クリック・2 本指でスクロール")
                            : Lang.t("タップでクリック・2 本指タップで右クリック・2 本指でスクロール"))
                            .setDuration(TopMessage.DURATION_SHORT);
                }
                m.show();
                updateSwipeRefreshForInput();
            }

            @Override
            public void sendLockedMove(float dx, float dy) {
                GeckoTabView tab = getCurrentTab();
                if (tab != null) tab.sendPointerLockMove(dx, dy);
            }

            @Override
            public void exitPointerLock() {
                GeckoTabView tab = getCurrentTab();
                if (tab != null) tab.exitPointerLock();
            }

            @Override
            public void openMouseSettings() {
                InputSheet.show(MainActivity.this, inputSheetHost, InputSheet.TAB_MOUSE);
            }
        }, contentFrame);
        contentFrame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> virtualMouse.onLayoutChanged());
        virtualGamepad = new VirtualGamepad();
        inputRouter = new InputRouter(() -> {
            // アドレスバーに入力中ならアドレスバーへ、それ以外は表示中のページへ
            if (urlEditText != null && urlEditText.hasFocus()) return urlEditText;
            return getCurrentTab();
        }, virtualMouse, virtualGamepad);
        virtualController = new VirtualController(this, contentFrame, inputRouter, virtualGamepad,
                new VirtualController.Host() {
                    @Override
                    public void openControllerSettings() {
                        InputSheet.show(MainActivity.this, inputSheetHost, InputSheet.TAB_CONTROLLER);
                    }

                    @Override
                    public void onControllerEditingChanged(boolean editing) {
                        updateSwipeRefreshForInput();
                    }
                });
        virtualController.apply();
        gamepadInput = new GamepadInput(this, virtualMouse, inputRouter, new GamepadInput.Host() {
            @Override
            public boolean canHandleController() {
                return tabSwitcher == null || !tabSwitcher.isShowing();
            }

            @Override
            public void openMenu() {
                if (popupbutton != null) showBottomMenu(popupbutton);
            }

            @Override
            public void exitFullScreen() {
                GeckoTabView tab = getCurrentTab();
                if (isFullScreen && tab != null) tab.getGeckoSession().exitFullScreen();
            }

            @Override
            public void goBack() {
                onBackPressed();
            }

            @Override
            public void goForward() {
                GeckoTabView tab = getCurrentTab();
                if (tab != null && tab.canGoForward()) tab.goForward();
            }

            @Override
            public void onPageModeChanged(boolean pageMode) {
                TopMessage.make(MainActivity.this, R.drawable.ic_tool_gamepad,
                                pageMode ? Lang.t("コントローラー: ページ入力") : Lang.t("コントローラー: ブラウザ操作"))
                        .setSubtitle(pageMode ? Lang.t("ボタンをページへ送ります。SELECT + START でブラウザ操作に戻ります")
                                : Lang.t("ボタンでブラウザを操作します。SELECT + START でページ入力に切り替えます"))
                        .setDuration(TopMessage.DURATION_SHORT)
                        .show();
            }

            @Override
            public boolean exitPointerLock() {
                GeckoTabView tab = getCurrentTab();
                if (tab == null || !tab.isPointerLocked()) return false;
                tab.exitPointerLock();
                return true;
            }
        });
    }

    /** ツールの「入力」のシートから操作するもの。 */
    private final InputSheet.Host inputSheetHost = new InputSheet.Host() {
        @Override
        public void applySoftKeyboard() {
            MainActivity.this.applySoftKeyboard();
        }

        @NonNull
        @Override
        public VirtualMouse virtualMouse() {
            return virtualMouse;
        }

        @NonNull
        @Override
        public VirtualController virtualController() {
            return virtualController;
        }

        @NonNull
        @Override
        public GamepadInput gamepadInput() {
            return gamepadInput;
        }
    };

    /** 仮想マウスのタッチ操作・仮想コントローラー・マウスの固定の間は、引っ張って更新しない(ゲーム中の誤操作を防ぐ)。 */
    private boolean isInputOverlayActive() {
        if (virtualMouse != null && (virtualMouse.isTouchMode() || virtualMouse.isPointerLocked())) return true;
        return virtualController != null && virtualController.isShown();
    }

    private void updateSwipeRefreshForInput() {
        if (swipeRefreshLayout == null) return;
        GeckoTabView current = getCurrentTab();
        if (isFullScreen) return;
        updateSwipeRefreshForUrl(current != null ? current.getUrl() : null);
    }

    // ------------------------------------------------------------------
    // マウスの固定(ポインターロック)
    // ------------------------------------------------------------------

    /** ページがマウスを固定した/解除した。カーソルを隠し、解除の方法を上に知らせる。 */
    private void onPointerLockChanged(@NonNull GeckoTabView tab, boolean locked) {
        if (!isCurrentTab(tab)) {
            if (locked) tab.exitPointerLock();
            return;
        }
        if (locked) {
            boolean usePhysical = !virtualMouse.isActive();
            virtualMouse.setPointerLocked(true, physicalMouseX, physicalMouseY);
            if (usePhysical) startPointerCapture(tab);
            TopMessage.make(this, R.drawable.ic_msg_pointer_lock, Lang.t("マウスポインタを非表示にしました"))
                    .setSubtitle(Lang.t("解除: Esc キー・戻る操作・3 本指でタップ"))
                    .setAction(Lang.t("解除"), tab::exitPointerLock)
                    .setDuration(TopMessage.DURATION_LONG)
                    .show();
        } else {
            virtualMouse.setPointerLocked(false, -1, -1);
            stopPointerCapture();
        }
        updateSwipeRefreshForInput();
    }

    /** 実際のマウスを捕まえて、Android のカーソルを隠し、動きだけを受け取る。 */
    private void startPointerCapture(@NonNull GeckoTabView tab) {
        stopPointerCapture();
        pointerCaptureTab = tab;
        tab.setOnCapturedPointerListener((view, event) -> {
            onCapturedPointer(event);
            return true;
        });
        tab.setFocusable(true);
        tab.requestFocus();
        tab.post(() -> {
            if (pointerCaptureTab == tab && tab.isPointerLocked()) tab.requestPointerCapture();
        });
    }

    private void stopPointerCapture() {
        GeckoTabView tab = pointerCaptureTab;
        pointerCaptureTab = null;
        if (tab == null) return;
        tab.setOnCapturedPointerListener(null);
        try {
            tab.releasePointerCapture();
        } catch (Exception ignored) {
        }
    }

    /** 捕まえたマウスの動き・ボタン・ホイール。 */
    private void onCapturedPointer(@NonNull MotionEvent e) {
        // マウスを捕まえるのはページがマウスを固定している間だけなので、固定中の速さを使う
        float speed = VirtualMouse.lockSpeed(this) / 5f;
        float density = getResources().getDisplayMetrics().density;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE: {
                float dx = 0, dy = 0;
                for (int h = 0; h < e.getHistorySize(); h++) {
                    dx += e.getHistoricalX(h);
                    dy += e.getHistoricalY(h);
                }
                dx += e.getX();
                dy += e.getY();
                GeckoTabView tab = getCurrentTab();
                if (tab != null) tab.sendPointerLockMove(dx / density * speed, dy / density * speed);
                break;
            }
            case MotionEvent.ACTION_BUTTON_PRESS:
                virtualMouse.press(e.getActionButton());
                break;
            case MotionEvent.ACTION_BUTTON_RELEASE:
                virtualMouse.release(e.getActionButton());
                break;
            case MotionEvent.ACTION_SCROLL: {
                float v = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
                float hz = e.getAxisValue(MotionEvent.AXIS_HSCROLL);
                virtualMouse.scrollPixels(-hz * 64 * density, -v * 64 * density);
                break;
            }
            default:
                break;
        }
    }

    /** 実際のマウスの位置を覚えておく(マウスを固定した時の位置)。 */
    private void rememberPhysicalMouse(@NonNull MotionEvent ev) {
        if (ev.getPointerCount() < 1 || ev.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE) return;
        if ((ev.getSource() & InputDevice.SOURCE_MOUSE) != InputDevice.SOURCE_MOUSE) return;
        GeckoTabView tab = getCurrentTab();
        if (tab == null) return;
        int[] loc = new int[2];
        tab.getLocationOnScreen(loc);
        physicalMouseX = ev.getRawX() - loc[0];
        physicalMouseY = ev.getRawY() - loc[1];
    }

    /** コントローラーのボタンを、割り当てた操作にする(割り当てていないボタンは普段どおり)。 */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // マウスを固定しているページは Esc で解除する(Esc はページへ渡さない。PC のブラウザと同じ)
        if (event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
            GeckoTabView tab = getCurrentTab();
            if (tab != null && tab.isPointerLocked()) {
                if (event.getAction() == KeyEvent.ACTION_UP) tab.exitPointerLock();
                return true;
            }
        }
        if (gamepadInput != null && gamepadInput.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    /** コントローラーのスティック(仮想マウスのカーソル・スクロール)。 */
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        rememberPhysicalMouse(ev);
        if (gamepadInput != null && gamepadInput.onMotion(ev)) return true;
        return super.dispatchGenericMotionEvent(ev);
    }

    /** タッチの開始/終了をバーの連動に伝える(指を離したら出し切る/隠し切る位置へ寄せる)。 */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        rememberPhysicalMouse(ev);
        if (dynamicToolbar != null) dynamicToolbar.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }



    private void applyMenuButtonStyle() {
        if (isSecretMode()) {
            // シークレットは常にダーク表示
            popupbutton.setImageResource(R.mipmap.aozora);
            return;
        }
        SharedPreferences prefs = getSharedPreferences(POPUP_PREFS_NAME, MODE_PRIVATE);
        int popup = prefs.getInt(KEY_POPUP, POPUPBUTTON_SYSTEM);
        SharedPreferences themeprefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int theme = themeprefs.getInt(KEY_THEME, THEME_SYSTEM);

        switch (popup) {
            case POPUPBUTTON_LIGHT:
                popupbutton.setImageResource(R.mipmap.aozora1);
                break;
            case POPUPBUTTON_DARK:
                popupbutton.setImageResource(R.mipmap.aozora);
                break;
            case POPUPBUTTON_SYSTEM:
            default:
                switch (theme) {
                    case THEME_LIGHT:
                        popupbutton.setImageResource(R.mipmap.aozora1);
                        break;
                    case THEME_DARK:
                        popupbutton.setImageResource(R.mipmap.aozora);
                        break;
                    case THEME_SYSTEM:
                    default:
                        // OS 側の設定に従う
                        int nightModeFlags = getResources().getConfiguration().uiMode
                                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;

                        if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                            popupbutton.setImageResource(R.mipmap.aozora);
                        } else {
                            popupbutton.setImageResource(R.mipmap.aozora1);
                        }
                        break;
                }
                break;
        }
    }

    private void applyArrowButtonStyle() {
        SharedPreferences prefs = getSharedPreferences(ARROW_PREFS_NAME, MODE_PRIVATE);
        int theme = prefs.getInt(KEY_ARROW, ARROWBUTTON_NEW);

        switch (theme) {
            case ARROWBUTTON_OLD:
                backButton.setImageResource(R.drawable.back);
                forwardButton.setImageResource(R.drawable.forward);
                break;
            case ARROWBUTTON_IOS:
                backButton.setImageResource(R.drawable.ios_back);
                forwardButton.setImageResource(R.drawable.ios_forward);
                break;
            case ARROWBUTTON_NEW:
            default:
                backButton.setImageResource(R.drawable.ic_sysbar_back);
                forwardButton.setImageResource(R.drawable.ic_sysbar_forward);
                break;
        }
    }

    /** Pixel 風の段の境目がないアイコン(レベル 0〜4)。 */
    private static final int[] SIGNAL_ICONS = {
            R.drawable.ic_sb_signal_0, R.drawable.ic_sb_signal_1, R.drawable.ic_sb_signal_2,
            R.drawable.ic_sb_signal_3, R.drawable.ic_sb_signal_4};
    private static final int[] WIFI_ICONS = {
            R.drawable.ic_sb_wifi_0, R.drawable.ic_sb_wifi_1, R.drawable.ic_sb_wifi_2,
            R.drawable.ic_sb_wifi_3, R.drawable.ic_sb_wifi_4};

    private static int signalIconRes(int level, boolean isWifi) {
        int[] icons = isWifi ? WIFI_ICONS : SIGNAL_ICONS;
        return icons[Math.max(0, Math.min(level, icons.length - 1))];
    }

    /** ステータスバーの電池を、デザインの設定(%・アイコン・アイコンと %)に合わせて出す。 */
    private void applyBatteryStyle() {
        if (batteryIcon == null || batteryStatus == null) return;
        int style = StatusBattery.style(this);
        boolean icon = style != StatusBattery.STYLE_PERCENT;
        boolean text = style != StatusBattery.STYLE_ICON;
        batteryIcon.setVisibility(icon ? View.VISIBLE : View.GONE);
        batteryStatus.setVisibility(text ? View.VISIBLE : View.GONE);
        if (lastBatteryPct >= 0) {
            if (icon) batteryDrawable.setLevel(lastBatteryPct, lastBatteryCharging);
            // アイコンと一緒に出す時は、充電中の印はアイコンの稲妻で分かるので数字だけにする
            String prefix = !icon && lastBatteryCharging ? "+" : "";
            batteryStatus.setText(prefix + lastBatteryPct + "%");
        }
        android.view.ViewGroup.MarginLayoutParams lp =
                (android.view.ViewGroup.MarginLayoutParams) batteryIcon.getLayoutParams();
        int gap = text ? Math.round(4 * getResources().getDisplayMetrics().density) : 0;
        if (lp.getMarginEnd() != gap) {
            lp.setMarginEnd(gap);
            batteryIcon.setLayoutParams(lp);
        }
        // アイコンだけの時はアイコンに読み上げの文を付ける
        String desc = lastBatteryPct >= 0 ? Lang.t("電池") + " " + lastBatteryPct + "%" : null;
        batteryIcon.setImportantForAccessibility(text
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO : View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        batteryIcon.setContentDescription(text ? null : desc);
        batteryStatus.setContentDescription(desc);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void updateSignalIcon(int level, boolean isWifi) {
        connectionStatus.setImageResource(signalIconRes(level, isWifi));
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void updateWifiIcon(int level, boolean isWifi) {
        wifi.setImageResource(signalIconRes(level, isWifi));
    }



    /**
     * ページの配色を今のテーマに合わせる。ライト/ダークが切り替わっていたら、サイトのキャッシュを
     * 消し終わってから開いているページを読み直す(前の配色のキャッシュが残ると切り替わらないため)。
     */
    private void applyPageColorScheme() {
        org.mozilla.geckoview.GeckoResult<Void> cleared = GeckoEngine.get(this).applyColorScheme();
        if (cleared == null) return;
        cleared.accept(v -> runOnUiThread(this::reloadTabsForColorScheme),
                e -> runOnUiThread(this::reloadTabsForColorScheme));
    }

    private void reloadTabsForColorScheme() {
        if (isFinishing() || isDestroyed()) return;
        for (GeckoTabView tab : tabs) {
            if (tab == null) continue;
            String url = tab.getUrl();
            if (url == null || url.isEmpty() || isInternalUrl(url)) continue;
            tab.reload();
        }
    }

    private void applyBarColors() {
        // Material 3 (DayNight) のテーマ属性から配色を取得。ライト/ダークは AppCompat 側で管理する。
        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int barColor = com.google.android.material.color.MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorSurfaceContainer, android.graphics.Color.BLACK);
        int iconColor = com.google.android.material.color.MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.GRAY);
        bottomBar = findViewById(R.id.bottomBar);
        bottomBar.setBackgroundColor(barColor);
        bottomBar.setGlassEnabled(GlassPrefs.enabled(this));
        // ステータスバーの電波・Wi-Fi は文字と同じ色で塗る(薄い背景部分の透明度はそのまま)
        int statusIconColor = com.google.android.material.color.MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.GRAY);
        connectionStatus.setColorFilter(statusIconColor, android.graphics.PorterDuff.Mode.SRC_IN);
        wifi.setColorFilter(statusIconColor, android.graphics.PorterDuff.Mode.SRC_IN);
        if (batteryDrawable != null) {
            batteryDrawable.setColors(statusIconColor, com.google.android.material.color.MaterialColors.getColor(
                    this, androidx.appcompat.R.attr.colorError, 0xFFE53935));
        }
        backButton.setColorFilter(iconColor);
        forwardButton.setColorFilter(iconColor);
        reloadButton.setColorFilter(iconColor);
        hideBottomButton.setColorFilter(iconColor);
        tabCountTextView.setTextColor(iconColor);
    }

    /** シークレットでは、Chrome と同じくアドレスバーの中(左端)にシークレットのマークを出す。 */
    private void showSecretUrlBarIcon() {
        android.graphics.drawable.Drawable icon =
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_ts_incognito);
        if (icon == null) return;
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(20 * density);
        icon = icon.mutate();
        icon.setBounds(0, 0, size, size);
        icon.setTint(com.google.android.material.color.MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.GRAY));
        urlEditText.setCompoundDrawablesRelative(icon, null, null, null);
        urlEditText.setCompoundDrawablePadding(Math.round(8 * density));
        updateUrlBarStartPadding();
        urlEditText.setContentDescription(Lang.t("シークレットタブ: アドレスバー"));
    }

    /**
     * URL バーの文字の左の余白。枠の中の左端にサイト情報(Favicon)のボタンがある間はその分を空け、
     * 入力中(ボタンを隠している間)は詰める。
     */
    private void updateUrlBarStartPadding() {
        float density = getResources().getDisplayMetrics().density;
        boolean favicon = faviconImageView != null && faviconImageView.getVisibility() == View.VISIBLE;
        int start = favicon ? 42 : isSecretMode() ? 12 : 16;
        urlEditText.setPaddingRelative(Math.round(start * density), urlEditText.getPaddingTop(),
                urlEditText.getPaddingEnd(), urlEditText.getPaddingBottom());
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void updateConnectionStatus() {
        if (connectivityManager == null) {
            connectionStatus.setVisibility(View.INVISIBLE);
            return;
        }
        if (connectivityManager != null) {
            connectionStatus.setVisibility(View.VISIBLE);
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            showDisconnected();
            return;
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            showDisconnected();
            return;
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            wifi.setImageResource(R.drawable.ic_sb_wifi_0);
            updateWifiSignal();
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            wifi.setVisibility(View.INVISIBLE);
            connectionStatus.setImageResource(R.drawable.ic_sb_signal_0);
            // モバイル電波強度は PhoneStateListener が自動更新
            // すぐに現在のモバイル電波強度を取得できるようにリスナーを一時的にトリガー
            if (telephonyManager != null) {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
            }
        } else {
            connectionStatus.setImageResource(R.drawable.ic_sb_signal_4);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void showDisconnected() {
        // 圏外: 電波・Wi-Fi アイコンを隠す。
        // (コールバックは onResume() で登録済み。ここで再登録すると二重登録で例外になる)
        connectionStatus.setVisibility(View.INVISIBLE);
        wifi.setVisibility(View.INVISIBLE);
        applyBarColors();
    }

    /** ネットワーク状態の監視を始める(二重登録しない)。 */
    private void registerNetworkCallback() {
        if (connectivityManager == null || networkCallback == null || networkCallbackRegistered) return;
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
            networkCallbackRegistered = true;
        } catch (RuntimeException e) {
            // 端末側の登録数上限などでも画面は落とさない
            e.printStackTrace();
        }
    }

    private void unregisterNetworkCallback() {
        if (connectivityManager == null || networkCallback == null || !networkCallbackRegistered) return;
        networkCallbackRegistered = false;
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void updateWifiSignal() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null) return;
            int rssi = wifiInfo.getRssi();
            int level = WifiManager.calculateSignalLevel(rssi, 5); // 0〜4
            if (level == 0) {
                Animation anim = AnimationUtils.loadAnimation(this, R.anim.slide_out_down);
                wifi.startAnimation(anim);
                wifi.setVisibility(View.INVISIBLE);
            } else {
                Animation anim = AnimationUtils.loadAnimation(this, R.anim.slide_in_up);
                wifi.startAnimation(anim);
                wifi.setVisibility(View.VISIBLE);
            }
            updateWifiIcon(level, true);
        }
    }

    /** 証明書を見るタブ(ページのプレビューなど、今のタブ以外)。null なら今のタブ。 */
    @Nullable private GeckoTabView certTab;
    /** 証明書のダイアログの「OK」で戻る先。null ならサイト情報。 */
    @Nullable private Runnable certBack;

    private void showCertificateDialog() {
        certTab = null;
        certBack = null;
        showCertificateDialogInternal();
    }

    /** 今のタブ以外(ページのプレビュー)の証明書を見る。 */
    private void showCertificateDialogFor(@NonNull GeckoTabView tab, @Nullable Runnable back) {
        certTab = tab;
        certBack = back;
        showCertificateDialogInternal();
    }

    private void afterCertificateDialog() {
        Runnable back = certBack;
        certTab = null;
        certBack = null;
        if (back != null) back.run(); else showSiteDialog();
    }

    private void showCertificateDialogInternal() {
        GeckoTabView webView = certTab != null ? certTab : getCurrentTab();
        SslCertificate cert = webView.getCertificate();
        if (cert == null) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(Lang.t("\uD83E\uDEAA セキュリティ証明書"))
                    .setMessage(Lang.t("証明書情報は、このサイトでは利用できません。"))
                    .setPositiveButton("OK", (dialog, which) -> afterCertificateDialog())
                    .show();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_certificate_info, null);

        TextView tvIssuedToCN = dialogView.findViewById(R.id.tvIssuedToCN);
        TextView tvIssuedToO  = dialogView.findViewById(R.id.tvIssuedToO);
        TextView tvIssuedToOU = dialogView.findViewById(R.id.tvIssuedToOU);

        TextView tvIssuedByCN = dialogView.findViewById(R.id.tvIssuedByCN);
        TextView tvIssuedByO  = dialogView.findViewById(R.id.tvIssuedByO);
        TextView tvIssuedByOU = dialogView.findViewById(R.id.tvIssuedByOU);

        TextView tvValidFrom  = dialogView.findViewById(R.id.tvValidFrom);
        TextView tvValidUntil = dialogView.findViewById(R.id.tvValidUntil);

        TextView tvSHA256     = dialogView.findViewById(R.id.tvSHA256);
        TextView tvSHA1       = dialogView.findViewById(R.id.tvSHA1);

        // 日付フォーマット
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());

        // SslCertificate から文字列を取得
        SslCertificate.DName to = cert.getIssuedTo();
        SslCertificate.DName by = cert.getIssuedBy();

        tvIssuedToCN.setText(Lang.t("共通名: ") + to.getCName());
        tvIssuedToO.setText(Lang.t("組織: ") + to.getOName());
        tvIssuedToOU.setText(Lang.t("組織単位: ") + to.getUName());

        tvIssuedByCN.setText(Lang.t("共通名: ") + by.getCName());
        tvIssuedByO.setText(Lang.t("組織: ") + by.getOName());
        tvIssuedByOU.setText(Lang.t("組織単位: ") + by.getUName());

        tvValidFrom.setText(Lang.t("発行: ") + sdf.format(cert.getValidNotBeforeDate()));
        tvValidUntil.setText(Lang.t("有効期限: ") + sdf.format(cert.getValidNotAfterDate()));

        boolean trusted = true;

        // X509Certificate に変換してフィンガープリント計算
        try {
            byte[] der = SslCertificate.saveState(cert).getByteArray("x509-certificate");
            if (der != null) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate x509 = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));

                tvSHA256.setText(Lang.t("SHA-256指紋: ") + getFingerprint(x509, "SHA-256"));
                tvSHA1.setText(Lang.t("SHA-1指紋: ") + getFingerprint(x509, "SHA-1"));

                trusted = isCertificateTrusted(x509);
            }
        } catch (Exception e) {
            e.printStackTrace();
            trusted = false;
        }

        if (!trusted) {
            // 信頼できない証明書の場合、別の処理へ
            showUntrustedCertificateDialog();
            return;
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("\uD83E\uDEAA セキュリティ証明書"))
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> afterCertificateDialog())
                .show();
    }





    private boolean isCertificateTrusted(X509Certificate cert) {
        try {
            // デフォルトのTrustManagerを取得
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);

            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    try {
                        ((X509TrustManager) tm).checkServerTrusted(new X509Certificate[]{cert}, "RSA");
                        return true; // 例外が出なければ信頼済み
                    } catch (CertificateException e) {
                        return false; // 検証失敗
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void showUntrustedCertificateDialog() {
        GeckoTabView webView = certTab != null ? certTab : getCurrentTab();
        SslCertificate cert = webView.getCertificate();
        if (cert == null) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(Lang.t("\uD83E\uDEAA セキュリティ証明書"))
                    .setMessage(Lang.t("証明書情報は, このサイトでは利用できません。"))
                    .setPositiveButton("OK", (dialog, which) -> afterCertificateDialog())
                    .show();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_certificate_info_failed, null);

        TextView tvIssuedToCN = dialogView.findViewById(R.id.tvIssuedToCN);
        TextView tvIssuedToO  = dialogView.findViewById(R.id.tvIssuedToO);
        TextView tvIssuedToOU = dialogView.findViewById(R.id.tvIssuedToOU);

        TextView tvIssuedByCN = dialogView.findViewById(R.id.tvIssuedByCN);
        TextView tvIssuedByO  = dialogView.findViewById(R.id.tvIssuedByO);
        TextView tvIssuedByOU = dialogView.findViewById(R.id.tvIssuedByOU);

        TextView tvValidFrom  = dialogView.findViewById(R.id.tvValidFrom);
        TextView tvValidUntil = dialogView.findViewById(R.id.tvValidUntil);

        TextView tvSHA256     = dialogView.findViewById(R.id.tvSHA256);
        TextView tvSHA1       = dialogView.findViewById(R.id.tvSHA1);

        // 日付フォーマット
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());

        // SslCertificate から文字列を取得
        SslCertificate.DName to = cert.getIssuedTo();
        SslCertificate.DName by = cert.getIssuedBy();

        tvIssuedToCN.setText(Lang.t("共通名: ") + to.getCName());
        tvIssuedToO.setText(Lang.t("組織: ") + to.getOName());
        tvIssuedToOU.setText(Lang.t("組織単位: ") + to.getUName());

        tvIssuedByCN.setText(Lang.t("共通名: ") + by.getCName());
        tvIssuedByO.setText(Lang.t("組織: ") + by.getOName());
        tvIssuedByOU.setText(Lang.t("組織単位: ") + by.getUName());

        tvValidFrom.setText(Lang.t("発行: ") + sdf.format(cert.getValidNotBeforeDate()));
        tvValidUntil.setText(Lang.t("有効期限: ") + sdf.format(cert.getValidNotAfterDate()));

        // X509Certificate に変換してフィンガープリント計算
        try {
            byte[] der = SslCertificate.saveState(cert).getByteArray("x509-certificate");
            if (der != null) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate x509 = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));

                tvSHA256.setText(Lang.t("SHA-256指紋: ") + getFingerprint(x509, "SHA-256"));
                tvSHA1.setText(Lang.t("SHA-1指紋: ") + getFingerprint(x509, "SHA-1"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("❌️ セキュリティ証明書"))
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> afterCertificateDialog())
                .show();
    }

    // faviconボタンのクリック時に呼び出すメソッド
    private void showSiteDialog() {
        GeckoTabView webView = getCurrentTab();
        if (webView == null || isFinishing()) return;
        String url = webView.getUrl();
        if (url == null) return;
        if (siteInfoDialog != null && siteInfoDialog.isShowing()) return;
        clearUrlBarFocus();
        closeKeyboard();

        Bitmap favicon = currentTabIndex >= 0 && currentTabIndex < tabInfos.size()
                ? tabInfos.get(currentTabIndex).getIcon() : null;
        if (favicon == null && faviconCache != null) favicon = faviconCache.get(url);

        siteInfoDialog = SiteInfoSheet.show(this, webView, url, favicon, isInternalUrl(url),
                new SiteInfoSheet.Host() {
                    @Override
                    public void showCertificateDialog() {
                        MainActivity.this.showCertificateDialog();
                    }

                    @Override
                    public void openInNewTab(@NonNull String target) {
                        addNewTab(target);
                    }
                });
        siteInfoDialog.setOnDismissListener(d -> {
            if (siteInfoDialog == d) siteInfoDialog = null;
        });
    }

    @Nullable private android.app.Dialog siteInfoDialog;

    private String getFingerprint(X509Certificate cert, String algorithm) throws Exception {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        byte[] der = cert.getEncoded();
        byte[] digest = md.digest(der);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            sb.append(String.format("%02X", digest[i]));
            if (i < digest.length - 1) sb.append(":");
        }
        return sb.toString();
    }

    /** このプロセスで更新を確認済みか(Activity を作り直しても再確認しない)。 */
    private static boolean updateCheckedThisProcess = false;

    private class CheckUpdateTask extends AsyncTask<Void, Void, UpdateChecker.Release> {
        @Override
        protected UpdateChecker.Release doInBackground(Void... voids) {
            return UpdateChecker.fetchLatest();
        }

        @Override
        protected void onPostExecute(UpdateChecker.Release info) {
            if (info == null || isFinishing() || isDestroyed()) return;
            // タグと今のバージョンを数字で比べ、新しい時だけ知らせる(同じ・古いタグでは出さない)
            if (UpdateChecker.isNewer(MainActivity.this, info)) {
                showUpdateDialog(info.version, info.notes);
            }
        }
    }

    private void showUpdateDialog(final String latestVersion, String changelog) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("アップデートがあります"))
                .setMessage(Lang.t("最新バージョン ") + latestVersion + Lang.t(" が利用可能です。\n\n【変更内容】\n") + changelog)
                .setPositiveButton(Lang.t("更新する"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // 初回セットアップと同じダウンロード画面で、新しい APK をダウンロードしてインストールする
                        startActivity(UpdateActivity.intent(MainActivity.this, latestVersion));
                        overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
                    }
                })
                .setNegativeButton(Lang.t("後で"), null)
                .show();
    }

    /** 検索履歴の最大件数(アドレスバーを押した直後に出す分)。 */
    private static final int MAX_URL_BAR_SEARCHES = 6;
    /** 検索サイトを開いている時に出す、履歴から推測したサイトの数。 */
    private static final int URL_BAR_SITE_COUNT = 4;

    /**
     * アドレスバーを押した直後(入力前)の候補を出す。検索履歴を新しい順に並べ、
     * Google・Bing・Yahoo・DuckDuckGo・Ecosia を開いている時はその上に推測したサイトを 4 つ並べる。
     */
    private void showUrlBarHistory() {
        List<String> searches = new ArrayList<>();
        List<UrlBarSuggestionsView.Site> sites = new ArrayList<>();
        GeckoTabView current = getCurrentTab();
        String pageUrl = current != null ? current.getUrl() : null;
        // シークレットモードでは通常の閲覧履歴を見せない
        if (!isSecretMode()) {
            Set<String> seen = new HashSet<>();
            for (int i = historyItems.size() - 1; i >= 0 && searches.size() < MAX_URL_BAR_SEARCHES; i--) {
                String q = searchQueryOf(historyItems.get(i).getUrl());
                if (q == null) continue;
                q = q.trim();
                if (!q.isEmpty() && seen.add(q.toLowerCase(Locale.ROOT))) searches.add(q);
            }
            if (isSearchSite(pageUrl)) sites = predictSites();
        }
        UrlBarSuggestionsView.Page page = null;
        if (current != null && hostOf(pageUrl) != null) {
            String title = current.getTitle();
            page = new UrlBarSuggestionsView.Page(title != null ? title : "", pageUrl, faviconCache.get(pageUrl));
        }
        urlBarSuggestions.showHistory(page, clipboardUrl(), sites, searches);
    }

    /** クリップボードに URL が入っていればそれを返す。 */
    @Nullable
    private String clipboardUrl() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) return null;
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence text = clip.getItemAt(0).getText();
            if (text == null) return null;
            String t = text.toString().trim();
            if (t.contains(" ") || t.contains("\n")) return null;
            return hostOf(t) != null ? t : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * ソフトキーボードの高さを見て、ページの表示領域と候補の画面をキーボードの上までに収める。
     * この画面はキーボードで縮まない設定(adjustNothing)なので、Google の AI モードのように
     * ページ下部にある入力欄がキーボードに隠れてしまう。
     */
    private void setupImeInsets() {
        View root = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            refreshImeHeight();
            // 既定の処理(fitsSystemWindows の余白)はそのまま行う
            return androidx.core.view.ViewCompat.onApplyWindowInsets(v, insets);
        });
        // 上の画面部品(AppCompat の fitsSystemWindows の枠など)がインセットを使い切ると、
        // ルートまでキーボードの高さが届かないことがある。キーボードの出入りはウィンドウの
        // 一番上(DecorView)でも見張り、毎回ウィンドウの生のインセットから高さを読み直す
        View decor = getWindow().getDecorView();
        if (tabletBarsBottom && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            // Android 10 以前は、画面がキーボードで縮まない設定(adjustNothing)だとインセットにも見えている範囲にも
            // キーボードの高さが入らず、全画面なので adjustResize も効かない。URL バーが下にある時はキーボードの
            // 上へ持ち上げる必要があるので、キーボードで縮む見えない窓(幅 0)を重ねて高さを測る
            decor.post(this::setupLegacyImeProbe);
        }
        androidx.core.view.ViewCompat.setWindowInsetsAnimationCallback(decor,
                new androidx.core.view.WindowInsetsAnimationCompat.Callback(
                        androidx.core.view.WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    @NonNull
                    @Override
                    public androidx.core.view.WindowInsetsCompat onProgress(
                            @NonNull androidx.core.view.WindowInsetsCompat insets,
                            @NonNull List<androidx.core.view.WindowInsetsAnimationCompat> running) {
                        return insets;
                    }

                    @Override
                    public void onEnd(@NonNull androidx.core.view.WindowInsetsAnimationCompat animation) {
                        if ((animation.getTypeMask() & androidx.core.view.WindowInsetsCompat.Type.ime()) != 0) {
                            refreshImeHeight();
                        }
                    }
                });
        // GeckoView はウィンドウの一番上でインセットを受け取るので、そこからも知らせてもらう
    }

    @Override
    public void onWindowInsetsChanged() {
        refreshImeHeight();
    }

    /**
     * ウィンドウの生のインセット(途中の画面部品に使われる前のもの)からキーボードの高さを読み、
     * 表示領域と候補の画面をキーボードの上までに収める。
     */
    private void refreshImeHeight() {
        androidx.core.view.WindowInsetsCompat raw =
                androidx.core.view.ViewCompat.getRootWindowInsets(getWindow().getDecorView());
        if (raw == null) return;
        int ime = raw.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
        // 画面はナビゲーションバーの上で終わっているので、その分は引く
        int nav = raw.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
        boolean visible = raw.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime());
        int height = visible ? Math.max(0, ime - nav) : 0;
        if (tabletBarsBottom && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            height = legacyImeHeightPx;
        }
        android.util.Log.d("AozoraIme", "ime=" + ime + " nav=" + nav + " visible=" + visible + " -> " + height);
        if (dynamicToolbar != null) dynamicToolbar.setImeHeight(height);
        // バーが下にある時は、候補をキーボードの上に持ち上げたツールバーの上までに収める
        if (urlBarSuggestions != null) {
            if (tabletBarsBottom) {
                // 候補の画面はツールバーより手前に重なるので、余白ではなく画面そのものをツールバーの上で終わらせる
                urlBarSuggestions.setBottomInset(0);
                ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) urlBarSuggestions.getLayoutParams();
                int bottom = height + action_Bar.getHeight();
                if (p.bottomMargin != bottom) {
                    p.bottomMargin = bottom;
                    urlBarSuggestions.setLayoutParams(p);
                }
            } else {
                urlBarSuggestions.setBottomInset(height);
            }
        }
    }

    /** Android 10 以前でキーボードの高さを測る見えない窓(タブレット UI でバーが下の時だけ)。 */
    @Nullable private android.widget.PopupWindow legacyImeProbe;
    /** その窓で測ったキーボードの高さ(px)。 */
    private int legacyImeHeightPx;

    /**
     * 幅 0・高さいっぱいの見えない窓を、キーボードで縮む設定(adjustResize)で出しておく。
     * キーボードが出ると窓の見えている範囲の下端が上がるので、その差をキーボードの高さとする。
     */
    private void setupLegacyImeProbe() {
        if (legacyImeProbe != null || isFinishing()) return;
        View decor = getWindow().getDecorView();
        if (decor.getWindowToken() == null) {
            decor.post(this::setupLegacyImeProbe);
            return;
        }
        View probe = new View(this);
        android.widget.PopupWindow pw = new android.widget.PopupWindow(probe, 0,
                ViewGroup.LayoutParams.MATCH_PARENT);
        pw.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        pw.setInputMethodMode(android.widget.PopupWindow.INPUT_METHOD_NEEDED);
        pw.setTouchable(false);
        pw.setFocusable(false);
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        android.graphics.Rect frame = new android.graphics.Rect();
        probe.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            probe.getWindowVisibleDisplayFrame(frame);
            View content = findViewById(android.R.id.content);
            int[] loc = new int[2];
            content.getLocationOnScreen(loc);
            int covered = loc[1] + content.getHeight() - frame.bottom;
            // ナビゲーションバーの出し入れなどの小さな差はキーボードとみなさない
            int h = covered > Math.round(80 * getResources().getDisplayMetrics().density) ? covered : 0;
            if (h != legacyImeHeightPx) {
                legacyImeHeightPx = h;
                refreshImeHeight();
            }
        });
        try {
            pw.showAtLocation(decor, android.view.Gravity.NO_GRAVITY, 0, 0);
            legacyImeProbe = pw;
        } catch (Exception e) {
            // 窓を出せなくても、ほかの動作には関係ない
        }
    }

    /** 候補の画面を作り、アドレスバーの下に重ねる。 */
    private void setupUrlBarSuggestions() {
        urlBarSuggestions = new UrlBarSuggestionsView(this);
        urlBarSuggestions.setHost(new UrlBarSuggestionsView.Host() {
            @Override
            public void onSubmit(@NonNull String text) {
                clearUrlBarFocus();
                closeKeyboard();
                loadUrlInCurrentTab(text);
            }

            @Override
            public void onFill(@NonNull String text) {
                urlEditText.setText(text);
                urlEditText.setSelection(urlEditText.length());
            }

            @Override
            public void onSharePage(@NonNull String title, @NonNull String url) {
                onShareUrl(url);
            }

            @Override
            public void onCopyUrl(@NonNull String url) {
                copyLink(url);
            }

            @Override
            public void onDeleteSearch(@NonNull String query) {
                deleteSearchFromHistory(query);
                if (urlEditText.hasFocus() && urlEditText.getText().toString().trim().isEmpty()) {
                    showUrlBarHistory();
                }
            }

            @Override
            public void onDismiss() {
                clearUrlBarFocus();
                closeKeyboard();
            }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        ((FrameLayout) findViewById(R.id.effectLayer)).addView(urlBarSuggestions, lp);
        // アドレスバーのすぐ下から出す(二画面では下の画面のページの上に重ねるので合わせない)。
        // タブレット UI でバーが下にある時は、ステータスバーの下からツールバーの上まで
        action_Bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (isDualScreenActive()) return;
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) urlBarSuggestions.getLayoutParams();
            int top = tabletBarsBottom ? StatusBar.getBottom() : b;
            if (p.topMargin != top) {
                p.topMargin = top;
                urlBarSuggestions.setLayoutParams(p);
            }
            if (tabletBarsBottom && b - t != ob - ot) refreshImeHeight();
        });
    }

    /** 最近の検索から、その言葉で検索した履歴を消す。 */
    private void deleteSearchFromHistory(String query) {
        boolean changed = false;
        for (int i = historyItems.size() - 1; i >= 0; i--) {
            String q = searchQueryOf(historyItems.get(i).getUrl());
            if (q != null && q.trim().equalsIgnoreCase(query.trim())) {
                historyItems.remove(i);
                changed = true;
            }
        }
        if (!changed) return;
        currentHistoryIndex = historyItems.size() - 1;
        saveHistory();
    }

    /** 検索サイト(Google・Bing・Yahoo・DuckDuckGo・Ecosia)のページか。 */
    private static boolean isSearchSite(@Nullable String url) {
        String host = hostOf(url);
        if (host == null) return false;
        return host.equals("google.com") || host.startsWith("google.") || host.contains(".google.")
                || host.equals("bing.com") || host.endsWith(".bing.com")
                || host.equals("yahoo.co.jp") || host.endsWith(".yahoo.co.jp")
                || host.equals("yahoo.com") || host.endsWith(".yahoo.com")
                || host.equals("duckduckgo.com") || host.endsWith(".duckduckgo.com")
                || host.equals("ecosia.org") || host.endsWith(".ecosia.org");
    }

    /** 検索サイトの検索結果ページなら検索した言葉を返す。 */
    @Nullable
    private static String searchQueryOf(@Nullable String url) {
        String host = hostOf(url);
        if (host == null) return null;
        Uri uri = Uri.parse(url);
        String path = uri.getPath() != null ? uri.getPath() : "";
        try {
            if ((host.startsWith("google.") || host.contains(".google.")) && path.equals("/search")) {
                return uri.getQueryParameter("q");
            }
            if ((host.equals("bing.com") || host.endsWith(".bing.com")) && path.equals("/search")) {
                return uri.getQueryParameter("q");
            }
            if ((host.equals("search.yahoo.co.jp") || host.equals("search.yahoo.com"))
                    && path.startsWith("/search")) {
                return uri.getQueryParameter("p");
            }
            if (host.equals("duckduckgo.com") || host.endsWith(".duckduckgo.com")) {
                return uri.getQueryParameter("q");
            }
            if ((host.equals("ecosia.org") || host.endsWith(".ecosia.org")) && path.equals("/search")) {
                return uri.getQueryParameter("q");
            }
        } catch (UnsupportedOperationException e) {
            // 階層を持たない URI
        }
        return null;
    }

    /** ファビコンはページの URL ごとに覚えているので、同じサイトの履歴から探す。 */
    @Nullable
    private Bitmap siteIcon(String host) {
        if (faviconCache == null) return null;
        for (int i = historyItems.size() - 1; i >= 0; i--) {
            String pageUrl = historyItems.get(i).getUrl();
            if (!host.equals(hostOf(pageUrl))) continue;
            Bitmap icon = faviconCache.get(pageUrl);
            if (icon != null) return icon;
        }
        return null;
    }

    /** http(s) の URL のホスト(先頭の www. は除く)。 */
    @Nullable
    private static String hostOf(@Nullable String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return null;
        String host = Uri.parse(url).getHost();
        if (host == null || host.isEmpty()) return null;
        host = host.toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    /**
     * 閲覧履歴から、よく・最近開いているサイトを推測する。新しい履歴ほど重く数え、
     * 検索サイト自体は除く。
     */
    private List<UrlBarSuggestionsView.Site> predictSites() {
        Map<String, Double> scores = new HashMap<>();
        Map<String, String> latestUrl = new HashMap<>();
        int n = historyItems.size();
        for (int i = 0; i < n; i++) {
            String url = historyItems.get(i).getUrl();
            String host = hostOf(url);
            if (host == null || isSearchSite(url)) continue;
            // 古い履歴は 1、一番新しい履歴は 2 として足していく
            double weight = 1.0 + (double) i / Math.max(1, n - 1);
            Double prev = scores.get(host);
            scores.put(host, (prev != null ? prev : 0) + weight);
            latestUrl.put(host, url);
        }
        List<String> hosts = new ArrayList<>(scores.keySet());
        Collections.sort(hosts, (a, b) -> Double.compare(scores.get(b), scores.get(a)));
        List<UrlBarSuggestionsView.Site> result = new ArrayList<>();
        for (String host : hosts) {
            if (result.size() >= URL_BAR_SITE_COUNT) break;
            Uri uri = Uri.parse(latestUrl.get(host));
            String origin = uri.getScheme() + "://" + uri.getHost();
            String label = host.startsWith("m.") ? host.substring(2) : host;
            result.add(new UrlBarSuggestionsView.Site(label, origin, siteIcon(host)));
        }
        return result;
    }

    /** 入力中に出す、以前に開いたページの数。 */
    private static final int URL_BAR_VISITED_COUNT = 4;

    /**
     * 入力した文字に合う、以前に開いたページ。URL の先頭・ドメインの区切り・URL の途中・タイトルの順に
     * 強く合うとみなし、よく・最近開いたページほど上に並べる。検索結果のページは「最近の検索」で出すので除く。
     */
    private List<UrlBarSuggestionsView.Page> matchVisitedPages(String query) {
        List<UrlBarSuggestionsView.Page> result = new ArrayList<>();
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty() || isSecretMode()) return result;
        String qUrl = q.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        if (qUrl.isEmpty()) return result;
        Map<String, Double> scores = new HashMap<>();
        Map<String, HistoryItem> latest = new HashMap<>();
        int n = historyItems.size();
        for (int i = 0; i < n; i++) {
            HistoryItem item = historyItems.get(i);
            String url = item.getUrl();
            if (hostOf(url) == null || searchQueryOf(url) != null) continue;
            String shown = url.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
            String key = shown.endsWith("/") ? shown.substring(0, shown.length() - 1) : shown;
            String lower = key.toLowerCase(Locale.ROOT);
            String title = item.getTitle() != null ? item.getTitle().toLowerCase(Locale.ROOT) : "";
            double match;
            if (lower.startsWith(qUrl)) match = 4;
            else if (lower.contains("." + qUrl) && lower.indexOf("." + qUrl) < hostEnd(lower)) match = 3;
            else if (lower.contains(qUrl)) match = 1.5;
            else if (title.contains(q)) match = 1;
            else continue;
            // 古い履歴は 1 倍、一番新しい履歴は 2 倍として足していく
            double weight = match * (1.0 + (double) i / Math.max(1, n - 1));
            Double prev = scores.get(key);
            scores.put(key, (prev != null ? prev : 0) + weight);
            HistoryItem old = latest.get(key);
            // タイトルの無い履歴で、前に覚えたタイトルを消さない
            if (old == null || (item.getTitle() != null && !item.getTitle().isEmpty())) latest.put(key, item);
        }
        List<String> keys = new ArrayList<>(scores.keySet());
        // 同じ点数なら短い URL(サイトのトップなど)を先に
        Collections.sort(keys, (a, b) -> {
            int c = Double.compare(scores.get(b), scores.get(a));
            return c != 0 ? c : Integer.compare(a.length(), b.length());
        });
        for (String key : keys) {
            if (result.size() >= URL_BAR_VISITED_COUNT) break;
            HistoryItem item = latest.get(key);
            String title = item.getTitle() != null ? item.getTitle() : "";
            Bitmap icon = faviconCache != null ? faviconCache.get(item.getUrl()) : null;
            result.add(new UrlBarSuggestionsView.Page(title, item.getUrl(), icon));
        }
        return result;
    }

    /** scheme と www. を除いた URL の、ホスト部分の終わりの位置。 */
    private static int hostEnd(String shownUrl) {
        int slash = shownUrl.indexOf('/');
        return slash >= 0 ? slash : shownUrl.length();
    }

    private void fetchSuggestions(String query) {
        if (query.trim().isEmpty()) return;

        String urlStr = "https://suggestqueries.google.com/complete/search?client=firefox&q=" + Uri.encode(query.trim());

        new Thread(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                } finally {
                    conn.disconnect();
                }

                JSONArray jsonArray = new JSONArray(sb.toString());
                JSONArray suggestions = jsonArray.getJSONArray(1);
                List<String> suggestionList = new ArrayList<>();
                for (int i = 0; i < suggestions.length(); i++) {
                    suggestionList.add(suggestions.getString(i));
                }

                runOnUiThread(() -> {
                    // 遅れて届いた古い入力の結果は捨てる(UrlBarSuggestionsView 側で判定)
                    urlBarSuggestions.setSuggestions(query, suggestionList);
                });

            } catch (Exception e) {
                // オフライン等: 先頭の入力内容だけのまま
                e.printStackTrace();
            }
        }).start();
    }

    public void updateTabCount() {
        if (tabCountTextView != null) {
            tabCountTextView.setText(String.valueOf(tabs.size()));
        }
        refreshTabStrip();
    }

    // カスタム WebChromeClient

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // この画面を表示できている = ロックは解除済み。同じプロセス内での作り直しにだけ引き継ぐ
        if (!redirectedToLock) outState.putString(STATE_UNLOCKED_NONCE, AuthTicket.PROCESS_NONCE);
    }

    /** Custom Tabs の Intent を簡易ブラウザの画面へ渡す(呼び出し元アプリの上に重ねて開く)。 */
    private void openCustomTab(@NonNull Intent source) {
        Intent ct = new Intent(source);
        ct.setClass(this, CustomTabActivity.class);
        // 呼び出し元アプリのタスクに重なっている時はそのまま重ねる。Aozora のタスクの根元にいる時
        // (呼び出し元が新しいタスクで開いた・ブラウザ本体の画面)は、簡易ブラウザだけの別タスクにする。
        // そうしないと、最小化した小窓のタスクに Aozora のほかの画面が入り込んでしまう
        ct.setFlags(isTaskRoot() ? Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK : 0);
        Uri referrer = getReferrer();
        if (referrer != null && "android-app".equals(referrer.getScheme())) {
            ct.putExtra(CustomTabActivity.EXTRA_CALLER_PACKAGE, referrer.getHost());
        }
        startActivity(ct);
        overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 外部から付けられた解除済みの印は使わない(ここでは解除の判断に使わないが、残さない)
        AuthTicket.consume(intent);

        // 他のアプリから開かれた簡易ブラウザ(Custom Tabs): タブは増やさず専用の画面で開く
        if (CustomTabActivity.isCustomTabIntent(intent)) {
            openCustomTab(intent);
            return;
        }

        // 「ページの準備ができました」の通知から: そのタブを開く
        int readyTabId = intent.getIntExtra(PageReadyNotifier.EXTRA_TAB_ID, -1);
        if (readyTabId >= 0) {
            intent.removeExtra(PageReadyNotifier.EXTRA_TAB_ID);
            int index = tabIds.indexOf(readyTabId);
            if (index >= 0) {
                if (tabSwitcher != null) tabSwitcher.hideNow();
                switchToTab(index);
            }
            return;
        }

        // 今後 getIntent() でも新しい Intent を取得できるようにする
        setIntent(intent);

        // 通常/シークレットの切り替え(既存の画面が前面に戻ってきた)
        if (intent.getBooleanExtra(EXTRA_NEW_TAB, false)) {
            intent.removeExtra(EXTRA_NEW_TAB);
            openNewTabAnimated(null);
            return;
        }
        if (intent.getBooleanExtra(EXTRA_SHOW_TAB_SWITCHER, false)) {
            boolean groups = intent.getBooleanExtra(EXTRA_SHOW_TAB_GROUPS, false);
            intent.removeExtra(EXTRA_SHOW_TAB_SWITCHER);
            intent.removeExtra(EXTRA_SHOW_TAB_GROUPS);
            showTabSwitcherNow();
            if (groups) tabSwitcher.showGroupsPane();
            else tabSwitcher.showTabsPane();
            return;
        }

        // 外部から ACTION_VIEW でURLが渡された場合
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();

            if (uri != null) {
                String url = uri.toString();

                // 新しいタブで開く
                addNewTab(url);
            }

            return;
        }

        // 既存の「url」extraも維持
        String url = intent.getStringExtra("url");
        if (url != null && !url.isEmpty()) {
            if (intent.getBooleanExtra(EXTRA_URL_IN_NEW_TAB, false)) {
                intent.removeExtra(EXTRA_URL_IN_NEW_TAB);
                if (tabSwitcher != null) tabSwitcher.hideNow();
                addNewTab(url);
            } else {
                load(url);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 端末のダークモード切り替えなどを Gecko(ページの配色)にも伝える
        org.mozilla.geckoview.GeckoRuntime runtime = GeckoEngine.get(this).getRuntime();
        if (runtime != null) runtime.configurationChanged(newConfig);
        applyPageColorScheme();
        applyBarColors(); // ダーク/ライト切り替え時に反映
        if (tabSwitcher != null) tabSwitcher.onThemeChanged();
        // メニューが表示中なら、新しい配色で開き直す
        if (aozoraMenuDialog != null && aozoraMenuDialog.isShowing()) {
            aozoraMenuDialog.dismiss();
            aozoraMenuDialog = null;
            showBottomMenu(popupbutton);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // デザインの設定で電池の表示を変えて戻ってきた時に反映する
        applyBatteryStyle();
        // 設定で一画面/二画面を切り替えて戻ってきた時に反映する
        if (dualScreen != null) dualScreen.refresh();
        if (pageReadyNotifier != null) pageReadyNotifier.onAppForeground();
        ExtensionManager.get(this).setTabHost(extensionTabHost);
        AozoraApplication app = (AozoraApplication) getApplication();
        if (authenticated) {
            // パスワード画面から来た直後: 画面の切り替えで立った「裏に回った」印だけ消して使い切る。
            // 以前はこの印を次に裏へ回るまで残していたので、解除後に最初にアプリを裏へ回して
            // 戻った時のロックが 1 回飛ばされていた。
            authenticated = false;
            app.clearAppWentToBackground();
        } else if (skipLockOnce) {
            // ファイル選択から戻ってきた → ロックせずフラグだけ消費する
            skipLockOnce = false;
            app.clearAppWentToBackground();
        } else if (app.isAppWentToBackground()) {
            app.clearAppWentToBackground();

            if (passwordManager.isPasswordSet() && !lockShowing) {
                lockShowing = true;
                Intent intent = new Intent(this, PasswordActivity.class);
                intent.putExtra(
                        "requirePasswordOnFirstLaunch",
                        false
                );

                intent.putExtra("usePasswordSkip", true);
                intent.putExtra("return_to_caller", true);
                startActivityForResult(intent, REQUEST_UNLOCK);
                // finish() と return は削除（以降の処理は通常どおり実行）
            }
        }
        SharedPreferences setupprefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean rebootApp = setupprefs.getBoolean("rebootApp", false);
        if (rebootApp) {
            SharedPreferences.Editor editor = setupprefs.edit();
            editor.putBoolean("rebootApp", false);
            editor.apply();
            // テーマや UI の切り替えを確実に反映するため、プロセスごと起動し直す
            // (閉じる間にタブを保存し終えるよう少し待つ)
            RestartActivity.restart(this, new Intent(this, BootingActivity.class), 1200);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            // API 24 以上のときだけ実行する処理
            registerNetworkCallback();
        }
        applyBarColors();
        // 時間帯の壁紙やニュースを新しくする
        if (startPageView != null && startPageView.getVisibility() == View.VISIBLE) startPageView.onShown(false);
        // テーマ設定の変更や、アプリが裏にいる間の端末のダークモード切り替えをページの配色に反映
        applyPageColorScheme();
        if (dynamicToolbar != null) {
            dynamicToolbar.setHideBottomOnScroll(
                    getSharedPreferences("AppPrefs", MODE_PRIVATE).getBoolean("hidebottom", true));
            dynamicToolbar.setUrlBarFixed(BrowserPrefs.isUrlBarFixed(this));
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            // API 24 以上のときだけ実行する処理
            updateWifiSignal();
            updateConnectionStatus();
        }
        // バッテリー残量更新開始
        if (!batteryReceiverRegistered) {
            registerReceiver(
                    batteryReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            );

            batteryReceiverRegistered = true;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            // API 24 以上のときだけ実行する処理
            if (telephonyManager != null) {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
            }
        }
        GeckoTabView webView = getCurrentTab();
        if (webView == null) return;
        // 戻ってきたときに実行したい処理
        webView.onResume();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (selectedUrl == null) return;

        // リンクを長押しした場合のメニュー
        if (selectedType == HIT_ANCHOR || selectedType == HIT_IMAGE_ANCHOR) {
            menu.setHeaderTitle(Lang.t("リンクメニュー"));
            menu.add(0, 1, 0, Lang.t("リンクをコピー"));
            menu.add(0, 2, 0, Lang.t("リンク先をダウンロード"));
            menu.add(0, 3, 0, Lang.t("リンク先を新しいタブで開く"));
        }

        // 画像を長押しした場合のメニュー
        if (selectedType == HIT_IMAGE || selectedType == HIT_IMAGE_ANCHOR) {
            menu.add(0, 4, 0, Lang.t("画像を保存"));
        }

    }

    // メニュー選択時の処理
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (selectedUrl == null) return false;

        switch (item.getItemId()) {
            case 1: // リンクをコピー
                copyLink(selectedUrl);
                return true;
            case 2: // リンク先をダウンロード
                downloadLink(selectedUrl);
                return true;
            case 3: // 新しいタブで開く
                addNewTab(selectedUrl);
                return true;
            case 4: // 画像を保存
                saveImage(selectedUrl);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        //MenuItem jsItem = menu.findItem(R.id.action_js);
        //if (jsItem != null) jsItem.setChecked(jsEnabled);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_ior1orl) {
            Intent intent = new Intent(this, CharCheckActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_screenshot) {
            takeScreenshot();
        } else if (itemId == R.id.action_txtphoto) {
            startActivity(new Intent(MainActivity.this, TextToImageActivity.class));
        } else if (itemId == R.id.action_asciiart) {
            startActivity(new Intent(MainActivity.this, ImageToTextActivity.class));
        } else if (itemId == R.id.action_pgdl) {
            startActivity(new Intent(MainActivity.this, PageDownloadActivity.class));
        } else if (itemId == R.id.action_num) {
            startActivity(new Intent(MainActivity.this, SimpleCalculatorActivity.class));
        } else if (itemId == R.id.action_qr) {
            startActivity(new Intent(MainActivity.this, QrCodeActivity.class));
        } else if (itemId == R.id.action_exec) {
            startActivity(new Intent(MainActivity.this, ExecActivity.class));
        } else if (itemId == R.id.action_grep) {
            startActivity(new Intent(MainActivity.this, GrepMd5Activity.class));
        } else if (itemId == R.id.action_htmlview) {
            startActivity(new Intent(MainActivity.this, HtmlEditorActivity.class));
        } else if (itemId == R.id.action_notepad) {
            startActivity(new Intent(MainActivity.this, NotepadActivity.class));
        } else if (itemId == R.id.action_qrcamera) {
            startActivity(new Intent(MainActivity.this, QrCameraActivity.class));
        } else if (itemId == R.id.action_File) {
            startActivity(new Intent(MainActivity.this, FileManager.class));
        } else if (itemId == R.id.action_negapoji) {
            applyNegapoji();
        } else if (itemId == R.id.action_translate) {
            translatePageToJapanese();
            return true;
            //} else if (itemId == R.id.action_js) {
            //    if (item.isChecked()) {
            //        disablejs();
            //        jsEnabled = false;
            //        Toast.makeText(MainActivity.this, "JavaScript無効", Toast.LENGTH_SHORT).show();
            //    } else {
            //        enablejs();
            //        jsEnabled = true;
            //        Toast.makeText(MainActivity.this, "JavaScript有効", Toast.LENGTH_SHORT).show();
            //    }
            //    item.setChecked(jsEnabled);
            //    pref.edit().putBoolean(KEY_JS_ENABLED, jsEnabled).apply();
        } else if (itemId == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivityForResult(intent, 1001); // requestCode を指定
        }
        return super.onOptionsItemSelected(item);
    }

    public void onRefresh() {
        GeckoTabView webView = getCurrentTab();
        if (webView != null) webView.reload();
    }

    @Nullable private android.app.Dialog aozoraMenuDialog;
    /** 開いているメニューのツールの一覧。コンソールでショートカットを足し外しした時に作り直す */
    @Nullable private List<AozoraMenuSheet.Item> menuToolItems;

    /**
     * ボトムバーの Aozora ボタンのメニュー。上によく使う画面のタイル、下に設定などの行を並べる。
     * シークレットでは履歴とシークレットの項目を出さない。
     */
    private void showBottomMenu(View anchor) {
        if (isFinishing()) return;
        if (aozoraMenuDialog != null && aozoraMenuDialog.isShowing()) return;
        clearUrlBarFocus();
        closeKeyboard();
        final boolean secret = isSecretMode();

        Runnable bookmarks = () -> openFromMenu(new Intent(MainActivity.this, BookmarksActivity.class)
                .putExtra(BookmarksActivity.EXTRA_SECRET, secret), false);
        Runnable history = this::showHistoryDialog;
        Runnable downloads = () -> openFromMenu(new Intent(MainActivity.this, DownloadListActivity.class), false);
        Runnable saved = () -> openFromMenu(new Intent(MainActivity.this, SavedPagesActivity.class)
                .putExtra(BookmarksActivity.EXTRA_SECRET, secret), false);
        Runnable top = this::startPage;
        Runnable secretMode = () -> {
            openSecretMode(true, false);
            overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
            Toast.makeText(getApplicationContext(), Lang.t("シークレットモード"), Toast.LENGTH_SHORT).show();
        };
        // ツールはメニューを閉じずに、メニューの中で一覧へ切り替える
        List<AozoraMenuSheet.Item> toolItems = buildToolItems();
        menuToolItems = toolItems;
        Runnable settings = () -> openFromMenu(new Intent(MainActivity.this, SettingsActivity.class), true);
        Runnable help = () -> openFromMenu(new Intent(MainActivity.this, HelpCenterActivity.class), true);

        List<AozoraMenuSheet.Item> tiles = new ArrayList<>();
        List<AozoraMenuSheet.Item> rows = new ArrayList<>();
        tiles.add(new AozoraMenuSheet.Item(R.drawable.ic_pm_bookmark, Lang.t("ブックマーク"), bookmarks));
        if (!secret) tiles.add(new AozoraMenuSheet.Item(R.drawable.ic_omni_history, Lang.t("履歴"), history));
        tiles.add(new AozoraMenuSheet.Item(R.drawable.ic_msg_download, Lang.t("ダウンロード"), downloads));
        tiles.add(new AozoraMenuSheet.Item(R.drawable.ic_pm_offline, Lang.t("保存済ページ"), saved));
        if (secret) {
            tiles.add(new AozoraMenuSheet.Item(R.drawable.ic_menu_home, Lang.t("トップページ"), top));
            tiles.add(new AozoraMenuSheet.Item(R.drawable.ic_menu_tools, Lang.t("ツール"), toolItems));
        } else {
            tiles.add(new AozoraMenuSheet.Item(R.drawable.ic_ts_incognito, Lang.t("シークレット"), secretMode));
            if (BrowserPrefs.homeEnabled(this)) {
                tiles.add(new AozoraMenuSheet.Item(R.drawable.ic_menu_home, Lang.t("トップページ"), top));
            }
            rows.add(new AozoraMenuSheet.Item(R.drawable.ic_menu_tools, Lang.t("ツール"), toolItems));
        }
        // コンソール: Java / Python の大きな入口と、追加したプログラムのショートカット
        rows.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_terminal, Lang.t("コンソール"), buildConsoleBig(), buildConsolePrograms())
                .childrenTitle(Lang.t("追加したプログラム"), Lang.t("Java・Python に追加したプログラムが、ここに並びます")));
        rows.add(new AozoraMenuSheet.Item(R.drawable.ic_menu_settings, Lang.t("設定"), settings));
        rows.add(new AozoraMenuSheet.Item(R.drawable.ic_menu_help, Lang.t("ヘルプ"), help));

        aozoraMenuDialog = AozoraMenuSheet.show(this, secret, tiles, rows, true);
        aozoraMenuDialog.setOnDismissListener(d -> {
            if (aozoraMenuDialog == d) aozoraMenuDialog = null;
        });
    }

    /** メニューから別の画面を開く(下からせり上がる)。forResult は設定などの戻りを受け取る画面。 */
    private void openFromMenu(Intent intent, boolean forResult) {
        if (forResult) {
            startActivityForResult(intent, 1001);
        } else {
            startActivity(intent);
        }
        overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
    }

    /** Aozora メニューの中に並べるツールの一覧 */
    private List<AozoraMenuSheet.Item> buildToolItems() {
        List<AozoraMenuSheet.Item> t = new ArrayList<>();
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_note, Lang.t("メモ"),
                () -> openFromMenu(new Intent(this, NotepadActivity.class), false)));
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_calc, Lang.t("電卓"),
                () -> openFromMenu(new Intent(this, Calculator.class), false)));
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_qr, Lang.t("QRコード作成"),
                () -> openFromMenu(new Intent(this, QrCodeActivity.class), false)));
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_qr_scan, Lang.t("QRスキャン"),
                () -> openFromMenu(new Intent(this, QrCameraActivity.class), false)));
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_folder, Lang.t("ファイル"),
                () -> openFromMenu(new Intent(this, FileManager.class), false)));
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_save, Lang.t("ページ保存"),
                () -> openFromMenu(new Intent(this, PageDownloadActivity.class), false)));
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_code, Lang.t("HTML エディタ"),
                () -> openFromMenu(new Intent(this, HtmlEditorActivity.class), false)));
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_screenshot, Lang.t("スクリーンショット"),
                () -> new Handler().postDelayed(this::takeScreenshot, 350)));
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_invert, Lang.t("ネガポジ"),
                () -> new Handler().postDelayed(this::applyNegapoji, 250)));
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_image, Lang.t("文字を画像に"),
                () -> openFromMenu(new Intent(this, TextToImageActivity.class), false)));
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_text, Lang.t("画像を文字に"),
                () -> openFromMenu(new Intent(this, ImageToTextActivity.class), false)));
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_search_file, "grep / MD5",
                () -> openFromMenu(new Intent(this, GrepMd5Activity.class), false)));
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_textfields, "I or 1 or l",
                () -> openFromMenu(new Intent(this, CharCheckActivity.class), false)));
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_terminal, "exec",
                () -> openFromMenu(new Intent(this, ExecActivity.class), false)));
        // キーボード・マウス・コントローラーは「入力」の 1 枚のシートにまとめる
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_input, Lang.t("入力"),
                () -> InputSheet.show(this, inputSheetHost)));
        t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_battery, Lang.t("バッテリー"),
                () -> openFromMenu(new Intent(this, BatteryCapacity.class), false)));
        // Java / Python の管理画面は「コンソール」から開く
        // JAR 実行ツールで「ツールに追加」した JAR。長押しでショートカットを削除できる
        for (JarStore.Entry e : JarStore.list(this)) {
            if (!e.shortcut) continue;
            t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_java, e.name, () -> JarStore.launch(this, e, null))
                    .onLongPress(remove -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle(Lang.t("ショートカットを削除しますか?"))
                            .setMessage(Lang.t("ツールから「") + e.name + Lang.t("」を外します。JAR は「Java 実行」に残ります。"))
                            .setPositiveButton(Lang.t("削除"), (d, w) -> {
                                JarStore.setShortcut(this, e.id, false);
                                remove.run();
                            })
                            .setNegativeButton(Lang.t("キャンセル"), null)
                            .show()));
        }
        // Python 実行ツールで「ツールに追加」したプログラム。長押しでショートカットを削除できる
        for (PyStore.Entry e : PyStore.list(this)) {
            if (!e.shortcut) continue;
            t.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_python, e.name, () -> PyStore.launch(this, e, null))
                    .onLongPress(remove -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle(Lang.t("ショートカットを削除しますか?"))
                            .setMessage(Lang.t("ツールから「") + e.name + Lang.t("」を外します。プログラムは「Python 実行」に残ります。"))
                            .setPositiveButton(Lang.t("削除"), (d, w) -> {
                                PyStore.setShortcut(this, e.id, false);
                                remove.run();
                            })
                            .setNegativeButton(Lang.t("キャンセル"), null)
                            .show()));
        }
        return t;
    }

    /** コンソールの大きなカード(Java 実行・Python 実行) */
    private List<AozoraMenuSheet.Item> buildConsoleBig() {
        List<AozoraMenuSheet.Item> big = new ArrayList<>();
        big.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_java, "Java",
                () -> openFromMenu(new Intent(this, JarToolActivity.class), false)).subtitle(Lang.t("JAR を実行 · 実験的な機能")));
        big.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_python, "Python",
                () -> openFromMenu(new Intent(this, PyToolActivity.class), false)).subtitle(Lang.t("スクリプトを実行 · 実験的な機能")));
        return big;
    }

    /** コンソールに並べる、Java / Python に追加したプログラム。長押しでツール・ホーム画面のショートカットを操作する */
    private List<AozoraMenuSheet.Item> buildConsolePrograms() {
        List<AozoraMenuSheet.Item> out = new ArrayList<>();
        for (JarStore.Entry e : JarStore.list(this)) {
            out.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_java, e.name, () -> JarStore.launch(this, e, null))
                    .tone(R.color.ps_tone_teal_bg, R.color.ps_tone_teal_fg)
                    .onLongPress(remove -> {
                        JarStore.Entry now = JarStore.find(this, e.id);
                        if (now == null) return;
                        programMenu(now.name, ProgramShortcuts.KIND_JAVA, now.id, now.shortcut,
                                on -> JarStore.setShortcut(this, now.id, on));
                    }));
        }
        for (PyStore.Entry e : PyStore.list(this)) {
            out.add(new AozoraMenuSheet.Item(R.drawable.ic_tool_python, e.name, () -> PyStore.launch(this, e, null))
                    .tone(R.color.ps_tone_blue_bg, R.color.ps_tone_blue_fg)
                    .onLongPress(remove -> {
                        PyStore.Entry now = PyStore.find(this, e.id);
                        if (now == null) return;
                        programMenu(now.name, ProgramShortcuts.KIND_PYTHON, now.id, now.shortcut,
                                on -> PyStore.setShortcut(this, now.id, on));
                    }));
        }
        return out;
    }

    private interface ShortcutToggle {
        void set(boolean on);
    }

    private void programMenu(String name, String kind, String id, boolean inTools, ShortcutToggle toggle) {
        String[] labels = {Lang.t("ホーム画面に追加"), inTools ? Lang.t("ツールのショートカットを削除") : Lang.t("ツールにショートカットを追加")};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(name)
                .setItems(labels, (d, w) -> {
                    if (w == 0) {
                        ProgramShortcuts.pin(this, kind, id, name);
                    } else {
                        toggle.set(!inTools);
                        // 開いているメニューの「ツール」にもすぐ反映する(一覧はメニューを開いた時に作っている)
                        List<AozoraMenuSheet.Item> tools = menuToolItems;
                        if (tools != null) {
                            tools.clear();
                            tools.addAll(buildToolItems());
                        }
                        Toast.makeText(this, inTools ? Lang.t("ショートカットを削除しました") : Lang.t("ツールに追加しました"),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /**
     * URL バー横のボタンの総合メニュー。下からせり上がり、外側のタップ・下へのスワイプで閉じる。
     * 上に「更新」「共有」、その下にページを残す項目と、ページの見方を変える項目をまとめて並べる。
     */
    private void showPageMenu() {
        GeckoTabView tab = getCurrentTab();
        if (tab == null || isFinishing()) return;
        if (pageMenuDialog != null && pageMenuDialog.isShowing()) return;
        clearUrlBarFocus();
        closeKeyboard();

        final String url = tab.getUrl();
        final String title = tab.getTitle();

        // 二画面の時は、URL バーのある下の画面に出す
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = DualScreenController.newBottomSheet(this);
        View sheet = LayoutInflater.from(dialog.getContext()).inflate(R.layout.sheet_page_menu, null);
        dialog.setContentView(SheetScroll.wrap(sheet)); // 横画面でも下までスクロールできるように
        dialog.setCanceledOnTouchOutside(true);
        com.google.android.material.bottomsheet.BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setDraggable(true);
        behavior.setSkipCollapsed(true); // 下へスワイプしたら途中で止まらずに閉じる
        behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);

        TextView titleView = sheet.findViewById(R.id.pm_title);
        TextView urlView = sheet.findViewById(R.id.pm_url);
        String host = hostOf(url);
        titleView.setText(title != null && !title.isEmpty() ? title : (host != null ? host : ""));
        urlView.setText(url != null ? url : "");

        boolean bookmarked = isBookmarked(url);
        ((ImageView) sheet.findViewById(R.id.pm_bookmark_icon)).setImageResource(
                bookmarked ? R.drawable.ic_pm_bookmark_filled : R.drawable.ic_pm_bookmark);
        ((TextView) sheet.findViewById(R.id.pm_bookmark_text)).setText(
                bookmarked ? Lang.t("ブックマークから削除") : Lang.t("ブックマークに追加"));

        com.google.android.material.materialswitch.MaterialSwitch desktopSwitch =
                sheet.findViewById(R.id.pm_desktop_switch);
        desktopSwitch.setChecked(tab.getSettings().isDesktopMode());

        bindPageMenuItem(sheet, R.id.pm_reload, dialog, this::reloadCurrentPage);
        bindPageMenuItem(sheet, R.id.pm_share, dialog, this::shareCurrentUrl);
        bindPageMenuItem(sheet, R.id.pm_bookmark, dialog, this::saveBookmark);
        bindPageMenuItem(sheet, R.id.pm_reading, dialog, this::addToReadingList);
        bindPageMenuItem(sheet, R.id.pm_offline, dialog, this::savePage);
        bindPageMenuItem(sheet, R.id.pm_startpage, dialog, () -> addCurrentPageToStartPage(url, title));
        bindPageMenuItem(sheet, R.id.pm_homescreen, dialog, () -> addCurrentPageToHomeScreen(url, title));
        bindPageMenuItem(sheet, R.id.pm_find, dialog, this::showFindInPageBar);
        // 翻訳済みのページでは「元の言語で表示」にする
        boolean translated = tab.isTranslated();
        ((TextView) sheet.findViewById(R.id.pm_translate_text)).setText(
                translated ? Lang.t("元の言語で表示") : Lang.t("翻訳"));
        bindPageMenuItem(sheet, R.id.pm_translate, dialog,
                translated ? pageTranslator::showOriginal : this::translatePageToJapanese);
        // 長押しで翻訳の設定(このサイトを翻訳しない など)
        sheet.findViewById(R.id.pm_translate).setOnLongClickListener(v -> {
            dialog.dismiss();
            pageTranslator.showSettings();
            return true;
        });
        ((TextView) sheet.findViewById(R.id.pm_reader_text)).setText(
                tab.isReaderActive() ? Lang.t("リーディングモードを終了") : Lang.t("リーディングモード"));
        bindPageMenuItem(sheet, R.id.pm_reader, dialog, this::toggleReaderMode);
        sheet.findViewById(R.id.pm_desktop).setOnClickListener(v -> {
            boolean desktop = !desktopSwitch.isChecked();
            desktopSwitch.setChecked(desktop);
            v.setEnabled(false);
            // スイッチが切り替わるのを見せてから閉じる
            v.postDelayed(() -> {
                dialog.dismiss();
                setDesktopSite(tab, desktop);
            }, 220);
        });

        // 項目を少しずつずらして下からふわっと出す
        ViewGroup content = (ViewGroup) sheet;
        float rise = 18 * getResources().getDisplayMetrics().density;
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(rise);
            child.animate().alpha(1f).translationY(0f)
                    .setStartDelay(60L + 35L * i).setDuration(260)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                    .start();
        }

        dialog.setOnDismissListener(d -> {
            if (pageMenuDialog == dialog) pageMenuDialog = null;
        });
        pageMenuDialog = dialog;
        dialog.show();
    }

    private void bindPageMenuItem(View sheet, int id, android.app.Dialog dialog, Runnable action) {
        sheet.findViewById(id).setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
    }

    /** 今のタブだけデスクトップ版のサイトを表示する/やめる。切り替えたら読み直す。 */
    private void setDesktopSite(@NonNull GeckoTabView tab, boolean desktop) {
        if (!tabs.contains(tab)) return;
        GeckoTabView.TabSettings s = tab.getSettings();
        if (desktop) {
            // UA を上書きしているとモバイルの UA のままになるので外す
            s.setUserAgentString(null);
            s.setDesktopMode(true);
        } else {
            applyTabSettings(tab);
            if (s.isDesktopMode()) {
                // 設定で「デスクトップ UA」にしている時も、このタブはモバイル表示にする
                s.setDesktopMode(false);
                s.setUserAgentString(defaultUserAgent());
            }
        }
        tab.reload();
        Toast.makeText(this, desktop ? Lang.t("デスクトップ版で表示します") : Lang.t("モバイル版で表示します"),
                Toast.LENGTH_SHORT).show();
    }

    /** 見ているページをスタートページのサイトに加える。 */
    private void addCurrentPageToStartPage(@Nullable String url, @Nullable String title) {
        String host = hostOf(url);
        if (host == null || startPageView == null) {
            Toast.makeText(this, Lang.t("このページは追加できません"), Toast.LENGTH_SHORT).show();
            return;
        }
        String name = title != null && !title.isEmpty() ? title : host;
        if (name.length() > 20) name = name.substring(0, 20);
        if (startPageView.addSite(name, url)) {
            Toast.makeText(this, Lang.t("スタートページに追加しました"), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, Lang.t("すでにスタートページにあります"), Toast.LENGTH_SHORT).show();
        }
    }

    /** 見ているページをホーム画面にショートカットとして置く(押すと Aozora で開く)。 */
    private void addCurrentPageToHomeScreen(@Nullable String url, @Nullable String title) {
        String host = hostOf(url);
        if (host == null) {
            Toast.makeText(this, Lang.t("このページは追加できません"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Toast.makeText(this, Lang.t("このホームアプリはショートカットの追加に対応していません"), Toast.LENGTH_SHORT).show();
            return;
        }
        String label = title != null && !title.isEmpty() ? title : host;
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url), this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Bitmap favicon = currentTabIndex >= 0 && currentTabIndex < tabInfos.size()
                ? tabInfos.get(currentTabIndex).getIcon() : null;
        if (favicon == null) favicon = faviconCache.get(url);
        androidx.core.graphics.drawable.IconCompat icon = favicon != null
                ? androidx.core.graphics.drawable.IconCompat.createWithAdaptiveBitmap(homeScreenIcon(favicon))
                : androidx.core.graphics.drawable.IconCompat.createWithResource(this, R.mipmap.ic_aozora);
        androidx.core.content.pm.ShortcutInfoCompat shortcut =
                new androidx.core.content.pm.ShortcutInfoCompat.Builder(this, "page_" + System.currentTimeMillis())
                        .setShortLabel(label.length() > 25 ? label.substring(0, 25) : label)
                        .setLongLabel(label)
                        .setIcon(icon)
                        .setIntent(open)
                        .build();
        androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(this, shortcut, null);
    }

    /** ファビコンを白地の真ん中に置いたアダプティブアイコン用の画像(周りはホームアプリが切り抜く)。 */
    private Bitmap homeScreenIcon(@NonNull Bitmap favicon) {
        int size = 216; // 108dp 相当。真ん中の 72dp(144px)は必ず見える
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        int icon = 88;
        int left = (size - icon) / 2;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(favicon, null, new android.graphics.Rect(left, left, left + icon, left + icon), paint);
        return out;
    }

    /** URL バーの横のショートカットのアイコン・表示を今の設定とページに合わせる。 */
    private void updateToolbarShortcut() {
        bindToolbarShortcut(findViewById(R.id.action_shortcut), ToolbarShortcut.SLOT_MAIN);
        if (tabletUi) {
            bindToolbarShortcut(findViewById(R.id.action_shortcut_tablet1), ToolbarShortcut.SLOT_TABLET_1);
            bindToolbarShortcut(findViewById(R.id.action_shortcut_tablet2), ToolbarShortcut.SLOT_TABLET_2);
        }
    }

    private void bindToolbarShortcut(@Nullable ImageButton b, int slot) {
        if (b == null) return;
        ToolbarShortcut.Option o = ToolbarShortcut.current(this, slot);
        GeckoTabView tab = currentTabIndex >= 0 && currentTabIndex < tabs.size() ? tabs.get(currentTabIndex) : null;
        boolean internal = tab == null || isInternalUrl(tab.getUrl());
        int icon = o.icon;
        String label = o.label;
        // ブックマーク済み・リーディングモード中は、押した時に起きることが分かるようにアイコンを変える
        if (ToolbarShortcut.BOOKMARK.equals(o.key) && tab != null && !internal && isBookmarked(tab.getUrl())) {
            icon = R.drawable.ic_pm_bookmark_filled;
        } else if (ToolbarShortcut.READER.equals(o.key) && tab != null && tab.isReaderActive()) {
            label = Lang.t("リーディングモードを終了");
        }
        b.setImageResource(icon);
        b.setContentDescription(label);
        b.setSelected(ToolbarShortcut.READER.equals(o.key) && tab != null && tab.isReaderActive());
        boolean show = !ToolbarShortcut.NONE.equals(o.key) && !(o.needsPage && internal);
        // 狭いツールバーでは、右の 2 つ(段階 1)→ 左端のショートカット(段階 2)の順に隠す
        boolean squeezed = tabletUi && (tabletCompactLevel >= 2
                || (tabletCompactLevel >= 1 && slot != ToolbarShortcut.SLOT_MAIN));
        if (squeezed) {
            b.setVisibility(View.GONE);
            return;
        }
        // タブレット UI では並びがずれないよう、使えない時は隠さずに薄くする
        if (tabletUi && !ToolbarShortcut.NONE.equals(o.key)) {
            b.setVisibility(View.VISIBLE);
            b.setEnabled(show);
            b.setAlpha(show ? 1f : 0.38f);
        } else {
            b.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    /** ツールバーのショートカットを押した。 */
    private void runToolbarShortcut(View button) {
        runToolbarShortcut(button, ToolbarShortcut.key(this));
    }

    private void runToolbarShortcut(View button, @NonNull String key) {
        clearUrlBarFocus();
        closeKeyboard();
        switch (key) {
            case ToolbarShortcut.SHARE: shareCurrentUrl(); break;
            case ToolbarShortcut.TRANSLATE: translatePageToJapanese(); break;
            case ToolbarShortcut.RELOAD: reloadCurrentPage(); break;
            case ToolbarShortcut.NEW_TAB: openNewTabAnimated(button); break;
            case ToolbarShortcut.BOOKMARK: saveBookmark(); break;
            case "note": openFromMenu(new Intent(this, NotepadActivity.class), false); break;
            case "calc": openFromMenu(new Intent(this, Calculator.class), false); break;
            case "qr": openFromMenu(new Intent(this, QrCodeActivity.class), false); break;
            case "qr_scan": openFromMenu(new Intent(this, QrCameraActivity.class), false); break;
            case "files": openFromMenu(new Intent(this, FileManager.class), false); break;
            case "save_page": openFromMenu(new Intent(this, PageDownloadActivity.class), false); break;
            case "html": openFromMenu(new Intent(this, HtmlEditorActivity.class), false); break;
            case "screenshot": takeScreenshot(); break;
            case "negapoji": applyNegapoji(); break;
            case "text_image": openFromMenu(new Intent(this, TextToImageActivity.class), false); break;
            case "image_text": openFromMenu(new Intent(this, ImageToTextActivity.class), false); break;
            case "grep": openFromMenu(new Intent(this, GrepMd5Activity.class), false); break;
            case "ior1orl": openFromMenu(new Intent(this, CharCheckActivity.class), false); break;
            case "exec": openFromMenu(new Intent(this, ExecActivity.class), false); break;
            case "battery": openFromMenu(new Intent(this, BatteryCapacity.class), false); break;
            case "java": openFromMenu(new Intent(this, JarToolActivity.class), false); break;
            case ToolbarShortcut.HOME: startPage(); break;
            case ToolbarShortcut.READER: toggleReaderMode(); break;
            default: break;
        }
        if (tabletUi) button.postDelayed(this::updateToolbarShortcut, 600);
    }

    private void shareCurrentUrl() {
        GeckoTabView current = getCurrentTab();
        String currentUrl = current != null ? current.getUrl() : null;
        if (currentUrl != null) {
            // QR コード・リンクのコピー・ほかのアプリから選べる共有シート
            ShareSheet.show(this, currentUrl, current.getTitle(),
                    tabSwitcherHost.getFaviconForUrl(currentUrl), false);
        }
    }






    /** ページをその場で翻訳する(Chrome / Firefox と同じ。翻訳サイトへは移動しない)。 */
    private void translatePageToJapanese() {
        pageTranslator.translateCurrentPage();
    }

    /** 本文だけを読みやすく表示するリーディングモードを開く/閉じる(内蔵拡張の reader.js)。 */
    private void toggleReaderMode() {
        GeckoTabView current = getCurrentTab();
        if (current == null || isInternalUrl(current.getUrl()) || !current.sendPageCommand("readerToggle")) {
            Toast.makeText(MainActivity.this, Lang.t("このページはリーディングモードで表示できません"), Toast.LENGTH_SHORT).show();
        }
    }

    private void applyNegapoji() {
        GeckoTabView current = getCurrentTab();
        if (current == null || !current.sendPageCommand("negapoji")) {
            Toast.makeText(MainActivity.this, Lang.t("このページでは使用できません"), Toast.LENGTH_SHORT).show();
        }
    }

    /** 長押しメニュー「リンクをダウンロード」。 */
    private void downloadLink(String url) {
        if (url == null || url.isEmpty()) return;
        runWithStoragePermission(() -> {
            GeckoTabView current = getCurrentTab();
            String ua = current != null ? current.getSettings().getUserAgentString() : null;
            if (!AppDownloader.downloadUrl(this, url, ua, false, isSecretMode()) && current != null) {
                // blob: など DownloadManager で扱えない URL はページに読み込ませ、Gecko のダウンロードに任せる
                current.loadUrl(url);
            }
        });
    }

    /**
     * Android 9 以前は公開フォルダへの保存に権限が必要なので、無ければ要求してから実行する。
     * Android 10 以降はそのまま実行する。
     */
    private void runWithStoragePermission(Runnable action) {
        if (!AppDownloader.needsStoragePermission(this)) {
            action.run();
            return;
        }
        pendingStorageAction = action;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
    }

    // ------------------------------------------------------------------
    // ページ内検索
    // ------------------------------------------------------------------

    /** 入力を止めてから検索するまでの待ち時間。 */
    private static final long FIND_DEBOUNCE_MS = 160;
    private final Runnable findRunnable = this::performFindInPage;
    @Nullable private FindMatchTrackView findTrack;
    @Nullable private View findCard;

    /** ページ内検索バーを、画面の上からカードが弾むように下りてくる動きで出す。 */
    private void showFindInPageBar() {
        if (findInPageBarView == null) {
            LayoutInflater inflater = LayoutInflater.from(this);
            findInPageBarView = inflater.inflate(R.layout.find_in_page_bar, null);
            findCard = findInPageBarView.findViewById(R.id.findCard);
            findTrack = findInPageBarView.findViewById(R.id.findTrack);
            etFindQuery = findInPageBarView.findViewById(R.id.etFindQuery);
            tvFindCount = findInPageBarView.findViewById(R.id.tvFindCount);
            btnFindPrev = findInPageBarView.findViewById(R.id.btnFindPrev);
            btnFindNext = findInPageBarView.findViewById(R.id.btnFindNext);
            btnFindClose = findInPageBarView.findViewById(R.id.btnFindClose);

            etFindQuery.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    // 決定キーは「次の一致へ」(入力中の検索は文字を打つたびに行っている)
                    if (totalMatches > 0) findStep(true);
                    else performFindInPage();
                    return true;
                }
                return false;
            });
            // 文字を打つたびに探す(少し待ってから)
            etFindQuery.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    etFindQuery.removeCallbacks(findRunnable);
                    if (findInPageBarView.getVisibility() == View.VISIBLE) {
                        etFindQuery.postDelayed(findRunnable, FIND_DEBOUNCE_MS);
                    }
                }
            });

            btnFindNext.setOnClickListener(v -> findStep(true));
            btnFindPrev.setOnClickListener(v -> findStep(false));
            btnFindClose.setOnClickListener(v -> hideFindInPageBar());

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP);
            addContentView(findInPageBarView, params);
        }
        if (findInPageBarView.getVisibility() == View.VISIBLE && findCard.getTranslationY() == 0f) {
            etFindQuery.requestFocus();
            return;
        }
        etFindQuery.setText("");
        updateFindResult(0, 0, false);
        findInPageBarView.setVisibility(View.VISIBLE);

        // カードは上から少し縮んだ状態で下りてきて、ばねで止まる。中身は少し遅れて順に現れる
        float density = getResources().getDisplayMetrics().density;
        findCard.animate().cancel();
        findCard.setAlpha(0f);
        findCard.setTranslationY(-48 * density);
        findCard.setScaleX(0.88f);
        findCard.setScaleY(0.7f);
        findCard.setPivotY(0f);
        findCard.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(420)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .start();
        View icon = findInPageBarView.findViewById(R.id.findIcon);
        icon.setRotation(-90f);
        icon.setScaleX(0.4f);
        icon.setScaleY(0.4f);
        icon.animate().rotation(0f).scaleX(1f).scaleY(1f).setStartDelay(120).setDuration(360)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
        View[] buttons = {btnFindPrev, btnFindNext, btnFindClose};
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setAlpha(0f);
            buttons[i].setTranslationX(16 * density);
            buttons[i].animate().alpha(1f).translationX(0f)
                    .setStartDelay(160L + 50L * i).setDuration(260)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        }

        etFindQuery.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etFindQuery, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void findStep(boolean forward) {
        GeckoTabView current = getCurrentTab();
        if (totalMatches <= 0 || current == null) return;
        current.findNext(forward);
        // 押した方向へ矢印を少し跳ねさせる
        View arrow = forward ? btnFindNext : btnFindPrev;
        float d = (forward ? 4 : -4) * getResources().getDisplayMetrics().density;
        arrow.animate().cancel();
        arrow.setTranslationY(d);
        arrow.animate().translationY(0f).setDuration(240)
                .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
    }

    private void performFindInPage() {
        GeckoTabView webView = getCurrentTab();
        if (webView == null || etFindQuery == null) return;
        String query = etFindQuery.getText().toString().trim();

        if (query.isEmpty()) {
            webView.clearMatches();
            updateFindResult(0, 0, false);
            return;
        }

        currentMatchIndex = 0;
        webView.setFindListener((activeMatchOrdinal, numberOfMatches) ->
                updateFindResult(activeMatchOrdinal, numberOfMatches, true));
        webView.findAllAsync(query);
    }

    /** 件数の札とトラックを更新する。見つからなかった時はカードを小さく横に揺らす。 */
    private void updateFindResult(int active, int total, boolean searched) {
        int before = totalMatches;
        currentMatchIndex = active;
        totalMatches = total;
        if (tvFindCount == null) return;
        boolean hasQuery = etFindQuery != null && etFindQuery.getText().toString().trim().length() > 0;
        String text = total > 0 ? (active + 1) + " / " + total : "0 / 0";
        if (!text.contentEquals(tvFindCount.getText())) {
            tvFindCount.setText(text);
            // 数字が変わったら札を一瞬だけ膨らませる
            tvFindCount.setScaleX(1.12f);
            tvFindCount.setScaleY(1.12f);
            tvFindCount.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
        }
        tvFindCount.setVisibility(hasQuery ? View.VISIBLE : View.GONE);
        int error = com.google.android.material.color.MaterialColors.getColor(tvFindCount,
                androidx.appcompat.R.attr.colorError);
        int onSecondary = com.google.android.material.color.MaterialColors.getColor(tvFindCount,
                com.google.android.material.R.attr.colorOnSecondaryContainer);
        tvFindCount.setTextColor(searched && hasQuery && total == 0 ? error : onSecondary);
        btnFindPrev.setEnabled(total > 0);
        btnFindNext.setEnabled(total > 0);
        btnFindPrev.setAlpha(total > 0 ? 1f : 0.38f);
        btnFindNext.setAlpha(total > 0 ? 1f : 0.38f);
        if (findTrack != null) findTrack.setMatches(active, total);
        if (searched && hasQuery && total == 0 && before != 0 && findCard != null) {
            float d = 6 * getResources().getDisplayMetrics().density;
            android.animation.ObjectAnimator.ofFloat(findCard, View.TRANSLATION_X, 0, d, -d, d / 2, -d / 2, 0)
                    .setDuration(320).start();
        }
    }

    private void hideFindInPageBar() {
        if (findInPageBarView == null || findInPageBarView.getVisibility() != View.VISIBLE) return;
        etFindQuery.removeCallbacks(findRunnable);
        GeckoTabView current = getCurrentTab();
        if (current != null) {
            current.clearMatches();
            current.setFindListener(null);
        }
        totalMatches = 0;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etFindQuery.getWindowToken(), 0);
        }
        etFindQuery.clearFocus();
        // 出てきた時と逆に、縮みながら上へ引っ込める
        float density = getResources().getDisplayMetrics().density;
        findCard.animate().cancel();
        findCard.animate().alpha(0f).translationY(-36 * density).scaleX(0.92f).scaleY(0.8f)
                .setDuration(200)
                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f))
                .withEndAction(() -> {
                    findInPageBarView.setVisibility(View.GONE);
                    findCard.setTranslationX(0f);
                })
                .start();
    }


    private void reloadCurrentPage() {
        GeckoTabView current = getCurrentTab();
        if (current != null) {
            // UA などの設定変更は再読み込みで反映される
            current.reload();
        }
    }

    private void takeScreenshot() {
        View root = getWindow().getDecorView().getRootView();
        int w = root.getWidth();
        int h = root.getHeight();
        if (w <= 0 || h <= 0) {
            Toast.makeText(MainActivity.this, Lang.t("スクリーンショット取得エラー: ビューサイズが無効"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Handler handler = new Handler(Looper.getMainLooper());
            PixelCopy.request(getWindow(), bmp, new PixelCopy.OnPixelCopyFinishedListener() {
                @Override
                public void onPixelCopyFinished(int copyResult) {
                    if (copyResult == PixelCopy.SUCCESS) {
                        GeckoTabView current = getCurrentTab();
                        if (current == null) {
                            saveScreenshot(bmp);
                            return;
                        }
                        current.capture(page -> {
                            if (page != null) {
                                int[] loc = new int[2];
                                current.getLocationInWindow(loc);
                                new Canvas(bmp).drawBitmap(page, loc[0], loc[1], null);
                            }
                            saveScreenshot(bmp);
                        });
                    } else {
                        Toast.makeText(MainActivity.this, Lang.t("スクリーンショットの取得に失敗しました"), Toast.LENGTH_SHORT).show();
                    }
                }
            }, handler);
        } else {
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            root.draw(canvas);
            saveScreenshot(bmp);
        }
    }

    private void saveScreenshot(Bitmap bmp) {
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                    String name = ts + ".png";
                    String relDir = Environment.DIRECTORY_DCIM + "/AirSky/Screenshot";
                    final String savedPath;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10 以降は DCIM/AirSky を直接作れない(すべてのファイルへのアクセスが無いと失敗する)ので、
                        // MediaStore に保存してフォルダも作ってもらう
                        android.content.ContentResolver cr = getContentResolver();
                        android.content.ContentValues v = new android.content.ContentValues();
                        v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name);
                        v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
                        v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, relDir);
                        v.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1);
                        Uri uri = cr.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                        if (uri == null) throw new java.io.IOException(Lang.t("保存先を作成できませんでした"));
                        try (java.io.OutputStream os = cr.openOutputStream(uri)) {
                            if (os == null || !bmp.compress(Bitmap.CompressFormat.PNG, 100, os)) {
                                throw new java.io.IOException(Lang.t("画像を書き込めませんでした"));
                            }
                        } catch (Exception e) {
                            cr.delete(uri, null, null);
                            throw e;
                        }
                        v.clear();
                        v.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
                        cr.update(uri, v, null, null);
                        savedPath = relDir + "/" + name;
                    } else {
                        File dir = new File(Environment.getExternalStorageDirectory(), relDir);
                        if (!dir.isDirectory() && !dir.mkdirs()) {
                            throw new java.io.IOException(Lang.t("保存先を作成できませんでした") + ": " + dir.getAbsolutePath());
                        }
                        File file = new File(dir, name);
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        }
                        android.media.MediaScannerConnection.scanFile(MainActivity.this,
                                new String[]{file.getAbsolutePath()}, new String[]{"image/png"}, null);
                        savedPath = file.getAbsolutePath();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, Lang.t("スクリーンショットを保存しました: ") + savedPath, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, Lang.t("スクリーンショット保存中にエラー: ") + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }








    private void enablejs() {
        GeckoTabView.TabSettings s = getCurrentTab().getSettings();
        s.setJavaScriptEnabled(true);
        reloadCurrentPage();
    }
    private void disablejs() {
        GeckoTabView.TabSettings s = getCurrentTab().getSettings();
        s.setJavaScriptEnabled(false);
        reloadCurrentPage();
    }

    public void load(String url) {
        if (tabSwitcher != null) tabSwitcher.hideNow();
        if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) {
            addNewTab(url);
            return;
        }
        GeckoTabView tab = loadTabIfNeeded(currentTabIndex);
        if (tab == null) return;
        openUrlInTab(tab, url);
        tabInfos.get(currentTabIndex).setUrl(url);
        updateStartPage();
        saveTabsState();
    }

    /**
     * URL をタブで開く。保存済みページ(アプリ内のファイルパス)の場合はファイルを読み込んで表示する。
     */
    private void openUrlInTab(GeckoTabView tab, String url) {
        if (url != null && url.startsWith("/")) {
            File file = new File(url);
            if (url.endsWith(".mht")) {
                Toast.makeText(this, Lang.t("以前のバージョンで保存したページ(.mht)は開けません"), Toast.LENGTH_LONG).show();
                return;
            }
            if (!file.exists()) {
                Toast.makeText(this, Lang.t("保存したページが見つかりません"), Toast.LENGTH_SHORT).show();
                return;
            }
            // data: URL に HTML を詰めると「#」以降が切り捨てられたり、大きなページが読めなかったりするので
            // ファイルとしてそのまま開く(Firefox で保存したページを開くのと同じ)
            tab.loadUrl(Uri.fromFile(file).toString());
            return;
        }
        tab.loadUrl(url);
    }

    /** UA の末尾に " AirSkyBrowser/バージョン" を付けた既定の User-Agent。 */
    private String defaultUserAgent() {
        return GeckoSession.getDefaultUserAgent() + APPEND_STR;
    }

    /**
     * 設定画面・ブラウザ設定の内容をタブに反映する(WebView 版の applyOptimizedSettings 相当)。
     */
    private void applyTabSettings(GeckoTabView tab) {
        SharedPreferences setupprefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean javaScript = setupprefs.getBoolean("javaScript", true);
        GeckoTabView.TabSettings s = tab.getSettings();
        s.setJavaScriptEnabled(javaScript && !jsEnabled);

        if (uaEnabled) {
            s.setDesktopMode(false);
            s.setUserAgentString(DOCOMO_UA);
        } else if (deskuaEnabled) {
            s.setUserAgentString(null);
            s.setDesktopMode(true);
        } else if (ct3uaEnabled) {
            s.setDesktopMode(false);
            s.setUserAgentString(CT3_UA);
        } else {
            s.setDesktopMode(false);
            s.setUserAgentString(defaultUserAgent());
        }
    }

    /**
     * 「スワイプで再読み込み」の設定に合わせる(サイトごとの例外はなし)。
     * ネイティブのスタートページでは、おすすめ記事などの更新に使う。
     */
    private void updateSwipeRefreshForUrl(String url) {
        swipeRefreshLayout.setEnabled(getSharedPreferences("AppPrefs", MODE_PRIVATE)
                .getBoolean("isSwipeReload", true));
    }



    private void onProgressChanged(int percentage){
        Animator animation = ObjectAnimator.ofInt(progressBar,"progress",percentage);
        animation.setDuration(500); // 0.5秒間でアニメーションする
        animation.setInterpolator(new DecelerateInterpolator());
        animation.start();
    }

    private void onProgressStep(int percentage) {
        ValueAnimator animator = ValueAnimator.ofInt(progressBar.getProgress(), percentage);
        animator.setDuration(500);
        animator.setInterpolator(null); // 線形
        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            // 10刻みでしか進まないように丸める
            int stepped = (value / 10) * 10;
            progressBar.setProgress(stepped);
        });
        animator.start();
    }

    /**
     * 読み込み完了後、波線がまっすぐ伸びきってから(Material の波線は 90% を超えると平らになる)
     * バーを消して 0 に戻す。
     */
    private void hideProgressBarDelayed() {
        progressHideHandler.removeCallbacksAndMessages(null);
        progressHideHandler.postDelayed(() -> {
            if (progressBar == null || isLoading) return;
            progressBar.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                if (!isLoading) progressBar.setProgressCompat(0, false);
                progressBar.setAlpha(1f);
            }).start();
        }, 800);
    }

    private final Handler progressHideHandler = new Handler(Looper.getMainLooper());

    // ------------------------------------------------------------------
    // GeckoView からのイベント(旧 WebViewClient / WebChromeClient)
    // ------------------------------------------------------------------

    /** シークレットモード(プライベートブラウジング)か。SecretActivity が true を返す。 */
    /** タブの Cookie・ストレージ等の区画。通常タブは共通(null)。 */
    @Nullable
    protected String sessionContextId() {
        return null;
    }

    protected boolean isSecretMode() {
        return false;
    }

    /** リンクを開くために作られた画面か(この後そのリンクのタブを開くので、空のタブは作らない)。 */
    private boolean isOpeningLink() {
        Intent intent = getIntent();
        return intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null;
    }

    /** タブを保存する所(新しいウィンドウは別に保存する)。 */
    protected String tabsStoreKey() {
        return KEY_TABS;
    }

    protected String currentTabStoreKey() {
        return KEY_CURRENT_TAB_ID;
    }

    /** タブの ID の始まり(ウィンドウごとに保存ファイルが重ならないようにする)。 */
    protected int firstTabId() {
        return 0;
    }

    /** タブグループを使うか(シークレット・新しいウィンドウでは使わない)。 */
    protected boolean supportsTabGroups() {
        return !isSecretMode();
    }

    /** スタートページをネイティブ UI(StartPageView)で表示するか。 */
    protected boolean useNativeStartPage() {
        return true;
    }

    private void setupStartPage() {
        if (!useNativeStartPage()) return;
        startPageView = new StartPageView(this);
        startPageView.setVisibility(View.GONE);
        startPageView.setHost(new StartPageView.Host() {
            @Override
            public void onOpenUrl(@NonNull String url) {
                load(url);
            }

            @Override
            public void onLinkMenu(@NonNull String url, @Nullable String title,
                                   @NonNull List<? extends kotlin.Pair<String, ? extends kotlin.jvm.functions.Function0<kotlin.Unit>>> extras) {
                // ページ内のリンクと同じメニュー。スタートページ独自の項目(削除・興味なし など)は後ろに足す
                List<LinkMenuSheet.Item> more = new ArrayList<>();
                for (kotlin.Pair<String, ? extends kotlin.jvm.functions.Function0<kotlin.Unit>> e : extras) {
                    String label = e.getFirst();
                    int icon = label.equals(Lang.t("削除")) ? R.drawable.ic_si_delete : R.drawable.ic_si_block;
                    more.add(new LinkMenuSheet.Item(icon, label, () -> e.getSecond().invoke()));
                }
                showLinkMenu(url, title, null, more);
            }

            @Override
            public void onOpenSecret() {
                openSecretMode(true, false);
                overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
            }

            @Override
            public void onPickWallpaper() {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                // 画像を選んで戻ってきた時にパスワード画面を出さない
                skipLockOnce = true;
                try {
                    startActivityForResult(Intent.createChooser(intent, Lang.t("壁紙を選択")), REQUEST_START_WALLPAPER);
                } catch (Exception e) {
                    skipLockOnce = false;
                    Toast.makeText(MainActivity.this, Lang.t("画像を選べるアプリがありません"), Toast.LENGTH_SHORT).show();
                }
            }
        });
        ((FrameLayout) findViewById(R.id.startPageContainer)).addView(startPageView,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** ボトムバーのカプセルの周りを、今のページの下端の色で塗る。 */
    private void updateBottomGapColor() {
        GeckoTabView current = getCurrentTab();
        updateBottomGapColor(current, current != null && isHomeUrl(current.getUrl()));
    }

    private void updateBottomGapColor(@Nullable GeckoTabView tab, boolean startPage) {
        if (bottomBar == null) return;
        // スタートページは下端がウィンドウの背景色(白/黒)へのグラデーションなので塗らなくてよい。
        // 二画面ではボトムバーはページに接していない(下の画面の URL バーの下)ので塗らない
        Integer color = startPage || tab == null || isDualScreenActive() ? null : tab.getBottomColor();
        bottomBar.setGapColor(color);
        updateNavigationBarColor(color);
    }

    /** テーマ既定のナビゲーションバーの色(ページの色に合わせるのをやめた時に戻す色)。 */
    @Nullable private Integer defaultNavBarColor;
    @Nullable private Boolean defaultLightNavBar;
    @Nullable private android.animation.ValueAnimator navBarAnimator;

    /**
     * ボトムバーのさらに下にあるシステムのナビゲーションバーも同じ色にして、
     * ページからボトムバーの下まで色がつながって見えるようにする。null ならテーマの色へ戻す。
     */
    private void updateNavigationBarColor(@Nullable Integer color) {
        android.view.Window window = getWindow();
        androidx.core.view.WindowInsetsControllerCompat insets =
                androidx.core.view.WindowCompat.getInsetsController(window, window.getDecorView());
        if (defaultNavBarColor == null) {
            defaultNavBarColor = window.getNavigationBarColor();
            defaultLightNavBar = insets.isAppearanceLightNavigationBars();
        }
        int target = color != null ? color : defaultNavBarColor;
        boolean light = color != null
                ? androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.5
                : defaultLightNavBar;
        // ナビゲーションのボタンの色(明/暗)は先に切り替える
        insets.setAppearanceLightNavigationBars(light);
        if (navBarAnimator != null) navBarAnimator.cancel();
        int from = window.getNavigationBarColor();
        if (from == target) return;
        // ボトムバーの余白と同じ速さで色を移り変わらせる
        navBarAnimator = android.animation.ValueAnimator.ofArgb(from, target);
        navBarAnimator.setDuration(300);
        navBarAnimator.addUpdateListener(a -> window.setNavigationBarColor((int) a.getAnimatedValue()));
        navBarAnimator.start();
    }

    /** ネイティブのスタートページを今表示しているタブ。 */
    @Nullable private GeckoTabView startPageTab;
    /** タブごとのスタートページのスクロール位置。無いタブ(新しいタブ)は一番上から表示する。 */
    private final Map<GeckoTabView, int[]> startPageScroll = new java.util.WeakHashMap<>();

    /** 表示中のタブがスタートページなら、ネイティブのスタートページを上に重ねる。 */
    private void updateStartPage() {
        if (startPageView == null) return;
        GeckoTabView current = getCurrentTab();
        boolean show = current != null && isHomeUrl(current.getUrl());
        boolean showing = startPageView.getVisibility() == View.VISIBLE;
        updateBottomGapColor(current, show);
        // スタートページは 1 つの画面をタブで使い回しているので、タブが変わったら
        // そのタブで見ていた位置へ移す(前のタブの途中の位置のまま出さない)
        GeckoTabView target = show ? current : null;
        if (target != startPageTab) {
            if (startPageTab != null && showing) startPageScroll.put(startPageTab, startPageView.getScrollPosition());
            if (target != null) {
                int[] pos = startPageScroll.get(target);
                startPageView.setScrollPosition(pos != null ? pos[0] : 0, pos != null ? pos[1] : 0);
            }
            startPageTab = target;
        }
        if (show == showing) return;
        startPageView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            startPageView.onShown();
        } else {
            startPageView.onHidden();
        }
    }

    /** ネイティブのスタートページが tab の表示として出ているか。 */
    private boolean isShowingStartPage(@Nullable GeckoTabView tab) {
        return startPageView != null && startPageView.getVisibility() == View.VISIBLE
                && tab != null && isCurrentTab(tab);
    }

    /** スタートページの URL。 */
    protected String homeUrl() {
        return "file:///android_asset/index.html";
    }

    /** スタートページ表示中にステータスバーへ出す名前。 */
    protected String homeSiteName() {
        return getString(R.string.new_tab);
    }

    /** スタートページかどうか。 */
    protected boolean isHomeUrl(@Nullable String url) {
        return url != null && (url.equals("file:///android_asset/index.html") ||
                url.equals("file:///android_asset/index_white.html") ||
                url.equals("file:///android_asset/secret.html"));
    }

    /** アプリ内蔵ページ(URL バーを隠さない・URL を表示しないページ)かどうか。 */
    protected boolean isInternalUrl(@Nullable String url) {
        return url != null && (isHomeUrl(url) ||
                url.equals("file:///android_asset/help.html") ||
                url.equals("file:///android_asset/error.html"));
    }

    private boolean isCurrentTab(GeckoTabView tab) {
        return currentTabIndex >= 0 && currentTabIndex < tabs.size() && tabs.get(currentTabIndex) == tab;
    }

    private final Handler saveTabsHandler = new Handler(Looper.getMainLooper());
    private final Runnable saveTabsRunnable = this::saveTabsState;

    /** タイトル変更などで頻繁に呼ばれるので、まとめてから保存する。 */
    private void scheduleSaveTabs() {
        saveTabsHandler.removeCallbacks(saveTabsRunnable);
        saveTabsHandler.postDelayed(saveTabsRunnable, 800);
    }

    private final GeckoTabView.Listener tabListener = new GeckoTabView.Listener() {
        @Override
        public void onPageStarted(GeckoTabView tab, String url) {
            // 別のページに移ったらタブ一覧のファビコンも取り直す
            int index = tabs.indexOf(tab);
            if (index >= 0 && index < tabInfos.size()) tabInfos.get(index).setIcon(null);
            if (pageReadyNotifier != null) pageReadyNotifier.onPageStarted(tab, url);
            if (isCurrentTab(tab)) {
                handlePageStarted(tab, url);
                updateStartPage();
            }
        }

        @Override
        public void onPageFinished(GeckoTabView tab, String url, boolean success) {
            handlePageFinished(tab, url, success);
            pageReadyNotifier.onPageFinished(tab, url, success);
            // タブ一覧のサムネイル用に、読み込み後のページを撮っておく(描画が落ち着くまで少し待つ)
            tabUnloadHandler.postDelayed(() -> {
                if (isCurrentTab(tab) && !tab.isDestroyed()
                        && (tabSwitcher == null || !tabSwitcher.isShowing())) {
                    captureTabSnapshot(tab);
                }
            }, 800);
        }

        @Override
        public void onProgressChanged(GeckoTabView tab, int progress) {
            if (!isCurrentTab(tab)) return;
            SharedPreferences setupprefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            boolean ProgressBarAnimation = setupprefs.getBoolean("ProgressBarAnimation", true);
            int p = Math.max(10, Math.min(progress, 100));
            if (ProgressBarAnimation) {
                MainActivity.this.onProgressChanged(p);
            } else {
                onProgressStep(p);
            }
        }

        @Override
        public void onTitleChanged(GeckoTabView tab, String title) {
            int index = tabs.indexOf(tab);
            if (index >= 0 && index < tabInfos.size() && title != null && !title.isEmpty()) {
                tabInfos.get(index).setTitle(title);
                if (tabSwitcher != null) tabSwitcher.refresh();
                refreshTabStrip();
            }
            if (isCurrentTab(tab) && !isHomeUrl(tab.getUrl()) && title != null && !title.isEmpty()) {
                sitename.setText(title);
            }
            scheduleSaveTabs();
        }

        @Override
        public void onLocationChanged(GeckoTabView tab, String url) {
            int index = tabs.indexOf(tab);
            if (index >= 0 && index < tabInfos.size() && url != null) {
                tabInfos.get(index).setUrl(url);
                refreshTabStrip();
            }
            if (isCurrentTab(tab)) {
                dynamicToolbar.onPageChanged();
                updateStartPage();
                if (!noUpdateUrl) updateUrlBar(tab);
                updateNavigationButtons();
                updateSwipeRefreshForUrl(url);
            }
            scheduleSaveTabs();
        }

        @Override
        public void onNavigationStateChanged(GeckoTabView tab) {
            if (isCurrentTab(tab)) updateNavigationButtons();
        }

        @Override
        public void onPageInfo(GeckoTabView tab, @Nullable String iconUrl) {
            requestFavicon(tab);
        }

        @Override
        public void onBottomColorChanged(GeckoTabView tab, @Nullable Integer color) {
            if (isCurrentTab(tab)) updateBottomGapColor();
        }

        @Override
        public void onScrollChanged(GeckoTabView tab, int scrollY, int oldScrollY) {
            // 戻る/進む・ズーム直後などの位置の飛びではバーを動かさない
            if (!isCurrentTab(tab) || nohideurl) return;
            dynamicToolbar.onPageScrolled(scrollY, oldScrollY);
        }

        @Override
        public void onSelectionWebSearch(GeckoTabView tab, String text) {
            addNewTab(BrowserPrefs.searchUrl(MainActivity.this, text));
        }

        @Override
        public void onSelectionTranslate(GeckoTabView tab, String query) {
            // ページのプレビューと同じシートで、翻訳の検索結果を見せる
            showPagePreview(BrowserPrefs.searchUrl(MainActivity.this, query));
        }

        @Override
        public boolean onExternalUrl(GeckoTabView tab, String url) {
            return handleCustomUrl(tab, url);
        }

        @Override
        public boolean onStoreRedirect(GeckoTabView tab, String pageUrl, String packageName) {
            return openStoreRedirectInApp(pageUrl, packageName);
        }

        @Override
        public void onNearBottomChanged(GeckoTabView tab) {
            if (isCurrentTab(tab)) dynamicToolbar.onNearBottomChanged();
        }

        @Nullable
        @Override
        public GeckoSession onNewWindow(GeckoTabView tab, String url) {
            // window.open / target="_blank" は新しいタブで開く
            int newId = nextTabId++;
            GeckoSession session = new GeckoSession(tab.getGeckoSession().getSettings());
            GeckoTabView newTab = createTab(newId, session);
            newTab.setRequestedUrl(url);
            addTabView(newTab, newId, url);
            return session;
        }

        @Override
        public void onCloseRequest(GeckoTabView tab) {
            // JS の window.close()
            int index = tabs.indexOf(tab);
            if (index < 0) return;
            android.view.animation.Animation fadeOut =
                    android.view.animation.AnimationUtils.loadAnimation(MainActivity.this, R.anim.tab_out);
            tab.startAnimation(fadeOut);
            closeTab(index);
            saveTabsState();
            if (tabSwitcher != null) tabSwitcher.refresh();
        }

        @Override
        public void onFullScreen(GeckoTabView tab, boolean fullScreen) {
            if (isCurrentTab(tab)) setFullScreenMode(fullScreen);
        }

        @Override
        public void onContextMenu(GeckoTabView tab, GeckoSession.ContentDelegate.ContextElement element) {
            if (isCurrentTab(tab)) showPageContextMenu(element);
        }

        @Override
        public void onDownload(GeckoTabView tab, WebResponse response) {
            handleDownloadResponse(response);
        }

        @Override
        public void onReaderModeChanged(GeckoTabView tab, boolean active, @Nullable String error) {
            if (error != null && isCurrentTab(tab)) {
                Toast.makeText(MainActivity.this, Lang.t("このページは本文を取り出せないため、リーディングモードで表示できません"),
                        Toast.LENGTH_SHORT).show();
            }
            if (isCurrentTab(tab)) updateToolbarShortcut();
        }

        @Nullable
        @Override
        public String onLoadError(GeckoTabView tab, @Nullable String url, WebRequestError error) {
            pageReadyNotifier.onLoadError(tab);
            if (isCurrentTab(tab)) {
                isLoading = false;
                reloadButton.setImageResource(R.drawable.reload);
                hideProgressBarDelayed();
                pageloading.setVisibility(View.INVISIBLE);
                if (swipeRefreshLayout.isRefreshing()) swipeRefreshLayout.setRefreshing(false);
            }
            return buildErrorPage(url, error);
        }

        @Override
        public void onSessionStateChanged(GeckoTabView tab) {
            scheduleSaveTabs();
        }

        @Override
        public void onTranslationStateChanged(GeckoTabView tab,
                TranslationsController.SessionTranslation.TranslationState state) {
            pageTranslator.onStateChanged(tab, state);
        }

        @Override
        public void onTranslationOffered(GeckoTabView tab) {
            pageTranslator.onOffered(tab);
        }

        @Override
        public void onTranslationExpected(GeckoTabView tab) {
            pageTranslator.onExpected(tab);
        }

        @Override
        public void onNoTranslatePage(GeckoTabView tab) {
            pageTranslator.onNoTranslatePage(tab);
        }

        @Override
        public void onPointerLockChanged(GeckoTabView tab, boolean locked) {
            MainActivity.this.onPointerLockChanged(tab, locked);
        }
    };

    private void handlePageStarted(GeckoTabView view, String url) {
        SharedPreferences setupprefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean ProgressBarAnimation = setupprefs.getBoolean("ProgressBarAnimation", true);
        boolean Vibrate = setupprefs.getBoolean("Vibrate", true);
        if (Vibrate) {
            vib.vibrate(20);
        }
        if (loadTabnoHideurl) {
            nohideurl = true;
        }
        isLoading = true;
        Animation anim = AnimationUtils.loadAnimation(MainActivity.this, R.anim.fade);
        pageloading.startAnimation(anim);
        pageloading.setVisibility(View.VISIBLE);
        sitename.setText("Loading...");
        showUrlBar();
        reloadButton.setImageResource(R.drawable.ic_close); // ×アイコンに変更
        webView = view;
        final String urlhere = url != null ? url : "";
        if (!noUpdateUrl) {
            updateUrlBar(view);
        }
        faviconImageView.setImageResource(R.drawable.transparent_vector);
        progressHideHandler.removeCallbacksAndMessages(null);
        progressBar.animate().cancel();
        progressBar.setAlpha(1f);
        progressBar.setVisibility(View.VISIBLE);
        if (ProgressBarAnimation) {
            onProgressChanged(10);
        } else {
            progressBar.setProgress(10);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> nohideurl = isInternalUrl(urlhere), 500);

        if (isHomeUrl(urlhere)) {
            sitename.startAnimation(anim);
            sitename.setText(homeSiteName());
        } else {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isCurrentTab(view)) return;
                String pageTitle = view.getTitle();
                if (pageTitle == null || pageTitle.isEmpty()) return;
                sitename.startAnimation(anim);
                sitename.setText(pageTitle);
            }, 600);
        }

        if (!isNewTab) {
            android.view.animation.Animation fadeIn =
                    android.view.animation.AnimationUtils.loadAnimation(MainActivity.this, R.anim.fade);
            view.startAnimation(fadeIn);
        }
    }

    private void handlePageFinished(GeckoTabView view, String url, boolean success) {
        boolean current = isCurrentTab(view);
        if (url == null) url = "";
        String title = view.getTitle();
        if (title == null || title.isEmpty()) title = url;

        if (current) {
            SharedPreferences setupprefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            boolean ProgressBarAnimation = setupprefs.getBoolean("ProgressBarAnimation", true);
            isLoading = false;
            reloadButton.setImageResource(R.drawable.reload); // リロードアイコンに戻す
            updateNavigationButtons();
            updateSwipeRefreshForUrl(url);
            if (swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
            if (ProgressBarAnimation) {
                onProgressChanged(100);
            } else {
                progressBar.setProgress(100);
            }
            hideProgressBarDelayed();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Animation anim = AnimationUtils.loadAnimation(MainActivity.this, R.anim.fadeout);
                pageloading.startAnimation(anim);
                pageloading.setVisibility(View.INVISIBLE);
            }, 500);
            if (isHomeUrl(url)) {
                sitename.setText(homeSiteName());
            } else if (view.getTitle() != null && !view.getTitle().isEmpty()) {
                sitename.setText(view.getTitle());
            }
            requestFavicon(view);
        }

        // エラーページ(data:)や読み込み失敗は履歴に残さない
        boolean recordable = success && !url.isEmpty() && !url.startsWith("data:") && !url.startsWith("about:");

        if (recordable && !isSecretMode()) {
            if (!isBackNavigation) {
                if (!noUpdateUrl) {
                    if (historyItems.size() > currentHistoryIndex + 1) {
                        historyItems.subList(currentHistoryIndex + 1, historyItems.size()).clear();
                    }
                    if (historyItems.isEmpty() || !historyItems.get(historyItems.size() - 1).getUrl().equals(url)) {
                        historyItems.add(new HistoryItem(title, url, System.currentTimeMillis()));
                        if (historyItems.size() > MAX_HISTORY_SIZE) {
                            historyItems.remove(0);
                        }
                        currentHistoryIndex = historyItems.size() - 1;
                        saveHistory();
                    }
                }
            } else {
                isBackNavigation = false;
            }
        }

        int index = tabs.indexOf(view);
        if (index >= 0 && index < tabInfos.size() && recordable) {
            TabInfo tabInfo = tabInfos.get(index);
            tabInfo.setUrl(url);
            tabInfo.setTitle(title);
            ArrayList<String> history = tabInfo.getHistory();
            if (history.isEmpty() || !history.get(history.size() - 1).equals(url)) {
                history.add(url);
            }
        }
        saveTabsState();
    }

    /**
     * http(s) 以外のリンク(intent:, tel:, アプリ独自のスキームなど)を Chrome と同じ考え方で処理する。
     *
     * <p>Android 11 以降はパッケージの公開範囲の制限があるため、resolveActivity() で
     * 「開けるアプリがあるか」を先に調べると、インストール済みのアプリでも見つからないことがある
     * (その結果、毎回 Play ストアが開いてしまっていた)。Chrome と同様に、まず実際に起動してみて、
     * 起動できなかった(ActivityNotFoundException)時だけ browser_fallback_url → Play ストアの順に進む。</p>
     */
    private boolean handleCustomUrl(GeckoTabView view, String url) {
        // エラーページの「ページの準備ができたらお知らせ」
        if (pageReadyNotifier != null && pageReadyNotifier.handleUrl(view, url)) return true;
        SharedPreferences setupprefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean intentJump = setupprefs.getBoolean("intentJump", true);
        String lower = url.toLowerCase(Locale.ROOT);
        try {
            if (lower.startsWith("tel:")) {
                if (intentJump) startExternal(new Intent(Intent.ACTION_DIAL, Uri.parse(url)));
                return true;
            }
            if (lower.startsWith("mailto:")) {
                if (intentJump) startExternal(new Intent(Intent.ACTION_SENDTO, Uri.parse(url)));
                return true;
            }

            Intent intent;
            if (lower.startsWith("intent:") || lower.startsWith("android-app:")) {
                intent = Intent.parseUri(url, lower.startsWith("intent:")
                        ? Intent.URI_INTENT_SCHEME : Intent.URI_ANDROID_APP_SCHEME);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            String fallbackUrl = intent.getStringExtra("browser_fallback_url");
            if (fallbackUrl != null && !isHttpUrl(fallbackUrl)) fallbackUrl = null;

            // 自分自身を指す intent はアプリを起動せずにページとして開く
            if (getPackageName().equals(intent.getPackage())) {
                if (fallbackUrl != null) view.loadUrl(fallbackUrl);
                return true;
            }
            if (!intentJump) {
                // 「アプリで開く」がオフ: 代わりのページがあればそれを表示する
                if (fallbackUrl != null) view.loadUrl(fallbackUrl);
                return true;
            }

            // Chrome と同じく、ページから任意のコンポーネントを直接起動させない
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            Intent selector = intent.getSelector();
            if (selector != null) {
                selector.addCategory(Intent.CATEGORY_BROWSABLE);
                selector.setComponent(null);
            }
            intent.setFlags(intent.getFlags() & ~(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION));

            if (startExternal(intent)) return true;

            // アプリが無い: 代わりのページ → Play ストアの順に試す
            if (fallbackUrl != null) {
                view.loadUrl(fallbackUrl);
                return true;
            }
            String packageName = intent.getPackage();
            if (packageName == null) packageName = getPackageNameFromScheme(lower);
            if (packageName != null && !lower.startsWith("market:")) {
                Intent market = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=" + packageName));
                market.addCategory(Intent.CATEGORY_BROWSABLE);
                if (startExternal(market)) return true;
            }
            Toast.makeText(this, Lang.t("このリンクを開けるアプリが見つかりません"), Toast.LENGTH_SHORT).show();
            return true;
        } catch (java.net.URISyntaxException e) {
            Log.w(TAG, "解析できない intent URL: " + url, e);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /** 押したリンクが Play ストアへ転送された: アプリがインストール済みなら、元のリンクをアプリで開く。 */
    private boolean openStoreRedirectInApp(@NonNull String pageUrl, @NonNull String packageName) {
        if (!getSharedPreferences("AppPrefs", MODE_PRIVATE).getBoolean("intentJump", true)) return false;
        return AppLinks.openInstalledApp(this, pageUrl, packageName);
    }

    /** 外部アプリを起動する。対応アプリが無ければ false。 */
    private boolean startExternal(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (android.content.ActivityNotFoundException e) {
            return false;
        } catch (SecurityException e) {
            Log.w(TAG, "外部アプリを起動できません", e);
            return false;
        }
    }

    private static boolean isHttpUrl(String url) {
        String l = url.toLowerCase(Locale.ROOT);
        return l.startsWith("http://") || l.startsWith("https://");
    }

    @Nullable
    private static String getPackageNameFromScheme(String url) {
        if (url.startsWith("fb:") || url.startsWith("facebook:")) return "com.facebook.katana";
        if (url.startsWith("twitter:")) return "com.twitter.android";
        if (url.startsWith("line:")) return "jp.naver.line.android";
        if (url.startsWith("instagram:")) return "com.instagram.android";
        if (url.startsWith("zoomus:")) return "us.zoom.videomeetings";
        return null;
    }

    /** 読み込みエラー時に表示するページ(data: URL)。 */
    /** 使えるネットワークが無いか(機内モード・圏外など)。 */
    private boolean isDeviceOffline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network active = cm.getActiveNetwork();
            if (active == null) return true;
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            return caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String buildErrorPage(@Nullable String failingUrl, WebRequestError error) {
        String title = Lang.t("ページの読み込み時に問題が発生しました");
        String description;
        String notifyLink = null;
        // 「接続する(安全ではありません)」の動作。null なら出さない
        String proceedScript = null;
        List<String> hints = new ArrayList<>();
        // 端末がオフラインなら、Gecko の分類(ホスト不明など)より「未接続」を優先して伝える
        int category = isDeviceOffline() ? WebRequestError.ERROR_CATEGORY_NETWORK : error.category;
        switch (category) {
            case WebRequestError.ERROR_CATEGORY_SECURITY:
                title = Lang.t("安全な接続ができませんでした");
                description = Lang.t("このサイトのセキュリティ証明書に問題があります。第三者が通信を盗み見ようとしている可能性があるため、読み込みを中止しました。");
                hints.add(Lang.t("URL が正しいか確認する"));
                hints.add(Lang.t("端末の日付と時刻が正しいか確認する"));
                hints.add(Lang.t("しばらくしてから再度アクセスする"));
                proceedScript = insecureProceedScript(failingUrl, error.code);
                break;
            case WebRequestError.ERROR_CATEGORY_URI:
                description = Lang.t("このアドレスは開けません。");
                hints.add(Lang.t("URL が正しいか確認する"));
                break;
            case WebRequestError.ERROR_CATEGORY_SAFEBROWSING:
                title = Lang.t("危険なサイトの可能性があります");
                description = Lang.t("このサイトは詐欺・マルウェア配布などの危険なサイトとして報告されているため、読み込みを中止しました。");
                break;
            case WebRequestError.ERROR_CATEGORY_NETWORK:
            default:
                if (isDeviceOffline()) {
                    description = Lang.t("インターネットに接続されていません。");
                } else if (error.code == WebRequestError.ERROR_UNKNOWN_HOST) {
                    description = Lang.t("サーバーが見つかりませんでした。");
                } else if (error.code == WebRequestError.ERROR_OFFLINE) {
                    description = Lang.t("インターネットに接続されていません。");
                } else if (error.code == WebRequestError.ERROR_NET_TIMEOUT) {
                    description = Lang.t("接続がタイムアウトしました。");
                } else if (error.code == WebRequestError.ERROR_CONNECTION_REFUSED) {
                    description = Lang.t("サーバーに接続を拒否されました。");
                } else {
                    description = Lang.t("ネットワークエラーが発生しました。");
                }
                hints.add(Lang.t("機内モードをオフにする"));
                hints.add(Lang.t("モバイルデータ または Wi-Fi を有効にする"));
                hints.add(Lang.t("電波状況を確認する"));
                hints.add(Lang.t("アプリケーションの更新を確認する"));
                if (failingUrl != null && !failingUrl.startsWith("data:") && pageReadyNotifier != null) {
                    notifyLink = pageReadyNotifier.createLink(failingUrl);
                }
                break;
        }
        StringBuilder li = new StringBuilder();
        for (String h : hints) li.append("<li>").append(escapeHtml(h)).append("</li>");
        String html = "<!DOCTYPE html>" +
                "<html lang=\"" + Lang.tag() + "\">" +
                "<head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<title>" + escapeHtml(Lang.t("エラー")) + "</title>" +
                "<style>" +
                "body { font-family: sans-serif; background-color:#111111; color:#aaaaaa; padding: 20px; line-height:1.6; }" +
                ".container { max-width:600px; margin:0 auto; text-align:left; }" +
                "h1 { font-size: 48px; margin-bottom: 10px; }" +
                "h2 { font-size: 20px; margin-bottom: 20px; }" +
                "p, li { font-size: 16px; }" +
                "ul { padding-left: 20px; }" +
                ".url { word-break: break-all; color:#777777; font-size:14px; }" +
                ".ready { display:inline-block; margin-top:16px; padding:12px 22px; border-radius:24px;" +
                " background:#8ab4f8; color:#202124; text-decoration:none; font-size:15px; font-weight:bold; }" +
                ".ready-note { font-size:13px; color:#777777; margin-top:10px; }" +
                ".proceed { margin-top:28px; font-size:12px; }" +
                ".proceed a { color:#777777; }" +
                ".proceed-error { font-size:12px; color:#f28b82; }" +
                "</style></head>" +
                "<body><div class=\"container\">" +
                "<h1>:(</h1>" +
                "<h2>" + escapeHtml(title) + "</h2>" +
                "<p>" + escapeHtml(description) + "</p>" +
                (failingUrl != null ? "<p class=\"url\">" + escapeHtml(failingUrl) + "</p>" : "") +
                "<p>" + escapeHtml(Lang.t("エラーコード: ")) + error.code + "</p>" +
                (hints.isEmpty() ? "" : "<p>" + escapeHtml(Lang.t("次をお試しください:")) + "</p><ul>" + li + "</ul>") +
                (notifyLink == null ? "" : "<a class=\"ready\" href=\"" + escapeHtml(notifyLink) + "\">"
                        + escapeHtml(Lang.t("ページの準備ができたらお知らせ")) + "</a>"
                        + "<p class=\"ready-note\">"
                        + escapeHtml(Lang.t("インターネットに接続されたら自動で読み込み、通知でお知らせします。")) + "</p>") +
                (proceedScript == null ? "" : "<p class=\"proceed\"><a id=\"proceed\" href=\"#\">"
                        + escapeHtml(Lang.t("接続する(安全ではありません)")) + "</a></p>"
                        + "<p id=\"proceedError\" class=\"proceed-error\" hidden>"
                        + escapeHtml(Lang.t("このサイトには接続できませんでした。")) + "</p>"
                        + "<script>" + proceedScript + "</script>") +
                "</div></body></html>";
        try {
            return "data:text/html;charset=utf-8;base64," +
                    Base64.encodeToString(html.getBytes("UTF-8"), Base64.NO_WRAP);
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    /**
     * エラーページの「接続する(安全ではありません)」を押した時のスクリプト。
     * 証明書のエラーは、Firefox の「危険性を承知で続行」と同じく Gecko のエラーページ用 API
     * (document.addCertException)でこのサイトの証明書を一時的に許可して読み込み直す。
     * それ以外(TLS の接続自体に失敗した等)で https の場合は、暗号化しない http で開く。
     * @return 出せない場合は null
     */
    @Nullable
    private static String insecureProceedScript(@Nullable String failingUrl, int errorCode) {
        if (failingUrl == null || failingUrl.startsWith("data:")) return null;
        String httpFallback = null;
        if (failingUrl.regionMatches(true, 0, "https://", 0, 8)) {
            httpFallback = "http://" + failingUrl.substring(8);
        }
        boolean badCert = errorCode == WebRequestError.ERROR_SECURITY_BAD_CERT;
        if (!badCert && httpFallback == null) return null;
        // JSON 文字列として埋め込む(引用符・</script> などを無害化)
        String fallback = httpFallback == null ? "null"
                : JSONObject.quote(httpFallback).replace("<", "\\u003c").replace(">", "\\u003e");
        return "(function(){" +
                "var fallback=" + fallback + ";" +
                "var link=document.getElementById('proceed');" +
                "function fail(){document.getElementById('proceedError').hidden=false;}" +
                "link.addEventListener('click',function(e){" +
                "e.preventDefault();" +
                (badCert
                        ? "if(document.addCertException){" +
                          "document.addCertException(true).then(function(){location.reload();}," +
                          "function(){if(fallback){location.href=fallback;}else{fail();}});" +
                          "return;}"
                        : "") +
                "if(fallback){location.href=fallback;}else{fail();}" +
                "});})();";
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final String OPEN_IN_NEW_GROUP = Lang.t("新しいタブをグループで開く");

    /**
     * リンクを、今のタブと一緒に新しいタブグループにまとめて裏で開く(Chrome と同じ)。
     * 今のタブが既にグループに入っていれば、そのグループに追加する。
     */
    private void openLinkInNewGroup(@NonNull String url) {
        if (tabGroups == null) return;
        int current = currentTabIndex;
        Integer currentGroup = current >= 0 && current < tabIds.size() ? tabGroupOf.get(tabIds.get(current)) : null;
        int index = addBackgroundTab(url, null);
        int newId = tabIds.get(index);
        Toast.makeText(this, Lang.t("バックグラウンドでタブを開きました。"), Toast.LENGTH_SHORT).show();
        if (currentGroup != null) {
            addTabToGroupEnd(newId, currentGroup);
            saveTabsState();
            return;
        }
        List<Integer> members = new ArrayList<>();
        if (current >= 0) members.add(current);
        members.add(tabIds.indexOf(newId));
        int gid = createTabGroup(members);
        TabGroup g = tabGroups.get(gid);
        if (g == null) return;
        TabGroupUi.showEditDialog(this, g, tabsInGroup(gid).size(), true, (title, color) -> {
            g.title = title;
            g.color = color;
            g.touch();
            tabGroups.save();
        });
    }

    /**
     * リンクの長押しメニュー(Chrome と同じ並び)。上にリンク先のファビコン・サイト名・URL を出す。
     * @param imageUrl 画像のリンクなら画像の URL(「画像を保存」を足す)
     */
    private void showLinkMenu(@NonNull String linkUrl, @Nullable String linkText, @Nullable String imageUrl) {
        showLinkMenu(linkUrl, linkText, imageUrl, new ArrayList<>());
    }

    /** @param extras 後ろに足す項目(スタートページの「削除」など) */
    private void showLinkMenu(@NonNull String linkUrl, @Nullable String linkText, @Nullable String imageUrl,
                              @NonNull List<LinkMenuSheet.Item> extras) {
        List<LinkMenuSheet.Item> items = new ArrayList<>();
        items.add(new LinkMenuSheet.Item(R.drawable.ic_lm_open_tab, Lang.t("新しいタブで開く"), () -> addNewTab(linkUrl)));
        if (tabGroups != null) {
            items.add(new LinkMenuSheet.Item(R.drawable.ic_tg_groups, OPEN_IN_NEW_GROUP,
                    () -> openLinkInNewGroup(linkUrl)));
        }
        // シークレットでは「新しいタブで開く」がシークレットタブになり、別のウィンドウには出さない
        if (!isSecretMode()) {
            items.add(new LinkMenuSheet.Item(R.drawable.ic_ts_incognito, Lang.t("シークレット タブで開く"),
                    () -> openInSecretTab(linkUrl)));
            items.add(new LinkMenuSheet.Item(R.drawable.ic_lm_window, Lang.t("新しいウィンドウで開く"),
                    () -> WindowActivity.open(this, linkUrl)));
        }
        items.add(new LinkMenuSheet.Item(R.drawable.ic_lm_preview, Lang.t("ページをプレビュー"), () -> showPagePreview(linkUrl)));
        items.add(new LinkMenuSheet.Item(R.drawable.ic_lm_link, Lang.t("リンクアドレスをコピー"), () -> copyLink(linkUrl)));
        if (linkText != null && !linkText.isEmpty()) {
            items.add(new LinkMenuSheet.Item(R.drawable.ic_lm_text, Lang.t("リンクテキストをコピー"), () -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText(Lang.t("リンクテキスト"), linkText));
                Toast.makeText(this, Lang.t("リンクテキストをコピーしました"), Toast.LENGTH_SHORT).show();
            }));
        }
        items.add(new LinkMenuSheet.Item(R.drawable.ic_lm_download, Lang.t("リンクをダウンロード"), () -> downloadLink(linkUrl)));
        items.add(new LinkMenuSheet.Item(R.drawable.ic_bm_reading, Lang.t("リーディング リストに追加"),
                () -> addLinkToReadingList(linkUrl, linkText)));
        items.add(new LinkMenuSheet.Item(R.drawable.ic_pm_share, Lang.t("リンクを共有"), () -> {
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, linkUrl);
            if (linkText != null && !linkText.isEmpty()) send.putExtra(Intent.EXTRA_SUBJECT, linkText);
            startActivity(Intent.createChooser(send, Lang.t("リンクを共有")));
        }));
        if (imageUrl != null) {
            items.add(new LinkMenuSheet.Item(R.drawable.ic_lm_image, Lang.t("画像を保存"), () -> saveImage(imageUrl)));
        }
        items.addAll(extras);
        LinkMenuSheet.show(this, linkUrl, tabSwitcherHost.getFaviconForUrl(linkUrl), items);
    }

    /** リンクをシークレットタブで開く(シークレットの画面が無ければ作る)。 */
    private void openInSecretTab(@NonNull String url) {
        saveTabsState();
        Intent intent = new Intent(this, SecretActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
    }

    /** リンクの長押しメニューの「リーディング リストに追加」。 */
    private void addLinkToReadingList(@NonNull String url, @Nullable String linkText) {
        if (isInternalUrl(url)) {
            Toast.makeText(this, Lang.t("このページは追加できません"), Toast.LENGTH_SHORT).show();
            return;
        }
        BookmarkStore store = BookmarkStore.get(this);
        if (store.findReading(url) != null) {
            Toast.makeText(this, Lang.t("すでにリーディングリストにあります"), Toast.LENGTH_SHORT).show();
            return;
        }
        String title = linkText != null && !linkText.isEmpty() ? linkText : LinkMenuSheet.siteName(url);
        long id = store.addBookmark(BookmarkStore.ROOT_READING, title, url, null);
        showBookmarkSavedBar(id, Lang.t("リーディングリストに追加しました"), Lang.t("タップしてリーディングリストを見る"), null,
                () -> {
                    startActivity(new Intent(this, BookmarksActivity.class)
                            .putExtra(BookmarksActivity.EXTRA_SECRET, isSecretMode())
                            .putExtra(BookmarksActivity.EXTRA_FOLDER, BookmarkStore.ROOT_READING));
                    overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
                });
    }

    /** 「ページをプレビュー」: タブを開かずにリンク先をシートの中で見る。 */
    private void showPagePreview(@NonNull String url) {
        PagePreviewSheet.show(this, url, isSecretMode(), sessionContextId(), new PagePreviewSheet.Host() {
            @Override
            public void showCertificateDialog() {
                MainActivity.this.showCertificateDialog();
            }

            @Override
            public void openInNewTab(@NonNull String target) {
                addNewTab(target);
            }

            @Nullable
            @Override
            public String buildErrorPage(@Nullable String failing, @NonNull WebRequestError error) {
                return MainActivity.this.buildErrorPage(failing, error);
            }

            @Override
            public void showCertificateDialogFor(@NonNull GeckoTabView tab) {
                MainActivity.this.showCertificateDialogFor(tab, () -> { });
            }

            @Nullable
            @Override
            public Bitmap faviconFor(@NonNull String target) {
                return tabSwitcherHost.getFaviconForUrl(target);
            }
        });
    }

    /** リンク・画像の長押しメニュー。 */
    private void showPageContextMenu(GeckoSession.ContentDelegate.ContextElement element) {
        final String linkUrl = element.linkUri;
        final String srcUrl = element.srcUri;
        final boolean isImage = element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE;
        final boolean isMedia = element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO
                || element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO;

        if (linkUrl != null) {
            selectedUrl = linkUrl;
            selectedType = isImage && srcUrl != null ? HIT_IMAGE_ANCHOR : HIT_ANCHOR;
            // リンクの文字(画像のリンクなら代替テキスト・タイトル)
            String text = element.linkText;
            if (text == null || text.trim().isEmpty()) text = element.altText;
            if (text == null || text.trim().isEmpty()) text = element.title;
            showLinkMenu(linkUrl, text != null ? text.trim() : null, isImage ? srcUrl : null);
        } else if (isImage && srcUrl != null) {
            selectedUrl = srcUrl;
            selectedType = HIT_IMAGE;
            final boolean isDataUrl = srcUrl.startsWith("data:");
            final String[] opts = isDataUrl
                    ? new String[]{Lang.t("リンクをコピー"), Lang.t("画像を保存")}
                    : new String[]{Lang.t("新しいタブで画像を開く"), Lang.t("リンクをコピー"), Lang.t("リンクをダウンロード"), Lang.t("画像を保存")};
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle(isDataUrl ? Lang.t("画像") : srcUrl)
                    .setItems(opts, (dialog, which) -> {
                        if (isDataUrl) {
                            if (which == 0) copyLink(srcUrl); else saveImage(srcUrl);
                        } else if (which == 0) {
                            addNewTab(srcUrl);
                        } else if (which == 1) {
                            copyLink(srcUrl);
                        } else if (which == 2) {
                            downloadLink(srcUrl);
                        } else if (which == 3) {
                            saveImage(srcUrl);
                        }
                    }).show();
        } else if (isMedia && srcUrl != null && !srcUrl.startsWith("blob:")) {
            final String[] opts = {Lang.t("新しいタブで開く"), Lang.t("リンクをコピー"), Lang.t("ダウンロード")};
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle(srcUrl)
                    .setItems(opts, (dialog, which) -> {
                        if (which == 0) {
                            addNewTab(srcUrl);
                        } else if (which == 1) {
                            copyLink(srcUrl);
                        } else {
                            downloadLink(srcUrl);
                        }
                    }).show();
        }
    }

    /** 動画などのページ全画面表示の切り替え(旧 onShowCustomView / onHideCustomView)。 */
    private void setFullScreenMode(boolean fullScreen) {
        if (isFullScreen == fullScreen) return;
        isFullScreen = fullScreen;
        dynamicToolbar.setFullScreen(fullScreen);
        if (dualScreen != null) dualScreen.setFullScreen(fullScreen);
        if (fullScreen) {
            swipeRefreshLayout.setEnabled(false);

            bottomBar.setVisibility(View.GONE);
            action_Bar.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);
            StatusBar.setVisibility(View.GONE);
            showBottomButton.setVisibility(View.GONE);

            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            bottomBar.setVisibility(tabletUi ? View.GONE : View.VISIBLE);
            action_Bar.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            StatusBar.setVisibility(View.VISIBLE);
            GeckoTabView current = getCurrentTab();
            updateSwipeRefreshForUrl(current != null ? current.getUrl() : null);

            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            showUrlBar();
        }
    }

    /** Gecko から渡されたダウンロードを保存する(旧 DownloadListener)。 */
    private void handleDownloadResponse(WebResponse response) {
        // 拡張機能(.xpi)はダウンロードせず、そのままインストールの確認へ進む
        if (isExtensionPackage(response)) {
            try {
                if (response.body != null) response.body.close();
            } catch (Exception ignored) {
            }
            ExtensionManager.get(this).install(response.uri, null);
            return;
        }
        runWithStoragePermission(() -> AppDownloader.saveResponse(this, response));
    }

    private static boolean isExtensionPackage(WebResponse response) {
        String type = response.headers.get("Content-Type");
        if (type == null) type = response.headers.get("content-type");
        if (type != null && type.toLowerCase(Locale.ROOT).contains("x-xpinstall")) return true;
        String path = Uri.parse(response.uri).getPath();
        return path != null && path.toLowerCase(Locale.ROOT).endsWith(".xpi")
                && response.uri.regionMatches(true, 0, "https://", 0, 8);
    }

    /** 拡張機能からのタブ操作(新しいタブ・タブの切り替え・閉じる)を受け持つ。 */
    private final ExtensionManager.TabHost extensionTabHost = new ExtensionManager.TabHost() {
        @Nullable
        @Override
        public GeckoSession onExtensionNewTab(@NonNull org.mozilla.geckoview.WebExtension ext,
                                              @Nullable String url, boolean active) {
            if (isFinishing()) return null;
            int newId = nextTabId++;
            org.mozilla.geckoview.GeckoSessionSettings.Builder b = new org.mozilla.geckoview.GeckoSessionSettings.Builder()
                    .usePrivateMode(isSecretMode())
                    .useTrackingProtection(true);
            String contextId = sessionContextId();
            if (contextId != null) b.contextId(contextId);
            GeckoSession session = new GeckoSession(b.build());
            GeckoTabView newTab = createTab(newId, session);
            newTab.setRequestedUrl(url);
            addTabView(newTab, newId, url != null ? url : "");
            return session;
        }

        @Override
        public void onExtensionOpenUrl(@NonNull String url) {
            if (isFinishing()) return;
            if (tabSwitcher != null) tabSwitcher.hideNow();
            addNewTab(url);
        }

        @Override
        public boolean onExtensionSelectTab(@NonNull GeckoSession session) {
            int index = indexOfSession(session);
            if (index < 0) return false;
            if (tabSwitcher != null) tabSwitcher.hideNow();
            switchToTab(index);
            return true;
        }

        @Override
        public boolean onExtensionLoadInTab(@NonNull GeckoSession session, @NonNull String url) {
            int index = indexOfSession(session);
            if (index < 0) return false;
            GeckoTabView tab = tabs.get(index);
            if (tab == null) return false;
            tab.loadUrl(url);
            return true;
        }

        @Override
        public boolean onExtensionCloseTab(@NonNull GeckoSession session) {
            int index = indexOfSession(session);
            if (index < 0) return false;
            closeTab(index);
            saveTabsState();
            if (tabSwitcher != null) tabSwitcher.refresh();
            return true;
        }

        @Nullable
        @Override
        public GeckoSession currentSession() {
            GeckoTabView tab = getCurrentTab();
            return tab != null && !tab.isDestroyed() ? tab.getGeckoSession() : null;
        }
    };

    private int indexOfSession(@NonNull GeckoSession session) {
        for (int i = 0; i < tabs.size(); i++) {
            GeckoTabView t = tabs.get(i);
            if (t != null && t.getGeckoSession() == session) return i;
        }
        return -1;
    }

    /** ファビコンを取得してアドレスバー横に表示する。 */
    private void requestFavicon(@Nullable GeckoTabView tab) {
        if (tab == null) return;
        boolean current = isCurrentTab(tab);
        int index = tabs.indexOf(tab);
        TabInfo info = (index >= 0 && index < tabInfos.size()) ? tabInfos.get(index) : null;
        String icon = tab.getIconUrl();
        if (icon != null && (icon.startsWith("http://") || icon.startsWith("https://"))) {
            // タブ一覧用に TabInfo にも残す。アドレスバー横は現在のタブの時だけ更新する。
            new DownloadFaviconTask(info, current).execute(icon);
        } else if (current) {
            faviconImageView.setImageResource(R.drawable.transparent_vector);
            if (icon == null) tab.sendPageCommand("getPage");
        }
    }

    // ---- 小さなファイル入出力 ----

    @Nullable
    private String readTextFile(File file) {
        if (!file.exists()) return null;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int off = 0;
            while (off < bytes.length) {
                int n = fis.read(bytes, off, bytes.length - off);
                if (n < 0) break;
                off += n;
            }
            return new String(bytes, 0, off, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** 一時ファイルに書いてから rename するので、書きかけのファイルが残らない。 */
    private void writeTextFile(File file, String text) {
        File tmp = new File(file.getParentFile(), file.getName() + "." + System.nanoTime() + ".tmp");
        try {
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(text.getBytes("UTF-8"));
                fos.flush();
            }
            if (!tmp.renameTo(file)) {
                file.delete();
                if (!tmp.renameTo(file)) tmp.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
            tmp.delete();
        }
    }



    private void savePage() {
        GeckoTabView webView = getCurrentTab();
        if (webView == null) return;
        String url = webView.getUrl();
        String title = webView.getTitle();
        String dateSaved = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        if (url == null || title == null) {
            Toast.makeText(this, Lang.t("ページを取得できません"), Toast.LENGTH_SHORT).show();
            return;
        }

        // スクリーンショットを撮る
        webView.capture(bitmap -> {
            if (bitmap == null) {
                Toast.makeText(this, Lang.t("スクリーンショットの保存に失敗しました"), Toast.LENGTH_SHORT).show();
                return;
            }
            // 保存フォルダ作成
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "PageScreenshots");
            if (!dir.exists()) dir.mkdirs();

            File imageFile = new File(dir, "screenshot_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, Lang.t("スクリーンショットの保存に失敗しました"), Toast.LENGTH_SHORT).show();
                return;
            }

            // CSS・画像を埋め込みスクリプトを除いた HTML を作ってもらう(オフラインでも同じ見た目で開ける)
            Toast.makeText(this, Lang.t("ページを保存しています..."), Toast.LENGTH_SHORT).show();
            webView.requestSnapshot(html -> {
                if (html == null) {
                    Toast.makeText(this, Lang.t("このページは保存できません"), Toast.LENGTH_SHORT).show();
                    imageFile.delete();
                    return;
                }
                final String content = html;

                File pageFile = new File(getFilesDir(), "page_" + System.currentTimeMillis() + ".html");
                backgroundExecutor.execute(() -> {
                    writeTextFile(pageFile, content);
                    runOnUiThread(() -> {
                        SQLiteDatabase db = dbH.getWritableDatabase();
                        ContentValues values = new ContentValues();
                        values.put("url", pageFile.getAbsolutePath());
                        values.put("title", title);
                        values.put("screenshot_path", imageFile.getAbsolutePath());
                        values.put("date_saved", dateSaved);
                        db.insert("pages", null, values);
                        Toast.makeText(this, Lang.t("ページを保存しました"), Toast.LENGTH_SHORT).show();
                    });
                });
            });
        });
    }

    /**
     * 総合メニューの「ブックマークに追加/から削除」。追加はその場で保存し(前回の保存先へ)、
     * 下に「「ブックマーク」に保存しました >」の帯を出す。帯を押すと保存先や名前を変えられる。
     */
    private void saveBookmark() {
        GeckoTabView webView = getCurrentTab();
        if (webView == null) return;
        String url = webView.getUrl();
        if (url == null || url.isEmpty() || isInternalUrl(url)) {
            Toast.makeText(this, Lang.t("このページは追加できません"), Toast.LENGTH_SHORT).show();
            return;
        }
        BookmarkStore store = BookmarkStore.get(this);
        BookmarkStore.Node existing = store.findBookmark(url);
        if (existing != null) {
            store.delete(existing.id);
            Toast.makeText(this, Lang.t("ブックマークを削除しました"), Toast.LENGTH_SHORT).show();
            return;
        }
        long folder = store.lastFolder();
        long id = store.addBookmark(folder, pageTitleFor(webView, url), url, null);
        captureBookmarkThumb(webView, id, thumb -> showBookmarkSavedBar(id,
                Lang.t("「") + BookmarkViews.folderName(this, folder) + Lang.t("」に保存しました"), Lang.t("タップして編集"), thumb,
                () -> {
                    startActivity(new Intent(this, BookmarkEditActivity.class)
                            .putExtra(BookmarkEditActivity.EXTRA_ID, id));
                    overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
                }));
    }

    /** 総合メニューの「リーディングリストに追加」。 */
    private void addToReadingList() {
        GeckoTabView webView = getCurrentTab();
        if (webView == null) return;
        String url = webView.getUrl();
        if (url == null || url.isEmpty() || isInternalUrl(url)) {
            Toast.makeText(this, Lang.t("このページは追加できません"), Toast.LENGTH_SHORT).show();
            return;
        }
        BookmarkStore store = BookmarkStore.get(this);
        if (store.findReading(url) != null) {
            Toast.makeText(this, Lang.t("すでにリーディングリストにあります"), Toast.LENGTH_SHORT).show();
            return;
        }
        long id = store.addBookmark(BookmarkStore.ROOT_READING, pageTitleFor(webView, url), url, null);
        captureBookmarkThumb(webView, id, thumb -> showBookmarkSavedBar(id,
                Lang.t("リーディングリストに追加しました"), Lang.t("タップしてリーディングリストを見る"), thumb,
                () -> {
                    startActivity(new Intent(this, BookmarksActivity.class)
                            .putExtra(BookmarksActivity.EXTRA_SECRET, isSecretMode())
                            .putExtra(BookmarksActivity.EXTRA_FOLDER, BookmarkStore.ROOT_READING));
                    overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
                }));
    }

    private String pageTitleFor(@NonNull GeckoTabView tab, @NonNull String url) {
        String title = tab.getTitle();
        if (title != null && !title.trim().isEmpty()) return title.trim();
        String host = hostOf(url);
        return host != null ? host : url;
    }

    private interface ThumbCallback {
        void onThumb(@Nullable Bitmap thumb);
    }

    /**
     * ブックマークのタイル用に、ページの上の方を正方形に切り出した小さな画像を保存する。
     * シークレットでは画像を残さない。
     */
    private void captureBookmarkThumb(@NonNull GeckoTabView tab, long id, @NonNull ThumbCallback done) {
        if (isSecretMode()) {
            done.onThumb(null);
            return;
        }
        tab.capture(captured -> {
            if (captured == null || isFinishing()) {
                done.onThumb(null);
                return;
            }
            int side = Math.min(captured.getWidth(), captured.getHeight());
            Bitmap square = Bitmap.createBitmap(captured, (captured.getWidth() - side) / 2, 0, side, side);
            Bitmap thumb = Bitmap.createScaledBitmap(square, 240, 240, true);
            File dir = new File(getFilesDir(), "bookmark_thumbs");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "bm_" + id + "_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                thumb.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                BookmarkStore.get(this).setThumb(id, file.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
            }
            done.onThumb(thumb);
        });
    }

    /** 保存した直後の帯(ページの画像・メッセージ・押すと次の画面)。下へスワイプで消せる。 */
    private void showBookmarkSavedBar(long id, @NonNull String text, @NonNull String hint,
                                      @Nullable Bitmap thumb, @NonNull Runnable onClick) {
        if (isFinishing() || snackbarHost == null) return;
        com.google.android.material.snackbar.Snackbar bar = com.google.android.material.snackbar.Snackbar
                .make(snackbarHost, "", 5000);
        placeSnackbar(bar);
        bar.setBehavior(new com.google.android.material.snackbar.BaseTransientBottomBar.Behavior() {
            @Override
            public boolean canSwipeDismissView(@NonNull View child) {
                return false;
            }
        });
        bar.setBackgroundTint(Color.TRANSPARENT);
        ViewGroup layout = (ViewGroup) bar.getView();
        layout.setPadding(0, 0, 0, 0);
        layout.setElevation(0f);
        for (int i = 0; i < layout.getChildCount(); i++) layout.getChildAt(i).setVisibility(View.GONE);
        View content = LayoutInflater.from(layout.getContext()).inflate(R.layout.view_bookmark_saved, layout, false);
        ((TextView) content.findViewById(R.id.bsText)).setText(text);
        ((TextView) content.findViewById(R.id.bsHint)).setText(hint);
        BookmarkStore.Node node = BookmarkStore.get(this).node(id);
        ImageView thumbView = content.findViewById(R.id.bmThumb);
        if (node != null) {
            BookmarkViews.bindPageTile(content.findViewById(R.id.bmTile), thumbView,
                    content.findViewById(R.id.bmLetter), node);
        }
        if (thumb != null) {
            thumbView.setImageBitmap(thumb);
            thumbView.setVisibility(View.VISIBLE);
            content.findViewById(R.id.bmLetter).setVisibility(View.GONE);
        }
        content.setOnClickListener(v -> {
            bar.dismiss();
            onClick.run();
        });
        layout.addView(content);
        // 画像のタイルを、帯が出た後に少し弾ませる
        View tile = content.findViewById(R.id.bmTile);
        tile.setScaleX(0.6f);
        tile.setScaleY(0.6f);
        tile.setRotation(-8f);
        tile.animate().scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(180).setDuration(420)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
        installTapOrSwipeDown(content, bar);
        updateSnackbarHost();
        bar.show();
    }

    /** 帯の中身: 押したら開き、下へスライドしたら消す。 */
    @SuppressLint("ClickableViewAccessibility")
    private void installTapOrSwipeDown(View content, com.google.android.material.snackbar.Snackbar bar) {
        View v = bar.getView();
        // 上に出している時(タブレット UI)は、上へスライドして消す
        float sign = snackbarOnTop() ? -1f : 1f;
        content.setOnTouchListener(new View.OnTouchListener() {
            float downY;
            boolean dragging;

            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = e.getRawY();
                        dragging = false;
                        view.setPressed(true);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dy = (e.getRawY() - downY) * sign;
                        if (!dragging && dy > ViewConfiguration.get(view.getContext()).getScaledTouchSlop()) {
                            dragging = true;
                            view.setPressed(false);
                        }
                        if (dragging) {
                            v.setTranslationY(Math.max(0, dy) * sign);
                            v.setAlpha(1f - Math.min(1f, Math.max(0, dy) / (v.getHeight() * 1.5f)));
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        view.setPressed(false);
                        if (!dragging) {
                            view.performClick();
                        } else if (v.getTranslationY() * sign > v.getHeight() / 3f) {
                            v.animate().translationY(v.getHeight() * 1.5f * sign).alpha(0f).setDuration(150)
                                    .withEndAction(bar::dismiss).start();
                        } else {
                            v.animate().translationY(0).alpha(1f).setDuration(150).start();
                        }
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        view.setPressed(false);
                        v.animate().translationY(0).alpha(1f).setDuration(150).start();
                        dragging = false;
                        return true;
                }
                return false;
            }
        });
    }


    private class DownloadFaviconTask extends AsyncTask<String, Void, Bitmap> {
        @Nullable private final TabInfo target;
        private final boolean updateBar;

        DownloadFaviconTask(@Nullable TabInfo target, boolean updateBar) {
            this.target = target;
            this.updateBar = updateBar;
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            String url = urls[0];
            try {
                InputStream in = new java.net.URL(url).openStream();
                return BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (target != null && result != null) {
                target.setIcon(result);
                if (tabSwitcher != null) tabSwitcher.refresh();
                refreshTabStrip();
            }
            if (!updateBar) return;
            if (result != null) {
                faviconImageView.setImageBitmap(result);
            } else {
                faviconImageView.setImageResource(R.drawable.transparent_vector);
            }
        }
    }

    private void updateUrlBar(GeckoTabView webView) {
        GeckoTabView webUrl = tabs.get(currentTabIndex);
        String currentUrl = webUrl.getUrl();
        ImageButton bmbutton = findViewById(R.id.action_bookmark);

        // 指定URLの場合は空白を表示
        if (isInternalUrl(currentUrl)) {
            urlEditText.setText(""); // URLを非表示
            bmbutton.setVisibility(tabletUi ? View.VISIBLE : View.GONE);
        } else {
            urlEditText.setText(currentUrl); // 通常のURLを表示
            bmbutton.setVisibility(View.VISIBLE);
        }
        if (tabletUi) {
            // タブレット UI では並びがずれないよう、隠さずに薄くする。
            // ただしスタートページでは使える項目が無いので、ページメニューのボタンごと隠す(いちばん右なので並びはずれない)
            boolean page = !isInternalUrl(currentUrl);
            bmbutton.setVisibility(currentUrl != null && isHomeUrl(currentUrl) ? View.GONE : View.VISIBLE);
            bmbutton.setEnabled(page);
            bmbutton.setAlpha(page ? 1f : 0.38f);
        }
        updateToolbarShortcut();
    }




    private GeckoTabView createTab(int id) {
        return createTab(id, null);
    }

    /**
     * タブ 1 枚分の GeckoTabView を作る。
     * @param existing window.open などで Gecko から渡されたセッション。null なら新規。
     */
    private GeckoTabView createTab(int id, @Nullable GeckoSession existing) {
        GeckoTabView tab = new GeckoTabView(this, isSecretMode(), sessionContextId(), existing);
        tab.setTag(id);
        tab.getGeckoSession().setPromptDelegate(promptDelegate);
        tab.getGeckoSession().setPermissionDelegate(permissionDelegate);
        applyTabSettings(tab);
        tab.setListener(tabListener);
        // 拡張機能のボタン(バッジ)やタブ操作の対象にする
        ExtensionManager.get(this).attachSession(tab.getGeckoSession());
        return tab;
    }









    /** 長押しメニュー「画像を保存」。 */
    private void saveImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;
        runWithStoragePermission(() -> {
            if (imageUrl.startsWith("data:")) {
                AppDownloader.saveDataUrlImage(this, imageUrl);
                return;
            }
            GeckoTabView current = getCurrentTab();
            String ua = current != null ? current.getSettings().getUserAgentString() : null;
            if (!AppDownloader.downloadUrl(this, imageUrl, ua, true, isSecretMode()) && current != null) {
                current.loadUrl(imageUrl);
            }
        });
    }

    private void updateNavigationButtons() {
        GeckoTabView currentWebView = getCurrentTab();
        if (currentWebView == null) return;
        // 🔹 戻るボタンの有効/無効を設定
        if (currentWebView.canGoBack()) {
            backView.setVisibility(View.GONE);
            backButton.setVisibility(View.VISIBLE);
        } else {
            backButton.setVisibility(View.GONE);
            backView.setVisibility(View.VISIBLE);
        }
        // 🔹 進むボタンの有効/無効を設定
        if (currentWebView.canGoForward()) {
            forwardView.setVisibility(View.GONE);
            forwardButton.setVisibility(View.VISIBLE);
        } else {
            forwardButton.setVisibility(View.GONE);
            forwardView.setVisibility(View.VISIBLE);
        }
    }

    private void goBack() {
        GeckoTabView currentWebView = getCurrentTab();
        if (currentWebView == null) return;
        if (currentWebView.canGoBack()) {
            nohideurl=true;
            currentWebView.goBack();
            new android.os.Handler().postDelayed(this::updateNavigationButtons, 300); // 300ms遅延
        }
    }

    private void goForward() {
        GeckoTabView currentWebView = getCurrentTab();
        if (currentWebView == null) return;
        if (currentWebView.canGoForward()) {
            nohideurl=true;
            currentWebView.goForward();
            new android.os.Handler().postDelayed(this::updateNavigationButtons, 300);
        }
    }

    /** その URL がブックマーク済みか(総合メニューの「ブックマークに追加/から削除」の表示用)。 */
    private boolean isBookmarked(@Nullable String url) {
        return BookmarkStore.get(this).findBookmark(url) != null;
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;

        GeckoTabView currentWebView = loadTabIfNeeded(index);
        if (currentWebView == null) return;

        // 他のタブは非表示にして、Gecko 側でも非アクティブにする(描画・タイマーを止めて省電力)
        for (GeckoTabView tab : tabs) {
            if (tab != null && tab != currentWebView) {
                tab.exitPointerLock();
                tab.setVisibility(View.GONE);
                tab.moveToBackground();
            }
        }

        currentWebView.setVisibility(View.VISIBLE);
        currentWebView.onResume();
        currentWebView.requestLayout();
        currentWebView.invalidate();

        currentTabIndex = index;
        webView = currentWebView;
        touchTabLru(index);
        tabLastUsed.put(tabIds.get(index), System.currentTimeMillis());
        // 拡張機能から見た「今のタブ」(tabs.query({active: true}))を合わせる
        ExtensionManager extensions = ExtensionManager.get(this);
        for (GeckoTabView tab : tabs) {
            if (tab != null && !tab.isDestroyed()) {
                extensions.setTabActive(tab.getGeckoSession(), tab == currentWebView);
            }
        }
        dynamicToolbar.attachTab(currentWebView);

        String url = currentWebView.getUrl();
        if (url == null) url = "";


        requestFavicon(currentWebView);

        updateUrlBar(currentWebView);
        updateNavigationButtons();

        String title = currentWebView.getTitle();
        if (index < tabInfos.size() && Lang.t("読込中...").equals(tabInfos.get(index).getTitle())
                && title != null && !title.isEmpty()) {
            tabInfos.get(index).setTitle(title);
        }

        showUrlBar();

        // 読み込み状態をこのタブに合わせる
        isLoading = currentWebView.isLoading();
        reloadButton.setImageResource(isLoading ? R.drawable.ic_close : R.drawable.reload);
        pageloading.setVisibility(isLoading ? View.VISIBLE : View.INVISIBLE);
        progressBar.setProgress(isLoading ? currentWebView.getProgress() : 0);

        if (isHomeUrl(url)) {
            sitename.setText(homeSiteName());
        } else {
            if (index < tabInfos.size() && (title == null || title.isEmpty())) {
                title = tabInfos.get(index).getTitle();
            }
            sitename.setText(title != null ? title : "");
        }
        nohideurl = isInternalUrl(url);
        updateStartPage();
        updateSwipeRefreshForUrl(url);
        refreshTabStrip();

        saveTabsState();
    }



    private void closeTab(int index) {
        closeTab(index, false);
    }

    /**
     * @param undoable 「〇〇を閉じました  元に戻す」を 10 秒間出す(ユーザーが閉じた場合)。
     *                 ページの window.close() などでは出さない。
     */
    private void closeTab(int index, boolean undoable) {
        if (index < 0 || index >= tabs.size()) return;

        GeckoTabView webView = tabs.get(index);
        int closedId = tabIds.get(index);
        TabInfo closedInfo = index < tabInfos.size() ? tabInfos.get(index) : null;

        ClosedTab closed = null;
        if (undoable) {
            closed = new ClosedTab();
            closed.index = index;
            closed.id = closedId;
            closed.wasCurrent = index == currentTabIndex;
            if (webView != null) {
                closed.url = webView.getUrl();
                closed.title = webView.getTitle();
                closed.state = webView.saveState();
                closed.snapshot = tabSnapshots.get(webView);
            } else if (!isSecretMode()) {
                // 破棄済みのタブは保存済みの状態から戻す
                closed.state = readTextFile(new File(getFilesDir(), "tab_state_" + closedId + ".json"));
            }
            if (closedInfo != null) {
                if (closed.url == null || closed.url.isEmpty() || closed.url.startsWith("data:")) {
                    closed.url = closedInfo.getUrl();
                }
                if (closed.title == null || closed.title.isEmpty()) closed.title = closedInfo.getTitle();
            }
        }

        tabs.remove(index);
        tabIds.remove(index);
        tabLastUsed.remove(closedId);
        if (index < tabInfos.size()) tabInfos.remove(index);
        if (pageReadyNotifier != null) pageReadyNotifier.onTabClosed(closedId);
        Integer closedGroup = tabGroupOf.remove(closedId);
        if (closedGroup != null) {
            TabGroup emptied = removeGroupIfEmpty(closedGroup);
            if (closed != null) {
                closed.groupId = closedGroup;
                closed.removedGroup = emptied;
            }
        }

        if (webView != null) {
            tabSnapshots.remove(webView);
            detachTabViews(() -> webViewContainer.removeView(webView));
            android.view.animation.Animation fadeOut =
                    android.view.animation.AnimationUtils.loadAnimation(this, R.anim.tab_out);
            webView.startAnimation(fadeOut);
            webView.stopLoading();
            webView.onPause();
            webView.destroy();
            originalUserAgents.remove(webView);
            webViewFavicons.remove(webView);
        }

        if (closed != null) {
            // 元に戻せる間は保存ファイルを残しておく(前に閉じたタブはここで確定する)
            finalizeClosedTab();
            lastClosedTab = closed;
        } else {
            deleteTabFiles(closedId);
        }

        tabLru.remove((Integer) index);
        for (int i = 0; i < tabLru.size(); i++) {
            int v = tabLru.get(i);
            if (v > index) tabLru.set(i, v - 1);
        }

        if (tabs.isEmpty()) {
            currentTabIndex = -1;
            if (closed != null) closed.replacedByNewTab = true;
            newStartPage();
        } else {
            if (currentTabIndex > index) {
                currentTabIndex--;
            } else if (currentTabIndex >= tabs.size()) {
                currentTabIndex = tabs.size() - 1;
            }
        }

        if (!tabs.isEmpty()) {
            switchToTab(currentTabIndex);
        }
        updateTabCount();
        if (closed != null) showUndoCloseTab(closed);
    }

    /**
     * タブのビューを画面から外す。フォーカスを持つページを外すと、Android が次のフォーカス先として
     * アドレスバーを選び、閉じたタブの URL で入力画面(候補)が開いてしまうので、その間は入力画面を出さず、
     * 外したあとにアドレスバーに移ったフォーカスを戻す。
     */
    private void detachTabViews(@NonNull Runnable removal) {
        boolean urlBarHadFocus = urlEditText.hasFocus();
        suppressUrlBarFocus = true;
        try {
            removal.run();
        } finally {
            suppressUrlBarFocus = false;
        }
        if (!urlBarHadFocus && urlEditText.hasFocus()) {
            clearUrlBarFocus();
            closeKeyboard();
        }
    }

    /** 「〇〇を閉じました  元に戻す」と同じ見た目・動きの帯(翻訳の取り消しなど)。 */
    private boolean showUndoBar(@NonNull String text, @NonNull Runnable undo) {
        if (isFinishing() || snackbarHost == null) return false;
        com.google.android.material.snackbar.Snackbar bar = com.google.android.material.snackbar.Snackbar
                .make(snackbarHost, text, UNDO_CLOSE_TAB_MS)
                .setAction(Lang.t("元に戻す"), v -> undo.run());
        placeSnackbar(bar);
        bar.setBehavior(new com.google.android.material.snackbar.BaseTransientBottomBar.Behavior() {
            @Override
            public boolean canSwipeDismissView(@NonNull View child) {
                return false;
            }
        });
        installSwipeDownToDismiss(bar);
        updateSnackbarHost();
        bar.show();
        return true;
    }

    /** 「〇〇を閉じました  元に戻す」(Chrome と同じく 10 秒間)。 */
    private void showUndoCloseTab(@NonNull ClosedTab closed) {
        String name = closed.title;
        if (name == null || name.isEmpty() || Lang.t("読込中...").equals(name)) {
            name = isHomeUrl(closed.url) ? homeSiteName() : closed.url;
        }
        if (name == null || name.isEmpty()) name = Lang.t("タブ");
        if (name.length() > 16) name = name.substring(0, 16) + "...";

        // ボトムバーの上に置いた専用の置き場(CoordinatorLayout)に出す。置き場はボトムバーの
        // 出し入れ・タブ一覧の表示に合わせて updateSnackbarHost() で上下する
        com.google.android.material.snackbar.Snackbar bar = com.google.android.material.snackbar.Snackbar
                .make(snackbarHost, name + Lang.t(" を閉じました"), UNDO_CLOSE_TAB_MS)
                .setAction(Lang.t("元に戻す"), v -> undoCloseTab(closed));
        placeSnackbar(bar);
        // 横スワイプではなく、指で下へスライドして消す
        bar.setBehavior(new com.google.android.material.snackbar.BaseTransientBottomBar.Behavior() {
            @Override
            public boolean canSwipeDismissView(@NonNull View child) {
                return false;
            }
        });
        installSwipeDownToDismiss(bar);
        bar.addCallback(new com.google.android.material.snackbar.BaseTransientBottomBar
                .BaseCallback<com.google.android.material.snackbar.Snackbar>() {
            @Override
            public void onDismissed(com.google.android.material.snackbar.Snackbar sb, int event) {
                if (event != DISMISS_EVENT_ACTION && lastClosedTab == closed) finalizeClosedTab();
            }
        });
        updateSnackbarHost();
        bar.show();
    }

    /** タブレット UI でバーを上に出している時は、メッセージ(スナックバー)も上に出す。 */
    private boolean snackbarOnTop() {
        return tabletUi && !tabletBarsBottom && !isDualScreenActive();
    }

    /** スナックバーを上に出す時は、置き場の上端(ツールバーのすぐ下)に寄せ、ふわっと出す。 */
    private void placeSnackbar(@NonNull com.google.android.material.snackbar.Snackbar bar) {
        if (!snackbarOnTop()) return;
        View v = bar.getView();
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) {
            ((androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) lp).gravity =
                    android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
            v.setLayoutParams(lp);
        }
        bar.setAnimationMode(com.google.android.material.snackbar.BaseTransientBottomBar.ANIMATION_MODE_FADE);
    }

    /** スナックバーを指で下へスライドして消せるようにする。 */
    @SuppressLint("ClickableViewAccessibility")
    private void installSwipeDownToDismiss(com.google.android.material.snackbar.Snackbar bar) {
        View v = bar.getView();
        // 上に出している時(タブレット UI)は、上へスライドして消す
        float sign = snackbarOnTop() ? -1f : 1f;
        v.setOnTouchListener(new View.OnTouchListener() {
            float downY;
            boolean dragging;

            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = e.getRawY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dy = (e.getRawY() - downY) * sign;
                        if (!dragging && dy > ViewConfiguration.get(view.getContext()).getScaledTouchSlop()) {
                            dragging = true;
                        }
                        if (dragging) {
                            view.setTranslationY(Math.max(0, dy) * sign);
                            view.setAlpha(1f - Math.min(1f, Math.max(0, dy) / (view.getHeight() * 1.5f)));
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dragging && view.getTranslationY() * sign > view.getHeight() / 3f) {
                            view.animate().translationY(view.getHeight() * 1.5f * sign).alpha(0f).setDuration(150)
                                    .withEndAction(bar::dismiss).start();
                        } else {
                            view.animate().translationY(0).alpha(1f).setDuration(150).start();
                        }
                        dragging = false;
                        return true;
                }
                return false;
            }
        });
    }

    /**
     * スナックバーの置き場をボトムバーのすぐ上へ動かす。ボトムバーが隠れている時・
     * タブ一覧を出している時は画面の下端。描画のたびに呼ばれるので、変わった時だけ動かす。
     */
    private void updateSnackbarHost() {
        if (snackbarHost == null) return;
        float lift = 0;
        boolean switcher = tabSwitcher != null && tabSwitcher.isShowing();
        if (tabletUi && !isDualScreenActive()) {
            if (!tabletBarsBottom) {
                // バーが上: メッセージはツールバーのすぐ下(隠れている時・タブ一覧の時は画面の上端)に出す
                float drop = switcher ? 0 : Math.max(0, action_Bar.getBottom() + action_Bar.getTranslationY());
                if (snackbarHost.getTranslationY() != drop) snackbarHost.setTranslationY(drop);
                return;
            }
            if (!switcher) lift = Math.max(0, action_Bar.getHeight() - action_Bar.getTranslationY());
            if (snackbarHost.getTranslationY() != -lift) snackbarHost.setTranslationY(-lift);
            return;
        }
        // 二画面ではボトムバーは下の画面にあるので持ち上げない
        if (!switcher && !isDualScreenActive() && bottomBar.getVisibility() == View.VISIBLE) {
            lift = Math.max(0, bottomBar.getHeight() - bottomBar.getTranslationY());
        }
        if (snackbarHost.getTranslationY() != -lift) snackbarHost.setTranslationY(-lift);
    }

    /** 元に戻せなくなったタブの保存ファイルを消す。 */
    private void finalizeClosedTab() {
        ClosedTab c = lastClosedTab;
        lastClosedTab = null;
        if (c != null && !tabIds.contains(c.id)) deleteTabFiles(c.id);
    }

    /** 閉じたタブを元の位置に戻す(戻る/進む履歴も含めて)。 */
    private void undoCloseTab(@NonNull ClosedTab c) {
        if (lastClosedTab != c || isFinishing()) return;
        lastClosedTab = null;

        int index = Math.max(0, Math.min(c.index, tabs.size()));
        GeckoTabView tab = createTab(c.id);
        if (!tab.restoreState(c.state)) {
            openUrlInTab(tab, c.url != null && !c.url.isEmpty() ? c.url : homeUrl());
        }
        tabs.add(index, tab);
        tabIds.add(index, c.id);
        tabLastUsed.put(c.id, System.currentTimeMillis());
        tabInfos.add(Math.min(index, tabInfos.size()), new TabInfo(
                c.title != null && !c.title.isEmpty() ? c.title : Lang.t("読込中..."), c.url, null));
        if (c.snapshot != null) tabSnapshots.put(tab, c.snapshot);
        for (int i = 0; i < tabLru.size(); i++) {
            int v = tabLru.get(i);
            if (v >= index) tabLru.set(i, v + 1);
        }
        tabLru.addLast(index);
        if (currentTabIndex >= index) currentTabIndex++;

        // グループに入っていたタブはグループに戻す(閉じて空になったグループも戻す)
        if (c.groupId >= 0 && tabGroups != null) {
            if (c.removedGroup != null) tabGroups.restore(c.removedGroup);
            TabGroup g = tabGroups.get(c.groupId);
            if (g != null && !g.closed) {
                tabGroupOf.put(c.id, c.groupId);
                g.touch();
            }
            tabGroups.save();
        }

        webViewContainer.addView(tab);
        tab.setVisibility(View.GONE);

        // 最後のタブを閉じた時に代わりに開いた空のタブは、戻したら閉じる
        if (c.replacedByNewTab && tabs.size() == 2) {
            int other = index == 0 ? 1 : 0;
            GeckoTabView auto = tabs.get(other);
            if (auto != null && isHomeUrl(auto.getUrl()) && !auto.canGoBack()) {
                if (c.wasCurrent) currentTabIndex = index;
                closeTab(other, false);
                index = tabs.indexOf(tab);
            }
        }

        if (c.wasCurrent) {
            switchToTab(index);
        }
        updateTabCount();
        saveTabsState();
        if (tabSwitcher != null) tabSwitcher.refresh();
    }



    private String getFaviconFilename(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString() + ".png";
        } catch (Exception e) {
            return Integer.toString(url.hashCode()) + ".png";
        }
    }

    private void loadFaviconFromDisk(String url) {
        File faviconsDir = new File(getFilesDir(), "favicons");
        File file = new File(faviconsDir, getFaviconFilename(url));
        if (file.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                faviconCache.put(url, bitmap);
            }
        }
    }

    private void initializePersistentFavicons() {
        for (Bookmark bm : bookmarks) {
            final String url = bm.getUrl();
            backgroundExecutor.execute(() -> loadFaviconFromDisk(url));
        }
        for (HistoryItem hi : historyItems) {
            final String url = hi.getUrl();
            backgroundExecutor.execute(() -> loadFaviconFromDisk(url));
        }
    }

    private void loadHistory() {
        String json = prefs.getString(KEY_HISTORY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            historyItems.clear();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                historyItems.add(new HistoryItem(obj.getString("title"), obj.getString("url"),
                        obj.optLong("time", 0L)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // 履歴の保存（JSON 配列として SharedPreferences に保存）
    private void saveHistory() {
        JSONArray arr = new JSONArray();
        for (HistoryItem item : historyItems) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("title", item.getTitle());
                obj.put("url", item.getUrl());
                if (item.getTime() > 0) obj.put("time", item.getTime());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            arr.put(obj);
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    private void showHistoryDialog() {
        Intent intent = new Intent(this, PageHistoryActivity.class);
        intent.putExtra("history_list", new ArrayList<>(historyItems));
        startActivityForResult(intent, REQUEST_HISTORY);
        overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
    }



    private void loadBookmarks() {
        String json = prefs.getString(KEY_BOOKMARKS, "[]");
        try {
            JSONArray array = new JSONArray(json);
            bookmarks.clear();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                bookmarks.add(new Bookmark(obj.getString("title"), obj.getString("url")));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void saveBookmarks() {
        JSONArray array = new JSONArray();
        for (Bookmark bm : bookmarks) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("title", bm.getTitle());
                obj.put("url", bm.getUrl());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            array.put(obj);
        }
        prefs.edit().putString(KEY_BOOKMARKS, array.toString()).apply();
    }





    private void reloadBrowserSettings() {
        SharedPreferences setupprefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        darkModeEnabled = pref.getBoolean(KEY_DARK_MODE, false);
        basicAuthEnabled = pref.getBoolean(KEY_BASIC_AUTH, false);
        zoomEnabled = pref.getBoolean(KEY_ZOOM_ENABLED, false);
        jsEnabled = pref.getBoolean(KEY_JS_ENABLED, false);
        imgBlockEnabled = pref.getBoolean(KEY_IMG_BLOCK_ENABLED, false);
        uaEnabled = pref.getBoolean(KEY_UA_ENABLED, false);
        deskuaEnabled = pref.getBoolean(KEY_DESKUA_ENABLED, false);
        ct3uaEnabled = pref.getBoolean(KEY_CT3UA_ENABLED, false);

        // 設定の「閲覧履歴データを削除」で消した履歴を読み直す
        loadHistory();
        updateToolbarShortcut();
        BrowserPrefs.apply(this);
        // 全タブ共通の設定(Cookie・JavaScript・配色・画像)は Gecko ランタイムに反映
        geckoEngine.applyCookieSetting();
        applyPageColorScheme();
        geckoEngine.setJavaScriptEnabled(setupprefs.getBoolean("javaScript", true));
        geckoEngine.setBlockImages(imgBlockEnabled);

        // タブごとの設定(UA・デスクトップ表示など)
        for (GeckoTabView tab : tabs) {
            if (tab != null) applyTabSettings(tab);
        }

        reloadCurrentPage();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissionDelegate != null && permissionDelegate.onRequestPermissionsResult(requestCode, grantResults)) {
            return;
        }
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            Runnable action = pendingStorageAction;
            pendingStorageAction = null;
            if (action == null) return;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                action.run();
            } else {
                Toast.makeText(this, Lang.t("ストレージへの保存が許可されなかったため、保存できません"), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_UNLOCK) {
            lockShowing = false;
            if (resultCode != RESULT_OK) finishAffinity();
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (requestCode == REQUEST_START_WALLPAPER) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && startPageView != null) {
                startPageView.setWallpaper(data.getData());
            }
            return;
        }
        if (requestCode == REQUEST_HISTORY && resultCode == RESULT_OK && data != null) {
            // 履歴画面で消した分を先に反映する(ページを選んで戻ってきた時も)
            ArrayList<HistoryItem> edited =
                    (ArrayList<HistoryItem>) data.getSerializableExtra("history_list");
            if (edited != null) {
                historyItems.clear();
                historyItems.addAll(edited);
                currentHistoryIndex = historyItems.size() - 1;
                saveHistory();
            }
            // URLが返ってきた場合 → そのままWebViewで開く
            String url = data.getStringExtra("selected_url");
            if (url != null) {
                tabSwitcher.hideNow();
                getCurrentTab().loadUrl(url);
                return; // URLが返ってきたらそれを優先
            }

            String addnewtaburl = data.getStringExtra("historyAddNewTab");
            if (addnewtaburl != null) {
                addNewTab(addnewtaburl);
                return; // URLが返ってきたらそれを優先
            }

        }
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            String action = data.getStringExtra("action");
            if ("contact".equals(action)) {
                // お問い合わせはアプリ内の画面で送る(以前は Google フォームをタブで開いていた)
                startActivity(ContactActivity.intent(this));
                overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
            } else if ("recreate".equals(action)) {
                reloadBrowserSettings();
            } else if ("negapoji".equals(action)) {
                new Handler().postDelayed(() -> {
                    applyNegapoji();
                }, 500);
            } else if ("translate".equals(action)) {
                new Handler().postDelayed(() -> {
                    translatePageToJapanese();
                }, 500);
            } else if ("screenshot".equals(action)) {
                new Handler().postDelayed(() -> {
                    takeScreenshot();
                }, 500);
            } else if ("settings".equals(action)) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivityForResult(intent, 1001);
            }
        }
        if (promptDelegate != null && promptDelegate.onActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    private void showEditBookmarkDialog(final int position, final BookmarkAdapter adapter) {
        Bookmark bm = bookmarks.get(position);

        // レイアウトのインフレート
        ViewGroup parent = findViewById(android.R.id.content);
        View editView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_bookmark, parent, false);

        EditText etTitle = editView.findViewById(R.id.editTitle);
        EditText etUrl = editView.findViewById(R.id.editUrl);

        etTitle.setText(bm.getTitle());
        etUrl.setText(bm.getUrl());

        // Holo スタイルの AlertDialog を使用
        AlertDialog.Builder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, android.R.style.Theme_Holo_Dialog);

        builder.setTitle(Lang.t("ブックマーク"))
                .setView(editView)
                .setPositiveButton(Lang.t("保存"), (dialog, which) -> {
                    String newTitle = etTitle.getText().toString().trim();
                    String newUrl = etUrl.getText().toString().trim();

                    // URL のバリデーション
                    if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                        newUrl = "http://" + newUrl;
                    }

                    // ブックマーク更新
                    bookmarks.set(position, new Bookmark(newTitle, newUrl));
                    saveBookmarks();
                    adapter.notifyDataSetChanged();

                    Toast.makeText(MainActivity.this, Lang.t("保存しました"), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(Lang.t("キャンセル"), (dialog, which) -> dialog.dismiss()) // 明示的に dismiss()
                .show();
    }

    private void captureTabSnapshot(GeckoTabView webView) {
        captureTabSnapshot(webView, null);
    }

    /**
     * タブ一覧用のサムネイルを撮る。GeckoView の描画は別サーフェスなので、
     * ウィンドウのコピーではなく Gecko からページの画像を受け取る。
     * @param then 撮影後(失敗時も)に実行する処理
     */
    private void captureTabSnapshot(GeckoTabView webView, @Nullable Runnable then) {
        if (webView == null) {
            if (then != null) then.run();
            return;
        }
        Object tag = webView.getTag();
        final int id = (tag instanceof Integer) ? (Integer) tag : -1;
        if (isShowingStartPage(webView)) {
            // Gecko 側は空のページなので、重ねているネイティブのスタートページを撮る
            startPageView.capture(getWindow(), bmp -> {
                if (bmp != null) {
                    startPageSnapshot = bmp;
                    tabSnapshots.put(webView, bmp);
                    if (!isSecretMode()) saveSnapshotFileAsync(id, bmp);
                }
                if (then != null) then.run();
            });
            return;
        }
        if (startPageView != null && isHomeUrl(webView.getUrl())) {
            if (startPageSnapshot != null) {
                tabSnapshots.put(webView, startPageSnapshot);
                if (!isSecretMode()) saveSnapshotFileAsync(id, startPageSnapshot);
            }
            if (then != null) then.run();
            return;
        }
        webView.capture(bmp -> {
            if (bmp != null) {
                tabSnapshots.put(webView, bmp);
                if (!isSecretMode()) saveSnapshotFileAsync(id, bmp);
            }
            if (then != null) then.run();
        });
    }

    /**
     * サムネイルを tab_snapshot_<id>.png として保存する(再起動後も残す)。
     * 一時ファイルに書いてから rename するので、読み込み側が書きかけを掴んだり、
     * 同じIDへの同時書き込みで壊れたりしない。
     */
    private void saveSnapshotFileAsync(final int id, final Bitmap bmp) {
        if (id < 0 || bmp == null) return;
        backgroundExecutor.execute(() -> {
            File tmp = new File(getFilesDir(),
                    "tab_snapshot_" + id + "." + System.nanoTime() + ".tmp");
            try {
                try (FileOutputStream fos = new FileOutputStream(tmp)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 80, fos);
                    fos.flush();
                }
                File dest = new File(getFilesDir(), "tab_snapshot_" + id + ".png");
                if (!tmp.renameTo(dest)) {
                    tmp.delete();
                }
            } catch (Exception e) {
                e.printStackTrace();
                tmp.delete();
            }
        });
    }

    private void deleteTabFiles(int id) {
        // シークレットのタブ ID は 0 から振られ、通常タブの ID と重なる。
        // シークレットは何も保存していないので、ここで消すと通常タブの履歴・サムネイルを消してしまう。
        if (isSecretMode()) return;
        new File(getFilesDir(), "tab_state_" + id + ".json").delete();
        // WebView 版の古い状態ファイル
        new File(getFilesDir(), "tab_state_" + id + ".dat").delete();
        new File(getFilesDir(), "tab_snapshot_" + id + ".png").delete();
    }

    /** 表示中のタブの文字選択と選択メニューを消す(タブ一覧の上にメニューが残らないように)。 */
    private boolean clearTextSelection() {
        GeckoTabView current = getCurrentTab();
        return current != null && current.clearTextSelection();
    }

    /** タブ一覧を開く(表示中のページがカードへ縮小するアニメーション付き)。 */
    private void showTabMenu() {
        if (findInPageBarView != null && findInPageBarView.getVisibility() == View.VISIBLE) {
            hideFindInPageBar();
        }
        closeKeyboard();
        clearUrlBarFocus();
        clearTextSelection();
        closeExpiredInactiveTabs();
        // 二画面では下の画面にメニューが残っていると、タブ一覧と同時に操作できてしまう
        if (aozoraMenuDialog != null && aozoraMenuDialog.isShowing()) aozoraMenuDialog.dismiss();
        if (pageMenuDialog != null && pageMenuDialog.isShowing()) pageMenuDialog.dismiss();
        tabSwitcher.show(true);
    }

    /**
     * アドレスバーの入力をやめて候補を閉じる。フォーカスが残っていると、タブ一覧でタブを閉じた時に
     * アドレスバーの文字が書き換わって候補がタブ一覧の上に出てしまう。
     */
    private void clearUrlBarFocus() {
        urlEditText.clearFocus();
        if (urlBarSuggestions != null) urlBarSuggestions.setShowing(false);
    }

    /** アニメーション無しでタブ一覧を出す。サムネイルは撮り直してから差し替える。 */
    private void showTabSwitcherNow() {
        if (tabSwitcher.isShowing()) {
            tabSwitcher.refresh();
            return;
        }
        closeKeyboard();
        clearUrlBarFocus();
        clearTextSelection();
        closeExpiredInactiveTabs();
        tabSwitcher.show(false);
        captureTabSnapshot(getCurrentTab(), () -> tabSwitcher.refresh());
    }

    /**
     * シークレットモードの画面へ。既にあれば作り直さずに前面へ戻す(シークレットタブを残すため)。
     * @param newTab 新しいシークレットタブを開く
     * @param showSwitcher シークレットのタブ一覧を表示する
     */
    private void openSecretMode(boolean newTab, boolean showSwitcher) {
        Intent intent = new Intent(this, SecretActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if (newTab) intent.putExtra(EXTRA_NEW_TAB, true);
        if (showSwitcher) intent.putExtra(EXTRA_SHOW_TAB_SWITCHER, true);
        startActivity(intent);
    }

    /** シークレットから通常の画面へ戻る(シークレットの画面は後ろに残す)。 */
    private void openNormalMode(boolean showSwitcher) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if (showSwitcher) intent.putExtra(EXTRA_SHOW_TAB_SWITCHER, true);
        startActivity(intent);
    }

    /**
     * シークレットのタブがすべて閉じられた: シークレットの画面を閉じ、通常のタブ一覧へ切り替える。
     * 一覧は表示したまま、通常の一覧が左から入りシークレットの一覧が右へ抜けるように見せる
     * (タブ一覧上部の切り替えボタンの並びと同じ向き)。
     */
    private void leaveSecretToNormalTabs() {
        openNormalMode(true);
        overridePendingTransition(R.anim.ts_mode_in_left, R.anim.ts_mode_out_right);
        finish();
    }

    /**
     * タブの並びを入れ替える(タブ一覧でのドラッグ・グループのタブをまとめる時)。
     * タブの実体・ID・情報を同じように動かし、index で覚えているもの(現在のタブ・LRU)を付け替える。
     * @param order 元の index を新しい並び順に並べたもの
     */
    private void applyTabOrder(@Nullable List<Integer> order) {
        int n = tabs.size();
        if (order == null || order.size() != n) return;
        int[] newIndex = new int[n];
        java.util.Arrays.fill(newIndex, -1);
        for (int k = 0; k < n; k++) {
            int old = order.get(k);
            if (old < 0 || old >= n || newIndex[old] >= 0) return; // 並べ替えになっていない
            newIndex[old] = k;
        }
        boolean infosAligned = tabInfos.size() == n;
        ArrayList<GeckoTabView> newTabs = new ArrayList<>(n);
        ArrayList<Integer> newIds = new ArrayList<>(n);
        ArrayList<TabInfo> newInfos = new ArrayList<>(n);
        for (int old : order) {
            newTabs.add(tabs.get(old));
            newIds.add(tabIds.get(old));
            if (infosAligned) newInfos.add(tabInfos.get(old));
        }
        tabs.clear();
        tabs.addAll(newTabs);
        tabIds.clear();
        tabIds.addAll(newIds);
        if (infosAligned) {
            tabInfos.clear();
            tabInfos.addAll(newInfos);
        }
        if (currentTabIndex >= 0 && currentTabIndex < n) currentTabIndex = newIndex[currentTabIndex];
        for (int i = 0; i < tabLru.size(); i++) {
            int v = tabLru.get(i);
            if (v >= 0 && v < n) tabLru.set(i, newIndex[v]);
        }
        scheduleSaveTabs();
    }

    // ---- タブグループ ----

    /** グループのタブの index(並び順)。 */
    private List<Integer> tabsInGroup(int groupId) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < tabIds.size(); i++) {
            Integer g = tabGroupOf.get(tabIds.get(i));
            if (g != null && g == groupId) list.add(i);
        }
        return list;
    }

    /** タブが無くなった(開いている)グループを消す。消したグループを返す。 */
    @Nullable
    private TabGroup removeGroupIfEmpty(int groupId) {
        if (tabGroups == null) return null;
        TabGroup g = tabGroups.get(groupId);
        if (g == null || g.closed) return null;
        if (!tabsInGroup(groupId).isEmpty()) {
            g.touch();
            return null;
        }
        tabGroups.remove(g);
        tabGroups.save();
        return g;
    }

    /** タブをまとめて新しいグループにする(グループのタブは最初のタブの位置に並べる)。 */
    private int createTabGroup(@NonNull List<Integer> indices) {
        if (tabGroups == null || indices.isEmpty()) return -1;
        TabGroup g = tabGroups.create();
        java.util.Set<Integer> previous = new java.util.HashSet<>();
        for (int i : indices) {
            if (i < 0 || i >= tabIds.size()) continue;
            Integer old = tabGroupOf.put(tabIds.get(i), g.id);
            if (old != null) previous.add(old);
        }
        gatherGroup(g.id);
        // 別のグループから移したタブで、元のグループが空になったら消す
        for (int old : previous) removeGroupIfEmpty(old);
        saveTabsState();
        tabGroups.save();
        return g.id;
    }

    /** グループのタブを、そのグループの最初のタブの位置に続けて並べる。 */
    private void gatherGroup(int groupId) {
        List<Integer> members = tabsInGroup(groupId);
        if (members.size() < 2) return;
        List<Integer> order = new ArrayList<>(tabs.size());
        boolean placed = false;
        for (int i = 0; i < tabs.size(); i++) {
            if (members.contains(i)) {
                if (!placed) {
                    order.addAll(members);
                    placed = true;
                }
            } else {
                order.add(i);
            }
        }
        applyTabOrder(order);
    }

    /**
     * タブをグループに移し、グループの今のタブの後ろに続けて並べる(タブ一覧でカードを重ねた時)。
     * 元のグループが空になれば消す。
     */
    private void moveTabsToGroup(@NonNull List<Integer> indices, int groupId) {
        if (tabGroups == null) return;
        TabGroup g = tabGroups.get(groupId);
        if (g == null || g.closed) return;
        List<Integer> movedIds = new ArrayList<>();
        java.util.Set<Integer> previous = new java.util.HashSet<>();
        for (int i : indices) {
            if (i < 0 || i >= tabIds.size()) continue;
            int id = tabIds.get(i);
            Integer old = tabGroupOf.put(id, groupId);
            if (old != null && old != groupId) previous.add(old);
            if (old == null || old != groupId) movedIds.add(id);
        }
        if (movedIds.isEmpty()) return;
        g.touch();
        // 元からのメンバー → 移したタブの順に、元からのメンバーの最初の位置へ並べる
        List<Integer> existing = new ArrayList<>();
        List<Integer> moved = new ArrayList<>();
        for (int i = 0; i < tabIds.size(); i++) {
            Integer gid = tabGroupOf.get(tabIds.get(i));
            if (gid == null || gid != groupId) continue;
            if (!movedIds.contains(tabIds.get(i))) existing.add(i);
        }
        for (int id : movedIds) moved.add(tabIds.indexOf(id));
        List<Integer> members = new ArrayList<>(existing);
        members.addAll(moved);
        int anchor = existing.isEmpty() ? moved.get(0) : existing.get(0);
        List<Integer> order = new ArrayList<>(tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            if (i == anchor) order.addAll(members);
            if (!members.contains(i)) order.add(i);
        }
        applyTabOrder(order);
        for (int old : previous) removeGroupIfEmpty(old);
        saveTabsState();
        tabGroups.save();
    }

    /** タブをグループから外し、グループのすぐ後ろに置く(グループのパネルから外へドラッグした時)。 */
    private void removeTabFromGroup(int index) {
        if (tabGroups == null || index < 0 || index >= tabIds.size()) return;
        int id = tabIds.get(index);
        Integer gid = tabGroupOf.get(id);
        if (gid == null) return;
        List<Integer> members = tabsInGroup(gid);
        tabGroupOf.remove(id);
        int last = members.isEmpty() ? index : members.get(members.size() - 1);
        if (last != index) {
            List<Integer> order = new ArrayList<>(tabs.size());
            for (int i = 0; i < tabs.size(); i++) {
                if (i == index) continue;
                order.add(i);
                if (i == last) order.add(index);
            }
            applyTabOrder(order);
        }
        removeGroupIfEmpty(gid);
        saveTabsState();
        tabGroups.save();
    }

    // ---- 非アクティブなタブ ----

    private long lastUsedOf(int tabId) {
        Long t = tabLastUsed.get(tabId);
        return t != null ? t : System.currentTimeMillis();
    }

    /** 重複の判定に使う URL(フラグメント・末尾の / を除く)。スタートページ・空のタブは null。 */
    @Nullable
    private String duplicateKey(int index) {
        String url = tabSwitcherHost.getTabUrl(index);
        if (url == null || url.isEmpty() || isHomeUrl(url) || isInternalUrl(url)) return null;
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null;
        int hash = url.indexOf('#');
        if (hash >= 0) url = url.substring(0, hash);
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url.toLowerCase(Locale.ROOT);
    }

    /**
     * 非アクティブなタブの index。しばらく使っていないタブ・グループと、同じページを開いている
     * 重複したタブ・グループ(一番最近使ったもの以外)。今のタブとそのグループは含めない。
     */
    @NonNull
    private java.util.Set<Integer> computeInactiveTabs() {
        java.util.Set<Integer> result = new java.util.HashSet<>();
        if (isSecretMode() || !InactiveTabs.isEnabled(this) || tabIds.isEmpty()) return result;
        long now = System.currentTimeMillis();
        long threshold = InactiveTabs.thresholdMs(this);
        boolean duplicates = InactiveTabs.archiveDuplicates(this);
        int n = tabIds.size();
        Integer currentGroup = currentTabIndex >= 0 && currentTabIndex < n
                ? tabGroupOf.get(tabIds.get(currentTabIndex)) : null;

        // グループに入っていないタブ
        Map<String, Integer> newestByUrl = new HashMap<>();
        String[] keys = new String[n];
        for (int i = 0; i < n; i++) {
            if (tabGroupOf.containsKey(tabIds.get(i))) continue;
            keys[i] = duplicateKey(i);
            if (keys[i] == null) continue;
            Integer best = newestByUrl.get(keys[i]);
            if (best == null || recency(i) > recency(best)) newestByUrl.put(keys[i], i);
        }
        for (int i = 0; i < n; i++) {
            if (i == currentTabIndex || tabGroupOf.containsKey(tabIds.get(i))) continue;
            boolean old = now - lastUsedOf(tabIds.get(i)) > threshold;
            boolean duplicate = duplicates && keys[i] != null && newestByUrl.get(keys[i]) != i;
            if (old || duplicate) result.add(i);
        }

        // グループ(グループ単位で判定する)
        Map<Integer, List<Integer>> members = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Integer gid = tabGroupOf.get(tabIds.get(i));
            if (gid == null) continue;
            List<Integer> list = members.get(gid);
            if (list == null) members.put(gid, list = new ArrayList<>());
            list.add(i);
        }
        Map<Integer, Long> groupRecency = new HashMap<>();
        Map<Integer, String> groupKey = new HashMap<>();
        Map<String, Integer> newestGroup = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : members.entrySet()) {
            long r = 0;
            List<String> urls = new ArrayList<>();
            boolean comparable = false;
            for (int i : e.getValue()) {
                r = Math.max(r, recency(i));
                String k = duplicateKey(i);
                if (k != null) comparable = true;
                urls.add(k != null ? k : "");
            }
            groupRecency.put(e.getKey(), r);
            if (!comparable) continue;
            java.util.Collections.sort(urls);
            String key = android.text.TextUtils.join("\n", urls);
            groupKey.put(e.getKey(), key);
            Integer best = newestGroup.get(key);
            if (best == null || r > groupRecency.get(best)) newestGroup.put(key, e.getKey());
        }
        for (Map.Entry<Integer, List<Integer>> e : members.entrySet()) {
            int gid = e.getKey();
            if (currentGroup != null && currentGroup == gid) continue;
            boolean old = now - groupRecency.get(gid) > threshold;
            String key = groupKey.get(gid);
            boolean duplicate = duplicates && key != null && newestGroup.get(key) != gid;
            if (old || duplicate) result.addAll(e.getValue());
        }
        return result;
    }

    /** 最近使った順の比較用。今のタブは常に一番新しい。 */
    private long recency(int index) {
        if (index == currentTabIndex) return Long.MAX_VALUE;
        return lastUsedOf(tabIds.get(index));
    }

    /** 3 か月使っていない非アクティブなタブ・グループを閉じる(設定で切れる)。 */
    private void closeExpiredInactiveTabs() {
        if (isSecretMode() || isFinishing() || !InactiveTabs.autoClose(this) || tabs.size() <= 1) return;
        java.util.Set<Integer> inactive = computeInactiveTabs();
        if (inactive.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<Integer> closeIds = new ArrayList<>();
        Map<Integer, Long> groupRecency = new HashMap<>();
        for (int i : inactive) {
            int id = tabIds.get(i);
            Integer gid = tabGroupOf.get(id);
            if (gid == null) {
                if (now - lastUsedOf(id) > InactiveTabs.AUTO_CLOSE_MS) closeIds.add(id);
            } else {
                Long r = groupRecency.get(gid);
                groupRecency.put(gid, Math.max(r != null ? r : 0, lastUsedOf(id)));
            }
        }
        List<Integer> deleteGroups = new ArrayList<>();
        for (Map.Entry<Integer, Long> e : groupRecency.entrySet()) {
            if (now - e.getValue() > InactiveTabs.AUTO_CLOSE_MS) deleteGroups.add(e.getKey());
        }
        if (closeIds.isEmpty() && deleteGroups.isEmpty()) return;
        for (int gid : deleteGroups) deleteTabGroup(gid);
        closeTabsById(closeIds);
        saveTabsState();
        updateTabCount();
    }

    /** 非アクティブなタブをすべて閉じる。グループは閉じて保存する(「タブグループ」から開き直せる)。 */
    private void closeInactiveTabs() {
        java.util.Set<Integer> inactive = computeInactiveTabs();
        if (inactive.isEmpty()) return;
        List<Integer> closeIds = new ArrayList<>();
        java.util.Set<Integer> groups = new java.util.LinkedHashSet<>();
        for (int i : inactive) {
            int id = tabIds.get(i);
            Integer gid = tabGroupOf.get(id);
            if (gid != null) groups.add(gid); else closeIds.add(id);
        }
        if (tabGroups != null) {
            for (int gid : groups) {
                TabGroup g = tabGroups.get(gid);
                if (g == null || g.closed) continue;
                saveGroupTabs(g);
                List<Integer> ids = new ArrayList<>();
                for (int i : tabsInGroup(gid)) ids.add(tabIds.get(i));
                for (int id : ids) tabGroupOf.remove(id);
                closeIds.addAll(ids);
            }
            tabGroups.save();
        }
        closeTabsById(closeIds);
        saveTabsState();
        updateTabCount();
        Toast.makeText(this, Lang.t("非アクティブなタブを閉じました"), Toast.LENGTH_SHORT).show();
    }

    /** 新しく開いたタブをグループの最後に入れる。 */
    private void addTabToGroupEnd(int tabId, int groupId) {
        if (tabGroups == null) return;
        TabGroup g = tabGroups.get(groupId);
        if (g == null || g.closed) return;
        List<Integer> members = tabsInGroup(groupId);
        tabGroupOf.put(tabId, groupId);
        g.touch();
        int index = tabIds.indexOf(tabId);
        if (!members.isEmpty() && index >= 0) {
            int insertAt = members.get(members.size() - 1) + 1;
            List<Integer> order = new ArrayList<>(tabs.size());
            for (int i = 0; i < tabs.size(); i++) {
                if (i == index) continue;
                if (i == insertAt) order.add(index);
                order.add(i);
            }
            if (insertAt >= tabs.size() || order.size() < tabs.size()) order.add(index);
            applyTabOrder(order);
        }
        tabGroups.save();
    }

    /**
     * 裏でタブを追加する(表示はしない)。ページは選んだ時に読み込む。
     * @return 追加したタブの index
     */
    private int addBackgroundTab(@NonNull String url, @Nullable String title) {
        int newId = nextTabId++;
        while (tabInfos.size() < tabs.size()) tabInfos.add(new TabInfo(Lang.t("読込中..."), "", null));
        tabs.add(null);
        tabIds.add(newId);
        tabLastUsed.put(newId, System.currentTimeMillis());
        tabInfos.add(new TabInfo(title != null && !title.isEmpty() ? title : Lang.t("読込中..."), url, null));
        tabLru.addLast(tabs.size() - 1);
        updateTabCount();
        saveTabsState();
        return tabs.size() - 1;
    }

    /** 開いているグループのタブを、閉じたグループとして URL・タイトルだけ残す。 */
    private void saveGroupTabs(@NonNull TabGroup g) {
        g.saved.clear();
        for (int i : tabsInGroup(g.id)) {
            String url = tabSwitcherHost.getTabUrl(i);
            String title = tabSwitcherHost.getTabTitle(i);
            g.saved.add(new TabGroup.SavedTab(url, title));
        }
        g.closed = true;
        g.touch();
    }

    /** グループのタブを ID で閉じる(グループの割り当ては先に外しておく)。 */
    private void closeTabsById(@NonNull List<Integer> ids) {
        for (int id : ids) {
            int index = tabIds.indexOf(id);
            if (index >= 0) closeTab(index, false);
        }
    }

    /** グループを閉じて保存する(タブは閉じ、タブ一覧の「タブグループ」から開き直せる)。 */
    private void closeTabGroup(int groupId) {
        if (tabGroups == null) return;
        TabGroup g = tabGroups.get(groupId);
        if (g == null || g.closed) return;
        saveGroupTabs(g);
        List<Integer> ids = new ArrayList<>();
        for (int i : tabsInGroup(groupId)) ids.add(tabIds.get(i));
        for (int id : ids) tabGroupOf.remove(id);
        closeTabsById(ids);
        tabGroups.save();
        saveTabsState();
        showGroupClosedMessage(g);
    }

    /** グループとそのタブを削除する。 */
    private void deleteTabGroup(int groupId) {
        if (tabGroups == null) return;
        TabGroup g = tabGroups.get(groupId);
        if (g == null) return;
        if (!g.closed) {
            List<Integer> ids = new ArrayList<>();
            for (int i : tabsInGroup(groupId)) ids.add(tabIds.get(i));
            for (int id : ids) tabGroupOf.remove(id);
            closeTabsById(ids);
        }
        tabGroups.remove(g);
        tabGroups.save();
        saveTabsState();
    }

    /** 閉じて保存していたグループのタブを開き直す(タブは選んだ時に読み込む)。 */
    private void reopenTabGroup(int groupId) {
        if (tabGroups == null) return;
        TabGroup g = tabGroups.get(groupId);
        if (g == null || !g.closed) return;
        for (TabGroup.SavedTab t : g.saved) {
            int index = addBackgroundTab(t.url.isEmpty() ? homeUrl() : t.url, t.title);
            tabGroupOf.put(tabIds.get(index), groupId);
        }
        g.saved.clear();
        g.closed = false;
        g.touch();
        tabGroups.save();
        saveTabsState();
        updateTabCount();
    }

    /** 「「〇〇」タブグループを閉じて保存しました  元に戻す」。 */
    private void showGroupClosedMessage(@NonNull TabGroup g) {
        String name = g.displayTitle(g.saved.size());
        com.google.android.material.snackbar.Snackbar bar = com.google.android.material.snackbar.Snackbar
                .make(snackbarHost, Lang.t("「") + name + Lang.t("」タブグループを閉じて保存しました"), UNDO_CLOSE_TAB_MS)
                .setAction(Lang.t("元に戻す"), v -> {
                    reopenTabGroup(g.id);
                    if (tabSwitcher != null) tabSwitcher.refresh();
                });
        placeSnackbar(bar);
        bar.setBehavior(new com.google.android.material.snackbar.BaseTransientBottomBar.Behavior() {
            @Override
            public boolean canSwipeDismissView(@NonNull View child) {
                return false;
            }
        });
        installSwipeDownToDismiss(bar);
        updateSnackbarHost();
        bar.show();
    }

    private void confirmCloseAllTabs() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setMessage(isSecretMode()
                        ? Lang.t("すべてのシークレットタブを閉じますか？")
                        : Lang.t("本当にすべてのタブを閉じますか？"))
                .setPositiveButton(Lang.t("はい"), (d, which) -> {
                    if (isSecretMode()) {
                        // シークレットは画面ごと閉じる(セッションも破棄される)
                        leaveSecretToNormalTabs();
                    } else if (tabSwitcher != null && tabSwitcher.isShowing()) {
                        tabSwitcher.playCloseAll(this::closeAllTabs);
                    } else {
                        playCloseAllOnPage();
                    }
                })
                .setNegativeButton(Lang.t("いいえ"), null)
                .show();
    }

    /** タブ一覧を出していない時の「すべてのタブを閉じる」: ページが奥へ縮みながら消え、新しいページがふわっと出る。 */
    private void playCloseAllOnPage() {
        View page = webViewContainer;
        if (page == null || page.getWidth() == 0) {
            closeAllTabs();
            return;
        }
        page.animate().cancel();
        page.setPivotX(page.getWidth() / 2f);
        page.setPivotY(page.getHeight() / 2f);
        page.animate().scaleX(0.82f).scaleY(0.82f).alpha(0f).setDuration(300)
                .setInterpolator(new android.view.animation.PathInterpolator(0.4f, 0f, 0.9f, 0.4f))
                .withEndAction(() -> {
                    closeAllTabs();
                    page.setScaleX(1.04f);
                    page.setScaleY(1.04f);
                    page.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(380)
                            .setInterpolator(new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f))
                            .start();
                }).start();
    }

    /** タブ一覧({@link TabSwitcher})にタブの情報と操作を渡す。 */
    private final TabSwitcher.Host tabSwitcherHost = new TabSwitcher.Host() {
        @Override
        public int getTabCount() {
            return tabs.size();
        }

        @Override
        public int getCurrentTabIndex() {
            return currentTabIndex;
        }

        @Override
        public int getTabId(int index) {
            return (index >= 0 && index < tabIds.size()) ? tabIds.get(index) : -1;
        }

        @Nullable
        @Override
        public String getTabTitle(int index) {
            if (index < 0 || index >= tabs.size()) return null;
            if (isHomeUrl(getTabUrl(index))) return homeSiteName();
            GeckoTabView tab = tabs.get(index);
            String title = tab != null ? tab.getTitle() : null;
            if ((title == null || title.isEmpty()) && index < tabInfos.size()) {
                title = tabInfos.get(index).getTitle();
            }
            if (Lang.t("読込中...").equals(title)) title = null;
            return title;
        }

        @Nullable
        @Override
        public String getTabUrl(int index) {
            if (index < 0 || index >= tabs.size()) return null;
            GeckoTabView tab = tabs.get(index);
            String url = tab != null ? tab.getUrl() : null;
            // エラーページ(data:)や未読込のタブは TabInfo の URL を使う
            if ((url == null || url.isEmpty() || url.startsWith("data:")) && index < tabInfos.size()) {
                url = tabInfos.get(index).getUrl();
            }
            return url;
        }

        @Nullable
        @Override
        public Bitmap getTabIcon(int index) {
            return (index >= 0 && index < tabInfos.size()) ? tabInfos.get(index).getIcon() : null;
        }

        @Nullable
        @Override
        public Bitmap getTabSnapshot(int index) {
            if (index < 0 || index >= tabs.size()) return null;
            GeckoTabView tab = tabs.get(index);
            return tab != null ? tabSnapshots.get(tab) : null;
        }

        @Nullable
        @Override
        public File getTabSnapshotFile(int index) {
            // シークレットは端末に保存しない
            if (isSecretMode() || index < 0 || index >= tabIds.size()) return null;
            return new File(getFilesDir(), "tab_snapshot_" + tabIds.get(index) + ".png");
        }

        @Override
        public int getOtherModeTabCount() {
            if (!isSecretMode()) return -1;
            try {
                // 通常タブは onPause() で保存されているので、その件数を出す
                return new JSONArray(prefs.getString(KEY_TABS, "[]")).length();
            } catch (JSONException e) {
                return -1;
            }
        }

        @Override
        public boolean isSecret() {
            return isSecretMode();
        }

        @Override
        public View getContentView() {
            return webViewContainer;
        }

        @Override
        public void onSelectTab(int index) {
            switchToTab(index);
        }

        @Override
        public void onCloseTab(int index) {
            if (isSecretMode() && tabs.size() <= 1) {
                // 最後のシークレットタブ → シークレットモードを終えて通常のタブ一覧へ
                leaveSecretToNormalTabs();
                return;
            }
            // 最後の通常タブを閉じた場合は closeTab() が新しいタブを開き、一覧も閉じる
            closeTab(index, true);
            saveTabsState();
        }

        @Override
        public void onReorderTabs(@NonNull List<Integer> order) {
            applyTabOrder(order);
        }

        // ---- タブグループ ----

        @Override
        public boolean supportsGroups() {
            return tabGroups != null;
        }

        @Override
        public int getTabGroupId(int index) {
            if (index < 0 || index >= tabIds.size()) return -1;
            Integer g = tabGroupOf.get(tabIds.get(index));
            return g != null ? g : -1;
        }

        @Nullable
        @Override
        public TabGroup getGroup(int groupId) {
            return tabGroups != null ? tabGroups.get(groupId) : null;
        }

        @NonNull
        @Override
        public List<TabGroup> getGroups() {
            return tabGroups != null ? tabGroups.all() : new ArrayList<>();
        }

        @NonNull
        @Override
        public List<Integer> getTabsInGroup(int groupId) {
            return tabsInGroup(groupId);
        }

        @Override
        public int countTabsInGroup(int groupId) {
            return tabsInGroup(groupId).size();
        }

        @Nullable
        @Override
        public Bitmap getFaviconForUrl(@Nullable String url) {
            if (url == null || url.isEmpty() || faviconCache == null) return null;
            Bitmap icon = faviconCache.get(url);
            if (icon != null) return icon;
            String host = hostOf(url);
            return host != null ? siteIcon(host) : null;
        }

        @Override
        public int onCreateGroup(@NonNull List<Integer> indices) {
            return createTabGroup(indices);
        }

        @Override
        public int onCreateGroupWithNewTab() {
            if (tabGroups == null) return -1;
            int index = addBackgroundTab(homeUrl(), null);
            List<Integer> one = new ArrayList<>();
            one.add(index);
            return createTabGroup(one);
        }

        @Override
        public void onGroupChanged(int groupId) {
            if (tabGroups != null) tabGroups.save();
        }

        @Override
        public void onNewTabInGroup(int groupId) {
            pendingNewTabGroupId = groupId;
            openNewTabAnimated(null);
        }

        @Override
        public void onCloseGroup(int groupId) {
            closeTabGroup(groupId);
        }

        @Override
        public void onDeleteGroup(int groupId) {
            deleteTabGroup(groupId);
        }

        @Override
        public void onUngroup(int groupId) {
            if (tabGroups == null) return;
            TabGroup g = tabGroups.get(groupId);
            for (int i : tabsInGroup(groupId)) tabGroupOf.remove(tabIds.get(i));
            if (g != null) tabGroups.remove(g);
            saveTabsState();
        }

        @Override
        public void onOpenSavedGroup(int groupId) {
            reopenTabGroup(groupId);
        }

        @Override
        public void onMoveTabsToGroup(@NonNull List<Integer> indices, int groupId) {
            moveTabsToGroup(indices, groupId);
        }

        @Override
        public void onRemoveTabFromGroup(int index) {
            removeTabFromGroup(index);
        }

        @NonNull
        @Override
        public java.util.Set<Integer> getInactiveTabIndices() {
            return computeInactiveTabs();
        }

        @Override
        public void onCloseInactiveTabs() {
            closeInactiveTabs();
        }

        @Override
        public void onShowGroups() {
            Intent intent = new Intent(MainActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.putExtra(EXTRA_SHOW_TAB_SWITCHER, true);
            intent.putExtra(EXTRA_SHOW_TAB_GROUPS, true);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }

        @Override
        public void onNewTab() {
            // タブ一覧の「+」ボタンから新しいページが広がるように見せる
            openNewTabAnimated(findViewById(R.id.tsNewTab));
        }

        @Override
        public void onSwitchMode() {
            if (isSecretMode()) {
                openNormalMode(true);
            } else {
                saveTabsState();
                openSecretMode(false, true);
            }
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }

        @Override
        public void onMenuItem(int itemId) {
            if (itemId == R.id.action_secret) {
                openSecretMode(true, false);
                overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
            } else if (itemId == R.id.action_history) {
                showHistoryDialog();
            } else if (itemId == R.id.action_bookmark) {
                startActivity(new Intent(MainActivity.this, BookmarksActivity.class).putExtra(BookmarksActivity.EXTRA_SECRET, isSecretMode()));
                overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
            } else if (itemId == R.id.action_savedpage) {
                startActivity(new Intent(MainActivity.this, SavedPagesActivity.class).putExtra(BookmarksActivity.EXTRA_SECRET, isSecretMode()));
                overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
            } else if (itemId == R.id.action_settings) {
                startActivityForResult(new Intent(MainActivity.this, SettingsActivity.class), 1001);
            } else if (itemId == R.id.action_alltabclose) {
                confirmCloseAllTabs();
            }
        }
    };

    private void closeAllTabs() {
        if (tabs.isEmpty()) {
            Toast.makeText(this, Lang.t("タブがありません"), Toast.LENGTH_SHORT).show();
            return;
        }

        tabUnloadHandler.removeCallbacks(tabUnloadRunnable);

        // 開いているグループは消さずに「閉じて保存」にする(タブ一覧の「タブグループ」から開き直せる)
        if (tabGroups != null) {
            for (TabGroup g : tabGroups.all()) {
                if (!g.closed) saveGroupTabs(g);
            }
            tabGroups.save();
        }
        tabGroupOf.clear();

        for (GeckoTabView wv : tabs) {
            if (wv != null) {
                try {
                    webViewContainer.removeView(wv);
                    wv.stopLoading();
                    wv.onPause();
                    wv.destroy();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        for (int closedId : tabIds) {
            deleteTabFiles(closedId);
        }

        tabs.clear();
        tabIds.clear();
        tabLastUsed.clear();
        tabLru.clear();
        tabInfos.clear();
        tabSnapshots.clear();
        webViewFavicons.clear();
        originalUserAgents.clear();
        currentTabIndex = -1;

        detachTabViews(webViewContainer::removeAllViews);

        new Handler(Looper.getMainLooper()).post(() -> {
            newStartPage();
            Toast.makeText(this, Lang.t("すべてのタブを閉じました"), Toast.LENGTH_SHORT).show();
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        });
    }


    private void loadUrlInCurrentTab(String url) {
        if (url.contains(" ")) {
            url = BrowserPrefs.searchUrl(this, url);
        } else if (!url.startsWith("https://") && !url.startsWith("http://")) {
            if (url.contains(".")) {
                url = "https://" + url;
            } else {
                // 設定で選んだ検索エンジンで検索する
                url = BrowserPrefs.searchUrl(this, url);
            }
        }
        GeckoTabView current = getCurrentTab();
        if (current == null) {
            addNewTab(url);
            return;
        }
        current.loadUrl(url);
        tabInfos.get(currentTabIndex).setUrl(url);
        saveTabsState();
    }

    private void closeKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(urlEditText.getWindowToken(), 0);
    }

    private void addNewTab(String url) {
        int newId = nextTabId++;
        GeckoTabView webView = createTab(newId);
        openUrlInTab(webView, url);
        addTabView(webView, newId, url);
    }

    /** 作成済みのタブを一覧に追加して前面に出す。 */
    private void addTabView(GeckoTabView webView, int newId, String url) {
        isNewTab = true;
        if (tabSwitcher != null) tabSwitcher.hideNow();

        tabs.add(webView);
        tabIds.add(newId);
        tabLastUsed.put(newId, System.currentTimeMillis());

        // ✅ タブ情報を必ず追加
        if (tabInfos.size() < tabs.size()) {
            tabInfos.add(new TabInfo(Lang.t("読込中..."), url, null));
        }

        android.view.animation.Animation fadeIn =
                android.view.animation.AnimationUtils.loadAnimation(this, R.anim.tab_in);
        webView.startAnimation(fadeIn);

        webViewContainer.addView(webView);

        if (pendingNewTabGroupId >= 0) {
            int gid = pendingNewTabGroupId;
            pendingNewTabGroupId = -1;
            addTabToGroupEnd(newId, gid);
        }
        switchToTab(tabIds.indexOf(newId));
        updateTabCount();
        new Handler().postDelayed(() -> isNewTab = false, 500);
    }

    // ✅ タブ状態の保存
    // ✅ タブ状態の保存
    private void saveTabsState() {
        saveTabsHandler.removeCallbacks(saveTabsRunnable);
        // シークレットモードのタブは端末に一切保存しない
        if (isSecretMode()) return;

        JSONArray tabsArray = new JSONArray();

        for (int i = 0; i < tabs.size(); i++) {
            int id = tabIds.get(i);
            GeckoTabView webView = tabs.get(i);
            String url = "";
            String title = null;

            if (webView != null) {
                url = webView.getUrl();
                if (url == null) url = "";

                // 戻る/進む履歴を含む Gecko のタブ状態(JSON)を保存
                String state = webView.saveState();
                if (state != null) {
                    writeTextFile(new File(getFilesDir(), "tab_state_" + id + ".json"), state);
                }

                // サムネイルは captureTabSnapshot() の時点で保存済みなのでここでは書き直さない。
                title = webView.getTitle();
            } else {
                // 破棄済みタブは state ファイルが unloadTab() で保存済み。
                TabInfo info = (i < tabInfos.size()) ? tabInfos.get(i) : null;
                if (info != null && info.getUrl() != null) {
                    url = info.getUrl();
                }
            }

            // エラーページ(data:)はそのまま保存せず、元の URL を残す
            if (url.startsWith("data:")) {
                TabInfo ti = (i < tabInfos.size()) ? tabInfos.get(i) : null;
                url = (ti != null && ti.getUrl() != null && !ti.getUrl().startsWith("data:")) ? ti.getUrl() : "";
            }

            // 読込前などで取れなければ TabInfo の値を使う。プレースホルダは保存しない。
            if (title == null || title.isEmpty()) {
                TabInfo ti = (i < tabInfos.size()) ? tabInfos.get(i) : null;
                if (ti != null) title = ti.getTitle();
            }
            if (title == null || Lang.t("読込中...").equals(title)) title = "";

            JSONObject obj = new JSONObject();
            try {
                obj.put("id", id);
                obj.put("url", url);
                obj.put("title", title);
                Integer group = tabGroupOf.get(id);
                if (group != null) obj.put("group", group);
                obj.put("used", lastUsedOf(id));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            tabsArray.put(obj);
        }

        if (tabGroups != null) tabGroups.save();
        if (tabIds.isEmpty()) return;

        int currentIndex = Math.max(0, Math.min(currentTabIndex, tabIds.size() - 1));
        int currentTabId = tabIds.get(currentIndex);

        prefs.edit()
                .putString(tabsStoreKey(), tabsArray.toString())
                .putInt(currentTabStoreKey(), currentTabId)
                .apply();
    }

    // ✅ タブ状態の読み込み
    // ✅ タブ状態の読み込み
    private void loadTabsState() {
        loadTabnoHideurl = true;
        String json = prefs.getString(tabsStoreKey(), "[]");
        int currentTabId = prefs.getInt(currentTabStoreKey(), -1);

        try {
            JSONArray array = new JSONArray(json);

            tabs.clear();
            tabIds.clear();
            tabInfos.clear();
            tabLru.clear();
            webViewContainer.removeAllViews();

            int maxId = -1;
            int restoreIndex = 0;
            long now = System.currentTimeMillis();
            tabLastUsed.clear();

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                int id = obj.getInt("id");
                String url = obj.getString("url");
                String savedTitle = obj.optString("title", "");

                tabIds.add(id);
                // 以前のバージョンで保存したタブは、今から数え始める
                tabLastUsed.put(id, obj.optLong("used", now));
                tabInfos.add(new TabInfo(savedTitle.isEmpty() ? Lang.t("読込中...") : savedTitle, url, null));
                int group = obj.optInt("group", -1);
                TabGroup g = group >= 0 && tabGroups != null ? tabGroups.get(group) : null;
                if (g != null && !g.closed) tabGroupOf.put(id, group);
                // GeckoSession は WebView より重いので、起動時は表示するタブだけ作る。
                // 他のタブは選択されたときに loadTabIfNeeded() で保存済みの状態から復元する。
                tabs.add(null);

                if (id == currentTabId) restoreIndex = i;
                if (id > maxId) maxId = id;
                tabLru.addLast(i);
            }

            nextTabId = Math.max(maxId + 1, firstTabId());
            // タブが 1 つも無くなっている開いたグループ(前回の終了時の不整合など)は消す
            if (tabGroups != null) {
                for (TabGroup g : tabGroups.all()) {
                    if (!g.closed) removeGroupIfEmpty(g.id);
                }
            }

            if (tabs.isEmpty()) {
                if (!isOpeningLink()) newStartPage();
                return;
            }

            currentTabIndex = restoreIndex;
            loadTabIfNeeded(currentTabIndex);

            // 現在タブをMRU先頭に移動。
            touchTabLru(currentTabIndex);
            updateStartPage();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                requestFavicon(getCurrentTab());
                loadTabnoHideurl = false;
            }, 2500);

        } catch (JSONException e) {
            e.printStackTrace();
            newStartPage();
        }
    }

    private GeckoTabView getCurrentTab() {
        if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) return null;
        return loadTabIfNeeded(currentTabIndex);
    }

    /** 破棄済みのタブなら復元して GeckoTabView を返す。ロード済みならそのまま返す。 */
    private GeckoTabView loadTabIfNeeded(int index) {
        if (index < 0 || index >= tabs.size()) return null;

        GeckoTabView wv = tabs.get(index);
        if (wv != null) return wv;

        int id = tabIds.get(index);
        GeckoTabView newWebView = createTab(id);

        String state = isSecretMode() ? null
                : readTextFile(new File(getFilesDir(), "tab_state_" + id + ".json"));
        TabInfo info = (index < tabInfos.size()) ? tabInfos.get(index) : null;
        String savedUrl = (info != null && info.getUrl() != null && !info.getUrl().isEmpty())
                ? info.getUrl() : null;
        if (!newWebView.restoreState(state)) {
            openUrlInTab(newWebView, savedUrl != null ? savedUrl : homeUrl());
        } else if (savedUrl != null && !savedUrl.startsWith("data:")
                && !sameUrlIgnoringFragment(savedUrl, newWebView.getStateUrl())) {
            // 履歴の保存が最後のページに間に合わなかった場合: 履歴を戻したうえで最後に見ていたページを開く
            openUrlInTab(newWebView, savedUrl);
        }

        webViewContainer.addView(newWebView);
        newWebView.setVisibility(View.GONE);

        tabs.set(index, newWebView);
        return newWebView;
    }

    private static boolean sameUrlIgnoringFragment(@Nullable String a, @Nullable String b) {
        if (a == null || b == null) return a == b;
        int ia = a.indexOf('#');
        int ib = b.indexOf('#');
        String na = ia >= 0 ? a.substring(0, ia) : a;
        String nb = ib >= 0 ? b.substring(0, ib) : b;
        if (na.endsWith("/")) na = na.substring(0, na.length() - 1);
        if (nb.endsWith("/")) nb = nb.substring(0, nb.length() - 1);
        return na.equals(nb);
    }

    /** バックグラウンドタブのWebView実体だけを破棄してメモリを解放する。 */
    private void unloadTab(int index) {
        if (isSecretMode()) return;
        if (index < 0 || index >= tabs.size()) return;
        if (index == currentTabIndex) return;

        GeckoTabView wv = tabs.get(index);
        if (wv == null) return;

        int id = tabIds.get(index);

        if (!isSecretMode()) {
            String state = wv.saveState();
            if (state != null) {
                writeTextFile(new File(getFilesDir(), "tab_state_" + id + ".json"), state);
            }
        }

        // 破棄後もタブ一覧でタイトルとURLを出せるよう、TabInfo に残す。
        // (サムネイルは captureTabSnapshot() で保存済みなので、破棄時に書き直さない)
        String unloadTitle = wv.getTitle();
        if (unloadTitle != null && !unloadTitle.isEmpty() && index < tabInfos.size()) {
            tabInfos.get(index).setTitle(unloadTitle);
        }
        String unloadUrl = wv.getUrl();
        if (unloadUrl != null && !unloadUrl.isEmpty() && !unloadUrl.startsWith("data:") && index < tabInfos.size()) {
            tabInfos.get(index).setUrl(unloadUrl);
        }

        webViewContainer.removeView(wv);
        wv.stopLoading();
        wv.destroy();

        originalUserAgents.remove(wv);
        webViewFavicons.remove(wv);
        tabSnapshots.remove(wv);
        tabs.set(index, null);
    }

    /** LRUを更新し、一定時間後に冷えたタブを解放する。 */
    private void touchTabLru(int index) {
        if (index < 0 || index >= tabs.size()) return;

        tabLru.remove((Integer) index);
        tabLru.addFirst(index);

        tabUnloadHandler.removeCallbacks(tabUnloadRunnable);
        tabUnloadHandler.postDelayed(tabUnloadRunnable, TAB_UNLOAD_DELAY_MS);
    }

    private void unloadColdTabs() {
        java.util.Set<Integer> keep = new java.util.HashSet<>();
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
            keep.add(currentTabIndex);
        }

        for (int idx : tabLru) {
            if (idx < 0 || idx >= tabs.size()) continue;
            if (tabs.get(idx) == null) continue;
            if (keep.size() >= MAX_LOADED_TABS) break;
            keep.add(idx);
        }

        for (int i = 0; i < tabs.size(); i++) {
            if (!keep.contains(i)) {
                unloadTab(i);
            }
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            for (int i = 0; i < tabs.size(); i++) {
                if (i != currentTabIndex) {
                    unloadTab(i);
                }
            }
        } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            unloadColdTabs();
        }
    }

    // ✅ WebViewの状態をファイルに保存



    private void copyLink(String link) {
        ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(Lang.t("リンク"), link);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(MainActivity.this, Lang.t("リンクをコピーしました"), Toast.LENGTH_SHORT).show();
    }

    private class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder> {
        private final List<Bookmark> items;
        private final boolean managementMode;
        private final AlertDialog dialog;
        public BookmarkAdapter(List<Bookmark> items, boolean managementMode, AlertDialog dialog) {
            this.items = items;
            this.managementMode = managementMode;
            this.dialog = dialog;
        }
        @Override
        public BookmarkViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bookmark, parent, false);
            return new BookmarkViewHolder(view);
        }

        @Override
        public void onBindViewHolder(BookmarkViewHolder holder, int position) {
            Bookmark bm = items.get(position);
            holder.title.setText(bm.getTitle());
            holder.url.setText(bm.getUrl());
            Bitmap icon = faviconCache.get(bm.getUrl());
            if (icon != null) {
                holder.favicon.setImageBitmap(icon);
            } else {
                holder.favicon.setImageResource(R.drawable.transparent_vector);
            }
            holder.itemView.setOnClickListener(v -> {
                getCurrentTab().loadUrl(bm.getUrl());
                dialog.dismiss();
            });
            if (managementMode) {
                holder.itemView.setOnLongClickListener(v -> {
                    int currentPosition = holder.getAdapterPosition();
                    if (currentPosition == RecyclerView.NO_POSITION) return true;
                    String[] options = {Lang.t("編集"), Lang.t("削除")};
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle(Lang.t("操作を選択"))
                            .setItems(options, (dialogInterface, which) -> {
                                if (which == 0) {
                                    showEditBookmarkDialog(currentPosition, this);
                                } else if (which == 1) {
                                    items.remove(currentPosition);
                                    notifyItemRemoved(currentPosition);
                                    saveBookmarks();
                                    Toast.makeText(MainActivity.this, Lang.t("削除しました"), Toast.LENGTH_SHORT).show();
                                }
                            }).show();
                    return true;
                });
            }
        }
        @Override
        public int getItemCount() { return items.size(); }
        class BookmarkViewHolder extends RecyclerView.ViewHolder {
            ImageView favicon;
            TextView title;
            TextView url;
            public BookmarkViewHolder(View itemView) {
                super(itemView);
                favicon = itemView.findViewById(R.id.bookmarkFavicon);
                title = itemView.findViewById(R.id.bookmarkTitle);
                url = itemView.findViewById(R.id.bookmarkUrl);
            }
        }
    }


    // ✅ WebViewの状態をファイルから読み込み

    @Override
    protected void onPause() {
        // 押したままのキー・傾けたままのスティックを残さない
        if (softKeyboard != null) softKeyboard.releaseAll();
        if (virtualMouse != null) virtualMouse.releaseAll();
        if (virtualController != null) virtualController.releaseAll();
        if (inputRouter != null) inputRouter.releaseAll();
        if (gamepadInput != null) gamepadInput.releasePageInput();
        // 画面を離れたらマウスの固定もやめる(PC のブラウザでウィンドウを切り替えた時と同じ)
        GeckoTabView lockedTab = getCurrentTab();
        if (lockedTab != null) lockedTab.exitPointerLock();
        super.onPause();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            unregisterNetworkCallback();
        }
        // バッテリー残量更新停止
        if (batteryReceiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException e) {
                // すでに解除されていた場合
            }
            batteryReceiverRegistered = false;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            if (telephonyManager != null) {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            }
        }
        GeckoTabView webView = getCurrentTab();
        // 最新の履歴を Gecko から通知してもらう(届き次第 onSessionStateChanged で保存し直す)
        for (GeckoTabView t : tabs) {
            if (t != null) t.flushSessionState();
        }
        // 戻ってきたときに実行したい処理
        saveTabsState();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (dualScreen != null) dualScreen.onStop();

        //wentToBackground = true;
    }

    @Override
    protected void onDestroy() {
        if (redirectedToLock) {
            // 画面を作る前にロック画面へ回した: 保存するものも閉じるものも無い
            super.onDestroy();
            return;
        }
        if (pageMenuDialog != null) pageMenuDialog.dismiss();
        if (legacyImeProbe != null) {
            legacyImeProbe.dismiss();
            legacyImeProbe = null;
        }
        if (dualScreen != null) {
            dualScreen.release();
            dualScreen = null;
        }
        ExtensionManager.get(this).clearTabHost(extensionTabHost);
        // 通常タブは最新の状態を保存してから閉じる(シークレットは保存しない)
        saveTabsState();
        finalizeClosedTab();
        if (pageReadyNotifier != null) pageReadyNotifier.release();
        tabUnloadHandler.removeCallbacks(tabUnloadRunnable);
        saveTabsHandler.removeCallbacksAndMessages(null);
        if (tabSwitcher != null) tabSwitcher.release();
        // GeckoSession は明示的に閉じないと残り続ける。
        // シークレットモードは全てのプライベートセッションを閉じた時点で Cookie 等が破棄される。
        for (GeckoTabView tab : tabs) {
            if (tab != null) {
                try {
                    tab.destroy();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // タブ一覧が出ていれば、まずそれを閉じる(現在のタブへ拡大して戻る)
        if (tabSwitcher != null && tabSwitcher.handleBack()) return;
        // 仮想コントローラーの編集中は編集を終える
        if (virtualController != null && virtualController.isEditing()) {
            virtualController.setEditing(false);
            return;
        }
        // マウスを固定しているページは、まず固定を解除する
        GeckoTabView lockedTab = getCurrentTab();
        if (lockedTab != null && lockedTab.isPointerLocked()) {
            lockedTab.exitPointerLock();
            return;
        }
        // アドレスバーの入力中は入力をやめる(候補の画面を閉じる)
        if (urlEditText.hasFocus()) {
            clearUrlBarFocus();
            closeKeyboard();
            return;
        }
        // スタートページのダイアログやメニューが開いていれば閉じる
        if (startPageView != null && startPageView.getVisibility() == View.VISIBLE
                && startPageView.handleBack()) return;
        GeckoTabView webView = getCurrentTab();
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        if (isFullScreen) {
            // 動画などの全画面表示を終了する
            webView.getGeckoSession().exitFullScreen();
        } else if (webView.isReaderActive() && webView.sendPageCommand("readerClose")) {
            // リーディングモードは前のページへ戻らずに、元の表示へ戻す
        } else if (webView.canGoBack()) {
            nohideurl=true;
            webView.goBack();
        } else {
            // 戻れるページが無ければ、どのタブでも2回押しで終了
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastBackPressedTime < 2000) {
                // 2秒以内に2回押されたら終了。
                // シークレットの画面は通常の画面の上に重なっている(タブ一覧から開いた時など)ので、
                // finish() だと後ろの画面(タブ一覧)が出てきてしまう。重なっている画面ごと閉じる
                if (toast != null) toast.cancel();
                finishAffinity();
                overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
            } else {
                if (toast != null) {
                    // 既存のToastをキャンセル
                    toast.cancel();
                }
                toast = Toast.makeText(this, Lang.t("もう一度 戻る で終了します"), Toast.LENGTH_SHORT);
                toast.show();
                lastBackPressedTime = currentTime;
            }
        }
    }
}