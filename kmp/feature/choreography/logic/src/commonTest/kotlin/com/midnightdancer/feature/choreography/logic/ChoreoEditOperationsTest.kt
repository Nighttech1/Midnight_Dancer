package com.midnightdancer.feature.choreography.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Matrix group D (edits) — headless data-integrity coverage for the Pro Editor, run on any CI with
 * no device or video. Complements the telemetry matrix (playback/scrub/seek) and the Split tests in
 * [ChoreographyLogicSmokeTest]. Here: Delete, Reorder, and combined-edit timeline invariants.
 */
class ChoreoEditOperationsTest {

    // The component runs on Dispatchers.Main; android-unit (Windows) needs it set to a test one.
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private fun editor(): DefaultSequenceEditorStubComponent {
        val lifecycle = LifecycleRegistry()
        val e = DefaultSequenceEditorStubComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()
        return e
    }

    private fun threeElements(e: DefaultSequenceEditorStubComponent) {
        e.onIntent(
            SequenceEditorIntent.AddElementClips(
                listOf(
                    EditorClip("A", 30_000L, mediaUri = "content://el/a"),
                    EditorClip("B", 40_000L, mediaUri = "content://el/b"),
                    EditorClip("C", 50_000L, mediaUri = "content://el/c"),
                ),
            ),
        )
    }

    /** Total = max(elements, music) and must never disagree with the lanes after any edit. */
    private fun assertTimelineInvariants(e: DefaultSequenceEditorStubComponent) {
        val s = e.uiState.value
        assertEquals(maxOf(s.elementsDurationMs, s.musicDurationMs), s.durationMs, "total = max(lanes)")
        assertTrue(s.elementClips.all { it.durationMs >= 1L }, "no zero-length element clip")
        assertTrue(s.musicClips.all { it.durationMs >= 1L }, "no zero-length music clip")
        assertTrue(s.playheadFraction in 0f..1f, "playhead fraction in range")
    }

    // ---- D2: Delete -------------------------------------------------------

    @Test
    fun deleteSelectedElement_removesIt_andRecomputesTotal() {
        val e = editor()
        threeElements(e)
        e.onIntent(SequenceEditorIntent.AddMusicClips(listOf(EditorClip("Bed", 90_000L))))
        assertEquals(120_000L, e.uiState.value.durationMs) // elements 120s > music 90s

        e.onIntent(SequenceEditorIntent.SelectElement(1)) // B
        e.onIntent(SequenceEditorIntent.DeleteSelected)

        val s = e.uiState.value
        assertEquals(listOf("A", "C"), s.elementClips.map { it.label })
        assertEquals(80_000L, s.elementsDurationMs)
        assertEquals(90_000L, s.durationMs) // now music (90s) is the longer lane
        assertEquals(-1, s.selectedElementIndex)
        assertTimelineInvariants(e)
    }

    @Test
    fun deleteSelectedMusic_removesIt_total_fallsBackToElements() {
        val e = editor()
        threeElements(e)
        e.onIntent(SequenceEditorIntent.AddMusicClips(listOf(EditorClip("Bed", 200_000L))))
        assertEquals(200_000L, e.uiState.value.durationMs) // music longest

        e.onIntent(SequenceEditorIntent.SelectMusic(0))
        e.onIntent(SequenceEditorIntent.DeleteSelected)

        val s = e.uiState.value
        assertTrue(s.musicClips.isEmpty())
        assertEquals(0L, s.musicDurationMs)
        assertEquals(120_000L, s.durationMs) // falls back to elements
        assertTimelineInvariants(e)
    }

    @Test
    fun deleteAllElements_leavesEmptyLane_noCrash() {
        val e = editor()
        threeElements(e)
        repeat(3) {
            e.onIntent(SequenceEditorIntent.SelectElement(0))
            e.onIntent(SequenceEditorIntent.DeleteSelected)
        }
        val s = e.uiState.value
        assertTrue(s.elementClips.isEmpty())
        assertEquals(0L, s.elementsDurationMs)
        assertEquals(0L, s.durationMs)
        assertTimelineInvariants(e)
    }

    // ---- D3: Reorder ------------------------------------------------------

    @Test
    fun reorderElements_changesOrder_keepsTotal() {
        val e = editor()
        threeElements(e)
        val before = e.uiState.value.durationMs

        e.onIntent(SequenceEditorIntent.ReorderElements(from = 0, to = 2)) // A → end

        val s = e.uiState.value
        assertEquals(listOf("B", "C", "A"), s.elementClips.map { it.label })
        assertEquals(before, s.durationMs) // reorder never changes total
        assertEquals(2, s.selectedElementIndex)
        assertTimelineInvariants(e)
    }

