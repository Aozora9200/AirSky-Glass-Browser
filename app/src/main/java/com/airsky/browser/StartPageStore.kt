package com.airsky.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.util.LruCache
import android.util.Xml
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt

/** スタートページの検索エンジン(旧 index.html の searchEngines と同じ)。 */
internal data class SearchEngine(
    val key: String,
    val label: String,
    val action: String,
    val param: String,
    val placeholder: String
) {
    fun searchUrl(query: String): String =
        "$action?$param=${URLEncoder.encode(query, "UTF-8")}"

    companion object {
        val ALL = listOf(
            SearchEngine("google", "Google", "https://www.google.com/search", "q", Lang.t("Googleで検索")),
            SearchEngine("bing", "Bing", "https://www.bing.com/search", "q", Lang.t("Bingで検索")),
            SearchEngine("yahoo", "Yahoo", "https://search.yahoo.co.jp/search", "p", Lang.t("Yahooで検索")),
            SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/", "q", Lang.t("DuckDuckGoで検索")),
            SearchEngine("ecosia", "Ecosia", "https://www.ecosia.org/search", "q", Lang.t("Ecosiaで検索")),
            SearchEngine("youtube", "YouTube", "https://www.youtube.com/results", "search_query", Lang.t("YouTubeで検索"))
        )

        fun of(key: String?): SearchEngine = ALL.firstOrNull { it.key == key } ?: ALL[0]
    }
}

/** ホーム画面風のサイトアイコン。 */
internal data class Shortcut(
    val name: String,
    val url: String,
    val icon: String,
    val whiteBackground: Boolean = false
) {
    companion object {
        fun faviconUrlFor(origin: String): String =
            "https://t0.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=" +
                URLEncoder.encode(origin, "UTF-8") + "&size=64"

        val DEFAULTS = listOf(
            Shortcut("Facebook", "https://www.facebook.com", faviconUrlFor("https://www.facebook.com/")),
            Shortcut("Google", "https://www.google.com/?pli=1", faviconUrlFor("https://www.google.com/")),
            Shortcut("Yahoo", "https://www.yahoo.co.jp", faviconUrlFor("https://www.yahoo.co.jp/")),
            Shortcut("Instagram", "https://www.instagram.com", faviconUrlFor("https://www.instagram.com/")),
            Shortcut("X", "https://x.com",
                "https://play-lh.googleusercontent.com/XyI6Hyz9AFg7E_joVzX2zh6CpWm9B2DG2JuEz5meCFVm4-wTKTnHgqbmg62iFKe4Gzca"),
            Shortcut(Lang.t("楽天市場"), "https://www.rakuten.co.jp", faviconUrlFor("https://www.rakuten.co.jp/"), true),
            Shortcut(Lang.t("楽天ブックス"), "https://books.rakuten.co.jp", faviconUrlFor("https://books.rakuten.co.jp/"), true),
            Shortcut("Wikipedia", "https://ja.wikipedia.org", faviconUrlFor("https://ja.wikipedia.org/")),
            Shortcut(Lang.t("ニコニコ"), "https://www.nicovideo.jp",
                "https://play-lh.googleusercontent.com/cGBrXFxZVesu51M5C3Y2r04l84Z4UiENmBFbGSGomGY9-5aW8QwEXMfWHXczcG_bBQ", true),
            Shortcut("YouTube", "https://www.youtube.com", faviconUrlFor("https://www.youtube.com"), true)
        )
    }
}

/** Discover(おすすめ記事)の 1 件。画像や配信元は表示する時に記事ページから取ってくる。 */
internal data class FeedEntry(
    val title: String,
    /** 開く URL(記事本文。無ければトピックのページ)。 */
    val url: String,
    /** RSS のトピックページ(記事ページが読めない時の画像の取り先)。 */
    val topicUrl: String,
    val publishedAt: Long,
    val category: String
)

/** 記事ページから読み取った、カードに出す情報(Discover と同じくサイトのヘッダー画像など)。 */
internal data class ArticleMeta(
    val imageUrl: String?,
    val source: String?,
    val publishedAt: Long?
)

