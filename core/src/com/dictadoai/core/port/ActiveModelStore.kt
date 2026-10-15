package com.dictadoai.core.port

/** Recuerda qué modelo eligió la persona. */
interface ActiveModelStore {
    /** Id del modelo elegido, o null si nunca eligió uno. */
    fun load(): String?

    fun save(modelId: String)
}
