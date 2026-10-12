package com.midnightdancer.feature.music.logic

import com.midnightdancer.core.domain.settings.ElementCategoryConfig
import com.midnightdancer.core.domain.validation.DomainValidators

/**
 * Music Motion Deck (C-62 / C-71 / C-76).
 * Playback engine later; rich mocks drive premium UI.
 */
data class MusicTrack(
    val id: String,
    val title: String,
    val style: String,
    /** Legacy single label; prefer [tags] for UI. */
    val level: String,
    val tags: List<String> = emptyList(),
    val bpm: Int,
    val durationLabel: String,
    /** File weight / size shown bottom-left with style · duration. */
    val weightLabel: String,
    val sizeLabel: String = weightLabel,
    val bitDepthLabel: String = "24-bit",
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val positionLabel: String = "00:00",
    /** Base listening speed 0.4–2.0 — persists with the track everywhere. */
    val basePlaybackSpeed: Double = 1.0,
)

enum class MusicSortMode {
    ByTag,
    Alphabetical,
}

data class MusicUiState(
    val title: String = "Музыка",
    val subtitle: String = "",
    val tracks: List<MusicTrack> = DEFAULT_TRACKS,
    val selectedStyle: String = "Все стили",
    val selectedLevel: String = "Все теги",
    val activeChip: String? = null,
    val searchQuery: String = "",
    val sortMode: MusicSortMode = MusicSortMode.ByTag,
    val editingTrackId: String? = null,
    val tagCatalog: List<String> = ElementCategoryConfig.DEFAULT_NAMES,
    val styleCatalog: List<String> = MOCK_STYLE_CATALOG,
) {
    /** Legacy alias for smoke tests / shell lists. */
    val placeholderTracks: List<String>
        get() = tracks.map { it.title }

    val playingTrack: MusicTrack?
        get() = tracks.firstOrNull { it.isPlaying }

    val libraryCount: Int
        get() = tracks.size

    val editingTrack: MusicTrack?
        get() = tracks.firstOrNull { it.id == editingTrackId }

    val filteredTracks: List<MusicTrack>
        get() = visibleMusicTracks(
            tracks = tracks,
            selectedStyle = selectedStyle,
            selectedTag = selectedLevel,
            searchQuery = searchQuery,
            sortMode = sortMode,
        )
}

sealed interface MusicIntent {
    data object RefreshPlaceholders : MusicIntent
    data class SelectTrack(val id: String) : MusicIntent
    data class StopTrack(val id: String) : MusicIntent
    data class SetStyleFilter(val style: String) : MusicIntent
    data class SetLevelFilter(val level: String) : MusicIntent
    data class SetChip(val chip: String?) : MusicIntent
    data class DeleteTrack(val id: String) : MusicIntent
    data class EditTrack(val id: String) : MusicIntent
    /** Creates a draft track and opens TrackEditDialog (replaces «Стиль и тег»). */
    data object BeginUpload : MusicIntent
    data object CloseEdit : MusicIntent
    data class SetSearchQuery(val query: String) : MusicIntent
    data class SetSortMode(val mode: MusicSortMode) : MusicIntent
    data class SaveTrackEdit(
        val id: String,
        val style: String,
        val tags: List<String>,
        val basePlaybackSpeed: Double,
    ) : MusicIntent
}

internal fun visibleMusicTracks(
    tracks: List<MusicTrack>,
    selectedStyle: String,
    selectedTag: String,
    searchQuery: String,
    sortMode: MusicSortMode,
): List<MusicTrack> {
    val q = searchQuery.trim()
    val filtered = tracks.filter { track ->
        val styleOk =
            selectedStyle == "Все стили" || track.style.equals(selectedStyle, ignoreCase = true)
        val tagOk =
            selectedTag == "Все теги" ||
                track.tags.any { it.equals(selectedTag, ignoreCase = true) } ||
                track.level.equals(selectedTag, ignoreCase = true)
        val searchOk =
            q.isEmpty() || track.title.contains(q, ignoreCase = true)
        styleOk && tagOk && searchOk
    }
    return when (sortMode) {
        MusicSortMode.Alphabetical -> filtered.sortedBy { it.title.lowercase() }
        MusicSortMode.ByTag ->
            filtered.sortedWith(
                compareBy<MusicTrack> {
                    tagRank(it.tags.firstOrNull() ?: it.level)
                }.thenBy { it.title.lowercase() },
            )
    }
}

