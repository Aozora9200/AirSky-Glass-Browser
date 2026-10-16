package com.airsky.browser;

import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Rational;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsSessionToken;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.TranslationsController;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 他のアプリがサインインなどで開く簡易ブラウザ(Chrome の Custom Tabs と同じ仕組み)。
 *
 * 呼び出し元アプリの上に重なって表示され、閉じると呼び出し元へ戻る。Cookie はブラウザ本体と
 * 共有するので、Aozora でサインイン済みのサイトはそのまま使える。
 * 呼び出し元が指定したツールバーの色・閉じるボタンのアイコン・アクションボタン・メニュー項目にも対応する。
 * 「最小化」でピクチャー イン ピクチャーの小さなカードになり、呼び出し元アプリを操作できる。
 */
public class CustomTabActivity extends AppCompatActivity implements GeckoTabView.WindowInsetsHost {

    /** MainActivity が受け取った Intent の呼び出し元パッケージ(リダイレクトで戻る先の判定用)。 */
    static final String EXTRA_CALLER_PACKAGE = "com.airsky.browser.extra.CT_CALLER";

    private static final int REQUEST_FILE_CHOOSER = 7101;
    private static final int REQUEST_GECKO_PERMISSIONS = 7102;
    private static final String UA_SUFFIX = " AirSkyBrowser/" + BuildConfig.VERSION_NAME;
    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final ExecutorService ICON_LOADER = Executors.newSingleThreadExecutor();

    /** Custom Tabs の Intent か(セッションの印が付いている)。 */
    static boolean isCustomTabIntent(@Nullable Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return false;
        try {
            return intent.hasExtra(CustomTabsIntent.EXTRA_SESSION);
        } catch (RuntimeException e) {
            // 壊れた extras(他アプリの Parcelable など)は通常のリンクとして扱う
            return false;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    private GeckoTabView tab;
    private GeckoPromptDelegate promptDelegate;
    private GeckoPermissionDelegate permissionDelegate;
    private PageTranslator translator;

    private View root, content, toolbar, bar, findBar, divider, pipView;
    private ImageButton closeButton, minimizeButton, actionButton, shareButton, menuButton;
    private TextView titleView, domainView, findCount, pipTitle, pipDomain;
    private ImageView securityIcon, pipIcon;
    private EditText findQuery;
    private LinearProgressIndicator progress;
    @Nullable private PopupWindow menuPopup;

    @Nullable private CustomTabsSessionToken sessionToken;
    @Nullable private String callerPackage;
    private boolean showTitle;
    private boolean shareEnabled = true;
    /** ツールバーの上の文字・アイコンの色。 */
    private int foreground;
    private int foregroundVariant;
    @Nullable private Bitmap favicon;
    @Nullable private String faviconForHost;
    private boolean fullScreen;
    private boolean pipSupported;
    /** 小さなカードから戻った直後(次に onResume なら全画面へ戻した、onStop なら閉じられた)。 */
    private boolean leftPip;

    private final ArrayList<MenuEntry> clientMenuItems = new ArrayList<>();

    private static final class MenuEntry {
        final String title;
        final PendingIntent intent;

        MenuEntry(String title, PendingIntent intent) {
            this.title = title;
            this.intent = intent;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        Uri data = intent.getData();
        if (data == null) {
            finish();
            return;
        }
        if (getSharedPreferences("AppPrefs", MODE_PRIVATE).getBoolean("useSecretSecurity", false)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
        setContentView(R.layout.activity_custom_tab);
        bindViews();

        try {
            sessionToken = CustomTabsSessionToken.getSessionTokenFromIntent(intent);
        } catch (RuntimeException ignored) {}
        callerPackage = intent.getStringExtra(EXTRA_CALLER_PACKAGE);
        pipSupported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);

        readClientCustomization(intent);
        setUpToolbar();
        setUpFindBar();
        createTab();

        String url = data.toString();
        updateUrlViews(url);
        tab.loadUrl(url);

        // 戻るキー: 検索 → 全画面 → ページの履歴 → 閉じる
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (findBar.getVisibility() == View.VISIBLE) {
                    hideFindBar();
                } else if (fullScreen) {
                    tab.getGeckoSession().exitFullScreen();
                } else if (tab != null && tab.canGoBack()) {
                    tab.goBack();
                } else {
                    close();
                }
            }
        });
    }

    private void bindViews() {
        root = findViewById(R.id.ctRoot);
        content = findViewById(R.id.ctContent);
        toolbar = findViewById(R.id.ctToolbar);
        bar = findViewById(R.id.ctBar);
        findBar = findViewById(R.id.ctFindBar);
        divider = findViewById(R.id.ctDivider);
        pipView = findViewById(R.id.ctPip);
        closeButton = findViewById(R.id.ctClose);
        minimizeButton = findViewById(R.id.ctMinimize);
        actionButton = findViewById(R.id.ctAction);
        shareButton = findViewById(R.id.ctShare);
        menuButton = findViewById(R.id.ctMenu);
        titleView = findViewById(R.id.ctTitle);
        domainView = findViewById(R.id.ctDomain);
        securityIcon = findViewById(R.id.ctSecurity);
        findQuery = findViewById(R.id.ctFindQuery);
        findCount = findViewById(R.id.ctFindCount);
        progress = findViewById(R.id.ctProgress);
        pipTitle = findViewById(R.id.ctPipTitle);
        pipDomain = findViewById(R.id.ctPipDomain);
        pipIcon = findViewById(R.id.ctPipIcon);
    }

    // ------------------------------------------------------------------
    // 呼び出し元アプリの指定(色・ボタン・メニュー)
    // ------------------------------------------------------------------

