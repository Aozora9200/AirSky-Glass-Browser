package com.airsky.browser;

import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.Nullable;

/**
 * スクロールに合わせてアドレスバー(上)とボトムバー(下)を出し入れする。
 *
 * Firefox(GeckoView の Dynamic Toolbar)/ Chrome と同じ考え方:
 * <ul>
 *   <li>ページ表示領域の大きさは変えない(毎フレームのリサイズ=再レイアウトがガタつきの原因)。
 *       バーと表示領域は translationY でずらすだけ。</li>
 *   <li>バーはスクロール量にそのまま追従して少しずつ動く(しきい値で一気に切り替えない)。</li>
 *   <li>指を離してスクロールが止まったら、出し切る/隠し切る位置へ短いアニメーションで寄せる。</li>
 *   <li>Gecko にはバーの最大の高さと今どれだけ隠れているかを伝え、ページ下端の固定要素
 *       (position: fixed; bottom: 0)が隠れたり浮いたりしないようにする。</li>
 *   <li>バーが全部出たまま止まったら、表示領域を見えている範囲ぴったりに縮める(Chrome と同じ)。
 *       縮めないと表示領域の下端がバーの分だけ画面外に出たままになり、ページ最下部でバーを
 *       出した時にページの一番下が見切れて、それ以上スクロールしても出てこない。</li>
 *   <li>指が触れている間は、ページのスクロール量ではなく指の動きでバーを動かす(Chrome と同じく
 *       バーが先にスクロールを受け取る)。ページがそれ以上スクロールできない所(一番下・画面より
 *       わずかに長いだけのページ)でも、指を動かせばバーが隠れて続きが見える。
 *       地図などページ自身がタッチを使う時と、中の枠だけがスクロールしている時は動かさない。</li>
 *   <li>画面よりわずかに長いだけのページ(バーを隠せば全体が収まるページ)では表示領域を縮めない。
 *       縮めると隠し始めに広げ直した瞬間にページ全体が収まってスクロール位置が 0 に戻り、
 *       「最上部なのでバーを出す → 縮める → またスクロールできる」を繰り返してガタつくため。</li>
 *   <li>表示領域を広げた直後に、指の向きと逆へスクロール位置が詰められても(リサイズのせい)
 *       バーを出し戻さない。</li>
 *   <li>指で動かした後の慣性スクロール以外(ページのスクリプトやページ内リンク、読み込み中の
 *       位置の復元など)でスクロールしても、バーは隠さない。</li>
 * </ul>
 */
public class DynamicToolbar {

    /** バーが全部隠れた/出た時の通知(ズームボタン等の表示切り替え用)。 */
    public interface Listener {
        void onToolbarFullyHidden(boolean hidden);
        void onBottomBarHidden(boolean hidden);
    }

    private static final long SNAP_DURATION = 160;
    /** ▼/▲でボトムバーを出し入れする時間。形の揺れはバー側(GlassBottomBar)のバネが付ける。 */
    private static final long BOTTOM_HIDE_DURATION = 260;
    private static final long BOTTOM_SHOW_DURATION = 420;
    /** 隠す: ゆっくり動き出して加速しながら沈む。 */
    private static final Interpolator SINK = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
    /** 出す: 勢いよく浮かび上がって、最後はゆっくり止まる。 */
    private static final Interpolator RISE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    /** スクロールが止まったとみなすまでの時間。 */
    private static final long SCROLL_IDLE_MS = 120;
    /**
     * 表示領域を広げてからこの時間内に最上部へ戻されたら「短いページ」とみなす。
     * Gecko がリサイズを反映してスクロール位置を詰めるまでには、端末によって 1 秒近くかかる。
     */
    private static final long UNFIT_BOUNCE_MS = 3000;
    /** 指を離してから慣性スクロールの最初の知らせが来るまで待つ時間。 */
    private static final long FLING_START_MS = 600;
    /** 慣性スクロールの知らせの間隔がこれより空いたら、慣性は止まったとみなす。 */
    private static final long FLING_GAP_MS = 300;

