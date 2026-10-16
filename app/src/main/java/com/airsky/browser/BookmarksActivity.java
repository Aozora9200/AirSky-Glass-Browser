package com.airsky.browser;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ブックマーク。一番上に「ブックマーク」「その他のブックマーク」「リーディングリスト」の 3 つのフォルダを置き、
 * その中を入れ子のフォルダで整理できる。フォルダは角丸のタイル(アイコンと中身の数)で見せる。
 * リーディングリストは未読・既読に分けて並べる。
 */
public class BookmarksActivity extends AppCompatActivity {

    /** シークレット画面から開かれた(選んだページはシークレットタブで開く)。 */
    static final String EXTRA_SECRET = "com.airsky.browser.extra.FROM_SECRET";
    /** 最初に開くフォルダ(リーディングリストなど)。 */
    static final String EXTRA_FOLDER = "com.airsky.browser.extra.BOOKMARK_FOLDER";

    private static final int REQUEST_MOVE = 1;
    private static final int UNDO_MS = 5000;

    /**
     * 選んだページを開くブラウザ画面への Intent。シークレットから開かれていれば
     * シークレット画面へ戻して開く(通常画面へ CLEAR_TOP するとシークレット画面が閉じてしまう)。
     */
    static Intent openPageIntent(@NonNull android.app.Activity from, @NonNull String url) {
        boolean secret = from.getIntent().getBooleanExtra(EXTRA_SECRET, false);
        Intent intent = new Intent(from, secret ? SecretActivity.class : MainActivity.class);
        intent.putExtra("url", url);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    private BookmarkStore store;
    private long folder = BookmarkStore.ROOT;
    private String query = "";

    private RecyclerView list;
    private View emptyView;
    private TextView titleView;
    private EditText searchField;
    private View searchClear, sortButton, newFolderButton;
    private View pathScroll;
    private LinearLayout pathView;
    private final Adapter adapter = new Adapter();
    /** 「元に戻す」を待っている削除(一覧には出さない)。 */
    private final Set<Long> pendingDelete = new LinkedHashSet<>();
    /** 移動先を選んでいるブックマーク。 */
    private long movingId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);
        TouchEffectView.attach(getWindow());
        store = BookmarkStore.get(this);

