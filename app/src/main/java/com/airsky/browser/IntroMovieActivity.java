package com.airsky.browser;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;

/**
 * 初回起動時のオープニング(旧 AZ_MOV)。
 *
 * スタートページと同じ青空の壁紙の上に「AirSky」と一言を、画面中央へ拡大しながらフェードで出す。
 * 幕と文字の色はアプリのテーマ(ライト / ダーク / システム)に合わせる。
 */
public class IntroMovieActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME = "selected_theme";
    private static final int THEME_LIGHT = 0;
    private static final int THEME_DARK = 1;

    /** スタートページの昼の壁紙(AutoWallpaper の 6:00〜16:00 と同じ)。 */
    private static final String SKY_ASSET = "browser/assets/img/bg-masthead.jpg";

    /** 文字を出し終えてから次の画面へ移るまで。 */
    private static final long HOLD_UNTIL = 4000;
    /** これより後なら画面をタップして飛ばせる。 */
    private static final long SKIPPABLE_AFTER = 1400;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable finishIntro = this::leave;
    private boolean leaving;
    private boolean fromAozora;
    private long startedAt;

    private ImageView background;
    private View logo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.az_mov);
        background = findViewById(R.id.background);
        logo = findViewById(R.id.intro_logo);

        boolean dark = isDark();
        applyColors(dark);
        loadSky();

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("isFirstMov", false).apply();

        fromAozora = AozoraMigrationActivity.shouldOffer(this, getIntent());
        // 起動直後は最初の描画まで時間がかかることがあるので、画面が出てから動かし始める
        startedAt = System.currentTimeMillis();
        logo.post(() -> {
            startedAt = System.currentTimeMillis();
            animateIn();
            handler.postDelayed(finishIntro, HOLD_UNTIL);
        });
        findViewById(R.id.intro_root).setOnClickListener(v -> {
            if (System.currentTimeMillis() - startedAt >= SKIPPABLE_AFTER) {
                handler.removeCallbacks(finishIntro);
                leave();
            }
        });
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** 設定のテーマ(システムなら端末の設定)がダークか。 */
    private boolean isDark() {
        int theme = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_THEME, 2);
        if (theme == THEME_DARK) return true;
        if (theme == THEME_LIGHT) return false;
        int night = Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyColors(boolean dark) {
        int base = dark ? Color.BLACK : Color.WHITE;
        findViewById(R.id.intro_root).setBackgroundColor(base);

        // 中央を少し濃くした幕で、空の明るい所に文字が重なっても読めるようにする
        GradientDrawable scrim = new GradientDrawable();
        scrim.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        scrim.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.9f);
        scrim.setColors(dark
                ? new int[]{Color.argb(170, 0, 0, 0), Color.argb(95, 0, 0, 0)}
                : new int[]{Color.argb(175, 255, 255, 255), Color.argb(70, 255, 255, 255)});
        findViewById(R.id.intro_scrim).setBackground(scrim);

        TextView title = findViewById(R.id.intro_title);
        TextView subtitle = findViewById(R.id.intro_subtitle);
        View line = findViewById(R.id.intro_line);
        subtitle.setText(Lang.t("美しさと安定性を両立させたブラウザ"));
        if (dark) {
            title.setTextColor(Color.WHITE);
            subtitle.setTextColor(0xFFCFE6F5);
            line.setBackgroundColor(0xFF7FCBF5);
            title.setShadowLayer(18f, 0f, 2f, Color.argb(140, 0, 20, 40));
            subtitle.setShadowLayer(10f, 0f, 1f, Color.argb(140, 0, 20, 40));
        } else {
            title.setTextColor(0xFF0F2C46);
            subtitle.setTextColor(0xFF2F5573);
            line.setBackgroundColor(0xFF1C74B5);
            title.setShadowLayer(18f, 0f, 2f, Color.argb(150, 255, 255, 255));
            subtitle.setShadowLayer(10f, 0f, 1f, Color.argb(150, 255, 255, 255));
        }

        // ステータスバー・ナビゲーションバーも壁紙の上に重ねる
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!dark);
        controller.setAppearanceLightNavigationBars(!dark);
    }

    /** 壁紙は大きい(数千 px)ので、画面を覆える大きさまで縮めて読み込む。 */
    private void loadSky() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int reqW = Math.max(1, dm.widthPixels);
        int reqH = Math.max(1, dm.heightPixels);
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = getAssets().open(SKY_ASSET)) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                int sample = 1;
                while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) {
                    sample *= 2;
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                try (InputStream in = getAssets().open(SKY_ASSET)) {
                    bitmap = BitmapFactory.decodeStream(in, null, options);
                }
            } catch (Exception | OutOfMemoryError e) { // 読めなくても文字だけで進める
                e.printStackTrace();
            }
            Bitmap result = bitmap;
            if (result == null) return;
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                background.setImageBitmap(result);
                background.animate().alpha(1f).setDuration(900)
                        .setInterpolator(new DecelerateInterpolator()).start();
            });
        }, "IntroSky").start();
    }

    private void animateIn() {
        PathInterpolator emphasized = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

        // 空はゆっくり引いていく
        background.setScaleX(1.14f);
        background.setScaleY(1.14f);
        background.animate().scaleX(1f).scaleY(1f).setDuration(4200)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();

        // ロゴ: 小さい所から拡大しながらフェードイン
        View title = findViewById(R.id.intro_title);
        title.setScaleX(0.55f);
        title.setScaleY(0.55f);
        title.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(350).setDuration(1100).setInterpolator(emphasized).start();

        // ロゴの下の線が中央から伸びる
        View line = findViewById(R.id.intro_line);
        line.setScaleX(0f);
        line.animate().alpha(1f).scaleX(1f)
                .setStartDelay(850).setDuration(800).setInterpolator(emphasized).start();

        // 一言は少し遅れて、同じく拡大しながら
        View subtitle = findViewById(R.id.intro_subtitle);
        subtitle.setScaleX(0.8f);
        subtitle.setScaleY(0.8f);
        subtitle.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(1050).setDuration(1000).setInterpolator(emphasized).start();
    }

    private void leave() {
        if (leaving) return;
        leaving = true;
        // ロゴは少し大きくなりながら消える
        logo.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f).setStartDelay(0).setDuration(380)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
                    // Aozora から引っ越しとして起動された時は、オープニングの後で引っ越すか聞く
                    Intent intent = new Intent(this,
                            fromAozora ? AozoraMigrationActivity.class : MainActivity.class);
                    startActivity(intent);
                    finish();
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }).start();
    }
}
