package com.airsky.browser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Python の GUI(tkinter / wx / flet)の部品を並べる入れ物。
 *
 * <p>子ごとに並べ方を持てる(Tk と同じく、1 つの入れ物の中で混ぜてよい)。
 * <ul>
 *   <li>pack  … Tk の packer と同じ計算(side / fill / expand / anchor / pad / ipad)</li>
 *   <li>grid  … 行と列。行・列ごとの weight / minsize、sticky、rowspan / columnspan</li>
 *   <li>place … 座標(x / y、relx / rely、幅と高さ、anchor)</li>
 *   <li>box   … 縦または横に並べる(wx の BoxSizer、flet の Row / Column)。weight・揃え・間隔・折り返し</li>
 *   <li>tab   … 選んでいる 1 つだけを全体に出す(Notebook / Tabs の中身)</li>
 * </ul>
 * 大きさは Python 側の 1 ピクセルを 1dp として扱う。Canvas の図形(draw)もここに描く。</p>
 */
@SuppressLint("ViewConstructor")
final class PyLayout extends ViewGroup {

    interface Events {
        void mouse(@NonNull PyLayout v, @NonNull String type, float xDp, float yDp);

        void configure(@NonNull PyLayout v, int wDp, int hDp, int xDp, int yDp);
    }

    final float d;
    @Nullable Events events;

    // 入れ物の設定
    String orient = "v";
    float spacing;
    String main = "start";
    String cross = "start";
    boolean wrap;
    boolean propagate = true;
    final Map<Integer, float[]> rows = new HashMap<>();
    final Map<Integer, float[]> cols = new HashMap<>();
    float explicitW = -1, explicitH = -1, minW = -1, minH = -1; // px
    final int[] pad = new int[4]; // px
    @Nullable String title;
    int titleColor;
    int borderColor;
    int selectedTab;
    final List<View> order = new ArrayList<>();
    @Nullable List<PyDraw.Item> draw;
    boolean mouse;
    boolean conf;

    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int lastW = -1, lastH = -1;

