#!/bin/sh
# Genera un audio de prueba en español (16 kHz, mono, 16 bits) con la voz Paulina de macOS.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/.local/samples"
mkdir -p "$OUT"
say -v Paulina -o "$OUT/prueba.aiff" "Hola, esta es una prueba de dictado en español. Mañana vamos a la oficina a las nueve."
afconvert -f WAVE -d LEI16@16000 -c 1 "$OUT/prueba.aiff" "$OUT/prueba-es.wav"
rm -f "$OUT/prueba.aiff"
echo "Muestra generada: $OUT/prueba-es.wav"
