package com.airsky.browser;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * アップデートの通知から開く、新しいバージョンの APK をダウンロードしてインストールする画面。
 * 見た目は初回セットアップのダウンロード画面({@link SetupProgress})と同じ。
 */
public class UpdateActivity extends AppCompatActivity {

    private static final String EXTRA_VERSION = "com.airsky.browser.extra.UPDATE_VERSION";
    private static final String RELEASE_API = "https://api.github.com/repos/Aozora9200/AirSky-Glass-Browser/releases/latest";
    private static final String DOWNLOAD_BASE = "https://github.com/Aozora9200/AirSky-Glass-Browser/releases/latest/download/";
    private static final String APK_NAME = "update.apk";

    @NonNull
    static Intent intent(@NonNull Context context, @NonNull String version) {
        return new Intent(context, UpdateActivity.class).putExtra(EXTRA_VERSION, version);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView titleText, bodyText, progressText, logText;
    private ProgressBar progressBar;
    private View buttons;
    private Button primary, secondary;
    @Nullable private ObjectAnimator progressAnimator;

    private String version;
    private volatile boolean cancelled;
    private boolean downloading;
    @Nullable private File downloaded;
    /** 「提供元不明のアプリ」の許可を求めて設定画面へ移った。戻ってきたらインストールに進む。 */
    private boolean waitingInstallPermission;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setup_progress);
        AppWallpaper.apply(findViewById(R.id.backgroundImage), null);
        TouchEffectView.attach(getWindow());

        titleText = findViewById(R.id.setupTitle);
        bodyText = findViewById(R.id.setupBody);
        progressText = findViewById(R.id.progressText);
        logText = findViewById(R.id.setupdownloadlog);
        progressBar = findViewById(R.id.setup_progress);
        buttons = findViewById(R.id.setupButtons);
        primary = findViewById(R.id.setupPrimary);
        secondary = findViewById(R.id.setupSecondary);

