package com.airsky.browser;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.format.Formatter;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Python 実行ツール(Pixel の設定風)。Python のプログラムを追加し、押すと実行する。
 * 長押し(または右の ︙)で、引数を付けて実行・ツール / ホーム画面へのショートカット・.env の編集・
 * パッケージの追加・作業フォルダの書き出し・名前の変更・削除ができる。
 */
public class PyToolActivity extends PixelSettingsActivity {

    static final String EXTRA_RUN = "run_py";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean importing;
    @Nullable private String installStatus;
    private final AtomicBoolean installCancel = new AtomicBoolean();
    @Nullable private PyStore.Entry pendingTarget;

    private final ActivityResultLauncher<String[]> filePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) runImport(() -> PyStore.addFile(getApplicationContext(), uri));
            });
    private final ActivityResultLauncher<Uri> folderPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri != null) runImport(() -> PyStore.addFolder(getApplicationContext(), uri));
            });
    private final ActivityResultLauncher<String[]> dataPicker = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(), this::onDataPicked);
    private final ActivityResultLauncher<String> exporter = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"), this::onExport);

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        return Lang.t("Python 実行");
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        installCancel.set(true);
    }

    private void handleRun(android.content.Intent intent) {
        String id = intent.getStringExtra(EXTRA_RUN);
        if (id == null) return;
        PyStore.Entry e = PyStore.find(this, id);
        if (e != null) PyStore.launch(this, e, null);
    }

    @Override
    protected void onBuild() {
        note(Lang.t("Python のプログラムを、この端末の上で実行します。文字を出すプログラムのほか、"
                + "tkinter・wxPython・Flet の画面を持つプログラムの多くは、Android の画面として表示されます。\n"
                + "Python 実行は実験的な機能です。動かないプログラムや、今後動きが変わることがあります。"));

        header(Lang.t("Python 本体"));
        boolean supported = PythonRuntime.supported(this);
        if (!supported) {
            row(R.drawable.ic_si_warning, Lang.t("この端末では使えません"), Lang.t("Python は 64bit の端末(arm64 / x86_64)にだけ対応しています"), null)
                    .tone(R.color.ps_tone_red_bg, R.color.ps_tone_red_fg);
        } else if (installStatus != null) {
            row(R.drawable.ic_tool_python, Lang.t("Python を準備しています"), installStatus, this::confirmCancelInstall)
                    .tone(R.color.ps_tone_blue_bg, R.color.ps_tone_blue_fg);
        } else if (PythonRuntime.installed(this)) {
            row(R.drawable.ic_tool_python, "Python " + PythonRuntime.VERSION,
                    Lang.t("インストール済み · ") + Formatter.formatShortFileSize(this, PythonRuntime.installedSize(this)),
                    this::runtimeOptions)
                    .tone(R.color.ps_tone_blue_bg, R.color.ps_tone_blue_fg);
            row(R.drawable.ic_tool_terminal, Lang.t("Python シェル"), Lang.t("1 行ずつコードを入力して試せます(対話モード)"),
                    () -> PyStore.launchRepl(this))
                    .tone(R.color.ps_tone_gray_bg, R.color.ps_tone_gray_fg).chevron();
        } else {
            row(R.drawable.ic_msg_download, Lang.t("Python をダウンロード"),
                    Lang.t("python.org の Android 版 Python ") + PythonRuntime.VERSION + Lang.t("(約 23MB)"), this::startInstall)
                    .tone(R.color.ps_tone_blue_bg, R.color.ps_tone_blue_fg);
        }

        header(Lang.t("プログラムを追加"));
        row(R.drawable.ic_si_add, Lang.t("Python ファイルを追加"), importing ? Lang.t("読み込んでいます…") : Lang.t(".py ファイル、またはプロジェクトの .zip"),
                importing ? null : this::pickFile)
                .tone(R.color.ps_tone_green_bg, R.color.ps_tone_green_fg);
        row(R.drawable.ic_tool_folder, Lang.t("フォルダごと追加"), Lang.t("データベースや .env、設定ファイルも一緒に写します"),
                importing ? null : this::pickFolder)
                .tone(R.color.ps_tone_orange_bg, R.color.ps_tone_orange_fg);

        List<PyStore.Entry> list = PyStore.list(this);
        if (!list.isEmpty()) {
            header(Lang.t("追加したプログラム"));
            for (PyStore.Entry e : list) {
                StringBuilder sum = new StringBuilder(e.main).append(" · ")
                        .append(Formatter.formatShortFileSize(this, e.size));
                if (e.shortcut) sum.append(Lang.t(" · ツールに表示中"));
                if (e.args != null && !e.args.isEmpty()) sum.append(Lang.t(" · 引数: ")).append(e.args);
                Row r = row(R.drawable.ic_tool_python, e.name, sum, () -> run(e, null))
                        .tone(R.color.ps_tone_blue_bg, R.color.ps_tone_blue_fg);
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
            note(Lang.t("長押しすると、ツールやホーム画面へのショートカット、.env の編集、パッケージの追加などができます。"
                    + "ツールに追加したプログラムは、ツールの一覧で長押しするとショートカットを削除できます。"));
        }

        header(Lang.t("できること・できないこと"));
        note(Lang.t("・input() / print() を使うコンソールのプログラム\n"
                + "・tkinter(ttk・Canvas・メニュー・ダイアログを含む)、wxPython、Flet の画面\n"
                + "・SQLite のデータベース、.env(環境変数)、ini / json / toml などの設定ファイル\n"
                + "  (プログラムのフォルダが作業フォルダになり、相対パスのファイルはここに作られます)\n"
                + "・requirements.txt や pip でのパッケージの追加(Python だけで書かれたもの)\n"
                + "・venv フォルダの中の site-packages も読み込みます\n\n"
                + "C などで書かれた部分を含むパッケージ(numpy・Pillow など)や、PyQt・pygame など"
                + "ほかの GUI には対応していません。"));
    }

    // ------------------------------------------------------------------
    // Python 本体
    // ------------------------------------------------------------------

    private void startInstall() {
        installCancel.set(false);
        installStatus = Lang.t("ダウンロードを始めています…");
        rebuild();
        new Thread(() -> {
            String error = null;
            try {
                PythonRuntime.install(getApplicationContext(), (msg, pct) -> ui.post(() -> {
                    installStatus = pct >= 0 ? msg + " (" + pct + "%)" : msg;
                    if (!isFinishing()) rebuild();
                }), installCancel);
            } catch (IOException e) {
                error = e.getMessage();
            }
            String err = error;
            ui.post(() -> {
                installStatus = null;
                if (isFinishing() || isDestroyed()) return;
                rebuild();
                if (err != null && !installCancel.get()) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(Lang.t("ダウンロードできませんでした"))
                            .setMessage(err)
                            .setPositiveButton("OK", null)
                            .show();
                } else if (err == null) {
                    Toast.makeText(this, Lang.t("Python を入れました"), Toast.LENGTH_SHORT).show();
                }
            });
        }, "py-install").start();
    }

    private void confirmCancelInstall() {
        confirm(Lang.t("ダウンロードを中止しますか?"), "", Lang.t("中止"), () -> installCancel.set(true));
    }

    private void runtimeOptions() {
        String[] labels = {Lang.t("Python シェルを開く"), Lang.t("入れ直す"), Lang.t("Python 本体を削除")};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Python " + PythonRuntime.VERSION)
                .setItems(labels, (d, w) -> {
                    if (w == 0) PyStore.launchRepl(this);
                    else if (w == 1) startInstall();
                    else confirm(Lang.t("Python 本体を削除しますか?"),
                                Lang.t("追加したプログラムと、そのデータ(作業フォルダ)は残ります。もう一度使う時はダウンロードし直します。"),
                                Lang.t("削除"), () -> {
                                    PythonRuntime.uninstall(this);
                                    rebuild();
                                });
                })
                .show();
    }

    private void run(PyStore.Entry e, @Nullable String args) {
        if (!PythonRuntime.supported(this)) {
            Toast.makeText(this, Lang.t("この端末では Python を使えません"), Toast.LENGTH_SHORT).show();
            return;
        }
        PyStore.launch(this, e, args);
    }

    // ------------------------------------------------------------------
    // 追加
    // ------------------------------------------------------------------

    private void pickFile() {
        try {
            filePicker.launch(new String[]{"text/x-python", "text/x-script.python", "application/x-python-code",
                    "application/zip", "text/plain", "application/octet-stream", "*/*"});
        } catch (RuntimeException e) {
            Toast.makeText(this, Lang.t("ファイルを選べません"), Toast.LENGTH_SHORT).show();
        }
    }

    private void pickFolder() {
        try {
            folderPicker.launch(null);
        } catch (RuntimeException e) {
            Toast.makeText(this, Lang.t("フォルダを選べません"), Toast.LENGTH_SHORT).show();
        }
    }

    private interface Importer {
        PyStore.Entry run() throws IOException;
    }

    private void runImport(Importer importer) {
        importing = true;
        rebuild();
        new Thread(() -> {
            PyStore.Entry e = null;
            String error = null;
            try {
                e = importer.run();
            } catch (Exception ex) {
                error = ex.getMessage();
            }
            PyStore.Entry added = e;
            String err = error;
            ui.post(() -> {
                importing = false;
                if (isFinishing()) return;
                rebuild();
                if (added == null) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(Lang.t("追加できませんでした"))
                            .setMessage(err != null ? err : Lang.t("ファイルを読めませんでした。"))
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
                boolean req = new File(PyStore.dir(this, added.id), "requirements.txt").isFile();
                MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this)
                        .setTitle(Lang.t("「") + added.name + Lang.t("」を追加しました"))
                        .setMessage(Lang.t("実行するファイル: ") + added.main
                                + (req ? Lang.t("\n\nrequirements.txt があります。必要なパッケージを先に入れておくと動きやすくなります。") : "")
                                + Lang.t("\n\nツールに追加すると、メニューのツール一覧からすぐに実行できます。"))
                        .setPositiveButton(Lang.t("実行"), (d, w) -> run(added, null))
                        .setNeutralButton(Lang.t("ツールに追加"), (d, w) -> {
                            PyStore.setShortcut(this, added.id, true);
                            Toast.makeText(this, Lang.t("ツールに追加しました"), Toast.LENGTH_SHORT).show();
                            rebuild();
                        })
                        .setNegativeButton(Lang.t("閉じる"), null);
                if (req) b.setNegativeButton(Lang.t("パッケージを入れる"), (d, w) -> PyStore.launchPip(this, added, "-r requirements.txt"));
                b.show();
            });
        }, "py-import").start();
    }

    private void onDataPicked(List<Uri> uris) {
        PyStore.Entry e = pendingTarget;
        pendingTarget = null;
        if (e == null || uris == null || uris.isEmpty()) return;
        new Thread(() -> {
            int ok = 0;
            for (Uri u : uris) {
                try {
                    PyStore.importInto(getApplicationContext(), PyStore.dir(this, e.id), u);
                    ok++;
                } catch (IOException ignored) {
                }
            }
            int n = ok;
            ui.post(() -> Toast.makeText(this, n + Lang.t(" 個のファイルを作業フォルダに追加しました"), Toast.LENGTH_SHORT).show());
        }, "py-data").start();
    }

    private void onExport(@Nullable Uri uri) {
        PyStore.Entry e = pendingTarget;
        pendingTarget = null;
        if (e == null || uri == null) return;
        new Thread(() -> {
            String err = null;
            try {
                PyStore.exportZip(getApplicationContext(), PyStore.dir(this, e.id), uri);
            } catch (IOException ex) {
                err = ex.getMessage();
            }
            String error = err;
            ui.post(() -> Toast.makeText(this, error == null ? Lang.t("書き出しました") : Lang.t("書き出せませんでした: ") + error,
                    Toast.LENGTH_SHORT).show());
        }, "py-export").start();
    }

    // ------------------------------------------------------------------
    // 1 つのプログラムの操作
    // ------------------------------------------------------------------

    private void options(PyStore.Entry e) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        File dir = PyStore.dir(this, e.id);
        labels.add(Lang.t("実行"));
        actions.add(() -> run(e, null));
        labels.add(Lang.t("引数を指定して実行"));
        actions.add(() -> askArgs(e));
        List<String> scripts = PyStore.listPy(dir, "", 0);
        if (scripts.size() > 1) {
            labels.add(Lang.t("実行するファイルを変更(") + e.main + ")");
            actions.add(() -> chooseMain(e, scripts));
        }
        if (e.shortcut) {
            labels.add(Lang.t("ツールのショートカットを削除"));
            actions.add(() -> {
                PyStore.setShortcut(this, e.id, false);
                Toast.makeText(this, Lang.t("ショートカットを削除しました"), Toast.LENGTH_SHORT).show();
                rebuild();
            });
        } else {
            labels.add(Lang.t("ツールにショートカットを追加"));
            actions.add(() -> {
                PyStore.setShortcut(this, e.id, true);
                Toast.makeText(this, Lang.t("ツールに追加しました"), Toast.LENGTH_SHORT).show();
                rebuild();
            });
        }
        labels.add(Lang.t("ホーム画面に追加"));
        actions.add(() -> ProgramShortcuts.pin(this, ProgramShortcuts.KIND_PYTHON, e.id, e.name));
        labels.add(Lang.t(".env(環境変数)を編集"));
        actions.add(() -> editEnv(e));
        labels.add(Lang.t("作業フォルダにファイルを追加"));
        actions.add(() -> {
            pendingTarget = e;
            try {
                dataPicker.launch(new String[]{"*/*"});
            } catch (RuntimeException ex) {
                pendingTarget = null;
            }
        });
        if (new File(dir, "requirements.txt").isFile()) {
            labels.add(Lang.t("requirements.txt のパッケージを入れる"));
            actions.add(() -> PyStore.launchPip(this, e, "-r requirements.txt"));
        }
        labels.add(Lang.t("パッケージを追加(pip)"));
        actions.add(() -> askPackages(e));
        labels.add(Lang.t("作業フォルダを書き出す(.zip)"));
        actions.add(() -> {
            pendingTarget = e;
            try {
                exporter.launch(PyStore.safeName(e.name) + ".zip");
            } catch (RuntimeException ex) {
                pendingTarget = null;
            }
        });
        labels.add(Lang.t("名前を変更"));
        actions.add(() -> rename(e));
        labels.add(Lang.t("削除"));
        actions.add(() -> confirm(Lang.t("「") + e.name + Lang.t("」を削除しますか?"),
                Lang.t("プログラムと、作業フォルダのデータ(データベース・設定ファイルなど)がすべて削除されます。"),
                Lang.t("削除"), () -> {
                    PyStore.remove(this, e.id);
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

    private void askArgs(PyStore.Entry e) {
        EditText f = field(e.args, Lang.t("例: input.txt 10 \"two words\""));
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("コマンドライン引数"))
                .setMessage(Lang.t("sys.argv に渡す引数を、空白で区切って入力します。次回からも使われます。"))
                .setView(box(f))
                .setPositiveButton(Lang.t("実行"), (d, w) -> {
                    e.args = f.getText().toString().trim();
                    PyStore.update(this, e);
                    run(e, e.args);
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
        f.requestFocus();
    }

    private void askPackages(PyStore.Entry e) {
        EditText f = field("", Lang.t("例: requests python-dotenv"));
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("パッケージを追加"))
                .setMessage(Lang.t("PyPI のパッケージ名を空白で区切って入力します。このプログラムだけで使えます。"))
                .setView(box(f))
                .setPositiveButton(Lang.t("入れる"), (d, w) -> {
                    String s = f.getText().toString().trim();
                    if (!s.isEmpty()) PyStore.launchPip(this, e, s);
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
        f.requestFocus();
    }

    private void chooseMain(PyStore.Entry e, List<String> scripts) {
        int checked = scripts.indexOf(e.main);
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("実行するファイル"))
                .setSingleChoiceItems(scripts.toArray(new String[0]), checked, (d, w) -> {
                    e.main = scripts.get(w);
                    PyStore.update(this, e);
                    d.dismiss();
                    rebuild();
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    private void editEnv(PyStore.Entry e) {
        File env = new File(PyStore.dir(this, e.id), ".env");
        EditText f = new EditText(this);
        f.setText(PythonRuntime.readText(env));
        f.setHint("API_KEY=xxxx\nDEBUG=1");
        f.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        f.setMinLines(5);
        f.setMaxLines(12);
        f.setGravity(Gravity.TOP | Gravity.START);
        f.setTypeface(android.graphics.Typeface.MONOSPACE);
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t(".env(環境変数)"))
                .setMessage(Lang.t("1 行に 1 つ「名前=値」で書きます。実行する時に環境変数として読み込まれます。"))
                .setView(box(f))
                .setPositiveButton(Lang.t("保存"), (d, w) -> {
                    try {
                        PythonRuntime.writeText(env, f.getText().toString());
                        Toast.makeText(this, Lang.t("保存しました"), Toast.LENGTH_SHORT).show();
                    } catch (IOException ex) {
                        Toast.makeText(this, Lang.t("保存できませんでした"), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    private void rename(PyStore.Entry e) {
        EditText f = field(e.name, Lang.t("名前"));
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("名前を変更"))
                .setView(box(f))
                .setPositiveButton("OK", (d, w) -> {
                    String n = f.getText().toString().trim();
                    if (n.isEmpty()) return;
                    e.name = n;
                    PyStore.update(this, e);
                    rebuild();
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
        f.requestFocus();
    }
}
