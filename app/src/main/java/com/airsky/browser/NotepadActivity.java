package com.airsky.browser;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * メモ一覧(Google Keep 風)。
 * 固定したメモを上にまとめ、カードを 2 列(または 1 列)で並べる。長押しで複数選んで、
 * 固定・色・書き出し・削除ができる。下のバーからリスト・図形描画・録音・画像付きのメモをすぐ作れる。
 */
public class NotepadActivity extends AppCompatActivity {

    private static final String PREF_SINGLE_COLUMN = "notes_single_column";

    private NoteStore store;
    private RecyclerView list;
    private StaggeredGridLayoutManager layoutManager;
    private final Adapter adapter = new Adapter();
    private final Set<String> selected = new LinkedHashSet<>();
    private String query = "";
    private boolean firstShow = true;
    private List<NoteStore.Note> exportTargets = new ArrayList<>();

    private final ActivityResultLauncher<String> exportZip = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"), this::onExportZip);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notepad);
        TouchEffectView.attach(getWindow());
        store = NoteStore.get(this);

        list = findViewById(R.id.ntList);
        boolean single = getSharedPreferences("AppPrefs", MODE_PRIVATE).getBoolean(PREF_SINGLE_COLUMN, false);
        layoutManager = new StaggeredGridLayoutManager(single ? 1 : 2, StaggeredGridLayoutManager.VERTICAL);
        list.setLayoutManager(layoutManager);
        list.setAdapter(adapter);
        updateLayoutIcon(single);