        version = getIntent().getStringExtra(EXTRA_VERSION);
        if (version == null) version = "";
        // 通知から開いた時も、ダイアログから開いた時も、残っているアップデートの通知は消す
        UpdateChecker.cancelNotification(this);
        titleText.setText(Lang.t("AirSky を最新の状態にします"));
        bodyText.setText(Lang.t("バージョン ") + version + Lang.t(" をダウンロードしてインストールします。\nタブやブックマークなどのデータはそのまま引き継がれます。"));
        progressText.setText(Lang.t("アップデートの準備をしています..."));
        logText.setText("Version " + version);

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (downloading) confirmCancel();
                else close();
            }
        });
        confirmStart();
    }

    /** 初回セットアップと同じく、通信量の注意を出してから始める。 */
    private void confirmStart() {
        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("アップデート"))
                .setMessage(Lang.t("最新バージョンのアプリ(100MB 以上)をダウンロードします。モバイルデータや従量制課金接続の場合、通信料が発生する場合があります。"))
                .setPositiveButton(Lang.t("開始"), (d, w) -> startDownload())
                .setNegativeButton(Lang.t("キャンセル"), (d, w) -> close())
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        dialog.show();
    }

    private void startDownload() {
        cancelled = false;
        downloading = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        progressText.setText(Lang.t("最新バージョンを確認しています..."));
        logText.setText("Connecting to github.com...");
        setProgressAnimated(0);
        showButtons(null, null, Lang.t("キャンセル"), this::confirmCancel);
        executor.execute(this::download);
    }

    // ------------------------------------------------------------------
    // ダウンロード(バックグラウンド)
    // ------------------------------------------------------------------

    private void download() {
        File out = new File(getExternalFilesDir(null), APK_NAME);
        File part = new File(out.getPath() + ".part");
        HttpURLConnection conn = null;
        try {
            String url = resolveApkUrl();
            if (cancelled) return;
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.connect();
            if (conn.getResponseCode() / 100 != 2) throw new IOException("HTTP " + conn.getResponseCode());
            long length = conn.getContentLengthLong();
            String fileName = Uri.parse(conn.getURL().toString()).getLastPathSegment();
            post(() -> progressText.setText(Lang.t("ダウンロードしています...")));

            long total = 0;
            long start = SystemClock.elapsedRealtime();
            long lastUi = 0;
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream fos = new FileOutputStream(part)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (cancelled) return;
                    fos.write(buf, 0, n);
                    total += n;
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastUi >= 250) {
                        lastUi = now;
                        long done = total;
                        double sec = Math.max(0.001, (now - start) / 1000.0);
                        double mbps = done / sec / (1024 * 1024);
                        int pct = length > 0 ? (int) (done * 100 / length) : -1;
                        String name = fileName != null ? fileName : APK_NAME;
                        post(() -> onDownloadProgress(pct, done, length, mbps, name));
                    }
                }
                fos.getFD().sync();
            }
            if (length > 0 && total != length) throw new IOException(Lang.t("途中で切断されました"));
            if (out.exists() && !out.delete()) throw new IOException(Lang.t("古いファイルを削除できません"));
            if (!part.renameTo(out)) throw new IOException(Lang.t("ファイルを保存できません"));
            post(() -> onDownloaded(out));
        } catch (Exception e) {
            if (!cancelled) post(() -> onFailed(e));
        } finally {
            if (conn != null) conn.disconnect();
            if (cancelled || !out.exists()) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
            }
        }
    }

    /**
     * リリースに添付された APK のうち、この端末の CPU(ABI)に合うものを選ぶ。
     * API が使えないときは AirSky-Browser-バージョン-ABI.apk を直接取りに行く。
     */
    @NonNull
    private String resolveApkUrl() {
        String fallback = DOWNLOAD_BASE + "AirSky-Browser-" + version + "-" + Build.SUPPORTED_ABIS[0] + ".apk";
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(RELEASE_API).openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            String body;
            try (InputStream in = conn.getInputStream(); Scanner sc = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
                body = sc.hasNext() ? sc.next() : "";
            }
            JSONArray assets = new JSONObject(body).optJSONArray("assets");
            if (assets == null) return fallback;
            String generic = null;
            for (String abi : Build.SUPPORTED_ABIS) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    String name = a.optString("name").toLowerCase(Locale.ROOT);
                    if (!name.endsWith(".apk")) continue;
                    if (name.contains(abi.toLowerCase(Locale.ROOT))) return a.getString("browser_download_url");
                }
            }
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                String name = a.optString("name").toLowerCase(Locale.ROOT);
                if (name.endsWith(".apk") && !name.contains("arm") && !name.contains("x86")) {
                    generic = a.getString("browser_download_url");
                    break;
                }
            }
            return generic != null ? generic : fallback;
        } catch (Exception e) {
            return fallback;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void post(Runnable r) {
        handler.post(() -> {
            if (!isFinishing() && !isDestroyed()) r.run();
        });
    }

    // ------------------------------------------------------------------
    // 画面の更新
    // ------------------------------------------------------------------

    private void onDownloadProgress(int pct, long done, long length, double mbps, String name) {
        if (pct >= 0) {
            setProgressAnimated(pct);
            progressText.setText(Lang.t("ダウンロードしています... ") + pct + "%");
        } else {
            progressBar.setIndeterminate(true);
        }
        String size = length > 0 ? mb(done) + " / " + mb(length) : mb(done);
        logText.setText("Downloading " + name + "\n" + size + " · " + String.format(Locale.US, "%.2f MB/s", mbps));
    }

    private void onDownloaded(@NonNull File file) {
        downloading = false;
        downloaded = file;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        progressBar.setIndeterminate(false);
        setProgressAnimated(100);
        progressText.setText(Lang.t("ダウンロードが完了しました"));
        logText.setText("Ready to install · " + mb(file.length()));
        showButtons(Lang.t("インストール"), this::install, Lang.t("後で"), this::close);
        install();
    }

    private void onFailed(@NonNull Exception e) {
        downloading = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        progressBar.setIndeterminate(false);
        progressText.setText(Lang.t("ダウンロードに失敗しました"));
        logText.setText("Error: " + e.getMessage());
        showButtons(Lang.t("再試行"), this::startDownload, Lang.t("閉じる"), this::close);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("エラー"))
                .setMessage(Lang.t("ダウンロードに失敗しました。ネットワーク接続をご確認ください。繰り返し症状が続く場合は、ヘルプ画面のお問い合わせからご連絡ください。"))
                .setPositiveButton("OK", null)
                .show();
    }

    private void confirmCancel() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("アップデートを中止しますか？"))
                .setMessage(Lang.t("ダウンロード中のファイルは削除されます。"))
                .setPositiveButton(Lang.t("中止する"), (d, w) -> {
                    cancelled = true;
                    close();
                })
                .setNegativeButton(Lang.t("続ける"), null)
                .show();
    }

    private void showButtons(@Nullable String primaryLabel, @Nullable Runnable primaryAction,
                             @NonNull String secondaryLabel, @NonNull Runnable secondaryAction) {
        buttons.setVisibility(View.VISIBLE);
        secondary.setText(secondaryLabel);
        secondary.setOnClickListener(v -> secondaryAction.run());
        if (primaryLabel != null && primaryAction != null) {
            primary.setVisibility(View.VISIBLE);
            primary.setText(primaryLabel);
            primary.setOnClickListener(v -> primaryAction.run());
        } else {
            primary.setVisibility(View.GONE);
        }
    }

    private void setProgressAnimated(int pct) {
        if (progressAnimator != null) progressAnimator.cancel();
        progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", pct);
        progressAnimator.setDuration(400);
        progressAnimator.setInterpolator(new DecelerateInterpolator());
        progressAnimator.start();
    }

    private static String mb(long bytes) {
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ------------------------------------------------------------------
    // インストール
    // ------------------------------------------------------------------

    private void install() {
        if (downloaded == null || !downloaded.exists()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(Lang.t("インストールの許可"))
                    .setMessage(Lang.t("アップデートをインストールするには、AirSky に「不明なアプリのインストール」を許可してください。"))
                    .setPositiveButton(Lang.t("設定を開く"), (d, w) -> {
                        waitingInstallPermission = true;
                        startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName())));
                    })
                    .setNegativeButton(Lang.t("キャンセル"), null)
                    .show();
            return;
        }
        progressText.setText(Lang.t("インストールしています..."));
        Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", downloaded);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            progressText.setText(Lang.t("インストーラーを開けませんでした"));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingInstallPermission) {
            waitingInstallPermission = false;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls()) {
                install();
            }
        }
    }

    private void close() {
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) cancelled = cancelled || downloading;
        executor.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
