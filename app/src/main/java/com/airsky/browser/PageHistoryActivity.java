package com.airsky.browser;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 履歴(Pixel の設定アプリ風)。
 *
 * <ul>
 *   <li>大きな見出しがスクロールで小さくなる。その下に検索欄と、期間(すべて・今日・昨日・7 日間・それ以前)と
 *       「サイトごと」の絞り込みのチップ。</li>
 *   <li>日付(またはサイト)ごとのまとまりを、角の丸いカードを 2dp 間隔で積み重ねて見せる。
 *       まとまりの最初と最後だけ大きく丸める(Android 16 の設定画面と同じ形)。</li>
 *   <li>押すと開く / 長押しで複数選択 / 左右にスワイプで削除(元に戻せる) / ︙ でその他の操作。</li>
 *   <li>「履歴を削除」は 過去 1 時間・今日・すべて から選べる。</li>
 * </ul>
 * 結果は "history_list"(編集後の全件)と、選んだページの "selected_url" / "historyAddNewTab" で返す。
 */
public class PageHistoryActivity extends AppCompatActivity {

    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final int RANGE_ALL = 0, RANGE_TODAY = 1, RANGE_YESTERDAY = 2, RANGE_WEEK = 3, RANGE_OLDER = 4;
    private static final String[] RANGE_LABELS = {Lang.t("すべて"), Lang.t("今日"), Lang.t("昨日"), Lang.t("過去 7 日間"), Lang.t("それ以前")};

    /** 全件(古い → 新しい の順。MainActivity と同じ並び)。 */
    private ArrayList<MainActivity.HistoryItem> items;
    private boolean edited;

    private RecyclerView list;
    private View emptyView;
    private TextView emptyText, emptySub;
    private MaterialToolbar toolbar;
    private CollapsingToolbarLayout collapsing;
    private EditText search;
    private final Adapter adapter = new Adapter();

    private String query = "";
    private int range = RANGE_ALL;
    private boolean bySite;
    /** 複数選択中の履歴(同じ項目を指す参照)。 */
    private final Set<MainActivity.HistoryItem> selected = new HashSet<>();

    private final ExecutorService iconLoader = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> icons = new LruCache<>(120);
    /** 読み込んだが無かった URL(何度も探しに行かない)。 */
    private final Set<String> missingIcons = new HashSet<>();

