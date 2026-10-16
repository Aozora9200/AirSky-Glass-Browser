package com.airsky.browser;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import org.mozilla.geckoview.WebExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 拡張機能の画面で共通に使う表示の道具(名前・アイコン・権限の説明・評価の星)。 */
final class ExtensionUi {

    private ExtensionUi() {}

    @NonNull
    static String name(@NonNull WebExtension ext) {
        String n = ext.metaData.name;
        return n != null && !n.isEmpty() ? n : ext.id;
    }

    /**
     * 拡張機能のアイコンを読み込む。読めるまでは名前の頭文字を丸い四角に描いた仮のアイコンを出す。
     */
    static void loadIcon(@NonNull ImageView view, @NonNull WebExtension ext, int sizePx) {
        view.setImageBitmap(letterIcon(view.getContext(), name(ext), sizePx));
        view.setTag(R.id.ext_icon_tag, ext.id);
        if (ext.metaData.icon == null) return;
        try {
            ext.metaData.icon.getBitmap(sizePx).accept(bmp -> {
                if (bmp == null) return;
                view.post(() -> {
                    if (ext.id.equals(view.getTag(R.id.ext_icon_tag))) view.setImageBitmap(bmp);
                });
            }, e -> { });
        } catch (Exception ignored) {
        }
    }

