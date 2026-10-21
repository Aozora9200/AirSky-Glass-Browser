package com.airsky.browser;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.airsky.browser.coara.ParsedPlugin;
import com.airsky.browser.coara.PluginInfo;
import com.airsky.browser.coara.PluginInstallException;
import com.airsky.browser.coara.PluginInstaller;
import com.airsky.browser.coara.PluginManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.mozilla.geckoview.GeckoSession;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 拡張機能の管理画面の「Coara Browser 拡張機能」の一覧。
 *
 * <p>Coara Browser のプラグイン(plugin.xml を含む .zip)・ユーザースクリプト・ユーザースタイルの
 * 追加・有効/無効・削除と、今のページで使えるプラグインの操作(メニューのコマンド)を並べる。</p>
 */
final class CoaraExtensionsSection implements PluginManager.Listener {

    private static final int LOG_VIEW_CHARS = 60_000;

    private final Activity activity;
    private final LinearLayout sections;
    private final Runnable rebind;
    private final Runnable pickFile;
    private final PluginManager manager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    CoaraExtensionsSection(@NonNull Activity activity, @NonNull LinearLayout sections,
                           @NonNull Runnable rebind, @NonNull Runnable pickFile) {
        this.activity = activity;
        this.sections = sections;
        this.rebind = rebind;
        this.pickFile = pickFile;
        this.manager = CoaraPlugins.get(activity).manager();
    }

    void start() {
        manager.addListener(this);
    }

    void stop() {
        manager.removeListener(this);
        io.shutdown();
    }

    @Override
    public void onPluginsChanged() {
        if (!activity.isFinishing()) rebind.run();
    }

    @Override
    public void onPluginAutoDisabled(PluginInfo info, String reason) {
    }

    // ------------------------------------------------------------------
    // 一覧
    // ------------------------------------------------------------------

    @NonNull
    String summary() {
        List<PluginInfo> list = manager.list();
        int enabled = 0;
        for (PluginInfo p : list) if (p.enabled) enabled++;
        return list.isEmpty()
                ? Lang.t("Coara Browser 用の拡張機能はまだ入っていません。ファイルから追加できます。")
                : list.size() + Lang.t(" 個の Coara Browser 拡張機能が入っています(有効 ") + enabled + Lang.t(" 個)");
    }

    void bind() {
        List<PluginInfo> enabled = new ArrayList<>();
        List<PluginInfo> disabled = new ArrayList<>();
        for (PluginInfo p : manager.list()) {
            if (p.enabled) enabled.add(p);
            else disabled.add(p);
        }
        addCommands();
        if (!enabled.isEmpty()) addSection(Lang.t("有効"), enabled);
        if (!disabled.isEmpty()) addSection(Lang.t("無効"), disabled);
        if (enabled.isEmpty() && disabled.isEmpty()) {
            addHeader(Lang.t("Coara Browser 拡張機能"));
            LinearLayout group = newGroup();
            View row = ExtensionUi.addRow(group, R.drawable.ic_ext_file, Lang.t("ファイルから追加"),
                    Lang.t(".zip(plugin.xml)・.user.js・.user.css を選んでください"));
            row.setOnClickListener(v -> pickFile.run());
            ExtensionUi.groupChildren(group);
        }
    }

    /** 今のページで使える、プラグインの操作(GM_registerMenuCommand)。押すとページに戻って実行する。 */
    private void addCommands() {
        CoaraPlugins plugins = CoaraPlugins.get(activity);
        GeckoSession session = ExtensionManager.get(activity).currentSession();
        List<CoaraPlugins.Command> commands = plugins.commandsFor(session);
        if (commands.isEmpty()) return;
        addHeader(Lang.t("このページで使える操作"));
        LinearLayout group = newGroup();
        for (CoaraPlugins.Command c : commands) {
            PluginInfo p = manager.get(c.pluginId);
            View row = ExtensionUi.addRow(group, R.drawable.ic_ext_open, c.title, p != null ? p.title : null);
            row.setOnClickListener(v -> {
                if (plugins.runCommand(session, c.commandId)) {
                    activity.finish();
                } else {
                    Toast.makeText(activity, Lang.t("ページを読み込み直してからお試しください"), Toast.LENGTH_SHORT).show();
                }
            });
        }
        ExtensionUi.groupChildren(group);
    }

