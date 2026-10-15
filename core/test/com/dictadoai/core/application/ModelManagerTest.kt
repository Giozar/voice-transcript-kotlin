package com.dictadoai.core.application

import com.dictadoai.core.domain.WhisperCatalog
import com.dictadoai.core.domain.WhisperModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ModelManagerTest {
    private val repo = FakeModelRepository()
    private val store = FakeActiveModelStore()
    private val downloader = FakeModelDownloader(repo)
    private val tiny = WhisperCatalog.byId("tiny")!!
    private val small = WhisperCatalog.byId("small")!!
    private val default = WhisperCatalog.byId(WhisperCatalog.DEFAULT_ID)!!

    private fun TestScope.manager() = ModelManager(
        repo, downloader, store, CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
    )

    private fun ModelManager.statusOf(model: WhisperModel) = entries.value.first { it.model.id == model.id }.status

    @Test fun `the chosen model is the active one while it is installed`() = runTest {
        repo.installed += "tiny"
        store.saved = "tiny"
        assertEquals(tiny, manager().activeModel())
    }

    @Test fun `falls back to the recommended model when the chosen one is missing`() = runTest {
        store.saved = "small"
        assertEquals(default, manager().activeModel())
    }

    @Test fun `there is no active model when nothing is installed`() = runTest {
        repo.installed.clear()
        assertNull(manager().activeModel())
    }

    @Test fun `select stores the choice only for installed models`() = runTest {
        val m = manager()
        assertFalse(m.select(small))
        assertNull(store.saved)
        repo.installed += "small"
        assertTrue(m.select(small))
        assertEquals("small", store.saved)
    }

    @Test fun `download installs the model and it becomes active when it is the only one`() = runTest {
        repo.installed.clear()
        val m = manager()
        m.download(tiny)
        advanceUntilIdle()
        assertTrue(repo.isInstalled(tiny))
        assertEquals(ModelStatus.Active, m.statusOf(tiny))
    }

    @Test fun `a failed download is reported and can be retried`() = runTest {
        repo.installed.clear()
        downloader.error = IllegalStateException("sin red")
        val m = manager()
        m.download(tiny)
        advanceUntilIdle()
        assertEquals(ModelStatus.Failed("sin red"), m.statusOf(tiny))

        downloader.error = null
        m.download(tiny)
        advanceUntilIdle()
        assertTrue(repo.isInstalled(tiny))
    }

    @Test fun `the model in use cannot be deleted`() = runTest {
        val m = manager()
        assertFalse(m.delete(default))
        assertTrue(repo.isInstalled(default))
    }

    @Test fun `an installed model that is not in use can be deleted`() = runTest {
        repo.installed += "tiny"
        store.saved = WhisperCatalog.DEFAULT_ID
        val m = manager()
        assertTrue(m.delete(tiny))
        assertFalse(repo.isInstalled(tiny))
    }
}
