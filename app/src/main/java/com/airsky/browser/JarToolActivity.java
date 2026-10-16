package com.airsky.browser;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * JAR 実行ツール(Pixel の設定風)。JAR を追加し、押すと実行する。
 * 長押し(または右の ︙)で、引数を付けて実行・ツールのショートカット・名前の変更・削除ができる。
 */
public class JarToolActivity extends PixelSettingsActivity {

    /** この JAR をすぐ実行する(実行画面の「もう一度実行」から)。 */
    static final String EXTRA_RUN = "run_jar";
    static final String EXTRA_RUN_ARGS = "run_jar_args";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean importing;

    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onPicked);

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("Java 実行");
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) handleRun(getIntent());
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        handleRun(intent);
    }

    private void handleRun(android.content.Intent intent) {
        String id = intent.getStringExtra(EXTRA_RUN);
        if (id == null) return;
        JarStore.Entry e = JarStore.find(this, id);
        String args = intent.getStringExtra(EXTRA_RUN_ARGS);
        if (e != null) JarStore.launch(this, e, args != null ? args : "");
    }

    @Override
    protected void onBuild() {
        note(Lang.t("JAR ファイル(Java のプログラム)を、この端末の上で実行します。"
                + "コンソールに文字を出すプログラムと、Swing / AWT の画面を持つプログラムの多くに対応しています。\n"
                + "Java 実行は実験的な機能です。動かないプログラムや、今後動きが変わることがあります。"));

        header(Lang.t("JAR ファイル"));
        row(R.drawable.ic_si_add, Lang.t("JAR ファイルを追加"), importing ? Lang.t("読み込んでいます…") : Lang.t("端末のファイルから .jar を選びます"),
                importing ? null : this::pick)
                .tone(R.color.ps_tone_blue_bg, R.color.ps_tone_blue_fg);

        List<JarStore.Entry> list = JarStore.list(this);
        if (!list.isEmpty()) {
            header(Lang.t("追加した JAR"));
            for (JarStore.Entry e : list) {
                StringBuilder sum = new StringBuilder(Formatter.formatShortFileSize(this, e.size));
                if (e.shortcut) sum.append(Lang.t(" · ツールに表示中"));
                if (e.args != null && !e.args.isEmpty()) sum.append(Lang.t(" · 引数: ")).append(e.args);
                Row r = row(R.drawable.ic_tool_java, e.name, sum, () -> JarStore.launch(this, e, null))
                        .tone(R.color.ps_tone_teal_bg, R.color.ps_tone_teal_fg);
                r.view.setOnLongClickListener(v -> {
                    options(e);
                    return true;
                });
                ImageButton more = new ImageButton(this);
                more.setImageResource(R.drawable.ic_jr_more);
                android.util.TypedValue tv = new android.util.TypedValue();
                getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
                more.setBackgroundResource(tv.resourceId);
                more.setContentDescription(Lang.t("その他"));
                more.setOnClickListener(v -> options(e));
                more.setMinimumWidth(dp(44));
                more.setMinimumHeight(dp(44));
                r.setEnd(more);
            }
            note(Lang.t("長押しすると、ツールやホーム画面へのショートカットの追加、削除ができます。"
                    + "ツールに追加した JAR は、ツールの一覧で長押しするとショートカットを削除できます。"));
        }

        header(Lang.t("できること・できないこと"));
        note(Lang.t("・System.in / System.out を使うコンソールのプログラム\n"
                + "・Swing / AWT の画面(ボタン・文字欄・一覧・メニュー・描画・タイマーなど)\n"
                + "・画面のタッチはマウス、キーのボタンは キーボードとして届きます\n"
                + "・相対パスで開くファイルは、JAR ごとの作業フォルダに置かれます\n\n"
                + "JavaFX、音声、ネイティブライブラリ(JNI)、一部の Swing 部品(JTable など)には対応していません。"
                + "初めて実行する時は Android 用への変換に少し時間がかかります。"));
    }

    private void pick() {
        try {
            picker.launch(new String[]{"application/java-archive", "application/x-java-archive",
                    "application/zip", "application/octet-stream"});
        } catch (RuntimeException e) {
            Toast.makeText(this, Lang.t("ファイルを選べません"), Toast.LENGTH_SHORT).show();
        }
    }

    private void onPicked(@Nullable Uri uri) {
        if (uri == null) return;
        importing = true;
        rebuild();
        new Thread(() -> {
            JarStore.Entry e = null;
            String error = null;
            try {
                e = JarStore.add(getApplicationContext(), uri);
            } catch (Exception ex) {
                error = ex.getMessage();
            }
            JarStore.Entry added = e;
            String err = error;
            ui.post(() -> {
                importing = false;
                if (isFinishing()) return;
                rebuild();
                if (added == null) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(Lang.t("追加できませんでした"))
                            .setMessage(err != null ? err : Lang.t("JAR ファイルを読めませんでした。"))
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
                new MaterialAlertDialogBuilder(this)
                        .setTitle(Lang.t("「") + added.name + Lang.t("」を追加しました"))
                        .setMessage(Lang.t("ツールに追加すると、メニューのツール一覧からすぐに実行できます。"))
                        .setPositiveButton(Lang.t("実行"), (d, w) -> JarStore.launch(this, added, null))
                        .setNeutralButton(Lang.t("ツールに追加"), (d, w) -> {
                            JarStore.setShortcut(this, added.id, true);
                            Toast.makeText(this, Lang.t("ツールに追加しました"), Toast.LENGTH_SHORT).show();
                            rebuild();
                        })
                        .setNegativeButton(Lang.t("閉じる"), null)
                        .show();
            });
        }, "jar-import").start();
    }

    private void options(JarStore.Entry e) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(Lang.t("実行"));
        actions.add(() -> JarStore.launch(this, e, null));
        labels.add(Lang.t("引数を指定して実行"));
        actions.add(() -> askArgs(e));
        if (e.shortcut) {
            labels.add(Lang.t("ツールのショートカットを削除"));
            actions.add(() -> {
                JarStore.setShortcut(this, e.id, false);
                Toast.makeText(this, Lang.t("ショートカットを削除しました"), Toast.LENGTH_SHORT).show();
                rebuild();
            });
        } else {
            labels.add(Lang.t("ツールにショートカットを追加"));
            actions.add(() -> {
                JarStore.setShortcut(this, e.id, true);
                Toast.makeText(this, Lang.t("ツールに追加しました"), Toast.LENGTH_SHORT).show();
                rebuild();
            });
        }
        labels.add(Lang.t("ホーム画面に追加"));
        actions.add(() -> ProgramShortcuts.pin(this, ProgramShortcuts.KIND_JAVA, e.id, e.name));
        labels.add(Lang.t("名前を変更"));
        actions.add(() -> rename(e));
        labels.add(Lang.t("削除"));
        actions.add(() -> confirm(Lang.t("「") + e.name + Lang.t("」を削除しますか?"),
                Lang.t("JAR と、変換したデータが削除されます。プログラムが作業フォルダに保存したファイルは残ります。"),
                Lang.t("削除"), () -> {
                    JarStore.remove(this, e.id);
                    rebuild();
                }));
        new MaterialAlertDialogBuilder(this)
                .setTitle(e.name)
                .setItems(labels.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .show();
    }

    private EditText field(String text, String hint) {
        EditText f = new EditText(this);
        f.setSingleLine(true);
        f.setText(text);
        f.setHint(hint);
        f.setSelectAllOnFocus(true);
        return f;
    }

    private FrameLayout box(EditText f) {
        FrameLayout b = new FrameLayout(this);
        b.setPadding(dp(20), dp(8), dp(20), 0);
        b.addView(f);
        return b;
    }

    private void askArgs(JarStore.Entry e) {
        EditText f = field(e.args, Lang.t("例: input.txt 10 \"two words\""));
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("コマンドライン引数"))
                .setMessage(Lang.t("main(String[] args) に渡す引数を、空白で区切って入力します。次回からも使われます。"))
                .setView(box(f))
                .setPositiveButton(Lang.t("実行"), (d, w) -> {
                    e.args = f.getText().toString().trim();
                    JarStore.update(this, e);
                    JarStore.launch(this, e, e.args);
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
        f.requestFocus();
    }

    private void rename(JarStore.Entry e) {
        EditText f = field(e.name, Lang.t("名前"));
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("名前を変更"))
                .setView(box(f))
                .setPositiveButton("OK", (d, w) -> {
                    String n = f.getText().toString().trim();
                    if (n.isEmpty()) return;
                    e.name = n;
                    JarStore.update(this, e);
                    rebuild();
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
        f.requestFocus();
    }
}
