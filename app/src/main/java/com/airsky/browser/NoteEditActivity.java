package com.airsky.browser;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * メモの編集(Google Keep 風)。
 * 題名・本文(マークダウン)・チェックリスト・写真・動画・録音・図形描画・URL のプレビュー・
 * 背景色・固定を扱い、閉じる時に自動で保存する。
 */
public class NoteEditActivity extends AppCompatActivity {

    static final String EXTRA_ID = "note_id";
    static final String EXTRA_NEW = "new_type";
    static final String NEW_TEXT = "text";
    static final String NEW_CHECKLIST = "checklist";
    static final String NEW_DRAWING = "drawing";
    static final String NEW_AUDIO = "audio";
    static final String NEW_IMAGE = "image";

    interface IntListener {
        void on(int value);
    }

    private NoteStore store;
    private NoteStore.Note note;
    private String savedJson;
    private boolean existed;

    private View root;
    private EditText title;
    private EditText body;
    private TextView rendered;
    private LinearLayout items;
    private LinearLayout checkedItems;
    private TextView checkedHeader;
    private boolean checkedExpanded = true;
    private boolean preview;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Runnable linkTask = this::refreshLinks;

    // 録音・再生
    @Nullable private MediaRecorder recorder;
    @Nullable private File recordingFile;
    private long recordStart;
    @Nullable private MediaPlayer player;
    @Nullable private String playingFile;
    @Nullable private Runnable pendingPermissionAction;
    @Nullable private Uri pendingCaptureUri;
    @Nullable private File pendingCaptureFile;
    @Nullable private String editingDrawing;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia = registerForActivityResult(
            new ActivityResultContracts.PickMultipleVisualMedia(10), uris -> {
                if (uris == null || uris.isEmpty()) return;
                io.execute(() -> {
                    List<NoteStore.Attachment> added = new ArrayList<>();
                    for (Uri u : uris) {
                        NoteStore.Attachment a = importMedia(u);
                        if (a != null) added.add(a);
                    }
                    main.post(() -> {
                        note.attachments.addAll(added);
                        rebuildMedia(true);
                    });
                });
            });

    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok) && pendingCaptureFile != null && pendingCaptureFile.length() > 0) {
                    note.attachments.add(new NoteStore.Attachment(NoteStore.TYPE_IMAGE, pendingCaptureFile.getName()));
                    rebuildMedia(true);
                } else if (pendingCaptureFile != null) {
                    //noinspection ResultOfMethodCallIgnored
                    pendingCaptureFile.delete();
                }
                pendingCaptureFile = null;
            });

    private final ActivityResultLauncher<Uri> captureVideo = registerForActivityResult(
            new ActivityResultContracts.CaptureVideo(), ok -> {
                if (Boolean.TRUE.equals(ok) && pendingCaptureFile != null && pendingCaptureFile.length() > 0) {
                    NoteStore.Attachment a = new NoteStore.Attachment(NoteStore.TYPE_VIDEO, pendingCaptureFile.getName());
                    a.durationMs = mediaDuration(pendingCaptureFile);
                    note.attachments.add(a);
                    rebuildMedia(true);
                } else if (pendingCaptureFile != null) {
                    //noinspection ResultOfMethodCallIgnored
                    pendingCaptureFile.delete();
                }
                pendingCaptureFile = null;
            });

    private final ActivityResultLauncher<Intent> drawing = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                String edited = editingDrawing;
                editingDrawing = null;
                if (r.getResultCode() != RESULT_OK || r.getData() == null) return;
                String file = r.getData().getStringExtra(DrawingActivity.EXTRA_FILE);
                if (file == null) return;
                if (edited == null) note.attachments.add(new NoteStore.Attachment(NoteStore.TYPE_DRAWING, file));
                rebuildMedia(edited == null);
            });

    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                Runnable r = pendingPermissionAction;
                pendingPermissionAction = null;
                if (Boolean.TRUE.equals(granted) && r != null) r.run();
                else if (!Boolean.TRUE.equals(granted)) {
                    Toast.makeText(this, Lang.t("権限が許可されていません"), Toast.LENGTH_SHORT).show();
                }
            });

    private String exportFormat = "md";
    private final ActivityResultLauncher<String> exportMd = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/markdown"), this::onExport);
    private final ActivityResultLauncher<String> exportTxt = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/plain"), this::onExport);
    private final ActivityResultLauncher<String> exportZip = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"), this::onExport);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_edit);
        TouchEffectView.attach(getWindow());
        store = NoteStore.get(this);

        String id = savedInstanceState != null ? savedInstanceState.getString(EXTRA_ID)
                : getIntent().getStringExtra(EXTRA_ID);
        NoteStore.Note found = id != null ? store.find(id) : null;
        existed = found != null;
        note = found != null ? found : new NoteStore.Note();
        if (savedInstanceState != null && found == null && id != null) note.id = id;
        savedJson = note.toJson().toString();

        root = findViewById(R.id.neRoot);
        title = findViewById(R.id.neTitle);
        body = findViewById(R.id.neBody);
        rendered = findViewById(R.id.neRendered);
        items = findViewById(R.id.neItems);
        checkedItems = findViewById(R.id.neCheckedItems);
        checkedHeader = findViewById(R.id.neCheckedHeader);

        title.setText(note.title);
        body.setText(note.body);
        applyColor(false);
        updatePin(false);
        updateEdited();

        findViewById(R.id.neBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.nePin).setOnClickListener(v -> {
            note.pinned = !note.pinned;
            updatePin(true);
        });
        findViewById(R.id.nePreview).setOnClickListener(v -> setPreview(!preview, true));
        findViewById(R.id.neAdd).setOnClickListener(v -> showAddMenu());
        findViewById(R.id.neColor).setOnClickListener(v -> showColorSheet(this, note.color, idx -> {
            note.color = idx;
            applyColor(true);
        }));
        findViewById(R.id.neMarkdownBtn).setOnClickListener(v -> {
            note.markdown = !note.markdown;
            Toast.makeText(this, note.markdown ? Lang.t("マークダウン: オン") : Lang.t("マークダウン: オフ"), Toast.LENGTH_SHORT).show();
            if (!note.markdown) setPreview(false, false);
            updateMarkdownUi();
        });
        findViewById(R.id.neMore).setOnClickListener(v -> showMoreMenu());
        findViewById(R.id.neAddItem).setOnClickListener(v -> addItem(note.items.size(), "", true));
        checkedHeader.setOnClickListener(v -> {
            checkedExpanded = !checkedExpanded;
            rebuildChecklist(null);
        });

        body.addTextChangedListener(new SimpleWatcher(s -> {
            note.body = s;
            scheduleLinks();
        }));
        title.addTextChangedListener(new SimpleWatcher(s -> note.title = s));
        body.setOnFocusChangeListener((v, f) -> updateFormatBar());
        title.setOnFocusChangeListener((v, f) -> updateFormatBar());
        buildFormatBar();

        rendered.setOnClickListener(v -> {
            setPreview(false, true);
            body.requestFocus();
            body.setSelection(body.length());
            showKeyboard(body);
        });

        setChecklistMode(note.checklist, false);
        rebuildMedia(false);
        rebuildAudio();
        rebuildLinks();
        updateMarkdownUi();
        // マークダウンで書いた既存のメモは、整えた表示で開く
        if (existed && note.markdown && !note.checklist && !note.body.trim().isEmpty()) setPreview(true, false);
        scheduleLinks();

        if (savedInstanceState == null) handleNewType(getIntent().getStringExtra(EXTRA_NEW));
        playEnter();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        save();
        out.putString(EXTRA_ID, note.id);
    }

    private void handleNewType(@Nullable String type) {
        if (type == null) return;
        switch (type) {
            case NEW_CHECKLIST:
                setChecklistMode(true, false);
                if (note.items.isEmpty()) addItem(0, "", true);
                break;
            case NEW_DRAWING:
                openDrawing(null);
                break;
            case NEW_AUDIO:
                body.post(this::startRecordingFlow);
                break;
            case NEW_IMAGE:
                body.post(this::showImageChoice);
                break;
            case NEW_TEXT:
            default:
                body.requestFocus();
                body.postDelayed(() -> showKeyboard(body), 250);
                break;
        }
    }

    private void playEnter() {
        View content = findViewById(R.id.neContent);
        content.setAlpha(0f);
        content.setTranslationY(24 * getResources().getDisplayMetrics().density);
        content.animate().alpha(1f).translationY(0f).setDuration(380)
                .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
        View bottom = findViewById(R.id.neBottom);
        bottom.setAlpha(0f);
        bottom.animate().alpha(1f).setStartDelay(120).setDuration(300).start();
    }

    // ------------------------------------------------------------------
    // 保存
    // ------------------------------------------------------------------

    @Override
    protected void onPause() {
        super.onPause();
        save();
        stopPlayback();
    }

    /** 空のメモは残さない。中身が変わった時だけ「編集」の時刻を進める */
    private boolean save() {
        if (recorder != null) return false;
        note.title = title.getText().toString();
        if (!note.checklist) note.body = body.getText().toString();
        if (note.isEmpty()) {
            if (store.find(note.id) != null) store.delete(note);
            return false;
        }
        String json = note.toJson().toString();
        if (!json.equals(savedJson)) {
            note.updated = System.currentTimeMillis();
            json = note.toJson().toString();
            savedJson = json;
            store.put(note);
            updateEdited();
        } else if (store.find(note.id) == null) {
            store.put(note);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        boolean kept = save();
        if (!kept && !existed && note.attachments.isEmpty()) {
            Toast.makeText(this, Lang.t("空のメモを破棄しました"), Toast.LENGTH_SHORT).show();
        }
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        stopRecording(false);
        stopPlayback();
        io.shutdown();
        super.onDestroy();
    }

    private void updateEdited() {
        TextView t = findViewById(R.id.neEdited);
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(note.updated);
        String when;
        if (now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)) {
            when = new SimpleDateFormat("H:mm", Locale.getDefault()).format(new Date(note.updated));
        } else if (now.get(Calendar.YEAR) == c.get(Calendar.YEAR)) {
            when = new SimpleDateFormat(Lang.t("M月d日"), Locale.getDefault()).format(new Date(note.updated));
        } else {
            when = new SimpleDateFormat(Lang.t("yyyy年M月d日"), Locale.getDefault()).format(new Date(note.updated));
        }
        t.setText(store.find(note.id) != null ? Lang.t("編集: ") + when : "");
    }

    // ------------------------------------------------------------------
    // 色・固定
    // ------------------------------------------------------------------

    private int noteBackground() {
        int c = NoteColors.color(this, note.color);
        return c != 0 ? c : MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurface);
    }

    private void applyColor(boolean animate) {
        int to = noteBackground();
        if (!animate || !(root.getBackground() instanceof android.graphics.drawable.ColorDrawable)) {
            root.setBackgroundColor(to);
            return;
        }
        int from = ((android.graphics.drawable.ColorDrawable) root.getBackground()).getColor();
        ValueAnimator a = ValueAnimator.ofArgb(from, to);
        a.setDuration(320);
        a.addUpdateListener(x -> root.setBackgroundColor((int) x.getAnimatedValue()));
        a.start();
    }

    private void updatePin(boolean animate) {
        ImageButton pin = findViewById(R.id.nePin);
        pin.setImageResource(note.pinned ? R.drawable.ic_nt_pin : R.drawable.ic_nt_pin_outline);
        pin.setContentDescription(note.pinned ? Lang.t("固定を解除") : Lang.t("固定"));
        if (animate) {
            pin.setRotation(note.pinned ? -45f : 45f);
            pin.setScaleX(0.6f);
            pin.setScaleY(0.6f);
            pin.animate().rotation(0f).scaleX(1f).scaleY(1f).setDuration(380)
                    .setInterpolator(new OvershootInterpolator(2.5f)).start();
            Toast.makeText(this, note.pinned ? Lang.t("メモを固定しました") : Lang.t("固定を解除しました"), Toast.LENGTH_SHORT).show();
        }
    }

    /** Keep の 12 色を丸で並べたシート(一覧の複数選択からも使う) */
    static void showColorSheet(Activity a, int current, IntListener listener) {
        BottomSheetDialog sheet = new BottomSheetDialog(a);
        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Math.round(16 * d), Math.round(20 * d), Math.round(16 * d), Math.round(24 * d));
        TextView head = new TextView(a);
        head.setText(Lang.t("色"));
        head.setTextSize(16);
        head.setTextColor(MaterialColors.getColor(box, com.google.android.material.R.attr.colorOnSurface,
                Color.BLACK));
        head.setPadding(Math.round(8 * d), 0, 0, Math.round(12 * d));
        box.addView(head);
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(a);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row);
        box.addView(scroll);
        int outline = MaterialColors.getColor(a.findViewById(android.R.id.content),
                com.google.android.material.R.attr.colorOutline);
        int onSurface = MaterialColors.getColor(a.findViewById(android.R.id.content),
                com.google.android.material.R.attr.colorOnSurface);
        for (int i = 0; i < NoteColors.count(); i++) {
            int idx = i;
            FrameLayout c = new FrameLayout(a);
            int size = Math.round(48 * d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = Math.round(8 * d);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            int col = NoteColors.color(a, i);
            g.setColor(col != 0 ? col : Color.TRANSPARENT);
            g.setStroke(Math.round((i == current ? 2.5f : 1) * d), i == current ? onSurface : outline);
            c.setBackground(g);
            ImageView mark = new ImageView(a);
            int pad = Math.round(12 * d);
            mark.setPadding(pad, pad, pad, pad);
            if (i == current) {
                mark.setImageResource(R.drawable.ic_nt_check);
            } else if (i == 0) {
                mark.setImageResource(R.drawable.ic_nt_close);
                mark.setAlpha(0.6f);
            }
            mark.setImageTintList(ColorStateList.valueOf(onSurface));
            c.addView(mark, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            c.setContentDescription(NoteColors.NAMES[i]);
            c.setOnClickListener(v -> {
                v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction(() -> {
                    sheet.dismiss();
                    listener.on(idx);
                }).start();
            });
            row.addView(c, lp);
            // 色の丸が左から順に弾んで出る
            c.setScaleX(0f);
            c.setScaleY(0f);
            c.animate().scaleX(1f).scaleY(1f).setStartDelay(60L + 25L * i).setDuration(300)
                    .setInterpolator(new OvershootInterpolator(2f)).start();
        }
        sheet.setContentView(box);
        sheet.show();
    }

    // ------------------------------------------------------------------
    // シートの共通部品
    // ------------------------------------------------------------------

    static LinearLayout sheetBox(Context ctx) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        float d = ctx.getResources().getDisplayMetrics().density;
        box.setPadding(0, Math.round(12 * d), 0, Math.round(16 * d));
        return box;
    }

    static View sheetRow(LinearLayout box, @DrawableRes int icon, String label, Runnable action) {
        Context ctx = box.getContext();
        View row = LayoutInflater.from(ctx).inflate(R.layout.item_aozora_menu_row, box, false);
        ImageView i = row.findViewById(R.id.am_row_icon);
        i.setImageResource(icon);
        i.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(box,
                com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)));
        ((TextView) row.findViewById(R.id.am_row_label)).setText(label);
        ViewGroup g = (ViewGroup) row;
        g.getChildAt(g.getChildCount() - 1).setVisibility(View.GONE); // 右の矢印は使わない
        row.setOnClickListener(v -> action.run());
        row.setPaddingRelative(row.getPaddingStart() + Math.round(6 * ctx.getResources().getDisplayMetrics().density),
                0, row.getPaddingEnd(), 0);
        box.addView(row);
        // 上から順にすべり込む
        int index = box.getChildCount();
        row.setAlpha(0f);
        row.setTranslationY(16 * ctx.getResources().getDisplayMetrics().density);
        row.animate().alpha(1f).translationY(0f).setStartDelay(40L * index).setDuration(280)
                .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
        return row;
    }

    private void showAddMenu() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout box = sheetBox(this);
        sheetRow(box, R.drawable.ic_nt_camera, Lang.t("写真を撮る"), () -> {
            sheet.dismiss();
            withPermission(Manifest.permission.CAMERA, this::capturePhoto);
        });
        sheetRow(box, R.drawable.ic_nt_image, Lang.t("画像・動画を追加"), () -> {
            sheet.dismiss();
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE).build());
        });
        sheetRow(box, R.drawable.ic_nt_video, Lang.t("動画を撮る"), () -> {
            sheet.dismiss();
            withPermission(Manifest.permission.CAMERA, this::captureVideoNow);
        });
        sheetRow(box, R.drawable.ic_nt_brush, Lang.t("図形描画"), () -> {
            sheet.dismiss();
            openDrawing(null);
        });
        sheetRow(box, R.drawable.ic_nt_mic, Lang.t("録音"), () -> {
            sheet.dismiss();
            startRecordingFlow();
        });
        sheetRow(box, R.drawable.ic_nt_checkbox, note.checklist ? Lang.t("チェックボックスを非表示") : Lang.t("チェックボックス"), () -> {
            sheet.dismiss();
            setChecklistMode(!note.checklist, true);
        });
        sheet.setContentView(box);
        sheet.show();
    }

    private void showImageChoice() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout box = sheetBox(this);
        sheetRow(box, R.drawable.ic_nt_camera, Lang.t("写真を撮る"), () -> {
            sheet.dismiss();
            withPermission(Manifest.permission.CAMERA, this::capturePhoto);
        });
        sheetRow(box, R.drawable.ic_nt_image, Lang.t("画像・動画を選ぶ"), () -> {
            sheet.dismiss();
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE).build());
        });
        sheet.setContentView(box);
        sheet.show();
    }

    private void showMoreMenu() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout box = sheetBox(this);
        sheetRow(box, R.drawable.ic_nt_export, Lang.t("ファイルに出力"), () -> {
            sheet.dismiss();
            chooseExport();
        });
        sheetRow(box, R.drawable.ic_omni_share, Lang.t("共有"), () -> {
            sheet.dismiss();
            share();
        });
        sheetRow(box, R.drawable.ic_nt_copy, Lang.t("コピーを作成"), () -> {
            sheet.dismiss();
            save();
            NoteStore.Note copy = note.copy();
            // 添付ファイルは別のファイルとして複製する(片方を消しても残るように)
            for (NoteStore.Attachment a : copy.attachments) {
                File src = store.media(a.file);
                String ext = a.file.contains(".") ? a.file.substring(a.file.lastIndexOf('.') + 1) : "bin";
                File dst = store.newMediaFile(ext);
                if (copyFile(src, dst)) a.file = dst.getName();
            }
            copy.links.clear();
            store.put(copy);
            Toast.makeText(this, Lang.t("コピーを作成しました"), Toast.LENGTH_SHORT).show();
        });
        sheetRow(box, R.drawable.ic_nt_checkbox, note.checklist ? Lang.t("チェックボックスを非表示") : Lang.t("チェックボックスを表示"), () -> {
            sheet.dismiss();
            setChecklistMode(!note.checklist, true);
        });
        sheetRow(box, R.drawable.ic_nt_delete, Lang.t("削除"), () -> {
            sheet.dismiss();
            new MaterialAlertDialogBuilder(this)
                    .setTitle(Lang.t("このメモを削除しますか？"))
                    .setMessage(Lang.t("添付した写真や録音も削除されます。"))
                    .setPositiveButton(Lang.t("削除"), (d, w) -> {
                        stopPlayback();
                        store.delete(note);
                        note = new NoteStore.Note();
                        finish();
                        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
                    })
                    .setNegativeButton(Lang.t("キャンセル"), null)
                    .show();
        });
        sheet.setContentView(box);
        sheet.show();
    }

    private void withPermission(String perm, Runnable action) {
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            action.run();
        } else {
            pendingPermissionAction = action;
            permission.launch(perm);
        }
    }

    // ------------------------------------------------------------------
    // マークダウン
    // ------------------------------------------------------------------

    private void updateMarkdownUi() {
        findViewById(R.id.neMarkdownBtn).setAlpha(note.markdown ? 1f : 0.4f);
        findViewById(R.id.nePreview).setVisibility(note.markdown && !note.checklist ? View.VISIBLE : View.GONE);
        updateFormatBar();
    }

    private void setPreview(boolean on, boolean animate) {
        if (note.checklist || !note.markdown) on = false;
        preview = on;
        ImageButton btn = findViewById(R.id.nePreview);
        btn.setImageTintList(on ? ColorStateList.valueOf(MaterialColors.getColor(btn, androidx.appcompat.R.attr.colorPrimary)) : null);
        View show = on ? rendered : body;
        View hide = on ? body : rendered;
        if (on) {
            rendered.setText(linkify(Markdown.render(body.getText().toString(),
                    MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary),
                    MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurfaceContainerHighest))));
            rendered.setMovementMethod(LinkMovementMethod.getInstance());
            hideKeyboard();
            body.clearFocus();
        }
        hide.setVisibility(View.GONE);
        show.setVisibility(View.VISIBLE);
        if (animate) {
            show.setAlpha(0f);
            show.animate().alpha(1f).setDuration(220).start();
        }
        updateFormatBar();
    }

    /** リンクを押した時は、この中のブラウザで開く */
    private CharSequence linkify(CharSequence text) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        for (URLSpan span : sb.getSpans(0, sb.length(), URLSpan.class)) {
            int s = sb.getSpanStart(span), e = sb.getSpanEnd(span);
            String url = span.getURL();
            sb.removeSpan(span);
            sb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    openUrl(url);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary));
                }
            }, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private void openUrl(String url) {
        save();
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url), this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }

    private void updateFormatBar() {
        View bar = findViewById(R.id.neFormatBar);
        boolean show = note.markdown && !note.checklist && !preview && body.hasFocus();
        if (show == (bar.getVisibility() == View.VISIBLE)) return;
        if (show) {
            bar.setVisibility(View.VISIBLE);
            bar.setAlpha(0f);
            bar.setTranslationY(bar.getHeight() > 0 ? bar.getHeight() : 48 * getResources().getDisplayMetrics().density);
            bar.animate().alpha(1f).translationY(0f).setDuration(220).start();
        } else {
            bar.setVisibility(View.GONE);
        }
    }

    private void buildFormatBar() {
        LinearLayout box = findViewById(R.id.neFormatButtons);
        addFormat(box, "B", Lang.t("太字"), () -> wrap("**", "**"));
        addFormat(box, "I", Lang.t("斜体"), () -> wrap("*", "*"));
        addFormat(box, "S", Lang.t("取り消し線"), () -> wrap("~~", "~~"));
        addFormat(box, "H", Lang.t("見出し"), this::cycleHeading);
        addFormat(box, "•", Lang.t("箇条書き"), () -> togglePrefix("- "));
        addFormat(box, "1.", Lang.t("番号付きリスト"), () -> togglePrefix("1. "));
        addFormat(box, "☐", Lang.t("チェック"), () -> togglePrefix("- [ ] "));
        addFormat(box, "❝", Lang.t("引用"), () -> togglePrefix("> "));
        addFormat(box, "</>", Lang.t("コード"), () -> wrap("`", "`"));
        addFormat(box, "🔗", Lang.t("リンク"), () -> wrap("[", "](https://)"));
        addFormat(box, "—", Lang.t("区切り線"), () -> insertLine("---"));
    }

    private void addFormat(LinearLayout box, String label, String desc, Runnable r) {
        MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label);
        b.setContentDescription(desc);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setInsetTop(0);
        b.setInsetBottom(0);
        float d = getResources().getDisplayMetrics().density;
        b.setPadding(Math.round(12 * d), 0, Math.round(12 * d), 0);
        b.setStrokeWidth(0);
        b.setTextSize(15);
        if ("B".equals(label)) b.setTypeface(null, android.graphics.Typeface.BOLD);
        if ("I".equals(label)) b.setTypeface(null, android.graphics.Typeface.ITALIC);
        if ("S".equals(label)) b.setPaintFlags(b.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(40 * d));
        lp.rightMargin = Math.round(2 * d);
        b.setOnClickListener(v -> r.run());
        box.addView(b, lp);
    }

    private void wrap(String before, String after) {
        Editable e = body.getText();
        int s = Math.max(0, body.getSelectionStart()), t = Math.max(0, body.getSelectionEnd());
        int a = Math.min(s, t), z = Math.max(s, t);
        e.insert(z, after);
        e.insert(a, before);
        if (a == z) body.setSelection(a + before.length());
        else body.setSelection(a + before.length(), z + before.length());
    }

    private void togglePrefix(String prefix) {
        Editable e = body.getText();
        String text = e.toString();
        int s = Math.max(0, body.getSelectionStart());
        int lineStart = text.lastIndexOf('\n', Math.max(0, s - 1)) + 1;
        if (s == 0) lineStart = 0;
        if (text.startsWith(prefix, lineStart)) {
            e.delete(lineStart, lineStart + prefix.length());
        } else {
            e.insert(lineStart, prefix);
        }
    }

    private void cycleHeading() {
        Editable e = body.getText();
        String text = e.toString();
        int s = Math.max(0, body.getSelectionStart());
        int lineStart = s == 0 ? 0 : text.lastIndexOf('\n', s - 1) + 1;
        int level = 0;
        while (lineStart + level < text.length() && text.charAt(lineStart + level) == '#') level++;
        if (level > 0 && lineStart + level < text.length() && text.charAt(lineStart + level) == ' ') {
            e.delete(lineStart, lineStart + level + 1);
            if (level < 3) {
                StringBuilder marks = new StringBuilder();
                for (int i = 0; i <= level; i++) marks.append('#');
                e.insert(lineStart, marks + " ");
            }
        } else {
            e.insert(lineStart, "# ");
        }
    }

    private void insertLine(String line) {
        Editable e = body.getText();
        int s = Math.max(0, body.getSelectionStart());
        String text = e.toString();
        String pre = s > 0 && text.charAt(s - 1) != '\n' ? "\n" : "";
        e.insert(s, pre + line + "\n");
    }

    // ------------------------------------------------------------------
    // チェックリスト
    // ------------------------------------------------------------------

    private void setChecklistMode(boolean on, boolean convert) {
        if (convert && on != note.checklist) {
            if (on) {
                note.body = body.getText().toString();
                note.items.clear();
                for (String line : note.body.split("\n")) {
                    String t = line;
                    boolean checked = false;
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("^\\s*(?:[-*+]\\s+)?(?:\\[( |x|X)]\\s+)?(.*)$").matcher(line);
                    if (m.matches()) {
                        checked = m.group(1) != null && !" ".equals(m.group(1));
                        t = m.group(2);
                    }
                    if (!t.trim().isEmpty()) note.items.add(new NoteStore.CheckItem(t, checked));
                }
                note.body = "";
                body.setText("");
                setPreview(false, false);
            } else {
                StringBuilder sb = new StringBuilder();
                for (NoteStore.CheckItem i : note.items) {
                    if (note.markdown) sb.append(i.checked ? "- [x] " : "- [ ] ");
                    sb.append(i.text).append('\n');
                }
                note.items.clear();
                note.body = sb.toString().replaceAll("\n$", "");
                body.setText(note.body);
            }
        }
        note.checklist = on;
        body.setVisibility(on ? View.GONE : (preview ? View.GONE : View.VISIBLE));
        rendered.setVisibility(!on && preview ? View.VISIBLE : View.GONE);
        View checklist = findViewById(R.id.neChecklist);
        checklist.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            if (note.items.isEmpty() && convert) note.items.add(new NoteStore.CheckItem("", false));
            rebuildChecklist(null);
            if (convert) {
                checklist.setAlpha(0f);
                checklist.animate().alpha(1f).setDuration(260).start();
            }
        }
        updateMarkdownUi();
    }

    /** チェックリストを作り直す。focus を渡すとその項目に入力の焦点を置く */
    private void rebuildChecklist(@Nullable NoteStore.CheckItem focus) {
        items.removeAllViews();
        checkedItems.removeAllViews();
        int checked = 0;
        for (NoteStore.CheckItem item : note.items) {
            if (item.checked) {
                checked++;
                if (checkedExpanded) checkedItems.addView(checkRow(item));
            } else {
                View row = checkRow(item);
                items.addView(row);
                if (item == focus) {
                    EditText et = row.findViewById(R.id.ne_item_text);
                    et.post(() -> {
                        et.requestFocus();
                        et.setSelection(et.length());
                        showKeyboard(et);
                    });
                }
            }
        }
        checkedHeader.setVisibility(checked > 0 ? View.VISIBLE : View.GONE);
        checkedHeader.setText(Lang.t("チェック済みのアイテム ") + checked + Lang.t(" 件"));
        checkedHeader.setCompoundDrawablesRelativeWithIntrinsicBounds(
                checkedExpanded ? R.drawable.ic_arrow_drop_down : R.drawable.ic_ts_chevron_right, 0, 0, 0);
        checkedItems.setVisibility(checkedExpanded ? View.VISIBLE : View.GONE);
        scheduleLinks();
    }

    private View checkRow(NoteStore.CheckItem item) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Math.round(8 * d), 0, Math.round(4 * d), 0);

        MaterialCheckBox box = new MaterialCheckBox(this);
        box.setChecked(item.checked);
        row.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText et = new EditText(this);
        et.setId(R.id.ne_item_text);
        et.setBackground(null);
        et.setText(item.text);
        et.setTextSize(16);
        et.setHint(Lang.t("リスト アイテム"));
        et.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        et.setHorizontallyScrolling(false);
        et.setMaxLines(Integer.MAX_VALUE);
        et.setTextColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface));
        if (item.checked) {
            et.setPaintFlags(et.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            et.setAlpha(0.6f);
        }
        row.addView(et, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton del = new ImageButton(this);
        del.setImageResource(R.drawable.ic_nt_close);
        del.setBackgroundResource(android.R.color.transparent);
        del.setContentDescription(Lang.t("削除"));
        del.setAlpha(0.7f);
        del.setVisibility(View.INVISIBLE);
        row.addView(del, new LinearLayout.LayoutParams(Math.round(44 * d), Math.round(44 * d)));

        et.addTextChangedListener(new SimpleWatcher(s -> {
            item.text = s;
            scheduleLinks();
        }));
        et.setOnFocusChangeListener((v, f) -> del.setVisibility(f ? View.VISIBLE : View.INVISIBLE));
        et.setOnEditorActionListener((v, actionId, ev) -> {
            boolean enter = actionId == EditorInfo.IME_ACTION_NEXT
                    || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN);
            if (!enter) return false;
            // Enter で、すぐ下に新しい項目を作る
            int idx = note.items.indexOf(item);
            int cursor = Math.min(Math.max(0, et.getSelectionStart()), et.length());
            String full = et.getText().toString();
            String rest = full.substring(cursor);
            if (!rest.isEmpty()) et.getText().delete(cursor, full.length());
            addItem(idx + 1, rest, false);
            return true;
        });
        et.setOnKeyListener((v, keyCode, ev) -> {
            if (keyCode == KeyEvent.KEYCODE_DEL && ev.getAction() == KeyEvent.ACTION_DOWN && et.length() == 0) {
                removeItem(item, true);
                return true;
            }
            return false;
        });
        del.setOnClickListener(v -> removeItem(item, false));
        box.setOnCheckedChangeListener((b, on) -> {
            item.checked = on;
            // チェックした項目は、少し縮んで消えてから「チェック済み」へ移る
            row.animate().alpha(0f).translationX((on ? 1 : -1) * 40 * d).setDuration(200).withEndAction(() -> {
                note.items.remove(item);
                if (on) note.items.add(item);
                else note.items.add(firstCheckedIndex(), item);
                rebuildChecklist(null);
            }).start();
        });
        return row;
    }

    private int firstCheckedIndex() {
        for (int i = 0; i < note.items.size(); i++) if (note.items.get(i).checked) return i;
        return note.items.size();
    }

    /**
     * 未チェックの項目を 1 つ足す。入力中の行を作り直すとキーボードの入力が乱れるので、
     * 足す行だけを差し込み、すぐに焦点を移す。
     */
    private void addItem(int index, String text, boolean atEnd) {
        NoteStore.CheckItem item = new NoteStore.CheckItem(text, false);
        int i = atEnd ? firstCheckedIndex() : Math.min(index, firstCheckedIndex());
        note.items.add(i, item);
        View row = checkRow(item);
        items.addView(row, Math.min(i, items.getChildCount()));
        row.setAlpha(0f);
        row.setTranslationY(-8 * getResources().getDisplayMetrics().density);
        row.animate().alpha(1f).translationY(0f).setDuration(200).start();
        EditText et = row.findViewById(R.id.ne_item_text);
        et.requestFocus();
        et.setSelection(0);
        showKeyboard(et);
    }

    private void removeItem(NoteStore.CheckItem item, boolean focusPrevious) {
        int idx = note.items.indexOf(item);
        if (idx < 0) return;
        if (item.checked) {
            note.items.remove(item);
            rebuildChecklist(null);
            return;
        }
        // 未チェックの行は note.items と同じ順で items に並んでいる
        View row = idx < items.getChildCount() ? items.getChildAt(idx) : null;
        note.items.remove(item);
        if (row != null) items.removeView(row);
        if (focusPrevious && idx > 0 && idx - 1 < items.getChildCount()) {
            EditText prev = items.getChildAt(idx - 1).findViewById(R.id.ne_item_text);
            prev.requestFocus();
            prev.setSelection(prev.length());
        }
        scheduleLinks();
    }

    // ------------------------------------------------------------------
    // 写真・動画・手書き
    // ------------------------------------------------------------------

    private void capturePhoto() {
        pendingCaptureFile = store.newMediaFile("jpg");
        pendingCaptureUri = uriFor(pendingCaptureFile);
        takePicture.launch(pendingCaptureUri);
    }

    private void captureVideoNow() {
        pendingCaptureFile = store.newMediaFile("mp4");
        pendingCaptureUri = uriFor(pendingCaptureFile);
        captureVideo.launch(pendingCaptureUri);
    }

    private void openDrawing(@Nullable String file) {
        editingDrawing = file;
        Intent i = new Intent(this, DrawingActivity.class);
        if (file != null) i.putExtra(DrawingActivity.EXTRA_FILE, file);
        drawing.launch(i);
        overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
    }

    /** 選んだ画像・動画をメモの media フォルダに複製する(元を消しても残るように) */
    @Nullable
    private NoteStore.Attachment importMedia(Uri uri) {
        String mime = getContentResolver().getType(uri);
        boolean video = mime != null && mime.startsWith("video/");
        String ext = mime != null ? MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) : null;
        if (ext == null) ext = video ? "mp4" : "jpg";
        File dst = store.newMediaFile(ext);
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(dst)) {
            if (in == null) return null;
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            dst.delete();
            return null;
        }
        NoteStore.Attachment a = new NoteStore.Attachment(video ? NoteStore.TYPE_VIDEO : NoteStore.TYPE_IMAGE, dst.getName());
        if (video) a.durationMs = mediaDuration(dst);
        return a;
    }

    private static long mediaDuration(File f) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getAbsolutePath());
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d != null ? Long.parseLong(d) : 0;
        } catch (Exception e) {
            return 0;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
            }
        }
    }

    private Uri uriFor(File f) {
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
    }

    /** 写真・動画・手書きを並べる。1 枚目は横いっぱい、2 枚目からは 2 列で */
    private void rebuildMedia(boolean animateLast) {
        LinearLayout box = findViewById(R.id.neMedia);
        box.removeAllViews();
        List<NoteStore.Attachment> visual = new ArrayList<>();
        for (NoteStore.Attachment a : note.attachments) {
            if (!NoteStore.TYPE_AUDIO.equals(a.type)) visual.add(a);
        }
        float d = getResources().getDisplayMetrics().density;
        int gap = Math.round(4 * d);
        LinearLayout row = null;
        for (int i = 0; i < visual.size(); i++) {
            NoteStore.Attachment a = visual.get(i);
            boolean wide = i == 0 && visual.size() % 2 == 1;
            View tile = mediaTile(a, wide);
            if (wide) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.round(220 * d));
                lp.bottomMargin = gap;
                box.addView(tile, lp);
            } else {
                if (row == null || row.getChildCount() >= 2) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            Math.round(160 * d));
                    rlp.bottomMargin = gap;
                    box.addView(row, rlp);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                if (row.getChildCount() == 0) lp.rightMargin = gap / 2;
                else lp.leftMargin = gap / 2;
                row.addView(tile, lp);
            }
            if (animateLast && i == visual.size() - 1) {
                tile.setAlpha(0f);
                tile.setScaleX(0.7f);
                tile.setScaleY(0.7f);
                tile.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380)
                        .setInterpolator(new OvershootInterpolator(1.4f)).start();
            }
        }
        if (row != null && row.getChildCount() == 1) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        }
        box.setPadding(box.getPaddingLeft(), visual.isEmpty() ? 0 : Math.round(4 * d), box.getPaddingRight(), 0);
    }

    private View mediaTile(NoteStore.Attachment a, boolean wide) {
        float d = getResources().getDisplayMetrics().density;
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundResource(R.drawable.bg_tool_card_low);
        f.setClipToOutline(true);
        ImageView img = new ImageView(this);
        img.setScaleType(NoteStore.TYPE_DRAWING.equals(a.type) ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        if (NoteStore.TYPE_DRAWING.equals(a.type)) img.setBackgroundColor(Color.WHITE);
        f.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        File file = store.media(a.file);
        io.execute(() -> {
            Bitmap b;
            if (NoteStore.TYPE_VIDEO.equals(a.type)) {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    r.setDataSource(file.getAbsolutePath());
                    b = r.getFrameAtTime(0);
                } catch (Exception e) {
                    b = null;
                } finally {
                    try {
                        r.release();
                    } catch (Exception ignored) {
                    }
                }
            } else {
                b = NotepadActivity.thumb(file, wide ? 1080 : 540);
            }
            Bitmap bmp = b;
            main.post(() -> {
                if (bmp != null) img.setImageBitmap(bmp);
            });
        });
        if (NoteStore.TYPE_VIDEO.equals(a.type)) {
            ImageView play = new ImageView(this);
            play.setImageResource(R.drawable.ic_nt_play);
            play.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(0x88000000);
            play.setBackground(circle);
            int p = Math.round(10 * d);
            play.setPadding(p, p, p, p);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(48 * d), Math.round(48 * d), Gravity.CENTER);
            f.addView(play, lp);
            if (a.durationMs > 0) {
                TextView dur = new TextView(this);
                dur.setText(formatTime(a.durationMs));
                dur.setTextColor(Color.WHITE);
                dur.setTextSize(12);
                dur.setShadowLayer(4, 0, 1, Color.BLACK);
                FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
                tlp.setMargins(0, 0, Math.round(10 * d), Math.round(8 * d));
                f.addView(dur, tlp);
            }
        }
        f.setOnClickListener(v -> {
            if (NoteStore.TYPE_DRAWING.equals(a.type)) {
                openDrawing(a.file);
            } else {
                viewFile(file, NoteStore.TYPE_VIDEO.equals(a.type) ? "video/*" : "image/*");
            }
        });
        f.setOnLongClickListener(v -> {
            BottomSheetDialog sheet = new BottomSheetDialog(this);
            LinearLayout box = sheetBox(this);
            sheetRow(box, R.drawable.ic_omni_share, Lang.t("共有"), () -> {
                sheet.dismiss();
                Intent send = new Intent(Intent.ACTION_SEND)
                        .setType(NoteStore.TYPE_VIDEO.equals(a.type) ? "video/*" : "image/*")
                        .putExtra(Intent.EXTRA_STREAM, uriFor(file))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, Lang.t("共有")));
            });
            sheetRow(box, R.drawable.ic_nt_delete, Lang.t("削除"), () -> {
                sheet.dismiss();
                v.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(200).withEndAction(() -> {
                    note.attachments.remove(a);
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                    rebuildMedia(false);
                }).start();
            });
            sheet.setContentView(box);
            sheet.show();
            return true;
        });
        return f;
    }

    private void viewFile(File f, String mime) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uriFor(f), mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Exception e) {
            Toast.makeText(this, Lang.t("開けるアプリがありません"), Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------
    // 録音
    // ------------------------------------------------------------------

    private void startRecordingFlow() {
        withPermission(Manifest.permission.RECORD_AUDIO, this::showRecorder);
    }

    private void showRecorder() {
        recordingFile = store.newMediaFile("m4a");
        try {
            MediaRecorder r = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? new MediaRecorder(this) : new MediaRecorder();
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioEncodingBitRate(128000);
            r.setAudioSamplingRate(44100);
            r.setOutputFile(recordingFile.getAbsolutePath());
            r.prepare();
            r.start();
            recorder = r;
            recordStart = SystemClock.elapsedRealtime();
        } catch (Exception e) {
            recorder = null;
            Toast.makeText(this, Lang.t("録音を始められませんでした"), Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setCancelable(false);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Math.round(24 * d), Math.round(28 * d), Math.round(24 * d), Math.round(24 * d));

        FrameLayout micBox = new FrameLayout(this);
        View pulse = new View(this);
        GradientDrawable pg = new GradientDrawable();
        pg.setShape(GradientDrawable.OVAL);
        pg.setColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorErrorContainer));
        pulse.setBackground(pg);
        micBox.addView(pulse, new FrameLayout.LayoutParams(Math.round(120 * d), Math.round(120 * d), Gravity.CENTER));
        ImageView mic = new ImageView(this);
        mic.setImageResource(R.drawable.ic_nt_mic);
        mic.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnError)));
        GradientDrawable mg = new GradientDrawable();
        mg.setShape(GradientDrawable.OVAL);
        mg.setColor(MaterialColors.getColor(root, androidx.appcompat.R.attr.colorError));
        mic.setBackground(mg);
        int mp = Math.round(20 * d);
        mic.setPadding(mp, mp, mp, mp);
        micBox.addView(mic, new FrameLayout.LayoutParams(Math.round(80 * d), Math.round(80 * d), Gravity.CENTER));
        box.addView(micBox, new LinearLayout.LayoutParams(Math.round(140 * d), Math.round(140 * d)));

        TextView time = new TextView(this);
        time.setTextSize(32);
        time.setText("0:00");
        time.setTextColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface));
        time.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Math.round(12 * d);
        box.addView(time, tlp);

        TextView hint = new TextView(this);
        hint.setText(Lang.t("録音しています…"));
        hint.setTextColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurfaceVariant));
        box.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Math.round(24 * d);
        MaterialButton cancel = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        cancel.setText(Lang.t("キャンセル"));
        MaterialButton stop = new MaterialButton(this);
        stop.setText(Lang.t("停止して保存"));
        stop.setIconResource(R.drawable.ic_nt_stop);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, Math.round(56 * d), 1f);
        half.rightMargin = Math.round(6 * d);
        buttons.addView(cancel, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, Math.round(56 * d), 1f);
        half2.leftMargin = Math.round(6 * d);
        buttons.addView(stop, half2);
        box.addView(buttons, blp);

        // マイクの周りが息をするように広がる
        ValueAnimator breathe = ValueAnimator.ofFloat(0.75f, 1.15f);
        breathe.setDuration(900);
        breathe.setRepeatMode(ValueAnimator.REVERSE);
        breathe.setRepeatCount(ValueAnimator.INFINITE);
        breathe.addUpdateListener(x -> {
            float s = (float) x.getAnimatedValue();
            float amp = 0f;
            try {
                if (recorder != null) amp = Math.min(1f, recorder.getMaxAmplitude() / 12000f);
            } catch (Exception ignored) {
            }
            pulse.setScaleX(s + amp * 0.25f);
            pulse.setScaleY(s + amp * 0.25f);
        });
        breathe.start();
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (recorder == null) return;
                time.setText(formatTime(SystemClock.elapsedRealtime() - recordStart));
                main.postDelayed(this, 250);
            }
        };
        main.post(tick);

        cancel.setOnClickListener(v -> {
            breathe.cancel();
            main.removeCallbacks(tick);
            stopRecording(false);
            sheet.dismiss();
        });
        stop.setOnClickListener(v -> {
            breathe.cancel();
            main.removeCallbacks(tick);
            stopRecording(true);
            sheet.dismiss();
        });
        sheet.setContentView(box);
        sheet.show();
    }

    private void stopRecording(boolean keep) {
        MediaRecorder r = recorder;
        if (r == null) return;
        recorder = null;
        long duration = SystemClock.elapsedRealtime() - recordStart;
        boolean ok = true;
        try {
            r.stop();
        } catch (Exception e) {
            ok = false; // 短すぎる録音など
        }
        r.release();
        if (keep && ok && recordingFile != null && recordingFile.length() > 0) {
            NoteStore.Attachment a = new NoteStore.Attachment(NoteStore.TYPE_AUDIO, recordingFile.getName());
            a.durationMs = duration;
            note.attachments.add(a);
            rebuildAudio();
            save();
        } else if (recordingFile != null) {
            //noinspection ResultOfMethodCallIgnored
            recordingFile.delete();
            if (keep) Toast.makeText(this, Lang.t("録音が短すぎます"), Toast.LENGTH_SHORT).show();
        }
        recordingFile = null;
    }

    private void rebuildAudio() {
        LinearLayout box = findViewById(R.id.neAudio);
        box.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        for (NoteStore.Attachment a : note.attachments) {
            if (!NoteStore.TYPE_AUDIO.equals(a.type)) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_tool_card_low);
            row.setPadding(Math.round(6 * d), Math.round(6 * d), Math.round(6 * d), Math.round(6 * d));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Math.round(6 * d);

            ImageButton play = new ImageButton(this);
            play.setImageResource(R.drawable.ic_nt_play);
            GradientDrawable pg = new GradientDrawable();
            pg.setShape(GradientDrawable.OVAL);
            pg.setColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorPrimaryContainer));
            play.setBackground(pg);
            play.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(root,
                    com.google.android.material.R.attr.colorOnPrimaryContainer)));
            play.setContentDescription(Lang.t("再生"));
            row.addView(play, new LinearLayout.LayoutParams(Math.round(44 * d), Math.round(44 * d)));

            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setPadding(Math.round(12 * d), 0, Math.round(8 * d), 0);
            LinearProgressIndicator bar = new LinearProgressIndicator(this);
            bar.setMax(1000);
            bar.setTrackCornerRadius(Math.round(4 * d));
            mid.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView time = new TextView(this);
            time.setTextSize(12);
            time.setText(formatTime(a.durationMs));
            time.setTextColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurfaceVariant));
            time.setPadding(0, Math.round(4 * d), 0, 0);
            mid.addView(time);
            row.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageButton del = new ImageButton(this);
            del.setImageResource(R.drawable.ic_nt_close);
            del.setBackgroundResource(android.R.color.transparent);
            del.setContentDescription(Lang.t("録音を削除"));
            row.addView(del, new LinearLayout.LayoutParams(Math.round(40 * d), Math.round(40 * d)));

            File file = store.media(a.file);
            play.setOnClickListener(v -> togglePlay(a, file, play, bar, time));
            del.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setTitle(Lang.t("録音を削除しますか？"))
                    .setPositiveButton(Lang.t("削除"), (dd, w) -> {
                        if (a.file.equals(playingFile)) stopPlayback();
                        note.attachments.remove(a);
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                        rebuildAudio();
                    })
                    .setNegativeButton(Lang.t("キャンセル"), null)
                    .show());
            box.addView(row, rlp);
        }
    }

    private void togglePlay(NoteStore.Attachment a, File file, ImageButton btn, LinearProgressIndicator bar, TextView time) {
        if (player != null && a.file.equals(playingFile)) {
            if (player.isPlaying()) {
                player.pause();
                btn.setImageResource(R.drawable.ic_nt_play);
            } else {
                player.start();
                btn.setImageResource(R.drawable.ic_nt_pause);
                progressLoop(bar, time, a);
            }
            return;
        }
        stopPlayback();
        try {
            MediaPlayer p = new MediaPlayer();
            p.setDataSource(file.getAbsolutePath());
            p.prepare();
            p.start();
            player = p;
            playingFile = a.file;
            btn.setImageResource(R.drawable.ic_nt_pause);
            p.setOnCompletionListener(mp -> {
                btn.setImageResource(R.drawable.ic_nt_play);
                bar.setProgressCompat(0, true);
                time.setText(formatTime(a.durationMs));
                stopPlayback();
            });
            progressLoop(bar, time, a);
        } catch (Exception e) {
            Toast.makeText(this, Lang.t("再生できませんでした"), Toast.LENGTH_SHORT).show();
        }
        // ほかの行の再生ボタンを戻す
        LinearLayout box = findViewById(R.id.neAudio);
        for (int i = 0; i < box.getChildCount(); i++) {
            ImageButton other = (ImageButton) ((ViewGroup) box.getChildAt(i)).getChildAt(0);
            if (other != btn) other.setImageResource(R.drawable.ic_nt_play);
        }
    }

    private void progressLoop(LinearProgressIndicator bar, TextView time, NoteStore.Attachment a) {
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                MediaPlayer p = player;
                if (p == null || !a.file.equals(playingFile)) return;
                try {
                    int dur = p.getDuration();
                    int pos = p.getCurrentPosition();
                    if (dur > 0) bar.setProgressCompat((int) (pos * 1000L / dur), true);
                    time.setText(formatTime(pos) + " / " + formatTime(dur));
                    if (p.isPlaying()) main.postDelayed(this, 200);
                } catch (IllegalStateException ignored) {
                }
            }
        }, 0);
    }

    private void stopPlayback() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        playingFile = null;
    }

    static String formatTime(long ms) {
        long s = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------
    // URL のプレビュー
    // ------------------------------------------------------------------

    private void scheduleLinks() {
        main.removeCallbacks(linkTask);
        main.postDelayed(linkTask, 1200);
    }

    private void refreshLinks() {
        StringBuilder all = new StringBuilder(note.title).append('\n');
        if (note.checklist) {
            for (NoteStore.CheckItem i : note.items) all.append(i.text).append('\n');
        } else {
            all.append(body.getText());
        }
        List<String> urls = Markdown.findUrls(all.toString());
        boolean changed = false;
        // 本文から消えた URL のプレビューは捨てる
        Iterator<java.util.Map.Entry<String, NoteStore.LinkPreview>> it = note.links.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, NoteStore.LinkPreview> e = it.next();
            if (!urls.contains(e.getKey())) {
                if (e.getValue().imageFile != null) {
                    //noinspection ResultOfMethodCallIgnored
                    store.media(e.getValue().imageFile).delete();
                }
                it.remove();
                changed = true;
            }
        }
        for (String u : urls) {
            if (note.links.containsKey(u)) continue;
            NoteStore.LinkPreview loading = new NoteStore.LinkPreview();
            loading.url = u;
            loading.title = Uri.parse(u).getHost();
            note.links.put(u, loading);
            changed = true;
            LinkPreviewFetcher.fetch(store, u, p -> {
                if (isDestroyed() || !note.links.containsKey(u)) return;
                NoteStore.LinkPreview cur = note.links.get(u);
                if (cur != null && cur.hidden) p.hidden = true;
                note.links.put(u, p);
                rebuildLinks();
            });
        }
        if (changed) rebuildLinks();
    }

    private void rebuildLinks() {
        LinearLayout box = findViewById(R.id.neLinks);
        int before = box.getChildCount();
        box.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        for (NoteStore.LinkPreview p : note.links.values()) {
            if (p.hidden) continue;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackgroundResource(R.drawable.bg_tool_card_low);
            card.setClipToOutline(true);
            card.setForeground(ContextCompat.getDrawable(this, android.R.drawable.list_selector_background));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.round(80 * d));
            clp.topMargin = Math.round(8 * d);

            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (p.imageFile != null) {
                img.setImageBitmap(NotepadActivity.thumb(store.media(p.imageFile), 240));
            } else {
                img.setImageResource(R.drawable.ic_nt_link);
                int pad = Math.round(24 * d);
                img.setPadding(pad, pad, pad, pad);
                img.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(root,
                        com.google.android.material.R.attr.colorOnSurfaceVariant)));
            }
            card.addView(img, new LinearLayout.LayoutParams(Math.round(80 * d), ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Math.round(14 * d), 0, Math.round(4 * d), 0);
            TextView t = new TextView(this);
            t.setText(p.title != null ? p.title : p.url);
            t.setMaxLines(2);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setTextSize(15);
            t.setTextColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface));
            texts.addView(t);
            TextView host = new TextView(this);
            String h = Uri.parse(p.url).getHost();
            host.setText(h != null ? h : p.url);
            host.setSingleLine(true);
            host.setTextSize(12);
            host.setTextColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurfaceVariant));
            host.setPadding(0, Math.round(2 * d), 0, 0);
            texts.addView(host);
            card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageButton hide = new ImageButton(this);
            hide.setImageResource(R.drawable.ic_nt_close);
            hide.setBackgroundResource(android.R.color.transparent);
            hide.setContentDescription(Lang.t("プレビューを消す"));
            hide.setAlpha(0.7f);
            card.addView(hide, new LinearLayout.LayoutParams(Math.round(44 * d), Math.round(44 * d)));

            card.setOnClickListener(v -> openUrl(p.url));
            hide.setOnClickListener(v -> card.animate().alpha(0f).translationX(60 * d).setDuration(200).withEndAction(() -> {
                p.hidden = true;
                rebuildLinks();
            }).start());
            box.addView(card, clp);
        }
        // 新しく増えたカードは下からふわっと出す
        for (int i = before; i < box.getChildCount(); i++) {
            View c = box.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(16 * d);
            c.animate().alpha(1f).translationY(0f).setDuration(320).start();
        }
    }

    // ------------------------------------------------------------------
    // 共有・書き出し
    // ------------------------------------------------------------------

    private void share() {
        save();
        String text = NoteExporter.toText(note);
        ArrayList<Uri> streams = new ArrayList<>();
        for (NoteStore.Attachment a : note.attachments) streams.add(uriFor(store.media(a.file)));
        Intent send;
        if (streams.isEmpty()) {
            send = new Intent(Intent.ACTION_SEND).setType("text/plain");
        } else {
            send = new Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, streams)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ClipData clip = ClipData.newRawUri("", streams.get(0));
            for (int i = 1; i < streams.size(); i++) clip.addItem(new ClipData.Item(streams.get(i)));
            send.setClipData(clip);
        }
        send.putExtra(Intent.EXTRA_TEXT, text);
        if (!note.title.trim().isEmpty()) send.putExtra(Intent.EXTRA_SUBJECT, note.title.trim());
        startActivity(Intent.createChooser(send, Lang.t("メモを共有")));
    }

    private void chooseExport() {
        save();
        List<String> labels = new ArrayList<>();
        labels.add("Markdown (.md)");
        labels.add(Lang.t("テキスト (.txt)"));
        if (!note.attachments.isEmpty()) labels.add(Lang.t("ZIP (添付ファイルも含む)"));
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("ファイルに出力"))
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        exportFormat = "md";
                        exportMd.launch(NoteExporter.fileName(note, "md"));
                    } else if (which == 1) {
                        exportFormat = "txt";
                        exportTxt.launch(NoteExporter.fileName(note, "txt"));
                    } else {
                        exportFormat = "zip";
                        exportZip.launch(NoteExporter.fileName(note, "zip"));
                    }
                })
                .show();
    }

    private void onExport(@Nullable Uri uri) {
        if (uri == null) return;
        try {
            switch (exportFormat) {
                case "zip":
                    List<NoteStore.Note> one = new ArrayList<>();
                    one.add(note);
                    NoteExporter.writeZip(this, uri, one);
                    break;
                case "txt":
                    NoteExporter.writeText(this, uri, NoteExporter.toText(note));
                    break;
                default:
                    NoteExporter.writeText(this, uri, NoteExporter.toMarkdown(note, null));
                    break;
            }
            Toast.makeText(this, Lang.t("保存しました"), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, Lang.t("保存に失敗しました"), Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------
    // 小物
    // ------------------------------------------------------------------

    private static boolean copyFile(File src, File dst) {
        try (InputStream in = new java.io.FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void showKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
    }

    private interface StringListener {
        void on(String s);
    }

    private static final class SimpleWatcher implements TextWatcher {
        private final StringListener l;

        SimpleWatcher(StringListener l) {
            this.l = l;
        }

        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

        @Override
        public void afterTextChanged(Editable s) {
            l.on(s.toString());
        }
    }
}
