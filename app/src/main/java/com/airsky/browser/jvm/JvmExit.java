package com.airsky.browser.jvm;

/** System.exit の代わりに投げる。プログラムの処理をそこで止める。 */
public final class JvmExit extends Error {
    public final int status;

    public JvmExit(int status) {
        super("System.exit(" + status + ")", null, false, false);
        this.status = status;
    }
}
