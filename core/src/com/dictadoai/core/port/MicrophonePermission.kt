package com.dictadoai.core.port

interface MicrophonePermission {
    /** Pide el permiso si hace falta. Devuelve `true` si el micrófono está autorizado. */
    suspend fun ensureGranted(): Boolean
}
