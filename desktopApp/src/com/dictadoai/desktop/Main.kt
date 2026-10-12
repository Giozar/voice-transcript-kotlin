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
import com.dictadoai.core.application.DictationController
import com.dictadoai.core.domain.BubblePosition
import com.dictadoai.desktop.adapter.clipboard.AwtClipboardWriter
import com.dictadoai.desktop.adapter.settings.PropertiesBubblePositionStore
import com.dictadoai.desktop.ui.PanelWindow
import com.dictadoai.desktop.ui.isOnAnyScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import java.awt.Point
import com.dictadoai.desktop.adapter.audio.JavaSoundRecorder
import com.dictadoai.desktop.adapter.audio.WavWriter
import com.dictadoai.desktop.adapter.macos.MacosMicrophonePermission
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--transcribe") {
        transcribeFile(File(args.getOrNull(1) ?: error("Uso: --transcribe <archivo.wav>")))
        return
    }
    if (args.firstOrNull() == "--record") {
        recordAndTranscribe(args.getOrNull(1)?.toInt() ?: 5)
        return
    }
    runApp()
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

/** Modo de diagnóstico: pide el micrófono, graba N segundos (con una pausa de prueba) y transcribe. */
private fun recordAndTranscribe(seconds: Int): Unit = runBlocking {
    val paths = AppPaths.resolve()
    val permission = MacosMicrophonePermission(MacosBridge.load(paths))
    println("Permiso de micrófono (si es la primera vez, macOS mostrará un aviso: pulsa Permitir)...")
    if (!permission.ensureGranted()) {
        println("PERMISO DENEGADO. Actívalo en Ajustes del Sistema > Privacidad y seguridad > Micrófono.")
        exitProcess(1)
    }
    val engine = WhisperCppEngine(paths)
    println("Cargando modelo...")
    engine.load()

    val recorder = JavaSoundRecorder()
    beep("Ping")
    println(">>> HABLA AHORA durante $seconds s (a mitad hago 2 s de pausa: no hables en ese momento)")
    recorder.start()
    val half = seconds * 500L
    delay(half)
    println(">>> PAUSA 2 s (grabado: ${recorder.recordedMillis.value} ms)")
    recorder.pause(); beep("Tink"); delay(2000); beep("Tink"); recorder.resume()
    println(">>> sigue grabando")
    delay(half)
    val clip = recorder.stop()
    beep("Pop")
    println("Grabado: ${clip.durationMillis} ms, RMS=${"%.4f".format(clip.rms())}")

    val wav = File(paths.dataDir, "ultima-grabacion.wav").also { it.parentFile.mkdirs() }
    WavWriter.write(clip, wav)
    println("Copia de depuración: $wav")

    println("TEXTO: " + engine.transcribe(clip, "es"))
    engine.close()
    exitProcess(0)
}

/** Sonido del sistema para marcar el inicio y el fin de la grabación de prueba (no se graba). */
private fun beep(name: String) {
    ProcessBuilder("afplay", "/System/Library/Sounds/$name.aiff").start().waitFor()
}

/** Raíz de composición: única clase que conoce a todos los adaptadores y los une con el controlador. */
private fun runApp() {
    System.setProperty("apple.awt.UIElement", "true") // sin icono en el Dock; antes de iniciar AWT
    val paths = AppPaths.resolve()
    val mac = MacosBridge.load(paths)
    val positionStore = PropertiesBubblePositionStore(File(paths.dataDir, "config.properties"))

    SwingUtilities.invokeLater {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
        val clipboard = AwtClipboardWriter()
        val controller = DictationController(
            recorder = JavaSoundRecorder(),
            engine = WhisperCppEngine(paths),
            clipboard = clipboard,
            permission = MacosMicrophonePermission(mac),
            scope = scope,
        )

        lateinit var panel: PanelWindow
        val bubble = BubbleWindow(
            state = controller.state,
            onClick = { controller.onBubbleClick() },
            onMoved = { x, y -> positionStore.save(BubblePosition(x, y)) },
            onBoundsChanged = { panel.reposition() },
        )
        panel = PanelWindow(controller, clipboard, mac, bubble)

        val saved = positionStore.load()?.let { Point(it.x, it.y) }?.takeIf { isOnAnyScreen(it) }
        bubble.create(saved)
        panel.create()
        bubble.show()
        mac.configureFloating(BubbleWindow.TITLE)
        mac.dictado_macos_start_focus_guard()

        scope.launch { controller.state.collect { panel.update(it) } }
        TrayMenu(onExit = {
            positionStore.save(BubblePosition(bubble.window.x, bubble.window.y))
            controller.shutdown()
            exitProcess(0)
        }).install()
        controller.start()
    }
}
