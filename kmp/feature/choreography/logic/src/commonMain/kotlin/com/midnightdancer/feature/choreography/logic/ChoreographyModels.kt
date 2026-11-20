package com.midnightdancer.feature.choreography.logic

import com.midnightdancer.core.domain.settings.ElementCategoryConfig
import com.midnightdancer.core.domain.validation.DomainValidators

/**
 * Kinetic Vault + Pro Editor (C-63 / C-64 / C-72 / C-73 / C-77).
 * Stitch: `midnight_dancer_kinetic_vault`, `midnight_dancer_pro_editor`.
 */

enum class VaultStatus {
    Draft,
    Ready,
}

enum class ChoreographySortMode {
    ByTag,
    Alphabetical,
    ByDuration,
}

/** Clip embedded in a saved choreography — survives catalog deletion (label + own duration). */
data class EditorClip(
    val label: String,
    val durationMs: Long,
    /** Content URI for Media3 preview / playback (C-190). */
    val mediaUri: String? = null,
    /**
     * Element playback / timeline speed (0.5–2.0). Displayed width ∝ durationMs / speed (C-216).
     * Music lane uses [SequenceEditorUiState.songPlaybackSpeed] instead.
     */
    val speed: Double = 1.0,
    /**
     * Offset into the source media (ms). After Split, the second half keeps the same URI but
     * starts at the cut point so playback continues — not from 0 (C-242).
     */
    val sourceStartMs: Long = 0L,
)

/** Persisted editor state for a vault item (in-memory until Room wave). */
data class ChoreographySnapshot(
    val title: String,
    val style: String,
    val elementClips: List<EditorClip> = emptyList(),
    val musicClips: List<EditorClip> = emptyList(),
    val songPlaybackSpeed: Double = 1.0,
    val musicVolume: Float = 1f,
    /** Optional single tag chosen in the create dialog (blank = none). */
    val tag: String = "",
)

data class ChoreographyVaultItem(
    val id: String,
    val title: String,
    val status: VaultStatus,
    val style: String,
    val tags: List<String> = emptyList(),
    val bpm: Int,
    val durationLabel: String,
    val trackName: String,
    val chrId: String,
    /** Relative bar heights 0..1 — unused in C-72 UI (waveform removed). */
    val sequencerBars: List<Float> = emptyList(),
    /**
     * Speed of attached track inside this choreography (0.5–2.0).
     * Independent of Song.basePlaybackSpeed.
     */
    val songPlaybackSpeed: Double = 1.0,
    /** Saved editor state; null for demo seed items. */
    val snapshot: ChoreographySnapshot? = null,
)

data class ChoreographyUiState(
    val styleNamesById: Map<String, String> = emptyMap(),
    val creationBlockedMessage: String? = null,
    val title: String = "Хореография",
    val vaultBadge: String = "",
    val items: List<ChoreographyVaultItem> = DEFAULT_VAULT_ITEMS,
    val searchQuery: String = "",
    val selectedStyle: String = "Все стили",
    val selectedTag: String = "Все",
    val sortMode: ChoreographySortMode = ChoreographySortMode.ByTag,
    val sortLabel: String = "By tag",
    val tagCatalog: List<String> = ElementCategoryConfig.DEFAULT_NAMES,
    /** Styles offered in the create / rename dialog. */
    val availableStyles: List<String> = ChoreoCatalog.styles,
) {
    fun displayStyle(value: String): String = styleNamesById[value] ?: value

    val placeholderItems: List<String>
        get() = items.map { it.title }

    val filteredItems: List<ChoreographyVaultItem>
        get() {
            val q = searchQuery.trim()
            val filtered = items.filter { item ->
                val styleOk =
                    selectedStyle == "Все стили" || (item.style.equals(selectedStyle, ignoreCase = true) || displayStyle(item.style).equals(selectedStyle, true))
                val tagOk =
                    selectedTag.equals("Все", ignoreCase = true) ||
                        selectedTag.equals("Все теги", ignoreCase = true) ||
                        item.tags.any { it.equals(selectedTag, ignoreCase = true) }
                val searchOk =
                    q.isEmpty() ||
                        item.title.contains(q, ignoreCase = true) ||
                        item.trackName.contains(q, ignoreCase = true)
                styleOk && tagOk && searchOk
            }
            return when (sortMode) {
                ChoreographySortMode.Alphabetical -> filtered.sortedBy { it.title.lowercase() }
                ChoreographySortMode.ByDuration -> filtered.sortedBy { it.durationLabel }
                ChoreographySortMode.ByTag ->
                    filtered.sortedWith(
                        compareBy<ChoreographyVaultItem> {
                            it.tags.firstOrNull().orEmpty()
                        }.thenBy { it.title.lowercase() },
                    )
            }
        }
}

