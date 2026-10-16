package com.airsky.browser;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * QR コードの作成。文字や URL(またはファイルを data URI にしたもの)から作り、
 * ドットの形・角の目印の形・色(グラデーションも)・誤り訂正・余白を選べる。
 * 選んだデザインは次に開いた時も使う。
 */
public class QrCodeActivity extends AppCompatActivity {

    private static final int FILE_SELECT_REQUEST = 1;
    private static final int MAX_QR_DATA_LENGTH = 2953;
    private static final int PREVIEW_PX = 720;
    private static final int EXPORT_PX = 1200;
    private static final String PREFS = "qr_style";

    private static final int[] FG_COLORS = {
            0xFF000000, 0xFF1A1C1E, 0xFF0B57D0, 0xFF146C2E, 0xFFB3261E, 0xFF7D5260,
            0xFF6750A4, 0xFFE8710A, 0xFF00639B, 0xFF8E24AA};
    private static final int[] BG_COLORS = {
            0xFFFFFFFF, ColorSwatches.TRANSPARENT, 0xFFF8F9FA, 0xFFFFF8E1, 0xFFE8F0FE, 0xFFE6F4EA,
            0xFFFCE8E6, 0xFF1A1C1E};

    private final QrRenderer.Style style = new QrRenderer.Style();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable renderTask = this::renderPreview;
    private int renderGeneration;

    private TextInputEditText input;
    private TextInputLayout inputLayout;
    private ImageView preview;
    private TextView placeholder;
    private TextView warn;
    private Chip fileChip;
    private Button saveButton;
    private Button shareButton;

