/*
 * AirSky Browser の Python 実行ツールが使う、小さな起動プログラム。
 *
 * python.org が配布している Android 版 CPython には実行ファイル(bin/python3)が無く、
 * libpython3.x.so だけが入っている。また targetSdk 29 以降のアプリは、自分のデータ領域に置いた
 * ファイルを実行できない(W^X)。そこでこのプログラムをアプリの jniLibs(実行できる場所)に
 * libairpython.so という名前で入れておき、ダウンロードした libpython を dlopen して Py_BytesMain を呼ぶ。
 *
 * 環境変数:
 *   AIRSKY_LIBPYTHON  読み込む libpython3.x.so のフルパス(必須)
 *   PYTHONHOME など   Python 本体の設定(アプリ側 PythonRuntime.java が用意する)
 *
 * ビルド: tools/build_python_launcher.sh(NDK の clang で arm64-v8a / x86_64 向けに作り、
 *         app/src/main/jniLibs/<abi>/libairpython.so に置く)
 */
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>

typedef int (*py_bytes_main_t)(int, char **);

int main(int argc, char **argv) {
    const char *lib = getenv("AIRSKY_LIBPYTHON");
    if (lib == NULL || lib[0] == '\0') {
        fprintf(stderr, "airpython: AIRSKY_LIBPYTHON is not set\n");
        return 127;
    }
    void *handle = dlopen(lib, RTLD_NOW | RTLD_GLOBAL);
    if (handle == NULL) {
        fprintf(stderr, "airpython: cannot load %s: %s\n", lib, dlerror());
        return 127;
    }
    py_bytes_main_t py_main = (py_bytes_main_t) dlsym(handle, "Py_BytesMain");
    if (py_main == NULL) {
        fprintf(stderr, "airpython: Py_BytesMain not found: %s\n", dlerror());
        return 127;
    }
    return py_main(argc, argv);
}
