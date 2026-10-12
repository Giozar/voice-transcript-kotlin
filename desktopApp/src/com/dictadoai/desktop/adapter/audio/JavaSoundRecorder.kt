package com.dictadoai.desktop.adapter.audio

import com.dictadoai.core.port.AudioClip
import com.dictadoai.core.port.AudioRecorder
import com.dictadoai.core.port.MicUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine

/**
 * Graba del micrófono predeterminado con javax.sound.sampled.
 * Intenta 16 kHz; si el micrófono no lo admite, graba a 48 o 44.1 kHz y convierte al terminar.
 * El audio solo vive en memoria.
 */
class JavaSoundRecorder : AudioRecorder {
    private val _recordedMillis = MutableStateFlow(0L)
    override val recordedMillis: StateFlow<Long> = _recordedMillis

    private val lock = Any()
    private var line: TargetDataLine? = null
    private var thread: Thread? = null
    private var captureRate = 0
    private var samples = ShortArray(0)
    private var count = 0

    @Volatile private var running = false
    @Volatile private var paused = false

    override suspend fun start() = withContext(Dispatchers.IO) {
        check(line == null) { "Ya se está grabando" }
        val (opened, rate) = openLine()
        synchronized(lock) {
            line = opened
            captureRate = rate
            samples = ShortArray(rate * 30) // se amplía según haga falta
            count = 0
            _recordedMillis.value = 0
            paused = false
            running = true
        }
        opened.start()
        thread = Thread({ readLoop(opened) }, "dictado-recorder").apply { isDaemon = true; start() }
    }

    override fun pause() { paused = true }
    override fun resume() { paused = false }

    override fun stop(): AudioClip {
        val rate: Int
        val data: ShortArray
        synchronized(lock) {
            rate = captureRate
            data = samples.copyOf(count)
        }
        shutdown()
        if (data.isEmpty()) return AudioClip(FloatArray(0))
        val floats = FloatArray(data.size) { data[it] / 32768f }
        return AudioClip(Resampler.toWhisperRate(floats, rate))
    }

    override fun cancel() {
        shutdown()
        synchronized(lock) { samples = ShortArray(0); count = 0 }
        _recordedMillis.value = 0
    }

    private fun shutdown() {
        running = false
        thread?.join(1000)
        thread = null
        line?.let { it.stop(); it.close() }
        line = null
    }

    private fun readLoop(l: TargetDataLine) {
        val buf = ByteArray(4096)
        while (running) {
            val n = l.read(buf, 0, buf.size)
            if (n <= 0) continue
            if (paused) continue // se sigue leyendo para vaciar el búfer, pero no se acumula
            synchronized(lock) {
                val shorts = n / 2
                if (count + shorts > samples.size) samples = samples.copyOf(maxOf(samples.size * 2, count + shorts))
                for (i in 0 until shorts) {
                    samples[count + i] = ((buf[2 * i + 1].toInt() shl 8) or (buf[2 * i].toInt() and 0xFF)).toShort()
                }
                count += shorts
                _recordedMillis.value = count * 1000L / captureRate
            }
        }
    }

    private fun openLine(): Pair<TargetDataLine, Int> {
        var lastError: Exception? = null
        for (rate in CANDIDATE_RATES) {
            val format = AudioFormat(rate.toFloat(), 16, 1, true, false)
            val info = DataLine.Info(TargetDataLine::class.java, format)
            if (!AudioSystem.isLineSupported(info)) continue
            try {
                val l = AudioSystem.getLine(info) as TargetDataLine
                l.open(format)
                return l to rate
            } catch (e: LineUnavailableException) {
                lastError = e
            } catch (e: IllegalArgumentException) {
                lastError = e
            }
        }
        throw MicUnavailableException(cause = lastError)
    }

    private companion object {
        val CANDIDATE_RATES = listOf(16_000, 48_000, 44_100)
    }
}
