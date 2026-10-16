package com.airsky.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.Presentation;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.ContextThemeWrapper;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.lifecycle.ViewTreeViewModelStoreOwner;
import androidx.savedstate.ViewTreeSavedStateRegistryOwner;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;

import org.mozilla.geckoview.GeckoDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * 二画面表示(AYN Thor のような画面を 2 つ持つ端末)。
 *
 * <ul>
 *   <li>上の画面(アプリが開いている画面): 上端にステータスバーとプログレスバー、その下にページ。</li>
 *   <li>下の画面: ページの続き、その下に URL バーとボトムバー。</li>
 * </ul>
 *
 * <p>ページは {@link DualScreenCompositor} が上下の画面をつなげた 1 枚の描画面に描かせて切り分けるので、
 * 上の画面でスクロールした絵がそのまま下の画面へ流れていく。下の画面に触れた操作は、ページ上の
 * 同じ場所への操作として表示中のタブへ渡す。</p>
 *
 * <p>URL バーとボトムバーは、ブラウザ画面のビューをそのまま下の画面へ移して使う(ボタンの動作や
 * 表示の更新はこれまでどおり MainActivity が行う)。二画面をやめる時は元の場所へ戻す。</p>
 */
final class DualScreenController implements GeckoTabView.ExternalDisplayHost, DisplayManager.DisplayListener {

    private static final String TAG = "DualScreen";

    interface Host {
        /** 今表示しているタブ。 */
        @Nullable
        GeckoTabView currentTab();

        /** 上の画面にネイティブのスタートページを出しているか。 */
        boolean isStartPageShowing();

        /** 二画面を始めた/やめた(ビューの移動の後)。上の画面のバーの並びを合わせる。 */
        void onDualScreenChanged(boolean active);

        /** 下の画面で戻るボタン(またはキー)が押された。 */
        void onBackFromSecondScreen();

        /** 下の画面の操作を止めるか(タブ一覧を開いている間など)。上の画面の操作と食い違わないようにする。 */
        boolean isSecondScreenLocked();

        /** 下の画面に来たキー(コントローラーのボタンなど)。使ったら true。 */
        boolean onSecondScreenKey(@NonNull android.view.KeyEvent event);

        /** 下の画面に来たスティックの動きなど。使ったら true。 */
        boolean onSecondScreenMotion(@NonNull MotionEvent event);
    }

    /** 二画面表示を持つ画面(メニューを下の画面に出すため)。 */
    interface Owner {
        @Nullable
        DualScreenController dualScreenController();
    }

    /**
     * メニューのボトムシートを作る。二画面の時は、URL バー・ボトムバーのある下の画面に出す。
     */
    @NonNull
    static BottomSheetDialog newBottomSheet(@NonNull Activity activity) {
        DualScreenController c = activity instanceof Owner ? ((Owner) activity).dualScreenController() : null;
        Context ctx = c != null ? c.secondScreenDialogContext() : null;
        if (ctx == null) {
            // タブレット UI でバーが上にある時は、上から下りてくるシートにする
            BottomSheetDialog d = TopSheet.adapt(activity, new BottomSheetDialog(activity));
            FocusHighlight.attach(d.getWindow());
            return d;
        }
        BottomSheetDialog d = new BottomSheetDialog(ctx);
        c.attachToSecondScreen(d);
        FocusHighlight.attach(d.getWindow());
        return d;
    }

    private final AppCompatActivity activity;
    private final Host host;
    private final FrameLayout topPageArea;
    /** 下の画面へ移すバー(上から並べる順)。 */
    private final View[] bars;
    /** 下の画面のページの上に重ねる物(URL の候補など)。 */
    @Nullable private final View pageOverlay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DisplayManager displayManager;

    private boolean started;
    private boolean active;
    private boolean fullScreen;

