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

class CreateFeedbackTest {
    private val lifecycles = mutableListOf<LifecycleRegistry>()
    private val originals = (1..5).map { SavedSet("keep-$it", "Keep $it", "s", "Sensual",
        origin = SavedSetOrigin.Imported, archivePath = "/owned/keep-$it.dance", tags = listOf("custom-$it")) }
    private val normal = ExportCatalogSnapshot(styles = listOf(StyleRef("s", "Sensual"), StyleRef("b", "Bachata")),
        songs = listOf(CatalogPickItem("song", "Song", styleId = "s", approximateBytes = 1024),
            CatalogPickItem("foreign", "Foreign", styleId = "b")))
    private class Disk(initial: List<SavedSet>) : SavedSetsPersister {
        var rows = initial.toList()
        var writes = 0
        var fail = false
        override fun read() = rows
        override fun write(sets: List<SavedSet>) { check(!fail) { "synthetic disk failure" }; rows = sets.toList(); writes++ }
        override fun retainArchive(sourcePath: String, setId: String): String = error("Create must not touch old archives")
    }
    private class Zip(private val catalog: ExportCatalogSnapshot) : ExchangeZipUseCase {
        var load: suspend () -> ExportCatalogSnapshot = { catalog }
        var pack: suspend () -> ExchangeZipResult = { ExchangeZipResult.Ok("OK", "/export/new.dance", 1) }
        val exports = MutableStateFlow<List<ExportPackageSelection?>>(emptyList())
        override suspend fun loadExportCatalog() = load()
        override suspend fun export(selection: ExportPackageSelection?): ExchangeZipResult { exports.update { it + selection }; return pack() }
        override suspend fun import(preferredPath: String?, contentFilter: ImportContentFilter, stylePlan: StyleImportPlan): ExchangeZipResult = error("No import during create")
        override suspend fun peekImport(preferredPath: String?): ImportPeek? = error("No import during create")
    }
    @BeforeTest fun reset() { SavedSetsStore.replaceAll(emptyList()) }
    @AfterTest fun cleanup() { lifecycles.forEach { it.destroy() }; lifecycles.clear(); SavedSetsStore.replaceAll(emptyList()) }
    private fun component(disk: Disk, zip: Zip): DefaultExchangeComponent {
        val life = LifecycleRegistry().also { it.resume(); lifecycles += it }
        return DefaultExchangeComponent(DefaultComponentContext(life), exchangeZip = zip, savedSetsPersister = disk)
    }
    private suspend fun await(predicate: () -> Boolean) { withTimeout(5000) { while (!predicate()) delay(5) } }
    private suspend fun open(c: DefaultExchangeComponent) { c.onIntent(ExchangeIntent.ExportStub); await { c.uiState.value.exportCatalogLoaded } }
    private fun pickSong(c: DefaultExchangeComponent) {
        c.onIntent(ExchangeIntent.OpenExportPick(ExportPickKind.Songs))
        c.onIntent(ExchangeIntent.ToggleExportPickItem("song")); c.onIntent(ExchangeIntent.ConfirmExportPick)
    }
    private fun assertOriginals(disk: Disk) { assertEquals(originals, disk.rows.filter { it.id.startsWith("keep-") }) }

    @Test
    fun local_set_can_use_catalog_without_zip_and_preserves_other_styles_and_archives() = runBlocking {
        val disk = Disk(originals)
        val life = LifecycleRegistry().also { it.resume(); lifecycles += it }
        val component = DefaultExchangeComponent(
            DefaultComponentContext(life),
            savedSetsPersister = disk,
            localSetCatalog = LocalSetCatalogLoader { normal },
        )
        open(component)
        component.onIntent(ExchangeIntent.OpenExportPick(ExportPickKind.Songs))
        component.onIntent(ExchangeIntent.ToggleExportPickItem("foreign"))
        component.onIntent(ExchangeIntent.ToggleExportPickItem("song"))
        component.onIntent(ExchangeIntent.ConfirmExportPick)
        component.onIntent(ExchangeIntent.SetExportSetName("Local set"))
        component.onIntent(ExchangeIntent.ConfirmLocalSet)

        val saved = disk.rows.single { it.origin == SavedSetOrigin.Mine }
        assertEquals("Local set", saved.name)
        assertEquals(listOf("song"), saved.songIds)
        assertFalse(component.uiState.value.showExportDialog)
        assertNull(component.uiState.value.operationError)
        assertOriginals(disk)

        component.onIntent(ExchangeIntent.EditSavedSet(saved.id))
        await { component.uiState.value.exportCatalogLoaded }
        component.onIntent(ExchangeIntent.SelectExportStyle("b"))
        assertEquals("s", component.uiState.value.exportStyleId)
    }

