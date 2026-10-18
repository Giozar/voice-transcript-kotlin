# Desarrollo

Guía para trabajar en el código. La instalación inicial paso a paso está en el [README](../README.md#4-descargar-las-dependencias-del-proyecto-paso-a-paso).

## 1. El entorno contenido

Todo lo que se descarga o se genera queda dentro de `voice-transcript-kotlin/.local/`, que está en `.gitignore`. Borrar esa carpeta devuelve el proyecto a cero sin tocar nada del sistema.

| Carpeta de `.local/` | Contenido | La crea |
|---|---|---|
| `kotlin-cli/` | Kotlin Toolchain y su propio Java | `./kotlin` (vía `scripts/kt`) |
| `kotlin-caches/` | Librerías de Maven descargadas | `scripts/kt` |
| `tools/cmake/` | CMake | `setup-tools.sh` |
| `src/whisper.cpp/` | Código de whisper.cpp v1.9.5 | `build-native.sh` |
| `native-build/` | Compilación intermedia de CMake | `build-native.sh` |
| `lib/` | `libdictado_whisper.dylib`, `libdictado_macos.dylib` | `build-native.sh` |
| `models/` | Modelos Whisper descargados (en desarrollo) | La app o `download-model.sh` |
| `dev-data/` | `config.properties` (posición de la bolita, modelo elegido) | La app |
| `samples/` | `prueba-es.wav` | `make-sample.sh` |
| `package/` | `DictadoAI.app` y `DictadoAI.dmg` | `package-macos.sh` |
| `tmp/`, `skiko/`, `jna/` | Temporales de Java, Compose y JNA | `env.sh` |

## 2. Los scripts

Siempre desde la raíz del repositorio.

| Script | Qué hace |
|---|---|
| `scripts/env.sh` | `source scripts/env.sh`: define `JAVA_HOME` (el JDK 25 del sistema), el `PATH` con CMake, `DICTADO_DEV_HOME` y redirige los temporales a `.local/`. |
| `scripts/kt` | Ejecuta el Kotlin Toolchain con el entorno cargado y su caché en `.local/`. Se usa en lugar de `./kotlin`. |
| `scripts/setup-tools.sh` | Descarga CMake con verificación de SHA-256. |
| `scripts/build-native.sh` | Compila whisper.cpp y los dos puentes a `.local/lib/`. |
| `scripts/download-model.sh` | Descarga el modelo recomendado desde la terminal (la app también puede hacerlo). |
| `scripts/make-sample.sh` | Genera un audio de prueba en español con la voz Paulina. |
| `scripts/dev.sh` | **Ejecuta la app en desarrollo.** Prepara CMake y los nativos si faltan y lanza `kt run`. |
| `scripts/package-macos.sh` | Genera `DictadoAI.app` y `DictadoAI.dmg` (ver [empaquetado-y-uso.md](empaquetado-y-uso.md)). |

## 3. Día a día

```sh
scripts/dev.sh                          # ejecutar la app
scripts/kt build                        # compilar todo
scripts/kt test                         # todas las pruebas
scripts/kt test -m core                 # solo el núcleo
scripts/kt run -m desktopApp --no-compose-hot-reload   # lo mismo que dev.sh, sin preparar nada
```

Para cerrar la app en desarrollo: **Salir** en el icono de la barra de menús, o `Ctrl+C` en la terminal.

### Modos de diagnóstico

Sirven para probar Whisper o el micrófono sin la interfaz. Usan el modelo elegido en la app, o el recomendado si está descargado.

```sh
scripts/dev.sh -- --transcribe .local/samples/prueba-es.wav   # transcribe un WAV y mide tiempos y memoria
scripts/dev.sh -- --record 5                                  # graba 5 s del micrófono y los transcribe
```

`--record` guarda una copia en `.local/dev-data/ultima-grabacion.wav` para depurar. Es el único caso en que el audio se escribe en disco.

## 4. Pruebas

`scripts/kt test` ejecuta:

- **`core` (68 pruebas):** máquina de estados, inserción de texto en el cursor, limpieza, `DictationController` y `ModelManager`, todo con fakes (`FakeRecorder`, `FakeEngine`, `FakeClipboard`, `FakePermission`, `FakeModelRepository`, `FakeModelDownloader`, `FakeActiveModelStore`) y `kotlinx-coroutines-test`.
- **`desktopApp` (6 pruebas):** el `Resampler`.

Lo que no cubren las pruebas automáticas y se verifica a mano: la bolita que no roba el foco, las ventanas flotantes, el micrófono real y la descarga de modelos. Lista de comprobación:

- [ ] La bolita no roba el foco (Terminal, editor, navegador); ⌘V pega en la app anterior.
- [ ] Se ve en todos los escritorios y sobre pantalla completa; no aparece en el Dock ni en ⌘Tab.
- [ ] Se arrastra y recuerda su posición al reiniciar.
- [ ] Grabar → pausar → editar → reanudar → terminar: el texto se inserta en el cursor y se copia.
- [ ] Cancelar descarta el audio sin tocar el portapapeles.
- [ ] Silencio → "No se detectó voz".
- [ ] Los clics se ignoran durante "Transcribiendo…".
- [ ] Micrófono denegado → mensaje y botón a Configuración del Sistema.
- [ ] Sin modelo → el panel pide elegir uno; al terminar la descarga se carga solo.
- [ ] Descargar, usar y eliminar un modelo; reanudar una descarga cortada.
- [ ] Funciona sin internet (con el modelo ya descargado).
- [ ] **Salir** desde la barra de menús.

## 5. Estructura del código

```
core/src/com/dictadoai/core/
├── domain/        DictationState, DictationEvent, DictationStateMachine, TranscriptDocument,
│                  TranscriptInserter, TranscriptCleaner, RecordingLimits, WhisperModel, BubblePosition
├── port/          AudioRecorder, AudioClip, TranscriptionEngine, ClipboardWriter, MicrophonePermission,
│                  BubblePositionStore, ModelRepository, ModelDownloader, ActiveModelStore
└── application/   DictationController, ModelManager

desktopApp/src/com/dictadoai/desktop/
├── Main.kt        raíz de composición y modos de diagnóstico
├── config/        AppPaths (rutas de desarrollo y de la app instalada)
├── adapter/       audio, whisper, macos, clipboard, settings, models
└── ui/            BubbleWindow, PanelWindow, TrayMenu, FloatingWindows, DictadoTheme

native/            CMakeLists.txt, whisper_bridge.c, macos_bridge.m
```

Cómo se explica y se justifica esto está en [arquitectura.md](arquitectura.md).

### Rutas en desarrollo y en la app instalada

`AppPaths` decide dónde buscar:

| | Desarrollo | App instalada |
|---|---|---|
| Se detecta por | variable de entorno `DICTADO_DEV_HOME` (la fija `env.sh`) | propiedad `dictado.appDir` (la fija jpackage) |
| Bibliotecas nativas | `.local/lib/` | `DictadoAI.app/Contents/app/lib/` |
| Modelos | `.local/models/` | `~/Library/Application Support/DictadoAI/models/` |
| Datos y configuración | `.local/dev-data/` | `~/Library/Application Support/DictadoAI/` |
| Registro | la terminal | `logs/dictadoai.log` |

## 6. Convenciones del repositorio

- Una rama por fase o por cambio (`fase-N-nombre`).
- Mensajes de commit en español, con prefijo (`feat(...)`, `docs`, `fix`…).
- Todo lo que se descarga o genera va a `.local/`; no se escribe fuera del proyecto.
- Los modelos nuevos se añaden al catálogo con su SHA-256 verificado (ver [arquitectura.md](arquitectura.md#añadir-un-modelo-al-catálogo)).

## 7. Solución de problemas en desarrollo

| Problema | Causa probable | Solución |
|---|---|---|
| macOS no pide permiso de micrófono o graba silencio | El permiso se asocia a la Terminal | Configuración del Sistema → Privacidad y seguridad → Micrófono: activa tu terminal y reiníciala |
| "No se detectó voz" | Volumen de entrada bajo | Sube el volumen del micrófono al 100 % (ver [funcionamiento.md](funcionamiento.md#3-el-audio)) |
| La app se cierra sola (código 137) tras compilar los nativos | Se reemplazó una `.dylib` que la app tenía cargada | Cierra la app y vuelve a abrirla; compila los nativos con la app cerrada |
| Error al cargar `libdictado_*.dylib` | Faltan los nativos | `scripts/build-native.sh` |
| `cmake: command not found` | No se cargó el entorno | `source scripts/env.sh` y `scripts/setup-tools.sh` |
| `xcrun: error: invalid active developer path` | Faltan las Command Line Tools | `xcode-select --install` |
| `Para empaquetar hace falta el JDK 25` | El JDK 25 no está instalado | Instálalo (ver README, sección 3.2) |
| Dos bolitas | Dos instancias abiertas (por ejemplo, desarrollo e instalada) | Sal de una con **Salir** |
| La bolita roba el foco | La ventana no se creó como panel | Revisa `FloatingWindows.kt` y que `configureFloating` devuelva 0 |

### Telemetría del toolchain

El Kotlin Toolchain escribe telemetría en `~/Library/Caches/JetBrains/Kotlin/telemetry/`, fuera del proyecto. No se ha encontrado una opción para moverla.
