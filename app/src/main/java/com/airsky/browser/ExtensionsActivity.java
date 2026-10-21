package com.airsky.browser;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.util.ArrayList;
import java.util.List;

/**
 * 拡張機能の管理画面。入っている拡張機能(有効/無効)と、Firefox のアドオンストアのおすすめを並べる。
 *
 * <p>.xpi ファイルを開いた時(ファイルアプリ・ダウンロード一覧から)もこの画面が受け取り、
 * インストールの確認を出す。</p>
 */
public class ExtensionsActivity extends AppCompatActivity implements ExtensionManager.Listener {

    /** 最初に開く一覧。{@link #KIND_COARA} なら Coara Browser 拡張機能の一覧。 */
    static final String EXTRA_KIND = "kind";
    static final String KIND_COARA = "coara";

    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

    private ExtensionManager manager;
    private LinearLayout sections;
    private TextView summary;
    private ExtendedFloatingActionButton find;
    private TextView note;
    @Nullable private List<ExtensionStore.Addon> recommended;
    private boolean recommendedFailed;
    private boolean firstBind = true;
    /** Coara Browser 拡張機能の一覧を出している */
    private boolean coaraMode;
    private CoaraExtensionsSection coara;

    private final ActivityResultLauncher<String[]> pickXpi = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) manager.installFromUri(uri);
            });

    private final ActivityResultLauncher<String[]> pickPlugin = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) coara.installFromUri(uri);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extensions);
        manager = ExtensionManager.get(this);
        sections = findViewById(R.id.exSections);
        summary = findViewById(R.id.exBigSummary);
        note = findViewById(R.id.exNote);
        coara = new CoaraExtensionsSection(this, sections, this::bind, this::pickPluginFile);
        coaraMode = savedInstanceState != null
                ? savedInstanceState.getBoolean(EXTRA_KIND)
                : KIND_COARA.equals(getIntent().getStringExtra(EXTRA_KIND));

        findViewById(R.id.exBack).setOnClickListener(v -> finish());
        findViewById(R.id.exMenu).setOnClickListener(this::showMenu);
        find = findViewById(R.id.exFind);
        find.setOnClickListener(v -> {
            if (coaraMode) pickPluginFile();
            else openStore(ExtensionManager.AMO_URL);
        });

        // [Firefox 拡張機能] [Coara Browser 拡張機能] の切り替え
        com.google.android.material.button.MaterialButtonToggleGroup kind = findViewById(R.id.exKind);
        ((TextView) findViewById(R.id.exKindFirefox)).setText(Lang.t("Firefox 拡張機能"));
        ((TextView) findViewById(R.id.exKindCoara)).setText(Lang.t("Coara Browser 拡張機能"));
        kind.check(coaraMode ? R.id.exKindCoara : R.id.exKindFirefox);
        kind.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            boolean now = id == R.id.exKindCoara;
            if (now == coaraMode) return;
            coaraMode = now;
            applyKind();
            bind();
            playEnter();
        });
        applyKind();

        // スクロールすると: 見出しに小さな題名がフェードで現れ、大きな題名は少し沈む。ボタンは縮む
        NestedScrollView scroll = findViewById(R.id.exScroll);
        View header = findViewById(R.id.exHeader);
        TextView small = findViewById(R.id.exSmallTitle);
        View big = findViewById(R.id.exBigTitle);
        float density = getResources().getDisplayMetrics().density;
        scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, x, y, ox, oy) -> {
            float f = Math.max(0f, Math.min(1f, (y - 24 * density) / (40 * density)));
            small.setAlpha(f);
            small.setTranslationY((1f - f) * 8 * density);
            big.setAlpha(1f - f * 0.6f);
            header.setElevation(y > 0 ? 3 * density : 0);
            if (y > oy + 4) find.shrink();
            else if (y < oy - 4) find.extend();
        });

        manager.addListener(this);
        coara.start();
        bind();
        loadRecommended(false);
        handleIntent(getIntent());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_KIND, coaraMode);
    }

    /** 一覧の種類に合わせて、下のボタンと注意書きを変える。 */
    private void applyKind() {
        if (coaraMode) {
            find.setText(Lang.t("ファイルから追加"));
            find.setIconResource(R.drawable.ic_ext_file);
            note.setText(Lang.t("Coara Browser 用のプラグイン(plugin.xml を含む .zip)・ユーザースクリプト(.user.js)・ユーザースタイル(.user.css)を追加できます。"));
        } else {
            find.setText(Lang.t("拡張機能を探す"));
            find.setIconResource(R.drawable.ic_ext_store);
            note.setText(Lang.t("拡張機能は Firefox のアドオンストアから入れられます。.xpi ファイルを開いてインストールすることもできます。"));
        }
        find.extend();
    }

    private void pickPluginFile() {
        try {
            pickPlugin.launch(new String[]{"*/*"});
        } catch (Exception e) {
            Toast.makeText(this, Lang.t("ファイルを選ぶアプリがありません"), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (KIND_COARA.equals(intent.getStringExtra(EXTRA_KIND)) && !coaraMode) {
            ((com.google.android.material.button.MaterialButtonToggleGroup) findViewById(R.id.exKind))
                    .check(R.id.exKindCoara);
        }
        handleIntent(intent);
    }

    /** .xpi を開いた時。 */
    private void handleIntent(@Nullable Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;
        Uri data = intent.getData();
        if (data == null) return;
        intent.setData(null);
        manager.installFromUri(data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bind();
    }

    @Override
    protected void onDestroy() {
        manager.removeListener(this);
        coara.stop();
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.ext_pop_in, R.anim.ext_pop_out);
    }

    @Override
    public void onExtensionsChanged() {
        if (!isFinishing()) bind();
    }

    // ------------------------------------------------------------------
    // 一覧
    // ------------------------------------------------------------------

    private void bind() {
        if (isFinishing()) return;
        if (coaraMode) {
            summary.setText(coara.summary());
            sections.removeAllViews();
            coara.bind();
            if (firstBind) {
                firstBind = false;
                playEnter();
            }
            return;
        }
        List<WebExtension> enabled = new ArrayList<>();
        List<WebExtension> disabled = new ArrayList<>();
        for (WebExtension ext : manager.getInstalled()) {
            if (ExtensionManager.isEnabled(ext)) enabled.add(ext);
            else disabled.add(ext);
        }
        int total = enabled.size() + disabled.size();
        summary.setText(total == 0
                ? Lang.t("まだ拡張機能は入っていません。おすすめから追加してみましょう。")
                : total + Lang.t(" 個の拡張機能が入っています(有効 ") + enabled.size() + Lang.t(" 個)"));

        sections.removeAllViews();
        if (!enabled.isEmpty()) addInstalledSection(Lang.t("有効"), enabled);
        if (!disabled.isEmpty()) addInstalledSection(Lang.t("無効"), disabled);
        addRecommendedSection();

        if (firstBind && manager.isLoaded()) {
            firstBind = false;
            playEnter();
        }
    }

    private void addInstalledSection(String title, List<WebExtension> list) {
        addHeader(title);
        LinearLayout group = newGroup();
        for (WebExtension ext : list) group.addView(installedRow(group, ext));
        ExtensionUi.groupChildren(group);
    }

    private View installedRow(ViewGroup parent, WebExtension ext) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_extension, parent, false);
        int iconPx = Math.round(34 * getResources().getDisplayMetrics().density);
        ImageView icon = row.findViewById(R.id.eIcon);
        ExtensionUi.loadIcon(icon, ext, iconPx);
        boolean enabled = ExtensionManager.isEnabled(ext);
        icon.setAlpha(enabled ? 1f : 0.45f);
        ((TextView) row.findViewById(R.id.eName)).setText(ExtensionUi.name(ext));
        TextView sum = row.findViewById(R.id.eSummary);
        String desc = ext.metaData.description;
        sum.setText(desc != null ? desc : "");
        sum.setVisibility(desc != null && !desc.isEmpty() ? View.VISIBLE : View.GONE);
        TextView meta = row.findViewById(R.id.eMeta);
        meta.setText(metaFor(meta, ext));

        MaterialSwitch sw = row.findViewById(R.id.eSwitch);
        sw.setVisibility(View.VISIBLE);
        sw.setChecked(enabled);
        sw.setContentDescription(ExtensionUi.name(ext) + Lang.t("を有効にする"));
        sw.setOnCheckedChangeListener((b, checked) -> {
            if (checked != ExtensionManager.isEnabled(ext)) manager.setEnabled(ext, checked);
        });
        row.setOnClickListener(v -> openDetail(ext));
        return row;
    }

    private CharSequence metaFor(View v, WebExtension ext) {
        if (!ExtensionManager.isEnabled(ext)) {
            int flags = ext.metaData.disabledFlags;
            if ((flags & WebExtension.DisabledFlags.BLOCKLIST) != 0) return Lang.t("安全上の理由でブロックされています");
            if ((flags & WebExtension.DisabledFlags.SIGNATURE) != 0) return Lang.t("署名を確認できないため無効になっています");
            if ((flags & WebExtension.DisabledFlags.APP_VERSION) != 0) return Lang.t("このバージョンには対応していません");
            return Lang.t("無効");
        }
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        if (ext.metaData.averageRating > 0) {
            sb.append(ExtensionUi.stars(v, ext.metaData.averageRating));
            sb.append(Lang.t("  レビュー数: ")).append(ExtensionUi.count(ext.metaData.reviewCount));
        } else if (ext.metaData.version != null) {
            sb.append(Lang.t("バージョン ")).append(ext.metaData.version);
        }
        if (ext.metaData.allowedInPrivateBrowsing) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(Lang.t("シークレットでも実行"));
        }
        return sb;
    }

    private void addRecommendedSection() {
        addHeader(Lang.t("おすすめ"));
        LinearLayout group = newGroup();
        if (recommended == null) {
            View row = ExtensionUi.addRow(group, R.drawable.ic_ext_store,
                    recommendedFailed ? Lang.t("おすすめを読み込めませんでした") : Lang.t("おすすめを読み込んでいます…"),
                    recommendedFailed ? Lang.t("タップしてもう一度読み込む") : null);
            if (recommendedFailed) row.setOnClickListener(v -> loadRecommended(true));
            ExtensionUi.groupChildren(group);
            return;
        }
        int shown = 0;
        for (ExtensionStore.Addon a : recommended) {
            if (manager.isInstalled(a.guid)) continue;
            group.addView(recommendedRow(group, a));
            shown++;
        }
        if (shown == 0) {
            ExtensionUi.addRow(group, R.drawable.ic_ext_check, Lang.t("おすすめの拡張機能はすべて追加済みです"), null);
        }
        ExtensionUi.groupChildren(group);
    }

    private View recommendedRow(ViewGroup parent, ExtensionStore.Addon a) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_extension, parent, false);
        ImageView icon = row.findViewById(R.id.eIcon);
        int iconPx = Math.round(34 * getResources().getDisplayMetrics().density);
        icon.setImageBitmap(ExtensionUi.letterIcon(this, a.name, iconPx));
        icon.setTag(R.id.ext_icon_tag, a.guid);
        ExtensionStore.loadIcon(a.iconUrl, bmp -> {
            if (bmp != null && a.guid.equals(icon.getTag(R.id.ext_icon_tag))) icon.setImageBitmap(bmp);
        });
        ((TextView) row.findViewById(R.id.eName)).setText(a.name);
        TextView sum = row.findViewById(R.id.eSummary);
        sum.setText(a.summary);
        sum.setVisibility(a.summary.isEmpty() ? View.GONE : View.VISIBLE);
        TextView meta = row.findViewById(R.id.eMeta);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        sb.append(ExtensionUi.stars(meta, a.rating));
        sb.append(Lang.t("  レビュー数: ")).append(ExtensionUi.count(a.reviews));
        meta.setText(sb);

        View box = row.findViewById(R.id.eAddBox);
        ImageButton add = row.findViewById(R.id.eAdd);
        View progress = row.findViewById(R.id.eProgress);
        box.setVisibility(a.downloadUrl != null ? View.VISIBLE : View.GONE);
        boolean busy = manager.isInstalling(a.downloadUrl);
        add.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        add.setContentDescription(a.name + Lang.t("を追加"));
        add.setOnClickListener(v -> {
            if (a.downloadUrl == null) return;
            v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(90).withEndAction(() -> {
                v.setScaleX(1f);
                v.setScaleY(1f);
                v.setVisibility(View.INVISIBLE);
                progress.setVisibility(View.VISIBLE);
                manager.install(a.downloadUrl, WebExtensionController.INSTALLATION_METHOD_MANAGER);
            }).start();
        });
        row.setOnClickListener(v -> {
            if (a.pageUrl != null) openStore(a.pageUrl);
        });
        return row;
    }

    private void loadRecommended(boolean force) {
        recommendedFailed = false;
        if (force) {
            recommended = null;
            bind();
        }
        ExtensionStore.loadRecommended(this, force, (list, fromNetwork) -> {
            if (isFinishing()) return;
            if (list.isEmpty()) {
                if (recommended == null) recommendedFailed = true;
            } else {
                recommended = list;
            }
            bind();
        });
    }

    private void addHeader(String title) {
        TextView h = new TextView(this, null, 0, R.style.ExtSectionHeader);
        h.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) h.getLayoutParams();
        float d = getResources().getDisplayMetrics().density;
        lp.topMargin = Math.round(24 * d);
        lp.bottomMargin = Math.round(8 * d);
        h.setText(title);
        sections.addView(h);
    }

    private LinearLayout newGroup() {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        sections.addView(g, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return g;
    }

    /** 最初に開いた時: カードが少しずつ遅れて下からすべり込む。 */
    private void playEnter() {
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < sections.getChildCount(); i++) {
            View c = sections.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(20 * d);
            c.animate().alpha(1f).translationY(0f).setStartDelay(40L * i).setDuration(420)
                    .setInterpolator(EMPHASIZED).start();
        }
    }

    // ------------------------------------------------------------------

    private void openDetail(WebExtension ext) {
        startActivity(new Intent(this, ExtensionDetailActivity.class)
                .putExtra(ExtensionDetailActivity.EXTRA_ID, ext.id));
        overridePendingTransition(R.anim.ext_in, R.anim.ext_out);
    }

    /** ストアのページをブラウザの新しいタブで開く。 */
    private void openStore(@NonNull String url) {
        startActivity(new Intent(this, MainActivity.class)
                .putExtra("url", url)
                .putExtra(MainActivity.EXTRA_URL_IN_NEW_TAB, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        overridePendingTransition(R.anim.ext_pop_in, R.anim.ext_pop_out);
    }

    private void showMenu(View anchor) {
        if (coaraMode) {
            PopupMenu popup = new PopupMenu(this, anchor);
            popup.getMenu().add(0, 1, 0, Lang.t("ファイルからインストール"));
            popup.getMenu().add(0, 2, 1, Lang.t("ログを表示"));
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) pickPluginFile();
                else coara.showLog(null);
                return true;
            });
            popup.show();
            return;
        }
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, Lang.t("ファイルからインストール"));
        popup.getMenu().add(0, 2, 1, Lang.t("更新を確認"));
        popup.getMenu().add(0, 3, 2, Lang.t("おすすめを再読み込み"));
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    try {
                        pickXpi.launch(new String[]{"application/x-xpinstall", "application/zip",
                                "application/octet-stream"});
                    } catch (Exception e) {
                        Toast.makeText(this, Lang.t("ファイルを選ぶアプリがありません"), Toast.LENGTH_SHORT).show();
                    }
                    return true;
                case 2:
                    checkUpdates();
                    return true;
                case 3:
                    loadRecommended(true);
                    return true;
            }
            return false;
        });
        popup.show();
    }

    private void checkUpdates() {
        List<WebExtension> list = manager.getInstalled();
        if (list.isEmpty()) {
            Toast.makeText(this, Lang.t("拡張機能が入っていません"), Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, Lang.t("更新を確認しています…"), Toast.LENGTH_SHORT).show();
        final int[] left = {list.size()};
        final int[] updated = {0};
        for (WebExtension ext : list) {
            String before = ext.metaData.version;
            manager.update(ext).accept(u -> runOnUiThread(() -> {
                if (u != null && u.metaData.version != null && !u.metaData.version.equals(before)) updated[0]++;
                if (--left[0] == 0) reportUpdates(updated[0]);
            }), e -> runOnUiThread(() -> {
                if (--left[0] == 0) reportUpdates(updated[0]);
            }));
        }
    }

    private void reportUpdates(int count) {
        if (isFinishing()) return;
        Toast.makeText(this, count > 0 ? count + Lang.t(" 個の拡張機能を更新しました") : Lang.t("すべての拡張機能は最新です"),
                Toast.LENGTH_SHORT).show();
    }
}