    private float dp;
    private int cardColor, selectedColor, onSurface, onVariant, primary;

    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_list);
        TouchEffectView.attach(getWindow());
        dp = getResources().getDisplayMetrics().density;
        cardColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceBright, Color.WHITE);
        selectedColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer, Color.LTGRAY);
        onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        onVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.BLUE);

        items = (ArrayList<MainActivity.HistoryItem>) getIntent().getSerializableExtra("history_list");
        if (items == null) items = new ArrayList<>();

        toolbar = findViewById(R.id.hiToolbar);
        collapsing = findViewById(R.id.hiCollapsing);
        list = findViewById(R.id.hiList);
        emptyView = findViewById(R.id.hiEmpty);
        emptyText = findViewById(R.id.hiEmptyText);
        emptySub = findViewById(R.id.hiEmptySub);
        search = findViewById(R.id.hiSearch);

        toolbar.setNavigationOnClickListener(v -> onBack());
        setupMenu();
        setupSearch();
        setupChips();

        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        list.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator() {
            {
                setAddDuration(220);
                setRemoveDuration(180);
                setMoveDuration(260);
                setChangeDuration(160);
            }
        });
        new ItemTouchHelper(new SwipeToDelete()).attachToRecyclerView(list);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBack();
            }
        });

        rebuild(true);
    }

    @Override
    protected void onDestroy() {
        iconLoader.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // 見出し・検索・チップ
    // ------------------------------------------------------------------

    private void setupMenu() {
        Menu m = toolbar.getMenu();
        m.clear();
        if (!selected.isEmpty()) {
            m.add(0, 1, 0, Lang.t("すべて選択")).setIcon(R.drawable.ic_tg_select).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
            m.add(0, 2, 1, Lang.t("削除")).setIcon(R.drawable.ic_si_delete).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        } else {
            m.add(0, 3, 0, Lang.t("履歴を削除")).setIcon(R.drawable.ic_si_delete).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        for (int i = 0; i < m.size(); i++) {
            if (m.getItem(i).getIcon() != null) {
                m.getItem(i).getIcon().mutate().setTint(onSurface);
            }
        }
        toolbar.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: selectAllVisible(); return true;
                case 2: deleteSelected(); return true;
                case 3: showClearDialog(); return true;
            }
            return false;
        });
    }

    private void updateTitle() {
        if (!selected.isEmpty()) {
            collapsing.setTitle(selected.size() + Lang.t(" 件を選択"));
            toolbar.setNavigationIcon(R.drawable.ic_ts_close);
            toolbar.setNavigationContentDescription(Lang.t("選択をやめる"));
        } else {
            collapsing.setTitle(Lang.t("履歴"));
            toolbar.setNavigationIcon(R.drawable.ic_si_back);
            toolbar.setNavigationContentDescription(Lang.t("戻る"));
        }
        if (toolbar.getNavigationIcon() != null) toolbar.getNavigationIcon().mutate().setTint(onSurface);
    }

    private void setupSearch() {
        View clear = findViewById(R.id.hiSearchClear);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                rebuild(false);
            }
        });
        clear.setOnClickListener(v -> search.setText(""));
    }

    private void setupChips() {
        ChipGroup group = findViewById(R.id.hiRangeChips);
        for (int i = 0; i < RANGE_LABELS.length; i++) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_history_chip, group, false);
            chip.setId(View.generateViewId());
            chip.setText(RANGE_LABELS[i]);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(true);
            chip.setTag(i);
            chip.setChecked(i == RANGE_ALL);
            group.addView(chip);
        }
        group.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            Chip c = g.findViewById(ids.get(0));
            range = (int) c.getTag();
            rebuild(true);
        });
        Chip site = findViewById(R.id.hiBySite);
        site.setOnCheckedChangeListener((b, checked) -> {
            bySite = checked;
            rebuild(true);
        });
    }

    // ------------------------------------------------------------------
    // 一覧の組み立て
    // ------------------------------------------------------------------

    private static final int TYPE_HEADER = 0, TYPE_ROW = 1;

    private static final class Entry {
        final int type;
        final String label;
        @Nullable final MainActivity.HistoryItem item;
        boolean first, last;

        Entry(String label) {
            type = TYPE_HEADER;
            this.label = label;
            item = null;
        }

        Entry(MainActivity.HistoryItem item) {
            type = TYPE_ROW;
            this.label = null;
            this.item = item;
        }
    }

    /** 絞り込み・まとめ方に合わせて一覧を作り直す。animate なら上から順に流れ込ませる。 */
    private void rebuild(boolean animate) {
        long todayStart = startOfDay(0);
        long yesterdayStart = startOfDay(-1);
        long weekStart = startOfDay(-6);

        // 新しい順に、表示するものだけ
        List<MainActivity.HistoryItem> shown = new ArrayList<>();
        for (int i = items.size() - 1; i >= 0; i--) {
            MainActivity.HistoryItem h = items.get(i);
            if (isInternal(h.getUrl())) continue;
            if (!matchesRange(h.getTime(), todayStart, yesterdayStart, weekStart)) continue;
            if (!query.isEmpty() && !matchesQuery(h)) continue;
            shown.add(h);
        }

        Map<String, List<MainActivity.HistoryItem>> groups = new LinkedHashMap<>();
        for (MainActivity.HistoryItem h : shown) {
            String key = bySite ? siteLabel(h.getUrl()) : dayLabel(h.getTime(), todayStart, yesterdayStart);
            List<MainActivity.HistoryItem> g = groups.get(key);
            if (g == null) {
                g = new ArrayList<>();
                groups.put(key, g);
            }
            g.add(h);
        }

        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, List<MainActivity.HistoryItem>> e : groups.entrySet()) {
            String label = e.getKey();
            if (bySite) label += "  ·  " + e.getValue().size() + Lang.t(" 件");
            entries.add(new Entry(label));
            List<MainActivity.HistoryItem> g = e.getValue();
            for (int i = 0; i < g.size(); i++) {
                Entry row = new Entry(g.get(i));
                row.first = i == 0;
                row.last = i == g.size() - 1;
                entries.add(row);
            }
        }
        adapter.setEntries(entries, animate);

        boolean empty = entries.isEmpty();
        if (empty) {
            boolean filtered = !query.isEmpty() || range != RANGE_ALL;
            emptyText.setText(filtered ? Lang.t("見つかりませんでした") : Lang.t("履歴はありません"));
            emptySub.setText(filtered ? Lang.t("ほかの言葉や期間で探してみてください") : Lang.t("見たページはここに日付ごとに並びます"));
        }
        if (empty != (emptyView.getVisibility() == View.VISIBLE)) {
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (empty) {
                View icon = findViewById(R.id.hiEmptyIconBg);
                icon.setScaleX(0.6f);
                icon.setScaleY(0.6f);
                icon.setRotation(-20f);
                icon.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(500)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
            }
        }
        if (animate && !empty) {
            LayoutAnimationController c = new LayoutAnimationController(
                    AnimationUtils.loadAnimation(this, R.anim.history_item_enter), 0.06f);
            c.setInterpolator(EMPHASIZED);
            list.setLayoutAnimation(c);
            list.scheduleLayoutAnimation();
            list.scrollToPosition(0);
        }
    }

    private boolean matchesRange(long t, long today, long yesterday, long week) {
        switch (range) {
            case RANGE_TODAY: return t >= today;
            case RANGE_YESTERDAY: return t >= yesterday && t < today;
            case RANGE_WEEK: return t >= week;
            case RANGE_OLDER: return t < week;
            default: return true;
        }
    }

    private boolean matchesQuery(MainActivity.HistoryItem h) {
        String t = h.getTitle() != null ? h.getTitle().toLowerCase(Locale.ROOT) : "";
        String u = h.getUrl() != null ? h.getUrl().toLowerCase(Locale.ROOT) : "";
        return t.contains(query) || u.contains(query);
    }

    private static boolean isInternal(@Nullable String url) {
        return url == null || url.startsWith("file:///android_asset/");
    }

    private static long startOfDay(int offsetDays) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, offsetDays);
        return c.getTimeInMillis();
    }

    private static String dayLabel(long t, long today, long yesterday) {
        if (t <= 0) return Lang.t("以前の履歴");
        if (t >= today) return Lang.t("今日");
        if (t >= yesterday) return Lang.t("昨日");
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        String pattern = c.get(Calendar.YEAR) == now.get(Calendar.YEAR) ? Lang.t("M月d日(E)") : Lang.t("yyyy年M月d日(E)");
        return new SimpleDateFormat(pattern, Locale.JAPAN).format(c.getTime());
    }

    private static String siteLabel(@Nullable String url) {
        String host = hostOf(url);
        return host != null ? host : Lang.t("その他");
    }

    @Nullable
    private static String hostOf(@Nullable String url) {
        if (url == null) return null;
        String host = Uri.parse(url).getHost();
        if (host == null || host.isEmpty()) return null;
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private String subtitleOf(MainActivity.HistoryItem h) {
        String host = hostOf(h.getUrl());
        String where = host != null ? host : h.getUrl();
        if (h.getTime() <= 0) return where;
        String time = new SimpleDateFormat(bySite ? "M/d HH:mm" : "HH:mm", Locale.JAPAN).format(h.getTime());
        return time + "  ·  " + where;
    }

    // ------------------------------------------------------------------
    // 操作
    // ------------------------------------------------------------------

    private void open(MainActivity.HistoryItem h, boolean newTab) {
        Intent data = resultData();
        data.putExtra(newTab ? "historyAddNewTab" : "selected_url", h.getUrl());
        setResult(RESULT_OK, data);
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }

    private Intent resultData() {
        Intent data = new Intent();
        if (edited) data.putExtra("history_list", items);
        return data;
    }

    private void markEdited() {
        edited = true;
        setResult(RESULT_OK, resultData());
    }

    private void onBack() {
        if (!selected.isEmpty()) {
            clearSelection();
            return;
        }
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }

    private void toggleSelected(MainActivity.HistoryItem h) {
        boolean wasEmpty = selected.isEmpty();
        if (!selected.remove(h)) selected.add(h);
        if (wasEmpty != selected.isEmpty()) setupMenu();
        updateTitle();
        adapter.notifyItemRangeChanged(0, adapter.getItemCount(), PAYLOAD_SELECTION);
    }

    private void clearSelection() {
        selected.clear();
        setupMenu();
        updateTitle();
        adapter.notifyItemRangeChanged(0, adapter.getItemCount(), PAYLOAD_SELECTION);
    }

    private void selectAllVisible() {
        for (Entry e : adapter.entries) if (e.item != null) selected.add(e.item);
        updateTitle();
        adapter.notifyItemRangeChanged(0, adapter.getItemCount(), PAYLOAD_SELECTION);
    }

    private void deleteSelected() {
        List<MainActivity.HistoryItem> doomed = new ArrayList<>(selected);
        selected.clear();
        setupMenu();
        updateTitle();
        deleteWithUndo(doomed, doomed.size() + Lang.t(" 件の履歴を削除しました"));
    }

    /** 消して、少しの間「元に戻す」を出す。 */
    private void deleteWithUndo(List<MainActivity.HistoryItem> doomed, String message) {
        if (doomed.isEmpty()) return;
        ArrayList<MainActivity.HistoryItem> before = new ArrayList<>(items);
        items.removeAll(doomed);
        markEdited();
        rebuild(false);
        Snackbar.make(findViewById(R.id.hiRoot), message, Snackbar.LENGTH_LONG)
                .setAction(Lang.t("元に戻す"), v -> {
                    items.clear();
                    items.addAll(before);
                    markEdited();
                    rebuild(false);
                })
                .show();
    }

    private void showClearDialog() {
        String[] choices = {Lang.t("過去 1 時間"), Lang.t("今日"), Lang.t("すべての期間")};
        final int[] which = {2};
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("履歴を削除"))
                .setIcon(R.drawable.ic_it_auto_delete)
                .setSingleChoiceItems(choices, 2, (d, w) -> which[0] = w)
                .setPositiveButton(Lang.t("削除"), (d, w) -> {
                    long from;
                    switch (which[0]) {
                        case 0: from = System.currentTimeMillis() - 60 * 60 * 1000L; break;
                        case 1: from = startOfDay(0); break;
                        default: from = Long.MIN_VALUE; break;
                    }
                    List<MainActivity.HistoryItem> doomed = new ArrayList<>();
                    for (MainActivity.HistoryItem h : items) {
                        if (from == Long.MIN_VALUE || h.getTime() >= from) doomed.add(h);
                    }
                    if (doomed.isEmpty()) {
                        Toast.makeText(this, Lang.t("削除する履歴はありません"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    deleteWithUndo(doomed, doomed.size() + Lang.t(" 件の履歴を削除しました"));
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    private void showRowMenu(MainActivity.HistoryItem h, View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, 1, 0, Lang.t("新しいタブで開く"));
        m.add(0, 2, 1, Lang.t("リンクをコピー"));
        m.add(0, 3, 2, Lang.t("共有・QR コード"));
        m.add(0, 4, 3, Lang.t("ブックマークに追加"));
        m.add(0, 5, 4, Lang.t("選択"));
        String host = hostOf(h.getUrl());
        if (host != null) m.add(0, 6, 5, host + Lang.t(" の履歴をすべて削除"));
        m.add(0, 7, 6, Lang.t("削除"));
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: open(h, true); break;
                case 2: {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("url", h.getUrl()));
                    if (android.os.Build.VERSION.SDK_INT < 33) {
                        Toast.makeText(this, Lang.t("リンクをコピーしました"), Toast.LENGTH_SHORT).show();
                    }
                    break;
                }
                case 3:
                    ShareSheet.show(this, h.getUrl(), h.getTitle(), icons.get(h.getUrl()), false);
                    break;
                case 4: saveBookmark(h.getUrl(), h.getTitle()); break;
                case 5: toggleSelected(h); break;
                case 6: {
                    List<MainActivity.HistoryItem> doomed = new ArrayList<>();
                    for (MainActivity.HistoryItem x : items) if (host.equals(hostOf(x.getUrl()))) doomed.add(x);
                    deleteWithUndo(doomed, host + Lang.t(" の履歴を ") + doomed.size() + Lang.t(" 件削除しました"));
                    break;
                }
                case 7: deleteWithUndo(java.util.Collections.singletonList(h), Lang.t("履歴を削除しました")); break;
            }
            return true;
        });
        pm.show();
    }

    /** 履歴の「ブックマークに追加」。登録済みなら削除する(以前と同じ切り替え)。保存先は前回のフォルダ。 */
    private void saveBookmark(String url, String title) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, Lang.t("ページを取得できません"), Toast.LENGTH_SHORT).show();
            return;
        }
        BookmarkStore store = BookmarkStore.get(this);
        BookmarkStore.Node existing = store.findBookmark(url);
        if (existing != null) {
            store.delete(existing.id);
            Toast.makeText(this, Lang.t("ブックマークを削除しました"), Toast.LENGTH_SHORT).show();
            return;
        }
        long folder = store.lastFolder();
        store.addBookmark(folder, title != null && !title.trim().isEmpty() ? title.trim() : url, url, null);
        Toast.makeText(this, Lang.t("「") + BookmarkViews.folderName(this, folder) + Lang.t("」に保存しました"),
                Toast.LENGTH_SHORT).show();
    }

    /** 左右にスワイプして削除。 */
    private final class SwipeToDelete extends ItemTouchHelper.SimpleCallback {
        SwipeToDelete() {
            super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        }

        @Override
        public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            if (!(vh instanceof RowHolder) || !selected.isEmpty()) return 0;
            return super.getSwipeDirs(rv, vh);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder a, @NonNull RecyclerView.ViewHolder b) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
            int pos = vh.getBindingAdapterPosition();
            if (pos < 0 || pos >= adapter.entries.size()) return;
            MainActivity.HistoryItem h = adapter.entries.get(pos).item;
            if (h != null) deleteWithUndo(java.util.Collections.singletonList(h), Lang.t("履歴を削除しました"));
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                float dX, float dY, int actionState, boolean active) {
            super.onChildDraw(c, rv, vh, dX, dY, actionState, active);
            // 引くほど薄く・小さくなって、指を離すと消えていく
            float f = Math.min(1f, Math.abs(dX) / (vh.itemView.getWidth() * 0.6f));
            vh.itemView.setAlpha(1f - f * 0.7f);
            float s = 1f - f * 0.05f;
            vh.itemView.setScaleX(s);
            vh.itemView.setScaleY(s);
        }

        @Override
        public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            super.clearView(rv, vh);
            vh.itemView.setAlpha(1f);
            vh.itemView.setScaleX(1f);
            vh.itemView.setScaleY(1f);
        }
    }

    // ------------------------------------------------------------------
    // ファビコン(ブラウザが files/favicons に保存したもの)
    // ------------------------------------------------------------------

    private void loadIcon(String url, ImageView target, View letter) {
        Bitmap cached = icons.get(url);
        if (cached != null) {
            showIcon(target, letter, cached, false);
            return;
        }
        if (missingIcons.contains(url)) return;
        target.setTag(url);
        iconLoader.execute(() -> {
            File f = new File(new File(getFilesDir(), "favicons"), faviconFilename(url));
            Bitmap b = f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
            main.post(() -> {
                if (b == null) {
                    missingIcons.add(url);
                    return;
                }
                icons.put(url, b);
                if (url.equals(target.getTag())) showIcon(target, letter, b, true);
            });
        });
    }

    private static void showIcon(ImageView target, View letter, Bitmap b, boolean fade) {
        target.setImageBitmap(b);
        target.setVisibility(View.VISIBLE);
        letter.setVisibility(View.GONE);
        if (fade) {
            target.setAlpha(0f);
            target.animate().alpha(1f).setDuration(180).start();
        }
    }

    /** MainActivity と同じ名前の付け方(URL の MD5)。 */
    private static String faviconFilename(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb + ".png";
        } catch (Exception e) {
            return url.hashCode() + ".png";
        }
    }

    // ------------------------------------------------------------------
    // 一覧
    // ------------------------------------------------------------------

    private static final Object PAYLOAD_SELECTION = new Object();

    /** サイトの名前から決まる、頭文字のタイルの色(Material の明るめの色)。 */
    private static final int[] LETTER_COLORS = {
            0xFFD3E3FD, 0xFFC4EED0, 0xFFFFDFA0, 0xFFF9DEDC, 0xFFE8DEF8, 0xFFC2E7FF, 0xFFFFD8E4, 0xFFDDE3EA
    };
    private static final int[] LETTER_TEXT = {
            0xFF041E49, 0xFF072711, 0xFF261900, 0xFF410E0B, 0xFF1D192B, 0xFF001D35, 0xFF31111D, 0xFF191C20
    };

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        List<Entry> entries = new ArrayList<>();

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        void setEntries(List<Entry> next, boolean replaceAll) {
            List<Entry> prev = entries;
            entries = next;
            if (replaceAll) {
                // 絞り込み・まとめ方を変えた: 全部を流れ込ませ直す
                notifyDataSetChanged();
                return;
            }
            // 削除・検索: 差分だけ動かす(消えた行は縮んで、下の行が詰まる)
            androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback() {
                @Override public int getOldListSize() { return prev.size(); }
                @Override public int getNewListSize() { return next.size(); }

                @Override
                public boolean areItemsTheSame(int o, int n) {
                    Entry a = prev.get(o), b = next.get(n);
                    if (a.type != b.type) return false;
                    return a.type == TYPE_HEADER ? a.label.equals(b.label) : a.item == b.item;
                }

                @Override
                public boolean areContentsTheSame(int o, int n) {
                    Entry a = prev.get(o), b = next.get(n);
                    return a.first == b.first && a.last == b.last && TextUtils.equals(a.label, b.label);
                }
            }).dispatchUpdatesTo(this);
        }

        @Override
        public int getItemViewType(int position) {
            return entries.get(position).type;
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                TextView t = new TextView(parent.getContext());
                t.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                t.setPadding(px(32), px(22), px(24), px(10));
                t.setTextColor(primary);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                if (android.os.Build.VERSION.SDK_INT >= 28) t.setAccessibilityHeading(true);
                return new RecyclerView.ViewHolder(t) {};
            }
            return new RowHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (!payloads.isEmpty() && holder instanceof RowHolder && payloads.contains(PAYLOAD_SELECTION)) {
                ((RowHolder) holder).bindSelection(entries.get(position), true);
                return;
            }
            onBindViewHolder(holder, position);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Entry e = entries.get(position);
            if (holder instanceof RowHolder) {
                ((RowHolder) holder).bind(e);
            } else {
                ((TextView) holder.itemView).setText(e.label);
            }
        }
    }

    private final class RowHolder extends RecyclerView.ViewHolder {
        final LinearLayout card;
        final View iconBg, check;
        final ImageView icon;
        final TextView letter, title, sub;
        final View more;
        final GradientDrawable bg = new GradientDrawable();
        @Nullable ValueAnimator radiusAnim;
        boolean shownSelected;

        RowHolder(View v) {
            super(v);
            card = v.findViewById(R.id.hrCard);
            iconBg = v.findViewById(R.id.hrIconBg);
            check = v.findViewById(R.id.hrCheck);
            icon = v.findViewById(R.id.hrIcon);
            letter = v.findViewById(R.id.hrLetter);
            title = v.findViewById(R.id.hrTitle);
            sub = v.findViewById(R.id.hrSub);
            more = v.findViewById(R.id.hrMore);
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            card.setForeground(getDrawable(tv.resourceId));
            card.setBackground(bg);
            card.setClipToOutline(true);
            GradientDrawable checkBg = new GradientDrawable();
            checkBg.setShape(GradientDrawable.OVAL);
            checkBg.setColor(primary);
            check.setBackground(checkBg);
        }

        void bind(Entry e) {
            MainActivity.HistoryItem h = e.item;
            if (h == null) return;
            String t = h.getTitle();
            title.setText(t != null && !t.trim().isEmpty() ? t : h.getUrl());
            sub.setText(subtitleOf(h));

            String host = hostOf(h.getUrl());
            String initial = host != null && !host.isEmpty() ? host.substring(0, 1).toUpperCase(Locale.ROOT) : "?";
            int ci = Math.abs((host != null ? host : "").hashCode()) % LETTER_COLORS.length;
            GradientDrawable ibg = new GradientDrawable();
            ibg.setShape(GradientDrawable.OVAL);
            ibg.setColor(LETTER_COLORS[ci]);
            iconBg.setBackground(ibg);
            letter.setText(initial);
            letter.setTextColor(LETTER_TEXT[ci]);
            letter.setVisibility(View.VISIBLE);
            icon.setVisibility(View.GONE);
            icon.setTag(null);
            loadIcon(h.getUrl(), icon, letter);

            // まとまりの中の位置で角の丸みを変え、まとまりの間は 2dp だけ空ける
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            lp.bottomMargin = e.last ? 0 : px(2);
            itemView.setLayoutParams(lp);

            itemView.setOnClickListener(null);
            card.setOnClickListener(v -> {
                if (!selected.isEmpty()) toggleSelected(h);
                else open(h, false);
            });
            card.setOnLongClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                toggleSelected(h);
                return true;
            });
            more.setOnClickListener(v -> showRowMenu(h, v));
            bindSelection(e, false);
        }

        void bindSelection(Entry e, boolean animate) {
            boolean sel = e.item != null && selected.contains(e.item);
            more.setVisibility(selected.isEmpty() ? View.VISIBLE : View.INVISIBLE);
            float big = px(24), small = px(6);
            float[] target = sel
                    ? new float[]{big, big, big, big}
                    : new float[]{e.first ? big : small, e.first ? big : small, e.last ? big : small, e.last ? big : small};
            bg.setColor(sel ? selectedColor : cardColor);
            if (radiusAnim != null) radiusAnim.cancel();
            if (animate && sel != shownSelected) {
                // 選ぶとカードが丸く膨らみ、外すと元の形に戻る
                float[] from = currentRadii(e);
                radiusAnim = ValueAnimator.ofFloat(0f, 1f);
                radiusAnim.setDuration(300);
                radiusAnim.setInterpolator(EMPHASIZED);
                radiusAnim.addUpdateListener(a -> {
                    float f = (float) a.getAnimatedValue();
                    setRadii(from[0] + (target[0] - from[0]) * f, from[1] + (target[1] - from[1]) * f,
                            from[2] + (target[2] - from[2]) * f, from[3] + (target[3] - from[3]) * f);
                });
                radiusAnim.start();
                View show = sel ? check : iconBg;
                View hide = sel ? iconBg : check;
                show.setVisibility(View.VISIBLE);
                show.setScaleX(0.4f);
                show.setScaleY(0.4f);
                show.setRotation(sel ? -90f : 90f);
                show.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(320)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
                hide.setVisibility(View.GONE);
                card.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).withEndAction(() ->
                        card.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(EMPHASIZED).start()).start();
            } else {
                setRadii(target[0], target[1], target[2], target[3]);
                check.setVisibility(sel ? View.VISIBLE : View.GONE);
                iconBg.setVisibility(sel ? View.GONE : View.VISIBLE);
                check.setScaleX(1f);
                check.setScaleY(1f);
                iconBg.setScaleX(1f);
                iconBg.setScaleY(1f);
            }
            shownSelected = sel;
        }

        private float[] currentRadii(Entry e) {
            float[] r = bg.getCornerRadii();
            if (r == null) return new float[]{px(6), px(6), px(6), px(6)};
            return new float[]{r[0], r[2], r[4], r[6]};
        }

        private void setRadii(float tl, float tr, float br, float bl) {
            bg.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
        }
    }

    private int px(float v) {
        return Math.round(v * dp);
    }
}
