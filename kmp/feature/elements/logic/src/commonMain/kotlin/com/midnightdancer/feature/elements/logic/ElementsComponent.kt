package com.midnightdancer.feature.elements.logic

import com.midnightdancer.core.domain.styles.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.lifecycle.doOnResume
import com.midnightdancer.core.domain.debug.EdgeStressUiBridge
import com.midnightdancer.core.domain.settings.DanceStyleConfig
import com.midnightdancer.core.domain.settings.ElementCategoryConfig
import com.midnightdancer.core.domain.usecase.LoadLibrary
import com.midnightdancer.core.domain.usecase.ReadWriteUserPrefs
import com.midnightdancer.core.domain.usecase.SaveAppData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface ElementsComponent {
    val uiState: StateFlow<ElementsUiState>
    fun onIntent(intent: ElementsIntent)

    /** SKIE-friendly aliases for SwiftUI bridge (C-124). */
    fun stylePrevious() = onIntent(ElementsIntent.StylePrevious)
    fun styleNext() = onIntent(ElementsIntent.StyleNext)
    fun addNewElement() = onIntent(ElementsIntent.AddNewElement)
    fun setSearchQuery(query: String) = onIntent(ElementsIntent.SetSearchQuery(query))
    fun setFilterCategory(category: String?) = onIntent(ElementsIntent.SetFilterCategory(category))
    fun playCard(cardId: String) = onIntent(ElementsIntent.PlayCard(cardId))
    fun stopPlayback() = onIntent(ElementsIntent.StopPlayback)
    fun openDetail(cardId: String) = onIntent(ElementsIntent.OpenDetail(cardId))
    fun closeDetail() = onIntent(ElementsIntent.CloseDetail)
    fun togglePin(cardId: String) = onIntent(ElementsIntent.TogglePin(cardId))
    fun openEditor(cardId: String?) = onIntent(ElementsIntent.OpenEditor(cardId))
    fun saveElement() = onIntent(ElementsIntent.SaveElement)
    fun closeEditor() = onIntent(ElementsIntent.CloseEditor)
}

