package com.airsky.browser;

import android.content.Context;
import android.content.Intent;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * オープンソース ライセンス(Pixel の「サードパーティ ライセンス」風)。
 *
 * <p>一覧ではライブラリ名とライセンスを並べ、押すとそのライブラリの詳細
 * (著作権者・ライセンス・ウェブサイト・説明)を同じ見た目の画面で出す。</p>
 */
public class OssLicensesActivity extends PixelSettingsActivity {

    private static final String EXTRA_INDEX = "index";

    /** 詳細を出すライブラリの番号。-1 なら一覧。 */
    private int index() {
        return getIntent().getIntExtra(EXTRA_INDEX, -1);
    }

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        LicenseCatalog cat = LicenseCatalog.get(this);
        int i = index();
        if (i >= 0 && i < cat.entries.size()) return cat.entries.get(i).title();
        return Lang.t("オープンソース ライセンス");
    }

    @Override
    protected void onBuild() {
        LicenseCatalog cat = LicenseCatalog.get(this);
        int i = index();
        if (i >= 0 && i < cat.entries.size()) {
            buildDetail(cat.entries.get(i));
        } else {
            buildList(cat);
        }
    }

    private void buildList(LicenseCatalog cat) {
        note(Lang.t("AirSky は、以下のオープンソース ソフトウェアを使用しています。"));
        gap();
        for (int i = 0; i < cat.entries.size(); i++) {
            LicenseCatalog.Entry e = cat.entries.get(i);
            final int index = i;
            row(0, e.title(), summaryOf(e), () -> {
                Intent intent = new Intent(this, OssLicensesActivity.class);
                intent.putExtra(EXTRA_INDEX, index);
                startActivity(intent);
            });
        }
        gap();
    }

    private static String summaryOf(LicenseCatalog.Entry e) {
        if (e.group != null && e.name != null && !e.name.isEmpty()) {
            return e.license != null ? e.group + "\n" + e.license : e.group;
        }
        return e.license;
    }

    private void buildDetail(LicenseCatalog.Entry e) {
        gap();
        if (e.group != null) row(R.drawable.ic_ps_code, Lang.t("グループ"), e.group, null);
        if (e.name != null && !e.name.isEmpty()) row(R.drawable.ic_ps_info, Lang.t("名前"), e.name, null);
        if (e.copyright != null) row(R.drawable.ic_ps_privacy, Lang.t("著作権者"), e.copyright, null);

        if (e.license != null || e.licenseUrl != null) {
            header(Lang.t("ライセンス"));
            String url = e.licenseUrl;
            Row r = row(R.drawable.ic_ps_reader, e.license != null ? e.license : Lang.t("ライセンス"),
                    url, url == null ? null : () -> LicenseCatalog.openUrl(this, url));
            if (url != null) r.chevron();
        }
        if (e.url != null) {
            header(Lang.t("ウェブサイト"));
            String url = e.url;
            row(R.drawable.ic_ps_link, Lang.t("プロジェクトのページ"), url, () -> LicenseCatalog.openUrl(this, url)).chevron();
        }
        if (e.note.length() > 0) {
            header(Lang.t("AirSky での利用"));
            card(paragraph(e.note));
        }
        gap();
    }

    /** カードの中の文章。 */
    private TextView paragraph(CharSequence text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextIsSelectable(true);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setLineSpacing(0, 1.25f);
        t.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurface));
        t.setPadding(dp(20), dp(18), dp(20), dp(18));
        return t;
    }

    static void open(Context c) {
        c.startActivity(new Intent(c, OssLicensesActivity.class));
    }
}
