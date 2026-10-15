package com.dictadoai.desktop.config

import java.io.File

/**
 * Resuelve dónde están las bibliotecas nativas, los modelos y los datos.
 *
 * - Desarrollo: variable de entorno `DICTADO_DEV_HOME` (la fija `scripts/env.sh`, apunta a `.local/`).
 * - App empaquetada: propiedad de sistema `dictado.appDir` (la fija jpackage). Los modelos y los datos van
 *   en `~/Library/Application Support/DictadoAI/`, no dentro de la app, para que la app pese poco.
 */
class AppPaths private constructor(
    val libDir: File,
    val modelsDir: File,
    val dataDir: File,
    /** True en la app instalada (`dictado.appDir`), false en desarrollo. */
    val isPackaged: Boolean,
) {
    companion object {
        fun resolve(
            env: Map<String, String> = System.getenv(),
            props: (String) -> String? = System::getProperty,
        ): AppPaths {
            props("dictado.appDir")?.let { appDir ->
                val data = File(System.getProperty("user.home"), "Library/Application Support/DictadoAI")
                return AppPaths(libDir = File(appDir, "lib"), modelsDir = File(data, "models"), dataDir = data, isPackaged = true)
            }
            env["DICTADO_DEV_HOME"]?.let { devHome ->
                val root = File(devHome)
                return AppPaths(libDir = File(root, "lib"), modelsDir = File(root, "models"), dataDir = File(root, "dev-data"), isPackaged = false)
            }
            error("No se pudo resolver el entorno: define DICTADO_DEV_HOME (desarrollo) o dictado.appDir (app empaquetada)")
        }
    }
}
