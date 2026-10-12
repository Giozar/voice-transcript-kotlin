package com.dictadoai.desktop.adapter.audio

import com.dictadoai.core.domain.RecordingLimits
import com.dictadoai.core.port.AudioClip
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/** Lee un WAV PCM de 16 bits, mono y 16 kHz y lo convierte a [AudioClip]. */
object WavReader {
    fun read(file: File): AudioClip {
        AudioSystem.getAudioInputStream(file).use { stream ->
            val f = stream.format
            require(
                f.encoding == AudioFormat.Encoding.PCM_SIGNED && f.sampleSizeInBits == 16 &&
                    f.channels == 1 && f.sampleRate.toInt() == RecordingLimits.SAMPLE_RATE && !f.isBigEndian,
            ) { "Se espera WAV PCM 16 bits, mono, 16 kHz, little-endian; recibido: $f" }
            val bytes = stream.readAllBytes()
            val samples = FloatArray(bytes.size / 2) { i ->
                val lo = bytes[2 * i].toInt() and 0xFF
                val hi = bytes[2 * i + 1].toInt()
                ((hi shl 8) or lo) / 32768f
            }
            return AudioClip(samples)
        }
    }
}
