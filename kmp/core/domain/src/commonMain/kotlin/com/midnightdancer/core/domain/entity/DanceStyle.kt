package com.midnightdancer.core.domain.entity

import kotlinx.serialization.Serializable

/**
 * Style with nested moves — 1:1 with Flutter `lib/data/models/dance_style.dart`.
 *
 * [currentMoveId] pins an element first in the list (outside filter/sort).
 */
@Serializable
data class DanceStyle(
    val id: String,
    val name: String,
    val moves: List<Move> = emptyList(),
    val currentMoveId: String? = null,
)
