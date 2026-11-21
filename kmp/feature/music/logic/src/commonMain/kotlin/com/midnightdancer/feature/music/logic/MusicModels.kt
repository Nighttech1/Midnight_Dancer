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
    /** Content URI of picked audio file (Android SAF). */
    val mediaUri: String? = null,
    /** Exact source duration, retained independently of the rounded display label. */
    val durationMs: Long? = null,
)

enum class MusicSortMode {
    ByTag,
    Alphabetical,
}

data class MusicUiState(
    val saving: Boolean = false,
    val operationError: String? = null,
    val title: String = "Музыка",
    val subtitle: String = "",
    val tracks: List<MusicTrack> = DEFAULT_TRACKS,
    val selectedStyle: String = "Все стили",
    val selectedLevel: String = "Все",
    val activeChip: String? = null,
    val searchQuery: String = "",
    val sortMode: MusicSortMode = MusicSortMode.ByTag,
    val editingTrackId: String? = null,
    /** C-152: stays set after Stop so glow + scrubber remain until another track / delete. */
    val selectedTrackId: String? = null,
    val tagCatalog: List<String> = ElementCategoryConfig.DEFAULT_NAMES,
    val styleCatalog: List<String> = MOCK_STYLE_CATALOG,
    val styleNamesById: Map<String, String> = emptyMap(),
) {
    fun displayStyle(value: String): String = styleNamesById[value] ?: value
    /** Legacy alias for smoke tests / shell lists. */
    val placeholderTracks: List<String>
        get() = tracks.map { it.title }

    val playingTrack: MusicTrack?
        get() = tracks.firstOrNull { it.isPlaying }

    val libraryCount: Int
        get() = tracks.size

    val selectedTrack: MusicTrack?
        get() = tracks.firstOrNull { it.id == selectedTrackId }

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
    /** Playback position 0..1 from the player ticker / scrubber (C-132). */
    data class SeekTrack(val id: String, val progress: Float) : MusicIntent
    data class SetStyleFilter(val style: String) : MusicIntent
    data class SetLevelFilter(val level: String) : MusicIntent
    data class SetChip(val chip: String?) : MusicIntent
    data class DeleteTrack(val id: String) : MusicIntent
    data class EditTrack(val id: String) : MusicIntent
    /** Creates a draft track and opens TrackEditDialog (replaces «Стиль и тег»). */
    data object BeginUpload : MusicIntent
    /** Android file picker result: draft track named from the file; style/tags stay blank. */
    data class AddPickedTrack(val title: String, val mediaUri: String, val durationMs: Long? = null) : MusicIntent
    /** Real duration from ExoPlayer after metadata is known. */
    data class UpdateTrackDuration(val id: String, val durationMs: Long) : MusicIntent
    data object CloseEdit : MusicIntent
    data class SetSearchQuery(val query: String) : MusicIntent
    data class SetSortMode(val mode: MusicSortMode) : MusicIntent
    data class SaveTrackEdit(
        val id: String,
        /** Display name — editable in TrackEditDialog (C-180). */
        val title: String,
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
            selectedTag.equals("Все", ignoreCase = true) ||
                selectedTag.equals("Все теги", ignoreCase = true) ||
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

internal val DEFAULT_TRACKS: List<MusicTrack> = emptyList()