    private final View[] topViews;
    private final View bottomBar;
    private final View content;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int touchSlop;

    private int topHeight;
    private int bottomHeight;

    /** 設定「スクロールでボトムバーも隠す」。 */
    private boolean hideBottomOnScroll;
    /** 設定「アドレスバーを固定」。スクロールしてもバーを隠さない(▼での手動の出し入れはできる)。 */
    private boolean urlBarFixed;
    /** ボトムバーを手動(▼ボタン)で隠している。 */
    private boolean bottomManuallyHidden;
    /** 内蔵ページなどでバーを常に出しておく。 */
    private boolean locked;
    /**
     * 二画面表示中。バーは上下の画面に分かれて常に出ているので、スクロールで動かさない。
     * 表示領域は上のバー(ステータスバーとプログレスバー)の下から画面の下端まで。
     */
    private boolean pinned;
    private boolean fullScreen;
    /**
     * ソフトキーボードがボトムバーより上に食い込んでいる高さ(px)。
     * 画面はキーボードで縮まない設定(adjustNothing)なので、その分だけ表示領域を自分で縮める。
     */
    private int imeHeight;
    /**
     * 下のバーに URL バーがある(タブレット UI でバーを下に置いた時)。キーボードが出たら
     * 下のバーをキーボードの上へ持ち上げ、表示領域はそのバーの上までにする。
     */
    private boolean bottomFollowsIme;

    /** 隠れている量(px)。0 = 全部表示、range() = 全部隠れている。 */
    private float offset;
    private int lastDirection;
    private boolean touching;
    private boolean lastFullyHidden;
    private boolean lastBottomHidden;

    @Nullable private ValueAnimator snapAnimator;
    @Nullable private ValueAnimator bottomAnimator;
    /** 手動で隠す/出す時のボトムバーの位置(0 = 表示, 1 = 隠れている)。 */
    private float manualBottomFraction;
    @Nullable private GeckoTabView tab;
    /** 表示領域を見えている範囲に縮めている(バーが全部出て止まっている間)。 */
    private boolean fitted;
    /** 表示領域を広げた時刻(uptimeMillis)。 */
    private long unfitAt;
    /**
     * 表示領域を広げたらページ全体が収まってしまった(スクロール位置が 0 に戻された)ページ。
     * このページでは表示領域を縮めない。ページから高さの報告がある時はそちらでも判断する({@link #isShortPage})。
     */
    private boolean shortPage;

    // ---- 指の動き ----
    private float touchStartX, touchStartY, lastTouchY;
    /** タッチの開始時のページのタッチ回数。変わっていなければページはこのタッチを受け取っていない。 */
    private int gestureAtDown;
    /** タッチスロップを超えて縦に動かし始めた。 */
    private boolean dragging;
    /** 2 本指(拡大縮小)になった。このタッチではバーを動かさない。 */
    private boolean multiTouch;
    /** このタッチで指の動きに合わせてバーを動かすか、決まった。 */
    private boolean driveDecided;
    private boolean drive;
    /** 決まるまでの間に動いた量。決まったらまとめて反映する。 */
    private float pendingDy;
    /** このタッチの間にページ全体がスクロールした(中の枠ではなく)。 */
    private boolean rootScrolled;
    /**
     * この時刻(uptimeMillis)までのスクロールは、指で動かした後の慣性スクロールとみなす。
     * これを過ぎたスクロールはページ自身が動かしたものなので、バーを隠さない。
     */
    private long flingUntil;

    private final Runnable snapRunnable = this::snap;
    private final Runnable fitRunnable = this::fit;

    public DynamicToolbar(View[] topViews, View bottomBar, View content, Listener listener) {
        this.topViews = topViews;
        this.bottomBar = bottomBar;
        this.content = content;
        this.listener = listener;
        this.touchSlop = android.view.ViewConfiguration.get(content.getContext()).getScaledTouchSlop();
    }