class DefaultElementsComponent(
    componentContext: ComponentContext,
    private val userPrefs: ReadWriteUserPrefs? = null,
    private val loadLibrary: LoadLibrary? = null,
    private val saveAppData: SaveAppData? = null,
    private val danceStyles: ManageDanceStyles? = null,
) : ElementsComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(ElementsUiState())
    override val uiState: StateFlow<ElementsUiState> = _uiState.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        // Prefs on resume; Room hydrate only on create (ChildPages DESTROY → new component).
        lifecycle.doOnResume { loadPrefsCatalog() }
        hydrateCatalogFromRoom()
        loadPrefsCatalog()
        danceStyles?.let { manager -> scope.launch { manager.state.filterNotNull().collect { snapshot ->
            _uiState.update { current ->
                val selected = snapshot.canonicalId(current.selectedStyle.id)
                val options = snapshot.entries.map { DanceStyleOption(it.id, it.name, "") }
                current.copy(styles = options, selectedStyleIndex = options.indexOfFirst { it.id == selected }.coerceAtLeast(0),
                    catalogCards = current.catalogCards.map { it.copy(styleId = snapshot.canonicalId(it.styleId)) },
                    editor = current.editor?.let { it.copy(styleId = snapshot.canonicalId(it.styleId)) }).recomputeVisible()
            }
        } } }
        scope.launch {
            EdgeStressUiBridge.requests.collectLatest { request ->
                if (request == null || request.elementCount <= 0) return@collectLatest
                _uiState.update { current ->
                    val stubs = buildEdgeStressElementCards(request.elementCount)
                        .map { it.copy(styleId = current.selectedStyle.id) }
                    val kept =
                        current.catalogCards.filterNot { it.id.startsWith("edge-stress-move-") }
                    current.copy(catalogCards = stubs + kept).recomputeVisible()
                }
            }
        }
    }

    override fun onIntent(intent: ElementsIntent) {
        when (intent) {
            ElementsIntent.StylePrevious ->
                _uiState.update { current ->
                    val next = (current.selectedStyleIndex - 1 + current.styles.size) % current.styles.size
                    current.copy(selectedStyleIndex = next).recomputeVisible()
                }
            ElementsIntent.StyleNext ->
                _uiState.update { current ->
                    val next = (current.selectedStyleIndex + 1) % current.styles.size
                    current.copy(selectedStyleIndex = next).recomputeVisible()
                }
            ElementsIntent.AddNewStyle -> {
                // Styles are managed in Settings (C-70); keep intent for smoke tests only.
            }
            // Add opens the editor sheet with an empty draft — nothing is inserted until Save.
            ElementsIntent.AddNewElement -> showEditor(null)
            is ElementsIntent.OpenEditor -> showEditor(intent.cardId)
            is ElementsIntent.UpdateEditorDraft ->
                _uiState.update { current ->
                    if (current.editor == null) current else current.copy(editor = intent.draft)
                }
            ElementsIntent.CloseEditor -> hideEditor()
            ElementsIntent.SaveElement -> saveEditor()
            is ElementsIntent.TogglePin ->
                _uiState.update { current ->
                    val target = current.catalogCards.find { it.id == intent.cardId }
                    val pinning = target != null && !target.pinned
                    val nextCards = current.catalogCards.map { card ->
                        when {
                            card.id == intent.cardId -> card.copy(pinned = pinning)
                            pinning -> card.copy(pinned = false)
                            else -> card
                        }
                    }
                    if (target != null) {
                        persistPin(
                            styleId = target.styleId.ifBlank { current.selectedStyle.id },
                            styleName = current.styles.firstOrNull {
                                it.id == target.styleId
                            }?.name ?: current.selectedStyle.name,
                            pinnedMoveId = if (pinning) intent.cardId else null,
                        )
                    }
                    current.copy(catalogCards = nextCards).recomputeVisible()
                }
            is ElementsIntent.SetSearchQuery ->
                _uiState.update { current ->
                    current.copy(searchQuery = intent.query).recomputeVisible()
                }
            is ElementsIntent.SetSortMode ->
                _uiState.update { current ->
                    current.copy(sortMode = intent.mode).recomputeVisible()
                }
            is ElementsIntent.SetFilterCategory ->
                _uiState.update { current ->
                    current.copy(filterCategory = intent.category).recomputeVisible()
                }
            is ElementsIntent.SetCategories ->
                _uiState.update { current ->
                    val cats = ElementCategoryConfig.sanitizeList(intent.categories)
                    // C-134: deleting a tag clears it from cards — media stays.
                    current.copy(
                        categories = cats,
                        catalogCards = current.catalogCards.withTagsClearedOutside(cats),
                        filterCategory = current.filterCategory?.takeIf { name ->
                            cats.any { it.equals(name, ignoreCase = true) }
                        },
                    ).recomputeVisible()
                }
            // Play on a card opens the SAME editor sheet (video preview + transport lives there).
            is ElementsIntent.PlayCard -> showEditor(intent.cardId)
            ElementsIntent.StopPlayback ->
                _uiState.update { it.copy(playingCardId = null) }
            is ElementsIntent.OpenDetail -> showEditor(intent.cardId)
            ElementsIntent.CloseDetail -> hideEditor()
            ElementsIntent.RefreshPlaceholders -> {
                _uiState.value = ElementsUiState(categories = _uiState.value.categories)
                _uiState.update { it.recomputeVisible() }
                hydrateCatalogFromRoom()
            }
            is ElementsIntent.SelectFilter ->
                _uiState.update { current ->
                    val category = legacyFilterToCategory(intent.filterId)
                    current.copy(filterCategory = category).recomputeVisible()
                }
        }
    }

    private fun showEditor(cardId: String?) {
        _uiState.update { current ->
            val existing = cardId?.let { id -> current.catalogCards.firstOrNull { it.id == id } }
            val draft = if (existing != null) {
                ElementEditorDraft(
                    cardId = existing.id,
                    title = existing.title,
                    styleId = existing.styleId.ifBlank { current.selectedStyle.id },
                    tag = existing.tags.firstOrNull(),
                    videoUri = existing.videoUri,
                    description = existing.description,
                    masteryPercent = existing.masteryPercent.coerceIn(0, 100),
                    durationMs = parseCardDurationMs(existing.durationLabel),
                )
            } else {
                ElementEditorDraft(cardId = null, styleId = current.selectedStyle.id)
            }
            current.copy(
                editor = draft,
                detailCardId = existing?.id,
                playingCardId = null,
            )
        }
    }

    private fun hideEditor() {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(editor = null, detailCardId = null, playingCardId = null) }
    }

    private fun saveEditor() {
        val current = _uiState.value
        if (current.saving) return
        val draft = current.editor ?: return
        val tags = ElementCategoryConfig.sanitizeTagsForItem(listOfNotNull(draft.tag))
        val tag = tags.firstOrNull().orEmpty()
        val styleId = danceStyles?.state?.value?.canonicalId(draft.styleId.ifBlank { current.selectedStyle.id })
            ?: draft.styleId.ifBlank { current.selectedStyle.id }
        val styleName = current.styles.firstOrNull { it.id == styleId }?.name ?: current.selectedStyle.name
        val old = current.catalogCards.firstOrNull { it.id == draft.cardId }
        var n = current.catalogCards.size + 1
        while (current.catalogCards.any { it.id == "new-element-$n" }) n++
        val card = if (old != null) old.copy(
            title = draft.title.trim().ifBlank { old.title }, levelLabel = tag, category = tag, tags = tags,
            description = draft.description, masteryPercent = draft.masteryPercent.coerceIn(0, 100), styleId = styleId,
            videoUri = draft.videoUri ?: old.videoUri, coverUrl = draft.videoUri ?: old.videoUri ?: old.coverUrl,
            durationLabel = if (draft.durationMs > 0L) com.midnightdancer.core.domain.media.ElementDurationCodec.formatLabel(draft.durationMs) else old.durationLabel,
        ) else ElementCard(
            id = "new-element-$n", title = draft.title.trim().ifBlank { "Новый элемент $n" },
            levelLabel = tag, category = tag, tags = tags,
            durationLabel = com.midnightdancer.core.domain.media.ElementDurationCodec.formatLabel(draft.durationMs),
            masteryPercent = draft.masteryPercent.coerceIn(0, 100), coverUrl = draft.videoUri,
            description = draft.description, kind = ElementCardKind.VideoCover, styleId = styleId, videoUri = draft.videoUri,
        )
        if (saveAppData == null) {
            _uiState.update { latest ->
                val catalog = if (latest.catalogCards.any { it.id == card.id })
                    latest.catalogCards.map { if (it.id == card.id) card else it }
                else listOf(card) + latest.catalogCards
                latest.copy(catalogCards = catalog, editor = null, detailCardId = null, playingCardId = null,
                    saving = false, saveError = null).recomputeVisible()
            }
            return
        }
        _uiState.update { it.copy(saving = true, saveError = null) }
        scope.launch {
            try {
                withContext(NonCancellable + Dispatchers.Default) {
                    saveAppData?.let { save ->
                        save.ensureDanceStyle(id = styleId, name = styleName.ifBlank { styleId })
                        val ms = draft.durationMs.takeIf { it > 0L } ?: parseCardDurationMs(card.durationLabel)
                        save.upsertMove(styleId = styleId, move = card.toMove(durationMs = ms))
                    }
                }
                _uiState.update { latest ->
                    val committed = card.copy(styleId = danceStyles?.state?.value?.canonicalId(card.styleId) ?: card.styleId)
                    val catalog = if (latest.catalogCards.any { it.id == committed.id })
                        latest.catalogCards.map { if (it.id == committed.id) committed else it }
                    else listOf(committed) + latest.catalogCards
                    latest.copy(catalogCards = catalog, editor = null, detailCardId = null, playingCardId = null,
                        saving = false, saveError = null).recomputeVisible()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(saving = false, saveError = "Не удалось сохранить элемент. Данные не заменены. Попробуйте снова.") }
            }
        }
    }

    private fun parseCardDurationMs(label: String): Long {
        val parts = label.trim().split(':')
        if (parts.size != 2) return 0L
        val m = parts[0].toLongOrNull() ?: return 0L
        val s = parts[1].toLongOrNull() ?: return 0L
        return (m * 60L + s) * 1000L
    }

    private fun persistPin(styleId: String, styleName: String, pinnedMoveId: String?) {
        val save = saveAppData ?: return
        scope.launch {
            withContext(NonCancellable + Dispatchers.Default) {
                save.ensureDanceStyle(
                    id = styleId.ifBlank { "default" },
                    name = styleName.ifBlank { styleId },
                    currentMoveId = pinnedMoveId,
                )
            }
        }
    }

    private fun hydrateCatalogFromRoom() {
        val load = loadLibrary ?: return
        scope.launch {
            val catalogFlow = danceStyles?.let { manager -> combine(load.observe(), manager.state.filterNotNull()) { data, _ -> data } } ?: load.observe()
            catalogFlow.collect { data ->
                val snapshot = danceStyles?.state?.value
                val cards = data.toElementCards().map { card ->
                    if (snapshot == null) card else card.copy(styleId = snapshot.canonicalId(card.styleId))
                }
                _uiState.update { current -> current.copy(catalogCards = cards).recomputeVisible() }
            }
        }
    }

    private fun loadPrefsCatalog() {
        val prefs = userPrefs ?: return
        scope.launch {
            val (tagNames, styleNames) = withContext(Dispatchers.Default) {
                prefs.readElementCategories().names to prefs.readDanceStyles().names
            }
            val styles = DanceStyleConfig.sanitizeList(styleNames).map { name ->
                DanceStyleOption(
                    id = styleIdFromName(name),
                    name = name,
                    bpmLabel = "",
                )
            }.ifEmpty {
                DanceStyleConfig.DEFAULT_NAMES.map { name ->
                    DanceStyleOption(
                        id = styleIdFromName(name),
                        name = name,
                        bpmLabel = "",
                    )
                }
            }
            _uiState.update { current ->
                val idx = current.selectedStyleIndex.coerceIn(0, (styles.size - 1).coerceAtLeast(0))
                val cats = ElementCategoryConfig.sanitizeList(tagNames)
                current.copy(
                    categories = cats,
                    styles = if (danceStyles == null) styles else current.styles,
                    selectedStyleIndex = if (danceStyles == null) idx else current.selectedStyleIndex,
                    catalogCards = current.catalogCards
                        .let { if (danceStyles == null) it.withValidStyles(styles) else it }
                        .withTagsClearedOutside(cats),
                    filterCategory = current.filterCategory?.takeIf { name ->
                        cats.any { it.equals(name, ignoreCase = true) }
                    },
                ).recomputeVisible()
            }
        }
    }
}

private fun legacyFilterToCategory(filterId: String): String? =
    when (filterId) {
        FILTER_ALL -> null
        FILTER_BEGINNER -> "Начинающий"
        FILTER_INTERMEDIATE -> "Средний"
        FILTER_PRO -> "Про"
        else -> null
    }

private fun ElementsUiState.recomputeVisible(): ElementsUiState =
    copy(
        cards = visibleElementCards(
            catalog = catalogCards,
            filterCategory = filterCategory,
            searchQuery = searchQuery,
            sortMode = sortMode,
            selectedStyleId = selectedStyle.id,
            selectedStyleName = selectedStyle.name,
        ),
    )