    private void readClientCustomization(@NonNull Intent intent) {
        int toolbarColor;
        Integer navColor = null;
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        Integer requested = null;
        try {
            CustomTabColorSchemeParams params = CustomTabsIntent.getColorSchemeParams(intent,
                    night ? CustomTabsIntent.COLOR_SCHEME_DARK : CustomTabsIntent.COLOR_SCHEME_LIGHT);
            requested = params.toolbarColor;
            navColor = params.navigationBarColor;
        } catch (RuntimeException ignored) {}

        if (requested != null) {
            toolbarColor = requested | 0xFF000000;
        } else {
            toolbarColor = MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurfaceContainer);
        }
        boolean lightBar = ColorUtils.calculateLuminance(toolbarColor) > 0.5;
        if (requested != null) {
            foreground = lightBar ? 0xDE000000 : Color.WHITE;
            foregroundVariant = lightBar ? 0x99000000 : 0xB3FFFFFF;
        } else {
            foreground = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface);
            foregroundVariant = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurfaceVariant);
        }
        toolbar.setBackgroundColor(toolbarColor);
        getWindow().setStatusBarColor(toolbarColor);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(lightBar);
        if (navColor != null) {
            getWindow().setNavigationBarColor(navColor | 0xFF000000);
            bars.setAppearanceLightNavigationBars(ColorUtils.calculateLuminance(navColor | 0xFF000000) > 0.5);
        }

        showTitle = intent.getIntExtra(CustomTabsIntent.EXTRA_TITLE_VISIBILITY_STATE,
                CustomTabsIntent.NO_TITLE) == CustomTabsIntent.SHOW_PAGE_TITLE;
        shareEnabled = intent.getIntExtra(CustomTabsIntent.EXTRA_SHARE_STATE,
                CustomTabsIntent.SHARE_STATE_DEFAULT) != CustomTabsIntent.SHARE_STATE_OFF;

        try {
            Bitmap closeIcon = intent.getParcelableExtra(CustomTabsIntent.EXTRA_CLOSE_BUTTON_ICON);
            if (closeIcon != null) closeButton.setImageBitmap(closeIcon);
            else tint(closeButton);
        } catch (RuntimeException e) {
            tint(closeButton);
        }

        try {
            Bundle action = intent.getBundleExtra(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE);
            if (action != null) {
                Bitmap icon = action.getParcelable(CustomTabsIntent.KEY_ICON);
                PendingIntent pi = action.getParcelable(CustomTabsIntent.KEY_PENDING_INTENT);
                String desc = action.getString(CustomTabsIntent.KEY_DESCRIPTION);
                if (icon != null && pi != null) {
                    actionButton.setImageBitmap(icon);
                    actionButton.setContentDescription(desc);
                    actionButton.setVisibility(View.VISIBLE);
                    actionButton.setOnClickListener(v -> sendToClient(pi));
                }
            }
        } catch (RuntimeException ignored) {}

        try {
            ArrayList<Bundle> items = intent.getParcelableArrayListExtra(CustomTabsIntent.EXTRA_MENU_ITEMS);
            if (items != null) {
                for (Bundle b : items) {
                    String title = b.getString(CustomTabsIntent.KEY_MENU_ITEM_TITLE);
                    PendingIntent pi = b.getParcelable(CustomTabsIntent.KEY_PENDING_INTENT);
                    if (title != null && pi != null) clientMenuItems.add(new MenuEntry(title, pi));
                }
            }
        } catch (RuntimeException ignored) {}
    }

    /** 呼び出し元のボタン・メニュー項目を押した: 今の URL を付けて呼び出し元へ伝える。 */
    private void sendToClient(@NonNull PendingIntent pi) {
        Intent fill = new Intent();
        String url = tab != null ? tab.getUrl() : null;
        if (url != null) fill.setData(Uri.parse(url));
        try {
            pi.send(this, 0, fill);
        } catch (PendingIntent.CanceledException ignored) {}
    }

    private void tint(ImageView v) {
        v.setImageTintList(ColorStateList.valueOf(foreground));
    }

    private void setUpToolbar() {
        tint(minimizeButton);
        tint(shareButton);
        tint(menuButton);
        titleView.setTextColor(foreground);
        securityIcon.setImageTintList(ColorStateList.valueOf(foregroundVariant));
        if (showTitle) {
            domainView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            domainView.setTextColor(foregroundVariant);
        } else {
            domainView.setTextColor(foreground);
        }
        minimizeButton.setVisibility(pipSupported ? View.VISIBLE : View.GONE);
        shareButton.setVisibility(shareEnabled ? View.VISIBLE : View.GONE);
        progress.setIndicatorColor(MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary));

        closeButton.setOnClickListener(v -> close());
        minimizeButton.setOnClickListener(v -> minimize());
        shareButton.setOnClickListener(v -> share());
        menuButton.setOnClickListener(v -> showMenu());
        findViewById(R.id.ctTitleArea).setOnClickListener(v -> showSiteInfo());
    }

    // ------------------------------------------------------------------
    // ページ
    // ------------------------------------------------------------------

    private void createTab() {
        tab = new GeckoTabView(this, false);
        promptDelegate = new GeckoPromptDelegate(this, REQUEST_FILE_CHOOSER, null);
        permissionDelegate = new GeckoPermissionDelegate(this, REQUEST_GECKO_PERMISSIONS);
        tab.getGeckoSession().setPromptDelegate(promptDelegate);
        tab.getGeckoSession().setPermissionDelegate(permissionDelegate);
        GeckoTabView.TabSettings s = tab.getSettings();
        s.setJavaScriptEnabled(getSharedPreferences("AppPrefs", MODE_PRIVATE).getBoolean("javaScript", true));
        s.setDesktopMode(false);
        s.setUserAgentString(GeckoSession.getDefaultUserAgent() + UA_SUFFIX);
        tab.setListener(tabListener);
        ((FrameLayout) findViewById(R.id.ctWebContainer)).addView(tab,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        translator = new PageTranslator(this, new PageTranslator.Host() {
            @Nullable
            @Override
            public GeckoTabView getCurrentTab() {
                return tab;
            }

            @Override
            public boolean isInternalUrl(@Nullable String url) {
                return url == null || !(url.startsWith("http://") || url.startsWith("https://"));
            }
        });
    }

    private final GeckoTabView.Listener tabListener = new GeckoTabView.Listener() {
        @Override
        public void onPageStarted(GeckoTabView t, String url) {
            updateUrlViews(url);
            progress.setVisibility(View.VISIBLE);
            progress.setProgressCompat(5, false);
            notifyClient(CustomTabsCallback.NAVIGATION_STARTED);
        }

        @Override
        public void onPageFinished(GeckoTabView t, String url, boolean success) {
            progress.setProgressCompat(100, true);
            handler.postDelayed(() -> {
                if (tab != null && !tab.isLoading()) progress.setVisibility(View.INVISIBLE);
            }, 300);
            loadFavicon(t.getIconUrl(), url);
            notifyClient(success ? CustomTabsCallback.NAVIGATION_FINISHED : CustomTabsCallback.NAVIGATION_FAILED);
        }

        @Override
        public void onProgressChanged(GeckoTabView t, int p) {
            progress.setProgressCompat(Math.max(5, p), true);
        }

        @Override
        public void onTitleChanged(GeckoTabView t, String title) {
            updateTitle(title);
        }

        @Override
        public void onLocationChanged(GeckoTabView t, String url) {
            updateUrlViews(url);
        }

        @Override
        public void onSecurityChanged(GeckoTabView t) {
            updateUrlViews(t.getUrl());
        }

        @Override
        public void onPageInfo(GeckoTabView t, @Nullable String iconUrl) {
            loadFavicon(iconUrl, t.getUrl());
        }

        @Override
        public boolean onExternalUrl(GeckoTabView t, String url) {
            return openExternal(url);
        }

        @Override
        public boolean onStoreRedirect(GeckoTabView t, String pageUrl, String packageName) {
            return AppLinks.openInstalledApp(CustomTabActivity.this, pageUrl, packageName);
        }

        @Nullable
        @Override
        public GeckoSession onNewWindow(GeckoTabView t, String url) {
            // 簡易ブラウザにタブは無いので、新しいウィンドウは同じ画面で開く
            t.loadUrl(url);
            return null;
        }

        @Override
        public void onCloseRequest(GeckoTabView t) {
            close();
        }

        @Override
        public void onFullScreen(GeckoTabView t, boolean full) {
            setFullScreen(full);
        }

        @Override
        public void onDownload(GeckoTabView t, WebResponse response) {
            if (AppDownloader.needsStoragePermission(CustomTabActivity.this)) {
                Toast.makeText(CustomTabActivity.this, Lang.t("ダウンロードは AirSky で開いてから行ってください"), Toast.LENGTH_SHORT).show();
                return;
            }
            if (t.hasPaintedContent()) {
                AppDownloader.saveResponse(CustomTabActivity.this, response);
                return;
            }
            // 開いた URL がダウンロードだけでページが無い: 白紙の代わりにダウンロードの様子を出す
            showDownloadPanel();
            AppDownloader.saveResponse(CustomTabActivity.this, response, new AppDownloader.Observer() {
                @Override
                public void onStarted(long id) {
                    watchDownload(id);
                }

                @Override
                public void onDiscarded() {
                    showDownloadEnded(DownloadOrbView.STATE_FAILED, Lang.t("キャンセルしました"), null);
                }
            });
        }

        @Override
        public void onTranslationStateChanged(GeckoTabView t,
                                              TranslationsController.SessionTranslation.TranslationState state) {
            translator.onStateChanged(t, state);
        }

        @Override
        public void onTranslationOffered(GeckoTabView t) {
            translator.onOffered(t);
        }

        @Override
        public void onTranslationExpected(GeckoTabView t) {
            translator.onExpected(t);
        }

        @Override
        public void onNoTranslatePage(GeckoTabView t) {
            translator.onNoTranslatePage(t);
        }

        @Override
        public void onContextMenu(GeckoTabView t, GeckoSession.ContentDelegate.ContextElement element) {
            if (element.linkUri == null) return;
            // リンクの文字(画像のリンクなら代替テキスト・タイトル)
            String text = element.linkText;
            if (text == null || text.trim().isEmpty()) text = element.altText;
            if (text == null || text.trim().isEmpty()) text = element.title;
            showLinkMenu(element.linkUri, text != null ? text.trim() : null);
        }
    };

    // ------------------------------------------------------------------
    // ダウンロードだけのページ
    // ------------------------------------------------------------------

    @Nullable private View dlPanel;
    private DownloadOrbView dlOrb;
    private TextView dlState, dlName, dlDetail;
    private ImageView dlTypeIcon;
    private LinearProgressIndicator dlBar;
    private com.google.android.material.button.MaterialButton dlPrimary, dlSecondary;
    private long dlId;
    private final Runnable dlTick = this::updateDownloadPanel;

    private void showDownloadPanel() {
        handler.removeCallbacks(dlTick);
        dlId = 0;
        if (dlPanel == null) {
            FrameLayout container = findViewById(R.id.ctWebContainer);
            dlPanel = getLayoutInflater().inflate(R.layout.ct_download_panel, container, false);
            container.addView(dlPanel);
            dlOrb = dlPanel.findViewById(R.id.ctDlOrb);
            dlState = dlPanel.findViewById(R.id.ctDlState);
            dlName = dlPanel.findViewById(R.id.ctDlName);
            dlDetail = dlPanel.findViewById(R.id.ctDlDetail);
            dlTypeIcon = dlPanel.findViewById(R.id.ctDlTypeIcon);
            dlBar = dlPanel.findViewById(R.id.ctDlBar);
            dlPrimary = dlPanel.findViewById(R.id.ctDlPrimary);
            dlSecondary = dlPanel.findViewById(R.id.ctDlSecondary);
            styleDownloadPanel();
        }
        dlOrb.setState(DownloadOrbView.STATE_RUNNING);
        dlOrb.setProgress(-1f);
        dlOrb.setIcon(R.drawable.ic_dl_file);
        dlTypeIcon.setImageResource(R.drawable.ic_dl_file);
        setChipColor(downloadAccent());
        dlState.setText(Lang.t("準備しています"));
        dlName.setText(domainView.getText());
        dlDetail.setText(Lang.t("ダウンロードを準備しています..."));
        dlBar.setIndeterminate(true);
        dlBar.setVisibility(View.VISIBLE);
        dlPrimary.setVisibility(View.GONE);
        dlSecondary.setVisibility(View.GONE);

        dlPanel.setVisibility(View.VISIBLE);
        dlPanel.setAlpha(0f);
        dlPanel.animate().alpha(1f).setDuration(260).start();
        dlOrb.setScaleX(0.7f);
        dlOrb.setScaleY(0.7f);
        dlOrb.animate().scaleX(1f).scaleY(1f).setDuration(520).setInterpolator(EMPHASIZED).start();
        View card = dlPanel.findViewById(R.id.ctDlCard);
        card.setTranslationY(40f * getResources().getDisplayMetrics().density);
        card.setAlpha(0f);
        card.animate().translationY(0f).alpha(1f).setStartDelay(120).setDuration(480)
                .setInterpolator(EMPHASIZED).start();
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** スタートページと同じ空色。 */
    private int downloadAccent() {
        return isNight() ? 0xFF7FCBF5 : 0xFF1C74B5;
    }

    /** 空の色をうっすら敷いた背景、角の丸いカード、状態のチップ。色はテーマに合わせる。 */
    private void styleDownloadPanel() {
        float d = getResources().getDisplayMetrics().density;
        int accent = downloadAccent();
        int surface = MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurface);
        int sky = isNight() ? 0xFF0D2132 : 0xFFDDEFFB;
        dlPanel.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{sky, surface, surface}));

        GradientDrawable card = new GradientDrawable();
        card.setCornerRadius(24 * d);
        card.setColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurfaceContainerHigh));
        card.setStroke(Math.round(d), ColorUtils.setAlphaComponent(accent, 0x33));
        dlPanel.findViewById(R.id.ctDlCard).setBackground(card);

        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(12 * d);
        iconBg.setColor(ColorUtils.setAlphaComponent(accent, 0x24));
        dlTypeIcon.setBackground(iconBg);
        dlTypeIcon.setImageTintList(ColorStateList.valueOf(accent));

        dlOrb.setColors(accent, 0xFFD9534F);
        dlBar.setIndicatorColor(accent);
        dlBar.setTrackColor(ColorUtils.setAlphaComponent(accent, 0x26));
    }

    private void setChipColor(int color) {
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(100 * getResources().getDisplayMetrics().density);
        chip.setColor(ColorUtils.setAlphaComponent(color, 0x24));
        dlState.setBackground(chip);
        dlState.setTextColor(color);
    }

    private void watchDownload(long id) {
        dlId = id;
        handler.removeCallbacks(dlTick);
        updateDownloadPanel();
    }

    private void updateDownloadPanel() {
        if (dlPanel == null || dlId == 0) return;
        AppDownloader.Progress p = AppDownloader.getProgress(dlId);
        if (p == null) return;
        String name = p.fileName != null ? p.fileName : "";
        dlName.setText(name);
        int typeIcon = typeIconFor(p.mimeType != null ? p.mimeType : AppDownloader.guessMime(name));
        dlTypeIcon.setImageResource(typeIcon);

        if (p.status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
            showDownloadEnded(DownloadOrbView.STATE_DONE, Lang.t("ダウンロード完了"), p);
            return;
        }
        if (p.status == android.app.DownloadManager.STATUS_FAILED) {
            showDownloadEnded(DownloadOrbView.STATE_FAILED, p.threat != null ? Lang.t("ウイルスを検出しました")
                    : p.cancelled ? Lang.t("キャンセルしました") : Lang.t("ダウンロードに失敗しました"), p);
            return;
        }
        dlOrb.setIcon(typeIcon);
        dlState.setText(Lang.t("ダウンロード中"));
        if (p.total > 0) {
            float f = Math.min(1f, p.downloaded / (float) p.total);
            dlOrb.setProgress(f);
            if (dlBar.isIndeterminate()) {
                // 不確定表示のままでは切り替えられないので、一度隠してから切り替える
                dlBar.setVisibility(View.INVISIBLE);
                dlBar.setIndeterminate(false);
                dlBar.setVisibility(View.VISIBLE);
            }
            dlBar.setProgressCompat(Math.round(f * 1000), true);
            dlDetail.setText(Math.round(f * 100) + "% • " + AppDownloader.formatSizeDetailed(p.downloaded)
                    + " / " + AppDownloader.formatSizeDetailed(p.total));
        } else {
            dlOrb.setProgress(-1f);
            dlDetail.setText(AppDownloader.formatSizeDetailed(p.downloaded));
        }
        dlSecondary.setVisibility(View.GONE);
        dlPrimary.setVisibility(View.VISIBLE);
        dlPrimary.setText(Lang.t("キャンセル"));
        dlPrimary.setOnClickListener(v -> AppDownloader.cancel(dlId));
        handler.postDelayed(dlTick, 200);
    }

    private void showDownloadEnded(int state, @NonNull String label, @Nullable AppDownloader.Progress p) {
        if (dlPanel == null || dlPanel.getVisibility() != View.VISIBLE) return;
        handler.removeCallbacks(dlTick);
        dlOrb.setState(state);
        dlState.setText(label);
        dlBar.setVisibility(View.GONE);
        boolean done = state == DownloadOrbView.STATE_DONE;
        setChipColor(done ? 0xFF2E9E5B : 0xFFD9534F);
        if (p == null) {
            dlName.setText(Lang.t("ダウンロードをキャンセルしました"));
            dlDetail.setText(Lang.t("ファイルは保存されませんでした"));
        } else if (done) {
            dlDetail.setText(AppDownloader.formatSizeDetailed(p.total) + " • " + Lang.t("保存しました"));
        } else {
            dlDetail.setText(p.threat != null ? p.threat : Lang.t("ファイルは保存されませんでした"));
        }

        dlSecondary.setVisibility(View.VISIBLE);
        dlSecondary.setText(done ? Lang.t("ダウンロード一覧") : Lang.t("閉じる"));
        dlSecondary.setOnClickListener(v -> {
            if (done) startActivity(new Intent(this, DownloadListActivity.class));
            else close();
        });
        if (done) {
            dlPrimary.setVisibility(View.VISIBLE);
            dlPrimary.setText(Lang.t("開く"));
            dlPrimary.setOnClickListener(v -> {
                Intent open = AppDownloader.openFileIntent(getApplicationContext(), p);
                try {
                    startActivity(open != null ? open : new Intent(this, DownloadListActivity.class));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, Lang.t("このファイルを開けるアプリがありません"), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            dlPrimary.setVisibility(View.GONE);
        }
    }

    @DrawableRes
    private static int typeIconFor(@Nullable String mime) {
        if (mime == null) return R.drawable.ic_dl_file;
        if (mime.startsWith("image/")) return R.drawable.ic_dl_image;
        if (mime.startsWith("video/")) return R.drawable.ic_dl_video;
        if (mime.startsWith("audio/")) return R.drawable.ic_dl_audio;
        return R.drawable.ic_dl_file;
    }

    private void hideDownloadPanel() {
        handler.removeCallbacks(dlTick);
        dlId = 0;
        if (dlPanel != null) dlPanel.setVisibility(View.GONE);
    }

    @Override
    public void onWindowInsetsChanged() {
        // ツールバーは通常の(システムバーを避ける)レイアウトなので、追加の処理は無い
    }

    private void updateTitle(@Nullable String title) {
        String t = title != null && !title.trim().isEmpty() ? title.trim() : null;
        titleView.setText(t != null ? t : "");
        // 題名がまだ無い間はドメインだけを(真ん中に)出す
        titleView.setVisibility(showTitle && t != null ? View.VISIBLE : View.GONE);
        pipTitle.setText(t != null ? t : domainView.getText());
    }

    private void updateUrlViews(@Nullable String url) {
        String host = hostOf(url);
        String shown = host != null ? stripWww(host) : (url != null ? url : "");
        domainView.setText(shown);
        pipDomain.setText(shown);
        boolean insecure = url != null && url.startsWith("http://");
        securityIcon.setVisibility(insecure ? View.VISIBLE : View.GONE);
        if (tab != null) updateTitle(tab.getTitle());
        if (host != null && !host.equals(faviconForHost)) {
            favicon = null;
            pipIcon.setImageResource(R.mipmap.ic_aozora);
        }
    }

    @Nullable
    private static String hostOf(@Nullable String url) {
        if (url == null) return null;
        try {
            String h = Uri.parse(url).getHost();
            return h == null || h.isEmpty() ? null : h;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String stripWww(String h) {
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    /** ファビコンを読み込む(最小化中のカード・サイト情報・ホーム画面のアイコンに使う)。 */
    private void loadFavicon(@Nullable String iconUrl, @Nullable String pageUrl) {
        String host = hostOf(pageUrl);
        if (host == null || (host.equals(faviconForHost) && favicon != null)) return;
        String src = iconUrl != null && (iconUrl.startsWith("https://") || iconUrl.startsWith("http://"))
                ? iconUrl : Uri.parse(pageUrl).getScheme() + "://" + host + "/favicon.ico";
        ICON_LOADER.execute(() -> {
            Bitmap bmp = null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(src).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                try (InputStream in = conn.getInputStream()) {
                    bmp = BitmapFactory.decodeStream(in);
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            Bitmap result = bmp;
            if (result == null) return;
            handler.post(() -> {
                if (isDestroyed() || !host.equals(hostOf(tab.getUrl()))) return;
                favicon = result;
                faviconForHost = host;
                pipIcon.setImageBitmap(result);
            });
        });
    }

    // ------------------------------------------------------------------
    // 外部アプリ(サインイン後のリダイレクトなど)
    // ------------------------------------------------------------------

    /**
     * http/https 以外のリンク。サインインの完了で呼び出し元アプリのスキーム(myapp://callback 等)へ
     * 戻された時は、そのアプリを開いて簡易ブラウザを閉じる。
     */
    private boolean openExternal(@NonNull String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        try {
            if (lower.startsWith("tel:")) {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(url)));
                return true;
            }
            if (lower.startsWith("mailto:")) {
                startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse(url)));
                return true;
            }
            Intent intent;
            if (lower.startsWith("intent:") || lower.startsWith("android-app:")) {
                intent = Intent.parseUri(url, lower.startsWith("intent:")
                        ? Intent.URI_INTENT_SCHEME : Intent.URI_ANDROID_APP_SCHEME);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            String fallback = intent.getStringExtra("browser_fallback_url");
            if (fallback != null && !(fallback.startsWith("https://") || fallback.startsWith("http://"))) {
                fallback = null;
            }
            if (getPackageName().equals(intent.getPackage())) {
                if (fallback != null) tab.loadUrl(fallback);
                return true;
            }
            // ページから任意のコンポーネントを直接起動させない(Chrome と同じ)
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

            ResolveInfo target = getPackageManager().resolveActivity(intent, 0);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                if (fallback != null) {
                    tab.loadUrl(fallback);
                } else {
                    Toast.makeText(this, Lang.t("このリンクを開けるアプリが見つかりません"), Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            // 呼び出し元アプリへ戻された(サインイン完了など): 簡易ブラウザの役目は終わり
            if (target != null && target.activityInfo != null && callerPackage != null
                    && callerPackage.equals(target.activityInfo.packageName)) {
                finish();
            }
            return true;
        } catch (java.net.URISyntaxException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // ツールバーのボタン
    // ------------------------------------------------------------------

    private void close() {
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }

    private void share() {
        String url = tab.getUrl();
        if (url == null) return;
        ShareSheet.show(this, url, tab.getTitle(), favicon, false);
    }

    /** 小さなカード(ピクチャー イン ピクチャー)にして、呼び出し元アプリを使えるようにする。 */
    private void minimize() {
        if (!pipSupported) return;
        try {
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(3, 2))
                    .build();
            enterPictureInPictureMode(params);
        } catch (IllegalStateException | IllegalArgumentException e) {
            Toast.makeText(this, Lang.t("最小化できません"), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPip, newConfig);
        if (menuPopup != null) menuPopup.dismiss();
        pipView.setVisibility(inPip ? View.VISIBLE : View.GONE);
        content.setVisibility(inPip ? View.INVISIBLE : View.VISIBLE);
        if (!inPip) {
            if (getLifecycle().getCurrentState() == Lifecycle.State.CREATED) {
                // 小さなカードの「×」で閉じられた
                finish();
            } else {
                // 元の大きさに戻すと続けて onResume、「×」で閉じると続けて onStop が来る(Android 9 など)
                leftPip = true;
            }
        }
    }

    private void showSiteInfo() {
        String url = tab.getUrl();
        if (url == null || hostOf(url) == null) return;
        SiteInfoSheet.show(this, tab, url, favicon, false, new SiteInfoSheet.Host() {
            @Override
            public void showCertificateDialog() {
                showCertificate(tab);
            }

            @Override
            public void openInNewTab(@NonNull String target) {
                openInAozora(target);
            }
        });
    }

    private void showCertificate(@NonNull GeckoTabView t) {
        X509Certificate cert = t.getX509Certificate();
        if (cert == null) {
            Toast.makeText(this, Lang.t("証明書の情報がありません"), Toast.LENGTH_SHORT).show();
            return;
        }
        DateFormat df = DateFormat.getDateInstance(DateFormat.LONG, Locale.JAPAN);
        String msg = Lang.t("発行先\n") + cert.getSubjectX500Principal().getName()
                + Lang.t("\n\n発行者\n") + cert.getIssuerX500Principal().getName()
                + Lang.t("\n\n有効期間\n") + df.format(cert.getNotBefore()) + Lang.t(" 〜 ") + df.format(cert.getNotAfter());
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("証明書"))
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    private void openInAozora(@NonNull String url) {
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url), this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(open);
    }

    // ------------------------------------------------------------------
    // リンクの長押しメニュー(メインのブラウザと同じ見た目)
    // ------------------------------------------------------------------

    private void showLinkMenu(@NonNull String linkUrl, @Nullable String linkText) {
        boolean hasText = linkText != null && !linkText.isEmpty();
        ArrayList<LinkMenuSheet.Item> items = new ArrayList<>();
        items.add(new LinkMenuSheet.Item(R.drawable.ic_ct_open_in_new, Lang.t("ブラウザで開く"),
                () -> openInAozora(linkUrl)));
        items.add(new LinkMenuSheet.Item(R.drawable.ic_lm_preview, Lang.t("ページをプレビュー"),
                () -> showPagePreview(linkUrl)));
        items.add(new LinkMenuSheet.Item(R.drawable.ic_lm_link, Lang.t("リンクアドレスをコピー"), () -> {
            copyText(Lang.t("リンク"), linkUrl);
            Toast.makeText(this, Lang.t("リンクをコピーしました"), Toast.LENGTH_SHORT).show();
        }));
        if (hasText) {
            items.add(new LinkMenuSheet.Item(R.drawable.ic_lm_text, Lang.t("リンクテキストをコピー"), () -> {
                copyText(Lang.t("リンクテキスト"), linkText);
                Toast.makeText(this, Lang.t("リンクテキストをコピーしました"), Toast.LENGTH_SHORT).show();
            }));
        }
        items.add(new LinkMenuSheet.Item(R.drawable.ic_lm_download, Lang.t("リンクをダウンロード"),
                () -> downloadLink(linkUrl)));
        items.add(new LinkMenuSheet.Item(R.drawable.ic_bm_reading, Lang.t("リーディング リストに追加"),
                () -> addLinkToReadingList(linkUrl, linkText)));
        items.add(new LinkMenuSheet.Item(R.drawable.ic_pm_share, Lang.t("リンクを共有"), () -> {
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, linkUrl);
            if (hasText) send.putExtra(Intent.EXTRA_SUBJECT, linkText);
            startActivity(Intent.createChooser(send, Lang.t("リンクを共有")));
        }));
        LinkMenuSheet.show(this, linkUrl, faviconFor(linkUrl), items);
    }

    private void copyText(@NonNull String label, @NonNull String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text));
    }

    /** 今のページと同じサイトなら、そのファビコン。 */
    @Nullable
    private Bitmap faviconFor(@NonNull String url) {
        String host = hostOf(url);
        return host != null && host.equals(faviconForHost) ? favicon : null;
    }

    /** 「ページをプレビュー」: 簡易ブラウザを離れずにリンク先をシートの中で見る。 */
    private void showPagePreview(@NonNull String url) {
        PagePreviewSheet.show(this, url, false, null, new PagePreviewSheet.Host() {
            @Override
            public void showCertificateDialog() {
                showCertificate(tab);
            }

            @Override
            public void openInNewTab(@NonNull String target) {
                openInAozora(target);
            }

            @Nullable
            @Override
            public String buildErrorPage(@Nullable String failing, @NonNull WebRequestError error) {
                // 簡易ブラウザには独自のエラーページが無いので、Gecko の既定のものを出す
                return null;
            }

            @Override
            public void showCertificateDialogFor(@NonNull GeckoTabView t) {
                showCertificate(t);
            }

            @Nullable
            @Override
            public Bitmap faviconFor(@NonNull String target) {
                return CustomTabActivity.this.faviconFor(target);
            }
        });
    }

    /** 「リンクをダウンロード」。 */
    private void downloadLink(@NonNull String url) {
        if (AppDownloader.needsStoragePermission(this)) {
            Toast.makeText(this, Lang.t("ダウンロードは AirSky で開いてから行ってください"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!AppDownloader.downloadUrl(this, url, tab.getSettings().getUserAgentString(), false, false)) {
            // blob: など DownloadManager で扱えない URL はページに読み込ませ、Gecko のダウンロードに任せる
            tab.loadUrl(url);
        }
    }

    /** 「リーディング リストに追加」。 */
    private void addLinkToReadingList(@NonNull String url, @Nullable String linkText) {
        if (hostOf(url) == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            Toast.makeText(this, Lang.t("このページは追加できません"), Toast.LENGTH_SHORT).show();
            return;
        }
        BookmarkStore store = BookmarkStore.get(this);
        if (store.findReading(url) != null) {
            Toast.makeText(this, Lang.t("すでにリーディングリストにあります"), Toast.LENGTH_SHORT).show();
            return;
        }
        String title = linkText != null && !linkText.isEmpty() ? linkText : LinkMenuSheet.siteName(url);
        store.addBookmark(BookmarkStore.ROOT_READING, title, url, null);
        Toast.makeText(this, Lang.t("リーディングリストに追加しました"), Toast.LENGTH_SHORT).show();
    }

    private void toggleBookmark() {
        String url = tab.getUrl();
        if (url == null || hostOf(url) == null) return;
        BookmarkStore store = BookmarkStore.get(this);
        BookmarkStore.Node existing = store.findBookmark(url);
        if (existing != null) {
            store.delete(existing.id);
            Toast.makeText(this, Lang.t("ブックマークを削除しました"), Toast.LENGTH_SHORT).show();
        } else {
            String title = tab.getTitle() != null && !tab.getTitle().isEmpty() ? tab.getTitle() : url;
            long folder = store.lastFolder();
            store.addBookmark(folder, title, url, null);
            Toast.makeText(this, Lang.t("「") + BookmarkViews.folderName(this, folder) + Lang.t("」に保存しました"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadPage() {
        String url = tab.getUrl();
        if (url == null || hostOf(url) == null) return;
        if (AppDownloader.needsStoragePermission(this)) {
            Toast.makeText(this, Lang.t("ダウンロードは AirSky で開いてから行ってください"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!AppDownloader.downloadUrl(this, url, tab.getSettings().getUserAgentString(), false, false)) {
            Toast.makeText(this, Lang.t("このページはダウンロードできません"), Toast.LENGTH_SHORT).show();
        }
    }

    private void addToHomeScreen() {
        String url = tab.getUrl();
        String host = hostOf(url);
        if (host == null) return;
        if (!androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Toast.makeText(this, Lang.t("このホームアプリはショートカットの追加に対応していません"), Toast.LENGTH_SHORT).show();
            return;
        }
        String title = tab.getTitle();
        String label = title != null && !title.isEmpty() ? title : stripWww(host);
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url), this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
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

    private static Bitmap homeScreenIcon(@NonNull Bitmap fav) {
        int size = 216;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        c.drawColor(Color.WHITE);
        int icon = 88;
        int left = (size - icon) / 2;
        android.graphics.Paint paint = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(fav, null, new android.graphics.Rect(left, left, left + icon, left + icon), paint);
        return out;
    }

    private void setDesktopMode(boolean desktop) {
        tab.getSettings().setDesktopMode(desktop);
        tab.getSettings().setUserAgentString(desktop ? null : GeckoSession.getDefaultUserAgent() + UA_SUFFIX);
        tab.reload();
    }

    private void setFullScreen(boolean full) {
        fullScreen = full;
        toolbar.setVisibility(full ? View.GONE : View.VISIBLE);
        divider.setVisibility(full ? View.GONE : View.VISIBLE);
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (full) {
            c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            c.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            c.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    // ------------------------------------------------------------------
    // ページ内検索
    // ------------------------------------------------------------------

    private void setUpFindBar() {
        findQuery.setTextColor(foreground);
        findQuery.setHintTextColor(foregroundVariant);
        findCount.setTextColor(foregroundVariant);
        tint(findViewById(R.id.ctFindClose));
        tint(findViewById(R.id.ctFindPrev));
        tint(findViewById(R.id.ctFindNext));
        findViewById(R.id.ctFindClose).setOnClickListener(v -> hideFindBar());
        findViewById(R.id.ctFindPrev).setOnClickListener(v -> tab.findNext(false));
        findViewById(R.id.ctFindNext).setOnClickListener(v -> tab.findNext(true));
        findQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString();
                if (q.isEmpty()) {
                    tab.clearMatches();
                    findCount.setText("");
                } else {
                    tab.findAllAsync(q);
                }
            }
        });
        findQuery.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH
                    || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                tab.findNext(true);
                return true;
            }
            return false;
        });
    }

    private void showFindBar() {
        tab.setFindListener((active, total) ->
                findCount.setText(total > 0 ? (active + 1) + "/" + total : "0/0"));
        bar.setVisibility(View.GONE);
        findBar.setVisibility(View.VISIBLE);
        findBar.setAlpha(0f);
        findBar.animate().alpha(1f).setDuration(180).start();
        findQuery.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(findQuery, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideFindBar() {
        tab.clearMatches();
        tab.setFindListener(null);
        findQuery.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(findQuery.getWindowToken(), 0);
        findBar.setVisibility(View.GONE);
        bar.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------
    // メニュー(右上の︙)
    // ------------------------------------------------------------------

    private void showMenu() {
        if (menuPopup != null && menuPopup.isShowing()) return;
        float dp = getResources().getDisplayMetrics().density;
        int surface = MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurfaceContainerHigh);
        int onSurface = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface);
        int onVariant = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int circle = MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurfaceContainerLowest);
        int outline = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOutlineVariant);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(surface);
        bg.setCornerRadius(28 * dp);
        panel.setBackground(bg);
        panel.setClipToOutline(true);
        panel.setElevation(8 * dp);

        // 上段: 戻る・進む・ブックマーク・ダウンロード・再読み込み
        LinearLayout icons = new LinearLayout(this);
        icons.setOrientation(LinearLayout.HORIZONTAL);
        icons.setGravity(Gravity.CENTER_VERTICAL);
        icons.setPadding(Math.round(12 * dp), Math.round(12 * dp), Math.round(12 * dp), Math.round(8 * dp));
        String url = tab.getUrl();
        boolean web = hostOf(url) != null;
        boolean bookmarked = web && BookmarkStore.get(this).findBookmark(url) != null;
        icons.addView(iconButton(R.drawable.ic_ct_back, Lang.t("戻る"), tab.canGoBack(), circle, onSurface, () -> tab.goBack()));
        icons.addView(iconButton(R.drawable.ic_ct_forward, Lang.t("進む"), tab.canGoForward(), circle, onSurface, () -> tab.goForward()));
        icons.addView(iconButton(bookmarked ? R.drawable.ic_ct_star_filled : R.drawable.ic_ct_star,
                bookmarked ? Lang.t("ブックマークを削除") : Lang.t("ブックマークに追加"), web, circle, onSurface, this::toggleBookmark));
        icons.addView(iconButton(R.drawable.ic_ct_download, Lang.t("ダウンロード"), web, circle, onSurface, this::downloadPage));
        boolean loading = tab.isLoading();
        icons.addView(iconButton(loading ? R.drawable.ic_ct_stop : R.drawable.ic_ct_reload,
                loading ? Lang.t("読み込みを中止") : Lang.t("再読み込み"), true, circle, onSurface,
                () -> {
                    if (tab.isLoading()) tab.stopLoading();
                    else tab.reload();
                }));
        panel.addView(icons);

        // 呼び出し元アプリのメニュー項目
        for (MenuEntry e : clientMenuItems) {
            panel.addView(menuRow(e.title, onSurface, null, null, () -> sendToClient(e.intent)));
        }

        TextView openIn = menuRow(Lang.t("AirSky で開く"), onSurface, R.drawable.ic_ct_open_in_new, null, () -> {
            String u = tab.getUrl();
            if (u != null) {
                openInAozora(u);
                finish();
            }
        });
        panel.addView(openIn);
        panel.addView(menuRow(Lang.t("ページ内検索"), onSurface, null, null, this::showFindBar));
        if (web) panel.addView(menuRow(Lang.t("ホーム画面に追加"), onSurface, null, null, this::addToHomeScreen));
        boolean desktop = tab.getSettings().isDesktopMode();
        panel.addView(menuRow(Lang.t("PC 版サイト"), onSurface, null, desktop, () -> setDesktopMode(!desktop)));
        if (web) panel.addView(menuRow(Lang.t("翻訳..."), onSurface, null, null, () -> translator.translateCurrentPage()));
        if (web) panel.addView(menuRow(Lang.t("サイトの設定"), onSurface, null, null, this::showSiteInfo));

        // 下段: 「Aozora で実行中」
        View line = new View(this);
        line.setBackgroundColor(outline);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(dp)));
        llp.topMargin = Math.round(4 * dp);
        panel.addView(line, llp);
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(Math.round(20 * dp), Math.round(14 * dp), Math.round(20 * dp), Math.round(18 * dp));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_aozora);
        footer.addView(logo, new LinearLayout.LayoutParams(Math.round(22 * dp), Math.round(22 * dp)));
        TextView running = new TextView(this);
        running.setText(Lang.t("AirSky で実行中"));
        running.setTextColor(onVariant);
        running.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMarginStart(Math.round(12 * dp));
        footer.addView(running, rlp);
        panel.addView(footer);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(screenW - Math.round(24 * dp), Math.round(320 * dp));
        FrameLayout holder = new FrameLayout(this);
        int pad = Math.round(8 * dp);
        holder.setPadding(pad, pad, pad, pad);
        holder.addView(panel, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));

        PopupWindow popup = new PopupWindow(holder, width + pad * 2, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(0);
        popup.setOnDismissListener(() -> menuPopup = null);
        menuPopup = popup;
        int[] loc = new int[2];
        toolbar.getLocationInWindow(loc);
        popup.showAtLocation(root, Gravity.TOP | Gravity.END, Math.round(4 * dp), loc[1] + Math.round(4 * dp));

        // 右上から広がって出てくる
        panel.setPivotX(width);
        panel.setPivotY(0);
        panel.setScaleX(0.85f);
        panel.setScaleY(0.6f);
        panel.setAlpha(0f);
        panel.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260).setInterpolator(EMPHASIZED).start();
    }

    private View iconButton(@DrawableRes int icon, String desc, boolean enabled, int bgColor, int fg, Runnable action) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setImageTintList(ColorStateList.valueOf(fg));
        b.setContentDescription(desc);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(bgColor);
        b.setBackground(oval);
        b.setForeground(getDrawable(resolveAttr(android.R.attr.selectableItemBackgroundBorderless)));
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : 0.38f);
        b.setOnClickListener(v -> {
            dismissMenu();
            action.run();
        });
        return wrapCentered(b);
    }

    /** 等間隔に並べるため、ボタンを均等幅の枠の中央に置く。 */
    private View wrapCentered(View button) {
        float dp = getResources().getDisplayMetrics().density;
        FrameLayout cell = new FrameLayout(this);
        int size = Math.round(52 * dp);
        button.setLayoutParams(new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        cell.addView(button);
        cell.setLayoutParams(new LinearLayout.LayoutParams(0, size, 1f));
        return cell;
    }

    private TextView menuRow(String title, int color, @Nullable @DrawableRes Integer trailingIcon,
                             @Nullable Boolean checked, Runnable action) {
        float dp = getResources().getDisplayMetrics().density;
        TextView row = new TextView(this);
        row.setText(title);
        row.setTextColor(color);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinHeight(Math.round(54 * dp));
        row.setPadding(Math.round(20 * dp), 0, Math.round(20 * dp), 0);
        row.setBackground(getDrawable(resolveAttr(android.R.attr.selectableItemBackground)));
        if (trailingIcon != null) {
            android.graphics.drawable.Drawable d = getDrawable(trailingIcon).mutate();
            d.setTint(color);
            int s = Math.round(22 * dp);
            d.setBounds(0, 0, s, s);
            row.setCompoundDrawablesRelative(null, null, d, null);
        } else if (checked != null) {
            CheckBox box = new CheckBox(this);
            box.setChecked(checked);
            box.setButtonTintList(ColorStateList.valueOf(color));
            box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            box.layout(0, 0, box.getMeasuredWidth(), box.getMeasuredHeight());
            android.graphics.drawable.Drawable d = box.getButtonDrawable();
            if (d != null) {
                d = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
                d.setState(checked ? new int[]{android.R.attr.state_checked, android.R.attr.state_enabled}
                        : new int[]{android.R.attr.state_enabled});
                d.setTint(color);
                int s = Math.round(24 * dp);
                d.setBounds(0, 0, s, s);
                row.setCompoundDrawablesRelative(null, null, d, null);
            }
        }
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(v -> {
            dismissMenu();
            action.run();
        });
        return row;
    }

    private void dismissMenu() {
        if (menuPopup != null) menuPopup.dismiss();
    }

    private int resolveAttr(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.resourceId;
    }

    // ------------------------------------------------------------------
    // 呼び出し元への通知・画面の状態
    // ------------------------------------------------------------------

    private void notifyClient(int event) {
        if (sessionToken == null) return;
        try {
            CustomTabsCallback cb = sessionToken.getCallback();
            if (cb != null) cb.onNavigationEvent(event, null);
        } catch (RuntimeException ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        leftPip = false;
        if (tab != null) tab.onResume();
        notifyClient(CustomTabsCallback.TAB_SHOWN);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 最小化中もページは動かしたままにする
        if (tab != null && !isInPictureInPictureMode()) tab.onPause();
        notifyClient(CustomTabsCallback.TAB_HIDDEN);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (leftPip) {
            leftPip = false;
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (promptDelegate != null && promptDelegate.onActivityResult(requestCode, resultCode, data)) return;
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissionDelegate != null) permissionDelegate.onRequestPermissionsResult(requestCode, grantResults);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri data = intent.getData();
        if (data != null && tab != null) {
            setIntent(intent);
            hideDownloadPanel();
            tab.loadUrl(data.toString());
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (menuPopup != null) menuPopup.dismiss();
        if (tab != null) {
            try {
                tab.destroy();
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
