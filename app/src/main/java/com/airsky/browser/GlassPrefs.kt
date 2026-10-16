package com.airsky.browser

import android.content.Context

/**
 * ガラスのデザイン(スタートページの部品とボトムバー)の設定。
 *
 * 使わない時は、ガラスの部分を白(ダークでは黒)の少し透ける面で描く。
 * 屈折は、ガラスを使う時だけ効く(背景がレンズのように曲がって見える効果)。
 */
object GlassPrefs {
    private const val FILE = "AppPrefs"
    const val KEY_ENABLED = "glassDesign"
    const val KEY_REFRACTION = "glassRefraction"

    @JvmStatic
    fun enabled(c: Context): Boolean =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    @JvmStatic
    fun refraction(c: Context): Boolean =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_REFRACTION, true)

    @JvmStatic
    fun setEnabled(c: Context, on: Boolean) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    @JvmStatic
    fun setRefraction(c: Context, on: Boolean) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_REFRACTION, on).apply()
    }
}
