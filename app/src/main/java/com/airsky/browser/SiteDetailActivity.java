package com.airsky.browser;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.mozilla.geckoview.StorageController;

/**
 * 1 つのサイトの権限(サイトごとの例外)。各権限を「既定の動作 / 許可 / ブロック」から選べる。
 */
public class SiteDetailActivity extends PixelSettingsActivity {

    static final String EXTRA_HOST = "host";

    private String host;

    public static void open(@NonNull Context c, @NonNull String host) {
        c.startActivity(new Intent(c, SiteDetailActivity.class).putExtra(EXTRA_HOST, host));
    }

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        host = getIntent().getStringExtra(EXTRA_HOST);
        if (host == null) host = "";
        return host;
    }

    @Override
    protected void onBuild() {
        header(Lang.t("権限"));
        for (int type : SitePermissions.ALL) {
            int site = SitePermissions.getSite(this, host, type);
            int def = SitePermissions.getDefault(this, type);
            String summary = site == SitePermissions.UNSET
                    ? Lang.t("既定の動作(") + SitePermissions.defaultSummary(type, def) + ")"
                    : SitePermissions.valueLabel(type, site);
            Row r = row(SitePermissions.icon(type), SitePermissions.label(type), summary, () -> edit(type));
            if (site != SitePermissions.UNSET) r.value(Lang.t("変更済み"));
        }

        gap();
        row(R.drawable.ic_ps_history, Lang.t("権限をリセット"), Lang.t("このサイトの権限をすべて既定の動作に戻します"),
                () -> confirm(Lang.t("権限をリセット"), host + Lang.t(" の権限をリセットしますか?"), Lang.t("リセット"), () -> {
                    SitePermissions.clearSite(this, host);
                    rebuild();
                }));
        row(R.drawable.ic_ps_delete, Lang.t("データを削除"), Lang.t("このサイトの Cookie・保存データ・キャッシュを削除します"),
                () -> confirm(Lang.t("データを削除"), host + Lang.t(" のデータを削除しますか?\nこのサイトからログアウトされる場合があります。"),
                        Lang.t("削除"), () -> {
                            GeckoEngine.get(this).getRuntime().getStorageController()
                                    .clearDataFromBaseDomain(host, StorageController.ClearFlags.ALL);
                            Toast.makeText(this, Lang.t("データを削除しました"), Toast.LENGTH_SHORT).show();
                        }));
    }

    private void edit(int type) {
        int[] options = SitePermissions.siteOptions(type);
        String[] labels = new String[options.length + 1];
        labels[0] = Lang.t("既定の動作を使用(") + SitePermissions.defaultSummary(type, SitePermissions.getDefault(this, type)) + ")";
        int checked = 0;
        int site = SitePermissions.getSite(this, host, type);
        for (int i = 0; i < options.length; i++) {
            labels[i + 1] = SitePermissions.valueLabel(type, options[i]);
            if (options[i] == site) checked = i + 1;
        }
        choose(SitePermissions.label(type), labels, checked, i ->
                SitePermissions.setSite(this, host, type, i == 0 ? SitePermissions.UNSET : options[i - 1]));
    }
}
