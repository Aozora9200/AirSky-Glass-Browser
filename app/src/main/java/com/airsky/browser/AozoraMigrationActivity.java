package com.airsky.browser;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 初回起動時、Aozora から {@code Aozora_to_AirSky = true} を付けて起動された時に、最初のオープニング(AZ_MOV)の後で出す
 * 「Aozora からデータを引っ越ししますか？」の画面。
 *
 * <ul>
 *   <li>はい: ダウンロード/Aozora/.Aozora-西暦.az を読み、AirSky 用に直して反映する({@link AozoraTransfer})。
 *       成功したら .az を消し、「引っ越しが完了しました」の後で再起動してセットアップへ進む。</li>
 *   <li>失敗したら理由を出し、途中まで反映したデータを残さないよう、すべてのデータを消して再起動する。</li>
 *   <li>いいえ: そのままセットアップへ進む。</li>
 * </ul>
 *
 * <p>初回起動でない時(AirSky を使っている途中)に Aozora から引っ越しとして起動された時は、
 * {@link #EXTRA_REPLACE} を付けて開く。この時は「Aozora 以外からの引っ越しの誘導に注意」と
 * 「既存のデータを削除して上書きする」の 2 つの警告を出し、同意した時だけ既存のデータを消して引っ越す。
 * キャンセルするとアプリを終了する。</p>
 */
public class AozoraMigrationActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE = 41;
    private static final int REQUEST_PICK = 42;
    private static final String STATE_WAITING = "waitingForAccess";
    private static final String STATE_BUSY = "busy";
    /** 初回起動でない時の引っ越し(既存のデータを消して上書きする)。 */
    static final String EXTRA_REPLACE = "aozoraReplaceExisting";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    /** 「すべてのファイルへのアクセス」の設定画面から戻ってくるのを待っている。 */
    private boolean waitingForAccess;
    private boolean busy;
    /** 既存のデータを消して上書きする引っ越し({@link #EXTRA_REPLACE})。 */
    private boolean replace;
    private CircularProgressIndicator progress;
    private TextView status;

    /** Aozora からの引っ越しとして開かれたか(初回起動で、まだ引っ越していない時だけ)。 */
    static boolean shouldOffer(@NonNull android.content.Context c, @Nullable Intent intent) {
        if (!hasFlag(intent)) return false;
        android.content.SharedPreferences p = c.getSharedPreferences("AppPrefs", MODE_PRIVATE);
        return p.getBoolean("isFirstRun_v2", true) && !p.getBoolean(AozoraTransfer.KEY_DONE, false);
    }

    /** 初回起動でない時に、Aozora からの引っ越しとして開かれた(既存のデータを上書きする引っ越し)。 */
    static boolean shouldOfferReplace(@NonNull android.content.Context c, @Nullable Intent intent) {
        if (!hasFlag(intent)) return false;
        return !c.getSharedPreferences("AppPrefs", MODE_PRIVATE).getBoolean("isFirstRun_v2", true);
    }

    private static boolean hasFlag(@Nullable Intent intent) {
        if (intent == null) return false;
        return intent.getBooleanExtra(AozoraTransfer.EXTRA_FROM_AOZORA, false)
                || "true".equalsIgnoreCase(intent.getStringExtra(AozoraTransfer.EXTRA_FROM_AOZORA));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        replace = getIntent().getBooleanExtra(EXTRA_REPLACE, false);
        setContentView(buildContent());
        if (savedInstanceState != null) {
            waitingForAccess = savedInstanceState.getBoolean(STATE_WAITING, false);
            busy = savedInstanceState.getBoolean(STATE_BUSY, false);
        }
        if (busy) {
            // 引っ越しの途中で画面が作り直された(処理は前の画面と一緒に止まっている)。最初から聞き直す
            busy = false;
        }
        if (!waitingForAccess) {
            if (replace) warnOtherSources();
            else ask();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(STATE_WAITING, waitingForAccess);
        out.putBoolean(STATE_BUSY, busy);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!waitingForAccess) return;
        waitingForAccess = false;
        if (hasFileAccess()) {
            startImportFromFolder();
        } else {
            explainAccess();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // 引っ越しの途中で戻ると、データが中途半端になるので戻れない
    }

    // ------------------------------------------------------------------
    // 画面
    // ------------------------------------------------------------------

    @NonNull
    private View buildContent() {
        float d = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface,
                0xFFFFFFFF));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(32 * d);
        box.setPadding(pad, pad, pad, pad);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(ContextCompat.getDrawable(this, R.mipmap.aozora));
        box.addView(icon, new LinearLayout.LayoutParams(Math.round(88 * d), Math.round(88 * d)));

        TextView title = new TextView(this);
        title.setText(Lang.t("Aozora から AirSky へ"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Math.round(20 * d);
        box.addView(title, tlp);

        progress = new CircularProgressIndicator(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = Math.round(28 * d);
        box.addView(progress, plp);

        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        status.setTextColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Math.round(12 * d);
        box.addView(status, slp);

        root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return root;
    }

    private void setBusy(@Nullable CharSequence text) {
        busy = text != null;
        progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        status.setText(text);
    }

    // ------------------------------------------------------------------
    // 流れ
    // ------------------------------------------------------------------

    private void ask() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("Aozora からデータを引っ越ししますか？"))
                .setMessage(Lang.t("Aozora のブックマーク・履歴・開いていたタブ・保存したページ・ダウンロード履歴・設定を AirSky に移します。\n\n引っ越しが終わると AirSky を再起動して、セットアップに進みます。"))
                .setCancelable(false)
                .setPositiveButton(Lang.t("はい"), (dlg, w) -> start())
                .setNegativeButton(Lang.t("いいえ"), (dlg, w) -> goToSetup())
                .show();
    }

    // ---- 初回起動でない時の引っ越し(既存のデータを上書きする) ----

    /** 1 つ目の警告: Aozora 以外からの引っ越しの誘導に注意。 */
    private void warnOtherSources() {
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_msg_warning)
                .setTitle(Lang.t("Aozora 以外からの引っ越しの誘導にご注意下さい！"))
                .setMessage(Lang.t("Aozora から AirSky へデータを引っ越すよう求められました。\n\nAozora 以外のアプリやウェブサイトから引っ越しを勧められた場合は、操作を続けないでください。\n\nほかのブラウザからの引っ越しは、思わぬ損害を招いたとしても一切の責任を負えません！"))
                .setCancelable(false)
                .setPositiveButton(Lang.t("次へ"), (dlg, w) -> warnOverwrite())
                .setNegativeButton(Lang.t("キャンセル"), (dlg, w) -> quitApp())
                .show();
    }

    /** 2 つ目の警告: 既存のデータを削除して上書きする。赤いボタンで同意してもらう。 */
    private void warnOverwrite() {
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_ps_delete)
                .setTitle(Lang.t("既存のデータが上書きされます"))
                .setMessage(Lang.t("引っ越しの際、既存のデータを削除してデータが上書きされます！\n\nAirSky のブックマーク・履歴・タブ・設定などはすべて削除され、Aozora のデータに置き換わります。元に戻すことはできません。"))
                .setCancelable(false)
                .setPositiveButton(Lang.t("同意して引っ越す"), (dlg, w) -> start())
                .setNegativeButton(Lang.t("キャンセル"), (dlg, w) -> quitApp())
                .show();
        // 取り消せない操作なので、赤い塗りのボタンにする
        android.widget.Button agree = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (agree != null) {
            int error = MaterialColors.getColor(agree, androidx.appcompat.R.attr.colorError, 0xFFD32F2F);
            int onError = MaterialColors.getColor(agree, com.google.android.material.R.attr.colorOnError, 0xFFFFFFFF);
            agree.setBackgroundTintList(android.content.res.ColorStateList.valueOf(error));
            agree.setTextColor(onError);
            float d = getResources().getDisplayMetrics().density;
            agree.setPadding(Math.round(20 * d), agree.getPaddingTop(), Math.round(20 * d), agree.getPaddingBottom());
        }
    }

    /** キャンセル: アプリを終了する。 */
    private void quitApp() {
        finishAffinity();
    }

    private void start() {
        if (hasFileAccess()) {
            startImportFromFolder();
        } else {
            requestFileAccess();
        }
    }

    /** 引っ越しファイルをパスで読めるか(Android 11 以降は「すべてのファイルへのアクセス」が必要)。 */
    private boolean hasFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Android 10 はほかのアプリが作ったファイルをパスで読めないことがある。読めるかを実際に確かめる
            File f = AozoraTransfer.findFile();
            return f != null && f.canRead();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            explainAccess();
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            pickFile();
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }

    /** ファイルを読む許可の説明。許可するか、自分でファイルを選ぶか。 */
    private void explainAccess() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("ファイルへのアクセスを許可してください"))
                .setMessage(Lang.t("Aozora が書き出した引っ越しファイル(ダウンロード/Aozora/.Aozora-西暦.az)を読み込むために、ファイルへのアクセスが必要です。\n\n許可しない場合は、引っ越しファイルを自分で選ぶこともできます。"))
                .setCancelable(false)
                .setPositiveButton(Lang.t("許可する"), (dlg, w) -> openAccessSettings())
                .setNeutralButton(Lang.t("ファイルを選ぶ"), (dlg, w) -> pickFile())
                .setNegativeButton(Lang.t("引っ越さない"), (dlg, w) -> goToSetup())
                .show();
    }

    private void openAccessSettings() {
        waitingForAccess = true;
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception e2) {
                waitingForAccess = false;
                pickFile();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_STORAGE) return;
        if (hasFileAccess()) startImportFromFolder();
        else explainPick();
    }

    /** 許可されなかった(Android 9 以前): ファイルを選んでもらう。 */
    private void explainPick() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("引っ越しファイルを選んでください"))
                .setMessage(Lang.t("ダウンロード/Aozora にある .Aozora-西暦.az を選んでください。"))
                .setCancelable(false)
                .setPositiveButton(Lang.t("ファイルを選ぶ"), (dlg, w) -> pickFile())
                .setNegativeButton(Lang.t("引っ越さない"), (dlg, w) -> goToSetup())
                .show();
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            i.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FAozora"));
        }
        try {
            startActivityForResult(i, REQUEST_PICK);
        } catch (Exception e) {
            failBeforeImport(Lang.t("ファイルを選ぶ画面を開けませんでした。"));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK) return;
        Uri uri = data != null ? data.getData() : null;
        if (resultCode != RESULT_OK || uri == null) {
            explainPick();
            return;
        }
        startImportFromUri(uri);
    }

    private void startImportFromFolder() {
        File file = AozoraTransfer.findFile();
        if (file == null) {
            failBeforeImport(Lang.t("ダウンロード/Aozora に引っ越しファイル(.Aozora-西暦.az)が見つかりませんでした。"));
            return;
        }
        runImport(() -> new FileInputStream(file), () -> {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        });
    }

    private void startImportFromUri(@NonNull Uri uri) {
        String name = displayName(uri);
        if (name != null && !AozoraTransfer.looksLikeTransferFile(name) && !name.endsWith(".az")) {
            failBeforeImport(Lang.t("選んだファイルは引っ越しファイル(.Aozora-西暦.az)ではありません。"));
            return;
        }
        runImport(() -> {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new java.io.FileNotFoundException(uri.toString());
            return in;
        }, () -> {
            try {
                DocumentsContract.deleteDocument(getContentResolver(), uri);
            } catch (Exception ignored) {
                // 消せなくても引っ越しは済んでいる
            }
        });
    }

    @Nullable
    private String displayName(@NonNull Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return null;
    }

    private interface StreamOpener {
        InputStream open() throws Exception;
    }

    private void runImport(@NonNull StreamOpener opener, @NonNull Runnable deleteSource) {
        setBusy(Lang.t("引っ越ししています..."));
        io.execute(() -> {
            AozoraTransfer.Result result = null;
            String error = null;
            boolean cleared = false;
            try (InputStream in = opener.open()) {
                result = AozoraTransfer.importFrom(getApplicationContext(), in, replace);
                getSharedPreferences("AppPrefs", MODE_PRIVATE).edit()
                        .putBoolean(AozoraTransfer.KEY_DONE, true).commit();
                deleteSource.run();
            } catch (AozoraTransfer.TransferException e) {
                error = e.getMessage();
                cleared = e.dataCleared;
            } catch (Exception e) {
                error = Lang.t("引っ越しファイルを読み込めませんでした。") + "\n(" + e.getClass().getSimpleName() + ")";
            }
            AozoraTransfer.Result r = result;
            String err = error;
            boolean dataCleared = cleared;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                String reason = err != null ? err : Lang.t("不明なエラー");
                if (r != null) succeed(r);
                else if (replace && !dataCleared) failKeepingData(reason);
                else fail(reason);
            });
        });
    }

    private void succeed(@NonNull AozoraTransfer.Result r) {
        setBusy(null);
        StringBuilder sb = new StringBuilder(Lang.t("Aozora のデータを AirSky に移しました。"));
        sb.append("\n");
        if (r.bookmarks > 0) sb.append("\n").append(Lang.t("ブックマーク")).append(": ").append(r.bookmarks);
        if (r.history > 0) sb.append("\n").append(Lang.t("履歴")).append(": ").append(r.history);
        if (r.tabs > 0) sb.append("\n").append(Lang.t("タブ")).append(": ").append(r.tabs);
        if (r.downloads > 0) sb.append("\n").append(Lang.t("ダウンロード履歴")).append(": ").append(r.downloads);
        if (r.savedPages > 0) sb.append("\n").append(Lang.t("保存したページ")).append(": ").append(r.savedPages);
        if (r.settings > 0) sb.append("\n").append(Lang.t("設定")).append(": ").append(r.settings);
        sb.append("\n\n").append(Lang.t("AirSky を再起動して、セットアップに進みます。"));
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_ps_check_circle)
                .setTitle(Lang.t("引っ越しが完了しました"))
                .setMessage(sb)
                .setCancelable(false)
                .setPositiveButton("OK", (dlg, w) -> restart())
                .show();
    }

    /**
     * 入れ替えの引っ越しで、既存のデータを消す前に失敗した(ファイルが無い・壊れているなど)。
     * データはそのまま残っているので消さずに終了する。
     */
    private void failKeepingData(@NonNull String reason) {
        setBusy(null);
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("引っ越しに失敗しました"))
                .setMessage(reason + "\n\n" + Lang.t("既存のデータは削除していません。AirSky を終了します。"))
                .setCancelable(false)
                .setPositiveButton("OK", (dlg, w) -> quitApp())
                .show();
    }

    /** 読み込みを始める前に失敗した。入れ替えの引っ越しなら、まだ何も消していないのでデータは残す。 */
    private void failBeforeImport(@NonNull String reason) {
        if (replace) failKeepingData(reason);
        else fail(reason);
    }

    private void fail(@NonNull String reason) {
        setBusy(null);
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("引っ越しに失敗しました"))
                .setMessage(reason + "\n\n" + Lang.t("途中まで移したデータが残らないよう、AirSky のデータをすべて削除して再起動します。"))
                .setCancelable(false)
                .setPositiveButton("OK", (dlg, w) -> {
                    setBusy(Lang.t("データを削除しています..."));
                    io.execute(() -> {
                        AozoraTransfer.wipeAllData(getApplicationContext());
                        main.post(this::restart);
                    });
                })
                .show();
    }

    private void goToSetup() {
        if (replace) {
            // 使っている途中の引っ越しをやめた: データには触れずに終了する
            quitApp();
            return;
        }
        startActivity(new Intent(this, MainActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /** AirSky を起動し直す(プロセスごと。設定を読み直してセットアップから始める)。 */
    private void restart() {
        // Aozora のタスクの上で開かれているので、AirSky のタスクを新しく作って開き直す
        RestartActivity.restart(this);
    }
}
