package com.dictadoai.core.port

interface TranscriptionEngine {
    /** Carga el modelo en memoria. Lanza una excepción si no se puede cargar. */
    suspend fun load()

    suspend fun transcribe(clip: AudioClip, language: String): String

    fun close()
}
