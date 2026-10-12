#!/bin/sh
# Descarga CMake dentro de .local/tools/ (idempotente). Verifica el SHA-256 publicado en la release.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/.local/tools/cmake"

if [ -x "$DEST/CMake.app/Contents/bin/cmake" ]; then
  echo "CMake ya instalado: $("$DEST/CMake.app/Contents/bin/cmake" --version | head -1)"
  exit 0
fi

mkdir -p "$ROOT/.local/tools" "$ROOT/.local/tmp"
TAG="$(curl -fsSL https://api.github.com/repos/Kitware/CMake/releases/latest | sed -n 's/.*"tag_name": *"\(v[0-9.]*\)".*/\1/p' | head -1)"
[ -n "$TAG" ] || { echo "No se pudo obtener la última versión de CMake" >&2; exit 1; }
VER="${TAG#v}"
BASE="https://github.com/Kitware/CMake/releases/download/$TAG"
TMP="$ROOT/.local/tmp/cmake-dl"
rm -rf "$TMP" && mkdir -p "$TMP"

echo "Descargando CMake $VER..."
curl -fL --progress-bar -o "$TMP/cmake.tar.gz" "$BASE/cmake-$VER-macos-universal.tar.gz"
curl -fsSL -o "$TMP/sums.txt" "$BASE/cmake-$VER-SHA-256.txt"
EXPECTED="$(grep "cmake-$VER-macos-universal.tar.gz" "$TMP/sums.txt" | awk '{print $1}')"
ACTUAL="$(shasum -a 256 "$TMP/cmake.tar.gz" | awk '{print $1}')"
[ -n "$EXPECTED" ] && [ "$EXPECTED" = "$ACTUAL" ] || { echo "SHA-256 no coincide (esperado '$EXPECTED', real '$ACTUAL')" >&2; exit 1; }

tar -xzf "$TMP/cmake.tar.gz" -C "$TMP"
rm -rf "$DEST" && mv "$TMP/cmake-$VER-macos-universal" "$DEST"
rm -rf "$TMP"
echo "CMake instalado: $("$DEST/CMake.app/Contents/bin/cmake" --version | head -1)"
