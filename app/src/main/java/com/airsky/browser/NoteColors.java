package com.airsky.browser;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.ColorInt;

/** メモの背景色(Google Keep と同じ 12 色。明るい画面と暗い画面で色を変える) */
final class NoteColors {

    static final String[] NAMES = {
            Lang.t("デフォルト"), Lang.t("コーラル"), Lang.t("ピーチ"), Lang.t("サンド"), Lang.t("ミント"), Lang.t("セージ"),
            Lang.t("フォグ"), Lang.t("ストーム"), Lang.t("ダスク"), Lang.t("ブロッサム"), Lang.t("クレイ"), Lang.t("チョーク")};

    private static final int[] LIGHT = {
            0, 0xFFFAAFA8, 0xFFF39F76, 0xFFFFF8B8, 0xFFE2F6D3, 0xFFB4DDD3,
            0xFFD4E4ED, 0xFFAECCDC, 0xFFD3BFDB, 0xFFF6E2DD, 0xFFE9E3D4, 0xFFEFEFF1};
    private static final int[] DARK = {
            0, 0xFF77172E, 0xFF692B17, 0xFF7C4A03, 0xFF264D3B, 0xFF0C625D,
            0xFF256377, 0xFF284255, 0xFF472E5B, 0xFF6C394F, 0xFF4B443A, 0xFF232427};

    private NoteColors() {}

    static int count() {
        return LIGHT.length;
    }

    static boolean isDark(Context ctx) {
        return (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 色の番号から実際の色。0(デフォルト)は 0 を返すので、呼び出し側でテーマの色を使う */
    @ColorInt
    static int color(Context ctx, int index) {
        if (index <= 0 || index >= LIGHT.length) return 0;
        return isDark(ctx) ? DARK[index] : LIGHT[index];
    }
}
