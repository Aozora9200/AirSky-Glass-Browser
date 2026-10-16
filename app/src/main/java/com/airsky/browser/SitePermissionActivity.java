package com.airsky.browser;

import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * 権限 1 種類の設定(既定値と、許可/ブロックしたサイトの一覧)。
 * 種類に -1 を渡すと「すべてのサイト」(例外を設定したサイトの一覧)になる。
 */
public class SitePermissionActivity extends PixelSettingsActivity {

    static final String EXTRA_TYPE = "type";

    private int type;

    public static void open(@NonNull Context c, int type) {
        c.startActivity(new Intent(c, SitePermissionActivity.class).putExtra(EXTRA_TYPE, type));
    }

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        type = getIntent().getIntExtra(EXTRA_TYPE, -1);
        return type < 0 ? Lang.t("すべてのサイト") : SitePermissions.label(type);
    }

    @Override
    protected void onBuild() {
        if (type < 0) {
            buildAllSites();
            return;
        }
        note(SitePermissions.description(type));

        header(Lang.t("既定の動作"));
        int current = SitePermissions.getDefault(this, type);
        for (int v : SitePermissions.defaultOptions(type)) {
            radioRow(0, optionTitle(v), optionSummary(v), v == current, () -> {
                SitePermissions.setDefault(this, type, v);
                rebuild();
            });
        }

        List<String> hosts = SitePermissions.hostsWith(this, type);
        if (type == SitePermissions.AUTOPLAY) {
            if (!hosts.isEmpty()) {
                header(Lang.t("カスタマイズした動作"));
                for (String h : hosts) siteRow(h);
            }
        } else {
            boolean anyAllowed = false;
            for (String h : hosts) {
                if (SitePermissions.getSite(this, h, type) != SitePermissions.ALLOW) continue;
                if (!anyAllowed) header(Lang.t("許可したサイト"));
                anyAllowed = true;
                siteRow(h);
            }
            boolean anyBlocked = false;
            for (String h : hosts) {
                if (SitePermissions.getSite(this, h, type) != SitePermissions.BLOCK) continue;
                if (!anyBlocked) header(Lang.t("ブロックしたサイト"));
                anyBlocked = true;
                siteRow(h);
            }
        }

        gap();
        row(R.drawable.ic_si_add, Lang.t("サイトを追加"), Lang.t("このサイトだけ許可・ブロックします"), this::addSite);
        if (!hosts.isEmpty()) {
            row(R.drawable.ic_ps_delete, Lang.t("例外をすべて削除"), Lang.t("すべてのサイトを既定の動作に戻します"),
                    () -> confirm(Lang.t("例外をすべて削除"), SitePermissions.label(type) + Lang.t(" のサイトごとの設定をすべて削除しますか?"),
                            Lang.t("削除"), () -> {
                                for (String h : hosts) SitePermissions.setSite(this, h, type, SitePermissions.UNSET);
                                rebuild();
                            }));
        }
    }

    private void siteRow(String host) {
        int v = SitePermissions.getSite(this, host, type);
        row(R.drawable.ic_si_globe, host, SitePermissions.valueLabel(type, v), () -> SiteDetailActivity.open(this, host))
                .chevron();
    }

    private String optionTitle(int v) {
        if (type == SitePermissions.AUTOPLAY) return SitePermissions.valueLabel(type, v);
        switch (v) {
            case SitePermissions.ALLOW: return Lang.t("許可する");
            case SitePermissions.BLOCK: return Lang.t("ブロックする");
            default: return Lang.t("最初に確認する");
        }
    }

    private String optionSummary(int v) {
        switch (type) {
            case SitePermissions.JAVASCRIPT:
                return v == SitePermissions.ALLOW ? Lang.t("サイトは JavaScript を使用できます") : Lang.t("サイトは JavaScript を使用できません");
            case SitePermissions.POPUPS:
                return v == SitePermissions.ALLOW ? Lang.t("サイトはポップアップを開いたり、リダイレクトしたりできます")
                        : v == SitePermissions.BLOCK ? Lang.t("サイトはポップアップを開けません(おすすめ)")
                        : Lang.t("サイトがポップアップを開こうとしたら確認します");
            case SitePermissions.AUTOPLAY:
                return v == SitePermissions.ALLOW ? Lang.t("動画や音声を自動的に再生します")
                        : v == SitePermissions.BLOCK ? Lang.t("ページを操作するまで動画も再生しません")
                        : Lang.t("音の出る動画は、ページを操作するまで再生しません(おすすめ)");
            case SitePermissions.STORAGE:
                return v == SitePermissions.ALLOW ? Lang.t("サイトはデバイスにデータを保存し続けられます")
                        : Lang.t("空き容量が少ない時などに、サイトのデータが削除されることがあります");
            default:
                return v == SitePermissions.BLOCK ? Lang.t("サイトに") + SitePermissions.label(type) + Lang.t("の使用を許可しません")
                        : v == SitePermissions.ALLOW ? Lang.t("サイトは確認なしで使用できます")
                        : Lang.t("サイトが") + SitePermissions.label(type) + Lang.t("を使用する前に確認します(おすすめ)");
        }
    }

    private void addSite() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        input.setHint("example.com");
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);
        int[] options = SitePermissions.siteOptions(type);
        String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) labels[i] = SitePermissions.valueLabel(type, options[i]);
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("サイトを追加"))
                .setView(box)
                .setPositiveButton(Lang.t("次へ"), (d, w) -> {
                    String text = input.getText().toString().trim();
                    String host = SitePermissions.hostOf(text.contains("://") ? text : "https://" + text);
                    if (host == null || !host.contains(".")) {
                        Toast.makeText(this, Lang.t("サイトのアドレスを入力してください"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    choose(host, labels, -1, i -> SitePermissions.setSite(this, host, type, options[i]));
                })
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
        input.requestFocus();
    }

    // ------------------------------------------------------------------

    private void buildAllSites() {
        List<String> hosts = SitePermissions.hosts(this);
        if (hosts.isEmpty()) {
            note(Lang.t("サイトの権限を許可・ブロックすると、ここに表示されます。"));
            return;
        }
        gap();
        for (String host : hosts) {
            int n = 0;
            for (int t : SitePermissions.ALL) if (SitePermissions.getSite(this, host, t) != SitePermissions.UNSET) n++;
            row(R.drawable.ic_si_globe, host, n + Lang.t(" 件の権限"), () -> SiteDetailActivity.open(this, host)).chevron();
        }
        gap();
        row(R.drawable.ic_ps_delete, Lang.t("すべてのサイトの権限をリセット"), null,
                () -> confirm(Lang.t("権限をリセット"), Lang.t("すべてのサイトの権限を既定の動作に戻しますか?"), Lang.t("リセット"), () -> {
                    for (String h : hosts) SitePermissions.clearSite(this, h);
                    rebuild();
                }));
    }
}