    PyLayout(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClipChildren(true);
        titlePaint.setTextSize(13 * c.getResources().getDisplayMetrics().scaledDensity);
        titlePaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Math.max(1, d));
    }

    // ------------------------------------------------------------------
    // 子の並べ方
    // ------------------------------------------------------------------

    static final class LP extends ViewGroup.LayoutParams {
        String m = "none";
        // pack
        String side = "top", fill = "none", anchor = "center";
        boolean expand;
        // grid
        int row, col, rowspan = 1, colspan = 1;
        String sticky = "";
        float aspect;
        // place
        float x, y, relx, rely, w = -1, h = -1, relw = -1, relh = -1;
        // box
        float weight;
        boolean flex;
        String align = null;
        // 共通(px)
        int padL, padR, padT, padB, ipadx, ipady;
        /** 子の props の width / height(px。-1 なら中身に合わせる) */
        int pw = -1, ph = -1, pminw = -1, pminh = -1;
        /** 子の props の margin(flet の Container) */
        final int[] cm = new int[4];

        // 計算結果
        int nw, nh, fx, fy, fw, fh;

        LP() {
            super(WRAP_CONTENT, WRAP_CONTENT);
        }

        void setOpts(JSONObject o, float d) {
            m = o.optString("m", "none");
            padL = padR = padT = padB = ipadx = ipady = 0;
            JSONArray px = o.optJSONArray("padx"), py = o.optJSONArray("pady");
            if (px != null) {
                padL = Math.round((float) px.optDouble(0) * d);
                padR = Math.round((float) px.optDouble(1) * d);
            }
            if (py != null) {
                padT = Math.round((float) py.optDouble(0) * d);
                padB = Math.round((float) py.optDouble(1) * d);
            }
            ipadx = Math.round((float) o.optDouble("ipadx", 0) * d);
            ipady = Math.round((float) o.optDouble("ipady", 0) * d);
            switch (m) {
                case "pack":
                    side = o.optString("side", "top");
                    fill = o.optString("fill", "none");
                    expand = o.optBoolean("expand");
                    anchor = o.optString("anchor", "center");
                    break;
                case "grid":
                    row = o.optInt("row");
                    col = o.optInt("col");
                    rowspan = Math.max(1, o.optInt("rowspan", 1));
                    colspan = Math.max(1, o.optInt("colspan", 1));
                    sticky = o.optString("sticky", "");
                    aspect = (float) o.optDouble("aspect", 0);
                    break;
                case "place":
                    x = (float) o.optDouble("x", 0) * d;
                    y = (float) o.optDouble("y", 0) * d;
                    relx = (float) o.optDouble("relx", 0);
                    rely = (float) o.optDouble("rely", 0);
                    w = o.has("w") ? (float) o.optDouble("w") * d : -1;
                    h = o.has("h") ? (float) o.optDouble("h") * d : -1;
                    relw = (float) o.optDouble("relw", -1);
                    relh = (float) o.optDouble("relh", -1);
                    anchor = o.optString("anchor", "nw");
                    break;
                case "box":
                    weight = (float) o.optDouble("weight", 0);
                    flex = o.optBoolean("flex", weight > 0);
                    expand = o.optBoolean("expand");
                    align = o.optString("align", null);
                    JSONArray mg = o.optJSONArray("margin");
                    if (mg != null) {
                        padL = Math.round((float) mg.optDouble(0) * d);
                        padT = Math.round((float) mg.optDouble(1) * d);
                        padR = Math.round((float) mg.optDouble(2) * d);
                        padB = Math.round((float) mg.optDouble(3) * d);
                    }
                    break;
                default:
                    break;
            }
        }

        int ml() {
            return padL + cm[0];
        }

        int mt() {
            return padT + cm[1];
        }

        int mr() {
            return padR + cm[2];
        }

        int mb() {
            return padB + cm[3];
        }
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LP;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LP();
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LP();
    }

    static LP lp(View v) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        return p instanceof LP ? (LP) p : new LP();
    }

    /** 並べる順(pack / box / tab)。order に無い子は最後。 */
    private List<View> ordered(String m) {
        List<View> out = new ArrayList<>();
        for (View v : order) {
            if (v.getParent() == this && v.getVisibility() != GONE && lp(v).m.equals(m)) out.add(v);
        }
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() != GONE && lp(v).m.equals(m) && !out.contains(v)) out.add(v);
        }
        return out;
    }

    void setOrder(List<View> views) {
        order.clear();
        order.addAll(views);
        requestLayout();
    }

    // ------------------------------------------------------------------
    // 大きさ
    // ------------------------------------------------------------------

    private static int spec(int size, int mode) {
        return MeasureSpec.makeMeasureSpec(Math.max(0, size), mode);
    }

    /** 子の「中身に合わせた大きさ」を測る。availW < 0 なら幅は制限なし。 */
    private void measureNatural(View c, int availW) {
        LP p = lp(c);
        int ws;
        if (p.pw >= 0) ws = spec(p.pw, MeasureSpec.EXACTLY);
        else if (availW >= 0) ws = spec(availW, MeasureSpec.AT_MOST);
        else ws = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int hs = p.ph >= 0 ? spec(p.ph, MeasureSpec.EXACTLY) : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        c.measure(ws, hs);
        p.nw = Math.max(c.getMeasuredWidth(), p.pminw);
        p.nh = Math.max(c.getMeasuredHeight(), p.pminh);
    }

    private int titleSpace() {
        if (title == null) return 0;
        Paint.FontMetrics fm = titlePaint.getFontMetrics();
        return Math.round(fm.descent - fm.ascent + 6 * d);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int wMode = MeasureSpec.getMode(widthMeasureSpec), wSize = MeasureSpec.getSize(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec), hSize = MeasureSpec.getSize(heightMeasureSpec);
        int padX = pad[0] + pad[2] + (title != null ? Math.round(16 * d) : 0);
        int padY = pad[1] + pad[3] + titleSpace() + (title != null ? Math.round(8 * d) : 0);
        int availW = wMode == MeasureSpec.UNSPECIFIED ? -1 : Math.max(0, wSize - padX);
        if (explicitW >= 0 && (!propagate || !hasManaged())) availW = Math.max(0, Math.round(explicitW) - padX);

        // 1. 子の自然な大きさ
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            LP p = lp(c);
            int a = availW < 0 ? -1 : Math.max(0, availW - p.ml() - p.mr() - p.padL * 0);
            measureNatural(c, a);
        }

        // 2. 中身が求める大きさ
        int[] req = requested(availW);
        int reqW = req[0] + padX, reqH = req[1] + padY;
        if (explicitW >= 0 && (!propagate || !hasManaged())) reqW = Math.round(explicitW);
        if (explicitH >= 0 && (!propagate || !hasManaged())) reqH = Math.round(explicitH);
        if (minW >= 0) reqW = Math.max(reqW, Math.round(minW));
        if (minH >= 0) reqH = Math.max(reqH, Math.round(minH));
        if (title != null) reqW = Math.max(reqW, Math.round(titlePaint.measureText(title) + 32 * d));

        int w = wMode == MeasureSpec.EXACTLY ? wSize : reqW;
        int h = hMode == MeasureSpec.EXACTLY ? hSize : reqH;
        setMeasuredDimension(w, h);

        // 3. 実際の大きさで並べ、子を測り直す
        int left = pad[0] + (title != null ? Math.round(8 * d) : 0);
        int top = pad[1] + titleSpace() + (title != null ? Math.round(4 * d) : 0);
        arrange(left, top, Math.max(0, w - padX), Math.max(0, h - padY));
    }

    private boolean hasManaged() {
        for (int i = 0; i < getChildCount(); i++) {
            String m = lp(getChildAt(i)).m;
            if (getChildAt(i).getVisibility() != GONE && (m.equals("pack") || m.equals("grid") || m.equals("box")
                    || m.equals("tab"))) return true;
        }
        return false;
    }

    /** 中身が求める大きさ(px、パディングを除く)。 */
    private int[] requested(int availW) {
        int w = 0, h = 0;
        int[] r = packRequested();
        w = Math.max(w, r[0]);
        h = Math.max(h, r[1]);
        r = gridRequested();
        w = Math.max(w, r[0]);
        h = Math.max(h, r[1]);
        r = boxRequested(availW);
        w = Math.max(w, r[0]);
        h = Math.max(h, r[1]);
        for (View c : ordered("tab")) {
            LP p = lp(c);
            w = Math.max(w, p.nw);
            h = Math.max(h, p.nh);
        }
        if (w == 0 && h == 0) {
            // place だけの入れ物(flet の Stack など): 子のはみ出しまで含めた大きさ
            for (View c : ordered("place")) {
                LP p = lp(c);
                if (p.relx != 0 || p.rely != 0 || p.relw > 0 || p.relh > 0) continue;
                w = Math.max(w, Math.round(p.x + (p.w >= 0 ? p.w : p.nw)));
                h = Math.max(h, Math.round(p.y + (p.h >= 0 ? p.h : p.nh)));
            }
        }
        return new int[]{w, h};
    }

    private void arrange(int left, int top, int width, int height) {
        arrangePack(left, top, width, height);
        arrangeGrid(left, top, width, height);
        arrangeBox(left, top, width, height);
        for (View c : ordered("tab")) setFrame(c, left, top, width, height);
        arrangePlace(left, top, width, height);
    }

    private void setFrame(View c, int x, int y, int w, int h) {
        LP p = lp(c);
        p.fx = x;
        p.fy = y;
        p.fw = Math.max(0, w);
        p.fh = Math.max(0, h);
        c.measure(spec(p.fw, MeasureSpec.EXACTLY), spec(p.fh, MeasureSpec.EXACTLY));
    }

    // ------------------------------------------------------------------
    // pack(Tk の tkPack.c と同じ計算)
    // ------------------------------------------------------------------

    private static boolean vertical(LP p) {
        return p.side.equals("top") || p.side.equals("bottom");
    }

    private int[] packRequested() {
        int width = 0, height = 0, maxW = 0, maxH = 0;
        for (View c : ordered("pack")) {
            LP p = lp(c);
            int cw = p.nw + p.padL + p.padR + 2 * p.ipadx + p.cm[0] + p.cm[2];
            int ch = p.nh + p.padT + p.padB + 2 * p.ipady + p.cm[1] + p.cm[3];
            if (vertical(p)) {
                maxW = Math.max(maxW, cw + width);
                height += ch;
            } else {
                maxH = Math.max(maxH, ch + height);
                width += cw;
            }
        }
        return new int[]{Math.max(maxW, width), Math.max(maxH, height)};
    }

    private static int reqW(LP p) {
        return p.nw + p.padL + p.padR + 2 * p.ipadx + p.cm[0] + p.cm[2];
    }

    private static int reqH(LP p) {
        return p.nh + p.padT + p.padB + 2 * p.ipady + p.cm[1] + p.cm[3];
    }

    private static int xExpansion(List<View> list, int from, int cavityWidth) {
        int minExpand = cavityWidth, numExpand = 0;
        for (int i = from; i < list.size(); i++) {
            LP p = lp(list.get(i));
            int childWidth = reqW(p);
            if (vertical(p)) {
                if (numExpand > 0) {
                    int cur = (cavityWidth - childWidth) / numExpand;
                    if (cur < minExpand) minExpand = cur;
                }
            } else {
                cavityWidth -= childWidth;
                if (p.expand) numExpand++;
            }
        }
        if (numExpand > 0) {
            int cur = cavityWidth / numExpand;
            if (cur < minExpand) minExpand = cur;
        }
        return Math.max(0, minExpand);
    }

    private static int yExpansion(List<View> list, int from, int cavityHeight) {
        int minExpand = cavityHeight, numExpand = 0;
        for (int i = from; i < list.size(); i++) {
            LP p = lp(list.get(i));
            int childHeight = reqH(p);
            if (!vertical(p)) {
                if (numExpand > 0) {
                    int cur = (cavityHeight - childHeight) / numExpand;
                    if (cur < minExpand) minExpand = cur;
                }
            } else {
                cavityHeight -= childHeight;
                if (p.expand) numExpand++;
            }
        }
        if (numExpand > 0) {
            int cur = cavityHeight / numExpand;
            if (cur < minExpand) minExpand = cur;
        }
        return Math.max(0, minExpand);
    }

    private void arrangePack(int left, int top, int width, int height) {
        List<View> list = ordered("pack");
        int cx = left, cy = top, cw = width, ch = height;
        for (int i = 0; i < list.size(); i++) {
            View c = list.get(i);
            LP p = lp(c);
            int frameX, frameY, frameW, frameH;
            if (vertical(p)) {
                frameW = cw;
                frameH = reqH(p);
                if (p.expand) frameH += yExpansion(list, i, ch);
                ch -= frameH;
                if (ch < 0) {
                    frameH += ch;
                    ch = 0;
                }
                frameX = cx;
                if (p.side.equals("top")) {
                    frameY = cy;
                    cy += frameH;
                } else {
                    frameY = cy + ch;
                }
            } else {
                frameH = ch;
                frameW = reqW(p);
                if (p.expand) frameW += xExpansion(list, i, cw);
                cw -= frameW;
                if (cw < 0) {
                    frameW += cw;
                    cw = 0;
                }
                frameY = cy;
                if (p.side.equals("left")) {
                    frameX = cx;
                    cx += frameW;
                } else {
                    frameX = cx + cw;
                }
            }
            int padX = p.padL + p.padR + p.cm[0] + p.cm[2];
            int padY = p.padT + p.padB + p.cm[1] + p.cm[3];
            int w = p.nw + 2 * p.ipadx;
            if (p.fill.equals("x") || p.fill.equals("both") || w > frameW - padX) w = frameW - padX;
            int h = p.nh + 2 * p.ipady;
            if (p.fill.equals("y") || p.fill.equals("both") || h > frameH - padY) h = frameH - padY;
            int x = frameX + p.padL + p.cm[0], y = frameY + p.padT + p.cm[1];
            int freeX = frameW - padX - w, freeY = frameH - padY - h;
            String a = p.anchor;
            if (a.contains("e")) x += freeX;
            else if (!a.contains("w")) x += freeX / 2;
            if (a.contains("s")) y += freeY;
            else if (!a.contains("n")) y += freeY / 2;
            setFrame(c, x, y, w, h);
        }
    }

    // ------------------------------------------------------------------
    // grid
    // ------------------------------------------------------------------

    private int[] gridSizes(List<View> list, boolean colsMode, int total) {
        int n = 0;
        for (View c : list) {
            LP p = lp(c);
            n = Math.max(n, colsMode ? p.col + p.colspan : p.row + p.rowspan);
        }
        Map<Integer, float[]> conf = colsMode ? cols : rows;
        for (Integer k : conf.keySet()) if (conf.get(k)[0] > 0 || conf.get(k)[1] > 0) n = Math.max(n, k + 1);
        int[] size = new int[n];
        for (int i = 0; i < n; i++) {
            float[] cf = conf.get(i);
            if (cf != null) size[i] = Math.round(cf[1] * d);
        }
        // 1 マスの子から
        for (View c : list) {
            LP p = lp(c);
            int span = colsMode ? p.colspan : p.rowspan;
            if (span != 1) continue;
            int i = colsMode ? p.col : p.row;
            int need = colsMode ? reqW(p) : reqH(p);
            if (!colsMode && p.aspect > 0) continue;
            size[i] = Math.max(size[i], need);
        }
        // 複数のマスにまたがる子は、足りない分を等分に足す
        for (View c : list) {
            LP p = lp(c);
            int span = colsMode ? p.colspan : p.rowspan;
            if (span == 1) continue;
            int i0 = colsMode ? p.col : p.row;
            int have = 0;
            for (int i = i0; i < i0 + span && i < n; i++) have += size[i];
            int need = colsMode ? reqW(p) : reqH(p);
            if (need > have) {
                int add = (need - have) / span;
                for (int i = i0; i < i0 + span && i < n; i++) size[i] += add;
            }
        }
        if (total >= 0) {
            int sum = 0;
            float wsum = 0;
            for (int i = 0; i < n; i++) {
                sum += size[i];
                float[] cf = conf.get(i);
                if (cf != null) wsum += cf[0];
            }
            int extra = total - sum;
            if (wsum > 0 && extra != 0) {
                int given = 0, last = -1;
                for (int i = 0; i < n; i++) {
                    float[] cf = conf.get(i);
                    if (cf == null || cf[0] <= 0) continue;
                    int add = Math.round(extra * cf[0] / wsum);
                    int minSize = Math.round(cf[1] * d);
                    int nv = Math.max(minSize, size[i] + add);
                    given += nv - size[i];
                    size[i] = nv;
                    last = i;
                }
                if (last >= 0 && extra > 0) size[last] = Math.max(0, size[last] + extra - given);
            }
        }
        return size;
    }

    private int[] gridRequested() {
        List<View> list = ordered("grid");
        if (list.isEmpty()) return new int[]{0, 0};
        int w = 0, h = 0;
        for (int s : gridSizes(list, true, -1)) w += s;
        int[] colSizes = gridSizes(list, true, -1);
        applyAspect(list, colSizes);
        for (int s : gridSizes(list, false, -1)) h += s;
        return new int[]{w, h};
    }

    /** flet の GridView: 縦横比が決まった子は、列の幅から高さを決める。 */
    private void applyAspect(List<View> list, int[] colSizes) {
        for (View c : list) {
            LP p = lp(c);
            if (p.aspect <= 0) continue;
            int w = 0;
            for (int i = p.col; i < p.col + p.colspan && i < colSizes.length; i++) w += colSizes[i];
            w -= p.padL + p.padR;
            p.nh = Math.round(Math.max(0, w) / p.aspect);
        }
    }

    private void arrangeGrid(int left, int top, int width, int height) {
        List<View> list = ordered("grid");
        if (list.isEmpty()) return;
        int[] cw = gridSizes(list, true, width);
        applyAspect(list, cw);
        int[] rh = gridSizes(list, false, rowsHaveWeight() ? height : -1);
        int[] cx = new int[cw.length + 1], ry = new int[rh.length + 1];
        cx[0] = left;
        for (int i = 0; i < cw.length; i++) cx[i + 1] = cx[i] + cw[i];
        ry[0] = top;
        for (int i = 0; i < rh.length; i++) ry[i + 1] = ry[i] + rh[i];
        for (View c : list) {
            LP p = lp(c);
            int c1 = Math.min(p.col + p.colspan, cw.length), r1 = Math.min(p.row + p.rowspan, rh.length);
            int x0 = cx[Math.min(p.col, cw.length)] + p.padL, x1 = cx[c1] - p.padR;
            int y0 = ry[Math.min(p.row, rh.length)] + p.padT, y1 = ry[r1] - p.padB;
            int cellW = Math.max(0, x1 - x0), cellH = Math.max(0, y1 - y0);
            String s = p.sticky;
            boolean e = s.contains("e"), w = s.contains("w"), n = s.contains("n"), so = s.contains("s");
            int fw = (e && w) ? cellW : Math.min(cellW, p.nw + 2 * p.ipadx);
            int fh = (n && so) ? cellH : Math.min(cellH, p.nh + 2 * p.ipady);
            int x = x0, y = y0;
            if (e && !w) x = x1 - fw;
            else if (!w) x = x0 + (cellW - fw) / 2;
            if (so && !n) y = y1 - fh;
            else if (!n) y = y0 + (cellH - fh) / 2;
            setFrame(c, x, y, fw, fh);
        }
    }

    private boolean rowsHaveWeight() {
        for (float[] f : rows.values()) if (f[0] > 0) return true;
        return false;
    }

    // ------------------------------------------------------------------
    // place
    // ------------------------------------------------------------------

    private void arrangePlace(int left, int top, int width, int height) {
        int fullW = getMeasuredWidth(), fullH = getMeasuredHeight();
        for (View c : ordered("place")) {
            LP p = lp(c);
            int w = p.w >= 0 ? Math.round(p.w) : (p.relw >= 0 ? 0 : p.nw);
            int h = p.h >= 0 ? Math.round(p.h) : (p.relh >= 0 ? 0 : p.nh);
            if (p.relw >= 0) w += Math.round(p.relw * fullW);
            if (p.relh >= 0) h += Math.round(p.relh * fullH);
            int x = Math.round(p.x + p.relx * fullW), y = Math.round(p.y + p.rely * fullH);
            String a = p.anchor;
            if (a.contains("e")) x -= w;
            else if (!a.contains("w")) x -= w / 2;
            if (a.contains("s")) y -= h;
            else if (!a.contains("n")) y -= h / 2;
            setFrame(c, x + p.cm[0], y + p.cm[1], w, h);
        }
    }

    // ------------------------------------------------------------------
    // box(縦・横に並べる)
    // ------------------------------------------------------------------

    private boolean horizontal() {
        return "h".equals(orient);
    }

    private int[] boxRequested(int availW) {
        List<View> list = ordered("box");
        if (list.isEmpty()) return new int[]{0, 0};
        boolean hz = horizontal();
        int sp = Math.round(spacing * d);
        if (wrap) {
            int lim = hz ? (availW >= 0 ? availW : Integer.MAX_VALUE / 4) : Integer.MAX_VALUE / 4;
            int lineMain = 0, lineCross = 0, totalCross = 0, maxMain = 0;
            for (View c : list) {
                LP p = lp(c);
                int m = (hz ? reqWBox(p) : reqHBox(p)), x = hz ? reqHBox(p) : reqWBox(p);
                if (lineMain > 0 && lineMain + sp + m > lim) {
                    totalCross += lineCross + sp;
                    maxMain = Math.max(maxMain, lineMain);
                    lineMain = 0;
                    lineCross = 0;
                }
                lineMain += (lineMain > 0 ? sp : 0) + m;
                lineCross = Math.max(lineCross, x);
            }
            totalCross += lineCross;
            maxMain = Math.max(maxMain, lineMain);
            return hz ? new int[]{maxMain, totalCross} : new int[]{totalCross, maxMain};
        }
        int mainSum = 0, crossMax = 0;
        for (View c : list) {
            LP p = lp(c);
            boolean flexed = p.flex && p.weight > 0;
            int m = hz ? reqWBox(p) : reqHBox(p);
            mainSum += flexed ? (hz ? p.ml() + p.mr() : p.mt() + p.mb()) : m;
            crossMax = Math.max(crossMax, hz ? reqHBox(p) : reqWBox(p));
        }
        mainSum += sp * Math.max(0, list.size() - 1);
        return hz ? new int[]{mainSum, crossMax} : new int[]{crossMax, mainSum};
    }

    private static int reqWBox(LP p) {
        return p.nw + p.ml() + p.mr();
    }

    private static int reqHBox(LP p) {
        return p.nh + p.mt() + p.mb();
    }

    private void arrangeBox(int left, int top, int width, int height) {
        List<View> list = ordered("box");
        if (list.isEmpty()) return;
        boolean hz = horizontal();
        int sp = Math.round(spacing * d);
        int mainAvail = hz ? width : height, crossAvail = hz ? height : width;
        if (wrap) {
            arrangeWrap(list, left, top, mainAvail, crossAvail, hz, sp);
            return;
        }
        int n = list.size();
        int[] mainSize = new int[n];
        float wsum = 0;
        int fixed = sp * Math.max(0, n - 1);
        int weightedNatural = 0;
        for (int i = 0; i < n; i++) {
            LP p = lp(list.get(i));
            int margins = hz ? p.ml() + p.mr() : p.mt() + p.mb();
            if (p.weight > 0) {
                wsum += p.weight;
                fixed += margins;
                weightedNatural += p.flex ? 0 : (hz ? p.nw : p.nh);
            } else {
                mainSize[i] = hz ? p.nw : p.nh;
                fixed += mainSize[i] + margins;
            }
        }
        int free = mainAvail - fixed;
        if (wsum > 0) {
            int share = Math.max(0, free);
            for (int i = 0; i < n; i++) {
                LP p = lp(list.get(i));
                if (p.weight <= 0) continue;
                int natural = hz ? p.nw : p.nh;
                if (p.flex) {
                    mainSize[i] = Math.round(share * p.weight / wsum);
                } else {
                    // wx の proportion: 最小の大きさに、余りを比率で足す
                    int extra = Math.max(0, free - weightedNatural);
                    mainSize[i] = natural + Math.round(extra * p.weight / wsum);
                }
            }
            free = 0;
        }
        int pos = 0, gap = sp;
        if (free > 0) {
            switch (main) {
                case "center":
                    pos = free / 2;
                    break;
                case "end":
                    pos = free;
                    break;
                case "spaceBetween":
                    if (n > 1) gap = sp + free / (n - 1);
                    break;
                case "spaceAround":
                    pos = free / (2 * n);
                    gap = sp + free / n;
                    break;
                case "spaceEvenly":
                    pos = free / (n + 1);
                    gap = sp + free / (n + 1);
                    break;
                default:
                    break;
            }
        }
        for (int i = 0; i < n; i++) {
            View c = list.get(i);
            LP p = lp(c);
            int m1 = hz ? p.ml() : p.mt(), m2 = hz ? p.mr() : p.mb();
            int c1 = hz ? p.mt() : p.ml(), c2 = hz ? p.mb() : p.mr();
            pos += m1;
            int crossRoom = Math.max(0, crossAvail - c1 - c2);
            String al = p.align != null ? p.align : cross;
            boolean stretch = p.expand || "stretch".equals(cross);
            int natCross = hz ? p.nh : p.nw;
            int cs = stretch ? crossRoom : Math.min(natCross, crossRoom);
            int co = c1;
            if (!stretch) {
                if ("center".equals(al)) co += (crossRoom - cs) / 2;
                else if ("end".equals(al)) co += crossRoom - cs;
            }
            if (hz) setFrame(c, left + pos, top + co, mainSize[i], cs);
            else setFrame(c, left + co, top + pos, cs, mainSize[i]);
            pos += mainSize[i] + m2 + gap;
        }
    }

    private void arrangeWrap(List<View> list, int left, int top, int mainAvail, int crossAvail, boolean hz, int sp) {
        int lineStart = 0;
        int crossPos = 0;
        while (lineStart < list.size()) {
            int lineMain = 0, lineCross = 0, end = lineStart;
            while (end < list.size()) {
                LP p = lp(list.get(end));
                int m = hz ? reqWBox(p) : reqHBox(p);
                if (end > lineStart && lineMain + sp + m > mainAvail) break;
                lineMain += (end > lineStart ? sp : 0) + m;
                lineCross = Math.max(lineCross, hz ? reqHBox(p) : reqWBox(p));
                end++;
            }
            int free = Math.max(0, mainAvail - lineMain);
            int pos = "center".equals(main) ? free / 2 : ("end".equals(main) ? free : 0);
            for (int i = lineStart; i < end; i++) {
                View c = list.get(i);
                LP p = lp(c);
                int w = p.nw, h = p.nh;
                if (hz) setFrame(c, left + pos + p.ml(), top + crossPos + p.mt(), w, h);
                else setFrame(c, left + crossPos + p.ml(), top + pos + p.mt(), w, h);
                pos += (hz ? reqWBox(p) : reqHBox(p)) + sp;
            }
            crossPos += lineCross + sp;
            lineStart = end;
        }
    }

    // ------------------------------------------------------------------

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            LP p = lp(c);
            if (p.m.equals("none")) {
                c.layout(0, 0, 0, 0);
                continue;
            }
            c.layout(p.fx, p.fy, p.fx + p.fw, p.fy + p.fh);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (conf && events != null && (w != lastW || h != lastH)) {
            lastW = w;
            lastH = h;
            events.configure(this, Math.round(w / d), Math.round(h / d), Math.round(getLeft() / d), Math.round(getTop() / d));
        }
    }

    void resendConfigure() {
        lastW = -1;
        lastH = -1;
        if (getWidth() > 0) onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
    }

    // ------------------------------------------------------------------
    // 描く
    // ------------------------------------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (draw != null && !draw.isEmpty()) {
            canvas.save();
            canvas.clipRect(0, 0, getWidth(), getHeight());
            PyDraw.drawAll(canvas, draw, d, this);
            canvas.restore();
        }
        if (title != null) {
            Paint.FontMetrics fm = titlePaint.getFontMetrics();
            float th = fm.descent - fm.ascent;
            float y = pad[1] + th / 2 + 2 * d;
            float r = 12 * d;
            RectF box = new RectF(pad[0] + d, y, getWidth() - pad[2] - d, getHeight() - pad[3] - d);
            borderPaint.setColor(borderColor);
            float tx = pad[0] + 16 * d;
            float tw = titlePaint.measureText(title);
            canvas.save();
            canvas.clipOutRect(tx - 6 * d, y - th, tx + tw + 6 * d, y + th);
            canvas.drawRoundRect(box, r, r, borderPaint);
            canvas.restore();
            titlePaint.setColor(titleColor);
            canvas.drawText(title, tx, y - (fm.ascent + fm.descent) / 2, titlePaint);
        }
    }

    // ------------------------------------------------------------------
    // タッチ(マウスの代わり)
    // ------------------------------------------------------------------

    private long lastDown;
    private float downX, downY;
    private boolean moved, longFired;
    private final Runnable longPress = () -> {
        longFired = true;
        float dens = getResources().getDisplayMetrics().density;
        if (events != null) events.mouse(this, "long", downX / dens, downY / dens);
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mouse || events == null) return super.onTouchEvent(ev);
        float x = ev.getX(), y = ev.getY();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                long now = ev.getEventTime();
                boolean dbl = now - lastDown < ViewConfiguration.getDoubleTapTimeout()
                        && Math.hypot(x - downX, y - downY) < 24 * d;
                lastDown = now;
                downX = x;
                downY = y;
                moved = false;
                longFired = false;
                events.mouse(this, dbl ? "double" : "press", x / d, y / d);
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(x - downX, y - downY) > 6 * d) {
                    moved = true;
                    removeCallbacks(longPress);
                }
                if (moved) events.mouse(this, "motion", x / d, y / d);
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                events.mouse(this, moved || longFired ? "release" : "click", x / d, y / d);
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                events.mouse(this, "release", x / d, y / d);
                return true;
            default:
                return true;
        }
    }
}
