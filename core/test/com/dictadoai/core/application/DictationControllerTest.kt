package com.dictadoai.core.application

import com.dictadoai.core.domain.DictationState.*
import com.dictadoai.core.domain.FailureKind
import com.dictadoai.core.port.AudioClip
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DictationControllerTest {
    private val recorder = FakeRecorder()
    private val engine = FakeEngine()
    private val clipboard = FakeClipboard()
    private val permission = FakePermission()

    private fun TestScope.controller(maxMillis: Long = 600_000) =
        DictationController(
            recorder, engine, clipboard, permission,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()), "es", maxMillis,
        )

    private suspend fun TestScope.ready(c: DictationController) {
        c.start(); advanceUntilIdle()
    }

    private suspend fun TestScope.recording(c: DictationController) {
        ready(c); c.onBubbleClick(); advanceUntilIdle()
    }

    @Test fun `loads the model and becomes idle`() = runTest {
        val c = controller()
        assertEquals(LoadingModel, c.state.value)
        ready(c)
        assertEquals(Idle, c.state.value)
    }

    @Test fun `model load failure leads to a terminal error`() = runTest {
        engine.loadError = IllegalStateException("roto")
        val c = controller()
        ready(c)
        assertEquals(ModelError("roto"), c.state.value)
        c.onBubbleClick(); advanceUntilIdle()
        assertEquals(ModelError("roto"), c.state.value)
        assertFalse(recorder.started)
    }

    @Test fun `full flow records, transcribes and copies`() = runTest {
        val c = controller()
        recording(c)
        assertEquals(Recording, c.state.value)
        assertTrue(recorder.started)

        c.finish(); advanceUntilIdle()
        assertEquals(Result("hola mundo", false), c.state.value)
        assertEquals(listOf("hola mundo"), clipboard.copied)
    }

    @Test fun `pause and resume reach the recorder`() = runTest {
        val c = controller()
        recording(c)
        c.pause()
        assertEquals(Paused, c.state.value); assertTrue(recorder.paused)
        c.resume()
        assertEquals(Recording, c.state.value); assertFalse(recorder.paused)
    }

    @Test fun `cancel discards audio and does not copy`() = runTest {
        val c = controller()
        recording(c)
        c.cancel(); advanceUntilIdle()
        assertEquals(Idle, c.state.value)
        assertTrue(recorder.cancelled)
        assertEquals(0, engine.transcribeCalls)
        assertTrue(clipboard.copied.isEmpty())
    }

    @Test fun `silence skips the engine and leaves the clipboard untouched`() = runTest {
        recorder.clip = AudioClip(FloatArray(16_000)) // todo ceros
        val c = controller()
        recording(c)
        c.finish(); advanceUntilIdle()
        assertEquals(NoSpeech(false), c.state.value)
        assertEquals(0, engine.transcribeCalls)
        assertTrue(clipboard.copied.isEmpty())
    }

    @Test fun `hallucinated text counts as no speech`() = runTest {
        engine.result = "[BLANK_AUDIO]"
        val c = controller()
        recording(c)
        c.finish(); advanceUntilIdle()
        assertEquals(NoSpeech(false), c.state.value)
        assertTrue(clipboard.copied.isEmpty())
    }

    @Test fun `recording limit stops and transcribes`() = runTest {
        val c = controller(maxMillis = 1000)
        recording(c)
        recorder.setRecorded(1000); advanceUntilIdle()
        assertEquals(Result("hola mundo", true), c.state.value)
        assertEquals(listOf("hola mundo"), clipboard.copied)
    }

    @Test fun `denied permission shows a failure and never records`() = runTest {
        permission.granted = false
        val c = controller()
        ready(c)
        c.onBubbleClick(); advanceUntilIdle()
        assertEquals(Failure(FailureKind.MIC_DENIED), c.state.value)
        assertFalse(recorder.started)
        c.close()
        assertEquals(Idle, c.state.value)
    }

    @Test fun `missing microphone shows a failure`() = runTest {
        recorder.micAvailable = false
        val c = controller()
        ready(c)
        c.onBubbleClick(); advanceUntilIdle()
        assertEquals(Failure(FailureKind.MIC_UNAVAILABLE), c.state.value)
    }

    @Test fun `transcription error shows a failure and does not copy`() = runTest {
        engine.transcribeError = RuntimeException("boom")
        val c = controller()
        recording(c)
        c.finish(); advanceUntilIdle()
        assertEquals(Failure(FailureKind.TRANSCRIPTION_FAILED), c.state.value)
        assertTrue(clipboard.copied.isEmpty())
    }

    @Test fun `clicking the bubble from a result starts a new dictation`() = runTest {
        val c = controller()
        recording(c)
        c.finish(); advanceUntilIdle()
        c.onBubbleClick(); advanceUntilIdle()
        assertEquals(Recording, c.state.value)
    }

    @Test fun `clicks while transcribing are ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        engine.gate = gate
        val c = controller()
        recording(c)
        c.finish()
        assertEquals(Transcribing(false), c.state.value)
        c.onBubbleClick(); c.cancel(); c.pause()
        assertEquals(Transcribing(false), c.state.value)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(Result("hola mundo", false), c.state.value)
    }

    @Test fun `shutdown releases the engine`() = runTest {
        val c = controller()
        ready(c)
        c.shutdown()
        assertTrue(engine.closed)
    }
}
