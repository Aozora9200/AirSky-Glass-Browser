package com.airsky.browser;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;
import com.google.android.material.color.MaterialColors;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 画面上部から降りてくる通知(Chrome の「1 件のファイルをダウンロードしています...」や
 * 「ページが翻訳されました」と同じ見た目)。一定時間で、または上へスワイプすると引っ込む。
 * 1 画面に同時に出すのは 1 つだけで、新しいものを出すと前のものは差し替わる。
 */
public final class TopMessage {

    public static final long DURATION_SHORT = 4000;
    public static final long DURATION_LONG = 7000;

    private static final Object TAG = new Object();

    private final Activity activity;
    private final View view;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long duration = DURATION_SHORT;
    private boolean dismissed;

    private TopMessage(@NonNull Activity activity) {
        this.activity = activity;
        this.view = LayoutInflater.from(activity).inflate(R.layout.view_top_message,
                (ViewGroup) activity.findViewById(android.R.id.content), false);
        view.setTag(TAG);
    }

    public static TopMessage make(@NonNull Activity activity, @DrawableRes int icon, @NonNull CharSequence title) {
        TopMessage m = new TopMessage(activity);
        ImageView iv = m.view.findViewById(R.id.topMessageIcon);
        if (icon != 0) iv.setImageResource(icon); else iv.setVisibility(View.GONE);
        ((TextView) m.view.findViewById(R.id.topMessageTitle)).setText(title);
        return m;
    }

    /**
     * アイコンを Lottie のアニメーションにする(繰り返し再生)。線の色はテーマの colorPrimary に合わせる。
     */
    public TopMessage setAnimation(@RawRes int lottie) {
        LottieAnimationView anim = view.findViewById(R.id.topMessageAnimation);
        view.findViewById(R.id.topMessageIcon).setVisibility(View.GONE);
        anim.setVisibility(View.VISIBLE);
        anim.setAnimation(lottie);
        anim.setRepeatCount(LottieDrawable.INFINITE);
        int color = MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary, 0xFF1A73E8);
        anim.addValueCallback(new KeyPath("**"), LottieProperty.STROKE_COLOR,
                new LottieValueCallback<>(color));
        anim.playAnimation();
        return this;
    }

    public TopMessage setSubtitle(@Nullable CharSequence subtitle) {
        TextView tv = view.findViewById(R.id.topMessageSubtitle);
        tv.setText(subtitle);
        tv.setVisibility(subtitle == null || subtitle.length() == 0 ? View.GONE : View.VISIBLE);
        return this;
    }

    /** 右端の文字ボタン(「詳細」「元に戻す」など)。押すと閉じてから action を実行する。 */
    public TopMessage setAction(@NonNull CharSequence label, @NonNull Runnable action) {
        Button b = view.findViewById(R.id.topMessageAction);
        b.setText(label);
        b.setVisibility(View.VISIBLE);
        b.setOnClickListener(v -> {
            dismiss();
            action.run();
        });
        return this;
    }

    /** 文字ボタンの左に置くアイコンボタン(翻訳の設定など)。区切り線も出す。 */
    public TopMessage setSecondaryIcon(@DrawableRes int icon, @NonNull CharSequence description,
                                       @NonNull Runnable action) {
        ImageButton b = view.findViewById(R.id.topMessageSecondary);
        b.setImageResource(icon);
        b.setContentDescription(description);
        b.setVisibility(View.VISIBLE);
        view.findViewById(R.id.topMessageDivider).setVisibility(View.VISIBLE);
        b.setOnClickListener(v -> {
            dismiss();
            action.run();
        });
        return this;
    }

    /** 表示時間(ms)。0 以下なら自動では閉じない。 */
    public TopMessage setDuration(long ms) {
        duration = ms;
        return this;
    }

    public void show() {
        if (activity.isFinishing()) return;
        FrameLayout root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        // 前の通知は差し替える
        for (int i = root.getChildCount() - 1; i >= 0; i--) {
            View child = root.getChildAt(i);
            if (child.getTag() == TAG) root.removeView(child);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        float density = activity.getResources().getDisplayMetrics().density;
        int margin = Math.round(12 * density);
        // 端末のステータスバーに重ならないようにする(画面全体に描画している場合)
        int statusBar = 0;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
        if (insets != null) statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
        int[] loc = new int[2];
        root.getLocationInWindow(loc);
        int top = Math.max(0, statusBar - loc[1]);
        // ブラウザ画面ではアドレスバー・プログレスバーの下に出す(URL バーに被せない)
        top = Math.max(top, bottomOf(R.id.statusbar, loc[1]));
        top = Math.max(top, bottomOf(R.id.action_Bar, loc[1]));
        top = Math.max(top, bottomOf(R.id.progressBar, loc[1]));
        lp.setMargins(margin, top + Math.round(8 * density), margin, 0);
        root.addView(view, lp);
        installSwipeToDismiss();

        view.setAlpha(0f);
        view.post(() -> {
            view.setTranslationY(-view.getHeight() - view.getTop());
            view.animate().translationY(0).alpha(1f).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator()).start();
        });
        if (duration > 0) handler.postDelayed(this::dismiss, duration);
    }

    /** 画面のバーが表示されていれば、その下端(content からの距離)。無い・隠れていれば 0。 */
    private int bottomOf(int id, int rootTop) {
        // この画面にあるバーだけ(二画面では URL バーが別の画面へ移っている)
        View v = activity.getWindow().getDecorView().findViewById(id);
        if (v == null || v.getVisibility() != View.VISIBLE || v.getHeight() == 0) return 0;
        int[] loc = new int[2];
        v.getLocationInWindow(loc); // スクロールで隠れている分(translationY)も反映される
        // 画面の下に置いたバー(タブレット UI でバーを下にした時)は避けなくてよい
        View screen = activity.getWindow().getDecorView();
        if (loc[1] > screen.getHeight() / 2) return 0;
        return Math.max(0, loc[1] + v.getHeight() - rootTop);
    }

    public void dismiss() {
        if (dismissed) return;
        dismissed = true;
        handler.removeCallbacksAndMessages(null);
        view.animate().translationY(-view.getHeight() - view.getTop()).alpha(0f).setDuration(200)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    ViewGroup parent = (ViewGroup) view.getParent();
                    if (parent != null) parent.removeView(view);
                }).start();
    }

    /** 上へスワイプで閉じる。触っている間は自動で閉じない。 */
    private void installSwipeToDismiss() {
        view.setOnTouchListener(new View.OnTouchListener() {
            float downY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = e.getRawY();
                        handler.removeCallbacksAndMessages(null);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        v.setTranslationY(Math.min(0, e.getRawY() - downY));
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (v.getTranslationY() < -v.getHeight() / 3f) {
                            dismiss();
                        } else {
                            v.animate().translationY(0).setDuration(150).start();
                            if (duration > 0) handler.postDelayed(TopMessage.this::dismiss, duration);
                        }
                        return true;
                }
                return false;
            }
        });
    }
}
