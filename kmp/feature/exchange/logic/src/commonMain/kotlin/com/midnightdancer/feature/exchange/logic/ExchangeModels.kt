package com.midnightdancer.feature.exchange.logic

import com.midnightdancer.core.domain.exchange.CatalogPickItem
import com.midnightdancer.core.domain.exchange.ExportCatalogSnapshot
import com.midnightdancer.core.domain.exchange.ExportPickKind
import com.midnightdancer.core.domain.exchange.PackageExportLimits
import com.midnightdancer.core.domain.exchange.StyleRef

/** C-289: bottom banner when Share on empty set. */
const val EMPTY_SET_SHARE_WARN_RU: String = "Сначала загрузите элементы в сет"

/** Hub top toggle: Мои | Добавленные (C-227). */
enum class SetHubTab {
    Mine,
    Imported,
}

/** Where a saved set came from. */
enum class SavedSetOrigin {
    Mine,
    Imported,
}

enum class SetsSortMode {
    ByStyle,
    Alphabetical,
}

/** Saved set card and selection; imported archives belong to this card, never to global UI state. */
data class SavedSet(
    val id: String,
    val name: String,
    val styleId: String,
    val styleName: String = "",
    val moveIds: List<String> = emptyList(),
    val songIds: List<String> = emptyList(),
    val choreoIds: List<String> = emptyList(),
    val estimatedBytes: Long = 0L,
    val updatedAt: Long = 0L,
    val origin: SavedSetOrigin = SavedSetOrigin.Mine,
    val tags: List<String> = emptyList(),
    val archivePath: String? = null,
) {
    /** Imported cards without a reconstructible selection own an opaque archive, even if its path is missing. */
    val isArchiveOnlyImported: Boolean
        get() = origin == SavedSetOrigin.Imported && moveIds.isEmpty() && songIds.isEmpty() && choreoIds.isEmpty()
}

