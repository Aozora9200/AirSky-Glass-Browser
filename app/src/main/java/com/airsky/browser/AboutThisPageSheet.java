package com.airsky.browser;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.mozilla.geckoview.GeckoSession;

/**
 * 「このページについて」(Google の searchviewer)をタブを開かずにシートの中で見せる。
 * ビューアの中から別のページへ移動しようとしたら、シートを閉じて新しいタブで開く。
 */
final class AboutThisPageSheet {

    private static final String VIEWER_PREFIX = "https://www.google.com/searchviewer";

    private AboutThisPageSheet() {}

    static BottomSheetDialog show(@NonNull Activity activity, @NonNull String pageUrl, boolean privateMode,
                                  @NonNull SiteInfoSheet.Host callbacks) {
        final String viewerUrl = SiteInfoSheet.aboutThisPageUrl(pageUrl);

        HeaderDragSheetDialog dialog = TopSheet.adapt(activity, new HeaderDragSheetDialog(activity));
        View sheet = LayoutInflater.from(dialog.getContext()).inflate(R.layout.sheet_about_page, null);
        int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.9f);
        dialog.setContentView(sheet, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        dialog.setCanceledOnTouchOutside(true);
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        // ページのスクロールとぶつからないよう、ドラッグで閉じられるのは見出しの部分だけ
        behavior.setHideable(true);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        LinearProgressIndicator progress = sheet.findViewById(R.id.ap_progress);
        FrameLayout content = sheet.findViewById(R.id.ap_content);
        dialog.setDragBoundary(content);
        GeckoTabView viewer = new GeckoTabView(activity, privateMode);
        content.addView(viewer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        viewer.setListener(new GeckoTabView.Listener() {
            @Override
            public void onProgressChanged(GeckoTabView tab, int p) {
                progress.setProgressCompat(p, true);
                progress.setVisibility(p >= 100 ? View.INVISIBLE : View.VISIBLE);
            }

            @Override
            public void onPageFinished(GeckoTabView tab, String url, boolean success) {
                progress.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onLocationChanged(GeckoTabView tab, String url) {
                if (isViewerUrl(url)) return;
                // 「移動」など、ビューアの外へのリンクは新しいタブで開く
                tab.stopLoading();
                dialog.dismiss();
                callbacks.openInNewTab(url);
            }

            @Nullable
            @Override
            public GeckoSession onNewWindow(GeckoTabView tab, String url) {
                if (url != null && !url.isEmpty() && !"about:blank".equals(url)) {
                    dialog.dismiss();
                    callbacks.openInNewTab(url);
                }
                return null;
            }
        });

        sheet.findViewById(R.id.ap_close).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.ap_open).setOnClickListener(v -> {
            String current = viewer.getUrl();
            dialog.dismiss();
            callbacks.openInNewTab(current != null && isViewerUrl(current) ? current : viewerUrl);
        });

        dialog.setOnShowListener(d -> viewer.onResume());
        dialog.setOnDismissListener(d -> viewer.destroy());
        viewer.loadUrl(viewerUrl);
        dialog.show();
        return dialog;
    }

    /** searchviewer の中(リダイレクトや読み込み前の空ページを含む)か。 */
    private static boolean isViewerUrl(@Nullable String url) {
        return url == null || url.isEmpty() || "about:blank".equals(url)
                || url.startsWith(VIEWER_PREFIX)
                || url.startsWith("https://www.google.com/search?")
                || url.startsWith("https://accounts.google.com/")
                || url.startsWith("https://consent.google.com/");
    }
}
