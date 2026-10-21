package com.airsky.browser;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.geckoview.WebRequest;
import org.mozilla.geckoview.WebResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ダウンロード・画像保存をまとめて扱う。
 *
 * ページからのダウンロード(GeckoView の onExternalResponse)も、長押しメニューの
 * 「リンクをダウンロード」「画像を保存」も、Gecko が Cookie・認証付きで取得したレスポンスを
 * そのまま書き出す。進行状況は通知と「ダウンロード」画面に表示する。
 *
 * ダウンロード画面は DownloadManager の ID で状態を問い合わせる作りのため、
 * ここでのダウンロードには負の ID を割り当て、{@link #getProgress(long)} で状態を返す。
 */
public final class AppDownloader {

    private static final String CHANNEL_ID = "downloads";
    private static final String GROUP_KEY = "com.airsky.browser.DOWNLOADS";
    private static final int SUMMARY_ID = 0x0D0A0D0A;
    static final String ACTION_CANCEL = "com.airsky.browser.action.CANCEL_DOWNLOAD";
    static final String EXTRA_ID = "download_id";
    /** ダウンロード画面を開いて、失敗したダウンロードをやり直す(通知の「再試行」)。 */
    static final String EXTRA_RETRY_ID = "retry_download_id";
    static final String EXTRA_RETRY_URL = "retry_download_url";
    static final int REQUEST_NOTIFICATION_PERMISSION = 0x0D1;

    static final String PREFS = "DownloadPrefs";
    /** 「ファイルをもう一度ダウンロードしますか？」の「次回から表示しない」。 */
    static final String KEY_SKIP_DUPLICATE_DIALOG = "skipDuplicateDialog";
    /** ダウンロードを始める前に、ファイル名と保存先を確認する。 */
    static final String KEY_ASK_BEFORE = "askBeforeDownload";
    /** ページからのダウンロードの保存先(Environment.DIRECTORY_DOWNLOADS など)。 */
    static final String KEY_DEFAULT_DIR = "defaultDir";
    private static final String KEY_NOTIFICATION_ASKED = "notificationAsked";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    /** 進行中(または直前に終わった)Gecko 経由のダウンロード。キーは負の ID。 */
    private static final Map<Long, Progress> ACTIVE = new ConcurrentHashMap<>();

    /** Gecko 経由ダウンロードの進行状況。 */
    public static final class Progress {
        // 完了時に確定した名前・場所へ差し替える(Android 11 以降は書き込み中は ".pending-..." という名前)
        public volatile String fileName;
        public volatile String filePath;
        /** MediaStore の URI(Android 10 以降)。無ければ null。 */
        @Nullable public volatile String contentUri;
        public volatile long downloaded;
        public volatile long total;
        @Nullable public volatile String mimeType;
        /** DownloadManager.STATUS_* と同じ値。 */
        public volatile int status = DownloadManager.STATUS_RUNNING;
        /** ウイルススキャンで見つかった検出名(見つかって中止した場合)。 */
        @Nullable public volatile String threat;
        /** ダウンロード元の URL(失敗した時の「再試行」に使う)。 */
        @Nullable volatile String sourceUrl;
        volatile boolean cancelled;

        Progress(String fileName, String filePath, long total) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.total = total;
        }
    }

    private AppDownloader() {}

    /** Android 9 以前で、公開フォルダへの書き込みに権限が必要か。 */
    public static boolean needsStoragePermission(@NonNull Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED;
    }

    /** ダウンロード画面用。Gecko 経由のダウンロードでなければ null。 */
    @Nullable
    public static Progress getProgress(long id) {
        return ACTIVE.get(id);
    }

    /** Gecko 経由のダウンロードを中止する。 */
    public static void cancel(long id) {
        Progress p = ACTIVE.get(id);
        if (p != null) p.cancelled = true;
    }

    // ------------------------------------------------------------------
    // ページからのダウンロード(GeckoView)
    // ------------------------------------------------------------------

    public static void saveResponse(@NonNull Activity activity, @NonNull WebResponse response) {
        saveResponse(activity, response, defaultDir(activity), null, null);
    }

    /** 書き込みが始まった・取りやめになったことを知りたい画面用(どちらも UI スレッドで呼ぶ)。 */
    public interface Observer {
        /** 書き込みを始めた。進行状況は {@link #getProgress(long)} で読む。 */
        void onStarted(long id);
        /** 確認でキャンセルされた・書き込みを始める前に失敗した。 */
        void onDiscarded();
    }

    /** {@link #saveResponse(Activity, WebResponse)} と同じ。開始・取りやめを observer に知らせる。 */
    public static void saveResponse(@NonNull Activity activity, @NonNull WebResponse response,
                                    @Nullable Observer observer) {
        saveResponse(activity, response, defaultDir(activity), null, observer);
    }

    /** ダウンロード設定で選んだ保存先(初期値は「ダウンロード」)。 */
    @NonNull
    static String defaultDir(@NonNull Context context) {
        String dir = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_DEFAULT_DIR, Environment.DIRECTORY_DOWNLOADS);
        return dir == null || dir.isEmpty() ? Environment.DIRECTORY_DOWNLOADS : dir;
    }

    /** ダウンロード設定で選べる保存先。 */
    @NonNull
    static String[] defaultDirChoices() {
        return new String[]{
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DOWNLOADS + "/AirSky",
                Environment.DIRECTORY_DOCUMENTS
        };
    }

    /**
     * @param publicDir   保存先(Environment.DIRECTORY_DOWNLOADS / DIRECTORY_PICTURES)
     * @param fallbackUrl レスポンスに URL が無い場合のファイル名推定用
     */
    private static void saveResponse(@NonNull Activity activity, @NonNull WebResponse response,
                                     @NonNull String publicDir, @Nullable String fallbackUrl,
                                     @Nullable Observer observer) {
        final Runnable cancel = () -> {
            discard(response);
            if (observer != null) observer.onDiscarded();
        };
        String mime = stripParams(header(response, "Content-Type"));
        String disposition = header(response, "Content-Disposition");
        String guessed = guessFileName(response.uri != null ? response.uri : fallbackUrl, disposition, mime);
        if (guessed.startsWith("downloadfile") && fallbackUrl != null) {
            // リダイレクト先から名前が決まらない場合は、元のリンクから決める
            guessed = guessFileName(fallbackUrl, disposition, mime);
        }
        final String fileName = guessed;
        // 保存時の種類はファイル名の拡張子を優先する(サーバーが application/octet-stream などを返すと、
        // 保存先が種類に合わせて .bin を付けたり拡張子を変えたりしてしまうため)
        String byName = guessMimeOrNull(fileName);
        final String mimeType = byName != null ? byName
                : (isGenericMime(mime) ? "application/octet-stream" : mime);
        final long total = parseLong(header(response, "Content-Length"));

        if (activity.isFinishing() || activity.isDestroyed()) {
            startSave(activity, response, fileName, mimeType, total, publicDir, observer);
            return;
        }

        // 確認の順: 有害な可能性のあるファイル → ダウンロードする前の確認(設定) → 同じ名前のファイル
        final String[] dir = {publicDir};
        Runnable checkDuplicate = () -> confirmDuplicate(activity, fileName, mimeType, dir[0],
                (name, chosen) -> startSave(activity, response, name, mimeType, total, chosen, observer),
                cancel);
        Runnable askFirst = () -> {
            boolean ask = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_ASK_BEFORE, false);
            if (!ask) {
                checkDuplicate.run();
                return;
            }
            confirmBeforeDownload(activity, fileName, mimeType, total, dir[0], chosen -> {
                dir[0] = chosen;
                checkDuplicate.run();
            }, cancel);
        };
        if (isPotentiallyHarmful(fileName, mimeType) || isPotentiallyHarmful(fileName, mime)) {
            new MaterialAlertDialogBuilder(activity)
                    .setIcon(R.drawable.ic_msg_warning)
                    .setTitle(Lang.t("有害なファイルの可能性があります"))
                    .setMessage(fileName + Lang.t(" のダウンロードを続行しますか？"))
                    .setNegativeButton(Lang.t("キャンセル"), (d, w) -> cancel.run())
                    .setPositiveButton(Lang.t("ダウンロードを続行"), (d, w) -> askFirst.run())
                    .setOnCancelListener(d -> cancel.run())
                    .show();
        } else {
            askFirst.run();
        }
    }

    /**
     * 「ダウンロードする前に確認する」がオンの時の確認。ファイル名・サイズ・保存先を見せ、
     * 保存先はその場で変えられる。
     */
    private static void confirmBeforeDownload(@NonNull Activity activity, @NonNull String fileName,
                                              @NonNull String mimeType, long total, @NonNull String publicDir,
                                              @NonNull FolderCallback onOk, @NonNull Runnable onCancel) {
        final boolean[] handled = {false};
        String size = total > 0 ? formatSizeDetailed(total) : Lang.t("サイズ不明");
        new MaterialAlertDialogBuilder(activity)
                .setIcon(R.drawable.ic_msg_download)
                .setTitle(Lang.t("ファイルをダウンロード"))
                .setMessage(fileName + "\n\n" + size + Lang.t(" ・ 保存先: ") + folderLabel(publicDir))
                .setNegativeButton(Lang.t("キャンセル"), null)
                .setNeutralButton(Lang.t("保存先を変更"), (d, w) -> {
                    handled[0] = true;
                    // 選び直したら(やめたら元の保存先で)もう一度この確認に戻る
                    chooseFolder(activity, mimeType, publicDir,
                            chosen -> confirmBeforeDownload(activity, fileName, mimeType, total, chosen, onOk, onCancel),
                            () -> confirmBeforeDownload(activity, fileName, mimeType, total, publicDir, onOk, onCancel));
                })
                .setPositiveButton(Lang.t("ダウンロード"), (d, w) -> {
                    handled[0] = true;
                    onOk.onFolder(publicDir);
                })
                .setOnDismissListener(d -> {
                    if (!handled[0]) onCancel.run();
                })
                .show();
    }

    /** ダウンロードを取りやめる(Gecko からのレスポンスを閉じて通信を止める)。 */
    private static void discard(@NonNull WebResponse response) {
        EXECUTOR.execute(() -> {
            try {
                if (response.body != null) response.body.close();
            } catch (Exception ignored) {
            }
        });
    }

    private interface TargetCallback {
        void onTarget(@NonNull String name, @NonNull String publicDir);
    }

    /**
     * 保存先に同じ名前のファイルがあれば「ファイルをもう一度ダウンロードしますか？」を出す。
     * ファイル名と保存先フォルダを変えられる。
     * 「次回から表示しない」にしていれば聞かずに "名前 (1).拡張子" で保存する。
     */
    private static void confirmDuplicate(@NonNull Activity activity, @NonNull String fileName,
                                         @NonNull String mimeType, @NonNull String publicDir,
                                         @NonNull TargetCallback onOk, @NonNull Runnable onCancel) {
        Context app = activity.getApplicationContext();
        if (!fileExists(app, fileName, publicDir)) {
            onOk.onTarget(fileName, publicDir);
            return;
        }
        String suggested = nextAvailableName(app, fileName, publicDir);
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SKIP_DUPLICATE_DIALOG, false)) {
            onOk.onTarget(suggested, publicDir);
            return;
        }

        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_download_again, null);
        EditText nameField = content.findViewById(R.id.downloadFileName);
        EditText folderField = content.findViewById(R.id.downloadFolder);
        CheckBox dontAsk = content.findViewById(R.id.downloadDontAsk);
        nameField.setText(suggested);
        final String[] dir = {publicDir};
        folderField.setText(folderLabel(publicDir));
        // 保存先を押すとフォルダを選べる。選んだフォルダに同じ名前が無ければ元の名前に戻す
        folderField.setOnClickListener(v -> chooseFolder(activity, mimeType, dir[0], chosen -> {
            dir[0] = chosen;
            folderField.setText(folderLabel(chosen));
            nameField.setText(fileExists(app, fileName, chosen) ? nextAvailableName(app, fileName, chosen) : fileName);
        }));

        final boolean[] handled = {false};
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(Lang.t("ファイルをもう一度ダウンロードしますか？"))
                .setView(content)
                .setNegativeButton(Lang.t("キャンセル"), null)
                .setPositiveButton(Lang.t("ダウンロード"), null)
                .setOnDismissListener(d -> {
                    if (!handled[0]) onCancel.run();
                })
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = sanitizeFileName(nameField.getText().toString());
            if (name.isEmpty()) {
                nameField.setError(Lang.t("ファイル名を入力してください"));
                return;
            }
            prefs.edit().putBoolean(KEY_SKIP_DUPLICATE_DIALOG, dontAsk.isChecked()).apply();
            handled[0] = true;
            dialog.dismiss();
            onOk.onTarget(name, dir[0]);
        }));
        dialog.show();
    }

    private interface FolderCallback {
        void onFolder(@NonNull String publicDir);
    }

    /**
     * 保存先フォルダを選ぶ。ファイルの種類ごとに Android が保存を許しているフォルダだけを出す
     * (例: 画像は「ピクチャ」「DCIM」にも保存できる)。「新しいフォルダ」で中にフォルダも作れる。
     */
    private static void chooseFolder(@NonNull Activity activity, @NonNull String mime,
                                     @NonNull String current, @NonNull FolderCallback cb) {
        chooseFolder(activity, mime, current, cb, null);
    }

    /** @param onCancel 何も選ばずに閉じた時(null なら何もしない) */
    private static void chooseFolder(@NonNull Activity activity, @NonNull String mime,
                                     @NonNull String current, @NonNull FolderCallback cb,
                                     @Nullable Runnable onCancel) {
        java.util.List<String> dirs = new java.util.ArrayList<>();
        dirs.add(Environment.DIRECTORY_DOWNLOADS);
        dirs.add(Environment.DIRECTORY_DOWNLOADS + "/AirSky");
        dirs.add(Environment.DIRECTORY_DOCUMENTS);
        if (mime.startsWith("image/")) {
            dirs.add(Environment.DIRECTORY_PICTURES);
            dirs.add(Environment.DIRECTORY_DCIM);
        } else if (mime.startsWith("video/")) {
            dirs.add(Environment.DIRECTORY_MOVIES);
            dirs.add(Environment.DIRECTORY_DCIM);
        } else if (mime.startsWith("audio/")) {
            dirs.add(Environment.DIRECTORY_MUSIC);
        }
        if (!dirs.contains(current)) dirs.add(0, current);

        String[] labels = new String[dirs.size() + 1];
        int checked = 0;
        for (int i = 0; i < dirs.size(); i++) {
            labels[i] = folderLabel(dirs.get(i));
            if (dirs.get(i).equals(current)) checked = i;
        }
        labels[dirs.size()] = Lang.t("新しいフォルダ...");

        final boolean[] chosen = {false};
        new MaterialAlertDialogBuilder(activity)
                .setTitle(Lang.t("保存先"))
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    chosen[0] = true;
                    d.dismiss();
                    if (which < dirs.size()) {
                        cb.onFolder(dirs.get(which));
                    } else {
                        createFolder(activity, current, cb, onCancel);
                    }
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .setOnDismissListener(d -> {
                    if (!chosen[0] && onCancel != null) onCancel.run();
                })
                .show();
    }

    /** 今の保存先の中に新しいフォルダを作って選ぶ。 */
    private static void createFolder(@NonNull Activity activity, @NonNull String parent,
                                     @NonNull FolderCallback cb, @Nullable Runnable onCancel) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(Lang.t("フォルダ名"));
        android.widget.FrameLayout box = new android.widget.FrameLayout(activity);
        int pad = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 3, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(folderLabel(parent) + Lang.t(" に新しいフォルダ"))
                .setView(box)
                .setNegativeButton(Lang.t("キャンセル"), (d, w) -> {
                    if (onCancel != null) onCancel.run();
                })
                .setPositiveButton(Lang.t("作成"), (d, w) -> {
                    String name = sanitizeFileName(input.getText().toString()).replace(".", "_");
                    if (!name.isEmpty()) cb.onFolder(parent + "/" + name);
                    else if (onCancel != null) onCancel.run();
                })
                .setOnCancelListener(d -> {
                    if (onCancel != null) onCancel.run();
                })
                .show();
    }

    /** "Download/AirSky" → "ダウンロード/AirSky"。 */
    @NonNull
    static String folderLabel(@NonNull String publicDir) {
        int slash = publicDir.indexOf('/');
        String top = slash >= 0 ? publicDir.substring(0, slash) : publicDir;
        String rest = slash >= 0 ? publicDir.substring(slash) : "";
        String name;
        if (Environment.DIRECTORY_DOWNLOADS.equals(top)) name = Lang.t("ダウンロード");
        else if (Environment.DIRECTORY_DOCUMENTS.equals(top)) name = Lang.t("ドキュメント");
        else if (Environment.DIRECTORY_PICTURES.equals(top)) name = Lang.t("ピクチャ");
        else if (Environment.DIRECTORY_DCIM.equals(top)) name = Lang.t("カメラ (DCIM)");
        else if (Environment.DIRECTORY_MOVIES.equals(top)) name = Lang.t("ムービー");
        else if (Environment.DIRECTORY_MUSIC.equals(top)) name = Lang.t("ミュージック");
        else name = top;
        return name + rest;
    }

    /** Android 10 以降で、保存先フォルダとファイルの種類に合う MediaStore のコレクション。 */
    private static Uri collectionFor(@NonNull String publicDir, @Nullable String mime) {
        int slash = publicDir.indexOf('/');
        String top = slash >= 0 ? publicDir.substring(0, slash) : publicDir;
        String m = mime != null ? mime : "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Environment.DIRECTORY_DOWNLOADS.equals(top)) return MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            if (m.startsWith("image/") && (Environment.DIRECTORY_PICTURES.equals(top)
                    || Environment.DIRECTORY_DCIM.equals(top))) {
                return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            }
            if (m.startsWith("video/") && (Environment.DIRECTORY_MOVIES.equals(top)
                    || Environment.DIRECTORY_DCIM.equals(top))) {
                return MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            }
            if (m.startsWith("audio/") && Environment.DIRECTORY_MUSIC.equals(top)) {
                return MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            }
        }
        return MediaStore.Files.getContentUri("external");
    }

    /** 書き込みを始める。進行状況は上部の案内・通知・ダウンロード画面に出す。 */
    private static void startSave(@NonNull Activity activity, @NonNull WebResponse response,
                                  @NonNull String fileName, @NonNull String mimeType, long total,
                                  @NonNull String publicDir, @Nullable Observer observer) {
        final Context app = activity.getApplicationContext();
        final long id = -System.currentTimeMillis();
        showStartedMessage(activity);

        EXECUTOR.execute(() -> {
            Target target = null;
            Progress progress = null;
            try (InputStream in = response.body) {
                if (in == null) throw new IllegalStateException(Lang.t("レスポンスが空です"));
                if (response.statusCode >= 400) {
                    throw new IllegalStateException("HTTP " + response.statusCode);
                }
                target = openTarget(app, fileName, mimeType, publicDir);
                progress = new Progress(target.displayName, target.path, total);
                progress.contentUri = target.mediaUri != null ? target.mediaUri.toString() : null;
                progress.mimeType = mimeType;
                progress.sourceUrl = response.uri;
                ACTIVE.put(id, progress);
                if (observer != null) activity.runOnUiThread(() -> observer.onStarted(id));
                // 開始した時点で一覧に載せ、ダウンロード画面から進行状況を確認できるようにする
                DownloadHistoryManager.addDownloadHistory(app, id, target.displayName, target.path,
                        progress.contentUri, response.uri);

                // 書き込みながらウイルススキャンする(見つかった時点で中止する)
                VirusScanner.Session scan = VirusScanner.isEnabled(app) ? new VirusScanner.Session() : null;
                byte[] buf = new byte[64 * 1024];
                long lastNotify = 0;
                int n;
                try (OutputStream out = target.out) {
                    while ((n = in.read(buf)) != -1) {
                        if (progress.cancelled) throw new InterruptedException("cancelled");
                        if (scan != null) {
                            String threat = scan.update(buf, 0, n);
                            if (threat != null) throw new ThreatFoundException(threat);
                        }
                        out.write(buf, 0, n);
                        progress.downloaded += n;
                        long now = System.currentTimeMillis();
                        if (now - lastNotify > 500) {
                            lastNotify = now;
                            notifyProgress(app, id, progress);
                        }
                    }
                    out.flush();
                }
                if (scan != null) {
                    // ファイル全体のハッシュの照合と、ZIP 形式なら中のファイルも調べる(公開する前に)
                    String threat = scan.finish();
                    if (threat == null && VirusScanner.isArchive(target.displayName)) {
                        try (InputStream saved = target.openInput(app)) {
                            if (saved != null) threat = VirusScanner.scanArchive(saved);
                        }
                    }
                    if (threat != null) throw new ThreatFoundException(threat);
                }
                target.finish(app);
                // 書き込み中(IS_PENDING)の名前・パスは完了時に変わるので、確定した値で履歴を更新する。
                // これをしないと一覧が存在しない ".pending-..." のパスを指し「見つからない」になる。
                progress.fileName = target.displayName;
                progress.filePath = target.path;
                DownloadHistoryManager.updateDownloadHistory(app, id, target.displayName, target.path,
                        progress.contentUri);
                progress.total = progress.downloaded;
                progress.status = DownloadManager.STATUS_SUCCESSFUL;
                notifyDone(app, id, progress, true);
                final Progress done = progress;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        Toast.makeText(app, Lang.t("ダウンロードが完了しました: ") + done.fileName, Toast.LENGTH_LONG).show();
                        return;
                    }
                    TopMessage.make(activity, R.drawable.ic_msg_download, Lang.t("ダウンロードが完了しました"))
                            .setSubtitle(done.fileName + " • " + formatSizeDetailed(done.total))
                            .setAction(Lang.t("開く"), () -> {
                                Intent open = openFileIntent(app, done);
                                try {
                                    activity.startActivity(open != null ? open
                                            : new Intent(activity, DownloadListActivity.class));
                                } catch (Exception ex) {
                                    Toast.makeText(app, Lang.t("このファイルを開けるアプリがありません"), Toast.LENGTH_SHORT).show();
                                }
                            })
                            .show();
                });
            } catch (Exception e) {
                // 書きかけ・ウイルスの見つかったファイルは消す
                if (target != null) target.abort(app);
                if (progress == null && observer != null) activity.runOnUiThread(observer::onDiscarded);
                boolean cancelled = progress != null && progress.cancelled;
                if (e instanceof ThreatFoundException) {
                    onThreatFound(activity, id, progress, ((ThreatFoundException) e).threat);
                    return;
                }
                if (progress != null) {
                    progress.status = DownloadManager.STATUS_FAILED;
                    if (cancelled) {
                        NotificationManagerCompat.from(app).cancel((int) id);
                        updateSummary(app);
                    } else {
                        notifyDone(app, id, progress, false);
                    }
                }
                if (cancelled) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(app, Lang.t("ダウンロードをキャンセルしました"), Toast.LENGTH_LONG).show());
                    return;
                }
                final String reason = e.getMessage();
                final String name = progress != null ? progress.fileName : fileName;
                final String url = response.uri;
                final long failedId = progress != null ? id : 0;
                activity.runOnUiThread(() -> showFailedMessage(activity, failedId, name, url, reason));
            }
        });
    }

    /** 「ダウンロードに失敗しました」を画面上部に出す。もう一度取得できる時は「再試行」ボタンを付ける。 */
    private static void showFailedMessage(@NonNull Activity activity, long id, @NonNull String name,
                                          @Nullable String url, @Nullable String reason) {
        Context app = activity.getApplicationContext();
        String detail = Lang.t("ダウンロードに失敗しました") + (reason != null ? ": " + reason : "");
        if (activity.isFinishing() || activity.isDestroyed()) {
            Toast.makeText(app, detail, Toast.LENGTH_LONG).show();
            return;
        }
        TopMessage m = TopMessage.make(activity, R.drawable.ic_msg_warning, Lang.t("ダウンロードに失敗しました"))
                .setSubtitle(reason != null ? name + " • " + reason : name);
        if (canRetry(url)) {
            boolean privateMode = activity instanceof MainActivity && ((MainActivity) activity).isSecretMode();
            m.setAction(Lang.t("再試行"), () -> retry(activity, id, url, privateMode));
        }
        m.show();
    }

    static boolean canRetry(@Nullable String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * 失敗したダウンロードをもう一度行う。失敗した方の履歴と通知は消す(一覧に同じものが 2 つ並ばないように)。
     * oldId が 0 の時は履歴に載る前に失敗したもの。
     */
    static boolean retry(@NonNull Activity activity, long oldId, @Nullable String url, boolean privateMode) {
        return retry(activity, oldId, url, privateMode, null);
    }

    static boolean retry(@NonNull Activity activity, long oldId, @Nullable String url, boolean privateMode,
                         @Nullable Observer observer) {
        // 失敗した方の履歴は、やり直しの書き込みが始まってから消す。
        // 先に消すと、やり直しも書き込む前に失敗した時(接続できない・HTTP エラー)に履歴から消えてしまう
        Context app = activity.getApplicationContext();
        Observer replace = new Observer() {
            @Override
            public void onStarted(long id) {
                if (oldId != 0 && oldId != id) {
                    ACTIVE.remove(oldId);
                    DownloadHistoryManager.removeDownloadHistory(app, oldId);
                    NotificationManagerCompat.from(app).cancel((int) oldId);
                    updateSummary(app);
                }
                if (observer != null) observer.onStarted(id);
            }

            @Override
            public void onDiscarded() {
                if (observer != null) observer.onDiscarded();
            }
        };
        if (!canRetry(url) || !downloadUrl(activity, url, null, false, privateMode, replace)) {
            Toast.makeText(activity, Lang.t("このダウンロードは再試行できません"), Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /** ウイルススキャンで不正なデータが見つかった。 */
    private static final class ThreatFoundException extends Exception {
        final String threat;

        ThreatFoundException(String threat) {
            super(Lang.t("ウイルスを検出しました: ") + threat);
            this.threat = threat;
        }
    }

    /** ウイルスが見つかってダウンロードを中止した(ファイルは削除済み)ことを記録して知らせる。 */
    private static void onThreatFound(@NonNull Activity activity, long id, @Nullable Progress progress,
                                      @NonNull String threat) {
        final Context app = activity.getApplicationContext();
        String name = progress != null ? progress.fileName : Lang.t("ファイル");
        if (progress != null) {
            progress.threat = threat;
            progress.status = DownloadManager.STATUS_FAILED;
            notifyDone(app, id, progress, false);
        }
        DownloadHistoryManager.markThreat(app, id, threat);
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                Toast.makeText(app, Lang.t("ウイルスが検出されたため、") + name + Lang.t(" のダウンロードを中止しました"),
                        Toast.LENGTH_LONG).show();
                return;
            }
            TopMessage.make(activity, R.drawable.ic_msg_warning, Lang.t("ウイルスが検出されたため、ダウンロードを中止しました"))
                    .setSubtitle(name + " • " + threat)
                    .setAction(Lang.t("詳細"), () -> activity.startActivity(new Intent(activity, DownloadListActivity.class)))
                    .show();
        });
    }

    /** 「1 件のファイルをダウンロードしています...」を画面上部に出す(Chrome と同じ)。 */
    private static void showStartedMessage(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        requestNotificationPermission(activity);
        int running = 1;
        for (Progress p : ACTIVE.values()) {
            if (p.status == DownloadManager.STATUS_RUNNING) running++;
        }
        TopMessage.make(activity, R.drawable.ic_msg_download, running + Lang.t(" 件のファイルをダウンロードしています..."))
                .setAnimation(R.raw.lottie_download)
                .setSubtitle(canNotify(activity)
                        ? Lang.t("ダウンロード ステータスは通知で表示されます")
                        : Lang.t("ダウンロード ステータスはダウンロード画面で確認できます"))
                .setAction(Lang.t("詳細"), () -> activity.startActivity(new Intent(activity, DownloadListActivity.class)))
                .show();
    }

    /** Android 13 以降で通知の許可がまだなら、最初のダウンロードの時に 1 回だけ尋ねる。 */
    private static void requestNotificationPermission(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT < 33 || canNotify(activity)) return;
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)) return;
        prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply();
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
    }

    // ------------------------------------------------------------------
    // 確認ダイアログ用の判定
    // ------------------------------------------------------------------

    /** 端末やデータを壊すおそれがある種類のファイル(Chrome が警告を出すものに相当)。 */
    private static final Set<String> HARMFUL_EXTENSIONS = new HashSet<>(Arrays.asList(
            "apk", "apks", "apkm", "xapk", "aab", "dex", "so",
            "exe", "msi", "msix", "msp", "com", "scr", "pif", "cpl", "dll", "sys", "lnk",
            "bat", "cmd", "ps1", "psm1", "vbs", "vbe", "js", "jse", "wsf", "wsh", "hta", "reg", "inf",
            "jar", "jnlp", "sh", "bash", "command", "run", "bin", "elf",
            "dmg", "pkg", "app", "deb", "rpm", "appimage", "msc", "gadget", "application"));

    private static final Set<String> HARMFUL_MIME_TYPES = new HashSet<>(Arrays.asList(
            "application/vnd.android.package-archive",
            "application/x-msdownload", "application/x-msdos-program", "application/x-msi",
            "application/x-ms-installer", "application/java-archive", "application/x-sh",
            "application/x-executable", "application/x-apple-diskimage"));

    static boolean isPotentiallyHarmful(@NonNull String fileName, @Nullable String mime) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && HARMFUL_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT))) {
            return true;
        }
        return mime != null && HARMFUL_MIME_TYPES.contains(mime.toLowerCase(Locale.ROOT));
    }

    /** 保存先フォルダに同じ名前のファイルがあるか。 */
    private static boolean fileExists(@NonNull Context ctx, @NonNull String name, @NonNull String publicDir) {
        if (new File(Environment.getExternalStoragePublicDirectory(publicDir), name).exists()) return true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        // Android 10 以降はファイルパスから見えないことがあるので MediaStore でも探す
        try (Cursor c = ctx.getContentResolver().query(MediaStore.Files.getContentUri("external"),
                new String[]{MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                        + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                new String[]{name, publicDir + "/"}, null)) {
            return c != null && c.getCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** "name (1).ext", "name (2).ext" ... のうち、まだ無い名前。 */
    private static String nextAvailableName(@NonNull Context ctx, @NonNull String name, @NonNull String publicDir) {
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 1; i < 1000; i++) {
            String candidate = base + " (" + i + ")" + ext;
            if (!fileExists(ctx, candidate, publicDir)) return candidate;
        }
        return base + "_" + System.currentTimeMillis() + ext;
    }

    private static String sanitizeFileName(@NonNull String name) {
        return name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    // ------------------------------------------------------------------
    // 長押しメニュー(リンク・画像)
    // ------------------------------------------------------------------

    /**
     * 長押しメニューの「リンクをダウンロード」「画像を保存」。
     * ブラウザと同じ Gecko の通信(Cookie・TLS)で取得し、ページからのダウンロードと同じく
     * 通知とダウンロード画面に進行状況を出す。http/https 以外(blob: など)は false を返す。
     */
    public static boolean downloadUrl(@NonNull Activity activity, @NonNull String url,
                                      @Nullable String userAgent, boolean image, boolean privateMode) {
        return downloadUrl(activity, url, userAgent, image, privateMode, null);
    }

    static boolean downloadUrl(@NonNull Activity activity, @NonNull String url, @Nullable String userAgent,
                               boolean image, boolean privateMode, @Nullable Observer observer) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        WebRequest.Builder req = new WebRequest.Builder(url);
        if (userAgent != null) req.addHeader("User-Agent", userAgent);
        int flags = privateMode ? GeckoWebExecutor.FETCH_FLAGS_PRIVATE : GeckoWebExecutor.FETCH_FLAGS_NONE;
        final String dir = image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS;
        new GeckoWebExecutor(GeckoEngine.get(activity).getRuntime())
                .fetch(req.build(), flags)
                .accept(response -> {
                    if (response != null) {
                        activity.runOnUiThread(() -> saveResponse(activity, response, dir, url, observer));
                    } else if (observer != null) {
                        activity.runOnUiThread(observer::onDiscarded);
                    }
                }, e -> activity.runOnUiThread(() -> {
                    Toast.makeText(activity,
                            (image ? Lang.t("画像を保存できませんでした") : Lang.t("ダウンロードできませんでした"))
                                    + (e != null && e.getMessage() != null ? ": " + e.getMessage() : ""),
                            Toast.LENGTH_LONG).show();
                    // 書き込みを始めずに終わったことを知らせる(再試行なら元の履歴を残したまま一覧を戻す)
                    if (observer != null) observer.onDiscarded();
                }));
        return true;
    }

    /** data: URL の画像を「ピクチャ」フォルダに保存する。 */
    public static void saveDataUrlImage(@NonNull Activity activity, @NonNull String dataUrl) {
        final Context app = activity.getApplicationContext();
        EXECUTOR.execute(() -> {
            String message;
            try {
                int comma = dataUrl.indexOf(',');
                if (comma < 0) throw new IllegalArgumentException(Lang.t("無効なデータです"));
                String meta = dataUrl.substring(5, comma);
                String mime = meta.contains(";") ? meta.substring(0, meta.indexOf(';')) : meta;
                if (mime.isEmpty()) mime = "image/png";
                byte[] data = meta.contains("base64")
                        ? Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
                        : Uri.decode(dataUrl.substring(comma + 1)).getBytes("UTF-8");
                String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                String fileName = "saved_image_" + System.currentTimeMillis() + (ext != null ? "." + ext : ".png");

                Target target = openTarget(app, fileName, mime, Environment.DIRECTORY_PICTURES);
                try (OutputStream out = target.out) {
                    out.write(data);
                    out.flush();
                }
                target.finish(app);
                message = Lang.t("画像を保存しました\n保存先: ") + target.path;
            } catch (Exception e) {
                message = Lang.t("画像の保存に失敗しました") + (e.getMessage() != null ? ": " + e.getMessage() : "");
            }
            final String msg = message;
            activity.runOnUiThread(() -> Toast.makeText(app, msg, Toast.LENGTH_LONG).show());
        });
    }

    // ------------------------------------------------------------------
    // 書き込み先
    // ------------------------------------------------------------------

    /** 書き込み先のファイル(Android 10 以降は MediaStore 経由)。 */
    private static final class Target {
        OutputStream out;
        String path;
        String displayName;
        @Nullable Uri mediaUri;
        @Nullable File file;

        void finish(Context ctx) {
            if (mediaUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = ctx.getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(mediaUri, v, null, null);
                // 公開後の実際の名前・パスを取り直す(重複時は "name (1).ext" などに変わることもある)
                displayName = queryString(resolver, mediaUri, MediaStore.MediaColumns.DISPLAY_NAME, displayName);
                //noinspection deprecation
                path = queryString(resolver, mediaUri, MediaStore.MediaColumns.DATA, path);
            }
        }

        /** 書き込んだ内容を読み直す(公開前のウイルススキャン用)。 */
        @Nullable
        InputStream openInput(Context ctx) throws java.io.IOException {
            if (mediaUri != null) return ctx.getContentResolver().openInputStream(mediaUri);
            return file != null ? new java.io.FileInputStream(file) : null;
        }

        void abort(Context ctx) {
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            if (mediaUri != null) {
                try { ctx.getContentResolver().delete(mediaUri, null, null); } catch (Exception ignored) {}
            } else if (file != null) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private static Target openTarget(Context ctx, String fileName, String mime, String publicDir) throws Exception {
        Target t = new Target();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = ctx.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, publicDir);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = resolver.insert(collectionFor(publicDir, mime), values);
            if (uri == null) throw new IllegalStateException(Lang.t("保存先を作成できません"));
            OutputStream out = resolver.openOutputStream(uri);
            if (out == null) {
                resolver.delete(uri, null, null);
                throw new IllegalStateException(Lang.t("保存先を開けません"));
            }
            t.mediaUri = uri;
            t.out = out;
            t.displayName = queryString(resolver, uri, MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            //noinspection deprecation
            t.path = queryString(resolver, uri, MediaStore.MediaColumns.DATA,
                    new File(Environment.getExternalStoragePublicDirectory(publicDir), t.displayName).getAbsolutePath());
            return t;
        }

        File dir = Environment.getExternalStoragePublicDirectory(publicDir);
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException(Lang.t("保存先フォルダを作成できません"));
        File file = uniqueFile(dir, fileName);
        t.file = file;
        t.out = new FileOutputStream(file);
        t.path = file.getAbsolutePath();
        t.displayName = file.getName();
        return t;
    }

    private static String queryString(ContentResolver resolver, Uri uri, String column, String fallback) {
        try (Cursor c = resolver.query(uri, new String[]{column}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) return c.getString(0);
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 1; ; i++) {
            f = new File(dir, base + " (" + i + ")" + ext);
            if (!f.exists()) return f;
        }
    }

    // ------------------------------------------------------------------
    // 通知
    // ------------------------------------------------------------------

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID, Lang.t("ダウンロード"), NotificationManager.IMPORTANCE_LOW));
            }
        }
    }

    private static PendingIntent downloadListIntent(Context ctx) {
        Intent open = new Intent(ctx, DownloadListActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** ダウンロードの通知は 1 つのグループにまとめる(Chrome と同じ)。 */
    private static NotificationCompat.Builder baseNotification(Context ctx) {
        ensureChannel(ctx);
        return new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentIntent(downloadListIntent(ctx))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setGroup(GROUP_KEY)
                .setShowWhen(true);
    }

    static boolean canNotify(Context ctx) {
        return Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("MissingPermission")
    private static void notifyProgress(Context ctx, long id, Progress p) {
        if (!canNotify(ctx)) return;
        int percent = p.total > 0 ? (int) (p.downloaded * 100 / p.total) : 0;
        String text = p.total > 0
                ? percent + "% • " + formatSizeDetailed(p.downloaded) + " / " + formatSizeDetailed(p.total)
                : formatSizeDetailed(p.downloaded);
        Intent cancel = new Intent(ctx, DownloadActionReceiver.class)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_ID, id);
        PendingIntent cancelPi = PendingIntent.getBroadcast(ctx, (int) id, cancel,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = baseNotification(ctx)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(p.fileName)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, percent, p.total <= 0)
                .addAction(0, Lang.t("キャンセル"), cancelPi);
        NotificationManagerCompat.from(ctx).notify((int) id, b.build());
        updateSummary(ctx);
    }

    @SuppressWarnings("MissingPermission")
    private static void notifyDone(Context ctx, long id, Progress p, boolean success) {
        if (!canNotify(ctx)) return;
        NotificationCompat.Builder b = baseNotification(ctx)
                .setSmallIcon(success ? android.R.drawable.stat_sys_download_done
                        : android.R.drawable.stat_notify_error)
                .setContentTitle(p.fileName)
                .setContentText(success
                        ? Lang.t("ダウンロードが完了しました• ") + formatSizeDetailed(p.total)
                        : p.threat != null
                        ? Lang.t("ウイルスが検出されたため中止しました(") + p.threat + ")"
                        : Lang.t("ダウンロードに失敗しました"))
                .setCategory(success ? NotificationCompat.CATEGORY_STATUS : NotificationCompat.CATEGORY_ERROR)
                .setAutoCancel(true);
        if (success) {
            // 押したらファイルを開く(開けるアプリが無い種類はダウンロード画面)
            Intent open = openFileIntent(ctx, p);
            if (open != null) {
                b.setContentIntent(PendingIntent.getActivity(ctx, (int) id, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
            Bitmap thumb = loadThumbnail(ctx, p);
            if (thumb != null) b.setLargeIcon(thumb);
        } else if (p.threat == null && canRetry(p.sourceUrl)) {
            // 失敗した: 「再試行」でダウンロード画面を開き、そこからやり直す
            Intent retry = new Intent(ctx, DownloadListActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_RETRY_ID, id)
                    .putExtra(EXTRA_RETRY_URL, p.sourceUrl);
            b.addAction(0, Lang.t("再試行"), PendingIntent.getActivity(ctx, (int) id ^ 0x5245, retry,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        NotificationManagerCompat.from(ctx).notify((int) id, b.build());
        updateSummary(ctx);
    }

    /** グループのまとめ通知(展開前に表示されるもの)。 */
    @SuppressWarnings("MissingPermission")
    static void updateSummary(Context ctx) {
        if (!canNotify(ctx)) return;
        int running = 0;
        for (Progress p : ACTIVE.values()) {
            if (p.status == DownloadManager.STATUS_RUNNING) running++;
        }
        NotificationCompat.Builder b = baseNotification(ctx)
                .setSmallIcon(running > 0 ? android.R.drawable.stat_sys_download
                        : android.R.drawable.stat_sys_download_done)
                .setContentTitle(Lang.t("ダウンロード"))
                .setContentText(running > 0 ? running + Lang.t(" 件のファイルをダウンロードしています") : Lang.t("ダウンロードが完了しました"))
                .setGroupSummary(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true);
        NotificationManagerCompat.from(ctx).notify(SUMMARY_ID, b.build());
    }

    /** ダウンロードしたファイルを開く Intent。開けるアプリが無ければ null。 */
    @Nullable
    static Intent openFileIntent(@NonNull Context ctx, @NonNull Progress p) {
        Uri uri = null;
        if (p.contentUri != null) {
            uri = Uri.parse(p.contentUri);
        } else if (p.filePath != null) {
            try {
                uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", new File(p.filePath));
            } catch (Exception ignored) {
            }
        }
        if (uri == null) return null;
        String mime = p.mimeType != null ? p.mimeType : guessMime(p.fileName);
        Intent view = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        return view.resolveActivity(ctx.getPackageManager()) != null ? view : null;
    }

    /** 画像・動画なら通知に出す縮小画像。 */
    @Nullable
    private static Bitmap loadThumbnail(@NonNull Context ctx, @NonNull Progress p) {
        String mime = p.mimeType != null ? p.mimeType : guessMime(p.fileName);
        if (!mime.startsWith("image/") && !mime.startsWith("video/")) return null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && p.contentUri != null) {
                return ctx.getContentResolver().loadThumbnail(Uri.parse(p.contentUri), new Size(256, 256), null);
            }
            if (mime.startsWith("image/") && p.filePath != null) {
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFile(p.filePath, o);
                int sample = 1;
                while (o.outWidth / (sample * 2) >= 256 && o.outHeight / (sample * 2) >= 256) sample *= 2;
                o = new android.graphics.BitmapFactory.Options();
                o.inSampleSize = sample;
                return android.graphics.BitmapFactory.decodeFile(p.filePath, o);
            }
        } catch (Exception | OutOfMemoryError ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------
    // ユーティリティ
    // ------------------------------------------------------------------

    @Nullable
    private static String header(WebResponse response, String name) {
        if (response.headers == null) return null;
        for (Map.Entry<String, String> e : response.headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    @Nullable
    private static String stripParams(@Nullable String mime) {
        if (mime == null) return null;
        int semi = mime.indexOf(';');
        return (semi >= 0 ? mime.substring(0, semi) : mime).trim();
    }

    private static long parseLong(@Nullable String s) {
        try {
            return s != null ? Long.parseLong(s.trim()) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * ファイル名を決める。必ず空でない、ファイル名として安全な文字列を返す。
     * 名前が分からない時は "downloadfile" で始まる名前を返す。
     *
     * <p>以前は URLUtil.guessFileName() を使っていたが、あれは Content-Disposition の
     * filename*=UTF-8''... や引用符の無い filename= を読めず、さらに種類が application/octet-stream だと
     * 拡張子を .bin に置き換えてしまうため(サイトによって .bin で保存される原因)、自前で決める。</p>
     */
    @NonNull
    static String guessFileName(@Nullable String url, @Nullable String disposition, @Nullable String mime) {
        String name = fileNameFromDisposition(disposition);
        boolean fromHeader = name != null;
        if (name == null && url != null && (url.startsWith("blob:") || url.startsWith("data:"))) {
            String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new java.util.Date());
            name = "download_" + ts;
        }
        if (name == null) name = fileNameFromUrl(url);
        if (name == null) name = "downloadfile";

        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        // 先頭の "." は隠しファイルになるので外す
        while (name.startsWith(".")) name = name.substring(1);
        if (name.isEmpty()) name = "downloadfile";

        String mimeExt = isGenericMime(mime) ? null
                : MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.trim().toLowerCase(Locale.ROOT));
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : null;
        if (ext == null) {
            // 拡張子が無ければ、種類が分かる時だけ付ける(application/octet-stream なら付けない)
            if (mimeExt != null) name = name + "." + mimeExt;
        } else if (!fromHeader && mimeExt != null && !ext.equals(mimeExt) && !HARMFUL_EXTENSIONS.contains(ext)) {
            // .xapk / .apks などは application/zip で配られることが多い。.zip に直すと有害なファイルの警告が出なくなるので残す
            // URL から取った名前(download.php など)は、拡張子が種類と大きく違う時だけ直す。
            // .csv に text/plain、.jpeg に image/jpeg のように大きな分類が同じなら元の拡張子を残す
            String extMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (extMime == null || !topLevelType(extMime).equals(topLevelType(mime))) {
                name = name.substring(0, dot) + "." + mimeExt;
            }
        }
        if (name.length() > 180) {
            // 長すぎる名前は保存できないことがあるので、拡張子を残して縮める
            int d = name.lastIndexOf('.');
            String tail = d > 0 && name.length() - d <= 12 ? name.substring(d) : "";
            name = name.substring(0, 180 - tail.length()) + tail;
        }
        return name;
    }

    /** 種類として役に立たない(中身が何か分からない)Content-Type か。 */
    private static boolean isGenericMime(@Nullable String mime) {
        if (mime == null) return true;
        String m = mime.trim().toLowerCase(Locale.ROOT);
        return m.isEmpty() || m.endsWith("/*")
                || m.equals("application/octet-stream") || m.equals("binary/octet-stream")
                || m.equals("application/binary") || m.equals("application/unknown")
                || m.equals("application/x-unknown") || m.equals("application/force-download")
                || m.equals("application/x-download") || m.equals("application/download");
    }

    @NonNull
    private static String topLevelType(@NonNull String mime) {
        int slash = mime.indexOf('/');
        return (slash > 0 ? mime.substring(0, slash) : mime).trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Content-Disposition からファイル名を取り出す。RFC 6266 / 5987 の filename*=UTF-8''%E3%81%82.pdf を優先し、
     * 無ければ filename="..." / filename=... を読む。
     */
    @Nullable
    static String fileNameFromDisposition(@Nullable String disposition) {
        if (disposition == null || disposition.isEmpty()) return null;
        String extended = null;
        String plain = null;
        for (String part : splitParams(disposition)) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = part.substring(eq + 1).trim();
            if (key.equals("filename*")) {
                extended = decodeExtValue(unquote(value));
            } else if (key.equals("filename")) {
                plain = unquote(value);
            }
        }
        String name;
        if (extended != null && !extended.trim().isEmpty()) {
            name = extended; // filename* はすでに復号済み
        } else if (plain != null) {
            name = plain;
            if (name.matches(".*%[0-9A-Fa-f]{2}.*")) {
                // 一部のサイトは filename= にパーセントエンコードした UTF-8 を入れてくる
                try {
                    name = java.net.URLDecoder.decode(name.replace("+", "%2B"), "UTF-8");
                } catch (Exception ignored) {
                }
            } else {
                name = fixMojibake(name);
            }
        } else {
            return null;
        }
        // パスが含まれていれば最後の部分だけ使う
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.trim();
        return name.isEmpty() ? null : name;
    }

    /** ; で区切る(引用符の中の ; は区切らない)。 */
    @NonNull
    private static List<String> splitParams(@NonNull String header) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < header.length(); i++) {
            char c = header.charAt(i);
            if (c == '\\' && quoted && i + 1 < header.length()) {
                cur.append(c).append(header.charAt(++i));
                continue;
            }
            if (c == '"') quoted = !quoted;
            if (c == ';' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    @NonNull
    private static String unquote(@NonNull String value) {
        String v = value.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1).replaceAll("\\\\(.)", "$1");
        }
        return v;
    }

    /** RFC 5987 の charset'lang'%XX... を復号する。読めなければ null。 */
    @Nullable
    private static String decodeExtValue(@NonNull String value) {
        int first = value.indexOf('\'');
        int second = first >= 0 ? value.indexOf('\'', first + 1) : -1;
        String charset = "UTF-8";
        String encoded = value;
        if (first >= 0 && second > first) {
            if (first > 0) charset = value.substring(0, first);
            encoded = value.substring(second + 1);
        }
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            for (int i = 0; i < encoded.length(); i++) {
                char c = encoded.charAt(i);
                if (c == '%' && i + 2 < encoded.length()) {
                    bytes.write(Integer.parseInt(encoded.substring(i + 1, i + 3), 16));
                    i += 2;
                } else {
                    byte[] b = String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    bytes.write(b, 0, b.length);
                }
            }
            return new String(bytes.toByteArray(), java.nio.charset.Charset.forName(charset));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * ヘッダーの値は ISO-8859-1 として渡されることがあり、UTF-8 の日本語名が文字化けする。
     * UTF-8 として読み直せる時だけ読み直す。
     */
    @NonNull
    private static String fixMojibake(@NonNull String name) {
        boolean high = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c > 0xFF) return name; // すでに正しい文字(Latin-1 の範囲外)を含む
            if (c >= 0x80) high = true;
        }
        if (!high) return name;
        byte[] raw = name.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        try {
            return java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(raw)).toString();
        } catch (Exception e) {
            return name;
        }
    }

    /** URL のパスの最後の部分(パーセントエンコードを戻したもの)。無ければ null。 */
    @Nullable
    private static String fileNameFromUrl(@Nullable String url) {
        if (url == null || url.startsWith("blob:") || url.startsWith("data:")) return null;
        try {
            String path = Uri.parse(url).getPath(); // getPath() は %XX を戻した値
            if (path == null) return null;
            int slash = path.lastIndexOf('/');
            String last = (slash >= 0 ? path.substring(slash + 1) : path).trim();
            return last.isEmpty() ? null : last;
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    static String guessMime(String fileName) {
        String m = guessMimeOrNull(fileName);
        return m != null ? m : "application/octet-stream";
    }

    @Nullable
    private static String guessMimeOrNull(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return null;
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** 通知用(Chrome と同じく小数 2 桁: "17.86 MB")。 */
    static String formatSizeDetailed(long size) {
        if (size < 0) return "";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(Locale.ROOT, "%.2f KB", size / 1024.0);
        if (size < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.2f MB", size / (1024.0 * 1024));
        return String.format(Locale.ROOT, "%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    static String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", size / 1024.0);
        if (size < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", size / (1024.0 * 1024));
        return String.format(Locale.ROOT, "%.1f GB", size / (1024.0 * 1024 * 1024));
    }
}
