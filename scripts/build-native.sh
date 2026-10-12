#!/bin/sh
# Clona whisper.cpp (versión fijada) y compila las bibliotecas nativas a .local/lib/ (idempotente).
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
. "$ROOT/scripts/env.sh" >/dev/null 2>&1

WHISPER_TAG="v1.9.5"
SRC="$ROOT/.local/src/whisper.cpp"
BUILD="$ROOT/.local/native-build"

if [ ! -d "$SRC/.git" ]; then
  mkdir -p "$ROOT/.local/src"
  git clone --depth 1 --branch "$WHISPER_TAG" https://github.com/ggml-org/whisper.cpp "$SRC"
fi

cmake -S "$ROOT/native" -B "$BUILD" -DCMAKE_BUILD_TYPE=Release -DCMAKE_OSX_ARCHITECTURES=arm64 \
      -DWHISPER_SRC="$SRC"
cmake --build "$BUILD" --config Release -j "$(sysctl -n hw.ncpu)"

mkdir -p "$ROOT/.local/lib"
# Se borra antes de copiar: sobrescribir un dylib ya firmado/cargado hace que macOS mate el proceso (SIGKILL).
rm -f "$ROOT/.local/lib/libdictado_whisper.dylib" "$ROOT/.local/lib/libdictado_macos.dylib"
cp "$BUILD/libdictado_whisper.dylib" "$BUILD/libdictado_macos.dylib" "$ROOT/.local/lib/"
echo "Bibliotecas nativas en $ROOT/.local/lib:"
ls -la "$ROOT/.local/lib"
