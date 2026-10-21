package com.airsky.browser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslCertificate;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.BasicSelectionActionDelegate;
import org.mozilla.geckoview.GeckoDisplay;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.PanZoomController;
import org.mozilla.geckoview.TranslationsController;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1 タブ = 1 GeckoSession を表示するビュー。
 *
 * 旧実装(android.webkit.WebView)と同じ名前のメソッド(loadUrl / getUrl / getTitle / canGoBack / goBack / reload ...)
 * を持たせているので、タブ管理側のコードは WebView 版からほぼそのまま移行できる。
 * ページのイベントは {@link Listener} でアクティビティへ通知する。
 */
@SuppressLint("ViewConstructor")
public class GeckoTabView extends GeckoView {

    private static final String TAG = "GeckoTabView";
    private static final String NATIVE_APP = "aozora";
    /** ポインターロック(pointerlock.js)用。iframe ごとに接続してくるので別の名前にしている。 */
    private static final String NATIVE_APP_INPUT = "aozora_input";

    /** ページのイベント通知先。必要なものだけ実装すればよい。 */
    public interface Listener {
        default void onPageStarted(GeckoTabView tab, String url) {}
        default void onPageFinished(GeckoTabView tab, String url, boolean success) {}
        default void onProgressChanged(GeckoTabView tab, int progress) {}
        default void onTitleChanged(GeckoTabView tab, String title) {}
        default void onLocationChanged(GeckoTabView tab, String url) {}
        default void onNavigationStateChanged(GeckoTabView tab) {}
        /** 接続の安全性(HTTPS・証明書)の情報が変わった。 */
        default void onSecurityChanged(GeckoTabView tab) {}
        /** コンテンツスクリプトからページ情報(ファビコン URL など)が届いた。 */
        default void onPageInfo(GeckoTabView tab, @Nullable String iconUrl) {}
        /** 画面下端に見えているページの背景色が変わった(ボトムバーの周りの色合わせ用)。 */
        default void onBottomColorChanged(GeckoTabView tab, @Nullable Integer color) {}
        default void onScrollChanged(GeckoTabView tab, int scrollY, int oldScrollY) {}
        /** ページの一番下付近にいるかどうかが変わった({@link #isNearBottom()})。 */
        default void onNearBottomChanged(GeckoTabView tab) {}
        /**
         * リーディングモードを開いた/閉じた({@link #isReaderActive()})。
         * @param error 開けなかった理由("unavailable" = 本文を取り出せないページ)。開閉できたなら null
         */
        default void onReaderModeChanged(GeckoTabView tab, boolean active, @Nullable String error) {}
        /** http/https 以外のスキーム。処理した(=Gecko で読み込まない)なら true。 */
        default boolean onExternalUrl(GeckoTabView tab, String url) { return false; }
        /** 文字選択メニューの「ウェブ検索」: 選んだ言葉を新しいタブで検索する。 */
        default void onSelectionWebSearch(GeckoTabView tab, String text) {}
        /** 文字選択メニューの「翻訳」: 翻訳の検索語をプレビューで調べる。 */
        default void onSelectionTranslate(GeckoTabView tab, String query) {}
        /**
         * 押したリンク {@code pageUrl} が Play ストアのアプリのページへ転送された(「アプリで開く」ボタンなど)。
         * インストール済みのアプリで開いたら true(Play ストアへは進まない)。
         */
        default boolean onStoreRedirect(GeckoTabView tab, String pageUrl, String packageName) { return false; }
        /** window.open など。新しいタブ用の(未オープンの)セッションを返す。開かないなら null。 */
        @Nullable default GeckoSession onNewWindow(GeckoTabView tab, String url) { return null; }
        default void onCloseRequest(GeckoTabView tab) {}
        default void onFullScreen(GeckoTabView tab, boolean fullScreen) {}
        default void onContextMenu(GeckoTabView tab, GeckoSession.ContentDelegate.ContextElement element) {}
        default void onDownload(GeckoTabView tab, WebResponse response) {}
        /** 読み込みエラー時に表示するページの URL(data: など)。既定のエラーページで良ければ null。 */
        @Nullable default String onLoadError(GeckoTabView tab, @Nullable String url, WebRequestError error) { return null; }
        /** 履歴(戻る/進む)を含むタブの状態が変わった。保存のタイミングに使う。 */
        default void onSessionStateChanged(GeckoTabView tab) {}
        /** ページ翻訳の状態(検出した言語・翻訳中の言語・エラー)が変わった。 */
        default void onTranslationStateChanged(GeckoTabView tab,
                                               TranslationsController.SessionTranslation.TranslationState state) {}
        /** ページの言語が表示言語と違うので翻訳を提案できる(Firefox の翻訳アイコンと同じ)。 */
        default void onTranslationOffered(GeckoTabView tab) {}
        /** 「常に翻訳」に設定された言語のページを開いた。 */
        default void onTranslationExpected(GeckoTabView tab) {}

        /** ページが翻訳しないように指定していた(meta name="google" content="notranslate" など)。 */
        default void onNoTranslatePage(GeckoTabView tab) {}
        /** ページがマウスを固定した/解除した(requestPointerLock / exitPointerLock)。 */
        default void onPointerLockChanged(GeckoTabView tab, boolean locked) {}
    }

    /** ページ内検索の結果。activeMatchOrdinal は 0 始まり(WebView.FindListener と同じ)。 */
    public interface FindCallback {
        void onFindResult(int activeMatchOrdinal, int numberOfMatches);
    }

    public interface HtmlCallback {
        void onHtml(@Nullable String html);
    }

    /** GeckoView が画面のルートに付ける、キーボードの高さを Gecko へ伝えるリスナーの名前。 */
    private static final String GECKO_KEYBOARD_INSETS_LISTENER = "KEYBOARD_WINDOW_INSETS_LISTENER";

    /** GeckoView がウィンドウのインセット(キーボードの出入りなど)を受け取ったことを知りたい画面。 */
    public interface WindowInsetsHost {
        void onWindowInsetsChanged();
    }

    private static final String AOZORA_INSETS_LISTENER = "AOZORA_WINDOW_INSETS_LISTENER";

    @Override
    public void onAttachedToWindow() {
        if (externalDisplay != null) {
            // 二画面の描画中は GeckoView に描画先を取りに行かせない(既に二画面側が持っている)
            mSession = null;
            try {
                super.onAttachedToWindow();
            } finally {
                mSession = session;
            }
        } else {
            super.onAttachedToWindow();
        }
        addWindowInsetsListener(AOZORA_INSETS_LISTENER, (v, insets) -> {
            // GeckoView はウィンドウの一番上(DecorView)でインセットを受け取るので、途中の画面部品に
            // 使い切られる前の状態で必ず呼ばれる。配り終わった後に画面に読み直してもらう
            if (getContext() instanceof WindowInsetsHost) {
                WindowInsetsHost host = (WindowInsetsHost) getContext();
                post(host::onWindowInsetsChanged);
            }
            return insets;
        });
        // GeckoView は既定でキーボードの高さを Gecko へ伝え、Gecko は「アプリが表示領域を縮めない」
        // (resizes-visual)前提で、その分だけレイアウト上の表示領域を下へ伸ばす。この画面は
        // DynamicToolbar が表示領域そのものをキーボードの上までに縮めているので、それも伝えると
        // 二重に引かれ、Gemini などの画面下端に固定された入力欄がキーボードの裏に残ってしまう。
        // 高さは表示領域の大きさだけで伝え、Gecko からはキーボードが無いように見せる。
        removeWindowInsetsListener(GECKO_KEYBOARD_INSETS_LISTENER);
    }

    @Override
    public void onDetachedFromWindow() {
        if (externalDisplay != null) {
            // GeckoView は描画先を返そうとするが、二画面の描画中は GeckoView の描画先は空なので触らせない
            mSession = null;
            try {
                super.onDetachedFromWindow();
            } finally {
                mSession = session;
            }
        } else {
            super.onDetachedFromWindow();
        }
    }

    // ---- ページ遷移のフェード ----
    /** 前のページの絵を消していく時間。目立たせず、切り替わりが滑らかに見える程度の短さ。 */
    private static final long PAGE_FADE_MS = 240;
    /** 新しいページが描かれないまま(エラー・ダウンロード等)でも前の絵を消すまでの時間。 */
    private static final long PAGE_FADE_TIMEOUT_MS = 4000;
    /** 遷移の直前に撮った前のページの絵。新しいページが描かれたらフェードで消す。 */
    @Nullable private ImageView pageFadeView;
    /** 今の遷移の番号(古い遷移の撮影結果を捨てるため)。 */
    private int pageFadeSeq;
    /** 今の遷移で新しいページがもう描かれたか。 */
    private boolean pagePaintedSinceStart = true;
    /** 最後に押した(ユーザー操作による)リンクと、その時刻。Play ストアへの転送をアプリで開くために使う。 */
    @Nullable private String appLinkCandidate;
    private long appLinkCandidateAt;
    /** このタブでページの中身が一度でも描かれたか(ダウンロードだけで終わったタブは false のまま)。 */
    private boolean contentEverPainted;
    private final Runnable pageFadeTimeout = () -> fadeOutPageSnapshot(PAGE_FADE_MS);