    @Test fun empty_create_and_repeated_identical_click_keep_visible_inline_error_without_extra_writes_or_exports() = runBlocking {
        val disk = Disk(originals); val zip = Zip(normal); val c = component(disk, zip); open(c)
        val draft = disk.rows.first(); val writes = disk.writes
        repeat(2) {
            c.onIntent(ExchangeIntent.ConfirmExport)
            assertEquals("Выберите хотя бы один элемент, трек или хореографию", c.uiState.value.exportValidationError)
            assertNull(c.uiState.value.operationError)
            assertTrue(c.uiState.value.showExportDialog)
            assertEquals(writes, disk.writes); assertEquals(draft, disk.rows.first())
        }
        c.onIntent(ExchangeIntent.SetExportSetName(c.uiState.value.exportSetName))
        assertNotNull(c.uiState.value.exportValidationError) // Replaying unchanged input does not hide the explanation.
        c.onIntent(ExchangeIntent.SetExportSetName("New name")); assertNull(c.uiState.value.exportValidationError)
        c.onIntent(ExchangeIntent.ConfirmExport); assertNotNull(c.uiState.value.exportValidationError)
        pickSong(c); assertNull(c.uiState.value.exportValidationError)
        assertTrue(zip.exports.value.isEmpty()); assertOriginals(disk)
    }

    @Test fun valid_create_updates_exactly_its_draft_and_duplicate_hub_or_confirm_does_not_write_or_export_again() = runBlocking {
        val disk = Disk(originals); val zip = Zip(normal); val release = CompletableDeferred<Unit>()
        zip.pack = { release.await(); ExchangeZipResult.Ok("OK", "/export/new.dance", 1) }
        val c = component(disk, zip); open(c)
        val draftId = disk.rows.first().id; val initialWrites = disk.writes
        c.onIntent(ExchangeIntent.ExportStub)
        assertEquals(6, disk.rows.size); assertEquals(initialWrites, disk.writes); assertEquals(draftId, c.uiState.value.editingSetId)
        c.onIntent(ExchangeIntent.SetExportSetName("Valid new set")); pickSong(c)
        c.onIntent(ExchangeIntent.ConfirmExport)
        await { zip.exports.value.size == 1 }
        assertTrue(c.uiState.value.busy); assertFalse(c.uiState.value.showExportDialog); assertFalse(c.uiState.value.isCreateSetFlow)
        val saved = disk.rows; val writes = disk.writes
        c.onIntent(ExchangeIntent.ConfirmExport); c.onIntent(ExchangeIntent.ExportStub)
        assertEquals(saved, disk.rows); assertEquals(writes, disk.writes)
        release.complete(Unit); await { !c.uiState.value.busy }
        c.onIntent(ExchangeIntent.ConfirmExport)
        assertEquals(writes, disk.writes); assertEquals(1, zip.exports.value.size)
        val created = disk.rows.single { it.id == draftId }
        assertEquals("Valid new set", created.name); assertEquals("s", created.styleId); assertEquals(listOf("song"), created.songIds)
        assertEquals(listOf("song"), zip.exports.value.single()!!.songIds); assertOriginals(disk)
    }

    @Test fun hub_and_dialog_persistence_failures_are_visible_in_correct_channel_and_never_change_old_cards() = runBlocking {
        val disk = Disk(originals).also { it.fail = true }; val zip = Zip(normal); val c = component(disk, zip)
        c.onIntent(ExchangeIntent.ExportStub)
        assertFalse(c.uiState.value.showExportDialog); assertTrue(c.uiState.value.operationError.orEmpty().contains("Не удалось сохранить"))
        assertEquals(originals, disk.rows); assertEquals(0, disk.writes)
        disk.fail = false; open(c); assertNull(c.uiState.value.operationError)
        pickSong(c); c.onIntent(ExchangeIntent.SetExportSetName("Unsaved name"))
        val before = disk.rows; disk.fail = true
        repeat(2) {
            c.onIntent(ExchangeIntent.ConfirmExport)
            assertTrue(c.uiState.value.exportValidationError.orEmpty().contains("Не удалось сохранить"))
            assertNull(c.uiState.value.operationError); assertTrue(c.uiState.value.showExportDialog)
            assertEquals(before, disk.rows); assertTrue(zip.exports.value.isEmpty())
        }
        disk.fail = false; c.onIntent(ExchangeIntent.ConfirmExport); await { !c.uiState.value.busy }
        assertEquals("Unsaved name", disk.rows.first().name); assertNull(c.uiState.value.exportValidationError); assertOriginals(disk)
    }