    @Nullable private DualScreenCompositor compositor;
    @Nullable private TextureView topView;
    @Nullable private SecondScreen presentation;
    @Nullable private TextureView bottomView;
    @Nullable private ImageView wallpaper;
    @Nullable private GeckoTabView boundTab;
    private boolean startPageShown;
    /** 下の画面の操作を止めている間、全体を覆う幕。 */
    @Nullable private View lockScrim;
    private boolean locked;
    /** 下の画面でページの入力欄に触れた時、キーボードの文字をタブへ渡す受け口。 */
    @Nullable private ImeProxy imeProxy;
    /** 下の画面に出す仮想マウスのカーソル。 */
    @Nullable private VirtualMouse.CursorView bottomCursor;

    /** 下の画面へ移したビューと、元の場所。 */
    private static final class Moved {
        final View view;
        final ViewGroup parent;
        final int index;
        final ViewGroup.LayoutParams params;
        final float translationY;

        Moved(View view) {
            this.view = view;
            this.parent = (ViewGroup) view.getParent();
            this.index = parent.indexOfChild(view);
            this.params = view.getLayoutParams();
            this.translationY = view.getTranslationY();
        }
    }

    private final List<Moved> moved = new ArrayList<>();

    private final Runnable syncRunnable = this::sync;

    DualScreenController(@NonNull AppCompatActivity activity, @NonNull Host host,
                         @NonNull FrameLayout topPageArea, @NonNull View[] bars, @Nullable View pageOverlay) {
        this.activity = activity;
        this.host = host;
        this.topPageArea = topPageArea;
        this.bars = bars;
        this.pageOverlay = pageOverlay;
        this.displayManager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
    }

    // ------------------------------------------------------------------
    // アクティビティから
    // ------------------------------------------------------------------

    void onStart() {
        started = true;
        if (displayManager != null) displayManager.registerDisplayListener(this, handler);
        update();
    }

    void onStop() {
        started = false;
        if (displayManager != null) displayManager.unregisterDisplayListener(this);
        update();
    }

    /** 設定が変わったかもしれない(設定画面から戻った時など)。 */
    void refresh() {
        update();
    }

    void release() {
        started = false;
        if (displayManager != null) displayManager.unregisterDisplayListener(this);
        deactivate();
    }

    boolean isActive() {
        return active;
    }

    /** 動画などの全画面表示。上の画面だけを使い、下の画面にはページを出さない。 */
    void setFullScreen(boolean fullScreen) {
        if (this.fullScreen == fullScreen) return;
        this.fullScreen = fullScreen;
        if (active) {
            updateGeometry();
            updateBottomContent();
        }
    }

    /** 下の画面へ移したビューから探す(アクティビティの画面に見つからなかった時)。 */
    @Nullable
    <T extends View> T findViewById(int id) {
        for (Moved m : moved) {
            T v = m.view.findViewById(id);
            if (v != null) return v;
        }
        return null;
    }

    /** 表示中のタブ・スタートページの表示に合わせる(描画の直前に毎回呼ばれる)。 */
    void sync() {
        if (!active) return;
        GeckoTabView tab = host.currentTab();
        if (tab != null && (tab.isDestroyed() || tab.getVisibility() != View.VISIBLE)) tab = null;
        if (tab != boundTab) {
            GeckoTabView old = boundTab;
            if (old != null) {
                returnIme(old);
                old.detachExternalDisplay();
            } // onExternalDisplayReleased で boundTab = null
            boundTab = null;
            if (tab != null) tab.attachExternalDisplay(this);
        }
        boolean start = host.isStartPageShowing();
        if (start != startPageShown) {
            startPageShown = start;
            updateBottomContent();
        }
        setLocked(host.isSecondScreenLocked());
    }

    /** 下の画面の操作を止める・戻す。止めている間は幕で覆い、触れても何も起きないようにする。 */
    private void setLocked(boolean lock) {
        View scrim = lockScrim;
        if (lock == locked || scrim == null) return;
        locked = lock;
        scrim.animate().cancel();
        if (lock) {
            // 触れ始めていた操作を終わらせる(押しっぱなしのボタンなどを残さない)
            View root = scrim.getRootView();
            long now = android.os.SystemClock.uptimeMillis();
            MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
            root.dispatchTouchEvent(cancel);
            cancel.recycle();
            scrim.setVisibility(View.VISIBLE);
            scrim.animate().alpha(1f).setDuration(180).start();
        } else {
            scrim.animate().alpha(0f).setDuration(180)
                    .withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        }
    }

