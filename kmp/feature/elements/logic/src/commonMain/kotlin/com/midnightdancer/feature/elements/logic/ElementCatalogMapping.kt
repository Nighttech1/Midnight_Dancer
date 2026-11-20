package com.midnightdancer.feature.elements.logic

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.media.ElementDurationCodec

/** Maps Room/domain [Move] rows into atelier [ElementCard]s (C-176 / C-189). */
internal fun AppData.toElementCards(): List<ElementCard> {
    val pinnedId = danceStyles.mapNotNull { it.currentMoveId }.firstOrNull()
    return danceStyles.flatMap { style ->
        style.moves.map { move -> move.toElementCard(styleId = style.id, pinned = move.id == pinnedId) }
    }
}

internal fun Move.toElementCard(styleId: String, pinned: Boolean = false): ElementCard {
    val tag = level.trim()
    val durationMs = ElementDurationCodec.durationMs(description)
    return ElementCard(
        id = id,
        title = name,
        levelLabel = tag,
        category = tag,
        tags = if (tag.isEmpty()) emptyList() else listOf(tag),
        durationLabel = ElementDurationCodec.formatLabel(durationMs),
        masteryPercent = masteryPercent.coerceIn(0, 100),
        coverUrl = videoUri,
        description = ElementDurationCodec.userText(description),
        kind = ElementCardKind.VideoCover,
        styleId = styleId,
        pinned = pinned,
        videoUri = videoUri,
    )
}

internal fun ElementCard.toMove(durationMs: Long = 0L): Move {
    val probed = durationMs.takeIf { it > 0L }
        ?: ElementDurationCodec.durationMs(description).takeIf { it > 0L }
        ?: parseDurationLabelMs(durationLabel)
    return Move(
        id = id,
        name = title,
        level = tags.firstOrNull()?.takeIf { it.isNotBlank() } ?: levelLabel,
        description = ElementDurationCodec.pack(description, probed).takeIf { it.isNotBlank() },
        videoUri = videoUri ?: coverUrl,
        masteryPercent = masteryPercent.coerceIn(0, 100),
    )
}

private fun parseDurationLabelMs(label: String): Long {
    val parts = label.trim().split(':')
    if (parts.size != 2) return 0L
    val m = parts[0].toLongOrNull() ?: return 0L
    val s = parts[1].toLongOrNull() ?: return 0L
    return (m * 60L + s) * 1000L
}
