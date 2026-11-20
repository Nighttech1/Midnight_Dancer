package com.midnightdancer.feature.exchange.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.midnightdancer.core.domain.exchange.*
import com.midnightdancer.core.domain.usecase.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** C08 metadata-edit expectations are superseded by W-2026-11-14; archive ownership guards remain. */
class ImportedArchiveMetadataTest {
    private val lifecycles = mutableListOf<LifecycleRegistry>()
    private val archivePath = "/owned/archive-original.dance"
    private val opaquePayload = "original A+B contents".encodeToByteArray()
    private fun imported(path: String? = archivePath) = SavedSet(
        "imported-1-1", "Original archive", "s", "Sensual", estimatedBytes = 184719068L,
        origin = SavedSetOrigin.Imported, tags = listOf("sEnSuAl", "custom"), archivePath = path,
    )
    private class Disk(initial: List<SavedSet>, initialArchives: Map<String, ByteArray> = emptyMap()) : SavedSetsPersister {
        var rows = initial.toList()
        val archives = initialArchives.mapValues { it.value.copyOf() }.toMutableMap()
        var writes = 0
        override fun read() = rows
        override fun write(sets: List<SavedSet>) {
            rows = sets.toList(); writes++
            archives.keys.retainAll(sets.mapNotNull { it.archivePath }.toSet())
        }
        override fun retainArchive(sourcePath: String, setId: String): String = error("Viewer must not replace archive")
    }
    private val catalog = ExportCatalogSnapshot(
        styles = listOf(StyleRef("s", "Sensual"), StyleRef("b", "Bachata")),
        elements = listOf(CatalogPickItem("A", "Local A", styleId = "s"), CatalogPickItem("B", "Foreign B", styleId = "b")),
        songs = listOf(CatalogPickItem("song-A", "Local song", styleId = "Sensual"), CatalogPickItem("song-B", "Foreign song", styleId = "b")),
        choreographies = listOf(CatalogPickItem("ch-A", "Local choreo", styleId = "s"), CatalogPickItem("ch-B", "Foreign choreo", styleId = "b")),
    )
    private val archive = ExportCatalogSnapshot(
        styles = listOf(StyleRef("x", "Archive style")),
        elements = listOf(CatalogPickItem("archive:A", "Archived element", styleId = "x")),
        songs = listOf(CatalogPickItem("archive:song", "Archived song", styleId = "x")),
        choreographies = listOf(CatalogPickItem("archive:ch", "Archived choreography", styleId = "x")),
    )
    private class Zip(val catalog: ExportCatalogSnapshot, val snapshot: ExportCatalogSnapshot) : ExchangeZipUseCase {
        val exports = MutableStateFlow<List<ExportPackageSelection?>>(emptyList())
        val inspected = MutableStateFlow<List<String>>(emptyList())
        var loads = 0
        var inspect: suspend (String) -> ExportCatalogSnapshot = { snapshot }
        var load: suspend () -> ExportCatalogSnapshot = { catalog }
        override suspend fun export(selection: ExportPackageSelection?): ExchangeZipResult {
            exports.update { it + selection }; return ExchangeZipResult.Ok("OK", "/export/rebuilt.dance", 10)
        }
        override suspend fun import(preferredPath: String?, contentFilter: ImportContentFilter, stylePlan: StyleImportPlan): ExchangeZipResult = error("Viewer must not import")
        override suspend fun peekImport(preferredPath: String?): ImportPeek? = error("Viewer must not peek/import")
        override suspend fun loadExportCatalog(): ExportCatalogSnapshot { loads++; return load() }
        override suspend fun inspectArchive(archivePath: String): ExportCatalogSnapshot {
            inspected.update { it + archivePath }; return inspect(archivePath)
        }
    }
    @BeforeTest fun reset() { SavedSetsStore.replaceAll(emptyList()) }
    @AfterTest fun cleanup() { lifecycles.forEach { it.destroy() }; lifecycles.clear(); SavedSetsStore.replaceAll(emptyList()) }
    private fun component(disk: Disk, zip: Zip): DefaultExchangeComponent {
        val life = LifecycleRegistry().also { it.resume(); lifecycles += it }
        return DefaultExchangeComponent(DefaultComponentContext(life), exchangeZip = zip, savedSetsPersister = disk)
    }
    private suspend fun await(predicate: () -> Boolean) { withTimeout(5000) { while (!predicate()) delay(5) } }
    private suspend fun catalogReady(c: DefaultExchangeComponent) = await { c.uiState.value.exportCatalogLoaded }