sealed interface ChoreographyIntent {
    data object DismissAccessMessage : ChoreographyIntent
    data object OpenSequenceEditorStub : ChoreographyIntent
    data object RefreshPlaceholders : ChoreographyIntent
    data object CreateChoreography : ChoreographyIntent

    /** Create dialog confirmed: open a fresh editor with name + style (3.1). */
    data class CreateNamed(
        val name: String,
        val style: String,
        /** Optional tag (blank = none) persisted on the created vault item. */
        val tag: String = "",
    ) : ChoreographyIntent
    data class CopyItem(val id: String) : ChoreographyIntent
    data class OpenEditor(val id: String) : ChoreographyIntent
    data class SetSearchQuery(val query: String) : ChoreographyIntent
    data class SetStyleFilter(val style: String) : ChoreographyIntent
    data class SetTagFilter(val tag: String) : ChoreographyIntent
    data class SetSortMode(val mode: ChoreographySortMode) : ChoreographyIntent
    data class DeleteItem(val id: String) : ChoreographyIntent
    data class EditItem(val id: String) : ChoreographyIntent
    data class SetItemSongPlaybackSpeed(val id: String, val speed: Double) : ChoreographyIntent
}

data class EditorLane(
    val id: String,
    val label: String,
    val kind: LaneKind,
    val fillStart: Float,
    val fillEnd: Float,
)

enum class LaneKind {
    Video,
    Audio,
    Markers,
}

data class SequenceEditorUiState(
    val title: String = "Pro Editor",
    val styleNamesById: Map<String, String> = emptyMap(),
    val bpm: Int = 128,
    val resolutionLabel: String = "4K · 60FPS",
    val timecode: String = "00.00.00",
    val isPlaying: Boolean = false,
    val playheadFraction: Float = 0f,
    val snapToBpm: Boolean = true,
    val lanes: List<EditorLane> = DEFAULT_EDITOR_LANES,
    /** Style this choreography belongs to (scopes element / music pickers). */
    val style: String = ChoreoCatalog.styles.first(),
    val availableStyles: List<String> = ChoreoCatalog.styles,
    /** Tag chosen at creation (blank = none); carried into the saved vault item. */
    val tag: String = "",
    /** Tag catalog for rename dialog (C-191). */
    val tagCatalog: List<String> = ElementCategoryConfig.DEFAULT_NAMES,
    /**
     * Timeline zoom 0.1..1.0 (10%–100%). Lower = compressed bars, higher = longer bars
     * with horizontal scroll. Pure view setting — not part of undo / save.
     */
    val timelineZoom: Float = DEFAULT_TIMELINE_ZOOM,
    /** Element clips on the timeline, each with its own real duration (3.8). */
    val elementClips: List<EditorClip> = emptyList(),
    /** Music clips; lane length = sum of own durations, not forced to elements (3.7). */
    val musicClips: List<EditorClip> = emptyList(),
    /** Index of selected element block on timeline (−1 = none). */
    val selectedElementIndex: Int = -1,
    val selectedMusicIndex: Int = -1,
    val musicVolume: Float = 1f,
    val clipSpeed: Double = 1.0,
    /** Preview label for current silent element video. */
    val previewElementLabel: String? = null,
    /** Choreo-local track speed (0.5–2), independent of library base. */
    val songPlaybackSpeed: Double = 1.0,
    /** Total choreography length in ms — initially = attached track length. */
    val durationMs: Long = 0L,
    val durationLabel: String = "00:00",
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val placeholderItems: List<String> = listOf("Video A", "Audio bed", "Move markers"),
    /** Incremented on each Save so UI can flash a confirmation. */
    val saveCount: Int = 0,
    /** C-272: bumps after Room catalog reload so picker recomposes with new elements. */
    val catalogRevision: Int = 0,
) {
    val styleDisplayName: String get() = styleNamesById[style] ?: style
    val addedElementLabels: List<String> get() = elementClips.map { it.label }
    val addedMusicLabels: List<String> get() = musicClips.map { it.label }

    /** Element clips with effective timeline width: durationMs / clip.speed (C-216). */
    val elementTimelineClips: List<EditorClip>
        get() = elementClips.map {
            it.copy(durationMs = EditorTrackDurations.effectiveMs(it.durationMs, it.speed))
        }

    /** Elements lane length after per-clip speed: faster = shorter. */
    val elementsDurationMs: Long get() = elementTimelineClips.sumOf { it.durationMs }

    /** Music clips with effective length on the timeline: durationMs / songPlaybackSpeed (4.3.5). */
    val musicTimelineClips: List<EditorClip>
        get() = musicClips.map { it.copy(durationMs = EditorTrackDurations.effectiveMs(it.durationMs, songPlaybackSpeed)) }

    /** Music lane length after speed: faster = shorter. */
    val musicDurationMs: Long get() = musicTimelineClips.sumOf { it.durationMs }

    /** Total length in the same MM.SS.cs format as [timecode] (4.3.2). */
    val durationTimecode: String get() = EditorTrackDurations.formatTimecode(durationMs)

    /** Playhead position in ms on the shared timeline axis. */
    val playheadMs: Long get() = (durationMs * playheadFraction.coerceIn(0f, 1f)).toLong()

    /** Index of the element clip under the playhead (effective durations), or -1 when past all. */
    val playheadElementIndex: Int get() = EditorTrackDurations.clipIndexAt(elementTimelineClips, playheadMs)
}

