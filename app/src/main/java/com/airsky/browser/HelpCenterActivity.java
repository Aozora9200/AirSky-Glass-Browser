package com.airsky.browser;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ヘルプ。Chrome のヘルプと同じく「検索 → 基本の操作 → よくある質問 → その他」の順に並べ、
 * 見た目は Pixel の設定アプリ風(大きな見出し・丸いカード・色の付いたアイコン)にしている。
 *
 * <p>「基本の操作」は Lottie のアニメーション(assets/help/*.json、tools/gen_help_lottie.py で生成)で
 * 実際の操作を見せる。押すと手順とヒントがシートで開く。</p>
 */
public class HelpCenterActivity extends AppCompatActivity {

    private static final int REQUEST_LEGACY_HELP = 1;
    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);

    /** 基本の操作(アニメーション付き)。 */
    private static final class Tutorial {
        final String anim, title, summary, tip;
        final String[] steps;

        Tutorial(String anim, String title, String summary, String[] steps, String tip) {
            this.anim = anim;
            this.title = title;
            this.summary = summary;
            this.steps = steps;
            this.tip = tip;
        }
    }

    /** よくある質問。 */
    private static final class Faq {
        @DrawableRes final int icon;
        final String question, answer;

        Faq(@DrawableRes int icon, String question, String answer) {
            this.icon = icon;
            this.question = question;
            this.answer = answer;
        }
    }

    private static final Tutorial[] TUTORIALS = {
            new Tutorial("help/tabs.json", Lang.t("タブを切り替える"), Lang.t("複数のページを行き来する"),
                    new String[]{
                            Lang.t("下のバーにある、数字の入った四角(タブボタン)をタップします。"),
                            Lang.t("開いているタブが一覧で表示されます。"),
                            Lang.t("見たいタブをタップすると、そのページに切り替わります。")},
                    Lang.t("タブを横にスワイプすると閉じられます。たくさんのタブは「タブグループ」にまとめると探しやすくなります。")),
            new Tutorial("help/search.json", Lang.t("検索・URL を入力する"), Lang.t("アドレスバーの使い方"),
                    new String[]{
                            Lang.t("画面上のアドレスバーをタップします。"),
                            Lang.t("調べたい言葉、または「example.com」のようなアドレスを入力します。"),
                            Lang.t("下に出てくる候補をタップするか、キーボードの決定キーで開きます。")},
                    Lang.t("言葉を入れると検索、アドレスを入れるとそのサイトが直接開きます。")),
            new Tutorial("help/refresh.json", Lang.t("引っ張って更新"), Lang.t("ページを読み込み直す"),
                    new String[]{
                            Lang.t("ページの一番上までスクロールします。"),
                            Lang.t("そのまま指で下へ引っ張ります。"),
                            Lang.t("くるくる回る印が出たら指を離すと、ページが読み込み直されます。")},
                    Lang.t("下のバーの再読み込みボタン(円を描く矢印)でも同じことができます。読み込み中は × に変わり、押すと中止できます。")),
            new Tutorial("help/scroll.json", Lang.t("バーが消えたときは"), Lang.t("スクロールで隠れるバー"),
                    new String[]{
                            Lang.t("ページを下へ読み進めると、画面を広く使うためにアドレスバーが自動で隠れます。"),
                            Lang.t("少し上へスクロールすると、バーがまた出てきます。"),
                            Lang.t("画面右下の△ボタンを押すことで表示させることもできます。")},
                    Lang.t("下部メニューバーも隠したい場合は、設定 →「全般」→「下部メニューバーを自動的に隠す」を使います。")),
            new Tutorial("help/longpress.json", Lang.t("長押しメニュー"), Lang.t("リンクや画像をもっと便利に"),
                    new String[]{
                            Lang.t("リンクや画像の上で、指を少しの間押し続けます。"),
                            Lang.t("下からメニューが出てきます。"),
                            Lang.t("「リンク先を新しいタブで開く」「リンクをコピー」などを選べます。")},
                    Lang.t("共有シートの QR コードも、長押しすると画像としてコピーできます。")),
            new Tutorial("help/back.json", Lang.t("前のページに戻る"), Lang.t("戻る操作のいろいろ"),
                    new String[]{
                            Lang.t("画面の端から内側へスワイプする(ジェスチャー操作)か、端末の戻るボタンを押します。"),
                            Lang.t("下のバーの「<」ボタンでも 1 つ前のページに戻れます。"),
                            Lang.t("戻りすぎたときは「>」ボタンで進めます。")},
                    Lang.t("戻れない時は、リンクが新しいタブで開いている可能性があります。タブボタンから元のタブを選んでください。")),
    };

    private static final Faq[] FAQS = {
            new Faq(R.drawable.ic_ts_search, Lang.t("アドレスバーが消えてしまった"),
                    Lang.t("ページをスクロールすると、画面を広く使うためにバーが隠れます。少し上へスクロールするか、"
                            + "ページの一番上まで戻ると再び表示されます。")),
            new Faq(R.drawable.ic_lm_open_tab, Lang.t("開いていたタブがなくなった"),
                    Lang.t("しばらく使っていないタブは「非アクティブなタブ」としてまとめられることがあります。タブの一覧の"
                            + "一番上を確認してください。期間は 設定 →「非アクティブなタブ」で変えられます。")),
            new Faq(R.drawable.ic_lm_download, Lang.t("ダウンロードしたファイルが見つからない"),
                    Lang.t("AirSky メニュー →「ダウンロード」に一覧があります。端末の「ダウンロード」フォルダにも保存されています。")),
            new Faq(R.drawable.ic_ts_incognito, Lang.t("シークレットモードでは何が残らない？"),
                    Lang.t("閲覧履歴・Cookie・サイトのデータは、シークレットのタブをすべて閉じると消去されます。"
                            + "ダウンロードしたファイルやブックマークは残ります。")),
            new Faq(R.drawable.ic_nt_video, Lang.t("動画を閉じると音も止まってしまう"),
                    Lang.t("設定 →「バックグラウンド再生」をオンにすると、ホーム画面に戻ったり画面を消したりしても再生が続きます。"
                            + "通知から一時停止・停止もできます。")),
            new Faq(R.drawable.ic_lm_open_tab, Lang.t("リンクを押すとアプリが開いてしまう"),
                    Lang.t("サイトによっては、対応するアプリ(YouTube、地図など)で開くように作られています。"
                            + "ブラウザで開きたい場合は、設定 →「帯域幅の管理」→「URLスキーム」をオフにしてください。")),
            new Faq(R.drawable.ic_si_info, Lang.t("ページが崩れる・古いまま表示される"),
                    Lang.t("まずは引っ張って更新してみてください。直らない場合は 設定 →「プライバシーとセキュリティ」→"
                            + "「キャッシュを消去」を試してください。")),
            new Faq(R.drawable.ic_omni_search, Lang.t("文字が小さくて読みにくい"),
                    Lang.t("2 本の指で広げると拡大できます(ピンチアウト)。設定 →「全般」→「ズームボタンの表示」をオンにすると、"
                            + "ボタンでも拡大・縮小できます。")),
            new Faq(R.drawable.ic_si_lock, Lang.t("「この接続は安全ではありません」と出る"),
                    Lang.t("サイトの証明書に問題があるときの警告です。銀行やショッピングなど大切な情報を入力するサイトでは、"
                            + "先へ進まずに戻ることをおすすめします。")),
    };

    private float dp;
    private int onSurface, onVariant, container, containerHigh, primary, onPrimaryContainer, primaryContainer;

    private final List<View> tutorialCards = new ArrayList<>();
    private final List<View> faqRows = new ArrayList<>();
    private View tutorialSection, faqSection, otherSection;
    private TextView noResult;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_center);
        TouchEffectView.attach(getWindow());
        dp = getResources().getDisplayMetrics().density;
        resolveColors();

        LinearLayout content = findViewById(R.id.tool_content);
        content.addView(buildSearch());

        noResult = text(Lang.t("一致するヘルプが見つかりませんでした。別の言葉で検索してみてください。"), 14, onVariant);
        noResult.setGravity(Gravity.CENTER);
        noResult.setPadding(px(24), px(32), px(24), px(32));
        noResult.setVisibility(View.GONE);
        content.addView(noResult);

        tutorialSection = buildTutorials();
        content.addView(tutorialSection);
        faqSection = buildFaqs();
        content.addView(faqSection);
        otherSection = buildOthers();
        content.addView(otherSection);

        ToolChrome.setup(this, Lang.t("ヘルプ"));
        ToolChrome.playEnter(content);
    }

    private void resolveColors() {
        onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        onVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY);
        container = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLowest, Color.WHITE);
        containerHigh = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.LTGRAY);
        primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0xFF0B57D0);
        primaryContainer = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, 0xFFD3E3FD);
        onPrimaryContainer = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFF041E49);
    }

    // ------------------------------------------------------------------
    // 検索
    // ------------------------------------------------------------------

    private View buildSearch() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(px(18), 0, px(10), 0);
        bar.setBackground(round(containerHigh, px(28)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_ts_search);
        icon.setImageTintList(ColorStateList.valueOf(onVariant));
        bar.addView(icon, new LinearLayout.LayoutParams(px(22), px(22)));
        EditText field = new EditText(this);
        field.setHint(Lang.t("ヘルプを検索"));
        field.setHintTextColor(onVariant);
        field.setTextColor(onSurface);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        field.setBackground(null);
        field.setSingleLine(true);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        flp.setMarginStart(px(12));
        bar.addView(field, flp);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                filter(s.toString());
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56));
        lp.bottomMargin = px(8);
        bar.setLayoutParams(lp);
        return bar;
    }

    private void filter(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        ViewGroup root = findViewById(R.id.tool_content);
        TransitionManager.beginDelayedTransition(root, new AutoTransition().setDuration(200));
        int tutorialHits = 0;
        for (int i = 0; i < tutorialCards.size(); i++) {
            Tutorial t = TUTORIALS[i];
            boolean hit = q.isEmpty() || matches(q, t.title, t.summary, t.tip, String.join("", t.steps));
            tutorialCards.get(i).setVisibility(hit ? View.VISIBLE : View.GONE);
            if (hit) tutorialHits++;
        }
        int faqHits = 0;
        for (int i = 0; i < faqRows.size(); i++) {
            Faq f = FAQS[i];
            boolean hit = q.isEmpty() || matches(q, f.question, f.answer);
            faqRows.get(i).setVisibility(hit ? View.VISIBLE : View.GONE);
            if (hit) faqHits++;
        }
        tutorialSection.setVisibility(tutorialHits > 0 ? View.VISIBLE : View.GONE);
        faqSection.setVisibility(faqHits > 0 ? View.VISIBLE : View.GONE);
        otherSection.setVisibility(q.isEmpty() ? View.VISIBLE : View.GONE);
        noResult.setVisibility(tutorialHits == 0 && faqHits == 0 ? View.VISIBLE : View.GONE);
    }

    private static boolean matches(String q, String... texts) {
        for (String t : texts) {
            if (t != null && t.toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 基本の操作(アニメーションのカードを横に並べる)
    // ------------------------------------------------------------------

    private View buildTutorials() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(sectionTitle(Lang.t("基本の操作")));

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(0, 0, 0, 0);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (Tutorial t : TUTORIALS) {
            View card = tutorialCard(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(216), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(px(10));
            row.addView(card, lp);
            tutorialCards.add(card);
        }
        scroller.addView(row);
        section.addView(scroller);
        return section;
    }

    private View tutorialCard(Tutorial t) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(container, px(24)));
        card.setClipToOutline(true);
        card.setForeground(getDrawable(resolve(android.R.attr.selectableItemBackground)));
        card.setPadding(px(8), px(8), px(8), px(14));

        LottieAnimationView anim = lottie(t.anim);
        anim.setBackground(round(primaryContainer, px(18)));
        card.addView(anim, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(134)));

        TextView title = text(t.title, 16, onSurface);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(px(8), px(12), px(8), 0);
        card.addView(title);
        TextView sub = text(t.summary, 13, onVariant);
        sub.setPadding(px(8), px(2), px(8), 0);
        card.addView(sub);
        card.setOnClickListener(v -> showTutorial(t));
        card.setContentDescription(t.title);
        return card;
    }

    private LottieAnimationView lottie(String asset) {
        LottieAnimationView anim = new LottieAnimationView(this);
        anim.setAnimation(asset);
        anim.setRepeatCount(LottieDrawable.INFINITE);
        anim.setScaleType(ImageView.ScaleType.FIT_CENTER);
        anim.playAnimation();
        anim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return anim;
    }

    /** 手順とヒントをシートで出す。 */
    private void showTutorial(Tutorial t) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(20), px(8), px(20), px(28));

        View handle = new View(this);
        handle.setBackground(round(onVariant, px(2)));
        handle.setAlpha(0.4f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(px(32), px(4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = px(16);
        root.addView(handle, hlp);

        LottieAnimationView anim = lottie(t.anim);
        anim.setBackground(round(primaryContainer, px(28)));
        root.addView(anim, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(220)));

        TextView title = text(t.title, 24, onSurface);
        title.setPadding(px(4), px(20), px(4), px(12));
        root.addView(title);

        for (int i = 0; i < t.steps.length; i++) {
            root.addView(step(i + 1, t.steps[i]));
        }

        // ヒント
        LinearLayout tip = new LinearLayout(this);
        tip.setOrientation(LinearLayout.HORIZONTAL);
        tip.setBackground(round(containerHigh, px(20)));
        tip.setPadding(px(16), px(14), px(16), px(14));
        ImageView bulb = new ImageView(this);
        bulb.setImageResource(R.drawable.ic_help_tip);
        bulb.setImageTintList(ColorStateList.valueOf(primary));
        tip.addView(bulb, new LinearLayout.LayoutParams(px(20), px(20)));
        TextView tipText = text(t.tip, 14, onSurface);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(px(12));
        tip.addView(tipText, tlp);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipLp.topMargin = px(12);
        root.addView(tip, tipLp);

        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(this);
        scroll.addView(root);
        dialog.setContentView(scroll);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.show();
        // 手順が順に浮かび上がる
        for (int i = 2; i < root.getChildCount(); i++) {
            View c = root.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(px(16));
            c.animate().alpha(1f).translationY(0f).setStartDelay(60L * i).setDuration(360)
                    .setInterpolator(EMPHASIZED).start();
        }
    }

    private View step(int n, String body) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, px(6), 0, px(6));
        TextView num = text(String.valueOf(n), 14, onPrimaryContainer);
        num.setGravity(Gravity.CENTER);
        num.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        num.setBackground(round(primaryContainer, px(14)));
        row.addView(num, new LinearLayout.LayoutParams(px(28), px(28)));
        TextView t = text(body, 15, onSurface);
        t.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(px(14));
        lp.topMargin = px(3);
        row.addView(t, lp);
        return row;
    }

    // ------------------------------------------------------------------
    // よくある質問(押すと答えが開く)
    // ------------------------------------------------------------------

    private View buildFaqs() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(sectionTitle(Lang.t("つまずきやすいポイント")));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < FAQS.length; i++) {
            View row = faqRow(FAQS[i], i == 0, i == FAQS.length - 1);
            list.addView(row);
            faqRows.add(row);
        }
        section.addView(list);
        return section;
    }

    /** Pixel の設定アプリのように、つながったカードの最初と最後だけ角を大きく丸める。 */
    private View faqRow(Faq f, boolean first, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        float big = px(24), small = px(6);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(container);
        bg.setCornerRadii(new float[]{first ? big : small, first ? big : small, first ? big : small, first ? big : small,
                last ? big : small, last ? big : small, last ? big : small, last ? big : small});
        row.setBackground(bg);
        row.setClipToOutline(true);
        row.setForeground(getDrawable(resolve(android.R.attr.selectableItemBackground)));
        row.setPadding(px(16), px(16), px(16), px(16));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = px(3);
        row.setLayoutParams(rlp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(tonalIcon(f.icon));
        TextView q = text(f.question, 16, onSurface);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        qlp.setMarginStart(px(16));
        qlp.setMarginEnd(px(8));
        head.addView(q, qlp);
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_ext_expand);
        chevron.setImageTintList(ColorStateList.valueOf(onVariant));
        head.addView(chevron, new LinearLayout.LayoutParams(px(22), px(22)));
        row.addView(head);

        TextView a = text(f.answer, 14.5f, onVariant);
        a.setLineSpacing(0, 1.2f);
        a.setVisibility(View.GONE);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.setMarginStart(px(56));
        alp.topMargin = px(10);
        row.addView(a, alp);

        row.setOnClickListener(v -> {
            boolean open = a.getVisibility() != View.VISIBLE;
            ViewGroup scene = findViewById(R.id.tool_content);
            TransitionManager.beginDelayedTransition(scene, new AutoTransition()
                    .setDuration(260).setInterpolator(EMPHASIZED));
            a.setVisibility(open ? View.VISIBLE : View.GONE);
            chevron.animate().rotation(open ? 180f : 0f).setDuration(260).setInterpolator(EMPHASIZED).start();
        });
        return row;
    }

    // ------------------------------------------------------------------
    // その他
    // ------------------------------------------------------------------

    private View buildOthers() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(sectionTitle(Lang.t("その他のヘルプ")));
        section.addView(linkRow(R.drawable.ic_ext_star, Lang.t("新機能"), Lang.t("このバージョンで追加・変わったこと"), true, false, () -> {
            startActivity(new Intent(this, NewFeatures.class));
            overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
        }));
        section.addView(linkRow(R.drawable.ic_help_feedback, Lang.t("お問い合わせ"), Lang.t("解決しない時は開発者に連絡できます"), false, true, () -> {
            startActivity(ContactActivity.intent(this));
            overridePendingTransition(R.anim.slide_in_up_low, R.anim.no_animation);
        }));
        return section;
    }

    private View linkRow(@DrawableRes int icon, String title, String summary, boolean first, boolean last, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        float big = px(24), small = px(6);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(container);
        bg.setCornerRadii(new float[]{first ? big : small, first ? big : small, first ? big : small, first ? big : small,
                last ? big : small, last ? big : small, last ? big : small, last ? big : small});
        row.setBackground(bg);
        row.setClipToOutline(true);
        row.setForeground(getDrawable(resolve(android.R.attr.selectableItemBackground)));
        row.setPadding(px(16), px(16), px(16), px(16));
        row.addView(tonalIcon(icon));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 16, onSurface));
        texts.addView(text(summary, 13.5f, onVariant));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(px(16));
        row.addView(texts, tlp);
        ImageView go = new ImageView(this);
        go.setImageResource(R.drawable.ic_ts_chevron_right);
        go.setImageTintList(ColorStateList.valueOf(onVariant));
        row.addView(go, new LinearLayout.LayoutParams(px(20), px(20)));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = px(3);
        row.setLayoutParams(rlp);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    // ------------------------------------------------------------------
    // 部品
    // ------------------------------------------------------------------

    private View tonalIcon(@DrawableRes int res) {
        FrameLayout circle = new FrameLayout(this);
        circle.setBackground(round(primaryContainer, px(20)));
        ImageView i = new ImageView(this);
        i.setImageResource(res);
        i.setImageTintList(ColorStateList.valueOf(onPrimaryContainer));
        circle.addView(i, new FrameLayout.LayoutParams(px(20), px(20), Gravity.CENTER));
        circle.setLayoutParams(new LinearLayout.LayoutParams(px(40), px(40)));
        return circle;
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 14, primary);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(px(8), px(20), px(8), px(10));
        return t;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private int resolve(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.resourceId;
    }

    private static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 動画のチュートリアル画面で「お問い合わせ」を押した時は、そのままブラウザへ伝える
        if (requestCode == REQUEST_LEGACY_HELP && resultCode == RESULT_OK && data != null
                && "contact".equals(data.getStringExtra("action"))) {
            setResult(RESULT_OK, data);
            close();
        }
    }

    @Override
    public void onBackPressed() {
        close();
    }

    private void close() {
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.slide_out_down_low);
    }

    @NonNull
    static Intent intent(@NonNull Context c) {
        return new Intent(c, HelpCenterActivity.class);
    }
}
