package com.midnightdancer.feature.exchange.logic

import com.midnightdancer.core.domain.exchange.CatalogPickItem
import com.midnightdancer.core.domain.exchange.ExportCatalogSnapshot
import com.midnightdancer.core.domain.exchange.ExportPickKind
import com.midnightdancer.core.domain.exchange.PackageExportLimits
import com.midnightdancer.core.domain.exchange.StyleRef

data class ExchangeUiState(
    val title: String = "Обмен данными",
    val lastImportName: String = "debug_export.mdance",
    val lastImportMb: Float = 0f,
    val lastActionLabel: String? = null,
    val busy: Boolean = false,
    val lastArchivePath: String? = null,
    val freeBytes: Long = 0L,
    val requiredBytes: Long = 0L,
    val requiredWithBufferBytes: Long = 0L,
    val importEnabled: Boolean = false,
    val freeLabel: String = "—",
    val requiredLabel: String = "—",
    /** C-117 import */
    val showImportParams: Boolean = false,
    val importLocalStyles: List<StyleRef> = emptyList(),
    val importPackageStyles: List<StyleRef> = emptyList(),
    /** Single target local style for entire import. */
    val importTargetStyleId: String = "",
    /** C-116 export */
    val showExportStylePicker: Boolean = false,
    val showExportDialog: Boolean = false,
    val exportCatalog: ExportCatalogSnapshot = ExportCatalogSnapshot(),
    val exportStyleId: String = "",
    val exportStyleName: String = "",
    val exportSelectedMoveIds: Set<String> = emptySet(),
    val exportSelectedSongIds: Set<String> = emptySet(),
    val exportSelectedChoreoIds: Set<String> = emptySet(),
    val exportLimitsText: String = PackageExportLimits.HUMAN_LIMITS_RU,
    /** Full-screen section picker overlay. */
    val exportPickKind: ExportPickKind? = null,
    val exportPickDraftIds: Set<String> = emptySet(),
)

sealed interface ExchangeIntent {
    data object ExportStub : ExchangeIntent

    data object DismissExportStylePicker : ExchangeIntent

    data class SelectExportStyle(val styleId: String) : ExchangeIntent

    data object DismissExportDialog : ExchangeIntent

    data object ConfirmExport : ExchangeIntent

    data class OpenExportPick(val kind: ExportPickKind) : ExchangeIntent

    data object DismissExportPick : ExchangeIntent

    data class ToggleExportPickItem(val id: String) : ExchangeIntent

    data object ConfirmExportPick : ExchangeIntent

    data object ImportStub : ExchangeIntent

    data object DismissImportParams : ExchangeIntent

    data class SetImportTargetStyle(val localStyleId: String) : ExchangeIntent

    data object ConfirmImport : ExchangeIntent

    data object RollbackStub : ExchangeIntent

    data object RefreshDiskGate : ExchangeIntent
}

fun ExportCatalogSnapshot.elementRow(id: String): CatalogPickItem? =
    elements.firstOrNull { it.id == id }
