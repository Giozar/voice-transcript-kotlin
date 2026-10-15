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
            ModelMissing -> NoModel
            else -> null
        }

        is ModelError -> when (event) {
            ModelSelected -> LoadingModel
            else -> null
        }

        NoModel -> when (event) {
            ModelSelected -> LoadingModel
            else -> null
        }

        Idle -> when (event) {
            BubbleClicked -> Starting(resumed = false)
            ModelSelected -> LoadingModel
            else -> null
        }

        is Starting -> when (event) {
            MicReady -> Recording(state.resumed)
            MicDenied -> Failure(FailureKind.MIC_DENIED)
            MicUnavailable -> Failure(FailureKind.MIC_UNAVAILABLE)
            CancelRequested -> afterCancelledSegment(state.resumed)
            else -> null
        }

        is Recording -> when (event) {
            PauseRequested -> Transcribing(Purpose.PAUSE, limitReached = false)
            FinishRequested -> Transcribing(Purpose.FINISH, limitReached = false)
            LimitReached -> Transcribing(Purpose.FINISH, limitReached = true)
            CancelRequested -> afterCancelledSegment(state.resumed)
            else -> null
        }

        is Paused -> when (event) {
            ResumeRequested -> Starting(resumed = true)
            FinishRequested -> Result(limitReached = false)
            CancelRequested, CloseRequested -> Idle
            else -> null
        }

        is Transcribing -> when (event) {
            is TranscriptionDone -> when {
                !event.hasSpeech && !event.sessionHasText -> NoSpeech(state.limitReached)
                state.purpose == Purpose.PAUSE -> Paused(if (event.hasSpeech) Notice.NONE else Notice.NO_SPEECH)
                else -> Result(state.limitReached, if (event.hasSpeech) Notice.NONE else Notice.NO_SPEECH)
            }
            is TranscriptionFailed -> when {
                !event.sessionHasText -> Failure(FailureKind.TRANSCRIPTION_FAILED)
                state.purpose == Purpose.PAUSE -> Paused(Notice.TRANSCRIPTION_FAILED)
                else -> Result(state.limitReached, Notice.TRANSCRIPTION_FAILED)
            }
            else -> null
        }

        is Result -> when (event) {
            CloseRequested -> Idle
            BubbleClicked -> Starting(resumed = false) // sesión nueva: el texto anterior se descarta
            ContinueRequested -> Starting(resumed = true)
            else -> null
        }

        is NoSpeech, is Failure -> when (event) {
            CloseRequested -> Idle
            BubbleClicked -> Starting(resumed = false)
            else -> null
        }
    }

    /** Cancelar un fragmento: si la sesión ya tenía texto se conserva (en pausa); si no, se vuelve a la bolita. */
    private fun afterCancelledSegment(resumed: Boolean): DictationState = if (resumed) Paused() else Idle
}
