package com.midnightdancer.core.domain.usecase

import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.timeline.ChoreographyTimelineRef

/**
 * Resolve choreography timeline values to moves.
 * Pure algorithms also available via [ChoreographyTimelineRef]; this interface is for DI.
 */
interface ResolveTimelineRefs {
    fun encode(styleId: String, moveId: String): String

    fun decode(value: String): ChoreographyTimelineRef.Parts?

    fun resolveMove(
        styles: List<DanceStyle>,
        choreographyStyleId: String,
        value: String,
    ): Move?
}