    private final GeckoEngine engine;
    private final GeckoSession session;
    private final boolean privateMode;
    private final TabSettings settings;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable private Listener listener;

    /** Gecko 側の URL(moz-extension:// を含む)。外へ出すときは getUrl() で変換する。 */
    @Nullable private String engineUrl;
    /** まだ Gecko に渡していない URL(起動直後で拡張機能の準備待ちなど)。アプリ側表現。 */
    @Nullable private String pendingUrl;
    /** 最後に読み込みを指示した URL。Gecko から位置が届くまでの getUrl() に使う。 */
    @Nullable private String requestedUrl;
    @Nullable private String title;
    @Nullable private String iconUrl;
    /** 画面下端に見えているページの背景色。まだ分からなければ null。 */
    @Nullable private Integer bottomColor;
    private boolean canGoBack;
    private boolean canGoForward;
    private boolean loading;
    private int progress;
    private int pageScrollY;
    @Nullable private GeckoSession.ProgressDelegate.SecurityInformation security;
    /** 今のページのトラッキング保護の権限(サードパーティ Cookie の例外に使う)。 */
    @Nullable private GeckoSession.PermissionDelegate.ContentPermission trackingPermission;
    /** ページのメディア(動画・音声)。再生中はアプリが裏に回っても止めない(バックグラウンド再生)。 */
    @Nullable private org.mozilla.geckoview.MediaSession mediaSession;
    private boolean mediaPlaying;
    /** アプリが裏に回った / タブが背面に回った(onPause 済み)。 */
    private boolean hostPaused;
    /** Cookie の例外を反映するために読み直したホスト(読み直しを繰り返さないため)。 */
    @Nullable private String cookieSyncReloadedHost;
    @Nullable private GeckoSession.SessionState sessionState;
    /**
     * 復元中の履歴。Gecko はセッションを開いた直後に about:blank だけの状態を通知してくるので、
     * 復元が反映されるまではそれで保存済みの履歴を上書きしないようにする。
     */
    private boolean restorePending;
    @Nullable private TranslationsController.SessionTranslation.TranslationState translationState;
    @Nullable private WebExtension.Port pagePort;
    private boolean destroyed;

    /** ズームボタンのピンチ操作を流している最中。 */
    private boolean pinchRunning;
    private static final int PINCH_STEPS = 12;
    private static final long PINCH_FRAME_MS = 16;

    private int htmlRequestSeq = 0;
    private final Map<Integer, HtmlCallback> htmlCallbacks = new HashMap<>();

    public GeckoTabView(@NonNull Context context, boolean privateMode) {
        this(context, privateMode, null);
    }

    /**
     * @param existing window.open 等で Gecko から渡された未オープンのセッション。null なら新規作成する。
     */
    public GeckoTabView(@NonNull Context context, boolean privateMode, @Nullable GeckoSession existing) {
        this(context, privateMode, null, existing);
    }

    /**
     * @param contextId Cookie・ストレージ等を分ける区画の ID。シークレットモードでは画面ごとに
     *                  新しい ID を渡し、他のどのタブとも共有しない一時的なプロファイルとして使う。
     *                  null なら通常の区画。
     */
    public GeckoTabView(@NonNull Context context, boolean privateMode, @Nullable String contextId,
                        @Nullable GeckoSession existing) {
        super(context);
        // 注意: ここで setViewBackend() を呼ぶと、生成直後に描画面を作り直すことになり
        // ページが真っ黒のまま描画されない/スクロールしなくなる。既定(SurfaceView)のまま使う。
        this.engine = GeckoEngine.get(context);
        this.privateMode = privateMode;
        // GeckoView は既定で履歴ごとのセッション状態を Activity の保存状態(Binder)に入れる。
        // 大きなページ(data: URL など)が履歴にあると 1MB の上限を超えて
        // TransactionTooLargeException で落ちるので保存しない。タブの状態は MainActivity が
        // ファイル(tab_state_<id>.json)に保存・復元している。
        setSaveEnabled(false);

        if (existing != null) {
            session = existing;
        } else {
            GeckoSessionSettings.Builder b = new GeckoSessionSettings.Builder()
                    .usePrivateMode(privateMode)
                    .useTrackingProtection(true);
            if (contextId != null) b.contextId(contextId);
            GeckoSessionSettings s = b.build();
            session = new GeckoSession(s);
        }
        settings = new TabSettings();
        installDelegates();
        installSelectionMenu(context);
        if (existing == null) {
            session.open(engine.getRuntime());
        }
        setSession(session);

        engine.whenReady(() -> {
            WebExtension ext = engine.getExtension();
            if (ext != null && !destroyed) {
                session.getWebExtensionController().setMessageDelegate(ext, pageMessageDelegate, NATIVE_APP);
                session.getWebExtensionController().setMessageDelegate(ext, inputMessageDelegate, NATIVE_APP_INPUT);
                // Coara Browser 拡張機能(プラグイン)からの依頼
                session.getWebExtensionController().setMessageDelegate(ext,
                        CoaraPlugins.get(getContext()).tabFor(session).delegate, CoaraPlugins.NATIVE_APP);
            }
        });
    }

    public boolean isPrivateMode() {
        return privateMode;
    }

    /** 文字を選択した時のメニューに「翻訳」「ウェブ検索」「共有」を足す。 */
    private void installSelectionMenu(@NonNull Context context) {
        if (session.getSelectionActionDelegate() instanceof PageSelectionDelegate) return;
        Context c = context;
        while (c instanceof android.content.ContextWrapper && !(c instanceof android.app.Activity)) {
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        if (!(c instanceof android.app.Activity)) return;
        session.setSelectionActionDelegate(new PageSelectionDelegate((android.app.Activity) c,
                new PageSelectionDelegate.Callback() {
                    @Override
                    public void onWebSearch(@NonNull String text) {
                        if (listener != null) listener.onSelectionWebSearch(GeckoTabView.this, text);
                    }

                    @Override
                    public void onTranslate(@NonNull String query) {
                        if (listener != null) listener.onSelectionTranslate(GeckoTabView.this, query);
                    }

                    @Nullable
                    @Override
                    public String currentUrl() {
                        // 内蔵ページ(スタートページなど)の URL は共有しない
                        String url = getUrl();
                        return url != null && url.startsWith("http") ? url : null;
                    }
                }));
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @NonNull
    public GeckoSession getGeckoSession() {
        return session;
    }


    // ------------------------------------------------------------------
    // WebView 互換 API
    // ------------------------------------------------------------------

    public void loadUrl(@Nullable String url) {
        if (url == null || destroyed) return;
        pendingUrl = url;
        requestedUrl = url;
        engine.whenReady(() -> {
            if (destroyed || !url.equals(pendingUrl)) return;
            pendingUrl = null;
            session.loadUri(engine.toEngineUrl(url));
        });
    }

    /** window.open で Gecko 側が読み込むタブ用。位置が届くまでの getUrl() の値を設定する。 */
    public void setRequestedUrl(@Nullable String url) {
        requestedUrl = url;
    }

    /** HTML 文字列を直接表示する(エラーページなど)。 */
    public void loadData(@NonNull String html) {
        session.load(new GeckoSession.Loader().data(html, "text/html"));
    }

    /** 現在の URL(内蔵ページは file:///android_asset/... 形式)。 */
    @Nullable
    public String getUrl() {
        if (pendingUrl != null) return pendingUrl;
        if (engineUrl == null || "about:blank".equals(engineUrl)) {
            // 復元直後で Gecko から URL がまだ来ていない場合は保存済み状態から推定する
            String fromState = urlFromState(sessionState);
            if (fromState != null) return engine.toAppUrl(fromState);
            if (requestedUrl != null) return requestedUrl;
            return engineUrl;
        }
        return engine.toAppUrl(engineUrl);
    }

    @Nullable
    public String getTitle() {
        if (title == null || title.isEmpty()) {
            String fromState = titleFromState(sessionState);
            if (fromState != null && !fromState.isEmpty()) return fromState;
        }
        return title;
    }

    @Nullable
    public String getIconUrl() {
        return iconUrl;
    }

    public int getProgress() {
        return progress;
    }

    public boolean isLoading() {
        return loading;
    }

    /** ページの中身が一度でも描かれたか。開いた URL がダウンロードだけだった時は false。 */
    public boolean hasPaintedContent() {
        return contentEverPainted;
    }

    public boolean canGoBack() {
        return canGoBack;
    }

    public boolean canGoForward() {
        return canGoForward;
    }

    public void goBack() {
        session.goBack();
    }

    public void goForward() {
        session.goForward();
    }

    public void reload() {
        session.reload();
    }


    public void stopLoading() {
        session.stop();
    }


    /** View#getScrollY() は final なので別名。ページのスクロール位置(CSS px)。 */
    public int getPageScrollY() {
        return pageScrollY;
    }

    // 今の指の動きを Gecko がどう扱うか(指を置いた時に Gecko から返事が来る)
    private int touchGesture;
    private boolean touchAnswered;
    private int touchHandled;
    private int touchScrollable;
    private int touchOverscroll;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // 前のページの絵が残っている間に触ったら、すぐ消して今のページを操作できるようにする
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && pageFadeView != null) {
            pagePaintedSinceStart = true;
            fadeOutPageSnapshot(90);
        }
        return super.dispatchTouchEvent(event);
    }

