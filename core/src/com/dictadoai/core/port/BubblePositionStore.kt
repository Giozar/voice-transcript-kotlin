package com.dictadoai.core.port

import com.dictadoai.core.domain.BubblePosition

interface BubblePositionStore {
    /** Devuelve la posición guardada, o `null` si no hay (o está dañada). */
    fun load(): BubblePosition?

    fun save(position: BubblePosition)
}
