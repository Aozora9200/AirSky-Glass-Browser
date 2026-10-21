package com.airsky.browser;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ダウンロード一覧(Aozora 独自のデザイン)。
 *
 * <ul>
 *   <li>上にファイル数と容量のカード。端末の使用量と、そのうち Aozora でダウンロードした量を輪で見せる</li>
 *   <li>種類(動画・音声・画像・ページ・その他)で絞り込む丸い札。ある種類だけ出す</li>
 *   <li>「たった今」「今日 ・ 日付」「昨日 ・ 日付」… の日付ごとに、つながったカードで並べる。
 *       種類ごとにアイコンの色を変え、画像はサムネイルの上に名前を重ねたカード</li>
 *   <li>各ファイルのメニューから共有・削除。削除は「元に戻す」が消えるまで実際には消さない</li>
 * </ul>
 */
public class DownloadListActivity extends AppCompatActivity {

    private static final String PREF_NAME = "AdvancedBrowserPrefs";
    private static final String KEY_DOWNLOAD_HISTORY = "download_history";
    /** この時間以内に始めたダウンロードは「たった今」にまとめる(Chromium と同じ)。 */
    private static final long JUST_NOW_MS = 30 * 60 * 1000L;
    private static final int UNDO_DELETE_MS = 6000;
    private static final int REQUEST_SETTINGS = 1;

    // ファイルの種類(絞り込みチップの並び順)
    private static final int CAT_ALL = 0;
    private static final int CAT_VIDEO = 1;
    private static final int CAT_AUDIO = 2;
    private static final int CAT_IMAGE = 3;
    private static final int CAT_PAGE = 4;
    private static final int CAT_OTHER = 5;
    private static final String[] CAT_LABELS = {Lang.t("すべて"), Lang.t("動画"), Lang.t("音声"), Lang.t("画像"), Lang.t("ページ"), Lang.t("その他")};
    private static final int[] CAT_ICONS = {0, R.drawable.ic_dl_video, R.drawable.ic_dl_audio,
            R.drawable.ic_dl_image, R.drawable.ic_omni_globe, R.drawable.ic_dl_file};

    private View root;
    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private View emptyView;
    private TextView storageText, deviceText, countText, ringText;
    private StorageRingView ring;
    private ChipGroup chipGroup;
    private View titleBar, searchBar;
    private EditText searchField;
    private ImageButton searchClear;

    private SharedPreferences pref;
    private DownloadManager downloadManager;
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService thumbExecutor = Executors.newFixedThreadPool(2);

    /** 新しい順。 */
    private final List<DownloadItem> allItems = new ArrayList<>();
    /** 「元に戻す」を待っている削除(一覧には出さない)。 */
    private final Set<DownloadItem> pendingDelete = new LinkedHashSet<>();
    private int category = CAT_ALL;
    private String query = "";
    private long storageTotal = -1;
    private long storageFree = -1;
    /** 一覧を初めて出す時だけ、行を順に出す。 */
    private boolean firstShown = true;
    private DownloadAdapter adapter;

