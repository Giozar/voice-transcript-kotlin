#!/bin/sh
# Empaqueta DictadoAI como DictadoAI.app y DictadoAI.dmg dentro de .local/package/ (idempotente).
# No incluye el modelo de voz: la app lo descarga desde su menú Modelos. No copia nada fuera del proyecto.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
. "$ROOT/scripts/env.sh" >/dev/null 2>&1

APP_VERSION="1.0.0"
PKG="$ROOT/.local/package"
PACKAGE_JDK="$(/usr/libexec/java_home -v 25)" # el código se compila a Java 25: jpackage debe usar ese JDK
APP="$PKG/out/DictadoAI.app"
PLIST="$APP/Contents/Info.plist"

echo "1/6 Compilando y reuniendo las dependencias..."
"$ROOT/scripts/kt" package -m desktopApp --format executable-jar
EXEC_JAR="$ROOT/build/tasks/_desktopApp_executableJarJvm/desktopApp-jvm-executable.jar"
APP_JAR="$ROOT/build/tasks/_desktopApp_jarJvm/desktopApp-jvm.jar"
[ -f "$EXEC_JAR" ] && [ -f "$APP_JAR" ] || { echo "No se encontraron los jars de la app" >&2; exit 1; }

echo "2/6 Preparando la carpeta de entrada de jpackage..."
rm -rf "$PKG/input" "$PKG/out" "$PKG/dmg" "$PKG/deps"
mkdir -p "$PKG/input/lib" "$PKG/deps" "$PKG/tmp" "$PKG/out"
# Las dependencias salen del jar ejecutable, planas en la raíz para que jpackage las ponga en el classpath.
unzip -q -o "$EXEC_JAR" 'BOOT-INF/lib/*' -d "$PKG/deps"
mv "$PKG/deps/BOOT-INF/lib/"*.jar "$PKG/input/"
rm -rf "$PKG/deps" "$PKG/input/desktopApp-jvm.jar"
cp "$APP_JAR" "$PKG/input/DictadoAI.jar"
cp "$ROOT/.local/lib/libdictado_whisper.dylib" "$ROOT/.local/lib/libdictado_macos.dylib" "$PKG/input/lib/"

# El empaquetador del toolchain guarda un envoltorio de 4 KB de runtime-desktop (mismo nombre que el
# artefacto real de androidx) y pierde las clases de Compose. Se sustituye por el artefacto real del caché.
for jar in "$PKG/input"/*.jar; do
  if [ "$(stat -f%z "$jar")" -lt 20000 ]; then
    real="$(find "$ROOT/.local/kotlin-caches/.m2.cache/androidx" -name "$(basename "$jar")" -size +20k 2>/dev/null | head -1)"
    [ -n "$real" ] && cp "$real" "$jar"
  fi
done
# Comprobación: sin estas clases la app se cierra al abrir la ventana.
for jar in "$PKG/input"/*.jar; do
  unzip -l "$jar" | grep -q "androidx/compose/runtime/SnapshotStateKt.class" && COMPOSE_OK=1
done
[ "${COMPOSE_OK:-0}" = 1 ] || { echo "Falta androidx.compose.runtime en el paquete" >&2; exit 1; }

echo "3/6 Generando DictadoAI.app con jpackage (JDK 25)..."
"$PACKAGE_JDK/bin/jpackage" \
  --type app-image \
  --name DictadoAI \
  --app-version "$APP_VERSION" \
  --input "$PKG/input" \
  --main-jar DictadoAI.jar \
  --main-class com.dictadoai.desktop.MainKt \
  --add-modules java.base,java.desktop,java.logging,java.management,java.naming,java.prefs,java.sql,java.xml,jdk.unsupported \
  --mac-package-identifier com.dictadoai.app \
  --java-options "-Ddictado.appDir=\$APPDIR" \
  --temp "$PKG/tmp" \
  --dest "$PKG/out"

echo "4/6 Ajustando Info.plist (sin Dock y descripción del micrófono)..."
set_key() { # $1 = clave, $2 = tipo, $3 = valor
  /usr/libexec/PlistBuddy -c "Set :$1 $3" "$PLIST" 2>/dev/null ||
    /usr/libexec/PlistBuddy -c "Add :$1 $2 $3" "$PLIST"
}
set_key LSUIElement bool true
set_key NSMicrophoneUsageDescription string "DictadoAI usa el micrófono para transcribir tu voz localmente."

echo "5/6 Firmando (firma ad hoc, sin notarizar)..."
codesign --force --deep --sign - "$APP"
codesign --verify --deep --strict "$APP"

echo "6/6 Creando DictadoAI.dmg..."
# Carpeta del DMG: la app y un acceso a /Applications para arrastrarla a instalar.
mkdir -p "$PKG/dmg"
cp -R "$APP" "$PKG/dmg/"
ln -s /Applications "$PKG/dmg/Applications"
hdiutil create -volname DictadoAI -srcfolder "$PKG/dmg" -ov -format UDZO "$PKG/out/DictadoAI.dmg" >/dev/null
rm -rf "$PKG/dmg"

echo "Listo:"
echo "  App: $APP"
echo "  DMG: $PKG/out/DictadoAI.dmg"
echo "  Java incluido: $(grep '^JAVA_VERSION=' "$APP/Contents/runtime/Contents/Home/release")"
