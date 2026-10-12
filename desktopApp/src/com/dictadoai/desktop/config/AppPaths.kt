package com.dictadoai.desktop.config

import java.io.File

/**
 * Resuelve dónde están el modelo, las bibliotecas nativas y los datos.
 *
 * - Desarrollo: variable de entorno `DICTADO_DEV_HOME` (la fija `scripts/env.sh`, apunta a `.local/`).
 * - App empaquetada: propiedad de sistema `dictado.appDir` (la fija jpackage).
 */
class AppPaths private constructor(
    val libDir: File,
    val modelFile: File,
    val dataDir: File,
) {
    companion object {
        const val MODEL_FILE_NAME = "ggml-large-v3-turbo.bin"

        fun resolve(
            env: Map<String, String> = System.getenv(),
            props: (String) -> String? = System::getProperty,
        ): AppPaths {
            props("dictado.appDir")?.let { appDir ->
                val root = File(appDir)
                return AppPaths(
                    libDir = File(root, "lib"),
                    modelFile = File(root, "models/$MODEL_FILE_NAME"),
                    dataDir = File(System.getProperty("user.home"), "Library/Application Support/DictadoAI"),
                )
            }
            env["DICTADO_DEV_HOME"]?.let { devHome ->
                val root = File(devHome)
                return AppPaths(
                    libDir = File(root, "lib"),
                    modelFile = File(root, "models/$MODEL_FILE_NAME"),
                    dataDir = File(root, "dev-data"),
                )
            }
            error("No se pudo resolver el entorno: define DICTADO_DEV_HOME (desarrollo) o dictado.appDir (app empaquetada)")
        }
    }
}
