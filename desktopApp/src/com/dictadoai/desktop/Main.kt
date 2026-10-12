package com.dictadoai.desktop

import com.dictadoai.desktop.adapter.macos.MacosBridge
import com.dictadoai.desktop.adapter.macos.configureFloating
import com.dictadoai.desktop.adapter.macos.focusState
import com.dictadoai.desktop.adapter.macos.frontmostApp
import com.dictadoai.desktop.ui.BubbleWindow
import com.dictadoai.desktop.ui.TrayMenu
import javax.swing.SwingUtilities
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
    // Provisional (fase 4): prototipo de la bolita. Se reemplaza por la raíz de composición en la fase 6.
    runBubblePrototype(useFocusGuard = "--focus-guard" in args)
}

/** Prototipo de la fase 4: muestra la bolita y diagnostica el foco al hacer clic. */
private fun runBubblePrototype(useFocusGuard: Boolean) {
    System.setProperty("apple.awt.UIElement", "true") // sin icono en el Dock; antes de iniciar AWT
    val mac = MacosBridge.load(AppPaths.resolve())
    val bubble = BubbleWindow(
        onClick = {
            println("[clic] ${mac.focusState()}")
            if (useFocusGuard) mac.dictado_macos_restore_focus()
            if (useFocusGuard) println("[clic] tras restaurar foco: ${mac.frontmostApp()}")
        },
        onMoved = { x, y -> println("[mover] bolita en ($x, $y)") },
    )
    SwingUtilities.invokeLater {
        bubble.create()
        bubble.show()
        val result = mac.configureFloating(BubbleWindow.TITLE)
        println("[ventana] código=${result.code} (0=NSPanel no activante, 2=NSWindow normal) ${result.info}")
        if (useFocusGuard) mac.dictado_macos_start_focus_guard()
        TrayMenu(onExit = { kotlin.system.exitProcess(0) }).install()
        println("[listo] app al frente ahora: ${mac.frontmostApp()}")
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
