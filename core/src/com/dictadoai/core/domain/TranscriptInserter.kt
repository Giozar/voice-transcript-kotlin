package com.dictadoai.core.domain

/** Inserta un fragmento dictado en el cursor, cuidando que no quede pegado a las palabras vecinas. */
object TranscriptInserter {

    /** Devuelve el documento con [piece] en el cursor (reemplazando la selección) y el cursor justo después. */
    fun insert(doc: TranscriptDocument, piece: String): TranscriptDocument {
        val clean = piece.trim()
        if (clean.isEmpty()) return doc

        val length = doc.text.length
        val start = minOf(doc.selectionStart, doc.selectionEnd).coerceIn(0, length)
        val end = maxOf(doc.selectionStart, doc.selectionEnd).coerceIn(0, length)

        val before = doc.text.substring(0, start)
        val after = doc.text.substring(end)
        val prefix = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val suffix = if (after.isNotEmpty() && !after.first().isWhitespace()) " " else ""

        val inserted = prefix + clean + suffix
        val caret = before.length + inserted.length
        return TranscriptDocument(before + inserted + after, caret, caret)
    }
}