    private final LruCache<Long, Bitmap> thumbs = new LruCache<Long, Bitmap>(
            (int) Math.min(Runtime.getRuntime().maxMemory() / 8, 48L * 1024 * 1024)) {
        @Override
        protected int sizeOf(Long key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private final Set<Long> thumbLoading = new HashSet<>();

    /** 進行中のダウンロードの状態を 1 秒ごとに取り直す。 */
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            refreshActiveItems();
            updateHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_history);
        TouchEffectView.attach(getWindow());

        root = findViewById(R.id.dlRoot);
        recyclerView = findViewById(R.id.recyclerView);
        tvEmpty = findViewById(R.id.tvEmpty);
        emptyView = findViewById(R.id.dlEmpty);
        storageText = findViewById(R.id.dlStorage);
        deviceText = findViewById(R.id.dlDevice);
        countText = findViewById(R.id.dlCount);
        ring = findViewById(R.id.dlRing);
        ringText = findViewById(R.id.dlRingText);
        int onHero = MaterialColors.getColor(ring, com.google.android.material.R.attr.colorOnPrimaryContainer);
        ring.setColors((onHero & 0x00FFFFFF) | 0x33000000, onHero,
                MaterialColors.getColor(ring, com.google.android.material.R.attr.colorTertiary));
        playHeroEnter();
        chipGroup = findViewById(R.id.dlChips);
        titleBar = findViewById(R.id.dlTitleBar);
        searchBar = findViewById(R.id.dlSearchBar);
        searchField = findViewById(R.id.dlSearchField);
        searchClear = findViewById(R.id.dlSearchClear);

        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        adapter = new DownloadAdapter();
        GridLayoutManager lm = new GridLayoutManager(this, 2);
        lm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.spanSizeAt(position);
            }
        });
        recyclerView.setLayoutManager(lm);
        recyclerView.setAdapter(adapter);
        // 進行状況の更新のたびに行が点滅しないようにする
        if (recyclerView.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        }

        findViewById(R.id.dlClose).setOnClickListener(v -> close());
        findViewById(R.id.dlSettings).setOnClickListener(v -> showSettings());
        findViewById(R.id.dlSearch).setOnClickListener(v -> setSearching(true));
        findViewById(R.id.dlSearchBack).setOnClickListener(v -> setSearching(false));
        searchClear.setOnClickListener(v -> searchField.setText(""));
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                searchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                rebuild();
            }
        });
        chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            Chip chip = group.findViewById(ids.get(0));
            if (chip != null && chip.getTag() instanceof Integer) {
                category = (Integer) chip.getTag();
                rebuild();
            }
        });

        if (getIntent().getBooleanExtra("clear_history", false)) {
            pref.edit().remove(KEY_DOWNLOAD_HISTORY).apply();
            Toast.makeText(this, Lang.t("ダウンロード履歴を全消去しました"), Toast.LENGTH_SHORT).show();
        }
        loadDownloadHistory();
        handleRetryIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleRetryIntent(intent);
    }

    /** 通知の「再試行」から開かれた: 失敗したダウンロードをやり直す。 */
    private void handleRetryIntent(@Nullable Intent intent) {
        if (intent == null || !intent.hasExtra(AppDownloader.EXTRA_RETRY_URL)) return;
        long id = intent.getLongExtra(AppDownloader.EXTRA_RETRY_ID, 0);
        String url = intent.getStringExtra(AppDownloader.EXTRA_RETRY_URL);
        intent.removeExtra(AppDownloader.EXTRA_RETRY_URL);
        retryGecko(id, url);
    }

    /** 失敗したダウンロードの「再試行」。 */
    private void retryDownload(DownloadItem item) {
        if (item.downloadId > 0) {
            // DownloadManager のダウンロードは、一時停止からの再開と同じく取り直す
            resumeDownload(item);
            return;
        }
        // 一覧からはすぐに消さない(やり直しが始まったら読み直して差し替わる。失敗したらそのまま残る)
        retryGecko(item.downloadId, item.downloadUrl);
    }

    private boolean retryGecko(long id, @Nullable String url) {
        return AppDownloader.retry(this, id, url, false, new AppDownloader.Observer() {
            @Override
            public void onStarted(long newId) {
                if (!isDestroyed()) loadDownloadHistory();
            }

            @Override
            public void onDiscarded() {
                if (!isDestroyed()) loadDownloadHistory();
            }
        });
    }

    /** 失敗して、もう一度取得できるダウンロードか。 */
    private static boolean canRetry(DownloadItem item) {
        return item.threat == null && item.status == DownloadManager.STATUS_FAILED && !item.isActive()
                && AppDownloader.canRetry(item.downloadUrl);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHandler.post(updateRunnable);
    }

    @Override
    protected void onPause() {
        updateHandler.removeCallbacks(updateRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // 「元に戻す」を待っていた削除は、画面を閉じた時点で確定する
        for (DownloadItem item : new ArrayList<>(pendingDelete)) commitDelete(item);
        executor.shutdown();
        thumbExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (searchBar.getVisibility() == View.VISIBLE) {
            setSearching(false);
            return;
        }
        close();
    }

    private void close() {
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }

    // ------------------------------------------------------------------
    // 読み込み
    // ------------------------------------------------------------------

    private void loadDownloadHistory() {
        executor.execute(() -> {
            List<DownloadItem> items = new ArrayList<>();
            try {
                JSONArray array = new JSONArray(pref.getString(KEY_DOWNLOAD_HISTORY, "[]"));
                for (int i = 0, len = array.length(); i < len; i++) {
                    items.add(readItem(array.getJSONObject(i)));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // 履歴は古い順に追加されているので逆にし、時刻の新しい順に並べる(時刻が無い古い履歴は最後)
            Collections.reverse(items);
            Collections.sort(items, (a, b) -> Long.compare(b.time, a.time));
            long total = storageTotalBytes();
            long free = storageFreeBytes();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                allItems.clear();
                allItems.addAll(items);
                storageTotal = total;
                storageFree = free;
                rebuild();
            });
        });
    }

    private DownloadItem readItem(JSONObject obj) throws JSONException {
        long downloadId = obj.getLong("id");
        String storedFileName = obj.optString("fileName", "");
        String filePath = fixPendingPath(obj.optString("filePath", ""));
        String contentUri = obj.optString("uri", "");

        DownloadItem item = getDownloadItem(downloadId);
        if (item == null) {
            File file = new File(filePath);
            int status = file.exists() ? DownloadManager.STATUS_SUCCESSFUL : DownloadManager.STATUS_FAILED;
            String fileName = !storedFileName.isEmpty() ? storedFileName : file.getName();
            item = new DownloadItem(downloadId, fileName, "", status, 0, file.exists() ? file.length() : 0,
                    "file://" + filePath, "");
        } else if (item.title == null || item.title.isEmpty()) {
            item.title = !storedFileName.isEmpty() ? storedFileName : new File(filePath).getName();
        }
        if (item.filePath == null || item.filePath.isEmpty()) item.filePath = filePath;
        if (item.contentUri == null && !contentUri.isEmpty()) item.contentUri = contentUri;
        if (item.downloadUrl == null || item.downloadUrl.isEmpty()) item.downloadUrl = obj.optString("url", "");
        if (obj.has("threat")) {
            item.threat = obj.optString("threat");
            item.status = DownloadManager.STATUS_FAILED;
        }
        item.time = obj.optLong("time", 0);
        if (item.title == null || item.title.isEmpty()) item.title = Lang.t("ダウンロード ") + downloadId;

        // 実際に存在するか(Android 10 以降は MediaStore で確認)・サイズ・日時
        if (item.threat == null && item.status == DownloadManager.STATUS_FAILED && fileExists(item)) {
            item.status = DownloadManager.STATUS_SUCCESSFUL;
        }
        item.exists = item.isActive() || (item.threat == null && fileExists(item));
        if (item.exists && !item.isActive()) fillSizeAndDate(item);
        item.category = categoryOf(item.title);
        return item;
    }

    /** サイズ・日時が分からない(古い履歴など)ファイルは、ファイル自体から取る。 */
    private void fillSizeAndDate(DownloadItem item) {
        if (item.contentUri != null && !item.contentUri.isEmpty()) {
            try (Cursor c = getContentResolver().query(Uri.parse(item.contentUri),
                    new String[]{MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED},
                    null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    if (item.totalSize <= 0) item.totalSize = c.getLong(0);
                    if (item.time <= 0) item.time = c.getLong(1) * 1000L;
                    return;
                }
            } catch (Exception ignored) {
                // パスでの確認に任せる
            }
        }
        File f = new File(item.filePath);
        if (f.exists()) {
            if (item.totalSize <= 0) item.totalSize = f.length();
            if (item.time <= 0) item.time = f.lastModified();
        }
    }

    private long storageTotalBytes() {
        try {
            return new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath()).getTotalBytes();
        } catch (Exception e) {
            return -1;
        }
    }

    private long storageFreeBytes() {
        try {
            return new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath()).getAvailableBytes();
        } catch (Exception e) {
            return -1;
        }
    }

    /** 上のカードを、少し縮んだ所から持ち上がるように出す。 */
    private void playHeroEnter() {
        View hero = findViewById(R.id.dlHero);
        float d = getResources().getDisplayMetrics().density;
        hero.setAlpha(0f);
        hero.setScaleX(0.94f);
        hero.setScaleY(0.94f);
        hero.setTranslationY(16 * d);
        hero.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setStartDelay(60).setDuration(420)
                .setInterpolator(new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f))
                .start();
    }

    @Nullable
    private DownloadItem getDownloadItem(long downloadId) {
        if (downloadId < 0) {
            // ブラウザ(GeckoView)経由のダウンロード。アプリ内で進行状況を管理している
            AppDownloader.Progress p = AppDownloader.getProgress(downloadId);
            if (p == null) return null;
            long total = p.total > 0 ? p.total : 0;
            DownloadItem item = new DownloadItem(downloadId, p.fileName, "", p.status, p.downloaded, total,
                    "file://" + p.filePath, "");
            item.filePath = p.filePath;
            item.contentUri = p.contentUri;
            item.threat = p.threat;
            return item;
        }
        DownloadManager.Query q = new DownloadManager.Query();
        q.setFilterById(downloadId);
        try (Cursor cursor = downloadManager.query(q)) {
            if (cursor != null && cursor.moveToFirst()) {
                String title = getRobustFileName(cursor);
                String description = safeGetString(cursor, DownloadManager.COLUMN_DESCRIPTION);
                int status = safeGetInt(cursor, DownloadManager.COLUMN_STATUS);
                long totalSize = safeGetLong(cursor, DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                long downloadedSize = safeGetLong(cursor, DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                String localUri = safeGetString(cursor, DownloadManager.COLUMN_LOCAL_URI);
                String downloadUrl = safeGetString(cursor, DownloadManager.COLUMN_URI);
                return new DownloadItem(downloadId, title, description, status, downloadedSize, totalSize, localUri, downloadUrl);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /** 進行中のダウンロードだけ状態を取り直す。終わったものがあれば一覧を作り直す。 */
    private void refreshActiveItems() {
        boolean structural = false;
        for (DownloadItem item : allItems) {
            if (!item.isActive()) continue;
            DownloadItem u = getDownloadItem(item.downloadId);
            if (u == null) continue;
            boolean statusChanged = item.status != u.status || !equalsOrNull(item.threat, u.threat);
            boolean progressChanged = item.downloadedSize != u.downloadedSize || item.totalSize != u.totalSize;
            item.status = u.status;
            item.threat = u.threat;
            item.downloadedSize = u.downloadedSize;
            item.totalSize = u.totalSize;
            item.localUri = u.localUri;
            if (u.title != null && !u.title.isEmpty()) item.title = u.title;
            // ブラウザ経由のダウンロードは完了時に名前・パスが確定する
            if (u.filePath != null && !u.filePath.isEmpty()) item.filePath = u.filePath;
            if (u.contentUri != null) item.contentUri = u.contentUri;
            if (statusChanged) {
                item.exists = item.isActive()
                        || (item.status == DownloadManager.STATUS_SUCCESSFUL && item.threat == null);
                item.category = categoryOf(item.title);
                structural = true;
            } else if (progressChanged) {
                adapter.notifyItemChanged(item);
            }
        }
        if (structural) rebuild();
    }

    // ------------------------------------------------------------------
    // 表示
    // ------------------------------------------------------------------

    /** 絞り込み・検索・削除待ちを反映して、チップ・容量・一覧を作り直す。 */
    private void rebuild() {
        // チップ: ファイルがある種類だけ出す
        Set<Integer> present = new HashSet<>();
        long used = 0;
        for (DownloadItem item : allItems) {
            if (pendingDelete.contains(item)) continue;
            present.add(item.category);
            if (item.exists && item.status == DownloadManager.STATUS_SUCCESSFUL) used += Math.max(0, item.totalSize);
        }
        if (category != CAT_ALL && !present.contains(category)) category = CAT_ALL;
        rebuildChips(present);

        int count = 0;
        for (DownloadItem item : allItems) if (!pendingDelete.contains(item)) count++;
        countText.setText(String.valueOf(count));
        storageText.setText(Lang.t("ダウンロード: ") + formatSize(used));
        if (storageTotal > 0 && storageFree >= 0) {
            long deviceUsed = Math.max(0, storageTotal - storageFree);
            float fraction = (float) deviceUsed / storageTotal;
            deviceText.setText(Lang.t("端末: ") + formatSize(storageTotal) + Lang.t(" 中 ") + formatSize(deviceUsed) + Lang.t(" 使用"));
            ringText.setText(Math.round(fraction * 100) + "%");
            ring.setValues(fraction, (float) used / storageTotal);
            ring.setContentDescription(Lang.t("端末の容量の ") + Math.round(fraction * 100) + Lang.t("% を使用中"));
        } else {
            deviceText.setText("");
            ringText.setText("");
        }

        String q = query.toLowerCase(Locale.ROOT);
        List<DownloadItem> visible = new ArrayList<>();
        for (DownloadItem item : allItems) {
            if (pendingDelete.contains(item)) continue;
            if (category != CAT_ALL && item.category != category) continue;
            if (!q.isEmpty() && (item.title == null || !item.title.toLowerCase(Locale.ROOT).contains(q))) continue;
            visible.add(item);
        }
        adapter.setItems(visible);

        boolean empty = visible.isEmpty();
        tvEmpty.setText(allItems.size() > pendingDelete.size()
                ? Lang.t("一致するダウンロードはありません")
                : Lang.t("ダウンロードしたファイルはここに表示されます"));
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (!empty && firstShown) {
            // 最初の表示だけ、行を上から順にすべり込ませる
            firstShown = false;
            android.view.animation.AnimationSet set = new android.view.animation.AnimationSet(true);
            set.addAnimation(new android.view.animation.AlphaAnimation(0f, 1f));
            android.view.animation.TranslateAnimation slide = new android.view.animation.TranslateAnimation(
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.35f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0f);
            set.addAnimation(slide);
            set.setDuration(320);
            set.setInterpolator(new android.view.animation.DecelerateInterpolator(2f));
            android.view.animation.LayoutAnimationController controller =
                    new android.view.animation.LayoutAnimationController(set, 0.12f);
            recyclerView.setLayoutAnimation(controller);
            recyclerView.scheduleLayoutAnimation();
        }
    }

    private void rebuildChips(Set<Integer> present) {
        // 出すチップが変わらなければ作り直さない(選択のアニメーションを保つ)
        List<Integer> wanted = new ArrayList<>();
        wanted.add(CAT_ALL);
        for (int c = CAT_VIDEO; c <= CAT_OTHER; c++) if (present.contains(c)) wanted.add(c);
        List<Integer> current = new ArrayList<>();
        for (int i = 0; i < chipGroup.getChildCount(); i++) current.add((Integer) chipGroup.getChildAt(i).getTag());
        if (!wanted.equals(current)) {
            chipGroup.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(this);
            for (int c : wanted) {
                Chip chip = (Chip) inflater.inflate(R.layout.item_download_chip, chipGroup, false);
                chip.setId(View.generateViewId());
                chip.setTag(c);
                chip.setText(CAT_LABELS[c]);
                if (CAT_ICONS[c] != 0) chip.setChipIconResource(CAT_ICONS[c]);
                chip.setChipIconVisible(CAT_ICONS[c] != 0);
                chipGroup.addView(chip);
            }
        }
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);
            boolean checked = (Integer) chip.getTag() == category;
            if (chip.isChecked() != checked) chip.setChecked(checked);
        }
        findViewById(R.id.dlChipScroll).setVisibility(wanted.size() > 1 ? View.VISIBLE : View.GONE);
    }

    private void setSearching(boolean searching) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        titleBar.setVisibility(searching ? View.GONE : View.VISIBLE);
        searchBar.setVisibility(searching ? View.VISIBLE : View.GONE);
        // 検索欄は虫めがねのボタンの所から横に伸びるように出す
        View from = searching ? searchBar : titleBar;
        from.setPivotX(searching ? from.getWidth() : 0);
        from.setScaleX(0.85f);
        from.setAlpha(0f);
        from.animate().scaleX(1f).alpha(1f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        if (searching) {
            searchField.requestFocus();
            if (imm != null) imm.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
        } else {
            searchField.setText("");
            if (imm != null) imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
            searchField.clearFocus();
        }
    }

    /** 見出し。「たった今」「今日 ・ 2026年10月4日」「昨日 ・ …」「2026年10月2日」。 */
    private static String sectionOf(DownloadItem item, long now) {
        if (item.isActive() || (item.time > 0 && now - item.time < JUST_NOW_MS)) return Lang.t("たった今");
        if (item.time <= 0) return Lang.t("日付不明");
        String date = new SimpleDateFormat(Lang.t("yyyy年M月d日"), Locale.JAPAN).format(new Date(item.time));
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(now);
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long startOfToday = today.getTimeInMillis();
        today.add(Calendar.DAY_OF_MONTH, -1);
        long startOfYesterday = today.getTimeInMillis();
        if (item.time >= startOfToday) return Lang.t("今日 ・ ") + date;
        if (item.time >= startOfYesterday) return Lang.t("昨日 ・ ") + date;
        return date;
    }

    private static int categoryOf(@Nullable String fileName) {
        String lower = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
        if (lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".mhtml")
                || lower.endsWith(".mht")) {
            return CAT_PAGE;
        }
        String mime = mimeOf(lower);
        if (mime.startsWith("image/")) return CAT_IMAGE;
        if (mime.startsWith("video/")) return CAT_VIDEO;
        if (mime.startsWith("audio/")) return CAT_AUDIO;
        return CAT_OTHER;
    }

    /** 画像のカードで出すか(画像があってウイルスも見つかっていない)。 */
    private static boolean showsAsImage(DownloadItem item) {
        return item.category == CAT_IMAGE && item.exists && item.threat == null
                && item.status == DownloadManager.STATUS_SUCCESSFUL;
    }

    private String subtitleOf(DownloadItem item) {
        if (item.threat != null) return Lang.t("ウイルスが検出されたため削除しました(") + item.threat + ")";
        if (item.isPaused || item.status == DownloadManager.STATUS_PAUSED) {
            return Lang.t("一時停止中 ・ ") + progressText(item);
        }
        switch (item.status) {
            case DownloadManager.STATUS_RUNNING:
                return progressText(item);
            case DownloadManager.STATUS_PENDING:
                return Lang.t("待機中");
            case DownloadManager.STATUS_FAILED:
                return AppDownloader.canRetry(item.downloadUrl)
                        ? Lang.t("ダウンロードに失敗しました ・ ↻ で再試行") : Lang.t("ダウンロードに失敗しました");
            case DownloadManager.STATUS_SUCCESSFUL:
                if (!item.exists) return Lang.t("ファイルが削除されたか、移動されています");
                String host = hostOf(item.downloadUrl);
                return formatSize(Math.max(0, item.totalSize)) + (host != null ? Lang.t(" ・ ") + host : "");
            default:
                return "";
        }
    }

    private static String progressText(DownloadItem item) {
        if (item.totalSize > 0) {
            return item.getProgress() + Lang.t("% ・ ") + formatSize(item.downloadedSize) + " / " + formatSize(item.totalSize);
        }
        return formatSize(item.downloadedSize);
    }

    @Nullable
    private static String hostOf(@Nullable String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            String host = Uri.parse(url).getHost();
            return host == null || host.isEmpty() ? null : host;
        } catch (Exception e) {
            return null;
        }
    }

    /** 1024 単位・小数 2 桁(「28.32 KB」「112.76 GB」)。 */
    static String formatSize(long size) {
        if (size < 1024) return size + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double v = size;
        int i = -1;
        do {
            v /= 1024;
            i++;
        } while (v >= 1024 && i < units.length - 1);
        return String.format(Locale.ROOT, "%.2f %s", v, units[i]);
    }

    // ------------------------------------------------------------------
    // 操作
    // ------------------------------------------------------------------

    private void onItemClicked(DownloadItem item) {
        if (item.threat != null) {
            Toast.makeText(this, Lang.t("ウイルスが検出されたため、このファイルは削除されました"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (item.isActive()) return;
        if (canRetry(item)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(Lang.t("ダウンロードに失敗しました"))
                    .setMessage(item.title)
                    .setPositiveButton(Lang.t("再試行"), (d, w) -> retryDownload(item))
                    .setNegativeButton(Lang.t("キャンセル"), null)
                    .show();
            return;
        }
        if (!item.exists) {
            Toast.makeText(this, Lang.t("ファイルが見つかりません"), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (item.filePath != null && item.filePath.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                installApk(item);
            } else {
                openItem(item);
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, Lang.t("ファイルを開けません"), Toast.LENGTH_SHORT).show();
        }
    }

    private void showItemMenu(View anchor, DownloadItem item) {
        boolean geckoRunning = item.downloadId < 0 && item.isActive();
        if (geckoRunning) {
            // ブラウザ経由のダウンロードは一時停止できないので、ボタンはキャンセル
            AppDownloader.cancel(item.downloadId);
            Toast.makeText(this, Lang.t("ダウンロードをキャンセルしました"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (canRetry(item)) {
            retryDownload(item);
            return;
        }
        showOtherMenu(anchor, item);
    }

    /** その他の操作(共有・削除など)のメニュー。 */
    private void showOtherMenu(View anchor, DownloadItem item) {
        PopupMenu popup = new PopupMenu(this, anchor);
        final int share = 1, delete = 2, cancel = 3, pause = 4, resume = 5, retry = 6;
        if (canRetry(item)) popup.getMenu().add(0, retry, 0, Lang.t("再試行"));
        if (item.isActive()) {
            if (item.isPaused) {
                popup.getMenu().add(0, resume, 0, Lang.t("再開"));
            } else {
                popup.getMenu().add(0, pause, 0, Lang.t("一時停止"));
            }
            popup.getMenu().add(0, cancel, 1, Lang.t("キャンセル"));
        } else {
            if (item.exists && item.status == DownloadManager.STATUS_SUCCESSFUL) {
                popup.getMenu().add(0, share, 0, Lang.t("共有"));
            }
            popup.getMenu().add(0, delete, 1, Lang.t("削除"));
        }
        popup.setOnMenuItemClickListener(mi -> {
            int id = mi.getItemId();
            if (id == share) {
                shareItem(item);
            } else if (id == delete) {
                deleteWithUndo(item);
            } else if (id == cancel) {
                downloadManager.remove(item.downloadId);
                DownloadHistoryManager.removeDownloadHistory(this, item.downloadId);
                allItems.remove(item);
                rebuild();
                Toast.makeText(this, Lang.t("ダウンロードをキャンセルしました"), Toast.LENGTH_SHORT).show();
            } else if (id == pause) {
                // DownloadManager には一時停止が無いので、止めておいて再開時に最初から取り直す
                downloadManager.remove(item.downloadId);
                item.isPaused = true;
                item.status = DownloadManager.STATUS_PAUSED;
                adapter.notifyItemChanged(item);
            } else if (id == resume) {
                resumeDownload(item);
            } else if (id == retry) {
                retryDownload(item);
            }
            return true;
        });
        popup.show();
    }

    private void resumeDownload(DownloadItem item) {
        if (item.downloadUrl == null || item.downloadUrl.isEmpty()) return;
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(item.downloadUrl));
        request.setTitle(item.title);
        request.setDescription(item.description != null ? item.description : "Downloading file...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        long oldId = item.downloadId;
        item.downloadId = downloadManager.enqueue(request);
        item.isPaused = false;
        item.status = DownloadManager.STATUS_PENDING;
        DownloadHistoryManager.removeDownloadHistory(this, oldId);
        DownloadHistoryManager.addDownloadHistory(this, item.downloadId, item.title, item.filePath,
                item.contentUri, item.downloadUrl);
        Toast.makeText(this, Lang.t("ダウンロードを再開しました"), Toast.LENGTH_SHORT).show();
        rebuild();
    }

    /** 一覧からすぐ消し、「元に戻す」が消えたら実際にファイルと履歴を消す(Chromium と同じ)。 */
    private void deleteWithUndo(DownloadItem item) {
        pendingDelete.add(item);
        rebuild();
        Snackbar bar = Snackbar.make(root, item.title + Lang.t(" を削除しました"), UNDO_DELETE_MS)
                .setAction(Lang.t("元に戻す"), v -> {
                    pendingDelete.remove(item);
                    rebuild();
                });
        bar.addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
            @Override
            public void onDismissed(Snackbar sb, int event) {
                if (event != DISMISS_EVENT_ACTION) commitDelete(item);
            }
        });
        bar.show();
    }

    private void commitDelete(DownloadItem item) {
        if (!pendingDelete.remove(item)) return;
        allItems.remove(item);
        DownloadHistoryManager.removeDownloadHistory(getApplicationContext(), item.downloadId);
        if (item.exists && item.threat == null) {
            Context app = getApplicationContext();
            new Thread(() -> deleteDownloadedFile(app, item)).start();
        }
        thumbs.remove(item.downloadId);
        if (!isDestroyed()) rebuild();
    }

    private void shareItem(DownloadItem item) {
        try {
            Uri uri = contentUriFor(item);
            if (uri == null) {
                Toast.makeText(this, Lang.t("ファイルが見つかりません"), Toast.LENGTH_SHORT).show();
                return;
            }
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType(mimeOf(item.title != null ? item.title.toLowerCase(Locale.ROOT) : ""))
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, Lang.t("共有")));
        } catch (Exception e) {
            Toast.makeText(this, Lang.t("共有できません: ") + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showSettings() {
        startActivityForResult(new Intent(this, DownloadSettingsActivity.class), REQUEST_SETTINGS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 設定でダウンロード履歴を消した
        if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK) loadDownloadHistory();
    }

    // ------------------------------------------------------------------
    // ファイル
    // ------------------------------------------------------------------

    private String getRobustFileName(Cursor cursor) {
        String title = safeGetString(cursor, DownloadManager.COLUMN_TITLE);
        if (title == null || title.isEmpty()) {
            String localUri = safeGetString(cursor, DownloadManager.COLUMN_LOCAL_URI);
            if (localUri != null && !localUri.isEmpty()) {
                try {
                    String path = Uri.parse(localUri).getPath();
                    if (path != null && !path.isEmpty()) title = new File(path).getName();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if (title == null || title.isEmpty()) {
            String downloadUrl = safeGetString(cursor, DownloadManager.COLUMN_URI);
            title = AppDownloader.guessFileName(downloadUrl, null, null);
        }
        return (title == null || title.isEmpty()) ? "Unknown" : title;
    }

    private String safeGetString(Cursor cursor, String columnName) {
        try {
            return cursor.getString(cursor.getColumnIndexOrThrow(columnName));
        } catch (Exception e) {
            return "";
        }
    }

    private int safeGetInt(Cursor cursor, String columnName) {
        try {
            return cursor.getInt(cursor.getColumnIndexOrThrow(columnName));
        } catch (Exception e) {
            return 0;
        }
    }

    private long safeGetLong(Cursor cursor, String columnName) {
        try {
            return cursor.getLong(cursor.getColumnIndexOrThrow(columnName));
        } catch (Exception e) {
            return 0;
        }
    }

    /** 拡張子から MIME タイプを推定する(小文字のファイル名を渡す)。 */
    private static String mimeOf(String lowerName) {
        int dot = lowerName.lastIndexOf('.');
        String type = null;
        if (dot != -1) type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(lowerName.substring(dot + 1));
        return type != null ? type : "*/*";
    }

    /**
     * 以前の版は書き込み中の名前(".pending-<時刻>-<名前>")を履歴に残していたので、
     * そのファイルが無ければ完了後の名前に読み替える。
     */
    private static String fixPendingPath(String path) {
        if (path == null || path.isEmpty()) return path;
        File f = new File(path);
        String name = f.getName();
        if (!name.startsWith(".pending-") || f.exists()) return path;
        String fixed = name.replaceFirst("^\\.pending-\\d+-", "");
        File parent = f.getParentFile();
        return parent != null ? new File(parent, fixed).getAbsolutePath() : fixed;
    }

    /** ファイルがあるか。MediaStore の URI があればそちらで確かめる(Android 10 はパスで見られない)。 */
    private boolean fileExists(DownloadItem item) {
        if (item.contentUri != null && !item.contentUri.isEmpty()) {
            try (Cursor c = getContentResolver().query(Uri.parse(item.contentUri),
                    new String[]{MediaStore.MediaColumns._ID}, null, null, null)) {
                return c != null && c.moveToFirst();
            } catch (Exception e) {
                // 権限が無い等はパスでの確認に任せる
            }
        }
        return item.filePath != null && !item.filePath.isEmpty() && new File(item.filePath).exists();
    }

    /** ダウンロードしたファイルを削除する。 */
    private static boolean deleteDownloadedFile(Context ctx, DownloadItem item) {
        if (item.contentUri != null && !item.contentUri.isEmpty()) {
            try {
                if (ctx.getContentResolver().delete(Uri.parse(item.contentUri), null, null) > 0) return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (item.filePath == null || item.filePath.isEmpty()) return false;
        File f = new File(item.filePath);
        return f.exists() && f.delete();
    }

    /** 他のアプリに渡せる URI(MediaStore の URI、無ければ FileProvider)。 */
    @Nullable
    private Uri contentUriFor(DownloadItem item) {
        if (item.contentUri != null && !item.contentUri.isEmpty()) return Uri.parse(item.contentUri);
        if (item.filePath == null || item.filePath.isEmpty()) return null;
        File file = new File(item.filePath);
        if (!file.exists()) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".fileprovider", file);
        }
        return Uri.fromFile(file);
    }

    private void openItem(DownloadItem item) {
        Uri uri = contentUriFor(item);
        if (uri == null) {
            Toast.makeText(this, Lang.t("ファイルが見つかりません"), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String mime = getContentResolver().getType(uri);
            if (mime == null) mime = mimeOf(item.title != null ? item.title.toLowerCase(Locale.ROOT) : "");
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            // URI 権限をすべての候補アプリに付与
            List<ResolveInfo> resInfoList = getPackageManager()
                    .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                grantUriPermission(resolveInfo.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, Lang.t("対応アプリが見つかりません"), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, Lang.t("ファイルを開けません: ") + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void installApk(DownloadItem item) {
        Uri uri = contentUriFor(item);
        if (uri == null) {
            Toast.makeText(this, Lang.t("ファイルが存在しません"), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, Lang.t("インストールできません: ") + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** 画像のサムネイルを読む(幅 maxSize 程度に縮小)。 */
    @Nullable
    private Bitmap decodeThumbnail(DownloadItem item, int maxSize) {
        Uri uri = contentUriFor(item);
        if (uri == null) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Throwable e) { // OOM・壊れた画像でも一覧は落とさない
            return null;
        }
    }

    private static boolean equalsOrNull(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }

    // ------------------------------------------------------------------
    // データ・アダプタ
    // ------------------------------------------------------------------

    public static class DownloadItem {
        public long downloadId;
        public String title;
        public String description;
        public int status;
        public long downloadedSize;
        public long totalSize;
        public String localUri;
        /** ダウンロード元の URL(無ければ空)。 */
        public String downloadUrl;
        public boolean isPaused;
        public String filePath;
        /** MediaStore の URI(Android 10 以降の保存先)。無ければ null。 */
        public String contentUri;
        /** ダウンロードを始めた時刻(古い履歴はファイルの日時、分からなければ 0)。 */
        public long time;
        /** ウイルススキャンで見つかった検出名。 */
        @Nullable public String threat;
        /** ファイルが端末にある(または書き込み中)。 */
        boolean exists;
        int category = CAT_OTHER;

        public DownloadItem(long downloadId, String title, String description, int status, long downloadedSize, long totalSize, String localUri, String downloadUrl) {
            this.downloadId = downloadId;
            this.title = title;
            this.description = description;
            this.status = status;
            this.downloadedSize = downloadedSize;
            this.totalSize = totalSize;
            this.localUri = localUri;
            this.downloadUrl = downloadUrl;
            this.isPaused = false;
            this.filePath = "";
        }

        public int getProgress() {
            return totalSize > 0 ? (int) ((downloadedSize * 100) / totalSize) : 0;
        }

        boolean isActive() {
            return isPaused || status == DownloadManager.STATUS_RUNNING
                    || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED;
        }
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_FILE = 1;
    private static final int TYPE_IMAGE = 2;

    private static final class Row {
        final int type;
        @Nullable final String header;
        @Nullable final DownloadItem item;
        /** 画像のカードを横幅いっぱいに出す(同じ日付に画像が 1 枚だけの時)。 */
        boolean fullSpan = true;
        /** つながったカードの最初・最後の行(角を大きく丸める)。 */
        boolean segFirst = true, segLast = true;

        Row(int type, @Nullable String header, @Nullable DownloadItem item) {
            this.type = type;
            this.header = header;
            this.item = item;
        }
    }

    private class DownloadAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<Row> rows = new ArrayList<>();

        void setItems(List<DownloadItem> items) {
            rows.clear();
            long now = System.currentTimeMillis();
            String section = null;
            List<Row> imageRun = new ArrayList<>();
            Row lastFile = null;
            for (DownloadItem item : items) {
                String s = sectionOf(item, now);
                if (!s.equals(section)) {
                    closeImageRun(imageRun);
                    lastFile = null;
                    section = s;
                    rows.add(new Row(TYPE_HEADER, s, null));
                }
                if (showsAsImage(item)) {
                    Row r = new Row(TYPE_IMAGE, null, item);
                    imageRun.add(r);
                    rows.add(r);
                    lastFile = null;
                } else {
                    closeImageRun(imageRun);
                    Row r = new Row(TYPE_FILE, null, item);
                    // 続けて並んだファイルの行は 1 枚のカードのようにつなげる
                    if (lastFile != null) {
                        lastFile.segLast = false;
                        r.segFirst = false;
                    }
                    lastFile = r;
                    rows.add(r);
                }
            }
            closeImageRun(imageRun);
            notifyDataSetChanged();
        }

        /** 続けて並んだ画像が 2 枚以上なら 2 列に並べる。 */
        private void closeImageRun(List<Row> run) {
            boolean grid = run.size() > 1;
            for (Row r : run) r.fullSpan = !grid;
            run.clear();
        }

        int spanSizeAt(int position) {
            if (position < 0 || position >= rows.size()) return 2;
            Row r = rows.get(position);
            return r.type == TYPE_IMAGE && !r.fullSpan ? 1 : 2;
        }

        void notifyItemChanged(DownloadItem item) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).item == item) notifyItemChanged(i);
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderHolder(inflater.inflate(R.layout.item_download_header, parent, false));
            }
            if (viewType == TYPE_IMAGE) {
                return new ImageHolder(inflater.inflate(R.layout.item_download_image, parent, false));
            }
            return new FileHolder(inflater.inflate(R.layout.item_download, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).text.setText(row.header);
                return;
            }
            DownloadItem item = row.item;
            if (item == null) return;
            if (holder instanceof ImageHolder) {
                bindImage((ImageHolder) holder, row, item);
            } else {
                bindFile((FileHolder) holder, row, item);
            }
        }

        private void bindImage(ImageHolder h, Row row, DownloadItem item) {
            float density = getResources().getDisplayMetrics().density;
            ViewGroup.LayoutParams lp = h.thumb.getLayoutParams();
            int height = Math.round((row.fullSpan ? 220 : 150) * density);
            if (lp.height != height) {
                lp.height = height;
                h.thumb.setLayoutParams(lp);
            }
            h.title.setText(item.title);
            h.status.setText(subtitleOf(item));
            h.menu.setOnClickListener(v -> showItemMenu(v, item));
            h.itemView.setOnClickListener(v -> onItemClicked(item));

            Bitmap bm = thumbs.get(item.downloadId);
            h.thumb.setImageBitmap(bm);
            if (bm == null && thumbLoading.add(item.downloadId)) {
                int maxSize = getResources().getDisplayMetrics().widthPixels / (row.fullSpan ? 1 : 2);
                thumbExecutor.execute(() -> {
                    Bitmap decoded = decodeThumbnail(item, Math.max(256, maxSize));
                    updateHandler.post(() -> {
                        thumbLoading.remove(item.downloadId);
                        if (decoded == null || isDestroyed()) return;
                        thumbs.put(item.downloadId, decoded);
                        notifyItemChanged(item);
                    });
                });
            }
        }

        private void bindFile(FileHolder h, Row row, DownloadItem item) {
            int onVariant = MaterialColors.getColor(h.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant);
            int error = MaterialColors.getColor(h.itemView, androidx.appcompat.R.attr.colorError);

            // つながったカード: 端の行だけ角を大きく丸める
            float d = getResources().getDisplayMetrics().density;
            float big = 22 * d, small = 6 * d;
            float top = row.segFirst ? big : small, bottom = row.segLast ? big : small;
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(MaterialColors.getColor(h.itemView, com.google.android.material.R.attr.colorSurfaceContainer));
            bg.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
            android.graphics.drawable.GradientDrawable mask = new android.graphics.drawable.GradientDrawable();
            mask.setColor(0xFFFFFFFF);
            mask.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
            h.itemView.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(MaterialColors.getColor(h.itemView,
                            androidx.appcompat.R.attr.colorControlHighlight)), bg, mask));

            h.title.setText(item.title);
            h.status.setText(subtitleOf(item));
            h.status.setTextColor(item.threat != null ? error : onVariant);

            int icon;
            if (item.threat != null) {
                icon = R.drawable.ic_dl_threat;
            } else {
                icon = CAT_ICONS[item.category] != 0 ? CAT_ICONS[item.category] : R.drawable.ic_dl_file;
            }
            h.icon.setImageResource(icon);
            // 種類ごとにアイコンの下地の色を変える
            int[] colors = categoryColors(h.itemView, item);
            h.iconBg.setBackgroundTintList(ColorStateList.valueOf(colors[0]));
            h.icon.setImageTintList(ColorStateList.valueOf(colors[1]));

            boolean running = item.isActive();
            h.progress.setVisibility(running ? View.VISIBLE : View.GONE);
            if (running) {
                boolean indeterminate = item.totalSize <= 0 || item.status == DownloadManager.STATUS_PENDING;
                if (h.progress.isIndeterminate() != indeterminate) {
                    // 表示中は不確定表示を切り替えられないので、一度隠してから切り替える
                    h.progress.setVisibility(View.INVISIBLE);
                    h.progress.setIndeterminate(indeterminate);
                    h.progress.setVisibility(View.VISIBLE);
                }
                if (!indeterminate) h.progress.setProgressCompat(item.getProgress(), false);
            }

            // ブラウザ経由のダウンロード中は、メニューの代わりにキャンセル(×)
            // 失敗したダウンロードは、メニューの代わりに再試行(↻)。削除は長押しのメニューから
            boolean cancelOnly = running && item.downloadId < 0;
            boolean retry = canRetry(item);
            h.menu.setImageResource(retry ? R.drawable.ic_ps_refresh
                    : cancelOnly ? R.drawable.ic_ts_close : R.drawable.ic_pm_more);
            h.menu.setImageTintList(ColorStateList.valueOf(retry
                    ? MaterialColors.getColor(h.itemView, androidx.appcompat.R.attr.colorPrimary) : onVariant));
            h.menu.setContentDescription(retry ? Lang.t("再試行")
                    : cancelOnly ? Lang.t("キャンセル") : Lang.t("その他の操作"));
            h.menu.setOnClickListener(v -> showItemMenu(v, item));
            h.menu.setOnLongClickListener(retry ? v -> {
                showOtherMenu(v, item);
                return true;
            } : null);
            h.itemView.setOnClickListener(v -> onItemClicked(item));
        }
    }

    /** 種類ごとのアイコンの下地の色と、アイコンの色。 */
    private static int[] categoryColors(View v, DownloadItem item) {
        int bg, fg;
        if (item.threat != null || (item.status == DownloadManager.STATUS_FAILED)) {
            bg = com.google.android.material.R.attr.colorErrorContainer;
            fg = com.google.android.material.R.attr.colorOnErrorContainer;
        } else {
            switch (item.category) {
                case CAT_VIDEO:
                    bg = com.google.android.material.R.attr.colorTertiaryContainer;
                    fg = com.google.android.material.R.attr.colorOnTertiaryContainer;
                    break;
                case CAT_AUDIO:
                    bg = com.google.android.material.R.attr.colorSecondaryContainer;
                    fg = com.google.android.material.R.attr.colorOnSecondaryContainer;
                    break;
                case CAT_IMAGE:
                case CAT_PAGE:
                    bg = com.google.android.material.R.attr.colorPrimaryContainer;
                    fg = com.google.android.material.R.attr.colorOnPrimaryContainer;
                    break;
                default:
                    bg = com.google.android.material.R.attr.colorSurfaceContainerHighest;
                    fg = com.google.android.material.R.attr.colorOnSurfaceVariant;
                    break;
            }
        }
        return new int[]{MaterialColors.getColor(v, bg), MaterialColors.getColor(v, fg)};
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView text;

        HeaderHolder(View v) {
            super(v);
            text = v.findViewById(R.id.dlHeader);
        }
    }

    private static final class FileHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final View iconBg;
        final TextView title, status;
        final LinearProgressIndicator progress;
        final ImageButton menu;

        FileHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.fileIcon);
            iconBg = v.findViewById(R.id.fileIconBg);
            title = v.findViewById(R.id.fileTitle);
            status = v.findViewById(R.id.fileStatus);
            progress = v.findViewById(R.id.progressBar);
            menu = v.findViewById(R.id.fileMenu);
        }
    }

    private static final class ImageHolder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView title, status;
        final ImageButton menu;

        ImageHolder(View v) {
            super(v);
            thumb = v.findViewById(R.id.dlThumb);
            title = v.findViewById(R.id.fileTitle);
            status = v.findViewById(R.id.fileStatus);
            menu = v.findViewById(R.id.fileMenu);
            // カード全体を押せるようにする(角丸の内側に波紋を出す)
            v.findViewById(R.id.dlImageCard).setOnClickListener(x -> v.performClick());
        }
    }
}
