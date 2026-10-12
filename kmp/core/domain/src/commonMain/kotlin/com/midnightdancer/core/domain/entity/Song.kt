package com.midnightdancer.core.domain.entity

import kotlinx.serialization.Serializable

/**
 * Library track — 1:1 with Flutter `lib/data/models/song.dart`.
 *
 * [playbackSpeed] is the **base** listening speed persisted in AppData (0.4–2.0).
 * Choreography may override independently via [Choreography.songPlaybackSpeed].
 */
@Serializable
data class Song(
    val id: String,
    val title: String,
    val danceStyle: String,
    val level: String,
    val fileName: String,
    val duration: Double,
    val sizeBytes: Int,
    val playbackSpeed: Double = 1.0,
)
