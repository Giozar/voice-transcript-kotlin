package com.dictadoai.desktop.adapter.settings

import com.dictadoai.core.port.ActiveModelStore
import java.io.File

/** Guarda el modelo elegido en `config.properties`. */
class PropertiesActiveModelStore(private val file: File) : ActiveModelStore {

    override fun load(): String? = SettingsFile.read(file).getProperty(KEY)

    override fun save(modelId: String) = SettingsFile.update(file) { setProperty(KEY, modelId) }

    private companion object {
        const val KEY = "model.active"
    }
}