    @Test fun all_imported_cards_are_immutable_browse_exact_archive_and_share_own_bytes_without_export() = runBlocking {
        for (withLegacyIds in listOf(false, true)) {
            val original = imported().let { if (withLegacyIds) it.copy(moveIds = listOf("B"), songIds = listOf("song-B"), choreoIds = listOf("ch-B")) else it }
            val disk = Disk(listOf(original), mapOf(archivePath to opaquePayload))
            val zip = Zip(catalog, archive)
            val c = component(disk, zip)
            c.onIntent(ExchangeIntent.EditSavedSet(original.id))
            await { c.uiState.value.archiveInspectionCatalog != null }
            assertTrue(c.uiState.value.viewingImportedSet && c.uiState.value.exportStyleLocked)
            assertEquals(archive, c.uiState.value.archiveInspectionCatalog)
            assertEquals(0, zip.loads)
            c.onIntent(ExchangeIntent.SetExportSetName("Wrong name"))
            c.onIntent(ExchangeIntent.SelectExportStyle("b"))
            for (kind in ExportPickKind.entries) {
                c.onIntent(ExchangeIntent.OpenExportPick(kind))
                assertEquals(kind, c.uiState.value.exportPickKind)
                c.onIntent(ExchangeIntent.ToggleExportPickItem("B"))
                c.onIntent(ExchangeIntent.ConfirmExportPick)
                assertTrue(c.uiState.value.exportPickDraftIds.isEmpty())
                c.onIntent(ExchangeIntent.DismissExportPick)
            }
            c.onIntent(ExchangeIntent.RemoveInvalidExportSelection)
            c.onIntent(ExchangeIntent.ConfirmExport)
            assertEquals(original.name, c.uiState.value.exportSetName)
            assertEquals(original.styleId, c.uiState.value.exportStyleId)
            c.onIntent(ExchangeIntent.DismissExportDialog)
            assertNull(c.uiState.value.archiveInspectionCatalog)
            assertEquals(original, disk.rows.single()); assertEquals(0, disk.writes)
            assertTrue(zip.exports.value.isEmpty())
            assertContentEquals(opaquePayload, disk.archives.getValue(archivePath))
            c.onIntent(ExchangeIntent.ShareSet(original.id))
            assertEquals(archivePath, c.uiState.value.pendingSharePath)
            assertEquals(listOf(archivePath), zip.inspected.value)
        }
    }

    @Test fun missing_archive_and_read_errors_are_not_empty_success_and_retry_reuses_own_path() = runBlocking {
        val original = imported(null)
        val zip = Zip(catalog, archive)
        val c = component(Disk(listOf(original)), zip)
        c.onIntent(ExchangeIntent.EditSavedSet(original.id))
        assertTrue(c.uiState.value.archiveInspectionError.orEmpty().contains("повторно"))
        assertNull(c.uiState.value.archiveInspectionCatalog)
        assertTrue(zip.inspected.value.isEmpty())
        c.onIntent(ExchangeIntent.ShareSet(original.id))
        assertNull(c.uiState.value.pendingSharePath)
        val c2 = component(Disk(listOf(imported())), zip)
        zip.inspect = { error("unsupported_editor_vault_schema") }
        c2.onIntent(ExchangeIntent.EditSavedSet(original.id))
        await { c2.uiState.value.archiveInspectionError != null }
        assertTrue(c2.uiState.value.archiveInspectionError.orEmpty().contains("не поддерживается"))
        c2.onIntent(ExchangeIntent.OpenExportPick(ExportPickKind.Elements))
        assertNull(c2.uiState.value.exportPickKind)
        zip.inspect = { archive }
        c2.onIntent(ExchangeIntent.RetryArchiveInspection)
        await { c2.uiState.value.archiveInspectionCatalog != null }
        assertNull(c2.uiState.value.archiveInspectionError)
        assertEquals(listOf(archivePath, archivePath), zip.inspected.value)
    }

