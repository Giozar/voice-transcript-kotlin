#!/bin/sh
# Descarga ggml-large-v3-turbo.bin a .local/models/ y verifica su SHA-256 (idempotente).
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NAME="ggml-large-v3-turbo.bin"
URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$NAME"
DEST="$ROOT/.local/models"
mkdir -p "$DEST"

if [ -f "$DEST/$NAME" ]; then
  echo "Modelo ya presente: $DEST/$NAME ($(du -h "$DEST/$NAME" | cut -f1))"
  exit 0
fi

EXPECTED="$(curl -sIL "$URL" | tr -d '\r' | awk 'tolower($1)=="x-linked-etag:" {gsub(/"/,"",$2); print $2; exit}')"
[ -n "$EXPECTED" ] || { echo "No se pudo obtener el SHA-256 esperado desde Hugging Face" >&2; exit 1; }
echo "SHA-256 esperado: $EXPECTED"

echo "Descargando $NAME (~1.6 GB)..."
curl -fL -C - --progress-bar -o "$DEST/$NAME.part" "$URL"

ACTUAL="$(shasum -a 256 "$DEST/$NAME.part" | awk '{print $1}')"
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "SHA-256 no coincide (esperado $EXPECTED, real $ACTUAL). Se borra la descarga." >&2
  rm -f "$DEST/$NAME.part"
  exit 1
fi
mv "$DEST/$NAME.part" "$DEST/$NAME"
echo "Modelo listo: $DEST/$NAME"
