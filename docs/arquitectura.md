# Arquitectura

DictadoAI usa **arquitectura hexagonal (puertos y adaptadores)** y los principios **SOLID**. Este documento explica cómo está organizado el código, por qué, y cómo ampliarlo.

## 1. Por qué hexagonal

- La app es sobre todo entrada y salida con el sistema: micrófono, whisper.cpp, portapapeles, ventanas de AppKit, descargas y archivos. La lógica propia es pequeña: una máquina de estados del dictado, un documento de texto y la limpieza de la transcripción.
- La arquitectura hexagonal da la misma regla que Clean Architecture (**el núcleo no conoce a nadie**) con menos capas: no hacen falta presenters, DTOs ni casos de uso separados.
- Encaja con el objetivo de llevar la app a otras plataformas (iOS, Android, Windows, Linux): el núcleo es Kotlin puro y común, y cada plataforma solo escribe sus adaptadores y su punto de entrada.

## 2. Módulos

```
voice-transcript-kotlin/
├── core/         Dominio + puertos + aplicación. Kotlin puro: sin java.*, sin Compose, sin macOS.
├── desktopApp/   Adaptadores de macOS + interfaz Compose + raíz de composición (Main.kt).
└── native/       Puentes en C y Objective-C (libdictado_whisper.dylib, libdictado_macos.dylib).
```

La regla de dependencias apunta siempre hacia dentro:

```
                 ┌───────────────────────────────────────┐
                 │ desktopApp                            │
                 │  ui/  Compose (bolita, panel, bandeja)│
                 │  adapter/  micrófono, whisper, macOS, │
                 │            portapapeles, modelos...   │
                 │  Main.kt  raíz de composición         │
                 └──────────────┬────────────────────────┘
                                │ implementa / usa
                 ┌──────────────▼────────────────────────┐
                 │ core                                  │
                 │  port/         interfaces (puertos)   │
                 │  application/  DictationController,   │
                 │                ModelManager           │
                 │  domain/       estados, eventos,      │
                 │                documento, limpieza    │
                 └───────────────────────────────────────┘
```

- `core` depende solo de `kotlinx-coroutines`. Se declara como `kmp/lib` con la plataforma `jvm`; añadir plataformas es cuestión de ampliar esa lista.
- `desktopApp` depende de `core`, Compose, JNA y de las bibliotecas nativas.

## 3. Puertos y adaptadores

Cada puerto es una interfaz pequeña de `core/port/`. Su implementación real vive en `desktopApp/adapter/`.

| Puerto (`core`) | Para qué sirve | Adaptador real (`desktopApp`) |
|---|---|---|
| `AudioRecorder` | Grabar un fragmento: `start()`, `stop()`, `cancel()` y los milisegundos grabados | `JavaSoundRecorder` (+ `Resampler`) |
| `TranscriptionEngine` | `load(model)`, `transcribe(clip, idioma)`, `close()` | `WhisperCppEngine` (+ `WhisperBridge`, JNA) |
| `ClipboardWriter` | Copiar texto | `AwtClipboardWriter` |
| `MicrophonePermission` | Pedir o comprobar el permiso de micrófono | `MacosMicrophonePermission` (+ `MacosBridge`) |
| `BubblePositionStore` | Recordar la posición de la bolita | `PropertiesBubblePositionStore` |
| `ModelRepository` | Qué modelos están descargados, dónde y borrarlos | `FileModelRepository` |
| `ModelDownloader` | Descargar un modelo con progreso y verificar el SHA-256 | `HttpModelDownloader` |
| `ActiveModelStore` | Recordar qué modelo eligió la persona | `PropertiesActiveModelStore` |

`SettingsFile` (interno) lee y escribe `config.properties` conservando las claves ajenas, porque lo comparten `PropertiesBubblePositionStore` y `PropertiesActiveModelStore`.

## 4. El núcleo (`core`)

### Dominio (`core/domain`)

| Archivo | Responsabilidad |
|---|---|
| `DictationState` | Los estados del dictado. La interfaz se dibuja a partir de este estado. |
| `DictationEvent` | Los eventos que pueden cambiarlo. |
| `DictationStateMachine` | Función pura `transition(estado, evento): estado?`. Devuelve `null` si el evento debe ignorarse. |
| `TranscriptDocument` | Texto de la sesión más la posición del cursor o la selección. |
| `TranscriptInserter` | Inserta un fragmento en el cursor, reemplaza la selección y añade espacios. |
| `TranscriptCleaner` | Quita etiquetas de ruido y alucinaciones típicas de Whisper en silencio. |
| `RecordingLimits` | `MAX_MILLIS = 600 000` (10 min), `SAMPLE_RATE = 16 000`, `SILENCE_RMS = 0.005`. |
| `WhisperModel` / `WhisperCatalog` | El catálogo curado de modelos, con tamaño y SHA-256. |
| `BubblePosition` | Posición de la bolita. |

