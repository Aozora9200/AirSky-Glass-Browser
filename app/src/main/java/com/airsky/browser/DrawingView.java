package com.airsky.browser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * メモの手書き・図形描画の画用紙。
 * ペン・マーカー・消しゴムの手書きと、直線・四角・丸・矢印の図形を描ける。元に戻す/やり直しができる。
 */
public class DrawingView extends View {

    static final int TOOL_PEN = 0;
    static final int TOOL_MARKER = 1;
    static final int TOOL_ERASER = 2;
    static final int TOOL_LINE = 3;
    static final int TOOL_RECT = 4;
    static final int TOOL_OVAL = 5;
    static final int TOOL_ARROW = 6;

    interface OnChangeListener {
        void onChange();
    }

    private static final class Stroke {
        int tool;
        int color;
        float width;
        boolean fill;
        final Path path = new Path();
        float x0, y0, x1, y1;
    }

    private final List<Stroke> strokes = new ArrayList<>();
    private final List<Stroke> redo = new ArrayList<>();
    @Nullable private Stroke current;
    @Nullable private Bitmap base;
    @Nullable private Bitmap layer;
    @Nullable private Canvas layerCanvas;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();

    private int tool = TOOL_PEN;
    @ColorInt private int color = Color.BLACK;
    private float width = 8f;
    private boolean fill;
    @ColorInt private int paperColor = Color.WHITE;
    private float lastX, lastY;
    @Nullable private OnChangeListener listener;

    public DrawingView(Context context) {
        this(context, null);
    }

