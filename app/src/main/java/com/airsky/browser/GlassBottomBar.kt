package com.airsky.browser

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs

/**
 * ガラスの見た目のボトムバー。
 * 設定でガラスを使わないようにした時は、白(ダークでは黒)の少し透ける面で描く([setGlassEnabled])。
 *
 * 画面下に浮かぶカプセル形のガラスを Compose で描き、その上に XML で並べたボタン(普通の View)を載せる。
 * ボタンの処理は今までどおり MainActivity が findViewById して設定すればよい。
 *
 * ページは Gecko が SurfaceView に描いているので、ガラスがページを取り込んで屈折・ぼかしすることはできない。
 * そのため半透明の面にふちの光(ハイライト)と影を付けたガラスにし、カプセルの周りの余白は
 * ページの下端の背景色で塗って、ページの上に浮いているように見せる([setGapColor])。
 *
 * 隠す/出す時は、バーの箱ごと下へずらすだけでなく、ガラスが水滴のように縮んで沈み込み、
 * 出る時はぷるんと横に広がって揺れながら落ち着く([setHideProgress])。動きはバネで少し遅れて
 * ついてくるので、速く動かすほど縦に伸び、止まると揺れ戻る。周りの余白の色も一緒に薄れていき、
 * 色の付いた帯だけが下へ動いて見える違和感をなくす。
 */
class GlassBottomBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** ガラスの色(テーマの colorSurfaceContainer)。setBackgroundColor で変わる。 */
    private var tint by mutableStateOf(Color(0xFF1E2328))

    /** カプセルの周りの余白の色。null なら塗らない(下のウィンドウの背景が見える)。 */
    private var gapColor by mutableStateOf<Color?>(null)

    /** ガラスで描くか(false なら白/黒の少し透ける面)。 */
    private var glass by mutableStateOf(true)

    /** 隠れ具合(0 = 表示, 1 = 隠れている)。 */
    private var hide by mutableFloatStateOf(0f)

    private val composeView: ComposeView

    init {
        clipChildren = false
        clipToPadding = false
        composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                GlassCapsule(tint, gapColor, hide, glass) { sx, sy, contentAlpha ->
                    applyToButtons(sx, sy, contentAlpha)
                }
            }
        }
        addView(composeView, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** 背景色の代わりにガラスの色を変える(バー全体を塗りつぶさない)。 */
    override fun setBackgroundColor(color: Int) {
        tint = Color(color)
    }

    /**
     * カプセルの周りの余白の色。ページの下端に見えている背景色を渡すと、余白がページの続きのように見える。
     * null なら塗らない(スタートページなど、下のウィンドウの背景がそのまま合う時)。
     */
    fun setGapColor(color: Int?) {
        gapColor = color?.let { Color(it) }
    }

    /** ガラスを使うか(デザインの設定)。 */
    fun setGlassEnabled(enabled: Boolean) {
        glass = enabled
    }

    /**
     * 隠れ具合を設定する(DynamicToolbar から毎フレーム)。
     * バー全体は [shiftPx] だけ下へずらし、ガラスとボタンの形は [progress] から決める。
     */
    fun setHideProgress(progress: Float, shiftPx: Float) {
        translationY = shiftPx
        hide = progress
    }

    /** ガラスと同じ変形をボタンの並びにも付ける(ボタンは Compose の外の普通の View)。 */
    private fun applyToButtons(sx: Float, sy: Float, alpha: Float) {
        for (i in 0 until childCount) {
            val child: View = getChildAt(i)
            if (child === composeView) continue
            child.pivotX = child.width / 2f
            child.pivotY = child.height.toFloat()
            child.scaleX = sx
            child.scaleY = sy
            child.alpha = alpha
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Composable
private fun GlassCapsule(
    tint: Color,
    gapColor: Color?,
    hide: Float,
    glass: Boolean,
    onTransform: (scaleX: Float, scaleY: Float, contentAlpha: Float) -> Unit
) {
    val dark = tint.luminance() < 0.5f
    // ガラスを使わない時は、テーマに合わせた白/黒を少しだけ透かす
    val target = if (glass) tint.copy(alpha = if (dark) 0.78f else 0.72f)
    else (if (dark) Color.Black else Color.White).copy(alpha = 0.86f)
    val surface by animateColorAsState(target, tween(250), label = "glass")
    // ページを切り替えた時などに色がパッと変わらないよう、ゆっくり移り変わらせる
    val gap by animateColorAsState(gapColor ?: Color.Transparent, tween(300), label = "gap")
    val shape = RoundedCornerShape(50)

    // 形はバネで少し遅れてついてくる。弱めの減衰なので、出し切った所で一度ふくらんでから落ち着く
    val liquid by animateFloatAsState(
        targetValue = hide,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 420f),
        label = "liquid"
    )
    // 実際の位置との差 = 動きの速さ。速く動いている間は縦に伸びて細くなる(水滴が引っぱられる感じ)
    val lag = (hide - liquid).coerceIn(-1f, 1f)
    val m = liquid.coerceIn(-0.2f, 1f)
    val sink = m.coerceAtLeast(0f)
    // 隠れるほど横に縮んで小さな水滴になる。行き過ぎ(m < 0)の間は少し横にふくらむ
    val scaleX = (lerp(1f, 0.42f, sink * sink) - m.coerceAtMost(0f) * 0.22f - abs(lag) * 0.10f)
        .coerceAtLeast(0.2f)
    val scaleY = (lerp(1f, 0.62f, sink) + m.coerceAtMost(0f) * 0.12f + abs(lag) * 0.28f)
        .coerceAtLeast(0.3f)
    val glassAlpha = 1f - smooth(0.72f, 1f, sink)
    // ボタンはガラスより先に消え、出る時はガラスが広がってから現れる
    val contentAlpha = 1f - smooth(0.05f, 0.45f, sink)
    // 周りの余白の色は、色の帯がそのまま下へ動いて見えないよう早めに薄れる
    val gapAlpha = 1f - smooth(0f, 0.6f, hide.coerceIn(0f, 1f))

    SideEffect { onTransform(scaleX, scaleY, contentAlpha) }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(gap.copy(alpha = gap.alpha * gapAlpha)) }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .graphicsLayer {
                this.scaleX = scaleX
                this.scaleY = scaleY
                this.alpha = glassAlpha
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .drawBackdrop(
                backdrop = emptyBackdrop(),
                shape = { shape },
                effects = {},
                // ふちの光。ダークでは控えめにしないと白く浮いてしまう
                highlight = if (glass) {
                    { Highlight.Default.copy(alpha = if (dark) 0.55f else 1f) }
                } else null,
                shadow = { Shadow(radius = 10.dp, color = Color.Black.copy(alpha = if (dark) 0.35f else 0.16f)) },
                innerShadow = if (glass) {
                    { InnerShadow(radius = 6.dp, color = Color.White.copy(alpha = if (dark) 0.06f else 0.35f)) }
                } else null,
                onDrawSurface = { drawRect(surface) }
            )
    )
}