### Aplicación (`core/application`)

- **`DictationController`** orquesta el dictado. Recibe los puertos y un `CoroutineScope`. Expone `state`, `document` y `elapsedMillis`. Métodos: `start()`, `onBubbleClick()`, `continueDictation()`, `pause()`, `resume()`, `finish()`, `cancel()`, `close()`, `updateDocument()`, `useModel()`, `shutdown()`. Toda transición pasa por `DictationStateMachine`. Debe usarse desde un único hilo (en la app, el de la interfaz).
- **`ModelManager`** gestiona el catálogo: lista (`entries`), descarga, elige y elimina. No carga el modelo en el motor: eso lo hace el controlador al recibir `useModel()`. Emite `downloaded` cuando termina una descarga.

## 5. Máquina de estados

Estados:

| Estado | Significado |
|---|---|
| `LoadingModel` | Se está cargando el modelo de voz |
| `NoModel` | No hay ningún modelo descargado |
| `ModelError(mensaje)` | El modelo no se pudo cargar |
| `Idle` | Solo se ve la bolita |
| `Starting(resumed)` | Se pide el micrófono; aún no se graba |
| `Recording(resumed)` | Grabando un fragmento |
| `Transcribing(purpose, limitReached)` | Transcribiendo; `purpose` es `PAUSE` o `FINISH` |
| `Paused(notice)` | En pausa: el texto se puede revisar y editar |
| `Result(limitReached, notice)` | Sesión terminada: el texto se puede editar, copiar o continuar |
| `NoSpeech(limitReached)` | La primera grabación no tenía voz |
| `Failure(kind)` | Permiso denegado, micrófono no disponible o transcripción fallida |

`resumed` indica que la sesión ya tiene texto. `notice` es un aviso sobre el último fragmento (`NONE`, `NO_SPEECH`, `TRANSCRIPTION_FAILED`).

Transiciones principales:

```
LoadingModel ──ModelLoaded──▶ Idle                 LoadingModel ──ModelMissing──▶ NoModel
LoadingModel ──ModelFailed──▶ ModelError           NoModel / ModelError / Idle ──ModelSelected──▶ LoadingModel

Idle ──clic──▶ Starting(false) ──MicReady──▶ Recording(false)
Starting ──MicDenied / MicUnavailable──▶ Failure
Recording ──pausar──▶ Transcribing(PAUSE) ──▶ Paused
Recording ──terminar──▶ Transcribing(FINISH) ──▶ Result
Recording ──10 min──▶ Transcribing(FINISH, límite) ──▶ Result(límite)
Recording / Starting ──cancelar──▶ Paused (si ya había texto) o Idle
Paused ──reanudar──▶ Starting(true)        Paused ──terminar──▶ Result      Paused ──descartar──▶ Idle
Result ──seguir dictando──▶ Starting(true) Result ──cerrar──▶ Idle          Result ──clic──▶ Starting(false)
Transcribing ──sin voz y sin texto──▶ NoSpeech      Transcribing ──error y sin texto──▶ Failure
NoSpeech / Failure ──cerrar──▶ Idle        NoSpeech / Failure ──clic──▶ Starting(false)
```

Los eventos sin flecha en un estado se ignoran (por ejemplo, un clic durante `Transcribing`). Está cubierto por `DictationStateMachineTest`.

## 6. SOLID aplicado

| Principio | Cómo se aplica | Ejemplo |
|---|---|---|
| **S** (responsabilidad única) | Una clase, una responsabilidad | `JavaSoundRecorder` solo graba; `Resampler` solo convierte frecuencias; `TranscriptCleaner` solo limpia el texto; `TranscriptInserter` solo inserta |
| **O** (abierto/cerrado) | Se añaden motores o plataformas implementando puertos, sin tocar el controlador | Un nuevo `TranscriptionEngine` no exige cambios en `DictationController` |
| **L** (sustitución de Liskov) | Los fakes de las pruebas y los adaptadores reales cumplen el mismo contrato | `FakeEngine` y `WhisperCppEngine` son intercambiables |
| **I** (segregación de interfaces) | Puertos pequeños y separados | `ModelRepository`, `ModelDownloader` y `ActiveModelStore` son tres puertos, no uno grande |
| **D** (inversión de dependencias) | `core` depende de interfaces; `Main.kt` es la única raíz de composición, con inyección manual y sin framework de DI | `DictationController(recorder, engine, clipboard, permission, models, scope)` |

