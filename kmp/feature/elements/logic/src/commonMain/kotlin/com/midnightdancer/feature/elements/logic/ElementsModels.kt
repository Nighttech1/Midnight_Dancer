package com.midnightdancer.feature.elements.logic

import com.midnightdancer.core.domain.settings.ElementCategoryConfig

/**
 * Kinetic atelier element card — SKIE-friendly (sealed kind, immutable).
 */
data class ElementCard(
    val id: String,
    val title: String,
    val levelLabel: String,
    val category: String,
    /** Up to [ElementCategoryConfig.MAX_TAGS_PER_ELEMENT] tags (C-70). */
    val tags: List<String> = emptyList(),
    val durationLabel: String,
    val masteryPercent: Int,
    val coverUrl: String?,
    /** Platform-relative demo asset key; Android maps to raw resource. */
    val videoAssetKey: String = "splash_boot",
    val description: String = "",
    val kind: ElementCardKind,
    /** Dance style this element belongs to ([DanceStyleOption.id]); StyleSplitSelector filters by it. */
    val styleId: String = "",
    /** Pinned cards float to the top of the list (flag / bookmark tap). */
    val pinned: Boolean = false,
    /** User-picked video (content Uri string); null → bundled demo clip. */
    val videoUri: String? = null,
)

/** Editor sheet draft — new element when [cardId] is null, otherwise edits that card. */
data class ElementEditorDraft(
    val cardId: String?,
    val title: String = "",
    val styleId: String,
    /** Optional — empty tags are valid. */
    val tag: String? = null,
    val videoUri: String? = null,
    val description: String = "",
    /** Mastery 0..100 edited via the «Прогресс изучения» slider. */
    val masteryPercent: Int = 0,
    /** Probed video length from the editor player (C-189); 0 = unknown. */
    val durationMs: Long = 0L,
)

sealed interface ElementCardKind {
    data object VideoCover : ElementCardKind
    data object Waveform : ElementCardKind
    data object CueOverlay : ElementCardKind
}

data class DanceStyleOption(
    val id: String,
    val name: String,
    val bpmLabel: String,
)

enum class ElementsSortMode {
    ByLevel,
    Alphabetical,
}

/**
 * Elements tab — catalog title «Элементы», search / sort / category filter.
 */
data class ElementsUiState(
    val saving: Boolean = false,
    val saveError: String? = null,
    val title: String = "Элементы",
    val styles: List<DanceStyleOption> = DEFAULT_STYLES,
    val selectedStyleIndex: Int = 0,
    val searchQuery: String = "",
    val sortMode: ElementsSortMode = ElementsSortMode.ByLevel,
    val filterCategory: String? = null,
    val categories: List<String> = ElementCategoryConfig.DEFAULT_NAMES,
    val catalogCards: List<ElementCard> = DEFAULT_ELEMENT_CARDS,
    val cards: List<ElementCard> = visibleElementCards(
        catalog = DEFAULT_ELEMENT_CARDS,
        filterCategory = null,
        searchQuery = "",
        sortMode = ElementsSortMode.ByLevel,
        selectedStyleId = DEFAULT_STYLES.first().id,
    ),
    val playingCardId: String? = null,
    val detailCardId: String? = null,
    /** Non-null while the element editor sheet is open. */
    val editor: ElementEditorDraft? = null,
) {
    val selectedStyle: DanceStyleOption
        get() = styles.getOrElse(selectedStyleIndex) { styles.first() }

    val detailCard: ElementCard?
        get() = catalogCards.firstOrNull { it.id == detailCardId }

    val placeholderItems: List<String>
        get() = cards.map { it.title }
}

