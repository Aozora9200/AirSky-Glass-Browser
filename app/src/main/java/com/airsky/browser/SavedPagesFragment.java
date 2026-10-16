package com.airsky.browser;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class SavedPagesFragment extends Fragment {

    private RecyclerView recyclerView;
    private View emptyView;
    private final List<SavedPage> pageList = new ArrayList<>();
    private SavedPageDatabase dbHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_saved_list, container, false);
        recyclerView = root.findViewById(R.id.recyclerView);
        emptyView = root.findViewById(R.id.emptyView);
        ((TextView) root.findViewById(R.id.emptyText)).setText(Lang.t("保存されたページはありません。"));
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        dbHelper = new SavedPageDatabase(requireContext());
        loadSavedPages();
        return root;
    }

    private void loadSavedPages() {
        pageList.clear();
        Cursor cursor = dbHelper.getReadableDatabase().rawQuery("SELECT id, url, title, screenshot_path, date_saved FROM pages", null);
        while (cursor.moveToNext()) {
            pageList.add(new SavedPage(cursor.getInt(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4)));
        }
        cursor.close();

        recyclerView.setAdapter(new SavedPageAdapter(requireContext(), pageList, new SavedPageAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(SavedPage page) {
                // シークレット画面から開いた場合はシークレットタブで開く
                startActivity(BookmarksActivity.openPageIntent(requireActivity(), page.url));
                requireActivity().finish();
            }

            @Override
            public void onItemLongClick(SavedPage page) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(Lang.t("削除"))
                        .setMessage(Lang.t("このページを削除しますか？"))
                        .setPositiveButton(Lang.t("削除"), (dialog, which) -> {
                            dbHelper.getWritableDatabase().delete("pages", "id=?", new String[]{String.valueOf(page.id)});
                            loadSavedPages();
                        })
                        .setNegativeButton(Lang.t("キャンセル"), null)
                        .show();
            }
        }));
        boolean empty = pageList.isEmpty();
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }
}
