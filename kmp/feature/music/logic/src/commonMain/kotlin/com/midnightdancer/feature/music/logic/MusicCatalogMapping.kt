package com.midnightdancer.feature.music.logic

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.Song

/** Maps Room/domain [Song] rows into deck [MusicTrack]s (C-177). */
internal fun AppData.toMusicTracks(
    previous: List<MusicTrack> = emptyList(),
): List<MusicTrack> {
    val prevById = previous.associateBy { it.id }
    return songs.map { song ->
        val prev = prevById[song.id]
        song.toMusicTrack(
            isPlaying = prev?.isPlaying == true,
            progress = prev?.progress ?: 0f,
            positionLabel = prev?.positionLabel ?: "00:00",
        )
    }
}

internal fun Song.toMusicTrack(
    isPlaying: Boolean = false,
    progress: Float = 0f,
    positionLabel: String = "00:00",
): MusicTrack {
    val durationMs = (duration * 1000.0).toLong().coerceAtLeast(0L)
    val label = formatDurationLabel(durationMs)
    val tag = level.trim()
    val size = sizeBytes.coerceAtLeast(0)
    val weight =
        when {
            size <= 0 -> "0 MB"
            size < 1024 * 1024 -> "${(size / 1024).coerceAtLeast(1)} KB"
            else -> "${(size / (1024 * 1024)).coerceAtLeast(1)} MB"
        }
    return MusicTrack(
        id = id,
        title = title,
        style = danceStyle,
        level = tag,
        tags = if (tag.isEmpty()) emptyList() else listOf(tag),
        bpm = 120,
        durationLabel = label,
        weightLabel = weight,
        isPlaying = isPlaying,
        progress = progress,
        positionLabel = if (isPlaying || progress > 0f) {
            positionLabelFor(label, progress)
        } else {
            "00:00"
        },
        basePlaybackSpeed = playbackSpeed,
        mediaUri = fileName.takeIf { it.isNotBlank() },
        durationMs = durationMs,
    )
}

internal fun MusicTrack.toSong(): Song {
    val durationSec = durationMs?.coerceAtLeast(0L)?.div(1000.0) ?: parseDurationLabelToSeconds(durationLabel)
    val size = parseWeightToBytes(weightLabel)
    return Song(
        id = id,
        title = title,
        danceStyle = style,
        level = tags.firstOrNull()?.takeIf { it.isNotBlank() } ?: level,
        fileName = mediaUri.orEmpty(),
        duration = durationSec,
        sizeBytes = size,
        playbackSpeed = basePlaybackSpeed,
    )
}

internal fun parseDurationLabelToSeconds(label: String): Double {
    val parts = label.split(":")
    if (parts.size != 2) return 0.0
    val min = parts[0].toIntOrNull() ?: 0
    val sec = parts[1].toIntOrNull() ?: 0
    return (min * 60 + sec).toDouble()
}

private fun parseWeightToBytes(weight: String): Int {
    val trimmed = weight.trim().uppercase()
    val number = trimmed.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return 0
    return when {
        trimmed.contains("GB") -> (number * 1024 * 1024 * 1024).toInt()
        trimmed.contains("MB") -> (number * 1024 * 1024).toInt()
        trimmed.contains("KB") -> (number * 1024).toInt()
        else -> number.toInt()
    }
}
