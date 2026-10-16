package com.airsky.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.shape.MaterialShapeDrawable;

/**
 * タブレット UI でバーを上に出している時、メニューのボトムシートを「上から下りてくるシート」にする。
 *
 * <p>BottomSheetBehavior は下からしか出せないので、シートを入れている CoordinatorLayout を上下反転させ、
 * シートの中身をもう一度反転して正しい向きに戻す。こうすると、展開・隠すといった BottomSheetBehavior の
 * 動きがそのまま上下逆になる。角の丸みも下側に付く。</p>
 *
 * <p>上から出すシートは、下から出すシートを裏返しただけに見えないよう、次のように作り変える。</p>
 * <ul>
 *   <li>つまみ(tag="sheet_handle")を中身のいちばん下へ移す(引っぱって閉じる側に付ける)。</li>
 *   <li>シートの高さを抑え、画面の下(ナビゲーションバー)まで届かないようにする。中身が長い時は
 *       シートの中でスクロールし、丸い下の角がいつも見える。</li>
 *   <li>上へスワイプすると閉じる。中身をいちばん下までスクロールした後に上へ引くとシートごと閉じる
 *       (下から出すシートで、いちばん上まで戻した後に下へ引くと閉じるのと同じ)。</li>
 * </ul>
 *
 * <p>ウィンドウはツールバーの下端から下だけにするので、ツールバーは暗くならず、シートはツールバーの
 * 下から出てくるように見える。ツールバーの部分を押すと、外を押した時と同じく閉じる。</p>
 *
 * <p>バーを下に出している時は何もしない(従来のボトムシートのまま)。</p>
 */
final class TopSheet {

    /** シートを上に出す画面(ツールバーの下端の位置を教える)。 */
    interface Host {
        /** ツールバーの下端(画面の上からの px)。 */
        int topSheetAnchorY();
    }

    /** つまみに付ける tag(レイアウトの android:tag)。 */
    static final String HANDLE_TAG = "sheet_handle";

    private static final int TAG_KEY = R.id.top_sheet_installed;
    private static final long ENTER_MS = 300;
    /** シートの下と画面の下(ナビゲーションバー)の間に空ける隙間(dp)。 */
    private static final int BOTTOM_GAP_DP = 24;

    private TopSheet() {
    }

