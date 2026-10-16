package com.airsky.browser;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 共有ボタンのシート。ページの見出しの下に「QR コード」「リンクをコピー」「その他のアプリ」を並べ、
 * 「QR コード」を押すとその場で QR コードが広がって出てくる(近くの人にカメラで読み取ってもらう)。
 * QR コードは画像として共有・保存もできる。
 */
final class ShareSheet {

    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final int QR_EXPORT_PX = 1024;

    private final Activity activity;
    private final String url;
    @Nullable private final String title;
    @Nullable private final Bitmap favicon;
    private final BottomSheetDialog dialog;
    private final float dp;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private FrameLayout qrPanel;
    private ImageView qrImage;
    private LinearLayout qrActions;
    private View qrChip;
    @Nullable private Bitmap qrBitmap;
    private boolean qrShown;

    static void show(@NonNull Activity activity, @NonNull String url, @Nullable String title,
                     @Nullable Bitmap favicon, boolean startWithQr) {
        ShareSheet s = new ShareSheet(activity, url, title, favicon);
        s.dialog.show();
        if (startWithQr) s.main.post(s::toggleQr);
    }

    private ShareSheet(Activity activity, String url, @Nullable String title, @Nullable Bitmap favicon) {
        this.activity = activity;
        this.url = url;
        this.title = title;
        this.favicon = favicon;
        dialog = DualScreenController.newBottomSheet(activity);
        this.dp = dialog.getContext().getResources().getDisplayMetrics().density;
        dialog.setContentView(SheetScroll.wrap(build(dialog.getContext())));
        dialog.setCanceledOnTouchOutside(true);
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.setOnDismissListener(d -> worker.shutdownNow());
    }

    // ------------------------------------------------------------------
    // 画面
    // ------------------------------------------------------------------

