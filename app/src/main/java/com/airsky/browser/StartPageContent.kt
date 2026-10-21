package com.airsky.browser

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Calendar
import kotlin.random.Random

/** ガラスの効果(ぼかし)が使える端末か。使えない端末ではガラスを濃くして読みやすくする。 */
private val GLASS_EFFECTS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** 旧 index.html の CSS 変数(--accent, --glass, --ink ...)に相当する色。 */
internal class StartColors(val dark: Boolean, glassEnabled: Boolean = true) {
    val accent = if (dark) Color(0xFF7FCBF5) else Color(0xFF1C74B5)
    val accentStrong = if (dark) Color(0xFFA9DEF8) else Color(0xFF145A8D)
    /** accent で塗ったボタンの文字色。ダークでは明るい水色に白文字だと読めないので濃紺にする。 */
    val onAccent = if (dark) Color(0xFF052238) else Color.White
    // ガラスを使わない設定では、白(ダークでは黒)を少しだけ透かした面にする
    val glass = when {
        !glassEnabled -> (if (dark) Color.Black else Color.White).copy(alpha = 0.80f)
        dark -> Color(13, 33, 50).copy(alpha = if (GLASS_EFFECTS) 0.40f else 0.55f)
        else -> Color.White.copy(alpha = if (GLASS_EFFECTS) 0.36f else 0.58f)
    }
    val glassStrong = when {
        !glassEnabled -> (if (dark) Color.Black else Color.White).copy(alpha = 0.88f)
        dark -> Color(13, 33, 50).copy(alpha = if (GLASS_EFFECTS) 0.62f else 0.78f)
        else -> Color.White.copy(alpha = if (GLASS_EFFECTS) 0.62f else 0.82f)
    }
    val glassBorder = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.75f)
    val ink = if (dark) Color(0xFFE7F3FB) else Color(0xFF12314A)
    val inkSoft = if (dark) Color(0xFFB9D4E5) else Color(0xFF3B5A70)
    val base = if (dark) Color.Black else Color.White
    val title = if (dark) Color.White else Color.Black
    val error = Color(0xFFC0392B)
    /** Discover のカード(ガラスにはしない。枚数が多くても軽いように)。 */
    val card = if (dark) Color(0xFF1C2630).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.94f)
    val placeholder = if (dark) Color.White.copy(alpha = 0.08f) else Color(0xFF12314A).copy(alpha = 0.07f)
}

private val GlassShadow: () -> Shadow? = { Shadow.Default }

/** ガラスのデザインの設定([GlassPrefs])。 */
internal data class GlassStyle(val enabled: Boolean, val refraction: Boolean)

internal val LocalGlassStyle = staticCompositionLocalOf { GlassStyle(enabled = true, refraction = true) }

/**
 * ガラス(ぼかし+彩度アップ、必要ならレンズ屈折)で背景を透かして描く。
 *
 * ガラスの部品はスクロールのたびに背景を取り直してぼかし直すので、数と面積がそのまま重さになる。
 * レンズ(屈折シェーダー)と影は特に重いので、目立つ小さな部品にだけ付ける(既定はなし)。
 * 屈折は設定でオフにでき、ガラス自体をオフにした時は [surface] で塗るだけの面になる。
 */
@Composable
private fun Modifier.glass(
    backdrop: Backdrop,
    shape: Shape,
    surface: Color,
    blurRadius: Float = 6f,
    lensHeight: Float = 0f,
    lensAmount: Float = 0f,
    shadow: Boolean = false,
    pressScale: (() -> Float)? = null
): Modifier {
    val style = LocalGlassStyle.current
    val layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)? = pressScale?.let { scale ->
        {
            val s = scale()
            scaleX = s
            scaleY = s
        }
    }
    if (!style.enabled) {
        return this
            .then(if (layerBlock != null) Modifier.graphicsLayer(layerBlock) else Modifier)
            .then(if (shadow) Modifier.shadow(6.dp, shape, clip = false) else Modifier)
            .background(surface, shape)
    }
    val useLens = style.refraction && lensHeight > 0f
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.dp.toPx())
            if (useLens) lens(lensHeight.dp.toPx(), lensAmount.dp.toPx())
        },
        shadow = if (shadow) GlassShadow else null,
        layerBlock = layerBlock,
        onDrawSurface = { drawRect(surface) }
    )
}

@Composable
private fun rememberPressScale(interaction: MutableInteractionSource): () -> Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 1.06f else 1f, tween(160), label = "press")
    return { scale }
}

/** スタートページを表示する時のアニメーションの長さ。 */
private const val APPEAR_MS = 750

/** 表示アニメーションで、それぞれの部品が動いている長さ(全体に対する割合)。 */
private const val APPEAR_PART = 0.64f

/**
 * 表示アニメーション用。全体の進み具合 [progress](0〜1)のうち [start] から始まって、
 * 下から少し浮き上がりながらフェードインする。描画時に読むので毎フレーム再コンポーズしない。
 */
