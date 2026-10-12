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
)

data class ChoreographyUiState(
    val title: String = "Хореография",
    val vaultBadge: String = "",
    val items: List<ChoreographyVaultItem> = DEFAULT_VAULT_ITEMS,
    val searchQuery: String = "",
    val selectedStyle: String = "Все стили",
    val selectedTag: String = "Все теги",
    val sortMode: ChoreographySortMode = ChoreographySortMode.ByTag,
    val sortLabel: String = "By tag",
    val tagCatalog: List<String> = ElementCategoryConfig.DEFAULT_NAMES,
) {
    val placeholderItems: List<String>
        get() = items.map { it.title }

    val filteredItems: List<ChoreographyVaultItem>
        get() {
            val q = searchQuery.trim()
            val filtered = items.filter { item ->
                val styleOk =
                    selectedStyle == "Все стили" || item.style.equals(selectedStyle, ignoreCase = true)
                val tagOk =
                    selectedTag == "Все теги" ||
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
    data object OpenSequenceEditorStub : ChoreographyIntent
    data object RefreshPlaceholders : ChoreographyIntent
    data object CreateChoreography : ChoreographyIntent
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
    val bpm: Int = 128,
    val resolutionLabel: String = "4K · 60FPS",
    val timecode: String = "00.00.0000",
    val isPlaying: Boolean = false,
    val playheadFraction: Float = 0f,
    val snapToBpm: Boolean = true,
    val lanes: List<EditorLane> = DEFAULT_EDITOR_LANES,
    /** Seeded demo clips so CapCut tools/undo work before first add. */
    val addedElementLabels: List<String> = listOf("Intro", "Body roll", "Turn"),
    val addedMusicLabels: List<String> = emptyList(),
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
)

sealed interface SequenceEditorIntent {
    data object TogglePlay : SequenceEditorIntent
    data object NudgeFrameBack : SequenceEditorIntent
    data object NudgeFrameForward : SequenceEditorIntent
    data object ToggleSnap : SequenceEditorIntent
    data object Undo : SequenceEditorIntent
    data object Redo : SequenceEditorIntent
    data class AddElements(val labels: List<String>) : SequenceEditorIntent
    data class AddMusicTracks(val labels: List<String>) : SequenceEditorIntent
    data class SetSongPlaybackSpeed(val speed: Double) : SequenceEditorIntent
    data class SelectElement(val index: Int) : SequenceEditorIntent
    data class SelectMusic(val index: Int) : SequenceEditorIntent
    data class ReorderElements(val from: Int, val to: Int) : SequenceEditorIntent
    data class ReorderMusic(val from: Int, val to: Int) : SequenceEditorIntent
    data object SplitSelected : SequenceEditorIntent
    data object DeleteSelected : SequenceEditorIntent
    data class SetMusicVolume(val volume: Float) : SequenceEditorIntent
    data class SetClipSpeed(val speed: Double) : SequenceEditorIntent
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

    /** Playhead display: MM.SS.msms (no hours/days). */
    fun formatTimecode(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val totalSec = (clamped / 1000L).toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        val frac = (clamped % 1000L).toInt()
        return "${m.toString().padStart(2, '0')}.${s.toString().padStart(2, '0')}.${frac.toString().padStart(4, '0')}"
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
