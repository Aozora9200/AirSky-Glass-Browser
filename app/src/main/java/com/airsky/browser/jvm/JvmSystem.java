package com.airsky.browser.jvm;

/**
 * JAR の中の System.exit / Runtime.exit / Runtime.halt の呼び出しは、変換の時にここへ付け替える。
 * ブラウザのプロセスを落とさずに、プログラムだけを終わらせる。
 */
public final class JvmSystem {
    private JvmSystem() {}

    public static void exit(int status) {
        JvmRuntime.exit(status);
    }

    public static void runtimeExit(Runtime runtime, int status) {
        JvmRuntime.exit(status);
    }
}