    // ---- 設定 ----

    /** バーの高さ(レイアウト後に呼ぶ)。 */
    public void setHeights(int topHeight, int bottomHeight) {
        if (this.topHeight == topHeight && this.bottomHeight == bottomHeight) return;
        this.topHeight = topHeight;
        this.bottomHeight = bottomHeight;
        relayoutContent();
        apply();
    }

    /** キーボードが出たら下のバーをその上へ持ち上げる({@link #bottomFollowsIme})。 */
    public void setBottomFollowsIme(boolean follow) {
        if (bottomFollowsIme == follow) return;
        bottomFollowsIme = follow;
        relayoutContent();
        apply();
    }

    public void setHideBottomOnScroll(boolean hide) {
        if (hideBottomOnScroll == hide) return;
        hideBottomOnScroll = hide;
        offset = 0;
        relayoutContent();
        apply();
    }

    /** 設定「アドレスバーを固定」。オンの間はスクロールしてもアドレスバーとボトムバーを隠さない。 */
    public void setUrlBarFixed(boolean fixed) {
        if (urlBarFixed == fixed) return;
        urlBarFixed = fixed;
        cancelSnap();
        handler.removeCallbacks(snapRunnable);
        offset = 0;
        fitted = false;
        relayoutContent();
        apply();
    }

    /** スクロールでボトムバーも隠すか(アドレスバーを固定している時は隠さない)。 */
    private boolean hidesBottomOnScroll() {
        return hideBottomOnScroll && !urlBarFixed;
    }

    /** 表示中のタブ。Gecko に Dynamic Toolbar の情報を渡す相手。 */
    public void attachTab(@Nullable GeckoTabView tab) {
        this.tab = tab;
        shortPage = false;
        flingUntil = 0;
        applyToGecko();
    }

    /** 別のページへ移った。短いページの判定をやり直す。 */
    public void onPageChanged() {
        shortPage = false;
        flingUntil = 0;
    }

    /** true の間はスクロールしてもバーを隠さない(内蔵ページなど)。 */
    public void setLocked(boolean locked) {
        this.locked = locked;
        if (locked) show(true);
    }

    /** 二画面表示の間はバーを動かさない({@link #pinned})。 */
    public void setPinned(boolean pinned) {
        if (this.pinned == pinned) return;
        this.pinned = pinned;
        cancelSnap();
        handler.removeCallbacks(snapRunnable);
        handler.removeCallbacks(fitRunnable);
        if (bottomAnimator != null) bottomAnimator.cancel();
        bottomAnimator = null;
        offset = 0;
        fitted = false;
        if (pinned) {
            bottomManuallyHidden = false;
            manualBottomFraction = 0f;
        }
        relayoutContent();
        apply();
    }

    public boolean isFullyShown() {
        return offset <= 0;
    }

    // ---- 入力 ----

