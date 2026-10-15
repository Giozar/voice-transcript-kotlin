package com.dictadoai.core.domain

/**
 * Estados del dictado. La interfaz se dibuja a partir de este estado.
 *
 * Una sesión de dictado es un texto editable al que se añaden fragmentos: cada vez que se pausa o se termina,
 * lo grabado se transcribe y se inserta en el cursor. El texto vive en [TranscriptDocument], no en el estado.
 */
sealed interface DictationState {
    /** El modelo de voz se está cargando. */
    data object LoadingModel : DictationState

    /** El modelo no se pudo cargar. Solo se acepta elegir otro modelo. */
    data class ModelError(val message: String) : DictationState

    /** No hay ningún modelo descargado: hay que elegir o descargar uno. */
    data object NoModel : DictationState

    /** Solo se ve la bolita. */
    data object Idle : DictationState

    /** Se pidió el micrófono (permiso y apertura de la línea); aún no se graba. [resumed] = la sesión ya tiene texto. */
    data class Starting(val resumed: Boolean) : DictationState

    data class Recording(val resumed: Boolean) : DictationState

    /** Se está transcribiendo el fragmento grabado. [purpose] indica cómo continúa la sesión después. */
    data class Transcribing(val purpose: Purpose, val limitReached: Boolean) : DictationState

    /** Sesión en pausa: el texto se puede revisar y editar. */
    data class Paused(val notice: Notice = Notice.NONE) : DictationState

    /** Sesión terminada: el texto se puede editar, copiar o continuar. */
    data class Result(val limitReached: Boolean, val notice: Notice = Notice.NONE) : DictationState

    /** La primera grabación no tenía voz y no hay texto. */
    data class NoSpeech(val limitReached: Boolean) : DictationState

    data class Failure(val kind: FailureKind) : DictationState
}

enum class Purpose { PAUSE, FINISH }

/** Aviso sobre el último fragmento, para sesiones que ya tienen texto. */
enum class Notice { NONE, NO_SPEECH, TRANSCRIPTION_FAILED }

enum class FailureKind { MIC_DENIED, MIC_UNAVAILABLE, TRANSCRIPTION_FAILED }