private fun Modifier.appearing(progress: () -> Float, start: Float): Modifier = graphicsLayer {
    val t = ((progress() - start) / APPEAR_PART).coerceIn(0f, 1f)
    val p = FastOutSlowInEasing.transform(t)
    alpha = p
    translationY = (1f - p) * 28.dp.toPx()
    val s = 0.96f + 0.04f * p
    scaleX = s
    scaleY = s
}

@Composable
internal fun StartPage(state: StartPageState) {
    val glassStyle = GlassStyle(state.glassEnabled, state.glassRefraction)
    val colors = StartColors(isSystemInDarkTheme(), glassStyle.enabled)
    val backdrop = rememberLayerBackdrop()
    val focusManager = LocalFocusManager.current

    // 表示されるたびに、壁紙が少し引いて(ズームアウト)部品が上から順にふわっと出てくる。
    // appearKey が変わった再コンポーズで 0 から作り直すので、最初の 1 フレームから隠れた状態で描かれる
    val appear = remember(state.appearKey) { Animatable(if (state.appearKey == 0) 1f else 0f) }
    LaunchedEffect(state.appearKey) {
        appear.animateTo(1f, tween(APPEAR_MS, easing = LinearEasing))
    }
    val progress = { appear.value }

    CompositionLocalProvider(LocalGlassStyle provides glassStyle) {
    Box(Modifier.fillMaxSize().background(colors.base)) {
        // 背景(壁紙)。ガラスの部品はここを透かして描く
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Wallpaper(state, colors, progress)
        }

        // 記事が何十件にもなるので、画面に出ている分だけを作る LazyColumn にする
        LazyColumn(
            state = state.listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "top", contentType = "top") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(40.dp))
                    Box(Modifier.appearing(progress, 0f)) { SearchArea(state, colors, backdrop) }
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.appearing(progress, 0.18f)) { ShortcutGrid(state, colors, backdrop) }
                    Spacer(Modifier.height(28.dp))
                }
            }
            // ニュース(おすすめ記事)は設定でオフにできる
            if (state.newsEnabled) discoverFeed(state, colors, Modifier.appearing(progress, 0.36f))
            item(key = "bottom", contentType = "bottom") { Spacer(Modifier.height(32.dp)) }
        }

        // 下の方まで来たら次の 10 件を読み込む
        val nearEnd by remember {
            derivedStateOf {
                val info = state.listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
            }
        }
        LaunchedEffect(nearEnd, state.feedShown, state.feed) {
            if (nearEnd) state.loadMore()
        }

        // 壁紙の設定は目立たないよう右上の小さなボタンにまとめる
        WallpaperCorner(state, colors, backdrop)

        // 雪がふわふわ舞う演出(旧ページの .fluffy)
        Snowfall(state, colors)

        // 「◯◯ に変更しました」のお知らせ
        AnimatedVisibility(
            visible = state.notification != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp)
        ) {
            BasicText(
                text = state.lastNotification,
                style = TextStyle(color = colors.ink, fontSize = 13.sp),
                modifier = Modifier
                    .glass(backdrop, RoundedCornerShape(50), colors.glassStrong)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        AnimatedVisibility(
            visible = state.addSiteOpen,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(180))
        ) {
            AddSiteDialog(state, colors, backdrop)
        }
    }
    }

    LaunchedEffect(state.visible) {
        if (!state.visible) focusManager.clearFocus(force = true)
    }

    // タブを切り替えた時など、そのタブで見ていた位置(新しいタブなら一番上)へ移る
    LaunchedEffect(state.scrollRequest) {
        val request = state.scrollRequest ?: return@LaunchedEffect
        state.listState.scrollToItem(request[0], request[1])
        state.scrollRequest = null
    }
}

// ---------------------------------------------------------------------------------------------
// 壁紙
// ---------------------------------------------------------------------------------------------