    private void addSection(String title, List<PluginInfo> list) {
        addHeader(title);
        LinearLayout group = newGroup();
        for (PluginInfo p : list) group.addView(row(group, p));
        ExtensionUi.groupChildren(group);
    }

    private View row(ViewGroup parent, PluginInfo p) {
        View row = LayoutInflater.from(activity).inflate(R.layout.item_extension, parent, false);
        int iconPx = Math.round(34 * activity.getResources().getDisplayMetrics().density);
        ImageView icon = row.findViewById(R.id.eIcon);
        Bitmap bmp = loadIcon(p, iconPx);
        icon.setImageBitmap(bmp != null ? bmp : ExtensionUi.letterIcon(activity, p.title, iconPx));
        icon.setAlpha(p.enabled ? 1f : 0.45f);
        ((TextView) row.findViewById(R.id.eName)).setText(p.title);
        TextView sum = row.findViewById(R.id.eSummary);
        sum.setText(p.description);
        sum.setVisibility(p.description.isEmpty() ? View.GONE : View.VISIBLE);
        ((TextView) row.findViewById(R.id.eMeta)).setText(metaFor(p));

        MaterialSwitch sw = row.findViewById(R.id.eSwitch);
        sw.setVisibility(View.VISIBLE);
        sw.setChecked(p.enabled);
        sw.setContentDescription(p.title + Lang.t("を有効にする"));
        sw.setOnCheckedChangeListener((b, checked) -> io.execute(() -> manager.setEnabled(p.id, checked)));
        row.setOnClickListener(v -> showDetail(p.id));
        return row;
    }