    /** タブレット UI でバーが上にある時だけ、シートを上から出すようにする。 */
    static <T extends BottomSheetDialog> T adapt(@NonNull Activity activity, @NonNull T dialog) {
        if (!(activity instanceof Host) || !TabletUi.barsOnTop(activity)) return dialog;
        Window w = dialog.getWindow();
        if (w == null) return dialog;
        Host host = (Host) activity;
        View decor = w.getDecorView();
        decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                install(activity, host, dialog);
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
            }
        });
        return dialog;
    }

    private static void install(@NonNull Activity activity, @NonNull Host host, @NonNull BottomSheetDialog dialog) {
        Window w = dialog.getWindow();
        if (w == null) return;
        View coordinator = w.findViewById(com.google.android.material.R.id.coordinator);
        ViewGroup sheet = w.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (coordinator == null || sheet == null) return;
        // ページを中に出すシート(プレビューなど)は、見出しを引いて動かす作りなのでそのままにする
        boolean menu = !(dialog instanceof HeaderDragSheetDialog);
        float density = activity.getResources().getDisplayMetrics().density;

        if (coordinator.getTag(TAG_KEY) == null) {
            coordinator.setTag(TAG_KEY, Boolean.TRUE);
            if (menu) {
                moveHandleToBottom(sheet, density);
                wrapForSwipeUp(dialog, sheet);
                // ドラッグは自前で行う(反転したシートでは BottomSheetBehavior の引っぱる向きが中身と合わない)
                dialog.getBehavior().setDraggable(false);
                clipToRoundedBottom(sheet, density);
            }
            coordinator.setScaleY(-1f);
            for (int i = 0; i < sheet.getChildCount(); i++) sheet.getChildAt(i).setScaleY(-1f);
            // あとから入れ替えられた中身も正しい向きにする
            sheet.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                @Override
                public void onChildViewAdded(View parent, View child) {
                    child.setScaleY(-1f);
                }

                @Override
                public void onChildViewRemoved(View parent, View child) {
                }
            });
        }

        // ウィンドウをツールバーの下端から画面の下(ナビゲーションバーの上)までにする
        int top = Math.max(0, host.topSheetAnchorY());
        View activityDecor = activity.getWindow().getDecorView();
        Rect visible = new Rect();
        activityDecor.getWindowVisibleDisplayFrame(visible);
        int bottom = visible.bottom > top ? visible.bottom : activityDecor.getHeight();
        WindowManager.LayoutParams a = w.getAttributes();
        a.gravity = Gravity.TOP;
        a.y = top;
        a.height = bottom > top ? bottom - top : WindowManager.LayoutParams.MATCH_PARENT;
        a.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        a.windowAnimations = R.style.TopSheetWindowAnimation;
        // 背景を暗くする(dim)は画面全体にかかってツールバーまで暗くなるので、代わりにウィンドウの中
        // (ツールバーより下)だけを同じ濃さで暗くする
        if ((a.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
            float dim = a.dimAmount;
            a.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            View outside = w.findViewById(com.google.android.material.R.id.touch_outside);
            if (outside != null) outside.setBackgroundColor(Color.argb(Math.round(dim * 255), 0, 0, 0));
        }
        w.setAttributes(a);
        if (menu && a.height > 0) {
            // 下の角の丸みが画面の下で切れないよう、シートの高さを抑える(中身はシートの中でスクロールする)
            dialog.getBehavior().setMaxHeight(Math.max(0, a.height - Math.round(BOTTOM_GAP_DP * density)));
        }

        // ツールバーの下から滑り出す(ウィンドウの上端で切れるので、ツールバーの裏から出てくるように見える)
        coordinator.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                coordinator.getViewTreeObserver().removeOnPreDrawListener(this);
                int shown = coordinator.getHeight() - sheet.getTop();
                if (shown <= 0) return true;
                coordinator.setTranslationY(-shown);
                coordinator.animate().translationY(0f).setDuration(ENTER_MS)
                        .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
                return true;
            }
        });
    }

    /**
     * スクロールした中身が下の丸い角からはみ出さないように、シートを角の丸みで切り取る。
     * シートは反転しているので、シートの中での上端が画面での下端になる。
     */
    private static void clipToRoundedBottom(@NonNull View sheet, float density) {
        float r = 28 * density;
        if (sheet.getBackground() instanceof MaterialShapeDrawable) {
            float resolved = ((MaterialShapeDrawable) sheet.getBackground()).getTopLeftCornerResolvedSize();
            if (resolved > 0) r = resolved;
        }
        final float radius = r;
        sheet.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                // 画面で上になる側(シートの中での下端)は丸めない
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + Math.round(radius), radius);
            }
        });
        sheet.setClipToOutline(true);
    }

    /** つまみを中身のいちばん下へ移す(上の余白・下の余白も入れ替える)。 */
    private static void moveHandleToBottom(@NonNull ViewGroup sheet, float density) {
        View handle = sheet.findViewWithTag(HANDLE_TAG);
        if (handle == null || !(handle.getParent() instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) handle.getParent();
        if (parent.getOrientation() != LinearLayout.VERTICAL) return;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) handle.getLayoutParams();
        int oldTop = lp.topMargin;
        int oldBottom = lp.bottomMargin;
        parent.removeView(handle);
        // つまみの上にあった余白を、ツールバーとの間の余白として残す
        parent.setPadding(parent.getPaddingLeft(), parent.getPaddingTop() + Math.round(8 * density),
                parent.getPaddingRight(), parent.getPaddingBottom());
        lp.topMargin = Math.max(oldBottom, Math.round(12 * density));
        // シートの下の端に近づけすぎないよう、下の余白(中身の paddingBottom)を減らした分だけ空ける
        lp.bottomMargin = Math.max(0, oldTop - parent.getPaddingBottom());
        parent.addView(handle, lp);
    }

    /** シートの中身を、上へのスワイプで閉じる入れ物に入れ替える。 */
    private static void wrapForSwipeUp(@NonNull BottomSheetDialog dialog, @NonNull ViewGroup sheet) {
        if (sheet.getChildCount() == 0) return;
        SwipeUpLayout wrapper = new SwipeUpLayout(sheet.getContext(), dialog, sheet);
        ViewGroup.LayoutParams first = sheet.getChildAt(0).getLayoutParams();
        while (sheet.getChildCount() > 0) {
            View child = sheet.getChildAt(0);
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            sheet.removeViewAt(0);
            wrapper.addView(child, lp);
        }
        sheet.addView(wrapper, new FrameLayout.LayoutParams(
                first != null ? first.width : ViewGroup.LayoutParams.MATCH_PARENT,
                first != null ? first.height : ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /**
     * 上へのスワイプでシートを閉じる入れ物。中身がまだ下へスクロールできる時は中身のスクロールを優先し、
     * いちばん下まで来ていたら(またはスクロールしない所なら)シートごと上へ引っぱる。
     */
    @SuppressLint("ViewConstructor")
    private static final class SwipeUpLayout extends FrameLayout {

        private final BottomSheetDialog dialog;
        /** 引っぱって動かすシート(反転した CoordinatorLayout の中にあるので、translationY + が画面の上向き)。 */
        private final View sheet;
        private final int touchSlop;
        private final float minFlingVelocity;

        private float downRawX;
        private float downRawY;
        private boolean dragging;
        private boolean canScrollAtDown;
        private float scrimAlphaAtStart = 1f;
        @Nullable private VelocityTracker velocity;

        SwipeUpLayout(@NonNull Context context, @NonNull BottomSheetDialog dialog, @NonNull View sheet) {
            super(context);
            this.dialog = dialog;
            this.sheet = sheet;
            ViewConfiguration vc = ViewConfiguration.get(context);
            touchSlop = vc.getScaledTouchSlop();
            minFlingVelocity = 600 * context.getResources().getDisplayMetrics().density;
            setClipChildren(false);
            setClipToPadding(false);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = ev.getRawX();
                    downRawY = ev.getRawY();
                    dragging = false;
                    canScrollAtDown = canScrollDown(this, ev.getX(), ev.getY());
                    track(ev, true);
                    break;
                case MotionEvent.ACTION_MOVE: {
                    track(ev, false);
                    float dx = ev.getRawX() - downRawX;
                    float dy = ev.getRawY() - downRawY;
                    // 上へ引いていて、中身がもう下へスクロールできない
                    if (dy < -touchSlop && Math.abs(dy) > Math.abs(dx)
                            && !(canScrollAtDown && canScrollDown(this, ev.getX(), ev.getY()))) {
                        startDrag(ev);
                        return true;
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    recycleVelocity();
                    break;
                default:
                    break;
            }
            return false;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // 中身が受け取らなかった所(余白など)を触り始めた
                    downRawX = ev.getRawX();
                    downRawY = ev.getRawY();
                    dragging = false;
                    track(ev, true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    track(ev, false);
                    float dy = ev.getRawY() - downRawY;
                    if (!dragging) {
                        if (dy < -touchSlop && Math.abs(dy) > Math.abs(ev.getRawX() - downRawX)) startDrag(ev);
                        else return true;
                    }
                    moveTo(ev.getRawY());
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    track(ev, false);
                    if (dragging) release();
                    recycleVelocity();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) settle(false, 0f);
                    recycleVelocity();
                    return true;
                default:
                    return true;
            }
        }

        private void startDrag(MotionEvent ev) {
            beginDrag();
            // 引っぱり始めた所を基準にする(touchSlop の分だけ飛ばないように)
            downRawY = ev.getRawY();
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        }

        private void beginDrag() {
            dragging = true;
            sheet.animate().cancel();
            View scrim = dialog.findViewById(com.google.android.material.R.id.touch_outside);
            scrimAlphaAtStart = scrim != null ? scrim.getAlpha() : 1f;
        }

        // ---- 中身(NestedScrollView)をいちばん下までスクロールした後の続きで、シートを引っぱる ----

        /** 中身のスクロールの続きで引っぱっている。 */
        private boolean nestedDragging;

        @Override
        public boolean onStartNestedScroll(View child, View target, int axes) {
            return (axes & View.SCROLL_AXIS_VERTICAL) != 0;
        }

        @Override
        public void onNestedScrollAccepted(View child, View target, int axes) {
            super.onNestedScrollAccepted(child, target, axes);
            nestedDragging = false;
        }

        @Override
        public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
            // 引っぱっている途中で指を下へ戻した時は、中身より先にシートを戻す
            if (nestedDragging && dy < 0) {
                float up = sheet.getTranslationY();
                float back = Math.min(up, -dy);
                sheet.setTranslationY(up - back);
                updateScrim(up - back);
                consumed[1] = -Math.round(back);
            }
        }

        @Override
        public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
            // 中身がいちばん下まで来て、なお上へ引いている
            if (dyUnconsumed > 0) {
                if (!nestedDragging) {
                    beginDrag();
                    nestedDragging = true;
                }
                float up = sheet.getTranslationY() + dyUnconsumed;
                sheet.setTranslationY(up);
                updateScrim(up);
            }
        }

        @Override
        public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
            if (!nestedDragging) return false;
            // velocityY は中身のスクロールの向き(+ が指を上へ)。画面の向きに直して閉じるか決める
            nestedDragging = false;
            float vy = -velocityY;
            float up = sheet.getTranslationY();
            boolean close = vy < -minFlingVelocity || (vy < minFlingVelocity && up > sheet.getHeight() * 0.3f);
            settle(close, vy);
            return true;
        }

        @Override
        public void onStopNestedScroll(View target) {
            super.onStopNestedScroll(target);
            if (nestedDragging) {
                nestedDragging = false;
                settle(sheet.getTranslationY() > sheet.getHeight() * 0.3f, 0f);
            }
        }

        private void moveTo(float rawY) {
            // 上(閉じる向き)へはそのまま、下へは動かさない
            float up = Math.max(0f, downRawY - rawY);
            sheet.setTranslationY(up);
            updateScrim(up);
        }

        private void updateScrim(float up) {
            View scrim = dialog.findViewById(com.google.android.material.R.id.touch_outside);
            int h = Math.max(1, sheet.getHeight());
            if (scrim != null) scrim.setAlpha(scrimAlphaAtStart * Math.max(0f, 1f - up / h));
        }

        private void release() {
            float vy = 0f;
            if (velocity != null) {
                velocity.computeCurrentVelocity(1000);
                vy = velocity.getYVelocity();
            }
            float up = sheet.getTranslationY();
            boolean close = vy < -minFlingVelocity || (vy < minFlingVelocity && up > sheet.getHeight() * 0.3f);
            settle(close, vy);
        }

        private void settle(boolean close, float vy) {
            dragging = false;
            float target = close ? sheet.getHeight() + getResources().getDisplayMetrics().density * 16 : 0f;
            float distance = Math.abs(target - sheet.getTranslationY());
            long duration = close && vy < 0
                    ? Math.max(120L, Math.min(260L, (long) (distance / -vy * 1000f)))
                    : 240L;
            sheet.animate().translationY(target).setDuration(duration)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                    .setUpdateListener(a -> updateScrim(sheet.getTranslationY()))
                    .withEndAction(() -> {
                        sheet.animate().setUpdateListener(null);
                        if (close) {
                            // もう画面の外にあるので、シートを隠す動きは付けずにそのまま閉じる
                            dialog.setDismissWithAnimation(false);
                            dialog.cancel();
                        }
                    })
                    .start();
        }

        private void track(MotionEvent ev, boolean reset) {
            if (reset || velocity == null) {
                recycleVelocity();
                velocity = VelocityTracker.obtain();
            }
            // シートが動くと中の座標もずれるので、画面上の座標で測る
            MotionEvent e = MotionEvent.obtain(ev);
            e.setLocation(ev.getRawX(), ev.getRawY());
            velocity.addMovement(e);
            e.recycle();
        }

        private void recycleVelocity() {
            if (velocity != null) {
                velocity.recycle();
                velocity = null;
            }
        }

        /** (x, y) の下に、まだ下へスクロールできる(指を上へ動かすと中身が動く)ビューがあるか。 */
        private static boolean canScrollDown(@NonNull View v, float x, float y) {
            if (v.getVisibility() != View.VISIBLE) return false;
            if (v.canScrollVertically(1)) return true;
            if (!(v instanceof ViewGroup)) return false;
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View c = g.getChildAt(i);
                float cx = x + g.getScrollX() - c.getLeft() - c.getTranslationX();
                float cy = y + g.getScrollY() - c.getTop() - c.getTranslationY();
                if (cx < 0 || cy < 0 || cx >= c.getWidth() || cy >= c.getHeight()) continue;
                if (canScrollDown(c, cx, cy)) return true;
            }
            return false;
        }
    }
}
