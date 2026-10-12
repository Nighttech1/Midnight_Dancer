package com.midnightdancer.core.domain.usecase

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.Choreography
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.entity.Song

/** Persist full AppData or upsert catalog entities. Implementation in `:core:data`. */
interface SaveAppData {
    suspend fun save(data: AppData)

    suspend fun upsertSong(song: Song)

    suspend fun upsertMove(styleId: String, move: Move)

    suspend fun upsertStyle(style: DanceStyle)

    suspend fun upsertChoreography(choreography: Choreography)
}