    // ---- ページ遷移のフェード ----

    /**
     * 別のページへ移り始めた。今見えているページを撮っておき、新しいページが描かれるまで重ねておく
     * (Gecko は新しいページが描かれるまで前の絵を出したままなので、見た目は変わらない)。
     * 新しいページが描かれた時にこの絵をフェードで消すと、ぱっと切り替わらずに滑らかに移って見える。
     */
    private void beginPageFade(@Nullable String fromUrl) {
        final int seq = ++pageFadeSeq;
        pagePaintedSinceStart = false;
        mainHandler.removeCallbacks(pageFadeTimeout);
        // 最初の読み込みや、画面に出ていないタブでは何もしない
        if (externalDisplay != null || fromUrl == null || fromUrl.equals("about:blank") || !isAttachedToWindow()
                || !isShown() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        try {
            capturePixels().accept(bitmap -> {
                if (bitmap == null) return;
                // 撮れた時には既に新しいページが描かれていた/次の遷移が始まっていたら使わない
                if (seq != pageFadeSeq || pagePaintedSinceStart || destroyed) {
                    bitmap.recycle();
                    return;
                }
                showPageSnapshot(bitmap);
                mainHandler.postDelayed(pageFadeTimeout, PAGE_FADE_TIMEOUT_MS);
            }, e -> { });
        } catch (Exception ignored) {
            // 描画面がまだ無いなど。フェードなしで切り替える
        }
    }

    private void showPageSnapshot(@NonNull Bitmap bitmap) {
        removePageSnapshot();
        ImageView v = new ImageView(getContext());
        v.setScaleType(ImageView.ScaleType.MATRIX); // 左上を合わせ、引き伸ばさない
        v.setImageBitmap(bitmap);
        v.setClickable(false);
        v.setFocusable(false);
        v.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(v, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageFadeView = v;
    }

    private void fadeOutPageSnapshot(long duration) {
        mainHandler.removeCallbacks(pageFadeTimeout);
        final ImageView v = pageFadeView;
        if (v == null) return;
        pageFadeView = null;
        v.animate().cancel();
        v.animate().alpha(0f).setDuration(duration)
                .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
                .withEndAction(() -> discardSnapshotView(v))
                .start();
    }

    private void removePageSnapshot() {
        mainHandler.removeCallbacks(pageFadeTimeout);
        ImageView v = pageFadeView;
        pageFadeView = null;
        if (v != null) {
            v.animate().cancel();
            discardSnapshotView(v);
        }
    }

    private void discardSnapshotView(@NonNull ImageView v) {
        removeView(v);
        android.graphics.drawable.Drawable d = v.getDrawable();
        v.setImageDrawable(null);
        if (d instanceof android.graphics.drawable.BitmapDrawable) {
            Bitmap b = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
            if (b != null && !b.isRecycled()) b.recycle();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event);
        // 指を置いた時だけ、ページがこのタッチを使うか・どちらへスクロールできるかを聞いておく
        final int gesture = ++touchGesture;
        touchAnswered = false;
        onTouchEventForDetailResult(event).accept(detail -> {
            if (gesture != touchGesture || detail == null) return;
            touchHandled = detail.handledResult();
            touchScrollable = detail.scrollableDirections();
            touchOverscroll = detail.overscrollDirections();
            touchAnswered = true;
        });
        return true;
    }

    /** 指を置いた回数。ページがこのタッチを受け取ったかを、前後の値を比べて調べる用。 */
    public int getTouchGesture() {
        return touchGesture;
    }

    /**
     * 今のタッチをページ自身が使うか(地図・ゲームなど preventDefault するページ)。
     * null はまだ分からない(Gecko の返事待ち)。
     */
    @Nullable
    public Boolean isTouchHandledByContent() {
        if (!touchAnswered) return null;
        return touchHandled == PanZoomController.INPUT_RESULT_HANDLED_CONTENT
                || touchHandled == PanZoomController.INPUT_RESULT_IGNORED;
    }

    /**
     * 今のタッチで、触れた所(ページ全体か中の枠)がその向きへまだスクロールできるか。
     * @param down 下へ(ページの続きの方へ)スクロールする向き
     */
    public boolean touchCanScroll(boolean down) {
        if (!touchAnswered || touchHandled == PanZoomController.INPUT_RESULT_UNHANDLED) return false;
        int flag = down ? PanZoomController.SCROLLABLE_FLAG_BOTTOM : PanZoomController.SCROLLABLE_FLAG_TOP;
        return (touchScrollable & flag) != 0;
    }

    /**
     * 今の指の動きで「下へ引っ張って更新」を始めてよいか。null はまだ分からない(Gecko の返事待ち)。
     * ページ自身がタッチを使う(地図など)・上へスクロールできる(中の枠や拡大中)時は false。
     */
    @Nullable
    public Boolean canPullToRefresh() {
        if (!touchAnswered) return null;
        if (pageScrollY > 0) return false;
        switch (touchHandled) {
            case PanZoomController.INPUT_RESULT_HANDLED_CONTENT:
            case PanZoomController.INPUT_RESULT_IGNORED:
                return false;
            case PanZoomController.INPUT_RESULT_UNHANDLED:
                // スクロールできないページ
                return true;
            default:
                if ((touchScrollable & PanZoomController.SCROLLABLE_FLAG_TOP) != 0) return false;
                // overscroll-behavior: none などで上へのはみ出しを止めているページでは更新しない
                return (touchOverscroll & PanZoomController.OVERSCROLL_FLAG_VERTICAL) != 0;
        }
    }

    /**
     * SurfaceView は「自分自身」の可視状態が変わった時しか描画面を隠さない(親を GONE にしても
     * 描画面は残る)。そのままだと同じ画面内でタブを切り替えた時に、後ろに回したタブの描画面が
     * 前面のタブより上に残って表示されてしまうので、中の SurfaceView にも可視状態を伝える。
     */
    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        for (int i = 0; i < getChildCount(); i++) {
            android.view.View child = getChildAt(i);
            if (child instanceof android.view.SurfaceView) {
                child.setVisibility(visibility == VISIBLE ? VISIBLE : INVISIBLE);
            }
        }
    }

    /** タブが前面に出た / アプリに戻った。 */
    public void onResume() {
        if (destroyed) return;
        if (getSession() != session) {
            setSession(session);
        }
        hostPaused = false;
        session.setActive(true);
        session.setFocused(true);
    }

    /**
     * 文字の選択を解除し、選択メニュー(コピー・共有など)を閉じる。
     * @return 選択中だった(解除は Gecko 側で少し遅れて反映される)
     */
    public boolean clearTextSelection() {
        if (destroyed) return false;
        GeckoSession.SelectionActionDelegate delegate = session.getSelectionActionDelegate();
        if (!(delegate instanceof BasicSelectionActionDelegate)) return false;
        BasicSelectionActionDelegate basic = (BasicSelectionActionDelegate) delegate;
        GeckoSession.SelectionActionDelegate.Selection selection = basic.getSelection();
        if (selection == null) return false;
        basic.clearSelection();
        // 選択を畳めないページでもメニューだけは確実に消す
        selection.hide();
        return true;
    }

    /**
     * アプリが裏にある間、読み込みだけ進められるようにする(「ページの準備ができたらお知らせ」用)。
     * 表示やフォーカスは変えない。終わったら {@link #onPause()} で止め直す。
     */
    public void activateForBackgroundLoad() {
        if (destroyed) return;
        session.setActive(true);
    }

    /** アプリが停止した(表示はそのまま)。 */
    public void onPause() {
        if (destroyed) return;
        hostPaused = true;
        session.setFocused(false);
        // 再生中のメディアがあれば、ページを動かしたままにして再生を続ける(バックグラウンド再生)
        if (mediaPlaying && MediaPlaybackService.isEnabled(getContext())) return;
        session.setActive(false);
    }

    /** メディアが止まった時、裏に回っているなら止め直す(電池のため)。 */
    private void deactivateIfHostPaused() {
        if (hostPaused && !destroyed) session.setActive(false);
    }

    /** 通知などからページのメディアを再生する。 */
    public void playMedia() {
        if (destroyed || mediaSession == null) return;
        session.setActive(true);
        mediaSession.play();
    }

    public void pauseMedia() {
        if (destroyed || mediaSession == null) return;
        mediaSession.pause();
    }

    public void stopMedia() {
        if (destroyed || mediaSession == null) return;
        mediaSession.stop();
    }

    public boolean isMediaPlaying() {
        return mediaPlaying;
    }

    /**
     * タブ切り替えで背面に回した。描画用のサーフェスも手放し、表示中のタブだけが GPU を使うようにする。
     * セッション自体は開いたままなので、ページの状態は失われない。
     */
    public void moveToBackground() {
        if (destroyed) return;
        detachExternalDisplay(false);
        onPause();
        if (getSession() != null) {
            releaseSession();
        }
    }

    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        MediaPlaybackService.stopFor(this);
        mediaSession = null;
        mediaPlaying = false;
        removePageSnapshot();
        detachExternalDisplay(false);
        listener = null;
        if (pagePort != null) {
            try { pagePort.disconnect(); } catch (Exception ignored) {}
            pagePort = null;
        }
        for (WebExtension.Port p : inputPorts) {
            try { p.disconnect(); } catch (Exception ignored) {}
        }
        inputPorts.clear();
        lockPort = null;
        releaseSession();
        session.close();
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    @NonNull
    public TabSettings getSettings() {
        return settings;
    }

    // ---- 証明書 ----

    @Nullable
    public X509Certificate getX509Certificate() {
        return security != null ? security.certificate : null;
    }

    @Nullable
    @SuppressWarnings("deprecation")
    public SslCertificate getCertificate() {
        X509Certificate x509 = getX509Certificate();
        return x509 != null ? new SslCertificate(x509) : null;
    }

    public boolean isSecure() {
        return security != null && security.isSecure;
    }

    @Nullable
    public GeckoSession.ProgressDelegate.SecurityInformation getSecurityInformation() {
        return security;
    }

    // ---- サードパーティ Cookie の例外 ----

    /**
     * 今のページの権限をサードパーティ Cookie の例外設定に合わせ、変えたら読み直す。
     * @param force 例外を切り替えた直後など、同じサイトでも読み直してよい時は true
     */
    public void syncCookieException(boolean force) {
        String url = engineUrl;
        String host = url != null ? android.net.Uri.parse(url).getHost() : null;
        if (host == null) return;
        if (!force && host.equals(cookieSyncReloadedHost)) return;
        if (CookiePolicy.syncTrackingPermission(getContext(), host, trackingPermission)) {
            cookieSyncReloadedHost = host;
            // 新しい権限で取り直さないと Cookie の扱いが変わらない
            session.reload();
        }
    }

    // ---- ページ内検索 ----

    @Nullable private FindCallback findCallback;

    public void setFindListener(@Nullable FindCallback callback) {
        this.findCallback = callback;
    }

    public void findAllAsync(@NonNull String query) {
        session.getFinder().setDisplayFlags(GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL);
        handleFind(session.getFinder().find(query, 0));
    }

    public void findNext(boolean forward) {
        handleFind(session.getFinder().find(null, forward ? 0 : GeckoSession.FINDER_FIND_BACKWARDS));
    }

    public void clearMatches() {
        session.getFinder().clear();
    }

    private void handleFind(GeckoResult<GeckoSession.FinderResult> result) {
        result.accept(r -> {
            FindCallback cb = findCallback;
            if (cb == null || r == null) return;
            int total = r.found ? r.total : 0;
            int current = r.found ? Math.max(0, r.current - 1) : 0;
            cb.onFindResult(current, total);
        }, e -> {
            FindCallback cb = findCallback;
            if (cb != null) cb.onFindResult(0, 0);
        });
    }

    // ---- 状態の保存・復元 ----

    /** 戻る/進む履歴を含むタブの状態(JSON 文字列)。まだ何も無ければ null。 */
    @Nullable
    public String saveState() {
        return sessionState != null ? sessionState.toString() : null;
    }

    /** {@link #saveState()} の結果からタブを復元する。失敗したら false。 */
    public boolean restoreState(@Nullable String state) {
        if (state == null || state.isEmpty()) return false;
        final GeckoSession.SessionState restored;
        try {
            restored = GeckoSession.SessionState.fromString(state);
        } catch (Exception e) {
            Log.w(TAG, "タブ状態の読み込みに失敗しました", e);
            return false;
        }
        if (restored == null || restored.size() == 0) return false;
        sessionState = restored;
        restorePending = true;
        engine.whenReady(() -> {
            if (!destroyed) session.restoreState(restored);
        });
        return true;
    }

    @Nullable
    /**
     * Gecko は戻る/進む履歴(セッション状態)の通知を数秒おきにまとめて行うため、
     * ページを開いた直後にアプリが終了すると、保存される履歴に最後のページが入らない。
     * ページの読み込み完了・URL の変化のたびに、すぐ通知してもらう。
     */
    private void requestSessionStateFlush() {
        if (destroyed) return;
        try {
            session.flushSessionState();
        } catch (Exception e) {
            Log.w(TAG, "セッション状態の更新に失敗しました", e);
        }
    }

    /** アプリ終了前などに、最新の履歴を通知してもらう。 */
    public void flushSessionState() {
        requestSessionStateFlush();
    }

    /** 保存済み(または復元した)履歴の現在のページの URL。無ければ null。 */
    @Nullable
    public String getStateUrl() {
        String u = urlFromState(sessionState);
        return u != null ? engine.toAppUrl(u) : null;
    }

    /** 履歴が無い、または about:blank だけの状態か。 */
    private static boolean isBlankState(@Nullable GeckoSession.SessionState state) {
        if (state == null || state.size() == 0) return true;
        if (state.size() > 1) return false;
        try {
            String uri = state.get(0).getUri();
            return uri == null || uri.isEmpty() || "about:blank".equals(uri);
        } catch (Exception e) {
            return false;
        }
    }

    private static String urlFromState(@Nullable GeckoSession.SessionState state) {
        if (state == null || state.size() == 0) return null;
        try {
            int idx = Math.max(0, Math.min(state.getCurrentIndex(), state.size() - 1));
            return state.get(idx).getUri();
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String titleFromState(@Nullable GeckoSession.SessionState state) {
        if (state == null || state.size() == 0) return null;
        try {
            int idx = Math.max(0, Math.min(state.getCurrentIndex(), state.size() - 1));
            return state.get(idx).getTitle();
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 二画面(このビューの外の描画面) ----

    /** このタブのページを、このビューではなく別の描画面(二画面表示)へ描かせる相手。 */
    public interface ExternalDisplayHost {
        /** 描画先を受け取った。描画面を渡して描かせる。 */
        void onExternalDisplayAcquired(@NonNull GeckoTabView tab, @NonNull GeckoDisplay display);

        /** 描画先を返してもらう直前。描画面を外す(surfaceDestroyed)。 */
        void onExternalDisplayReleased(@NonNull GeckoTabView tab, @NonNull GeckoDisplay display);
    }

    @Nullable private ExternalDisplayHost externalHost;
    @Nullable private GeckoDisplay externalDisplay;

    /**
     * ページの描画を host へ任せる。このビューは画面に何も描かなくなるが、タッチ・キーボード入力・
     * 文字の選択などはこれまでどおりこのビューで受け取ってページへ渡す。
     */
    public void attachExternalDisplay(@NonNull ExternalDisplayHost host) {
        if (destroyed || externalHost == host) return;
        detachExternalDisplay(true);
        removePageSnapshot();
        // GeckoView から描画先だけを外す。releaseSession() は入力の受け口や選択メニューも外すので、
        // セッション(mSession)と選択メニューはすぐ戻し、タッチ・キー入力は GeckoView のまま流す
        GeckoSession.SelectionActionDelegate selection = session.getSelectionActionDelegate();
        if (getSession() != null) releaseSession();
        mSession = session;
        session.getTextInput().setView(this);
        if (session.getSelectionActionDelegate() == null && selection != null) {
            session.setSelectionActionDelegate(selection);
        }
        GeckoDisplay d = session.acquireDisplay();
        externalHost = host;
        externalDisplay = d;
        // 描画先を外した時に GeckoView がページを止めているので動かし直す
        if (!hostPaused) session.setActive(true);
        host.onExternalDisplayAcquired(this, d);
    }

    /** 描画をこのビューへ戻す。 */
    public void detachExternalDisplay() {
        detachExternalDisplay(true);
    }

    /** @param restore GeckoView へセッションをつなぎ直す(背面に回す・閉じる時は false) */
    private void detachExternalDisplay(boolean restore) {
        GeckoDisplay d = externalDisplay;
        ExternalDisplayHost host = externalHost;
        if (d == null) return;
        externalDisplay = null;
        externalHost = null;
        if (host != null) host.onExternalDisplayReleased(this, d);
        try {
            session.releaseDisplay(d);
        } catch (Exception e) {
            Log.w(TAG, "releaseDisplay", e);
        }
        if (session.getTextInput().getView() == this) session.getTextInput().setView(null);
        // GeckoView の描画先は外れたままなので、セッションだけ外してから普通につなぎ直す
        mSession = null;
        if (restore && !destroyed) setSession(session);
    }

    /** ページを別の描画面(二画面表示)へ描いているか。 */
    public boolean hasExternalDisplay() {
        return externalDisplay != null;
    }

    @NonNull
    @Override
    public GeckoResult<Bitmap> capturePixels() {
        if (externalDisplay != null) return externalDisplay.capturePixels();
        return super.capturePixels();
    }

    // ---- 画面キャプチャ・ページ取得 ----

    public interface BitmapCallback {
        void onBitmap(@Nullable Bitmap bitmap);
    }

    /** 表示中のページを画像として取得する(メインスレッドで callback)。 */
    public void capture(@NonNull BitmapCallback callback) {
        if (destroyed || getWidth() <= 0 || getHeight() <= 0) {
            callback.onBitmap(null);
            return;
        }
        try {
            capturePixels().accept(bitmap -> {
                // 二画面の描画中は下の画面の分まで写るので、このビューに見えている所だけにする
                if (bitmap != null && externalDisplay != null && bitmap.getHeight() > getHeight()
                        && bitmap.getWidth() > 0) {
                    Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0,
                            Math.min(bitmap.getWidth(), getWidth()), getHeight());
                    if (cropped != bitmap) bitmap.recycle();
                    bitmap = cropped;
                }
                callback.onBitmap(bitmap);
            }, e -> callback.onBitmap(null));
        } catch (Exception e) {
            callback.onBitmap(null);
        }
    }

    /** 表示中のページの HTML を取得する(内蔵拡張のコンテンツスクリプト経由)。 */
    public void requestHtml(@NonNull HtmlCallback callback) {
        requestPageHtml("getHtml", 5000, callback);
    }

    /**
     * ページ保存用の HTML を取得する。CSS と画像を埋め込み、スクリプトを除いた 1 ファイルの HTML になるので
     * オフラインでもそのまま開ける。画像の取得に時間がかかるため待ち時間を長めにしている。
     */
    public void requestSnapshot(@NonNull HtmlCallback callback) {
        requestPageHtml("getSnapshot", 60000, callback);
    }

    private void requestPageHtml(String type, long timeoutMs, @NonNull HtmlCallback callback) {
        if (pagePort == null) {
            callback.onHtml(null);
            return;
        }
        int id = ++htmlRequestSeq;
        htmlCallbacks.put(id, callback);
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", type);
            msg.put("id", id);
            pagePort.postMessage(msg);
        } catch (Exception e) {
            htmlCallbacks.remove(id);
            callback.onHtml(null);
            return;
        }
        // 応答が無いページ(読み込み途中で遷移した等)でも呼び出し元が止まらないようにする
        mainHandler.postDelayed(() -> {
            HtmlCallback cb = htmlCallbacks.remove(id);
            if (cb != null) cb.onHtml(null);
        }, timeoutMs);
    }

    /** ページ側スクリプトへ命令を送る("negapoji" など)。送れなければ false。 */
    public boolean sendPageCommand(@NonNull String type) {
        if (pagePort == null) return false;
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", type);
            msg.put("lang", Lang.tag());
            pagePort.postMessage(msg);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 拡大縮小ボタン用。指でのピンチイン/ピンチアウトと同じ動きにするため、画面中央で 2 本指を
     * 広げる(狭める)タッチ操作を作って Gecko に渡す。CSS zoom と違ってレイアウトは変わらず、
     * ページが拡大を禁止していれば指の時と同じく拡大されない。
     *
     * @param factor 1 より大きければ拡大、小さければ縮小(指の間隔をこの倍率だけ変える)
     */
    public void zoomBy(float factor) {
        if (destroyed || pinchRunning || factor <= 0f || factor == 1f) return;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        // 指の間隔は画面に収まるよう、広い方の端を短辺の 8 割までにする
        float maxSpan = Math.min(w, h) * 0.8f;
        float startSpan = factor > 1f ? maxSpan / factor : maxSpan;
        float endSpan = startSpan * factor;

        pinchRunning = true;
        long downTime = SystemClock.uptimeMillis();
        sendPinchEvent(downTime, MotionEvent.ACTION_DOWN, 1, cx, cy, startSpan);
        sendPinchEvent(downTime, MotionEvent.ACTION_POINTER_DOWN
                | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, cx, cy, startSpan);

        for (int i = 1; i <= PINCH_STEPS + 1; i++) {
            final int step = Math.min(i, PINCH_STEPS);
            mainHandler.postDelayed(() -> {
                if (destroyed) return;
                // 最初と最後をゆっくりにして、指で動かした時のような自然な速さにする
                float t = (float) step / PINCH_STEPS;
                float eased = t * t * (3f - 2f * t);
                float span = startSpan + (endSpan - startSpan) * eased;
                // 最後は同じ位置でもう一度 MOVE を送り、指を止めてから離す(慣性で動き続けないように)
                sendPinchEvent(downTime, MotionEvent.ACTION_MOVE, 2, cx, cy, span);
            }, (long) i * PINCH_FRAME_MS);
        }
        mainHandler.postDelayed(() -> {
            if (!destroyed) {
                sendPinchEvent(downTime, MotionEvent.ACTION_POINTER_UP
                        | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, cx, cy, endSpan);
                sendPinchEvent(downTime, MotionEvent.ACTION_UP, 1, cx, cy, endSpan);
            }
            pinchRunning = false;
        }, (long) (PINCH_STEPS + 2) * PINCH_FRAME_MS);
    }

    /** 中央 (cx, cy) から左右に span/2 ずつ離れた指(1 本なら左の指だけ)のタッチを Gecko に渡す。 */
    private void sendPinchEvent(long downTime, int action, int pointers, float cx, float cy, float span) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[pointers];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointers];
        for (int i = 0; i < pointers; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = i;
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = cx + (i == 0 ? -span / 2f : span / 2f);
            coords[i].y = cy;
            coords[i].pressure = 1f;
            coords[i].size = 1f;
        }
        MotionEvent ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, pointers,
                props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try {
            getPanZoomController().onTouchEvent(ev);
        } catch (Exception e) {
            Log.w(TAG, "pinch event failed", e);
        } finally {
            ev.recycle();
        }
    }

    // ------------------------------------------------------------------
    // Gecko の各 delegate
    // ------------------------------------------------------------------

    private final WebExtension.MessageDelegate pageMessageDelegate = new WebExtension.MessageDelegate() {
        /** ポートを使わない 1 回だけの知らせ(notranslate.js の「翻訳しないで」など)。 */
        @Nullable
        @Override
        public GeckoResult<Object> onMessage(@NonNull String nativeApp, @NonNull Object message,
                                             @NonNull WebExtension.MessageSender sender) {
            if (message instanceof JSONObject) handlePageMessage((JSONObject) message);
            return null;
        }

        @Override
        public void onConnect(@NonNull WebExtension.Port port) {
            if (pagePort != null && pagePort != port) {
                try { pagePort.disconnect(); } catch (Exception ignored) {}
            }
            pagePort = port;
            port.setDelegate(new WebExtension.PortDelegate() {
                @Override
                public void onPortMessage(@NonNull Object message, @NonNull WebExtension.Port p) {
                    if (message instanceof JSONObject) handlePageMessage((JSONObject) message);
                }

                @Override
                public void onDisconnect(@NonNull WebExtension.Port p) {
                    if (pagePort == p) pagePort = null;
                }
            });
        }
    };

    // ---- ポインターロック(マウスの固定) ----

    private final java.util.List<WebExtension.Port> inputPorts = new java.util.ArrayList<>();
    /** マウスを固定しているフレームの接続。固定していなければ null。 */
    @Nullable private WebExtension.Port lockPort;
    private float lockMoveX, lockMoveY;
    private boolean lockMovePosted;

    private final WebExtension.MessageDelegate inputMessageDelegate = new WebExtension.MessageDelegate() {
        @Override
        public void onConnect(@NonNull WebExtension.Port port) {
            inputPorts.add(port);
            port.setDelegate(new WebExtension.PortDelegate() {
                @Override
                public void onPortMessage(@NonNull Object message, @NonNull WebExtension.Port p) {
                    if (!(message instanceof JSONObject)) return;
                    String type = ((JSONObject) message).optString("type");
                    if ("lock".equals(type)) {
                        WebExtension.Port old = lockPort;
                        lockPort = p;
                        if (old != null && old != p) postInput(old, "unlock");
                        if (old == null && listener != null) listener.onPointerLockChanged(GeckoTabView.this, true);
                    } else if ("unlock".equals(type) && lockPort == p) {
                        lockPort = null;
                        if (listener != null) listener.onPointerLockChanged(GeckoTabView.this, false);
                    }
                }

                @Override
                public void onDisconnect(@NonNull WebExtension.Port p) {
                    inputPorts.remove(p);
                    if (lockPort == p) {
                        lockPort = null;
                        if (listener != null) listener.onPointerLockChanged(GeckoTabView.this, false);
                    }
                }
            });
        }
    };

    private static void postInput(@NonNull WebExtension.Port port, @NonNull String type) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", type);
            port.postMessage(msg);
        } catch (Exception ignored) {
        }
    }

