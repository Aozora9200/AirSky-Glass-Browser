package com.airsky.browser.jvm.java.awt.geom;

import android.graphics.Path;

import com.airsky.browser.jvm.JvmShapes;
import com.airsky.browser.jvm.java.awt.Shape;

/** java.awt.geom.Area の互換クラス(Android の Path の演算で作る)。 */
public class Area extends Path2D {
    public Area() { super(WIND_NON_ZERO); }

    public Area(Shape s) {
        super(WIND_NON_ZERO);
        if (s != null) path = JvmShapes.path(s);
    }

    private void op(Area other, Path.Op op) {
        Path out = new Path();
        out.op(path, other.path, op);
        path = out;
    }

    public void add(Area a) { op(a, Path.Op.UNION); }
    public void subtract(Area a) { op(a, Path.Op.DIFFERENCE); }
    public void intersect(Area a) { op(a, Path.Op.INTERSECT); }
    public void exclusiveOr(Area a) { op(a, Path.Op.XOR); }
    public boolean isEmpty() { return path.isEmpty(); }

    @Override
    public Object clone() {
        Area a = new Area();
        a.path = new Path(path);
        return a;
    }

    public Area createTransformedArea(AffineTransform t) {
        Area a = (Area) clone();
        a.transform(t);
        return a;
    }
}
