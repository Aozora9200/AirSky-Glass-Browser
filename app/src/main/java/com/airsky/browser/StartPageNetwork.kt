package com.airsky.browser

import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Xml
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * ファビコン・記事の画像用のローダー。
 * 記事のヘッダー画像は 1920px 級なので、表示する幅に縮小してからキャッシュする。
 */
internal object RemoteImages {

    /** バイト数で上限を決める(大きな記事画像が並んでもメモリを使いすぎない)。 */
    private val cache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(48L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun key(url: String, maxWidth: Int) = "$maxWidth|$url"

    fun cached(url: String, maxWidth: Int = 0): ImageBitmap? = cache.get(key(url, maxWidth))

    /** I/O スレッドで呼ぶ。maxWidth が 0 なら縮小しない(ファビコン用)。 */
    fun load(url: String, maxWidth: Int = 0): ImageBitmap? {
        cache.get(key(url, maxWidth))?.let { return it }
        return try {
            val bytes = StartPageNetwork.getBytes(url)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            if (maxWidth > 0) {
                while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample })
                ?.asImageBitmap()
                ?.also { cache.put(key(url, maxWidth), it) }
        } catch (e: Exception) {
            null
        }
    }
}

/** 検索候補・おすすめ記事の取得。load/fetch で始まるものは I/O スレッドで呼ぶ。 */
internal object StartPageNetwork {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36"

    /** Yahoo!ニュース・トピックスの各カテゴリ(記事本文への直接リンクがあり、画像も取れる)。 */
    private val FEED_CATEGORIES = listOf(
        "top-picks", "domestic", "world", "business", "entertainment", "sports", "it", "science", "local"
    )

    /** 記事ページのメタ情報は何度も取らない。 */
    private val metaCache = ConcurrentHashMap<String, ArticleMeta>()
    private val metaExecutor = Executors.newFixedThreadPool(4)

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "ja,en;q=0.8")
        }

    fun getBytes(url: String): ByteArray {
        val conn = open(url)
        try {
            return conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                input.copyTo(out)
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun get(url: String): String = String(getBytes(url), Charsets.UTF_8)

    fun suggestions(query: String): List<String> {
        val body = get("https://suggestqueries.google.com/complete/search?client=firefox&q=" +
            URLEncoder.encode(query, "UTF-8"))
        val words = JSONArray(body).optJSONArray(1) ?: return emptyList()
        return (0 until words.length()).map { words.optString(it) }.filter { it.isNotEmpty() }
    }

    /**
     * おすすめ記事の一覧を作る。各カテゴリの RSS をまとめて読み、カテゴリが偏らないように
     * 新しいものから順に交互に並べる(Discover のようにいろいろな話題が混ざるように)。
     */
    fun fetchFeed(): List<FeedEntry> {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures: List<Future<List<FeedEntry>>> = FEED_CATEGORIES.map { c ->
                pool.submit<List<FeedEntry>> {
                    try {
                        parseRss(get("https://news.yahoo.co.jp/rss/topics/$c.xml"), c)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }
            val byCategory = futures.map { it.get().sortedByDescending { e -> e.publishedAt } }
            if (byCategory.all { it.isEmpty() }) throw java.io.IOException("feed unavailable")

            val seen = HashSet<String>()
            val result = ArrayList<FeedEntry>()
            val maxSize = byCategory.maxOf { it.size }
            for (i in 0 until maxSize) {
                byCategory.mapNotNull { it.getOrNull(i) }
                    .sortedByDescending { it.publishedAt }
                    .forEach { if (seen.add(it.url)) result.add(it) }
            }
            return result
        } finally {
            pool.shutdown()
        }
    }

    private val rssDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

    private fun parseRss(xml: String, category: String): List<FeedEntry> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xml.reader())

        val items = ArrayList<FeedEntry>()
        var inItem = false
        var title: String? = null
        var link: String? = null
        var comments: String? = null
        var date: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        inItem = true; title = null; link = null; comments = null; date = null
                    }
                    "title" -> if (inItem) title = parser.nextText()
                    "link" -> if (inItem) link = parser.nextText()
                    "comments" -> if (inItem) comments = parser.nextText()
                    "pubDate" -> if (inItem) date = parser.nextText()
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    inItem = false
                    val t = title?.trim()
                    val l = link?.trim()
                    if (!t.isNullOrEmpty() && !l.isNullOrEmpty()) {
                        // comments は「記事 URL + /comments」。記事本文を直接開けるようにする
                        val article = comments?.trim()?.removeSuffix("/comments")
                            ?.takeIf { it.startsWith("https://news.yahoo.co.jp/articles/") }
                        val published = try {
                            synchronized(rssDate) { rssDate.parse(date?.trim() ?: "")?.time }
                        } catch (e: Exception) {
                            null
                        } ?: 0L
                        items.add(FeedEntry(t, article ?: l, l, published, category))
                    }
                }
            }
            event = parser.next()
        }
        return items
    }

    fun cachedMeta(entry: FeedEntry): ArticleMeta? = metaCache[entry.url]

    /**
     * 記事ページを読み、Discover と同じくページのヘッダー画像(og:image)・配信元・公開日時を取る。
     * 記事ページで画像が取れなければトピックのページの画像を使う。
     */
    fun fetchMeta(entry: FeedEntry): ArticleMeta {
        metaCache[entry.url]?.let { return it }
        var meta = try {
            parseMeta(get(entry.url))
        } catch (e: Exception) {
            ArticleMeta(null, null, null)
        }
        if (meta.imageUrl == null && entry.topicUrl != entry.url) {
            val topic = try {
                parseMeta(get(entry.topicUrl))
            } catch (e: Exception) {
                null
            }
            meta = meta.copy(imageUrl = topic?.imageUrl)
        }
        metaCache[entry.url] = meta
        return meta
    }

    /** 画面に出る少し前に、次の記事の情報を先読みしておく。 */
    fun prefetchMeta(entries: List<FeedEntry>) {
        entries.filter { !metaCache.containsKey(it.url) }.forEach { e ->
            metaExecutor.execute { fetchMeta(e) }
        }
    }

    private val ogImage = Regex("""<meta[^>]+property="og:image"[^>]+content="([^"]+)"""")
    private val ogImageAlt = Regex("""<meta[^>]+content="([^"]+)"[^>]+property="og:image"""")
    private val authorName = Regex(""""author":\{"@type":"Organization","name":"([^"]+)"""")
    private val ogSiteName = Regex("""<meta[^>]+property="og:site_name"[^>]+content="([^"]+)"""")
    private val datePublished = Regex(""""datePublished":"([^"]+)"""")
    private val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    private fun parseMeta(html: String): ArticleMeta {
        val image = (ogImage.find(html) ?: ogImageAlt.find(html))?.groupValues?.get(1)
            ?.replace("&amp;", "&")
            // サイトのロゴなど汎用の画像は記事の写真ではないので使わない
            ?.takeUnless { it.contains("/logo/") || it.contains("noimage", ignoreCase = true) }
        val source = authorName.find(html)?.groupValues?.get(1)
            ?: ogSiteName.find(html)?.groupValues?.get(1)
        val published = datePublished.find(html)?.groupValues?.get(1)?.let {
            try {
                synchronized(isoDate) { isoDate.parse(it)?.time }
            } catch (e: Exception) {
                null
            }
        }
        return ArticleMeta(image, source?.let { unescape(it) }, published)
    }

    private fun unescape(s: String): String =
        s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">")
}
