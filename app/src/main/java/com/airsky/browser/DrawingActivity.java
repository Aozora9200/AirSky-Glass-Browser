package com.airsky.browser;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * メモに付ける手書き・図形の画面。完了で PNG を media フォルダに保存し、ファイル名を返す。
 * EXTRA_FILE を渡すと、その絵の続きを描く(保存すると同じファイルを上書きする)。
 */
public class DrawingActivity extends AppCompatActivity {

    static final String EXTRA_FILE = "file";

    private static final int[] COLORS = {
            0xFF000000, 0xFF5F6368, 0xFFFFFFFF, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835,
            0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA, 0xFFD81B60, 0xFF6D4C41};

    private DrawingView canvas;
    private boolean dirty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawing);
        TouchEffectView.attach(getWindow());
        canvas = findViewById(R.id.drCanvas);
        canvas.setPaperColor(Color.WHITE);
        canvas.setOnChangeListener(() -> {
            dirty = true;
            updateUndo();
        });

        String file = getIntent().getStringExtra(EXTRA_FILE);
        if (file != null) {
            File f = NoteStore.get(this).media(file);
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) canvas.post(() -> canvas.setBase(b));
        }

        findViewById(R.id.drBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.drUndo).setOnClickListener(v -> canvas.undo());
        findViewById(R.id.drRedo).setOnClickListener(v -> canvas.redo());
        findViewById(R.id.drClear).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("すべて消しますか？"))
                .setPositiveButton(Lang.t("消す"), (d, w) -> canvas.clear())
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show());
        findViewById(R.id.drDone).setOnClickListener(v -> save());

        MaterialButtonToggleGroup tools = findViewById(R.id.drTools);
        tools.check(R.id.drPen);
        tools.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            int t = id == R.id.drMarker ? DrawingView.TOOL_MARKER
                    : id == R.id.drEraser ? DrawingView.TOOL_ERASER
                    : id == R.id.drLine ? DrawingView.TOOL_LINE
                    : id == R.id.drArrow ? DrawingView.TOOL_ARROW
                    : id == R.id.drRect ? DrawingView.TOOL_RECT
                    : id == R.id.drOval ? DrawingView.TOOL_OVAL : DrawingView.TOOL_PEN;
            canvas.setTool(t);
            g.findViewById(id).animate().scaleX(1.1f).scaleY(1.1f).setDuration(90).withEndAction(() ->
                    g.findViewById(id).animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
        });

        float density = getResources().getDisplayMetrics().density;
        Slider width = findViewById(R.id.drWidth);
        width.addOnChangeListener((s, value, fromUser) -> canvas.setStrokeWidth(value * density));
        MaterialCheckBox fill = findViewById(R.id.drFill);
        fill.setOnCheckedChangeListener((b, on) -> canvas.setFill(on));

        LinearLayout colors = findViewById(R.id.drColors);
        ColorSwatches.build(colors, COLORS, COLORS[0], null, c -> canvas.setColor(c));
        updateUndo();
    }

    private void updateUndo() {
        findViewById(R.id.drUndo).setAlpha(canvas.canUndo() ? 1f : 0.35f);
        findViewById(R.id.drRedo).setAlpha(canvas.canRedo() ? 1f : 0.35f);
    }

    private void save() {
        if (canvas.isBlank()) {
            finishWithAnimation();
            return;
        }
        NoteStore store = NoteStore.get(this);
        String name = getIntent().getStringExtra(EXTRA_FILE);
        File f = name != null ? store.media(name) : store.newMediaFile("png");
        Bitmap bmp = canvas.export();
        try (OutputStream out = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            Toast.makeText(this, Lang.t("保存できませんでした"), Toast.LENGTH_SHORT).show();
            return;
        }
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_FILE, f.getName()));
        finishWithAnimation();
    }

    @Override
    public void onBackPressed() {
        if (!dirty) {
            finishWithAnimation();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("描いた内容を保存しますか？"))
                .setPositiveButton(Lang.t("保存"), (d, w) -> save())
                .setNegativeButton(Lang.t("破棄"), (d, w) -> finishWithAnimation())
                .setNeutralButton(Lang.t("キャンセル"), null)
                .show();
    }

    private void finishWithAnimation() {
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }
}
