package com.dictadoai.core.port

import com.dictadoai.core.domain.WhisperModel

interface TranscriptionEngine {
    /** Carga el modelo en memoria, sustituyendo el anterior si lo hay. Lanza una excepción si no se puede cargar. */
    suspend fun load(model: WhisperModel)

    suspend fun transcribe(clip: AudioClip, language: String): String

    fun close()
}