    @Test fun late_inspection_and_late_catalog_cannot_replace_another_card_or_reopen_closed_viewer() = runBlocking {
        val a = imported().copy(id = "a", archivePath = "/A.dance")
        val b = imported().copy(id = "b", archivePath = "/B.dance")
        val mine = SavedSet("mine", "Mine", "s", moveIds = listOf("A"))
        val zip = Zip(catalog, archive)
        val releaseA = CompletableDeferred<Unit>(); val enteredA = CompletableDeferred<Unit>(); val finishedA = CompletableDeferred<Unit>()
        zip.inspect = { path -> if (path == "/A.dance") withContext(NonCancellable) {
            enteredA.complete(Unit); releaseA.await(); finishedA.complete(Unit); catalog
        } else archive }
        val c = component(Disk(listOf(a, b, mine)), zip)
        c.onIntent(ExchangeIntent.EditSavedSet(a.id)); enteredA.await()
        c.onIntent(ExchangeIntent.EditSavedSet(b.id)); await { c.uiState.value.archiveInspectionCatalog == archive }
        releaseA.complete(Unit); finishedA.await(); delay(30)
        assertEquals(b.id, c.uiState.value.editingSetId); assertEquals(archive, c.uiState.value.archiveInspectionCatalog)
        val releaseCatalog = CompletableDeferred<Unit>(); val enteredCatalog = CompletableDeferred<Unit>()
        zip.load = { withContext(NonCancellable) { enteredCatalog.complete(Unit); releaseCatalog.await(); catalog } }
        c.onIntent(ExchangeIntent.EditSavedSet(mine.id)); enteredCatalog.await()
        c.onIntent(ExchangeIntent.EditSavedSet(b.id)); await { c.uiState.value.archiveInspectionCatalog == archive }
        releaseCatalog.complete(Unit); delay(30)
        assertEquals(ExportCatalogSnapshot(), c.uiState.value.exportCatalog)
        c.onIntent(ExchangeIntent.DismissExportDialog)
        assertNull(c.uiState.value.archiveInspectionCatalog); assertFalse(c.uiState.value.archiveInspectionLoading)
        assertNull(c.uiState.value.editingSetId)
    }

    @Test fun mine_style_is_locked_and_all_three_picker_intents_reject_foreign_unknown_ids() = runBlocking {
        val original = SavedSet("mine", "Mine", "s", "Historical label", moveIds = listOf("A"))
        val disk = Disk(listOf(original)); val zip = Zip(catalog, archive); val c = component(disk, zip)
        c.onIntent(ExchangeIntent.EditSavedSet(original.id)); catalogReady(c)
        c.onIntent(ExchangeIntent.SelectExportStyle("b"))
        assertEquals("s", c.uiState.value.exportStyleId); assertEquals("Historical label", c.uiState.value.exportStyleName)
        for ((kind, ids) in listOf(ExportPickKind.Elements to ("A" to "B"), ExportPickKind.Songs to ("song-A" to "song-B"), ExportPickKind.Choreographies to ("ch-A" to "ch-B"))) {
            c.onIntent(ExchangeIntent.OpenExportPick(kind))
            c.onIntent(ExchangeIntent.ToggleExportPickItem(ids.second)); c.onIntent(ExchangeIntent.ToggleExportPickItem("unknown"))
            assertFalse(ids.second in c.uiState.value.exportPickDraftIds)
            assertFalse("unknown" in c.uiState.value.exportPickDraftIds)
            if (ids.first !in c.uiState.value.exportPickDraftIds) c.onIntent(ExchangeIntent.ToggleExportPickItem(ids.first))
            c.onIntent(ExchangeIntent.ConfirmExportPick)
        }
        c.onIntent(ExchangeIntent.SetExportSetName("Edited Mine")); c.onIntent(ExchangeIntent.ConfirmExport)
        await { !c.uiState.value.busy }
        val selected = zip.exports.value.single()!!
        assertEquals(listOf("A"), selected.moveIds); assertEquals(listOf("song-A"), selected.songIds); assertEquals(listOf("ch-A"), selected.choreographyIds)
        assertEquals("Historical label", disk.rows.single().styleName)
        assertEquals("s", disk.rows.single().styleId)
    }