@Composable
private fun Wallpaper(state: StartPageState, colors: StartColors, appear: () -> Float) {
    val context = LocalContext.current
    val custom = state.customWallpaper

    // 時間帯の壁紙。切り替わる時刻まで待って差し替える。
    // カスタム壁紙の下にも用意しておき、背景をクリアした時にそのままフェードで見えてくるようにする
    var autoImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var previousImage by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(state.wallpaperTick) {
        while (isActive) {
            val now = Calendar.getInstance()
            val asset = AutoWallpaper.assetFor(now)
            val image = withContext(Dispatchers.IO) { AutoWallpaper.load(context, asset) }
            if (image != null && image !== autoImage) {
                val first = autoImage == null
                previousImage = autoImage
                autoImage = image
                if (first) {
                    fade.snapTo(1f)
                } else {
                    // 新しい画像を前面でフェードイン(下の旧画像は不透明のまま → 暗転しない)
                    fade.snapTo(0f)
                    fade.animateTo(1f, tween(2000))
                }
                previousImage = null
            }
            delay(AutoWallpaper.millisUntilNextChange(Calendar.getInstance()))
        }
    }

    // カスタム壁紙はフェードで出し入れする。消した時は最後の画像を覚えておき、
    // 下の時間帯の壁紙が透けて見えてくるようにフェードアウトさせる
    var customTop by remember { mutableStateOf(custom) }
    var customBelow by remember { mutableStateOf<ImageBitmap?>(null) }
    val customAlpha = remember { Animatable(if (custom != null) 1f else 0f) }
    LaunchedEffect(custom) {
        val target = if (custom != null) 1f else 0f
        if (custom === customTop && customAlpha.value == target) return@LaunchedEffect
        try {
            if (custom != null) {
                // 別の画像に替えた時は、古い画像の上に新しい画像を重ねてフェードイン
                if (customTop != null && customTop !== custom && customAlpha.value > 0f) {
                    customBelow = customTop
                    customAlpha.snapTo(0f)
                }
                customTop = custom
                customAlpha.animateTo(1f, tween(700))
            } else {
                customAlpha.animateTo(0f, tween(900))
                customTop = null
            }
        } finally {
            customBelow = null
        }
    }

    val overlay = Brush.verticalGradient(
        0f to colors.base.copy(alpha = 0.3f),
        0.75f to colors.base.copy(alpha = 0.7f),
        1f to colors.base
    )
    Box(Modifier.fillMaxSize().drawWithContent {
        drawContent()
        drawRect(overlay)
    }) {
        // 表示アニメーション: 少し拡大した所から元の大きさへ引いていく
        Box(Modifier.fillMaxSize().graphicsLayer {
            val p = FastOutSlowInEasing.transform(appear())
            val scale = 1.08f - 0.08f * p
            scaleX = scale
            scaleY = scale
        }) {
            previousImage?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            autoImage?.let {
                // 透明度は描画時に読む(フェード中に毎フレーム再コンポーズしない)
                Image(it, null, Modifier.fillMaxSize().graphicsLayer { alpha = fade.value },
                    contentScale = ContentScale.Crop)
            }
            customBelow?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            customTop?.let {
                Image(it, null, Modifier.fillMaxSize().graphicsLayer { alpha = customAlpha.value },
                    contentScale = ContentScale.Crop)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// タイトル
// ---------------------------------------------------------------------------------------------

//@Composable
//private fun Masthead(colors: StartColors) {
//    BasicText(
//        text = "",
//        style = TextStyle(
//            brush = Brush.verticalGradient(listOf(colors.title.copy(alpha = 0.9f), colors.title.copy(alpha = 0f))),
//            fontSize = 40.sp,
//            lineHeight = 44.sp,
//            letterSpacing = 0.32.em,
//            fontWeight = FontWeight.Normal,
//            textAlign = TextAlign.Center
//        )
//    )
//    Spacer(Modifier.height(8.dp))
//    BasicText(
//        text = "Aozora 2",
//        style = TextStyle(color = colors.inkSoft, fontSize = 16.sp, textAlign = TextAlign.Center)
//    )
//}

// ---------------------------------------------------------------------------------------------
// 検索
// ---------------------------------------------------------------------------------------------

@Composable
private fun SearchArea(state: StartPageState, colors: StartColors, backdrop: Backdrop) {
    val focusManager = LocalFocusManager.current
    val engine = state.engine

    fun submit(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        focusManager.clearFocus(force = true)
        state.suggestions.clear()
        state.openUrl(StartPageState.urlForInput(q, engine))
    }

    // 入力が止まってから候補を取りに行く
    LaunchedEffect(state.query.text) {
        val q = state.query.text.trim()
        if (q.isEmpty()) {
            state.suggestions.clear()
            return@LaunchedEffect
        }
        delay(300)
        val result = try {
            withContext(Dispatchers.IO) { StartPageNetwork.suggestions(q) }
        } catch (e: Exception) {
            emptyList()
        }
        if (state.query.text.trim() == q) {
            state.suggestions.clear()
            state.suggestions.addAll(result.take(8))
        }
    }

    Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .glass(backdrop, RoundedCornerShape(50), colors.glass,
                    lensHeight = 14f, lensAmount = 28f, shadow = true)
                .padding(start = 20.dp, end = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = state.query,
                onValueChange = { state.query = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.ink, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(state.query.text) }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.query.text.isEmpty()) {
                            BasicText(engine.placeholder, style = TextStyle(color = colors.inkSoft, fontSize = 16.sp))
                        }
                        inner()
                    }
                }
            )
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "go")
            BasicText(
                text = Lang.t("検索"),
                style = TextStyle(color = colors.onAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(50))
                    .background(if (pressed) colors.accentStrong else colors.accent)
                    .clickable(interaction, null) { submit(state.query.text) }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }

        // 検索候補
        AnimatedVisibility(
            visible = state.suggestions.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .glass(backdrop, RoundedCornerShape(18.dp), colors.glassStrong)
                    .padding(vertical = 4.dp)
            ) {
                state.suggestions.toList().forEach { word ->
                    BasicText(
                        text = word,
                        style = TextStyle(color = colors.ink, fontSize = 14.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                state.query = TextFieldValue(word)
                                submit(word)
                            }
                            .padding(horizontal = 18.dp, vertical = 11.dp)
                    )
                }
            }
        }

        // シークレットモード / 検索エンジンの選択
        Spacer(Modifier.height(10.dp))
        val rotation by animateFloatAsState(if (state.engineMenuOpen) 180f else 0f, label = "chevron")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecretModeButton(state, colors, backdrop)
            // 壁紙の上に直接文字を置くと読みにくいので、シークレットモードと同じガラスの上に載せる
            val engineInteraction = remember { MutableInteractionSource() }
            val enginePressScale = rememberPressScale(engineInteraction)
            Row(
                Modifier
                    .glass(backdrop, RoundedCornerShape(50), colors.glass, pressScale = enginePressScale)
                    .clickable(engineInteraction, null) { state.engineMenuOpen = !state.engineMenuOpen }
                    .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(Lang.t("検索エンジンを選択"), style = TextStyle(color = colors.ink, fontSize = 13.sp))
                Spacer(Modifier.width(5.dp))
                Chevron(colors.ink, Modifier.size(12.dp).rotate(rotation))
            }
        }
        AnimatedVisibility(
            visible = state.engineMenuOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                Modifier
                    .padding(top = 6.dp)
                    .width(190.dp)
                    .glass(backdrop, RoundedCornerShape(18.dp), colors.glassStrong)
                    .padding(8.dp)
            ) {
                SearchEngine.all(LocalContext.current).forEach { e ->
                    val selected = e.key == engine.key
                    BasicText(
                        text = e.label,
                        style = TextStyle(
                            color = if (selected) colors.accentStrong else colors.ink,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) colors.accent.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { state.selectEngine(e) }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

/** 帽子とメガネのマーク付きの「シークレット モード」ボタン。 */
@Composable
private fun SecretModeButton(state: StartPageState, colors: StartColors, backdrop: Backdrop) {
    val interaction = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interaction)
    Row(
        Modifier
            .glass(backdrop, RoundedCornerShape(50), colors.glass, pressScale = pressScale)
            .clickable(interaction, null) { state.openSecret() }
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_ts_incognito),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.ink),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        BasicText(Lang.t("シークレット モード"), style = TextStyle(color = colors.ink, fontSize = 13.sp))
    }
}

