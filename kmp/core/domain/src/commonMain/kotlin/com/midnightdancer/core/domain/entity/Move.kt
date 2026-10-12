package com.midnightdancer.core.domain.entity

import com.midnightdancer.core.domain.serialization.MoveSerializer
import kotlinx.serialization.Serializable

/**
 * Dance element — 1:1 with Flutter `lib/data/models/move.dart`.
 *
 * Legacy JSON may store the video under `videoFileName` or `videoRef`; both map to [videoUri]
 * with Flutter priority (`videoUri` wins). See [MoveSerializer].
 * [masteryPercent] is 0–100.
 */
@Serializable(with = MoveSerializer::class)
data class Move(
    val id: String,
    val name: String,
    val level: String,
    val description: String? = null,
    val videoUri: String? = null,
    val masteryPercent: Int = 0,
)
