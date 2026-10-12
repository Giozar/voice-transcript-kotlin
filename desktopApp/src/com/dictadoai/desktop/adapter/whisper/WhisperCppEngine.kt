package com.dictadoai.desktop.adapter.whisper

import com.dictadoai.core.port.AudioClip
import com.dictadoai.core.port.TranscriptionEngine
import com.dictadoai.desktop.config.AppPaths
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Motor de transcripción local: whisper.cpp con Metal, a través del puente en C. */
class WhisperCppEngine(
    private val paths: AppPaths,
    private val threads: Int = 4,
) : TranscriptionEngine {
    private var bridge: WhisperBridge? = null
    private var context: Pointer? = null

    override suspend fun load() = withContext(Dispatchers.IO) {
        check(paths.modelFile.isFile) { "No se encontró el modelo de voz: ${paths.modelFile}" }
        val lib = File(paths.libDir, "libdictado_whisper.dylib")
        check(lib.isFile) { "No se encontró la biblioteca nativa: $lib" }

        val loaded = Native.load(
            lib.absolutePath,
            WhisperBridge::class.java,
            mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"),
        )
        context = loaded.dictado_whisper_init(paths.modelFile.absolutePath)
            ?: error("No se pudo cargar el modelo de voz: ${paths.modelFile.name}")
        bridge = loaded
    }

    override suspend fun transcribe(clip: AudioClip, language: String): String = withContext(Dispatchers.IO) {
        val b = checkNotNull(bridge) { "El modelo no está cargado" }
        val ctx = checkNotNull(context) { "El modelo no está cargado" }
        val textPtr = b.dictado_whisper_transcribe(ctx, clip.samples, clip.samples.size, language, threads)
            ?: error("La transcripción falló")
        try {
            textPtr.getString(0, "UTF-8")
        } finally {
            b.dictado_whisper_free_text(textPtr)
        }
    }

    override fun close() {
        val b = bridge
        val ctx = context
        if (b != null && ctx != null) b.dictado_whisper_free(ctx)
        context = null
        bridge = null
    }
}
