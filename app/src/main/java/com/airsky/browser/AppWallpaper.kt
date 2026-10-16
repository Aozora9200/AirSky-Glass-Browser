package com.airsky.browser

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.compose.ui.graphics.asAndroidBitmap
import java.util.Calendar
import java.util.concurrent.Executors

/**
 * ツールなどアプリ側の画面の壁紙。スタートページの壁紙と同じものを出す。
 *
 * スタートページで「壁紙を設定」した画像があればそれを、無ければスタートページの既定の壁紙
 * (時間帯で切り替わる壁紙)を使い、画面を開いている間に切り替わる時刻が来たら差し替える。
 */
internal object AppWallpaper {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /**
     * @param image 壁紙を出す ImageView(画面いっぱい・centerCrop)
     * @param overlay 壁紙の上に重ねて文字を読みやすくする幕(あれば表示する)
     */
    @JvmStatic
    fun apply(image: ImageView?, overlay: View?) {
        if (image == null) return
        image.visibility = View.VISIBLE
        overlay?.visibility = View.VISIBLE
        image.scaleType = ImageView.ScaleType.CENTER_CROP

        val context = image.context.applicationContext
        val token = Any()
        image.setTag(R.id.app_wallpaper_token, token)
        (image.getTag(R.id.app_wallpaper_timer) as? Runnable)?.let { image.removeCallbacks(it) }

        val store = StartPageStore(context)
        val custom = store.hasCustomWallpaper()
        io.execute {
            var bitmap: Bitmap? = null
            try {
                if (custom) bitmap = store.loadCustomWallpaper()?.asAndroidBitmap()
                if (bitmap == null) {
                    bitmap = AutoWallpaper.load(context, AutoWallpaper.assetFor(Calendar.getInstance()))
                        ?.asAndroidBitmap()
                }
            } catch (e: Throwable) { // 読めなくても画面は開けるようにする
                e.printStackTrace()
            }
            val result = bitmap ?: return@execute
            main.post {
                if (image.getTag(R.id.app_wallpaper_token) !== token) return@post
                val first = image.drawable == null
                image.setImageBitmap(result)
                // レイアウトに仮で置いてある背景は、壁紙が出たら外す
                if (image !is android.widget.ImageButton) image.background = null
                if (first) {
                    image.alpha = 0f
                    image.animate().alpha(1f).setDuration(220).start()
                }
            }
        }

        // 既定の壁紙は時間帯で変わるので、切り替わる時刻に読み直す
        if (!custom) {
            val timer = Runnable {
                if (image.isAttachedToWindow && image.getTag(R.id.app_wallpaper_token) === token) {
                    apply(image, overlay)
                }
            }
            image.setTag(R.id.app_wallpaper_timer, timer)
            image.postDelayed(timer, AutoWallpaper.millisUntilNextChange(Calendar.getInstance()))
            if (image.getTag(R.id.app_wallpaper_detach) == null) {
                val listener = object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}
                    override fun onViewDetachedFromWindow(v: View) {
                        (v.getTag(R.id.app_wallpaper_timer) as? Runnable)?.let { v.removeCallbacks(it) }
                    }
                }
                image.addOnAttachStateChangeListener(listener)
                image.setTag(R.id.app_wallpaper_detach, listener)
            }
        }
    }
}
