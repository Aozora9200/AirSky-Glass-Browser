package com.airsky.browser.jvm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;

import com.airsky.browser.jvm.java.awt.AlphaComposite;
import com.airsky.browser.jvm.java.awt.BasicStroke;
import com.airsky.browser.jvm.java.awt.Color;
import com.airsky.browser.jvm.java.awt.Composite;
import com.airsky.browser.jvm.java.awt.Font;
import com.airsky.browser.jvm.java.awt.FontMetrics;
import com.airsky.browser.jvm.java.awt.GradientPaint;
import com.airsky.browser.jvm.java.awt.Graphics;
import com.airsky.browser.jvm.java.awt.Graphics2D;
import com.airsky.browser.jvm.java.awt.Image;
import com.airsky.browser.jvm.java.awt.Paint;
import com.airsky.browser.jvm.java.awt.Rectangle;
import com.airsky.browser.jvm.java.awt.RenderingHints;
import com.airsky.browser.jvm.java.awt.Shape;
import com.airsky.browser.jvm.java.awt.Stroke;
import com.airsky.browser.jvm.java.awt.geom.AffineTransform;
import com.airsky.browser.jvm.java.awt.geom.Rectangle2D;
import com.airsky.browser.jvm.java.awt.image.BufferedImage;
import com.airsky.browser.jvm.java.awt.image.ImageObserver;

import java.util.Map;

/**
 * AWT の Graphics2D を Android の Canvas で描く。
 *
 * <p>変換は AWT の座標(ウィンドウや画像の左上が原点)で持ち、描く時だけ端末の倍率を掛ける。
 * create() で作った子は同じ Canvas を共有するが、変換と切り抜きは自分の分を持つので、
 * dispose() し忘れても親の描画はおかしくならない。</p>
 */
public final class JvmGraphics extends Graphics2D {

    private final Canvas canvas;
    private final float scale;
    private AffineTransform tx;
    /** 切り抜き(AWT 座標・変換前)。null なら切り抜かない。 */
    private Path clip;
    private Color color = Color.BLACK;
    private Paint paint = Color.BLACK;
    private Color background = Color.WHITE;
    private Font font = new Font(Font.DIALOG, Font.PLAIN, 12);
    private Stroke stroke = new BasicStroke(1f);
    private Composite composite = AlphaComposite.SrcOver;
    private boolean antialias = true;
    private final RenderingHints hints = new RenderingHints(null);
    private Runnable onDispose;
    private boolean disposed;

    private final android.graphics.Paint fill = new android.graphics.Paint();
    private final android.graphics.Paint line = new android.graphics.Paint();
    private final android.graphics.Paint text = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Paint image = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
    private final Matrix base = new Matrix();
    private final RectF rect = new RectF();

    public JvmGraphics(Canvas canvas, float scale) {
        this.canvas = canvas;
        this.scale = scale;
        this.tx = new AffineTransform();
        fill.setStyle(android.graphics.Paint.Style.FILL);
        line.setStyle(android.graphics.Paint.Style.STROKE);
        base.setScale(scale, scale);
    }

    private JvmGraphics(JvmGraphics g) {
        this(g.canvas, g.scale);
        tx = new AffineTransform(g.tx);
        clip = g.clip != null ? new Path(g.clip) : null;
        color = g.color;
        paint = g.paint;
        background = g.background;
        font = g.font;
        stroke = g.stroke;
        composite = g.composite;
        antialias = g.antialias;
        hints.putAll(g.hints);
    }

    /** dispose() の時に呼ぶ(getGraphics() で描いた絵を画面に出すため)。 */
    public void jvm$setOnDispose(Runnable r) {
        onDispose = r;
    }

    // ------------------------------------------------------------------
    // 描く準備
    // ------------------------------------------------------------------

    private int begin() {
        int sc = canvas.save();
        canvas.setMatrix(base);
        if (clip != null) canvas.clipPath(clip);
        canvas.concat(tx.jvm$matrix());
        return sc;
    }

    private void end(int sc) {
        canvas.restoreToCount(sc);
    }

    private int argb(Color c) {
        int v = c.getRGB();
        float a = ((v >>> 24) & 0xFF) * extraAlpha();
        return (Math.round(a) << 24) | (v & 0xFFFFFF);
    }

    private float extraAlpha() {
        return composite instanceof AlphaComposite ? ((AlphaComposite) composite).getAlpha() : 1f;
    }

