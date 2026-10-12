package com.midnightdancer.core.data.choreography

import com.midnightdancer.core.domain.entity.Move

/**
 * Merge package moves into an existing style — Flutter [ChoreographyImportMerge].
 */
object ChoreographyImportMerge {
    data class MoveMergeResult(
        val imported: List<Move>,
        val renameFromOriginal: Map<String, String>,
    )

    fun mergeMoves(
        existingMoves: List<Move>,
        payloadMoves: List<Move>,
        newMoveId: (oneBasedIndex: Int) -> String,
    ): MoveMergeResult {
        val takenNames = existingMoves.map { it.name }.toMutableSet()
        val renameFromOriginal = linkedMapOf<String, String>()

        fun uniqueName(desired: String): String {
            if (desired !in takenNames) {
                takenNames += desired
                return desired
            }
            var i = 2
            while (true) {
                val n = "$desired ($i)"
                if (n !in takenNames) {
                    takenNames += n
                    return n
                }
                i++
            }
        }

        var moveIndex = 0
        val newImported = mutableListOf<Move>()
        for (m in payloadMoves) {
            moveIndex++
            val finalName = uniqueName(m.name)
            if (finalName != m.name) {
                renameFromOriginal[m.name] = finalName
            }
            newImported +=
                Move(
                    id = newMoveId(moveIndex),
                    name = finalName,
                    level = m.level,
                    description = m.description,
                    videoUri = null,
                    masteryPercent = 0,
                )
        }
        return MoveMergeResult(imported = newImported, renameFromOriginal = renameFromOriginal)
    }

    fun remapTimeline(
        timeline: Map<Double, String>,
        renameFromOriginal: Map<String, String>,
    ): Map<Double, String> =
        timeline.mapValues { (_, v) -> renameFromOriginal[v] ?: v }
}
