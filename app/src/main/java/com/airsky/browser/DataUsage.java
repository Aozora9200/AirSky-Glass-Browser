package com.airsky.browser;

import android.animation.ValueAnimator;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * データ使用量(Pixel の設定アプリ風)。
 *
 * <ul>
 *   <li>今日・7 日間・30 日間 の使用量を、Wi-Fi とモバイルに色分けしたリングで見せる
 *       (端末の記録 NetworkStatsManager から、このアプリの分だけを読む。読めない端末では起動後の合計)。</li>
 *   <li>いまのダウンロード/アップロードの速度を 1 秒ごとに測り、流れる折れ線グラフで見せる。</li>
 *   <li>「計測中」: リセットしてからの使用量。</li>
 *   <li>日ごと(今日は 1 時間ごと)の棒グラフ。棒を触るとその日の量を出す。</li>
 *   <li>受信・送信・平均・いちばん多かった日・データセーバー の内訳。</li>
 * </ul>
 */
public class DataUsage extends AppCompatActivity {

    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final int RANGE_TODAY = 0, RANGE_WEEK = 1, RANGE_MONTH = 2;
    private static final String PREFS = "data_usage";
    private static final long DAY = 24 * 60 * 60 * 1000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private float dp;
    private int primary, tertiary, track, onSurface, onVariant, cardColor;

    private RingView ring;
    private BarChartView chart;
    private SparklineView downGraph, upGraph;
    private TextView downSpeed, upSpeed, session, sessionSince, chartValue, chartTitle, note;
    private LinearLayout legend, breakdown;
    private int range = RANGE_TODAY;

    /** 期間ごとに読んだ結果。 */
    private static final class Usage {
        boolean fromSystem;
        long wifi, mobile, rx, tx;
        /** グラフの棒(今日は 24 時間、それ以外は日)。 */
        long[] barWifi, barMobile;
        String[] barLabels;
        String[] barTitles;
    }

