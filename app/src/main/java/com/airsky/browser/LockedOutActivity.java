package com.airsky.browser;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;

public class LockedOutActivity extends AppCompatActivity {

    private SharedPreferences appPrefs;

    private TextView timeout, cant_use_aozora;
    private LinearLayout statusbar;
    ImageView BackgroundImage;

    private String destinationActivity;
    private boolean requirePasswordOnFirstLaunch = true;

    private static final String PREFS_NAME = "theme_prefs";




    // PasswordActivityと共通
    private static final String PREF_FAILED_COUNT =
            "password_failed_count";


    private static final String PREF_LOCK_UNTIL =
            "password_lock_until";

    private ImageView Background;

    private LottieAnimationView lottieAnimationView;

    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_cant_use_aozora
        );
        TouchEffectView.attach(getWindow());

        Background =
                findViewById(
                        R.id.background
                );

        BackgroundImage =
                findViewById(
                        R.id.backgroundImage
                );

        timeout = findViewById(R.id.timeout);
        cant_use_aozora = findViewById(R.id.cant_use_aozora);
        statusbar = findViewById(R.id.statusbar);

        applySavedBackground();

        BackgroundImage.setVisibility(View.INVISIBLE);
        Background.setVisibility(View.INVISIBLE);

        // PasswordActivityから引き継いだ情報
        destinationActivity =
                getIntent().getStringExtra(
                        "destination_activity"
                );

        requirePasswordOnFirstLaunch =
                getIntent().getBooleanExtra(
                        "requirePasswordOnFirstLaunch",
                        true
                );

        // 未指定ならMainActivity
        if (destinationActivity == null ||
                destinationActivity.isEmpty()) {

            destinationActivity =
                    "MainActivity";
        }

        // AppPrefs
        appPrefs =
                getSharedPreferences(
                        "AppPrefs",
                        MODE_PRIVATE
                );

        lottieAnimationView =
                findViewById(
                        R.id.lottieAnimationView
                );

        lottieAnimationView.setAnimation(
                "animation/wrong.json"
        );

        Animation anim = AnimationUtils.loadAnimation(LockedOutActivity.this, R.anim.fade);

        new Handler().postDelayed(() -> {
            cant_use_aozora.startAnimation(anim);
            cant_use_aozora.setVisibility(View.VISIBLE);
        }, 2000);

        new Handler().postDelayed(() -> {
            timeout.startAnimation(anim);
            timeout.setVisibility(View.VISIBLE);
            statusbar.startAnimation(anim);
            statusbar.setVisibility(View.VISIBLE);
        }, 3000);

        new Handler().postDelayed(() -> {
            Background.startAnimation(anim);
            Background.setVisibility(View.VISIBLE);
            BackgroundImage.startAnimation(anim);
            BackgroundImage.setVisibility(View.VISIBLE);
        }, 4000);

        new Handler().postDelayed(() -> {

            lottieAnimationView.playAnimation();

        }, 5000);

        // ロック期限を確認
        checkLockState();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 画面に戻ってきたときにも再確認
        if (appPrefs != null) {
            checkLockState();
        }
    }

    /**
     * ロック状態を確認
     */

    private void checkLockState() {

        long lockUntil =
                appPrefs.getLong(
                        PREF_LOCK_UNTIL,
                        0
                );

        long now =
                System.currentTimeMillis();

        // まだロック中
        if (lockUntil > now) {

            updateTimeoutText(lockUntil);

            scheduleReturnToPassword(lockUntil);

            return;
        }

        // 期限切れ
        if (lockUntil != 0) {
            unlockAndReturn();
        }
    }

    private void updateTimeoutText(long lockUntil) {

        long remaining =
                lockUntil - System.currentTimeMillis();

        if (remaining <= 0) {
            timeout.setText(Lang.t("0分後にやり直して下さい"));
            return;
        }

        // 1分未満でも「1分」と表示
        long remainingMinutes =
                (remaining + 59999) / 60000;

        timeout.setText(
                remainingMinutes
                        + Lang.t("分後にやり直して下さい")
        );
    }

    /**
     * ロック解除時刻まで待機
     */
    private void scheduleReturnToPassword(long lockUntil) {

        handler.removeCallbacksAndMessages(null);

        long delay =
                lockUntil - System.currentTimeMillis();

        if (delay <= 0) {

            unlockAndReturn();

            return;
        }

        // 現在の残り時間を表示
        updateTimeoutText(lockUntil);

        // 1秒ごとに表示を更新
        handler.postDelayed(() -> {

            long currentLockUntil =
                    appPrefs.getLong(
                            PREF_LOCK_UNTIL,
                            0
                    );

            long now =
                    System.currentTimeMillis();

            if (currentLockUntil <= now) {

                unlockAndReturn();

            } else {

                updateTimeoutText(currentLockUntil);

                scheduleReturnToPassword(
                        currentLockUntil
                );
            }

        }, 1000);
    }

    /**
     * ロック解除してPasswordActivityへ戻る
     */
    private void unlockAndReturn() {

        // 失敗回数だけリセット
        // ロック時間はそのまま保持する
        appPrefs.edit()
                .putInt(
                        PREF_FAILED_COUNT,
                        0
                )
                .remove(
                        PREF_LOCK_UNTIL
                )
                .apply();

        Toast.makeText(
                this,
                Lang.t("再度認証できます。"),
                Toast.LENGTH_SHORT
        ).show();

        Intent intent =
                new Intent(
                        LockedOutActivity.this,
                        PasswordActivity.class
                );

        // 認証後の移動先を維持
        intent.putExtra(
                "destination_activity",
                destinationActivity
        );

        // 起動時パスワード設定を維持
        intent.putExtra(
                "requirePasswordOnFirstLaunch",
                requirePasswordOnFirstLaunch
        );

        startActivity(intent);

        overridePendingTransition(
                R.anim.fade,
                R.anim.no_animation
        );

        finish();
    }

    private void applySavedBackground() {
        // 壁紙はスタートページと同じもの(時間帯で変わる既定の壁紙・設定した画像)
        AppWallpaper.apply(BackgroundImage, Background);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {

        // 使用停止中は戻るボタンで抜けられない
        finishAffinity();
    }
}