package com.midnightdancer.core.data.backup

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.exchange.StyleImportPlan

/**
 * Flutter parity: [applyStyleRedirectBeforeMerge] — сложить содержимое стилей пакета
 * в выбранные локальные стили до [FullBackupImportMerge].
 */
object FullBackupStyleRedirect {
    fun apply(
        imported: AppData,
        local: AppData,
        plan: StyleImportPlan,
    ): AppData {
        val redirect =
            plan.archiveStyleToLocalId
                .filter { (from, to) -> from.isNotBlank() && to.isNotBlank() && from != to }
        if (redirect.isEmpty()) return imported

        val folded = redirect.keys
        val movesByTarget = linkedMapOf<String, MutableList<Move>>()
        val styleMap = imported.danceStyles.associateBy { it.id }

        for (archiveId in folded) {
            val style = styleMap[archiveId] ?: continue
            val target = redirect[archiveId] ?: continue
            movesByTarget.getOrPut(target) { mutableListOf() }.addAll(style.moves)
        }

        val newStyles = mutableListOf<DanceStyle>()
        for (s in imported.danceStyles) {
            if (s.id in folded) continue
            newStyles += s
        }

        for ((targetId, extraMoves) in movesByTarget) {
            val deduped = dedupeMovesById(extraMoves)
            val idx = newStyles.indexOfFirst { it.id == targetId }
            if (idx >= 0) {
                val existing = newStyles[idx]
                newStyles[idx] =
                    existing.copy(moves = mergeMoveListsById(existing.moves, deduped))
            } else {
                val name =
                    local.danceStyles.firstOrNull { it.id == targetId }?.name
                        ?: imported.danceStyles.firstOrNull { it.id == targetId }?.name
                        ?: targetId
                newStyles +=
                    DanceStyle(
                        id = targetId,
                        name = name,
                        moves = deduped,
                        currentMoveId = null,
                    )
            }
        }

        val newChoreos =
            imported.choreographies.map { c ->
                val newStyleId = redirect[c.styleId] ?: c.styleId
                c.copy(
                    styleId = newStyleId,
                    timeline = remapTimelineForStyleRedirect(c.timeline, redirect),
                )
            }

        return imported.copy(
            danceStyles = newStyles,
            choreographies = newChoreos,
        )
    }

    private fun mergeMoveListsById(
        base: List<Move>,
        extra: List<Move>,
    ): List<Move> {
        val ids = base.map { it.id }.toSet()
        return base + extra.filter { it.id !in ids }
    }

    private fun dedupeMovesById(moves: List<Move>): List<Move> {
        val seen = linkedSetOf<String>()
        val out = mutableListOf<Move>()
        for (m in moves) {
            if (m.id in seen) continue
            seen += m.id
            out += m
        }
        return out
    }

    /** Timeline refs `styleId::moveId` — переписать префикс стиля. */
    fun remapTimelineForStyleRedirect(
        timeline: Map<Double, String>,
        redirect: Map<String, String>,
    ): Map<Double, String> =
        timeline.mapValues { (_, ref) ->
            val sep = "::"
            if (!ref.contains(sep)) return@mapValues ref
            val styleId = ref.substringBefore(sep)
            val rest = ref.substringAfter(sep)
            val mapped = redirect[styleId]
            if (mapped != null) "$mapped$sep$rest" else ref
        }
}
