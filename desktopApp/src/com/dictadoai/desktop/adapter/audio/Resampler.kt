package com.dictadoai.desktop.adapter.audio

import com.dictadoai.core.domain.RecordingLimits

/** Convierte audio mono a 16 kHz. Al reducir la frecuencia promedia las muestras de origen (filtro simple). */
object Resampler {
    fun toWhisperRate(input: FloatArray, fromRate: Int): FloatArray = resample(input, fromRate, RecordingLimits.SAMPLE_RATE)

    fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        require(fromRate > 0 && toRate > 0) { "Frecuencias inválidas" }
        if (fromRate == toRate || input.isEmpty()) return input.copyOf()

        val ratio = fromRate.toDouble() / toRate
        val outSize = (input.size / ratio).toInt()
        val out = FloatArray(outSize)
        for (i in 0 until outSize) {
            val start = i * ratio
            val end = start + ratio
            if (ratio > 1.0) {
                // Reducción: promedio ponderado de las muestras que caen en [start, end)
                val first = start.toInt()
                val last = minOf(end.toInt(), input.size - 1)
                var sum = 0.0
                var weight = 0.0
                for (j in first..last) {
                    val w = minOf(end, j + 1.0) - maxOf(start, j.toDouble())
                    if (w > 0) { sum += input[j] * w; weight += w }
                }
                out[i] = if (weight > 0) (sum / weight).toFloat() else 0f
            } else {
                // Ampliación: interpolación lineal
                val j = start.toInt()
                val frac = (start - j).toFloat()
                val a = input[j]
                val b = if (j + 1 < input.size) input[j + 1] else a
                out[i] = a + (b - a) * frac
            }
        }
        return out
    }
}
