package com.dictadoai.core.domain

import com.dictadoai.core.domain.DictationEvent.*
import com.dictadoai.core.domain.DictationState.*

/** Transiciones del dictado. Función pura: sin efectos secundarios ni dependencias. */
object DictationStateMachine {

    /** Devuelve el nuevo estado, o `null` si el evento debe ignorarse en el estado actual. */
    fun transition(state: DictationState, event: DictationEvent): DictationState? = when (state) {
        LoadingModel -> when (event) {
            ModelLoaded -> Idle
            is ModelFailed -> ModelError(event.message)
            else -> null
        }

        is ModelError -> null

        Idle -> when (event) {
            BubbleClicked -> Starting
            else -> null
        }

        Starting -> when (event) {
            MicReady -> Recording
            MicDenied -> Failure(FailureKind.MIC_DENIED)
            MicUnavailable -> Failure(FailureKind.MIC_UNAVAILABLE)
            CancelRequested -> Idle
            else -> null
        }

        Recording -> when (event) {
            PauseRequested -> Paused
            FinishRequested -> Transcribing(limitReached = false)
            LimitReached -> Transcribing(limitReached = true)
            CancelRequested -> Idle
            else -> null
        }

        Paused -> when (event) {
            ResumeRequested -> Recording
            FinishRequested -> Transcribing(limitReached = false)
            CancelRequested -> Idle
            else -> null
        }

        is Transcribing -> when (event) {
            is TranscriptionDone ->
                if (event.text.isBlank()) NoSpeech(state.limitReached) else Result(event.text, state.limitReached)
            TranscriptionFailed -> Failure(FailureKind.TRANSCRIPTION_FAILED)
            else -> null
        }

        is Result, is NoSpeech, is Failure -> when (event) {
            CloseRequested -> Idle
            BubbleClicked -> Starting
            else -> null
        }
    }
}
