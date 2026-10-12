package com.dictadoai.core.application

import com.dictadoai.core.domain.DictationEvent
import com.dictadoai.core.domain.DictationState
import com.dictadoai.core.domain.DictationStateMachine
import com.dictadoai.core.domain.RecordingLimits
import com.dictadoai.core.domain.TranscriptCleaner
import com.dictadoai.core.port.AudioRecorder
import com.dictadoai.core.port.ClipboardWriter
import com.dictadoai.core.port.MicUnavailableException
import com.dictadoai.core.port.MicrophonePermission
import com.dictadoai.core.port.TranscriptionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Orquesta el dictado. Solo conoce puertos (interfaces); no sabe nada de macOS, Whisper ni de la interfaz.
 *
 * Debe usarse desde un único hilo (el de [scope], en la app real el hilo de la interfaz).
 */
class DictationController(
    private val recorder: AudioRecorder,
    private val engine: TranscriptionEngine,
    private val clipboard: ClipboardWriter,
    private val permission: MicrophonePermission,
    private val scope: CoroutineScope,
    private val language: String = "es",
    private val maxRecordingMillis: Long = RecordingLimits.MAX_MILLIS,
) {
    private val _state = MutableStateFlow<DictationState>(DictationState.LoadingModel)
    val state: StateFlow<DictationState> = _state

    /** Tiempo grabado, para el cronómetro. */
    val elapsedMillis: StateFlow<Long> get() = recorder.recordedMillis

    private var limitJob: Job? = null

    /** Carga el modelo en segundo plano. */
    fun start() {
        scope.launch {
            try {
                engine.load()
                dispatch(DictationEvent.ModelLoaded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dispatch(DictationEvent.ModelFailed(e.message ?: "No se pudo cargar el modelo de voz"))
            }
        }
    }

    fun onBubbleClick() {
        if (!dispatch(DictationEvent.BubbleClicked)) return
        scope.launch {
            if (!permission.ensureGranted()) {
                dispatch(DictationEvent.MicDenied)
                return@launch
            }
            if (_state.value != DictationState.Starting) return@launch // cancelado mientras se pedía el permiso
            try {
                recorder.start()
            } catch (e: MicUnavailableException) {
                dispatch(DictationEvent.MicUnavailable)
                return@launch
            }
            if (!dispatch(DictationEvent.MicReady)) {
                recorder.cancel() // cancelado mientras se abría el micrófono
                return@launch
            }
            watchLimit()
        }
    }

    fun pause() {
        if (dispatch(DictationEvent.PauseRequested)) recorder.pause()
    }

    fun resume() {
        if (dispatch(DictationEvent.ResumeRequested)) recorder.resume()
    }

    fun finish() {
        if (dispatch(DictationEvent.FinishRequested)) beginTranscription()
    }

    fun cancel() {
        if (dispatch(DictationEvent.CancelRequested)) {
            limitJob?.cancel()
            recorder.cancel()
        }
    }

    /** Cierra el panel de resultado o de error. */
    fun close() {
        dispatch(DictationEvent.CloseRequested)
    }

    /** Libera recursos al salir de la app. */
    fun shutdown() {
        limitJob?.cancel()
        recorder.cancel()
        engine.close()
    }

    private fun watchLimit() {
        limitJob?.cancel()
        limitJob = scope.launch {
            recorder.recordedMillis.first { it >= maxRecordingMillis }
            if (dispatch(DictationEvent.LimitReached)) beginTranscription()
        }
    }

    private fun beginTranscription() {
        limitJob?.cancel()
        val clip = recorder.stop()
        scope.launch {
            if (clip.rms() < RecordingLimits.SILENCE_RMS) {
                dispatch(DictationEvent.TranscriptionDone(""))
                return@launch
            }
            val text = try {
                TranscriptCleaner.clean(engine.transcribe(clip, language))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dispatch(DictationEvent.TranscriptionFailed)
                return@launch
            }
            if (dispatch(DictationEvent.TranscriptionDone(text)) && text.isNotBlank()) clipboard.copy(text)
        }
    }

    private fun dispatch(event: DictationEvent): Boolean {
        val next = DictationStateMachine.transition(_state.value, event) ?: return false
        _state.value = next
        return true
    }
}
