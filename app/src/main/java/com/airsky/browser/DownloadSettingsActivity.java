package com.airsky.browser;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * ダウンロード設定。保存先・ダウンロード前の確認・同じ名前のファイルの確認・ウイルススキャン・履歴の削除。
 * 履歴を消した時は RESULT_OK を返す(ダウンロード一覧を読み直してもらう)。
 */
public class DownloadSettingsActivity extends AppCompatActivity {

    private static final String HISTORY_PREFS = "AdvancedBrowserPrefs";
    private static final String KEY_DOWNLOAD_HISTORY = "download_history";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_settings);
        TouchEffectView.attach(getWindow());
        prefs = getSharedPreferences(AppDownloader.PREFS, MODE_PRIVATE);

        findViewById(R.id.dsBack).setOnClickListener(v -> finish());

        updateFolderSummary();
        findViewById(R.id.dsFolder).setOnClickListener(v -> chooseFolder());

        MaterialSwitch ask = findViewById(R.id.dsAskSwitch);
        ask.setChecked(prefs.getBoolean(AppDownloader.KEY_ASK_BEFORE, false));
        findViewById(R.id.dsAsk).setOnClickListener(v -> {
            ask.toggle();
            prefs.edit().putBoolean(AppDownloader.KEY_ASK_BEFORE, ask.isChecked()).apply();
        });

        MaterialSwitch duplicate = findViewById(R.id.dsDuplicateSwitch);
        duplicate.setChecked(!prefs.getBoolean(AppDownloader.KEY_SKIP_DUPLICATE_DIALOG, false));
        findViewById(R.id.dsDuplicate).setOnClickListener(v -> {
            duplicate.toggle();
            prefs.edit().putBoolean(AppDownloader.KEY_SKIP_DUPLICATE_DIALOG, !duplicate.isChecked()).apply();
        });

        MaterialSwitch scan = findViewById(R.id.dsScanSwitch);
        scan.setChecked(VirusScanner.isEnabled(this));
        findViewById(R.id.dsScan).setOnClickListener(v -> {
            scan.toggle();
            VirusScanner.setEnabled(this, scan.isChecked());
        });

        findViewById(R.id.dsClear).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("ダウンロード履歴を削除しますか？"))
                .setMessage(Lang.t("ダウンロードしたファイルは削除されません"))
                .setNegativeButton(Lang.t("キャンセル"), null)
                .setPositiveButton(Lang.t("削除"), (d, w) -> {
                    getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE).edit().remove(KEY_DOWNLOAD_HISTORY).apply();
                    setResult(RESULT_OK);
                    Toast.makeText(this, Lang.t("ダウンロード履歴を削除しました"), Toast.LENGTH_SHORT).show();
                })
                .show());
    }

    private void chooseFolder() {
        String[] dirs = AppDownloader.defaultDirChoices();
        String current = AppDownloader.defaultDir(this);
        String[] labels = new String[dirs.length];
        int checked = 0;
        for (int i = 0; i < dirs.length; i++) {
            labels[i] = AppDownloader.folderLabel(dirs[i]);
            if (dirs[i].equals(current)) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("ダウンロードフォルダ"))
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    prefs.edit().putString(AppDownloader.KEY_DEFAULT_DIR, dirs[which]).apply();
                    updateFolderSummary();
                    d.dismiss();
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    private void updateFolderSummary() {
        ((TextView) findViewById(R.id.dsFolderSummary)).setText(AppDownloader.folderLabel(AppDownloader.defaultDir(this)));
    }
}
