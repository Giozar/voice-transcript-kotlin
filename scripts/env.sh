#!/bin/sh
# Entorno contenido: todo lo que se descarga o genera queda dentro de .local/
# Uso: source scripts/env.sh   (desde voice-transcript-kotlin/)

if [ -n "${BASH_SOURCE:-}" ]; then _src="${BASH_SOURCE[0]}"; elif [ -n "${ZSH_VERSION:-}" ]; then _src="${(%):-%x}"; else _src="$0"; fi
ROOT="$(cd "$(dirname "$_src")/.." && pwd)"
export ROOT

mkdir -p "$ROOT/.local/kotlin-cli" "$ROOT/.local/kotlin-caches" "$ROOT/.local/tmp" \
         "$ROOT/.local/skiko" "$ROOT/.local/jna" "$ROOT/.local/tools" "$ROOT/.local/lib" \
         "$ROOT/.local/models" "$ROOT/.local/dev-data" "$ROOT/.local/samples"

export KOTLIN_CLI_BOOTSTRAP_CACHE_DIR="$ROOT/.local/kotlin-cli"
# JDK del sistema (java_home -v 25 devuelve el 25 o, si falta, el más reciente instalado). Solo el empaquetado lo usa
# y comprueba la versión; compilar y ejecutar usa el Java del toolchain.
if JAVA_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null)"; then
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
fi
export PATH="$ROOT/.local/tools/cmake/CMake.app/Contents/bin:$PATH"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$ROOT/.local/tmp -Dskiko.data.path=$ROOT/.local/skiko -Djna.tmpdir=$ROOT/.local/jna"
export DICTADO_DEV_HOME="$ROOT/.local"