sealed interface ElementsIntent {
    data object StylePrevious : ElementsIntent
    data object StyleNext : ElementsIntent
    data object AddNewStyle : ElementsIntent
    data object AddNewElement : ElementsIntent
    data class SetSearchQuery(val query: String) : ElementsIntent
    data class SetSortMode(val mode: ElementsSortMode) : ElementsIntent
    data class SetFilterCategory(val category: String?) : ElementsIntent
    data class SetCategories(val categories: List<String>) : ElementsIntent
    data class PlayCard(val cardId: String) : ElementsIntent
    data object StopPlayback : ElementsIntent
    data class OpenDetail(val cardId: String) : ElementsIntent
    data object CloseDetail : ElementsIntent

    /** Open editor sheet: [cardId] null → new element draft. */
    data class OpenEditor(val cardId: String?) : ElementsIntent
    data class UpdateEditorDraft(val draft: ElementEditorDraft) : ElementsIntent
    data object SaveElement : ElementsIntent
    data object CloseEditor : ElementsIntent

    /** Flag / bookmark tap — pin card to the top of the list. */
    data class TogglePin(val cardId: String) : ElementsIntent
    data object RefreshPlaceholders : ElementsIntent

    /** @deprecated level chips removed — kept for test migration until callers update. */
    data class SelectFilter(val filterId: String) : ElementsIntent
}

internal val DEFAULT_STYLES: List<DanceStyleOption> = listOf(
    DanceStyleOption("sensual", "Sensual", "128 BPM"),
    DanceStyleOption("bachata", "Bachata", "118 BPM"),
    DanceStyleOption("kizomba", "Kizomba", "96 BPM"),
)

internal val DEFAULT_ELEMENT_CARDS: List<ElementCard> = emptyList()

