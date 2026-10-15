package com.dictadoai.desktop.adapter.settings

import java.io.File
import java.util.Properties

/**
 * Lectura y escritura del `config.properties` compartido. Cada escritura conserva las claves ajenas,
 * así la posición de la bolita y el modelo elegido no se borran mutuamente.
 */
internal object SettingsFile {
    fun read(file: File): Properties = Properties().apply {
        if (file.isFile) runCatching { file.inputStream().use { load(it) } }
    }

    fun update(file: File, change: Properties.() -> Unit) {
        runCatching {
            file.parentFile?.mkdirs()
            val props = read(file).apply(change)
            file.outputStream().use { props.store(it, "DictadoAI") }
        }
    }
}
