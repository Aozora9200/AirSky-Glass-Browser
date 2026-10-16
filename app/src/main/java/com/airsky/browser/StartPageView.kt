package com.airsky.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.PixelCopy
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.input.TextFieldValue
import java.net.URL
import java.util.concurrent.Executors

/**
 * ネイティブ UI のスタートページ(Chrome の新しいタブと同じく、HTML ではなくアプリの画面として描く)。
 *
 * タブ自体は従来どおり Gecko でスタートページの URL(中身は空のページ)を開いており、
 * そのタブが前面にある間だけ、この View をページの上に重ねて表示する。
 * こうすると戻る/進むの履歴やタブの保存・復元は今までと同じ仕組みのまま使える。
 */
class StartPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** スタートページから MainActivity へのお願い。 */
    interface Host {
        /** URL を今のタブで開く。 */
        fun onOpenUrl(url: String)

        /** 壁紙にする画像を選ぶ画面を出す。選ばれたら [setWallpaper] を呼ぶ。 */
        fun onPickWallpaper()

        /** シークレットモードを開く。 */
        fun onOpenSecret()

        /**
         * リンクの長押しメニュー(ページ内のリンクを長押しした時と同じメニュー)を出す。
         * [title] はリンクの文字(サイト名・記事の見出し)、[extras] はその後ろに足す項目。
         */
        fun onLinkMenu(url: String, title: String?, extras: List<Pair<String, () -> Unit>>)
    }

    fun interface CaptureCallback {
        fun onCaptured(bitmap: Bitmap?)
    }

    private val state = StartPageState(context)

    init {
        // ページの何もない所をタップしても、下の Gecko にタッチが抜けないようにする
        isClickable = true
        isFocusable = false
        addView(ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { StartPage(state) }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setHost(host: Host?) {
        state.host = host
    }

    /**
     * 表示された(タブを切り替えた・アプリに戻った)。時間帯の壁紙やニュースを新しくする。
     *
     * @param animate 表示のアニメーションをする(アプリに戻っただけの時は false)
     */
    @JvmOverloads
    fun onShown(animate: Boolean = true) {
        state.visible = true
        state.reloadGlassStyle()
        if (animate) state.appearKey++
        state.wallpaperTick++
        state.refreshFeedIfStale()
    }

    /** 隠れた。入力中の文字やメニューは閉じておく。 */
    fun onHidden() {
        state.visible = false
        state.resetTransientUi()
        hideKeyboard()
    }

    /** 戻るボタン。開いているダイアログやメニューを閉じたら true。 */
    fun handleBack(): Boolean = state.handleBack()

    /** 今のスクロール位置({項目の番号, ずれ})。タブごとに覚えておく用。 */
    fun getScrollPosition(): IntArray =
        intArrayOf(state.listState.firstVisibleItemIndex, state.listState.firstVisibleItemScrollOffset)

    /** スクロール位置を戻す。新しいタブでは (0, 0) で一番上から表示する。 */
    fun setScrollPosition(index: Int, offset: Int) {
        state.requestScroll(index, offset)
    }

    /** 一番上までスクロールしていない(引っ張って更新はまだ効かない)。 */
    fun canScrollUp(): Boolean = state.canScrollUp()

    /** 引っ張って更新。おすすめ記事と壁紙を読み直し、終わったら [done] を呼ぶ。 */
    fun refresh(done: Runnable) {
        state.refresh { done.run() }
    }

    /** メニューの「スタートページに追加」。追加できなかった(URL が不正・追加済み)時は false。 */
    fun addSite(name: String, url: String): Boolean = state.addSiteFromPage(name, url)

    /** 「壁紙を設定」で選ばれた画像。 */
    fun setWallpaper(uri: Uri) {
        state.setWallpaper(uri)
    }

    /** 画面に出ている見た目をそのまま撮る(タブ一覧のサムネイル用)。 */
    fun capture(window: Window, callback: CaptureCallback) {
        if (width <= 0 || height <= 0 || !isShown) {
            callback.onCaptured(null)
            return
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val rect = Rect(loc[0], loc[1], loc[0] + width, loc[1] + height)
        val main = Handler(Looper.getMainLooper())
        // 結果が返ってこない端末でもタブ一覧が開けるよう、待ちすぎたら諦める
        var done = false
        val finish = { result: Bitmap? ->
            if (!done) {
                done = true
                callback.onCaptured(result)
            }
        }
        main.postDelayed({ finish(null) }, CAPTURE_TIMEOUT_MS)
        try {
            PixelCopy.request(window, rect, bitmap, { result ->
                if (result != PixelCopy.SUCCESS) Log.w(TAG, "PixelCopy failed: $result")
                finish(if (result == PixelCopy.SUCCESS) bitmap else null)
            }, main)
        } catch (e: Exception) {
            Log.w(TAG, "PixelCopy failed", e)
            finish(null)
        }
    }

    private companion object {
        const val TAG = "StartPageView"
        const val CAPTURE_TIMEOUT_MS = 400L
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }
}

/** スタートページの表示状態。Compose の画面はこれを見て描く。 */
internal class StartPageState(context: Context) {

    /** ダイアログ・共有画面を出すための Activity の Context。 */
    private val uiContext = context
    private val appContext = context.applicationContext
    private val store = StartPageStore(appContext)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var host: StartPageView.Host? = null

    var visible by mutableStateOf(false)

    // ---- ガラスのデザイン(設定で変えられる) ----
    var glassEnabled by mutableStateOf(GlassPrefs.enabled(appContext))
        private set
    var glassRefraction by mutableStateOf(GlassPrefs.refraction(appContext))
        private set

    fun reloadGlassStyle() {
        glassEnabled = GlassPrefs.enabled(appContext)
        glassRefraction = GlassPrefs.refraction(appContext)
    }

    // ---- 壁紙 ----
    var customWallpaper by mutableStateOf<ImageBitmap?>(null)
        private set
    /** 増やすと時間帯の壁紙を判定し直す。 */
    var wallpaperTick by mutableStateOf(0)

    /** 増やすとスタートページを表示するアニメーションをやり直す。 */
    var appearKey by mutableStateOf(0)

    // ---- 検索 ----
    var query by mutableStateOf(TextFieldValue(""))
    val suggestions = mutableStateListOf<String>()
    var engine by mutableStateOf(store.engine)
        private set
    var engineMenuOpen by mutableStateOf(false)

    // ---- サイト ----
    var customSites by mutableStateOf(store.loadCustomSites())
        private set
    var editMode by mutableStateOf(false)
    var addSiteOpen by mutableStateOf(false)
    /** 追加した直後のサイト(customSites の番号)。そのアイコンだけ飛び出すように出す。 */
    var justAddedIndex by mutableStateOf(-1)

    // ---- おすすめ記事(Discover) ----
    /** 読み込んだ記事すべて。null は読み込み中。 */
    var feed by mutableStateOf<List<FeedEntry>?>(null)
        private set
    /** そのうち表示している件数(下までスクロールすると FEED_PAGE 件ずつ増やす)。 */
    var feedShown by mutableStateOf(FEED_PAGE)
        private set
    var feedFailed by mutableStateOf(false)
        private set
    /** 追加分を読み込んでいる演出中。 */
    var feedLoadingMore by mutableStateOf(false)
        private set
    private var feedLoadedAt = 0L
    private var feedLoading = false

    /** ページ全体のスクロール位置(引っ張って更新の判定に使う)。 */
    val listState = LazyListState()

    /** 移動したいスクロール位置({項目の番号, ずれ})。StartPage の LaunchedEffect が移動して null に戻す。 */
    var scrollRequest by mutableStateOf<IntArray?>(null)

    // ---- おすすめ記事の好み ----
    /** 「表示しない」「興味なし」にした記事の URL。 */
    var hiddenArticles by mutableStateOf(store.loadHiddenArticles())
        private set
    /** 「◯◯のコンテンツを表示しない」にした配信元。 */
    var blockedSources by mutableStateOf(store.loadBlockedSources())
        private set

    /** 壁紙メニュー(右上のボタン)を開いている。 */
    var wallpaperMenuOpen by mutableStateOf(false)

    // ---- お知らせ ----
    var notification by mutableStateOf<String?>(null)
        private set
    /** 消えるアニメーション中も文字を残すため、最後に出した文言を覚えておく。 */
    var lastNotification by mutableStateOf("")
        private set
    private val hideNotification = Runnable { notification = null }

    init {
        if (store.hasCustomWallpaper()) {
            io.execute {
                val image = store.loadCustomWallpaper()
                main.post { if (image != null) customWallpaper = image }
            }
        }
    }

    fun openUrl(url: String) {
        host?.onOpenUrl(url)
    }

    fun requestScroll(index: Int, offset: Int) {
        scrollRequest = intArrayOf(index, offset)
    }

    /**
     * リンクの長押しメニュー(ページ内のリンクを長押しした時と同じ項目)。
     * [extras] はその後ろに足す項目(ニュースの「興味なし」など)。
     */
    fun showLinkMenu(url: String, extras: List<Pair<String, () -> Unit>> = emptyList(), title: String? = null) {
        host?.onLinkMenu(url, title, extras)
    }

    /** 記事を共有する。 */
    fun share(title: String, url: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        }
        try {
            uiContext.startActivity(Intent.createChooser(send, Lang.t("共有")))
        } catch (e: Exception) {
            Toast.makeText(appContext, Lang.t("共有できるアプリがありません"), Toast.LENGTH_SHORT).show()
        }
    }

    // ---- おすすめ記事の好み ----

    /** 記事の長押しメニューに足す項目。配信元が分かっていれば配信元ごと消す項目も出す。 */
    fun feedMenuExtras(entry: FeedEntry, source: String?): List<Pair<String, () -> Unit>> =
        listOfNotNull(
            Lang.t("興味なし") to { notInterested(entry) },
            Lang.t("表示しない") to { hideArticle(entry) },
            source?.let { Lang.f("%sのコンテンツを表示しない", it) to { blockSource(it) } }
        )

    /** この記事を消し、同じカテゴリの記事を今後少なくする。 */
    private fun notInterested(entry: FeedEntry) {
        store.addNotInterested(entry.category)
        hideArticleSilently(entry)
        notify(Lang.t("今後このような記事を減らします"))
    }

    private fun hideArticle(entry: FeedEntry) {
        hideArticleSilently(entry)
        notify(Lang.t("この記事を表示しません"))
    }

    private fun hideArticleSilently(entry: FeedEntry) {
        hiddenArticles = hiddenArticles + entry.url
        store.saveHiddenArticles(hiddenArticles)
    }

    private fun blockSource(source: String) {
        blockedSources = blockedSources + source
        store.saveBlockedSources(blockedSources)
        notify(Lang.f("%sのコンテンツを表示しません", source))
    }

    /** 表示する記事か(消した記事・表示しない配信元の記事は出さない)。 */
    fun isVisible(entry: FeedEntry): Boolean {
        if (entry.url in hiddenArticles) return false
        val source = StartPageNetwork.cachedMeta(entry)?.source
        return source == null || source !in blockedSources
    }

    fun selectEngine(e: SearchEngine) {
        engine = e
        store.engine = e
        engineMenuOpen = false
        notify(Lang.f("%s に変更しました", e.label))
    }

    /** 画面下に短いお知らせを出す。 */
    fun showMessage(text: String) = notify(text)

    private fun notify(text: String) {
        lastNotification = text
        notification = text
        main.removeCallbacks(hideNotification)
        main.postDelayed(hideNotification, 2000)
    }

    // ---- サイト ----

    fun openAddSite() {
        editMode = false
        addSiteOpen = true
    }

    fun addCustomSite(name: String, url: URL) {
        val sites = customSites + Shortcut(name, url.toString(), Shortcut.faviconUrlFor(originOf(url)))
        customSites = sites
        store.saveCustomSites(sites)
        justAddedIndex = sites.lastIndex
        addSiteOpen = false
    }

    /** 見ているページをサイトとして追加する。同じ URL が既にあれば追加しない。 */
    fun addSiteFromPage(name: String, url: String): Boolean {
        val u = try {
            URL(url)
        } catch (e: Exception) {
            return false
        }
        if (customSites.any { it.url == u.toString() }) return false
        addCustomSite(name.ifBlank { u.host }, u)
        return true
    }

    fun removeCustomSite(index: Int) {
        if (index !in customSites.indices) return
        justAddedIndex = -1
        val sites = customSites.toMutableList().apply { removeAt(index) }
        customSites = sites
        store.saveCustomSites(sites)
    }

    // ---- 壁紙 ----

    fun pickWallpaper() {
        host?.onPickWallpaper()
    }

    fun setWallpaper(uri: Uri) {
        io.execute {
            val image = try {
                store.saveCustomWallpaper(uri)
            } catch (e: Exception) {
                null
            }
            main.post {
                if (image != null) {
                    customWallpaper = image
                } else {
                    Toast.makeText(appContext, Lang.t("この画像は壁紙に設定できませんでした"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun clearWallpaper() {
        io.execute { store.clearCustomWallpaper() }
        // 画面からはフェードアウトしながら消える(Wallpaper 側で最後の画像を覚えておく)
        customWallpaper = null
    }

    // ---- ニュース ----

    fun refreshFeedIfStale() {
        val age = SystemClock.elapsedRealtime() - feedLoadedAt
        if (feedLoadedAt != 0L && !feedFailed && age < FEED_REFRESH_MS) return
        loadFeed(null)
    }

    /** 引っ張って更新。 */
    fun refresh(done: () -> Unit) {
        wallpaperTick++
        loadFeed(done)
    }

    private fun loadFeed(done: (() -> Unit)?) {
        if (feedLoading) {
            done?.let { main.postDelayed({ it() }, 600) }
            return
        }
        feedLoading = true
        if (feed.isNullOrEmpty()) feed = null
        io.execute {
            val result = try {
                StartPageNetwork.fetchFeed()
            } catch (e: Exception) {
                null
            }
            // 最初の数件は画像を先に取りに行っておく
            result?.let { StartPageNetwork.prefetchMeta(it.take(FEED_PAGE)) }
            main.post {
                feedLoading = false
                feedLoadedAt = SystemClock.elapsedRealtime()
                feedFailed = result == null
                if (result != null) {
                    val filtered = applyPreferences(result)
                    feed = filtered
                    feedShown = minOf(FEED_PAGE, filtered.size)
                } else if (feed == null) {
                    feed = emptyList()
                }
                done?.invoke()
            }
        }
    }

    /**
     * 「興味なし」を n 回選んだカテゴリは、そのカテゴリの記事を (n + 1) 件に 1 件だけ残す。
     * 消した記事もここで外しておく(読み込み直すたびに出てこないように)。
     */
    private fun applyPreferences(entries: List<FeedEntry>): List<FeedEntry> {
        val penalties = store.loadNotInterested()
        val seen = HashMap<String, Int>()
        return entries.filter { e ->
            if (e.url in hiddenArticles) return@filter false
            val penalty = penalties[e.category] ?: 0
            if (penalty <= 0) return@filter true
            val n = seen.getOrDefault(e.category, 0)
            seen[e.category] = n + 1
            n % (penalty + 1) == 0
        }
    }

    /** 一番下まで来たら次の FEED_PAGE 件を出す。 */
    fun loadMore() {
        val all = feed ?: return
        if (feedLoadingMore || feedShown >= all.size) return
        feedLoadingMore = true
        val next = minOf(feedShown + FEED_PAGE, all.size)
        // 次の分の画像を先読みしてから出す(カードが空のまま並ばないように少しだけ待つ)
        io.execute {
            StartPageNetwork.prefetchMeta(all.subList(feedShown, next))
            main.postDelayed({
                feedShown = next
                feedLoadingMore = false
            }, 350)
        }
    }

    fun canScrollUp(): Boolean =
        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0

    fun openSecret() {
        host?.onOpenSecret()
    }

    // ---- 戻る・後片付け ----

    fun handleBack(): Boolean {
        when {
            addSiteOpen -> addSiteOpen = false
            wallpaperMenuOpen -> wallpaperMenuOpen = false
            engineMenuOpen -> engineMenuOpen = false
            suggestions.isNotEmpty() -> suggestions.clear()
            editMode -> editMode = false
            else -> return false
        }
        return true
    }

    fun resetTransientUi() {
        addSiteOpen = false
        wallpaperMenuOpen = false
        engineMenuOpen = false
        editMode = false
        suggestions.clear()
        query = TextFieldValue("")
    }

    companion object {
        private const val FEED_REFRESH_MS = 10 * 60 * 1000L
        /** おすすめ記事を一度に出す件数。 */
        const val FEED_PAGE = 10

        /** 入力されたURLを解釈する(旧ページの normalizeUrl と同じく、スキームが無ければ https を付ける)。 */
        fun normalizeUrl(raw: String): URL? {
            var value = raw.trim()
            if (value.isEmpty()) return null
            if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(value)) value = "https://$value"
            return try {
                URL(value).takeIf { !it.host.isNullOrEmpty() }
            } catch (e: Exception) {
                null
            }
        }

        fun originOf(url: URL): String =
            url.protocol + "://" + url.host + if (url.port != -1) ":" + url.port else ""

        /** 検索欄の入力。URL らしければそのまま開き、それ以外は選んだ検索エンジンで検索する。 */
        fun urlForInput(input: String, engine: SearchEngine): String {
            val text = input.trim()
            if (Regex("^(https?|about|file)://", RegexOption.IGNORE_CASE).containsMatchIn(text)) return text
            val looksLikeHost = !text.contains(' ') &&
                Regex("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/.*)?$").matches(text)
            return if (looksLikeHost) "https://$text" else engine.searchUrl(text)
        }
    }
}
