package com.airsky.browser;

import androidx.appcompat.app.AppCompatActivity;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BootingActivity extends AppCompatActivity {

    private static final long ICON_ANIM_MS = 600L;

    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME = "selected_theme";
    private static final int THEME_LIGHT = 0;
    private static final int THEME_DARK = 1;
    private static final int THEME_SYSTEM = 2;
    private ImageView Background;

    private static final String POPUP_PREFS_NAME = "popup_prefs";
    private static final String KEY_POPUP = "selected_popupbutton";
    private static final int POPUPBUTTON_LIGHT = 0;
    private static final int POPUPBUTTON_DARK = 1;
    private static final int POPUPBUTTON_SYSTEM = 2;

    private MediaPlayer mediaPlayer;
    private long splashStartTime;
    private ExecutorService ioExecutor;
    private Handler mainHandler;
    private ProgressBar progressBar;

    private View splash;
    private ImageView splashIcon;
    private final java.util.List<Animator> dotAnimators = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        SharedPreferences setupprefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstMov = setupprefs.getBoolean("isFirstMov", true);
        // 初回起動で、Aozora から「データを引っ越す」として起動された(Aozora_to_AirSky = true)
        boolean fromAozora = AozoraMigrationActivity.shouldOffer(this, getIntent());
        if (AozoraMigrationActivity.shouldOfferReplace(this, getIntent())) {
            // 使っている途中に Aozora から引っ越しとして起動された: 警告してから既存のデータを上書きする
            startActivity(new Intent(this, AozoraMigrationActivity.class)
                    .putExtra(AozoraMigrationActivity.EXTRA_REPLACE, true));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return;
        }
        if (isFirstMov) {
            Intent intent = new Intent(this, IntroMovieActivity.class);
            // 動画の後で「Aozora からデータを引っ越ししますか？」を出す
            if (fromAozora) intent.putExtra(AozoraTransfer.EXTRA_FROM_AOZORA, true);
            startActivity(intent);
            finish();
            return;
        }
        if (fromAozora) {
            // 動画はもう見た(セットアップの途中でやめた時など): すぐに聞く
            startActivity(new Intent(this, AozoraMigrationActivity.class));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return;
        }

        boolean bootScreen = setupprefs.getBoolean("bootScreen", false);
        if (!bootScreen) {
            Intent intent = new Intent(this, MainActivity.class);
            finish(); // ← 今のアクティビティを閉じたい場合
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return;
        }
        setContentView(R.layout.booting);
        TouchEffectView.attach(getWindow());
        Background = findViewById(R.id.background);
        applySavedBackground();
        applyBackTheme();
        initializeViews();
        applyMenuButtonStyle();
        initializeThreading();
        onProgressChanged(40);
        ioExecutor.execute(this::loadSplashIconAndPlaySound);
        splashStartTime = System.currentTimeMillis();
        playStartupAnimation();
        mainHandler.postDelayed(() -> onProgressChanged(100), 1300);
        mainHandler.postDelayed(this::playExitAnimation, 1780);
        mainHandler.postDelayed(() -> {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish(); // ← 今のアクティビティを閉じたい場合
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 2000); // 2秒 (2000ミリ秒)
    }

    private void applyMenuButtonStyle() {
        SharedPreferences prefs = getSharedPreferences(POPUP_PREFS_NAME, MODE_PRIVATE);
        int popup = prefs.getInt(KEY_POPUP, POPUPBUTTON_SYSTEM);
        SharedPreferences themeprefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int theme = themeprefs.getInt(KEY_THEME, THEME_SYSTEM);

        switch (popup) {
            case POPUPBUTTON_LIGHT:
                splashIcon.setImageResource(R.mipmap.aozora1);
                break;
            case POPUPBUTTON_DARK:
                splashIcon.setImageResource(R.mipmap.aozora);
                break;
            case POPUPBUTTON_SYSTEM:
            default:
                switch (theme) {
                    case THEME_LIGHT:
                        splashIcon.setImageResource(R.mipmap.aozora1);
                        break;
                    case THEME_DARK:
                        splashIcon.setImageResource(R.mipmap.aozora);
                        break;
                    case THEME_SYSTEM:
                    default:
                        // OS 側の設定に従う
                        int nightModeFlags = getResources().getConfiguration().uiMode
                                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;

                        if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                            splashIcon.setImageResource(R.mipmap.aozora);
                        } else {
                            splashIcon.setImageResource(R.mipmap.aozora1);
                        }
                        break;
                }
                break;
        }
    }


    private void applySavedBackground() {
        // 壁紙はスタートページと同じもの(時間帯で変わる既定の壁紙・設定した画像)
        AppWallpaper.apply(findViewById(R.id.backgroundImage), Background);
    }



    private void applyBackTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int theme = prefs.getInt(KEY_THEME, THEME_SYSTEM);
        int black = getResources().getColor(R. color. backgroundBlack);
        int white = getResources().getColor(R. color. backgroundWhite);

        switch (theme) {
            case THEME_LIGHT:
                Background.setBackgroundColor(white);
                break;
            case THEME_DARK:
                Background.setBackgroundColor(black);
                break;
            case THEME_SYSTEM:
            default:
                // OS 側の設定に従う
                int nightModeFlags = getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK;

                if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                    Background.setBackgroundColor(black);
                } else {
                    Background.setBackgroundColor(white);
                }
                break;
        }
    }

    private void onProgressChanged(int percentage){
        Animator animation = ObjectAnimator.ofInt(progressBar,"progress",percentage);
        animation.setDuration(500); // 0.5秒間でアニメーションする
        animation.setInterpolator(new DecelerateInterpolator());
        animation.start();
    }

    private void loadSplashIconAndPlaySound() {
        Bitmap bitmap = null;
        InputStream is = null;
        try {
            is = getAssets().open("aozora.png");
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opt);
            closeQuietly(is);

            opt.inSampleSize = calculateInSampleSize(opt.outWidth, opt.outHeight, 512, 512);
            opt.inJustDecodeBounds = false;
            opt.inPreferredConfig = Bitmap.Config.RGB_565;
            opt.inDither = false;
            opt.inMutable = false;
            is = getAssets().open("aozora.png");
            bitmap = BitmapFactory.decodeStream(is, null, opt);
        } catch (IOException e) {

        } finally {
            closeQuietly(is);
        }

        final Bitmap finalBitmap = bitmap;
        mainHandler.post(() -> {
            //if (splashIcon != null && finalBitmap != null) splashIcon.setImageBitmap(finalBitmap);
            SharedPreferences setupprefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            boolean bootSound = setupprefs.getBoolean("bootSound", false);
            if (bootSound) {
                playBootSound();
            }
        });
    }

    private static int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height >> 1;
            int halfWidth = width >> 1;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize <<= 1;
            }
        }
        return inSampleSize;
    }

    private void initializeViews() {
        splash = findViewById(R.id.splash);
        splashIcon = findViewById(R.id.splashIcon);
        progressBar = findViewById(R.id.booting_progress);
    }

    private void initializeThreading() {
        mainHandler = new Handler(Looper.getMainLooper());
        ioExecutor = Executors.newSingleThreadExecutor();
    }

    private static void closeQuietly(InputStream is) {
        if (is != null) try { is.close(); } catch (IOException ignored) {}
    }

    private void playBootSound() {
        releaseMediaPlayer();
        try {
            MediaPlayer mp = new MediaPlayer();
            mediaPlayer = mp;
            android.content.res.AssetFileDescriptor afd = getAssets().openFd("boot.ogg");
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setLooping(false);
            mp.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setVolume(0.8f, 0.8f);
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnCompletionListener(m -> releaseMediaPlayer());
            mp.setOnErrorListener((m, what, extra) -> { releaseMediaPlayer(); return true; });
            mp.prepareAsync();
        } catch (IOException e) {

            releaseMediaPlayer();
        }
    }
    private void releaseMediaPlayer() {
        final MediaPlayer mp = mediaPlayer;
        mediaPlayer = null;
        if (mp != null) {
            try { mp.stop(); } catch (Throwable ignored) {}
            mp.release();
        }
    }

    /**
     * Pixel の起動のような動き。
     * アイコンの丸がふわっと大きくなり、輪が外へ広がって消える。名前が下から現れ、
     * 4 色の点が波のように跳ね続ける。
     */
    private void playStartupAnimation() {
        float d = getResources().getDisplayMetrics().density;
        android.view.animation.PathInterpolator emphasized =
                new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

        View box = findViewById(R.id.splashIconBox);
        box.setScaleX(0.4f);
        box.setScaleY(0.4f);
        box.setAlpha(0f);
        box.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(ICON_ANIM_MS)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();
        splashIcon.setRotation(-90f);
        splashIcon.setScaleX(0.5f);
        splashIcon.setScaleY(0.5f);
        splashIcon.animate().rotation(0f).scaleX(1f).scaleY(1f).setStartDelay(80).setDuration(700)
                .setInterpolator(emphasized).start();

        View ring = findViewById(R.id.splashRing);
        ring.animate().alpha(0.9f).setStartDelay(260).setDuration(120).withEndAction(() ->
                ring.animate().alpha(0f).scaleX(1.45f).scaleY(1.45f).setStartDelay(0).setDuration(900)
                        .setInterpolator(new DecelerateInterpolator(2f)).start()).start();

        int[] ids = {R.id.title, R.id.subtitle, R.id.booting};
        for (int i = 0; i < ids.length; i++) {
            View v = findViewById(ids[i]);
            v.setAlpha(0f);
            v.setTranslationY(16 * d);
            v.animate().alpha(1f).translationY(0f).setStartDelay(320L + 90L * i).setDuration(520)
                    .setInterpolator(emphasized).start();
        }

        View footer = findViewById(R.id.splashFooter);
        footer.setAlpha(0f);
        footer.animate().alpha(1f).setStartDelay(500).setDuration(500).start();

        android.view.ViewGroup dots = findViewById(R.id.splashDots);
        for (int i = 0; i < dots.getChildCount(); i++) {
            View dot = dots.getChildAt(i);
            dot.setScaleX(0f);
            dot.setScaleY(0f);
            dot.animate().scaleX(1f).scaleY(1f).setStartDelay(520L + 70L * i).setDuration(320)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
            ObjectAnimator hop = ObjectAnimator.ofFloat(dot, View.TRANSLATION_Y, 0f, -9 * d, 0f);
            hop.setDuration(620);
            hop.setStartDelay(860L + 110L * i);
            hop.setRepeatCount(ObjectAnimator.INFINITE);
            hop.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            hop.start();
            dotAnimators.add(hop);
        }
    }

    /** 終わり: 全体が少し大きくなりながら薄くなる(次の画面へフェードでつながる)。 */
    private void playExitAnimation() {
        if (isFinishing()) return;
        splash.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f).setDuration(260)
                .setInterpolator(new android.view.animation.PathInterpolator(0.3f, 0f, 0.8f, 0.15f)).start();
        findViewById(R.id.splashFooter).animate().alpha(0f).setDuration(200).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (Animator a : dotAnimators) a.cancel();
        dotAnimators.clear();
        // 起動音は次の画面に移っても最後まで鳴らす(鳴り終わると releaseMediaPlayer される)
        if (mainHandler != null) mainHandler.removeCallbacksAndMessages(null);
    }
}