@Composable
private fun Chevron(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        drawLine(color, Offset(w * 0.1f, h * 0.3f), Offset(w * 0.5f, h * 0.72f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.9f, h * 0.3f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------------------------
// サイトのアイコン
// ---------------------------------------------------------------------------------------------

private sealed interface GridEntry {
    data class Site(val shortcut: Shortcut, val customIndex: Int) : GridEntry
    object Add : GridEntry
}

@Composable
private fun ShortcutGrid(state: StartPageState, colors: StartColors, backdrop: Backdrop) {
    val entries = buildList<GridEntry> {
        Shortcut.DEFAULTS.forEach { add(GridEntry.Site(it, -1)) }
        state.customSites.forEachIndexed { i, s -> add(GridEntry.Site(s, i)) }
        add(GridEntry.Add)
    }
    // 追加したサイトを全部消したら編集を終える
    LaunchedEffect(state.customSites.isEmpty()) {
        if (state.customSites.isEmpty()) state.editMode = false
    }
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .glass(backdrop, RoundedCornerShape(28.dp), colors.glass.copy(alpha = colors.glass.alpha * 0.6f),
                lensHeight = 16f, lensAmount = 32f)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { entry ->
                    // 追加・削除で並びが変わっても、アイコンごとのアニメーションの状態を取り違えないようにする
                    key(if (entry is GridEntry.Site) "${entry.customIndex}:${entry.shortcut.url}" else "add") {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            when (entry) {
                                is GridEntry.Site -> SiteTile(state, colors, entry)
                                GridEntry.Add -> AddTile(state, colors)
                            }
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        AnimatedVisibility(state.editMode, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            BasicText(
                Lang.t("完了"),
                style = TextStyle(color = colors.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.accent)
                    .clickable { state.editMode = false }
                    .padding(horizontal = 20.dp, vertical = 7.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteTile(state: StartPageState, colors: StartColors, entry: GridEntry.Site) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 1.1f else 1f, tween(200), label = "tile")
    val haptics = LocalHapticFeedback.current
    val removable = entry.customIndex >= 0
    val editing = state.editMode && removable

    // 追加したばかりのサイトは、ダイアログが閉じてから小さい所からポンと飛び出す
    val pop = remember { Animatable(if (removable && entry.customIndex == state.justAddedIndex) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (pop.value < 1f) {
            delay(160)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f))
            if (state.justAddedIndex == entry.customIndex) state.justAddedIndex = -1
        }
    }

    // 編集中の(消せる)アイコンは小さく揺らす
    val wiggle = remember { Animatable(0f) }
    LaunchedEffect(editing) {
        if (editing) {
            while (isActive) {
                wiggle.animateTo(2.5f, tween(120))
                wiggle.animateTo(-2.5f, tween(120))
            }
        } else {
            wiggle.animateTo(0f, tween(100))
        }
    }

    Box(Modifier.graphicsLayer {
        rotationZ = wiggle.value
        val p = pop.value
        scaleX = p
        scaleY = p
        alpha = p.coerceIn(0f, 1f)
    }) {
        Column(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.showLinkMenu(
                            entry.shortcut.url,
                            if (removable) listOf(Lang.t("削除") to { state.removeCustomSite(entry.customIndex) })
                            else emptyList(),
                            entry.shortcut.name
                        )
                    },
                    onClick = {
                        if (state.editMode) {
                            if (!removable) state.editMode = false
                        } else {
                            state.openUrl(entry.shortcut.url)
                        }
                    }
                )
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RemoteIcon(
                url = entry.shortcut.icon,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (entry.shortcut.whiteBackground) Color.White else Color.Transparent)
            )
            Spacer(Modifier.height(5.dp))
            BasicText(
                text = entry.shortcut.name,
                style = TextStyle(color = colors.title, fontSize = 12.sp, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 76.dp)
            )
        }
        if (editing) {
            BasicText(
                text = "×",
                style = TextStyle(color = colors.ink, fontSize = 12.sp, textAlign = TextAlign.Center),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.glassStrong)
                    .border(1.dp, colors.glassBorder, RoundedCornerShape(50))
                    .clickable { state.removeCustomSite(entry.customIndex) }
                    .padding(top = 1.dp)
            )
        }
    }
}

@Composable
private fun AddTile(state: StartPageState, colors: StartColors) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 押すとへこみ、離すとばねのように弾んで戻る
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f,
        spring(dampingRatio = 0.4f, stiffness = 600f), label = "add")
    // ダイアログを開いている間は「＋」を回して「×」にする
    val rotation by animateFloatAsState(if (state.addSiteOpen) 135f else 0f,
        spring(dampingRatio = 0.55f, stiffness = 300f), label = "plus")
    val highlight by animateFloatAsState(if (pressed || state.addSiteOpen) 1f else 0f, tween(200), label = "addBg")
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(interaction, null) { state.openAddSite() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(10.dp))
                .background(colors.glass)
                .drawWithContent {
                    drawRect(colors.accent.copy(alpha = 0.35f * highlight))
                    drawContent()
                }
                .border(1.dp, colors.glassBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            BasicText(Lang.t("＋"), style = TextStyle(color = colors.ink, fontSize = 20.sp),
                modifier = Modifier.graphicsLayer { rotationZ = rotation })
        }
        Spacer(Modifier.height(5.dp))
        BasicText(Lang.t("サイト追加"), style = TextStyle(color = colors.title, fontSize = 12.sp))
    }
}

