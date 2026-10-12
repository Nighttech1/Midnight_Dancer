package com.midnightdancer.core.data.backup

import com.midnightdancer.core.data.choreography.ChoreoScopedIds
import com.midnightdancer.core.data.choreography.ChoreographyImportMerge
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Song
import com.midnightdancer.core.domain.settings.LastImportManifest
import com.midnightdancer.core.domain.usecase.MergeImport
import com.midnightdancer.core.domain.usecase.MergeImportResult

/** [MergeImport] over full-backup + choreography-package merge algorithms. */
class DefaultMergeImport : MergeImport {
    override suspend fun mergeFullBackup(
        local: AppData,
        imported: AppData,
    ): MergeImportResult {
        val plan = FullBackupImportMerge.mergeInto(local, imported)
        val settings = LastImportManifest.embed(plan.merged.settings, plan.manifest)
        return MergeImportResult(
            merged = plan.merged.copy(settings = settings),
            manifest = plan.manifest,
        )
    }

    /**
     * Choreography package (C-113):
     * - Choreography → Vault with timeline **names** for trainer.
     * - Package moves live in a **ghost style** (not shown in Elements).
     * - Package songs get scoped ids (not shown in Music).
     * Editor can still resolve names via ghost style + scoped songs.
     */
    override suspend fun mergeChoreographyPackage(
        local: AppData,
        packageData: AppData,
    ): MergeImportResult {
        val pkgStyle = packageData.danceStyles.firstOrNull()
        if (pkgStyle == null && packageData.choreographies.isEmpty()) {
            return MergeImportResult(merged = local, manifest = LastImportManifest.empty())
        }

        val payloadMoves = pkgStyle?.moves.orEmpty()
        val ghostExisting =
            local.danceStyles.find { ChoreoScopedIds.isGhostStyle(it.id) }?.moves.orEmpty()
        val mergeMoves =
            ChoreographyImportMerge.mergeMoves(
                existingMoves = ghostExisting,
                payloadMoves = payloadMoves,
            ) { idx -> "${ChoreoScopedIds.GHOST_STYLE_ID}-imp-$idx" }

        val ghostStyle =
            DanceStyle(
                id = ChoreoScopedIds.GHOST_STYLE_ID,
                name = ChoreoScopedIds.GHOST_STYLE_NAME,
                moves = ghostExisting + mergeMoves.imported,
            )
        val stylesWithoutGhost =
            local.danceStyles.filterNot { ChoreoScopedIds.isGhostStyle(it.id) }
        val styles = stylesWithoutGhost + ghostStyle

        val songIdRemap = linkedMapOf<String, String>()
        val newSongs = mutableListOf<Song>()
        for (song in packageData.songs) {
            val scopedId = ChoreoScopedIds.scopedSongId(song.id)
            songIdRemap[song.id] = scopedId
            if (local.songs.none { it.id == scopedId } && newSongs.none { it.id == scopedId }) {
                newSongs += song.copy(id = scopedId)
            }
        }

        val choreoIds = local.choreographies.map { it.id }.toMutableSet()
        val newChoreos =
            packageData.choreographies.map { c ->
                val remappedTimeline =
                    ChoreographyImportMerge.remapTimeline(
                        c.timeline,
                        mergeMoves.renameFromOriginal,
                    )
                val songId = songIdRemap[c.songId] ?: c.songId
                val base =
                    c.copy(
                        timeline = remappedTimeline,
                        songId = songId,
                        // Style pointer stays local preferred; ghost not a user style.
                        styleId = c.styleId.takeIf { it != pkgStyle?.id }
                            ?: local.danceStyles.firstOrNull()?.id
                            ?: c.styleId,
                    )
                if (base.id in choreoIds) {
                    base.copy(id = "${base.id}-pkg")
                } else {
                    base
                }.also { choreoIds += it.id }
            }

        val manifest =
            LastImportManifest(
                wholeStyleIds = emptySet(),
                moveIds = emptySet(), // not catalog moves
                songIds = emptySet(), // not catalog songs
                choreographyIds = newChoreos.map { it.id }.toSet(),
            )
        val merged =
            AppData(
                danceStyles = styles,
                songs = local.songs + newSongs,
                choreographies = local.choreographies + newChoreos,
                settings = LastImportManifest.embed(local.settings, manifest),
            )
        return MergeImportResult(merged = merged, manifest = manifest)
    }
}
