package com.dictadoai.core.application

import com.dictadoai.core.domain.WhisperModel
import com.dictadoai.core.port.AudioClip
import com.dictadoai.core.port.AudioRecorder
import com.dictadoai.core.port.ClipboardWriter
import com.dictadoai.core.port.MicUnavailableException
import com.dictadoai.core.port.MicrophonePermission
import com.dictadoai.core.port.TranscriptionEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeRecorder : AudioRecorder {
    override val recordedMillis = MutableStateFlow(0L) as StateFlow<Long>
    private val millis get() = recordedMillis as MutableStateFlow<Long>

    var micAvailable = true
    var clip = AudioClip(FloatArray(16_000) { 0.5f }) // 1 s de audio con nivel claramente por encima del silencio
    var started = false
    var startCount = 0
    var cancelled = false

    fun setRecorded(ms: Long) { millis.value = ms }

    override suspend fun start() {
        if (!micAvailable) throw MicUnavailableException()
        started = true
        startCount++
        millis.value = 0
    }

    override fun stop(): AudioClip { started = false; return clip }
    override fun cancel() { started = false; cancelled = true }
}

class FakeEngine : TranscriptionEngine {
    var loadError: Exception? = null
    var transcribeError: Exception? = null
    /** Textos que devuelve, uno por llamada (el último se repite). */
    var results = mutableListOf("hola mundo")
    private var call = 0
    var transcribeCalls = 0
    var closed = false
    var loadedModel: WhisperModel? = null

    /** Si no es nulo, `transcribe` espera a que se complete (para observar el estado Transcribing). */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun load(model: WhisperModel) {
        loadError?.let { throw it }
        loadedModel = model
    }

    override suspend fun transcribe(clip: AudioClip, language: String): String {
        transcribeCalls++
        gate?.await()
        transcribeError?.let { throw it }
        return results[minOf(call++, results.size - 1)]
    }

    override fun close() { closed = true }
}

class FakeClipboard : ClipboardWriter {
    val copied = mutableListOf<String>()
    override fun copy(text: String) { copied += text }
}

class FakePermission(var granted: Boolean = true) : MicrophonePermission {
    override suspend fun ensureGranted() = granted
}
