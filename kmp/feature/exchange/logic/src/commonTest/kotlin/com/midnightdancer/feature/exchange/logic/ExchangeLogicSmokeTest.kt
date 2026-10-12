package com.midnightdancer.feature.exchange.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.midnightdancer.core.domain.exchange.ExportCatalogSnapshot
import com.midnightdancer.core.domain.exchange.ExportPackageSelection
import com.midnightdancer.core.domain.exchange.ImportPeek
import com.midnightdancer.core.domain.exchange.StyleImportPlan
import com.midnightdancer.core.domain.exchange.StyleRef
import com.midnightdancer.core.domain.usecase.ExchangeZipResult
import com.midnightdancer.core.domain.usecase.ExchangeZipUseCase
import com.midnightdancer.core.domain.usecase.ImportContentFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class ExchangeLogicSmokeTest {

    @Test
    fun export_opens_package_dialog_directly() {
        val lifecycle = LifecycleRegistry()
        val component = DefaultExchangeComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()
        component.onIntent(ExchangeIntent.ExportStub)
        assertTrue(component.uiState.value.showExportDialog)
        assertTrue(!component.uiState.value.showExportStylePicker)
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
            component.onIntent(ExchangeIntent.ConfirmImport)
            withTimeout(3_000) {
                while (component.uiState.value.lastActionLabel?.contains("Import rejected") != true) {
                    delay(20)
                }
            }
        }
}