        findViewById(R.id.ntBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.ntLayout).setOnClickListener(v -> toggleLayout());
        findViewById(R.id.ntMore).setOnClickListener(v -> showMoreMenu());
        EditText search = findViewById(R.id.ntSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                refresh();
            }
        });

        findViewById(R.id.ntFab).setOnClickListener(v -> openNew(NoteEditActivity.NEW_TEXT, v));
        findViewById(R.id.ntNewList).setOnClickListener(v -> openNew(NoteEditActivity.NEW_CHECKLIST, v));
        findViewById(R.id.ntNewDrawing).setOnClickListener(v -> openNew(NoteEditActivity.NEW_DRAWING, v));
        findViewById(R.id.ntNewAudio).setOnClickListener(v -> openNew(NoteEditActivity.NEW_AUDIO, v));
        findViewById(R.id.ntNewImage).setOnClickListener(v -> openNew(NoteEditActivity.NEW_IMAGE, v));

        findViewById(R.id.ntSelClose).setOnClickListener(v -> clearSelection());
        findViewById(R.id.ntSelPin).setOnClickListener(v -> pinSelected());
        findViewById(R.id.ntSelColor).setOnClickListener(v -> colorSelected());
        findViewById(R.id.ntSelExport).setOnClickListener(v -> exportNotes(selectedNotes()));
        findViewById(R.id.ntSelDelete).setOnClickListener(v -> deleteSelected());

        handleIncoming(getIntent());
        playEnter();
        // カードが順に浮かぶ動きは、開いた直後だけにする
        list.postDelayed(() -> firstShow = false, 900);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncoming(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** ほかのアプリから開いたテキストファイルや共有された文字を、新しいメモにする */
    private void handleIncoming(Intent intent) {
        if (intent == null) return;
        String text = null;
        String title = "";
        Uri uri = intent.getData();
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && uri != null) {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append('\n');
                    text = sb.toString();
                    String seg = uri.getLastPathSegment();
                    if (seg != null) {
                        title = seg.substring(seg.lastIndexOf('/') + 1).replaceAll("\\.(txt|md)$", "");
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, Lang.t("ファイルの読み込み中にエラーが発生しました。"), Toast.LENGTH_LONG).show();
            }
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            text = intent.getStringExtra(Intent.EXTRA_TEXT);
            String sub = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            if (sub != null) title = sub;
        }
        if (text == null) return;
        NoteStore.Note n = new NoteStore.Note();
        n.title = title;
        n.body = text;
        store.put(n);
        intent.setData(null);
        intent.setAction(null);
        openNote(n, null);
    }

    // ------------------------------------------------------------------
    // 一覧
    // ------------------------------------------------------------------

    private void refresh() {
        List<NoteStore.Note> all = store.all();
        List<Object> rows = new ArrayList<>();
        List<NoteStore.Note> pinned = new ArrayList<>();
        List<NoteStore.Note> others = new ArrayList<>();
        String q = query.toLowerCase(Locale.ROOT);
        for (NoteStore.Note n : all) {
            if (!q.isEmpty() && !(n.title + "\n" + n.plainText()).toLowerCase(Locale.ROOT).contains(q)) continue;
            (n.pinned ? pinned : others).add(n);
        }
        if (!pinned.isEmpty()) {
            rows.add(Lang.t("固定済み"));
            rows.addAll(pinned);
            if (!others.isEmpty()) rows.add(Lang.t("その他"));
        }
        rows.addAll(others);
        // 消えたメモが選ばれたままにならないようにする
        Set<String> ids = new LinkedHashSet<>();
        for (NoteStore.Note n : all) ids.add(n.id);
        selected.retainAll(ids);
        adapter.submit(rows);

        View empty = findViewById(R.id.ntEmpty);
        boolean none = rows.isEmpty();
        ((TextView) findViewById(R.id.ntEmptyText)).setText(q.isEmpty()
                ? Lang.t("追加したメモがここに表示されます") : Lang.t("一致するメモはありません"));
        if (none && empty.getVisibility() != View.VISIBLE) {
            empty.setVisibility(View.VISIBLE);
            View icon = findViewById(R.id.ntEmptyIcon);
            icon.setScaleX(0.6f);
            icon.setScaleY(0.6f);
            icon.animate().scaleX(1f).scaleY(1f).setDuration(500)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
        } else if (!none) {
            empty.setVisibility(View.GONE);
        }
        updateSelectionBar();
    }

    private void playEnter() {
        View search = findViewById(R.id.ntSearchBar);
        search.setAlpha(0f);
        search.setTranslationY(-24 * getResources().getDisplayMetrics().density);
        search.animate().alpha(1f).translationY(0f).setDuration(420)
                .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
        View bar = findViewById(R.id.ntBottomBar);
        bar.setTranslationY(80 * getResources().getDisplayMetrics().density);
        bar.animate().translationY(0f).setStartDelay(80).setDuration(420)
                .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
        View fab = findViewById(R.id.ntFab);
        fab.setScaleX(0f);
        fab.setScaleY(0f);
        fab.setRotation(-90f);
        fab.animate().scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(200).setDuration(450)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f)).start();
    }

    private void toggleLayout() {
        SharedPreferences p = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean single = !p.getBoolean(PREF_SINGLE_COLUMN, false);
        p.edit().putBoolean(PREF_SINGLE_COLUMN, single).apply();
        updateLayoutIcon(single);
        list.animate().alpha(0f).setDuration(120).withEndAction(() -> {
            layoutManager.setSpanCount(single ? 1 : 2);
            adapter.notifyDataSetChanged();
            list.animate().alpha(1f).setDuration(220).start();
        }).start();
    }

    private void updateLayoutIcon(boolean single) {
        ImageButton b = findViewById(R.id.ntLayout);
        b.setImageResource(single ? R.drawable.ic_nt_grid : R.drawable.ic_nt_list);
        b.setContentDescription(single ? Lang.t("グリッド表示") : Lang.t("リスト表示"));
    }

    private void showMoreMenu() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout box = NoteEditActivity.sheetBox(this);
        NoteEditActivity.sheetRow(box, R.drawable.ic_nt_export, Lang.t("すべてのメモをファイルに出力"), () -> {
            sheet.dismiss();
            exportNotes(store.all());
        });
        NoteEditActivity.sheetRow(box, R.drawable.ic_ext_file, Lang.t("テキストファイルを読み込む"), () -> {
            sheet.dismiss();
            importLauncher.launch(new String[]{"text/*"});
        });
        sheet.setContentView(box);
        sheet.show();
    }

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                handleIncoming(new Intent(Intent.ACTION_VIEW, uri));
            });

    // ------------------------------------------------------------------
    // 開く
    // ------------------------------------------------------------------

    private void openNew(String type, View from) {
        Intent i = new Intent(this, NoteEditActivity.class).putExtra(NoteEditActivity.EXTRA_NEW, type);
        startActivity(i);
        overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
    }

    private void openNote(NoteStore.Note n, View card) {
        Intent i = new Intent(this, NoteEditActivity.class).putExtra(NoteEditActivity.EXTRA_ID, n.id);
        if (card != null) {
            // カードの場所から広がるように開く
            ActivityOptionsCompat o = ActivityOptionsCompat.makeScaleUpAnimation(card, 0, 0,
                    card.getWidth(), card.getHeight());
            startActivity(i, o.toBundle());
        } else {
            startActivity(i);
        }
    }

    // ------------------------------------------------------------------
    // 選択
    // ------------------------------------------------------------------

    private List<NoteStore.Note> selectedNotes() {
        List<NoteStore.Note> out = new ArrayList<>();
        for (String id : selected) {
            NoteStore.Note n = store.find(id);
            if (n != null) out.add(n);
        }
        return out;
    }

    private void toggleSelect(NoteStore.Note n) {
        if (!selected.remove(n.id)) selected.add(n.id);
        adapter.notifyDataSetChanged();
        updateSelectionBar();
    }

    private void clearSelection() {
        selected.clear();
        adapter.notifyDataSetChanged();
        updateSelectionBar();
    }

    private void updateSelectionBar() {
        View bar = findViewById(R.id.ntSelectBar);
        boolean show = !selected.isEmpty();
        if (show) {
            ((TextView) findViewById(R.id.ntSelCount)).setText(selected.size() + Lang.t(" 件を選択"));
            boolean allPinned = true;
            for (NoteStore.Note n : selectedNotes()) allPinned &= n.pinned;
            ((ImageButton) findViewById(R.id.ntSelPin)).setImageResource(
                    allPinned ? R.drawable.ic_nt_pin : R.drawable.ic_nt_pin_outline);
        }
        if (show && bar.getVisibility() != View.VISIBLE) {
            bar.setVisibility(View.VISIBLE);
            bar.setAlpha(0f);
            bar.setTranslationY(-bar.getHeight() / 2f);
            bar.animate().alpha(1f).translationY(0f).setDuration(220).start();
        } else if (!show && bar.getVisibility() == View.VISIBLE) {
            bar.animate().alpha(0f).setDuration(160).withEndAction(() -> bar.setVisibility(View.GONE)).start();
        }
    }

    private void pinSelected() {
        List<NoteStore.Note> notes = selectedNotes();
        boolean allPinned = true;
        for (NoteStore.Note n : notes) allPinned &= n.pinned;
        for (NoteStore.Note n : notes) {
            n.pinned = !allPinned;
            store.put(n);
        }
        clearSelection();
        refresh();
    }

    private void colorSelected() {
        NoteEditActivity.showColorSheet(this, -1, index -> {
            for (NoteStore.Note n : selectedNotes()) {
                n.color = index;
                store.put(n);
            }
            clearSelection();
            refresh();
        });
    }

    private void deleteSelected() {
        List<NoteStore.Note> notes = selectedNotes();
        new MaterialAlertDialogBuilder(this)
                .setTitle(notes.size() + Lang.t(" 件のメモを削除しますか？"))
                .setMessage(Lang.t("添付した写真や録音も削除されます。"))
                .setPositiveButton(Lang.t("削除"), (d, w) -> {
                    for (NoteStore.Note n : notes) store.delete(n);
                    clearSelection();
                    refresh();
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    // ------------------------------------------------------------------
    // 書き出し
    // ------------------------------------------------------------------

    private void exportNotes(List<NoteStore.Note> notes) {
        if (notes.isEmpty()) {
            Toast.makeText(this, Lang.t("メモがありません"), Toast.LENGTH_SHORT).show();
            return;
        }
        exportTargets = notes;
        String name = notes.size() == 1 ? NoteExporter.fileName(notes.get(0), "zip")
                : Lang.t("メモ_") + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date()) + ".zip";
        exportZip.launch(name);
    }

    private void onExportZip(Uri uri) {
        if (uri == null) return;
        try {
            NoteExporter.writeZip(this, uri, exportTargets);
            Toast.makeText(this, exportTargets.size() + Lang.t(" 件のメモを書き出しました"), Toast.LENGTH_SHORT).show();
            clearSelection();
        } catch (Exception e) {
            Toast.makeText(this, Lang.t("書き出せませんでした"), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (!selected.isEmpty()) {
            clearSelection();
            return;
        }
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }

    // ------------------------------------------------------------------
    // カード
    // ------------------------------------------------------------------

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<Object> rows = new ArrayList<>();
        private int animatedUpTo = -1;

        void submit(List<Object> r) {
            rows = r;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof String ? 0 : 1;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 0) {
                TextView t = new TextView(parent.getContext());
                float d = getResources().getDisplayMetrics().density;
                t.setPadding(Math.round(12 * d), Math.round(12 * d), 0, Math.round(6 * d));
                t.setTextSize(12);
                t.setAllCaps(true);
                t.setLetterSpacing(0.08f);
                t.setTextColor(MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOnSurfaceVariant));
                StaggeredGridLayoutManager.LayoutParams lp = new StaggeredGridLayoutManager.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setFullSpan(true);
                t.setLayoutParams(lp);
                return new RecyclerView.ViewHolder(t) {};
            }
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note_card, parent, false);
            return new RecyclerView.ViewHolder(v) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            Object row = rows.get(position);
            if (row instanceof String) {
                ((TextView) h.itemView).setText((String) row);
                return;
            }
            bindCard(h.itemView, (NoteStore.Note) row);
            if (firstShow && position > animatedUpTo) {
                animatedUpTo = position;
                View v = h.itemView;
                v.setAlpha(0f);
                v.setTranslationY(40 * getResources().getDisplayMetrics().density);
                v.setScaleX(0.94f);
                v.setScaleY(0.94f);
                v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                        .setStartDelay(Math.min(position, 10) * 45L).setDuration(460)
                        .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
            }
        }
    }

    private void bindCard(View v, NoteStore.Note n) {
        MaterialCardView card = (MaterialCardView) v;
        int color = NoteColors.color(this, n.color);
        int surface = MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurface);
        card.setCardBackgroundColor(color != 0 ? color : surface);
        card.setStrokeWidth(color != 0 ? 0 : Math.round(getResources().getDisplayMetrics().density));
        boolean isSelected = selected.contains(n.id);
        card.setChecked(isSelected);
        card.setStrokeColor(ColorStateList.valueOf(isSelected
                ? MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnSurface)
                : MaterialColors.getColor(card, com.google.android.material.R.attr.colorOutlineVariant)));
        if (isSelected) card.setStrokeWidth(Math.round(2 * getResources().getDisplayMetrics().density));

        TextView title = v.findViewById(R.id.ncTitle);
        title.setText(n.title);
        // 題名が無くても固定のピンは右上に出す
        ((View) title.getParent()).setVisibility(n.title.trim().isEmpty() && !n.pinned ? View.GONE : View.VISIBLE);
        v.findViewById(R.id.ncPin).setVisibility(n.pinned ? View.VISIBLE : View.GONE);

        TextView body = v.findViewById(R.id.ncBody);
        LinearLayout checks = v.findViewById(R.id.ncChecks);
        checks.removeAllViews();
        if (n.checklist) {
            body.setVisibility(View.GONE);
            int shown = 0, hiddenChecked = 0;
            for (NoteStore.CheckItem item : n.items) {
                if (item.checked) {
                    hiddenChecked++;
                    continue;
                }
                if (shown >= 8) continue;
                checks.addView(checkRow(item));
                shown++;
            }
            if (hiddenChecked > 0) {
                TextView more = new TextView(this);
                more.setText(Lang.t("+ チェック済みのアイテム ") + hiddenChecked + Lang.t(" 件"));
                more.setTextSize(12);
                more.setTextColor(MaterialColors.getColor(v, com.google.android.material.R.attr.colorOnSurfaceVariant));
                more.setPadding(0, Math.round(4 * getResources().getDisplayMetrics().density), 0, 0);
                checks.addView(more);
            }
        } else {
            String text = n.body.trim();
            body.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
            if (n.markdown) {
                int accent = MaterialColors.getColor(v, androidx.appcompat.R.attr.colorPrimary);
                int codeBg = MaterialColors.getColor(v, com.google.android.material.R.attr.colorSurfaceContainerHighest);
                body.setText(Markdown.render(text.length() > 1200 ? text.substring(0, 1200) : text, accent, codeBg));
            } else {
                body.setText(text);
            }
        }

        // 一番上の画像(写真か手書き)
        ImageView image = v.findViewById(R.id.ncImage);
        NoteStore.Attachment first = null;
        int audioCount = 0, videoCount = 0;
        for (NoteStore.Attachment a : n.attachments) {
            if (first == null && (NoteStore.TYPE_IMAGE.equals(a.type) || NoteStore.TYPE_DRAWING.equals(a.type))) first = a;
            if (NoteStore.TYPE_AUDIO.equals(a.type)) audioCount++;
            if (NoteStore.TYPE_VIDEO.equals(a.type)) videoCount++;
        }
        if (first != null) {
            image.setVisibility(View.VISIBLE);
            image.setImageBitmap(thumb(store.media(first.file), 480));
        } else {
            image.setVisibility(View.GONE);
            image.setImageDrawable(null);
        }

        ChipGroup chips = v.findViewById(R.id.ncChips);
        chips.removeAllViews();
        if (audioCount > 0) chips.addView(miniChip(R.drawable.ic_nt_mic, Lang.t("録音 ") + audioCount));
        if (videoCount > 0) chips.addView(miniChip(R.drawable.ic_nt_video, Lang.t("動画 ") + videoCount));
        for (NoteStore.LinkPreview p : n.links.values()) {
            String host = Uri.parse(p.url).getHost();
            chips.addView(miniChip(R.drawable.ic_nt_link, host != null ? host : p.url));
            if (chips.getChildCount() >= 4) break;
        }
        chips.setVisibility(chips.getChildCount() > 0 ? View.VISIBLE : View.GONE);

        v.setOnClickListener(x -> {
            if (!selected.isEmpty()) toggleSelect(n);
            else openNote(n, x);
        });
        v.setOnLongClickListener(x -> {
            x.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            x.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).withEndAction(() ->
                    x.animate().scaleX(1f).scaleY(1f).setDuration(200).start()).start();
            toggleSelect(n);
            return true;
        });
    }

    private View checkRow(NoteStore.CheckItem item) {
        TextView t = new TextView(this);
        t.setText("☐  " + item.text);
        t.setTextSize(14);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setTextColor(MaterialColors.getColor(list, com.google.android.material.R.attr.colorOnSurface));
        t.setPadding(0, Math.round(2 * getResources().getDisplayMetrics().density), 0, 0);
        if (item.checked) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        return t;
    }

    private Chip miniChip(int icon, String text) {
        Chip c = new Chip(this);
        c.setText(text);
        c.setChipIconResource(icon);
        c.setChipIconSize(14 * getResources().getDisplayMetrics().density);
        c.setTextSize(11);
        c.setEnsureMinTouchTargetSize(false);
        c.setChipMinHeight(26 * getResources().getDisplayMetrics().density);
        c.setClickable(false);
        c.setChipBackgroundColor(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        c.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.setMaxWidth(Math.round(140 * getResources().getDisplayMetrics().density));
        return c;
    }

    /** 小さく読み込んだ画像(一覧用) */
    static Bitmap thumb(File f, int maxPx) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= maxPx) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }
}
