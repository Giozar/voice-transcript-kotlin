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
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$ROOT/.local/tools/cmake/CMake.app/Contents/bin:$PATH"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$ROOT/.local/tmp -Dskiko.data.path=$ROOT/.local/skiko -Djna.tmpdir=$ROOT/.local/jna"
export DICTADO_DEV_HOME="$ROOT/.local"
