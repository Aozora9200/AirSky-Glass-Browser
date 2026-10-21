package com.airsky.browser;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 検索エンジンの選択。URL バーとスタートページの検索欄で使う検索エンジンが変わる。
 */
public class SearchEngineSettingsActivity extends PixelSettingsActivity {

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("検索エンジン");
    }

    @Override
    protected void onBuild() {
        String current = BrowserPrefs.engineKey(this);
        gap();
        for (String[] e : BrowserPrefs.ENGINES) {
            String key = e[0];
            radioRow(R.drawable.ic_ps_search, e[1], e[2], key.equals(current), () -> {
                BrowserPrefs.setEngineKey(this, key);
                rebuild();
            }).rawIcon(new LogoDrawable(logoText(key), Color.parseColor(e[5])));
        }

        // 自分で追加した検索エンジン(長押しで削除)
        header(Lang.t("追加した検索エンジン"));
        for (String[] e : BrowserPrefs.customEngines(this)) {
            String key = e[0];
            Row r = radioRow(R.drawable.ic_ps_search, e[1], e[2], key.equals(current), () -> {
                BrowserPrefs.setEngineKey(this, key);
                rebuild();
            });
            r.rawIcon(new LogoDrawable(initialOf(e[1]), Color.parseColor(e[5])));
            r.view.setOnLongClickListener(v -> {
                confirmRemove(e);
                return true;
            });
        }
        row(R.drawable.ic_si_add, Lang.t("検索エンジンを追加"),
                Lang.t("検索結果のページの URL を登録します"), this::showAddDialog);
        note(Lang.t("URL バーに言葉を入力して検索する時と、スタートページの検索欄で使われます。追加した検索エンジンは長押しで削除できます。"));
    }

    @NonNull
    private static String initialOf(@NonNull String name) {
        String t = name.trim();
        return t.isEmpty() ? "?" : t.substring(0, t.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
    }

    /** 名前と検索 URL(言葉の場所を %s にしたもの)を入れてもらう。 */
    private void showAddDialog() {
        float d = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = Math.round(24 * d);
        box.setPadding(pad, Math.round(8 * d), pad, 0);

        com.google.android.material.textfield.TextInputLayout nameLayout =
                new com.google.android.material.textfield.TextInputLayout(this);
        nameLayout.setHint(Lang.t("名前"));
        com.google.android.material.textfield.TextInputEditText name =
                new com.google.android.material.textfield.TextInputEditText(nameLayout.getContext());
        name.setSingleLine(true);
        nameLayout.addView(name);
        box.addView(nameLayout);

        com.google.android.material.textfield.TextInputLayout urlLayout =
                new com.google.android.material.textfield.TextInputLayout(this);
        urlLayout.setHint(Lang.t("検索 URL(言葉の場所を %s にする)"));
        urlLayout.setHelperText(Lang.t("例: https://www.example.com/search?q=%s"));
        com.google.android.material.textfield.TextInputEditText url =
                new com.google.android.material.textfield.TextInputEditText(urlLayout.getContext());
        url.setSingleLine(true);
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        url.setText("https://");
        urlLayout.addView(url);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(12 * d);
        box.addView(urlLayout, lp);

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("検索エンジンを追加"))
                .setView(box)
                .setPositiveButton(Lang.t("追加"), null)
                .setNegativeButton(Lang.t("キャンセル"), null)
                .create();
        dialog.setOnShowListener(di -> dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String n = name.getText() != null ? name.getText().toString().trim() : "";
                    String u = url.getText() != null ? url.getText().toString().trim() : "";
                    nameLayout.setError(n.isEmpty() ? Lang.t("名前を入力してください") : null);
                    urlLayout.setError(BrowserPrefs.isValidEngineUrl(u) ? null
                            : Lang.t("http:// か https:// で始まり、%s を含む URL を入力してください"));
                    if (n.isEmpty() || !BrowserPrefs.isValidEngineUrl(u)) return;
                    String key = BrowserPrefs.addCustomEngine(this, n, u);
                    BrowserPrefs.setEngineKey(this, key);
                    dialog.dismiss();
                    rebuild();
                }));
        dialog.show();
    }

    private void confirmRemove(@NonNull String[] e) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("「") + e[1] + Lang.t("」を削除しますか?"))
                .setMessage(e[3])
                .setPositiveButton(Lang.t("削除"), (d, w) -> {
                    BrowserPrefs.removeCustomEngine(this, e[0]);
                    rebuild();
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    @NonNull
    private static String logoText(String key) {
        switch (key) {
            case "yahoo": return "Y!";
            case "bing": return "b";
            case "duckduckgo": return "D";
            case "ecosia": return "E";
            default: return "G";
        }
    }

    /** 検索エンジンの丸いロゴ(ブランドの色の丸に頭文字)。 */
    static final class LogoDrawable extends Drawable {
        private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String label;

        LogoDrawable(String label, int color) {
            this.label = label;
            circle.setColor(color);
            text.setColor(Color.WHITE);
            text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            text.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            float r = Math.min(b.width(), b.height()) / 2f;
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r, circle);
            text.setTextSize(r * (label.length() > 1 ? 0.95f : 1.15f));
            float y = b.exactCenterY() - (text.descent() + text.ascent()) / 2f;
            canvas.drawText(label, b.exactCenterX(), y, text);
        }

        @Override public void setAlpha(int alpha) { circle.setAlpha(alpha); text.setAlpha(alpha); }
        @Override public void setColorFilter(@Nullable ColorFilter cf) { circle.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
