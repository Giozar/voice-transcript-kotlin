package com.dictadoai.desktop.adapter.models

import com.dictadoai.core.domain.WhisperModel
import com.dictadoai.core.port.ModelDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Descarga modelos desde Hugging Face a [modelsDir]. Usa un archivo `.part` y reanuda si quedó a medias.
 * Solo al terminar y verificar el SHA-256 lo renombra al nombre final.
 */
class HttpModelDownloader(private val modelsDir: File) : ModelDownloader {

    override suspend fun download(model: WhisperModel, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        modelsDir.mkdirs()
        val part = File(modelsDir, "${model.fileName}.part")
        val target = File(modelsDir, model.fileName)
        if (part.length() > model.sizeBytes) part.delete()

        val digest = MessageDigest.getInstance("SHA-256")
        var offset = part.length()
        if (offset > 0) part.inputStream().use { hashAll(it, digest) } // el SHA-256 cubre todo el archivo, también lo ya bajado

        val conn = URL(BASE_URL + model.fileName).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        if (offset > 0) conn.setRequestProperty("Range", "bytes=$offset-")
        try {
            val code = conn.responseCode
            when {
                code == 416 && offset == model.sizeBytes -> Unit // el .part ya estaba completo
                code !in 200..299 -> throw IOException("El servidor respondió HTTP $code")
                else -> {
                    val resuming = code == 206 && offset > 0 // 200 = el servidor ignoró Range: se empieza de cero
                    if (!resuming) {
                        offset = 0
                        digest.reset()
                    }
                    conn.inputStream.use { writeTo(it, part, resuming, offset, digest, model.sizeBytes, onProgress) }
                }
            }
        } finally {
            conn.disconnect()
        }

        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (part.length() != model.sizeBytes || actual != model.sha256) {
            part.delete()
            throw IOException("La descarga no coincide con el SHA-256 esperado. Se borró; vuelve a intentarlo.")
        }
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        onProgress(1f)
    }

    private fun writeTo(
        input: InputStream,
        part: File,
        append: Boolean,
        start: Long,
        digest: MessageDigest,
        total: Long,
        onProgress: (Float) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var done = start
        var lastReported = start
        FileOutputStream(part, append).use { out ->
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                digest.update(buffer, 0, n)
                done += n
                // Se informa cada 0.5 %, no en cada trozo: cada aviso relee el disco para repintar la lista.
                if (done - lastReported >= total / 200) {
                    lastReported = done
                    onProgress(done.toFloat() / total)
                }
            }
        }
    }

    private fun hashAll(input: InputStream, digest: MessageDigest) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }

    private companion object {
        const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"
        const val BUFFER_SIZE = 64 * 1024
    }
}