data class ExchangeUiState(
    val title: String = "Сеты",
    val lastImportName: String = "debug_export.dance",
    val lastImportMb: Float = 0f,
    val lastActionLabel: String? = null,
    /** User-visible failure, separate from diagnostic/success labels. */
    val operationError: String? = null,
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
    /** SAF content URI of picked .mdance package. */
    val importPreferredPath: String? = null,
    val importDisplayName: String = "",
    /** C-116 / C-219 export */
    val showExportStylePicker: Boolean = false,
    val showExportDialog: Boolean = false,
    val exportCatalog: ExportCatalogSnapshot = ExportCatalogSnapshot(),
    val exportCatalogLoaded: Boolean = false,
    val exportCatalogLoading: Boolean = false,
    /** Persistent inline explanation for create/edit validation, including repeated identical clicks. */
    val exportValidationError: String? = null,
    val archiveInspectionLoading: Boolean = false,
    val archiveInspectionError: String? = null,
    val archiveInspectionCatalog: ExportCatalogSnapshot? = null,
    val exportStyleId: String = "",
    val exportStyleName: String = "",
    val exportSelectedMoveIds: Set<String> = emptySet(),
    val exportSelectedSongIds: Set<String> = emptySet(),
    val exportSelectedChoreoIds: Set<String> = emptySet(),
    val exportLimitsText: String = PackageExportLimits.HUMAN_LIMITS_RU,
    /** Estimated weight of current selection (C-219). */
    val exportEstimatedBytes: Long = 0L,
    val exportWeightMb: Float = 0f,
    /** Full-screen section picker overlay. */
    val exportPickKind: ExportPickKind? = null,
    val exportPickDraftIds: Set<String> = emptySet(),
    /** C-227 Sets hub */
    val hubTab: SetHubTab = SetHubTab.Mine,
    val savedSets: List<SavedSet> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: String = "Все теги",
    val sortMode: SetsSortMode = SetsSortMode.ByStyle,
    /** Non-null while editing an existing set in the export/edit dialog. */
    val editingSetId: String? = null,
    /** C-288: true after «Создать сет» CTA — dialog title «Создать сет» even though draft id exists. */
    val isCreateSetFlow: Boolean = false,
    val exportSetName: String = "",
    /** After dismiss of edit dialog stay on hub (legacy flag kept for flow). */
    val returnToMySets: Boolean = false,
    /** @deprecated C-227 hub is always visible; kept for smoke-test compat. */
    val showMySets: Boolean = true,
    /** Android UI: path to .mdance for ACTION_SEND chooser. */
    val pendingSharePath: String? = null,
    val pendingShareFileName: String = "",
    /** Share without a specific card — pick a set first. */
    val showShareSetPicker: Boolean = false,
) {
    val exportOverWeight: Boolean
        get() = exportEstimatedBytes > PackageExportLimits.MAX_PACKAGE_BYTES

    val isEditingSet: Boolean
        get() = editingSetId != null && !isCreateSetFlow

    /** Legacy archive-only discriminator; Imported viewer now covers every Imported card. */
    val editingArchiveOnly: Boolean
        get() = isEditingSet && savedSets.firstOrNull { it.id == editingSetId }?.isArchiveOnlyImported == true

    val viewingImportedSet: Boolean
        get() = savedSets.firstOrNull { it.id == editingSetId }?.origin == SavedSetOrigin.Imported

    val exportStyleLocked: Boolean
        get() = editingSetId != null && !isCreateSetFlow

    fun allowedExportRows(kind: ExportPickKind): List<CatalogPickItem> =
        exportCatalog.rowsForStyle(kind, exportStyleId)

    val invalidExportSelectionCount: Int
        get() {
            if (viewingImportedSet || !exportCatalogLoaded) return 0
            val count = (exportSelectedMoveIds - allowedExportRows(ExportPickKind.Elements).map { it.id }.toSet()).size +
                (exportSelectedSongIds - allowedExportRows(ExportPickKind.Songs).map { it.id }.toSet()).size +
                (exportSelectedChoreoIds - allowedExportRows(ExportPickKind.Choreographies).map { it.id }.toSet()).size
            return if (exportCatalog.canonicalStyleId(exportStyleId) == null)
                count.coerceAtLeast(1) else count
        }

    val hasInvalidExportSelection: Boolean
        get() = invalidExportSelectionCount > 0

    val tagCatalog: List<String>
        get() =
            savedSets
                .filter { it.origin == hubTab.toOrigin() }
                .map { it.styleName.ifBlank { it.styleId } }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedBy { it.lowercase() }

    val visibleSets: List<SavedSet>
        get() {
            val origin = hubTab.toOrigin()
            var list = savedSets.filter { it.origin == origin }
            val filter = selectedFilter.trim()
            if (
                filter.isNotEmpty() &&
                !filter.equals("Все теги", ignoreCase = true) &&
                !filter.equals("Все", ignoreCase = true)
            ) {
                list =
                    list.filter {
                        it.styleName.equals(filter, ignoreCase = true) ||
                            it.styleId.equals(filter, ignoreCase = true) ||
                            it.tags.any { t -> t.equals(filter, ignoreCase = true) }
                    }
            }
            val q = searchQuery.trim()
            if (q.isNotEmpty()) {
                list =
                    list.filter {
                        it.name.contains(q, ignoreCase = true) ||
                            it.styleName.contains(q, ignoreCase = true)
                    }
            }
            return when (sortMode) {
                SetsSortMode.ByStyle ->
                    list.sortedWith(
                        compareBy(
                            { it.styleName.ifBlank { it.styleId }.lowercase() },
                            { it.name.lowercase() },
                        ),
                    )
                SetsSortMode.Alphabetical -> list.sortedBy { it.name.lowercase() }
            }
        }
}

fun SetHubTab.toOrigin(): SavedSetOrigin =
    when (this) {
        SetHubTab.Mine -> SavedSetOrigin.Mine
        SetHubTab.Imported -> SavedSetOrigin.Imported
    }

sealed interface ExchangeIntent {
    data object RetryArchiveInspection : ExchangeIntent
    data object RemoveInvalidExportSelection : ExchangeIntent
    data object ExportStub : ExchangeIntent

    data object DismissExportStylePicker : ExchangeIntent

    data class SelectExportStyle(val styleId: String) : ExchangeIntent

    data object DismissExportDialog : ExchangeIntent

    data object ConfirmExport : ExchangeIntent
    data object ConfirmLocalSet : ExchangeIntent

    data class OpenExportPick(val kind: ExportPickKind) : ExchangeIntent

    data object DismissExportPick : ExchangeIntent

    data class ToggleExportPickItem(val id: String) : ExchangeIntent