    /** ページがマウスを固定しているか(requestPointerLock)。 */
    public boolean isPointerLocked() {
        return lockPort != null;
    }

    /** マウスの固定をやめる(Esc・3 本指タップなど)。 */
    public void exitPointerLock() {
        WebExtension.Port p = lockPort;
        if (p == null) return;
        lockPort = null;
        postInput(p, "unlock");
        if (listener != null) listener.onPointerLockChanged(this, false);
    }

    /** 固定中のマウスの動き(CSS px)。1 フレーム分をまとめて送る。 */
    public void sendPointerLockMove(float dx, float dy) {
        if (lockPort == null) return;
        lockMoveX += dx;
        lockMoveY += dy;
        if (lockMovePosted) return;
        lockMovePosted = true;
        postOnAnimation(() -> {
            lockMovePosted = false;
            WebExtension.Port p = lockPort;
            float mx = lockMoveX, my = lockMoveY;
            lockMoveX = 0;
            lockMoveY = 0;
            if (p == null || (mx == 0 && my == 0)) return;
            try {
                JSONObject msg = new JSONObject();
                msg.put("type", "move");
                msg.put("dx", (double) mx);
                msg.put("dy", (double) my);
                p.postMessage(msg);
            } catch (Exception ignored) {
            }
        });
    }

