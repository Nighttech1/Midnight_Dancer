package com.midnightdancer.feature.choreography.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Wave E: map timeline → (clip index, local offset) for overlay/reorder; CompositionPlayer seeks absolute ms.
 */
class CompositionSeekTargetTest {

    private fun clip(label: String, dur: Long, speed: Double = 1.0) = EditorClip(
        label = label,
        durationMs = dur,
        mediaUri = "file://$label",
        speed = speed,
    )

    @Test
    fun empty_returns_null() {
        assertNull(EditorTrackDurations.compositionSeekTarget(emptyList(), 0L))
    }

    @Test
    fun three_clips_1s_each_zero_mid_second_seam_end() {
        val clips = listOf(clip("a", 1000), clip("b", 1000), clip("c", 1000))
        assertEquals(0 to 0L, EditorTrackDurations.compositionSeekTarget(clips, 0L))
        assertEquals(1 to 500L, EditorTrackDurations.compositionSeekTarget(clips, 1500L))
        // Second seam = start of clip 2 (index 2)
        assertEquals(2 to 0L, EditorTrackDurations.compositionSeekTarget(clips, 2000L))
        assertEquals(2 to 999L, EditorTrackDurations.compositionSeekTarget(clips, 2999L))
        assertEquals(2 to 999L, EditorTrackDurations.compositionSeekTarget(clips, 5000L))
    }

    @Test
    fun speed_2x_shortens_presentation() {
        // raw 1000 ms at 2x → 500 ms on timeline
        val clips = listOf(clip("a", 1000, speed = 2.0), clip("b", 1000, speed = 1.0))
        assertEquals(0 to 250L, EditorTrackDurations.compositionSeekTarget(clips, 250L))
        assertEquals(1 to 0L, EditorTrackDurations.compositionSeekTarget(clips, 500L))
        assertEquals(1 to 100L, EditorTrackDurations.compositionSeekTarget(clips, 600L))
    }

    @Test
    fun play_from_middle_of_second_and_scrub_to_zero() {
        val clips = listOf(clip("a", 2000), clip("b", 2000), clip("c", 2000))
        assertEquals(1 to 1000L, EditorTrackDurations.compositionSeekTarget(clips, 3000L))
        assertEquals(0 to 0L, EditorTrackDurations.compositionSeekTarget(clips, 0L))
        assertEquals(0 to 500L, EditorTrackDurations.compositionSeekTarget(clips, 500L))
    }

    /** Wave E: index = current lane order after drag-reorder, not import order. */
    @Test
    fun after_swap_first_two_indices_follow_new_order() {
        val a = clip("a", 1000)
        val b = clip("b", 1000)
        val c = clip("c", 1000)
        val before = listOf(a, b, c)
        // Mid of first slot → clip a
        assertEquals(0 to 500L, EditorTrackDurations.compositionSeekTarget(before, 500L))
        // After ReorderElements(0,1): b, a, c — mid of first slot → clip b
        val after = listOf(b, a, c)
        assertEquals(0 to 500L, EditorTrackDurations.compositionSeekTarget(after, 500L))
        assertEquals(1 to 500L, EditorTrackDurations.compositionSeekTarget(after, 1500L))
        // Same timeline ms maps to different media when order changes
        assertEquals(
            before[0].label,
            before[EditorTrackDurations.compositionSeekTarget(before, 500L)!!.first].label,
        )
        assertEquals(
            after[0].label,
            after[EditorTrackDurations.compositionSeekTarget(after, 500L)!!.first].label,
        )
        assertEquals("b", after[EditorTrackDurations.compositionSeekTarget(after, 500L)!!.first].label)
        assertEquals("a", before[EditorTrackDurations.compositionSeekTarget(before, 500L)!!.first].label)
    }
}
