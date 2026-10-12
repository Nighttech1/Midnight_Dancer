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

/** Freestyle temp choreo cue — wall-clock seconds from session start. */
data class CueAt(
    val sec: Float,
    val label: String,
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
    val trackTitle: String = "",
    val bpm: Int = 128,
    val levelLabel: String = "",
    /** Element tag shown in step 3 summary (alias of level for freestyle pick). */
    val elementTagLabel: String = "",
    val intervalSec: Float = 0f,
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
)

data class TrainerSessionUiState(
    val title: String = "Тренировка",
    val trackLabel: String = "Zacarias F. — Asesina",
    val timecode: String = "01:24",
    val bpm: Int = 128,
    val isPaused: Boolean = false,
    val phaseIndex: Int = 2,
    val phaseTotal: Int = 8,
    val moveTitle: String = "BASIC STEP",
    val moveSubtitle: String = "",
    val trackVol: Float = 0.72f,
    val voiceVol: Float = 0.55f,
    val runway: List<RunwayItem> = DEFAULT_RUNWAY,
    /** 0f..1f track progress for PhaseHub ring + seek knob. */
    val playheadFraction: Float = 0f,
    /** Freestyle temp choreo memory — cues at 0, 8, 16… sec. */
    val cueSchedule: List<CueAt> = DEFAULT_CUE_SCHEDULE,
    val holdProgress: Float = 0f,
    val placeholderItems: List<String> = listOf(
        "Phase cue",
        "Next move",
    ),
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
    data class SelectVoice(val voice: TrainerVoice) : TrainerIntent
    /** Starts platform TTS for [selectedVoice] (or [voice] override). */
    data class RequestSpeak(val voice: TrainerVoice? = null) : TrainerIntent
    data object RefreshPlaceholders : TrainerIntent
}

sealed interface TrainerSessionIntent {
    data object TogglePause : TrainerSessionIntent
    data class SetTrackVol(val value: Float) : TrainerSessionIntent
    data class SetVoiceVol(val value: Float) : TrainerSessionIntent
    data class HoldFinishTick(val progress: Float) : TrainerSessionIntent
    data object ResetHold : TrainerSessionIntent
    /** Seek playhead 0f..1f; updates [TrainerSessionUiState.moveTitle] from [cueSchedule]. */
    data class Seek(val fraction: Float) : TrainerSessionIntent
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
                if (state.voiceName.isNotBlank()) 100 else 0
            }
        }

    fun applySeek(state: TrainerSessionUiState, fraction: Float): TrainerSessionUiState {
        val f = fraction.coerceIn(0f, 1f)
        val cues = state.cueSchedule
        val totalSec =
            if (cues.isEmpty()) {
                1f
            } else {
                (cues.last().sec + CUE_STEP_SEC).coerceAtLeast(CUE_STEP_SEC)
            }
        val t = f * totalSec
        val cue = cues.lastOrNull { it.sec <= t } ?: cues.firstOrNull()
        return state.copy(
            playheadFraction = f,
            moveTitle = cue?.label ?: state.moveTitle,
            moveSubtitle = "",
        )
    }

    fun seedCueSchedule(runway: List<RunwayItem>): List<CueAt> =
        runway.mapIndexed { index, item ->
            CueAt(sec = index * CUE_STEP_SEC, label = item.label)
        }
}

private const val CUE_STEP_SEC = 8f

private val DEFAULT_ITEMS: List<String> = listOf(
    "Warm-up",
    "Combo drill",
)

internal val FREESTYLE_STORYLINE: List<StorylineStep> = listOf(
    StorylineStep(1, "Режим сессии", "Фристайл · Хореография"),
    StorylineStep(2, "Стиль и Трек", "стиль · имя трека"),
    StorylineStep(3, "Выбор элементов и интервала", "тег · интервал"),
    StorylineStep(4, "Голосовой тренер", "голос"),
)

internal val CHOREOGRAPHY_STORYLINE: List<StorylineStep> = listOf(
    StorylineStep(1, "Режим сессии", "Фристайл · Хореография"),
    StorylineStep(2, "Голосовой тренер", "голос"),
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