    /** 頭文字のアイコン(色は名前から決める)。 */
    @NonNull
    static Bitmap letterIcon(@NonNull Context c, @NonNull String name, int size) {
        size = Math.max(size, 8);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        int[] hues = {210, 260, 150, 20, 330, 180, 45};
        int hue = hues[Math.abs(name.hashCode()) % hues.length];
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.HSVToColor(new float[]{hue, 0.45f, 0.78f}));
        float r = size * 0.28f;
        canvas.drawRoundRect(0, 0, size, size, r, r, bg);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Color.WHITE);
        t.setTextSize(size * 0.48f);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextAlign(Paint.Align.CENTER);
        String letter = name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        Paint.FontMetrics fm = t.getFontMetrics();
        canvas.drawText(letter, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, t);
        return b;
    }

    // ------------------------------------------------------------------
    // まとまった角丸のカード(Pixel の設定と同じく、つながった項目は内側の角を小さくする)
    // ------------------------------------------------------------------

    static final int POS_SINGLE = 0;
    static final int POS_TOP = 1;
    static final int POS_MIDDLE = 2;
    static final int POS_BOTTOM = 3;

    static int position(int index, int count) {
        if (count <= 1) return POS_SINGLE;
        if (index == 0) return POS_TOP;
        if (index == count - 1) return POS_BOTTOM;
        return POS_MIDDLE;
    }

    /** 項目の背景(押した時の波紋つき)。 */
    static void applyGroupBackground(@NonNull View v, int position) {
        applyGroupBackground(v, position, false);
    }

    /** @param onSheet 下から出るシートの上に置く(シートの地の色と区別できる濃さにする) */
    static void applyGroupBackground(@NonNull View v, int position, boolean onSheet) {
        float d = v.getResources().getDisplayMetrics().density;
        float big = 24 * d;
        float small = 6 * d;
        float top = position == POS_SINGLE || position == POS_TOP ? big : small;
        float bottom = position == POS_SINGLE || position == POS_BOTTOM ? big : small;
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(MaterialColors.getColor(v, onSheet
                ? com.google.android.material.R.attr.colorSurfaceContainerHighest
                : com.google.android.material.R.attr.colorSurfaceContainerLow));
        shape.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
        int ripple = MaterialColors.getColor(v, com.google.android.material.R.attr.colorOnSurface);
        v.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(Color.argb(30, Color.red(ripple), Color.green(ripple), Color.blue(ripple))),
                shape, mask));
        v.setClipToOutline(true);
    }

    /** カードの 1 行(item_ext_row)を作って parent に足す。 */
    @NonNull
    static View addRow(@NonNull android.view.ViewGroup parent, int icon, @NonNull CharSequence title,
                       @Nullable CharSequence summary) {
        View row = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ext_row, parent, false);
        ImageView iv = row.findViewById(R.id.erIcon);
        if (icon != 0) iv.setImageResource(icon);
        else iv.setVisibility(View.GONE);
        ((android.widget.TextView) row.findViewById(R.id.erTitle)).setText(title);
        android.widget.TextView sum = row.findViewById(R.id.erSummary);
        if (summary != null && summary.length() > 0) {
            sum.setText(summary);
            sum.setVisibility(View.VISIBLE);
        }
        parent.addView(row);
        return row;
    }

    /** parent の子の背景を、並び順に合わせてつながった角丸にする。 */
    static void groupChildren(@NonNull android.view.ViewGroup parent) {
        groupChildren(parent, false);
    }

    /**
     * 下から出るシートの中身の下端に、ナビゲーションバーの高さの分だけ余白を足す
     * (シートは画面の下端まで広がるので、そのままだとボタンがバーに重なる)。
     */
    static void padForNavigationBar(@NonNull View content) {
        final int base = content.getPaddingBottom();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            int nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), base + nav);
            return insets;
        });
    }

    static void groupChildren(@NonNull android.view.ViewGroup parent, boolean onSheet) {
        int n = 0;
        for (int i = 0; i < parent.getChildCount(); i++) if (parent.getChildAt(i).getVisibility() != View.GONE) n++;
        int k = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View c = parent.getChildAt(i);
            if (c.getVisibility() == View.GONE) continue;
            applyGroupBackground(c, position(k++, n), onSheet);
        }
    }

    // ------------------------------------------------------------------
    // 評価
    // ------------------------------------------------------------------

    /** ★★★★☆ の形で評価を表す(半分は ⯪ の代わりに薄い星で表す)。 */
    @NonNull
    static CharSequence stars(@NonNull View v, double rating) {
        int on = MaterialColors.getColor(v, androidx.appcompat.R.attr.colorPrimary);
        int off = MaterialColors.getColor(v, com.google.android.material.R.attr.colorOutlineVariant);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        double rounded = Math.round(rating * 2) / 2.0;
        for (int i = 1; i <= 5; i++) {
            int start = sb.length();
            sb.append('★');
            int color;
            if (rounded >= i) color = on;
            else if (rounded >= i - 0.5) color = blend(on, off);
            else color = off;
            sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private static int blend(int a, int b) {
        return Color.rgb((Color.red(a) + Color.red(b)) / 2, (Color.green(a) + Color.green(b)) / 2,
                (Color.blue(a) + Color.blue(b)) / 2);
    }

    @NonNull
    static String count(int n) {
        return String.format(Locale.JAPAN, "%,d", n);
    }

    // ------------------------------------------------------------------
    // 権限の説明
    // ------------------------------------------------------------------

    private static final Map<String, String> PERMISSIONS = new HashMap<>();

    static {
        PERMISSIONS.put("bookmarks", Lang.t("ブックマークの読み取りと変更"));
        PERMISSIONS.put("browserSettings", Lang.t("ブラウザーの設定の読み取りと変更"));
        PERMISSIONS.put("browsingData", Lang.t("最近の閲覧履歴、Cookie、関連データの消去"));
        PERMISSIONS.put("clipboardRead", Lang.t("クリップボードからのデータの読み取り"));
        PERMISSIONS.put("clipboardWrite", Lang.t("クリップボードへのデータの書き込み"));
        PERMISSIONS.put("declarativeNetRequest", Lang.t("すべてのページでコンテンツをブロック"));
        PERMISSIONS.put("declarativeNetRequestFeedback", Lang.t("閲覧履歴の読み取り"));
        PERMISSIONS.put("devtools", Lang.t("開発ツールを拡張してタブのデータにアクセス"));
        PERMISSIONS.put("downloads", Lang.t("ファイルのダウンロードと、ダウンロード履歴の読み取りと変更"));
        PERMISSIONS.put("downloads.open", Lang.t("ダウンロードしたファイルを開く"));
        PERMISSIONS.put("find", Lang.t("開いているすべてのタブのテキストの読み取り"));
        PERMISSIONS.put("geolocation", Lang.t("位置情報へのアクセス"));
        PERMISSIONS.put("history", Lang.t("閲覧履歴へのアクセス"));
        PERMISSIONS.put("management", Lang.t("拡張機能の使用状況の監視とテーマの管理"));
        PERMISSIONS.put("nativeMessaging", Lang.t("他のアプリとのメッセージのやり取り"));
        PERMISSIONS.put("notifications", Lang.t("通知の表示"));
        PERMISSIONS.put("pkcs11", Lang.t("暗号化認証サービスの提供"));
        PERMISSIONS.put("privacy", Lang.t("プライバシー設定の読み取りと変更"));
        PERMISSIONS.put("proxy", Lang.t("ブラウザーのプロキシ設定の制御"));
        PERMISSIONS.put("sessions", Lang.t("最近閉じたタブへのアクセス"));
        PERMISSIONS.put("tabs", Lang.t("ブラウザーのタブへのアクセス"));
        PERMISSIONS.put("tabHide", Lang.t("ブラウザーのタブの表示/非表示"));
        PERMISSIONS.put("topSites", Lang.t("閲覧履歴へのアクセス"));
        PERMISSIONS.put("trialML", Lang.t("端末内の AI モデルのダウンロードと実行"));
        PERMISSIONS.put("userScripts", Lang.t("サイトへの未確認のサードパーティスクリプトの挿入"));
        PERMISSIONS.put("webNavigation", Lang.t("ナビゲーション中のブラウザーアクティビティへのアクセス"));
    }

    /** 確認画面には出さないが、権限の画面では説明を付ける権限。 */
    private static final Map<String, String> QUIET_PERMISSIONS = new HashMap<>();

    static {
        QUIET_PERMISSIONS.put("activeTab", Lang.t("操作した時だけ、今のタブにアクセス"));
        QUIET_PERMISSIONS.put("alarms", Lang.t("決まった時刻に処理を実行"));
        QUIET_PERMISSIONS.put("contextMenus", Lang.t("長押しメニューへの項目の追加"));
        QUIET_PERMISSIONS.put("menus", Lang.t("長押しメニューへの項目の追加"));
        QUIET_PERMISSIONS.put("cookies", Lang.t("Cookie の読み取りと変更"));
        QUIET_PERMISSIONS.put("dns", Lang.t("ホスト名の IP アドレスの確認"));
        QUIET_PERMISSIONS.put("idle", Lang.t("端末が操作されていないかの確認"));
        QUIET_PERMISSIONS.put("scripting", Lang.t("ページへのスクリプトの挿入"));
        QUIET_PERMISSIONS.put("storage", Lang.t("データの保存"));
        QUIET_PERMISSIONS.put("unlimitedStorage", Lang.t("容量の制限なくデータを保存"));
        QUIET_PERMISSIONS.put("webRequest", Lang.t("通信の監視"));
        QUIET_PERMISSIONS.put("webRequestBlocking", Lang.t("通信のブロックと書き換え"));
        QUIET_PERMISSIONS.put("webRequestFilterResponse", Lang.t("通信内容の書き換え"));
        QUIET_PERMISSIONS.put("search", Lang.t("検索エンジンの使用"));
        QUIET_PERMISSIONS.put("theme", Lang.t("見た目のテーマの変更"));
    }

    /** インストール確認画面用の説明(Firefox と同じく、説明のある権限だけを並べる)。 */
    @NonNull
    static List<String> describePermissions(@Nullable String[] permissions, @Nullable String[] origins) {
        Set<String> lines = new LinkedHashSet<>();
        String hosts = describeOrigins(origins);
        if (hosts != null) lines.add(hosts);
        if (permissions != null) {
            for (String p : permissions) {
                String s = PERMISSIONS.get(p);
                if (s != null) lines.add(s);
            }
        }
        return new ArrayList<>(lines);
    }

    /** 権限 1 つの説明(権限の画面用。分からない権限は名前をそのまま出す)。 */
    @NonNull
    static String describePermission(@NonNull String permission) {
        String s = PERMISSIONS.get(permission);
        if (s == null) s = QUIET_PERMISSIONS.get(permission);
        return s != null ? s : permission;
    }

    /** サイトへのアクセスの説明。 */
    @Nullable
    static String describeOrigins(@Nullable String[] origins) {
        if (origins == null || origins.length == 0) return null;
        List<String> hosts = new ArrayList<>();
        for (String o : origins) {
            if (isAllUrls(o)) return Lang.t("すべてのウェブサイトの保存されたデータへのアクセス");
            String h = hostOf(o);
            if (h != null && !hosts.contains(h)) hosts.add(h);
        }
        if (hosts.isEmpty()) return null;
        Collections.sort(hosts);
        if (hosts.size() == 1) return hosts.get(0) + Lang.t(" のサイトのデータへのアクセス");
        if (hosts.size() <= 3) return String.join(Lang.t("、"), hosts) + Lang.t(" のサイトのデータへのアクセス");
        return hosts.get(0) + Lang.t(" など ") + hosts.size() + Lang.t(" 個のサイトのデータへのアクセス");
    }

    static boolean isAllUrls(@NonNull String origin) {
        return origin.equals("<all_urls>") || origin.equals("*://*/*") || origin.equals("http://*/*")
                || origin.equals("https://*/*") || origin.equals("*://*/");
    }

    @Nullable
    static String hostOf(@NonNull String origin) {
        int s = origin.indexOf("://");
        String rest = s >= 0 ? origin.substring(s + 3) : origin;
        int slash = rest.indexOf('/');
        if (slash >= 0) rest = rest.substring(0, slash);
        if (rest.startsWith("*.")) rest = rest.substring(2);
        return rest.isEmpty() || rest.equals("*") ? null : rest;
    }

    // ------------------------------------------------------------------
    // データ収集
    // ------------------------------------------------------------------

    private static final Map<String, String> DATA = new HashMap<>();

    static {
        DATA.put("authenticationInfo", Lang.t("認証情報"));
        DATA.put("bookmarksInfo", Lang.t("ブックマーク"));
        DATA.put("browsingActivity", Lang.t("閲覧の履歴"));
        DATA.put("financialAndPaymentInfo", Lang.t("金融・支払いの情報"));
        DATA.put("healthInfo", Lang.t("健康の情報"));
        DATA.put("locationInfo", Lang.t("位置情報"));
        DATA.put("personalCommunications", Lang.t("個人的なやり取り"));
        DATA.put("personallyIdentifyingInfo", Lang.t("個人を特定できる情報"));
        DATA.put("searchTerms", Lang.t("検索した言葉"));
        DATA.put("websiteActivity", Lang.t("サイトでの操作"));
        DATA.put("websiteContent", Lang.t("サイトの内容"));
        DATA.put("technicalAndInteraction", Lang.t("技術・操作のデータ"));
    }

    /** 必須のデータ収集の説明。「必要としない」なら null。 */
    @Nullable
    static String describeDataCollection(@Nullable String[] data) {
        if (data == null || data.length == 0) return null;
        List<String> names = new ArrayList<>();
        for (String d : data) {
            if ("none".equals(d)) continue;
            String s = DATA.get(d);
            names.add(s != null ? s : d);
        }
        if (names.isEmpty()) return null;
        return Lang.t("開発者によると、この拡張機能は次のデータを収集します: ") + String.join(Lang.t("、"), names);
    }

    static boolean offersTechnicalData(@NonNull WebExtension ext) {
        String[] opt = ext.metaData.optionalDataCollectionPermissions;
        if (opt == null) return false;
        for (String s : opt) if ("technicalAndInteraction".equals(s)) return true;
        return false;
    }
}
