package com.dictadoai.core.domain

import com.dictadoai.core.domain.DictationEvent.*
import com.dictadoai.core.domain.DictationState.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DictationStateMachineTest {
    private fun t(s: DictationState, e: DictationEvent) = DictationStateMachine.transition(s, e)
    private val speech = TranscriptionDone(hasSpeech = true, sessionHasText = true)
    private val silentEmpty = TranscriptionDone(hasSpeech = false, sessionHasText = false)
    private val silentWithText = TranscriptionDone(hasSpeech = false, sessionHasText = true)

    @Test fun `loading model goes to idle or error`() {
        assertEquals(Idle, t(LoadingModel, ModelLoaded))
        assertEquals(ModelError("x"), t(LoadingModel, ModelFailed("x")))
        assertNull(t(LoadingModel, BubbleClicked))
    }

    @Test fun `model error ignores everything`() {
        listOf(BubbleClicked, ModelLoaded, CloseRequested, CancelRequested).forEach { assertNull(t(ModelError("x"), it)) }
    }

    @Test fun `click on idle starts a new session`() {
        assertEquals(Starting(false), t(Idle, BubbleClicked))
        assertNull(t(Idle, FinishRequested))
    }

    @Test fun `starting transitions`() {
        assertEquals(Recording(false), t(Starting(false), MicReady))
        assertEquals(Recording(true), t(Starting(true), MicReady))
        assertEquals(Failure(FailureKind.MIC_DENIED), t(Starting(false), MicDenied))
        assertEquals(Failure(FailureKind.MIC_UNAVAILABLE), t(Starting(true), MicUnavailable))
        assertNull(t(Starting(false), FinishRequested))
        assertNull(t(Starting(false), PauseRequested))
    }

    @Test fun `cancelling a segment keeps the session text when it already had some`() {
        assertEquals(Idle, t(Starting(false), CancelRequested))
        assertEquals(Idle, t(Recording(false), CancelRequested))
        assertEquals(Paused(), t(Starting(true), CancelRequested))
        assertEquals(Paused(), t(Recording(true), CancelRequested))
    }

    @Test fun `recording pause, finish and limit start a transcription`() {
        assertEquals(Transcribing(Purpose.PAUSE, false), t(Recording(false), PauseRequested))
        assertEquals(Transcribing(Purpose.FINISH, false), t(Recording(true), FinishRequested))
        assertEquals(Transcribing(Purpose.FINISH, true), t(Recording(false), LimitReached))
        assertNull(t(Recording(false), BubbleClicked))
    }

    @Test fun `paused transitions`() {
        assertEquals(Starting(true), t(Paused(), ResumeRequested))
        assertEquals(Result(false), t(Paused(), FinishRequested))
        assertEquals(Idle, t(Paused(), CancelRequested))
        assertEquals(Idle, t(Paused(), CloseRequested))
        assertNull(t(Paused(), LimitReached))
        assertNull(t(Paused(), BubbleClicked))
    }

    @Test fun `transcribing ignores clicks and cancel`() {
        val s = Transcribing(Purpose.FINISH, false)
        listOf(BubbleClicked, CancelRequested, CloseRequested, PauseRequested, ContinueRequested).forEach { assertNull(t(s, it)) }
    }

    @Test fun `speech after a pause goes to paused, after finish to result`() {
        assertEquals(Paused(Notice.NONE), t(Transcribing(Purpose.PAUSE, false), speech))
        assertEquals(Result(false, Notice.NONE), t(Transcribing(Purpose.FINISH, false), speech))
        assertEquals(Result(true, Notice.NONE), t(Transcribing(Purpose.FINISH, true), speech))
    }

    @Test fun `silence with no text is no speech, with text it keeps the session and warns`() {
        assertEquals(NoSpeech(false), t(Transcribing(Purpose.FINISH, false), silentEmpty))
        assertEquals(NoSpeech(false), t(Transcribing(Purpose.PAUSE, false), silentEmpty))
        assertEquals(Paused(Notice.NO_SPEECH), t(Transcribing(Purpose.PAUSE, false), silentWithText))
        assertEquals(Result(false, Notice.NO_SPEECH), t(Transcribing(Purpose.FINISH, false), silentWithText))
    }

    @Test fun `transcription failure keeps text if there is any`() {
        assertEquals(Failure(FailureKind.TRANSCRIPTION_FAILED), t(Transcribing(Purpose.FINISH, false), TranscriptionFailed(false)))
        assertEquals(Paused(Notice.TRANSCRIPTION_FAILED), t(Transcribing(Purpose.PAUSE, false), TranscriptionFailed(true)))
        assertEquals(Result(false, Notice.TRANSCRIPTION_FAILED), t(Transcribing(Purpose.FINISH, false), TranscriptionFailed(true)))
    }

    @Test fun `result can continue, restart or close`() {
        assertEquals(Starting(true), t(Result(false), ContinueRequested))
        assertEquals(Starting(false), t(Result(false), BubbleClicked))
        assertEquals(Idle, t(Result(false), CloseRequested))
        assertNull(t(Result(false), FinishRequested))
    }

    @Test fun `no speech and failure close or restart`() {
        listOf(NoSpeech(false), Failure(FailureKind.MIC_DENIED)).forEach {
            assertEquals(Idle, t(it, CloseRequested))
            assertEquals(Starting(false), t(it, BubbleClicked))
            assertNull(t(it, ContinueRequested))
        }
    }
}
