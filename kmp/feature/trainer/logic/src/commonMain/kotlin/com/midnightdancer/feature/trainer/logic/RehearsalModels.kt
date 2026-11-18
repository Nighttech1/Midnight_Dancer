package com.midnightdancer.feature.trainer.logic

import kotlinx.serialization.Serializable
import kotlin.time.TimeSource

@Serializable
data class RehearsalSettings(
    val choreographyId: String,
    val choreographyRevision: String,
    val startMs: Long,
    val endMs: Long,
    val totalPasses: Int = 5,
    val restMs: Long = 5_000L,
    val preparationMs: Long = 3_000L,
    val voiceEnabled: Boolean = true,
    val schemaVersion: Int = 1,
) {
    val sectionDurationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    val estimatedDurationMs: Long
        get() = preparationMs + totalPasses * sectionDurationMs + (totalPasses - 1).coerceAtLeast(0) * restMs
}

data class RehearsalMusicClip(
    val uri: String,
    val timelineStartMs: Long,
    val sourceStartMs: Long,
    val durationMs: Long,
)

data class RehearsalElement(
    val startMs: Long,
    val endMs: Long,
    val label: String,
    val stableOrder: Int,
)

data class RehearsalCue(val offsetMs: Long, val label: String)

data class RehearsalPlan(
    val choreographyId: String,
    val revision: String,
    val durationMs: Long,
    val musicClips: List<RehearsalMusicClip>,
    val elements: List<RehearsalElement>,
    val musicVolume: Float = 1f,
) {
    /** All labels are kept for the visual layer; simultaneous voice events use last stable order. */
    fun visualElementsAt(positionMs: Long): List<RehearsalElement> =
        elements.filter { positionMs >= it.startMs && positionMs < it.endMs }

    fun cues(settings: RehearsalSettings): List<RehearsalCue> {
        val activeAtStart = elements.filter {
            it.startMs < settings.startMs && it.endMs > settings.startMs
        }.map { Triple(0L, it.stableOrder, it.label) }
        val events = elements.filter {
            it.startMs >= settings.startMs && it.startMs < settings.endMs
        }.map { Triple(it.startMs - settings.startMs, it.stableOrder, it.label) }
        return (activeAtStart + events).sortedWith(compareBy({ it.first }, { it.second }))
            .groupBy { it.first }
            .map { (offset, group) -> RehearsalCue(offset, group.last().third) }
            .filter { it.label.isNotBlank() }
    }

    fun sourcePositionAt(timelineMs: Long): Pair<Int, Long>? {
        val index = musicClips.indexOfFirst {
            timelineMs >= it.timelineStartMs && timelineMs < it.timelineStartMs + it.durationMs
        }
        if (index < 0) return null
        val clip = musicClips[index]
        return index to clip.sourceStartMs + (timelineMs - clip.timelineStartMs)
    }
}

data class RehearsalPlanResult(val plan: RehearsalPlan? = null, val error: String? = null)

object RehearsalValidator {
    fun errors(settings: RehearsalSettings, durationMs: Long?, revision: String): List<String> = buildList {
        if (settings.schemaVersion != 1) add("Версия настроек не поддерживается. Сбросьте настройки отработки.")
        if (settings.choreographyId.isBlank()) add("Выберите хореографию.")
        if (settings.choreographyRevision != revision) add("Постановка изменена. Проверьте и подтвердите границы участка.")
        if (durationMs == null || durationMs <= 0L) add("Не удалось определить доступную длительность постановки.")
        if (settings.startMs < 0L || settings.endMs <= settings.startMs ||
            (durationMs != null && settings.endMs > durationMs)
        ) add("Участок должен находиться внутри постановки: начало раньше конца.")
        if (settings.startMs >= 0L && settings.endMs >= settings.startMs &&
            settings.endMs - settings.startMs < 1_000L
        ) add("Минимальная длина участка — 1 секунда.")
        if (settings.startMs % 100L != 0L ||
            (settings.endMs % 100L != 0L && settings.endMs != durationMs)
        ) add("Введите время с точностью до 0,1 секунды или выберите «Конец».")
        if (settings.totalPasses !in 1..99) add("Укажите от 1 до 99 проходов.")
        if (settings.restMs !in 0L..120_000L || settings.restMs % 1_000L != 0L) add("Отдых: от 0 до 120 целых секунд.")
        if (settings.preparationMs !in 0L..10_000L || settings.preparationMs % 1_000L != 0L) add("Подготовка: от 0 до 10 целых секунд.")
    }
}

enum class RehearsalPhase { Ready, Preparing, Playing, Resting, Paused, Completed, Error }
enum class RehearsalAccess { Granted, Free, Unavailable }

data class RehearsalState(
    val phase: RehearsalPhase = RehearsalPhase.Ready,
    val pausedFrom: RehearsalPhase? = null,
    val pass: Int = 0,
    val totalPasses: Int = 0,
    val positionMs: Long = 0L,
    val remainingMs: Long = 0L,
    val currentLabel: String = "",
    val activeLabels: List<String> = emptyList(),
    val isWaitingForPlayer: Boolean = false,
    val access: RehearsalAccess? = null,
    val error: String? = null,
)

interface RehearsalSettingsRepository {
    suspend fun read(choreographyId: String): RehearsalSettings?
    suspend fun write(settings: RehearsalSettings)
    suspend fun delete(choreographyId: String)
}

fun interface RehearsalEntitlementPort {
    suspend fun access(): RehearsalAccess
}

/** Suspend operations MUST cooperate with cancellation; prepare verifies every source and trim. */
interface RehearsalPlaybackPort {
    suspend fun prepare(plan: RehearsalPlan): Long?
    suspend fun seekTo(positionMs: Long)
    suspend fun awaitReady()
    /** Return only when audio really starts, not when playWhenReady was assigned. */
    suspend fun playAndAwaitStarted()
    fun positionMs(): Long
    fun pause()
    fun stop()
}

interface RehearsalVoicePort {
    /** Initialize the selected voice without speech; cancellation must abort pending initialization. */
    suspend fun prepare() = Unit
    /** Latest wins. stop must immediately cancel generation/playback and pending utterances. */
    fun speak(label: String)
    fun stop()
    /** Optional short beep, never a movement name. Included within preparation/rest. */
    fun countdown(seconds: Int) = Unit
}

fun interface RehearsalClock {
    fun nowMs(): Long

    companion object {
        fun monotonic(): RehearsalClock {
            val origin = TimeSource.Monotonic.markNow()
            return RehearsalClock { origin.elapsedNow().inWholeMilliseconds }
        }
    }
}
