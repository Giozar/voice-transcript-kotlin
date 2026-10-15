package com.dictadoai.core.application

import com.dictadoai.core.domain.DictationState.*
import com.dictadoai.core.domain.FailureKind
import com.dictadoai.core.domain.Notice
import com.dictadoai.core.domain.Purpose
import com.dictadoai.core.domain.WhisperCatalog
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DictationControllerTest {
    private val recorder = FakeRecorder()
    private val engine = FakeEngine()
    private val clipboard = FakeClipboard()
    private val permission = FakePermission()
    private val repository = FakeModelRepository()
    private val store = FakeActiveModelStore()
    private val downloader = FakeModelDownloader(repository)

    private lateinit var models: ModelManager

    private fun TestScope.controller(maxMillis: Long = 600_000): DictationController {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        models = ModelManager(repository, downloader, store, scope)
        return DictationController(recorder, engine, clipboard, permission, models, scope, "es", maxMillis)
    }

    private suspend fun TestScope.ready(c: DictationController) { c.start(); advanceUntilIdle() }

    private suspend fun TestScope.recording(c: DictationController) {
        ready(c); c.onBubbleClick(); advanceUntilIdle()
    }

    @Test fun `loads the model and becomes idle`() = runTest {
        val c = controller()
        assertEquals(LoadingModel, c.state.value)
        ready(c)
        assertEquals(Idle, c.state.value)
    }

    @Test fun `model load failure shows an error and ignores clicks`() = runTest {
        engine.loadError = IllegalStateException("roto")
        val c = controller()
        ready(c)
        assertEquals(ModelError("roto"), c.state.value)
        c.onBubbleClick(); advanceUntilIdle()
        assertEquals(ModelError("roto"), c.state.value)
        assertFalse(recorder.started)
    }

    @Test fun `full flow records, transcribes, fills the document and copies`() = runTest {
        val c = controller()
        recording(c)
        assertEquals(Recording(resumed = false), c.state.value)
        assertTrue(recorder.started)

        c.finish(); advanceUntilIdle()
        assertEquals(Result(limitReached = false), c.state.value)
        assertEquals("hola mundo", c.document.value.text)
        assertEquals(listOf("hola mundo"), clipboard.copied)
    }

    @Test fun `pausing transcribes the segment so it can be reviewed`() = runTest {
        val c = controller()
        recording(c)
        c.pause(); advanceUntilIdle()
        assertEquals(Paused(Notice.NONE), c.state.value)
        assertEquals("hola mundo", c.document.value.text)
        assertEquals(listOf("hola mundo"), clipboard.copied)
    }

    @Test fun `resuming records a new segment and inserts it at the cursor with a space`() = runTest {
        engine.results = mutableListOf("hola", "mundo")
        val c = controller()
        recording(c)
        c.pause(); advanceUntilIdle()
        c.resume(); advanceUntilIdle()
        assertEquals(Recording(resumed = true), c.state.value)
        assertEquals(2, recorder.startCount)

        c.finish(); advanceUntilIdle()
        assertEquals("hola mundo", c.document.value.text)
        assertEquals(listOf("hola", "hola mundo"), clipboard.copied)
    }

    @Test fun `new text goes where the cursor is after the user edited`() = runTest {
        engine.results = mutableListOf("uno tres", "dos")
        val c = controller()
        recording(c)
        c.pause(); advanceUntilIdle()
        c.updateDocument("uno tres", selectionStart = 3, selectionEnd = 3) // el usuario mueve el cursor tras "uno"
        c.resume(); advanceUntilIdle()
        c.finish(); advanceUntilIdle()
        assertEquals("uno dos tres", c.document.value.text)
    }

    @Test fun `continuing from the result keeps and extends the text`() = runTest {
        engine.results = mutableListOf("primera", "segunda")
        val c = controller()
        recording(c)
        c.finish(); advanceUntilIdle()
        c.updateDocument("Primera editada.", 16, 16)
        c.continueDictation(); advanceUntilIdle()
        assertEquals(Recording(resumed = true), c.state.value)
        c.finish(); advanceUntilIdle()
        assertEquals("Primera editada. segunda", c.document.value.text)
        assertEquals("Primera editada. segunda", clipboard.copied.last())
    }

    @Test fun `finishing from pause does not need new audio and copies the text`() = runTest {
        val c = controller()
        recording(c)
        c.pause(); advanceUntilIdle()
        clipboard.copied.clear()
        c.updateDocument("texto editado", 13, 13)
        c.finish(); advanceUntilIdle()
        assertEquals(Result(limitReached = false), c.state.value)
        assertEquals(1, engine.transcribeCalls)
        assertEquals(listOf("texto editado"), clipboard.copied)
    }

    @Test fun `cancel on the first segment discards everything`() = runTest {
        val c = controller()
        recording(c)
        c.cancel(); advanceUntilIdle()
        assertEquals(Idle, c.state.value)
        assertTrue(recorder.cancelled)
        assertEquals(0, engine.transcribeCalls)
        assertTrue(clipboard.copied.isEmpty())
    }

    @Test fun `cancel on a later segment keeps the session text`() = runTest {
        val c = controller()
        recording(c)
        c.pause(); advanceUntilIdle()
        c.resume(); advanceUntilIdle()
        c.cancel(); advanceUntilIdle()
        assertEquals(Paused(Notice.NONE), c.state.value)
        assertEquals("hola mundo", c.document.value.text)
    }

    @Test fun `discarding from pause clears the session`() = runTest {
        val c = controller()
        recording(c)
        c.pause(); advanceUntilIdle()
        c.cancel()
        assertEquals(Idle, c.state.value)
        assertEquals("", c.document.value.text)
    }

    @Test fun `a new session starts with an empty document`() = runTest {
        val c = controller()
        recording(c)
        c.finish(); advanceUntilIdle()
        c.onBubbleClick(); advanceUntilIdle()
        assertEquals(Recording(resumed = false), c.state.value)
        assertEquals("", c.document.value.text)
    }

    @Test fun `closing the result clears the session`() = runTest {
        val c = controller()
        recording(c)
        c.finish(); advanceUntilIdle()
        c.close()
        assertEquals(Idle, c.state.value)
        assertEquals("", c.document.value.text)
    }

    @Test fun `silence on the first segment is no speech and leaves the clipboard untouched`() = runTest {
        recorder.clip = AudioClip(FloatArray(16_000)) // todo ceros
        val c = controller()
        recording(c)
        c.finish(); advanceUntilIdle()
        assertEquals(NoSpeech(false), c.state.value)
        assertEquals(0, engine.transcribeCalls)
        assertTrue(clipboard.copied.isEmpty())
    }

    @Test fun `silence on a later segment keeps the text and warns`() = runTest {
        val c = controller()
        recording(c)
        c.pause(); advanceUntilIdle()
        recorder.clip = AudioClip(FloatArray(16_000))
        c.resume(); advanceUntilIdle()
        c.finish(); advanceUntilIdle()
        assertEquals(Result(false, Notice.NO_SPEECH), c.state.value)
        assertEquals("hola mundo", c.document.value.text)
    }

    @Test fun `hallucinated text counts as no speech`() = runTest {
        engine.results = mutableListOf("[BLANK_AUDIO]")
        val c = controller()
        recording(c)
        c.finish(); advanceUntilIdle()
        assertEquals(NoSpeech(false), c.state.value)
        assertTrue(clipboard.copied.isEmpty())
    }

    @Test fun `recording limit stops and transcribes the segment`() = runTest {
        val c = controller(maxMillis = 1000)
        recording(c)
        recorder.setRecorded(1000); advanceUntilIdle()
        assertEquals(Result(limitReached = true), c.state.value)
        assertEquals("hola mundo", c.document.value.text)
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

    @Test fun `transcription error on the first segment shows a failure`() = runTest {
        engine.transcribeError = RuntimeException("boom")
        val c = controller()
        recording(c)
        c.finish(); advanceUntilIdle()
        assertEquals(Failure(FailureKind.TRANSCRIPTION_FAILED), c.state.value)
        assertTrue(clipboard.copied.isEmpty())
    }

    @Test fun `transcription error on a later segment keeps the text`() = runTest {
        val c = controller()
        recording(c)
        c.pause(); advanceUntilIdle()
        c.resume(); advanceUntilIdle()
        engine.transcribeError = RuntimeException("boom")
        c.finish(); advanceUntilIdle()
        assertEquals(Result(false, Notice.TRANSCRIPTION_FAILED), c.state.value)
        assertEquals("hola mundo", c.document.value.text)
    }

    @Test fun `clicks while transcribing are ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        engine.gate = gate
        val c = controller()
        recording(c)
        c.finish()
        assertEquals(Transcribing(Purpose.FINISH, false), c.state.value)
        c.onBubbleClick(); c.cancel(); c.pause(); c.continueDictation()
        assertEquals(Transcribing(Purpose.FINISH, false), c.state.value)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(Result(false), c.state.value)
    }

    @Test fun `shutdown releases the engine`() = runTest {
        val c = controller()
        ready(c)
        c.shutdown()
        assertTrue(engine.closed)
    }

    @Test fun `without an installed model it asks to choose one and loads nothing`() = runTest {
        repository.installed.clear()
        val c = controller()
        ready(c)
        assertEquals(NoModel, c.state.value)
        assertNull(engine.loadedModel)
    }

    @Test fun `choosing an installed model loads it and remembers the choice`() = runTest {
        repository.installed.clear()
        repository.installed += "tiny"
        val c = controller()
        ready(c)
        c.useModel(WhisperCatalog.byId("tiny")!!)
        advanceUntilIdle()
        assertEquals(Idle, c.state.value)
        assertEquals("tiny", engine.loadedModel?.id)
        assertEquals("tiny", store.saved)
    }

    @Test fun `a model downloaded while there is none is loaded automatically`() = runTest {
        repository.installed.clear()
        val c = controller()
        ready(c)
        assertEquals(NoModel, c.state.value)
        models.download(WhisperCatalog.byId("tiny")!!)
        advanceUntilIdle()
        assertEquals(Idle, c.state.value)
        assertEquals("tiny", engine.loadedModel?.id)
    }

    @Test fun `a model downloaded while another is in use does not replace it`() = runTest {
        val c = controller()
        ready(c)
        models.download(WhisperCatalog.byId("tiny")!!)
        advanceUntilIdle()
        assertEquals(WhisperCatalog.DEFAULT_ID, engine.loadedModel?.id)
        assertEquals(Idle, c.state.value)
    }

    @Test fun `after a failed model another one can be chosen`() = runTest {
        engine.loadError = IllegalStateException("roto")
        val c = controller()
        ready(c)
        assertEquals(ModelError("roto"), c.state.value)
        engine.loadError = null
        c.useModel(WhisperCatalog.byId(WhisperCatalog.DEFAULT_ID)!!)
        advanceUntilIdle()
        assertEquals(Idle, c.state.value)
    }

    @Test fun `a model cannot be chosen while recording`() = runTest {
        val c = controller()
        recording(c)
        engine.loadedModel = null // solo nos interesa si se intenta cargar otro modelo
        c.useModel(WhisperCatalog.byId("tiny")!!)
        advanceUntilIdle()
        assertEquals(Recording(resumed = false), c.state.value)
        assertNull(engine.loadedModel)
    }
}