    private void prepareFill(android.graphics.Paint p, boolean aa) {
        p.setAntiAlias(aa && antialias);
        p.setShader(null);
        if (paint instanceof GradientPaint) {
            GradientPaint gp = (GradientPaint) paint;
            p.setColor(0xFF000000 | 0);
            p.setAlpha(Math.round(255 * extraAlpha()));
            p.setShader(new LinearGradient(
                    (float) gp.getPoint1().getX(), (float) gp.getPoint1().getY(),
                    (float) gp.getPoint2().getX(), (float) gp.getPoint2().getY(),
                    gp.getColor1().getRGB(), gp.getColor2().getRGB(),
                    gp.isCyclic() ? Shader.TileMode.MIRROR : Shader.TileMode.CLAMP));
        } else {
            p.setColor(argb(color));
        }
        p.setXfermode(xfermode());
    }

    private void prepareLine(boolean aa) {
        prepareFill(line, aa);
        if (stroke instanceof BasicStroke) {
            BasicStroke bs = (BasicStroke) stroke;
            line.setStrokeWidth(Math.max(bs.getLineWidth(), 0.0001f));
            switch (bs.getEndCap()) {
                case BasicStroke.CAP_ROUND: line.setStrokeCap(android.graphics.Paint.Cap.ROUND); break;
                case BasicStroke.CAP_SQUARE: line.setStrokeCap(android.graphics.Paint.Cap.SQUARE); break;
                default: line.setStrokeCap(android.graphics.Paint.Cap.BUTT); break;
            }
            switch (bs.getLineJoin()) {
                case BasicStroke.JOIN_ROUND: line.setStrokeJoin(android.graphics.Paint.Join.ROUND); break;
                case BasicStroke.JOIN_BEVEL: line.setStrokeJoin(android.graphics.Paint.Join.BEVEL); break;
                default: line.setStrokeJoin(android.graphics.Paint.Join.MITER); break;
            }
            float[] dash = bs.getDashArray();
            line.setPathEffect(dash != null && dash.length >= 2
                    ? new DashPathEffect(dash.length % 2 == 0 ? dash : doubled(dash), bs.getDashPhase()) : null);
        } else {
            line.setStrokeWidth(1f);
            line.setPathEffect(null);
        }
    }

    private static float[] doubled(float[] d) {
        float[] out = new float[d.length * 2];
        System.arraycopy(d, 0, out, 0, d.length);
        System.arraycopy(d, 0, out, d.length, d.length);
        return out;
    }

    /** 1 ピクセルの線を AWT と同じ位置に出すためのずらし。 */
    private float half() {
        if (stroke instanceof BasicStroke) {
            float w = ((BasicStroke) stroke).getLineWidth();
            return Math.round(w) % 2 == 1 || w <= 1f ? 0.5f : 0f;
        }
        return 0.5f;
    }

