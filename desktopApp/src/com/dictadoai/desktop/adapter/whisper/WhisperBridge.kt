package com.dictadoai.desktop.adapter.whisper

import com.sun.jna.Library
import com.sun.jna.Pointer

/** Interfaz JNA de `libdictado_whisper.dylib` (ver `native/whisper_bridge.c`). */
interface WhisperBridge : Library {
    fun dictado_whisper_init(modelPath: String): Pointer?

    /** Devuelve un puntero al texto (hay que liberarlo con [dictado_whisper_free_text]) o null si falla. */
    fun dictado_whisper_transcribe(ctx: Pointer, samples: FloatArray, nSamples: Int, language: String, nThreads: Int): Pointer?

    fun dictado_whisper_free_text(text: Pointer)
    fun dictado_whisper_free(ctx: Pointer)
}
