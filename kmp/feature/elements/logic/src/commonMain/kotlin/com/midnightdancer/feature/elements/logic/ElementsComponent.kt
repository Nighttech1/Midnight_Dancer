package com.midnightdancer.feature.elements.logic

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.lifecycle.doOnResume
import com.midnightdancer.core.domain.debug.EdgeStressUiBridge
import com.midnightdancer.core.domain.settings.DanceStyleConfig
import com.midnightdancer.core.domain.settings.ElementCategoryConfig
import com.midnightdancer.core.domain.usecase.ReadWriteUserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
}

class DefaultElementsComponent(
    componentContext: ComponentContext,
    private val userPrefs: ReadWriteUserPrefs? = null,
) : ElementsComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(ElementsUiState())
    override val uiState: StateFlow<ElementsUiState> = _uiState.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        lifecycle.doOnResume { loadPrefsCatalog() }
        loadPrefsCatalog()
        scope.launch {
            EdgeStressUiBridge.requests.collectLatest { request ->
                if (request == null || request.elementCount <= 0) return@collectLatest
                val stubs = buildEdgeStressElementCards(request.elementCount)
                _uiState.update { current ->
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
                    current.copy(selectedStyleIndex = next)
                }
            ElementsIntent.StyleNext ->
                _uiState.update { current ->
                    val next = (current.selectedStyleIndex + 1) % current.styles.size
                    current.copy(selectedStyleIndex = next)
                }
            ElementsIntent.AddNewStyle -> {
                // Styles are managed in Settings (C-70); keep intent for smoke tests only.
            }
            ElementsIntent.AddNewElement ->
                _uiState.update { current ->
                    val n = current.catalogCards.size + 1
                    val seedTag = current.filterCategory ?: "Базовый"
                    val card = ElementCard(
                        id = "new-element-$n",
                        title = "Новый элемент $n",
                        levelLabel = seedTag,
                        category = seedTag,
                        tags = ElementCategoryConfig.sanitizeTagsForItem(listOf(seedTag)),
                        durationLabel = "00:30",
                        masteryPercent = 0,
                        coverUrl = null,
                        description = "Черновик нового элемента.",
                        kind = ElementCardKind.VideoCover,
                    )
                    current.copy(
                        catalogCards = listOf(card) + current.catalogCards,
                    ).recomputeVisible()
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
                    current.copy(
                        categories = ElementCategoryConfig.sanitizeList(intent.categories),
                    ).recomputeVisible()
                }
            is ElementsIntent.PlayCard ->
                _uiState.update { current ->
                    val id = intent.cardId
                    current.copy(
                        playingCardId = if (current.playingCardId == id) null else id,
                    )
                }
            ElementsIntent.StopPlayback ->
                _uiState.update { it.copy(playingCardId = null) }
            is ElementsIntent.OpenDetail ->
                _uiState.update {
                    it.copy(
                        detailCardId = intent.cardId,
                        playingCardId = null,
                    )
                }
            ElementsIntent.CloseDetail ->
                _uiState.update { it.copy(detailCardId = null) }
            ElementsIntent.RefreshPlaceholders -> {
                _uiState.value = ElementsUiState(categories = _uiState.value.categories)
                _uiState.update { it.recomputeVisible() }
            }
            is ElementsIntent.SelectFilter ->
                _uiState.update { current ->
                    val category = legacyFilterToCategory(intent.filterId)
                    current.copy(filterCategory = category).recomputeVisible()
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
                    id = name.lowercase().replace(' ', '-'),
                    name = name,
                    bpmLabel = "",
                )
            }.ifEmpty {
                DanceStyleConfig.DEFAULT_NAMES.map { name ->
                    DanceStyleOption(
                        id = name.lowercase(),
                        name = name,
                        bpmLabel = "",
                    )
                }
            }
            _uiState.update { current ->
                val idx = current.selectedStyleIndex.coerceIn(0, (styles.size - 1).coerceAtLeast(0))
                current.copy(
                    categories = tagNames,
                    styles = styles,
                    selectedStyleIndex = idx,
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
        ),
    )
