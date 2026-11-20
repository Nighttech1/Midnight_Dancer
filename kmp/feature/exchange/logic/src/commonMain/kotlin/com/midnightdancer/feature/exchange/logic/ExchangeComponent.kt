package com.midnightdancer.feature.exchange.logic

import com.midnightdancer.core.domain.styles.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
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
    private val savedSetsPersister: SavedSetsPersister? = null,
    private val danceStyles: ManageDanceStyles? = null,
) : ExchangeComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(ExchangeUiState())
    override val uiState: StateFlow<ExchangeUiState> = _uiState.asStateFlow()

    private var inspectionJob: Job? = null
    private var formGeneration = 0L

    private fun cancelFormWork() {
        formGeneration++
        inspectionJob?.cancel()
        inspectionJob = null
    }

    private var setSeq: Long = 0L
    private var clockMs: Long = 0L
    private var savedSetsLoadFailed: Boolean = false

    /** When set, next successful export opens the Android share sheet instead of only saving. */
    private var shareAfterExportFileName: String? = null

    /** After successful import, register an «Добавленные» card. */
    private var pendingImportedSetName: String? = null
    private var pendingImportedStyleId: String = ""
    private var pendingImportedStyleName: String = ""

    init {
        lifecycle.doOnDestroy { cancelFormWork(); scope.cancel() }
        // C-244: restore sets after ChildPages DESTROY (tab leave).
        val restored = runCatching { savedSetsPersister?.read() }
        val cached = restored.getOrNull() ?: SavedSetsStore.snapshot()
        if (restored.isFailure) {
            savedSetsLoadFailed = true
            _uiState.update { it.copy(lastActionLabel = "Не удалось прочитать сохранённые сеты") }
        }
        SavedSetsStore.replaceAll(cached)
        if (cached.isNotEmpty()) {
            SavedSetsStore.syncSeqFrom(cached.map { it.id })
            setSeq = SavedSetsStore.currentSeq()
            _uiState.update { it.copy(savedSets = cached) }
        }
        performRefreshDiskGate()
        danceStyles?.let { manager -> scope.launch { manager.state.filterNotNull().collect { snapshot ->
            val normalized = normalizeSavedSetStyles(_uiState.value.savedSets, snapshot)
            val saved = !savedSetsLoadFailed && runCatching { savedSetsPersister?.refreshStyles() }.isSuccess
            if (saved) SavedSetsStore.replaceAll(normalized)
            _uiState.update { current ->
                val imported = current.viewingImportedSet
                val selectedId = current.exportStyleId.takeIf { it.isNotBlank() }?.let(snapshot::canonicalId).orEmpty()
                current.copy(savedSets = if (saved) normalized else current.savedSets,
                    operationError = if (saved) current.operationError else "Не удалось обновить стили сохранённых сетов",
                    importLocalStyles = snapshot.entries.map { com.midnightdancer.core.domain.exchange.StyleRef(it.id, it.name) },
                    importTargetStyleId = current.importTargetStyleId.takeIf { it.isNotBlank() }?.let(snapshot::canonicalId).orEmpty(),
                    exportCatalog = if (imported) current.exportCatalog else current.exportCatalog.normalizeLocalCatalog(snapshot),
                    exportStyleId = if (imported) current.exportStyleId else selectedId,
                    exportStyleName = if (imported || selectedId.isBlank()) current.exportStyleName else snapshot.displayName(selectedId))
            }
        } } }
    }

    private fun persistSets(sets: List<SavedSet>): Boolean {
        val sets = danceStyles?.state?.value?.let { normalizeSavedSetStyles(sets, it) } ?: sets
        if (savedSetsLoadFailed) return false // Preserve unreadable/newer metadata for recovery.
        if (runCatching { savedSetsPersister?.write(sets) }.isFailure) return false
        SavedSetsStore.replaceAll(sets)
        SavedSetsStore.syncSeqFrom(sets.map { it.id })
        return true
    }

    private fun nowMs(): Long {
        clockMs += 1L
        return clockMs
    }

    override fun onIntent(intent: ExchangeIntent) {
        when (intent) {
            ExchangeIntent.RetryArchiveInspection -> inspectCurrentArchive()
            ExchangeIntent.RemoveInvalidExportSelection -> removeInvalidSelection()
            ExchangeIntent.RefreshDiskGate -> performRefreshDiskGate()
            ExchangeIntent.ImportStub -> openImportDialog(preferredPath = null, displayName = "")
            is ExchangeIntent.ImportPackagePicked ->
                openImportDialog(
                    preferredPath = intent.contentUri,
                    displayName = intent.displayName,
                )
            ExchangeIntent.DismissImportParams ->
                _uiState.update {
                    it.copy(
                        showImportParams = false,
                        importPreferredPath = null,
                        importDisplayName = "",
                    )
                }
            is ExchangeIntent.SetImportTargetStyle ->
                _uiState.update { it.copy(importTargetStyleId = danceStyles?.state?.value?.canonicalId(intent.localStyleId) ?: intent.localStyleId) }
            ExchangeIntent.CreateImportTargetStyle -> createImportTargetStyle()
            ExchangeIntent.ConfirmImport -> performConfirmImport()
            ExchangeIntent.ExportStub -> createDraftSetAndOpen()
            ExchangeIntent.DismissExportStylePicker ->
                _uiState.update { it.copy(showExportStylePicker = false) }
            is ExchangeIntent.SelectExportStyle -> performSelectExportStyle(intent.styleId)
            ExchangeIntent.DismissExportDialog -> dismissExportDialog()
            ExchangeIntent.ConfirmExport -> performConfirmExport()
            is ExchangeIntent.OpenExportPick -> openExportPick(intent.kind)
            ExchangeIntent.DismissExportPick ->
                _uiState.update {
                    it.copy(
                        exportPickKind = null,
                        exportPickDraftIds = emptySet(),
                        showExportDialog = true,
                    ).withExportWeight()
                }
            is ExchangeIntent.ToggleExportPickItem -> togglePickItem(intent.id)
            ExchangeIntent.ConfirmExportPick -> confirmExportPick()
            ExchangeIntent.RollbackStub ->
                _uiState.update { it.copy(lastActionLabel = "Rollback stub") }
            is ExchangeIntent.SelectSetHubTab ->
                _uiState.update {
                    it.copy(
                        hubTab = intent.tab,
                        searchQuery = "",
                        selectedFilter = "Все теги",
                    )
                }
            is ExchangeIntent.SetSetsSearchQuery ->
                _uiState.update { it.copy(searchQuery = intent.query) }
            is ExchangeIntent.SetSetsFilter ->
                _uiState.update {
                    it.copy(selectedFilter = intent.tag?.ifBlank { null } ?: "Все теги")
                }
            is ExchangeIntent.SetSetsSortMode ->
                _uiState.update { it.copy(sortMode = intent.mode) }
            is ExchangeIntent.ShareSet -> shareSavedSet(intent.id)
            ExchangeIntent.DismissShareSetPicker ->
                _uiState.update { it.copy(showShareSetPicker = false) }
            ExchangeIntent.ClearPendingShare ->
                _uiState.update {
                    it.copy(pendingSharePath = null, pendingShareFileName = "")
                }
            ExchangeIntent.OpenMySets ->
                _uiState.update {
                    it.copy(hubTab = SetHubTab.Mine, showMySets = true)
                }
            ExchangeIntent.DismissMySets -> {
                cancelFormWork()
                _uiState.update {
                    it.copy(
                        showMySets = true,
                        returnToMySets = false,
                        showExportDialog = false,
                        archiveInspectionCatalog = null,
                        archiveInspectionLoading = false,
                        archiveInspectionError = null,
                        exportPickKind = null,
                        editingSetId = null,
                        exportSetName = "",
                    )
                }
            }
            is ExchangeIntent.EditSavedSet -> openEditSavedSet(intent.id)
            is ExchangeIntent.CopySavedSet -> copySavedSet(intent.id)
            is ExchangeIntent.DeleteSavedSet -> deleteSavedSet(intent.id)
            is ExchangeIntent.SetExportSetName ->
                if (!_uiState.value.viewingImportedSet) _uiState.update {
                    if (it.exportSetName == intent.name) it else it.copy(exportSetName = intent.name, exportValidationError = null)
                }
        }
    }

    private fun openExportDialog() {
        _uiState.update {
            it.copy(
                showExportStylePicker = false,
                showExportDialog = true,
                exportPickKind = null,
                exportLimitsText = PackageExportLimits.HUMAN_LIMITS_RU,
                editingSetId = null,
                exportSetName = "",
                returnToMySets = true,
                exportSelectedMoveIds = emptySet(),
                exportSelectedSongIds = emptySet(),
                exportSelectedChoreoIds = emptySet(),
            ).withExportWeight()
        }
        loadExportCatalog()
    }

    /**
     * C-230: tapping «Создать сет» immediately persists a draft («новый сет» / «новый сет N»)
     * so dismissing the dialog by outside tap does not lose the set.
     */
    private fun createDraftSetAndOpen() {
        val current = _uiState.value
        if (current.busy) {
            _uiState.update { it.copy(operationError = "Подождите — идёт операция с сетом") }
            return
        }
        if (current.isCreateSetFlow && (current.showExportDialog || current.exportPickKind != null)) return
        cancelFormWork()
        setSeq++
        val name = nextNewSetName(_uiState.value.savedSets)
        val id = "set-${setSeq}-${nowMs()}"
        val draft =
            SavedSet(
                id = id,
                name = name,
                styleId = "",
                styleName = "",
                updatedAt = nowMs(),
                origin = SavedSetOrigin.Mine,
            )
        _uiState.update {
            val sets = listOf(draft) + it.savedSets
            if (!persistSets(sets)) return@update it.copy(lastActionLabel = "Не удалось сохранить сет", operationError = "Не удалось сохранить сет. Попробуйте снова")
            it.copy(
                savedSets = sets,
                hubTab = SetHubTab.Mine,
                showExportStylePicker = false,
                showExportDialog = true,
                exportPickKind = null,
                exportLimitsText = PackageExportLimits.HUMAN_LIMITS_RU,
                editingSetId = id,
                isCreateSetFlow = true,
                exportStyleId = "",
                exportStyleName = "",
                exportCatalog = com.midnightdancer.core.domain.exchange.ExportCatalogSnapshot(),
                exportCatalogLoaded = false,
                exportCatalogLoading = false,
                exportValidationError = null,
                archiveInspectionCatalog = null,
                archiveInspectionLoading = false,
                archiveInspectionError = null,
                operationError = null,
                exportSetName = name,
                returnToMySets = true,
                exportSelectedMoveIds = emptySet(),
                exportSelectedSongIds = emptySet(),
                exportSelectedChoreoIds = emptySet(),
            ).withExportWeight()
        }
        loadExportCatalog()
    }

    private fun nextNewSetName(existing: List<SavedSet>): String {
        val names = existing.map { it.name.trim().lowercase() }.toSet()
        if ("новый сет" !in names) return "новый сет"
        var n = 2
        while ("новый сет $n" in names) n++
        return "новый сет $n"
    }

    private fun loadExportCatalog() {
        val useCase = exchangeZip
        if (useCase == null) {
            _uiState.update { it.copy(exportCatalogLoading = false, exportValidationError = "Материалы сета сейчас недоступны") }
            return
        }
        _uiState.update { it.copy(exportCatalogLoading = true) }
        val generation = formGeneration
        scope.launch {
            val catalog = try { useCase.loadExportCatalog().let { raw ->
                danceStyles?.state?.value?.let { raw.normalizeLocalCatalog(it) } ?: raw
            } } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    _uiState.update { if (generation == formGeneration && !it.viewingImportedSet)
                        it.copy(exportCatalogLoading = false, exportValidationError = "Не удалось загрузить материалы сета. Закройте окно и попробуйте снова") else it }
                    return@launch
                }
            _uiState.update { state ->
                if (generation != formGeneration || state.viewingImportedSet) return@update state
                val first = catalog.styles.firstOrNull()
                val styleId = if (state.exportStyleLocked) state.exportStyleId
                    else state.exportStyleId.ifBlank { first?.id.orEmpty() }
                val styleName = if (state.exportStyleLocked) state.exportStyleName
                    else catalog.styles.firstOrNull { it.id == styleId }?.name ?: first?.name.orEmpty()
                state.copy(exportCatalog = catalog, exportCatalogLoaded = true, exportCatalogLoading = false, exportValidationError = null,
                    exportStyleId = styleId, exportStyleName = styleName).withExportWeight()
            }
        }
    }

    private fun inspectCurrentArchive() {
        val set = _uiState.value.savedSets.firstOrNull { it.id == _uiState.value.editingSetId }
            ?.takeIf { it.origin == SavedSetOrigin.Imported } ?: return
        cancelFormWork()
        val generation = formGeneration
        val path = set.archivePath
        _uiState.update { it.copy(archiveInspectionCatalog = null, archiveInspectionLoading = !path.isNullOrBlank(),
            archiveInspectionError = if (path.isNullOrBlank()) "Архив этого сета недоступен. Импортируйте его повторно" else null,
            exportPickKind = null, exportPickDraftIds = emptySet(), showExportDialog = true) }
        if (path.isNullOrBlank()) return
        inspectionJob = scope.launch {
            val result = try { Result.success(requireNotNull(exchangeZip) { "archive_inspection_unavailable" }.inspectArchive(path)) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { Result.failure(e) }
            _uiState.update { state ->
                val current = state.savedSets.firstOrNull { it.id == set.id }
                if (generation != formGeneration || state.editingSetId != set.id || !state.viewingImportedSet || current?.archivePath != path)
                    return@update state
                state.copy(archiveInspectionLoading = false, archiveInspectionCatalog = result.getOrNull(),
                    archiveInspectionError = result.exceptionOrNull()?.let {
                        when {
                            it is UnsupportedOperationException -> "Просмотр архива недоступен в этой версии"
                            it.message == "archive_missing" -> "Архив этого сета недоступен. Импортируйте его повторно"
                            it.message == "insufficient_free_space" -> "Недостаточно места для просмотра архива"
                            it.message == "unsupported_editor_vault_schema" -> "Версия постановок в архиве пока не поддерживается"
                            else -> "Не удалось прочитать архив этого сета. Файл повреждён или недоступен"
                        }
                    })
            }
        }
    }

    private fun removeInvalidSelection() {
        _uiState.update { state ->
            if (state.viewingImportedSet || !state.exportCatalogLoaded || !state.hasInvalidExportSelection) return@update state
            if (state.exportSelectedMoveIds.isEmpty() && state.exportSelectedSongIds.isEmpty() && state.exportSelectedChoreoIds.isEmpty()) return@update state
            state.copy(
                exportValidationError = null,
                exportSelectedMoveIds = state.exportSelectedMoveIds.intersect(state.allowedExportRows(ExportPickKind.Elements).map { it.id }.toSet()),
                exportSelectedSongIds = state.exportSelectedSongIds.intersect(state.allowedExportRows(ExportPickKind.Songs).map { it.id }.toSet()),
                exportSelectedChoreoIds = state.exportSelectedChoreoIds.intersect(state.allowedExportRows(ExportPickKind.Choreographies).map { it.id }.toSet()),
                exportPickDraftIds = emptySet(), operationError = null,
            ).withExportWeight()
        }
    }

    private fun dismissExportDialog() {
        cancelFormWork()
        _uiState.update { state ->
            state.copy(
                showExportDialog = false,
                archiveInspectionCatalog = null,
                archiveInspectionLoading = false,
                archiveInspectionError = null,
                exportValidationError = null,
                exportCatalogLoading = false,
                exportPickKind = null,
                editingSetId = null,
                isCreateSetFlow = false,
                exportSetName = "",
                returnToMySets = false,
            )
        }
    }

    private fun openEditSavedSet(id: String) {
        val set = _uiState.value.savedSets.firstOrNull { it.id == id } ?: return
        cancelFormWork()
        val creating = set.origin == SavedSetOrigin.Mine && set.styleId.isBlank() &&
            set.moveIds.isEmpty() && set.songIds.isEmpty() && set.choreoIds.isEmpty()
        _uiState.update {
            it.copy(
                showExportDialog = true,
                exportPickKind = null,
                editingSetId = set.id,
                exportPickDraftIds = emptySet(),
                isCreateSetFlow = creating,
                exportCatalog = com.midnightdancer.core.domain.exchange.ExportCatalogSnapshot(),
                exportCatalogLoaded = false,
                exportCatalogLoading = false,
                exportValidationError = null,
                archiveInspectionCatalog = null,
                archiveInspectionLoading = false,
                archiveInspectionError = null,
                operationError = null,
                exportSetName = set.name,
                exportStyleId = set.styleId,
                exportStyleName = set.styleName.ifBlank { set.styleId },
                exportSelectedMoveIds = set.moveIds.toSet(),
                exportSelectedSongIds = set.songIds.toSet(),
                exportSelectedChoreoIds = set.choreoIds.toSet(),
                exportLimitsText = PackageExportLimits.HUMAN_LIMITS_RU,
                returnToMySets = true,
                hubTab =
                    when (set.origin) {
                        SavedSetOrigin.Mine -> SetHubTab.Mine
                        SavedSetOrigin.Imported -> SetHubTab.Imported
                    },
            ).withExportWeight()
        }
        if (set.origin == SavedSetOrigin.Imported) inspectCurrentArchive() else loadExportCatalog()
    }

    private fun copySavedSet(id: String) {
        val src = _uiState.value.savedSets.firstOrNull { it.id == id } ?: return
        setSeq++
        val copy =
            src.copy(
                id = "set-${setSeq}-${nowMs()}",
                name = src.name + " копия",
                updatedAt = nowMs(),
                origin = src.origin,
            )
        _uiState.update {
            val sets = listOf(copy) + it.savedSets
            if (!persistSets(sets)) return@update it.copy(lastActionLabel = "Не удалось сохранить копию сета")
            it.copy(savedSets = sets)
        }
    }

    private fun deleteSavedSet(id: String) {
        if (_uiState.value.editingSetId == id) dismissExportDialog()
        _uiState.update {
            val sets = it.savedSets.filterNot { s -> s.id == id }
            if (!persistSets(sets)) return@update it.copy(lastActionLabel = "Не удалось удалить сет")
            it.copy(savedSets = sets)
        }
    }

    private fun shareSavedSet(id: String) {
        val state = _uiState.value
        val resolvedId =
            if (id.isNotBlank()) {
                id
            } else {
                val candidates = state.visibleSets.ifEmpty { state.savedSets }
                when {
                    candidates.isEmpty() -> {
                        _uiState.update {
                            it.copy(lastActionLabel = "Нет сетов для отправки")
                        }
                        return
                    }
                    candidates.size == 1 -> candidates.first().id
                    else -> {
                        _uiState.update { it.copy(showShareSetPicker = true) }
                        return
                    }
                }
            }
        val set = state.savedSets.firstOrNull { it.id == resolvedId } ?: return
        _uiState.update { it.copy(showShareSetPicker = false) }
        if (set.origin == SavedSetOrigin.Imported) {
            val path = set.archivePath
            _uiState.update {
                if (path.isNullOrBlank()) it.copy(operationError = "Архив этого сета не сохранён. Импортируйте его повторно",
                    lastActionLabel = "Архив этого сета не сохранён. Импортируйте его повторно")
                else it.copy(pendingSharePath = path, pendingShareFileName = sanitizeShareFileName(set.name),
                    lastActionLabel = "Поделиться «${set.name}»")
            }
            return
        }
        if (set.moveIds.isEmpty() && set.songIds.isEmpty() && set.choreoIds.isEmpty()) {
            _uiState.update { it.copy(lastActionLabel = EMPTY_SET_SHARE_WARN_RU) }
            return
        }
        val selection =
            ExportPackageSelection(
                moveIds = set.moveIds,
                songIds = set.songIds,
                choreographyIds = set.choreoIds,
            )
        if (_uiState.value.busy) {
            _uiState.update { it.copy(lastActionLabel = "Подождите — идёт операция с сетом") }
            return
        }
        runZip {
            val catalog = exchangeZip?.loadExportCatalog()
            val current = _uiState.value.savedSets.firstOrNull { it.id == set.id }
            val valid = current == set && catalog != null && catalog.canonicalStyleId(set.styleId) != null &&
                set.moveIds.all { id -> catalog.rowsForStyle(ExportPickKind.Elements, set.styleId).any { it.id == id } } &&
                set.songIds.all { id -> catalog.rowsForStyle(ExportPickKind.Songs, set.styleId).any { it.id == id } } &&
                set.choreoIds.all { id -> catalog.rowsForStyle(ExportPickKind.Choreographies, set.styleId).any { it.id == id } }
            if (!valid) ExchangeZipResult.Failed("style_selection_conflict", listOf("style_selection_conflict"))
            else {
                shareAfterExportFileName = sanitizeShareFileName(set.name)
                exchangeZip?.export(selection)
            }
        }
    }

    private fun sanitizeShareFileName(name: String): String {
        val base =
            name.trim()
                .ifBlank { "set" }
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(64)
        return when {
            base.endsWith(".dance", ignoreCase = true) -> base
            base.endsWith(".mdance", ignoreCase = true) ->
                base.dropLast(".mdance".length) + ".dance"
            else -> "$base.dance"
        }
    }

    private fun performSelectExportStyle(styleId: String) {
        val styleId = danceStyles?.state?.value?.canonicalId(styleId) ?: styleId
        if (_uiState.value.viewingImportedSet || _uiState.value.exportStyleLocked) return
        val catalog = _uiState.value.exportCatalog
        val name = catalog.styles.firstOrNull { it.id == styleId }?.name ?: return
        if (_uiState.value.exportStyleId == styleId) return
        _uiState.update {
            it.copy(
                exportStyleId = styleId,
                exportStyleName = name,
                exportValidationError = null,
                exportPickKind = null,
                exportPickDraftIds = emptySet(),
                exportSelectedMoveIds =
                    it.exportSelectedMoveIds.filter { id ->
                        catalog.rowsForStyle(ExportPickKind.Elements, styleId).any { row -> row.id == id }
                    }.toSet(),
                exportSelectedSongIds =
                    it.exportSelectedSongIds.filter { id ->
                        catalog.rowsForStyle(ExportPickKind.Songs, styleId).any { row -> row.id == id }
                    }.toSet(),
                exportSelectedChoreoIds =
                    it.exportSelectedChoreoIds.filter { id ->
                        catalog.rowsForStyle(ExportPickKind.Choreographies, styleId).any { row -> row.id == id }
                    }.toSet(),
            ).withExportWeight()
        }
    }

    private fun openExportPick(kind: ExportPickKind) {
        val state = _uiState.value
        if (state.viewingImportedSet) {
            if (state.archiveInspectionLoading || state.archiveInspectionError != null || state.archiveInspectionCatalog == null) return
            _uiState.update { it.copy(exportPickKind = kind, exportPickDraftIds = emptySet(), showExportDialog = false) }
            return
        }
        if (state.exportStyleId.isBlank() || !state.exportCatalogLoaded) return
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
            ).withExportWeight()
        }
    }

    private fun togglePickItem(id: String) {
        if (_uiState.value.viewingImportedSet) return
        val state = _uiState.value
        val kind = state.exportPickKind ?: return
        if (state.allowedExportRows(kind).none { it.id == id }) return
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
        _uiState.update { it.copy(exportPickDraftIds = next, exportValidationError = if (next != current) null else it.exportValidationError).withExportWeight() }
    }

    private fun confirmExportPick() {
        if (_uiState.value.viewingImportedSet) return
        val state = _uiState.value
        val kind = state.exportPickKind ?: return
        val allowed = state.allowedExportRows(kind).map { it.id }.toSet()
        val original = when (kind) {
            ExportPickKind.Elements -> state.exportSelectedMoveIds
            ExportPickKind.Songs -> state.exportSelectedSongIds
            ExportPickKind.Choreographies -> state.exportSelectedChoreoIds
        }
        val confirmed = (original - allowed) + state.exportPickDraftIds.intersect(allowed)
        _uiState.update {
            when (kind) {
                ExportPickKind.Elements ->
                    it.copy(
                        exportSelectedMoveIds = confirmed,
                        exportValidationError = if (confirmed != original) null else it.exportValidationError,
                        exportPickKind = null,
                        exportPickDraftIds = emptySet(),
                        showExportDialog = true,
                    ).withExportWeight()
                ExportPickKind.Songs ->
                    it.copy(
                        exportSelectedSongIds = confirmed,
                        exportValidationError = if (confirmed != original) null else it.exportValidationError,
                        exportPickKind = null,
                        exportPickDraftIds = emptySet(),
                        showExportDialog = true,
                    ).withExportWeight()
                ExportPickKind.Choreographies ->
                    it.copy(
                        exportSelectedChoreoIds = confirmed,
                        exportValidationError = if (confirmed != original) null else it.exportValidationError,
                        exportPickKind = null,
                        exportPickDraftIds = emptySet(),
                        showExportDialog = true,
                    ).withExportWeight()
            }
        }
    }

    private fun createImportTargetStyle() {
        val manager = danceStyles
        if (manager != null) {
            scope.launch {
                try {
                    val existing = manager.state.value ?: error("Каталог стилей загружается.")
                    var n = existing.entries.size + 1
                    while (existing.entries.any { it.name.equals("Новый стиль $n", true) }) n++
                    val name = "Новый стиль $n"
                    manager.add(name)
                    val current = checkNotNull(manager.state.value)
                    val id = current.entries.single { it.name == name }.id
                    _uiState.update { it.copy(importLocalStyles = current.entries.map { row ->
                        com.midnightdancer.core.domain.exchange.StyleRef(row.id, row.name)
                    }, importTargetStyleId = id, operationError = null) }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) { _uiState.update { it.copy(operationError = error.message ?: "Не удалось создать стиль.") } }
            }
            return
        }
        _uiState.update { state ->
            var n = state.importLocalStyles.size + 1
            while (state.importLocalStyles.any { it.id == "import-style-$n" }) n++
            val id = "import-style-$n"
            val name = "Новый стиль $n"
            val style = com.midnightdancer.core.domain.exchange.StyleRef(id = id, name = name)
            state.copy(
                importLocalStyles = listOf(style) + state.importLocalStyles,
                importTargetStyleId = id,
            )
        }
    }

    private fun openImportDialog(preferredPath: String?, displayName: String) {
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
                importPreferredPath = preferredPath,
                importDisplayName = displayName,
                hubTab = SetHubTab.Imported,
            )
        }
        val useCase = exchangeZip ?: return
        scope.launch {
            val peek = runCatching { useCase.peekImport(preferredPath) }.getOrNull()
            val peekedLocal = peek?.localStyles.orEmpty()
            val defaults =
                com.midnightdancer.core.domain.settings.DanceStyleConfig.DEFAULT_NAMES.map { name ->
                    val id =
                        peekedLocal.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                            ?: name.lowercase().replace(' ', '-')
                    com.midnightdancer.core.domain.exchange.StyleRef(id = id, name = name)
                }
            val local =
                (danceStyles?.state?.value?.entries?.map { com.midnightdancer.core.domain.exchange.StyleRef(it.id, it.name) } ?: (peekedLocal + defaults))
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
        val packageLabel =
            state.importDisplayName.ifBlank {
                state.importPreferredPath
                    ?.substringAfterLast('/')
                    ?.substringBefore('?')
                    .orEmpty()
            }.ifBlank { state.lastImportName }
        val styleName =
            state.importLocalStyles.firstOrNull { it.id == state.importTargetStyleId }?.name
                ?: state.importTargetStyleId
        pendingImportedSetName =
            packageLabel
                .removeSuffix(".dance")
                .removeSuffix(".mdance")
                .ifBlank { packageLabel }
        pendingImportedStyleId = state.importTargetStyleId
        pendingImportedStyleName = styleName
        _uiState.update { it.copy(showImportParams = false, hubTab = SetHubTab.Imported) }
        runZip {
            val raw =
                exchangeZip?.import(
                    preferredPath = state.importPreferredPath,
                    contentFilter = ImportContentFilter.All,
                    stylePlan = plan,
                )
            if (raw is ExchangeZipResult.Ok) {
                raw.copy(message = "сет \"$packageLabel\" успешно импортирован!")
            } else {
                raw
            }
        }
    }

    private fun rejectExport(message: String) {
        _uiState.update { it.copy(exportValidationError = message, lastActionLabel = message) }
    }

    private fun performConfirmExport() {
        val state = _uiState.value.withExportWeight()
        if (state.viewingImportedSet || !state.showExportDialog) return
        if (state.busy) {
            rejectExport("Подождите — идёт операция с сетом")
            return
        }
        if (!state.exportCatalogLoaded) {
            rejectExport(if (state.exportCatalogLoading) "Материалы ещё загружаются. Подождите"
                else "Не удалось загрузить материалы сета. Закройте окно и попробуйте снова")
            return
        }
        if (state.exportStyleId.isBlank()) {
            rejectExport("Сначала выберите стиль")
            return
        }
        if (state.hasInvalidExportSelection) {
            rejectExport("Проверьте стиль и уберите недоступные материалы из сета")
            return
        }
        if (state.exportOverWeight) {
            rejectExport("Вес сета превышает ${PackageExportLimits.MAX_PACKAGE_MEGABYTES} МБ")
            return
        }
        val selection = ExportPackageSelection(
            moveIds = state.exportSelectedMoveIds.toList(),
            songIds = state.exportSelectedSongIds.toList(),
            choreographyIds = state.exportSelectedChoreoIds.toList(),
        )
        val invalid = selection.validate()
        if (invalid.isNotEmpty()) {
            rejectExport(if ("empty_selection" in invalid) "Выберите хотя бы один элемент, трек или хореографию"
                else "Превышен лимит сета")
            return
        }
        if (!upsertSavedSetFromCurrent(state)) return
        _uiState.update {
            it.copy(showExportDialog = false, editingSetId = null, isCreateSetFlow = false,
                exportSetName = "", exportValidationError = null, returnToMySets = false, hubTab = SetHubTab.Mine)
        }
        runZip {
            val r = exchangeZip?.export(selection)
            performRefreshDiskGate()
            r
        }
    }

    private fun upsertSavedSetFromCurrent(state: ExchangeUiState): Boolean {
        var persisted = false
        val weighted = state.withExportWeight()
        val now = nowMs()
        val name =
            weighted.exportSetName.trim().ifBlank {
                val style = weighted.exportStyleName.ifBlank { "Сет" }
                "Сет · $style"
            }
        val editingId = weighted.editingSetId
        if (editingId != null) {
            _uiState.update { s ->
                val sets =
                    s.savedSets.map { set ->
                        if (set.id != editingId) {
                            set
                        } else {
                            set.copy(
                                name = name,
                                styleId = if (weighted.isCreateSetFlow) weighted.exportStyleId else set.styleId,
                                styleName = if (weighted.isCreateSetFlow) weighted.exportStyleName else set.styleName,
                                moveIds = weighted.exportSelectedMoveIds.toList(),
                                songIds = weighted.exportSelectedSongIds.toList(),
                                choreoIds = weighted.exportSelectedChoreoIds.toList(),
                                estimatedBytes = weighted.exportEstimatedBytes,
                                updatedAt = now,
                                tags =
                                    listOfNotNull(
                                        weighted.exportStyleName.takeIf { it.isNotBlank() },
                                    ),
                            )
                        }
                    }
                if (!persistSets(sets)) return@update s.copy(lastActionLabel = "Не удалось сохранить сет", exportValidationError = "Не удалось сохранить сет. Попробуйте снова")
                persisted = true
                s.copy(savedSets = sets)
            }
            return persisted
        }
        setSeq++
        val created =
            SavedSet(
                id = "set-${setSeq}-$now",
                name = name,
                styleId = weighted.exportStyleId,
                styleName = weighted.exportStyleName,
                moveIds = weighted.exportSelectedMoveIds.toList(),
                songIds = weighted.exportSelectedSongIds.toList(),
                choreoIds = weighted.exportSelectedChoreoIds.toList(),
                estimatedBytes = weighted.exportEstimatedBytes,
                updatedAt = now,
                origin = SavedSetOrigin.Mine,
                tags =
                    listOfNotNull(weighted.exportStyleName.takeIf { it.isNotBlank() }),
            )
        _uiState.update {
            val sets = listOf(created) + it.savedSets
            if (!persistSets(sets)) return@update it.copy(lastActionLabel = "Не удалось сохранить сет", exportValidationError = "Не удалось сохранить сет. Попробуйте снова")
            persisted = true
            it.copy(savedSets = sets)
        }
        return persisted
    }

    private fun registerImportedSet(archivePath: String, sizeBytes: Long) {
        val name = pendingImportedSetName ?: return
        pendingImportedSetName = null
        setSeq++
        val now = nowMs()
        val id = "imported-${setSeq}-$now"
        val retainedArchive = savedSetsPersister?.retainArchive(archivePath, id) ?: archivePath
        val created =
            SavedSet(
                id = id,
                name = name,
                styleId = pendingImportedStyleId,
                styleName = pendingImportedStyleName,
                estimatedBytes = sizeBytes.coerceAtLeast(0L),
                updatedAt = now,
                origin = SavedSetOrigin.Imported,
                archivePath = retainedArchive,
                tags =
                    listOfNotNull(pendingImportedStyleName.takeIf { it.isNotBlank() }),
            )
        pendingImportedStyleId = ""
        pendingImportedStyleName = ""
        _uiState.update {
            val sets = listOf(created) + it.savedSets
            check(persistSets(sets)) { "cannot_persist_imported_set" }
            it.copy(
                savedSets = sets,
                hubTab = SetHubTab.Imported,
                lastArchivePath = archivePath.ifBlank { it.lastArchivePath },
            )
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
            shareAfterExportFileName = null
            pendingImportedSetName = null
            _uiState.update { it.copy(lastActionLabel = "Zip unavailable (no ExchangeZipUseCase)", operationError = "Обмен сетами сейчас недоступен") }
            return
        }
        _uiState.update { it.copy(busy = true, operationError = null) }
        scope.launch {
            val result =
                runCatching { block() }.getOrElse { e ->
                    if (e is CancellationException) throw e
                    ExchangeZipResult.Failed(
                        message = "Zip crashed",
                        reasons = listOf(e.message ?: e::class.simpleName ?: "error"),
                    )
                }
            when (result) {
                null -> {
                    shareAfterExportFileName = null
                    pendingImportedSetName = null
                    _uiState.update {
                        it.copy(busy = false, lastActionLabel = "Zip unavailable", operationError = "Обмен сетами сейчас недоступен")
                    }
                }
                is ExchangeZipResult.Ok -> {
                    val shareName = shareAfterExportFileName
                    shareAfterExportFileName = null
                    if (pendingImportedSetName != null) {
                        val saved = runCatching { registerImportedSet(result.archivePath, result.sizeBytes) }
                        if (saved.isFailure) {
                            pendingImportedSetName = null
                            _uiState.update { it.copy(busy = false, lastActionLabel = "Импорт выполнен, но карточка или архив сета не сохранены", operationError = "Импорт выполнен, но карточка или архив сета не сохранены") }
                            return@launch
                        }
                    }
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
                            pendingSharePath =
                                if (shareName != null && result.archivePath.isNotBlank()) {
                                    result.archivePath
                                } else {
                                    it.pendingSharePath
                                },
                            pendingShareFileName = shareName.orEmpty().ifBlank { it.pendingShareFileName },
                        )
                    }
                    performRefreshDiskGate()
                }
                is ExchangeZipResult.Failed -> {
                    shareAfterExportFileName = null
                    pendingImportedSetName = null
                    _uiState.update {
                        it.copy(
                            busy = false,
                            operationError = exchangeFailureMessage(result),
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
    }

    private fun exchangeFailureMessage(result: ExchangeZipResult.Failed): String {
        val detail = (listOf(result.message) + result.reasons).joinToString(" ").lowercase()
        return when {
            "style_selection_conflict" in detail -> "В сете есть материалы другого стиля или недоступные материалы. Откройте сет и исправьте состав"
            "media_missing" in detail || "missing_media" in detail ->
                "Не удалось найти файл музыки или видео. Проверьте материалы сета и повторите."
            "слишком большой" in detail || "лимит" in detail || "size_limit" in detail ->
                "Сет превышает допустимый размер. Уберите часть материалов и повторите."
            "мало места" in detail || "insufficient_space" in detail ->
                "Недостаточно свободного места для сета. Освободите место и повторите."
            "import" in detail ->
                "Не удалось импортировать сет. Проверьте выбранный файл и доступ к нему."
            else -> "Не удалось подготовить сет для передачи. Проверьте его материалы и повторите."
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
