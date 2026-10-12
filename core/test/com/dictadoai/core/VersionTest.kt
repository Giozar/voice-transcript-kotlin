package com.dictadoai.core

import kotlin.test.Test
import kotlin.test.assertEquals

class VersionTest {
    @Test
    fun `app name is DictadoAI`() {
        assertEquals("DictadoAI", Version.NAME)
    }
}
