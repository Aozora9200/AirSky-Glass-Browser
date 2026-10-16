package com.airsky.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * アドレスバーを押した時に、アドレスバーの下へ重ねて出す候補の画面(Chrome のアドレスバーの候補と同じ作り)。
 *
 * 入力前: 今のページ / コピーしたリンク / 履歴から推測したサイト(検索サイトを開いている時)/ 最近の検索
 * 入力中: コピーしたリンク / 入力した内容・以前に開いたページ・検索候補
 */
class UrlBarSuggestionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface Host {
        /** 候補を開く(URL ならそのまま、言葉なら検索)。 */
        fun onSubmit(text: String)

        /** ↖ ボタン。アドレスバーに文字を入れて続きを入力できるようにする。 */
        fun onFill(text: String)

        fun onSharePage(title: String, url: String)

        fun onCopyUrl(url: String)

        /** 最近の検索から消す。 */
        fun onDeleteSearch(query: String)

        /** 候補の無い所を押した。アドレスバーの入力をやめる。 */
        fun onDismiss()
    }

    /** 今開いているページ。 */
    class Page(val title: String, val url: String, val icon: Bitmap?)

    /** 履歴から推測したサイト。 */
    class Site(val label: String, val url: String, val icon: Bitmap?)

    private val state = UrlBarState()

    init {
        addView(ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { UrlBarSuggestions(state) }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setHost(host: Host?) {
        state.host = host
    }

    fun isShowing(): Boolean = state.showing

    /** 出す(アニメーション付き)/ 隠す。 */
    fun setShowing(show: Boolean) {
        if (show && !state.showing) {
            state.shownAt = SystemClock.uptimeMillis()
            state.showKey++
        }
        state.showing = show
    }

    /** 入力前の表示。 */
    fun showHistory(page: Page?, clipboardUrl: String?, sites: List<Site>, searches: List<String>) {
        state.query = ""
        state.page = page
        state.clipboardUrl = clipboardUrl
        state.sites.clear()
        state.sites.addAll(sites)
        state.items.clear()
        state.items.addAll(searches)
        state.visited.clear()
    }

    /**
     * 入力中の表示。通信を待たずに入力内容と、閲覧履歴から入力に合うページ(visited)を先に出す。
     */
    fun showQuery(query: String, clipboardUrl: String?, visited: List<Page>) {
        state.query = query.trim()
        state.page = null
        state.clipboardUrl = clipboardUrl
        state.sites.clear()
        state.items.clear()
        if (state.query.isNotEmpty()) state.items.add(state.query)
        state.visited.clear()
        if (state.query.isNotEmpty()) state.visited.addAll(visited)
    }

    /** 検索候補を差し替える。forQuery が今の入力と違う(古い通信の結果)なら無視する。 */
    fun setSuggestions(forQuery: String, suggestions: List<String>) {
        val q = forQuery.trim()
        if (q != state.query || q.isEmpty()) return
        val result = LinkedHashSet<String>()
        result.add(q)
        suggestions.forEach { s ->
            val t = s.trim()
            // 入力と同じ候補は先頭と重複するので除く
            if (t.isNotEmpty() && !t.equals(q, ignoreCase = true)) result.add(t)
        }
        state.items.clear()
        state.items.addAll(result.take(9))
    }

    /** ソフトキーボードの高さ(候補が隠れないように、その分だけ下に余白を空ける)。 */
    fun setBottomInset(px: Int) {
        state.bottomInset = px
    }

    internal fun confirmDelete(query: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(query)
            .setMessage(Lang.t("履歴から候補を削除しますか？"))
            .setNegativeButton(Lang.t("キャンセル"), null)
            .setPositiveButton("OK") { _, _ -> state.host?.onDeleteSearch(query) }
            .show()
    }

    private inner class UrlBarState {
        var host: Host? = null
        var showing by mutableStateOf(false)
        var showKey by mutableStateOf(0)
        var shownAt = 0L
        var query by mutableStateOf("")
        var page by mutableStateOf<Page?>(null)
        var clipboardUrl by mutableStateOf<String?>(null)
        val sites = mutableStateListOf<Site>()
        /** 入力前は最近の検索、入力中は入力内容と検索候補。 */
        val items = mutableStateListOf<String>()
        /** 入力中だけ: 入力に合う、以前に開いたページ。 */
        val visited = mutableStateListOf<Page>()
        var bottomInset by mutableStateOf(0)
        val confirmDelete: (String) -> Unit = { this@UrlBarSuggestionsView.confirmDelete(it) }
    }

    // ---------------------------------------------------------------------------------------------
    // 画面
    // ---------------------------------------------------------------------------------------------

    @Composable
    private fun UrlBarSuggestions(s: UrlBarState) {
        val c = rememberOmniColors()
        AnimatedVisibility(
            visible = s.showing,
            enter = fadeIn(tween(160)) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { -it / 16 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 32 }
        ) {
            val density = LocalDensity.current
            val bottom = with(density) { s.bottomInset.toDp() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.background)
                    .clickable(remember { MutableInteractionSource() }, null) { s.host?.onDismiss() }
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = bottom + 16.dp)
                ) {
                    var index = 0
                    val history = s.query.isEmpty()
                    s.page?.let { page ->
                        val i = index++
                        item(key = "page") { Staggered(i, s, itemMotion()) { PageCard(page, c, s) } }
                    }
                    s.clipboardUrl?.let { url ->
                        val i = index++
                        item(key = "clip") { Staggered(i, s, itemMotion()) { ClipboardCard(url, c, s) } }
                    }
                    if (history && s.sites.isNotEmpty()) {
                        val i = index++
                        item(key = "sites") { Staggered(i, s, itemMotion()) { SitesRow(s.sites.toList(), c, s) } }
                    }
                    if (history && s.items.isNotEmpty()) {
                        val i = index++
                        item(key = "header") {
                            Staggered(i, s, itemMotion()) {
                                BasicText(
                                    Lang.t("最近の検索"),
                                    style = TextStyle(color = c.onSurfaceVariant, fontSize = 14.sp),
                                    modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 10.dp)
                                )
                            }
                        }
                    } else if (s.page != null || s.clipboardUrl != null) {
                        item(key = "gap") { Spacer(itemMotion().height(10.dp)) }
                    }
                    val items = s.items.toList()
                    // 入力中は、入力した内容の行のすぐ下に以前に開いたページを並べ、その下に検索候補を続ける
                    val rows: List<Any> = if (history) items else buildList {
                        items.firstOrNull()?.let { add(it) }
                        addAll(s.visited.toList())
                        addAll(items.drop(1))
                    }
                    rows.forEachIndexed { n, row ->
                        val i = index++
                        val first = n == 0
                        val last = n == rows.size - 1
                        if (row is Page) {
                            item(key = "v:${row.url}") {
                                Staggered(i, s, itemMotion()) { VisitedRow(row, first, last, c, s) }
                            }
                            return@forEachIndexed
                        }
                        val text = row as String
                        // 入力中の先頭の行(入力した内容)は同じ行のまま文字だけ変える。
                        // 候補の行は文字ごとに別の行にして、入れ替わる時に動かす
                        val key = when {
                            history -> "h:$text"
                            n == 0 -> "q-input"
                            else -> "q:$text"
                        }
                        item(key = key) {
                            Staggered(i, s, itemMotion()) {
                                SuggestionRow(
                                    text = text,
                                    icon = when {
                                        history -> R.drawable.ic_omni_history
                                        looksLikeUrl(text) -> R.drawable.ic_omni_globe
                                        else -> R.drawable.ic_omni_search
                                    },
                                    first = first,
                                    last = last,
                                    c = c,
                                    onClick = { s.host?.onSubmit(text) },
                                    onLongClick = if (history) ({ s.confirmDelete(text) }) else null,
                                    onFill = { s.host?.onFill(text) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 候補の行が増えた・減った・並びが変わった時の動き。
     * 増えた行はふわっと出し、消える行は薄くし、残った行は新しい位置へばねで滑らせる。
     */
    private fun LazyItemScope.itemMotion(): Modifier = Modifier.animateItem(
        fadeInSpec = tween(220, delayMillis = 40, easing = FastOutSlowInEasing),
        placementSpec = spring(
            dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = IntOffset.VisibilityThreshold
        ),
        fadeOutSpec = tween(120)
    )

    /**
     * 出てきた直後は、上から順に少しずつずらしてふわっと出す。
     * 表示中に候補が届いて加わった行は、少し上から下りてきて、わずかに縮んだ所から元の大きさに戻す。
     */
    @Composable
    private fun Staggered(index: Int, s: UrlBarState, modifier: Modifier, content: @Composable () -> Unit) {
        val fresh = SystemClock.uptimeMillis() - s.shownAt < 300
        val anim = remember(s.showKey) { Animatable(if (fresh) 0f else 1f) }
        // 表示の途中で加わった行だけ動かす(出てきた直後の行は上の anim に任せる)
        val arrive = remember { Animatable(if (fresh) 1f else 0f) }
        LaunchedEffect(s.showKey) {
            if (anim.value < 1f) {
                delay(40L + index * 30L)
                anim.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            }
        }
        LaunchedEffect(Unit) {
            if (arrive.value < 1f) {
                arrive.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
            }
        }
        Box(modifier.graphicsLayer {
            alpha = anim.value
            translationY = (1f - anim.value) * 14.dp.toPx() - (1f - arrive.value) * 10.dp.toPx()
            val scale = 0.96f + 0.04f * arrive.value
            scaleX = scale
            scaleY = scale
        }) { content() }
    }

    @Composable
    private fun PageCard(page: Page, c: OmniColors, s: UrlBarState) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(c.card)
                .clickable { s.host?.onSubmit(page.url) }
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SiteIcon(page.url, page.icon, Modifier.size(26.dp).clip(CircleShape))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    page.title.ifEmpty { displayUrl(page.url) },
                    style = TextStyle(color = c.onSurface, fontSize = 17.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    displayUrl(page.url),
                    style = TextStyle(color = c.primary, fontSize = 15.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(R.drawable.ic_omni_share, Lang.t("共有"), c) { s.host?.onSharePage(page.title, page.url) }
            IconButton(R.drawable.ic_omni_copy, Lang.t("コピー"), c) { s.host?.onCopyUrl(page.url) }
            IconButton(R.drawable.ic_omni_edit, Lang.t("編集"), c) { s.host?.onFill(page.url) }
        }
    }

    @Composable
    private fun ClipboardCard(url: String, c: OmniColors, s: UrlBarState) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(c.card)
                .clickable { s.host?.onSubmit(url) }
                .padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(R.drawable.ic_omni_link, c.onSurfaceVariant, Modifier.size(24.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                BasicText(Lang.t("コピーしたリンク"), style = TextStyle(color = c.onSurface, fontSize = 17.sp), maxLines = 1)
                Spacer(Modifier.height(2.dp))
                BasicText(
                    displayUrl(url),
                    style = TextStyle(color = c.primary, fontSize = 15.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(R.drawable.ic_omni_north_west, Lang.t("編集"), c) { s.host?.onFill(url) }
        }
    }

    @Composable
    private fun SitesRow(sites: List<Site>, c: OmniColors, s: UrlBarState) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
            sites.take(SITE_COUNT).forEach { site ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { s.host?.onSubmit(site.url) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(c.card), contentAlignment = Alignment.Center) {
                        SiteIcon(site.url, site.icon, Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)))
                    }
                    Spacer(Modifier.height(10.dp))
                    BasicText(
                        site.label,
                        style = TextStyle(color = c.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            repeat(SITE_COUNT - sites.size.coerceAtMost(SITE_COUNT)) { Spacer(Modifier.weight(1f)) }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun SuggestionRow(
        text: String,
        icon: Int,
        first: Boolean,
        last: Boolean,
        c: OmniColors,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)?,
        onFill: () -> Unit
    ) {
        val haptics = LocalHapticFeedback.current
        // 行が増減して端の行が変わった時は、角の丸みもなめらかに付け替える
        val top by animateDpAsState(if (first) BIG_CORNER else SMALL_CORNER, tween(260), label = "top")
        val bottom by animateDpAsState(if (last) BIG_CORNER else SMALL_CORNER, tween(260), label = "bottom")
        val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .clip(shape)
                .background(c.card)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick?.let { f ->
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            f()
                        }
                    }
                )
                .heightIn(min = 64.dp)
                .padding(start = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, c.onSurfaceVariant, Modifier.size(24.dp))
            Spacer(Modifier.width(18.dp))
            BasicText(
                text,
                style = TextStyle(color = c.onSurface, fontSize = 17.sp, lineHeight = 24.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp)
            )
            IconButton(R.drawable.ic_omni_north_west, Lang.t("入力欄に入れる"), c, onFill)
        }
    }

    /** 以前に開いたページの行(ファビコン・タイトル・URL)。検索候補の行と一続きに並べる。 */
    @Composable
    private fun VisitedRow(page: Page, first: Boolean, last: Boolean, c: OmniColors, s: UrlBarState) {
        val top by animateDpAsState(if (first) BIG_CORNER else SMALL_CORNER, tween(260), label = "top")
        val bottom by animateDpAsState(if (last) BIG_CORNER else SMALL_CORNER, tween(260), label = "bottom")
        val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .clip(shape)
                .background(c.card)
                .clickable { s.host?.onSubmit(page.url) }
                .heightIn(min = 64.dp)
                .padding(start = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SiteIcon(page.url, page.icon, Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                BasicText(
                    page.title.ifEmpty { displayUrl(page.url) },
                    style = TextStyle(color = c.onSurface, fontSize = 17.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    displayUrl(page.url),
                    style = TextStyle(color = c.primary, fontSize = 14.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(R.drawable.ic_omni_north_west, Lang.t("入力欄に入れる"), c) { s.host?.onFill(page.url) }
        }
    }

    @Composable
    private fun IconButton(icon: Int, description: String, c: OmniColors, onClick: () -> Unit) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painterResource(icon), description,
                colorFilter = ColorFilter.tint(c.onSurface),
                modifier = Modifier.size(24.dp)
            )
        }
    }

    @Composable
    private fun Icon(icon: Int, color: Color, modifier: Modifier) {
        Image(painterResource(icon), null, colorFilter = ColorFilter.tint(color), modifier = modifier)
    }

    /** 手元のファビコンを使い、無ければネットから取ってくる。 */
    @Composable
    private fun SiteIcon(url: String, local: Bitmap?, modifier: Modifier) {
        val remoteUrl = remember(url) {
            val uri = Uri.parse(url)
            if (uri.host.isNullOrEmpty()) null
            else Shortcut.faviconUrlFor(uri.scheme + "://" + uri.host)
        }
        val image by produceState<ImageBitmap?>(local?.asImageBitmap() ?: remoteUrl?.let { RemoteImages.cached(it) }, url, local) {
            if (value == null && remoteUrl != null) {
                value = withContext(Dispatchers.IO) { RemoteImages.load(remoteUrl) }
            }
        }
        Box(modifier) {
            image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
    }

    private companion object {
        const val SITE_COUNT = 4

        /** 一続きの行の角。両端の行だけ大きく丸め、間は小さく丸める(Material 3 Expressive のリスト)。 */
        val BIG_CORNER = 24.dp
        val SMALL_CORNER = 4.dp

        fun looksLikeUrl(text: String): Boolean =
            Regex("^(https?://)?[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/\\S*)?$", RegexOption.IGNORE_CASE).matches(text.trim())

        /** 表示用の URL(先頭の https:// と www. は省く)。 */
        fun displayUrl(url: String): String =
            url.replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "").removePrefix("www.")
    }
}

/** 候補の画面の色。アプリのテーマ(Material 3)の色を使う。 */
private class OmniColors(
    val background: Color,
    val card: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color
)

@Composable
private fun rememberOmniColors(): OmniColors {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    return remember(context, dark) {
        fun attr(id: Int, fallback: Long): Color {
            val tv = TypedValue()
            if (!context.theme.resolveAttribute(id, tv, true)) return Color(fallback)
            val color = if (tv.resourceId != 0) {
                try {
                    androidx.core.content.ContextCompat.getColor(context, tv.resourceId)
                } catch (e: Exception) {
                    return Color(fallback)
                }
            } else tv.data
            return Color(color)
        }
        OmniColors(
            background = attr(com.google.android.material.R.attr.colorSurfaceContainer,
                if (dark) 0xFF1E1F25 else 0xFFEDEDF4),
            // ダークでは背景より明るい面にしないとカードが沈んで見える
            card = if (dark) attr(com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF33353A)
            else attr(com.google.android.material.R.attr.colorSurfaceContainerLowest, 0xFFFFFFFF),
            onSurface = attr(com.google.android.material.R.attr.colorOnSurface,
                if (dark) 0xFFE3E2E9 else 0xFF1A1B21),
            onSurfaceVariant = attr(com.google.android.material.R.attr.colorOnSurfaceVariant,
                if (dark) 0xFFC5C6D0 else 0xFF44464F),
            primary = attr(androidx.appcompat.R.attr.colorPrimary,
                if (dark) 0xFFADC6FF else 0xFF415F91)
        )
    }
}
