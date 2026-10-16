package com.airsky.browser;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * ブックマークの移動先(保存先)を選ぶ。フォルダをたどり、「ここに移動」で今開いているフォルダを返す。
 * 動かすのがフォルダなら、そのフォルダ自身とリーディングリストは選べない。
 */
public class BookmarkFolderPickerActivity extends AppCompatActivity {

    /** 動かすブックマーク・フォルダの ID(無ければ -1)。 */
    static final String EXTRA_MOVING = "com.airsky.browser.extra.MOVING";
    /** 最初に開くフォルダ。 */
    static final String EXTRA_START = "com.airsky.browser.extra.START";
    /** 選んだフォルダの ID。 */
    static final String EXTRA_RESULT = "com.airsky.browser.extra.RESULT";

    private BookmarkStore store;
    @androidx.annotation.Nullable private BookmarkStore.Node moving;
    private long folder = BookmarkStore.ROOT;
    private final List<BookmarkStore.Node> folders = new ArrayList<>();
    private RecyclerView list;
    private TextView title, empty;
    private View ok, newFolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmark_picker);
        TouchEffectView.attach(getWindow());
        store = BookmarkStore.get(this);
        moving = store.node(getIntent().getLongExtra(EXTRA_MOVING, -1));

        list = findViewById(R.id.bmpList);
        title = findViewById(R.id.bmpTitle);
        empty = findViewById(R.id.bmpEmpty);
        ok = findViewById(R.id.bmpOk);
        newFolder = findViewById(R.id.bmpNewFolder);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new FolderAdapter());

        findViewById(R.id.bmpBack).setOnClickListener(v -> goBack());
        findViewById(R.id.bmpCancel).setOnClickListener(v -> finish());
        ok.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_RESULT, folder));
            finish();
        });
        newFolder.setOnClickListener(v -> askNewFolder());

        long start = getIntent().getLongExtra(EXTRA_START, BookmarkStore.ROOT);
        open(selectable(start) ? start : BookmarkStore.ROOT, 0);
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
        if (folder == BookmarkStore.ROOT) {
            finish();
            return;
        }
        BookmarkStore.Node n = store.node(folder);
        open(n != null ? n.parent : BookmarkStore.ROOT, -1);
    }

    /** そのフォルダを移動先にできるか。 */
    private boolean selectable(long id) {
        BookmarkStore.Node n = store.node(id);
        if (n == null || !n.folder) return false;
        if (moving == null) return true;
        if (moving.folder) {
            return id != BookmarkStore.ROOT_READING && !store.isInside(id, moving.id);
        }
        return true;
    }

    private void open(long id, int direction) {
        folder = id;
        folders.clear();
        for (BookmarkStore.Node n : store.folders(id)) {
            if (moving != null && n.id == moving.id) continue; // 動かすフォルダ自身は出さない
            if (moving != null && moving.folder && n.id == BookmarkStore.ROOT_READING) continue;
            folders.add(n);
        }
        list.getAdapter().notifyDataSetChanged();
        BookmarkStore.Node current = store.node(id);
        title.setText(current == null ? Lang.t("… に移動") : current.title);
        boolean canPick = id != BookmarkStore.ROOT && selectable(id);
        ok.setEnabled(canPick);
        newFolder.setVisibility(id != BookmarkStore.ROOT && id != BookmarkStore.ROOT_READING ? View.VISIBLE : View.GONE);
        empty.setVisibility(folders.isEmpty() && id != BookmarkStore.ROOT_READING ? View.VISIBLE : View.GONE);
        empty.setText(id == BookmarkStore.ROOT_READING ? "" : Lang.t("この中にフォルダはありません\n右上のボタンで作れます"));
        if (direction != 0) {
            float d = getResources().getDisplayMetrics().density;
            list.setAlpha(0f);
            list.setTranslationX(direction * 40 * d);
            list.animate().alpha(1f).translationX(0f).setDuration(240)
                    .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
        }
    }

    private void askNewFolder() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(Lang.t("フォルダ名"));
        FrameLayout box = new FrameLayout(this);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 3, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("新しいフォルダ"))
                .setView(box)
                .setNegativeButton(Lang.t("キャンセル"), null)
                .setPositiveButton(Lang.t("作成"), (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    long id = store.addFolder(folder, name);
                    // 作ったフォルダをそのまま開く
                    if (id > 0) open(id, 1);
                })
                .show();
    }

    private final class FolderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return folders.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bm_folder, parent, false);
            return new RecyclerView.ViewHolder(v) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            BookmarkStore.Node n = folders.get(position);
            View v = holder.itemView;
            ((TextView) v.findViewById(R.id.bmTitle)).setText(n.title);
            BookmarkViews.bindFolderTile(v.findViewById(R.id.bmTile), n, store.count(n.id));
            // リーディングリストの中にはフォルダが無いので、押したらそこを選ぶだけ
            v.setOnClickListener(x -> open(n.id, 1));
        }
    }
}
