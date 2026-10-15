package com.dictadoai.core.domain

sealed interface DictationEvent {
    data object ModelLoaded : DictationEvent
    data object ModelMissing : DictationEvent

    /** La persona eligió un modelo descargado; se carga. */
    data object ModelSelected : DictationEvent
    data class ModelFailed(val message: String) : DictationEvent
    data object BubbleClicked : DictationEvent
    data object ContinueRequested : DictationEvent
    data object MicReady : DictationEvent
    data object MicDenied : DictationEvent
    data object MicUnavailable : DictationEvent
    data object PauseRequested : DictationEvent
    data object ResumeRequested : DictationEvent
    data object FinishRequested : DictationEvent
    data object LimitReached : DictationEvent
    data object CancelRequested : DictationEvent
    data object CloseRequested : DictationEvent

    /** [hasSpeech]: el fragmento produjo texto. [sessionHasText]: el documento tiene texto tras insertarlo. */
    data class TranscriptionDone(val hasSpeech: Boolean, val sessionHasText: Boolean) : DictationEvent
    data class TranscriptionFailed(val sessionHasText: Boolean) : DictationEvent
}
