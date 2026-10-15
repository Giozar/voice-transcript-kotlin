#!/bin/sh
# Ejecuta la app en desarrollo. Prepara lo que falte (CMake y bibliotecas nativas) y la lanza.
# Los argumentos extra se pasan a `kt run`; por ejemplo: scripts/dev.sh -- --transcribe .local/samples/prueba-es.wav
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
. "$ROOT/scripts/env.sh" >/dev/null 2>&1

command -v cmake >/dev/null 2>&1 || "$ROOT/scripts/setup-tools.sh"
if [ ! -f "$ROOT/.local/lib/libdictado_whisper.dylib" ] || [ ! -f "$ROOT/.local/lib/libdictado_macos.dylib" ]; then
  "$ROOT/scripts/build-native.sh"
fi
if ! ls "$ROOT/.local/models/"*.bin >/dev/null 2>&1; then
  echo "Aún no hay modelos de voz: la app te dejará descargar uno desde el menú de la barra superior (Modelos…)."
fi

exec "$ROOT/scripts/kt" run -m desktopApp --no-compose-hot-reload "$@"