@Composable
private fun RemoteIcon(url: String?, modifier: Modifier, maxWidth: Int = 0) {
    val image by produceState(url?.let { RemoteImages.cached(it, maxWidth) }, url, maxWidth) {
        if (url != null && value == null) value = withContext(Dispatchers.IO) { RemoteImages.load(url, maxWidth) }
    }
    // 読み込めたらふわっと出す(最初から読み込み済みならそのまま)
    val alpha = remember(url) { Animatable(if (image != null) 1f else 0f) }
    LaunchedEffect(image != null) {
        if (image != null) alpha.animateTo(1f, tween(220))
    }
    Box(modifier) {
        image?.let {
            Image(it, null, Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value },
                contentScale = ContentScale.Crop)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// ニュース
// ---------------------------------------------------------------------------------------------

/** おすすめ記事(Chrome の Discover のようなカード)を LazyColumn に並べる。 */
private fun LazyListScope.discoverFeed(state: StartPageState, colors: StartColors, appear: Modifier) {
    item(key = "feed-header", contentType = "header") {
        Row(
            appear.widthIn(max = 600.dp).fillMaxWidth().padding(start = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(Lang.t("おすすめ"), style = TextStyle(color = colors.title, fontSize = 18.sp, fontWeight = FontWeight.Bold))
        }
    }
    val feed = state.feed
    when {
        feed == null -> items(2, key = { "skeleton-$it" }, contentType = { "skeleton" }) {
            Box(appear) { DiscoverSkeleton(colors) }
        }
        feed.isEmpty() -> item(key = "feed-empty", contentType = "message") {
            Box(appear) {
                FeedMessage(
                    if (state.feedFailed) Lang.t("おすすめの記事を読み込めませんでした") else Lang.t("おすすめの記事はありません"),
                    colors, retry = { state.refresh { } }
                )
            }
        }
        else -> {
            val shown = feed.subList(0, state.feedShown.coerceAtMost(feed.size)).filter { state.isVisible(it) }
            items(shown, key = { it.url }, contentType = { "card" }) { entry ->
                Box(appear) { DiscoverCard(state, entry, colors) }
            }
            item(key = "feed-footer", contentType = "footer") {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    if (state.feedShown < feed.size || state.feedLoadingMore) {
                        LoadingDots(colors)
                    } else {
                        BasicText(Lang.t("これ以上のおすすめはありません"),
                            style = TextStyle(color = colors.inkSoft, fontSize = 13.sp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverCard(state: StartPageState, entry: FeedEntry, colors: StartColors) {
    val meta by produceState(StartPageNetwork.cachedMeta(entry), entry.url) {
        if (value == null) value = withContext(Dispatchers.IO) { StartPageNetwork.fetchMeta(entry) }
    }
    // 配信元は記事ページを読むまで分からないので、表示しない配信元だと分かった時点で消す
    if (meta?.source?.let { it in state.blockedSources } == true) return
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val imageWidth = with(density) { LocalConfiguration.current.screenWidthDp.coerceAtMost(600).dp.roundToPx() }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(150), label = "card")

    Column(
        Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.showLinkMenu(entry.url, state.feedMenuExtras(entry, meta?.source), entry.title)
                },
                onClick = { state.openUrl(entry.url) }
            )
    ) {
        // サイトのヘッダー画像(og:image)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(colors.placeholder)
        ) {
            val image = meta?.imageUrl
            if (image != null) RemoteIcon(image, Modifier.fillMaxSize(), maxWidth = imageWidth)
        }
        Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 8.dp)) {
            BasicText(
                text = entry.title,
                style = TextStyle(color = colors.title, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(top = 4.dp)
            )
            // 共有ボタン
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { state.share(entry.title, entry.url) },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_omni_share),
                    contentDescription = Lang.t("共有"),
                    colorFilter = ColorFilter.tint(colors.inkSoft),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val source = meta?.source
            SourceAvatar(source, colors)
            Spacer(Modifier.width(8.dp))
            val published = meta?.publishedAt ?: entry.publishedAt
            BasicText(
                text = listOfNotNull(source ?: Lang.t("Yahoo!ニュース"), timeAgo(published)).joinToString(Lang.t(" ・ ")),
                style = TextStyle(color = colors.inkSoft, fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 配信元のマーク(頭文字の丸)。 */
@Composable
private fun SourceAvatar(source: String?, colors: StartColors) {
    val name = source ?: "Y"
    val hue = (name.hashCode() and 0x7fffffff) % 360
    Box(
        Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.hsv(hue.toFloat(), 0.45f, if (colors.dark) 0.75f else 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            name.take(1),
            style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        )
    }
}

/** 読み込み中のカード(形だけ)。 */
@Composable
private fun DiscoverSkeleton(colors: StartColors) {
    Column(
        Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(colors.placeholder))
        Box(Modifier.padding(start = 16.dp, top = 14.dp).fillMaxWidth(0.85f).height(16.dp)
            .clip(RoundedCornerShape(8.dp)).background(colors.placeholder))
        Box(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 18.dp).fillMaxWidth(0.5f).height(16.dp)
            .clip(RoundedCornerShape(8.dp)).background(colors.placeholder))
    }
}

@Composable
private fun FeedMessage(text: String, colors: StartColors, retry: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(text, style = TextStyle(color = colors.ink, fontSize = 14.sp))
        Spacer(Modifier.height(12.dp))
        BasicText(
            Lang.t("再読み込み"),
            style = TextStyle(color = colors.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.accent)
                .clickable(onClick = retry)
                .padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
}

/** 次の 10 件を読み込んでいる間の「・・・」。 */
@Composable
private fun LoadingDots(colors: StartColors) {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            phase.snapTo(0f)
            phase.animateTo(3f, tween(900))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        val d = (phase.value - i).let { if (it < 0) it + 3 else it }
                        alpha = if (d < 1f) 1f else 0.3f
                    }
                    .clip(RoundedCornerShape(50))
                    .background(colors.accent)
            )
        }
    }
}

/** 「3時間前」のような表示。 */
private fun timeAgo(time: Long): String? {
    if (time <= 0L) return null
    val diff = (System.currentTimeMillis() - time).coerceAtLeast(0L) / 1000
    return when {
        diff < 60 -> Lang.t("たった今")
        diff < 3600 -> Lang.f("%d分前", diff / 60)
        diff < 86400 -> Lang.f("%d時間前", diff / 3600)
        diff < 86400 * 7 -> Lang.f("%d日前", diff / 86400)
        else -> java.text.SimpleDateFormat(Lang.t("M月d日"), java.util.Locale.JAPAN).format(java.util.Date(time))
    }
}

// ---------------------------------------------------------------------------------------------
// 壁紙・サイト編集のボタン
// ---------------------------------------------------------------------------------------------

/** 右上の小さな壁紙ボタンと、そのメニュー(壁紙を設定 / 背景をクリア)。 */
@Composable
private fun WallpaperCorner(state: StartPageState, colors: StartColors, backdrop: Backdrop) {
    // スクロールしたらメニューは閉じる(ボタンはページと一緒に流れないので)
    LaunchedEffect(state.listState.isScrollInProgress) {
        if (state.listState.isScrollInProgress) state.wallpaperMenuOpen = false
    }
    val atTop by remember { derivedStateOf { state.listState.firstVisibleItemIndex == 0 &&
        state.listState.firstVisibleItemScrollOffset < 40 } }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = atTop || state.wallpaperMenuOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 12.dp)
        ) {
            val interaction = remember { MutableInteractionSource() }
            val pressScale = rememberPressScale(interaction)
            Box(
                Modifier
                    .size(32.dp)
                    .glass(backdrop, RoundedCornerShape(50), colors.glass, pressScale = pressScale)
                    .clickable(interaction, null) { state.wallpaperMenuOpen = !state.wallpaperMenuOpen },
                contentAlignment = Alignment.Center
            ) {
                PictureIcon(colors.inkSoft, Modifier.size(16.dp))
            }
        }
        AnimatedVisibility(
            visible = state.wallpaperMenuOpen,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 42.dp, end = 12.dp)
        ) {
            Column(
                Modifier
                    .width(170.dp)
                    .glass(backdrop, RoundedCornerShape(18.dp), colors.glassStrong)
                    .padding(6.dp)
            ) {
                MenuRow(Lang.t("壁紙を設定"), colors) {
                    state.wallpaperMenuOpen = false
                    state.pickWallpaper()
                }
                if (state.customWallpaper != null) {
                    MenuRow(Lang.t("背景をクリア"), colors) {
                        state.wallpaperMenuOpen = false
                        state.clearWallpaper()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(text: String, colors: StartColors, onClick: () -> Unit) {
    BasicText(
        text,
        style = TextStyle(color = colors.ink, fontSize = 14.sp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

/** 写真(山と太陽)のアイコン。 */
@Composable
private fun PictureIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        drawRoundRect(color, topLeft = Offset(stroke / 2, h * 0.12f),
            size = androidx.compose.ui.geometry.Size(w - stroke, h * 0.76f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.68f, h * 0.36f))
        val mountain = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.12f, h * 0.8f)
            lineTo(w * 0.4f, h * 0.45f)
            lineTo(w * 0.6f, h * 0.68f)
            lineTo(w * 0.72f, h * 0.56f)
            lineTo(w * 0.9f, h * 0.8f)
            close()
        }
        drawPath(mountain, color)
    }
}

@Composable
private fun GlassButton(
    text: String,
    colors: StartColors,
    backdrop: Backdrop,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interaction)
    BasicText(
        text = text,
        style = TextStyle(
            color = if (filled) colors.onAccent else colors.accentStrong,
            fontSize = 13.sp,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.Normal
        ),
        modifier = Modifier
            .glass(backdrop, RoundedCornerShape(50), if (filled) colors.accent else colors.glass,
                lensHeight = 8f, lensAmount = 16f, pressScale = pressScale)
            .clickable(interaction, null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    )
}

// ---------------------------------------------------------------------------------------------
// サイトを追加
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AnimatedVisibilityScope.AddSiteDialog(state: StartPageState, colors: StartColors, backdrop: Backdrop) {
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var url by remember { mutableStateOf(TextFieldValue("")) }
    var error by remember { mutableStateOf<String?>(null) }

    // URL 入力に応じてファビコンをプレビュー
    val preview by produceState<URL?>(null, url.text) {
        delay(300)
        value = StartPageState.normalizeUrl(url.text)
    }

    fun save() {
        val u = StartPageState.normalizeUrl(url.text)
        if (u == null) {
            error = Lang.t("正しいURLを入力してください。")
            return
        }
        state.addCustomSite(name.text.trim().ifEmpty { u.host }, u)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(remember { MutableInteractionSource() }, null) { state.addSiteOpen = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                // 下から弾むように大きくなって出てくる
                .animateEnterExit(
                    enter = scaleIn(spring(dampingRatio = 0.62f, stiffness = 420f), initialScale = 0.8f) +
                        slideInVertically(spring(dampingRatio = 0.75f, stiffness = 420f)) { it / 5 },
                    exit = scaleOut(tween(160), targetScale = 0.9f) + slideOutVertically(tween(160)) { it / 8 }
                )
                .padding(24.dp)
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .glass(backdrop, RoundedCornerShape(24.dp), colors.glassStrong, blurRadius = 12f,
                    lensHeight = 18f, lensAmount = 36f, shadow = true)
                .clickable(remember { MutableInteractionSource() }, null) { }
                .padding(20.dp)
        ) {
            BasicText(Lang.t("サイトを追加"),
                style = TextStyle(color = colors.accentStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(14.dp))
            error?.let {
                BasicText(it, style = TextStyle(color = colors.error, fontSize = 12.sp))
                Spacer(Modifier.height(10.dp))
            }
            DialogField(Lang.t("名前"), Lang.t("例：マイサイト"), name, colors, ImeAction.Next, KeyboardType.Text) {
                name = if (it.text.length > 20) it.copy(text = it.text.take(20)) else it
            }
            DialogField("URL", Lang.t("例：https://example.com"), url, colors, ImeAction.Done, KeyboardType.Uri,
                onDone = { save() }) {
                url = it
                error = null
            }
            preview?.let { u ->
                Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RemoteIcon(
                        Shortcut.faviconUrlFor(StartPageState.originOf(u)),
                        Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Color.White)
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicText(u.host, style = TextStyle(color = colors.inkSoft, fontSize = 12.sp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                BasicText(
                    Lang.t("キャンセル"),
                    style = TextStyle(color = colors.inkSoft, fontSize = 13.sp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { state.addSiteOpen = false }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Spacer(Modifier.width(8.dp))
                BasicText(
                    Lang.t("追加"),
                    style = TextStyle(color = colors.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.accent)
                        .clickable { save() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DialogField(
    label: String,
    placeholder: String,
    value: TextFieldValue,
    colors: StartColors,
    imeAction: ImeAction,
    keyboardType: KeyboardType,
    onDone: (() -> Unit)? = null,
    onValueChange: (TextFieldValue) -> Unit
) {
    BasicText(label, style = TextStyle(color = colors.inkSoft, fontSize = 12.sp))
    Spacer(Modifier.height(4.dp))
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.ink, fontSize = 14.sp),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.base.copy(alpha = 0.5f))
            .border(1.dp, colors.glassBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        decorationBox = { inner ->
            Box {
                if (value.text.isEmpty()) {
                    BasicText(placeholder, style = TextStyle(color = colors.inkSoft, fontSize = 14.sp))
                }
                inner()
            }
        }
    )
}

// ---------------------------------------------------------------------------------------------
// 雪の演出
// ---------------------------------------------------------------------------------------------

private class Flake(
    val x: Float,          // 画面幅に対する割合
    val sizeIndex: Int,    // SNOW_SIZES の番号
    val startNanos: Long,
    val durationNanos: Long
)

@Composable
private fun Snowfall(state: StartPageState, colors: StartColors) {
    val flakes = remember { mutableStateListOf<Flake>() }
    var frame by remember { mutableStateOf(0L) }
    val measurer = rememberTextMeasurer()
    // 大きさは 3 段階に丸めて、毎フレームの文字の計測をしない
    val layouts = remember(colors.title) {
        SNOW_SIZES.map { measurer.measure("❄", TextStyle(color = colors.title, fontSize = it.sp)) }
    }

    LaunchedEffect(state.visible) {
        if (!state.visible) {
            flakes.clear()
            return@LaunchedEffect
        }
        var lastSpawn = 0L
        while (isActive) {
            // 雪が無い間は毎フレーム描き直さない
            if (flakes.isEmpty()) delay(500)
            val now = withFrameNanos { it }
            frame = now
            // 旧ページは 0.5 秒ごとに 8 種類の模様から 1 つを出していた(見えるのは雪だけ)
            if (now - lastSpawn >= 500_000_000L) {
                lastSpawn = now
                if (Random.nextInt(8) == 0) {
                    val duration = ((8 + Random.nextFloat() * 8) * 1_000_000_000L).toLong()
                    flakes.add(Flake(Random.nextFloat(), Random.nextInt(SNOW_SIZES.size), now, duration))
                }
            }
            flakes.removeAll { now - it.startNanos > it.durationNanos }
        }
    }

    // 専用のレイヤーにして、雪の再描画がガラスや壁紙の描き直しにつながらないようにする
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val now = frame
        flakes.forEach { f ->
            val t = ((now - f.startNanos).toFloat() / f.durationNanos).coerceIn(0f, 1f)
            val e = FastOutSlowInEasing.transform(t)
            val layout = layouts[f.sizeIndex]
            val x = f.x * size.width
            val y = -0.1f * size.height + e * size.height
            rotate(360f * e, Offset(x + layout.size.width / 2f, y + layout.size.height / 2f)) {
                drawText(layout, topLeft = Offset(x, y), alpha = 0.8f * (1f - e))
            }
        }
    }
}

private val SNOW_SIZES = listOf(16f, 22f, 28f)
