package com.dictadoai.desktop.adapter.settings

import com.dictadoai.core.domain.BubblePosition
import com.dictadoai.core.port.BubblePositionStore
import java.io.File
import java.util.Properties

/** Guarda la posición de la bolita en un archivo `.properties`. Si falta o está dañado, devuelve null. */
class PropertiesBubblePositionStore(private val file: File) : BubblePositionStore {

    override fun load(): BubblePosition? = runCatching {
        val props = Properties().apply { file.inputStream().use { load(it) } }
        BubblePosition(props.getProperty("bubble.x").toInt(), props.getProperty("bubble.y").toInt())
    }.getOrNull()

    override fun save(position: BubblePosition) {
        runCatching {
            file.parentFile?.mkdirs()
            val props = Properties().apply {
                setProperty("bubble.x", position.x.toString())
                setProperty("bubble.y", position.y.toString())
            }
            file.outputStream().use { props.store(it, "DictadoAI") }
        }
    }
}
