# DictadoAI (Kotlin) — app de dictado local para macOS

App de escritorio que graba voz, la transcribe localmente con Whisper y copia el texto al portapapeles.
Detalles de requisitos y plan en `../PLAN.md`.

## Módulos
- `core/`: dominio, puertos y lógica de aplicación (Kotlin puro).
- `desktopApp/`: adaptadores macOS, interfaz Compose y punto de entrada.

## Comandos (siempre desde esta carpeta)
```sh
source scripts/env.sh        # entorno contenido en .local/
scripts/setup-tools.sh       # una vez: descarga CMake a .local/
scripts/kt build             # compilar
scripts/kt test              # pruebas
scripts/kt run -m desktopApp # ejecutar
```