    /** 下の画面に出すダイアログ用の Context(アクティビティと同じテーマ)。二画面でない時は null。 */
    @Nullable
    private Context secondScreenDialogContext() {
        SecondScreen p = presentation;
        if (!active || p == null || p.getWindow() == null
                || p.getWindow().getDecorView().getWindowToken() == null) {
            return null;
        }
        // 下の画面の Resources(画面の密度が違う)で、アクティビティと同じテーマを組み立てる
        int theme;
        try {
            theme = activity.getPackageManager().getActivityInfo(activity.getComponentName(), 0).getThemeResource();
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            theme = activity.getApplicationInfo().theme;
        }
        if (theme == 0) return null;
        return new ContextThemeWrapper(p.getContext(), theme);
    }

    /** ダイアログを下の画面の窓の子にする(下の画面に重なり、一緒に閉じられる)。 */
    private void attachToSecondScreen(@NonNull Dialog d) {
        SecondScreen p = presentation;
        android.view.Window w = d.getWindow();
        if (p == null || p.getWindow() == null || w == null) return;
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        lp.token = p.getWindow().getDecorView().getWindowToken();
        w.setAttributes(lp);
    }

    // ------------------------------------------------------------------
    // 始める・やめる
    // ------------------------------------------------------------------

    private void update() {
        Display second = started && DualScreen.isEnabled(activity) && !activity.isFinishing()
                ? DualScreen.findSecondScreen(activity) : null;
        if (second == null) {
            deactivate();
        } else if (!active) {
            activate(second);
        } else if (presentation != null && presentation.getDisplay().getDisplayId() != second.getDisplayId()) {
            deactivate();
            activate(second);
        }
    }

    private void activate(@NonNull Display display) {
        int surface = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurface,
                android.graphics.Color.BLACK);

        SecondScreen p = new SecondScreen(activity, display);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(surface);
        // ボトムバーのガラス(Compose)が、移った先の画面でもアクティビティの寿命で動くようにする
        ViewTreeLifecycleOwner.set(column, activity);
        ViewTreeViewModelStoreOwner.set(column, activity);
        ViewTreeSavedStateRegistryOwner.set(column, activity);

        // 下の画面の全体: バーとページの列の上に、操作を止める時の幕を重ねる
        FrameLayout root = new FrameLayout(activity);
        root.addView(column, matchParent());
        ViewTreeLifecycleOwner.set(root, activity);
        ViewTreeViewModelStoreOwner.set(root, activity);
        ViewTreeSavedStateRegistryOwner.set(root, activity);
        View scrim = new View(activity);
        scrim.setBackgroundColor(android.graphics.Color.argb(140, 0, 0, 0));
        scrim.setClickable(true); // 下へタッチを通さない
        scrim.setFocusable(false);
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        root.addView(scrim, matchParent());
        ImeProxy ime = new ImeProxy(activity);
        root.addView(ime, 0, new FrameLayout.LayoutParams(1, 1));

