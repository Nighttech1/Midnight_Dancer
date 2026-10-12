package com.midnightdancer.core.data.db.mapper

import com.midnightdancer.core.data.db.entity.ChoreographyEntity
import com.midnightdancer.core.data.db.entity.DanceStyleEntity
import com.midnightdancer.core.data.db.entity.MoveEntity
import com.midnightdancer.core.data.db.entity.SongEntity
import com.midnightdancer.core.domain.entity.Choreography
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.entity.Song
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.serialization.DoubleKeyedStringMapSerializer

internal object CatalogMappers {
    private val json = DomainJson.format

    fun MoveEntity.toDomain(): Move =
        Move(
            id = id,
            name = name,
            level = level,
            description = description,
            videoUri = videoUri,
            masteryPercent = masteryPercent,
        )

    fun Move.toEntity(styleId: String, sortOrder: Int): MoveEntity =
        MoveEntity(
            id = id,
            styleId = styleId,
            name = name,
            level = level,
            description = description,
            videoUri = videoUri,
            masteryPercent = masteryPercent,
            sortOrder = sortOrder,
        )

    fun DanceStyleEntity.toDomain(moves: List<Move>): DanceStyle =
        DanceStyle(
            id = id,
            name = name,
            moves = moves,
            currentMoveId = currentMoveId,
        )

    fun DanceStyle.toEntity(): DanceStyleEntity =
        DanceStyleEntity(
            id = id,
            name = name,
            currentMoveId = currentMoveId,
        )

    fun SongEntity.toDomain(): Song =
        Song(
            id = id,
            title = title,
            danceStyle = danceStyle,
            level = level,
            fileName = fileName,
            duration = duration,
            sizeBytes = sizeBytes,
            playbackSpeed = playbackSpeed,
        )

    fun Song.toEntity(): SongEntity =
        SongEntity(
            id = id,
            title = title,
            danceStyle = danceStyle,
            level = level,
            fileName = fileName,
            duration = duration,
            sizeBytes = sizeBytes,
            playbackSpeed = playbackSpeed,
        )

    fun encodeTimeline(timeline: Map<Double, String>): String =
        json.encodeToString(DoubleKeyedStringMapSerializer, timeline)

    fun decodeTimeline(raw: String): Map<Double, String> =
        if (raw.isBlank()) {
            emptyMap()
        } else {
            json.decodeFromString(DoubleKeyedStringMapSerializer, raw)
        }

    fun ChoreographyEntity.toDomain(): Choreography =
        Choreography(
            id = id,
            name = name,
            songId = songId,
            styleId = styleId,
            timeline = decodeTimeline(timelineJson),
            startTime = startTime,
            endTime = endTime,
        )

    fun Choreography.toEntity(): ChoreographyEntity =
        ChoreographyEntity(
            id = id,
            name = name,
            songId = songId,
            styleId = styleId,
            timelineJson = encodeTimeline(timeline),
            startTime = startTime,
            endTime = endTime,
        )
}
