package com.airsky.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * ガラスのデザインを選ぶ画面(Pixel の設定アプリ風)。
 *
 * 上に「ガラス」と「シンプル」の 2 つのプレビューを並べ、押した方に切り替わる。プレビューは
 * 本物と同じ壁紙・同じガラスの描き方(ぼかし・屈折)で描くので、選ぶ前に見た目を確かめられる。
 * ガラスを選んでいる時だけ、その下に「屈折を有効にする」のスイッチを出す。
 *
 * 初回セットアップでも開き([setupIntent])、その時は下に「戻る」「次へ」を出す。
 */
class GlassSettingsActivity : PixelSettingsActivity() {

    companion object {
        private const val EXTRA_SETUP = "com.airsky.browser.extra.GLASS_SETUP"

        /** 設定(デザイン)から開く。 */
        @JvmStatic
        fun intent(c: Context): Intent = Intent(c, GlassSettingsActivity::class.java)

        /** 初回セットアップの途中で開く。「次へ」で RESULT_OK を返して閉じる。 */
        @JvmStatic
        fun setupIntent(c: Context): Intent = intent(c).putExtra(EXTRA_SETUP, true)

        /** 今の設定を 1 行で(デザインの画面の説明用)。 */
        @JvmStatic
        fun summary(c: Context): String = when {
            !GlassPrefs.enabled(c) -> Lang.t("シンプル")
            GlassPrefs.refraction(c) -> Lang.t("ガラス(屈折あり)")
            else -> Lang.t("ガラス")
        }
    }

    private val setup: Boolean get() = intent.getBooleanExtra(EXTRA_SETUP, false)

    private lateinit var choice: GlassChoiceState
    private var refractionRow: PixelSettingsActivity.Row? = null

    override fun pageTitle(): CharSequence = if (setup) Lang.t("見た目を選択") else Lang.t("ガラスのデザイン")

    override fun onCreate(savedInstanceState: Bundle?) {
        choice = GlassChoiceState(GlassPrefs.enabled(this), GlassPrefs.refraction(this))
        super.onCreate(savedInstanceState)
        if (setup) setBottomBar(buildSetupButtons())
    }

    override fun onBuild() {
        if (setup) {
            note(Lang.t("スタートページとボトムバーの見た目を選んでください。あとから設定の「デザイン」で変更できます。"))
        } else {
            gap()
        }
        card(buildChoiceView())

        gap()
        val row = switchRow(R.drawable.ic_ps_lens, Lang.t("屈折を有効にする"),
            Lang.t("ガラスの縁で、背景がレンズを通したように曲がって見えるようにします"),
            choice.refraction) { on ->
            GlassPrefs.setRefraction(this, on)
            choice.refraction = on
        }
        row.view.visibility = if (choice.glass) View.VISIBLE else View.GONE
        refractionRow = row

        note(Lang.t("ガラス: 背景が透けて見える、すりガラスのような見た目です。\n" +
                "シンプル: 白(ダークモードでは黒)の面で表示します。端末への負担が少なくなります。"))
        note(Lang.t("ガラスの描画には、Kyant 氏のオープンソース ライブラリ「backdrop」を使用しています。"))
    }

    private fun select(glass: Boolean) {
        if (choice.glass == glass) return
        choice.glass = glass
        GlassPrefs.setEnabled(this, glass)
        // 屈折のスイッチはガラスの時だけ。出し入れは下の項目ごとなめらかに動かす
        refractionRow?.let {
            TransitionManager.beginDelayedTransition(body, AutoTransition().setDuration(200))
            it.view.visibility = if (glass) View.VISIBLE else View.GONE
        }
    }