    @Test
    fun reorderElements_sourceStartMsTravelsWithClip() {
        val e = editor()
        // Split A so the second half has a non-zero sourceStartMs, then reorder it.
        e.onIntent(SequenceEditorIntent.AddElementClips(listOf(EditorClip("A", 100_000L, mediaUri = "content://el/a"))))
        e.onIntent(SequenceEditorIntent.SeekTo(0.25f))
        e.onIntent(SequenceEditorIntent.SelectElement(0))
        e.onIntent(SequenceEditorIntent.SplitSelected) // [A@0 25s, A@25s 75s]
        e.onIntent(SequenceEditorIntent.ReorderElements(from = 1, to = 0)) // move the @25s half first

        val s = e.uiState.value
        assertEquals(2, s.elementClips.size)
        assertEquals(25_000L, s.elementClips[0].sourceStartMs) // the moved half kept its source offset
        assertEquals(0L, s.elementClips[1].sourceStartMs)
        assertTimelineInvariants(e)
    }

    // ---- D (combined) + C-315 stability -----------------------------------

    @Test
    fun combinedEdits_keepTimelineInvariants() {
        val e = editor()
        threeElements(e)
        e.onIntent(SequenceEditorIntent.AddMusicClips(listOf(EditorClip("Bed", 90_000L))))

        e.onIntent(SequenceEditorIntent.SeekTo(0.5f))
        e.onIntent(SequenceEditorIntent.SelectElement(1))
        e.onIntent(SequenceEditorIntent.SetClipSpeed(2.0))          // B shrinks
        assertTimelineInvariants(e)

        e.onIntent(SequenceEditorIntent.ReorderElements(from = 2, to = 0))
        assertTimelineInvariants(e)

        e.onIntent(SequenceEditorIntent.SelectElement(0))
        e.onIntent(SequenceEditorIntent.DeleteSelected)
        assertTimelineInvariants(e)

        // compositionSeekTarget must resolve for every ms across the final element lane.
        val clips = e.uiState.value.elementTimelineClips
        val total = clips.sumOf { it.durationMs }
        for (t in 0..total step (total / 5).coerceAtLeast(1L)) {
            val mapped = EditorTrackDurations.compositionSeekTarget(clips, t)
            assertTrue(mapped != null && mapped.first in clips.indices, "seek target valid at $t")
        }
    }

    @Test
    fun pureSeek_doesNotChangeTotal_C315() {
        // C-315 guard: the timeline total must NOT move while only scrubbing/seeking.
        val e = editor()
        threeElements(e)
        e.onIntent(SequenceEditorIntent.AddMusicClips(listOf(EditorClip("Bed", 90_000L))))
        val total = e.uiState.value.durationMs
        listOf(0.1f, 0.9f, 0.3f, 1f, 0f, 0.55f).forEach {
            e.onIntent(SequenceEditorIntent.SeekTo(it))
            assertEquals(total, e.uiState.value.durationMs, "total stable during seek")
        }
    }

    // ---- edge cases that must never crash -----------------------------------

    @Test
    fun seekAndToggle_onEmptyTimeline_noCrash() {
        val e = editor()
        e.onIntent(SequenceEditorIntent.SeekTo(0.5f))
        e.onIntent(SequenceEditorIntent.SeekEnd)
        e.onIntent(SequenceEditorIntent.SeekStart)
        e.onIntent(SequenceEditorIntent.TogglePlay)
        assertEquals(0L, e.uiState.value.durationMs)
        assertTrue(!e.uiState.value.isPlaying) // play ignored on empty
        assertTimelineInvariants(e)
    }

    @Test
    fun deleteWithClearedSelection_isNoOp() {
        val e = editor()
        threeElements(e)
        e.onIntent(SequenceEditorIntent.SelectElement(-1)) // clear selection (also clears music)
        e.onIntent(SequenceEditorIntent.DeleteSelected)
        assertEquals(3, e.uiState.value.elementClips.size)
        assertTimelineInvariants(e)
    }

    @Test
    fun reorderWithInvalidIndices_isNoOp() {
        val e = editor()
        threeElements(e)
        val order = e.uiState.value.elementClips.map { it.label }
        e.onIntent(SequenceEditorIntent.ReorderElements(from = 5, to = 0))
        e.onIntent(SequenceEditorIntent.ReorderElements(from = 0, to = 9))
        assertEquals(order, e.uiState.value.elementClips.map { it.label })
        assertTimelineInvariants(e)
    }

    @Test
    fun rapidSeekSweep_singleClip_staysConsistent() {
        val e = editor()
        e.onIntent(SequenceEditorIntent.AddElementClips(listOf(EditorClip("Solo", 60_000L, mediaUri = "content://el/s"))))
        // Scrub-like sweep forward and backward many times.
        var f = 0f
        repeat(50) {
            f = ((it * 7) % 100) / 100f
            e.onIntent(SequenceEditorIntent.SeekTo(f))
            assertTimelineInvariants(e)
        }
        assertEquals(0, e.uiState.value.playheadElementIndex)
    }

    @Test
    fun deleteLastElement_whileSelected_thenSeek_noCrash() {
        val e = editor()
        e.onIntent(SequenceEditorIntent.AddElementClips(listOf(EditorClip("Only", 40_000L))))
        e.onIntent(SequenceEditorIntent.SelectElement(0))
        e.onIntent(SequenceEditorIntent.DeleteSelected)
        e.onIntent(SequenceEditorIntent.SeekTo(0.7f))
        e.onIntent(SequenceEditorIntent.TogglePlay)
        assertTrue(e.uiState.value.elementClips.isEmpty())
        assertTimelineInvariants(e)
    }
}