    private void handlePageMessage(JSONObject msg) {
        String type = msg.optString("type");
        if ("page".equals(type)) {
            String icon = msg.optString("icon", "");
            iconUrl = icon.isEmpty() ? null : icon;
            if (listener != null) listener.onPageInfo(this, iconUrl);
            if (msg.optBoolean("noTranslate", false)) markNoTranslatePage();
        } else if ("noTranslate".equals(type)) {
            markNoTranslatePage();
        } else if ("html".equals(type)) {
            HtmlCallback cb = htmlCallbacks.remove(msg.optInt("id"));
            String html = msg.isNull("html") ? null : msg.optString("html", null);
            if (cb != null) cb.onHtml(html == null || html.isEmpty() ? null : html);
        } else if ("docHeight".equals(type)) {
            documentHeight = msg.optInt("value", 0);
        } else if ("nearBottom".equals(type)) {
            Boolean near = msg.optBoolean("value");
            if (near.equals(nearBottom)) return;
            nearBottom = near;
            if (listener != null) listener.onNearBottomChanged(this);
        } else if ("reader".equals(type)) {
            readerActive = msg.optBoolean("active");
            String error = msg.isNull("error") ? null : msg.optString("error", null);
            if (listener != null) listener.onReaderModeChanged(this, readerActive, error);
        } else if ("bottomColor".equals(type)) {
            Integer color = parseCssRgb(msg.optString("color"));
            if (color == null || color.equals(bottomColor)) return;
            bottomColor = color;
            if (listener != null) listener.onBottomColorChanged(this, color);
        }
    }

