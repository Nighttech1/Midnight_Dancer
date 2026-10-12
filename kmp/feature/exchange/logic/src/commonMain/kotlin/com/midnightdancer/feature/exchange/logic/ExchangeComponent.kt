package com.midnightdancer.feature.exchange.logic

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.midnightdancer.core.domain.exchange.ExportPackageSelection
import com.midnightdancer.core.domain.exchange.ExportPickKind
import com.midnightdancer.core.domain.exchange.PackageExportLimits
import com.midnightdancer.core.domain.exchange.StyleImportPlan
import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.storage.DiskSpaceGate
import com.midnightdancer.core.domain.usecase.ExchangeZipResult
import com.midnightdancer.core.domain.usecase.ExchangeZipUseCase
import com.midnightdancer.core.domain.usecase.ImportContentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

interface ExchangeComponent {
    val uiState: StateFlow<ExchangeUiState>
    fun onIntent(intent: ExchangeIntent)

    /** SKIE-friendly aliases for SwiftUI bridge (C-124 / C-127). */
    fun openExport() = onIntent(ExchangeIntent.ExportStub)
    fun dismissExport() = onIntent(ExchangeIntent.DismissExportDialog)
    fun selectExportStyle(styleId: String) = onIntent(ExchangeIntent.SelectExportStyle(styleId))
    fun confirmExport() = onIntent(ExchangeIntent.ConfirmExport)
    fun openImport() = onIntent(ExchangeIntent.ImportStub)
    fun dismissImport() = onIntent(ExchangeIntent.DismissImportParams)
    fun setImportTargetStyle(localStyleId: String) =
        onIntent(ExchangeIntent.SetImportTargetStyle(localStyleId))
    fun confirmImport() = onIntent(ExchangeIntent.ConfirmImport)
    fun refreshDiskGate() = onIntent(ExchangeIntent.RefreshDiskGate)
}

