package com.airsky.browser;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import org.mozilla.geckoview.StorageController;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 設定 › ストレージ(Pixel の「ストレージ」風)。
 *
 * <p>AirSky が使っている容量を「アプリ本体・ユーザーデータ・サイトのデータ・キャッシュ」に分けて帯で見せ、
 * キャッシュやサイトのデータだけを選んで消せるようにする。</p>
 *
 * <p>マニフェストの {@code android:manageSpaceActivity} にこの画面を割り当てているので、
 * 端末の設定 › アプリ › AirSky › ストレージ の「データを削除」もここに来る。
 * そのままだと 1 回押しただけでブックマーク・パスワード・タブまですべて消えてしまうが、
 * ここでは「すべて削除」を内容を確かめたうえでボタンを長押しした時だけ実行する。</p>
 */
public class StorageManageActivity extends PixelSettingsActivity {

    /** 内訳の並び(帯の左から)。 */
    private static final int APP = 0, DATA = 1, WEB = 2, CACHE = 3;

    private final Handler main = new Handler(Looper.getMainLooper());
    /** 測った大きさ(バイト)。まだなら null。 */
    @Nullable private long[] sizes;
    private long deviceTotal, deviceFree;
    /** 帯と合計の数字を伸ばすのは、測り終わった最初の 1 回だけ。 */
    private boolean animateValues;
    /** 開いた時に項目を上から順に出す。 */
    private boolean cascade = true;
    /** 消去中の内訳(その行に「消去しています…」と出す)。 */
    private int busy = -1;
    private int measureGeneration;

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("ストレージ");
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        measure();
    }

    @Override
    protected void onBuild() {
        long[] s = sizes;
        List<View> fresh = new ArrayList<>();

        // ---- 合計と帯 ----
        card(buildSummary(s));

        // ---- 内訳 ----
        header(Lang.t("内訳"));
        breakdownRow(fresh, APP, R.drawable.ic_ps_browser, Lang.t("アプリ"),
                Lang.t("AirSky Browser と GeckoView のプログラム"), R.color.ps_storage_app);
        breakdownRow(fresh, DATA, R.drawable.ic_ps_data, Lang.t("ユーザーデータ"),
                Lang.t("ブックマーク、パスワード、タブ、メモ、ツール、設定"), R.color.ps_storage_data);
        breakdownRow(fresh, WEB, R.drawable.ic_si_cookie, Lang.t("サイトのデータ"),
                Lang.t("Cookie、サイトのストレージ、閲覧履歴、拡張機能"), R.color.ps_storage_web);
        breakdownRow(fresh, CACHE, R.drawable.ic_ps_speed, Lang.t("キャッシュ"),
                Lang.t("一時ファイル。消しても必要な時に作り直されます"), R.color.ps_storage_cache);

        // ---- 空き容量を増やす ----
        header(Lang.t("空き容量を増やす"));
        Row cache = row(R.drawable.ic_ps_refresh, Lang.t("キャッシュを消去"),
                busy == CACHE ? Lang.t("消去しています…") : Lang.t("ログイン状態やデータはそのまま残ります"),
                () -> confirm(Lang.t("キャッシュを消去しますか?"),
                        Lang.t("一時ファイルを削除します。ページの表示が最初だけ少し遅くなることがあります。"),
                        Lang.t("消去"), this::clearCache));
        if (s != null) fresh.add(valueOf(cache.value(size(s[CACHE]))));
        cache.setEnabled(busy < 0);
        Row web = row(R.drawable.ic_si_cookie, Lang.t("Cookie とサイトデータを消去"),
                busy == WEB ? Lang.t("消去しています…") : Lang.t("ほとんどのサイトからログアウトします"),
                () -> confirm(Lang.t("Cookie とサイトデータを消去しますか?"),
                        Lang.t("すべてのサイトの Cookie と保存されたデータを削除します。ほとんどのサイトからログアウトします。\n"
                                + "ブックマーク・パスワード・タブは消えません。"),
                        Lang.t("消去"), this::clearSiteData));
        if (s != null) fresh.add(valueOf(web.value(size(s[WEB]))));
        web.setEnabled(busy < 0);
        row(R.drawable.ic_ps_history, Lang.t("閲覧履歴データを削除"), Lang.t("履歴・Cookie・キャッシュから選んで削除"),
                () -> startActivity(new Intent(this, PrivacySettingsActivity.class)
                        .putExtra(EXTRA_FOCUS_TITLE, Lang.t("閲覧履歴データを削除")))).chevron();
        row(R.drawable.ic_ps_download, Lang.t("ダウンロードしたファイル"), Lang.t("いらないファイルを削除"),
                () -> open(DownloadListActivity.class)).chevron();

        // ---- すべて削除 ----
        header(Lang.t("すべてのデータ"));
        row(R.drawable.ic_ps_delete, Lang.t("AirSky のデータをすべて削除"),
                Lang.t("アプリをインストールした直後の状態に戻します"), this::showWipeSheet)
                .tone(R.color.ps_tone_red_bg, R.color.ps_tone_red_fg)
                .setEnabled(busy < 0);
        note(Lang.t("端末の設定の「データを削除」からこの画面を開いた場合も、ここで選んだものだけが消えます。"
                + "すべてのデータを削除する時は、確認してからボタンを長押しした時だけ実行します。"));

        // 測り終わった時は、値の文字をふわっと出す
        if (animateValues) {
            for (View v : fresh) {
                v.setAlpha(0f);
                v.animate().alpha(1f).setDuration(300).setStartDelay(250).start();
            }
        }
        animateValues = false;
        if (cascade) {
            cascade = false;
            cascadeIn();
        }
    }

    // ------------------------------------------------------------------
    // 画面
    // ------------------------------------------------------------------

    /** 上のカード: 合計・帯・端末の空き容量。 */
    private View buildSummary(@Nullable long[] s) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(24), dp(24), dp(22));

        TextView total = new TextView(this);
        total.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        total.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurface));
        total.setIncludeFontPadding(false);
        total.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
        box.addView(total);

        TextView caption = new TextView(this);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        caption.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        caption.setText(Lang.t("AirSky Browser が使用中"));
        caption.setPadding(0, dp(6), 0, dp(18));
        box.addView(caption);

        StorageBarView bar = new StorageBarView(this);
        bar.setTrackColor(ContextCompat.getColor(this, R.color.ps_storage_track));
        box.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));

        // 帯の色の見本
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setPadding(0, dp(14), 0, 0);
        String[] names = {Lang.t("アプリ"), Lang.t("ユーザーデータ"), Lang.t("サイト"), Lang.t("キャッシュ")};
        for (int i = 0; i < names.length; i++) legend.addView(legendItem(names[i], segmentColors()[i]));
        android.widget.HorizontalScrollView legendScroll = new android.widget.HorizontalScrollView(this);
        legendScroll.setHorizontalScrollBarEnabled(false);
        legendScroll.addView(legend);
        box.addView(legendScroll);

        TextView device = new TextView(this);
        device.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        device.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        device.setPadding(0, dp(16), 0, 0);
        box.addView(device);

        if (s == null) {
            total.setText("—");
            caption.setText(Lang.t("計算しています…"));
            bar.setSegments(new float[0], new int[0], false);
            device.setVisibility(View.GONE);
            return box;
        }
        long sum = s[APP] + s[DATA] + s[WEB] + s[CACHE];
        // 帯は AirSky の合計に対する割合で分ける(端末全体だと細すぎて見えない)
        float[] fr = new float[4];
        for (int i = 0; i < 4; i++) fr[i] = sum > 0 ? (float) s[i] / sum : 0f;
        bar.setSegments(fr, segmentColors(), animateValues);
        device.setText(String.format(Locale.ROOT, Lang.t("端末の空き容量 %1$s / 全体 %2$s"),
                size(deviceFree), size(deviceTotal)));
        if (animateValues) {
            // 合計の数字を 0 から数え上げる
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(1100);
            a.setStartDelay(120);
            a.setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f));
            a.addUpdateListener(an -> total.setText(size((long) (sum * (float) an.getAnimatedValue()))));
            a.start();
            total.setText(size(0));
            device.setAlpha(0f);
            device.animate().alpha(1f).setStartDelay(500).setDuration(300).start();
        } else {
            total.setText(size(sum));
        }
        return box;
    }

    private View legendItem(String name, int color) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(0, 0, dp(16), 0);
        View dot = new View(this);
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(color);
        dot.setBackground(d);
        item.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        t.setPadding(dp(6), 0, 0, 0);
        item.addView(t);
        return item;
    }

    private void breakdownRow(List<View> fresh, int index, int icon, String title, String summary, int color) {
        Row r = row(icon, title, busy == index ? Lang.t("消去しています…") : summary, null);
        r.icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, color)));
        long[] s = sizes;
        if (s != null) {
            fresh.add(valueOf(r.value(size(s[index]))));
        } else {
            r.value("…");
        }
    }

    /** {@link Row#value} で付けた文字。 */
    private static View valueOf(Row r) {
        ViewGroup end = r.view.findViewById(R.id.ps_end);
        return end.getChildCount() > 0 ? end.getChildAt(0) : end;
    }

    private int[] segmentColors() {
        return new int[]{
                ContextCompat.getColor(this, R.color.ps_storage_app),
                ContextCompat.getColor(this, R.color.ps_storage_data),
                ContextCompat.getColor(this, R.color.ps_storage_web),
                ContextCompat.getColor(this, R.color.ps_storage_cache)};
    }

    /** 開いた時に、カードと行を上から順に少し下から浮かび上がらせる(Pixel の設定の開き方)。 */
    private void cascadeIn() {
        PathInterpolator ease = new PathInterpolator(0.2f, 0f, 0f, 1f);
        for (int i = 0; i < body.getChildCount(); i++) {
            View v = body.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(28));
            v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(Math.min(i, 14) * 28L)
                    .setDuration(420).setInterpolator(ease).start();
        }
    }

    private String size(long bytes) {
        return Formatter.formatShortFileSize(this, bytes);
    }

    // ------------------------------------------------------------------
    // 測る
    // ------------------------------------------------------------------

    private void measure() {
        int gen = ++measureGeneration;
        new Thread(() -> {
            long[] s = new long[4];
            ApplicationInfo ai = getApplicationInfo();
            s[APP] += fileSize(new File(ai.sourceDir));
            if (ai.splitSourceDirs != null) for (String p : ai.splitSourceDirs) s[APP] += fileSize(new File(p));
            if (ai.nativeLibraryDir != null) s[APP] += dirSize(new File(ai.nativeLibraryDir));

            s[CACHE] += dirSize(getCacheDir());
            s[CACHE] += dirSize(getCodeCacheDir());
            File extCache = getExternalCacheDir();
            if (extCache != null) s[CACHE] += dirSize(extCache);

            // データの場所: files/mozilla は GeckoView のプロフィール(サイトのデータ)、残りは AirSky のデータ
            File dataDir = new File(ai.dataDir);
            File[] top = dataDir.listFiles();
            if (top != null) {
                for (File f : top) {
                    String n = f.getName();
                    if (n.equals("cache") || n.equals("code_cache") || n.equals("lib") || isLink(f)) continue;
                    if (n.equals("files")) {
                        File[] inner = f.listFiles();
                        if (inner == null) continue;
                        for (File g : inner) {
                            if (isGeckoProfile(g)) s[WEB] += dirSize(g);
                            else s[DATA] += dirSize(g);
                        }
                    } else if (n.startsWith("app_") && n.contains("gecko")) {
                        s[WEB] += dirSize(f);
                    } else {
                        s[DATA] += dirSize(f);
                    }
                }
            }
            File extFiles = getExternalFilesDir(null);
            if (extFiles != null) s[DATA] += dirSize(extFiles);

            long total = 0, free = 0;
            try {
                StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
                total = fs.getTotalBytes();
                free = fs.getAvailableBytes();
            } catch (Exception ignored) {
            }
            long t = total, fr = free;
            main.post(() -> {
                if (gen != measureGeneration || isFinishing() || isDestroyed()) return;
                boolean first = sizes == null;
                sizes = s;
                deviceTotal = t;
                deviceFree = fr;
                animateValues = true;
                if (!first) busy = -1;
                rebuild();
            });
        }, "StorageMeasure").start();
    }

    private static boolean isGeckoProfile(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.equals("mozilla") || n.contains("gecko");
    }

    private static boolean isLink(File f) {
        try {
            return Files.isSymbolicLink(f.toPath());
        } catch (Exception e) {
            return false;
        }
    }

    private static long fileSize(File f) {
        return f.isFile() ? f.length() : 0;
    }

    /** フォルダの中身の合計(リンクはたどらない)。 */
    private static long dirSize(File root) {
        if (root == null || !root.exists()) return 0;
        if (root.isFile()) return root.length();
        long sum = 0;
        ArrayDeque<File> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            File[] list = stack.pop().listFiles();
            if (list == null) continue;
            for (File f : list) {
                if (isLink(f)) continue;
                if (f.isDirectory()) stack.push(f);
                else sum += f.length();
            }
        }
        return sum;
    }

    // ------------------------------------------------------------------
    // 消す
    // ------------------------------------------------------------------

    private void clearCache() {
        busy = CACHE;
        rebuild();
        GeckoEngine.get(this).getRuntime().getStorageController()
                .clearData(StorageController.ClearFlags.ALL_CACHES)
                .accept(v -> afterGeckoCacheCleared(), e -> afterGeckoCacheCleared());
    }

    private void afterGeckoCacheCleared() {
        new Thread(() -> {
            // GeckoView が使っている所は GeckoView 自身に消してもらったので、それ以外を消す
            deleteChildren(getCacheDir(), true);
            File ext = getExternalCacheDir();
            if (ext != null) deleteChildren(ext, true);
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, Lang.t("キャッシュを削除しました"), Toast.LENGTH_SHORT).show();
                measure();
            });
        }, "StorageClear").start();
    }

    private void clearSiteData() {
        busy = WEB;
        rebuild();
        GeckoEngine.get(this).getRuntime().getStorageController()
                .clearData(StorageController.ClearFlags.SITE_DATA | StorageController.ClearFlags.COOKIES)
                .accept(v -> afterSiteDataCleared(), e -> afterSiteDataCleared());
    }

    private void afterSiteDataCleared() {
        main.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            Toast.makeText(this, Lang.t("Cookie とサイトデータを削除しました"), Toast.LENGTH_SHORT).show();
            measure();
        });
    }

    private static void deleteChildren(File dir, boolean skipGecko) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (skipGecko && isGeckoProfile(f)) continue;
            deleteTree(f);
        }
    }

    private static void deleteTree(File f) {
        if (f.isDirectory() && !isLink(f)) {
            File[] list = f.listFiles();
            if (list != null) for (File c : list) deleteTree(c);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** すべて削除の確認。消えるものを並べて見せ、ボタンを長押しした時だけ実行する。 */
    private void showWipeSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), dp(24));

        int error = attrColor(androidx.appcompat.R.attr.colorError);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_ps_delete);
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ps_tone_red_fg)));
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(ContextCompat.getColor(this, R.color.ps_tone_red_bg));
        icon.setBackground(circle);
        icon.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(64), dp(64));
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        ilp.topMargin = dp(8);
        box.addView(icon, ilp);

        TextView title = new TextView(this);
        title.setText(Lang.t("AirSky のデータをすべて削除しますか?"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurface));
        title.setPadding(0, dp(16), 0, dp(12));
        box.addView(title);

        TextView body = new TextView(this);
        body.setText(Lang.t("次のものがこの端末から完全に削除され、元に戻せません。")
                + "\n\n• " + Lang.t("ブックマーク・閲覧履歴・開いているタブ")
                + "\n• " + Lang.t("保存したパスワード・自動入力の情報")
                + "\n• " + Lang.t("メモ・ツール・拡張機能・保存したページ")
                + "\n• " + Lang.t("Cookie とサイトのデータ・すべての設定")
                + "\n\n" + Lang.t("別の端末に残したい場合は、先に「データの引っ越し」でバックアップしてください。"));
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        body.setLineSpacing(0, 1.2f);
        body.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        box.addView(body);

        HoldToConfirmButton hold = new HoldToConfirmButton(this);
        hold.setLabel(Lang.t("長押ししてすべて削除"));
        hold.setColors(ContextCompat.getColor(this, R.color.ps_tone_red_bg),
                ContextCompat.getColor(this, R.color.ps_tone_red_fg),
                error, attrColor(com.google.android.material.R.attr.colorOnError));
        hold.setHoldMs(3000);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        hlp.topMargin = dp(24);
        box.addView(hold, hlp);

        MaterialButton backup = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        backup.setText(Lang.t("データの引っ越し"));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(12);
        box.addView(backup, blp);
        MaterialButton cancel = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        cancel.setStrokeWidth(0);
        cancel.setText(Lang.t("キャンセル"));
        box.addView(cancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        backup.setOnClickListener(v -> {
            sheet.dismiss();
            open(DeviceMigrationActivity.class);
        });
        cancel.setOnClickListener(v -> sheet.dismiss());
        hold.setOnConfirm(() -> {
            sheet.setCancelable(false);
            cancel.setEnabled(false);
            backup.setEnabled(false);
            hold.setLabel(Lang.t("削除しています…"));
            // 満ちたのが見えるよう少し待ってから消す(この後プロセスは終了する)
            main.postDelayed(this::wipeAll, 350);
        });

        sheet.setContentView(box);
        sheet.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.show();
    }

    private void wipeAll() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am == null || !am.clearApplicationUserData()) {
            Toast.makeText(this, Lang.t("データを削除できませんでした"), Toast.LENGTH_LONG).show();
        }
    }
}
