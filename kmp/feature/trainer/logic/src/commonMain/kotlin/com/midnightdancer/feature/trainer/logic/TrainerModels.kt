package com.midnightdancer.feature.trainer.logic

import com.midnightdancer.core.tts.TrainerVoice
import com.midnightdancer.core.tts.TtsBackend

enum class TrainerMode {
    Freestyle,
    Choreography,
}

data class StorylineStep(
    val index: Int,
    val title: String,
    val subtitle: String,
)

enum class RunwayKind {
    Past,
    Active,
    Next,
}

data class RunwayItem(
    val label: String,
    val kind: RunwayKind,
    val detail: String = "",
)

/**
 * Trainer tab shell — Stage 4 wires real speak via [TrainerIntent.RequestSpeak] → Voice TTS.
 * Storyline fields (C-92): empty until configured; Dance only at readyPercent == 100.
 */
data class TrainerUiState(
    val title: String = "Тренировка",
    val mode: TrainerMode = TrainerMode.Freestyle,
    /** Accordion step 1..N (freestyle 4 / choreography 2). */
    val expandedStep: Int = 1,
    val styleName: String = "",
    val selectedStyleId: String = "",
    val trackTitle: String = "",
    val bpm: Int = 128,
    val levelLabel: String = "",
    /** Element tag shown in step 3 summary (alias of level for freestyle pick). */
    val elementTagLabel: String = "",
    val intervalSec: Float = 0f,
    /**
     * When true, Live cue spacing uses each element's video duration (C-225);
     * missing/zero duration falls back to [intervalSec].
     */
    /** C-229: default ON — use video duration when present; interval = fallback for no-video. */
    val useElementTiming: Boolean = true,
    /** Display name from [selectedVoice]; kept in sync on SelectVoice. */
    val voiceName: String = TrainerVoice.Ruslan.displayNameRu,
    val storylineSteps: List<StorylineStep> = FREESTYLE_STORYLINE,
    val readyPercent: Int = 0,
    val placeholderItems: List<String> = DEFAULT_ITEMS,
    val selectedVoice: TrainerVoice = TrainerVoice.Ruslan,
    /** 0=Ruslan, 1=Irina, 2=Kamila — SKIE-friendly Int for SwiftUI. */
    val selectedVoiceIndex: Int = TrainerVoice.Ruslan.ordinal,
    /** Human-readable speak CTA / status (Piper or System TTS). */
    val speakLabel: String = TrainerTtsLabels.idle(TrainerVoice.Ruslan, backend = null),
    val speakRequestedCount: Int = 0,
    val speakBackend: TtsBackend? = null,
    val isSpeaking: Boolean = false,
    val lastSpeakNote: String? = null,
    /** Real Room catalogs for Storyline pickers (C-192) — empty until LoadLibrary. */
    val styleOptions: List<String> = emptyList(),
    val trackOptions: List<String> = emptyList(),
    val tagOptions: List<String> = emptyList(),
    /**
     * C-273: choreography-mode step 2 — filter by tag then pick a vault choreography.
     * Live session builds cues from [selectedChoreographyId] snapshot.
     */
    val choreographyTag: String = "",
    val selectedChoreographyId: String = "",
    val selectedChoreographyTitle: String = "",
    val choreographyOptions: List<String> = emptyList(),
    val choreographyIdsByTitle: Map<String, String> = emptyMap(),
)

data class TrainerSessionUiState(
    val title: String = "Тренировка",
    val trackLabel: String = "",
    val timecode: String = "00:00",
    val bpm: Int = 128,
    val isPaused: Boolean = false,
    val phaseIndex: Int = 0,
    val phaseTotal: Int = 1,
    val moveTitle: String = "",
    val moveSubtitle: String = "",
    val trackVol: Float = 0.72f,
    val voiceVol: Float = 0.55f,
    val runway: List<RunwayItem> = emptyList(),
    /** 0f..1f track progress for PhaseHub ring + seek knob. */
    val playheadFraction: Float = 0f,
    /** Freestyle temp choreo memory — cues at intervalSec steps. */
    val cueSchedule: List<CueAt> = emptyList(),
    val holdProgress: Float = 0f,
    val placeholderItems: List<String> = listOf(
        "Phase cue",
        "Next move",
    ),
    /** Content URI of the selected library track (C-192 Media3). */
    val trackMediaUri: String? = null,
    /**
     * C-276: ordered music blocks from the selected choreography (or single freestyle track).
     * Live plays this playlist; empty → fall back to [trackMediaUri].
     */
    val musicPlaylist: List<MusicClipAt> = emptyList(),
    /** Full track length for playhead clock (ms). */
    val trackDurationMs: Long = 180_000L,
    /** Voice used for cue announcements. */
    val selectedVoice: TrainerVoice = TrainerVoice.Ruslan,
    /** Video URI of the cue currently under the playhead (optional preview). */
    val currentElementMediaUri: String? = null,
)