        list = findViewById(R.id.bmList);
        emptyView = findViewById(R.id.bmEmpty);
        titleView = findViewById(R.id.bmTitle);
        searchField = findViewById(R.id.bmSearch);
        searchClear = findViewById(R.id.bmSearchClear);
        sortButton = findViewById(R.id.bmSort);
        newFolderButton = findViewById(R.id.bmNewFolder);
        pathScroll = findViewById(R.id.bmPathScroll);
        pathView = findViewById(R.id.bmPath);

        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.bmBack).setOnClickListener(v -> goBack());
        findViewById(R.id.bmClose).setOnClickListener(v -> finish());
        sortButton.setOnClickListener(this::showSortMenu);
        newFolderButton.setOnClickListener(v -> askNewFolder());
        searchClear.setOnClickListener(v -> searchField.setText(""));
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                searchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                reload();
            }
        });

        long start = getIntent().getLongExtra(EXTRA_FOLDER, BookmarkStore.ROOT);
        BookmarkStore.Node n = store.node(start);
        open(n != null && n.folder ? start : BookmarkStore.ROOT, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 編集・移動から戻った時に最新にする(消えたフォルダにいたら一番上へ)
        if (folder != BookmarkStore.ROOT && store.node(folder) == null) folder = BookmarkStore.ROOT;
        reload();
    }

    @Override
    protected void onDestroy() {
        // 「元に戻す」を待っていた削除は、画面を閉じた時点で確定する
        for (long id : new ArrayList<>(pendingDelete)) store.delete(id);
        pendingDelete.clear();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }

    private void goBack() {
        if (!query.isEmpty()) {
            searchField.setText("");
            return;
        }
        if (folder == BookmarkStore.ROOT) {
            finish();
            return;
        }
        BookmarkStore.Node n = store.node(folder);
        open(n != null ? n.parent : BookmarkStore.ROOT, -1);
    }

    // ------------------------------------------------------------------
    // 表示
    // ------------------------------------------------------------------

    /** フォルダを開く。direction: 1 = 奥へ、-1 = 手前へ(動きの向き)、0 = 動かさない。 */
    private void open(long id, int direction) {
        folder = id;
        reload();
        list.scrollToPosition(0);
        if (direction != 0) {
            float d = getResources().getDisplayMetrics().density;
            list.animate().cancel();
            list.setAlpha(0f);
            list.setTranslationX(direction * 40 * d);
            list.animate().alpha(1f).translationX(0f).setDuration(260)
                    .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
        } else {
            playFirstShow();
        }
    }

    /** 初めて出す時は、行を上から順にふわっと出す。 */
    private void playFirstShow() {
        android.view.animation.AnimationSet set = new android.view.animation.AnimationSet(true);
        set.addAnimation(new android.view.animation.AlphaAnimation(0f, 1f));
        set.addAnimation(new android.view.animation.TranslateAnimation(
                android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.4f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0f));
        set.setDuration(340);
        set.setInterpolator(new DecelerateInterpolator(2f));
        list.setLayoutAnimation(new android.view.animation.LayoutAnimationController(set, 0.15f));
        list.scheduleLayoutAnimation();
    }

    private void reload() {
        BookmarkStore.Node current = folder == BookmarkStore.ROOT ? null : store.node(folder);
        titleView.setText(current != null ? current.title : Lang.t("ブックマーク"));
        // 一番上とリーディングリストにはフォルダを作れない。一番上は並べ替えない
        boolean canFolder = folder != BookmarkStore.ROOT && folder != BookmarkStore.ROOT_READING;
        newFolderButton.setVisibility(canFolder ? View.VISIBLE : View.GONE);
        sortButton.setVisibility(folder != BookmarkStore.ROOT ? View.VISIBLE : View.GONE);
        bindPath();

        List<Row> rows = new ArrayList<>();
        if (!query.isEmpty()) {
            List<BookmarkStore.Node> found = store.search(query, folder);
            List<BookmarkStore.Node> shown = new ArrayList<>();
            for (BookmarkStore.Node n : found) if (!pendingDelete.contains(n.id)) shown.add(n);
            if (!shown.isEmpty()) rows.add(Row.header(Lang.t("検索結果 ・ ") + shown.size() + Lang.t(" 件")));
            for (BookmarkStore.Node n : shown) rows.add(Row.node(n, 0));
        } else if (folder == BookmarkStore.ROOT) {
            for (BookmarkStore.Node n : store.children(BookmarkStore.ROOT, BookmarkStore.SORT_NEWEST)) {
                rows.add(Row.root(n, visibleCount(n.id)));
            }
        } else {
            List<BookmarkStore.Node> kids = store.children(folder, store.sortMode());
            if (folder == BookmarkStore.ROOT_READING) {
                List<Row> unread = new ArrayList<>(), read = new ArrayList<>();
                for (BookmarkStore.Node n : kids) {
                    if (pendingDelete.contains(n.id)) continue;
                    (n.read ? read : unread).add(Row.node(n, 0));
                }
                if (!unread.isEmpty()) {
                    rows.add(Row.header(Lang.t("未読 ・ ") + unread.size()));
                    rows.addAll(unread);
                }
                if (!read.isEmpty()) {
                    rows.add(Row.header(Lang.t("既読 ・ ") + read.size()));
                    rows.addAll(read);
                }
            } else {
                for (BookmarkStore.Node n : kids) {
                    if (pendingDelete.contains(n.id)) continue;
                    rows.add(Row.node(n, n.folder ? visibleCount(n.id) : 0));
                }
            }
        }
        adapter.setRows(rows);
        bindEmpty(rows.isEmpty());
    }

    private int visibleCount(long id) {
        int count = store.count(id);
        for (long p : pendingDelete) {
            BookmarkStore.Node n = store.node(p);
            if (n != null && n.parent == id) count--;
        }
        return Math.max(0, count);
    }

    /** 一覧が空の時の絵と説明。リーディングリストは使い方も説明する。 */
    private void bindEmpty(boolean empty) {
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (!empty) return;
        TextView title = findViewById(R.id.bmEmptyTitle);
        TextView body = findViewById(R.id.bmEmptyBody);
        ImageView icon = findViewById(R.id.bmEmptyIcon);
        BookmarkStore.Node current = store.node(folder);
        int[] colors = current != null ? BookmarkViews.folderColors(emptyView, current)
                : BookmarkViews.folderColors(emptyView, store.node(BookmarkStore.ROOT_BOOKMARKS));
        View front = findViewById(R.id.bmEmptyFront);
        front.setBackgroundTintList(ColorStateList.valueOf(colors[0]));
        int back = (colors[0] & 0x00FFFFFF) | 0x66000000;
        findViewById(R.id.bmEmptyBack1).setBackgroundTintList(ColorStateList.valueOf(back));
        findViewById(R.id.bmEmptyBack2).setBackgroundTintList(ColorStateList.valueOf(back));
        icon.setImageTintList(ColorStateList.valueOf(colors[1]));
        if (!query.isEmpty()) {
            icon.setImageResource(R.drawable.ic_ts_search);
            title.setText(Lang.t("見つかりませんでした"));
            body.setText(Lang.t("「") + query + Lang.t("」に一致するブックマークはありません"));
        } else if (folder == BookmarkStore.ROOT_READING) {
            icon.setImageResource(R.drawable.ic_bm_reading);
            title.setText(Lang.t("リーディングリストはここに表示されます"));
            body.setText(Lang.t("ページのメニューの「リーディングリストに追加」で、後で読みたいページをためておけます"));
        } else {
            icon.setImageResource(current != null ? BookmarkStore.iconOf(current) : R.drawable.ic_pm_bookmark);
            title.setText(Lang.t("このフォルダは空です"));
            body.setText(Lang.t("ページのメニューの「ブックマークに追加」で保存したページが、ここに並びます"));
        }
        // 絵の板を少しずつずらして持ち上げる
        View art = findViewById(R.id.bmEmptyArt);
        art.setScaleX(0.8f);
        art.setScaleY(0.8f);
        art.setAlpha(0f);
        art.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(420)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
    }

    /** 今いるフォルダまでの道筋。押したフォルダへ戻る。 */
    private void bindPath() {
        pathView.removeAllViews();
        if (folder == BookmarkStore.ROOT || !query.isEmpty()) {
            pathScroll.setVisibility(View.GONE);
            return;
        }
        List<BookmarkStore.Node> path = store.pathTo(folder);
        if (path.size() < 2) {
            pathScroll.setVisibility(View.GONE);
            return;
        }
        pathScroll.setVisibility(View.VISIBLE);
        int primary = MaterialColors.getColor(pathView, androidx.appcompat.R.attr.colorPrimary);
        int variant = MaterialColors.getColor(pathView, com.google.android.material.R.attr.colorOnSurfaceVariant);
        for (int i = 0; i < path.size(); i++) {
            BookmarkStore.Node n = path.get(i);
            boolean last = i == path.size() - 1;
            TextView t = new TextView(this);
            t.setText(n.title);
            t.setTextSize(13);
            t.setTextColor(last ? variant : primary);
            t.setPadding(dp(4), dp(6), dp(4), dp(6));
            if (!last) {
                t.setBackgroundResource(R.drawable.ripple_circle_small);
                t.setOnClickListener(v -> open(n.id, -1));
            }
            pathView.addView(t);
            if (!last) {
                TextView sep = new TextView(this);
                sep.setText("›");
                sep.setTextSize(14);
                sep.setTextColor(variant);
                sep.setPadding(dp(2), 0, dp(2), 0);
                pathView.addView(sep);
            }
        }
        pathScroll.post(() -> pathScroll.scrollTo(pathView.getWidth(), 0));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------
    // 操作
    // ------------------------------------------------------------------

    private void onNodeClicked(BookmarkStore.Node n) {
        if (n.folder) {
            if (!query.isEmpty()) searchField.setText("");
            open(n.id, 1);
            return;
        }
        if (n.url == null) return;
        if (store.rootOf(n.id) == BookmarkStore.ROOT_READING) store.setRead(n.id, true);
        startActivity(openPageIntent(this, n.url));
        super.finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }

    private void showSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String[] labels = {Lang.t("新しい順"), Lang.t("古い順"), Lang.t("名前順")};
        int current = store.sortMode();
        for (int i = 0; i < labels.length; i++) {
            menu.getMenu().add(0, i, i, labels[i]).setCheckable(true).setChecked(i == current);
        }
        menu.getMenu().setGroupCheckable(0, true, true);
        menu.setOnMenuItemClickListener(item -> {
            store.setSortMode(item.getItemId());
            reload();
            return true;
        });
        menu.show();
    }

    private void askNewFolder() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(Lang.t("フォルダ名"));
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("新しいフォルダ"))
                .setView(box)
                .setNegativeButton(Lang.t("キャンセル"), null)
                .setPositiveButton(Lang.t("作成"), (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    store.addFolder(folder, name);
                    reload();
                })
                .show();
        input.requestFocus();
    }

    /** ブックマーク・フォルダを押し続けた時/︙ボタンの操作シート。 */
    private void showActions(BookmarkStore.Node n) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(dialog.getContext()).inflate(R.layout.sheet_bm_actions, null);
        dialog.setContentView(sheet);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        ((TextView) sheet.findViewById(R.id.bmActionsTitle)).setText(n.title.isEmpty() ? n.url : n.title);
        TextView sub = sheet.findViewById(R.id.bmActionsSubtitle);
        sub.setText(n.folder ? store.count(n.id) + Lang.t(" 件") : n.url);

        LinearLayout actions = sheet.findViewById(R.id.bmActions);
        boolean reading = !n.folder && store.rootOf(n.id) == BookmarkStore.ROOT_READING;
        if (n.folder) {
            addAction(actions, dialog, R.drawable.ic_bm_folder, Lang.t("開く"), () -> onNodeClicked(n));
        } else {
            addAction(actions, dialog, R.drawable.ic_si_open, Lang.t("開く"), () -> onNodeClicked(n));
        }
        if (!n.isRoot()) {
            addAction(actions, dialog, R.drawable.ic_omni_edit, Lang.t("編集"), () -> edit(n));
            addAction(actions, dialog, R.drawable.ic_bm_move, Lang.t("移動"), () -> chooseMove(n));
        }
        if (reading) {
            addAction(actions, dialog, R.drawable.ic_dl_check, n.read ? Lang.t("未読にする") : Lang.t("既読にする"), () -> {
                store.setRead(n.id, !n.read);
                reload();
            });
        } else if (!n.folder && n.url != null && store.findReading(n.url) == null) {
            addAction(actions, dialog, R.drawable.ic_bm_reading, Lang.t("リーディングリストに追加"), () -> {
                store.addBookmark(BookmarkStore.ROOT_READING, n.title, n.url, n.thumb);
                Toast.makeText(this, Lang.t("リーディングリストに追加しました"), Toast.LENGTH_SHORT).show();
            });
        }
        if (!n.folder && n.url != null) {
            addAction(actions, dialog, R.drawable.ic_pm_share, Lang.t("共有"), () -> {
                Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, n.url);
                startActivity(Intent.createChooser(send, Lang.t("共有")));
            });
        }
        if (!n.isRoot()) {
            addAction(actions, dialog, R.drawable.ic_si_delete, Lang.t("削除"), () -> confirmDelete(n));
        }
        dialog.show();
    }

    private void addAction(LinearLayout parent, BottomSheetDialog dialog, int icon, String label, Runnable action) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bm_action, parent, false);
        ImageView iv = row.findViewById(R.id.bmActionIcon);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(
                MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurfaceVariant)));
        ((TextView) row.findViewById(R.id.bmActionLabel)).setText(label);
        row.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        parent.addView(row);
    }

    private void edit(BookmarkStore.Node n) {
        startActivity(new Intent(this, BookmarkEditActivity.class).putExtra(BookmarkEditActivity.EXTRA_ID, n.id));
        overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
    }

    private void chooseMove(BookmarkStore.Node n) {
        movingId = n.id;
        startActivityForResult(new Intent(this, BookmarkFolderPickerActivity.class)
                .putExtra(BookmarkFolderPickerActivity.EXTRA_MOVING, n.id)
                .putExtra(BookmarkFolderPickerActivity.EXTRA_START, n.parent), REQUEST_MOVE);
        overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_MOVE || resultCode != RESULT_OK || data == null || movingId < 0) return;
        long target = data.getLongExtra(BookmarkFolderPickerActivity.EXTRA_RESULT, -1);
        if (target >= 0 && store.move(movingId, target)) {
            Toast.makeText(this, Lang.t("「") + BookmarkViews.folderName(this, target) + Lang.t("」に移動しました"),
                    Toast.LENGTH_SHORT).show();
        }
        movingId = -1;
        reload();
    }

    /** フォルダは中身があれば確かめてから、ブックマークはすぐ消して「元に戻す」を出す。 */
    private void confirmDelete(BookmarkStore.Node n) {
        int count = n.folder ? store.count(n.id) : 0;
        if (n.folder && count > 0) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(Lang.t("「") + n.title + Lang.t("」を削除しますか？"))
                    .setMessage(Lang.t("中の ") + count + Lang.t(" 件も削除されます"))
                    .setNegativeButton(Lang.t("キャンセル"), null)
                    .setPositiveButton(Lang.t("削除"), (d, w) -> deleteWithUndo(n))
                    .show();
        } else {
            deleteWithUndo(n);
        }
    }

    private void deleteWithUndo(BookmarkStore.Node n) {
        pendingDelete.add(n.id);
        reload();
        Snackbar bar = Snackbar.make(findViewById(R.id.bmRoot), Lang.t("「") + n.title + Lang.t("」を削除しました"), UNDO_MS)
                .setAction(Lang.t("元に戻す"), v -> {
                    pendingDelete.remove(n.id);
                    reload();
                });
        bar.addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
            @Override
            public void onDismissed(Snackbar sb, int event) {
                if (event != DISMISS_EVENT_ACTION && pendingDelete.remove(n.id)) {
                    store.delete(n.id);
                    if (!isDestroyed()) reload();
                }
            }
        });
        bar.show();
    }

    // ------------------------------------------------------------------
    // 一覧
    // ------------------------------------------------------------------

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROOT = 1;
    private static final int TYPE_FOLDER = 2;
    private static final int TYPE_URL = 3;

    private static final class Row {
        final int type;
        @Nullable final String header;
        @Nullable final BookmarkStore.Node node;
        final int count;

        private Row(int type, @Nullable String header, @Nullable BookmarkStore.Node node, int count) {
            this.type = type;
            this.header = header;
            this.node = node;
            this.count = count;
        }

        static Row header(String text) {
            return new Row(TYPE_HEADER, text, null, 0);
        }

        static Row root(BookmarkStore.Node n, int count) {
            return new Row(TYPE_ROOT, null, n, count);
        }

        static Row node(BookmarkStore.Node n, int count) {
            return new Row(n.folder ? TYPE_FOLDER : TYPE_URL, null, n, count);
        }
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Row> rows = new ArrayList<>();

        void setRows(List<Row> newRows) {
            rows.clear();
            rows.addAll(newRows);
            notifyDataSetChanged();
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
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            int layout;
            switch (viewType) {
                case TYPE_HEADER:
                    layout = R.layout.item_bm_header;
                    break;
                case TYPE_URL:
                    layout = R.layout.item_bm_url;
                    break;
                default:
                    layout = R.layout.item_bm_folder;
                    break;
            }
            View v = inf.inflate(layout, parent, false);
            if (viewType == TYPE_ROOT) {
                // 一番上の 3 つは大きなタイルにする
                View tile = v.findViewById(R.id.bmTile);
                ViewGroup.LayoutParams lp = tile.getLayoutParams();
                lp.width = lp.height = dp(88);
                tile.setLayoutParams(lp);
                ((TextView) v.findViewById(R.id.bmTitle)).setTextSize(19);
                v.setMinimumHeight(dp(108));
            }
            return new RecyclerView.ViewHolder(v) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            View v = holder.itemView;
            if (row.type == TYPE_HEADER) {
                ((TextView) v).setText(row.header);
                return;
            }
            BookmarkStore.Node n = row.node;
            if (n == null) return;
            TextView title = v.findViewById(R.id.bmTitle);
            TextView sub = v.findViewById(R.id.bmSubtitle);
            if (row.type == TYPE_URL) {
                title.setText(n.title.isEmpty() ? n.url : n.title);
                String host = BookmarkViews.hostOf(n.url);
                sub.setText(host != null ? host : n.url);
                BookmarkViews.bindPageTile(v.findViewById(R.id.bmTile), v.findViewById(R.id.bmThumb),
                        v.findViewById(R.id.bmLetter), n);
                boolean unread = !n.read && n.parent == BookmarkStore.ROOT_READING;
                v.findViewById(R.id.bmUnread).setVisibility(unread ? View.VISIBLE : View.GONE);
                v.findViewById(R.id.bmMore).setOnClickListener(x -> showActions(n));
            } else {
                title.setText(n.title);
                BookmarkViews.bindFolderTile(v.findViewById(R.id.bmTile), n, row.count);
                if (row.type == TYPE_ROOT) {
                    sub.setVisibility(View.VISIBLE);
                    sub.setText(n.id == BookmarkStore.ROOT_READING ? Lang.t("後で読むページ")
                            : n.id == BookmarkStore.ROOT_OTHER ? Lang.t("整理しておく場所") : Lang.t("いつものブックマーク"));
                } else {
                    sub.setVisibility(View.GONE);
                }
            }
            v.setOnClickListener(x -> onNodeClicked(n));
            v.setOnLongClickListener(x -> {
                showActions(n);
                return true;
            });
        }
    }
}
