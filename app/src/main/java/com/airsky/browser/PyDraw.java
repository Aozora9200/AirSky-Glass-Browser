package com.airsky.browser;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Python の Canvas(tkinter の Canvas、wx の PaintDC)の図形を描く。
 * 座標は dp。色は "#rrggbb" / "#aarrggbb"(null は塗らない・線を引かない)。
 */
final class PyDraw {

    private PyDraw() {}

    static final class Item {
        String type;
        float[] c = new float[0];
        @Nullable Integer fill, outline;
        float width = 1;
        @Nullable float[] dash;
        @Nullable String arrow;
        String cap = "butt";
        boolean smooth;
        float start, extent = 90;
        String style = "pieslice";
        float radius;
        float alpha = 1;
        // text
        String text = "";
        @Nullable JSONObject font;
        String anchor = "center";
        float wrap;
        String justify = "left";
        float angle;
        // image
        @Nullable JSONObject img;
    }

    @Nullable
    static Integer color(@Nullable Object v) {
        if (!(v instanceof String)) return null;
        String s = (String) v;
        if (s.isEmpty()) return null;
        try {
            return Color.parseColor(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static List<Item> parse(@Nullable JSONArray arr) {
        List<Item> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Item it = new Item();
            it.type = o.optString("t", "line");
            JSONArray c = o.optJSONArray("c");
            if (c != null) {
                it.c = new float[c.length()];
                for (int k = 0; k < c.length(); k++) it.c[k] = (float) c.optDouble(k);
            }
            it.fill = o.isNull("fill") ? null : color(o.opt("fill"));
            it.outline = o.isNull("outline") ? null : color(o.opt("outline"));
            it.width = (float) o.optDouble("w", 1);
            JSONArray dash = o.optJSONArray("dash");
            if (dash != null && dash.length() >= 2) {
                it.dash = new float[dash.length() % 2 == 0 ? dash.length() : dash.length() * 2];
                for (int k = 0; k < it.dash.length; k++) it.dash[k] = (float) dash.optDouble(k % dash.length());
            }
            it.arrow = o.optString("arrow", null);
            it.cap = o.optString("cap", "butt");
            it.smooth = o.optBoolean("smooth");
            it.start = (float) o.optDouble("start", 0);
            it.extent = (float) o.optDouble("extent", 90);
            it.style = o.optString("style", "pieslice");
            it.radius = (float) o.optDouble("radius", 0);
            it.alpha = (float) o.optDouble("alpha", 1);
            it.text = o.optString("text", "");
            it.font = o.optJSONObject("font");
            it.anchor = o.optString("anchor", "center");
            it.wrap = (float) o.optDouble("wrap", 0);
            it.justify = o.optString("justify", "left");
            it.angle = (float) o.optDouble("angle", 0);
            it.img = o.optJSONObject("img");
            out.add(it);
        }
        return out;
    }

    static Typeface typeface(@Nullable JSONObject font) {
        String fam = font != null ? font.optString("family", "sans") : "sans";
        boolean bold = font != null && font.optBoolean("bold");
        boolean italic = font != null && font.optBoolean("italic");
        int style = bold && italic ? Typeface.BOLD_ITALIC : bold ? Typeface.BOLD : italic ? Typeface.ITALIC : Typeface.NORMAL;
        Typeface base;
        switch (fam) {
            case "mono":
                base = Typeface.MONOSPACE;
                break;
            case "serif":
                base = Typeface.SERIF;
                break;
            default:
                base = font != null && font.optBoolean("medium")
                        ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.SANS_SERIF;
                break;
        }
        return Typeface.create(base, style);
    }

    private static final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF rect = new RectF();

    static {
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
    }

    static void drawAll(@NonNull Canvas canvas, @NonNull List<Item> items, float d, @NonNull View host) {
        for (Item it : items) {
            try {
                drawItem(canvas, it, d, host);
            } catch (RuntimeException ignored) {
                // 1 つの図形の不具合で全体を止めない
            }
        }
    }

    private static void setStroke(Item it, float d) {
        strokePaint.setColor(it.outline != null ? it.outline : Color.BLACK);
        strokePaint.setStrokeWidth(Math.max(0.5f, it.width) * d);
        strokePaint.setPathEffect(it.dash != null ? new DashPathEffect(scale(it.dash, d), 0) : null);
        strokePaint.setStrokeCap("round".equals(it.cap) ? Paint.Cap.ROUND
                : "projecting".equals(it.cap) ? Paint.Cap.SQUARE : Paint.Cap.BUTT);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setAlpha(Math.round(Color.alpha(strokePaint.getColor()) * it.alpha));
    }

    private static float[] scale(float[] a, float d) {
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) out[i] = Math.max(1, a[i]) * d;
        return out;
    }

    private static void drawItem(Canvas canvas, Item it, float d, View host) {
        float[] c = it.c;
        switch (it.type) {
            case "rectangle":
            case "oval":
            case "arc": {
                if (c.length < 4) return;
                rect.set(Math.min(c[0], c[2]) * d, Math.min(c[1], c[3]) * d, Math.max(c[0], c[2]) * d, Math.max(c[1], c[3]) * d);
                if (it.fill != null) {
                    fillPaint.setColor(it.fill);
                    fillPaint.setAlpha(Math.round(Color.alpha(it.fill) * it.alpha));
                    drawShape(canvas, it, fillPaint, d, true);
                }
                if (it.outline != null && it.width > 0) {
                    setStroke(it, d);
                    drawShape(canvas, it, strokePaint, d, false);
                }
                return;
            }
            case "line": {
                if (c.length < 4) return;
                Path p = polyline(c, d, it.smooth, false);
                strokePaint.setColor(it.fill != null ? it.fill : Color.BLACK);
                strokePaint.setStrokeWidth(Math.max(0.5f, it.width) * d);
                strokePaint.setPathEffect(it.dash != null ? new DashPathEffect(scale(it.dash, d), 0) : null);
                strokePaint.setStrokeCap("round".equals(it.cap) ? Paint.Cap.ROUND
                        : "projecting".equals(it.cap) ? Paint.Cap.SQUARE : Paint.Cap.BUTT);
                strokePaint.setStrokeJoin(Paint.Join.ROUND);
                canvas.drawPath(p, strokePaint);
                if (it.arrow != null) {
                    fillPaint.setColor(strokePaint.getColor());
                    if (it.arrow.equals("last") || it.arrow.equals("both")) {
                        arrowHead(canvas, c[c.length - 4], c[c.length - 3], c[c.length - 2], c[c.length - 1], it.width, d);
                    }
                    if (it.arrow.equals("first") || it.arrow.equals("both")) {
                        arrowHead(canvas, c[2], c[3], c[0], c[1], it.width, d);
                    }
                }
                return;
            }
            case "polygon": {
                if (c.length < 4) return;
                Path p = polyline(c, d, it.smooth, true);
                if (it.fill != null) {
                    fillPaint.setColor(it.fill);
                    fillPaint.setAlpha(Math.round(Color.alpha(it.fill) * it.alpha));
                    canvas.drawPath(p, fillPaint);
                }
                if (it.outline != null && it.width > 0) {
                    setStroke(it, d);
                    canvas.drawPath(p, strokePaint);
                }
                return;
            }
            case "text":
                drawText(canvas, it, d);
                return;
            case "image": {
                if (c.length < 2 || it.img == null) return;
                Bitmap bmp = PyImages.get(host.getContext(), it.img, host::invalidate);
                if (bmp == null) return;
                float w = bmp.getWidth(), h = bmp.getHeight();
                float x = c[0] * d, y = c[1] * d;
                float[] o = anchorOffset(it.anchor, w, h);
                canvas.drawBitmap(bmp, x + o[0], y + o[1], null);
                return;
            }
            default:
        }
    }

    private static void drawShape(Canvas canvas, Item it, Paint paint, float d, boolean fill) {
        switch (it.type) {
            case "rectangle":
                if (it.radius > 0) canvas.drawRoundRect(rect, it.radius * d, it.radius * d, paint);
                else canvas.drawRect(rect, paint);
                break;
            case "oval":
                canvas.drawOval(rect, paint);
                break;
            case "arc": {
                // Tk の角度は 3 時の方向から反時計回り。Android は時計回り
                float start = -it.start, sweep = -it.extent;
                boolean center = "pieslice".equals(it.style);
                if ("arc".equals(it.style) && fill) return;
                if ("chord".equals(it.style)) {
                    Path p = new Path();
                    p.arcTo(rect, start, sweep, true);
                    p.close();
                    canvas.drawPath(p, paint);
                } else {
                    canvas.drawArc(rect, start, sweep, center, paint);
                }
                break;
            }
            default:
        }
    }

    private static Path polyline(float[] c, float d, boolean smooth, boolean close) {
        Path p = new Path();
        int n = c.length / 2;
        if (!smooth || n < 3) {
            p.moveTo(c[0] * d, c[1] * d);
            for (int i = 1; i < n; i++) p.lineTo(c[2 * i] * d, c[2 * i + 1] * d);
            if (close) p.close();
            return p;
        }
        // Tk の smooth(2 次ベジェで中点をつなぐ)
        if (close) {
            float mx = (c[2 * (n - 1)] + c[0]) / 2, my = (c[2 * (n - 1) + 1] + c[1]) / 2;
            p.moveTo(mx * d, my * d);
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float nx = (c[2 * i] + c[2 * j]) / 2, ny = (c[2 * i + 1] + c[2 * j + 1]) / 2;
                p.quadTo(c[2 * i] * d, c[2 * i + 1] * d, nx * d, ny * d);
            }
            p.close();
        } else {
            p.moveTo(c[0] * d, c[1] * d);
            for (int i = 1; i < n - 1; i++) {
                float nx = (c[2 * i] + c[2 * i + 2]) / 2, ny = (c[2 * i + 1] + c[2 * i + 3]) / 2;
                if (i == n - 2) {
                    nx = c[2 * i + 2];
                    ny = c[2 * i + 3];
                }
                p.quadTo(c[2 * i] * d, c[2 * i + 1] * d, nx * d, ny * d);
            }
        }
        return p;
    }