    /** ページのスクロール位置が変わった(Gecko の ScrollDelegate から)。 */
    public void onPageScrolled(int scrollY, int oldScrollY) {
        if (fullScreen || imeHeight > 0) return;
        int range = range();
        if (range <= 0) return;
        int delta = scrollY - oldScrollY;

        if (scrollY <= 0 && oldScrollY > 0 && oldScrollY <= range && lastDirection > 0
                && SystemClock.uptimeMillis() - unfitAt < UNFIT_BOUNCE_MS) {
            // 隠そうとしていたのに、表示領域を広げた後で少しの位置から一気に最上部へ戻された
            // = 広げるとページ全体が収まる短いページ(Gecko がスクロール位置を詰めた)。
            // バーはそのまま(指の動きで出し入れする)にして、このページでは表示領域を縮めない
            shortPage = true;
            return;
        }
        // バーの高さ以上スクロールできた = 読み込みが進むなどして、もう短いページではない
        if (shortPage && scrollY > range) shortPage = false;

        if (touching) {
            // 指が触れている間は指の動きでバーを動かす。ページのスクロールは
            // 「ページ全体がスクロールしている(中の枠ではない)」という印にだけ使う
            if (delta != 0) rootScrolled = true;
            return;
        }

        if (scrollY <= 0) {
            // 最上部ではバーを出す(Chrome / Firefox と同じ)。短いページはいつも 0 なので出し戻さない
            if (offset > 0 && !isShortPage()) show(true);
            return;
        }
        if (locked || delta == 0) return;
        // 指で動かした後の慣性スクロールでなければ(ページのスクリプト・ページ内リンク・
        // 読み込み中の位置の復元など)バーは動かさない。勝手に隠れてしまうのを防ぐ
        long now = SystemClock.uptimeMillis();
        if (now > flingUntil) return;
        flingUntil = Math.max(flingUntil, now + FLING_GAP_MS);
        int direction = delta > 0 ? 1 : -1;
        // 指を離した後の慣性スクロールは向きが変わらない。表示領域を広げた直後に逆向きへ動いたのは
        // リサイズでスクロール位置が詰められただけなので、バーを動かさない
        if (direction != lastDirection && SystemClock.uptimeMillis() - unfitAt < UNFIT_BOUNCE_MS) return;
        lastDirection = direction;
        cancelSnap();
        // 隠し始める前に表示領域を元の大きさへ戻す(戻さないと下に隙間ができる)
        if (delta > 0) unfit();
        setOffset(offset + delta);
        // 慣性スクロールが止まったら寄せる
        handler.removeCallbacks(snapRunnable);
        handler.postDelayed(snapRunnable, SCROLL_IDLE_MS);
    }