    private String metaFor(PluginInfo p) {
        if (!p.enabled && !p.disabledReason.isEmpty() && !"手動で無効化".equals(p.disabledReason)) {
            return Lang.t("自動で無効になりました: ") + p.disabledReason;
        }
        StringBuilder sb = new StringBuilder();
        if (!p.version.isEmpty()) sb.append(Lang.t("バージョン ")).append(p.version);
        if (!p.author.isEmpty()) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(p.author);
        }
        if (sb.length() > 0) sb.append("  ·  ");
        sb.append(formatName(p.format));
        return sb.toString();
    }

    private static String formatName(String format) {
        switch (format) {
            case PluginInfo.FORMAT_USERSCRIPT:
                return Lang.t("ユーザースクリプト");
            case PluginInfo.FORMAT_USERSTYLE:
                return Lang.t("ユーザースタイル");
            case PluginInfo.FORMAT_WEBEXT:
                return Lang.t("WebExtension(簡易)");
            default:
                return Lang.t("プラグイン");
        }
    }

    @Nullable
    private Bitmap loadIcon(PluginInfo p, int size) {
        File f = manager.iconFile(p);
        if (f == null) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= size && o.outHeight / (sample * 2) >= size) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getPath(), d);
        } catch (Exception e) {
            return null;
        }
    }

    private void addHeader(String title) {
        TextView h = new TextView(activity, null, 0, R.style.ExtSectionHeader);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        float d = activity.getResources().getDisplayMetrics().density;
        lp.topMargin = Math.round(24 * d);
        lp.bottomMargin = Math.round(8 * d);
        h.setText(title);
        sections.addView(h, lp);
    }

    private LinearLayout newGroup() {
        LinearLayout g = new LinearLayout(activity);
        g.setOrientation(LinearLayout.VERTICAL);
        sections.addView(g, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return g;
    }

    // ------------------------------------------------------------------
    // 詳細
    // ------------------------------------------------------------------

    private void showDetail(String id) {
        PluginInfo p = manager.get(id);
        if (p == null) return;
        StringBuilder sb = new StringBuilder();
        if (!p.description.isEmpty()) sb.append(p.description).append("\n\n");
        line(sb, Lang.t("種類"), formatName(p.format));
        if (!p.version.isEmpty()) line(sb, Lang.t("バージョン"), p.version);
        if (!p.author.isEmpty()) line(sb, Lang.t("作者"), p.author);
        line(sb, Lang.t("対象のサイト"), p.matches.isEmpty() ? Lang.t("すべてのサイト") : TextUtils.join(", ", p.matches));
        line(sb, Lang.t("権限"), permissions(p.permissions));
        line(sb, Lang.t("保存データ"), manager.storageCount(p.id) + Lang.t(" 件"));
        if (!p.lastError.isEmpty()) line(sb, Lang.t("最後のエラー"), p.lastError);
        if (!p.enabled && !p.disabledReason.isEmpty()) line(sb, Lang.t("無効の理由"), p.disabledReason);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(p.title)
                .setMessage(sb.toString().trim())
                .setPositiveButton(Lang.t("閉じる"), null)
                .setNegativeButton(Lang.t("削除"), (d, w) -> confirmDelete(p))
                .setNeutralButton(Lang.t("その他"), (d, w) -> showMore(p))
                .show();
    }

    private void showMore(PluginInfo p) {
        String[] items = {Lang.t("ログを表示"), Lang.t("保存データを消去")};
        new MaterialAlertDialogBuilder(activity)
                .setTitle(p.title)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        showLog(p.id);
                    } else {
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(Lang.t("保存データを消去しますか?"))
                                .setMessage(Lang.t("「") + p.title + Lang.t("」の設定や記録が消えます。"))
                                .setPositiveButton(Lang.t("消去"), (d2, w2) -> io.execute(() -> {
                                    manager.clearData(p.id);
                                    CoaraPlugins.get(activity).onStoreCleared(p.id);
                                }))
                                .setNegativeButton(Lang.t("キャンセル"), null)
                                .show();
                    }
                })
                .show();
    }

    private static void line(StringBuilder sb, String label, String value) {
        sb.append(label).append(Lang.t(": ")).append(value).append('\n');
    }

    private static String permissions(List<String> perms) {
        if (perms.isEmpty()) return Lang.t("なし");
        List<String> out = new ArrayList<>();
        for (String perm : perms) {
            switch (perm) {
                case PluginInfo.PERM_NOTIFICATION:
                    out.add(Lang.t("通知を表示"));
                    break;
                case PluginInfo.PERM_CLIPBOARD:
                    out.add(Lang.t("クリップボードにコピー"));
                    break;
                case PluginInfo.PERM_XHR:
                    out.add(Lang.t("他のサイトとの通信"));
                    break;
                case PluginInfo.PERM_OPENTAB:
                    out.add(Lang.t("新しいタブを開く"));
                    break;
                default:
                    out.add(perm);
            }
        }
        return TextUtils.join(Lang.t("、"), out);
    }

    private void confirmDelete(PluginInfo p) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(Lang.t("「") + p.title + Lang.t("」を削除しますか?"))
                .setMessage(Lang.t("保存データも一緒に削除されます。"))
                .setPositiveButton(Lang.t("削除"), (d, w) -> io.execute(() -> {
                    boolean ok = manager.delete(p.id);
                    main.post(() -> Toast.makeText(activity,
                            ok ? Lang.t("削除しました") : Lang.t("削除できませんでした"), Toast.LENGTH_SHORT).show());
                }))
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    /** ログ(id が null なら全部)。 */
    void showLog(@Nullable String id) {
        io.execute(() -> {
            String text = id == null ? manager.readLog(LOG_VIEW_CHARS) : manager.readLogFor(id, LOG_VIEW_CHARS);
            main.post(() -> {
                if (activity.isFinishing()) return;
                TextView tv = new TextView(activity);
                tv.setText(text == null || text.isEmpty() ? Lang.t("ログはありません") : text);
                tv.setTypeface(Typeface.MONOSPACE);
                tv.setTextSize(12);
                tv.setTextIsSelectable(true);
                int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density);
                tv.setPadding(pad, pad / 2, pad, pad / 2);
                ScrollView scroll = new ScrollView(activity);
                scroll.addView(tv);
                MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(activity)
                        .setTitle(Lang.t("ログ"))
                        .setView(scroll)
                        .setPositiveButton(Lang.t("閉じる"), null);
                if (id == null) {
                    b.setNeutralButton(Lang.t("消去"), (d, w) -> io.execute(manager::clearLog));
                }
                b.show();
            });
        });
    }

    // ------------------------------------------------------------------
    // 追加
    // ------------------------------------------------------------------

    /** ファイル(.zip / .user.js / .user.css / .xml)を読んで、追加の確認を出す。 */
    void installFromUri(@NonNull Uri uri) {
        String name = displayName(uri);
        Toast.makeText(activity, Lang.t("拡張機能を読み込んでいます…"), Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            ParsedPlugin parsed = null;
            String error = null;
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException("open failed");
                parsed = PluginInstaller.parse(in, name, null);
            } catch (PluginInstallException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = Lang.t("ファイルを読み込めませんでした");
            }
            ParsedPlugin result = parsed;
            String message = error;
            main.post(() -> {
                if (activity.isFinishing()) return;
                if (result == null) {
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(Lang.t("インストールに失敗しました"))
                            .setMessage(message != null ? message : Lang.t("この拡張機能は AirSky に対応していません。"))
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    confirmInstall(result);
                }
            });
        });
    }

    private void confirmInstall(ParsedPlugin parsed) {
        PluginInfo p = parsed.info;
        StringBuilder sb = new StringBuilder();
        if (!p.description.isEmpty()) sb.append(p.description).append("\n\n");
        line(sb, Lang.t("種類"), formatName(p.format));
        if (!p.version.isEmpty()) line(sb, Lang.t("バージョン"), p.version);
        if (!p.author.isEmpty()) line(sb, Lang.t("作者"), p.author);
        line(sb, Lang.t("対象のサイト"), p.matches.isEmpty() ? Lang.t("すべてのサイト") : TextUtils.join(", ", p.matches));
        line(sb, Lang.t("権限"), permissions(p.permissions));
        if (!parsed.warnings.isEmpty()) {
            sb.append('\n').append(Lang.t("注意:")).append('\n');
            for (String w : parsed.warnings) sb.append(Lang.t("・")).append(w).append('\n');
        }
        boolean update = manager.get(p.id) != null && !parsed.fallbackTitle;
        new MaterialAlertDialogBuilder(activity)
                .setTitle((update ? Lang.t("更新しますか?: ") : Lang.t("追加しますか?: ")) + p.title)
                .setMessage(sb.toString().trim())
                .setPositiveButton(update ? Lang.t("更新") : Lang.t("追加"), (d, w) -> io.execute(() -> {
                    String msg;
                    try {
                        PluginManager.InstallOutcome outcome = manager.install(parsed);
                        msg = (outcome.updated ? Lang.t("更新しました: ") : Lang.t("追加しました: ")) + outcome.info.title;
                    } catch (PluginInstallException e) {
                        msg = e.getMessage();
                    }
                    String shown = msg;
                    main.post(() -> Toast.makeText(activity, shown, Toast.LENGTH_LONG).show());
                }))
                .setNegativeButton(Lang.t("キャンセル"), null)
                .show();
    }

    @Nullable
    private String displayName(@NonNull Uri uri) {
        try (Cursor c = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last;
    }
}
