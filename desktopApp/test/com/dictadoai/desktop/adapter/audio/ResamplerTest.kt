package com.dictadoai.desktop.adapter.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResamplerTest {
    private fun sine(rate: Int, seconds: Int, freq: Double = 440.0, amp: Float = 0.8f) =
        FloatArray(rate * seconds) { (amp * sin(2 * PI * freq * it / rate)).toFloat() }

    @Test fun `48 kHz becomes 16 kHz with the expected length`() {
        assertEquals(16_000, Resampler.toWhisperRate(sine(48_000, 1), 48_000).size)
    }

    @Test fun `44_1 kHz becomes about 16 kHz`() {
        val out = Resampler.toWhisperRate(sine(44_100, 1), 44_100)
        assertTrue(abs(out.size - 16_000) <= 1, "tamaño ${out.size}")
    }

    @Test fun `amplitude is preserved`() {
        val out = Resampler.toWhisperRate(sine(48_000, 1), 48_000)
        val peak = out.maxOf { abs(it) }
        assertTrue(peak in 0.7f..0.85f, "pico $peak")
    }

    @Test fun `same rate returns a copy`() {
        val input = sine(16_000, 1)
        val out = Resampler.toWhisperRate(input, 16_000)
        assertEquals(input.size, out.size)
        assertTrue(input.contentEquals(out))
    }

    @Test fun `empty input gives empty output`() {
        assertEquals(0, Resampler.toWhisperRate(FloatArray(0), 48_000).size)
    }

    @Test fun `upsampling interpolates`() {
        assertEquals(16_000, Resampler.resample(sine(8_000, 1), 8_000, 16_000).size)
    }
}
