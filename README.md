# DictadoAI: dictado local para macOS

DictadoAI es una app de escritorio para macOS que convierte tu voz en texto **sin internet**. En pantalla aparece una pequeña **bolita flotante**, siempre visible:

- Al hacer clic en ella, empieza a grabar.
- Cuando terminas, transcribe el audio en tu propia Mac con el modelo **Whisper `large-v3-turbo`**, mediante [whisper.cpp](https://github.com/ggml-org/whisper.cpp) y Metal.
- **Copia el texto al portapapeles** automáticamente.

Después solo tienes que pegarlo con **⌘V** donde quieras: la Terminal, Codex CLI, Gemini CLI, el navegador, un editor…

La bolita **no roba el foco**: el cursor sigue en la app donde estabas escribiendo.

## Documentación

| Documento | Para qué sirve |
|---|---|
| Este README | Qué es, requisitos, instalación desde GitHub y primeros pasos |
| [docs/funcionamiento.md](docs/funcionamiento.md) | Cómo funciona: flujo de uso, audio, transcripción, modelos, permisos, errores y privacidad |
| [docs/arquitectura.md](docs/arquitectura.md) | Arquitectura hexagonal y SOLID, máquina de estados, puente nativo y cómo ampliar el proyecto |
| [docs/desarrollo.md](docs/desarrollo.md) | Entorno, scripts, pruebas, estructura del código y solución de problemas |
| [docs/empaquetado-y-uso.md](docs/empaquetado-y-uso.md) | Generar el `.app` y el `.dmg`, instalar, usar y desinstalar |

---

## Índice

1. [En qué consiste](#1-en-qué-consiste)
2. [Requisitos](#2-requisitos)
3. [Instalar las herramientas base (Xcode CLT, Java y Kotlin Toolchain)](#3-instalar-las-herramientas-base)
4. [Clonar el repositorio y descargar las dependencias paso a paso](#4-descargar-las-dependencias-del-proyecto-paso-a-paso)
5. [Compilar y probar](#5-compilar-y-probar)
6. [Ejecutar la app](#6-ejecutar-la-app)
7. [Cómo se usa](#7-cómo-se-usa)
8. [Modos de diagnóstico](#8-modos-de-diagnóstico)
9. [Qué hay en `.local/`](#9-qué-hay-en-local)
10. [Solución de problemas](#10-solución-de-problemas)
11. [Estructura del proyecto](#11-estructura-del-proyecto)

---

## 1. En qué consiste

### Flujo de uso

```
 clic en la bolita ──► grabando ──► Pausar ──► (revisas o editas el texto) ──► Reanudar ──► …
                            │                                                        │
                            └──────────────── Terminar ◄─────────────────────────────┘
                                                 │
                                    transcripción local (Whisper)
                                                 │
                         texto en el panel + copiado al portapapeles ──► ⌘V en tu app
```

- **Pausar** transcribe lo que llevas dictado y te deja revisar el texto.
- Puedes **hacer clic en el texto para editarlo**. Lo siguiente que dictes se inserta **donde esté el cursor**, con un espacio automático.
- Después de cada fragmento, se copia al portapapeles **todo** el texto de la sesión.
- **Seguir dictando** (en la pantalla final) añade más voz al mismo texto.
- **Cancelar** o **Descartar** tiran el audio sin tocar el portapapeles.
- Cada grabación dura como máximo **10 minutos**. Al llegar al límite se transcribe automáticamente.
- El audio **solo vive en memoria**: nunca se guarda en disco ni sale de tu Mac.

### Arquitectura (resumen)

El proyecto usa **arquitectura hexagonal (puertos y adaptadores)** y principios **SOLID**. Está pensado para llevarlo en el futuro a otras plataformas (iOS, Android, Windows, Linux) sin tocar el núcleo.

| Módulo | Qué contiene | Depende de |
|---|---|---|
| `core/` | Dominio (máquina de estados del dictado, documento de transcripción, limpieza del texto), **puertos** (interfaces) y `DictationController`. Kotlin puro. | Solo `kotlinx-coroutines` |
| `desktopApp/` | **Adaptadores** de macOS (micrófono, Whisper, portapapeles, ventanas, permisos), la interfaz en Compose y `Main.kt`, la raíz de composición. | `core`, Compose, JNA |
| `native/` | Puentes nativos en C (`whisper_bridge.c`) y Objective-C (`macos_bridge.m`). Se compilan a `libdictado_whisper.dylib` y `libdictado_macos.dylib`. | whisper.cpp, AppKit, AVFoundation |

Kotlin llama a las `.dylib` mediante **JNA**.

---

## 2. Requisitos

| Requisito | Detalle |
|---|---|
| Mac | Apple Silicon (M1 o posterior). Las bibliotecas nativas se compilan para `arm64`. |
| macOS | Una versión reciente. Se desarrolló en macOS 26 (Tahoe). |
| Disco | ~4 GB libres: el modelo ocupa 1.6 GB, más whisper.cpp, el toolchain y las cachés. |
| RAM | La app usa ~1.8 GB mientras está abierta (el modelo vive en memoria). |
| Micrófono | Cualquiera: el integrado, los EarPods con jack, uno USB… |
| Internet | Solo la **primera vez**, para descargar las dependencias y el modelo. Después funciona sin conexión. |
| Command Line Tools de Xcode | Necesarias. Aportan `clang`, `git` y los SDK de macOS. Ver [3.1](#31-command-line-tools-de-xcode). |
| JDK 21 y JDK 25 | JDK 21 para los scripts y JDK 25 para empaquetar la app. Ver [3.2](#32-java-jdk-21). |
| Kotlin Toolchain | **Ya viene incluido** en el proyecto (script `./kotlin`). Ver [3.3](#33-kotlin-toolchain). |
| CMake | **No hay que instalarlo**: el proyecto lo descarga dentro de `.local/`. |

> **Regla del proyecto:** todo lo que se descarga o se genera queda dentro de `voice-transcript-kotlin/.local/`. Esa carpeta está en `.gitignore`. Si la borras, se vuelve a crear siguiendo los pasos de la sección 4.

---

## 3. Instalar las herramientas base

Estas tres herramientas se instalan **una sola vez por Mac**.

### 3.1 Command Line Tools de Xcode

**No necesitas Xcode completo** (los ~15 GB de la App Store). Basta con las **Command Line Tools**. Xcode completo no hace falta porque whisper.cpp se compila con `GGML_METAL_EMBED_LIBRARY=ON`: el shader de Metal va incrustado en la biblioteca y se compila en el momento de ejecutar, así que no hace falta el compilador `metal` de Xcode.

1. Comprueba si ya las tienes:
   ```sh
   xcode-select -p
   clang --version
   ```
   Si `xcode-select -p` imprime una ruta (por ejemplo `/Library/Developer/CommandLineTools`) y `clang` responde, ya las tienes. Pasa a 3.2.
2. Si no las tienes, instálalas:
   ```sh
   xcode-select --install
   ```
   Se abre una ventana de macOS: pulsa **Instalar** y acepta la licencia. Tarda unos minutos.
3. Vuelve a ejecutar `clang --version` para confirmar la instalación.

> Xcode completo solo haría falta en el futuro, para compilar la versión **iOS**. La documentación del Kotlin Toolchain lo pide únicamente para los módulos de iOS.

### 3.2 Java (JDK 21 y 25)

El proyecto usa **JDK 21** para los scripts y **JDK 25** para empaquetar la app (el código se compila a Java 25):
- `scripts/env.sh` lo selecciona con `/usr/libexec/java_home -v 21`;
- `scripts/package-macos.sh` usa `jpackage` del **JDK 25** para generar el `.app` (ver la [sección 6](#como-app-instalable-app--dmg)).

1. Comprueba si ya lo tienes:
   ```sh
   /usr/libexec/java_home -V
   ```
   Debe aparecer una línea con `21.x`, por ejemplo `21.0.x (arm64) "Oracle Corporation" - "Java SE 21"`.
2. Si no aparece, descarga el instalador **macOS ARM64 DMG** de JDK 21 (y de JDK 25, para empaquetar) desde una de estas páginas:
   - Oracle: <https://www.oracle.com/java/technologies/downloads/#java21>
   - Eclipse Temurin: <https://adoptium.net/temurin/releases/?version=21>

   Ábrelo y sigue el asistente. Queda instalado en `/Library/Java/JavaVirtualMachines/`.
3. Verifica:
   ```sh
   /usr/libexec/java_home -v 21
   ```
   Debe imprimir la ruta del JDK 21.

Puedes tener otras versiones instaladas (17 o 25); no estorban. Los scripts del proyecto siempre eligen la 21 sin cambiar tu Java por defecto.

> El Kotlin Toolchain descarga **su propio** Java (dentro de `.local/kotlin-cli`) para ejecutarse a sí mismo. Ese Java es independiente del JDK 21.

### 3.3 Kotlin Toolchain

Este proyecto **no usa Gradle**. Usa el **[Kotlin Toolchain](https://kotlinlang.org/docs/multiplatform/kotlin-toolchain.html)** de JetBrains, que está en fase Alpha y está basado en Amper. Su configuración está en `project.yaml`, `*/module.yaml` y `libs.versions.toml`.

**No necesitas instalar nada.** El proyecto incluye el script `./kotlin` (el *wrapper*), que fija la versión `0.13.0` y su SHA-256:
- La primera vez que se ejecuta, descarga el toolchain y verifica su SHA-256.
- Lo descarga en `.local/kotlin-cli`, porque `scripts/env.sh` define `KOTLIN_CLI_BOOTSTRAP_CACHE_DIR`.

En este proyecto **siempre** se usa `scripts/kt`, que es ese wrapper más el entorno contenido en `.local/`.

<details>
<summary>Opcional: instalar el comando <code>kotlin</code> globalmente (para crear otros proyectos)</summary>

Según la [documentación oficial](https://kotlinlang.org/docs/multiplatform/kotlin-toolchain.html), hay dos formas de instalarlo en macOS:

```sh
# Opción 1: con SDKMAN!
sdk install kotlintoolchain

# Opción 2: con el script oficial
curl -fsSL https://kotl.in/install.sh | sh
exec $SHELL          # reinicia la terminal para que 'kotlin' esté disponible

# Verificar
kotlin --version
```

Para crear un proyecto nuevo:
1. Ejecuta `kotlin new` en la carpeta donde quieras crearlo.
2. Elige la plantilla **Compose Multiplatform application**.
3. Acepta los *targets* y escribe un *project ID*.

Ten en cuenta que esta instalación global escribe **fuera** del proyecto, en tu carpeta de usuario. Para trabajar en DictadoAI no hace falta.

Si usas IntelliJ IDEA, instala los plugins **Kotlin Multiplatform** y **Kotlin Toolchain**. Con ellos el IDE reconoce la estructura del proyecto y crea las configuraciones de ejecución.
</details>

---

## 4. Descargar las dependencias del proyecto paso a paso

### Paso 0: Clonar el repositorio desde GitHub

Elige una carpeta para tus proyectos y clona el repositorio:

```sh
cd ~/Development                 # o la carpeta donde guardes tus proyectos
git clone https://github.com/Giozar/voice-transcript-kotlin.git
cd voice-transcript-kotlin
```

El repositorio solo trae **código y scripts**. Lo pesado no está en Git y se descarga o genera en los pasos siguientes, dentro de `.local/`:
- el modelo de 1.6 GB;
- whisper.cpp;
- CMake;
- el toolchain;
- las librerías.

Si los scripts no tienen permiso de ejecución después de clonar (error `permission denied`), dáselo con:
```sh
chmod +x kotlin scripts/*.sh scripts/kt
```

A partir de aquí, ejecuta **todos** los comandos desde la carpeta raíz del repositorio (`voice-transcript-kotlin/`).

### Paso 1: Cargar el entorno contenido

```sh
source scripts/env.sh
```

Este comando:
- crea las subcarpetas de `.local/`;
- exporta `JAVA_HOME` (JDK 21), `KOTLIN_CLI_BOOTSTRAP_CACHE_DIR` y `DICTADO_DEV_HOME`;
- añade CMake al `PATH`;
- envía los temporales de Java, Skiko y JNA a `.local/`.

Hay que repetirlo **en cada terminal nueva** antes de usar `cmake` o `java` directamente. Los scripts `scripts/kt` y `build-native.sh` ya lo cargan por su cuenta.

Para comprobar que funcionó:
```sh
echo $JAVA_HOME      # debe terminar en .../jdk-21.../Contents/Home
java -version        # debe decir 21
```

> Verás el aviso `Picked up JAVA_TOOL_OPTIONS: ...` cada vez que se ejecute Java. Es normal: indica que Java usa las carpetas de `.local/`.

### Paso 2: Descargar CMake (una vez, ~80 MB)

```sh
scripts/setup-tools.sh
```

El script:
1. consulta en GitHub la última versión estable de CMake;
2. descarga `cmake-<versión>-macos-universal.tar.gz` y **verifica su SHA-256** con el que publica Kitware;
3. lo extrae en `.local/tools/cmake/`.

Si ya está instalado, solo muestra la versión. Para comprobarlo:
```sh
cmake --version
```

### Paso 3: Compilar whisper.cpp y los puentes nativos (unos minutos)

```sh
scripts/build-native.sh
```

El script:
1. clona whisper.cpp en la versión fijada **`v1.9.5`** dentro de `.local/src/whisper.cpp`;
2. configura con CMake el proyecto `native/` para `arm64` en modo Release, con **Metal activado y el shader incrustado**;
3. compila whisper.cpp de forma estática junto con los dos puentes;
4. copia el resultado a `.local/lib/`:
   - `libdictado_whisper.dylib`: el motor de transcripción;
   - `libdictado_macos.dylib`: las ventanas flotantes, el foco y el permiso de micrófono.

Para comprobarlo:
```sh
ls -la .local/lib
```

> Mientras la app esté abierta, no vuelvas a compilar los nativos: macOS cerraría la app al cambiar una biblioteca que está en uso.

### Paso 4: Descargar un modelo de voz

Puedes hacerlo **desde la propia app** (menú de la barra → **Modelos…**, ver la [sección 7](#modelos-de-voz)). Ahí eliges el tamaño que quieras.

Si prefieres hacerlo desde la terminal, el modelo recomendado (Large v3 Turbo, ~1.6 GB) también se descarga con:

```sh
scripts/download-model.sh
```

El script:
1. pide a Hugging Face el SHA-256 oficial de `ggml-large-v3-turbo.bin` (cabecera `x-linked-etag`);
2. descarga el modelo a `.local/models/ggml-large-v3-turbo.bin.part`. Si se corta, vuelve a ejecutar el script: **la descarga se reanuda**;
3. verifica el SHA-256 y solo entonces lo renombra a `ggml-large-v3-turbo.bin`. Si no coincide, borra la descarga.

Valores esperados:
- SHA-256: `1fc70f774d38eb169993ac391eea357ef47c88757ef72ee5943879b7e8e2bc69`
- Tamaño: 1 624 555 275 bytes

### Paso 5: Descargar las dependencias de Kotlin y compilar

```sh
scripts/kt build
```

La primera vez, el wrapper descarga varias cosas, así que tarda más:
- el Kotlin Toolchain (en `.local/kotlin-cli`);
- las librerías del proyecto: Compose Multiplatform, kotlinx-coroutines, JNA, logback… (en `.local/kotlin-caches`).

Las siguientes compilaciones son rápidas. El comando debe terminar sin errores.

### Paso 6 (opcional): Generar un audio de prueba

```sh
scripts/make-sample.sh
```

Usa la voz **Paulina** de macOS (`say`) y `afconvert` para crear `.local/samples/prueba-es.wav` (16 kHz, mono). Sirve para probar Whisper sin micrófono (ver la [sección 8](#8-modos-de-diagnóstico)).

Si macOS no tiene la voz Paulina, añádela en **Configuración del Sistema → Accesibilidad → Contenido leído → Voz del sistema → Gestionar voces → Español (México)**.

### Resumen (copiar y pegar)

```sh
git clone https://github.com/Giozar/voice-transcript-kotlin.git
cd voice-transcript-kotlin
source scripts/env.sh
scripts/setup-tools.sh
scripts/build-native.sh
scripts/download-model.sh
scripts/kt build
```

---

## 5. Compilar y probar

| Comando | Qué hace |
|---|---|
| `scripts/kt build` | Compila todos los módulos. |
| `scripts/kt build -m desktopApp` | Compila solo la app de escritorio. |
| `scripts/kt test` | Ejecuta todas las pruebas: las de `core` (máquina de estados, inserción de texto, limpieza, controlador) y las del `Resampler` en `desktopApp`. |
| `scripts/kt test -m core` | Solo las pruebas del núcleo. |
| `scripts/kt --help` | Ayuda del toolchain. También existe `scripts/kt run --help`, etc. |

---

## 6. Ejecutar la app

### En desarrollo

1. **Sube el volumen de entrada del micrófono.** Ve a **Configuración del Sistema → Sonido → Entrada**, elige tu micrófono y lleva **el volumen de entrada al 100 %**. Con un volumen bajo (por ejemplo, 50 % con EarPods), el audio llega casi en silencio y la app responde "No se detectó voz".
2. Desde `voice-transcript-kotlin/`, ejecuta:
   ```sh
   scripts/dev.sh
   ```
   Si faltan CMake o las bibliotecas nativas, el script los prepara antes de lanzar la app. Es equivalente a `scripts/kt run -m desktopApp --no-compose-hot-reload`.
   La terminal queda ocupada mientras la app está abierta. Ahí verás los registros.
3. Aparece la **bolita** en la esquina inferior derecha, o en la última posición donde la dejaste:
   - **gris con un círculo girando:** está cargando el modelo (~10 s la primera vez, mientras se compilan los shaders de Metal);
   - **azul:** está lista.
4. **Permiso de micrófono:** la primera vez que grabes, macOS pedirá permiso **para la app desde la que lanzaste el comando** (Terminal, iTerm o el terminal de tu IDE), no para "DictadoAI". Acéptalo. Si lo rechazaste, actívalo en **Configuración del Sistema → Privacidad y seguridad → Micrófono**.
5. **Cerrar la app:** haz clic en el icono de DictadoAI en la **barra de menús**, arriba a la derecha, y elige **Salir**. La app no aparece en el Dock ni en ⌘Tab. También puedes pulsar `Ctrl+C` en la terminal donde la lanzaste.

> Si no ves la bolita, comprueba que no quedó otra instancia abierta (`ps aux | grep desktopApp`), sal de ella con "Salir" y vuelve a lanzar la app.

### Como app instalable (`.app` / `.dmg`)

Para tener la app como cualquier otra aplicación de tu Mac, genera el `.app` y el `.dmg`:

```sh
cd ruta/a/voice-transcript-kotlin
scripts/package-macos.sh
```

El script:
1. compila el jar ejecutable (`scripts/kt package`);
2. genera `DictadoAI.app` con `jpackage` del **JDK 25**, que incluye su propio Java: no necesitas tenerlo instalado para usar la app;
3. añade a `Info.plist` que no aparezca en el Dock (`LSUIElement`) y el texto del permiso de micrófono;
4. firma la app de forma ad hoc (sin notarizar);
5. crea `DictadoAI.dmg` con la app y un acceso a **Aplicaciones**.

Resultado, dentro de `.local/package/out/`:
- `DictadoAI.app`
- `DictadoAI.dmg` (unos 80 MB, sin el modelo de voz)

**Instalar:** abre `DictadoAI.dmg` y arrastra **DictadoAI** a **Aplicaciones**.

**Primera vez:**
1. Abre **DictadoAI** desde Aplicaciones (o desde Launchpad).
2. Como no hay modelo, el panel te avisa: pulsa **Elegir modelo**, luego **Descargar** en *Large v3 Turbo* (1.6 GB) y después **Usar**.
   - Si ya tienes el modelo de desarrollo, puedes evitar la descarga copiándolo (un solo comando, en tu terminal):
     ```sh
     mkdir -p ~/"Library/Application Support/DictadoAI/models"
     cp .local/models/ggml-large-v3-turbo.bin ~/"Library/Application Support/DictadoAI/models/"
     ```
3. Al dictar por primera vez, macOS pedirá permiso de micrófono para **DictadoAI** (no para la Terminal). Acéptalo.
4. Para salir, usa **Salir** en el menú de la barra superior.

Si macOS dice que no puede verificar la app, abre **Configuración del Sistema → Privacidad y seguridad**, baja hasta el aviso de DictadoAI y pulsa **Abrir igualmente**. Como la app se firma en tu propio Mac, normalmente no hace falta.

**Dónde están tus datos** (no dentro de la app, así que sobreviven a las actualizaciones):
- modelos: `~/Library/Application Support/DictadoAI/models/`
- posición de la bolita y modelo elegido: `~/Library/Application Support/DictadoAI/config.properties`
- registro de la app: `~/Library/Application Support/DictadoAI/logs/dictadoai.log`

**Desinstalar:** borra `DictadoAI` de Aplicaciones. Si quieres borrar también los modelos y la configuración, borra la carpeta `~/Library/Application Support/DictadoAI`.

**Arrancar al iniciar sesión:** Configuración del Sistema → General → Ítems de inicio → **+** → elige DictadoAI.

---

## 7. Cómo se usa

| Estado | Lo que ves | Botones |
|---|---|---|
| Lista | Bolita azul | Clic → empieza a grabar |
| Grabando | Bolita roja con pulso, cronómetro | **Pausar** · **Terminar** · **Cancelar** |
| Transcribiendo | Círculo girando | Los clics se ignoran |
| En pausa | Texto editable de la sesión | **Reanudar** · **Terminar** · **Descartar** |
| Resultado | Texto final (ya está en el portapapeles) | **Seguir dictando** · **Copiar** · **Cerrar** |

Consejos:
- **Arrastra** la bolita para moverla; recuerda su posición al reiniciar. La posición se guarda en `.local/dev-data/config.properties` en desarrollo.
- **Editar:** haz clic en el texto del panel. Al pulsar cualquier botón, el foco vuelve a la app donde estabas.
- Pega con **⌘V** en la app donde estabas escribiendo; nunca perdiste el foco.
- La app limpia las "alucinaciones" típicas de Whisper en silencio, como "Subtítulos realizados por la comunidad de Amara.org" o `[BLANK_AUDIO]`.

---

### Modelos de voz

El modelo de voz es lo que convierte tu voz en texto. Puedes cambiarlo en cualquier momento desde el menú de la barra superior → **Modelos…**. Son modelos Whisper multilingües (sirven para español):

| Modelo | Tamaño | Uso recomendado |
|---|---|---|
| Tiny | 78 MB | Solo para probar; precisión baja |
| Base | 148 MB | Ligero, precisión básica |
| Small | 488 MB | Equilibrado para equipos modestos |
| Medium | 1.5 GB | Buena precisión, más lento |
| Large v3 Turbo (q5) | 574 MB | Como Turbo, comprimido: ocupa menos y usa menos memoria |
| **Large v3 Turbo** | **1.6 GB** | **Recomendado**: muy buena precisión con buena velocidad |
| Large v3 | 3.1 GB | La mayor precisión; lento y pesado |

En la pantalla de modelos:
- **Descargar:** baja el modelo con una barra de progreso. Si se corta, vuelve a pulsarlo: **se reanuda** donde quedó. Al terminar se verifica su SHA-256.
- **Usar:** lo convierte en el modelo activo. Solo se puede cambiar con la bolita libre (no mientras grabas o transcribes). Tarda unos segundos en cargarse.
- **Eliminar:** borra el archivo para liberar espacio. No se puede eliminar el modelo que está en uso.

Si no tienes ningún modelo descargado, la bolita abre directamente esta pantalla.

Dónde se guardan:
- en desarrollo: `voice-transcript-kotlin/.local/models/`;
- en la app instalada: `~/Library/Application Support/DictadoAI/models/`.

El modelo elegido se guarda en `config.properties`, junto con la posición de la bolita.

---

## 8. Modos de diagnóstico

Sirven para comprobar Whisper o el micrófono sin la interfaz.

```sh
# Transcribe un WAV y muestra texto, tiempos y memoria
scripts/kt run -m desktopApp --no-compose-hot-reload -- --transcribe .local/samples/prueba-es.wav

# Graba N segundos (por defecto 5) del micrófono y los transcribe
scripts/kt run -m desktopApp --no-compose-hot-reload -- --record 5
```

Lo que todo va después de `--` se pasa como argumento a la app.

Mediciones de referencia en un Mac mini M4:

| Medida | Valor |
|---|---|
| Carga del modelo | ~9.6 s |
| Memoria (RSS) | ~1.8 GB |
| Transcripción de 5.7 s de audio | 0.9–1.3 s |

---

## 9. Qué hay en `.local/`

| Carpeta | Contenido | Quién la crea |
|---|---|---|
| `kotlin-cli/` | Kotlin Toolchain y su Java | `./kotlin` (vía `scripts/kt`) |
| `kotlin-caches/` | Librerías descargadas (Maven) | `scripts/kt` |
| `tools/cmake/` | CMake | `setup-tools.sh` |
| `src/whisper.cpp/` | Código de whisper.cpp v1.9.5 | `build-native.sh` |
| `native-build/` | Compilación intermedia de CMake | `build-native.sh` |
| `lib/` | `libdictado_whisper.dylib`, `libdictado_macos.dylib` | `build-native.sh` |
| `models/` | Modelos Whisper descargados (ver [Modelos de voz](#modelos-de-voz)) | La app, o `download-model.sh` para el recomendado |
| `dev-data/` | `config.properties` (posición de la bolita) | La app |
| `samples/` | `prueba-es.wav` | `make-sample.sh` |
| `tmp/`, `skiko/`, `jna/` | Temporales de Java, Compose y JNA | `env.sh` |

Para empezar de cero, borra `.local/` y repite la sección 4. Si quieres evitar volver a descargar 1.6 GB, conserva `.local/models/`.

---

## 10. Solución de problemas

| Problema | Causa probable | Solución |
|---|---|---|
| "No se detectó voz" aunque hablaste | Volumen de entrada bajo o micrófono equivocado | Configuración del Sistema → Sonido → Entrada: elige el micrófono correcto y sube el volumen al 100 %. Prueba con `--record 5`: un RMS menor que 0.005 se considera silencio. |
| "No se pudo cargar el modelo de voz" | El modelo está dañado o no se pudo cargar | Menú **Modelos…** → **Elegir modelo**: elige otro, o elimina el actual y descárgalo de nuevo. |
| "No hay modelo de voz" | No hay ningún modelo descargado | Abre **Modelos…** y descarga uno (recomendado: Large v3 Turbo). |
| "La descarga no coincide con el SHA-256" | La descarga se corrompió | Pulsa **Reintentar**: el archivo parcial se borró y se empieza de nuevo. |
| Error al cargar `libdictado_*.dylib` | Faltan los nativos | `scripts/build-native.sh` |
| La app se cierra sola (código 137) justo después de compilar los nativos | Se reemplazó una `.dylib` mientras la app la usaba | Cierra la app y vuelve a abrirla. |
| macOS no pide permiso de micrófono, o graba silencio | El permiso se denegó a la Terminal | Configuración del Sistema → Privacidad y seguridad → Micrófono → activa tu terminal y reiníciala. |
| `java_home: Unable to find any JVMs matching version "21"` | JDK 21 no está instalado | Ver [3.2](#32-java-jdk-21). |
| `xcrun: error: invalid active developer path` | Faltan las Command Line Tools | `xcode-select --install` |
| `cmake: command not found` | No se ejecutó `setup-tools.sh` o no cargaste el entorno | `scripts/setup-tools.sh` y `source scripts/env.sh` |
| Dos bolitas en pantalla | Hay dos instancias abiertas | Sal de ambas desde la barra de menús y abre una sola. |

---

## 11. Estructura del proyecto

```
voice-transcript-kotlin/
├── kotlin                    wrapper del Kotlin Toolchain (versión fijada)
├── project.yaml              módulos: core, desktopApp
├── libs.versions.toml        versiones de las dependencias
├── core/
│   ├── module.yaml           kmp/lib (jvm por ahora)
│   ├── src/com/dictadoai/core/
│   │   ├── domain/           DictationState, DictationEvent, DictationStateMachine,
│   │   │                     TranscriptDocument, TranscriptInserter, TranscriptCleaner, RecordingLimits…
│   │   ├── port/             AudioRecorder, TranscriptionEngine, ClipboardWriter,
│   │   │                     MicrophonePermission, BubblePositionStore
│   │   └── application/      DictationController
│   └── test/                 pruebas con fakes
├── desktopApp/
│   ├── module.yaml           jvm/app, mainClass com.dictadoai.desktop.MainKt
│   ├── src/com/dictadoai/desktop/
│   │   ├── Main.kt           raíz de composición y modos de diagnóstico
│   │   ├── config/           AppPaths (rutas de desarrollo y de la app empaquetada)
│   │   ├── adapter/          audio, whisper, macos, clipboard, settings
│   │   └── ui/               BubbleWindow, PanelWindow, TrayMenu, tema
│   └── test/                 pruebas del Resampler
├── native/                   CMakeLists.txt, whisper_bridge.c, macos_bridge.m
├── docs/                     funcionamiento, arquitectura, desarrollo y empaquetado
└── scripts/                  env.sh, kt, dev.sh, setup-tools.sh, build-native.sh,
                              download-model.sh, make-sample.sh, package-macos.sh
```