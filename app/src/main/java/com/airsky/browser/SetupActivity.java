package com.airsky.browser;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.Button;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 初回セットアップ(Pixel のセットアップ風)。
 *
 * <p>ようこそ → 利用規約 → テーマ → (ガラスのデザインの画面) → 自分好みの設定 → 一画面/二画面 → 既定のブラウザ → 完了
 * の順に、下の「次へ」で進む。利用規約に同意しないと先へ進めない。背景はスタートページと同じ壁紙。</p>
 */
public class SetupActivity extends AppCompatActivity {

    static final int PAGE_WELCOME = 0;
    static final int PAGE_TERMS = 1;
    static final int PAGE_THEME = 2;
    static final int PAGE_CUSTOM = 3;
    static final int PAGE_SCREEN = 4;
    static final int PAGE_DEFAULT = 5;
    static final int PAGE_DONE = 6;

    /** 各ページのレイアウト(並びは PAGE_* と同じ)。 */
    private final int[] layouts = {
            R.layout.setup_page1,
            R.layout.setup_page_terms,
            R.layout.setup_page2,
            R.layout.setup_page3,
            R.layout.setup_page_screen,
            R.layout.setup_page4,
            R.layout.setup_page5
    };

    private static final Interpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private ViewPager viewPager;
    private Button btnPrev, btnNext;
    private SetupPagerAdapter adapter;

    /** ガラスのデザインの画面。「次へ」で戻ってきたら次のページへ。 */
    private final ActivityResultLauncher<Intent> glassLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) goTo(PAGE_CUSTOM);
            });

    /** アップデートの通知を出すための許可。許可してもしなくても次へ進む。 */
    private final ActivityResultLauncher<String> notificationLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> goTo(PAGE_SCREEN));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        TouchEffectView.attach(getWindow());
        // 壁紙はスタートページと同じもの(時間帯で変わる既定の壁紙・設定した画像)
        AppWallpaper.apply(findViewById(R.id.backgroundImage), null);

        viewPager = findViewById(R.id.viewPager);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);

        adapter = new SetupPagerAdapter(this, layouts);
        viewPager.setAdapter(adapter);
        // テーマを変えて画面が作り直されても、ページは ViewPager が覚えていて戻してくれる
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                updateButtons(position);
                playIllustration(position);
            }
        });
        updateButtons(viewPager.getCurrentItem());

        btnPrev.setOnClickListener(v -> onPrev());
        btnNext.setOnClickListener(v -> onNext());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewPager.getCurrentItem() == PAGE_WELCOME) {
                    finish();
                } else {
                    onPrev();
                }
            }
        });
    }

    private void onPrev() {
        int pos = viewPager.getCurrentItem();
        if (pos == PAGE_TERMS) {
            confirmDecline();
        } else if (pos > 0) {
            goTo(pos - 1);
        }
    }

    private void onNext() {
        int pos = viewPager.getCurrentItem();
        switch (pos) {
            case PAGE_TERMS:
                Terms.accept(this);
                goTo(PAGE_THEME);
                break;
            case PAGE_THEME:
                // ガラスのデザインは専用の画面(設定と同じ Pixel 風の画面)で選ぶ
                glassLauncher.launch(GlassSettingsActivity.setupIntent(this));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                break;
            case PAGE_CUSTOM:
                if (UpdateChecker.isEnabled(this) && Build.VERSION.SDK_INT >= 33
                        && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                } else {
                    goTo(PAGE_SCREEN);
                }
                break;
            case PAGE_DONE:
                // 最終ページ → 測定画面へ遷移
                startActivity(new Intent(this, SetupProgress.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
                break;
            default:
                goTo(pos + 1);
                break;
        }
    }

    /** 隣のページへ。入ってくるページは進む向きから少し寄ってきながらフェードインする。 */
    private void goTo(int target) {
        int from = viewPager.getCurrentItem();
        if (target == from || target < 0 || target >= layouts.length) return;
        viewPager.setCurrentItem(target, false);
        View page = adapter.viewAt(target);
        if (page == null) return;
        float dir = target > from ? 1f : -1f;
        page.animate().cancel();
        page.setAlpha(0f);
        page.setTranslationX(dir * 56 * getResources().getDisplayMetrics().density);
        page.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(420)
                .setInterpolator(EMPHASIZED)
                .start();
    }

    private void updateButtons(int position) {
        btnPrev.setVisibility(position == PAGE_WELCOME ? View.INVISIBLE : View.VISIBLE);
        btnPrev.setText(position == PAGE_TERMS ? Lang.t("同意しない") : Lang.t("戻る"));
        switch (position) {
            case PAGE_WELCOME:
                btnNext.setText(Lang.t("始める"));
                break;
            case PAGE_TERMS:
                btnNext.setText(Lang.t("同意する"));
                break;
            case PAGE_DONE:
                btnNext.setText(Lang.t("完了"));
                break;
            default:
                btnNext.setText(Lang.t("次へ"));
                break;
        }
    }

    /** ページを開くたびに、そのページのイラスト(Lottie)を最初から再生する。 */
    private void playIllustration(int position) {
        View page = adapter.viewAt(position);
        if (page == null) return;
        LottieAnimationView lottie = page.findViewById(R.id.setup_lottie);
        if (lottie == null) return;
        lottie.setProgress(0f);
        lottie.playAnimation();
    }

    private void confirmDecline() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("利用規約に同意しない"))
                .setMessage(Lang.t("利用規約に同意いただけない場合、AirSky をご利用いただけません。アプリを終了しますか?"))
                .setPositiveButton(Lang.t("戻る"), null)
                .setNegativeButton(Lang.t("終了"), (d, w) -> finishAffinity())
                .show();
    }
}
