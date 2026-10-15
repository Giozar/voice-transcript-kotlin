package com.dictadoai.desktop.adapter.settings

import com.dictadoai.core.domain.BubblePosition
import com.dictadoai.core.port.BubblePositionStore
import java.io.File

/** Guarda la posición de la bolita en `config.properties`. Si falta o está dañado, devuelve null. */
class PropertiesBubblePositionStore(private val file: File) : BubblePositionStore {

    override fun load(): BubblePosition? = runCatching {
        val props = SettingsFile.read(file)
        BubblePosition(props.getProperty("bubble.x").toInt(), props.getProperty("bubble.y").toInt())
    }.getOrNull()

    override fun save(position: BubblePosition) = SettingsFile.update(file) {
        setProperty("bubble.x", position.x.toString())
        setProperty("bubble.y", position.y.toString())
    }
}
