package com.airsky.browser;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;

/**
 * 拡張機能のポップアップ(ツールバーボタンを押すと出るパネル)を、下から出るシートの中に表示する。
 * シートの中はページなので、指でのスクロールはページに任せ、シート自体はドラッグで動かさない。
 */
final class ExtensionPopupSheet {

    private ExtensionPopupSheet() {}

    static void show(@NonNull Activity activity, @NonNull WebExtension ext, @NonNull WebExtension.Action action,
                     @NonNull GeckoSession session) {
        if (activity.isFinishing()) {
            session.close();
            return;
        }
        BottomSheetDialog dialog = TopSheet.adapt(activity, new BottomSheetDialog(activity));
        View sheet = LayoutInflater.from(dialog.getContext()).inflate(R.layout.sheet_extension_popup, null);
        dialog.setContentView(sheet);
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setDraggable(false);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        int iconPx = Math.round(28 * activity.getResources().getDisplayMetrics().density);
        ExtensionUi.loadIcon(sheet.findViewById(R.id.epIcon), ext, iconPx);
        String title = action.title != null && !action.title.isEmpty() ? action.title : ExtensionUi.name(ext);
        ((TextView) sheet.findViewById(R.id.epTitle)).setText(title);

        // ページの高さ: 画面の 7 割(ポップアップの多くは縦長)
        FrameLayout holder = sheet.findViewById(R.id.epContent);
        int h = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.7f);
        ViewGroup.LayoutParams lp = holder.getLayoutParams();
        lp.height = h;
        holder.setLayoutParams(lp);

        GeckoView view = new GeckoView(activity);
        view.setSaveEnabled(false);
        holder.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        view.setSession(session);

        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onCloseRequest(@NonNull GeckoSession s) {
                dialog.dismiss();
            }
        });
        // ポップアップ内のふつうのリンクは、ブラウザの新しいタブで開いてシートを閉じる
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Nullable
            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(@NonNull GeckoSession s, @NonNull LoadRequest request) {
                String uri = request.uri;
                if (uri != null && (uri.startsWith("http://") || uri.startsWith("https://"))) {
                    openInNewTab(activity, uri);
                    dialog.dismiss();
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW);
            }

            @Nullable
            @Override
            public GeckoResult<GeckoSession> onNewSession(@NonNull GeckoSession s, @NonNull String uri) {
                openInNewTab(activity, uri);
                dialog.dismiss();
                return null;
            }
        });

        ImageView settings = sheet.findViewById(R.id.epSettings);
        String options = ext.metaData.optionsPageUrl;
        settings.setVisibility(options != null && !options.isEmpty() ? View.VISIBLE : View.GONE);
        settings.setOnClickListener(v -> {
            dialog.dismiss();
            ExtensionManager.get(activity).openOptionsPage(ext);
        });
        sheet.findViewById(R.id.epClose).setOnClickListener(v -> dialog.dismiss());
        ExtensionUi.padForNavigationBar(sheet);
        dialog.setOnDismissListener(d -> {
            view.releaseSession();
            session.close();
        });
        dialog.show();
    }

    private static void openInNewTab(@NonNull Activity activity, @NonNull String url) {
        activity.startActivity(new Intent(activity, MainActivity.class)
                .putExtra("url", url)
                .putExtra(MainActivity.EXTRA_URL_IN_NEW_TAB, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
}
