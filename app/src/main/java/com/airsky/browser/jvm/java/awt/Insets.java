package com.airsky.browser.jvm.java.awt;

/** java.awt.Insets の互換クラス。 */
public class Insets implements Cloneable, java.io.Serializable {
    public int top, left, bottom, right;

    public Insets(int top, int left, int bottom, int right) {
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
    }

    public void set(int top, int left, int bottom, int right) {
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Insets)) return false;
        Insets i = (Insets) o;
        return i.top == top && i.left == left && i.bottom == bottom && i.right == right;
    }

    @Override public int hashCode() { return ((top * 31 + left) * 31 + bottom) * 31 + right; }

    @Override public String toString() {
        return getClass().getName() + "[top=" + top + ",left=" + left + ",bottom=" + bottom + ",right=" + right + "]";
    }

    @Override public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }
}
