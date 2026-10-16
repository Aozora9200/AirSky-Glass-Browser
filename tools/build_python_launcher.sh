#!/usr/bin/env bash
# Python 実行ツールの起動プログラム(app/src/main/cpp/airpython_launcher.c)を NDK でビルドし、
# app/src/main/jniLibs/<abi>/libairpython.so に置く。
# python.org の Android 版 CPython がある arm64-v8a と x86_64 だけを作る。
#
#   ANDROID_NDK_HOME=/path/to/ndk tools/build_python_launcher.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${ANDROID_NDK_HOME:-${LOCALAPPDATA:-$HOME}/Android/Sdk/ndk/29.0.14206865}"
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) HOST=windows-x86_64; EXT=.cmd ;;
  Darwin) HOST=darwin-x86_64; EXT= ;;
  *) HOST=linux-x86_64; EXT= ;;
esac
BIN="$NDK/toolchains/llvm/prebuilt/$HOST/bin"
SRC="$ROOT/app/src/main/cpp/airpython_launcher.c"

build() {
  local abi="$1" triple="$2"
  local out="$ROOT/app/src/main/jniLibs/$abi"
  mkdir -p "$out"
  "$BIN/${triple}26-clang$EXT" -O2 -fPIE -pie -Wall -Werror -o "$out/libairpython.so" "$SRC" -ldl
  "$BIN/llvm-strip" "$out/libairpython.so"
  echo "built $out/libairpython.so"
}

build arm64-v8a aarch64-linux-android
build x86_64 x86_64-linux-android