    /** 画面へのタッチ(Activity.dispatchTouchEvent から。子の View に渡す前に呼ぶ)。 */
    public void onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touching = true;
                handler.removeCallbacks(snapRunnable);
                touchStartX = ev.getX();
                touchStartY = lastTouchY = ev.getY();
                dragging = false;
                multiTouch = false;
                driveDecided = false;
                drive = false;
                pendingDy = 0;
                rootScrolled = false;
                GeckoTabView t = tab;
                gestureAtDown = t != null ? t.getTouchGesture() : 0;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                multiTouch = true;
                break;
            case MotionEvent.ACTION_MOVE:
                onTouchMove(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                // 縦に指を動かしたタッチの後だけ、続く慣性スクロールでバーを動かす
                flingUntil = dragging ? SystemClock.uptimeMillis() + FLING_START_MS : 0;
                handler.removeCallbacks(snapRunnable);
                handler.postDelayed(snapRunnable, SCROLL_IDLE_MS);
                break;
        }
    }

    private void onTouchMove(MotionEvent ev) {
        float y = ev.getY();
        float dy = lastTouchY - y; // 正 = 指を上へ = ページの続きの方へスクロール
        lastTouchY = y;
        if (multiTouch || locked || fullScreen || imeHeight > 0 || range() <= 0) return;
        if (!dragging) {
            float totalY = touchStartY - y;
            float totalX = ev.getX() - touchStartX;
            if (Math.abs(totalY) <= touchSlop || Math.abs(totalY) <= Math.abs(totalX)) return;
            dragging = true;
            // スロップを超えた分だけ動かす(超えた瞬間に跳ねないように)
            dy = totalY - Math.signum(totalY) * touchSlop;
        }
        if (!driveDecided) {
            pendingDy += dy;
            Boolean d = shouldDrive(pendingDy > 0);
            if (d == null) return;
            driveDecided = true;
            drive = d;
            dy = pendingDy;
            pendingDy = 0;
        } else if (!drive && rootScrolled) {
            // 中の枠の端まで来てページ全体のスクロールに移った
            drive = true;
        }
        if (drive) driveBy(dy);
    }

    /**
     * このタッチで指の動きに合わせてバーを動かすか。null はまだ分からない。
     * @param down 下へ(ページの続きの方へ)スクロールする向き
     */
    @Nullable
    private Boolean shouldDrive(boolean down) {
        GeckoTabView t = tab;
        // ページがタッチを受け取っていない(スタートページ・バーの上など)
        if (t == null || t.isDestroyed() || t.getTouchGesture() == gestureAtDown) return false;
        Boolean content = t.isTouchHandledByContent();
        if (Boolean.TRUE.equals(content)) return false; // 地図・ゲームなど
        if (rootScrolled) return true;
        if (content == null) return null;
        // 触れた所がその向きへもうスクロールできない(ページの端・画面より少し長いだけのページ)
        if (!t.touchCanScroll(down)) return true;
        // スクロールできる: ページ全体なら少し後にスクロールの知らせが来る。中の枠なら来ないので動かさない
        return null;
    }

    /** 指の動き [dy] の分だけバーを動かす。 */
    private void driveBy(float dy) {
        if (dy == 0) return;
        lastDirection = dy > 0 ? 1 : -1;
        cancelSnap();
        // 隠し始める前に表示領域を元の大きさへ戻す(戻さないと下に隙間ができる)
        if (dy > 0 && offset < range()) unfit();
        setOffset(offset + dy);
    }

    // ---- 操作 ----

    public void show(boolean animate) {
        handler.removeCallbacks(snapRunnable);
        animateTo(0, animate);
    }

    public void hide(boolean animate) {
        if (locked || fullScreen || imeHeight > 0) return;
        handler.removeCallbacks(snapRunnable);
        unfit();
        animateTo(range(), animate);
    }

    /** ▼/▲ボタンでボトムバーだけを出し入れする。 */
    public void setBottomManuallyHidden(boolean hidden) {
        if (bottomManuallyHidden == hidden) return;
        bottomManuallyHidden = hidden;
        if (bottomAnimator != null) bottomAnimator.cancel();
        // 出す時は先に表示領域を戻し、隠す時はアニメーションの後で広げる(リサイズは 1 回だけ)
        if (!hidden) relayoutContent();
        float from = manualBottomFraction;
        float to = hidden ? 1f : 0f;
        ValueAnimator va = ValueAnimator.ofFloat(from, to);
        va.setDuration(hidden ? BOTTOM_HIDE_DURATION : BOTTOM_SHOW_DURATION);
        va.setInterpolator(hidden ? SINK : RISE);
        va.addUpdateListener(a -> {
            manualBottomFraction = (float) a.getAnimatedValue();
            apply();
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (bottomAnimator != animation) return;
                bottomAnimator = null;
                if (bottomManuallyHidden) relayoutContent();
                offset = Math.min(offset, range());
                apply();
            }
        });
        bottomAnimator = va;
        va.start();
    }

    /**
     * ソフトキーボードの高さが変わった。出ている間はバーを出したままにし、表示領域をキーボードの上までに縮める
     * (ページ下部の入力欄がキーボードに隠れないように)。
     */
    public void setImeHeight(int height) {
        height = Math.max(0, height);
        if (imeHeight == height) return;
        imeHeight = height;
        if (height > 0) {
            cancelSnap();
            handler.removeCallbacks(snapRunnable);
            offset = 0;
            apply();
        } else if (bottomFollowsIme) {
            // 持ち上げていた下のバーを戻す
            apply();
        }
        relayoutContent();
    }

    public void setFullScreen(boolean fullScreen) {
        if (this.fullScreen == fullScreen) return;
        this.fullScreen = fullScreen;
        cancelSnap();
        offset = 0;
        relayoutContent();
        apply();
    }

    // ---- 内部 ----

    /** スクロールで動く高さの合計。 */
    private int range() {
        if (fullScreen || pinned || urlBarFixed) return 0;
        return topHeight + (bottomScrolls() ? bottomHeight : 0);
    }

    private boolean bottomScrolls() {
        return hidesBottomOnScroll() && !bottomManuallyHidden;
    }

    /**
     * 表示領域の余白を決める。スクロール中には呼ばない(呼ぶとリサイズ=再レイアウトになる)。
     * 上: 0(代わりに translationY でアドレスバーの下へずらす)
     * 下: ボトムバーが固定表示ならその高さ、スクロールで隠れる/手動で隠しているなら 0
     *     (fitted の間はさらにバーの分だけ縮めて、ページの下端を画面内に収める)
     */
    private void relayoutContent() {
        ViewGroup.LayoutParams p = content.getLayoutParams();
        if (!(p instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) p;
        int bottom = (fullScreen || hidesBottomOnScroll() || bottomManuallyHidden) ? 0 : bottomHeight;
        if (fitted) bottom += range();
        // 二画面: ボトムバーは下の画面にあるので、上のバーの分だけ下端を詰めて画面内に収める
        if (pinned) bottom = fullScreen ? 0 : topHeight;
        if (imeHeight > 0) {
            // 表示領域はアドレスバーの高さ分だけ下へずらしているので、その分も足してキーボードの上で切る
            bottom = imeHeight + (fullScreen ? 0 : topHeight);
            // 下のバーをキーボードの上に持ち上げている時は、そのバーの上で切る
            if (bottomFollowsIme && !fullScreen) bottom += bottomHeight;
        }
        if (lp.topMargin != 0 || lp.bottomMargin != bottom) {
            lp.topMargin = 0;
            lp.bottomMargin = bottom;
            content.setLayoutParams(lp);
        }
        applyToGecko();
    }

    private void setOffset(float value) {
        float clamped = Math.max(0, Math.min(range(), value));
        if (clamped == offset) return;
        offset = clamped;
        apply();
    }

    private void fit() {
        handler.removeCallbacks(fitRunnable);
        if (fitted || touching || offset > 0 || snapAnimator != null || range() <= 0) return;
        // バーを隠せば全体が収まるページは縮めない(隠し始めに広げ直すとスクロール位置が 0 に戻ってガタつく)。
        // 下端がバーに隠れていても、指で上へ動かせばバーが隠れて見える
        if (!locked && isShortPage()) return;
        // 縮める(=表示領域のリサイズ)と、次に下へスクロールし始めた時に元へ戻すリサイズが必要になり、
        // その瞬間に画面が 1 フレームほど乱れる。縮めないと困るのはページの一番下でバーを出した時
        // (下端がバーに隠れる)だけなので、ページから「一番下ではない」と分かっている時は縮めない。
        GeckoTabView t = tab;
        if (t != null && Boolean.FALSE.equals(t.isNearBottom())) return;
        fitted = true;
        relayoutContent();
    }

    /**
     * バーを全部隠せばページ全体が画面に収まる(スクロールしなくてよい)ページか。
     * 一度「広げたら最上部へ戻された」ページか、ページから報告された高さで判断する。
     */
    private boolean isShortPage() {
        if (shortPage) return true;
        GeckoTabView t = tab;
        if (t == null) return false;
        int doc = t.getDocumentHeight();
        if (doc <= 0 || !(content.getParent() instanceof View)) return false;
        int full = ((View) content.getParent()).getHeight();
        if (full <= 0) return false;
        int bottom = (fullScreen || hidesBottomOnScroll() || bottomManuallyHidden) ? 0 : bottomHeight;
        return doc <= full - bottom + 2;
    }

    /** ページの一番下付近に来た/離れた(ページから報告される)。 */
    public void onNearBottomChanged() {
        GeckoTabView t = tab;
        if (t == null) return;
        if (Boolean.TRUE.equals(t.isNearBottom())) {
            // 一番下でバーが出ているなら、スクロールが落ち着いてから縮める
            handler.removeCallbacks(fitRunnable);
            if (offset <= 0 && !fitted) handler.postDelayed(fitRunnable, SCROLL_IDLE_MS);
        }
    }

    /** 表示領域を元の大きさ(バーの下まで広がった状態)へ戻す。 */
    private void unfit() {
        handler.removeCallbacks(fitRunnable);
        if (!fitted) return;
        fitted = false;
        unfitAt = SystemClock.uptimeMillis();
        relayoutContent();
    }

    /** offset から各ビューの位置を決める。 */
    private void apply() {
        int range = range();
        float f = range > 0 ? offset / range : 0f; // 0 = 表示, 1 = 隠れている

        float topShift = fullScreen ? 0 : f * topHeight;
        for (View v : topViews) v.setTranslationY(-topShift);
        content.setTranslationY(fullScreen ? 0 : topHeight - topShift);

        float bottomShift;
        if (bottomScrolls()) {
            // ▲で手動で隠したのを戻す途中は、スクロール連動に切り替わっていてもアニメーションで出す
            bottomShift = Math.max(f, manualBottomFraction) * bottomHeight;
        } else {
            bottomShift = manualBottomFraction * bottomHeight;
        }
        if (bottomFollowsIme && imeHeight > 0 && !fullScreen) {
            // キーボードの上へ持ち上げる(隠れていても出す)
            bottomBar.setTranslationY(-imeHeight);
        } else if (bottomBar instanceof GlassBottomBar) {
            // ずらすだけでなく、ガラスが水滴のように縮んだり広がったりする
            ((GlassBottomBar) bottomBar).setHideProgress(
                    bottomHeight > 0 ? bottomShift / bottomHeight : 0f, bottomShift);
        } else {
            bottomBar.setTranslationY(bottomShift);
        }

        applyToGecko();

        // バーが全部出たら、スクロールが落ち着いてから表示領域を縮める(起動直後も含む)
        handler.removeCallbacks(fitRunnable);
        if (offset <= 0 && !fitted) handler.postDelayed(fitRunnable, SCROLL_IDLE_MS);

        boolean fullyHidden = range > 0 && offset >= range;
        if (fullyHidden != lastFullyHidden) {
            lastFullyHidden = fullyHidden;
            listener.onToolbarFullyHidden(fullyHidden);
        }
        boolean bottomHidden = bottomHeight > 0 && bottomShift >= bottomHeight;
        if (bottomHidden != lastBottomHidden) {
            lastBottomHidden = bottomHidden;
            listener.onBottomBarHidden(bottomHidden);
        }
    }

    /**
     * Gecko に「バーの最大の高さ」と「今どれだけバーが隠れているか」を伝える。
     * Gecko はバーが全部出ている状態(=表示領域の下端がバーの分だけ隠れている)を基準に
     * ページ下端の固定要素を置くので、バーが隠れた分だけ負の値で下へずらしてもらう
     * (Firefox Android と同じ渡し方)。
     */
    private void applyToGecko() {
        GeckoTabView t = tab;
        if (t == null || t.isDestroyed()) return;
        // 縮めている間はバーと重ならないので、Gecko から見ると動くバーは無い
        boolean shrunk = fitted || imeHeight > 0;
        t.setDynamicToolbarMaxHeight(shrunk ? 0 : range());
        t.setVerticalClipping(shrunk ? 0 : -Math.round(offset));
    }

    private void snap() {
        if (touching) return;
        int range = range();
        if (offset <= 0) {
            fit();
            return;
        }
        if (offset >= range) return;
        // 最後に動いた向きへ寄せる(下へスクロール中なら隠す、上へなら出す)
        animateTo(lastDirection > 0 ? range : 0, true);
    }

    private void animateTo(float target, boolean animate) {
        cancelSnap();
        if (!animate || Math.abs(target - offset) < 1) {
            setOffset(target);
            return;
        }
        ValueAnimator va = ValueAnimator.ofFloat(offset, target);
        va.setDuration(SNAP_DURATION);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(a -> setOffset((float) a.getAnimatedValue()));
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (snapAnimator != animation) return;
                snapAnimator = null;
                if (offset <= 0) fit();
            }
        });
        snapAnimator = va;
        va.start();
    }

    private void cancelSnap() {
        if (snapAnimator != null) {
            ValueAnimator a = snapAnimator;
            snapAnimator = null;
            a.cancel();
        }
    }
}
