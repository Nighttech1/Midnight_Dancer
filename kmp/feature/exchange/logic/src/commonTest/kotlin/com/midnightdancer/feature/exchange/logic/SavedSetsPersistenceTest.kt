package com.midnightdancer.feature.exchange.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.midnightdancer.core.domain.exchange.ExportCatalogSnapshot
import com.midnightdancer.core.domain.exchange.ExportPackageSelection
import com.midnightdancer.core.domain.exchange.ImportPeek
import com.midnightdancer.core.domain.exchange.StyleImportPlan
import com.midnightdancer.core.domain.exchange.StyleRef
import com.midnightdancer.core.domain.usecase.ExchangeZipResult
import com.midnightdancer.core.domain.usecase.ExchangeZipUseCase
import com.midnightdancer.core.domain.usecase.ImportContentFilter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class SavedSetsPersistenceTest {
    @BeforeTest fun reset() { SavedSetsStore.replaceAll(emptyList()) }
    @AfterTest fun cleanup() { SavedSetsStore.replaceAll(emptyList()) }

    private class Disk(var rows: List<SavedSet>? = null) : SavedSetsPersister {
        override fun read() = rows
        override fun write(sets: List<SavedSet>) { rows = sets.toList() }
        override fun retainArchive(sourcePath: String, setId: String) = "/owned/$setId/${sourcePath.substringAfterLast('/')}"
    }

    private class Zip : ExchangeZipUseCase {
        override suspend fun export(selection: ExportPackageSelection?) = ExchangeZipResult.Ok("export", "/export/latest.dance")
        override suspend fun import(preferredPath: String?, contentFilter: ImportContentFilter, stylePlan: StyleImportPlan) =
            ExchangeZipResult.Ok("import", requireNotNull(preferredPath), 100L)
        override suspend fun peekImport(preferredPath: String?) = ImportPeek(emptyList(), listOf(StyleRef("s", "Style")))
        override suspend fun loadExportCatalog() = ExportCatalogSnapshot(styles = listOf(StyleRef("s", "Style")))
    }

    @Test fun draft_copy_and_delete_survive_process_memory_reset() {
        val disk = Disk()
        val lifecycle = LifecycleRegistry().also { it.resume() }
        val first = DefaultExchangeComponent(DefaultComponentContext(lifecycle), savedSetsPersister = disk)
        first.onIntent(ExchangeIntent.ExportStub)
        val draftId = first.uiState.value.savedSets.single().id
        first.onIntent(ExchangeIntent.CopySavedSet(draftId))
        assertEquals(2, disk.rows?.size)
        lifecycle.destroy()
        SavedSetsStore.replaceAll(emptyList()) // A new process cannot retain the singleton cache.
        val secondLife = LifecycleRegistry().also { it.resume() }
        val restored = DefaultExchangeComponent(DefaultComponentContext(secondLife), savedSetsPersister = disk)
        assertEquals(2, restored.uiState.value.savedSets.size)
        restored.onIntent(ExchangeIntent.DeleteSavedSet(draftId))
        val remaining = restored.uiState.value.savedSets.single().id
        restored.onIntent(ExchangeIntent.DeleteSavedSet(remaining))
        secondLife.destroy()
        // A deliberately empty disk file is authoritative even if a stale process cache exists.
        SavedSetsStore.replaceAll(listOf(SavedSet("stale", "old", "s")))
        val empty = DefaultExchangeComponent(DefaultComponentContext(LifecycleRegistry()), savedSetsPersister = disk)
        assertTrue(empty.uiState.value.savedSets.isEmpty())
    }

    @Test fun imported_cards_share_their_own_archives_after_another_import_and_restart() = runBlocking {
        val disk = Disk()
        val life = LifecycleRegistry().also { it.resume() }
        val component = DefaultExchangeComponent(DefaultComponentContext(life), exchangeZip = Zip(), savedSetsPersister = disk)
        suspend fun import(path: String) {
            component.onIntent(ExchangeIntent.ImportPackagePicked(path, path.substringAfterLast('/')))
            withTimeout(3000) { while (component.uiState.value.importLocalStyles.isEmpty()) delay(5) }
            component.onIntent(ExchangeIntent.ConfirmImport)
            withTimeout(3000) { while (component.uiState.value.busy) delay(5) }
        }
        import("/inbox/first.dance")
        val first = component.uiState.value.savedSets.single()
        assertTrue(first.moveIds.isEmpty() && first.songIds.isEmpty() && first.choreoIds.isEmpty())
        import("/inbox/second.dance")
        val second = component.uiState.value.savedSets.first()
        component.onIntent(ExchangeIntent.ShareSet(first.id))
        assertEquals(first.archivePath, component.uiState.value.pendingSharePath)
        assertTrue(component.uiState.value.pendingSharePath!!.endsWith("first.dance"))
        component.onIntent(ExchangeIntent.ClearPendingShare)
        component.onIntent(ExchangeIntent.ShareSet(second.id))
        assertEquals(second.archivePath, component.uiState.value.pendingSharePath)
        life.destroy()
        SavedSetsStore.replaceAll(emptyList())
        val restored = DefaultExchangeComponent(DefaultComponentContext(LifecycleRegistry()), savedSetsPersister = disk)
        restored.onIntent(ExchangeIntent.ShareSet(first.id))
        assertEquals(first.archivePath, restored.uiState.value.pendingSharePath)
    }

    @Test fun legacy_import_without_own_archive_never_shares_another_sets_file() {
        val legacy = SavedSet("old", "Old import", "s", origin = SavedSetOrigin.Imported)
        val component = DefaultExchangeComponent(DefaultComponentContext(LifecycleRegistry()), savedSetsPersister = Disk(listOf(legacy)))
        component.onIntent(ExchangeIntent.ShareSet(legacy.id))
        assertNull(component.uiState.value.pendingSharePath)
        assertTrue(component.uiState.value.lastActionLabel.orEmpty().contains("повторно"))
    }

    @Test fun disk_failure_does_not_report_a_persisted_draft_or_crash() {
        val failing = object : SavedSetsPersister {
            override fun read(): List<SavedSet>? = null
            override fun write(sets: List<SavedSet>): Unit = error("disk full")
            override fun retainArchive(sourcePath: String, setId: String): String = error("disk full")
        }
        val component = DefaultExchangeComponent(DefaultComponentContext(LifecycleRegistry()), savedSetsPersister = failing)
        component.onIntent(ExchangeIntent.ExportStub)
        assertTrue(component.uiState.value.savedSets.isEmpty())
        assertEquals("Не удалось сохранить сет", component.uiState.value.lastActionLabel)
    }

    @Test fun unreadable_metadata_is_never_overwritten_by_a_new_draft() {
        var writes = 0
        val newerSchema = object : SavedSetsPersister {
            override fun read(): List<SavedSet>? = error("unsupported schema")
            override fun write(sets: List<SavedSet>) { writes++ }
            override fun retainArchive(sourcePath: String, setId: String) = sourcePath
        }
        val component = DefaultExchangeComponent(DefaultComponentContext(LifecycleRegistry()), savedSetsPersister = newerSchema)
        assertTrue(component.uiState.value.lastActionLabel.orEmpty().contains("прочитать"))
        component.onIntent(ExchangeIntent.ExportStub)
        assertEquals(0, writes)
        assertTrue(component.uiState.value.savedSets.isEmpty())
    }
}