## 7. Puente nativo (C y Objective-C con JNA)

Kotlin llama a dos bibliotecas dinámicas con **JNA**, cargadas por **ruta absoluta** (`Native.load(File(libDir, "libdictado_whisper.dylib").absolutePath, ...)`, con codificación UTF-8). Se compilan con CMake (`native/CMakeLists.txt`).

### `libdictado_whisper.dylib` (`whisper_bridge.c`)

whisper.cpp v1.9.5 se enlaza **estáticamente** con Metal (`GGML_METAL=ON`) y el shader incrustado (`GGML_METAL_EMBED_LIBRARY=ON`). Así no hace falta Xcode completo: el shader se compila al ejecutar. API mínima:

| Función | Qué hace |
|---|---|
| `dictado_whisper_init(modelo)` | Carga el modelo y devuelve un contexto |
| `dictado_whisper_transcribe(ctx, muestras, n, idioma, hilos)` | Decodificación greedy, sin marcas de tiempo, segmentos concatenados en un búfer reservado con `malloc` |
| `dictado_whisper_free_text(texto)` | Libera el texto devuelto |
| `dictado_whisper_free(ctx)` | Libera el contexto |

### `libdictado_macos.dylib` (`macos_bridge.m`, compilado con `-fobjc-arc`)

Todas las llamadas a AppKit se ejecutan en el hilo principal (`dispatch_sync`).

| Función | Qué hace |
|---|---|
| `dictado_macos_configure_floating(titulo, info, len)` | Deja la ventana como panel flotante: en todos los escritorios, sobre pantalla completa, sin ocultarse al desactivar la app, nivel de estado. Devuelve 0 si fue bien, 1 si no encontró la ventana, 2 si no es un `NSPanel` |
| `dictado_macos_set_accessory`, `dictado_macos_ping` | Política de activación y comprobación |
| `dictado_macos_start_focus_guard`, `dictado_macos_restore_focus`, `dictado_macos_frontmost_app`, `dictado_macos_focus_state` | Recuerdan la última app activa y le devuelven el foco |
| `dictado_macos_activate_app` | Activa DictadoAI cuando se va a editar texto |
| `dictado_macos_mic_status`, `dictado_macos_request_mic` | Permiso de micrófono (AVFoundation) |

### Decisiones que costó descubrir

- **La bolita no roba el foco** porque una ventana AWT de tipo `UTILITY` con `focusableWindowState = false` se crea como `AWTWindow_Panel`, una subclase de `NSPanel` que no puede ser ventana clave. Después se le añade `NonactivatingPanel`.
- **`setLevel:` va después de `setFloatingPanel:`**, que reinicia el nivel.
- **`ComposeWindow` se crea en el hilo de AWT** (`SwingUtilities.invokeLater`).
- **Editar texto:** `focusableWindowState` no se aplica de forma fiable con la ventana visible en macOS. El panel se oculta, se cambia el estado y se vuelve a mostrar. Al pulsar un botón, el foco vuelve a la app anterior.
- **`-fobjc-arc`** es necesario: la app anterior guardada en una variable estática se liberaba y el programa se cerraba.
- **Sobrescribir una `.dylib` cargada** hace que macOS mate el proceso (SIGKILL, código 137); `build-native.sh` borra el archivo antes de copiarlo.

## 8. Cómo ampliar

### Añadir otro motor de transcripción

1. Crea una clase en `desktopApp/adapter/` (o en un módulo nuevo) que implemente `TranscriptionEngine`.
2. Cámbiala en `Main.kt`, que es la única raíz de composición. El controlador no se toca.

### Añadir un modelo al catálogo

1. Obtén su tamaño y SHA-256 de Hugging Face:
   ```sh
   curl -sIL https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-NOMBRE.bin | grep -i "x-linked-size\|x-linked-etag"
   ```
2. Añade un `WhisperModel` a `WhisperCatalog.models` en `core/domain/WhisperModel.kt`.

### Llevar la app a otra plataforma

1. Crea un módulo nuevo (por ejemplo `androidApp` o `windowsApp`) que dependa de `core`.
2. Implementa los puertos con APIs de esa plataforma (micrófono, portapapeles, almacenamiento de modelos, permisos).
3. Escribe la interfaz y su propia raíz de composición.
4. Añade la plataforma a la lista `platforms` de `core/module.yaml`. El núcleo no necesita cambios porque es Kotlin puro.

Lo que se deberá resolver en cada plataforma es la ventana flotante que no roba el foco, que en macOS es la parte más delicada (ver la sección 7).
