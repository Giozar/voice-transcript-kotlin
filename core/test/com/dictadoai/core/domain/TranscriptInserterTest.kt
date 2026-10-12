package com.dictadoai.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class TranscriptInserterTest {
    private fun insert(text: String, start: Int, end: Int = start, piece: String) =
        TranscriptInserter.insert(TranscriptDocument(text, start, end), piece)

    @Test fun `into an empty document there is no extra space`() {
        val r = insert("", 0, piece = " hola mundo ")
        assertEquals(TranscriptDocument("hola mundo", 10, 10), r)
    }

    @Test fun `at the end adds a leading space`() {
        val r = insert("Hola", 4, piece = "mundo")
        assertEquals("Hola mundo", r.text)
        assertEquals(10, r.selectionStart)
    }

    @Test fun `at the end after a space does not double it`() {
        assertEquals("Hola mundo", insert("Hola ", 5, piece = "mundo").text)
    }

    @Test fun `in the middle gets a space on both sides`() {
        val r = insert("Hola mundo", 4, piece = "querido")
        assertEquals("Hola querido mundo", r.text)
        assertEquals(12, r.selectionStart) // justo después de "Hola querido"
    }

    @Test fun `in the middle where spaces already exist adds none`() {
        assertEquals("Hola querido mundo", insert("Hola  mundo".replace("  ", " "), 5, piece = "querido").text)
    }

    @Test fun `at the beginning adds a trailing space`() {
        val r = insert("mundo", 0, piece = "Hola")
        assertEquals("Hola mundo", r.text)
        assertEquals(5, r.selectionStart)
    }

    @Test fun `a selection is replaced`() {
        assertEquals("Hola nuevo mundo", insert("Hola viejo mundo", 5, 10, "nuevo").text)
    }

    @Test fun `reversed selection is handled`() {
        assertEquals("Hola nuevo mundo", insert("Hola viejo mundo", 10, 5, "nuevo").text)
    }

    @Test fun `out of range cursor is clamped`() {
        assertEquals("Hola mundo", insert("Hola", 99, piece = "mundo").text)
    }

    @Test fun `blank piece changes nothing`() {
        val doc = TranscriptDocument("Hola", 4, 4)
        assertEquals(doc, TranscriptInserter.insert(doc, "   "))
    }

    @Test fun `consecutive inserts keep spacing`() {
        var doc = TranscriptDocument.EMPTY
        doc = TranscriptInserter.insert(doc, "uno")
        doc = TranscriptInserter.insert(doc, "dos")
        doc = TranscriptInserter.insert(doc, "tres")
        assertEquals("uno dos tres", doc.text)
    }
}