sealed interface TrainerIntent {
    data object OpenSessionStub : TrainerIntent
    /** Alias of [OpenSessionStub] — Stitch CTA «ТАНЦУЕМ!»; ignored unless readyPercent == 100. */
    data object StartDance : TrainerIntent
    data class SetMode(val mode: TrainerMode) : TrainerIntent
    data class SetExpandedStep(val step: Int) : TrainerIntent
    data class SetStyle(val style: String) : TrainerIntent
    data class SetTrack(val track: String) : TrainerIntent
    data class SetElementTag(val tag: String) : TrainerIntent
    data class SetInterval(val seconds: Float) : TrainerIntent
    data class SetUseElementTiming(val enabled: Boolean) : TrainerIntent
    data class SelectVoice(val voice: TrainerVoice) : TrainerIntent
    /** Starts platform TTS for [selectedVoice] (or [voice] override). */
    data class RequestSpeak(val voice: TrainerVoice? = null) : TrainerIntent
    data object RefreshPlaceholders : TrainerIntent
    /** C-273: filter choreography list by tag. */
    data class SetChoreographyTag(val tag: String) : TrainerIntent
    /** C-273: pick vault choreography by display title. */
    data class SetSelectedChoreography(val title: String) : TrainerIntent
}

sealed interface TrainerSessionIntent {
    data object TogglePause : TrainerSessionIntent
    data class SetTrackVol(val value: Float) : TrainerSessionIntent
    data class SetVoiceVol(val value: Float) : TrainerSessionIntent
    data class HoldFinishTick(val progress: Float) : TrainerSessionIntent
    data object ResetHold : TrainerSessionIntent
    /** Seek playhead 0f..1f; updates [TrainerSessionUiState.moveTitle] from [cueSchedule]. */
    /** [fromUserScrub]=true → debounce TTS; false → speak immediately with cue highlight (C-255). */
    data class Seek(val fraction: Float, val fromUserScrub: Boolean = false) : TrainerSessionIntent
}

object TrainerTtsLabels {
    fun idle(voice: TrainerVoice, backend: TtsBackend?): String {
        val b =
            when (backend) {
                TtsBackend.PiperOffline -> "Piper"
                TtsBackend.SystemFallback -> "System TTS"
                null -> "ready"
            }
        return "Speak · ${voice.displayNameRu} · $b"
    }

    fun speaking(voice: TrainerVoice, backend: TtsBackend?): String {
        val b =
            when (backend) {
                TtsBackend.PiperOffline -> "Piper"
                TtsBackend.SystemFallback -> "System TTS"
                null -> "…"
            }
        return "Speaking · ${voice.displayNameRu} · $b"
    }
}

/** Storyline steps + readyPercent for hub. */
object TrainerReady {
    fun storylineFor(mode: TrainerMode): List<StorylineStep> =
        when (mode) {
            TrainerMode.Freestyle -> FREESTYLE_STORYLINE
            TrainerMode.Choreography -> CHOREOGRAPHY_STORYLINE
        }

    fun compute(state: TrainerUiState): Int =
        when (state.mode) {
            TrainerMode.Freestyle -> {
                val ready =
                    state.styleName.isNotBlank() &&
                        state.trackTitle.isNotBlank() &&
                        state.elementTagLabel.isNotBlank() &&
                        state.intervalSec > 0f &&
                        state.voiceName.isNotBlank()
                if (ready) 100 else 0
            }
            TrainerMode.Choreography -> {
                val ready =
                    state.selectedChoreographyId.isNotBlank() &&
                        state.voiceName.isNotBlank()
                if (ready) 100 else 0
            }
        }

