package com.airsky.browser;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 設定 › データの引っ越し。別の端末の AirSky Browser と、タブ一覧・非アクティブなタブ・タブグループ・履歴・
 * ブックマーク・保存したページ・設定・タブごとのページ遷移をやりとりする(中身は {@link DeviceMigration})。
 *
 * <ul>
 *   <li>QR コード: 送る端末が Wi-Fi で待ち受け、接続先と暗号化の鍵を QR コードで見せる。受け取る端末が読み取ってつなぐ。</li>
 *   <li>Bluetooth: 受け取る端末が待ち受け、送る端末がペア設定済みの端末から選んでつなぐ。</li>
 *   <li>Quick Share: 引っ越しファイル(.airsky)を共有し、受け取る端末で「ファイルから受け取る」で選ぶ。</li>
 * </ul>
 *
 * <p>受け取ったら内容を見せて確認し、{@link DeviceMigration#stage} してから AirSky を起動し直す。
 * 起動直後に反映され、この画面が {@link #EXTRA_FINISHED} 付きで開いて結果を見せる。</p>
 *
 * <p>画面は Pixel の初期設定風(大きな見出し・角の丸いカード)。ページは左右に滑って切り替わり、
 * 中身は上から順にふわっと出る。</p>
 */
@SuppressLint("MissingPermission")
public class DeviceMigrationActivity extends AppCompatActivity {

    /** 引っ越しを反映して起動し直した後に開く時に付ける。 */
    static final String EXTRA_FINISHED = "migrationFinished";
    /** ブラウザの QR コード読み取りで引っ越しの QR コードを読んだ時に、その中身を渡す。 */
    static final String EXTRA_TICKET = "migrationTicket";

    private static final int REQ_BT_PERMISSION = 71;
    private static final int REQ_BT_ENABLE = 72;
    private static final int REQ_PICK_FILE = 73;

    private final ExecutorService io = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());
    private float d;

    private FrameLayout stage;
    private View currentView;
    private TextView barTitle;

    /** 今のページと、戻った時に出すページ。 */
    private interface Page {
        View build();
    }

    private Page current;
    private final ArrayDeque<Page> history = new ArrayDeque<>();
    /** 受け取る方法の選択(QR コードを読んだ後は、ここまで戻ってから進む)。 */
    private final Page receiveMethods = this::receiveMethodsPage;

    /** 実行中の処理の番号。中止したら増やし、古い処理の結果は捨てる。 */
    private volatile int op;
    /** 受け渡しの最中(戻る時に中止してよいか聞く)。 */
    private boolean transferring;
    /** 中止した時に閉じる接続。 */
    private volatile Closeable active;
    /** Bluetooth の許可・オンを待っている間の続き。 */
    @Nullable private Runnable afterBluetooth;
    /** 端末の一覧のページを開いている(Bluetooth の設定から戻ったら作り直す)。 */
    private boolean btListShown;

    /** 書き出した引っ越しファイル。 */
    @Nullable private File bundle;
    @Nullable private DeviceMigration.Summary bundleSummary;
    /** 受け取った引っ越しファイル。 */
    @Nullable private File received;
    @Nullable private DeviceMigration.Summary receivedSummary;
    @Nullable private Bitmap qrBitmap;
    @Nullable private String qrNotice;

    /** 受け渡しのページの部品。 */
    private MigrationViews.RingView ring;
    private TextView ringTitle;
    private TextView ringSub;
    private TextView ringDetail;
    private int lastPercent = -1;

    // ------------------------------------------------------------------
    // 開く・閉じる
    // ------------------------------------------------------------------

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        d = getResources().getDisplayMetrics().density;
        TouchEffectView.attach(getWindow());
        int bg = ContextCompat.getColor(this, R.color.ps_page_bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat insets = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insets.setAppearanceLightStatusBars(!night);
        insets.setAppearanceLightNavigationBars(!night);
        setContentView(buildFrame());

        if (getIntent().getBooleanExtra(EXTRA_FINISHED, false)) {
            JSONObject result = DeviceMigration.takeResult(this);
            if (result == null && savedInstanceState != null) {
                try {
                    String s = savedInstanceState.getString("result");
                    if (s != null) result = new JSONObject(s);
                } catch (Exception ignored) {
                }
            }
            finishedResult = result;
            show(this::finishedPage, 0);
            return;
        }
        show(this::homePage, 0);
        MigrationChannel.Ticket ticket = MigrationChannel.Ticket.parse(getIntent().getStringExtra(EXTRA_TICKET));
        if (ticket != null) {
            go(receiveMethods);
            receiveWithTicket(ticket);
        }
    }

    @Nullable private JSONObject finishedResult;

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (finishedResult != null) out.putString("result", finishedResult.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Bluetooth の設定でペア設定して戻ってきた: 端末の一覧を作り直す
        if (btListShown && current != null && !transferring) show(current, 0);
    }

    @Override
    protected void onDestroy() {
        cancelOperation();
        io.shutdownNow();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        goBack();
    }

    private void goBack() {
        if (getIntent().getBooleanExtra(EXTRA_FINISHED, false)) {
            openBrowser();
            return;
        }
        if (transferring) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(Lang.t("引っ越しを中止しますか?"))
                    .setMessage(Lang.t("データの受け渡しを途中でやめます。どちらの端末のデータも変わりません。"))
                    .setPositiveButton(Lang.t("中止する"), (dlg, w) -> {
                        cancelOperation();
                        popPage();
                    })
                    .setNegativeButton(Lang.t("続ける"), null)
                    .show();
            return;
        }
        cancelOperation();
        popPage();
    }

    private void popPage() {
        Page prev = history.pollLast();
        if (prev == null) {
            finish();
            overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
            return;
        }
        current = prev;
        show(prev, -1);
    }

    /** 実行中の受け渡しを止める(接続を閉じ、結果を捨てる)。 */
    private void cancelOperation() {
        op++;
        transferring = false;
        btListShown = false;
        Closeable c = active;
        active = null;
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ------------------------------------------------------------------
    // ページの切り替え
    // ------------------------------------------------------------------

    @NonNull
    private View buildFrame() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.ps_page_bg));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(8), 0);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_si_back);
        back.setContentDescription(Lang.t("戻る"));
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        back.setBackgroundResource(tv.resourceId);
        back.setOnClickListener(v -> goBack());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        barTitle = new TextView(this);
        barTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        barTitle.setTextColor(onSurface());
        barTitle.setSingleLine(true);
        barTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        barTitle.setAlpha(0f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(16);
        bar.addView(barTitle, tlp);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        stage = new FrameLayout(this);
        stage.setClipChildren(false);
        root.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    /** 次のページへ進む(戻ると今のページに戻る)。 */
    private void go(@NonNull Page page) {
        if (current != null) history.addLast(current);
        current = page;
        show(page, 1);
    }

    /** 今のページを入れ替える(戻る先は変えない。受け渡しの途中の段階など)。 */
    private void replace(@NonNull Page page) {
        current = page;
        show(page, 1);
    }

    /**
     * ページを出す。Material の「共有軸(X)」の動き: 前のページは少し左へ滑りながら消え、
     * 新しいページは右から滑り込む(戻る時は逆)。
     */
    private void show(@NonNull Page page, int direction) {
        current = page;
        btListShown = false;
        barTitle.animate().alpha(0f).setDuration(100).start();
        View next = page.build();
        View old = currentView;
        currentView = next;
        stage.addView(next, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (old != null) {
            old.animate().cancel();
            old.animate().alpha(0f).translationX(-direction * dp(32)).setDuration(direction == 0 ? 90 : 150)
                    .setInterpolator(MigrationViews.STANDARD)
                    .withEndAction(() -> stage.removeView(old)).start();
        }
        next.setAlpha(0f);
        next.setTranslationX(direction * dp(32));
        next.animate().alpha(1f).translationX(0f).setStartDelay(old != null ? 70 : 0).setDuration(380)
                .setInterpolator(MigrationViews.EMPHASIZED).start();
    }

    // ------------------------------------------------------------------
    // ページの部品(Pixel の初期設定風)
    // ------------------------------------------------------------------

    /** 大きな見出しのあるページ。content に中身を足し、必要なら bottom にボタンを置く。 */
    private final class Scaffold {
        final LinearLayout root = new LinearLayout(DeviceMigrationActivity.this);
        final NestedScrollView scroll = new NestedScrollView(DeviceMigrationActivity.this);
        final LinearLayout content = new LinearLayout(DeviceMigrationActivity.this);
        final LinearLayout bottom = new LinearLayout(DeviceMigrationActivity.this);
        final TextView title = new TextView(DeviceMigrationActivity.this);
        final TextView sub = new TextView(DeviceMigrationActivity.this);

        Scaffold(@DrawableRes int icon, @ColorInt int toneBg, @ColorInt int toneFg,
                 @NonNull String titleText, @Nullable CharSequence subText) {
            root.setOrientation(LinearLayout.VERTICAL);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(24), dp(4), dp(24), dp(32));
            content.setClipChildren(false);
            content.setClipToPadding(false);
            scroll.setClipToPadding(false);
            scroll.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            if (icon != 0) {
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(56), dp(56));
                ilp.topMargin = dp(8);
                content.addView(toneIcon(icon, toneBg, toneFg, 56, 30), ilp);
            }
            title.setText(titleText);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
            title.setTextColor(onSurface());
            title.setLineSpacing(0, 1.05f);
            content.addView(title, matchWrap(0, dp(icon != 0 ? 20 : 12)));
            if (subText != null) {
                sub.setText(subText);
                sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                sub.setTextColor(onSurfaceVariant());
                sub.setLineSpacing(0, 1.2f);
                content.addView(sub, matchWrap(0, dp(12)));
            }
            // 見出しが隠れたら上のバーに小さく出す
            scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, x, y, ox, oy) -> {
                float target = y > title.getBottom() - dp(8) ? 1f : 0f;
                if (barTitle.getTag() == null || (float) barTitle.getTag() != target) {
                    barTitle.setTag(target);
                    barTitle.setText(titleText);
                    barTitle.animate().alpha(target).setDuration(150).start();
                }
            });
            barTitle.setTag(0f);

            bottom.setOrientation(LinearLayout.HORIZONTAL);
            bottom.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            bottom.setPadding(dp(16), dp(12), dp(24), dp(20));
            bottom.setVisibility(View.GONE);
            root.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        /** 下のバーに左寄せのボタン(キャンセルなど)を足す。 */
        void leftButton(@NonNull View b) {
            bottom.setVisibility(View.VISIBLE);
            if (bottom.getChildCount() == 0) {
                bottom.addView(b);
                View space = new View(DeviceMigrationActivity.this);
                bottom.addView(space, new LinearLayout.LayoutParams(0, 1, 1f));
            } else {
                bottom.addView(b, 0);
            }
        }

        /** 下のバーに右寄せのボタンを足す。 */
        void rightButton(@NonNull View b) {
            bottom.setVisibility(View.VISIBLE);
            if (bottom.getChildCount() == 0) {
                View space = new View(DeviceMigrationActivity.this);
                bottom.addView(space, new LinearLayout.LayoutParams(0, 1, 1f));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(8);
            bottom.addView(b, lp);
        }

        /** できあがったページ。中身は上から順にふわっと出る。 */
        @NonNull
        View done() {
            for (int i = 0; i < content.getChildCount(); i++) {
                View c = content.getChildAt(i);
                c.setAlpha(0f);
                c.setTranslationY(dp(20));
                c.animate().alpha(1f).translationY(0f).setStartDelay(90 + Math.min(i, 10) * 45L)
                        .setDuration(450).setInterpolator(MigrationViews.EMPHASIZED).start();
            }
            if (bottom.getVisibility() == View.VISIBLE) {
                bottom.setAlpha(0f);
                bottom.animate().alpha(1f).setStartDelay(250).setDuration(300).start();
            }
            return root;
        }
    }

    @NonNull
    private View toneIcon(@DrawableRes int icon, @ColorInt int bg, @ColorInt int fg, int sizeDp, int iconDp) {
        FrameLayout box = new FrameLayout(this);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(bg);
        box.setBackground(circle);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(fg));
        box.addView(iv, new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER));
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return box;
    }

    /** 大きなカードのボタン(送る・受け取る・方法の選択)。押すと少し縮む。 */
    @NonNull
    private View actionCard(@DrawableRes int icon, int toneBgRes, int toneFgRes, @NonNull String title,
                            @NonNull String sub, @NonNull Runnable onClick) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(20), dp(20), dp(16), dp(20));
        card.setBackground(rippleCard(dp(28), dp(28)));
        card.addView(toneIcon(icon, color(toneBgRes), color(toneFgRes), 52, 26));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        t.setTextColor(onSurface());
        texts.addView(t);
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        s.setTextColor(onSurfaceVariant());
        s.setPadding(0, dp(2), 0, 0);
        texts.addView(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(16);
        card.addView(texts, lp);
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_mig_chevron);
        chevron.setImageTintList(ColorStateList.valueOf(onSurfaceVariant()));
        card.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));
        pressable(card, onClick);
        return card;
    }

    /** 押すと縮み、離すと少し弾んで戻る。 */
    @SuppressLint("ClickableViewAccessibility")
    private void pressable(@NonNull View v, @NonNull Runnable onClick) {
        v.setClickable(true);
        v.setOnTouchListener((view, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(120).setStartDelay(0)
                        .setInterpolator(MigrationViews.STANDARD).start();
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(300).setStartDelay(0)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f)).start();
            }
            return false;
        });
        v.setOnClickListener(view -> onClick.run());
    }

    @NonNull
    private RippleDrawable rippleCard(float top, float bottom) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(R.color.ps_card_bg));
        bg.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        int ripple = MigrationViews.attr(this, androidx.appcompat.R.attr.colorControlHighlight);
        return new RippleDrawable(ColorStateList.valueOf(ripple), bg, mask);
    }

    /** Pixel の設定のような、つながったカードのまとまり(最初と最後だけ角が大きい)。 */
    @NonNull
    private LinearLayout group(@NonNull List<View> rows) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < rows.size(); i++) {
            View r = rows.get(i);
            float top = i == 0 ? dp(24) : dp(4);
            float bottom = i == rows.size() - 1 ? dp(24) : dp(4);
            r.setBackground(rippleCard(top, bottom));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = dp(2);
            g.addView(r, lp);
        }
        return g;
    }

    /** 1 行(アイコン・見出し・説明・右の値)。 */
    @NonNull
    private LinearLayout row(@DrawableRes int icon, @NonNull String title, @Nullable String sub, @Nullable String value) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(64));
        r.setPadding(dp(20), dp(14), dp(20), dp(14));
        if (icon != 0) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(icon);
            iv.setImageTintList(ColorStateList.valueOf(onSurfaceVariant()));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(24), dp(24));
            ilp.rightMargin = dp(18);
            r.addView(iv, ilp);
        }
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        t.setTextColor(onSurface());
        texts.addView(t);
        if (sub != null) {
            TextView s = new TextView(this);
            s.setText(sub);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            s.setTextColor(onSurfaceVariant());
            s.setPadding(0, dp(2), 0, 0);
            texts.addView(s);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (value != null) {
            TextView v = new TextView(this);
            v.setText(value);
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            v.setTextColor(MigrationViews.attr(this, androidx.appcompat.R.attr.colorPrimary));
            v.setTag("value");
            v.setPadding(dp(12), 0, 0, 0);
            r.addView(v);
        }
        return r;
    }

    @NonNull
    private TextView sectionHeader(@NonNull String text) {
        TextView h = new TextView(this);
        h.setText(text);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        h.setTextColor(MigrationViews.attr(this, androidx.appcompat.R.attr.colorPrimary));
        h.setPadding(dp(16), dp(28), dp(16), dp(10));
        return h;
    }

    @NonNull
    private MaterialButton filledButton(@NonNull String text, @NonNull Runnable onClick) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setMinHeight(dp(48));
        b.setPadding(dp(24), 0, dp(24), 0);
        b.setCornerRadius(dp(24));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    @NonNull
    private MaterialButton tonalButton(@NonNull String text, @NonNull Runnable onClick) {
        MaterialButton b = filledButton(text, onClick);
        b.setBackgroundTintList(ColorStateList.valueOf(
                MigrationViews.attr(this, com.google.android.material.R.attr.colorSecondaryContainer)));
        b.setTextColor(MigrationViews.attr(this, com.google.android.material.R.attr.colorOnSecondaryContainer));
        return b;
    }

    @NonNull
    private MaterialButton textButton(@NonNull String text, @NonNull Runnable onClick) {
        MaterialButton b = new MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    // ------------------------------------------------------------------
    // トップ
    // ------------------------------------------------------------------

    @NonNull
    private View homePage() {
        Scaffold s = new Scaffold(0, 0, 0, Lang.t("データの引っ越し"),
                Lang.t("別の端末の AirSky Browser と、タブや履歴、ブックマーク、設定などをまるごとやりとりします。"));
        MigrationViews.HeroView hero = new MigrationViews.HeroView(this);
        s.content.addView(hero, 0, matchWrap(0, dp(4)));

        View sendCard = actionCard(R.drawable.ic_mig_send, R.color.ps_tone_blue_bg, R.color.ps_tone_blue_fg,
                Lang.t("このデバイスから送る"), Lang.t("今の端末のデータを新しい端末へ"), () -> go(this::sendMethodsPage));
        s.content.addView(sendCard, matchWrap(0, dp(28)));
        View recvCard = actionCard(R.drawable.ic_mig_receive, R.color.ps_tone_green_bg, R.color.ps_tone_green_fg,
                Lang.t("このデバイスで受け取る"), Lang.t("前の端末のデータをこの端末へ"), () -> go(receiveMethods));
        s.content.addView(recvCard, matchWrap(0, dp(12)));

        s.content.addView(sectionHeader(Lang.t("引っ越せるもの")));
        List<View> rows = new ArrayList<>();
        LinearLayout tabs = row(R.drawable.ic_ps_tab, Lang.t("タブ一覧"), null, "—");
        LinearLayout inactive = row(R.drawable.ic_mig_inactive, Lang.t("非アクティブなタブ"), null, "—");
        LinearLayout groups = row(R.drawable.ic_mig_groups, Lang.t("タブグループ"), null, "—");
        LinearLayout hist = row(R.drawable.ic_ps_history, Lang.t("履歴"), null, "—");
        LinearLayout bms = row(R.drawable.ic_mig_bookmark, Lang.t("ブックマーク"), Lang.t("フォルダ・リーディングリストも含む"), "—");
        LinearLayout pages = row(R.drawable.ic_mig_page, Lang.t("保存したページ"), null, "—");
        LinearLayout settings = row(R.drawable.ic_ps_tune, Lang.t("設定"), Lang.t("サイトの設定・デザイン・コントローラーなど"), "—");
        LinearLayout nav = row(R.drawable.ic_mig_route, Lang.t("タブごとのページ遷移"), Lang.t("各タブの「戻る」「進む」の履歴"), "—");
        LinearLayout notes = row(R.drawable.ic_mig_note, Lang.t("メモ・その他"), Lang.t("メモ帳・サイトのアイコン・タブの画像など"), null);
        rows.add(tabs); rows.add(inactive); rows.add(groups); rows.add(hist); rows.add(bms);
        rows.add(pages); rows.add(settings); rows.add(nav); rows.add(notes);
        s.content.addView(group(rows));

        s.content.addView(sectionHeader(Lang.t("ご自身で引っ越すもの")));
        s.content.addView(manualMoveGroup());

        // 件数は裏で数えて、数え上げるように出す
        int token = op;
        io.execute(() -> {
            DeviceMigration.Summary c = DeviceMigration.count(getApplicationContext());
            main.post(() -> {
                if (token != op || isFinishing()) return;
                countUp(tabs, c.tabs);
                countUp(inactive, c.inactiveTabs);
                countUp(groups, c.tabGroups);
                countUp(hist, c.history);
                countUp(bms, c.bookmarks);
                countUp(pages, c.savedPages);
                countUp(settings, c.settings);
                countUp(nav, c.tabStates);
            });
        });
        return s.done();
    }

    /** 壁紙・Python・Java など、引っ越しに入らないものの案内。押すとその画面を開く。 */
    @NonNull
    private View manualMoveGroup() {
        List<View> rows = new ArrayList<>();
        View wall = row(R.drawable.ic_mig_wallpaper, Lang.t("壁紙"),
                Lang.t("新しい端末のスタートページで「壁紙を設定」から設定し直してください"), null);
        rows.add(wall);
        View py = row(R.drawable.ic_tool_python, Lang.t("Python の環境とアプリ"),
                Lang.t("ツール › Python で作業フォルダを書き出し(.zip)、新しい端末で追加し直してください"), null);
        pressable(py, () -> openTool(PyToolActivity.class));
        rows.add(py);
        View java = row(R.drawable.ic_tool_java, Lang.t("Java の環境とアプリ"),
                Lang.t("追加した .jar ファイルを新しい端末に送り、ツール › Java で追加し直してください"), null);
        pressable(java, () -> openTool(JarToolActivity.class));
        rows.add(java);
        rows.add(row(R.drawable.ic_ps_key, Lang.t("パスワードとアプリのロック"),
                Lang.t("この端末の鍵で保護されているため移せません。新しい端末で設定し直してください"), null));
        rows.add(row(R.drawable.ic_ps_privacy, Lang.t("Cookie とログイン状態"),
                Lang.t("新しい端末で、サイトにもう一度ログインしてください"), null));
        return group(rows);
    }

    private void openTool(@NonNull Class<?> activity) {
        try {
            startActivity(new Intent(this, activity));
        } catch (Exception e) {
            Toast.makeText(this, Lang.t("開けませんでした"), Toast.LENGTH_SHORT).show();
        }
    }

    private void countUp(@NonNull View row, int value) {
        TextView v = row.findViewWithTag("value");
        if (v == null) return;
        ValueAnimator a = ValueAnimator.ofInt(0, value);
        a.setDuration(Math.min(900, 300 + value * 4L));
        a.setInterpolator(MigrationViews.EMPHASIZED);
        a.addUpdateListener(x -> v.setText(String.format(Locale.getDefault(), "%,d", (int) x.getAnimatedValue())));
        a.start();
    }

    // ------------------------------------------------------------------
    // 送る
    // ------------------------------------------------------------------

    @NonNull
    private View sendMethodsPage() {
        Scaffold s = new Scaffold(R.drawable.ic_mig_send, color(R.color.ps_tone_blue_bg), color(R.color.ps_tone_blue_fg),
                Lang.t("送る方法を選ぶ"),
                Lang.t("受け取る端末でも 設定 › データの引っ越し を開き、「このデバイスで受け取る」を選んでください。"));
        s.content.addView(actionCard(R.drawable.ic_mig_qr, R.color.ps_tone_blue_bg, R.color.ps_tone_blue_fg,
                Lang.t("QR コード"), Lang.t("同じ Wi-Fi の端末に、いちばん速く送ります"),
                () -> go(this::startQrSend)), matchWrap(0, dp(28)));
        s.content.addView(actionCard(R.drawable.ic_mig_bluetooth, R.color.ps_tone_purple_bg, R.color.ps_tone_purple_fg,
                "Bluetooth", Lang.t("ペア設定した端末に直接送ります"),
                () -> withBluetooth(() -> go(this::btDevicesPage))), matchWrap(0, dp(12)));
        s.content.addView(actionCard(R.drawable.ic_mig_share, R.color.ps_tone_teal_bg, R.color.ps_tone_teal_fg,
                "Quick Share", Lang.t("引っ越しファイルにまとめて、Quick Share などで送ります"),
                () -> go(this::startShareSend)), matchWrap(0, dp(12)));
        return s.done();
    }

    /** 引っ越しファイルを作ってから then を呼ぶ(作ってあればすぐ)。作っている間は進み具合を出す。 */
    private void prepareBundle(@NonNull Runnable then) {
        if (bundle != null && bundle.isFile() && bundleSummary != null) {
            then.run();
            return;
        }
        int token = op;
        io.execute(() -> {
            File dir = new File(getCacheDir(), "shared");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File[] old = dir.listFiles((f, n) -> n.endsWith(DeviceMigration.EXTENSION));
            if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
                f.delete();
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date());
            File out = new File(dir, "AirSky-" + stamp + DeviceMigration.EXTENSION);
            try {
                DeviceMigration.Summary sum = DeviceMigration.export(getApplicationContext(), out,
                        (done, total) -> postProgress(token, done, total));
                main.post(() -> {
                    if (token != op) return;
                    bundle = out;
                    bundleSummary = sum;
                    then.run();
                });
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
                main.post(() -> {
                    if (token != op) return;
                    replace(() -> errorPage(Lang.t("データをまとめられませんでした"),
                            Lang.t("空き容量が足りないか、ファイルを読み込めませんでした。") + "\n(" + e.getClass().getSimpleName() + ")",
                            null));
                });
            }
        });
    }

    // ---- QR コード(Wi-Fi) ----

    @NonNull
    private View startQrSend() {
        View page = transferPage(Lang.t("データをまとめています"),
                Lang.t("タブ・履歴・ブックマーク・設定などを 1 つにまとめています。"));
        int token = op;
        main.post(() -> prepareBundle(() -> {
            if (token != op) return;
            java.util.List<String> hosts = MigrationChannel.localAddresses();
            if (hosts.isEmpty()) {
                replace(() -> errorPage(Lang.t("Wi-Fi に接続されていません"),
                        Lang.t("両方の端末を同じ Wi-Fi に接続してから、もう一度お試しください。Wi-Fi が無い場所では Bluetooth か Quick Share をお使いください。"),
                        () -> replace(this::startQrSend)));
                return;
            }
            serveQr(token, hosts);
        }));
        return page;
    }

    private void serveQr(int token, @NonNull List<String> hosts) {
        File file = bundle;
        DeviceMigration.Summary sum = bundleSummary;
        if (file == null || sum == null) return;
        io.execute(() -> {
            ServerSocket server;
            try {
                server = new ServerSocket(0);
            } catch (IOException e) {
                main.post(() -> {
                    if (token == op) replace(() -> errorPage(Lang.t("待ち受けを始められませんでした"), String.valueOf(e.getMessage()), null));
                });
                return;
            }
            active = server;
            MigrationChannel.Ticket t = new MigrationChannel.Ticket();
            t.hosts.addAll(hosts);
            t.port = server.getLocalPort();
            t.key = MigrationChannel.newKey();
            t.device = DeviceMigration.deviceName();
            try {
                QrRenderer.Style st = new QrRenderer.Style();
                st.dot = QrRenderer.DOT_ROUNDED;
                st.eye = QrRenderer.EYE_ROUNDED;
                st.fg = 0xFF1B1B1F;
                st.bg = 0xFFFFFFFF;
                st.margin = 1;
                qrBitmap = QrRenderer.render(t.toQr(), st, 720);
            } catch (Exception e) {
                qrBitmap = null;
            }
            qrNotice = null;
            main.post(() -> {
                if (token == op) replace(this::qrPage);
            });
            // 鍵を知らない相手がつないできても、データは読めない。失敗したら次の接続を待つ
            int failures = 0;
            while (token == op && !server.isClosed()) {
                Socket s;
                try {
                    s = server.accept();
                } catch (IOException e) {
                    break;
                }
                active = s;
                main.post(() -> {
                    if (token != op) return;
                    transferring = true;
                    replace(() -> transferPage(Lang.t("送信しています"),
                            Lang.t("画面を閉じずに、そのままお待ちください。")));
                });
                try {
                    s.setSoTimeout(120_000);
                    s.setTcpNoDelay(true);
                    MigrationChannel.send(s.getInputStream(), s.getOutputStream(), t.key, file, sum,
                            (done, total) -> postProgress(token, done, total));
                    closeQuietly(s);
                    closeQuietly(server);
                    main.post(() -> {
                        if (token == op) sent();
                    });
                    return;
                } catch (MigrationChannel.RemoteRejected r) {
                    closeQuietly(s);
                    closeQuietly(server);
                    main.post(() -> {
                        if (token == op) failTransfer(r);
                    });
                    return;
                } catch (IOException e) {
                    closeQuietly(s);
                    active = server;
                    if (token != op) return;
                    failures++;
                    qrNotice = Lang.t("接続が切れました。受け取る端末で、もう一度 QR コードを読み取ってください。");
                    if (failures >= 5) {
                        closeQuietly(server);
                        main.post(() -> {
                            if (token == op) failTransfer(e);
                        });
                        return;
                    }
                    main.post(() -> {
                        if (token != op) return;
                        transferring = false;
                        replace(this::qrPage);
                    });
                }
            }
        });
    }

    @NonNull
    private View qrPage() {
        Scaffold s = new Scaffold(0, 0, 0, Lang.t("QR コードを読み取ってください"),
                Lang.t("受け取る端末で「このデバイスで受け取る」›「QR コードを読み取る」を選び、この QR コードを読み取ってください。両方の端末を同じ Wi-Fi に接続しておいてください。"));
        MigrationViews.PulseLayout pulse = new MigrationViews.PulseLayout(this);
        pulse.setMinRadius(dp(120));
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(28));
        card.setBackground(bg);
        card.setElevation(dp(2));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        ImageView qr = new ImageView(this);
        if (qrBitmap != null) qr.setImageBitmap(qrBitmap);
        card.addView(qr, new FrameLayout.LayoutParams(dp(220), dp(220)));
        pulse.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        s.content.addView(pulse, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));
        // QR コードは少し回りながら大きくなって出る
        card.setScaleX(0.6f);
        card.setScaleY(0.6f);
        card.setRotation(-8f);
        card.animate().scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(200).setDuration(600)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();

        TextView info = new TextView(this);
        info.setGravity(Gravity.CENTER);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        info.setTextColor(onSurfaceVariant());
        String size = bundle != null ? Formatter.formatShortFileSize(this, bundle.length()) : "";
        info.setText(DeviceMigration.deviceName() + " · " + size);
        s.content.addView(info, matchWrap(0, 0));
        if (qrNotice != null) {
            TextView n = new TextView(this);
            n.setText(qrNotice);
            n.setGravity(Gravity.CENTER);
            n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            n.setTextColor(MigrationViews.attr(this, androidx.appcompat.R.attr.colorError));
            s.content.addView(n, matchWrap(0, dp(12)));
        }
        s.leftButton(textButton(Lang.t("キャンセル"), this::goBack));
        return s.done();
    }

    // ---- Bluetooth ----

    /** Bluetooth の許可とオンを確かめてから then を呼ぶ。 */
    private void withBluetooth(@NonNull Runnable then) {
        BluetoothAdapter adapter = bluetoothAdapter();
        if (adapter == null) {
            Toast.makeText(this, Lang.t("この端末は Bluetooth に対応していません"), Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            afterBluetooth = then;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT_PERMISSION);
            return;
        }
        if (!adapter.isEnabled()) {
            afterBluetooth = then;
            try {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_BT_ENABLE);
            } catch (Exception e) {
                Toast.makeText(this, Lang.t("Bluetooth をオンにしてください"), Toast.LENGTH_LONG).show();
            }
            return;
        }
        then.run();
    }

    @Nullable
    private BluetoothAdapter bluetoothAdapter() {
        BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        return bm != null ? bm.getAdapter() : null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_BT_PERMISSION) return;
        Runnable then = afterBluetooth;
        afterBluetooth = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (then != null) withBluetooth(then);
        } else {
            Toast.makeText(this, Lang.t("Bluetooth を使うには「付近のデバイス」の許可が必要です"), Toast.LENGTH_LONG).show();
        }
    }

    @NonNull
    private View btDevicesPage() {
        Scaffold s = new Scaffold(R.drawable.ic_mig_bluetooth, color(R.color.ps_tone_purple_bg), color(R.color.ps_tone_purple_fg),
                Lang.t("送り先を選ぶ"),
                Lang.t("受け取る端末で「このデバイスで受け取る」›「Bluetooth で受け取る」を開いてから、その端末を選んでください。"));
        BluetoothAdapter adapter = bluetoothAdapter();
        List<View> rows = new ArrayList<>();
        Set<BluetoothDevice> bonded = null;
        try {
            if (adapter != null) bonded = adapter.getBondedDevices();
        } catch (SecurityException ignored) {
        }
        if (bonded != null) {
            List<BluetoothDevice> phones = new ArrayList<>();
            List<BluetoothDevice> others = new ArrayList<>();
            for (BluetoothDevice dev : bonded) {
                int major = dev.getBluetoothClass() != null ? dev.getBluetoothClass().getMajorDeviceClass() : 0;
                if (major == android.bluetooth.BluetoothClass.Device.Major.PHONE
                        || major == android.bluetooth.BluetoothClass.Device.Major.COMPUTER) phones.add(dev);
                else others.add(dev);
            }
            phones.addAll(others);
            for (BluetoothDevice dev : phones) {
                String name = dev.getName() != null ? dev.getName() : dev.getAddress();
                int major = dev.getBluetoothClass() != null ? dev.getBluetoothClass().getMajorDeviceClass() : 0;
                int icon = major == android.bluetooth.BluetoothClass.Device.Major.PHONE ? R.drawable.ic_ps_phone
                        : major == android.bluetooth.BluetoothClass.Device.Major.COMPUTER ? R.drawable.ic_ps_tablet
                        : R.drawable.ic_mig_bluetooth;
                View r = row(icon, name, null, null);
                pressable(r, () -> go(() -> startBtSend(dev, name)));
                rows.add(r);
            }
        }
        if (rows.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(Lang.t("ペア設定済みの端末がありません。下の「新しい端末をペア設定」から、受け取る端末とペア設定してください。"));
            none.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            none.setTextColor(onSurfaceVariant());
            s.content.addView(none, matchWrap(0, dp(24)));
        } else {
            s.content.addView(sectionHeader(Lang.t("ペア設定済みの端末")));
            s.content.addView(group(rows));
        }
        List<View> pair = new ArrayList<>();
        View add = row(R.drawable.ic_ps_add_tab, Lang.t("新しい端末をペア設定"), Lang.t("Bluetooth の設定を開きます"), null);
        pressable(add, this::openBluetoothSettings);
        pair.add(add);
        s.content.addView(group(pair), matchWrap(0, dp(16)));
        View v = s.done();
        btListShown = true;
        return v;
    }

    private void openBluetoothSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, Lang.t("開けませんでした"), Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    private View startBtSend(@NonNull BluetoothDevice device, @NonNull String name) {
        View page = transferPage(Lang.t("データをまとめています"),
                Lang.t("タブ・履歴・ブックマーク・設定などを 1 つにまとめています。"));
        int token = op;
        main.post(() -> prepareBundle(() -> {
            if (token != op) return;
            File file = bundle;
            DeviceMigration.Summary sum = bundleSummary;
            if (file == null || sum == null) return;
            transferring = true;
            lastPercent = -1;
            setTransferText(Lang.f("%s に接続しています", name), Lang.t("受け取る端末で「Bluetooth で受け取る」を開いておいてください。"));
            ring.spin();
            io.execute(() -> {
                BluetoothSocket socket = null;
                try {
                    socket = device.createRfcommSocketToServiceRecord(MigrationChannel.BT_UUID);
                    active = socket;
                    socket.connect();
                    main.post(() -> {
                        if (token == op) setTransferText(Lang.t("送信しています"), Lang.f("%s へ送っています。画面を閉じずにお待ちください。", name));
                    });
                    MigrationChannel.send(socket.getInputStream(), socket.getOutputStream(), null, file, sum,
                            (done, total) -> postProgress(token, done, total));
                    closeQuietly(socket);
                    main.post(() -> {
                        if (token == op) sent();
                    });
                } catch (IOException e) {
                    closeQuietly(socket);
                    main.post(() -> {
                        if (token != op) return;
                        if (e instanceof MigrationChannel.RemoteRejected) {
                            failTransfer(e);
                        } else {
                            transferring = false;
                            replace(() -> errorPage(Lang.t("接続できませんでした"),
                                    Lang.t("受け取る端末で「Bluetooth で受け取る」を開いているか、端末が近くにあるかを確認して、もう一度お試しください。"),
                                    () -> replace(() -> startBtSend(device, name))));
                        }
                    });
                }
            });
        }));
        return page;
    }

    // ---- Quick Share(ファイル) ----

    @NonNull
    private View startShareSend() {
        View page = transferPage(Lang.t("データをまとめています"),
                Lang.t("タブ・履歴・ブックマーク・設定などを 1 つにまとめています。"));
        int token = op;
        main.post(() -> prepareBundle(() -> {
            if (token != op) return;
            replace(this::sharePage);
            main.postDelayed(() -> {
                if (token == op) shareBundle(false);
            }, 600);
        }));
        return page;
    }

    @NonNull
    private View sharePage() {
        String fileName = bundle != null ? bundle.getName() : "";
        String size = bundle != null ? Formatter.formatShortFileSize(this, bundle.length()) : "";
        Scaffold s = new Scaffold(R.drawable.ic_mig_share, color(R.color.ps_tone_teal_bg), color(R.color.ps_tone_teal_fg),
                Lang.t("Quick Share で送る"),
                Lang.t("共有の画面で Quick Share を選び、受け取る端末を選んでください。"));
        List<View> steps = new ArrayList<>();
        steps.add(row(R.drawable.ic_mig_send, Lang.t("1. この端末から共有"), fileName + " · " + size, null));
        steps.add(row(R.drawable.ic_mig_receive, Lang.t("2. 受け取る端末で受け入れる"),
                Lang.t("ファイルは「ダウンロード」フォルダに保存されます"), null));
        steps.add(row(R.drawable.ic_mig_folder, Lang.t("3. 受け取る端末の AirSky で開く"),
                Lang.t("設定 › データの引っ越し ›「このデバイスで受け取る」›「ファイルから受け取る」で、このファイルを選びます"), null));
        s.content.addView(group(steps), matchWrap(0, dp(24)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        MaterialButton qs = filledButton(Lang.t("Quick Share を開く"), () -> shareBundle(false));
        qs.setIconResource(R.drawable.ic_mig_share);
        buttons.addView(qs, matchWrap(0, 0));
        buttons.addView(tonalButton(Lang.t("ほかのアプリで共有(Bluetooth など)"), () -> shareBundle(true)), matchWrap(0, dp(8)));
        s.content.addView(buttons, matchWrap(0, dp(24)));
        s.rightButton(filledButton(Lang.t("完了"), this::finishScreen));
        return s.done();
    }

    /** 引っ越しファイルを共有する。chooser=false なら Quick Share を直接開く(無ければ共有の画面)。 */
    private void shareBundle(boolean chooser) {
        File file = bundle;
        if (file == null || !file.isFile()) return;
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/octet-stream");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_TITLE, file.getName());
        send.setClipData(ClipData.newRawUri(file.getName(), uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (!chooser) {
            // Google の Quick Share(Play 開発者サービス)・Samsung の Quick Share
            for (String pkg : new String[]{"com.google.android.gms", "com.samsung.android.app.sharelive"}) {
                try {
                    startActivity(new Intent(send).setPackage(pkg));
                    return;
                } catch (ActivityNotFoundException | SecurityException ignored) {
                }
            }
        }
        try {
            startActivity(Intent.createChooser(send, Lang.t("引っ越しファイルを送る")));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, Lang.t("共有できるアプリがありません"), Toast.LENGTH_SHORT).show();
        }
    }

    /** 送り終えた。 */
    private void sent() {
        transferring = false;
        ring.done();
        setTransferText(Lang.t("送信しました"),
                Lang.t("受け取る端末で内容を確認して「引っ越す」を押すと、引っ越しが完了します。"));
        ringDetail.setText("");
        vibrate();
        View bottomDone = filledButton(Lang.t("完了"), this::finishScreen);
        addBottom(bottomDone);
    }

    private void finishScreen() {
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }

    // ------------------------------------------------------------------
    // 受け取る
    // ------------------------------------------------------------------

    @NonNull
    private View receiveMethodsPage() {
        Scaffold s = new Scaffold(R.drawable.ic_mig_receive, color(R.color.ps_tone_green_bg), color(R.color.ps_tone_green_fg),
                Lang.t("受け取る方法を選ぶ"),
                Lang.t("送る端末で選んだ方法と同じものを選んでください。受け取ったデータは、確認してから反映します。"));
        s.content.addView(actionCard(R.drawable.ic_mig_scan, R.color.ps_tone_blue_bg, R.color.ps_tone_blue_fg,
                Lang.t("QR コードを読み取る"), Lang.t("送る端末に表示された QR コードをカメラで読み取ります"),
                this::scanQr), matchWrap(0, dp(28)));
        s.content.addView(actionCard(R.drawable.ic_mig_bluetooth, R.color.ps_tone_purple_bg, R.color.ps_tone_purple_fg,
                Lang.t("Bluetooth で受け取る"), Lang.t("送る端末から、この端末を選んでもらいます"),
                () -> withBluetooth(() -> go(this::btWaitPage))), matchWrap(0, dp(12)));
        s.content.addView(actionCard(R.drawable.ic_mig_folder, R.color.ps_tone_teal_bg, R.color.ps_tone_teal_fg,
                Lang.t("ファイルから受け取る"), Lang.t("Quick Share などで受け取った .airsky ファイルを選びます"),
                this::pickFile), matchWrap(0, dp(12)));
        return s.done();
    }

    // ---- QR コード ----

    private void scanQr() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setCaptureActivity(QrCaptureActivity.class);
        integrator.setPrompt(Lang.t("送る端末に表示された QR コードを読み取ってください"));
        integrator.setBeepEnabled(false);
        integrator.setOrientationLocked(false);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.initiateScan();
    }

    private void receiveWithTicket(@NonNull MigrationChannel.Ticket t) {
        go(() -> transferPage(Lang.t("接続しています"), Lang.f("%s に接続しています…", t.device)));
        int token = op;
        transferring = true;
        io.execute(() -> {
            Socket socket = null;
            for (String host : t.hosts) {
                Socket s = new Socket();
                try {
                    active = s;
                    s.connect(new InetSocketAddress(host, t.port), 6000);
                    socket = s;
                    break;
                } catch (IOException e) {
                    closeQuietly(s);
                    if (token != op) return;
                }
            }
            if (socket == null) {
                main.post(() -> {
                    if (token != op) return;
                    transferring = false;
                    replace(() -> errorPage(Lang.t("接続できませんでした"),
                            Lang.t("両方の端末が同じ Wi-Fi に接続されているかを確認して、もう一度 QR コードを読み取ってください。"),
                            this::scanQr));
                });
                return;
            }
            try {
                socket.setSoTimeout(120_000);
                socket.setTcpNoDelay(true);
                receiveFrom(token, socket.getInputStream(), socket.getOutputStream(), t.key, t.device);
            } catch (IOException e) {
                main.post(() -> {
                    if (token == op) failTransfer(e);
                });
            } finally {
                closeQuietly(socket);
            }
        });
    }

    // ---- Bluetooth ----

    @NonNull
    private View btWaitPage() {
        BluetoothAdapter adapter = bluetoothAdapter();
        String myName = DeviceMigration.deviceName();
        try {
            if (adapter != null && adapter.getName() != null) myName = adapter.getName();
        } catch (SecurityException ignored) {
        }
        Scaffold s = new Scaffold(0, 0, 0, Lang.t("Bluetooth で待っています"),
                Lang.t("送る端末で「このデバイスから送る」›「Bluetooth」を選び、この端末を選んでください。"));
        MigrationViews.PulseLayout pulse = new MigrationViews.PulseLayout(this);
        pulse.setMinRadius(dp(48));
        View icon = toneIcon(R.drawable.ic_mig_bluetooth, color(R.color.ps_tone_purple_bg), color(R.color.ps_tone_purple_fg), 96, 44);
        pulse.addView(icon, new FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER));
        s.content.addView(pulse, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280)));

        TextView name = new TextView(this);
        name.setGravity(Gravity.CENTER);
        name.setText(myName);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        name.setTextColor(onSurface());
        s.content.addView(name, matchWrap(0, 0));
        TextView label = new TextView(this);
        label.setGravity(Gravity.CENTER);
        label.setText(Lang.t("この端末の名前"));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTextColor(onSurfaceVariant());
        s.content.addView(label, matchWrap(0, dp(2)));

        TextView hint = new TextView(this);
        hint.setText(Lang.t("送る端末の一覧に出てこない時は、先に Bluetooth の設定で 2 台をペア設定してください。"));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hint.setTextColor(onSurfaceVariant());
        hint.setGravity(Gravity.CENTER);
        s.content.addView(hint, matchWrap(0, dp(24)));
        s.leftButton(textButton(Lang.t("キャンセル"), this::goBack));
        s.rightButton(tonalButton(Lang.t("Bluetooth の設定"), this::openBluetoothSettings));

        // 待ち受けを始める
        int token = op;
        io.execute(() -> {
            BluetoothServerSocket server = null;
            BluetoothSocket socket = null;
            try {
                BluetoothAdapter a = bluetoothAdapter();
                if (a == null) throw new IOException("no adapter");
                server = a.listenUsingRfcommWithServiceRecord(MigrationChannel.BT_NAME, MigrationChannel.BT_UUID);
                active = server;
                socket = server.accept();
                closeQuietly(server);
                active = socket;
                String from = socket.getRemoteDevice() != null && socket.getRemoteDevice().getName() != null
                        ? socket.getRemoteDevice().getName() : "Bluetooth";
                main.post(() -> {
                    if (token != op) return;
                    transferring = true;
                    replace(() -> transferPage(Lang.t("受信しています"), Lang.f("%s から受け取っています。画面を閉じずにお待ちください。", from)));
                });
                receiveFrom(token, socket.getInputStream(), socket.getOutputStream(), null, from);
            } catch (IOException | SecurityException e) {
                main.post(() -> {
                    if (token != op) return;
                    if (transferring) failTransfer(e instanceof IOException ? (IOException) e : new IOException(e));
                    else replace(() -> errorPage(Lang.t("Bluetooth で待ち受けられませんでした"),
                            Lang.t("Bluetooth がオンになっているかを確認して、もう一度お試しください。"),
                            () -> replace(this::btWaitPage)));
                });
            } finally {
                closeQuietly(server);
                closeQuietly(socket);
            }
        });
        return s.done();
    }

    // ---- ファイル ----

    private void pickFile() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        try {
            startActivityForResult(pick, REQ_PICK_FILE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, Lang.t("ファイルを選べませんでした"), Toast.LENGTH_SHORT).show();
        }
    }

    private void receiveFromFile(@NonNull Uri uri) {
        go(() -> transferPage(Lang.t("ファイルを読み込んでいます"), Lang.t("引っ越しファイルの中身を確認しています。")));
        int token = op;
        io.execute(() -> {
            File out = new File(getCacheDir(), "migration_in" + DeviceMigration.EXTENSION);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(out)) {
                if (in == null) throw new IOException("open failed");
                long total = 0;
                try (android.database.Cursor c = getContentResolver().query(uri,
                        new String[]{android.provider.OpenableColumns.SIZE}, null, null, null)) {
                    if (c != null && c.moveToFirst() && !c.isNull(0)) total = c.getLong(0);
                } catch (Exception ignored) {
                }
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (token != op) return;
                    os.write(buf, 0, n);
                    done += n;
                    if (done > DeviceMigration.MAX_SIZE) throw new IOException("too large");
                    if (total > 0) postProgress(token, done, total);
                }
            } catch (IOException e) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
                main.post(() -> {
                    if (token == op) replace(() -> errorPage(Lang.t("ファイルを読み込めませんでした"),
                            String.valueOf(e.getMessage()), this::pickFile));
                });
                return;
            }
            inspectReceived(token, out);
        });
    }

    // ---- 共通 ----

    /** 受け取って確かめ、確認のページを出す(裏のスレッドで呼ぶ)。 */
    private void receiveFrom(int token, @NonNull InputStream in, @NonNull OutputStream out, @Nullable byte[] key,
                             @NonNull String from) throws IOException {
        File file = new File(getCacheDir(), "migration_in" + DeviceMigration.EXTENSION);
        main.post(() -> {
            if (token == op) setTransferText(Lang.t("受信しています"), Lang.f("%s から受け取っています。画面を閉じずにお待ちください。", from));
        });
        try {
            MigrationChannel.receive(in, out, key, file, null, (done, total) -> postProgress(token, done, total));
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            throw e;
        }
        inspectReceived(token, file);
    }

    private void inspectReceived(int token, @NonNull File file) {
        try {
            DeviceMigration.Summary s = DeviceMigration.inspect(file);
            main.post(() -> {
                if (token != op) return;
                transferring = false;
                received = file;
                receivedSummary = s;
                ring.done();
                setTransferText(Lang.t("受け取りました"), s.device);
                vibrate();
                main.postDelayed(() -> {
                    if (token == op) replace(this::confirmPage);
                }, 900);
            });
        } catch (DeviceMigration.MigrationException e) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            main.post(() -> {
                if (token != op) return;
                transferring = false;
                replace(() -> errorPage(Lang.t("引っ越しできないファイルです"), String.valueOf(e.getMessage()), null));
            });
        }
    }

    @NonNull
    private View confirmPage() {
        DeviceMigration.Summary sum = receivedSummary != null ? receivedSummary : new DeviceMigration.Summary();
        String when = sum.created > 0
                ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(sum.created)) : "";
        Scaffold s = new Scaffold(R.drawable.ic_ps_swap, color(R.color.ps_tone_blue_bg), color(R.color.ps_tone_blue_fg),
                Lang.t("このデータで引っ越しますか?"),
                (sum.device.isEmpty() ? "" : Lang.f("%s のデータです。", sum.device)) + (when.isEmpty() ? "" : "(" + when + ")"));
        s.content.addView(summaryGroup(sum), matchWrap(0, dp(24)));

        // 注意(置き換わる)
        LinearLayout warn = new LinearLayout(this);
        warn.setPadding(dp(20), dp(18), dp(20), dp(18));
        GradientDrawable wbg = new GradientDrawable();
        wbg.setCornerRadius(dp(24));
        wbg.setColor(MigrationViews.attr(this, com.google.android.material.R.attr.colorErrorContainer));
        warn.setBackground(wbg);
        ImageView wi = new ImageView(this);
        wi.setImageResource(R.drawable.ic_mig_warning);
        int onErr = MigrationViews.attr(this, com.google.android.material.R.attr.colorOnErrorContainer);
        wi.setImageTintList(ColorStateList.valueOf(onErr));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(dp(24), dp(24));
        wlp.rightMargin = dp(16);
        warn.addView(wi, wlp);
        TextView wt = new TextView(this);
        wt.setText(Lang.t("この端末の AirSky のタブ・履歴・ブックマーク・設定などは、受け取ったデータに置き換わります。壁紙・Python・Java・パスワード・アプリのロックはそのまま残ります。"));
        wt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        wt.setTextColor(onErr);
        wt.setLineSpacing(0, 1.15f);
        warn.addView(wt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        s.content.addView(warn, matchWrap(0, dp(16)));

        s.leftButton(textButton(Lang.t("キャンセル"), () -> {
            if (received != null) //noinspection ResultOfMethodCallIgnored
                received.delete();
            received = null;
            goBack();
        }));
        s.rightButton(filledButton(Lang.t("引っ越す"), this::applyReceived));
        return s.done();
    }

    @NonNull
    private View summaryGroup(@NonNull DeviceMigration.Summary sum) {
        List<View> rows = new ArrayList<>();
        rows.add(row(R.drawable.ic_ps_tab, Lang.t("タブ一覧"), null, num(sum.tabs)));
        rows.add(row(R.drawable.ic_mig_inactive, Lang.t("非アクティブなタブ"), null, num(sum.inactiveTabs)));
        rows.add(row(R.drawable.ic_mig_groups, Lang.t("タブグループ"), null, num(sum.tabGroups)));
        rows.add(row(R.drawable.ic_ps_history, Lang.t("履歴"), null, num(sum.history)));
        rows.add(row(R.drawable.ic_mig_bookmark, Lang.t("ブックマーク"), null, num(sum.bookmarks)));
        rows.add(row(R.drawable.ic_mig_page, Lang.t("保存したページ"), null, num(sum.savedPages)));
        rows.add(row(R.drawable.ic_ps_tune, Lang.t("設定"), null, num(sum.settings)));
        rows.add(row(R.drawable.ic_mig_route, Lang.t("タブごとのページ遷移"), null, num(sum.tabStates)));
        if (sum.bytes > 0) {
            rows.add(row(R.drawable.ic_ps_storage, Lang.t("大きさ"), null, Formatter.formatShortFileSize(this, sum.bytes)));
        }
        return group(rows);
    }

    @NonNull
    private static String num(int n) {
        return String.format(Locale.getDefault(), "%,d", n);
    }

    /** 受け取ったデータを置いて、AirSky を起動し直す(起動直後に反映される)。 */
    private void applyReceived() {
        File file = received;
        if (file == null || !file.isFile()) return;
        replace(() -> transferPage(Lang.t("引っ越しています"), Lang.t("AirSky を再起動してデータを反映します…")));
        ring.spin();
        int token = op;
        io.execute(() -> {
            try {
                DeviceMigration.stage(getApplicationContext(), file);
                main.postDelayed(() -> {
                    if (token == op) restartIntoResult();
                }, 700);
            } catch (IOException e) {
                main.post(() -> {
                    if (token == op) replace(() -> errorPage(Lang.t("引っ越しに失敗しました"),
                            Lang.t("空き容量が足りない可能性があります。") + "\n(" + e.getMessage() + ")", null));
                });
            }
        });
    }

    /** プロセスごと起動し直し、反映した結果をこの画面で見せる。 */
    private void restartIntoResult() {
        Intent next = new Intent(this, DeviceMigrationActivity.class)
                .putExtra(EXTRA_FINISHED, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, next,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am != null) am.set(AlarmManager.RTC, System.currentTimeMillis() + 600, pi);
        finishAffinity();
        main.postDelayed(() -> System.exit(0), 300);
    }

    // ------------------------------------------------------------------
    // 終わった後
    // ------------------------------------------------------------------

    @NonNull
    private View finishedPage() {
        JSONObject r = finishedResult;
        boolean ok = r != null && r.optBoolean("ok");
        if (!ok) {
            String reason = r != null ? r.optString("error", "") : "";
            return errorPage(Lang.t("引っ越しに失敗しました"),
                    (reason.isEmpty() ? Lang.t("データを反映できませんでした。") : reason)
                            + "\n\n" + Lang.t("もう一度、送る端末から送り直してください。"), null);
        }
        DeviceMigration.Summary sum = DeviceMigration.Summary.fromJson(r.optJSONObject("summary"));
        Scaffold s = new Scaffold(0, 0, 0, Lang.t("引っ越しが完了しました"),
                (sum.device.isEmpty() ? "" : Lang.f("%s のデータを引き継ぎました。", sum.device)));
        FrameLayout ringBox = new FrameLayout(this);
        MigrationViews.RingView done = new MigrationViews.RingView(this);
        ringBox.addView(done, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        s.content.addView(ringBox, 0, matchWrap(0, dp(8)));
        main.postDelayed(() -> {
            done.done();
            vibrate();
        }, 350);
        s.content.addView(summaryGroup(sum), matchWrap(0, dp(24)));
        s.content.addView(sectionHeader(Lang.t("ご自身で引っ越すもの")));
        s.content.addView(manualMoveGroup());
        s.rightButton(filledButton(Lang.t("AirSky を開く"), this::openBrowser));
        return s.done();
    }

    private void openBrowser() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch == null) launch = new Intent(this, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(launch);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ------------------------------------------------------------------
    // 受け渡しの進み具合のページ
    // ------------------------------------------------------------------

    @NonNull
    private View transferPage(@NonNull String title, @NonNull String sub) {
        lastPercent = -1;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(24), dp(24), 0);

        View spaceTop = new View(this);
        root.addView(spaceTop, new LinearLayout.LayoutParams(1, 0, 0.6f));
        ring = new MigrationViews.RingView(this);
        root.addView(ring, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        ringTitle = new TextView(this);
        ringTitle.setGravity(Gravity.CENTER);
        ringTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        ringTitle.setTextColor(onSurface());
        root.addView(ringTitle, matchWrap(0, dp(28)));
        ringSub = new TextView(this);
        ringSub.setGravity(Gravity.CENTER);
        ringSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        ringSub.setTextColor(onSurfaceVariant());
        ringSub.setLineSpacing(0, 1.2f);
        root.addView(ringSub, matchWrap(0, dp(12)));
        ringDetail = new TextView(this);
        ringDetail.setGravity(Gravity.CENTER);
        ringDetail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        ringDetail.setTextColor(onSurfaceVariant());
        root.addView(ringDetail, matchWrap(0, dp(8)));
        View spaceBottom = new View(this);
        root.addView(spaceBottom, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setTag("bottom");
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, dp(12), 0, dp(20));
        bottom.addView(textButton(Lang.t("キャンセル"), this::goBack));
        bottom.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        root.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setTransferText(title, sub);

        ring.setScaleX(0.7f);
        ring.setScaleY(0.7f);
        ring.setAlpha(0f);
        ring.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(80).setDuration(500)
                .setInterpolator(MigrationViews.EMPHASIZED).start();
        return root;
    }

    /** 受け渡しのページの文を変える(変わる時は軽くフェードする)。 */
    private void setTransferText(@NonNull String title, @NonNull String sub) {
        if (ringTitle == null) return;
        if (!title.contentEquals(ringTitle.getText())) {
            ringTitle.setAlpha(0f);
            ringTitle.setTranslationY(dp(8));
            ringTitle.setText(title);
            ringTitle.animate().alpha(1f).translationY(0f).setDuration(300).setInterpolator(MigrationViews.EMPHASIZED).start();
        }
        ringSub.setText(sub);
    }

    /** 受け渡しのページの下のボタンを、キャンセルから入れ替える。 */
    private void addBottom(@NonNull View button) {
        if (currentView == null) return;
        View bottom = currentView.findViewWithTag("bottom");
        if (!(bottom instanceof LinearLayout)) return;
        LinearLayout b = (LinearLayout) bottom;
        b.removeAllViews();
        b.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        b.addView(button);
        button.setAlpha(0f);
        button.animate().alpha(1f).setDuration(250).start();
    }

    private void postProgress(int token, long done, long total) {
        if (total <= 0) return;
        int pct = (int) Math.min(100, done * 100 / total);
        if (pct == lastPercent) return;
        lastPercent = pct;
        main.post(() -> {
            if (token != op || ring == null) return;
            ring.progress(done / (float) total);
            ringDetail.setText(Formatter.formatShortFileSize(this, done) + " / " + Formatter.formatShortFileSize(this, total));
        });
    }

    private void failTransfer(@NonNull IOException e) {
        transferring = false;
        String title = Lang.t("引っ越しを完了できませんでした");
        String msg;
        if (e instanceof MigrationChannel.WrongKey) {
            msg = Lang.t("QR コードが古いか、別の端末のものです。送る端末に表示されている QR コードを読み取り直してください。");
        } else if (e instanceof MigrationChannel.NoSpace) {
            msg = Lang.t("受け取る端末の空き容量が足りません。不要なファイルを消してから、もう一度お試しください。");
        } else if (e instanceof MigrationChannel.RemoteRejected) {
            String why = e.getMessage();
            if ("nospace".equals(why)) msg = Lang.t("受け取る端末の空き容量が足りません。");
            else if ("corrupt".equals(why)) msg = Lang.t("データが途中で壊れました。もう一度お試しください。");
            else msg = Lang.t("受け取る端末でキャンセルされました。");
        } else {
            msg = Lang.t("接続が切れました。2 台の端末を近くに置いて、もう一度お試しください。");
        }
        replace(() -> errorPage(title, msg, null));
    }

    @NonNull
    private View errorPage(@NonNull String title, @NonNull String message, @Nullable Runnable retry) {
        transferring = false;
        View page = transferPage(title, message);
        MigrationViews.RingView r = ring;
        main.postDelayed(r::error, 250);
        LinearLayout bottom = page.findViewWithTag("bottom");
        bottom.removeAllViews();
        bottom.addView(textButton(Lang.t("戻る"), this::goBack));
        bottom.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        if (retry != null) bottom.addView(filledButton(Lang.t("もう一度"), retry));
        return page;
    }

    // ------------------------------------------------------------------
    // 結果
    // ------------------------------------------------------------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult scan = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scan != null) {
            if (scan.getContents() == null) return; // キャンセル
            MigrationChannel.Ticket t = MigrationChannel.Ticket.parse(scan.getContents());
            if (t == null) {
                Toast.makeText(this, Lang.t("引っ越しの QR コードではありません"), Toast.LENGTH_LONG).show();
                return;
            }
            // QR コードの読み取り後は、受け取る方法の選択のページから進む
            cancelOperation();
            while (current != receiveMethods && !history.isEmpty()) current = history.pollLast();
            receiveWithTicket(t);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_BT_ENABLE) {
            Runnable then = afterBluetooth;
            afterBluetooth = null;
            BluetoothAdapter a = bluetoothAdapter();
            if (then != null && a != null && a.isEnabled()) then.run();
        } else if (requestCode == REQ_PICK_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            receiveFromFile(data.getData());
        }
    }

    // ------------------------------------------------------------------

    private void vibrate() {
        View v = currentView;
        if (v != null) v.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
    }

    private static void closeQuietly(@Nullable Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
        }
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    @ColorInt
    private int color(int res) {
        return ContextCompat.getColor(this, res);
    }

    @ColorInt
    private int onSurface() {
        return MigrationViews.attr(this, com.google.android.material.R.attr.colorOnSurface);
    }

    @ColorInt
    private int onSurfaceVariant() {
        return MigrationViews.attr(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
    }

    @NonNull
    private static LinearLayout.LayoutParams matchWrap(int left, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = left;
        lp.topMargin = top;
        return lp;
    }

    @NonNull
    private static LinearLayout.LayoutParams wrap(int left, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = left;
        lp.topMargin = top;
        return lp;
    }
}
