package com.dictadoai.core.domain

object RecordingLimits {
    /** Límite de duración de un dictado: 10 minutos. */
    const val MAX_MILLIS: Long = 600_000

    /** Frecuencia de muestreo que espera Whisper. */
    const val SAMPLE_RATE: Int = 16_000

    /** Por debajo de este nivel RMS (en [-1, 1]) el audio se considera silencio y no se transcribe. */
    const val SILENCE_RMS: Float = 0.005f
}
