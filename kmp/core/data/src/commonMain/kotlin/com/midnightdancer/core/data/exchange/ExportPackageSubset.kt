package com.midnightdancer.core.data.exchange

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.exchange.ExportPackageSelection

/** Вырезать из каталога только выбранные сущности для zip (C-115). */
object ExportPackageSubset {
    fun apply(
        full: AppData,
        selection: ExportPackageSelection,
    ): AppData {
        val moveIds = selection.moveIds.toSet()
        val songIds = selection.songIds.toSet()
        val choreoIds = selection.choreographyIds.toSet()
        val styleIdsNeeded =
            full.choreographies
                .filter { it.id in choreoIds }
                .map { it.styleId }
                .toMutableSet()

        val styles =
            full.danceStyles.mapNotNull { style ->
                val moves = style.moves.filter { it.id in moveIds }
                when {
                    moves.isNotEmpty() -> {
                        styleIdsNeeded += style.id
                        style.copy(moves = moves)
                    }
                    style.id in styleIdsNeeded -> style.copy(moves = emptyList())
                    else -> null
                }
            }

        // Ensure choreo-referenced empty shells exist even if no moves selected from that style.
        val present = styles.map { it.id }.toSet()
        val shells =
            full.danceStyles
                .filter { it.id in styleIdsNeeded && it.id !in present }
                .map { it.copy(moves = emptyList()) }

        return full.copy(
            danceStyles = styles + shells,
            songs = full.songs.filter { it.id in songIds },
            choreographies = full.choreographies.filter { it.id in choreoIds },
        )
    }
}
