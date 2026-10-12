package com.dictadoai.core.domain

/** Texto de la sesión de dictado y posición del cursor (o de la selección) dentro de él. */
data class TranscriptDocument(
    val text: String = "",
    val selectionStart: Int = text.length,
    val selectionEnd: Int = selectionStart,
) {
    companion object {
        val EMPTY = TranscriptDocument()
    }
}
