package com.airsky.browser;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.ProgressDelegate.SecurityInformation;
import org.mozilla.geckoview.WebRequestError;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * リンクの長押しメニューの「ページをプレビュー」。タブを開かずにシートの中でリンク先を見せる
 * (「このページについて」のシートと同じ形)。
 *
 * <p>見出しにはリンク先のファビコン・サイト名・URL を出す。メインのブラウザと同じく接続の安全性
 * (HTTPS か・証明書が信頼できるか)を確かめて鍵/警告のマークで示し、押すとサイト情報を開く。
 * 証明書のエラーや危険なサイトはメインのブラウザと同じエラーページで読み込みを止める。</p>
 */
final class PagePreviewSheet {

    interface Host extends SiteInfoSheet.Host {
        /** 読み込みエラーの時に出すページ(メインのブラウザと同じもの)。 */
        @Nullable String buildErrorPage(@Nullable String url, @NonNull WebRequestError error);
        /** プレビューのページの証明書を見せる。 */
        void showCertificateDialogFor(@NonNull GeckoTabView tab);
        /** 分かっていればそのサイトのファビコン。 */
        @Nullable Bitmap faviconFor(@NonNull String url);
    }

    /** 接続の状態。 */
    private enum Security { SECURE, MIXED, CERT_ERROR, INSECURE, LOCAL, UNKNOWN }

    private final Activity activity;
    private final Host host;
    private final HeaderDragSheetDialog dialog;
    private final View sheet;
    private final GeckoTabView viewer;
    private final ImageView icon;
    private final TextView title, urlView, warningText;
    private final ImageView securityIcon;
    private final View warning;
    @Nullable private Bitmap favicon;
    private boolean hasFavicon;
    private final Handler main = new Handler(Looper.getMainLooper());

    private PagePreviewSheet(Activity activity, String url, boolean privateMode, @Nullable String contextId, Host host) {
        this.activity = activity;
        this.host = host;
        dialog = TopSheet.adapt(activity, new HeaderDragSheetDialog(activity));
        sheet = LayoutInflater.from(dialog.getContext()).inflate(R.layout.sheet_page_preview, null);
        int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.9f);
        dialog.setContentView(sheet, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        dialog.setCanceledOnTouchOutside(true);
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        // ページのスクロールとぶつからないよう、ドラッグで閉じられるのは見出しの部分だけ
        behavior.setHideable(true);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        icon = sheet.findViewById(R.id.pp_icon);
        title = sheet.findViewById(R.id.pp_title);
        urlView = sheet.findViewById(R.id.pp_url);
        securityIcon = sheet.findViewById(R.id.pp_security_icon);
        warning = sheet.findViewById(R.id.pp_warning);
        warningText = sheet.findViewById(R.id.pp_warning_text);

        LinearProgressIndicator progress = sheet.findViewById(R.id.pp_progress);
        FrameLayout content = sheet.findViewById(R.id.pp_content);
        dialog.setDragBoundary(content);
        // メインのブラウザと同じ区画(シークレットならその一時プロファイル)で読み込む
        viewer = new GeckoTabView(activity, privateMode, contextId, null);
        content.addView(viewer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        favicon = host.faviconFor(url);
        bindHeader(url, null);

        viewer.setListener(new GeckoTabView.Listener() {
            @Override
            public void onProgressChanged(GeckoTabView tab, int p) {
                progress.setProgressCompat(p, true);
                progress.setVisibility(p >= 100 ? View.INVISIBLE : View.VISIBLE);
            }

            @Override
            public void onPageStarted(GeckoTabView tab, String u) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(GeckoTabView tab, String u, boolean success) {
                progress.setVisibility(View.INVISIBLE);
                bindHeader(currentUrl(url), tab.getTitle());
            }

            @Override
            public void onLocationChanged(GeckoTabView tab, String u) {
                // プレビューの中でページを移動した: 見出しを新しいページに合わせる
                if (u == null || u.isEmpty() || u.startsWith("data:") || "about:blank".equals(u)) return;
                if (!sameHost(u, urlView.getText().toString())) {
                    favicon = host.faviconFor(u);
                    hasFavicon = false;
                }
                bindHeader(u, null);
            }

            @Override
            public void onTitleChanged(GeckoTabView tab, String t) {
                bindHeader(currentUrl(url), t);
            }

            @Override
            public void onSecurityChanged(GeckoTabView tab) {
                bindHeader(currentUrl(url), tab.getTitle());
            }

            @Override
            public void onPageInfo(GeckoTabView tab, @Nullable String iconUrl) {
                loadFavicon(iconUrl);
            }

            @Nullable
            @Override
            public GeckoSession onNewWindow(GeckoTabView tab, String u) {
                // 新しいウィンドウ(target=_blank など)は、プレビューを閉じて新しいタブで開く
                if (u != null && !u.isEmpty() && !"about:blank".equals(u)) {
                    dialog.dismiss();
                    host.openInNewTab(u);
                }
                return null;
            }

            @Nullable
            @Override
            public String onLoadError(GeckoTabView tab, @Nullable String u, WebRequestError error) {
                progress.setVisibility(View.INVISIBLE);
                return host.buildErrorPage(u, error);
            }
        });

        sheet.findViewById(R.id.pp_close).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.pp_open).setOnClickListener(v -> {
            String current = currentUrl(url);
            dialog.dismiss();
            host.openInNewTab(current);
        });
        View.OnClickListener siteInfo = v -> showSiteInfo(url);
        sheet.findViewById(R.id.pp_site).setOnClickListener(siteInfo);
        warning.setOnClickListener(siteInfo);

        dialog.setOnShowListener(d -> viewer.onResume());
        dialog.setOnDismissListener(d -> viewer.destroy());
        viewer.loadUrl(url);
    }