    /** ページ全体の高さ(画面の px)。ページから報告が無ければ 0。 */
    private int documentHeight;

    /** ページ全体の高さ(画面の px)。分からなければ 0。 */
    public int getDocumentHeight() {
        return documentHeight;
    }

    /** ページの一番下付近までスクロールしているか。ページから報告が無ければ(内蔵ページ等) null。 */
    @Nullable private Boolean nearBottom;

    /** リーディングモードで表示中か(ページを移ると元に戻る)。 */
    private boolean readerActive;

    public boolean isReaderActive() {
        return readerActive;
    }

    @Nullable
    public Boolean isNearBottom() {
        return nearBottom;
    }

    /** 画面下端に見えているページの背景色。まだ分からなければ null。 */
    @Nullable
    public Integer getBottomColor() {
        return bottomColor;
    }

    /** "rgb(r, g, b)" を色の値にする。読めなければ null。 */
    @Nullable
    private static Integer parseCssRgb(String value) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("rgba?\\(\\s*([\\d.]+)[\\s,]+([\\d.]+)[\\s,]+([\\d.]+)").matcher(value);
        if (!m.find()) return null;
        try {
            int r = Math.round(Float.parseFloat(m.group(1)));
            int g = Math.round(Float.parseFloat(m.group(2)));
            int b = Math.round(Float.parseFloat(m.group(3)));
            return android.graphics.Color.rgb(r, g, b);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 画面のキーボード(ツールの「キーボード」)を出している間は、ページの入力欄に触れても端末の
     * キーボードを自動では出さない(2 つのキーボードが重なって画面のキーボードが隠れるため)。
     * 端末のキーボードは、画面のキーボードの日本語入力ボタンで出す。
     */
    static volatile boolean suppressSoftInput;
    /** 次の 1 回だけ端末のキーボードを出してよい(日本語入力ボタン)。 */
    static volatile boolean allowSoftInputOnce;

    private void installTextInputDelegate() {
        final GeckoSession.TextInputDelegate base = session.getTextInput().getDelegate();
        session.getTextInput().setDelegate(new GeckoSession.TextInputDelegate() {
            @Override
            public void restartInput(@NonNull GeckoSession s, int reason) {
                base.restartInput(s, reason);
            }

            @Override
            public void showSoftInput(@NonNull GeckoSession s) {
                if (suppressSoftInput && !allowSoftInputOnce) return;
                allowSoftInputOnce = false;
                base.showSoftInput(s);
            }

            @Override
            public void hideSoftInput(@NonNull GeckoSession s) {
                base.hideSoftInput(s);
            }

            @Override
            public void updateSelection(@NonNull GeckoSession s, int selStart, int selEnd, int compStart, int compEnd) {
                base.updateSelection(s, selStart, selEnd, compStart, compEnd);
            }

            @Override
            public void updateExtractedText(@NonNull GeckoSession s,
                                            @NonNull android.view.inputmethod.ExtractedTextRequest request,
                                            @NonNull android.view.inputmethod.ExtractedText text) {
                base.updateExtractedText(s, request, text);
            }

            @Override
            public void updateCursorAnchorInfo(@NonNull GeckoSession s,
                                               @NonNull android.view.inputmethod.CursorAnchorInfo info) {
                base.updateCursorAnchorInfo(s, info);
            }
        });
    }

    private void installDelegates() {
        installTextInputDelegate();
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(@NonNull GeckoSession s, @NonNull String url) {
                beginPageFade(engineUrl);
                translationState = null;
                noTranslatePage = false;
                readerActive = false;
                loading = true;
                progress = 0;
                engineUrl = url;
                iconUrl = null;
                if (listener != null) listener.onPageStarted(GeckoTabView.this, engine.toAppUrl(url));
            }

            @Override
            public void onPageStop(@NonNull GeckoSession s, boolean success) {
                // 描画が来ないまま終わった(エラー・ダウンロード・同じ絵のまま等)時も前の絵を消す
                if (!pagePaintedSinceStart) mainHandler.postDelayed(() -> {
                    pagePaintedSinceStart = true;
                    fadeOutPageSnapshot(PAGE_FADE_MS);
                }, 300);
                loading = false;
                progress = 100;
                requestSessionStateFlush();
                if (listener != null) listener.onPageFinished(GeckoTabView.this, getUrl(), success);
            }

            @Override
            public void onProgressChange(@NonNull GeckoSession s, int p) {
                progress = p;
                if (listener != null) listener.onProgressChanged(GeckoTabView.this, p);
            }

            @Override
            public void onSecurityChange(@NonNull GeckoSession s, @NonNull SecurityInformation info) {
                security = info;
                if (listener != null) listener.onSecurityChanged(GeckoTabView.this);
            }

            @Override
            public void onSessionStateChange(@NonNull GeckoSession s, @NonNull GeckoSession.SessionState state) {
                if (restorePending) {
                    if (isBlankState(state)) return; // 復元前の空の状態は無視する
                    restorePending = false;
                }
                sessionState = state;
                if (listener != null) listener.onSessionStateChanged(GeckoTabView.this);
            }
        });

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onLocationChange(@NonNull GeckoSession s, @Nullable String url,
                                         @NonNull List<GeckoSession.PermissionDelegate.ContentPermission> perms,
                                         @NonNull Boolean hasUserGesture) {
                engineUrl = url;
                nearBottom = null;
                documentHeight = 0;
                trackingPermission = null;
                for (GeckoSession.PermissionDelegate.ContentPermission p : perms) {
                    if (p.permission == GeckoSession.PermissionDelegate.PERMISSION_TRACKING) {
                        trackingPermission = p;
                        break;
                    }
                }
                syncCookieException(false);
                requestSessionStateFlush();
                if (listener != null) listener.onLocationChanged(GeckoTabView.this, engine.toAppUrl(url));
            }

