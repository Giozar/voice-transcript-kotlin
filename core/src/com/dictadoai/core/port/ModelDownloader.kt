package com.dictadoai.core.port

import com.dictadoai.core.domain.WhisperModel

interface ModelDownloader {
    /**
     * Descarga el modelo y verifica su SHA-256. [onProgress] recibe una fracción de 0 a 1.
     * Lanza una excepción si la descarga falla o no coincide con el SHA-256 esperado.
     */
    suspend fun download(model: WhisperModel, onProgress: (Float) -> Unit)
}
