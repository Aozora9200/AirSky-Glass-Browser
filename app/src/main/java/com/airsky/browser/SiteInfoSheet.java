package com.airsky.browser;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.appcompat.app.AppCompatDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.mozilla.geckoview.GeckoSession.ProgressDelegate.SecurityInformation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * URL バー横のファビコンをタップした時に出すページ情報。
 * 画面の上に浮かぶカードで、接続の状態を色付きのカード(安全なら主色、危険なら警告の色)で見せ、
 * その下に「Cookie とサイトデータ」「このページについて」のタイルを並べる。
 * 詳しい内容はカードの中でページを切り替えて見せる。
 */
final class SiteInfoSheet {

    interface Host {
        /** 従来の証明書情報ダイアログを開く。 */
        void showCertificateDialog();
        /** URL を新しいタブで開く。 */
        void openInNewTab(@NonNull String url);
    }

    /** 接続の状態。 */
    private enum State { SECURE, MIXED, CERT_ERROR, INSECURE, INTERNAL, LOCAL, UNKNOWN }

    private final Activity activity;
    private final GeckoTabView tab;
    private final String url;
    @Nullable private final String host;
    private final Host callbacks;

    private final AppCompatDialog dialog;
    private final View sheet;
    private final Deque<View> backStack = new ArrayDeque<>();
    private View currentPage;
    private final State state;

    static AppCompatDialog show(@NonNull Activity activity, @NonNull GeckoTabView tab, @NonNull String url,
                           @Nullable Bitmap favicon, boolean internal, @NonNull Host callbacks) {
        SiteInfoSheet s = new SiteInfoSheet(activity, tab, url, favicon, internal, callbacks);
        s.dialog.show();
        s.playEnter();
        return s.dialog;
    }

