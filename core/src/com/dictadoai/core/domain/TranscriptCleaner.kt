package com.dictadoai.core.domain

/** Limpia la salida de Whisper: marcas de ruido y frases que el modelo inventa con silencio. */
object TranscriptCleaner {

    private val noiseTags = Regex(
        """\[\s*(?:blank_audio|silence|silencio|music|música|musica|aplausos|applause)[^\]]*]|""" +
            """\(\s*(?:music|música|musica|aplausos|applause|silencio|silence)\s*\)""",
        RegexOption.IGNORE_CASE,
    )

    private val hallucinations = listOf(
        "subtítulos realizados por la comunidad de amara.org",
        "subtitulos realizados por la comunidad de amara.org",
        "subtítulos por la comunidad de amara.org",
        "suscríbete",
        "suscribete",
        "gracias por ver el vídeo",
        "gracias por ver el video",
    )

    fun clean(raw: String): String {
        val withoutTags = noiseTags.replace(raw, " ")
        val text = withoutTags.replace(Regex("""\s+"""), " ").trim()
        return if (isHallucination(text)) "" else text
    }

    private fun isHallucination(text: String): Boolean {
        val normalized = text.lowercase().trim { !it.isLetterOrDigit() }
        return normalized.isNotEmpty() && hallucinations.any { normalized == it }
    }
}
