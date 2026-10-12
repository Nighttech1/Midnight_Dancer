package com.midnightdancer.core.domain.entity

import com.midnightdancer.core.domain.serialization.DoubleKeyedStringMapSerializer
import kotlinx.serialization.Serializable

/**
 * Choreography with time → move-ref timeline — 1:1 with Flutter `choreography.dart`.
 *
 * Flutter stores [timeline] as `Map<double, String>`; JSON keys are strings (e.g. `"1.5"`).
 * Values are move refs: `styleId::moveId` or legacy bare name/id (see timeline helpers).
 */
@Serializable
data class Choreography(
    val id: String,
    val name: String,
    val songId: String,
    val styleId: String,
    @Serializable(with = DoubleKeyedStringMapSerializer::class)
    val timeline: Map<Double, String> = emptyMap(),
    val startTime: Double = 0.0,
    val endTime: Double,
    /**
     * Playback speed for the attached track **inside this choreography only** (0.5–2.0).
     * Does not mutate [Song.playbackSpeed].
     */
    val songPlaybackSpeed: Double = 1.0,
)