    private PorterDuffXfermode xfermode() {
        if (!(composite instanceof AlphaComposite)) return null;
        switch (((AlphaComposite) composite).getRule()) {
            case AlphaComposite.CLEAR: return new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
            case AlphaComposite.SRC: return new PorterDuffXfermode(PorterDuff.Mode.SRC);
            case AlphaComposite.DST_OVER: return new PorterDuffXfermode(PorterDuff.Mode.DST_OVER);
            case AlphaComposite.SRC_IN: return new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);
            case AlphaComposite.DST_IN: return new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
            case AlphaComposite.SRC_OUT: return new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT);
            case AlphaComposite.DST_OUT: return new PorterDuffXfermode(PorterDuff.Mode.DST_OUT);
            case AlphaComposite.SRC_ATOP: return new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP);
            case AlphaComposite.DST_ATOP: return new PorterDuffXfermode(PorterDuff.Mode.DST_ATOP);
            case AlphaComposite.XOR: return new PorterDuffXfermode(PorterDuff.Mode.XOR);
            case AlphaComposite.DST: return new PorterDuffXfermode(PorterDuff.Mode.DST);
            default: return null;
        }
    }

    // ------------------------------------------------------------------
    // Graphics
    // ------------------------------------------------------------------

    @Override public Graphics create() {
        return new JvmGraphics(this);
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        if (onDispose != null) onDispose.run();
    }

    @Override public void translate(int x, int y) {
        tx.translate(x, y);
    }

    @Override public Color getColor() {
        return color;
    }

    @Override public void setColor(Color c) {
        if (c == null) return;
        color = c;
        paint = c;
    }

    @Override public void setPaintMode() {
        composite = AlphaComposite.SrcOver;
    }

    @Override public void setXORMode(Color c) {
        // XOR は使えないので、そのまま描く
    }

    @Override public Font getFont() {
        return font;
    }

    @Override public void setFont(Font f) {
        if (f != null) font = f;
    }

    @Override public FontMetrics getFontMetrics(Font f) {
        return JvmFonts.metrics(f != null ? f : font);
    }

    @Override public Rectangle getClipBounds() {
        if (clip == null) return null;
        clip.computeBounds(rect, true);
        AffineTransform inv;
        try {
            inv = tx.createInverse();
        } catch (Exception e) {
            return new Rectangle();
        }
        Rectangle2D r = inv.jvm$transformBounds(rect.left, rect.top, rect.right, rect.bottom);
        return r.getBounds();
    }

    @Override public Shape getClip() {
        return getClipBounds();
    }

    @Override public void clipRect(int x, int y, int w, int h) {
        clip(new Rectangle(x, y, w, h));
    }

    @Override public void setClip(int x, int y, int w, int h) {
        setClip(new Rectangle(x, y, w, h));
    }

    @Override public void setClip(Shape s) {
        if (s == null) {
            clip = null;
            return;
        }
        Path p = JvmShapes.path(s);
        p.transform(tx.jvm$matrix());
        clip = p;
    }

    @Override public void clip(Shape s) {
        if (s == null) return;
        Path p = JvmShapes.path(s);
        p.transform(tx.jvm$matrix());
        if (clip == null) {
            clip = p;
        } else {
            Path out = new Path();
            out.op(clip, p, Path.Op.INTERSECT);
            clip = out;
        }
    }

    @Override public void copyArea(int x, int y, int w, int h, int dx, int dy) {
        // 手前の絵をそのまま写すのは難しいので省く
    }

    @Override public void drawLine(int x1, int y1, int x2, int y2) {
        prepareLine(true);
        float h = half();
        int sc = begin();
        if (x1 == x2 && y1 == y2) {
            line.setStyle(android.graphics.Paint.Style.FILL);
            canvas.drawRect(x1, y1, x1 + 1, y1 + 1, line);
            line.setStyle(android.graphics.Paint.Style.STROKE);
        } else {
            canvas.drawLine(x1 + h, y1 + h, x2 + h, y2 + h, line);
        }
        end(sc);
    }

    @Override public void fillRect(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        prepareFill(fill, false);
        int sc = begin();
        canvas.drawRect(x, y, x + w, y + h, fill);
        end(sc);
    }

    @Override public void drawRect(int x, int y, int w, int h) {
        if (w < 0 || h < 0) return;
        prepareLine(false);
        float o = half();
        int sc = begin();
        canvas.drawRect(x + o, y + o, x + w + o, y + h + o, line);
        end(sc);
    }

    @Override public void clearRect(int x, int y, int w, int h) {
        Paint p = paint;
        Color c = color;
        Composite comp = composite;
        composite = AlphaComposite.Src;
        setColor(background != null ? background : Color.WHITE);
        fillRect(x, y, w, h);
        color = c;
        paint = p;
        composite = comp;
    }

    @Override public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) {
        prepareLine(true);
        float o = half();
        int sc = begin();
        rect.set(x + o, y + o, x + w + o, y + h + o);
        canvas.drawRoundRect(rect, aw / 2f, ah / 2f, line);
        end(sc);
    }

    @Override public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) {
        prepareFill(fill, true);
        int sc = begin();
        rect.set(x, y, x + w, y + h);
        canvas.drawRoundRect(rect, aw / 2f, ah / 2f, fill);
        end(sc);
    }

    @Override public void drawOval(int x, int y, int w, int h) {
        prepareLine(true);
        float o = half();
        int sc = begin();
        rect.set(x + o, y + o, x + w + o, y + h + o);
        canvas.drawOval(rect, line);
        end(sc);
    }

    @Override public void fillOval(int x, int y, int w, int h) {
        prepareFill(fill, true);
        int sc = begin();
        rect.set(x, y, x + w, y + h);
        canvas.drawOval(rect, fill);
        end(sc);
    }

    @Override public void drawArc(int x, int y, int w, int h, int start, int arc) {
        prepareLine(true);
        float o = half();
        int sc = begin();
        rect.set(x + o, y + o, x + w + o, y + h + o);
        canvas.drawArc(rect, -start, -arc, false, line);
        end(sc);
    }

    @Override public void fillArc(int x, int y, int w, int h, int start, int arc) {
        prepareFill(fill, true);
        int sc = begin();
        rect.set(x, y, x + w, y + h);
        canvas.drawArc(rect, -start, -arc, true, fill);
        end(sc);
    }

    @Override public void drawPolyline(int[] xs, int[] ys, int n) {
        if (n < 2) return;
        prepareLine(true);
        float o = half();
        Path p = new Path();
        p.moveTo(xs[0] + o, ys[0] + o);
        for (int i = 1; i < n; i++) p.lineTo(xs[i] + o, ys[i] + o);
        int sc = begin();
        canvas.drawPath(p, line);
        end(sc);
    }

    @Override public void drawPolygon(int[] xs, int[] ys, int n) {
        if (n < 2) return;
        prepareLine(true);
        float o = half();
        Path p = new Path();
        p.moveTo(xs[0] + o, ys[0] + o);
        for (int i = 1; i < n; i++) p.lineTo(xs[i] + o, ys[i] + o);
        p.close();
        int sc = begin();
        canvas.drawPath(p, line);
        end(sc);
    }

    @Override public void fillPolygon(int[] xs, int[] ys, int n) {
        if (n < 3) return;
        prepareFill(fill, true);
        Path p = new Path();
        p.setFillType(Path.FillType.EVEN_ODD);
        p.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) p.lineTo(xs[i], ys[i]);
        p.close();
        int sc = begin();
        canvas.drawPath(p, fill);
        end(sc);
    }

    @Override public void drawString(String s, int x, int y) {
        drawString(s, (float) x, (float) y);
    }

    @Override public void drawString(String s, float x, float y) {
        if (s == null || s.isEmpty()) return;
        prepareFill(text, true);
        text.setAntiAlias(true);
        JvmFonts.apply(text, font);
        int sc = begin();
        canvas.drawText(s, x, y, text);
        end(sc);
    }

    @Override public void drawString(java.text.AttributedCharacterIterator it, int x, int y) {
        StringBuilder sb = new StringBuilder();
        for (char c = it.first(); c != java.text.CharacterIterator.DONE; c = it.next()) sb.append(c);
        drawString(sb.toString(), x, y);
    }

    @Override public void drawString(java.text.AttributedCharacterIterator it, float x, float y) {
        drawString(it, (int) x, (int) y);
    }

    // ------------------------------------------------------------------
    // 画像
    // ------------------------------------------------------------------

    private static Bitmap bitmapOf(Image img) {
        return img instanceof BufferedImage ? ((BufferedImage) img).jvm$bitmap() : null;
    }

    private boolean drawBitmap(Image img, float dx1, float dy1, float dx2, float dy2,
                               int sx1, int sy1, int sx2, int sy2, Color bg) {
        Bitmap b = bitmapOf(img);
        if (b == null || b.isRecycled()) return true;
        int sc = begin();
        RectF dst = new RectF(Math.min(dx1, dx2), Math.min(dy1, dy2), Math.max(dx1, dx2), Math.max(dy1, dy2));
        if (bg != null) {
            fill.setShader(null);
            fill.setXfermode(null);
            fill.setColor(argb(bg));
            canvas.drawRect(dst, fill);
        }
        boolean flipX = (dx2 < dx1) != (sx2 < sx1);
        boolean flipY = (dy2 < dy1) != (sy2 < sy1);
        if (flipX || flipY) {
            canvas.scale(flipX ? -1 : 1, flipY ? -1 : 1, dst.centerX(), dst.centerY());
        }
        image.setAlpha(Math.round(255 * extraAlpha()));
        image.setXfermode(xfermode());
        // 拡大した時は補間するかどうかをヒントに合わせる(ドット絵はくっきり)
        Object interp = hints.get(RenderingHints.KEY_INTERPOLATION);
        image.setFilterBitmap(interp == null || interp != RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        android.graphics.Rect src = new android.graphics.Rect(Math.min(sx1, sx2), Math.min(sy1, sy2),
                Math.max(sx1, sx2), Math.max(sy1, sy2));
        canvas.drawBitmap(b, src, dst, image);
        end(sc);
        return true;
    }

    @Override public boolean drawImage(Image img, int x, int y, ImageObserver o) {
        if (img == null) return true;
        int w = img.getWidth(null), h = img.getHeight(null);
        return drawBitmap(img, x, y, x + w, y + h, 0, 0, w, h, null);
    }

    @Override public boolean drawImage(Image img, int x, int y, int w, int h, ImageObserver o) {
        if (img == null) return true;
        return drawBitmap(img, x, y, x + w, y + h, 0, 0, img.getWidth(null), img.getHeight(null), null);
    }

    @Override public boolean drawImage(Image img, int x, int y, Color bg, ImageObserver o) {
        if (img == null) return true;
        int w = img.getWidth(null), h = img.getHeight(null);
        return drawBitmap(img, x, y, x + w, y + h, 0, 0, w, h, bg);
    }

    @Override public boolean drawImage(Image img, int x, int y, int w, int h, Color bg, ImageObserver o) {
        if (img == null) return true;
        return drawBitmap(img, x, y, x + w, y + h, 0, 0, img.getWidth(null), img.getHeight(null), bg);
    }

    @Override public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                                       int sx1, int sy1, int sx2, int sy2, ImageObserver o) {
        if (img == null) return true;
        return drawBitmap(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
    }

    @Override public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                                       int sx1, int sy1, int sx2, int sy2, Color bg, ImageObserver o) {
        if (img == null) return true;
        return drawBitmap(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bg);
    }

    @Override public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
        if (img == null) return true;
        AffineTransform saved = new AffineTransform(tx);
        if (xform != null) tx.concatenate(xform);
        drawImage(img, 0, 0, obs);
        tx = saved;
        return true;
    }

    @Override public void drawImage(BufferedImage img, com.airsky.browser.jvm.java.awt.image.BufferedImageOp op, int x, int y) {
        drawImage(op != null ? op.filter(img, null) : img, x, y, null);
    }

    // ------------------------------------------------------------------
    // Graphics2D
    // ------------------------------------------------------------------

    @Override public void draw(Shape s) {
        if (s == null) return;
        prepareLine(true);
        Path p = JvmShapes.path(s);
        int sc = begin();
        canvas.drawPath(p, line);
        end(sc);
    }

    @Override public void fill(Shape s) {
        if (s == null) return;
        prepareFill(fill, !(s instanceof Rectangle));
        Path p = JvmShapes.path(s);
        int sc = begin();
        canvas.drawPath(p, fill);
        end(sc);
    }

    @Override public void setStroke(Stroke s) {
        if (s != null) stroke = s;
    }

    @Override public Stroke getStroke() {
        return stroke;
    }

    @Override public void setPaint(Paint p) {
        if (p == null) return;
        paint = p;
        if (p instanceof Color) color = (Color) p;
    }

    @Override public Paint getPaint() {
        return paint;
    }

    @Override public void setComposite(Composite c) {
        if (c != null) composite = c;
    }

    @Override public Composite getComposite() {
        return composite;
    }

    @Override public void setBackground(Color c) {
        background = c;
    }

    @Override public Color getBackground() {
        return background;
    }

    @Override public void setRenderingHint(RenderingHints.Key key, Object value) {
        hints.put(key, value);
        if (key == RenderingHints.KEY_ANTIALIASING) antialias = value != RenderingHints.VALUE_ANTIALIAS_OFF;
    }

    @Override public Object getRenderingHint(RenderingHints.Key key) {
        return hints.get(key);
    }

    @Override public void setRenderingHints(Map<?, ?> m) {
        hints.clear();
        addRenderingHints(m);
    }

    @Override public void addRenderingHints(Map<?, ?> m) {
        if (m == null) return;
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() instanceof RenderingHints.Key) setRenderingHint((RenderingHints.Key) e.getKey(), e.getValue());
        }
    }

    @Override public RenderingHints getRenderingHints() {
        RenderingHints r = new RenderingHints(null);
        r.putAll(hints);
        return r;
    }

    @Override public void translate(double x, double y) {
        tx.translate(x, y);
    }

    @Override public void rotate(double theta) {
        tx.rotate(theta);
    }

    @Override public void rotate(double theta, double x, double y) {
        tx.rotate(theta, x, y);
    }

    @Override public void scale(double sx, double sy) {
        tx.scale(sx, sy);
    }

    @Override public void shear(double shx, double shy) {
        tx.shear(shx, shy);
    }

    @Override public void transform(AffineTransform t) {
        if (t != null) tx.concatenate(t);
    }

    @Override public void setTransform(AffineTransform t) {
        tx = t != null ? new AffineTransform(t) : new AffineTransform();
    }

    @Override public AffineTransform getTransform() {
        return new AffineTransform(tx);
    }

    @Override public com.airsky.browser.jvm.java.awt.font.FontRenderContext getFontRenderContext() {
        return new com.airsky.browser.jvm.java.awt.font.FontRenderContext(null, antialias, true);
    }
}
