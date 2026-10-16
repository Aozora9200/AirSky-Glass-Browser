package com.airsky.browser;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * ページ用の「下へ引っ張って更新」。
 *
 * <p>そのままの SwipeRefreshLayout は「ページの一番上にいるか」しか見ないので、次のような時にも
 * 更新が始まってしまう。この版では、指の動きごとに引っ張りを始めてよいかを確かめる。</p>
 * <ul>
 *   <li>2 本目の指が触れた(ピンチで拡大・縮小している)</li>
 *   <li>ページ自身がタッチを使っている(地図・雨雲レーダー・スライダーなど)</li>
 *   <li>ページの一番上でも、中の枠(チャット欄・一覧など)がスクロールしている</li>
 *   <li>拡大していて、上にまだ動かせる</li>
 *   <li>横向きの動きが主(カルーセルなど)</li>
 * </ul>
 */
public class BrowserSwipeRefreshLayout extends SwipeRefreshLayout {

    /** 引っ張りを始めてよいか。null はまだ分からない(ページの返事待ち)。 */
    public interface PullGate {
        @Nullable
        Boolean canStartPull();
    }

    @Nullable private PullGate gate;
    private final int touchSlop;

    /** この指の動きでは更新しない。 */
    private boolean blocked;
    /** 引っ張りを始めた(ここから先は自分でタッチを受けている)。 */
    private boolean pulling;
    private float downX, downY;

    public BrowserSwipeRefreshLayout(@NonNull Context context) {
        this(context, null);
    }

    public BrowserSwipeRefreshLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setPullGate(@Nullable PullGate gate) {
        this.gate = gate;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                blocked = false;
                pulling = false;
                downX = ev.getX();
                downY = ev.getY();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                // ピンチ操作。この指の動きが終わるまで更新しない
                blocked = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (blocked) break;
                float dx = Math.abs(ev.getX() - downX);
                float dy = ev.getY() - downY;
                if (dx > touchSlop && dx > Math.abs(dy)) {
                    // 横向きの動きが主
                    blocked = true;
                    break;
                }
                if (dy < -touchSlop) {
                    // 上へ動かした(ページのスクロール)
                    blocked = true;
                    break;
                }
                if (gate != null) {
                    Boolean ok = gate.canStartPull();
                    if (ok == null) {
                        // ページの返事がまだ。決めずに待つ(SwipeRefreshLayout には渡さない)
                        return false;
                    }
                    if (!ok) blocked = true;
                }
                break;
            default:
                break;
        }
        if (blocked) return false;
        boolean intercept = super.onInterceptTouchEvent(ev);
        if (intercept) pulling = true;
        return intercept;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (blocked) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                blocked = false;
                pulling = false;
            }
            return true;
        }
        if (pulling && action == MotionEvent.ACTION_POINTER_DOWN && !isRefreshing()) {
            // 引っ張っている途中で 2 本目の指: ピンチにしたいので、引っ張りをやめて円を元に戻す
            blocked = true;
            cancelPull(ev);
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) pulling = false;
        return super.onTouchEvent(ev);
    }

    /** 引っ張り始めた位置で指を離したことにして、更新せずに円を引っ込める。 */
    private void cancelPull(MotionEvent ev) {
        MotionEvent up = MotionEvent.obtain(ev.getDownTime(), ev.getEventTime(),
                MotionEvent.ACTION_UP, downX, downY, 0);
        try {
            super.onTouchEvent(up);
        } finally {
            up.recycle();
        }
        pulling = false;
    }
}