    public DrawingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        width = 4f * getResources().getDisplayMetrics().density;
    }

    void setTool(int tool) {
        this.tool = tool;
    }

    int getTool() {
        return tool;
    }

    void setColor(@ColorInt int color) {
        this.color = color;
    }

    void setStrokeWidth(float px) {
        this.width = px;
    }

    void setFill(boolean fill) {
        this.fill = fill;
    }

    void setPaperColor(@ColorInt int c) {
        paperColor = c;
        invalidate();
    }

    void setOnChangeListener(@Nullable OnChangeListener l) {
        listener = l;
    }

    /** 前に描いた絵を下に敷く(続きを描く時) */
    void setBase(@Nullable Bitmap bmp) {
        base = bmp;
        rebuildLayer();
    }

    boolean canUndo() {
        return !strokes.isEmpty();
    }

    boolean canRedo() {
        return !redo.isEmpty();
    }

    boolean isBlank() {
        return strokes.isEmpty() && base == null;
    }

    void undo() {
        if (strokes.isEmpty()) return;
        redo.add(strokes.remove(strokes.size() - 1));
        rebuildLayer();
        changed();
    }

    void redo() {
        if (redo.isEmpty()) return;
        strokes.add(redo.remove(redo.size() - 1));
        rebuildLayer();
        changed();
    }

    void clear() {
        strokes.clear();
        redo.clear();
        base = null;
        rebuildLayer();
        changed();
    }

    /** 画用紙の色と、敷いた絵と、描いたものを 1 枚にする */
    Bitmap export() {
        Bitmap out = Bitmap.createBitmap(Math.max(1, getWidth()), Math.max(1, getHeight()), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        drawContent(c);
        return out;
    }

    private void changed() {
        invalidate();
        if (listener != null) listener.onChange();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        layerCanvas = new Canvas(layer);
        rebuildLayer();
    }

    private void rebuildLayer() {
        redrawLayer(null);
        invalidate();
    }

    /** 敷いた絵と描いた線を描き直す(消しゴムで敷いた絵も消せるよう、同じ層に描く) */
    private void redrawLayer(@Nullable Stroke extra) {
        if (layer == null || layerCanvas == null) return;
        layer.eraseColor(Color.TRANSPARENT);
        if (base != null) {
            Rect src = new Rect(0, 0, base.getWidth(), base.getHeight());
            float scale = Math.min(getWidth() / (float) base.getWidth(), getHeight() / (float) base.getHeight());
            float w = base.getWidth() * scale, h = base.getHeight() * scale;
            rect.set((getWidth() - w) / 2f, 0, (getWidth() + w) / 2f, h);
            layerCanvas.drawBitmap(base, src, rect, bitmapPaint);
        }
        for (Stroke s : strokes) drawStroke(layerCanvas, s);
        if (extra != null) drawStroke(layerCanvas, extra);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        drawContent(canvas);
        // 描いている途中の図形(消しゴム以外)は、離すまで上に重ねて見せる
        if (current != null && current.tool != TOOL_PEN && current.tool != TOOL_MARKER && current.tool != TOOL_ERASER) {
            drawStroke(canvas, current);
        }
    }

    private void drawContent(Canvas canvas) {
        canvas.drawColor(paperColor);
        if (layer != null) canvas.drawBitmap(layer, 0, 0, bitmapPaint);
    }

    private void drawStroke(Canvas c, Stroke s) {
        paint.setXfermode(null);
        paint.setShader(null);
        paint.setColor(s.color);
        paint.setStrokeWidth(s.width);
        paint.setStyle(Paint.Style.STROKE);
        switch (s.tool) {
            case TOOL_ERASER:
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                c.drawPath(s.path, paint);
                paint.setXfermode(null);
                break;
            case TOOL_MARKER:
                paint.setAlpha(110);
                paint.setStrokeCap(Paint.Cap.SQUARE);
                c.drawPath(s.path, paint);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setAlpha(255);
                break;
            case TOOL_LINE:
                c.drawLine(s.x0, s.y0, s.x1, s.y1, paint);
                break;
            case TOOL_ARROW: {
                c.drawLine(s.x0, s.y0, s.x1, s.y1, paint);
                double angle = Math.atan2(s.y1 - s.y0, s.x1 - s.x0);
                float head = Math.max(s.width * 3.5f, 24f);
                Path p = new Path();
                p.moveTo(s.x1, s.y1);
                p.lineTo((float) (s.x1 - head * Math.cos(angle - Math.PI / 7)),
                        (float) (s.y1 - head * Math.sin(angle - Math.PI / 7)));
                p.moveTo(s.x1, s.y1);
                p.lineTo((float) (s.x1 - head * Math.cos(angle + Math.PI / 7)),
                        (float) (s.y1 - head * Math.sin(angle + Math.PI / 7)));
                c.drawPath(p, paint);
                break;
            }
            case TOOL_RECT:
                rect.set(Math.min(s.x0, s.x1), Math.min(s.y0, s.y1), Math.max(s.x0, s.x1), Math.max(s.y0, s.y1));
                if (s.fill) paint.setStyle(Paint.Style.FILL_AND_STROKE);
                c.drawRoundRect(rect, s.width, s.width, paint);
                break;
            case TOOL_OVAL:
                rect.set(Math.min(s.x0, s.x1), Math.min(s.y0, s.y1), Math.max(s.x0, s.x1), Math.max(s.y0, s.y1));
                if (s.fill) paint.setStyle(Paint.Style.FILL_AND_STROKE);
                c.drawOval(rect, paint);
                break;
            case TOOL_PEN:
            default:
                c.drawPath(s.path, paint);
                break;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                getParent().requestDisallowInterceptTouchEvent(true);
                Stroke s = new Stroke();
                s.tool = tool;
                s.color = color;
                s.width = tool == TOOL_MARKER ? width * 3f : tool == TOOL_ERASER ? width * 4f : width;
                s.fill = fill;
                s.path.moveTo(x, y);
                s.x0 = s.x1 = x;
                s.y0 = s.y1 = y;
                current = s;
                lastX = x;
                lastY = y;
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (current == null) return false;
                if (current.tool == TOOL_PEN || current.tool == TOOL_MARKER || current.tool == TOOL_ERASER) {
                    // なめらかにするため、前の点との中点を通る曲線でつなぐ
                    for (int i = 0; i < e.getHistorySize(); i++) {
                        addPoint(current, e.getHistoricalX(i), e.getHistoricalY(i));
                    }
                    addPoint(current, x, y);
                    redrawLayer(current);
                } else {
                    current.x1 = x;
                    current.y1 = y;
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (current == null) return false;
                if (current.tool == TOOL_PEN || current.tool == TOOL_MARKER || current.tool == TOOL_ERASER) {
                    current.path.lineTo(x, y);
                    // 点を打っただけの時も、点として残す
                    if (Math.abs(current.x0 - x) < 1 && Math.abs(current.y0 - y) < 1) current.path.lineTo(x + 0.1f, y + 0.1f);
                } else {
                    current.x1 = x;
                    current.y1 = y;
                }
                strokes.add(current);
                redo.clear();
                current = null;
                rebuildLayer();
                changed();
                return true;
            }
            default:
                return super.onTouchEvent(e);
        }
    }

    private void addPoint(Stroke s, float x, float y) {
        s.path.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f);
        lastX = x;
        lastY = y;
    }
}
