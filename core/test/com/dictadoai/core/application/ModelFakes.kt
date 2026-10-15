package com.dictadoai.core.application

import com.dictadoai.core.domain.WhisperCatalog
import com.dictadoai.core.domain.WhisperModel
import com.dictadoai.core.port.ActiveModelStore
import com.dictadoai.core.port.ModelDownloader
import com.dictadoai.core.port.ModelRepository

class FakeModelRepository(val installed: MutableSet<String> = mutableSetOf(WhisperCatalog.DEFAULT_ID)) : ModelRepository {
    override fun isInstalled(model: WhisperModel) = model.id in installed
    override fun locate(model: WhisperModel) = "/fake/${model.fileName}"
    override fun delete(model: WhisperModel) { installed -= model.id }
}

class FakeModelDownloader(private val repository: FakeModelRepository) : ModelDownloader {
    var error: Exception? = null
    var steps = listOf(0.5f)

    override suspend fun download(model: WhisperModel, onProgress: (Float) -> Unit) {
        steps.forEach(onProgress)
        error?.let { throw it }
        repository.installed += model.id
    }
}

class FakeActiveModelStore(var saved: String? = null) : ActiveModelStore {
    override fun load() = saved
    override fun save(modelId: String) { saved = modelId }
}
