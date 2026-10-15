# Cómo funciona

## 1. El flujo del usuario

1. La app se abre manualmente y aparece la **bolita flotante**. Está siempre visible, en todos los escritorios y sobre apps en pantalla completa.
2. **Clic en la bolita:** pide el micrófono (la primera vez) y empieza a grabar. Junto a la bolita se abre un panel con el cronómetro.
3. Mientras grabas puedes **Pausar**, **Terminar** o **Cancelar**.
   - **Pausar** transcribe lo grabado y te deja revisar el texto.
   - **Terminar** transcribe y muestra el resultado.
4. Con la sesión en **pausa** puedes **editar el texto** haciendo clic en él, y luego **Reanudar**. Lo nuevo se inserta **donde esté el cursor**, con un espacio de separación. **Descartar** tira la sesión.
5. En el **resultado** puedes **Seguir dictando** (añade más voz al mismo texto), **Copiar** o **Cerrar**.
6. Después de cada fragmento, **todo el texto de la sesión se copia al portapapeles**. Pega con **⌘V** donde quieras: Terminal, Codex CLI, Gemini CLI, un navegador, un editor…

La app **no roba el foco**: el cursor sigue en la app donde estabas escribiendo. Solo toma el foco mientras editas el texto del panel y lo devuelve al pulsar cualquier botón.

La app no aparece en el Dock ni en ⌘Tab. Vive en la bolita y en un icono de la barra de menús con **Modelos…** y **Salir**.

## 2. La sesión de dictado

El texto vive en un **documento de sesión** (texto más posición del cursor). Cada fragmento grabado se transcribe y se inserta en el cursor:

- se reemplaza la selección, si la hay;
- se añade un espacio antes y después si el vecino no es un espacio;
- el cursor queda tras lo insertado.

Reglas:
- **Cancelar** el primer fragmento descarta todo. En un fragmento posterior, conserva el texto de la sesión.
- Un clic en la bolita fuera de una grabación **empieza una sesión nueva** (el texto anterior se descarta). No hay historial.
- Si un fragmento no tiene voz o falla la transcripción y ya había texto, se conserva el texto y el panel avisa.
- **Límite de 10 minutos por fragmento.** Al llegar, se detiene solo, se transcribe y el panel lo avisa.
- El idioma de dictado es **español**.

## 3. El audio

- Se captura con `javax.sound.sampled` en **16 kHz, 16 bits, mono**, el formato que espera Whisper.
- Si el micrófono no admite 16 kHz, se graba a 48 kHz o 44.1 kHz y el `Resampler` convierte.
- Las muestras se acumulan **solo en memoria**. Cancelar o terminar las descarta: el audio **nunca se guarda en disco**.
- **Silencio:** si el nivel (RMS) del fragmento es menor que `0.005`, se considera silencio y ni siquiera se llama a Whisper. Esto evita que Whisper invente texto.
- **Volumen de entrada:** con el volumen del micrófono bajo (por ejemplo, 50 % con unos EarPods), el audio llega casi en silencio y la app dice "No se detectó voz". Configuración del Sistema → Sonido → Entrada: sube el volumen al **100 %**.

## 4. La transcripción

- Motor: **whisper.cpp** con Metal, ejecutado localmente en la GPU del Mac.
- El modelo se carga al arrancar y se mantiene en memoria, así que cada dictado empieza sin espera. Cargarlo tarda unos 10 s la primera vez (se compilan los shaders de Metal).
- Parámetros: idioma `es`, sin traducción, decodificación greedy, 4 hilos.
- Después, `TranscriptCleaner` quita etiquetas de ruido (`[BLANK_AUDIO]`, `(música)`, `[aplausos]`…) y descarta alucinaciones conocidas ("Subtítulos realizados por la comunidad de Amara.org", "¡Suscríbete!"…). Si no queda texto, es "sin voz".
- Referencia (Mac mini M4, 16 GB, Large v3 Turbo): ~1.8 GB de memoria, y unos 5 s de audio se transcriben en 1 s.

## 5. Modelos de voz

La app incluye un gestor de modelos (**Modelos…** en la barra de menús). El catálogo tiene 7 modelos Whisper multilingües, de Tiny (78 MB) a Large v3 (3.1 GB). El recomendado es **Large v3 Turbo** (1.6 GB).

- **Descargar:** con progreso y reanudación. Se verifica el SHA-256 al terminar.
- **Usar:** lo carga como modelo activo. Solo con la bolita libre.
- **Eliminar:** libera espacio. No se puede eliminar el modelo en uso.
- Si no hay ningún modelo, el panel pide elegir uno; al terminar la descarga, se carga solo.

## 6. Permisos

- **Micrófono:** se pide la primera vez que se dicta.
  - En **desarrollo**, el permiso se concede a la aplicación desde la que lanzas el comando (Terminal, iTerm o el IDE).
  - En la **app instalada**, se concede a **DictadoAI**.
  - Si se deniega, el panel explica cómo activarlo y ofrece un botón que abre Configuración del Sistema.
- **Accesibilidad:** no se necesita. La app no pega por ti; tú pegas con ⌘V.

## 7. Errores

| Situación | Qué ves |
|---|---|
| Micrófono denegado | "Sin permiso de micrófono", con botón a Configuración del Sistema |
| Micrófono no disponible | "No se encontró un micrófono" |
| Sin voz en la primera grabación | "No se detectó voz"; el portapapeles queda intacto |
| Falla la transcripción | "Falló la transcripción" (o un aviso si ya había texto) |
| No hay modelo | "No hay modelo de voz" con botón **Elegir modelo** |
| No se pudo cargar el modelo | "No se pudo cargar el modelo de voz" con botón **Elegir modelo** |
| Falla una descarga | "Error al descargar" con botón **Reintentar** |

## 8. Privacidad y datos en disco

- El audio y la transcripción se procesan **solo en tu Mac**.
- La app **solo se conecta a internet para descargar modelos** (a Hugging Face), cuando tú pulsas **Descargar**. Para dictar no necesita conexión.
- Datos que guarda la app instalada, en `~/Library/Application Support/DictadoAI/`:

  | Ruta | Contenido |
  |---|---|
  | `models/` | Los modelos descargados |
  | `config.properties` | Posición de la bolita y modelo elegido |
  | `logs/dictadoai.log` | Registro de la app, para diagnosticar problemas |

  En desarrollo estas rutas están dentro del proyecto, en `.local/`.
