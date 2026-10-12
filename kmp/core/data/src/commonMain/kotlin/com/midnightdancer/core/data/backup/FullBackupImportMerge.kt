package com.midnightdancer.core.data.backup

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.Choreography
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.entity.Song
import com.midnightdancer.core.domain.settings.LastImportManifest
import kotlinx.serialization.json.JsonObject

/**
 * Plan for full-backup merge — Flutter [FullBackupMergePlan].
 * Media write sets tell importers which files to unpack from the archive (C-14 paths).
 */
data class FullBackupMergePlan(
    val merged: AppData,
    val videoMoveIdsToWrite: Set<String>,
    val musicSongIdsToWrite: Set<String>,
    val manifest: LastImportManifest,
)

/**
 * Merge imported metadata into local without deleting existing entities.
 * Parity with Flutter [FullBackupImportMerge].
 */
object FullBackupImportMerge {
    fun mergeInto(
        local: AppData,
        imported: AppData,
        choreographyIdFactory: () -> String = { "import-${imported.choreographies.size}" },
    ): FullBackupMergePlan {
        val imported = ImportMediaRefSanitizer.sanitizeImportedAppData(imported)
        val localMovesById = localMovesById(local)
        val localSongIds = local.songs.map { it.id }.toSet()

        val mergedStyles = mutableListOf<DanceStyle>()
        val importedStyleById = imported.danceStyles.associateBy { it.id }
        val addedWholeStyles = mutableSetOf<String>()
        val addedMoves = mutableSetOf<String>()

        for (locStyle in local.danceStyles) {
            val imp = importedStyleById[locStyle.id]
            if (imp == null) {
                mergedStyles += locStyle
                continue
            }
            val localMoveIds = locStyle.moves.map { it.id }.toSet()
            val extra = imp.moves.filter { it.id !in localMoveIds }
            extra.forEach { addedMoves += it.id }
            mergedStyles += locStyle.copy(moves = locStyle.moves + extra)
        }

        val localStyleIds = local.danceStyles.map { it.id }.toSet()
        for (impStyle in imported.danceStyles) {
            if (impStyle.id !in localStyleIds) {
                mergedStyles += impStyle
                addedWholeStyles += impStyle.id
            }
        }

        val mergedSongs = local.songs.toMutableList()
        val addedSongs = mutableSetOf<String>()
        for (s in imported.songs) {
            if (s.id !in localSongIds) {
                mergedSongs += s
                addedSongs += s.id
            }
        }

        val mergedChoreos = local.choreographies.toMutableList()
        val knownChoreoIds = mergedChoreos.map { it.id }.toMutableSet()
        val addedChoreos = mutableSetOf<String>()
        var suffix = 0
        for (c in imported.choreographies) {
            if (c.id !in knownChoreoIds) {
                mergedChoreos += c
                knownChoreoIds += c.id
                addedChoreos += c.id
            } else {
                suffix++
                val newId = "${c.id}-import-${choreographyIdFactory()}-$suffix"
                mergedChoreos += c.copy(id = newId)
                knownChoreoIds += newId
                addedChoreos += newId
            }
        }

        val merged =
            AppData(
                danceStyles = mergedStyles,
                songs = mergedSongs,
                choreographies = mergedChoreos,
                settings = mergeSettings(local.settings, imported.settings),
            )

        val videoMoveIdsToWrite = mutableSetOf<String>()
        for (style in imported.danceStyles) {
            for (m in style.moves) {
                val loc = localMovesById[m.id]
                if (loc == null) {
                    videoMoveIdsToWrite += m.id
                } else {
                    val hadVideo = !loc.videoUri.isNullOrEmpty()
                    if (!hadVideo) videoMoveIdsToWrite += m.id
                }
            }
        }
        val musicSongIdsToWrite =
            imported.songs.map { it.id }.filter { it !in localSongIds }.toSet()

        val manifest =
            LastImportManifest(
                wholeStyleIds = addedWholeStyles,
                moveIds = addedMoves,
                songIds = addedSongs,
                choreographyIds = addedChoreos,
            )

        return FullBackupMergePlan(
            merged = merged,
            videoMoveIdsToWrite = videoMoveIdsToWrite,
            musicSongIdsToWrite = musicSongIdsToWrite,
            manifest = manifest,
        )
    }

    /** Local settings win; archive fills missing keys only. */
    fun mergeSettings(
        local: JsonObject,
        imported: JsonObject,
    ): JsonObject {
        val map = imported.toMutableMap()
        map.putAll(local)
        return JsonObject(map)
    }

    private fun localMovesById(local: AppData): Map<String, Move> {
        val map = linkedMapOf<String, Move>()
        for (s in local.danceStyles) {
            for (m in s.moves) map[m.id] = m
        }
        return map
    }
}

/** Subset options for full backup export — Flutter [FullBackupExportSubset] skeleton. */
data class FullBackupExportSubset(
    val includeVideos: Boolean = true,
    val includeMusic: Boolean = true,
    val styleIds: Set<String>? = null,
)
