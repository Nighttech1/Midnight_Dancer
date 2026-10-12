package com.midnightdancer.core.data.debug

import com.midnightdancer.core.data.db.RoomCatalogRepository
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.Choreography
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.entity.Song

/**
 * DEBUG / Client edge-stress fixtures — stub catalog rows for 50+ scroll/list load.
 *
 * IDs use [ID_PREFIX] so re-seed is idempotent for the stress style and stub songs.
 * Does **not** wipe the rest of the library (upsert merge).
 */
object EdgeStressCatalogSeeder {
    const val STYLE_ID: String = "edge-stress"
    const val STYLE_NAME: String = "Edge Stress"
    const val ID_PREFIX: String = "edge-stress-"
    const val DEFAULT_COUNT: Int = 55
    const val DEFAULT_CHOREO_COUNT: Int = 5

    private val LEVELS = listOf("Начинающий", "Базовый", "Средний", "Про")

    data class Result(
        val moveCount: Int,
        val songCount: Int,
        val choreographyCount: Int,
        val styleId: String = STYLE_ID,
    )

    fun buildStubCatalog(
        count: Int = DEFAULT_COUNT,
        choreographyCount: Int = DEFAULT_CHOREO_COUNT.coerceAtMost(count.coerceAtLeast(0)),
    ): AppData {
        val n = count.coerceAtLeast(0)
        val moves =
            (1..n).map { i ->
                val level = LEVELS[(i - 1) % LEVELS.size]
                Move(
                    id = moveId(i),
                    name = "Edge Stub Element $i",
                    level = level,
                    description = "DEBUG edge-stress stub #$i (not a real lesson).",
                    videoUri = "${moveId(i)}.mp4",
                    masteryPercent = (i * 7) % 101,
                )
            }
        val songs =
            (1..n).map { i ->
                val level = LEVELS[(i - 1) % LEVELS.size]
                Song(
                    id = songId(i),
                    title = "Edge Stub Track $i",
                    danceStyle = STYLE_NAME,
                    level = level,
                    fileName = "${songId(i)}.mp3",
                    duration = 30.0 + (i % 90),
                    sizeBytes = 1024 + i,
                    playbackSpeed = 1.0,
                )
            }
        val choreos =
            (1..choreographyCount.coerceAtLeast(0)).map { i ->
                val song = songs.getOrElse(i - 1) { songs.first() }
                val moveA = moves.getOrElse(i - 1) { moves.first() }
                val moveB = moves.getOrElse(i) { moveA }
                Choreography(
                    id = choreoId(i),
                    name = "Edge Stub Choreo $i",
                    songId = song.id,
                    styleId = STYLE_ID,
                    timeline =
                        mapOf(
                            0.0 to "$STYLE_ID::${moveA.id}",
                            4.0 to moveB.id,
                        ),
                    startTime = 0.0,
                    endTime = 60.0 + i,
                )
            }
        return AppData(
            danceStyles =
                listOf(
                    DanceStyle(
                        id = STYLE_ID,
                        name = STYLE_NAME,
                        moves = moves,
                        currentMoveId = moves.firstOrNull()?.id,
                    ),
                ),
            songs = songs,
            choreographies = choreos,
        )
    }

    /**
     * Upserts stress style (N moves), N songs, and a few choreographies into Room.
     * Existing non-prefix catalog rows are left intact.
     */
    suspend fun seedInto(
        repository: RoomCatalogRepository,
        count: Int = DEFAULT_COUNT,
        choreographyCount: Int = DEFAULT_CHOREO_COUNT,
    ): Result {
        val stub = buildStubCatalog(count = count, choreographyCount = choreographyCount)
        val style = stub.danceStyles.single()
        repository.upsertStyle(style)
        stub.songs.forEach { repository.upsertSong(it) }
        stub.choreographies.forEach { repository.upsertChoreography(it) }
        return Result(
            moveCount = style.moves.size,
            songCount = stub.songs.size,
            choreographyCount = stub.choreographies.size,
        )
    }

    fun moveId(index: Int): String = "${ID_PREFIX}move-$index"

    fun songId(index: Int): String = "${ID_PREFIX}song-$index"

    fun choreoId(index: Int): String = "${ID_PREFIX}choreo-$index"
}