    @Test fun historical_mixed_selection_is_preserved_and_blocks_save_share_until_explicit_removal() = runBlocking {
        val original = SavedSet("mixed", "Mixed", "s", "Sensual", moveIds = listOf("A", "B"), songIds = listOf("song-B"), choreoIds = listOf("ch-B", "deleted"))
        val disk = Disk(listOf(original)); val zip = Zip(catalog, archive); val c = component(disk, zip)
        c.onIntent(ExchangeIntent.EditSavedSet(original.id)); catalogReady(c)
        assertEquals(4, c.uiState.value.invalidExportSelectionCount)
        c.onIntent(ExchangeIntent.OpenExportPick(ExportPickKind.Elements)); c.onIntent(ExchangeIntent.ConfirmExportPick)
        assertEquals(setOf("A", "B"), c.uiState.value.exportSelectedMoveIds)
        c.onIntent(ExchangeIntent.ConfirmExport)
        assertEquals(original, disk.rows.single()); assertEquals(0, disk.writes)
        c.onIntent(ExchangeIntent.ShareSet(original.id)); await { !c.uiState.value.busy }
        assertTrue(zip.exports.value.isEmpty()); assertNull(c.uiState.value.pendingSharePath)
        c.onIntent(ExchangeIntent.RemoveInvalidExportSelection)
        assertEquals(0, c.uiState.value.invalidExportSelectionCount)
        assertEquals(setOf("A"), c.uiState.value.exportSelectedMoveIds)
        assertTrue(c.uiState.value.exportSelectedSongIds.isEmpty() && c.uiState.value.exportSelectedChoreoIds.isEmpty())
        assertEquals(original, disk.rows.single()) // Removal is a draft edit until Save.
        c.onIntent(ExchangeIntent.ConfirmExport); await { !c.uiState.value.busy }
        assertEquals(listOf("A"), disk.rows.single().moveIds); assertEquals(1, zip.exports.value.size)
    }

    @Test fun creation_resumes_only_blank_empty_mine_and_new_draft_does_not_inherit_previous_style() = runBlocking {
        val blank = SavedSet("draft", "Draft", "")
        val locked = SavedSet("emptylocked", "Empty locked", "retired", "Old label")
        val zip = Zip(catalog, archive); val c = component(Disk(listOf(blank, locked)), zip)
        c.onIntent(ExchangeIntent.EditSavedSet(locked.id)); catalogReady(c)
        assertTrue(c.uiState.value.exportStyleLocked); assertEquals("retired", c.uiState.value.exportStyleId)
        assertTrue(c.uiState.value.hasInvalidExportSelection)
        c.onIntent(ExchangeIntent.SelectExportStyle("b")); assertEquals("retired", c.uiState.value.exportStyleId)
        c.onIntent(ExchangeIntent.EditSavedSet(blank.id)); catalogReady(c)
        assertTrue(c.uiState.value.isCreateSetFlow); assertFalse(c.uiState.value.exportStyleLocked)
        c.onIntent(ExchangeIntent.SelectExportStyle("b")); assertEquals("b", c.uiState.value.exportStyleId)
        c.onIntent(ExchangeIntent.DismissExportDialog)
        c.onIntent(ExchangeIntent.ExportStub); catalogReady(c)
        assertEquals("s", c.uiState.value.exportStyleId) // First catalog default, not previous Bachata.
        assertEquals("", c.uiState.value.savedSets.first().styleId)
    }

    @Test fun copied_archive_keeps_payload_after_original_deleted_until_final_reference_is_deleted() {
        val original = imported(); val disk = Disk(listOf(original), mapOf(archivePath to opaquePayload)); val zip = Zip(catalog, archive)
        val c = component(disk, zip)
        c.onIntent(ExchangeIntent.CopySavedSet(original.id))
        val copy = disk.rows.single { it.id != original.id }
        assertEquals(original.origin, copy.origin); assertEquals(original.archivePath, copy.archivePath)
        c.onIntent(ExchangeIntent.DeleteSavedSet(original.id))
        assertContentEquals(opaquePayload, disk.archives.getValue(archivePath))
        c.onIntent(ExchangeIntent.ShareSet(copy.id)); assertEquals(archivePath, c.uiState.value.pendingSharePath)
        c.onIntent(ExchangeIntent.DeleteSavedSet(copy.id)); assertTrue(disk.rows.isEmpty() && disk.archives.isEmpty())
    }
}