@Suppress("unused")
private val LEGACY_DEFAULT_ELEMENT_CARDS: List<ElementCard> = buildList {
    fun card(
        id: String,
        title: String,
        level: String,
        duration: String,
        mastery: Int,
        kind: ElementCardKind,
        description: String = "Описание техники «$title». Держи контроль корпуса и дыхание.",
    ) = ElementCard(
        id = id,
        title = title,
        levelLabel = level,
        category = level,
        tags = ElementCategoryConfig.sanitizeTagsForItem(listOf(level)),
        durationLabel = duration,
        masteryPercent = mastery,
        coverUrl = null,
        videoAssetKey = "splash_boot",
        description = description,
        kind = kind,
        styleId = DEFAULT_STYLES[size % DEFAULT_STYLES.size].id,
    )
    add(
        card(
            id = "wave-to-self-swivels",
            title = "Волна к себе через свифлы",
            level = "Начинающий",
            duration = "03:40",
            mastery = 100,
            kind = ElementCardKind.VideoCover,
            description = "Мягкая волна корпуса через свифлы. Держи дыхание ровным, плечи свободны.",
        ),
    )
    add(card("lateral-wave", "Боковая волна (Lateral Wave)", "Начинающий", "—", 0, ElementCardKind.Waveform))
    add(card("sensual-head-roll", "Sensual Head Roll", "Средний", "01:15", 65, ElementCardKind.CueOverlay))
    add(card("madrid-step-pivot", "Мадридский шаг & поворот", "Базовый", "00:45", 15, ElementCardKind.CueOverlay))
    add(card("chest-pop-soft", "Chest Pop Soft", "Начинающий", "00:55", 40, ElementCardKind.VideoCover))
    add(card("hip-circle-8", "Круг бёдрами 8-ка", "Средний", "01:30", 55, ElementCardKind.CueOverlay))
    add(card("body-roll-down", "Body Roll вниз", "Про", "02:10", 80, ElementCardKind.VideoCover))
    add(card("shoulder-shimmy", "Shimmy плеч", "Базовый", "00:40", 25, ElementCardKind.Waveform))
    add(card("camel-walk", "Camel Walk", "Средний", "01:05", 50, ElementCardKind.CueOverlay))
    add(card("isolation-ribs", "Изоляция рёбер", "Начинающий", "01:20", 70, ElementCardKind.VideoCover))
    add(card("floor-wave", "Волна на полу", "Про", "02:45", 35, ElementCardKind.Waveform))
    add(card("spiral-turn", "Спиральный поворот", "Средний", "01:50", 60, ElementCardKind.CueOverlay))
    add(card("footwork-basic", "Базовый футворк", "Базовый", "01:00", 90, ElementCardKind.VideoCover))
    add(card("arm-flow", "Поток рук", "Начинающий", "00:50", 45, ElementCardKind.CueOverlay))
    add(card("contra-body", "Contra Body Movement", "Про", "02:00", 20, ElementCardKind.Waveform))
    add(card("pulse-sync", "Pulse Sync 128", "Средний", "01:12", 75, ElementCardKind.VideoCover))
    add(card("knee-drop", "Knee Drop Controlled", "Про", "01:40", 10, ElementCardKind.CueOverlay))
    add(card("sway-balance", "Sway Balance", "Базовый", "00:48", 85, ElementCardKind.VideoCover))
    add(card("cross-basic", "Cross Basic Sync", "Базовый", "00:42", 55, ElementCardKind.VideoCover))
    add(card("shadow-turn", "Shadow Turn", "Средний", "01:22", 48, ElementCardKind.CueOverlay))
    add(card("elastic-wave", "Elastic Wave", "Начинающий", "01:08", 62, ElementCardKind.Waveform))
    add(card("frame-hold", "Frame Hold Soft", "Базовый", "00:36", 78, ElementCardKind.VideoCover))
    add(card("figure-eight", "Figure Eight Hips", "Средний", "01:35", 41, ElementCardKind.CueOverlay))
    add(card("rise-fall", "Rise & Fall", "Про", "02:05", 28, ElementCardKind.Waveform))
    add(card("spot-turn", "Spot Turn Clean", "Начинающий", "00:58", 88, ElementCardKind.VideoCover))
    add(card("connection-pulse", "Connection Pulse", "Средний", "01:18", 52, ElementCardKind.CueOverlay))
    add(card("floor-spiral", "Floor Spiral", "Про", "02:20", 18, ElementCardKind.VideoCover))
    add(card("soft-break", "Soft Break Timing", "Базовый", "00:52", 66, ElementCardKind.Waveform))
    add(card("lead-follow-mirror", "Lead / Follow Mirror", "Средний", "01:44", 37, ElementCardKind.CueOverlay))
    add(card("accent-hit", "Accent Hit 4&", "Про", "01:10", 72, ElementCardKind.VideoCover))
}

/**
 * In-memory stub cards for Client edge-stress (EL-50+). Reuses demo [videoAssetKey].
 * Prefixed ids match Room seeder (`edge-stress-move-*`).
 */
fun buildEdgeStressElementCards(count: Int): List<ElementCard> {
    val n = count.coerceAtLeast(0)
    val levels = listOf("Начинающий", "Базовый", "Средний", "Про")
    val kinds =
        listOf(
            ElementCardKind.VideoCover,
            ElementCardKind.Waveform,
            ElementCardKind.CueOverlay,
        )
    return (1..n).map { i ->
        val level = levels[(i - 1) % levels.size]
        ElementCard(
            id = "edge-stress-move-$i",
            title = "Edge Stub Element $i",
            levelLabel = level,
            category = level,
            tags = ElementCategoryConfig.sanitizeTagsForItem(listOf(level)),
            durationLabel = "00:${(20 + i % 40).toString().padStart(2, '0')}",
            masteryPercent = (i * 7) % 101,
            coverUrl = null,
            videoAssetKey = "splash_boot",
            description = "DEBUG edge-stress stub #$i",
            kind = kinds[(i - 1) % kinds.size],
        )
    }
}

/** Stable style id from a Settings style name (shared by component + defaults). */
internal fun styleIdFromName(name: String): String =
    if (name.equals(com.midnightdancer.core.domain.styles.ReservedDanceStyle.NAME, true))
        com.midnightdancer.core.domain.styles.ReservedDanceStyle.ID
    else name.trim().lowercase().replace(' ', '-')

