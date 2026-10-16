package com.airsky.browser;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.mozilla.geckoview.WebExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 拡張機能 1 つの画面。有効/無効・シークレットでの実行・設定ページ・詳細・権限・削除・報告。
 */
public class ExtensionDetailActivity extends AppCompatActivity implements ExtensionManager.Listener {

    public static final String EXTRA_ID = "extension_id";
    /** シートの中のカードの印(シートの地の色と区別できる色にする)。 */
    private static final String SHEET_GROUP = "sheet_group";

    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

    private ExtensionManager manager;
    private String id;
    @Nullable private WebExtension ext;
    private boolean entered;
    /** スイッチを押してから Gecko の返事が来るまでは、一覧を作り直さない(スイッチの動きが途切れるため)。 */
    private boolean pending;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extension_detail);
        manager = ExtensionManager.get(this);
        id = getIntent().getStringExtra(EXTRA_ID);
        findViewById(R.id.edBack).setOnClickListener(v -> finish());

        NestedScrollView scroll = findViewById(R.id.edScroll);
        View header = findViewById(R.id.edHeader);
        TextView small = findViewById(R.id.edSmallTitle);
        View hero = findViewById(R.id.edHero);
        float d = getResources().getDisplayMetrics().density;
        scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, x, y, ox, oy) -> {
            float f = Math.max(0f, Math.min(1f, (y - 80 * d) / (48 * d)));
            small.setAlpha(f);
            small.setTranslationY((1f - f) * 8 * d);
            // 大きなアイコンと名前は、スクロールに合わせて少し縮みながら薄くなる
            float g = Math.max(0f, Math.min(1f, y / (160 * d)));
            hero.setAlpha(1f - g * 0.7f);
            hero.setScaleX(1f - g * 0.06f);
            hero.setScaleY(1f - g * 0.06f);
            header.setElevation(y > 0 ? 3 * d : 0);
        });

        findViewById(R.id.edRemove).setOnClickListener(v -> confirmRemove());
        findViewById(R.id.edReport).setOnClickListener(v -> {
            if (ext == null) return;
            open("https://addons.mozilla.org/ja/android/feedback/addon/" + ext.id + "/");
        });
        manager.addListener(this);
        bind();
    }

    @Override
    protected void onDestroy() {
        manager.removeListener(this);
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.ext_pop_in, R.anim.ext_pop_out);
    }

    @Override
    public void onExtensionsChanged() {
        if (isFinishing()) return;
        if (manager.isLoaded() && manager.find(id) == null) {
            // 削除された
            finish();
            return;
        }
        if (!pending) bind();
    }

    // ------------------------------------------------------------------

    private void bind() {
        ext = manager.find(id);
        if (ext == null) return;
        WebExtension e = ext;
        String name = ExtensionUi.name(e);
        ((TextView) findViewById(R.id.edSmallTitle)).setText(name);
        ((TextView) findViewById(R.id.edName)).setText(name);
        TextView author = findViewById(R.id.edAuthor);
        author.setText(e.metaData.creatorName != null ? Lang.t("作成者: ") + e.metaData.creatorName : "");
        author.setVisibility(e.metaData.creatorName != null ? View.VISIBLE : View.GONE);
        ImageView icon = findViewById(R.id.edHeroIcon);
        ExtensionUi.loadIcon(icon, e, Math.round(60 * getResources().getDisplayMetrics().density));
        icon.setAlpha(ExtensionManager.isEnabled(e) ? 1f : 0.45f);

        bindChips(e);
        bindWarning(e);
        bindToggles(e);
        bindLinks(e);

        if (!entered) {
            entered = true;
            playEnter();
        }
    }

    private void bindChips(WebExtension e) {
        LinearLayout chips = findViewById(R.id.edChips);
        chips.removeAllViews();
        if (e.metaData.version != null) addChip(chips, "v" + e.metaData.version, 0);
        if (e.metaData.averageRating > 0) {
            addChip(chips, String.format(java.util.Locale.JAPAN, "%.1f", e.metaData.averageRating), R.drawable.ic_ext_star);
        }
        if (e.metaData.isRecommended) addChip(chips, Lang.t("おすすめ"), R.drawable.ic_ext_data);
        if (!ExtensionManager.isEnabled(e)) addChip(chips, Lang.t("無効"), 0);
    }

    private void addChip(LinearLayout parent, String text, int icon) {
        float d = getResources().getDisplayMetrics().density;
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(com.google.android.material.color.MaterialColors.getColor(t,
                com.google.android.material.R.attr.colorOnSecondaryContainer));
        t.setBackgroundResource(R.drawable.bg_ext_info_chip);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(Math.round((icon != 0 ? 8 : 12) * d), 0, Math.round(12 * d), 0);
        if (icon != 0) {
            t.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
            t.setCompoundDrawablePadding(Math.round(4 * d));
            t.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(t.getCurrentTextColor()));
            android.graphics.drawable.Drawable dr = t.getCompoundDrawablesRelative()[0];
            if (dr != null) {
                dr.setBounds(0, 0, Math.round(16 * d), Math.round(16 * d));
                t.setCompoundDrawablesRelative(dr, null, null, null);
            }
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(28 * d));
        lp.setMarginStart(Math.round(3 * d));
        lp.setMarginEnd(Math.round(3 * d));
        parent.addView(t, lp);
    }

    private void bindWarning(WebExtension e) {
        TextView w = findViewById(R.id.edWarning);
        int flags = e.metaData.disabledFlags;
        String msg = null;
        if ((flags & WebExtension.DisabledFlags.BLOCKLIST) != 0) {
            msg = Lang.t("この拡張機能は安全上の理由で Mozilla によりブロックされています。");
        } else if ((flags & WebExtension.DisabledFlags.SIGNATURE) != 0) {
            msg = Lang.t("この拡張機能は署名を確認できないため、無効になっています。");
        } else if ((flags & WebExtension.DisabledFlags.APP_VERSION) != 0) {
            msg = Lang.t("この拡張機能は今のバージョンの AirSky に対応していません。");
        }
        w.setText(msg);
        w.setVisibility(msg != null ? View.VISIBLE : View.GONE);
    }

    private void bindToggles(WebExtension e) {
        LinearLayout g = findViewById(R.id.edToggles);
        g.removeAllViews();
        boolean enabled = ExtensionManager.isEnabled(e);
        View on = ExtensionUi.addRow(g, R.drawable.ic_ext_puzzle, Lang.t("有効"),
                enabled ? Lang.t("ページを開くとこの拡張機能が動きます") : Lang.t("この拡張機能は止まっています"));
        bindSwitch(on, enabled, checked -> {
            pending = true;
            manager.setEnabled(e, checked).accept(x -> runOnUiThread(this::settle), x -> runOnUiThread(this::settle));
        });
        View priv = ExtensionUi.addRow(g, R.drawable.ic_ts_incognito, Lang.t("シークレットモードで実行する"),
                Lang.t("シークレットタブでもこの拡張機能を使えるようにします"));
        bindSwitch(priv, e.metaData.allowedInPrivateBrowsing, checked -> {
            pending = true;
            manager.setAllowedInPrivate(e, checked).accept(x -> runOnUiThread(this::settle), x -> runOnUiThread(this::settle));
        });
        ExtensionUi.groupChildren(g, SHEET_GROUP.equals(g.getTag()));
    }

    private void settle() {
        pending = false;
        if (!isFinishing()) bind();
    }

    private void bindLinks(WebExtension e) {
        LinearLayout g = findViewById(R.id.edLinks);
        g.removeAllViews();
        String options = e.metaData.optionsPageUrl;
        if (options != null && !options.isEmpty()) {
            View s = ExtensionUi.addRow(g, R.drawable.ic_menu_settings, Lang.t("設定"), Lang.t("拡張機能の設定ページを開きます"));
            chevron(s);
            s.setEnabled(ExtensionManager.isEnabled(e));
            s.setAlpha(ExtensionManager.isEnabled(e) ? 1f : 0.5f);
            s.setOnClickListener(v -> {
                if (!ExtensionManager.isEnabled(e)) return;
                manager.openOptionsPage(e);
            });
        }
        View info = ExtensionUi.addRow(g, R.drawable.ic_si_info, Lang.t("詳細"), Lang.t("説明・バージョン・作成者など"));
        chevron(info);
        info.setOnClickListener(v -> showDetails(e));
        int count = countPermissions(e);
        View perms = ExtensionUi.addRow(g, R.drawable.ic_ext_permission, Lang.t("権限"),
                count == 0 ? Lang.t("特別な権限はありません") : count + Lang.t(" 個の権限"));
        chevron(perms);
        perms.setOnClickListener(v -> showPermissions(e));
        if (e.metaData.amoListingUrl != null) {
            View store = ExtensionUi.addRow(g, R.drawable.ic_ext_store, Lang.t("アドオンストアで見る"), null);
            ((ImageView) store.findViewById(R.id.erChevron)).setImageResource(R.drawable.ic_ext_open);
            chevron(store);
            store.setOnClickListener(v -> open(e.metaData.amoListingUrl));
        }
        ExtensionUi.groupChildren(g, SHEET_GROUP.equals(g.getTag()));
    }

    private static void chevron(View row) {
        row.findViewById(R.id.erChevron).setVisibility(View.VISIBLE);
    }

    private interface OnChecked {
        void onChecked(boolean checked);
    }

    private static void bindSwitch(View row, boolean checked, OnChecked listener) {
        MaterialSwitch sw = row.findViewById(R.id.erSwitch);
        sw.setVisibility(View.VISIBLE);
        sw.setChecked(checked);
        row.setOnClickListener(v -> {
            sw.toggle();
            listener.onChecked(sw.isChecked());
        });
    }

    // ------------------------------------------------------------------
    // 詳細・権限のシート
    // ------------------------------------------------------------------

    private void showDetails(WebExtension e) {
        LinearLayout body = newSheet(Lang.t("詳細"));
        String full = e.metaData.fullDescription != null ? e.metaData.fullDescription : e.metaData.description;
        if (full != null && !full.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(Html.fromHtml(full.replace("\n", "<br>"), Html.FROM_HTML_MODE_COMPACT));
            t.setMovementMethod(LinkMovementMethod.getInstance());
            t.setTextSize(15);
            t.setLineSpacing(0, 1.15f);
            t.setTextColor(com.google.android.material.color.MaterialColors.getColor(t,
                    com.google.android.material.R.attr.colorOnSurface));
            float d = getResources().getDisplayMetrics().density;
            t.setPadding(Math.round(8 * d), 0, Math.round(8 * d), Math.round(12 * d));
            body.addView(t);
        }
        LinearLayout g = group(body);
        if (e.metaData.creatorName != null) {
            View r = ExtensionUi.addRow(g, R.drawable.ic_ext_person, Lang.t("作成者"), e.metaData.creatorName);
            if (e.metaData.creatorUrl != null) r.setOnClickListener(v -> open(e.metaData.creatorUrl));
        }
        if (e.metaData.version != null) ExtensionUi.addRow(g, R.drawable.ic_ext_puzzle, Lang.t("バージョン"), e.metaData.version);
        if (e.metaData.updateDate != null) {
            ExtensionUi.addRow(g, R.drawable.ic_ext_update, Lang.t("最終更新日"), formatDate(e.metaData.updateDate));
        }
        if (e.metaData.averageRating > 0) {
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
            sb.append(ExtensionUi.stars(g, e.metaData.averageRating))
                    .append(String.format(java.util.Locale.JAPAN, Lang.t("  %.1f  ·  レビュー数: %s"),
                            e.metaData.averageRating, ExtensionUi.count(e.metaData.reviewCount)));
            View r = ExtensionUi.addRow(g, R.drawable.ic_ext_star, Lang.t("評価"), sb);
            if (e.metaData.reviewUrl != null) r.setOnClickListener(v -> open(e.metaData.reviewUrl));
        }
        if (e.metaData.homepageUrl != null) {
            View r = ExtensionUi.addRow(g, R.drawable.ic_ext_globe, Lang.t("ホームページ"), e.metaData.homepageUrl);
            r.setOnClickListener(v -> open(e.metaData.homepageUrl));
        }
        ExtensionUi.addRow(g, R.drawable.ic_si_info, "ID", e.id);
        ExtensionUi.groupChildren(g, SHEET_GROUP.equals(g.getTag()));
    }

    private void showPermissions(WebExtension e) {
        LinearLayout body = newSheet(Lang.t("権限"));
        List<String> required = new ArrayList<>();
        String sites = ExtensionUi.describeOrigins(e.metaData.requiredOrigins);
        if (sites != null) required.add(sites);
        if (e.metaData.requiredPermissions != null) {
            for (String p : e.metaData.requiredPermissions) required.add(ExtensionUi.describePermission(p));
        }
        subHeader(body, Lang.t("必要な権限"));
        LinearLayout g = group(body);
        if (required.isEmpty()) {
            ExtensionUi.addRow(g, R.drawable.ic_ext_check, Lang.t("特別な権限は必要ありません"), null);
        } else {
            for (String s : required) {
                ExtensionUi.addRow(g, s.equals(sites) ? R.drawable.ic_ext_globe : R.drawable.ic_ext_permission, s, null);
            }
        }
        ExtensionUi.groupChildren(g, SHEET_GROUP.equals(g.getTag()));

        // 任意の権限(あとから許可/取り消しできる)
        List<String> optPerms = list(e.metaData.optionalPermissions);
        List<String> optOrigins = list(e.metaData.optionalOrigins);
        if (!optPerms.isEmpty() || !optOrigins.isEmpty()) {
            subHeader(body, Lang.t("任意の権限"));
            LinearLayout og = group(body);
            List<String> granted = list(e.metaData.grantedOptionalPermissions);
            List<String> grantedOrigins = list(e.metaData.grantedOptionalOrigins);
            for (String p : optPerms) addOptional(og, e, p, false, granted.contains(p));
            for (String o : optOrigins) addOptional(og, e, o, true, grantedOrigins.contains(o));
            ExtensionUi.groupChildren(og, SHEET_GROUP.equals(og.getTag()));
        }

        subHeader(body, Lang.t("データ収集"));
        LinearLayout dg = group(body);
        String data = ExtensionUi.describeDataCollection(e.metaData.requiredDataCollectionPermissions);
        ExtensionUi.addRow(dg, R.drawable.ic_ext_data,
                data != null ? data : Lang.t("開発者によると、この拡張機能はデータ収集を必要としません。"), null);
        ExtensionUi.groupChildren(dg, SHEET_GROUP.equals(dg.getTag()));
    }

    private void addOptional(LinearLayout g, WebExtension e, String perm, boolean origin, boolean granted) {
        String title = origin
                ? (ExtensionUi.isAllUrls(perm) ? Lang.t("すべてのウェブサイトのデータへのアクセス") : perm)
                : ExtensionUi.describePermission(perm);
        View row = ExtensionUi.addRow(g, origin ? R.drawable.ic_ext_globe : R.drawable.ic_ext_permission, title, null);
        bindSwitch(row, granted, checked -> manager.setOptionalPermission(e, perm, origin, checked));
    }

    @NonNull
    private static List<String> list(@Nullable String[] a) {
        return a == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(a));
    }

    private static int countPermissions(WebExtension e) {
        int n = 0;
        if (e.metaData.requiredPermissions != null) n += e.metaData.requiredPermissions.length;
        if (e.metaData.requiredOrigins != null && e.metaData.requiredOrigins.length > 0) n++;
        return n;
    }

    /** 中身をコードで作る、下から出るシート。中身を足す LinearLayout を返す。 */
    private LinearLayout newSheet(String title) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        float d = getResources().getDisplayMetrics().density;
        NestedScrollView scroll = new NestedScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Math.round(16 * d), 0, Math.round(16 * d), Math.round(24 * d));
        scroll.addView(root);

        View handle = new View(this);
        handle.setBackgroundResource(R.drawable.bg_page_menu_handle);
        handle.setAlpha(0.6f);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Math.round(36 * d), Math.round(4 * d));
        hl.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        hl.topMargin = Math.round(12 * d);
        root.addView(handle, hl);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(24);
        t.setTextColor(com.google.android.material.color.MaterialColors.getColor(t,
                com.google.android.material.R.attr.colorOnSurface));
        t.setPadding(Math.round(8 * d), Math.round(16 * d), Math.round(8 * d), Math.round(12 * d));
        root.addView(t);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body);

        dialog.setContentView(scroll);
        ExtensionUi.padForNavigationBar(root);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();
        return body;
    }

    private LinearLayout group(LinearLayout body) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setTag(SHEET_GROUP);
        body.addView(g, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return g;
    }

    private void subHeader(LinearLayout body, String text) {
        TextView h = new TextView(this, null, 0, R.style.ExtSectionHeader);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(16 * d);
        lp.bottomMargin = Math.round(8 * d);
        h.setText(text);
        body.addView(h, lp);
    }

    @NonNull
    private static String formatDate(@NonNull String iso) {
        // 例: 2026-09-01T12:34:56Z → 2026年9月1日
        try {
            String[] p = iso.substring(0, 10).split("-");
            return Integer.parseInt(p[0]) + Lang.t("年") + Integer.parseInt(p[1]) + Lang.t("月") + Integer.parseInt(p[2]) + Lang.t("日");
        } catch (Exception e) {
            return iso;
        }
    }

    // ------------------------------------------------------------------

    private void confirmRemove() {
        WebExtension e = ext;
        if (e == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("「") + ExtensionUi.name(e) + Lang.t("」を削除しますか？"))
                .setMessage(Lang.t("この拡張機能とその設定・保存したデータが削除されます。"))
                .setPositiveButton(Lang.t("削除"), (dlg, w) -> manager.uninstall(e))
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    private void open(@NonNull String url) {
        startActivity(new Intent(this, MainActivity.class)
                .putExtra("url", url)
                .putExtra(MainActivity.EXTRA_URL_IN_NEW_TAB, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    /** 開いた時: アイコンが少し大きい所から収まり、カードが順にすべり込む。 */
    private void playEnter() {
        float d = getResources().getDisplayMetrics().density;
        View tile = findViewById(R.id.edIconTile);
        tile.setScaleX(0.7f);
        tile.setScaleY(0.7f);
        tile.setAlpha(0f);
        tile.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(480)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
        ViewGroup content = findViewById(R.id.edContent);
        for (int i = 1; i < content.getChildCount(); i++) {
            View c = content.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(18 * d);
            c.animate().alpha(1f).translationY(0f).setStartDelay(80L + 50L * i).setDuration(420)
                    .setInterpolator(EMPHASIZED).start();
        }
    }
}