    fun applySeek(state: TrainerSessionUiState, fraction: Float): TrainerSessionUiState {
        val f = fraction.coerceIn(0f, 1f)
        val cues = state.cueSchedule
        val totalSec = sessionTotalSec(state)
        val t = f * totalSec
        val cue = cues.lastOrNull { it.sec <= t } ?: cues.firstOrNull()
        return state.copy(
            playheadFraction = f,
            moveTitle = cue?.label ?: state.moveTitle,
            moveSubtitle = "",
            currentElementMediaUri = cue?.mediaUri ?: state.currentElementMediaUri,
            phaseIndex = if (cues.isEmpty()) 0 else cues.indexOfLast { it.sec <= t }.coerceAtLeast(0),
            phaseTotal = cues.size.coerceAtLeast(1),
        )
    }

    /**
     * C-280: first cue that has **not** started yet at [tSec].
     * Scrub TTS must speak this — never the in-progress cue (`sec <= t`).
     */
    fun nextCueAfter(cues: List<CueAt>, tSec: Float): CueAt? =
        cues.firstOrNull { it.sec > tSec }

    fun nextCueAfter(state: TrainerSessionUiState): CueAt? {
        val t = state.playheadFraction.coerceIn(0f, 1f) * sessionTotalSec(state)
        return nextCueAfter(state.cueSchedule, t)
    }

    fun sessionTotalSec(state: TrainerSessionUiState): Float {
        val fromTrack = (state.trackDurationMs / 1000f).coerceAtLeast(1f)
        val cues = state.cueSchedule
        if (cues.isEmpty()) return fromTrack
        // C-229: last cue duration = last inter-cue gap (variable element timing).
        val lastStep = lastCueStepSec(cues)
        return (cues.last().sec + lastStep).coerceAtLeast(fromTrack)
    }

    fun cueStepSec(cues: List<CueAt>): Float {
        if (cues.size < 2) return CUE_STEP_SEC
        val delta = cues[1].sec - cues[0].sec
        return if (delta > 0f) delta else CUE_STEP_SEC
    }

    fun lastCueStepSec(cues: List<CueAt>): Float {
        if (cues.size < 2) return cueStepSec(cues)
        val gaps = cues.zipWithNext().map { (a, b) -> b.sec - a.sec }.filter { it > 0f }
        return gaps.lastOrNull() ?: cueStepSec(cues)
    }

    fun seedCueSchedule(runway: List<RunwayItem>, intervalSec: Float = CUE_STEP_SEC): List<CueAt> =
        runway.mapIndexed { index, item ->
            CueAt(sec = index * intervalSec.coerceAtLeast(1f), label = item.label)
        }
}

/** Freestyle cue — wall-clock seconds from session start; optional element video. */
/** One music block in a Live playlist (C-276). */
data class MusicClipAt(
    val uri: String,
    val durationMs: Long,
    val label: String = "",
)

data class CueAt(
    val sec: Float,
    val label: String,
    val mediaUri: String? = null,
)

private const val CUE_STEP_SEC = 8f

private val DEFAULT_ITEMS: List<String> = listOf(
    "Warm-up",
    "Combo drill",
)

internal val FREESTYLE_STORYLINE: List<StorylineStep> = listOf(
    StorylineStep(1, "Режим сессии", "Фристайл · Хореография"),
    StorylineStep(2, "Стиль и трек", "Стиль · Имя трека"),
    StorylineStep(3, "Выбор элементов и интервала", "Тег · Интервал"),
    StorylineStep(4, "Голосовой тренер", "Голос"),
)

internal val CHOREOGRAPHY_STORYLINE: List<StorylineStep> = listOf(
    StorylineStep(1, "Режим сессии", "Фристайл · Хореография"),
    StorylineStep(2, "Выбор хореографии", "Тег · Хореография"),
    StorylineStep(3, "Голосовой тренер", "Голос"),
)

private val DEFAULT_RUNWAY: List<RunwayItem> = listOf(
    RunwayItem("BASIC", RunwayKind.Past, "пройдено"),
    RunwayItem("CROSS BODY", RunwayKind.Active, "сейчас"),
    RunwayItem("ВОЛНА", RunwayKind.Next, "через 3.2с"),
    RunwayItem("HAMMERLOCK", RunwayKind.Next, "далее"),
    RunwayItem("DIP", RunwayKind.Next, "далее"),
    RunwayItem("SHINE", RunwayKind.Next, "финал"),
)

private val DEFAULT_CUE_SCHEDULE: List<CueAt> =
    TrainerReady.seedCueSchedule(DEFAULT_RUNWAY)