    data object ConfirmExportPick : ExchangeIntent

    data object ImportStub : ExchangeIntent

    /** User picked a package via SAF — open import params after peek. */
    data class ImportPackagePicked(val contentUri: String, val displayName: String) : ExchangeIntent

    data object DismissImportParams : ExchangeIntent

    data object CreateImportTargetStyle : ExchangeIntent

    data class SetImportTargetStyle(val localStyleId: String) : ExchangeIntent

    data object ConfirmImport : ExchangeIntent

    data object RollbackStub : ExchangeIntent

    data object RefreshDiskGate : ExchangeIntent

    /** C-227 hub tab toggle. */
    data class SelectSetHubTab(val tab: SetHubTab) : ExchangeIntent

    data class SetSetsSearchQuery(val query: String) : ExchangeIntent

    data class SetSetsFilter(val tag: String?) : ExchangeIntent

    data class SetSetsSortMode(val mode: SetsSortMode) : ExchangeIntent

    /**
     * Share a set via Android system sheet.
     * Blank [id] → share the only set or open picker when several exist.
     */
    data class ShareSet(val id: String = "") : ExchangeIntent

    data object DismissShareSetPicker : ExchangeIntent

    data object ClearPendingShare : ExchangeIntent

    /** @deprecated C-227 — hub always shows lists; kept for smoke tests. */
    data object OpenMySets : ExchangeIntent

    data object DismissMySets : ExchangeIntent

    data class EditSavedSet(val id: String) : ExchangeIntent

    data class CopySavedSet(val id: String) : ExchangeIntent

    data class DeleteSavedSet(val id: String) : ExchangeIntent

    data class SetExportSetName(val name: String) : ExchangeIntent
}

fun ExportCatalogSnapshot.elementRow(id: String): CatalogPickItem? =
    elements.firstOrNull { it.id == id }

/** Sum approximateBytes for selected move/song/choreo ids (draft-aware). */
fun ExchangeUiState.computeExportEstimatedBytes(): Long {
    val moveIds =
        if (exportPickKind == ExportPickKind.Elements) {
            exportPickDraftIds
        } else {
            exportSelectedMoveIds
        }
    val songIds =
        if (exportPickKind == ExportPickKind.Songs) {
            exportPickDraftIds
        } else {
            exportSelectedSongIds
        }
    val choreoIds =
        if (exportPickKind == ExportPickKind.Choreographies) {
            exportPickDraftIds
        } else {
            exportSelectedChoreoIds
        }
    val catalog = exportCatalog
    var total = 0L
    // Fallback when file size is unknown (content URI / missing path) so «Вес сета» still moves.
    val fallbackElement = 8L * 1024L * 1024L
    val fallbackSong = 4L * 1024L * 1024L
    val fallbackChoreo = 15L * 1024L * 1024L
    for (id in moveIds) {
        val known = catalog.elements.firstOrNull { it.id == id }?.approximateBytes ?: 0L
        total += if (known > 0L) known else fallbackElement
    }
    for (id in songIds) {
        val known = catalog.songs.firstOrNull { it.id == id }?.approximateBytes ?: 0L
        total += if (known > 0L) known else fallbackSong
    }
    for (id in choreoIds) {
        val known = catalog.choreographies.firstOrNull { it.id == id }?.approximateBytes ?: 0L
        total += if (known > 0L) known else fallbackChoreo
    }
    return total.coerceAtLeast(0L)
}

fun ExchangeUiState.withExportWeight(): ExchangeUiState {
    val bytes = if (editingArchiveOnly) {
        savedSets.firstOrNull { it.id == editingSetId }?.estimatedBytes?.coerceAtLeast(0L) ?: 0L
    } else computeExportEstimatedBytes()
    return copy(
        exportEstimatedBytes = bytes,
        exportWeightMb = bytes / (1024f * 1024f),
    )
}

/** One fail-closed scope shared by picker display, direct intents, Save and Share. */
internal fun ExportCatalogSnapshot.rowsForStyle(kind: ExportPickKind, styleId: String): List<CatalogPickItem> {
    return when (kind) {
        ExportPickKind.Elements -> elementsForStyle(styleId)
        ExportPickKind.Songs -> songsForStyle(styleId)
        ExportPickKind.Choreographies -> choreosForStyle(styleId)
    }
}
