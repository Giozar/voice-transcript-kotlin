package com.dictadoai.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class TranscriptCleanerTest {
    @Test fun `trims and collapses whitespace`() {
        assertEquals("hola mundo", TranscriptCleaner.clean("  hola \n  mundo  "))
    }

    @Test fun `removes noise tags`() {
        assertEquals("", TranscriptCleaner.clean("[BLANK_AUDIO]"))
        assertEquals("", TranscriptCleaner.clean(" [Música] "))
        assertEquals("", TranscriptCleaner.clean("(música)"))
        assertEquals("hola", TranscriptCleaner.clean("[Música] hola (aplausos)"))
    }

    @Test fun `removes known hallucinations`() {
        assertEquals("", TranscriptCleaner.clean("Subtítulos realizados por la comunidad de Amara.org"))
        assertEquals("", TranscriptCleaner.clean("¡Suscríbete!"))
    }

    @Test fun `keeps real text that mentions parentheses`() {
        assertEquals("ver anexo (tabla 2)", TranscriptCleaner.clean("ver anexo (tabla 2)"))
    }

    @Test fun `keeps text that merely contains a hallucination phrase`() {
        assertEquals("Suscríbete al plan premium hoy", TranscriptCleaner.clean("Suscríbete al plan premium hoy"))
    }
}
