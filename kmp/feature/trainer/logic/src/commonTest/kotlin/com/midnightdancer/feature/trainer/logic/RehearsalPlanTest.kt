package com.midnightdancer.feature.trainer.logic

import com.midnightdancer.feature.choreography.logic.ChoreographySnapshot
import com.midnightdancer.feature.choreography.logic.EditorClip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RehearsalPlanTest {
    private val settings = RehearsalSettings("c1", "r1", 35_000L, 55_000L, 5, 8_000L, 3_000L)

    @Test
    fun canonicalExample_hasFivePassesFourRestsAnd135Seconds() {
        assertEquals(135_000L, settings.estimatedDurationMs)
        assertTrue(RehearsalValidator.errors(settings, 90_000L, "r1").isEmpty())
        assertEquals(23_000L, settings.copy(totalPasses = 1).estimatedDurationMs)
    }

    @Test
    fun validation_rejectsUnknownDurationStaleRevisionAndOutOfRangeValues() {
        assertTrue(RehearsalValidator.errors(settings, null, "r1").isNotEmpty())
        assertTrue(RehearsalValidator.errors(settings, 90_000L, "r2").isNotEmpty())
        listOf(
            settings.copy(startMs = -100L), settings.copy(endMs = 35_000L),
            settings.copy(endMs = 95_000L), settings.copy(endMs = 35_900L),
            settings.copy(totalPasses = 0), settings.copy(totalPasses = 100),
            settings.copy(restMs = -1L), settings.copy(restMs = 120_001L),
            settings.copy(restMs = 1_500L), settings.copy(preparationMs = 10_001L),
            settings.copy(preparationMs = -1L), settings.copy(schemaVersion = 2),
            settings.copy(startMs = 35_001L), settings.copy(endMs = 55_001L),
        ).forEach { invalid -> assertTrue(RehearsalValidator.errors(invalid, 90_000L, "r1").isNotEmpty(), invalid.toString()) }
    }

    @Test
    fun exactEnd_canKeepNonRoundedDurationButManualTimeCannot() {
        val exact = settings.copy(startMs = 0L, endMs = 5_037L)
        assertTrue(RehearsalValidator.errors(exact, 5_037L, "r1").isEmpty())
        assertTrue(RehearsalValidator.errors(exact, 6_000L, "r1").isNotEmpty())
    }

    @Test
    fun trimmedPlaylist_mapsAcrossSourceOffsetsAndSpeedAdjustedElements() {
        val result = RehearsalPlanBuilder.fromSnapshot("c1", snapshot())
        val plan = assertNotNull(result.plan)
        assertEquals(10_000L, plan.durationMs)
        assertEquals(0 to 2_500L, plan.sourcePositionAt(500L))
        assertEquals(1 to 10_000L, plan.sourcePositionAt(4_000L))
        assertEquals(1 to 15_999L, plan.sourcePositionAt(9_999L))
        assertNull(plan.sourcePositionAt(10_000L))
        assertEquals(2_000L, plan.elements[0].endMs)
        assertEquals(2_000L, plan.elements[1].startMs)
    }

    @Test
    fun fingerprint_changesForTimingOrCueLabelsButNotTitle() {
        val source = snapshot()
        val original = assertNotNull(RehearsalPlanBuilder.fromSnapshot("c1", source).plan)
        val renamed = assertNotNull(RehearsalPlanBuilder.fromSnapshot("c1", source.copy(title = "Renamed")).plan)
        val changed = assertNotNull(RehearsalPlanBuilder.fromSnapshot("c1", source.copy(
            elementClips = source.elementClips.mapIndexed { i, clip -> if (i == 0) clip.copy(durationMs = 5_000L) else clip },
        )).plan)
        assertEquals(original.revision, renamed.revision)
        assertNotEquals(original.revision, changed.revision)
    }

    @Test
    fun builder_rejectsUnavailableMusicInvalidDurationsAndNonOneMusicSpeed() {
        assertNotNull(RehearsalPlanBuilder.fromSnapshot("c1", snapshot().copy(songPlaybackSpeed = 0.75)).error)
        assertNotNull(RehearsalPlanBuilder.fromSnapshot("c1", snapshot().copy(musicClips = emptyList())).error)
        assertNotNull(RehearsalPlanBuilder.fromSnapshot("c1", snapshot(), resolveMusicUri = { null }).error)
        assertNotNull(RehearsalPlanBuilder.fromSnapshot("c1", snapshot().copy(
            elementClips = listOf(EditorClip("Bad", 1_000L, speed = Double.NaN)),
        )).error)
    }

    @Test
    fun musicVolume_isPreservedClampedAndNonFiniteIsRejected() {
        assertEquals(0.35f, assertNotNull(RehearsalPlanBuilder.fromSnapshot(
            "c1", snapshot().copy(musicVolume = 0.35f),
        ).plan).musicVolume)
        assertEquals(1f, assertNotNull(RehearsalPlanBuilder.fromSnapshot(
            "c1", snapshot().copy(musicVolume = 2f),
        ).plan).musicVolume)
        assertEquals(0f, assertNotNull(RehearsalPlanBuilder.fromSnapshot(
            "c1", snapshot().copy(musicVolume = -0.5f),
        ).plan).musicVolume)
        assertNotNull(RehearsalPlanBuilder.fromSnapshot("c1", snapshot().copy(musicVolume = Float.NaN)).error)
        assertNotNull(RehearsalPlanBuilder.fromSnapshot("c1", snapshot().copy(musicVolume = Float.POSITIVE_INFINITY)).error)
    }

    @Test
    fun cues_includeActiveAtAExcludeBAndDeduplicateByStableOrder() {
        val plan = RehearsalPlan("c1", "r1", 30_000L, emptyList(), listOf(
            RehearsalElement(0L, 10_000L, "X", 0),
            RehearsalElement(10_000L, 20_000L, "Y", 1),
            RehearsalElement(20_000L, 30_000L, "Z", 2),
        ))
        val section = RehearsalSettings("c1", "r1", 5_000L, 20_000L)
        assertEquals(listOf(RehearsalCue(0L, "X"), RehearsalCue(5_000L, "Y")), plan.cues(section))
        assertEquals(listOf(RehearsalCue(0L, "Y")), plan.cues(section.copy(startMs = 10_000L)))
        val simultaneous = plan.copy(elements = listOf(
            RehearsalElement(0L, 20_000L, "First", 0),
            RehearsalElement(5_000L, 20_000L, "Last", 1),
        ))
        assertEquals(listOf("First", "Last"), simultaneous.visualElementsAt(5_000L).map { it.label })
        assertEquals(listOf(RehearsalCue(0L, "Last")), simultaneous.cues(section))
    }

    private fun snapshot() = ChoreographySnapshot(
        title = "Practice", style = "Salsa",
        musicClips = listOf(
            EditorClip("A", 4_000L, "file://a.mp3", sourceStartMs = 2_000L),
            EditorClip("B", 6_000L, "file://b.mp3", sourceStartMs = 10_000L),
        ),
        elementClips = listOf(
            EditorClip("Fast", 4_000L, speed = 2.0),
            EditorClip("Next", 8_000L),
        ),
    )
}