    private SiteInfoSheet(Activity activity, GeckoTabView tab, String url, @Nullable Bitmap favicon,
                          boolean internal, Host callbacks) {
        this.activity = activity;
        this.tab = tab;
        this.url = url;
        this.callbacks = callbacks;
        Uri uri = Uri.parse(url);
        this.host = uri.getHost();
        this.state = resolveState(uri, internal);

        // 画面の上に浮かぶカードにする(上から縦に広がって下りてくる)
        dialog = new AppCompatDialog(activity, R.style.Theme_Aozora_SiteInfoPanel);
        sheet = LayoutInflater.from(dialog.getContext()).inflate(R.layout.sheet_site_info, null);
        dialog.setContentView(sheet);
        dialog.setCanceledOnTouchOutside(true);
        setUpTopWindow();
        // 下位ページでは戻るキーでひとつ前のページへ
        dialog.getOnBackPressedDispatcher().addCallback(new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!popPage()) dialog.dismiss();
            }
        });
        // Cookie の設定画面から戻ってきたら、例外の切り替えを最新にする
        sheet.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
            if (hasFocus) refreshCookiesPage();
        });

        // カードの外側(余白)を押しても閉じる。カードの中を押しても閉じない
        sheet.setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.si_card).setOnClickListener(v -> { });
        sheet.findViewById(R.id.si_close).setOnClickListener(v -> dialog.dismiss());

        bindHeader(favicon);
        bindMain();
        bindSecurity();
        bindCookies();

        currentPage = sheet.findViewById(R.id.si_page_main);
    }

    /** ダイアログのウィンドウを画面の上端に横いっぱいで置き、ステータスバー(切り欠き)の下から中身を始める。 */
    private void setUpTopWindow() {
        android.view.Window window = dialog.getWindow();
        if (window == null) return;
        window.setGravity(android.view.Gravity.TOP);
        window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        // ブラウザ画面が全画面表示なら、パネルを出してもステータスバーを出さない
        if ((activity.getWindow().getAttributes().flags
                & android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            android.view.WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false);

        // カードはステータスバー(切り欠き)の下から始める
        final View root = sheet;
        final int baseTop = root.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(sheet, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.displayCutout());
            root.setPadding(root.getPaddingLeft(), baseTop + bars.top,
                    root.getPaddingRight(), root.getPaddingBottom());
            return insets;
        });
    }

    // ------------------------------------------------------------------
    // 状態
    // ------------------------------------------------------------------

    private State resolveState(Uri uri, boolean internal) {
        if (internal) return State.INTERNAL;
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        if ("http".equals(scheme)) return State.INSECURE;
        if (!"https".equals(scheme)) return State.LOCAL;
        SecurityInformation info = tab.getSecurityInformation();
        if (info == null) return State.UNKNOWN;
        if (info.isException) return State.CERT_ERROR;
        if (!info.isSecure) return State.INSECURE;
        if (info.mixedModeActive == SecurityInformation.CONTENT_LOADED
                || info.mixedModePassive == SecurityInformation.CONTENT_LOADED) return State.MIXED;
        return State.SECURE;
    }

    private boolean isWebPage() {
        return state != State.INTERNAL && state != State.LOCAL && host != null;
    }

    private boolean isWarning() {
        return state == State.INSECURE || state == State.CERT_ERROR || state == State.MIXED;
    }

    // ------------------------------------------------------------------
    // 見出し
    // ------------------------------------------------------------------

    private void bindHeader(@Nullable Bitmap favicon) {
        ImageView icon = sheet.findViewById(R.id.si_favicon);
        TextView hostView = sheet.findViewById(R.id.si_host);

        if (state == State.INTERNAL) {
            icon.setImageResource(R.mipmap.ic_aozora);
            icon.setImageTintList(null);
            // アプリのアイコンは丸いっぱいに出す
            android.view.ViewGroup.LayoutParams lp = icon.getLayoutParams();
            lp.width = lp.height = Math.round(44 * activity.getResources().getDisplayMetrics().density);
            icon.setLayoutParams(lp);
            hostView.setText("AirSky");
            return;
        }
        if (favicon != null) {
            icon.setImageBitmap(favicon);
            icon.setImageTintList(null);
        }
        hostView.setText(host != null ? stripWww(host) : url);
        // サイト名の長押しで URL をコピー
        hostView.setOnLongClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("URL", url));
            Toast.makeText(activity, Lang.t("URL をコピーしました"), Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private static String stripWww(String h) {
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    // ------------------------------------------------------------------
    // トップ
    // ------------------------------------------------------------------

    private void bindMain() {
        View row = sheet.findViewById(R.id.si_row_connection);
        ImageView icon = sheet.findViewById(R.id.si_connection_icon);
        TextView title = sheet.findViewById(R.id.si_connection_title);

        boolean clickable = true;
        switch (state) {
            case SECURE:
                icon.setImageResource(R.drawable.ic_si_lock);
                title.setText(Lang.t("保護された接続"));
                break;
            case MIXED:
                icon.setImageResource(R.drawable.ic_si_warning);
                title.setText(Lang.t("この接続は完全には保護されていません"));
                break;
            case CERT_ERROR:
                icon.setImageResource(R.drawable.ic_si_warning);
                title.setText(Lang.t("保護されていない通信"));
                break;
            case INSECURE:
                icon.setImageResource(R.drawable.ic_si_warning);
                title.setText(Lang.t("この接続は保護されていません"));
                break;
            case INTERNAL:
                icon.setImageResource(R.drawable.ic_si_info);
                title.setText(Lang.t("安全な AirSky のページを表示しています"));
                clickable = false;
                break;
            case LOCAL:
                icon.setImageResource(R.drawable.ic_si_info);
                title.setText(Lang.t("このページはネットワークを使わずに表示されています"));
                clickable = false;
                break;
            default:
                icon.setImageResource(R.drawable.ic_si_info);
                title.setText(Lang.t("接続を確認しています"));
                clickable = false;
                break;
        }
        // 状態でカードの色を変える: 安全 → 主色 / 危険 → 警告色 / その他 → 落ち着いた色
        int containerAttr, onAttr;
        if (isWarning()) {
            containerAttr = com.google.android.material.R.attr.colorErrorContainer;
            onAttr = com.google.android.material.R.attr.colorOnErrorContainer;
        } else if (state == State.SECURE) {
            containerAttr = com.google.android.material.R.attr.colorPrimaryContainer;
            onAttr = com.google.android.material.R.attr.colorOnPrimaryContainer;
        } else {
            containerAttr = com.google.android.material.R.attr.colorSecondaryContainer;
            onAttr = com.google.android.material.R.attr.colorOnSecondaryContainer;
        }
        int container = MaterialColors.getColor(row, containerAttr);
        int on = MaterialColors.getColor(row, onAttr);
        row.setBackgroundTintList(ColorStateList.valueOf(container));
        ((TextView) sheet.findViewById(R.id.si_host)).setTextColor(on);
        title.setTextColor(on);
        icon.setImageTintList(ColorStateList.valueOf(on));
        ImageView chevron = sheet.findViewById(R.id.si_connection_chevron);
        chevron.setImageTintList(ColorStateList.valueOf(on));
        if (clickable) {
            row.setOnClickListener(v -> pushPage(R.id.si_page_security));
        } else {
            row.setClickable(false);
            row.setFocusable(false);
            row.setForeground(null);
            chevron.setVisibility(View.GONE);
        }

        int web = isWebPage() ? View.VISIBLE : View.GONE;
        sheet.findViewById(R.id.si_tiles).setVisibility(web);
        sheet.findViewById(R.id.si_divider_cookies).setVisibility(web);
        sheet.findViewById(R.id.si_row_cookies).setVisibility(web);
        sheet.findViewById(R.id.si_divider_about).setVisibility(web);
        sheet.findViewById(R.id.si_row_about).setVisibility(web);
        sheet.findViewById(R.id.si_row_cookies).setOnClickListener(v -> pushPage(R.id.si_page_cookies));
        sheet.findViewById(R.id.si_row_about).setOnClickListener(v -> {
            dialog.dismiss();
            AboutThisPageSheet.show(activity, url, tab.isPrivateMode(), callbacks);
        });
    }

    // ------------------------------------------------------------------
    // 接続
    // ------------------------------------------------------------------

    private void bindSecurity() {
        View page = sheet.findViewById(R.id.si_page_security);
        ImageView icon = page.findViewById(R.id.si_sec_icon);
        TextView text = page.findViewById(R.id.si_sec_text);
        TextView text2 = page.findViewById(R.id.si_sec_text2);
        TextView text3 = page.findViewById(R.id.si_sec_text3);
        View certLink = page.findViewById(R.id.si_sec_cert);
        String name = host != null ? host : url;

        X509Certificate cert = tab.getX509Certificate();
        String issuer = cert != null ? nameOf(cert.getIssuerX500Principal().getName()) : null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
        String expiry = cert != null ? Lang.t("証明書の有効期限: ") + sdf.format(cert.getNotAfter()) : null;

        String title;
        switch (state) {
            case SECURE:
                title = Lang.t("この接続は保護されています");
                icon.setImageResource(R.drawable.ic_si_lock);
                text.setText(issuer != null
                        ? Lang.t("AirSky で、このウェブサイトの証明書が ") + issuer + Lang.t(" 発行のものであると確認されました。")
                        : Lang.t("AirSky で、このウェブサイトの証明書が確認されました。"));
                setOptionalText(text2, stripWww(name) + Lang.t(" への接続は暗号化されています。"));
                setOptionalText(text3, expiry);
                break;
            case MIXED:
                title = Lang.t("この接続は完全には保護されていません");
                icon.setImageResource(R.drawable.ic_si_warning);
                text.setText(Lang.t("このサイトには、暗号化されずに読み込まれた画像やスクリプトなどが含まれています。"
                        + "パスワードやクレジットカード情報などの機密情報はこのサイトに入力しないでください。"));
                setOptionalText(text2, issuer != null ? Lang.t("このウェブサイトの証明書は ") + issuer + Lang.t(" 発行のものです。") : null);
                setOptionalText(text3, expiry);
                break;
            case CERT_ERROR:
                title = Lang.t("保護されていない通信");
                icon.setImageResource(R.drawable.ic_si_warning);
                text.setText(Lang.t("このサイトの証明書を検証できなかったため、警告を無視して表示しています。"
                        + "攻撃者がパスワードやメッセージ、クレジットカード情報などを盗もうとしている可能性があります。"));
                setOptionalText(text2, null);
                setOptionalText(text3, expiry);
                break;
            default:
                title = Lang.t("この接続は保護されていません");
                icon.setImageResource(R.drawable.ic_si_warning);
                text.setText(Lang.t("パスワードやクレジットカード情報などの機密情報はこのサイトに入力しないでください。"
                        + "悪意のあるユーザーに盗まれる可能性があります。"));
                setOptionalText(text2, null);
                setOptionalText(text3, null);
                break;
        }
        bindBackBar(page.findViewById(R.id.si_security_bar), title);
        if (isWarning()) {
            icon.setImageTintList(ColorStateList.valueOf(
                    MaterialColors.getColor(icon, androidx.appcompat.R.attr.colorError)));
        }

        if (cert == null || state == State.INSECURE) {
            certLink.setVisibility(View.GONE);
        } else {
            ((TextView) certLink).getPaint().setUnderlineText(true);
            certLink.setOnClickListener(v -> {
                dialog.dismiss();
                callbacks.showCertificateDialog();
            });
        }
    }

    private static void setOptionalText(TextView view, @Nullable String text) {
        view.setText(text);
        view.setVisibility(text != null ? View.VISIBLE : View.GONE);
    }

    /** 識別名(RFC 2253)から共通名(なければ組織名)を取り出す。 */
    private static String nameOf(String dn) {
        String cn = null, o = null;
        // 「\,」でエスケープされたカンマでは区切らない
        for (String part : dn.split("(?<!\\\\)[,+]")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String type = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim().replace("\\", "");
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            if ("CN".equalsIgnoreCase(type) && cn == null) cn = value;
            else if ("O".equalsIgnoreCase(type) && o == null) o = value;
        }
        if (cn != null) return cn;
        if (o != null) return o;
        return dn;
    }

    // ------------------------------------------------------------------
    // Cookie とサイトデータ
    // ------------------------------------------------------------------

    private void bindCookies() {
        View page = sheet.findViewById(R.id.si_page_cookies);
        bindBackBar(page.findViewById(R.id.si_cookies_bar), Lang.t("Cookie とサイトデータ"));
        if (host == null) return;

        // 説明文の「設定」から Cookie の設定画面へ
        TextView text = page.findViewById(R.id.si_cookie_text);
        String body = Lang.t("Cookie と他のサイトデータは、ログインや広告のパーソナライズなどでユーザーを覚えておくために"
                + "使用されます。すべてのサイトの Cookie を管理するには、設定をご覧ください。");
        SpannableString span = new SpannableString(body);
        int start = body.lastIndexOf(Lang.t("設定"));
        final int linkColor = MaterialColors.getColor(text, androidx.appcompat.R.attr.colorPrimary);
        span.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                CookieSettingsActivity.open(activity);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(linkColor);
                ds.setUnderlineText(true);
            }
        }, start, start + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setText(span);
        text.setMovementMethod(LinkMovementMethod.getInstance());

        page.findViewById(R.id.si_cookie_delete).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(Lang.t("Cookie を削除しますか?"))
                        .setMessage(host + Lang.t(" の Cookie などのサイトデータが削除されます"))
                        .setNegativeButton(Lang.t("キャンセル"), null)
                        .setPositiveButton(Lang.t("削除"), (d, w) -> CookiePolicy.clearSiteData(activity, host)
                                .accept(r -> activity.runOnUiThread(() ->
                                                Toast.makeText(activity, Lang.t("サイトデータを削除しました"), Toast.LENGTH_SHORT).show()),
                                        e -> activity.runOnUiThread(() ->
                                                Toast.makeText(activity, Lang.t("削除できませんでした"), Toast.LENGTH_SHORT).show())))
                        .show());

        page.findViewById(R.id.si_row_site_exception).setOnClickListener(v -> {
            boolean enable = !CookiePolicy.isException(activity, host);
            if (enable) CookiePolicy.addException(activity, host);
            else CookiePolicy.removeExceptionsFor(activity, host);
            refreshCookiesPage();
            // 新しい設定でページを読み直す
            tab.syncCookieException(true);
        });
        refreshCookiesPage();
    }

    private void refreshCookiesPage() {
        if (host == null) return;
        View page = sheet.findViewById(R.id.si_page_cookies);
        // サードパーティ Cookie をブロックしている時だけ、サイトごとに許可を切り替えられる
        boolean blocking = CookiePolicy.isCookiesAccepted(activity) && !CookiePolicy.isThirdPartyAllowed(activity);
        page.findViewById(R.id.si_row_site_exception).setVisibility(blocking ? View.VISIBLE : View.GONE);
        boolean exception = CookiePolicy.isException(activity, host);
        ((MaterialSwitch) page.findViewById(R.id.si_site_exception_switch)).setChecked(exception);
        ((TextView) page.findViewById(R.id.si_site_exception_text)).setText(exception
                ? Lang.t("このサイトでは許可しています")
                : Lang.t("ブロックしています"));
    }

    // ------------------------------------------------------------------
    // 開く時の動き
    // ------------------------------------------------------------------

    /** カードが下りてくるのに合わせて、状態のカード → タイルの順に中身を浮かび上がらせる。 */
    private void playEnter() {
        float d = activity.getResources().getDisplayMetrics().density;
        android.view.animation.PathInterpolator emphasized =
                new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
        View hero = sheet.findViewById(R.id.si_row_connection);
        hero.setAlpha(0f);
        hero.setTranslationY(-10 * d);
        hero.animate().alpha(1f).translationY(0f).setStartDelay(90).setDuration(360)
                .setInterpolator(emphasized).start();
        View badge = sheet.findViewById(R.id.si_connection_icon);
        badge.setScaleX(0f);
        badge.setScaleY(0f);
        badge.animate().scaleX(1f).scaleY(1f).setStartDelay(260).setDuration(360)
                .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
        View[] tiles = {sheet.findViewById(R.id.si_row_cookies), sheet.findViewById(R.id.si_row_about)};
        for (int i = 0; i < tiles.length; i++) {
            tiles[i].setAlpha(0f);
            tiles[i].setScaleX(0.9f);
            tiles[i].setScaleY(0.9f);
            tiles[i].animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(170L + 60L * i)
                    .setDuration(340).setInterpolator(emphasized).start();
        }
    }

    // ------------------------------------------------------------------
    // ページの切り替え
    // ------------------------------------------------------------------

    private void bindBackBar(View bar, String title) {
        ((TextView) bar.findViewById(R.id.si_back_title)).setText(title);
        bar.findViewById(R.id.si_back_button).setOnClickListener(v -> popPage());
    }

    private void pushPage(int id) {
        View next = sheet.findViewById(id);
        if (next == currentPage) return;
        if (id == R.id.si_page_cookies) refreshCookiesPage();
        backStack.push(currentPage);
        switchPage(next, true);
    }

    private boolean popPage() {
        if (backStack.isEmpty()) return false;
        switchPage(backStack.pop(), false);
        return true;
    }

    private void switchPage(View next, boolean forward) {
        View prev = currentPage;
        currentPage = next;
        float shift = 24 * activity.getResources().getDisplayMetrics().density;
        prev.animate().cancel();
        prev.setVisibility(View.GONE);
        next.setVisibility(View.VISIBLE);
        next.setAlpha(0f);
        next.setTranslationX(forward ? shift : -shift);
        next.animate().alpha(1f).translationX(0f).setDuration(200)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    // ------------------------------------------------------------------
    // このページについて
    // ------------------------------------------------------------------

    /**
     * Google の「この結果について」ビューア(searchviewer)の URL を作る。
     * svid はページの URL を入れた protobuf を URL セーフな Base64 にしたもの。
     */
    @NonNull
    static String aboutThisPageUrl(@NonNull String pageUrl) {
        int hash = pageUrl.indexOf('#');
        String target = hash >= 0 ? pageUrl.substring(0, hash) : pageUrl;
        byte[] u = target.getBytes(StandardCharsets.UTF_8);
        byte[] item = ("atritem-" + target).getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        writeBytesField(inner, 2, item);
        writeBytesField(inner, 4, u);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarint(out, 1 << 3);
        writeVarint(out, 16);
        writeBytesField(out, 2, inner.toByteArray());
        writeVarint(out, 3 << 3);
        writeVarint(out, 47);

        String svid = Base64.encodeToString(out.toByteArray(),
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        return "https://www.google.com/searchviewer/47?svid=" + svid;
    }

    private static void writeBytesField(ByteArrayOutputStream out, int field, byte[] value) {
        writeVarint(out, (field << 3) | 2);
        writeVarint(out, value.length);
        out.write(value, 0, value.length);
    }

    private static void writeVarint(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }
}