sealed interface SequenceEditorIntent {
    data object TogglePlay : SequenceEditorIntent
    data object NudgeFrameBack : SequenceEditorIntent
    data object NudgeFrameForward : SequenceEditorIntent
    data object ToggleSnap : SequenceEditorIntent
    data object Undo : SequenceEditorIntent
    data object Redo : SequenceEditorIntent
    data class AddElements(val labels: List<String>) : SequenceEditorIntent
    data class AddMusicTracks(val labels: List<String>) : SequenceEditorIntent
    data class AddElementClips(val clips: List<EditorClip>) : SequenceEditorIntent
    data class AddMusicClips(val clips: List<EditorClip>) : SequenceEditorIntent
    data object SeekStart : SequenceEditorIntent
    data object SeekEnd : SequenceEditorIntent
    data class SeekTo(val fraction: Float) : SequenceEditorIntent
    data class SetTitleAndStyle(
        val title: String,
        val style: String,
        val tag: String = "",
    ) : SequenceEditorIntent
    data object Save : SequenceEditorIntent
    data class SetSongPlaybackSpeed(val speed: Double) : SequenceEditorIntent
    data class SelectElement(val index: Int) : SequenceEditorIntent
    data class SelectMusic(val index: Int) : SequenceEditorIntent
    data class ReorderElements(val from: Int, val to: Int) : SequenceEditorIntent
    data class ReorderMusic(val from: Int, val to: Int) : SequenceEditorIntent
    data object SplitSelected : SequenceEditorIntent
    data object DeleteSelected : SequenceEditorIntent
    data class SetMusicVolume(val volume: Float) : SequenceEditorIntent
    data class SetClipSpeed(val speed: Double) : SequenceEditorIntent
    data class SetTimelineZoom(val zoom: Float) : SequenceEditorIntent
    /** C-272: reload Room catalog before opening Add Element / Music pickers. */
    data object RefreshCatalog : SequenceEditorIntent
}

const val MIN_TIMELINE_ZOOM: Float = 0.1f
const val MAX_TIMELINE_ZOOM: Float = 1.0f
const val DEFAULT_TIMELINE_ZOOM: Float = 0.5f

/** Clamp to 0.1..1.0 and snap to the nearest 10% step. */
fun snapTimelineZoom(zoom: Float): Float {
    val steps = kotlin.math.round(zoom.coerceIn(MIN_TIMELINE_ZOOM, MAX_TIMELINE_ZOOM) * 10f)
    return (steps / 10f).coerceIn(MIN_TIMELINE_ZOOM, MAX_TIMELINE_ZOOM)
}

/** Stub full-track durations (seconds) for picker labels until media probe lands. */
object EditorTrackDurations {
    private const val ELEMENT_PAD_SEC = 8

    fun secondsFor(label: String): Int = when {
        label.contains("Ocasión", ignoreCase = true) -> 204
        label.contains("Propuesta", ignoreCase = true) -> 236
        label.contains("Vivir", ignoreCase = true) -> 252
        label.contains("Carita", ignoreCase = true) -> 198
        else -> 180
    }

    fun formatMmSs(totalSec: Int): String {
        val m = totalSec / 60
        val s = totalSec % 60
        return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }

    /** Playhead display: MM.SS.cs — cs = centiseconds (2 digits), e.g. 01.23.45. */
    fun formatTimecode(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val totalSec = (clamped / 1000L).toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        val cs = ((clamped % 1000L) / 10L).toInt()
        return "${m.toString().padStart(2, '0')}.${s.toString().padStart(2, '0')}.${cs.toString().padStart(2, '0')}"
    }

    /** Timeline length of a music clip at the choreo track speed (faster = shorter). */
    fun effectiveMs(durationMs: Long, speed: Double): Long {
        val safe = if (speed > 0.0) speed else 1.0
        return (durationMs / safe).toLong()
    }

