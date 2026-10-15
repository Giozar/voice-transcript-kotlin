package com.dictadoai.core.application

import com.dictadoai.core.domain.DictationEvent
import com.dictadoai.core.domain.DictationState
import com.dictadoai.core.domain.DictationStateMachine
import com.dictadoai.core.domain.RecordingLimits
import com.dictadoai.core.domain.TranscriptCleaner
import com.dictadoai.core.domain.TranscriptDocument
import com.dictadoai.core.domain.TranscriptInserter
import com.dictadoai.core.domain.WhisperModel
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
 * Una sesión es un [document] editable: al pausar o terminar, lo grabado se transcribe y se inserta en el cursor.
 * Debe usarse desde un único hilo (el de [scope], en la app real el hilo de la interfaz).
 */
class DictationController(
    private val recorder: AudioRecorder,
    private val engine: TranscriptionEngine,
    private val clipboard: ClipboardWriter,
    private val permission: MicrophonePermission,
    private val models: ModelManager,
    private val scope: CoroutineScope,
    private val language: String = "es",
    private val maxRecordingMillis: Long = RecordingLimits.MAX_MILLIS,
) {
    private val _state = MutableStateFlow<DictationState>(DictationState.LoadingModel)
    val state: StateFlow<DictationState> = _state

    private val _document = MutableStateFlow(TranscriptDocument.EMPTY)
    val document: StateFlow<TranscriptDocument> = _document

    /** Tiempo grabado en el fragmento actual, para el cronómetro. */
    val elapsedMillis: StateFlow<Long> get() = recorder.recordedMillis

    private var limitJob: Job? = null

    /** Carga el modelo activo; si no hay ninguno descargado, pide elegir uno. */
    fun start() {
        val model = models.activeModel()
        if (model == null) dispatch(DictationEvent.ModelMissing) else loadModel(model)
    }

    /** Elige un modelo descargado y lo carga. Solo se acepta con la bolita libre o sin modelo. */
    fun useModel(model: WhisperModel) {
        if (!dispatch(DictationEvent.ModelSelected)) return
        if (!models.select(model)) {
            dispatch(DictationEvent.ModelFailed("El modelo ${model.displayName} no está descargado"))
            return
        }
        loadModel(model)
    }

    private fun loadModel(model: WhisperModel) {
        scope.launch {
            try {
                engine.load(model)
                dispatch(DictationEvent.ModelLoaded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dispatch(DictationEvent.ModelFailed(e.message ?: "No se pudo cargar el modelo de voz"))
            }
        }
    }

    /** Clic en la bolita: empieza una sesión nueva (el texto anterior se descarta). */
    fun onBubbleClick() {
        if (dispatch(DictationEvent.BubbleClicked)) startSegment()
    }

    /** Sigue dictando sobre el texto actual (desde el resultado). */
    fun continueDictation() {
        if (dispatch(DictationEvent.ContinueRequested)) startSegment()
    }

    /** Pausa: transcribe lo grabado hasta ahora para poder revisarlo. */
    fun pause() {
        if (dispatch(DictationEvent.PauseRequested)) beginTranscription()
    }

    /** Reanuda la sesión en pausa grabando un fragmento nuevo. */
    fun resume() {
        if (dispatch(DictationEvent.ResumeRequested)) startSegment()
    }

    fun finish() {
        val wasPaused = _state.value is DictationState.Paused
        if (!dispatch(DictationEvent.FinishRequested)) return
        if (wasPaused) copyDocument() // no hay audio nuevo que transcribir
        else beginTranscription()
    }

    /** Cancela el fragmento en curso; si la sesión ya tenía texto, se conserva. En pausa, descarta la sesión. */
    fun cancel() {
        if (dispatch(DictationEvent.CancelRequested)) {
            limitJob?.cancel()
            recorder.cancel()
        }
    }

    /** Cierra el panel (descarta la sesión). */
    fun close() {
        dispatch(DictationEvent.CloseRequested)
    }

    /** La interfaz informa de cada cambio en el texto o en la posición del cursor. */
    fun updateDocument(text: String, selectionStart: Int, selectionEnd: Int) {
        _document.value = TranscriptDocument(text, selectionStart, selectionEnd)
    }

    /** Libera recursos al salir de la app. */
    fun shutdown() {
        limitJob?.cancel()
        recorder.cancel()
        engine.close()
    }

    private fun startSegment() {
        scope.launch {
            if (!permission.ensureGranted()) {
                dispatch(DictationEvent.MicDenied)
                return@launch
            }
            if (_state.value !is DictationState.Starting) return@launch // cancelado mientras se pedía el permiso
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
            val piece = if (clip.rms() < RecordingLimits.SILENCE_RMS) {
                "" // silencio: no se llama al motor
            } else {
                try {
                    TranscriptCleaner.clean(engine.transcribe(clip, language))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    dispatch(DictationEvent.TranscriptionFailed(sessionHasText = _document.value.text.isNotBlank()))
                    return@launch
                }
            }
            val hasSpeech = piece.isNotBlank()
            if (hasSpeech) {
                _document.value = TranscriptInserter.insert(_document.value, piece)
                copyDocument()
            }
            dispatch(DictationEvent.TranscriptionDone(hasSpeech, sessionHasText = _document.value.text.isNotBlank()))
        }
    }

    private fun copyDocument() {
        val text = _document.value.text
        if (text.isNotBlank()) clipboard.copy(text)
    }

    private fun dispatch(event: DictationEvent): Boolean {
        val next = DictationStateMachine.transition(_state.value, event) ?: return false
        // Al volver a la bolita o empezar una sesión nueva, el texto anterior se descarta.
        if (next == DictationState.Idle || next == DictationState.Starting(resumed = false)) {
            _document.value = TranscriptDocument.EMPTY
        }
        _state.value = next
        return true
    }
}
