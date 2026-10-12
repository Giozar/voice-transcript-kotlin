package com.dictadoai.core.domain

sealed interface DictationEvent {
    data object ModelLoaded : DictationEvent
    data class ModelFailed(val message: String) : DictationEvent
    data object BubbleClicked : DictationEvent
    data object MicReady : DictationEvent
    data object MicDenied : DictationEvent
    data object MicUnavailable : DictationEvent
    data object PauseRequested : DictationEvent
    data object ResumeRequested : DictationEvent
    data object FinishRequested : DictationEvent
    data object LimitReached : DictationEvent
    data object CancelRequested : DictationEvent
    data object CloseRequested : DictationEvent
    data class TranscriptionDone(val text: String) : DictationEvent
    data object TranscriptionFailed : DictationEvent
}
