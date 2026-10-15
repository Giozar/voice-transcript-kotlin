package com.dictadoai.core.application

import com.dictadoai.core.domain.WhisperCatalog
import com.dictadoai.core.domain.WhisperModel
import com.dictadoai.core.port.ActiveModelStore
import com.dictadoai.core.port.ModelDownloader
import com.dictadoai.core.port.ModelRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Situación de un modelo del catálogo en la pantalla de modelos. */
sealed interface ModelStatus {
    data object NotInstalled : ModelStatus
    data class Downloading(val progress: Float) : ModelStatus
    data object Installed : ModelStatus

    /** Descargado y en uso por el dictado. */
    data object Active : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

data class ModelEntry(val model: WhisperModel, val status: ModelStatus)

/**
 * Descarga, elige, elimina y lista los modelos del catálogo.
 * No carga el modelo en el motor: eso lo hace [DictationController] al elegirlo.
 */
class ModelManager(
    private val repository: ModelRepository,
    private val downloader: ModelDownloader,
    private val activeStore: ActiveModelStore,
    private val scope: CoroutineScope,
) {
    private val tick = MutableStateFlow(0)
    private val downloads = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val failures = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Se recalcula al cambiar algo (lee el disco para saber qué está instalado). */
    val entries: StateFlow<List<ModelEntry>> = combine(tick, downloads, failures) { _, dl, fl ->
        buildEntries(dl, fl)
    }.stateIn(scope, SharingStarted.Eagerly, buildEntries(emptyMap(), emptyMap()))

    /**
     * Modelo que usa el dictado: el elegido si sigue descargado. Si no, el recomendado o el primero descargado.
     * Null si no hay ninguno.
     */
    fun activeModel(): WhisperModel? {
        val chosen = activeStore.load()?.let(WhisperCatalog::byId)
        if (chosen != null && repository.isInstalled(chosen)) return chosen
        val fallbackIds = listOf(WhisperCatalog.DEFAULT_ID) + WhisperCatalog.models.map { it.id }
        return fallbackIds.firstNotNullOfOrNull { id ->
            WhisperCatalog.byId(id)?.takeIf { repository.isInstalled(it) }
        }
    }

    /** Guarda la elección. Devuelve false si el modelo no está descargado. */
    fun select(model: WhisperModel): Boolean {
        if (!repository.isInstalled(model)) return false
        activeStore.save(model.id)
        tick.update { it + 1 }
        return true
    }

    /** Descarga en segundo plano. No hace nada si ya está instalado o en curso. */
    fun download(model: WhisperModel) {
        if (repository.isInstalled(model) || downloads.value.containsKey(model.id)) return
        failures.update { it - model.id }
        downloads.update { it + (model.id to 0f) }
        scope.launch {
            try {
                downloader.download(model) { progress -> downloads.update { it + (model.id to progress) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures.update { it + (model.id to (e.message ?: "No se pudo descargar el modelo")) }
            } finally {
                downloads.update { it - model.id }
                tick.update { it + 1 }
            }
        }
    }

    /** Elimina un modelo descargado. No se puede eliminar el que está en uso. Devuelve false en ese caso. */
    fun delete(model: WhisperModel): Boolean {
        if (activeModel()?.id == model.id) return false
        repository.delete(model)
        tick.update { it + 1 }
        return true
    }

    private fun buildEntries(dl: Map<String, Float>, fl: Map<String, String>): List<ModelEntry> {
        val active = activeModel()?.id
        return WhisperCatalog.models.map { model ->
            val status = when {
                dl.containsKey(model.id) -> ModelStatus.Downloading(dl.getValue(model.id))
                fl.containsKey(model.id) -> ModelStatus.Failed(fl.getValue(model.id))
                !repository.isInstalled(model) -> ModelStatus.NotInstalled
                model.id == active -> ModelStatus.Active
                else -> ModelStatus.Installed
            }
            ModelEntry(model, status)
        }
    }
}
