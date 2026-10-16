package com.airsky.browser;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.mozilla.geckoview.WebExtension;

import java.util.List;

/**
 * 拡張機能のインストール確認と、追加が終わった時のお知らせ(どちらも下から出るシート)。
 *
 * <p>Firefox のダイアログと同じ内容(権限・データ収集・シークレットでの実行)を、
 * Pixel の設定のような角丸のカードを積み重ねた見た目で出す。</p>
 */
final class ExtensionInstallSheet {

    interface Answer {
        void onAnswer(boolean accepted, boolean allowPrivate, boolean allowTechnicalData);
    }

    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

    private ExtensionInstallSheet() {}

    /** 権限の確認。答えは必ず 1 回だけ返す(外側のタップ・戻るキーはキャンセル)。 */
    static void showPrompt(@NonNull Activity activity, @NonNull WebExtension ext,
                           @NonNull String[] permissions, @NonNull String[] origins,
                           @NonNull String[] dataCollection, @NonNull Answer answer) {
        BottomSheetDialog dialog = TopSheet.adapt(activity, new BottomSheetDialog(activity));
        View sheet = LayoutInflater.from(dialog.getContext()).inflate(R.layout.sheet_extension_install, null);
        dialog.setContentView(sheet);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);

        final boolean[] answered = {false};
        final MaterialSwitch[] privateSwitch = {null};
        final MaterialSwitch[] techSwitch = {null};
        Runnable cancel = () -> {
            if (answered[0]) return;
            answered[0] = true;
            answer.onAnswer(false, false, false);
        };

        int iconPx = Math.round(56 * activity.getResources().getDisplayMetrics().density);
        ExtensionUi.loadIcon(sheet.findViewById(R.id.eiIcon), ext, iconPx);
        String name = ExtensionUi.name(ext);
        ((TextView) sheet.findViewById(R.id.eiTitle)).setText(name + Lang.t(" を追加しますか？"));
        StringBuilder sub = new StringBuilder();
        if (ext.metaData.creatorName != null && !ext.metaData.creatorName.isEmpty()) {
            sub.append(Lang.t("作成者: ")).append(ext.metaData.creatorName);
        }
        if (ext.metaData.version != null && !ext.metaData.version.isEmpty()) {
            if (sub.length() > 0) sub.append("  ·  ");
            sub.append(Lang.t("バージョン ")).append(ext.metaData.version);
        }
        TextView subtitle = sheet.findViewById(R.id.eiSubtitle);
        subtitle.setText(sub);
        subtitle.setVisibility(sub.length() > 0 ? View.VISIBLE : View.GONE);
        sheet.findViewById(R.id.eiBadge).setVisibility(ext.metaData.isRecommended ? View.VISIBLE : View.GONE);

        // 必要な権限
        LinearLayout perms = sheet.findViewById(R.id.eiPermissions);
        List<String> lines = ExtensionUi.describePermissions(permissions, origins);
        if (lines.isEmpty()) {
            ExtensionUi.addRow(perms, R.drawable.ic_ext_check, Lang.t("特別な権限は必要ありません"), null);
        } else {
            String allSites = ExtensionUi.describeOrigins(origins);
            for (String l : lines) {
                int icon = l.equals(allSites) ? R.drawable.ic_ext_globe : R.drawable.ic_ext_permission;
                ExtensionUi.addRow(perms, icon, l, null);
            }
        }
        ExtensionUi.groupChildren(perms, true);

        // データ収集
        LinearLayout data = sheet.findViewById(R.id.eiData);
        String collected = ExtensionUi.describeDataCollection(dataCollection);
        ExtensionUi.addRow(data, R.drawable.ic_ext_data,
                collected != null ? collected : Lang.t("開発者によると、この拡張機能はデータ収集を必要としません。"), null);
        ExtensionUi.groupChildren(data, true);

        // 任意の設定
        LinearLayout options = sheet.findViewById(R.id.eiOptions);
        View priv = ExtensionUi.addRow(options, R.drawable.ic_ts_incognito,
                Lang.t("シークレットモードで実行する"), Lang.t("シークレットタブでもこの拡張機能を使えるようにします"));
        privateSwitch[0] = bindSwitch(priv, false);
        if (ExtensionUi.offersTechnicalData(ext)) {
            View tech = ExtensionUi.addRow(options, R.drawable.ic_ext_data,
                    Lang.t("技術・操作のデータを開発者と共有する"), Lang.t("拡張機能の改善のために使われます"));
            techSwitch[0] = bindSwitch(tech, false);
        }
        ExtensionUi.groupChildren(options, true);

        // ストアのページ
        TextView more = sheet.findViewById(R.id.eiMore);
        String listing = ext.metaData.amoListingUrl;
        if (listing == null || listing.isEmpty()) {
            more.setVisibility(View.GONE);
        } else {
            more.setOnClickListener(v -> openInBrowser(activity, listing));
        }

