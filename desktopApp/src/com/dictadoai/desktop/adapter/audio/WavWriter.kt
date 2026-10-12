package com.dictadoai.desktop.adapter.audio

import com.dictadoai.core.domain.RecordingLimits
import com.dictadoai.core.port.AudioClip
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/** Guarda un [AudioClip] como WAV de 16 bits. Solo para depuración: la app no guarda audio. */
object WavWriter {
    fun write(clip: AudioClip, file: File) {
        val bytes = ByteArray(clip.samples.size * 2)
        clip.samples.forEachIndexed { i, s ->
            val v = (s.coerceIn(-1f, 1f) * 32767f).toInt()
            bytes[2 * i] = (v and 0xFF).toByte()
            bytes[2 * i + 1] = (v shr 8).toByte()
        }
        val format = AudioFormat(RecordingLimits.SAMPLE_RATE.toFloat(), 16, 1, true, false)
        AudioInputStream(ByteArrayInputStream(bytes), format, clip.samples.size.toLong()).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, file)
        }
    }
}