    private fun buildChoiceView(): View {
        val primary = Color(attrColor(androidx.appcompat.R.attr.colorPrimary))
        val onSurface = Color(attrColor(com.google.android.material.R.attr.colorOnSurface))
        val onSurfaceVariant = Color(attrColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        val outline = Color(attrColor(com.google.android.material.R.attr.colorOutlineVariant))
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                GlassChoice(choice, GlassChoiceColors(primary, onSurface, onSurfaceVariant, outline), ::select)
            }
        }
    }

    private fun buildSetupButtons(): View {
        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(24f), dp(12f), dp(24f), dp(20f))
        val inflater = LayoutInflater.from(this)
        val back = inflater.inflate(R.layout.item_ps_text_button, bar, false) as Button
        back.text = Lang.t("戻る")
        back.setOnClickListener { finish() }
        bar.addView(back)
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val next = inflater.inflate(R.layout.item_ps_filled_button, bar, false) as Button
        next.text = Lang.t("次へ")
        next.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }
        bar.addView(next)
        return bar
    }

    override fun finish() {
        super.finish()
        if (setup) overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

/** 選んでいるデザイン(押すとすぐ変わり、プレビューも描き直す)。 */
internal class GlassChoiceState(glass: Boolean, refraction: Boolean) {
    var glass by mutableStateOf(glass)
    var refraction by mutableStateOf(refraction)
}

internal data class GlassChoiceColors(
    val primary: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color
)

@Composable
private fun GlassChoice(state: GlassChoiceState, colors: GlassChoiceColors, onSelect: (Boolean) -> Unit) {
    val wallpaper = rememberPreviewWallpaper()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ChoiceOption(
            label = Lang.t("ガラス"), glass = true, selected = state.glass, refraction = state.refraction,
            wallpaper = wallpaper, colors = colors, onClick = { onSelect(true) },
            modifier = Modifier.weight(1f)
        )
        ChoiceOption(
            label = Lang.t("シンプル"), glass = false, selected = !state.glass, refraction = false,
            wallpaper = wallpaper, colors = colors, onClick = { onSelect(false) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChoiceOption(
    label: String,
    glass: Boolean,
    selected: Boolean,
    refraction: Boolean,
    wallpaper: ImageBitmap?,
    colors: GlassChoiceColors,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val ring by animateColorAsState(if (selected) colors.primary else colors.outline, tween(200), label = "ring")
    val ringWidth by animateFloatAsState(if (selected) 3f else 1f, tween(200), label = "ringWidth")
    val scale by animateFloatAsState(if (selected) 1f else 0.95f, tween(220), label = "scale")
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.56f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .border(ringWidth.dp, ring, shape)
                .padding(5.dp)
                .clip(RoundedCornerShape(19.dp))
        ) {
            PhonePreview(glass, refraction, wallpaper)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioDot(selected, colors)
            Spacer(Modifier.width(8.dp))
            BasicText(
                label,
                style = TextStyle(
                    color = if (selected) colors.primary else colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                )
            )
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean, colors: GlassChoiceColors) {
    val color by animateColorAsState(if (selected) colors.primary else colors.onSurfaceVariant, tween(200), label = "dot")
    val inner by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "inner")
    Canvas(Modifier.size(20.dp)) {
        val r = size.minDimension / 2f
        drawCircle(color, radius = r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        if (inner > 0f) drawCircle(color, radius = (r - 5.dp.toPx()) * inner)
    }
}

/**
 * スタートページを小さくしたプレビュー。壁紙の上に検索欄・ショートカット・ボトムバーを置き、
 * [glass] なら本物と同じくガラス(ぼかし+鮮やかさ、[refraction] なら屈折も)で、そうでなければ面で描く。
 */
@Composable
private fun PhonePreview(glass: Boolean, refraction: Boolean, wallpaper: ImageBitmap?) {
    val dark = isSystemInDarkTheme()
    val backdrop = rememberLayerBackdrop()
    val ink = if (dark) Color(0xFFE7F3FB) else Color(0xFF12314A)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            if (wallpaper != null) {
                Image(wallpaper, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6FA8DC), Color(0xFFB4D7F0), Color(0xFFF6D6B8)),
                            Offset.Zero, Offset(0f, Float.POSITIVE_INFINITY)
                        )
                    )
                )
            }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(26.dp))
            // 検索欄
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .previewSurface(backdrop, CircleShape, glass, refraction, dark, lensHeight = 8f),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    Modifier
                        .padding(start = 10.dp)
                        .size(width = 48.dp, height = 5.dp)
                        .background(ink.copy(alpha = 0.45f), CircleShape)
                )
            }
            Spacer(Modifier.height(14.dp))
            // ショートカット
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(4) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .previewSurface(backdrop, CircleShape, glass, refraction, dark, lensHeight = 6f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(8.dp).background(ink.copy(alpha = 0.5f), CircleShape))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // カード
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .previewSurface(backdrop, RoundedCornerShape(12.dp), glass, refraction, dark, lensHeight = 10f)
            )
            Spacer(Modifier.weight(1f))
            // ボトムバー
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .previewSurface(backdrop, CircleShape, glass, refraction, dark, lensHeight = 9f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) {
                    Box(Modifier.size(7.dp).background(ink.copy(alpha = 0.6f), CircleShape))
                }
            }
        }
    }
}

/** プレビューの部品の面。スタートページの glass() と同じ描き方を小さくしたもの。 */
@Composable
private fun Modifier.previewSurface(
    backdrop: Backdrop,
    shape: Shape,
    glass: Boolean,
    refraction: Boolean,
    dark: Boolean,
    lensHeight: Float
): Modifier {
    if (!glass) {
        val solid = (if (dark) Color.Black else Color.White).copy(alpha = 0.88f)
        return this.background(solid, shape)
    }
    val tint = if (dark) Color(13, 33, 50).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.36f)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(4.dp.toPx())
            if (refraction) lens(lensHeight.dp.toPx(), (lensHeight * 2f).dp.toPx())
        },
        highlight = { Highlight.Default.copy(alpha = if (dark) 0.55f else 1f) },
        onDrawSurface = { drawRect(tint) }
    )
}

/** スタートページと同じ壁紙(設定した画像か、時間帯で変わる既定の壁紙)。 */
@Composable
private fun rememberPreviewWallpaper(): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    return produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.IO) {
            try {
                val store = StartPageStore(context)
                (if (store.hasCustomWallpaper()) store.loadCustomWallpaper() else null)
                    ?: AutoWallpaper.load(context, AutoWallpaper.assetFor(Calendar.getInstance()))
            } catch (e: Throwable) { // 読めなければグラデーションで描く
                null
            }
        }
    }.value
}