private fun tagRank(label: String): Int =
    when {
        label.contains("Начин", ignoreCase = true) -> 0
        label.contains("Базов", ignoreCase = true) -> 1
        label.contains("Средн", ignoreCase = true) -> 2
        label.contains("Про", ignoreCase = true) -> 3
        else -> 9
    }

private fun track(
    id: String,
    title: String,
    style: String,
    tag: String,
    bpm: Int,
    duration: String,
    weight: String,
    playing: Boolean = false,
    progress: Float = 0f,
    position: String = "00:00",
    speed: Double = 1.0,
) = MusicTrack(
    id = id,
    title = title,
    style = style,
    level = tag,
    tags = ElementCategoryConfig.sanitizeTagsForItem(listOf(tag)),
    bpm = bpm,
    durationLabel = duration,
    weightLabel = weight,
    sizeLabel = weight,
    isPlaying = playing,
    progress = progress,
    positionLabel = position,
    basePlaybackSpeed = speed.coerceIn(
        DomainValidators.PLAYBACK_SPEED_MIN,
        DomainValidators.PLAYBACK_SPEED_MAX,
    ),
)

internal val MOCK_STYLE_CATALOG: List<String> = listOf(
    "Бачата",
    "Сальса",
    "Sensual",
    "Kizomba",
    "Dominican",
    "Modern",
    "Zouk",
    "Танго",
    "Cha-cha",
    "Merengue",
)

/**
 * In-memory stub tracks for Client edge-stress (MU-50+).
 * Prefixed ids match Room seeder (`edge-stress-song-*`).
 */
fun buildEdgeStressTracks(count: Int): List<MusicTrack> {
    val n = count.coerceAtLeast(0)
    val levels = listOf("Начинающий", "Базовый", "Средний", "Про")
    return (1..n).map { i ->
        val tag = levels[(i - 1) % levels.size]
        MusicTrack(
            id = "edge-stress-song-$i",
            title = "Edge Stub Track $i",
            style = "Edge Stress",
            level = tag,
            tags = ElementCategoryConfig.sanitizeTagsForItem(listOf(tag)),
            bpm = 110 + (i % 30),
            durationLabel = "0${(i % 5) + 1}:${(i * 3 % 60).toString().padStart(2, '0')}",
            weightLabel = "${1 + i % 12}.${i % 10} MB",
        )
    }
}

internal val DEFAULT_TRACKS: List<MusicTrack> = listOf(
    track(
        id = "t1",
        title = "Charles Luis - Ocasión",
        style = "Бачата",
        tag = "Начинающий",
        bpm = 124,
        duration = "03:08",
        weight = "7.2 MB",
        playing = true,
        progress = 0.55f,
        position = "01:42",
        speed = 1.0,
    ),
    track("t2", "Jensen - Carita", "Бачата", "Начинающий", 118, "02:59", "6.8 MB"),
    track("t3", "Romeo Santos - Propuesta", "Бачата", "Средний", 126, "03:44", "8.1 MB"),
    track("t4", "Marc Anthony - Vivir Mi Vida", "Сальса", "Средний", 132, "04:12", "9.4 MB"),
    track("t5", "Prince Royce - Darte un Beso", "Бачата", "Базовый", 120, "03:21", "7.0 MB"),
    track("t6", "Aventura - Obsesión", "Бачата", "Про", 128, "04:05", "8.9 MB"),
    track("t7", "Celia Cruz - La Vida Es Un Carnaval", "Сальса", "Средний", 130, "04:38", "10.2 MB"),
    track("t8", "Grupo Niche - Cali Pachanguero", "Сальса", "Про", 134, "05:02", "11.0 MB"),
)