            @Override
            public void onCanGoBack(@NonNull GeckoSession s, boolean can) {
                canGoBack = can;
                if (listener != null) listener.onNavigationStateChanged(GeckoTabView.this);
            }

            @Override
            public void onCanGoForward(@NonNull GeckoSession s, boolean can) {
                canGoForward = can;
                if (listener != null) listener.onNavigationStateChanged(GeckoTabView.this);
            }

            @Nullable
            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(@NonNull GeckoSession s, @NonNull LoadRequest request) {
                String uri = request.uri;
                // 押したリンクが Play ストアへ転送された: アプリがインストール済みならアプリで開く
                String storePackage = AppLinks.storePackage(uri);
                if (storePackage != null) {
                    String candidate = appLinkCandidate;
                    boolean fresh = candidate != null
                            && SystemClock.uptimeMillis() - appLinkCandidateAt < AppLinks.CANDIDATE_TTL_MS;
                    // ストアのリンクそのものを押した時はそのまま開く(転送された時だけ)
                    boolean redirected = request.isRedirect || !request.hasUserGesture;
                    if (fresh && redirected && listener != null
                            && listener.onStoreRedirect(GeckoTabView.this, candidate, storePackage)) {
                        appLinkCandidate = null;
                        return GeckoResult.deny();
                    }
                } else if (isWebScheme(uri) && request.hasUserGesture && !request.isRedirect) {
                    appLinkCandidate = uri;
                    appLinkCandidateAt = SystemClock.uptimeMillis();
                }
                // 「常に安全な接続」がメインセッションのみの時は、シークレット以外で HTTP を HTTPS に切り替える
                String upgraded = httpsUpgradeFor(request);
                if (upgraded != null) {
                    s.loadUri(upgraded);
                    return GeckoResult.deny();
                }
                if (isWebScheme(uri)) {
                    applySiteJavaScript(uri);
                    return GeckoResult.allow();
                }
                boolean handled = listener != null && listener.onExternalUrl(GeckoTabView.this, uri);
                return handled ? GeckoResult.deny() : GeckoResult.allow();
            }

            @Nullable
            @Override
            public GeckoResult<GeckoSession> onNewSession(@NonNull GeckoSession s, @NonNull String uri) {
                GeckoSession created = listener != null
                        ? listener.onNewWindow(GeckoTabView.this, engine.toAppUrl(uri)) : null;
                return created != null ? GeckoResult.fromValue(created) : null;
            }

