package com.midnightdancer.feature.trainer.logic

import com.midnightdancer.feature.choreography.logic.ChoreographySnapshot
import com.midnightdancer.feature.choreography.logic.EditorClip
import kotlin.math.roundToLong

object RehearsalPlanBuilder {
    fun fromSnapshot(
        choreographyId: String,
        snapshot: ChoreographySnapshot,
        resolveMusicUri: (EditorClip) -> String? = { it.mediaUri },
    ): RehearsalPlanResult {
        if (choreographyId.isBlank()) return RehearsalPlanResult(error = "Выберите хореографию.")
        if (!snapshot.songPlaybackSpeed.isFinite() || snapshot.songPlaybackSpeed != 1.0) {
            return RehearsalPlanResult(error = "Для отработки установите скорость музыки 1×. Обычная тренировка доступна с прежней скоростью.")
        }
        if (!snapshot.musicVolume.isFinite()) {
            return RehearsalPlanResult(error = "Проверьте громкость музыки в постановке.")
        }
        if (snapshot.musicClips.isEmpty()) return RehearsalPlanResult(error = "Добавьте музыку в постановку.")
        val music = ArrayList<RehearsalMusicClip>()
        var musicMs = 0L
        snapshot.musicClips.forEach { clip ->
            val uri = resolveMusicUri(clip)?.takeIf { it.isNotBlank() }
                ?: return RehearsalPlanResult(error = "Не найден музыкальный файл «${clip.label}».")
            if (clip.durationMs <= 0L || clip.sourceStartMs < 0L ||
                clip.durationMs > Long.MAX_VALUE - clip.sourceStartMs ||
                clip.durationMs > Long.MAX_VALUE - musicMs
            ) return RehearsalPlanResult(error = "Неизвестна длительность музыкального участка «${clip.label}».")
            music += RehearsalMusicClip(uri, musicMs, clip.sourceStartMs, clip.durationMs)
            musicMs += clip.durationMs
        }
        val elements = ArrayList<RehearsalElement>()
        var elementMs = 0L
        snapshot.elementClips.forEachIndexed { index, clip ->
            if (!clip.speed.isFinite() || clip.speed <= 0.0 || clip.durationMs <= 0L) {
                return RehearsalPlanResult(error = "Проверьте длительность и скорость движения «${clip.label}».")
            }
            val effectiveDouble = clip.durationMs.toDouble() / clip.speed
            if (!effectiveDouble.isFinite() || effectiveDouble > Long.MAX_VALUE.toDouble() - elementMs) {
                return RehearsalPlanResult(error = "Слишком большая длительность движения «${clip.label}».")
            }
            val effectiveMs = effectiveDouble.roundToLong().coerceAtLeast(1L)
            if (effectiveMs > Long.MAX_VALUE - elementMs) {
                return RehearsalPlanResult(error = "Слишком большая длительность постановки.")
            }
            elements += RehearsalElement(elementMs, elementMs + effectiveMs, clip.label, index)
            elementMs += effectiveMs
        }
        val revisionBody = buildString {
            append("rehearsal-v1;")
            fun field(value: Any?) {
                val text = value?.toString().orEmpty()
                append(text.length).append(':').append(text).append(';')
            }
            field(choreographyId)
            field(snapshot.songPlaybackSpeed)
            snapshot.musicClips.forEachIndexed { i, clip ->
                field(music[i].uri); field(clip.sourceStartMs); field(clip.durationMs)
            }
            snapshot.elementClips.forEach { clip ->
                field(clip.label); field(clip.mediaUri); field(clip.durationMs)
                field(clip.sourceStartMs); field(clip.speed)
            }
        }
        // Stable content fingerprint, not a security primitive or entitlement proof.
        var hash = -3750763034362895579L
        revisionBody.forEach { char -> hash = (hash xor char.code.toLong()) * 1099511628211L }
        return RehearsalPlanResult(
            plan = RehearsalPlan(
                choreographyId, "v1-${hash.toString(16)}", musicMs, music, elements,
                musicVolume = snapshot.musicVolume.coerceIn(0f, 1f),
            ),
        )
    }
}
