package com.dictadoai.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dictadoai.desktop.adapter.audio.WavReader
import com.dictadoai.desktop.adapter.whisper.WhisperCppEngine
import com.dictadoai.desktop.config.AppPaths
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--transcribe") {
        transcribeFile(File(args.getOrNull(1) ?: error("Uso: --transcribe <archivo.wav>")))
        return
    }
    // Provisional: se reemplaza por la raíz de composición en la fase 6.
    application {
        Window(onCloseRequest = ::exitApplication, title = "DictadoAI") {
            MaterialTheme { Text("DictadoAI") }
        }
    }
}

/** Modo de diagnóstico sin interfaz: transcribe un WAV e imprime texto, tiempos y memoria. */
private fun transcribeFile(wav: File): Unit = runBlocking {
    val engine = WhisperCppEngine(AppPaths.resolve())
    val clip = WavReader.read(wav)
    println("Audio: ${clip.durationMillis} ms, RMS=${"%.4f".format(clip.rms())}")

    val loadMs = measureTimeMillis { engine.load() }
    println("Modelo cargado en $loadMs ms (RSS ${rssMb()} MB)")

    var text = ""
    val firstMs = measureTimeMillis { text = engine.transcribe(clip, "es") }
    println("Transcripción (1.ª, con calentamiento): $firstMs ms")
    val secondMs = measureTimeMillis { engine.transcribe(clip, "es") }
    println("Transcripción (2.ª): $secondMs ms (RSS ${rssMb()} MB)")
    println("TEXTO: $text")
    engine.close()
    exitProcess(0)
}

private fun rssMb(): Long =
    ProcessBuilder("ps", "-o", "rss=", "-p", ProcessHandle.current().pid().toString())
        .start().inputStream.bufferedReader().readText().trim().toLong() / 1024
