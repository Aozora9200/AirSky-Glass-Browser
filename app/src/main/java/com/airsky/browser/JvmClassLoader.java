package com.airsky.browser;

import dalvik.system.DexClassLoader;

/**
 * 変換した JAR のクラスを読む。JAR に入っているクラスを先に探す(ブラウザ本体が持つ同じ名前の
 * ライブラリより、JAR の中のものを使う)。Java 標準・Android・互換レイヤーのクラスは本体から読む。
 */
final class JvmClassLoader extends DexClassLoader {

    JvmClassLoader(String dexPath, ClassLoader parent) {
        super(dexPath, null, null, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (this) {
            Class<?> c = findLoadedClass(name);
            if (c != null) return c;
            if (name.startsWith("java.") || name.startsWith("android.") || name.startsWith("dalvik.")
                    || name.startsWith("com.airsky.browser.jvm.")) {
                return super.loadClass(name, resolve);
            }
            try {
                return findClass(name);
            } catch (ClassNotFoundException e) {
                return super.loadClass(name, resolve);
            }
        }
    }
}
