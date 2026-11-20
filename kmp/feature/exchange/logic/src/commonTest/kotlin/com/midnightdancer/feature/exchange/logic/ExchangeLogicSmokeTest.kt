package com.midnightdancer.feature.exchange.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.midnightdancer.core.domain.exchange.CatalogPickItem
import com.midnightdancer.core.domain.exchange.ExportCatalogSnapshot
import com.midnightdancer.core.domain.exchange.ExportPackageSelection
import com.midnightdancer.core.domain.exchange.ImportPeek
import com.midnightdancer.core.domain.exchange.PackageExportLimits
import com.midnightdancer.core.domain.exchange.StyleImportPlan
import com.midnightdancer.core.domain.exchange.StyleRef
import com.midnightdancer.core.domain.usecase.ExchangeZipResult
import com.midnightdancer.core.domain.usecase.ExchangeZipUseCase
import com.midnightdancer.core.domain.usecase.ImportContentFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class ExchangeLogicSmokeTest {

    @Test
    fun default_title_is_sets() {
        val lifecycle = LifecycleRegistry()
        val component = DefaultExchangeComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()
        assertEquals("Сеты", component.uiState.value.title)
        assertEquals(SetHubTab.Mine, component.uiState.value.hubTab)
    }

    @Test
    fun export_opens_package_dialog_directly() {
        val lifecycle = LifecycleRegistry()
        val component = DefaultExchangeComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()
        component.onIntent(ExchangeIntent.ExportStub)
        assertTrue(component.uiState.value.showExportDialog)
        // C-230: draft saved immediately as «новый сет».
        assertTrue(component.uiState.value.savedSets.any { it.name == "новый сет" })
        assertTrue(!component.uiState.value.showExportStylePicker)
        assertEquals(PackageExportLimits.HUMAN_LIMITS_RU, component.uiState.value.exportLimitsText)
    }

    @Test
    fun set_hub_tab_and_filter_search_sort() {
        val lifecycle = LifecycleRegistry()
        val component = DefaultExchangeComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()
        component.onIntent(ExchangeIntent.SelectSetHubTab(SetHubTab.Imported))
        assertEquals(SetHubTab.Imported, component.uiState.value.hubTab)
        component.onIntent(ExchangeIntent.SetSetsSearchQuery("bachata"))
        assertEquals("bachata", component.uiState.value.searchQuery)
        component.onIntent(ExchangeIntent.SetSetsFilter("Sensual"))
        assertEquals("Sensual", component.uiState.value.selectedFilter)
        component.onIntent(ExchangeIntent.SetSetsSortMode(SetsSortMode.Alphabetical))
        assertEquals(SetsSortMode.Alphabetical, component.uiState.value.sortMode)
        component.onIntent(ExchangeIntent.SelectSetHubTab(SetHubTab.Mine))
        assertEquals(SetHubTab.Mine, component.uiState.value.hubTab)
        assertEquals("", component.uiState.value.searchQuery)
        assertEquals("Все теги", component.uiState.value.selectedFilter)
    }

    @Test
    fun weight_gate_blocks_confirm_when_over_300mb() =
        runBlocking {
            val lifecycle = LifecycleRegistry()
            val overweight =
                PackageExportLimits.MAX_PACKAGE_BYTES + 1L
            val fake =
                object : ExchangeZipUseCase {
                    override suspend fun export(
                        selection: ExportPackageSelection?,
                    ): ExchangeZipResult =
                        ExchangeZipResult.Ok(message = "should_not_run")

                    override suspend fun import(
                        preferredPath: String?,
                        contentFilter: ImportContentFilter,
                        stylePlan: StyleImportPlan,
                    ): ExchangeZipResult = ExchangeZipResult.Failed("n/a")

                    override suspend fun peekImport(preferredPath: String?): ImportPeek =
                        ImportPeek(emptyList(), emptyList())

                    override suspend fun loadExportCatalog(): ExportCatalogSnapshot =
                        ExportCatalogSnapshot(
                            styles = listOf(StyleRef("sensual", "Sensual")),
                            elements =
                                listOf(
                                    CatalogPickItem(
                                        id = "heavy",
                                        title = "Heavy",
                                        styleId = "sensual",
                                        approximateBytes = overweight,
                                    ),
                                ),
                        )
                }
            val component =
                DefaultExchangeComponent(
                    componentContext = DefaultComponentContext(lifecycle = lifecycle),
                    exchangeZip = fake,
                )
            lifecycle.resume()
            component.onIntent(ExchangeIntent.ExportStub)
            withTimeout(3_000) {
                while (component.uiState.value.exportCatalog.elements.isEmpty()) delay(20)
            }
            component.onIntent(ExchangeIntent.SelectExportStyle("sensual"))
            component.onIntent(
                ExchangeIntent.OpenExportPick(
                    com.midnightdancer.core.domain.exchange.ExportPickKind.Elements,
                ),
            )
            component.onIntent(ExchangeIntent.ToggleExportPickItem("heavy"))
            component.onIntent(ExchangeIntent.ConfirmExportPick)
            assertTrue(component.uiState.value.exportOverWeight)
            assertTrue(component.uiState.value.exportEstimatedBytes > PackageExportLimits.MAX_PACKAGE_BYTES)
            component.onIntent(ExchangeIntent.ConfirmExport)
            assertTrue(
                component.uiState.value.lastActionLabel?.contains("300") == true ||
                    component.uiState.value.lastActionLabel?.contains("превышает", ignoreCase = true) == true,
            )
            // C-230: draft «новый сет» may exist from ExportStub; overweight confirm must not add another.
            assertTrue(component.uiState.value.savedSets.none { it.moveIds.contains("heavy") })
        }

    @Test
    fun create_set_adds_to_mine_and_copy_delete_share() =
        runBlocking {
            val lifecycle = LifecycleRegistry()
            var exported = 0
            val fake =
                object : ExchangeZipUseCase {
                    override suspend fun export(
                        selection: ExportPackageSelection?,
                    ): ExchangeZipResult {
                        exported++
                        return ExchangeZipResult.Ok(
                            message = "Export OK",
                            archivePath = "/tmp/debug_export.mdance",
                            sizeBytes = 1000L,
                        )
                    }

                    override suspend fun import(
                        preferredPath: String?,
                        contentFilter: ImportContentFilter,
                        stylePlan: StyleImportPlan,
                    ): ExchangeZipResult = ExchangeZipResult.Failed("n/a")

                    override suspend fun peekImport(preferredPath: String?): ImportPeek =
                        ImportPeek(emptyList(), emptyList())

                    override suspend fun loadExportCatalog(): ExportCatalogSnapshot =
                        ExportCatalogSnapshot(
                            styles = listOf(StyleRef("sensual", "Sensual")),
                            songs =
                                listOf(
                                    CatalogPickItem(
                                        id = "s1",
                                        title = "Track",
                                        styleId = "sensual",
                                        approximateBytes = 1024L,
                                    ),
                                ),
                        )
                }
            val component =
                DefaultExchangeComponent(
                    componentContext = DefaultComponentContext(lifecycle = lifecycle),
                    exchangeZip = fake,
                )
            lifecycle.resume()
            component.onIntent(ExchangeIntent.ExportStub)
            withTimeout(3_000) {
                while (component.uiState.value.exportCatalog.songs.isEmpty()) delay(20)
            }
            component.onIntent(ExchangeIntent.SetExportSetName("My First Set"))
            component.onIntent(ExchangeIntent.SelectExportStyle("sensual"))
            component.onIntent(
                ExchangeIntent.OpenExportPick(
                    com.midnightdancer.core.domain.exchange.ExportPickKind.Songs,
                ),
            )
            component.onIntent(ExchangeIntent.ToggleExportPickItem("s1"))
            component.onIntent(ExchangeIntent.ConfirmExportPick)
            assertFalse(component.uiState.value.exportOverWeight)
            component.onIntent(ExchangeIntent.ConfirmExport)
            withTimeout(3_000) {
                while (component.uiState.value.savedSets.isEmpty()) delay(20)
            }
            assertEquals(1, component.uiState.value.savedSets.size)
            assertEquals("My First Set", component.uiState.value.savedSets.first().name)
            assertEquals(SavedSetOrigin.Mine, component.uiState.value.savedSets.first().origin)
            assertEquals(SetHubTab.Mine, component.uiState.value.hubTab)
            withTimeout(3_000) {
                while (exported < 1) delay(20)
            }

            val id = component.uiState.value.savedSets.first().id
            component.onIntent(ExchangeIntent.CopySavedSet(id))
            assertEquals(2, component.uiState.value.savedSets.size)
            assertTrue(component.uiState.value.savedSets.any { it.name.endsWith(" копия") })

            component.onIntent(ExchangeIntent.OpenMySets)
            assertTrue(component.uiState.value.showMySets)
            assertEquals(SetHubTab.Mine, component.uiState.value.hubTab)

            component.onIntent(ExchangeIntent.ShareSet(id))
            withTimeout(3_000) {
                while (component.uiState.value.pendingSharePath == null) delay(20)
            }
            assertNotNull(component.uiState.value.pendingSharePath)
            assertTrue(component.uiState.value.pendingShareFileName.contains(".dance"))
            component.onIntent(ExchangeIntent.ClearPendingShare)
            assertEquals(null, component.uiState.value.pendingSharePath)

            component.onIntent(ExchangeIntent.DeleteSavedSet(id))
            assertEquals(1, component.uiState.value.savedSets.size)
        }

    @Test
    fun share_set_blank_id_opens_picker_when_multiple() =
        runBlocking {
            val lifecycle = LifecycleRegistry()
            val fake =
                object : ExchangeZipUseCase {
                    override suspend fun export(
                        selection: ExportPackageSelection?,
                    ): ExchangeZipResult =
                        ExchangeZipResult.Ok(
                            message = "Export OK",
                            archivePath = "/tmp/debug_export.mdance",
                            sizeBytes = 1000L,
                        )

                    override suspend fun import(
                        preferredPath: String?,
                        contentFilter: ImportContentFilter,
                        stylePlan: StyleImportPlan,
                    ): ExchangeZipResult = ExchangeZipResult.Failed("n/a")

                    override suspend fun peekImport(preferredPath: String?): ImportPeek =
                        ImportPeek(emptyList(), emptyList())

                    override suspend fun loadExportCatalog(): ExportCatalogSnapshot =
                        ExportCatalogSnapshot(
                            styles = listOf(StyleRef("sensual", "Sensual")),
                            songs =
                                listOf(
                                    CatalogPickItem(
                                        id = "s1",
                                        title = "Track",
                                        styleId = "sensual",
                                        approximateBytes = 1024L,
                                    ),
                                ),
                        )
                }
            val component =
                DefaultExchangeComponent(
                    componentContext = DefaultComponentContext(lifecycle = lifecycle),
                    exchangeZip = fake,
                )
            lifecycle.resume()
            // Create two sets via export
            repeat(2) { idx ->
                withTimeout(3_000) {
                    while (component.uiState.value.busy) delay(20)
                }
                component.onIntent(ExchangeIntent.ExportStub)
                withTimeout(3_000) {
                    while (component.uiState.value.exportCatalog.songs.isEmpty()) delay(20)
                }
                component.onIntent(ExchangeIntent.SetExportSetName("Set $idx"))
                component.onIntent(ExchangeIntent.SelectExportStyle("sensual"))
                component.onIntent(
                    ExchangeIntent.OpenExportPick(
                        com.midnightdancer.core.domain.exchange.ExportPickKind.Songs,
                    ),
                )
                component.onIntent(ExchangeIntent.ToggleExportPickItem("s1"))
                component.onIntent(ExchangeIntent.ConfirmExportPick)
                component.onIntent(ExchangeIntent.ConfirmExport)
                withTimeout(3_000) {
                    while (component.uiState.value.savedSets.size < idx + 1) delay(20)
                }
                withTimeout(3_000) {
                    while (component.uiState.value.busy) delay(20)
                }
            }
            assertTrue(component.uiState.value.savedSets.size >= 2)
            component.onIntent(ExchangeIntent.ShareSet(""))
            assertTrue(component.uiState.value.showShareSetPicker)
            component.onIntent(ExchangeIntent.DismissShareSetPicker)
            assertFalse(component.uiState.value.showShareSetPicker)
        }

    @Test
    fun intent_withFakeUseCase_export_and_import() =
        runBlocking {
            val lifecycle = LifecycleRegistry()
            val fake =
                object : ExchangeZipUseCase {
                    override suspend fun export(
                        selection: ExportPackageSelection?,
                    ): ExchangeZipResult =
                        ExchangeZipResult.Ok(
                            message = "Export OK",
                            archivePath = "/tmp/debug_export.mdance",
                            sizeBytes = 100_000L,
                        )

                    override suspend fun import(
                        preferredPath: String?,
                        contentFilter: ImportContentFilter,
                        stylePlan: StyleImportPlan,
                    ): ExchangeZipResult =
                        ExchangeZipResult.Failed(
                            message = "Import rejected",
                            reasons = listOf("cannot_list_archive: invalid"),
                        )

                    override suspend fun peekImport(preferredPath: String?): ImportPeek =
                        ImportPeek(
                            packageStyles = listOf(StyleRef("p1", "Pack")),
                            localStyles = listOf(StyleRef("sensual", "Sensual")),
                        )

                    override suspend fun loadExportCatalog(): ExportCatalogSnapshot =
                        ExportCatalogSnapshot(
                            styles =
                                listOf(
                                    StyleRef("sensual", "Sensual"),
                                    StyleRef("bachata", "Bachata"),
                                    StyleRef("kizomba", "Kizomba"),
                                ),
                        )
                }
            val component =
                DefaultExchangeComponent(
                    componentContext = DefaultComponentContext(lifecycle = lifecycle),
                    exchangeZip = fake,
                )
            lifecycle.resume()

            component.onIntent(ExchangeIntent.ExportStub)
            withTimeout(3_000) {
                while (!component.uiState.value.showExportDialog) delay(20)
            }
            assertTrue(component.uiState.value.showExportDialog)
            withTimeout(3_000) {
                while (component.uiState.value.exportCatalog.styles.isEmpty()) delay(20)
            }
            assertTrue(component.uiState.value.exportCatalog.styles.size >= 2)

            component.onIntent(ExchangeIntent.SelectExportStyle("bachata"))
            assertEquals("bachata", component.uiState.value.exportStyleId)

            component.onIntent(ExchangeIntent.DismissExportDialog)
            component.onIntent(ExchangeIntent.ImportStub)
            withTimeout(3_000) {
                while (!component.uiState.value.showImportParams) delay(20)
            }
            assertEquals(SetHubTab.Imported, component.uiState.value.hubTab)
            component.onIntent(ExchangeIntent.ConfirmImport)
            withTimeout(3_000) {
                while (component.uiState.value.lastActionLabel?.contains("Import rejected") != true) {
                    delay(20)
                }
            }
            assertEquals(
                "Не удалось импортировать сет. Проверьте выбранный файл и доступ к нему.",
                component.uiState.value.operationError,
            )
            assertEquals(false, component.uiState.value.busy)
            assertEquals(null, component.uiState.value.pendingSharePath)
        }

    @Test
    fun share_empty_set_sets_c289_warn_and_no_pending_share() =
        runBlocking {
            val lifecycle = LifecycleRegistry()
            val component = DefaultExchangeComponent(DefaultComponentContext(lifecycle = lifecycle))
            lifecycle.resume()
            component.onIntent(ExchangeIntent.ExportStub)
            withTimeout(3_000) {
                while (component.uiState.value.savedSets.isEmpty()) delay(20)
            }
            val id = component.uiState.value.savedSets.first().id
            assertTrue(component.uiState.value.savedSets.first().moveIds.isEmpty())
            component.onIntent(ExchangeIntent.DismissExportDialog)
            component.onIntent(ExchangeIntent.ShareSet(id))
            assertEquals(EMPTY_SET_SHARE_WARN_RU, component.uiState.value.lastActionLabel)
            assertEquals(null, component.uiState.value.pendingSharePath)
        }
}
