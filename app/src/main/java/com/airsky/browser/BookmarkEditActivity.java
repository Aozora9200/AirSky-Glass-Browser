package com.airsky.browser;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * ブックマーク(またはフォルダ)の編集。名前・URL・入れるフォルダを変える。
 * 戻る時に保存する(URL が空なら保存しない)。右上のごみ箱で削除。
 */
public class BookmarkEditActivity extends AppCompatActivity {

    static final String EXTRA_ID = "com.airsky.browser.extra.BOOKMARK_ID";
    private static final int REQUEST_FOLDER = 1;

    private BookmarkStore store;
    private BookmarkStore.Node node;
    private long parent;
    private TextInputEditText name, url;
    private boolean deleted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmark_edit);
        TouchEffectView.attach(getWindow());
        store = BookmarkStore.get(this);
        node = store.node(getIntent().getLongExtra(EXTRA_ID, -1));
        if (node == null || node.isRoot()) {
            finish();
            return;
        }
        parent = savedInstanceState != null ? savedInstanceState.getLong("parent", node.parent) : node.parent;

        name = findViewById(R.id.bmeName);
        url = findViewById(R.id.bmeUrl);
        ((TextView) findViewById(R.id.bmeTitleBar)).setText(node.folder ? Lang.t("フォルダを編集") : Lang.t("ブックマークを編集"));
        name.setText(node.title);
        if (node.folder) {
            findViewById(R.id.bmeUrlLayout).setVisibility(View.GONE);
        } else {
            url.setText(node.url);
        }

        findViewById(R.id.bmeBack).setOnClickListener(v -> finish());
        findViewById(R.id.bmeDelete).setOnClickListener(v -> confirmDelete());
        findViewById(R.id.bmeFolder).setOnClickListener(v -> {
            startActivityForResult(new Intent(this, BookmarkFolderPickerActivity.class)
                    .putExtra(BookmarkFolderPickerActivity.EXTRA_MOVING, node.id)
                    .putExtra(BookmarkFolderPickerActivity.EXTRA_START, parent), REQUEST_FOLDER);
            overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
        });
        bindFolder();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("parent", parent);
    }

    private void bindFolder() {
        View row = findViewById(R.id.bmeFolder);
        BookmarkStore.Node folder = store.node(parent);
        if (folder == null) return;
        ((TextView) row.findViewById(R.id.bmTitle)).setText(folder.title);
        BookmarkViews.bindFolderTile(row.findViewById(R.id.bmTile), folder, store.count(folder.id));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FOLDER && resultCode == RESULT_OK && data != null) {
            long target = data.getLongExtra(BookmarkFolderPickerActivity.EXTRA_RESULT, -1);
            if (target > 0) {
                parent = target;
                bindFolder();
            }
        }
    }

    private void confirmDelete() {
        int count = node.folder ? store.count(node.id) : 0;
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("「") + node.title + Lang.t("」を削除しますか？"))
                .setMessage(count > 0 ? Lang.t("中の ") + count + Lang.t(" 件も削除されます") : null)
                .setNegativeButton(Lang.t("キャンセル"), null)
                .setPositiveButton(Lang.t("削除"), (d, w) -> {
                    store.delete(node.id);
                    deleted = true;
                    Toast.makeText(this, Lang.t("削除しました"), Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
    }

    /** 戻る時に保存する。 */
    private void save() {
        if (deleted || node == null) return;
        String title = name.getText() != null ? name.getText().toString().trim() : "";
        String address = node.folder ? null : (url.getText() != null ? url.getText().toString().trim() : "");
        if (!node.folder && (address == null || address.isEmpty())) {
            Toast.makeText(this, Lang.t("URL が空のため保存しませんでした"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (title.isEmpty()) title = node.folder ? node.title : address;
        if (!title.equals(node.title) || (address != null && !address.equals(node.url))) {
            store.update(node.id, title, address);
        }
        if (parent != node.parent && store.move(node.id, parent)) {
            store.setLastFolder(parent);
        }
    }

    @Override
    public void finish() {
        save();
        super.finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }
}