    @Test fun delayed_failed_and_blank_style_catalogs_have_specific_visible_reasons_without_persist_or_export() = runBlocking {
        val disk = Disk(originals); val zip = Zip(normal); val gate = CompletableDeferred<ExportCatalogSnapshot>()
        zip.load = { gate.await() }; val c = component(disk, zip)
        c.onIntent(ExchangeIntent.ExportStub)
        assertTrue(c.uiState.value.exportCatalogLoading)
        c.onIntent(ExchangeIntent.ConfirmExport)
        assertEquals("Материалы ещё загружаются. Подождите", c.uiState.value.exportValidationError)
        gate.completeExceptionally(IllegalStateException("synthetic load failure"))
        await { !c.uiState.value.exportCatalogLoading }
        c.onIntent(ExchangeIntent.ConfirmExport)
        assertTrue(c.uiState.value.exportValidationError.orEmpty().contains("Не удалось загрузить"))
        assertNull(c.uiState.value.operationError); assertTrue(zip.exports.value.isEmpty()); assertEquals(1, disk.writes)
        val blankDisk = Disk(originals); val blankZip = Zip(ExportCatalogSnapshot()); val blank = component(blankDisk, blankZip)
        open(blank); blank.onIntent(ExchangeIntent.ConfirmExport)
        assertEquals("Сначала выберите стиль", blank.uiState.value.exportValidationError)
        assertEquals(1, blankDisk.writes); assertTrue(blankZip.exports.value.isEmpty()); assertOriginals(disk); assertOriginals(blankDisk)
    }

    @Test fun overweight_and_mixed_selection_show_inline_reason_and_preserve_data_until_explicit_correction() = runBlocking {
        val heavy = normal.copy(songs = listOf(normal.songs.first().copy(approximateBytes = PackageExportLimits.MAX_PACKAGE_BYTES + 1)))
        val disk = Disk(originals); val zip = Zip(heavy); val c = component(disk, zip); open(c); pickSong(c)
        val before = disk.rows; val writes = disk.writes
        c.onIntent(ExchangeIntent.ConfirmExport)
        assertTrue(c.uiState.value.exportValidationError.orEmpty().contains("300")); assertNull(c.uiState.value.operationError)
        assertEquals(before, disk.rows); assertEquals(writes, disk.writes); assertTrue(zip.exports.value.isEmpty())
        val mixed = SavedSet("mixed", "Mixed", "s", "Sensual", songIds = listOf("song", "foreign"))
        val mixedDisk = Disk(originals + mixed); val mixedZip = Zip(normal); val m = component(mixedDisk, mixedZip)
        m.onIntent(ExchangeIntent.EditSavedSet(mixed.id)); await { m.uiState.value.exportCatalogLoaded }
        m.onIntent(ExchangeIntent.ConfirmExport)
        assertTrue(m.uiState.value.exportValidationError.orEmpty().contains("недоступные материалы"))
        assertEquals(mixed, mixedDisk.rows.last()); assertEquals(0, mixedDisk.writes)
        m.onIntent(ExchangeIntent.RemoveInvalidExportSelection); assertNull(m.uiState.value.exportValidationError)
        assertEquals(mixed, mixedDisk.rows.last()); m.onIntent(ExchangeIntent.ConfirmExport); await { !m.uiState.value.busy }
        assertEquals(listOf("song"), mixedDisk.rows.last().songIds); assertEquals(1, mixedZip.exports.value.size); assertOriginals(mixedDisk)
    }

    @Test fun busy_active_editor_cannot_persist_new_changes_before_export_gate() = runBlocking {
        val mine = SavedSet("mine", "Mine", "s", "Sensual", songIds = listOf("song"))
        val disk = Disk(originals + mine); val zip = Zip(normal); val release = CompletableDeferred<Unit>()
        zip.pack = { release.await(); ExchangeZipResult.Ok("OK", "/export/shared.dance", 1) }
        val c = component(disk, zip); c.onIntent(ExchangeIntent.EditSavedSet(mine.id)); await { c.uiState.value.exportCatalogLoaded }
        c.onIntent(ExchangeIntent.ShareSet(mine.id)); await { zip.exports.value.size == 1 }
        c.onIntent(ExchangeIntent.SetExportSetName("Must not save while busy")); c.onIntent(ExchangeIntent.ConfirmExport)
        assertEquals("Подождите — идёт операция с сетом", c.uiState.value.exportValidationError)
        assertEquals(0, disk.writes); assertEquals(mine, disk.rows.last())
        release.complete(Unit); await { !c.uiState.value.busy }; assertOriginals(disk)
    }
}
