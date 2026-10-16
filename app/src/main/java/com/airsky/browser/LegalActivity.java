package com.airsky.browser;

import android.content.Intent;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * 法的情報(Pixel の「法的情報」風)。
 *
 * <p>オープンソース ライセンスの一覧と、LICENSE.MD の文章の部分(サードパーティのアセット・
 * Special Thanks・商標通知)を並べる。文章の部分は同じ画面を {@link #EXTRA_SECTION} 付きで開いて読む。</p>
 */
public class LegalActivity extends PixelSettingsActivity {

    private static final String EXTRA_SECTION = "section";

    private int section() {
        return getIntent().getIntExtra(EXTRA_SECTION, -1);
    }

    @NonNull
    @Override
    protected CharSequence pageTitle() {
        LicenseCatalog cat = LicenseCatalog.get(this);
        int s = section();
        if (s >= 0 && s < cat.sections.size()) return sectionTitle(cat.sections.get(s));
        return Lang.t("法的情報");
    }

    @Override
    protected void onBuild() {
        LicenseCatalog cat = LicenseCatalog.get(this);
        int s = section();
        if (s >= 0 && s < cat.sections.size()) {
            gap();
            card(paragraph(LicenseCatalog.plain(cat.sections.get(s).body)));
            gap();
            return;
        }

        gap();
        row(R.drawable.ic_ps_reader, Lang.t("利用規約"), Lang.t("AirSky をご利用いただく際の条件"),
                () -> startActivity(TermsActivity.viewIntent(this))).chevron();
        row(R.drawable.ic_ps_code, Lang.t("オープンソース ライセンス"),
                cat.entries.size() + Lang.t(" 件のライブラリとソフトウェア"),
                () -> OssLicensesActivity.open(this)).chevron();
        for (int i = 0; i < cat.sections.size(); i++) {
            LicenseCatalog.Section sec = cat.sections.get(i);
            final int index = i;
            row(sectionIcon(sec), sectionTitle(sec), sectionSummary(sec), () -> {
                Intent intent = new Intent(this, LegalActivity.class);
                intent.putExtra(EXTRA_SECTION, index);
                startActivity(intent);
            }).chevron();
        }
        if (cat.footer.length() > 0) note(LicenseCatalog.plain(cat.footer));
    }

    private static String sectionTitle(LicenseCatalog.Section s) {
        if (s.title.startsWith("Third-party assets")) return Lang.t("サードパーティのアセット");
        return s.title;
    }

    private static String sectionSummary(LicenseCatalog.Section s) {
        if (s.title.startsWith("Third-party assets")) return Lang.t("アニメーション素材のライセンス");
        if (s.title.startsWith("Special Thanks")) return Lang.t("AirSky の基盤となったプロジェクト");
        if (s.title.contains("商標")) return Lang.t("このアプリに登場する商標について");
        return null;
    }

    private static int sectionIcon(LicenseCatalog.Section s) {
        if (s.title.startsWith("Third-party assets")) return R.drawable.ic_ps_image;
        if (s.title.startsWith("Special Thanks")) return R.drawable.ic_ps_info;
        return R.drawable.ic_ps_reader;
    }

    private TextView paragraph(CharSequence text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextIsSelectable(true);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setLineSpacing(0, 1.25f);
        t.setTextColor(attrColor(com.google.android.material.R.attr.colorOnSurface));
        t.setPadding(dp(20), dp(18), dp(20), dp(18));
        android.text.util.Linkify.addLinks(t, android.text.util.Linkify.WEB_URLS);
        return t;
    }
}
