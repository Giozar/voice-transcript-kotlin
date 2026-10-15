package com.dictadoai.desktop.adapter.models

import com.dictadoai.core.domain.WhisperModel
import com.dictadoai.core.port.ModelRepository
import java.io.File

/** Modelos guardados como archivos en una carpeta. Un archivo cuenta como instalado si tiene el tamaño esperado. */
class FileModelRepository(private val modelsDir: File) : ModelRepository {

    private fun fileOf(model: WhisperModel) = File(modelsDir, model.fileName)

    override fun isInstalled(model: WhisperModel): Boolean =
        fileOf(model).let { it.isFile && it.length() == model.sizeBytes }

    override fun locate(model: WhisperModel): String = fileOf(model).absolutePath

    override fun delete(model: WhisperModel) {
        fileOf(model).delete()
    }
}
