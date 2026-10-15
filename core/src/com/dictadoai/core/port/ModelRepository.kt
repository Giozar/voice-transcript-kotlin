package com.dictadoai.core.port

import com.dictadoai.core.domain.WhisperModel

/** Almacén local de los modelos descargados. */
interface ModelRepository {
    /** El modelo está descargado y completo. */
    fun isInstalled(model: WhisperModel): Boolean

    /** Ruta absoluta del archivo del modelo, para que el motor lo cargue. */
    fun locate(model: WhisperModel): String

    fun delete(model: WhisperModel)
}