    private View build(Context ctx) {
        int onSurface = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        int onVariant = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        int highest = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurfaceContainerHighest, Color.LTGRAY);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, px(20));

        // つまみ
        View handle = new View(ctx);
        GradientDrawable hd = new GradientDrawable();
        hd.setCornerRadius(px(2));
        hd.setColor(onVariant);
        handle.setBackground(hd);
        handle.setAlpha(0.4f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(px(36), px(4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = px(10);
        root.addView(handle, hlp);

        // 見出し: ファビコン・タイトル・URL
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(px(20), px(14), px(20), px(10));
        FrameLayout iconBg = new FrameLayout(ctx);
        iconBg.setBackground(round(highest, px(16)));
        ImageView icon = new ImageView(ctx);
        if (favicon != null) {
            icon.setImageBitmap(favicon);
        } else {
            icon.setImageResource(R.drawable.ic_si_globe);
            icon.setImageTintList(ColorStateList.valueOf(onVariant));
        }
        iconBg.addView(icon, new FrameLayout.LayoutParams(px(24), px(24), Gravity.CENTER));
        header.addView(iconBg, new LinearLayout.LayoutParams(px(44), px(44)));
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(ctx);
        t.setText(TextUtils.isEmpty(title) ? url : title);
        t.setTextColor(onSurface);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        TextView u = new TextView(ctx);
        u.setText(url);
        u.setTextColor(onVariant);
        u.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        u.setSingleLine(true);
        u.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        texts.addView(t);
        texts.addView(u);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(px(16));
        header.addView(texts, tlp);
        root.addView(header);

        // QR コード(最初は畳んでおき、押すと広がる)
        qrPanel = new FrameLayout(ctx);
        qrPanel.setVisibility(View.GONE);
        LinearLayout qrColumn = new LinearLayout(ctx);
        qrColumn.setOrientation(LinearLayout.VERTICAL);
        qrColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        qrColumn.setPadding(0, px(8), 0, px(4));
        FrameLayout qrCard = new FrameLayout(ctx);
        qrCard.setBackground(round(Color.WHITE, px(28)));
        qrCard.setElevation(px(1));
        qrCard.setPadding(px(14), px(14), px(14), px(14));
        qrImage = new ImageView(ctx);
        qrImage.setContentDescription(Lang.t("このページの QR コード"));
        qrCard.addView(qrImage, new FrameLayout.LayoutParams(px(212), px(212)));
        // QR コードを長押しすると、画像としてクリップボードにコピーする
        qrCard.setForeground(ctx.getDrawable(resolve(ctx, android.R.attr.selectableItemBackground)));
        qrCard.setClipToOutline(true);
        qrCard.setOnLongClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(110).setInterpolator(EMPHASIZED)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(260)
                            .setInterpolator(EMPHASIZED).start())
                    .start();
            copyQr();
            return true;
        });
        qrColumn.addView(qrCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView hint = new TextView(ctx);
        hint.setText(Lang.t("カメラで読み取るとこのページを開けます\n長押しで QR コードをコピー"));
        hint.setGravity(Gravity.CENTER);
        hint.setTextColor(onVariant);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = px(12);
        qrColumn.addView(hint, hintLp);
        qrActions = new LinearLayout(ctx);
        qrActions.setOrientation(LinearLayout.HORIZONTAL);
        qrActions.setGravity(Gravity.CENTER);
        qrActions.addView(pill(ctx, Lang.t("画像を共有"), R.drawable.ic_pm_share, v -> exportQr(true)));
        View gap = new View(ctx);
        qrActions.addView(gap, new LinearLayout.LayoutParams(px(10), 1));
        qrActions.addView(pill(ctx, Lang.t("保存"), R.drawable.ic_lm_download, v -> exportQr(false)));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = px(12);
        qrColumn.addView(qrActions, alp);
        qrPanel.addView(qrColumn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(qrPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 共有の方法
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(px(12), px(12), px(12), 0);
        qrChip = tile(ctx, Lang.t("QR コード"), R.drawable.ic_tool_qr, v -> toggleQr());
        row.addView(qrChip, tileLp());
        row.addView(tile(ctx, Lang.t("リンクをコピー"), R.drawable.ic_omni_copy, v -> {
            copyLink();
            dialog.dismiss();
        }), tileLp());
        row.addView(tile(ctx, Lang.t("その他のアプリ"), R.drawable.ic_pm_more, v -> {
            dialog.dismiss();
            shareText();
        }), tileLp());
        root.addView(row);
        return root;
    }

    private LinearLayout.LayoutParams tileLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(px(4), 0, px(4), 0);
        return lp;
    }

    /** Pixel の共有シートのような、丸いアイコンの下に名前が付いたボタン。 */
    private View tile(Context ctx, String label, @DrawableRes int iconRes, View.OnClickListener l) {
        int container = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSecondaryContainer, Color.LTGRAY);
        int onContainer = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSecondaryContainer, Color.BLACK);
        int onSurface = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        LinearLayout v = new LinearLayout(ctx);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setPadding(0, px(8), 0, px(8));
        TypedValue tv = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        v.setBackgroundResource(tv.resourceId);
        FrameLayout circle = new FrameLayout(ctx);
        circle.setBackground(round(container, px(20)));
        ImageView i = new ImageView(ctx);
        i.setImageResource(iconRes);
        i.setImageTintList(ColorStateList.valueOf(onContainer));
        circle.addView(i, new FrameLayout.LayoutParams(px(24), px(24), Gravity.CENTER));
        v.addView(circle, new LinearLayout.LayoutParams(px(60), px(60)));
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setTextColor(onSurface);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(8);
        v.addView(t, lp);
        v.setOnClickListener(l);
        v.setContentDescription(label);
        return v;
    }

    private View pill(Context ctx, String label, @DrawableRes int iconRes, View.OnClickListener l) {
        int container = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurfaceContainerHighest, Color.LTGRAY);
        int onSurface = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        LinearLayout v = new LinearLayout(ctx);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(px(16), px(10), px(18), px(10));
        v.setBackground(round(container, px(20)));
        v.setForeground(ctx.getDrawable(resolve(ctx, android.R.attr.selectableItemBackground)));
        ImageView i = new ImageView(ctx);
        i.setImageResource(iconRes);
        i.setImageTintList(ColorStateList.valueOf(onSurface));
        v.addView(i, new LinearLayout.LayoutParams(px(18), px(18)));
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setTextColor(onSurface);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(px(8));
        v.addView(t, lp);
        v.setOnClickListener(l);
        v.setClipToOutline(true);
        return v;
    }

    private static int resolve(Context ctx, int attr) {
        TypedValue tv = new TypedValue();
        ctx.getTheme().resolveAttribute(attr, tv, true);
        return tv.resourceId;
    }

    private static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    // ------------------------------------------------------------------
    // QR コード
    // ------------------------------------------------------------------

    /** QR コードを広げる / 畳む。広げる時は高さを伸ばしながら、QR が少し拡大して浮かび上がる。 */
    private void toggleQr() {
        qrShown = !qrShown;
        qrChip.setSelected(qrShown);
        if (qrShown) {
            if (qrBitmap == null) {
                qrBitmap = renderQr(px(212) * 2);
                qrImage.setImageBitmap(qrBitmap);
            }
            qrPanel.setVisibility(View.VISIBLE);
            qrPanel.measure(View.MeasureSpec.makeMeasureSpec(((View) qrPanel.getParent()).getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int target = qrPanel.getMeasuredHeight();
            animateHeight(0, target, () -> setPanelHeight(ViewGroup.LayoutParams.WRAP_CONTENT));
            View card = (View) qrImage.getParent();
            card.setScaleX(0.6f);
            card.setScaleY(0.6f);
            card.setAlpha(0f);
            card.setRotation(-8f);
            card.animate().scaleX(1f).scaleY(1f).alpha(1f).rotation(0f)
                    .setDuration(450).setInterpolator(EMPHASIZED).start();
            qrActions.setAlpha(0f);
            qrActions.setTranslationY(px(12));
            qrActions.animate().alpha(1f).translationY(0f).setStartDelay(150)
                    .setDuration(300).setInterpolator(EMPHASIZED).start();
        } else {
            int from = qrPanel.getHeight();
            View card = (View) qrImage.getParent();
            card.animate().scaleX(0.8f).scaleY(0.8f).alpha(0f).setDuration(200).start();
            animateHeight(from, 0, () -> {
                qrPanel.setVisibility(View.GONE);
                setPanelHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            });
        }
    }

    private void animateHeight(int from, int to, Runnable end) {
        ValueAnimator va = ValueAnimator.ofInt(from, to);
        va.setDuration(380);
        va.setInterpolator(EMPHASIZED);
        va.addUpdateListener(a -> setPanelHeight((int) a.getAnimatedValue()));
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                end.run();
            }
        });
        setPanelHeight(from);
        va.start();
    }

    private void setPanelHeight(int h) {
        ViewGroup.LayoutParams lp = qrPanel.getLayoutParams();
        lp.height = h;
        qrPanel.setLayoutParams(lp);
    }

    @Nullable
    private Bitmap renderQr(int size) {
        QrRenderer.Style st = new QrRenderer.Style();
        st.dot = QrRenderer.DOT_ROUNDED;
        st.eye = QrRenderer.EYE_ROUNDED;
        st.fg = 0xFF1B1B1F;
        st.ec = ErrorCorrectionLevel.Q;
        st.margin = 1;
        try {
            Bitmap qr = QrRenderer.render(url, st, size);
            return favicon != null ? withCenterIcon(qr, favicon) : qr;
        } catch (Exception e) {
            return null;
        }
    }

    /** 真ん中にファビコンを載せる(誤り訂正 Q なので読み取れる)。 */
    private static Bitmap withCenterIcon(Bitmap qr, Bitmap icon) {
        Bitmap out = qr.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(out);
        float size = out.getWidth() * 0.2f;
        float cx = out.getWidth() / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(Color.WHITE);
        float bg = size * 0.62f;
        c.drawRoundRect(new RectF(cx - bg, cx - bg, cx + bg, cx + bg), bg * 0.45f, bg * 0.45f, p);
        float half = size / 2f;
        c.drawBitmap(icon, null, new RectF(cx - half, cx - half, cx + half, cx + half), p);
        return out;
    }

    /** QR コードの画像を共有する / 写真に保存する。 */
    private void exportQr(boolean share) {
        Context app = activity.getApplicationContext();
        worker.execute(() -> {
            Bitmap bmp = renderQr(QR_EXPORT_PX);
            Uri uri = null;
            if (bmp != null) {
                uri = share ? writeShareFile(app, bmp) : saveToPictures(app, bmp);
            }
            Uri result = uri;
            main.post(() -> {
                if (activity.isFinishing()) return;
                if (result == null) {
                    Toast.makeText(activity, share ? Lang.t("共有できませんでした") : Lang.t("保存に失敗しました"), Toast.LENGTH_SHORT).show();
                } else if (share) {
                    Intent send = new Intent(Intent.ACTION_SEND)
                            .setType("image/png")
                            .putExtra(Intent.EXTRA_STREAM, result)
                            .putExtra(Intent.EXTRA_TEXT, url)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    send.setClipData(ClipData.newRawUri("QR", result));
                    activity.startActivity(Intent.createChooser(send, Lang.t("QR コードを共有")));
                } else {
                    Toast.makeText(activity, Lang.t("QR コードを写真に保存しました"), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /** QR コードの画像をクリップボードにコピーする(画像を貼れないアプリ向けに URL も一緒に入れる)。 */
    private void copyQr() {
        Context app = activity.getApplicationContext();
        worker.execute(() -> {
            Bitmap bmp = renderQr(QR_EXPORT_PX);
            Uri uri = bmp != null ? writeShareFile(app, bmp, "page_qr_copy.png") : null;
            main.post(() -> {
                if (activity.isFinishing()) return;
                ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (uri == null || cm == null) {
                    Toast.makeText(activity, Lang.t("コピーできませんでした"), Toast.LENGTH_SHORT).show();
                    return;
                }
                ClipData clip = ClipData.newUri(app.getContentResolver(), Lang.t("QR コード"), uri);
                clip.addItem(new ClipData.Item(url));
                cm.setPrimaryClip(clip);
                if (android.os.Build.VERSION.SDK_INT < 33) {
                    Toast.makeText(activity, Lang.t("QR コードをコピーしました"), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Nullable
    private static Uri writeShareFile(Context ctx, Bitmap bmp) {
        return writeShareFile(ctx, bmp, "page_qr.png");
    }

    @Nullable
    private static Uri writeShareFile(Context ctx, Bitmap bmp, String name) {
        File dir = new File(ctx.getCacheDir(), "shared");
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        File f = new File(dir, name);
        try (OutputStream out = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            return null;
        }
        return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
    }

    @Nullable
    private static Uri saveToPictures(Context ctx, Bitmap bmp) {
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "AirSky_QR_" + System.currentTimeMillis() + ".png");
        v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AirSky");
        }
        Uri uri;
        try {
            uri = ctx.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return null;
            try (OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
                if (out == null) return null;
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        } catch (Exception e) {
            return null;
        }
        return uri;
    }

    // ------------------------------------------------------------------
    // その他の共有
    // ------------------------------------------------------------------

    private void copyLink() {
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("URL", url));
        if (android.os.Build.VERSION.SDK_INT < 33) {
            Toast.makeText(activity, Lang.t("リンクをコピーしました"), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareText() {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url);
        if (!TextUtils.isEmpty(title)) send.putExtra(Intent.EXTRA_SUBJECT, title);
        activity.startActivity(Intent.createChooser(send, Lang.t("共有")));
    }
}