            @Nullable
            @Override
            public GeckoResult<String> onLoadError(@NonNull GeckoSession s, @Nullable String uri,
                                                   @NonNull WebRequestError error) {
                loading = false;
                offerHttpFallback(uri);
                String page = listener != null
                        ? listener.onLoadError(GeckoTabView.this, engine.toAppUrl(uri), error) : null;
                return page != null ? GeckoResult.fromValue(page) : null;
            }
        });

        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onFirstContentfulPaint(@NonNull GeckoSession s) {
                // 新しいページが描かれた: 前のページの絵をゆっくり消して、新しいページを浮かび上がらせる
                pagePaintedSinceStart = true;
                contentEverPainted = true;
                fadeOutPageSnapshot(PAGE_FADE_MS);
            }

            @Override
            public void onTitleChange(@NonNull GeckoSession s, @Nullable String t) {
                title = t;
                if (listener != null) listener.onTitleChanged(GeckoTabView.this, t);
            }

            @Override
            public void onCloseRequest(@NonNull GeckoSession s) {
                if (listener != null) listener.onCloseRequest(GeckoTabView.this);
            }

            @Override
            public void onFullScreen(@NonNull GeckoSession s, boolean fullScreen) {
                if (listener != null) listener.onFullScreen(GeckoTabView.this, fullScreen);
            }

            @Override
            public void onContextMenu(@NonNull GeckoSession s, int screenX, int screenY,
                                      @NonNull ContextElement element) {
                if (listener != null) listener.onContextMenu(GeckoTabView.this, element);
            }

            @Override
            public void onExternalResponse(@NonNull GeckoSession s, @NonNull WebResponse response) {
                if (listener != null) listener.onDownload(GeckoTabView.this, response);
            }

            @Override
            public void onCrash(@NonNull GeckoSession s) {
                recoverFromCrash();
            }

            @Override
            public void onKill(@NonNull GeckoSession s) {
                recoverFromCrash();
            }
        });

        session.setTranslationsSessionDelegate(new TranslationsController.SessionTranslation.Delegate() {
            @Override
            public void onOfferTranslate(@NonNull GeckoSession s) {
                if (listener != null) listener.onTranslationOffered(GeckoTabView.this);
            }

            @Override
            public void onExpectedTranslate(@NonNull GeckoSession s) {
                if (listener != null) listener.onTranslationExpected(GeckoTabView.this);
            }

            @Override
            public void onTranslationStateChange(@NonNull GeckoSession s,
                    @Nullable TranslationsController.SessionTranslation.TranslationState state) {
                translationState = state;
                if (listener != null && state != null) {
                    listener.onTranslationStateChanged(GeckoTabView.this, state);
                }
            }
        });

        session.setMediaSessionDelegate(new org.mozilla.geckoview.MediaSession.Delegate() {
            @Override
            public void onActivated(@NonNull GeckoSession s, @NonNull org.mozilla.geckoview.MediaSession m) {
                mediaSession = m;
            }

            @Override
            public void onDeactivated(@NonNull GeckoSession s, @NonNull org.mozilla.geckoview.MediaSession m) {
                if (mediaSession == m) mediaSession = null;
                mediaPlaying = false;
                MediaPlaybackService.stopFor(GeckoTabView.this);
                deactivateIfHostPaused();
            }

            @Override
            public void onMetadata(@NonNull GeckoSession s, @NonNull org.mozilla.geckoview.MediaSession m,
                                   @NonNull org.mozilla.geckoview.MediaSession.Metadata meta) {
                MediaPlaybackService.onMetadata(GeckoTabView.this, meta.title, meta.artist, null);
                if (meta.artwork != null) {
                    meta.artwork.getBitmap(256).accept(bmp -> {
                        if (bmp != null && !destroyed) {
                            MediaPlaybackService.onMetadata(GeckoTabView.this, meta.title, meta.artist, bmp);
                        }
                    }, e -> { });
                }
            }

            @Override
            public void onPlay(@NonNull GeckoSession s, @NonNull org.mozilla.geckoview.MediaSession m) {
                mediaSession = m;
                mediaPlaying = true;
                MediaPlaybackService.onMediaState(GeckoTabView.this, true);
            }

            @Override
            public void onPause(@NonNull GeckoSession s, @NonNull org.mozilla.geckoview.MediaSession m) {
                mediaPlaying = false;
                MediaPlaybackService.onMediaState(GeckoTabView.this, false);
                deactivateIfHostPaused();
            }

            @Override
            public void onStop(@NonNull GeckoSession s, @NonNull org.mozilla.geckoview.MediaSession m) {
                mediaPlaying = false;
                MediaPlaybackService.stopFor(GeckoTabView.this);
                deactivateIfHostPaused();
            }
        });

        session.setScrollDelegate(new GeckoSession.ScrollDelegate() {
            @Override
            public void onScrollChanged(@NonNull GeckoSession s, int scrollX, int scrollY) {
                int old = pageScrollY;
                pageScrollY = scrollY;
                if (listener != null) listener.onScrollChanged(GeckoTabView.this, scrollY, old);
            }
        });
    }

    // ---- ページ翻訳(Firefox と同じ端末内の翻訳エンジン) ----

    /** 最後に通知された翻訳の状態。まだ無ければ null。 */
    @Nullable
    public TranslationsController.SessionTranslation.TranslationState getTranslationState() {
        return translationState;
    }

    /** ページが翻訳しないように指定しているか(&lt;meta name="google" content="notranslate"&gt; など)。 */
    private boolean noTranslatePage;

    public boolean isNoTranslatePage() {
        return noTranslatePage;
    }

    private void markNoTranslatePage() {
        if (noTranslatePage) return;
        noTranslatePage = true;
        if (listener != null) listener.onNoTranslatePage(this);
    }

    /** 翻訳済み(または翻訳中)か。 */
    public boolean isTranslated() {
        TranslationsController.SessionTranslation.TranslationState st = translationState;
        return st != null && st.requestedTranslationPair != null;
    }

    /** ページを from → to に翻訳する。言語モデルが無ければダウンロードする。 */
    @Nullable
    public GeckoResult<Void> translate(@NonNull String from, @NonNull String to) {
        TranslationsController.SessionTranslation st = destroyed ? null : session.getSessionTranslation();
        if (st == null) return null;
        return st.translate(from, to, new TranslationsController.SessionTranslation.TranslationOptions.Builder()
                .downloadModel(true).build());
    }

    /** 翻訳を取り消して原文に戻す。 */
    @Nullable
    public GeckoResult<Void> restoreOriginalPage() {
        TranslationsController.SessionTranslation st = destroyed ? null : session.getSessionTranslation();
        return st != null ? st.restoreOriginalPage() : null;
    }

    /** 「このサイトを翻訳しない」。 */
    @Nullable
    public GeckoResult<Void> setNeverTranslateSite(boolean never) {
        TranslationsController.SessionTranslation st = destroyed ? null : session.getSessionTranslation();
        return st != null ? st.setNeverTranslateSiteSetting(never) : null;
    }

    @Nullable
    public GeckoResult<Boolean> getNeverTranslateSite() {
        TranslationsController.SessionTranslation st = destroyed ? null : session.getSessionTranslation();
        return st != null ? st.getNeverTranslateSiteSetting() : null;
    }

    /** コンテンツプロセスが落ちたら、保存済みの状態でタブを開き直す。 */
    private void recoverFromCrash() {
        if (destroyed) return;
        final GeckoSession.SessionState last = sessionState;
        final String lastUrl = getUrl();
        mainHandler.post(() -> {
            if (destroyed) return;
            try {
                // 二画面の描画先につないでいたら外す(作り直した後で二画面側がつなぎ直す)
                detachExternalDisplay(false);
                releaseSession();
                session.close();
                session.open(engine.getRuntime());
                setSession(session);
                if (last != null && last.size() > 0) {
                    session.restoreState(last);
                } else if (lastUrl != null) {
                    session.loadUri(engine.toEngineUrl(lastUrl));
                }
            } catch (Exception e) {
                Log.e(TAG, "タブの復旧に失敗しました", e);
            }
        });
    }

    /** 設定(全体)の JavaScript の有効/無効。サイトごとの例外はページを開く時に上書きする。 */
    private boolean baseJavaScript = true;

    /** 「サイトの設定 › JavaScript」のサイトごとの例外を、これから開くページに当てる。 */
    private void applySiteJavaScript(@Nullable String uri) {
        if (uri == null) return;
        String lower = uri.toLowerCase(java.util.Locale.ROOT);
        boolean js = baseJavaScript;
        if (lower.startsWith("http:") || lower.startsWith("https:")) {
            int v = SitePermissions.getSite(getContext(), SitePermissions.hostOf(uri), SitePermissions.JAVASCRIPT);
            if (v == SitePermissions.ALLOW) js = true;
            else if (v == SitePermissions.BLOCK) js = false;
        } else if (lower.startsWith("moz-extension:") || lower.startsWith("resource:")) {
            js = true; // 内蔵ページは常に動かす
        }
        if (session.getSettings().getAllowJavascript() != js) {
            session.getSettings().setAllowJavascript(js);
        }
    }

    // ------------------------------------------------------------------
    // 常に安全な接続(メインセッションのみ)
    // ------------------------------------------------------------------

    /** 直前に HTTPS に切り替えたホストと時刻(HTTP に戻す転送や、つながらない時の確認に使う)。 */
    @Nullable private String httpsUpgradedHost;
    private long httpsUpgradedAt;
    /** HTTP のまま開くと決めたホスト(このタブの間だけ)。 */
    private final java.util.Set<String> insecureAllowedHosts = new java.util.HashSet<>();

    /** HTTPS に切り替えて読み込む URL。切り替えない時は null。 */
    @Nullable
    private String httpsUpgradeFor(@NonNull GeckoSession.NavigationDelegate.LoadRequest request) {
        String uri = request.uri;
        if (privateMode || uri == null || !uri.regionMatches(true, 0, "http://", 0, 7)) return null;
        if (!BrowserPrefs.httpsOnlyMainSession(getContext())) return null;
        String host = android.net.Uri.parse(uri).getHost();
        if (host == null || isLocalHost(host) || insecureAllowedHosts.contains(host)) return null;
        // HTTPS にしたらサイトが HTTP へ転送してきた: 切り替えを繰り返さないよう HTTP のまま開く
        if (request.isRedirect && host.equals(httpsUpgradedHost)
                && SystemClock.uptimeMillis() - httpsUpgradedAt < 15_000) {
            insecureAllowedHosts.add(host);
            return null;
        }
        httpsUpgradedHost = host;
        httpsUpgradedAt = SystemClock.uptimeMillis();
        return "https://" + uri.substring(7);
    }

    /** HTTPS に切り替えたページが開けなかった時、HTTP で開き直すか尋ねる。 */
    private void offerHttpFallback(@Nullable String uri) {
        if (uri == null || !uri.regionMatches(true, 0, "https://", 0, 8)) return;
        String host = android.net.Uri.parse(uri).getHost();
        if (host == null || !host.equals(httpsUpgradedHost)
                || SystemClock.uptimeMillis() - httpsUpgradedAt > 60_000) return;
        httpsUpgradedHost = null;
        String http = "http://" + uri.substring(8);
        post(() -> {
            if (destroyed) return;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_si_warning)
                    .setTitle(Lang.t("安全な接続を使用できません"))
                    .setMessage(host + Lang.t(" は安全な接続(HTTPS)に対応していない可能性があります。\n"
                            + "HTTP で開くと、送受信する情報が第三者に見られるおそれがあります。"))
                    .setPositiveButton(Lang.t("HTTP で開く"), (d, w) -> {
                        insecureAllowedHosts.add(host);
                        session.loadUri(http);
                    })
                    .setNegativeButton(Lang.t("キャンセル"), null)
                    .show();
        });
    }

    private static boolean isLocalHost(@NonNull String host) {
        String h = host.toLowerCase(java.util.Locale.ROOT);
        return h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local")
                || h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.")
                || h.matches("172\\.(1[6-9]|2\\d|3[01])\\..*") || h.equals("[::1]") || h.equals("::1");
    }

    private static boolean isWebScheme(@Nullable String uri) {
        if (uri == null) return true;
        String lower = uri.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http:") || lower.startsWith("https:")
                || lower.startsWith("about:") || lower.startsWith("data:")
                || lower.startsWith("blob:") || lower.startsWith("file:")
                || lower.startsWith("moz-extension:") || lower.startsWith("resource:")
                || lower.startsWith("view-source:") || lower.startsWith("javascript:");
    }

    // ------------------------------------------------------------------

    /**
     * タブごとの設定。WebSettings で使っていた項目のうち、Gecko で実現できるものだけを持つ。
     */
    public class TabSettings {

        public void setUserAgentString(@Nullable String ua) {
            session.getSettings().setUserAgentOverride(ua);
        }

        @NonNull
        public String getUserAgentString() {
            String ua = session.getSettings().getUserAgentOverride();
            return ua != null ? ua : GeckoSession.getDefaultUserAgent();
        }

        /** デスクトップ表示(UA と viewport の両方を PC 向けにする)。 */
        public void setDesktopMode(boolean desktop) {
            session.getSettings().setUserAgentMode(desktop
                    ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                    : GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
            session.getSettings().setViewportMode(desktop
                    ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                    : GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        }

        public boolean isDesktopMode() {
            return session.getSettings().getUserAgentMode() == GeckoSessionSettings.USER_AGENT_MODE_DESKTOP;
        }


        public void setJavaScriptEnabled(boolean enabled) {
            baseJavaScript = enabled;
            session.getSettings().setAllowJavascript(enabled);
        }


        /** 画像の読み込み。Gecko では全タブ共通の設定になる。 */
        public void setLoadsImagesAutomatically(boolean load) {
            engine.setBlockImages(!load);
        }

    }
}