    // いまの速度
    private long lastRx = -1, lastTx = -1, lastSampleAt;
    private long sessionBaseRx, sessionBaseTx, sessionStart;
    private final Runnable sampler = this::sample;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_datausage);
        TouchEffectView.attach(getWindow());
        dp = getResources().getDisplayMetrics().density;
        primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.BLUE);
        tertiary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary, Color.MAGENTA);
        track = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest, Color.LTGRAY);
        onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        onVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        cardColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceBright, Color.WHITE);

        MaterialToolbar toolbar = findViewById(R.id.duToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        downSpeed = findViewById(R.id.duDownSpeed);
        upSpeed = findViewById(R.id.duUpSpeed);
        session = findViewById(R.id.duSession);
        sessionSince = findViewById(R.id.duSessionSince);
        chartValue = findViewById(R.id.duChartValue);
        chartTitle = findViewById(R.id.duChartTitle);
        note = findViewById(R.id.duNote);
        legend = findViewById(R.id.duLegend);
        breakdown = findViewById(R.id.duBreakdown);

        ring = new RingView(this);
        ((FrameLayout) findViewById(R.id.duRingHolder)).addView(ring);
        chart = new BarChartView(this);
        ((FrameLayout) findViewById(R.id.duChartHolder)).addView(chart);
        downGraph = new SparklineView(this, primary);
        ((FrameLayout) findViewById(R.id.duDownGraph)).addView(downGraph);
        upGraph = new SparklineView(this, tertiary);
        ((FrameLayout) findViewById(R.id.duUpGraph)).addView(upGraph);

        setupRangeChips();
        setupSession();
        findViewById(R.id.duSystemSettings).setOnClickListener(v -> openSystemSettings());

        playEnter();
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastRx = -1;
        main.post(sampler);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(sampler);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** カードを上から順に浮かび上がらせる。 */
    private void playEnter() {
        ViewGroup content = findViewById(R.id.duContent);
        for (int i = 0; i < content.getChildCount(); i++) {
            View v = content.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(px(28));
            v.animate().alpha(1f).translationY(0f).setStartDelay(40L * i).setDuration(480)
                    .setInterpolator(EMPHASIZED).start();
        }
    }

    private void setupRangeChips() {
        ChipGroup group = findViewById(R.id.duRange);
        String[] labels = {Lang.t("今日"), Lang.t("7 日間"), Lang.t("30 日間")};
        for (int i = 0; i < labels.length; i++) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_history_chip, group, false);
            chip.setId(View.generateViewId());
            chip.setText(labels[i]);
            chip.setCheckable(true);
            chip.setTag(i);
            chip.setChecked(i == range);
            group.addView(chip);
        }
        group.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            range = (int) ((Chip) g.findViewById(ids.get(0))).getTag();
            load();
        });
    }

    // ------------------------------------------------------------------
    // 期間の使用量
    // ------------------------------------------------------------------

    private void load() {
        final int r = range;
        worker.execute(() -> {
            Usage u = query(r);
            main.post(() -> {
                if (isFinishing() || r != range) return;
                show(u);
            });
        });
    }

    private Usage query(int r) {
        Usage u = new Usage();
        Calendar c = Calendar.getInstance();
        long now = c.getTimeInMillis();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long todayStart = c.getTimeInMillis();
        int bars = r == RANGE_TODAY ? 24 : (r == RANGE_WEEK ? 7 : 30);
        long barLen = r == RANGE_TODAY ? 60 * 60 * 1000L : DAY;
        long start = r == RANGE_TODAY ? todayStart : todayStart - (bars - 1) * DAY;
        u.barWifi = new long[bars];
        u.barMobile = new long[bars];
        u.barLabels = new String[bars];
        u.barTitles = new String[bars];
        SimpleDateFormat dayFmt = new SimpleDateFormat("M/d(E)", Locale.JAPAN);
        for (int i = 0; i < bars; i++) {
            long t = start + i * barLen;
            if (r == RANGE_TODAY) {
                u.barLabels[i] = i % 6 == 0 ? String.valueOf(i) : "";
                u.barTitles[i] = i + Lang.t(":00〜") + (i + 1) + ":00";
            } else {
                Calendar d = Calendar.getInstance();
                d.setTimeInMillis(t);
                boolean label = r == RANGE_WEEK || i % 5 == 0 || i == bars - 1;
                u.barLabels[i] = label ? (r == RANGE_WEEK
                        ? new SimpleDateFormat("E", Locale.JAPAN).format(d.getTime())
                        : String.valueOf(d.get(Calendar.DAY_OF_MONTH))) : "";
                u.barTitles[i] = dayFmt.format(d.getTime());
            }
        }

        NetworkStatsManager nsm = (NetworkStatsManager) getSystemService(Context.NETWORK_STATS_SERVICE);
        int uid = android.os.Process.myUid();
        boolean any = false;
        if (nsm != null) {
            any |= collect(nsm, ConnectivityManager.TYPE_WIFI, uid, start, now, barLen, u, true);
            any |= collect(nsm, ConnectivityManager.TYPE_MOBILE, uid, start, now, barLen, u, false);
        }
        u.fromSystem = any;
        if (!any) {
            // 端末の記録が読めない: 起動してからの合計だけ(回線の区別はできない)
            long rx = TrafficStats.getUidRxBytes(uid), tx = TrafficStats.getUidTxBytes(uid);
            if (rx != TrafficStats.UNSUPPORTED && tx != TrafficStats.UNSUPPORTED) {
                u.rx = rx;
                u.tx = tx;
                u.wifi = rx + tx;
                u.barWifi[bars - 1] = rx + tx;
            }
        }
        return u;
    }

    private static boolean collect(NetworkStatsManager nsm, int type, int uid, long start, long end,
                                   long barLen, Usage u, boolean wifi) {
        NetworkStats stats = null;
        try {
            stats = nsm.queryDetailsForUid(type, null, start, end, uid);
            NetworkStats.Bucket b = new NetworkStats.Bucket();
            boolean any = false;
            while (stats.hasNextBucket()) {
                stats.getNextBucket(b);
                long bytes = b.getRxBytes() + b.getTxBytes();
                if (bytes <= 0) continue;
                any = true;
                u.rx += b.getRxBytes();
                u.tx += b.getTxBytes();
                if (wifi) u.wifi += bytes; else u.mobile += bytes;
                int i = (int) ((Math.max(b.getStartTimeStamp(), start) - start) / barLen);
                if (i >= 0 && i < u.barWifi.length) {
                    if (wifi) u.barWifi[i] += bytes; else u.barMobile[i] += bytes;
                }
            }
            return any;
        } catch (Exception e) {
            // モバイルの回線情報が無い/読めない端末など
            return false;
        } finally {
            if (stats != null) stats.close();
        }
    }

    private void show(Usage u) {
        String label = range == RANGE_TODAY ? Lang.t("今日") : (range == RANGE_WEEK ? Lang.t("過去 7 日間") : Lang.t("過去 30 日間"));
        ring.setValues(u.wifi, u.mobile, u.fromSystem ? label : Lang.t("起動してから"));
        note.setText(u.fromSystem
                ? Lang.t("このアプリ(AirSky)の通信量です。端末の記録から集計しています。")
                : Lang.t("この端末では期間ごとの記録を読めないため、端末を起動してからの合計を表示しています。"));

        legend.removeAllViews();
        if (u.fromSystem) {
            legend.addView(legendItem(primary, "Wi-Fi", u.wifi));
            View gap = new View(this);
            legend.addView(gap, new LinearLayout.LayoutParams(px(20), 1));
            legend.addView(legendItem(tertiary, Lang.t("モバイル"), u.mobile));
        } else {
            // 回線の区別ができないので合計だけ
            legend.addView(legendItem(primary, Lang.t("合計"), u.wifi));
        }

        chartTitle.setText(range == RANGE_TODAY ? Lang.t("時間ごとの使用量") : Lang.t("日ごとの使用量"));
        chart.setData(u);
        long total = u.wifi + u.mobile;
        chartValue.setText(range == RANGE_TODAY ? Lang.t("今日の合計 ") + format(total) : Lang.t("合計 ") + format(total));

        // 内訳
        breakdown.removeAllViews();
        long peak = 0;
        int peakAt = -1;
        int days = 0;
        for (int i = 0; i < u.barWifi.length; i++) {
            long v = u.barWifi[i] + u.barMobile[i];
            if (v > peak) {
                peak = v;
                peakAt = i;
            }
            if (v > 0) days++;
        }
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        rows.add(new String[]{Lang.t("受信(ダウンロード)"), format(u.rx)});
        rows.add(new String[]{Lang.t("送信(アップロード)"), format(u.tx)});
        if (range != RANGE_TODAY && u.fromSystem) {
            rows.add(new String[]{Lang.t("1 日の平均"), format(days > 0 ? total / Math.max(1, u.barWifi.length) : 0)});
            if (peakAt >= 0) rows.add(new String[]{Lang.t("いちばん多かった日"), u.barTitles[peakAt] + "  " + format(peak)});
        } else if (range == RANGE_TODAY && peakAt >= 0 && u.fromSystem) {
            rows.add(new String[]{Lang.t("いちばん多かった時間"), u.barTitles[peakAt] + "  " + format(peak)});
        }
        int uid = android.os.Process.myUid();
        long bootRx = TrafficStats.getUidRxBytes(uid), bootTx = TrafficStats.getUidTxBytes(uid);
        if (bootRx != TrafficStats.UNSUPPORTED && bootTx != TrafficStats.UNSUPPORTED) {
            rows.add(new String[]{Lang.t("端末を起動してからの合計"), format(bootRx + bootTx)});
        }
        rows.add(new String[]{Lang.t("データセーバー"), dataSaverState()});
        for (int i = 0; i < rows.size(); i++) {
            breakdown.addView(row(rows.get(i)[0], rows.get(i)[1], i == 0, i == rows.size() - 1));
        }
    }

    private String dataSaverState() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return Lang.t("不明");
        switch (cm.getRestrictBackgroundStatus()) {
            case ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED: return Lang.t("オン");
            case ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED: return Lang.t("オン(このアプリは対象外)");
            default: return Lang.t("オフ");
        }
    }

    private View legendItem(int color, String name, long bytes) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        dot.setBackground(d);
        l.addView(dot, new LinearLayout.LayoutParams(px(10), px(10)));
        TextView t = new TextView(this);
        t.setText(name + "  " + format(bytes));
        t.setTextColor(onSurface);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(px(6));
        l.addView(t, lp);
        return l;
    }

    /** 内訳の 1 行。まとまりの最初と最後だけ大きく丸めたカードを 2dp 間隔で積む。 */
    private View row(String name, String value, boolean first, boolean last) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(px(60));
        r.setPadding(px(20), px(12), px(20), px(12));
        GradientDrawable bg = new GradientDrawable();
        float big = px(24), small = px(6);
        float t = first ? big : small, b = last ? big : small;
        bg.setCornerRadii(new float[]{t, t, t, t, b, b, b, b});
        bg.setColor(cardColor);
        r.setBackground(bg);
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextColor(onSurface);
        n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        r.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(onVariant);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        r.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = last ? 0 : px(2);
        r.setLayoutParams(lp);
        return r;
    }

    private void openSystemSettings() {
        Intent i = Build.VERSION.SDK_INT >= 28
                ? new Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
                : new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // ------------------------------------------------------------------
    // いまの速度・計測中
    // ------------------------------------------------------------------

    private void setupSession() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        int uid = android.os.Process.myUid();
        long rx = TrafficStats.getUidRxBytes(uid), tx = TrafficStats.getUidTxBytes(uid);
        sessionBaseRx = p.getLong("baseRx", -1);
        sessionBaseTx = p.getLong("baseTx", -1);
        sessionStart = p.getLong("start", 0);
        long boot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        // 未設定、または端末を再起動して数え直しになった
        if (sessionBaseRx < 0 || sessionStart < boot || rx < sessionBaseRx || tx < sessionBaseTx) {
            resetSession(rx, tx);
        }
        findViewById(R.id.duSessionReset).setOnClickListener(v -> {
            resetSession(TrafficStats.getUidRxBytes(uid), TrafficStats.getUidTxBytes(uid));
            session.animate().scaleX(0.85f).scaleY(0.85f).alpha(0.3f).setDuration(120).withEndAction(() -> {
                updateSession();
                session.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
            }).start();
        });
        updateSession();
    }

    private void resetSession(long rx, long tx) {
        sessionBaseRx = Math.max(0, rx);
        sessionBaseTx = Math.max(0, tx);
        sessionStart = System.currentTimeMillis();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong("baseRx", sessionBaseRx).putLong("baseTx", sessionBaseTx)
                .putLong("start", sessionStart).apply();
    }

    private void updateSession() {
        int uid = android.os.Process.myUid();
        long rx = TrafficStats.getUidRxBytes(uid), tx = TrafficStats.getUidTxBytes(uid);
        if (rx == TrafficStats.UNSUPPORTED) {
            session.setText("—");
            sessionSince.setText(Lang.t("この端末では測れません"));
            return;
        }
        session.setText(format(Math.max(0, rx - sessionBaseRx) + Math.max(0, tx - sessionBaseTx)));
        sessionSince.setText(new SimpleDateFormat("M/d HH:mm", Locale.JAPAN).format(sessionStart) + Lang.t(" から"));
    }

    private void sample() {
        int uid = android.os.Process.myUid();
        long rx = TrafficStats.getUidRxBytes(uid), tx = TrafficStats.getUidTxBytes(uid);
        long now = SystemClock.elapsedRealtime();
        if (rx != TrafficStats.UNSUPPORTED && tx != TrafficStats.UNSUPPORTED) {
            if (lastRx >= 0 && now > lastSampleAt) {
                float sec = (now - lastSampleAt) / 1000f;
                float down = Math.max(0, rx - lastRx) / sec;
                float up = Math.max(0, tx - lastTx) / sec;
                downSpeed.setText(format((long) down) + "/s");
                upSpeed.setText(format((long) up) + "/s");
                downGraph.push(down);
                upGraph.push(up);
            }
            lastRx = rx;
            lastTx = tx;
            lastSampleAt = now;
            updateSession();
        }
        main.postDelayed(sampler, 1000);
    }

    // ------------------------------------------------------------------
    // 共通
    // ------------------------------------------------------------------

    static String format(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double v = bytes / 1024.0;
        String[] units = {"KB", "MB", "GB", "TB"};
        int u = 0;
        while (v >= 1024 && u < units.length - 1) {
            v /= 1024;
            u++;
        }
        return String.format(Locale.JAPAN, v >= 100 ? "%.0f %s" : (v >= 10 ? "%.1f %s" : "%.2f %s"), v, units[u]);
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    // ------------------------------------------------------------------
    // リング
    // ------------------------------------------------------------------

    /** Wi-Fi とモバイルの割合を色分けした太いリング。値が変わると伸び縮みし、真ん中の数字も数え上がる。 */
    private final class RingView extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private float wifiFrac, mobileFrac;
        private float fromWifi, fromMobile, toWifi, toMobile;
        private long shownTotal, fromTotal, toTotal;
        private String label = "";
        private float intro;
        private ValueAnimator anim;

        RingView(Context c) {
            super(c);
            float stroke = 22 * dp;
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeWidth(stroke);
            trackPaint.setColor(track);
            trackPaint.setStrokeCap(Paint.Cap.ROUND);
            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeWidth(stroke);
            arcPaint.setStrokeCap(Paint.Cap.ROUND);
            big.setColor(onSurface);
            big.setTextAlign(Paint.Align.CENTER);
            big.setTextSize(36 * dp);
            big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            small.setColor(onVariant);
            small.setTextAlign(Paint.Align.CENTER);
            small.setTextSize(14 * dp);
            setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        void setValues(long wifi, long mobile, String label) {
            this.label = label;
            long total = wifi + mobile;
            fromWifi = wifiFrac;
            fromMobile = mobileFrac;
            // 1 色だけでも全部は埋めず、少し隙間を残す(リングに見えるように)
            float fill = total > 0 ? 0.92f : 0f;
            toWifi = total > 0 ? fill * wifi / total : 0f;
            toMobile = total > 0 ? fill * mobile / total : 0f;
            fromTotal = shownTotal;
            toTotal = total;
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1100);
            anim.setInterpolator(EMPHASIZED);
            anim.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                wifiFrac = fromWifi + (toWifi - fromWifi) * f;
                mobileFrac = fromMobile + (toMobile - fromMobile) * f;
                shownTotal = fromTotal + (long) ((toTotal - fromTotal) * f);
                intro = Math.max(intro, f);
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onDraw(@NonNull Canvas c) {
            float stroke = trackPaint.getStrokeWidth();
            float size = Math.min(getWidth(), getHeight()) - stroke;
            float l = (getWidth() - size) / 2f, t = (getHeight() - size) / 2f;
            oval.set(l, t, l + size, t + size);
            // 背景のリングは最初に少し回りながら現れる
            c.save();
            c.rotate(-30f * (1f - intro), getWidth() / 2f, getHeight() / 2f);
            c.drawArc(oval, 0, 360, false, trackPaint);
            c.restore();
            float start = -90f;
            float gap = 6f; // 色の間のすき間(度)
            if (wifiFrac > 0.001f) {
                arcPaint.setColor(primary);
                c.drawArc(oval, start, Math.max(0.5f, wifiFrac * 360f - (mobileFrac > 0.001f ? gap : 0)), false, arcPaint);
            }
            if (mobileFrac > 0.001f) {
                arcPaint.setColor(tertiary);
                c.drawArc(oval, start + wifiFrac * 360f, Math.max(0.5f, mobileFrac * 360f - gap), false, arcPaint);
            }
            float cy = getHeight() / 2f;
            c.drawText(format(shownTotal), getWidth() / 2f, cy + big.getTextSize() * 0.3f, big);
            c.drawText(label, getWidth() / 2f, cy + big.getTextSize() * 0.3f + small.getTextSize() * 1.8f, small);
        }
    }

    // ------------------------------------------------------------------
    // 棒グラフ
    // ------------------------------------------------------------------

    /** 日ごと(時間ごと)の積み上げ棒グラフ。棒は左から順に伸び、触った棒の量を上に出す。 */
    private final class BarChartView extends View {
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path clip = new Path();
        private Usage data;
        private float grow;
        private int selected = -1;
        private ValueAnimator anim;

        BarChartView(Context c) {
            super(c);
            labelPaint.setColor(onVariant);
            labelPaint.setTextSize(11 * dp);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            gridPaint.setColor(track);
            gridPaint.setStrokeWidth(dp);
            setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        void setData(Usage u) {
            data = u;
            selected = -1;
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(900);
            anim.setInterpolator(EMPHASIZED);
            anim.addUpdateListener(a -> {
                grow = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        private long max() {
            long m = 1;
            for (int i = 0; i < data.barWifi.length; i++) m = Math.max(m, data.barWifi[i] + data.barMobile[i]);
            return m;
        }

        @Override
        protected void onDraw(@NonNull Canvas c) {
            if (data == null) return;
            int n = data.barWifi.length;
            float labelH = 18 * dp;
            float h = getHeight() - labelH;
            float slot = getWidth() / (float) n;
            float w = Math.min(slot * 0.62f, 28 * dp);
            float radius = Math.min(w / 2f, 8 * dp);
            c.drawLine(0, h, getWidth(), h, gridPaint);
            c.drawLine(0, h / 2f, getWidth(), h / 2f, gridPaint);
            long max = max();
            for (int i = 0; i < n; i++) {
                // 左の棒から順に少しずつ遅れて伸びる
                float local = Math.max(0f, Math.min(1f, (grow * 1.6f) - i * (0.6f / n)));
                float cx = slot * i + slot / 2f;
                long total = data.barWifi[i] + data.barMobile[i];
                float barH = h * 0.94f * total / max * local;
                float mobileH = total > 0 ? barH * data.barMobile[i] / total : 0;
                boolean dim = selected >= 0 && selected != i;
                r.set(cx - w / 2f, h - barH, cx + w / 2f, h);
                clip.reset();
                clip.addRoundRect(r, new float[]{radius, radius, radius, radius, 0, 0, 0, 0}, Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                barPaint.setShader(null);
                barPaint.setColor(withAlpha(primary, dim ? 0.35f : 1f));
                c.drawRect(r.left, h - barH + mobileH, r.right, h, barPaint);
                barPaint.setColor(withAlpha(tertiary, dim ? 0.35f : 1f));
                c.drawRect(r.left, h - barH, r.right, h - barH + mobileH, barPaint);
                c.restore();
                if (total == 0) {
                    // 使っていない日も、どこにあるか分かるように小さな点を置く
                    barPaint.setColor(track);
                    c.drawCircle(cx, h - 3 * dp, 2.5f * dp, barPaint);
                }
                String label = data.barLabels[i];
                if (!label.isEmpty()) c.drawText(label, cx, getHeight() - 4 * dp, labelPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (data == null) return false;
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                int n = data.barWifi.length;
                int i = (int) (e.getX() / (getWidth() / (float) n));
                i = Math.max(0, Math.min(n - 1, i));
                if (i != selected) {
                    selected = i;
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    long wifi = data.barWifi[i], mobile = data.barMobile[i];
                    chartValue.setText(data.barTitles[i] + "  " + format(wifi + mobile)
                            + (mobile > 0 ? Lang.t("(モバイル ") + format(mobile) + ")" : ""));
                    invalidate();
                }
                return true;
            }
            return super.onTouchEvent(e);
        }
    }

    private static int withAlpha(int color, float a) {
        return (Math.round(Color.alpha(color) * a) << 24) | (color & 0x00FFFFFF);
    }

    // ------------------------------------------------------------------
    // 速度の折れ線
    // ------------------------------------------------------------------

    /** 直近 60 秒の速度。新しい値が入ると左へ滑らかに流れる。 */
    private final class SparklineView extends View {
        private static final int POINTS = 60;
        private final float[] values = new float[POINTS];
        private int count;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Path area = new Path();
        private final int color;
        private float shift; // 0→1 で 1 目盛り分流れる
        private float shownMax = 1;
        private ValueAnimator anim;

        SparklineView(Context c, int color) {
            super(c);
            this.color = color;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2.5f * dp);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            line.setColor(color);
            dot.setColor(color);
            setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        void push(float v) {
            System.arraycopy(values, 1, values, 0, POINTS - 1);
            values[POINTS - 1] = v;
            count = Math.min(POINTS, count + 1);
            float targetMax = 1;
            for (float x : values) targetMax = Math.max(targetMax, x);
            final float fromMax = shownMax, toMax = targetMax * 1.15f;
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(900);
            anim.setInterpolator(EMPHASIZED);
            anim.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                shift = 1f - f;
                shownMax = fromMax + (toMax - fromMax) * f;
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            fill.setShader(new LinearGradient(0, 0, 0, h, withAlpha(color, 0.35f), withAlpha(color, 0f),
                    Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(@NonNull Canvas c) {
            float w = getWidth(), h = getHeight();
            float step = w / (POINTS - 2);
            path.reset();
            area.reset();
            float pad = line.getStrokeWidth();
            float prevX = 0, prevY = 0;
            for (int i = 0; i < POINTS; i++) {
                float x = (i - 1 + shift) * step;
                float y = h - pad - (h - pad * 2) * Math.min(1f, values[i] / shownMax);
                if (i == 0) {
                    path.moveTo(x, y);
                    area.moveTo(x, h);
                    area.lineTo(x, y);
                } else {
                    // なめらかな曲線でつなぐ
                    float mx = (prevX + x) / 2f;
                    path.cubicTo(mx, prevY, mx, y, x, y);
                    area.cubicTo(mx, prevY, mx, y, x, y);
                }
                prevX = x;
                prevY = y;
            }
            area.lineTo(prevX, h);
            area.close();
            c.save();
            c.clipRect(0, 0, w, h);
            c.drawPath(area, fill);
            c.drawPath(path, line);
            c.restore();
            // いちばん新しい点
            if (count > 0) c.drawCircle(Math.min(prevX, w - 4 * dp), prevY, 3.5f * dp, dot);
        }
    }
}
