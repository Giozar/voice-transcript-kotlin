package com.dictadoai.core.port

import com.dictadoai.core.domain.RecordingLimits
import kotlin.math.sqrt

/** Audio mono a 16 kHz con muestras en el rango [-1, 1]. */
class AudioClip(val samples: FloatArray) {
    val durationMillis: Long get() = samples.size * 1000L / RecordingLimits.SAMPLE_RATE

    /** Nivel RMS; 0 si no hay muestras. */
    fun rms(): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s
        return sqrt(sum / samples.size).toFloat()
    }
}