        MaterialButton add = sheet.findViewById(R.id.eiAdd);
        add.setOnClickListener(v -> {
            if (answered[0]) return;
            answered[0] = true;
            answer.onAnswer(true, privateSwitch[0] != null && privateSwitch[0].isChecked(),
                    techSwitch[0] != null && techSwitch[0].isChecked());
            dialog.dismiss();
        });
        sheet.findViewById(R.id.eiCancel).setOnClickListener(v -> {
            cancel.run();
            dialog.dismiss();
        });
        ExtensionUi.padForNavigationBar(sheet.findViewById(R.id.eiRoot));
        dialog.setOnDismissListener(d -> cancel.run());
        dialog.setOnShowListener(d -> playEnter(sheet));
        dialog.show();
    }

    /** 追加が終わった時のお知らせ。 */
    static void showInstalled(@NonNull Activity activity, @NonNull WebExtension ext) {
        if (activity.isFinishing()) return;
        BottomSheetDialog dialog = TopSheet.adapt(activity, new BottomSheetDialog(activity));
        View sheet = LayoutInflater.from(dialog.getContext()).inflate(R.layout.sheet_extension_installed, null);
        dialog.setContentView(sheet);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);

        int iconPx = Math.round(56 * activity.getResources().getDisplayMetrics().density);
        ExtensionUi.loadIcon(sheet.findViewById(R.id.edIcon), ext, iconPx);
        ((TextView) sheet.findViewById(R.id.edTitle)).setText(ExtensionUi.name(ext) + Lang.t(" を追加しました"));

        boolean hasAction = ExtensionManager.get(activity).actionFor(ext, null) != null;
        ((TextView) sheet.findViewById(R.id.edMessage)).setText(hasAction
                ? Lang.t("AirSky メニューの「拡張機能」から使えます。権限とデータの設定は、拡張機能の設定でいつでも変更できます。")
                : Lang.t("権限とデータの設定は、拡張機能の設定でいつでも変更できます。"));

        sheet.findViewById(R.id.edSettings).setOnClickListener(v -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, ExtensionDetailActivity.class)
                    .putExtra(ExtensionDetailActivity.EXTRA_ID, ext.id));
            activity.overridePendingTransition(R.anim.fade, R.anim.no_animation);
        });
        sheet.findViewById(R.id.edOk).setOnClickListener(v -> dialog.dismiss());
        ExtensionUi.padForNavigationBar(sheet);
        dialog.setOnShowListener(d -> playInstalled(sheet));
        dialog.show();
    }

    // ------------------------------------------------------------------

    @NonNull
    private static MaterialSwitch bindSwitch(@NonNull View row, boolean checked) {
        MaterialSwitch sw = row.findViewById(R.id.erSwitch);
        sw.setVisibility(View.VISIBLE);
        sw.setChecked(checked);
        row.setOnClickListener(v -> sw.toggle());
        return sw;
    }

    static void openInBrowser(@NonNull Activity activity, @NonNull String url) {
        Intent i = new Intent(activity, MainActivity.class)
                .putExtra("url", url)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(i);
    }

    /** 開く時: アイコンが光の輪の中から弾むように現れ、カードが下から順にすべり込む。 */
    private static void playEnter(@NonNull View sheet) {
        float d = sheet.getResources().getDisplayMetrics().density;
        View halo = sheet.findViewById(R.id.eiHalo);
        halo.setScaleX(0.2f);
        halo.setScaleY(0.2f);
        halo.setAlpha(0f);
        halo.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(520).setInterpolator(EMPHASIZED).start();
        View icon = sheet.findViewById(R.id.eiIcon);
        icon.setScaleX(0f);
        icon.setScaleY(0f);
        icon.animate().scaleX(1f).scaleY(1f).setStartDelay(120).setDuration(420)
                .setInterpolator(new OvershootInterpolator(2.2f)).start();
        ViewGroup root = sheet.findViewById(R.id.eiRoot);
        for (int i = 3; i < root.getChildCount(); i++) {
            View c = root.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(18 * d);
            c.animate().alpha(1f).translationY(0f).setStartDelay(140L + 28L * (i - 3)).setDuration(360)
                    .setInterpolator(EMPHASIZED).start();
        }
    }

    /** 追加できた時: チェックの印がくるっと回って現れ、輪が一度だけ波紋のように広がる。 */
    private static void playInstalled(@NonNull View sheet) {
        View ring = sheet.findViewById(R.id.edRing);
        ring.setScaleX(0.6f);
        ring.setScaleY(0.6f);
        ring.setAlpha(0.9f);
        ring.animate().scaleX(1.6f).scaleY(1.6f).alpha(0f).setStartDelay(260).setDuration(700)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f)).start();
        ImageView check = sheet.findViewById(R.id.edCheck);
        check.setScaleX(0f);
        check.setScaleY(0f);
        check.setRotation(-90f);
        check.animate().scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(220).setDuration(460)
                .setInterpolator(new OvershootInterpolator(2.6f)).start();
        View icon = sheet.findViewById(R.id.edIcon);
        icon.setScaleX(0.6f);
        icon.setScaleY(0.6f);
        icon.setAlpha(0f);
        icon.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(420).setInterpolator(EMPHASIZED).start();
    }

    @Nullable
    static String safe(@Nullable String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