    private static void arrowHead(Canvas canvas, float x0, float y0, float x1, float y1, float w, float d) {
        double ang = Math.atan2(y1 - y0, x1 - x0);
        float len = (8 + w * 2) * d, half = (4 + w) * d;
        float bx = x1 * d - (float) Math.cos(ang) * len, by = y1 * d - (float) Math.sin(ang) * len;
        Path p = new Path();
        p.moveTo(x1 * d, y1 * d);
        p.lineTo(bx + (float) Math.sin(ang) * half, by - (float) Math.cos(ang) * half);
        p.lineTo(bx - (float) Math.sin(ang) * half, by + (float) Math.cos(ang) * half);
        p.close();
        canvas.drawPath(p, fillPaint);
    }

    static float[] anchorOffset(String a, float w, float h) {
        float ox = a.contains("w") ? 0 : a.contains("e") ? -w : -w / 2;
        float oy = a.contains("n") ? 0 : a.contains("s") ? -h : -h / 2;
        return new float[]{ox, oy};
    }

    private static void drawText(Canvas canvas, Item it, float d) {
        if (it.c.length < 2 || it.text.isEmpty() || it.fill == null) return;
        float size = it.font != null ? (float) it.font.optDouble("size", 13) : 13;
        textPaint.setTextSize(size * d * 1.0f);
        textPaint.setTypeface(typeface(it.font));
        textPaint.setUnderlineText(it.font != null && it.font.optBoolean("underline"));
        textPaint.setStrikeThruText(it.font != null && it.font.optBoolean("strike"));
        textPaint.setColor(it.fill);
        int width;
        if (it.wrap > 0) {
            width = Math.round(it.wrap * d);
        } else {
            float max = 0;
            for (String line : it.text.split("\n", -1)) max = Math.max(max, textPaint.measureText(line));
            width = (int) Math.ceil(max) + 1;
        }
        Layout.Alignment al = "center".equals(it.justify) ? Layout.Alignment.ALIGN_CENTER
                : "right".equals(it.justify) ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL;
        StaticLayout layout = StaticLayout.Builder.obtain(it.text, 0, it.text.length(), textPaint, Math.max(1, width))
                .setAlignment(al).setIncludePad(false).build();
        float w = layout.getWidth(), h = layout.getHeight();
        float x = it.c[0] * d, y = it.c[1] * d;
        float[] o = anchorOffset(it.anchor, w, h);
        canvas.save();
        if (it.angle != 0) canvas.rotate(-it.angle, x, y);
        canvas.translate(x + o[0], y + o[1]);
        layout.draw(canvas);
        canvas.restore();
    }
}