/** スタートページの保存データ(検索エンジン・追加したサイト・壁紙)。 */
internal class StartPageStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("start_page", Context.MODE_PRIVATE)

    /** 「壁紙を設定」で選んだ画像(縮小して保存したもの)。 */
    val wallpaperFile = File(appContext.filesDir, "start_page_wallpaper.jpg")

    var engine: SearchEngine
        get() = SearchEngine.of(prefs.getString(KEY_ENGINE, null))
        set(value) = prefs.edit().putString(KEY_ENGINE, value.key).apply()

    fun loadCustomSites(): List<Shortcut> {
        val json = prefs.getString(KEY_SITES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url")
                if (url.isEmpty()) null
                else Shortcut(o.optString("name", url), url, o.optString("icon"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomSites(sites: List<Shortcut>) {
        val array = JSONArray()
        sites.forEach {
            array.put(JSONObject().put("name", it.name).put("url", it.url).put("icon", it.icon))
        }
        prefs.edit().putString(KEY_SITES, array.toString()).apply()
    }

    /** 「表示しない」「興味なし」にした記事の URL(新しいものを後ろに、多くなりすぎたら古いものから捨てる)。 */
    fun loadHiddenArticles(): Set<String> = loadStrings(KEY_HIDDEN_ARTICLES)

    fun saveHiddenArticles(urls: Set<String>) =
        saveStrings(KEY_HIDDEN_ARTICLES, urls.toList().takeLast(MAX_HIDDEN_ARTICLES))

    /** 「◯◯のコンテンツを表示しない」にした配信元。 */
    fun loadBlockedSources(): Set<String> = loadStrings(KEY_BLOCKED_SOURCES)

    fun saveBlockedSources(sources: Set<String>) = saveStrings(KEY_BLOCKED_SOURCES, sources.toList())

    /** カテゴリごとに「興味なし」を選んだ回数。 */
    fun loadNotInterested(): Map<String, Int> {
        val json = prefs.getString(KEY_NOT_INTERESTED, null) ?: return emptyMap()
        return try {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.optInt(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun addNotInterested(category: String) {
        val counts = loadNotInterested().toMutableMap()
        counts[category] = (counts[category] ?: 0) + 1
        prefs.edit().putString(KEY_NOT_INTERESTED, JSONObject(counts as Map<*, *>).toString()).apply()
    }

    private fun loadStrings(key: String): Set<String> {
        val json = prefs.getString(key, null) ?: return emptySet()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNullTo(LinkedHashSet()) { array.optString(it).ifEmpty { null } }
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun saveStrings(key: String, values: List<String>) {
        val array = JSONArray()
        values.forEach { array.put(it) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    fun hasCustomWallpaper(): Boolean = wallpaperFile.exists()

    /** 保存済みの壁紙を画面の大きさで読み込む(I/O スレッドで呼ぶ)。 */
    fun loadCustomWallpaper(): ImageBitmap? {
        if (!wallpaperFile.exists()) return null
        val (w, h) = WallpaperBitmaps.screenSize(appContext)
        return WallpaperBitmaps.decodeCover({ wallpaperFile.inputStream() }, w, h)?.asImageBitmap()
    }

    /**
     * 選んだ画像を縮小して保存する(I/O スレッドで呼ぶ)。
     * 旧ページは大きい画像を縮小して localStorage に入れていたので、ここでも長辺を抑えて保存する。
     */
    fun saveCustomWallpaper(uri: Uri): ImageBitmap? {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_WALLPAPER_EDGE) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val tmp = File(wallpaperFile.path + ".tmp")
        FileOutputStream(tmp).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        if (!tmp.renameTo(wallpaperFile)) {
            tmp.copyTo(wallpaperFile, overwrite = true)
            tmp.delete()
        }
        return loadCustomWallpaper()
    }

    fun clearCustomWallpaper() {
        wallpaperFile.delete()
    }

    companion object {
        private const val KEY_ENGINE = "engine"
        private const val KEY_SITES = "custom_sites"
        private const val KEY_HIDDEN_ARTICLES = "hidden_articles"
        private const val KEY_BLOCKED_SOURCES = "blocked_sources"
        private const val KEY_NOT_INTERESTED = "not_interested"
        private const val MAX_HIDDEN_ARTICLES = 300
        private const val MAX_WALLPAPER_EDGE = 2048
    }
}

/** 時間帯で切り替わる壁紙(旧 index.html の WALLPAPER_SCHEDULE と同じ)。 */
internal object AutoWallpaper {

    /** hour から次の項目の hour まで有効。0:00〜4:00 は最後の枠が続く。 */
    private val SCHEDULE = listOf(
        4 to "browser/assets/img/yugure.png",       // 4:00〜6:00
        6 to "browser/assets/img/bg-masthead.jpg",  // 6:00〜16:00
        16 to "browser/assets/img/yugure.png",      // 16:00〜18:00
        18 to "browser/assets/img/nightsky.png"     // 18:00〜翌4:00
    )

    private val cache = LruCache<String, ImageBitmap>(2)

    fun assetFor(now: Calendar): String {
        val hour = now.get(Calendar.HOUR_OF_DAY)
        var pick = SCHEDULE.last().second
        for ((h, src) in SCHEDULE) if (hour >= h) pick = src
        return pick
    }

    fun millisUntilNextChange(now: Calendar): Long {
        var min = Long.MAX_VALUE
        for ((h, _) in SCHEDULE) {
            val t = now.clone() as Calendar
            t.set(Calendar.HOUR_OF_DAY, h)
            t.set(Calendar.MINUTE, 0)
            t.set(Calendar.SECOND, 0)
            t.set(Calendar.MILLISECOND, 0)
            if (!t.after(now)) t.add(Calendar.DAY_OF_MONTH, 1)
            min = minOf(min, t.timeInMillis - now.timeInMillis)
        }
        return min + 250
    }

    /**
     * 画面の大きさに縮小・切り抜きして読み込む(I/O スレッドで呼ぶ)。
     * 元画像は最大 5632x3072 あり、そのまま使うと 1 枚 70MB 近いテクスチャになって
     * ガラスのぼかしのたびに読まれるため、表示に必要な分だけにする。
     */
    fun load(context: Context, asset: String): ImageBitmap? {
        val (w, h) = WallpaperBitmaps.screenSize(context)
        val key = "$asset@${w}x$h"
        cache.get(key)?.let { return it }
        return try {
            WallpaperBitmaps.decodeCover({ context.assets.open(asset) }, w, h)?.asImageBitmap()
                ?.also { cache.put(key, it) }
        } catch (e: Exception) {
            null
        }
    }
}

/** 壁紙用に、大きな画像を画面を覆う大きさまで縮小して中央を切り抜く。 */
internal object WallpaperBitmaps {

    /** 今の向きでの画面の大きさ(px)。 */
    fun screenSize(context: Context): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        return dm.widthPixels.coerceAtLeast(1) to dm.heightPixels.coerceAtLeast(1)
    }

    /**
     * ContentScale.Crop と同じく、画面を覆う倍率で中央を切り抜いた画像を作る(拡大はしない)。
     * 切り抜く範囲だけをデコードするので、巨大な画像でも全体をメモリに展開しない。
     */
    @Suppress("DEPRECATION")
    fun decodeCover(open: () -> InputStream, reqW: Int, reqH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null

        val scale = max(reqW / w.toFloat(), reqH / h.toFloat())
        val cropW = (reqW / scale).roundToInt().coerceIn(1, w)
        val cropH = (reqH / scale).roundToInt().coerceIn(1, h)
        val region = Rect((w - cropW) / 2, (h - cropH) / 2, (w - cropW) / 2 + cropW, (h - cropH) / 2 + cropH)

        // 画面を覆える範囲で、できるだけ小さくデコードする
        var sample = 1
        while (cropW / (sample * 2) >= reqW && cropH / (sample * 2) >= reqH) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = try {
            open().use { BitmapRegionDecoder.newInstance(it, false)?.let { d ->
                try { d.decodeRegion(region, options) } finally { d.recycle() }
            } }
        } catch (e: Exception) {
            null
        } ?: open().use {
            // 部分デコードできない形式は全体を読んでから切り抜く
            val full = BitmapFactory.decodeStream(it, null, options) ?: return null
            val r = Rect(region.left / sample, region.top / sample,
                (region.right / sample).coerceAtMost(full.width), (region.bottom / sample).coerceAtMost(full.height))
            Bitmap.createBitmap(full, r.left, r.top, r.width().coerceAtLeast(1), r.height().coerceAtLeast(1))
                .also { if (it !== full) full.recycle() }
        }

        val f = max(reqW / decoded.width.toFloat(), reqH / decoded.height.toFloat())
        if (f >= 1f) return decoded
        val out = Bitmap.createScaledBitmap(decoded,
            (decoded.width * f).roundToInt().coerceAtLeast(1), (decoded.height * f).roundToInt().coerceAtLeast(1), true)
        if (out !== decoded) decoded.recycle()
        return out
    }
}