        FrameLayout pageArea = new FrameLayout(activity);
        column.addView(pageArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ImageView wp = new ImageView(activity);
        wp.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wp.setVisibility(View.GONE);
        pageArea.addView(wp, matchParent());

        TextureView bottom = new TextureView(activity);
        bottom.setOpaque(true);
        bottom.setSurfaceTextureListener(new OutputListener(DualScreenCompositor.OUTPUT_BOTTOM));
        bottom.setOnTouchListener(this::onBottomTouch);
        pageArea.addView(bottom, matchParent());
        VirtualMouse.CursorView cursor = new VirtualMouse.CursorView(activity);
        pageArea.addView(cursor, matchParent());

        try {
            p.setContentView(root);
            if (p.getWindow() != null) {
                p.getWindow().setBackgroundDrawable(new ColorDrawable(surface));
                // キーボードが出たら URL バーをその上へ持ち上げる
                p.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            p.setOnBackListener(host::onBackFromSecondScreen);
            p.setInputHost(host);
            p.show();
            // 下の画面でも、触れた所にタッチエフェクトを出す
            if (p.getWindow() != null) TouchEffectView.attach(p.getWindow());
            FocusHighlight.attach(p.getWindow());
        } catch (WindowManager.InvalidDisplayException | IllegalStateException e) {
            Log.w(TAG, "2 つ目の画面に表示できませんでした", e);
            return;
        }

        presentation = p;
        imeProxy = ime;
        bottomCursor = cursor;
        lockScrim = scrim;
        locked = false;
        bottomView = bottom;
        wallpaper = wp;
        compositor = new DualScreenCompositor();
        compositor.setClearColor(surface);
        active = true;

        // 上の画面: ページの描画面(タブのビューの上に重ね、タッチはタブへ通す)
        TextureView top = new TextureView(activity);
        top.setOpaque(true);
        top.setClickable(false);
        top.setFocusable(false);
        top.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        top.setSurfaceTextureListener(new OutputListener(DualScreenCompositor.OUTPUT_TOP));
        top.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateGeometry());
        // スタートページ(startPageContainer)より下、タブ(webViewContainer)より上
        topPageArea.addView(top, Math.min(1, topPageArea.getChildCount()), matchParent());
        topView = top;

        // 下の画面: ページの上に URL の候補、下に URL バーとボトムバー
        if (pageOverlay != null) {
            moveInto(pageOverlay, pageArea, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        for (int i = 0; i < bars.length; i++) {
            View bar = bars[i];
            int h = bar.getLayoutParams() != null ? bar.getLayoutParams().height : ViewGroup.LayoutParams.WRAP_CONTENT;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
            // ページと URL バーの間に少し余白を空ける
            if (i == 0) lp.topMargin = Math.round(8 * activity.getResources().getDisplayMetrics().density);
            moveInto(bar, column, lp);
            bar.setTranslationY(0f);
        }

        startPageShown = host.isStartPageShowing();
        updateBottomContent();
        host.onDualScreenChanged(true);
        handler.post(syncRunnable);
    }

    private void deactivate() {
        if (!active) return;
        active = false;
        handler.removeCallbacks(syncRunnable);

        GeckoTabView tab = boundTab;
        if (tab != null) {
            returnIme(tab);
            tab.detachExternalDisplay();
        }
        boundTab = null;
        imeProxy = null;
        bottomCursor = null;

        // 移したビューを元の場所へ(取り出した順の逆に戻すと元の並び順になる)
        for (int i = moved.size() - 1; i >= 0; i--) {
            Moved m = moved.get(i);
            ViewGroup parent = (ViewGroup) m.view.getParent();
            if (parent != null) parent.removeView(m.view);
            m.parent.addView(m.view, Math.min(m.index, m.parent.getChildCount()), m.params);
            m.view.setTranslationY(m.translationY);
        }
        moved.clear();

        if (topView != null) {
            topPageArea.removeView(topView);
            topView = null;
        }
        if (presentation != null) {
            presentation.setOnBackListener(null);
            presentation.setInputHost(null);
            try {
                presentation.dismiss();
            } catch (Exception ignored) {
            }
            presentation = null;
        }
        bottomView = null;
        wallpaper = null;
        lockScrim = null;
        locked = false;
        if (compositor != null) {
            compositor.release();
            compositor = null;
        }
        host.onDualScreenChanged(false);
    }

    private void moveInto(@NonNull View view, @NonNull ViewGroup parent, @NonNull ViewGroup.LayoutParams lp) {
        if (!(view.getParent() instanceof ViewGroup)) return;
        Moved m = new Moved(view);
        moved.add(m);
        m.parent.removeView(view);
        parent.addView(view, lp);
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    // ------------------------------------------------------------------
    // ページの描画面
    // ------------------------------------------------------------------

    @Override
    public void onExternalDisplayAcquired(@NonNull GeckoTabView tab, @NonNull GeckoDisplay display) {
        if (!active || compositor == null) {
            handler.post(tab::detachExternalDisplay);
            return;
        }
        boundTab = tab;
        // バーは画面に固定なので、Gecko から見て動くツールバーは無い
        display.setDynamicToolbarMaxHeight(0);
        display.setVerticalClipping(0);
        compositor.bindDisplay(display);
        updateGeometry();
    }

    @Override
    public void onExternalDisplayReleased(@NonNull GeckoTabView tab, @NonNull GeckoDisplay display) {
        if (compositor != null && compositor.getDisplay() == display) compositor.unbindDisplay();
        returnIme(tab);
        if (tab == boundTab) {
            boundTab = null;
            // タブが自分で外れた(背面に回った・作り直した)。次の描画でつなぎ直す
            if (active) handler.post(syncRunnable);
        }
    }

    /** 上下の画面の大きさから、ページの描画面の大きさと切り分け方を決める。 */
    private void updateGeometry() {
        DualScreenCompositor c = compositor;
        TextureView top = topView;
        if (c == null || top == null) return;
        int width = top.getWidth();
        int topHeight = top.getHeight();
        if (width <= 0 || topHeight <= 0) return;
        int height = topHeight;
        TextureView bottom = bottomView;
        if (!fullScreen && bottom != null && bottom.getWidth() > 0 && bottom.getHeight() > 0) {
            // 下の画面は幅を合わせて縮めて出すので、その縮め方で何ピクセル分のページが入るか
            height += Math.round(bottom.getHeight() * (width / (float) bottom.getWidth()));
        }
        c.setPageGeometry(width, topHeight, height);
        GeckoDisplay d = c.getDisplay();
        if (d != null) {
            int[] loc = new int[2];
            top.getLocationOnScreen(loc);
            d.screenOriginChanged(loc[0], loc[1]);
        }
    }

    /** 下の画面のページ部分に何を出すか(ページの続き / スタートページの続きの壁紙 / 何も出さない)。 */
    private void updateBottomContent() {
        DualScreenCompositor c = compositor;
        if (c == null) return;
        boolean page = !startPageShown && !fullScreen;
        c.setOutputVisible(DualScreenCompositor.OUTPUT_BOTTOM, page);
        ImageView wp = wallpaper;
        if (wp != null) {
            boolean show = startPageShown && !fullScreen;
            if (show && wp.getVisibility() != View.VISIBLE) AppWallpaper.apply(wp, null);
            wp.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (bottomView != null) bottomView.setVisibility(startPageShown ? View.INVISIBLE : View.VISIBLE);
    }

    /** 描き写し先の TextureView の準備・大きさの変化・破棄を描画側へ伝える。 */
    private final class OutputListener implements TextureView.SurfaceTextureListener {
        private final int index;

        OutputListener(int index) {
            this.index = index;
        }

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture st, int width, int height) {
            if (compositor == null) return;
            compositor.setOutput(index, st, width, height);
            updateGeometry();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture st, int width, int height) {
            if (compositor == null) return;
            compositor.resizeOutput(index, width, height);
            updateGeometry();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture st) {
            if (compositor == null) return true;
            compositor.setOutput(index, null, 0, 0);
            // 描画スレッドが使い終わってから解放する
            compositor.releaseOutputTexture(st);
            if (index == DualScreenCompositor.OUTPUT_BOTTOM) updateGeometry();
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture st) {
        }
    }

    // ------------------------------------------------------------------
    // 下の画面のタッチ
    // ------------------------------------------------------------------

    /**
     * 下の画面に触れた位置を、ページ上の位置(上の画面のタブのビューから見た位置)に直してタブへ渡す。
     * 下の画面はページを幅に合わせて縮めて出しているので、その分だけ拡大して上の画面の高さを足す。
     */
    @SuppressLint("ClickableViewAccessibility")
    private boolean onBottomTouch(View v, MotionEvent ev) {
        GeckoTabView tab = boundTab;
        TextureView top = topView;
        if (tab == null || top == null || v.getWidth() <= 0 || top.getWidth() <= 0) return false;
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) takeIme(tab);
        float scale = top.getWidth() / (float) v.getWidth();
        float offsetY = top.getHeight();
        int[] origin = new int[2];
        tab.getLocationOnScreen(origin);

        int n = ev.getPointerCount();
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[n];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[n];
        for (int i = 0; i < n; i++) {
            props[i] = new MotionEvent.PointerProperties();
            ev.getPointerProperties(i, props[i]);
            coords[i] = new MotionEvent.PointerCoords();
            ev.getPointerCoords(i, coords[i]);
            // 画面上の位置で作り、あとでビューの位置へずらす(Gecko は差から画面上の位置を求める)
            coords[i].x = coords[i].x * scale + origin[0];
            coords[i].y = coords[i].y * scale + offsetY + origin[1];
            coords[i].size *= scale;
            coords[i].touchMajor *= scale;
            coords[i].touchMinor *= scale;
            coords[i].toolMajor *= scale;
            coords[i].toolMinor *= scale;
        }
        MotionEvent e = MotionEvent.obtain(ev.getDownTime(), ev.getEventTime(), ev.getAction(), n, props, coords,
                ev.getMetaState(), ev.getButtonState(), 1f, 1f, ev.getDeviceId(), ev.getEdgeFlags(),
                ev.getSource(), ev.getFlags());
        e.offsetLocation(-origin[0], -origin[1]);
        try {
            tab.onTouchEvent(e);
        } finally {
            e.recycle();
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 仮想マウス
    // ------------------------------------------------------------------

    /** 下の画面に出ているページの高さ(上の画面のタブのビューの大きさに直した値)。出ていなければ 0。 */
    float bottomPageHeight() {
        TextureView top = topView;
        TextureView bottom = bottomView;
        if (!active || fullScreen || startPageShown || top == null || bottom == null
                || bottom.getWidth() <= 0 || top.getWidth() <= 0) {
            return 0f;
        }
        return bottom.getHeight() * (top.getWidth() / (float) bottom.getWidth());
    }

    /**
     * 下の画面のカーソル。位置はページ上の位置(上の画面のタブのビューから見た位置)で受け取り、
     * 下の画面の縮め方に合わせて描く。
     */
    void setBottomCursor(boolean shown, float pageX, float pageY, boolean scrollMode) {
        VirtualMouse.CursorView c = bottomCursor;
        TextureView top = topView;
        TextureView bottom = bottomView;
        if (c == null) return;
        if (top == null || bottom == null || top.getWidth() <= 0 || bottom.getWidth() <= 0) {
            c.set(false, 0, 0, scrollMode);
            return;
        }
        float scale = top.getWidth() / (float) bottom.getWidth();
        c.set(shown && bottomPageHeight() > 0, pageX / scale, (pageY - top.getHeight()) / scale, scrollMode);
    }

    void setBottomCursorPressed(boolean pressed) {
        if (bottomCursor != null) bottomCursor.setPressed(pressed);
    }

    // ------------------------------------------------------------------
    // 下の画面でのキーボード入力
    // ------------------------------------------------------------------

    /*
     * 下の画面に触れると、キーボードは下の画面の窓(この Presentation)につながる。ページの入力欄は
     * 上の画面のタブのビューが受け口なので、そのままでは打った文字がどこにも届かない。
     * 下の画面のページに触れた間は、下の画面に置いた受け口をタブの入力の窓口にして、Gecko の
     * キーボードの出し入れと文字の受け渡しをこの受け口から行う。
     */

    /** 下の画面の受け口をタブの入力の窓口にする。 */
    private void takeIme(@NonNull GeckoTabView tab) {
        ImeProxy ime = imeProxy;
        if (ime == null) return;
        org.mozilla.geckoview.SessionTextInput input = tab.getGeckoSession().getTextInput();
        if (input.getView() != ime) input.setView(ime);
        // 普段は十字キーで選ばれないよう、入力を受ける間だけフォーカスを取れるようにする
        ime.setFocusable(true);
        ime.setFocusableInTouchMode(true);
        if (!ime.hasFocus()) ime.requestFocus();
    }

    /** 入力の窓口をタブのビューへ戻す(上の画面に触れた・タブが替わった時)。 */
    private void returnIme(@NonNull GeckoTabView tab) {
        ImeProxy ime = imeProxy;
        if (ime == null || tab.isDestroyed()) return;
        org.mozilla.geckoview.SessionTextInput input = tab.getGeckoSession().getTextInput();
        if (input.getView() == ime) input.setView(tab);
        ime.setFocusable(false);
    }

    /** 下の画面に置く、見えないキーボードの受け口。中身は表示中のタブの入力へそのまま渡す。 */
    private final class ImeProxy extends View {
        ImeProxy(@NonNull Context context) {
            super(context);
            setFocusable(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Nullable
        private org.mozilla.geckoview.SessionTextInput input() {
            GeckoTabView tab = boundTab;
            if (tab == null || tab.isDestroyed()) return null;
            org.mozilla.geckoview.SessionTextInput in = tab.getGeckoSession().getTextInput();
            return in.getView() == this ? in : null;
        }

        @Override
        public boolean onCheckIsTextEditor() {
            return input() != null;
        }

        @Nullable
        @Override
        public android.view.inputmethod.InputConnection onCreateInputConnection(
                @NonNull android.view.inputmethod.EditorInfo outAttrs) {
            org.mozilla.geckoview.SessionTextInput in = input();
            return in != null ? in.onCreateInputConnection(outAttrs) : null;
        }

        @Override
        public boolean onKeyPreIme(int keyCode, android.view.KeyEvent event) {
            org.mozilla.geckoview.SessionTextInput in = input();
            return (in != null && in.onKeyPreIme(keyCode, event)) || super.onKeyPreIme(keyCode, event);
        }

        @Override
        public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
            org.mozilla.geckoview.SessionTextInput in = input();
            return (in != null && in.onKeyDown(keyCode, event)) || super.onKeyDown(keyCode, event);
        }

        @Override
        public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
            org.mozilla.geckoview.SessionTextInput in = input();
            return (in != null && in.onKeyUp(keyCode, event)) || super.onKeyUp(keyCode, event);
        }

        @Override
        public boolean onKeyMultiple(int keyCode, int repeatCount, android.view.KeyEvent event) {
            org.mozilla.geckoview.SessionTextInput in = input();
            return (in != null && in.onKeyMultiple(keyCode, repeatCount, event))
                    || super.onKeyMultiple(keyCode, repeatCount, event);
        }

        @Override
        public void onWindowFocusChanged(boolean hasWindowFocus) {
            super.onWindowFocusChanged(hasWindowFocus);
            // 上の画面に触れて下の画面の窓がフォーカスを失ったら、入力の窓口をタブへ戻す
            GeckoTabView tab = boundTab;
            if (!hasWindowFocus && tab != null) returnIme(tab);
        }
    }

    // ------------------------------------------------------------------
    // 画面のつなぎ外し
    // ------------------------------------------------------------------

    @Override
    public void onDisplayAdded(int displayId) {
        update();
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        update();
    }

    @Override
    public void onDisplayChanged(int displayId) {
        update();
    }

    /** 下の画面に出す窓。戻るキーで閉じずにブラウザの「戻る」として扱う。 */
    private static final class SecondScreen extends Presentation {
        @Nullable private Runnable onBack;
        @Nullable private Host inputHost;

        void setInputHost(@Nullable Host host) {
            this.inputHost = host;
        }

        @Override
        public boolean dispatchKeyEvent(@NonNull android.view.KeyEvent event) {
            // コントローラーのボタンは、どちらの画面に来ても同じ操作にする
            if (inputHost != null && inputHost.onSecondScreenKey(event)) return true;
            return super.dispatchKeyEvent(event);
        }

        @Override
        public boolean dispatchGenericMotionEvent(@NonNull MotionEvent ev) {
            if (inputHost != null && inputHost.onSecondScreenMotion(ev)) return true;
            return super.dispatchGenericMotionEvent(ev);
        }

        SecondScreen(@NonNull Context outer, @NonNull Display display) {
            super(outer, display);
            setCancelable(false);
        }

        void setOnBackListener(@Nullable Runnable onBack) {
            this.onBack = onBack;
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onBackPressed() {
            if (onBack != null) onBack.run();
        }
    }
}