    static BottomSheetDialog show(@NonNull Activity activity, @NonNull String url, boolean privateMode,
                                  @Nullable String contextId, @NonNull Host host) {
        PagePreviewSheet s = new PagePreviewSheet(activity, url, privateMode, contextId, host);
        s.dialog.show();
        return s.dialog;
    }

    private String currentUrl(String fallback) {
        String u = viewer.getUrl();
        return u != null && !u.isEmpty() && !u.startsWith("data:") && !"about:blank".equals(u) ? u : fallback;
    }

    /** 見出し(ファビコン・サイト名・URL・接続の安全性)を描き直す。 */
    private void bindHeader(@NonNull String url, @Nullable String pageTitle) {
        if (favicon != null) {
            icon.setImageBitmap(favicon);
            icon.setImageTintList(null);
        } else {
            icon.setImageResource(R.drawable.ic_si_globe);
            icon.setImageTintList(ColorStateList.valueOf(
                    MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnSurfaceVariant)));
        }
        String site = LinkMenuSheet.siteName(url);
        title.setText(pageTitle != null && !pageTitle.trim().isEmpty() ? pageTitle.trim() : site);
        urlView.setText(url);

        Security s = resolve(url);
        int normal = MaterialColors.getColor(sheet, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int error = MaterialColors.getColor(sheet, androidx.appcompat.R.attr.colorError);
        String message = null;
        switch (s) {
            case SECURE:
                securityIcon.setImageResource(R.drawable.ic_si_lock);
                securityIcon.setImageTintList(ColorStateList.valueOf(normal));
                securityIcon.setContentDescription(Lang.t("保護された通信"));
                break;
            case MIXED:
                securityIcon.setImageResource(R.drawable.ic_si_warning);
                securityIcon.setImageTintList(ColorStateList.valueOf(error));
                message = Lang.t("このページは完全には保護されていません。パスワードなどの機密情報は入力しないでください。");
                break;
            case CERT_ERROR:
                securityIcon.setImageResource(R.drawable.ic_si_warning);
                securityIcon.setImageTintList(ColorStateList.valueOf(error));
                message = Lang.t("このサイトの証明書を確認できません。保護されていない通信です。");
                break;
            case INSECURE:
                securityIcon.setImageResource(R.drawable.ic_si_warning);
                securityIcon.setImageTintList(ColorStateList.valueOf(error));
                message = Lang.t("この接続は保護されていません (HTTPS ではありません)。パスワードやクレジットカード情報は入力しないでください。");
                break;
            default:
                securityIcon.setImageResource(R.drawable.ic_si_info);
                securityIcon.setImageTintList(ColorStateList.valueOf(normal));
                break;
        }
        warning.setVisibility(message != null ? View.VISIBLE : View.GONE);
        if (message != null) warningText.setText(message);
    }

    private Security resolve(@NonNull String url) {
        String scheme = Uri.parse(url).getScheme();
        scheme = scheme != null ? scheme.toLowerCase(Locale.ROOT) : "";
        if ("http".equals(scheme)) return Security.INSECURE;
        if (!"https".equals(scheme)) return Security.LOCAL;
        SecurityInformation info = viewer.getSecurityInformation();
        if (info == null) return Security.UNKNOWN;
        if (info.isException) return Security.CERT_ERROR;
        if (!info.isSecure) return Security.INSECURE;
        if (info.mixedModeActive == SecurityInformation.CONTENT_LOADED
                || info.mixedModePassive == SecurityInformation.CONTENT_LOADED) return Security.MIXED;
        return Security.SECURE;
    }

    /** メインのブラウザと同じサイト情報(接続の詳細・証明書・Cookie)を、プレビューのページについて開く。 */
    private void showSiteInfo(@NonNull String fallbackUrl) {
        String url = currentUrl(fallbackUrl);
        SiteInfoSheet.show(activity, viewer, url, favicon, false, new SiteInfoSheet.Host() {
            @Override
            public void showCertificateDialog() {
                host.showCertificateDialogFor(viewer);
            }

            @Override
            public void openInNewTab(@NonNull String target) {
                dialog.dismiss();
                host.openInNewTab(target);
            }
        });
    }

    private static boolean sameHost(String a, String b) {
        String ha = Uri.parse(a).getHost();
        String hb = Uri.parse(b).getHost();
        return ha != null && ha.equalsIgnoreCase(hb);
    }

    /** ページが知らせてきたファビコンを読み込む(分かっているものが無い時だけ)。 */
    private void loadFavicon(@Nullable String iconUrl) {
        if (hasFavicon || iconUrl == null
                || !(iconUrl.startsWith("https://") || iconUrl.startsWith("http://"))) return;
        hasFavicon = true;
        new Thread(() -> {
            Bitmap bm = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(iconUrl).openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                try (InputStream in = c.getInputStream()) {
                    bm = BitmapFactory.decodeStream(in);
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
            final Bitmap result = bm;
            main.post(() -> {
                if (result == null || !dialog.isShowing()) return;
                favicon = result;
                bindHeader(currentUrl(urlView.getText().toString()), viewer.getTitle());
            });
        }, "preview-favicon").start();
    }
}
