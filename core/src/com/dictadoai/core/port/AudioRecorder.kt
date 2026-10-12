package com.dictadoai.core.port

import kotlinx.coroutines.flow.StateFlow

class MicUnavailableException(message: String = "No se encontró un micrófono", cause: Throwable? = null) :
    Exception(message, cause)

interface AudioRecorder {
    /** Milisegundos de audio grabado. No cuenta el tiempo en pausa. */
    val recordedMillis: StateFlow<Long>

    /** Abre el micrófono y empieza a grabar. @throws MicUnavailableException si no hay micrófono. */
    suspend fun start()

    fun pause()
    fun resume()

    /** Detiene la grabación y devuelve el audio. El audio solo vive en memoria. */
    fun stop(): AudioClip

    /** Detiene la grabación y descarta el audio. */
    fun cancel()
}
