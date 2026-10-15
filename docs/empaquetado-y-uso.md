# Empaquetado e instalación

Cómo generar la app para usarla como cualquier otra aplicación de tu Mac, sin terminal.

## 1. Generar `DictadoAI.app` y `DictadoAI.dmg`

Requisitos: haber seguido el [README](../README.md) (CMake, bibliotecas nativas y una compilación correcta) y tener instalado el **JDK 25**, porque el código se compila a Java 25 y `jpackage` debe usar esa misma versión.

```sh
cd voice-transcript-kotlin
scripts/package-macos.sh
```

El script hace seis cosas:

| Paso | Qué hace |
|---|---|
| 1 | Compila el jar ejecutable con `scripts/kt package`. |
| 2 | Prepara la entrada de `jpackage` en `.local/package/input/`: las dependencias, el jar de la app y las dos `.dylib` en `lib/`. |
| 3 | Ejecuta `jpackage` del JDK 25 y genera `DictadoAI.app` con su **propio Java incluido**. |
| 4 | Edita `Info.plist` con PlistBuddy: `LSUIElement` (sin Dock) y `NSMicrophoneUsageDescription`. |
| 5 | Firma la app de forma **ad hoc** con `codesign` (sin certificado de Apple). |
| 6 | Crea `DictadoAI.dmg` con `hdiutil`, con un acceso directo a Aplicaciones. |

Resultado, en `.local/package/out/`:

- `DictadoAI.app`
- `DictadoAI.dmg` (unos 80 MB). **No incluye el modelo de voz**: se descarga desde la app.

El script no copia nada fuera del proyecto. Instalar es cosa tuya.

### Un detalle del empaquetado

El jar ejecutable del toolchain guarda un envoltorio de 4 KB llamado `runtime-desktop-1.12.1.jar` en lugar del artefacto real de Compose, que tiene el mismo nombre. Sin el real, la app se cierra al abrir su ventana con `NoClassDefFoundError: androidx/compose/runtime/SnapshotStateKt`. El script lo sustituye por el real y **se detiene con un error** si la clase no está en el paquete.

## 2. Instalar

1. Cierra DictadoAI si está abierta (**Salir** en la barra de menús).
2. Abre `.local/package/out/DictadoAI.dmg`.
3. Arrastra **DictadoAI** a **Aplicaciones**.

Para **actualizar** a una versión nueva, repite el proceso: borra la app anterior (`rm -rf /Applications/DictadoAI.app`), genera el paquete y arrástralo de nuevo. Tus modelos y tu configuración se conservan, porque viven fuera de la app.

## 3. Primera ejecución

1. Abre **DictadoAI** desde Aplicaciones o Launchpad. No aparecerá en el Dock: busca la bolita y el icono de la barra de menús.
2. Como no hay modelo, el panel dice **"No hay modelo de voz"**. Pulsa **Elegir modelo**, luego **Descargar** en *Large v3 Turbo* (1.6 GB). Al terminar, el modelo se carga solo.
   - Para no descargarlo otra vez si ya lo tienes del desarrollo:
     ```sh
     mkdir -p ~/"Library/Application Support/DictadoAI/models"
     cp .local/models/ggml-large-v3-turbo.bin ~/"Library/Application Support/DictadoAI/models/"
     ```
3. La primera vez que dictes, macOS pedirá permiso de micrófono para **DictadoAI**. Acéptalo.
4. Sube el volumen de entrada del micrófono al 100 % (Configuración del Sistema → Sonido → Entrada).
5. Haz clic en la bolita y habla. Cuando termines, pega con **⌘V**.

## 4. Uso diario

Consulta [funcionamiento.md](funcionamiento.md#1-el-flujo-del-usuario) para el flujo completo.

- **Abrir al iniciar sesión:** Configuración del Sistema → General → Ítems de inicio → **+** → elige DictadoAI. La app no se añade sola.
- **Cambiar de modelo:** icono de la barra de menús → **Modelos…**.
- **Salir:** icono de la barra de menús → **Salir**.

## 5. Dónde están tus datos

| Ruta (`~/Library/Application Support/DictadoAI/`) | Contenido |
|---|---|
| `models/` | Los modelos descargados |
| `config.properties` | Posición de la bolita y modelo elegido |
| `logs/dictadoai.log` | Registro de la app. Crece con el tiempo; se puede borrar |

## 6. Desinstalar

1. Borra `DictadoAI` de Aplicaciones.
2. Si quieres eliminar también los modelos y la configuración: borra la carpeta `~/Library/Application Support/DictadoAI`.

## 7. Solución de problemas

| Problema | Qué hacer |
|---|---|
| Al abrirla no pasa nada | Ejecuta `/Applications/DictadoAI.app/Contents/MacOS/DictadoAI` en la Terminal y revisa el error. Mira también `logs/dictadoai.log` |
| `NoClassDefFoundError` en el registro | El paquete se generó sin las clases de Compose. Regénralo con `scripts/package-macos.sh` (ver "Un detalle del empaquetado") |
| macOS dice que no puede verificar la app | Configuración del Sistema → Privacidad y seguridad → **Abrir igualmente**. Si descargaste el `.dmg`, quita la cuarentena con `xattr -dr com.apple.quarantine /Applications/DictadoAI.app` |
| No pide permiso de micrófono | Actívalo en Configuración del Sistema → Privacidad y seguridad → Micrófono |
| "No hay modelo de voz" | Descarga uno en **Modelos…** |
| Dos bolitas | Hay dos instancias (por ejemplo, la de desarrollo y la instalada). Sal de una con **Salir** |

## 8. Compartir la app con otras personas

La v1 está pensada para **uso personal**: la app se firma ad hoc y no está notarizada. En otro Mac, macOS mostrará un aviso y la persona tendrá que permitirla manualmente en Privacidad y seguridad.

Para distribuirla sin avisos hacen falta trámites de Apple:

1. Unirse al **Apple Developer Program** (99 USD al año).
2. Firmar la app con un certificado **Developer ID Application** y activar el *hardened runtime*, con el permiso de micrófono (`com.apple.security.device.audio-input`) en los entitlements.
3. **Notarizar** la app con `notarytool` y grapar el resultado con `stapler`.

Esto queda como trabajo futuro.