/**
 * Cards of [selectedStyleId] (or style [selectedStyleName]) → tag filter → search → sort.
 * Pinned cards always come first, then the regular sort order.
 */
internal fun visibleElementCards(
    catalog: List<ElementCard>,
    filterCategory: String?,
    searchQuery: String,
    sortMode: ElementsSortMode,
    selectedStyleId: String? = null,
    selectedStyleName: String? = null,
): List<ElementCard> {
    val query = searchQuery.trim()
    val filtered = catalog.filter { card ->
        val noStyleFilter = selectedStyleId.isNullOrBlank() && selectedStyleName.isNullOrBlank()
        val matchesStyle =
            noStyleFilter ||
                (!selectedStyleId.isNullOrBlank() && card.styleId.equals(selectedStyleId, ignoreCase = true)) ||
                (!selectedStyleName.isNullOrBlank() && card.styleId.equals(selectedStyleName, ignoreCase = true))
        if (!matchesStyle) return@filter false
        val matchesFilter =
            filterCategory.isNullOrBlank() ||
                card.category.equals(filterCategory, ignoreCase = true) ||
                card.tags.any { it.equals(filterCategory, ignoreCase = true) }
        val matchesSearch =
            query.isEmpty() || card.title.contains(query, ignoreCase = true)
        matchesFilter && matchesSearch
    }
    return when (sortMode) {
        ElementsSortMode.Alphabetical ->
            filtered.sortedWith(
                compareByDescending<ElementCard> { it.pinned }.thenBy { it.title.lowercase() },
            )
        ElementsSortMode.ByLevel ->
            filtered.sortedWith(
                compareByDescending<ElementCard> { it.pinned }
                    .thenBy { levelRank(it.levelLabel) }
                    .thenBy { it.title.lowercase() },
            )
    }
}

/** Re-home cards whose [ElementCard.styleId] no longer exists in Settings styles. */
internal fun List<ElementCard>.withValidStyles(styles: List<DanceStyleOption>): List<ElementCard> {
    if (styles.isEmpty()) return this
    return mapIndexed { index, card ->
        val known = styles.any {
            it.id.equals(card.styleId, ignoreCase = true) ||
                it.name.equals(card.styleId, ignoreCase = true)
        }
        if (known) card else card.copy(styleId = com.midnightdancer.core.domain.styles.ReservedDanceStyle.ID)
    }
}

/**
 * C-134: when a tag is deleted from Settings, strip it from cards.
 * Elements stay in the catalog — only the tag label is cleared.
 */
internal fun List<ElementCard>.withTagsClearedOutside(allowedTags: List<String>): List<ElementCard> {
    if (allowedTags.isEmpty()) {
        return map { card ->
            if (card.tags.isEmpty()) card
            else card.copy(tags = emptyList(), category = "", levelLabel = "")
        }
    }
    val allowed = allowedTags.map { it.lowercase() }.toSet()
    return map { card ->
        val kept = card.tags.filter { it.lowercase() in allowed }
        if (kept == card.tags) {
            card
        } else {
            val primary = kept.firstOrNull().orEmpty()
            card.copy(tags = kept, category = primary, levelLabel = primary)
        }
    }
}

private fun levelRank(label: String): Int =
    when {
        label.contains("Начин", ignoreCase = true) -> 0
        label.contains("Базов", ignoreCase = true) -> 1
        label.contains("Средн", ignoreCase = true) -> 2
        label.contains("Про", ignoreCase = true) -> 3
        else -> 9
    }

/** Legacy chip ids mapped to category filter for smoke tests. */
internal const val FILTER_ALL: String = "all"
internal const val FILTER_BEGINNER: String = "beginner"
internal const val FILTER_INTERMEDIATE: String = "intermediate"
internal const val FILTER_PRO: String = "pro"
