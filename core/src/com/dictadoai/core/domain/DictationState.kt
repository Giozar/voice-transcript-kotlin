package com.dictadoai.core.domain

/** Estados posibles del dictado. La interfaz se dibuja a partir de este estado. */
sealed interface DictationState {
    /** El modelo de voz se está cargando. */
    data object LoadingModel : DictationState

    /** El modelo no se pudo cargar. Estado terminal: se ignoran todos los eventos. */
    data class ModelError(val message: String) : DictationState

    /** Solo se ve la bolita. */
    data object Idle : DictationState

    /** Se pidió el micrófono (permiso y apertura de la línea); aún no se graba. */
    data object Starting : DictationState

    data object Recording : DictationState

    data object Paused : DictationState

    /** [limitReached] indica que la grabación se detuvo por alcanzar el límite de duración. */
    data class Transcribing(val limitReached: Boolean) : DictationState

    data class Result(val text: String, val limitReached: Boolean) : DictationState

    data class NoSpeech(val limitReached: Boolean) : DictationState

    data class Failure(val kind: FailureKind) : DictationState
}

enum class FailureKind { MIC_DENIED, MIC_UNAVAILABLE, TRANSCRIPTION_FAILED }