    /**
     * Offset inside the clip at [positionMs] (0-based). Null when outside all clips.
     * Pair = (clipIndex, localMs).
     */
    fun clipLocalMs(clips: List<EditorClip>, positionMs: Long): Pair<Int, Long>? {
        val idx = clipIndexAt(clips, positionMs)
        if (idx < 0) return null
        var start = 0L
        for (i in 0 until idx) start += clips[i].durationMs.coerceAtLeast(0L)
        return idx to (positionMs - start).coerceAtLeast(0L)
    }

    /** Index of the clip covering [positionMs] using cumulative durations; -1 if outside. */
    fun clipIndexAt(clips: List<EditorClip>, positionMs: Long): Int {
        if (clips.isEmpty() || positionMs < 0L) return -1
        var start = 0L
        clips.forEachIndexed { index, clip ->
            val end = start + clip.durationMs
            if (positionMs < end) return index
            start = end
        }
        return -1
    }

    /**
     * Wave E (2026-10-25): map **speed-adjusted** element-lane playhead →
     * `(clipIndex, localPresentationMs)` for logs, scrub overlay, and reorder checks.
     *
     * **CompositionPlayer seek** uses absolute lane ms (`seekTo(timelineMs)`): Media3
     * CompositionPlayer ignores mediaItemIndex. Index here still follows **current**
     * [EditorClip] order after [SequenceEditorIntent.ReorderElements].
     *
     * Empty list → null. Past end → last index at last presentation ms (clamped).
     */
    fun compositionSeekTarget(clips: List<EditorClip>, timelineMs: Long): Pair<Int, Long>? {
        if (clips.isEmpty()) return null
        val t = timelineMs.coerceAtLeast(0L)
        var start = 0L
        clips.forEachIndexed { index, clip ->
            val speed = clip.speed.takeIf { it > 0.0 } ?: 1.0
            val present = effectiveMs(clip.durationMs.coerceAtLeast(1L), speed).coerceAtLeast(1L)
            val end = start + present
            if (t < end) {
                val local = (t - start).coerceIn(0L, (present - 1L).coerceAtLeast(0L))
                return index to local
            }
            start = end
        }
        val last = clips.lastIndex
        val lastSpeed = clips[last].speed.takeIf { it > 0.0 } ?: 1.0
        val lastPresent = effectiveMs(clips[last].durationMs.coerceAtLeast(1L), lastSpeed).coerceAtLeast(1L)
        return last to (lastPresent - 1L).coerceAtLeast(0L)
    }

    fun elementPadMs(): Long = ELEMENT_PAD_SEC * 1000L
}

private fun vault(
    id: String,
    title: String,
    status: VaultStatus,
    style: String,
    tag: String,
    bpm: Int,
    duration: String,
    track: String,
    chr: String,
    speed: Double = 1.0,
) = ChoreographyVaultItem(
    id = id,
    title = title,
    status = status,
    style = style,
    tags = ElementCategoryConfig.sanitizeTagsForItem(listOf(tag)),
    bpm = bpm,
    durationLabel = duration,
    trackName = track,
    chrId = chr,
    songPlaybackSpeed = speed.coerceIn(
        DomainValidators.CHOREO_PLAYBACK_SPEED_MIN,
        DomainValidators.CHOREO_PLAYBACK_SPEED_MAX,
    ),
)

internal val DEFAULT_VAULT_ITEMS: List<ChoreographyVaultItem> = listOf(
    vault("c1", "Новая хореография", VaultStatus.Draft, "Sensual", "Базовый", 124, "03:08", "Charles Luis - Ocasión", "CHR-014"),
    vault("c2", "Sensual Body Roll Suite", VaultStatus.Ready, "Sensual", "Средний", 128, "03:44", "Romeo Santos - Propuesta", "CHR-008", 1.1),
    vault("c3", "Madrid Step Flow", VaultStatus.Ready, "Dominican", "Средний", 132, "04:12", "Marc Anthony - Vivir Mi Vida", "CHR-021"),
    vault("c4", "Wave Combinations Lab", VaultStatus.Draft, "Modern", "Начинающий", 118, "02:59", "Jensen - Carita", "CHR-003", 0.8),
    vault("c5", "Salsa Shine Circuit", VaultStatus.Ready, "Salsa", "Про", 130, "04:38", "Celia Cruz - Carnaval", "CHR-017"),
)

internal val DEFAULT_EDITOR_LANES: List<EditorLane> = listOf(
    EditorLane("v1", "Hero Preview", LaneKind.Video, 0.05f, 0.72f),
    EditorLane("a1", "Master Audio", LaneKind.Audio, 0.0f, 0.95f),
    EditorLane("m1", "Move Markers", LaneKind.Markers, 0.12f, 0.88f),
)
