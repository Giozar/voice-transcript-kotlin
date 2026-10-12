package com.dictadoai.core.domain

import com.dictadoai.core.domain.DictationEvent.*
import com.dictadoai.core.domain.DictationState.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DictationStateMachineTest {
    private fun t(s: DictationState, e: DictationEvent) = DictationStateMachine.transition(s, e)

    @Test fun `loading model goes to idle or error`() {
        assertEquals(Idle, t(LoadingModel, ModelLoaded))
        assertEquals(ModelError("x"), t(LoadingModel, ModelFailed("x")))
        assertNull(t(LoadingModel, BubbleClicked))
    }

    @Test fun `model error ignores everything`() {
        listOf(BubbleClicked, ModelLoaded, CloseRequested, CancelRequested).forEach {
            assertNull(t(ModelError("x"), it))
        }
    }

    @Test fun `click on idle starts the microphone`() {
        assertEquals(Starting, t(Idle, BubbleClicked))
        assertNull(t(Idle, FinishRequested))
    }

    @Test fun `starting transitions`() {
        assertEquals(Recording, t(Starting, MicReady))
        assertEquals(Failure(FailureKind.MIC_DENIED), t(Starting, MicDenied))
        assertEquals(Failure(FailureKind.MIC_UNAVAILABLE), t(Starting, MicUnavailable))
        assertEquals(Idle, t(Starting, CancelRequested))
        assertNull(t(Starting, FinishRequested))
        assertNull(t(Starting, PauseRequested))
    }

    @Test fun `recording transitions`() {
        assertEquals(Paused, t(Recording, PauseRequested))
        assertEquals(Transcribing(false), t(Recording, FinishRequested))
        assertEquals(Transcribing(true), t(Recording, LimitReached))
        assertEquals(Idle, t(Recording, CancelRequested))
        assertNull(t(Recording, BubbleClicked))
    }

    @Test fun `paused transitions`() {
        assertEquals(Recording, t(Paused, ResumeRequested))
        assertEquals(Transcribing(false), t(Paused, FinishRequested))
        assertEquals(Idle, t(Paused, CancelRequested))
        assertNull(t(Paused, LimitReached))
    }

    @Test fun `transcribing ignores clicks and cancel`() {
        assertNull(t(Transcribing(false), BubbleClicked))
        assertNull(t(Transcribing(false), CancelRequested))
        assertNull(t(Transcribing(false), CloseRequested))
    }

    @Test fun `transcription result depends on text`() {
        assertEquals(Result("hola", false), t(Transcribing(false), TranscriptionDone("hola")))
        assertEquals(Result("hola", true), t(Transcribing(true), TranscriptionDone("hola")))
        assertEquals(NoSpeech(false), t(Transcribing(false), TranscriptionDone("  ")))
        assertEquals(Failure(FailureKind.TRANSCRIPTION_FAILED), t(Transcribing(false), TranscriptionFailed))
    }

    @Test fun `result, no speech and failure close or restart`() {
        listOf(Result("x", false), NoSpeech(false), Failure(FailureKind.MIC_DENIED)).forEach {
            assertEquals(Idle, t(it, CloseRequested))
            assertEquals(Starting, t(it, BubbleClicked))
            assertNull(t(it, FinishRequested))
        }
    }
}
