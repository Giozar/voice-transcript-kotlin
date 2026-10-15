package com.dictadoai.core.domain

/**
 * Modelo de voz Whisper del catálogo. [sizeBytes] y [sha256] se verifican al descargar y al comprobar
 * si el archivo está instalado.
 */
data class WhisperModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val description: String,
)

/** Catálogo curado de modelos multilingües (sin variantes `.en`, el dictado es en español). */
object WhisperCatalog {
    const val DEFAULT_ID = "large-v3-turbo"

    val models: List<WhisperModel> = listOf(
        WhisperModel(
            "tiny", "Tiny", "ggml-tiny.bin", 77_691_713,
            "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
            "Muy ligero y con poca precisión. Útil para probar.",
        ),
        WhisperModel(
            "base", "Base", "ggml-base.bin", 147_951_465,
            "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe",
            "Ligero, con precisión básica.",
        ),
        WhisperModel(
            "small", "Small", "ggml-small.bin", 487_601_967,
            "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b",
            "Equilibrado para equipos modestos.",
        ),
        WhisperModel(
            "medium", "Medium", "ggml-medium.bin", 1_533_763_059,
            "6c14d5adee5f86394037b4e4e8b59f1673b6cee10e3cf0b11bbdbee79c156208",
            "Buena precisión, más lento.",
        ),
        WhisperModel(
            "large-v3-turbo-q5", "Large v3 Turbo (q5)", "ggml-large-v3-turbo-q5_0.bin", 574_041_195,
            "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
            "Large v3 Turbo comprimido: ocupa menos espacio y memoria.",
        ),
        WhisperModel(
            DEFAULT_ID, "Large v3 Turbo", "ggml-large-v3-turbo.bin", 1_624_555_275,
            "1fc70f774d38eb169993ac391eea357ef47c88757ef72ee5943879b7e8e2bc69",
            "Recomendado: muy buena precisión con buena velocidad.",
        ),
        WhisperModel(
            "large-v3", "Large v3", "ggml-large-v3.bin", 3_095_033_483,
            "64d182b440b98d5203c4f9bd541544d84c605196c4f7b845dfa11fb23594d1e2",
            "La mayor precisión. Lento y pesado.",
        ),
    )

    fun byId(id: String): WhisperModel? = models.firstOrNull { it.id == id }
}