class DefaultExchangeComponent(
    componentContext: ComponentContext,
    private val exchangeZip: ExchangeZipUseCase? = null,
    private val deviceStorage: DeviceStorage? = null,
) : ExchangeComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(ExchangeUiState())
    override val uiState: StateFlow<ExchangeUiState> = _uiState.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        performRefreshDiskGate()
    }

    override fun onIntent(intent: ExchangeIntent) {
        when (intent) {
            ExchangeIntent.RefreshDiskGate -> performRefreshDiskGate()
            ExchangeIntent.ImportStub -> openImportDialog()
            ExchangeIntent.DismissImportParams ->
                _uiState.update { it.copy(showImportParams = false) }
            is ExchangeIntent.SetImportTargetStyle ->
                _uiState.update { it.copy(importTargetStyleId = intent.localStyleId) }
            ExchangeIntent.ConfirmImport -> performConfirmImport()
            ExchangeIntent.ExportStub -> openExportDialog()
            ExchangeIntent.DismissExportStylePicker ->
                _uiState.update { it.copy(showExportStylePicker = false) }
            is ExchangeIntent.SelectExportStyle -> performSelectExportStyle(intent.styleId)
            ExchangeIntent.DismissExportDialog ->
                _uiState.update {
                    it.copy(
                        showExportDialog = false,
                        exportPickKind = null,
                    )
                }
            ExchangeIntent.ConfirmExport -> performConfirmExport()
            is ExchangeIntent.OpenExportPick -> openExportPick(intent.kind)
            ExchangeIntent.DismissExportPick ->
                _uiState.update {
                    it.copy(
                        exportPickKind = null,
                        exportPickDraftIds = emptySet(),
                        showExportDialog = true,
                    )
                }
            is ExchangeIntent.ToggleExportPickItem -> togglePickItem(intent.id)
            ExchangeIntent.ConfirmExportPick -> confirmExportPick()
            ExchangeIntent.RollbackStub ->
                _uiState.update { it.copy(lastActionLabel = "Rollback stub") }
        }
    }

    private fun openExportDialog() {
        _uiState.update {
            it.copy(
                showExportStylePicker = false,
                showExportDialog = true,
                exportPickKind = null,
                exportLimitsText = PackageExportLimits.HUMAN_LIMITS_RU,
            )
        }
        val useCase = exchangeZip ?: return
        scope.launch {
            val catalog =
                runCatching { useCase.loadExportCatalog() }.getOrElse {
                    com.midnightdancer.core.domain.exchange.ExportCatalogSnapshot()
                }
            val first = catalog.styles.firstOrNull()
            _uiState.update { state ->
                val styleId = state.exportStyleId.ifBlank { first?.id.orEmpty() }
                val styleName =
                    catalog.styles.firstOrNull { it.id == styleId }?.name
                        ?: first?.name.orEmpty()
                state.copy(
                    exportCatalog = catalog,
                    exportStyleId = styleId,
                    exportStyleName = styleName,
                )
            }
        }
    }

    private fun performSelectExportStyle(styleId: String) {
        val catalog = _uiState.value.exportCatalog
        val name = catalog.styles.firstOrNull { it.id == styleId }?.name ?: styleId
        _uiState.update {
            it.copy(
                exportStyleId = styleId,
                exportStyleName = name,
                exportSelectedMoveIds =
                    it.exportSelectedMoveIds.filter { id ->
                        catalog.elementsForStyle(styleId).any { row -> row.id == id }
                    }.toSet(),
                exportSelectedSongIds =
                    it.exportSelectedSongIds.filter { id ->
                        catalog.songsForStyle(styleId).any { row -> row.id == id }
                    }.toSet(),
                exportSelectedChoreoIds =
                    it.exportSelectedChoreoIds.filter { id ->
                        catalog.choreosForStyle(styleId).any { row -> row.id == id }
                    }.toSet(),
            )
        }
    }

    private fun openExportPick(kind: ExportPickKind) {
        val state = _uiState.value
        if (state.exportStyleId.isBlank()) return
        val draft =
            when (kind) {
                ExportPickKind.Elements -> state.exportSelectedMoveIds
                ExportPickKind.Songs -> state.exportSelectedSongIds
                ExportPickKind.Choreographies -> state.exportSelectedChoreoIds
            }
        _uiState.update {
            it.copy(
                showExportDialog = false,
                exportPickKind = kind,
                exportPickDraftIds = draft,
            )
        }
    }

    private fun togglePickItem(id: String) {
        val state = _uiState.value
        val kind = state.exportPickKind ?: return
        val max =
            when (kind) {
                ExportPickKind.Elements -> PackageExportLimits.MAX_ELEMENTS
                ExportPickKind.Songs -> PackageExportLimits.MAX_SONGS
                ExportPickKind.Choreographies -> PackageExportLimits.MAX_CHOREOGRAPHIES
            }
        val current = state.exportPickDraftIds
        val next =
            when {
                id in current -> current - id
                current.size >= max -> current
                else -> current + id
            }
        _uiState.update { it.copy(exportPickDraftIds = next) }
    }

    private fun confirmExportPick() {
        val state = _uiState.value
        val kind = state.exportPickKind ?: return
        _uiState.update {
            when (kind) {
                ExportPickKind.Elements ->
                    it.copy(
                        exportSelectedMoveIds = state.exportPickDraftIds,
                        exportPickKind = null,
                        exportPickDraftIds = emptySet(),
                        showExportDialog = true,
                    )
                ExportPickKind.Songs ->
                    it.copy(
                        exportSelectedSongIds = state.exportPickDraftIds,
                        exportPickKind = null,
                        exportPickDraftIds = emptySet(),
                        showExportDialog = true,
                    )
                ExportPickKind.Choreographies ->
                    it.copy(
                        exportSelectedChoreoIds = state.exportPickDraftIds,
                        exportPickKind = null,
                        exportPickDraftIds = emptySet(),
                        showExportDialog = true,
                    )
            }
        }
    }

    private fun openImportDialog() {
        performRefreshDiskGate()
        if (deviceStorage != null && !_uiState.value.importEnabled) {
            _uiState.update {
                it.copy(
                    lastActionLabel =
                        "Загрузка недоступна · мало места " +
                            "(свободно ${it.freeLabel}, нужно ${it.requiredLabel})",
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                showImportParams = true,
                importLocalStyles = emptyList(),
                importPackageStyles = emptyList(),
                importTargetStyleId = "",
            )
        }
        val useCase = exchangeZip ?: return
        scope.launch {
            val peek = runCatching { useCase.peekImport(null) }.getOrNull()
            val peekedLocal = peek?.localStyles.orEmpty()
            val defaults =
                com.midnightdancer.core.domain.settings.DanceStyleConfig.DEFAULT_NAMES.map { name ->
                    val id =
                        peekedLocal.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                            ?: name.lowercase().replace(' ', '-')
                    com.midnightdancer.core.domain.exchange.StyleRef(id = id, name = name)
                }
            val local =
                (peekedLocal + defaults)
                    .distinctBy { it.id.lowercase() }
                    .distinctBy { it.name.lowercase() }
            val pack = peek?.packageStyles.orEmpty()
            val target = local.firstOrNull()?.id.orEmpty()
            _uiState.update {
                it.copy(
                    importLocalStyles = local,
                    importPackageStyles = pack,
                    importTargetStyleId = target,
                )
            }
        }
    }

    private fun performConfirmImport() {
        performRefreshDiskGate()
        if (deviceStorage != null && !_uiState.value.importEnabled) {
            _uiState.update {
                it.copy(
                    showImportParams = false,
                    lastActionLabel =
                        "Загрузка недоступна · мало места " +
                            "(свободно ${it.freeLabel}, нужно ${it.requiredLabel})",
                )
            }
            return
        }
        val state = _uiState.value
        if (state.importTargetStyleId.isBlank() && state.importLocalStyles.isNotEmpty()) {
            _uiState.update {
                it.copy(lastActionLabel = "Укажите стиль для импорта")
            }
            return
        }
        val plan =
            StyleImportPlan.allInto(
                localStyleId = state.importTargetStyleId,
                packageStyleIds = state.importPackageStyles.map { it.id },
            )
        _uiState.update { it.copy(showImportParams = false) }
        runZip {
            exchangeZip?.import(
                preferredPath = null,
                contentFilter = ImportContentFilter.All,
                stylePlan = plan,
            )
        }
    }

    private fun performConfirmExport() {
        val state = _uiState.value
        if (state.exportStyleId.isBlank()) {
            _uiState.update { it.copy(lastActionLabel = "Сначала выберите стиль") }
            return
        }
        val selection =
            ExportPackageSelection(
                moveIds = state.exportSelectedMoveIds.toList(),
                songIds = state.exportSelectedSongIds.toList(),
                choreographyIds = state.exportSelectedChoreoIds.toList(),
            )
        val invalid = selection.validate()
        if (invalid.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    lastActionLabel =
                        when {
                            "empty_selection" in invalid ->
                                "Выберите хотя бы один элемент, трек или хореографию"
                            else ->
                                "Превышен лимит пакета: не больше пяти элементов, " +
                                    "двух треков и одной хореографии"
                        },
                )
            }
            return
        }
        _uiState.update { it.copy(showExportDialog = false) }
        runZip {
            val r = exchangeZip?.export(selection)
            performRefreshDiskGate()
            r
        }
    }

    private fun performRefreshDiskGate() {
        val storage = deviceStorage
        val required = max(estimateRequiredBytes(), 1L)
        val withBuffer = DiskSpaceGate.requiredWithBuffer(required)
        if (storage == null) {
            _uiState.update {
                it.copy(
                    freeBytes = 0L,
                    requiredBytes = required,
                    requiredWithBufferBytes = withBuffer,
                    importEnabled = true,
                    freeLabel = "—",
                    requiredLabel = formatBytes(withBuffer),
                )
            }
            return
        }
        val free = storage.freeBytes()
        val enabled = DiskSpaceGate.canImport(free, required)
        _uiState.update {
            it.copy(
                freeBytes = free,
                requiredBytes = required,
                requiredWithBufferBytes = withBuffer,
                importEnabled = enabled && free > 0L,
                freeLabel = formatBytes(free),
                requiredLabel = formatBytes(withBuffer),
            )
        }
    }

    private fun estimateRequiredBytes(): Long {
        val path = _uiState.value.lastArchivePath
        if (!path.isNullOrBlank()) {
            val mb = _uiState.value.lastImportMb
            if (mb > 0f) return (mb * 1024f * 1024f).toLong().coerceAtLeast(1L)
        }
        return 64L * 1024L * 1024L
    }

    private fun runZip(block: suspend () -> ExchangeZipResult?) {
        if (_uiState.value.busy) return
        val useCase = exchangeZip
        if (useCase == null) {
            _uiState.update { it.copy(lastActionLabel = "Zip unavailable (no ExchangeZipUseCase)") }
            return
        }
        scope.launch {
            _uiState.update { it.copy(busy = true) }
            val result =
                runCatching { block() }.getOrElse { e ->
                    ExchangeZipResult.Failed(
                        message = "Zip crashed",
                        reasons = listOf(e.message ?: e::class.simpleName ?: "error"),
                    )
                }
            when (result) {
                null ->
                    _uiState.update {
                        it.copy(busy = false, lastActionLabel = "Zip unavailable")
                    }
                is ExchangeZipResult.Ok -> {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            lastActionLabel = result.message,
                            lastArchivePath = result.archivePath.ifBlank { it.lastArchivePath },
                            lastImportName =
                                result.archivePath
                                    .substringAfterLast('/')
                                    .substringAfterLast('\\')
                                    .ifBlank { it.lastImportName },
                            lastImportMb =
                                if (result.sizeBytes > 0L) {
                                    result.sizeBytes / (1024f * 1024f)
                                } else {
                                    it.lastImportMb
                                },
                        )
                    }
                    performRefreshDiskGate()
                }
                is ExchangeZipResult.Failed ->
                    _uiState.update {
                        it.copy(
                            busy = false,
                            lastActionLabel =
                                buildString {
                                    append(result.message)
                                    if (result.reasons.isNotEmpty()) {
                                        append(" · ")
                                        append(result.reasons.take(3).joinToString("; "))
                                    }
                                },
                        )
                    }
            }
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 Б"
            val kb = 1024.0
            val mb = kb * 1024
            val gb = mb * 1024
            return when {
                bytes >= gb -> "${((bytes / gb) * 10).toInt() / 10.0} ГБ"
                bytes >= mb -> "${((bytes / mb) * 10).toInt() / 10.0} МБ"
                else -> "${(bytes / kb).toInt()} КБ"
            }
        }
    }
}
