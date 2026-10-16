package com.airsky.browser;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import java.util.EnumMap;
import java.util.Map;

/**
 * QR コードを好きな形・色で描く。
 *
 * <p>zxing でモジュール(黒白の升目)の並びだけを作り、ドットと角の目印(ファインダーパターン)を
 * それぞれ選んだ形で Canvas に描く。読み取りやすさのため、目印は升目と別に大きな図形として描く。</p>
 */
final class QrRenderer {

    static final int DOT_SQUARE = 0;
    static final int DOT_CIRCLE = 1;
    static final int DOT_SMALL_CIRCLE = 2;
    static final int DOT_ROUNDED = 3;   // となり合うドットはつながり、外側の角だけ丸い
    static final int DOT_DIAMOND = 4;

    static final int EYE_SQUARE = 0;
    static final int EYE_ROUNDED = 1;
    static final int EYE_CIRCLE = 2;
    static final int EYE_LEAF = 3;      // 対角の 2 隅だけ丸い葉っぱの形

    /** 描き方の指定 */
    static final class Style {
        int dot = DOT_SQUARE;
        int eye = EYE_SQUARE;
        @ColorInt int fg = Color.BLACK;
        @ColorInt int bg = Color.WHITE;
        /** 目印の色。0 なら fg と同じ */
        @ColorInt int eyeColor = 0;
        boolean gradient = false;
        @ColorInt int fg2 = 0xFF3D5AFE;
        ErrorCorrectionLevel ec = ErrorCorrectionLevel.M;
        /** 周りの余白(升目の数) */
        int margin = 2;
    }

    private QrRenderer() {}

    @NonNull
    static Bitmap render(@NonNull String text, @NonNull Style st, int sizePx) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        QRCode code = Encoder.encode(text, st.ec, hints);
        ByteMatrix m = code.getMatrix();
        int n = m.getWidth();
        int total = n + st.margin * 2;
        float cell = sizePx / (float) total;
        float origin = st.margin * cell;

        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(st.bg);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        if (st.gradient) {
            paint.setShader(new LinearGradient(0, 0, sizePx, sizePx, st.fg, st.fg2, Shader.TileMode.CLAMP));
        } else {
            paint.setColor(st.fg);
        }

        Path path = new Path();
        RectF r = new RectF();
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (m.get(x, y) != 1 || inFinder(x, y, n)) continue;
                float left = origin + x * cell;
                float top = origin + y * cell;
                switch (st.dot) {
                    case DOT_CIRCLE:
                        path.addCircle(left + cell / 2f, top + cell / 2f, cell * 0.48f, Path.Direction.CW);
                        break;
                    case DOT_SMALL_CIRCLE:
                        path.addCircle(left + cell / 2f, top + cell / 2f, cell * 0.36f, Path.Direction.CW);
                        break;
                    case DOT_DIAMOND: {
                        float cx = left + cell / 2f, cy = top + cell / 2f, h = cell * 0.55f;
                        path.moveTo(cx, cy - h);
                        path.lineTo(cx + h, cy);
                        path.lineTo(cx, cy + h);
                        path.lineTo(cx - h, cy);
                        path.close();
                        break;
                    }
                    case DOT_ROUNDED: {
                        boolean up = dark(m, x, y - 1, n), down = dark(m, x, y + 1, n);
                        boolean lft = dark(m, x - 1, y, n), rgt = dark(m, x + 1, y, n);
                        float rad = cell * 0.5f;
                        float tl = (!up && !lft) ? rad : 0f;
                        float tr = (!up && !rgt) ? rad : 0f;
                        float br = (!down && !rgt) ? rad : 0f;
                        float bl = (!down && !lft) ? rad : 0f;
                        r.set(left - 0.3f, top - 0.3f, left + cell + 0.3f, top + cell + 0.3f);
                        path.addRoundRect(r, new float[]{tl, tl, tr, tr, br, br, bl, bl}, Path.Direction.CW);
                        break;
                    }
                    case DOT_SQUARE:
                    default:
                        // 隣との間に髪の毛のような隙間が出ないよう、少しだけ大きく塗る
                        r.set(left - 0.3f, top - 0.3f, left + cell + 0.3f, top + cell + 0.3f);
                        path.addRect(r, Path.Direction.CW);
                        break;
                }
            }
        }
        c.drawPath(path, paint);

        Paint eyePaint = paint;
        if (st.eyeColor != 0) {
            eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            eyePaint.setColor(st.eyeColor);
        }
        drawEye(c, eyePaint, st.eye, origin, origin, cell);
        drawEye(c, eyePaint, st.eye, origin + (n - 7) * cell, origin, cell);
        drawEye(c, eyePaint, st.eye, origin, origin + (n - 7) * cell, cell);
        return bmp;
    }

    private static boolean inFinder(int x, int y, int n) {
        return (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7);
    }

    private static boolean dark(ByteMatrix m, int x, int y, int n) {
        if (x < 0 || y < 0 || x >= n || y >= n) return false;
        return m.get(x, y) == 1 && !inFinder(x, y, n);
    }

    /** 7×7 の目印(外枠の輪と、中の 3×3)を描く */
    private static void drawEye(Canvas c, Paint p, int shape, float left, float top, float cell) {
        Path ring = new Path();
        ring.setFillType(Path.FillType.EVEN_ODD);
        RectF outer = new RectF(left, top, left + 7 * cell, top + 7 * cell);
        RectF inner = new RectF(left + cell, top + cell, left + 6 * cell, top + 6 * cell);
        RectF ball = new RectF(left + 2 * cell, top + 2 * cell, left + 5 * cell, top + 5 * cell);
        Path center = new Path();
        switch (shape) {
            case EYE_CIRCLE:
                ring.addOval(outer, Path.Direction.CW);
                ring.addOval(inner, Path.Direction.CW);
                center.addOval(ball, Path.Direction.CW);
                break;
            case EYE_ROUNDED:
                ring.addRoundRect(outer, cell * 2.2f, cell * 2.2f, Path.Direction.CW);
                ring.addRoundRect(inner, cell * 1.4f, cell * 1.4f, Path.Direction.CW);
                center.addRoundRect(ball, cell * 0.9f, cell * 0.9f, Path.Direction.CW);
                break;
            case EYE_LEAF: {
                float ro = cell * 3f, ri = cell * 2f, rb = cell * 1.3f;
                ring.addRoundRect(outer, new float[]{ro, ro, 0, 0, ro, ro, 0, 0}, Path.Direction.CW);
                ring.addRoundRect(inner, new float[]{ri, ri, 0, 0, ri, ri, 0, 0}, Path.Direction.CW);
                center.addRoundRect(ball, new float[]{rb, rb, 0, 0, rb, rb, 0, 0}, Path.Direction.CW);
                break;
            }
            case EYE_SQUARE:
            default:
                ring.addRect(outer, Path.Direction.CW);
                ring.addRect(inner, Path.Direction.CW);
                center.addRect(ball, Path.Direction.CW);
                break;
        }
        c.drawPath(ring, p);
        c.drawPath(center, p);
    }

    /** 2 色の明るさの差(WCAG のコントラスト比)。読み取りにくい色の組み合わせを知らせるのに使う */
    static double contrast(@ColorInt int a, @ColorInt int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(@ColorInt int c) {
        double r = channel(Color.red(c)), g = channel(Color.green(c)), b = channel(Color.blue(c));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
