package com.midnightdancer.core.domain.validation

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.Choreography
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.entity.Song
import com.midnightdancer.core.domain.settings.DanceReminderConfig
import com.midnightdancer.core.domain.timeline.ChoreographyTimelineRef

/** Result of a pure domain invariant check. */
sealed class ValidationResult {
    data object Ok : ValidationResult()

    data class Err(val reasons: List<String>) : ValidationResult() {
        constructor(reason: String) : this(listOf(reason))
    }

    val isOk: Boolean get() = this is Ok
}

/**
 * Pure validations (no Android/iOS SDK) — mirrors Flutter clamps and id rules.
 */
object DomainValidators {
    /** Library track base listening speed (Music edit sheet) — product 2026-10-08. */
    const val PLAYBACK_SPEED_MIN: Double = 0.4
    const val PLAYBACK_SPEED_MAX: Double = 2.0
    /** Per-choreography override — independent of Song.base. */
    const val CHOREO_PLAYBACK_SPEED_MIN: Double = 0.5
    const val CHOREO_PLAYBACK_SPEED_MAX: Double = 2.0
    const val MASTERY_MIN: Int = 0
    const val MASTERY_MAX: Int = 100

    fun isPlaybackSpeedValid(speed: Double): Boolean =
        speed in PLAYBACK_SPEED_MIN..PLAYBACK_SPEED_MAX

    fun isChoreoPlaybackSpeedValid(speed: Double): Boolean =
        speed in CHOREO_PLAYBACK_SPEED_MIN..CHOREO_PLAYBACK_SPEED_MAX

    fun isMasteryPercentValid(percent: Int): Boolean =
        percent in MASTERY_MIN..MASTERY_MAX

    fun validateSong(song: Song): ValidationResult {
        val reasons = mutableListOf<String>()
        if (song.id.isBlank()) reasons += "song.id must be non-blank"
        if (song.title.isBlank()) reasons += "song.title must be non-blank"
        if (song.fileName.isBlank()) reasons += "song.fileName must be non-blank"
        if (!isPlaybackSpeedValid(song.playbackSpeed)) {
            reasons += "song.playbackSpeed must be in $PLAYBACK_SPEED_MIN..$PLAYBACK_SPEED_MAX"
        }
        if (song.duration < 0) reasons += "song.duration must be >= 0"
        if (song.sizeBytes < 0) reasons += "song.sizeBytes must be >= 0"
        return if (reasons.isEmpty()) ValidationResult.Ok else ValidationResult.Err(reasons)
    }

    fun validateMove(move: Move): ValidationResult {
        val reasons = mutableListOf<String>()
        if (move.id.isBlank()) reasons += "move.id must be non-blank"
        if (move.name.isBlank()) reasons += "move.name must be non-blank"
        if (!isMasteryPercentValid(move.masteryPercent)) {
            reasons += "move.masteryPercent must be in $MASTERY_MIN..$MASTERY_MAX"
        }
        return if (reasons.isEmpty()) ValidationResult.Ok else ValidationResult.Err(reasons)
    }

    fun validateDanceStyle(style: DanceStyle): ValidationResult {
        val reasons = mutableListOf<String>()
        if (style.id.isBlank()) reasons += "style.id must be non-blank"
        if (style.name.isBlank()) reasons += "style.name must be non-blank"
        for (m in style.moves) {
            when (val r = validateMove(m)) {
                is ValidationResult.Err -> reasons += r.reasons
                ValidationResult.Ok -> Unit
            }
        }
        val current = style.currentMoveId
        if (current != null && current.isBlank()) {
            reasons += "style.currentMoveId must be non-blank when set"
        }
        return if (reasons.isEmpty()) ValidationResult.Ok else ValidationResult.Err(reasons)
    }

    fun validateChoreography(choreography: Choreography): ValidationResult {
        val reasons = mutableListOf<String>()
        if (choreography.id.isBlank()) reasons += "choreography.id must be non-blank"
        if (choreography.name.isBlank()) reasons += "choreography.name must be non-blank"
        if (choreography.songId.isBlank()) reasons += "choreography.songId must be non-blank"
        if (choreography.styleId.isBlank()) reasons += "choreography.styleId must be non-blank"
        if (choreography.endTime < choreography.startTime) {
            reasons += "choreography.endTime must be >= startTime"
        }
        if (!isChoreoPlaybackSpeedValid(choreography.songPlaybackSpeed)) {
            reasons +=
                "choreography.songPlaybackSpeed must be in " +
                    "$CHOREO_PLAYBACK_SPEED_MIN..$CHOREO_PLAYBACK_SPEED_MAX"
        }
        for ((t, ref) in choreography.timeline) {
            if (t < 0) reasons += "choreography.timeline key $t must be >= 0"
            if (!ChoreographyTimelineRef.isValidRef(ref)) {
                reasons += "choreography.timeline ref at $t must be non-empty id"
            }
        }
        return if (reasons.isEmpty()) ValidationResult.Ok else ValidationResult.Err(reasons)
    }

    fun validateReminder(config: DanceReminderConfig): ValidationResult {
        val reasons = mutableListOf<String>()
        if (config.hour !in 0..23) reasons += "reminder.hour must be 0..23"
        if (config.minute !in 0..59) reasons += "reminder.minute must be 0..59"
        if (config.weekday !in 1..7) reasons += "reminder.weekday must be 1..7"
        return if (reasons.isEmpty()) ValidationResult.Ok else ValidationResult.Err(reasons)
    }

    fun validateAppData(data: AppData): ValidationResult {
        val reasons = mutableListOf<String>()
        for (s in data.danceStyles) {
            when (val r = validateDanceStyle(s)) {
                is ValidationResult.Err -> reasons += r.reasons
                ValidationResult.Ok -> Unit
            }
        }
        for (song in data.songs) {
            when (val r = validateSong(song)) {
                is ValidationResult.Err -> reasons += r.reasons
                ValidationResult.Ok -> Unit
            }
        }
        for (c in data.choreographies) {
            when (val r = validateChoreography(c)) {
                is ValidationResult.Err -> reasons += r.reasons
                ValidationResult.Ok -> Unit
            }
        }
        return if (reasons.isEmpty()) ValidationResult.Ok else ValidationResult.Err(reasons)
    }
}