    /** ファイルから作った時の中身(data URI)。null なら入力欄の文字を使う */
    @Nullable private String fileContent;
    @Nullable private String fileName;
    @Nullable private Bitmap lastPreview;
    private volatile boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_code);
        TouchEffectView.attach(getWindow());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        }
        ToolChrome.setup(this, Lang.t("QR コードを作成"));
        loadStyle();

        input = findViewById(R.id.qrInput);
        inputLayout = findViewById(R.id.qrInputLayout);
        inputLayout.setCounterMaxLength(MAX_QR_DATA_LENGTH);
        preview = findViewById(R.id.qrPreview);
        placeholder = findViewById(R.id.qrPlaceholder);
        warn = findViewById(R.id.qrWarn);
        fileChip = findViewById(R.id.qrFileChip);
        saveButton = findViewById(R.id.qrSave);
        shareButton = findViewById(R.id.qrShare);

        // プレビューは画面の幅に合わせた正方形にする
        View box = findViewById(R.id.qrPreviewBox);
        box.post(() -> {
            View card = (View) box.getParent();
            int w = card.getWidth() - card.getPaddingLeft() - card.getPaddingRight();
            int side = Math.min(w, Math.round(320 * getResources().getDisplayMetrics().density));
            if (side > 0) {
                box.getLayoutParams().width = side;
                box.getLayoutParams().height = side;
                box.requestLayout();
            }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (fileContent != null && s.length() > 0) clearFile();
                schedule();
            }
        });
        Intent intent = getIntent();
        String shared = intent != null ? intent.getStringExtra(Intent.EXTRA_TEXT) : null;
        if (shared != null) input.setText(shared);

        findViewById(R.id.qrFile).setOnClickListener(v -> selectFile());
        fileChip.setOnCloseIconClickListener(v -> {
            clearFile();
            schedule();
        });
        saveButton.setOnClickListener(v -> export(false));
        shareButton.setOnClickListener(v -> export(true));
        findViewById(R.id.qrReset).setOnClickListener(v -> {
            QrRenderer.Style d = new QrRenderer.Style();
            style.dot = d.dot;
            style.eye = d.eye;
            style.fg = d.fg;
            style.bg = d.bg;
            style.eyeColor = d.eyeColor;
            style.gradient = d.gradient;
            style.fg2 = d.fg2;
            style.ec = d.ec;
            style.margin = d.margin;
            bindOptions();
            changed();
        });

        bindOptions();
        schedule();
    }

    // ------------------------------------------------------------------
    // デザインの選択肢
    // ------------------------------------------------------------------

    private void bindOptions() {
        ChipGroup dots = findViewById(R.id.qrDotGroup);
        fillChips(dots, new String[]{Lang.t("四角"), Lang.t("丸"), Lang.t("小さな丸"), Lang.t("なめらか"), Lang.t("ひし形")},
                new int[]{QrRenderer.DOT_SQUARE, QrRenderer.DOT_CIRCLE, QrRenderer.DOT_SMALL_CIRCLE,
                        QrRenderer.DOT_ROUNDED, QrRenderer.DOT_DIAMOND},
                style.dot, v -> {
                    style.dot = v;
                    changed();
                });
        ChipGroup eyes = findViewById(R.id.qrEyeGroup);
        fillChips(eyes, new String[]{Lang.t("四角"), Lang.t("角丸"), Lang.t("丸"), Lang.t("葉っぱ")},
                new int[]{QrRenderer.EYE_SQUARE, QrRenderer.EYE_ROUNDED, QrRenderer.EYE_CIRCLE,
                        QrRenderer.EYE_LEAF},
                style.eye, v -> {
                    style.eye = v;
                    changed();
                });

        ColorSwatches.build(findViewById(R.id.qrFgColors), FG_COLORS, style.fg, null, c -> {
            style.fg = c;
            changed();
        });
        ColorSwatches.build(findViewById(R.id.qrFg2Colors), FG_COLORS, style.fg2, null, c -> {
            style.fg2 = c;
            changed();
        });
        ColorSwatches.build(findViewById(R.id.qrEyeColors), FG_COLORS, style.eyeColor, Lang.t("同じ"), c -> {
            style.eyeColor = c;
            changed();
        });
        ColorSwatches.build(findViewById(R.id.qrBgColors), BG_COLORS, style.bg, null, c -> {
            style.bg = c;
            changed();
        });

        MaterialSwitch gradient = findViewById(R.id.qrGradient);
        View fg2Box = findViewById(R.id.qrFg2Box);
        gradient.setOnCheckedChangeListener(null);
        gradient.setChecked(style.gradient);
        fg2Box.setVisibility(style.gradient ? View.VISIBLE : View.GONE);
        gradient.setOnCheckedChangeListener((b, on) -> {
            style.gradient = on;
            if (on) {
                fg2Box.setVisibility(View.VISIBLE);
                fg2Box.setAlpha(0f);
                fg2Box.setTranslationY(-12 * getResources().getDisplayMetrics().density);
                fg2Box.animate().alpha(1f).translationY(0f).setDuration(260).start();
            } else {
                fg2Box.setVisibility(View.GONE);
            }
            changed();
        });

        MaterialButtonToggleGroup ec = findViewById(R.id.qrEcGroup);
        ec.clearOnButtonCheckedListeners();
        int ecId = style.ec == ErrorCorrectionLevel.L ? R.id.qrEcL
                : style.ec == ErrorCorrectionLevel.Q ? R.id.qrEcQ
                : style.ec == ErrorCorrectionLevel.H ? R.id.qrEcH : R.id.qrEcM;
        ec.check(ecId);
        ec.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            style.ec = id == R.id.qrEcL ? ErrorCorrectionLevel.L
                    : id == R.id.qrEcQ ? ErrorCorrectionLevel.Q
                    : id == R.id.qrEcH ? ErrorCorrectionLevel.H : ErrorCorrectionLevel.M;
            changed();
        });

        Slider margin = findViewById(R.id.qrMargin);
        margin.clearOnChangeListeners();
        margin.setValue(style.margin);
        margin.addOnChangeListener((s, v, fromUser) -> {
            style.margin = Math.round(v);
            changed();
        });
    }

    private interface IntListener {
        void on(int value);
    }

    private void fillChips(ChipGroup group, String[] labels, int[] values, int selected, IntListener l) {
        group.setOnCheckedStateChangeListener(null);
        group.removeAllViews();
        for (int i = 0; i < labels.length; i++) {
            Chip chip = new Chip(group.getContext(), null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setText(labels[i]);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(true);
            chip.setTag(values[i]);
            group.addView(chip);
            if (values[i] == selected) chip.setChecked(true);
        }
        group.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            View chip = g.findViewById(ids.get(0));
            chip.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90).withEndAction(() ->
                    chip.animate().scaleX(1f).scaleY(1f).setDuration(180).start()).start();
            l.on((Integer) chip.getTag());
        });
    }

    private void changed() {
        saveStyle();
        schedule();
    }

    // ------------------------------------------------------------------
    // プレビュー
    // ------------------------------------------------------------------

    @Nullable
    private String content() {
        if (fileContent != null) return fileContent;
        CharSequence t = input.getText();
        String s = t != null ? t.toString() : "";
        return s.trim().isEmpty() ? null : s;
    }

    private void schedule() {
        main.removeCallbacks(renderTask);
        main.postDelayed(renderTask, 120);
    }

    private void renderPreview() {
        String text = content();
        updateContrastWarning();
        if (text == null) {
            renderGeneration++;
            showPreview(null);
            return;
        }
        int gen = ++renderGeneration;
        QrRenderer.Style snapshot = copyStyle();
        worker.execute(() -> {
            Bitmap bmp = null;
            String error = null;
            try {
                bmp = QrRenderer.render(text, snapshot, PREVIEW_PX);
            } catch (WriterException | IllegalArgumentException e) {
                error = Lang.t("内容が長すぎて QR コードにできません。誤り訂正を下げると入る場合があります");
            }
            Bitmap result = bmp;
            String err = error;
            main.post(() -> {
                if (gen != renderGeneration || isFinishing()) return;
                inputLayout.setError(err);
                showPreview(result);
            });
        });
    }

    private void showPreview(@Nullable Bitmap bmp) {
        boolean had = lastPreview != null;
        lastPreview = bmp;
        saveButton.setEnabled(bmp != null);
        shareButton.setEnabled(bmp != null);
        if (bmp == null) {
            preview.setImageDrawable(null);
            placeholder.setVisibility(View.VISIBLE);
            return;
        }
        placeholder.setVisibility(View.GONE);
        preview.setImageBitmap(bmp);
        if (!had) {
            // 初めて出る時は、小さく回りながら現れる
            preview.setScaleX(0.6f);
            preview.setScaleY(0.6f);
            preview.setRotation(-12f);
            preview.setAlpha(0f);
            preview.animate().scaleX(1f).scaleY(1f).rotation(0f).alpha(1f).setDuration(420)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();
        }
    }

    private void updateContrastWarning() {
        int bg = Color.alpha(style.bg) == 0 ? Color.WHITE : style.bg;
        double c = QrRenderer.contrast(style.fg, bg);
        if (style.gradient) c = Math.min(c, QrRenderer.contrast(style.fg2, bg));
        if (style.eyeColor != ColorSwatches.NONE) c = Math.min(c, QrRenderer.contrast(style.eyeColor, bg));
        boolean darkBg = QrRenderer.contrast(bg, Color.BLACK) < QrRenderer.contrast(bg, Color.WHITE);
        String msg = null;
        if (c < 2.5) msg = Lang.t("ドットと背景の色が近いため、読み取れない場合があります");
        else if (darkBg && QrRenderer.contrast(style.fg, Color.WHITE) < QrRenderer.contrast(bg, Color.WHITE))
            msg = Lang.t("明るいドットに暗い背景は、読み取れないアプリがあります");
        warn.setText(msg);
        warn.setVisibility(msg == null ? View.GONE : View.VISIBLE);
    }

    private QrRenderer.Style copyStyle() {
        QrRenderer.Style s = new QrRenderer.Style();
        s.dot = style.dot;
        s.eye = style.eye;
        s.fg = style.fg;
        s.bg = style.bg;
        s.eyeColor = style.eyeColor;
        s.gradient = style.gradient;
        s.fg2 = style.fg2;
        s.ec = style.ec;
        s.margin = style.margin;
        return s;
    }

    private void loadStyle() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        style.dot = p.getInt("dot", style.dot);
        style.eye = p.getInt("eye", style.eye);
        style.fg = p.getInt("fg", style.fg);
        style.bg = p.getInt("bg", style.bg);
        style.eyeColor = p.getInt("eyeColor", style.eyeColor);
        style.gradient = p.getBoolean("gradient", style.gradient);
        style.fg2 = p.getInt("fg2", style.fg2);
        try {
            style.ec = ErrorCorrectionLevel.valueOf(p.getString("ec", style.ec.name()));
        } catch (IllegalArgumentException ignored) {
        }
        style.margin = p.getInt("margin", style.margin);
    }

    private void saveStyle() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt("dot", style.dot)
                .putInt("eye", style.eye)
                .putInt("fg", style.fg)
                .putInt("bg", style.bg)
                .putInt("eyeColor", style.eyeColor)
                .putBoolean("gradient", style.gradient)
                .putInt("fg2", style.fg2)
                .putString("ec", style.ec.name())
                .putInt("margin", style.margin)
                .apply();
    }

    // ------------------------------------------------------------------
    // ファイルから作る
    // ------------------------------------------------------------------

    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, FILE_SELECT_REQUEST);
    }

    private void clearFile() {
        fileContent = null;
        fileName = null;
        fileChip.setVisibility(View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_SELECT_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri fileUri = data.getData();
            String name = getFileName(fileUri);
            String uri = createDataUri(fileUri);
            if (uri != null) {
                input.setText("");
                fileContent = uri;
                fileName = name;
                fileChip.setText(name);
                fileChip.setVisibility(View.VISIBLE);
                fileChip.setScaleX(0.6f);
                fileChip.setScaleY(0.6f);
                fileChip.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
                schedule();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) result = cursor.getString(index);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (result == null) result = uri.getLastPathSegment();
        return result != null ? result : "unknown";
    }

    @Nullable
    private String createDataUri(Uri fileUri) {
        String mimeType = getContentResolver().getType(fileUri);
        if (mimeType == null) mimeType = "application/octet-stream";
        String base64Data = convertFileToBase64(fileUri);
        if (base64Data == null) {
            Toast.makeText(this, Lang.t("ファイルの変換に失敗しました"), Toast.LENGTH_LONG).show();
            return null;
        }
        String prefix = mimeType.equals("text/plain")
                ? "data:" + mimeType + ";charset=utf-8;base64,"
                : "data:" + mimeType + ";base64,";
        String dataUri = prefix + base64Data;
        if (dataUri.length() > MAX_QR_DATA_LENGTH) {
            Toast.makeText(this, Lang.t("ファイル容量がQRコードのサイズを超えています"), Toast.LENGTH_LONG).show();
            return null;
        }
        return dataUri;
    }

    @Nullable
    private String convertFileToBase64(Uri fileUri) {
        try (InputStream in = getContentResolver().openInputStream(fileUri)) {
            if (in == null) return null;
            BufferedInputStream bis = new BufferedInputStream(in);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Base64OutputStream b64 = new Base64OutputStream(bytes, Base64.NO_WRAP);
            byte[] buffer = new byte[8192];
            int n;
            int total = 0;
            while ((n = bis.read(buffer)) != -1) {
                total += n;
                // QR に入らない大きさなら、全部読まずにやめる
                if (total > MAX_QR_DATA_LENGTH) return new String(new char[MAX_QR_DATA_LENGTH + 1]);
                b64.write(buffer, 0, n);
            }
            b64.close();
            return bytes.toString("UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 保存・共有
    // ------------------------------------------------------------------

    private void export(boolean share) {
        String text = content();
        if (text == null || isProcessing) return;
        isProcessing = true;
        QrRenderer.Style snapshot = copyStyle();
        String base = (fileName != null ? fileName + "_" : "QR_") + new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        worker.execute(() -> {
            Uri result = null;
            try {
                Bitmap bmp = QrRenderer.render(text, snapshot, EXPORT_PX);
                result = share ? writeShareFile(bmp, base + ".png") : saveBitmapToPictures(bmp, base + ".png");
            } catch (WriterException | IllegalArgumentException e) {
                e.printStackTrace();
            }
            Uri uri = result;
            main.post(() -> {
                isProcessing = false;
                if (isFinishing()) return;
                if (uri == null) {
                    Toast.makeText(this, share ? Lang.t("共有できませんでした") : Lang.t("保存に失敗しました"), Toast.LENGTH_LONG).show();
                } else if (share) {
                    Intent send = new Intent(Intent.ACTION_SEND)
                            .setType("image/png")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, Lang.t("QR コードを共有")));
                } else {
                    Toast.makeText(this, Lang.t("画像を保存しました: ") + base + ".png", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Nullable
    private Uri writeShareFile(Bitmap bmp, String name) {
        File dir = new File(getCacheDir(), "shared");
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        File f = new File(dir, name);
        try (OutputStream out = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
    }

    @Nullable
    private Uri saveBitmapToPictures(Bitmap bitmap, String fileName) {
        try {
            Uri uri;
            OutputStream out;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
                uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return null;
                out = getContentResolver().openOutputStream(uri);
            } else {
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), fileName);
                uri = Uri.fromFile(file);
                out = new FileOutputStream(file);
            }
            if (out == null) return null;
            try (OutputStream o = out) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, o);
            }
            return uri;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        if (isProcessing) {
            Toast.makeText(this, Lang.t("変換中はバックキーが無効です"), Toast.LENGTH_SHORT).show();
        } else {
            finish();
            overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
        }
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(renderTask);
        worker.shutdown();
        super.onDestroy();
    }
}
