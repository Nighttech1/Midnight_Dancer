package com.midnightdancer.feature.trainer.logic

import com.midnightdancer.feature.choreography.logic.ChoreographySnapshot
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultItem
import com.midnightdancer.feature.choreography.logic.EditorClip
import com.midnightdancer.feature.choreography.logic.VaultStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RehearsalSelectionResolverTest {
    @Test
    fun blankSelectionDoesNotUseFirstPlayableItem() {
        val result = RehearsalSelectionResolver.resolve("  ", listOf(item("playable", playable())))

        assertFalse(result.canConfigure)
        assertEquals("  ", result.choreographyId)
        assertEquals("Сначала выберите хореографию", result.planResult.error)
        assertNull(result.planResult.plan)
        assertNull(result.durationMs)
    }

    @Test
    fun missingSelectionDoesNotResolveByTitleOrTrimmedId() {
        val result = RehearsalSelectionResolver.resolve(
            "chosen",
            listOf(item(" chosen ", playable(), title = "chosen")),
        )

        assertFalse(result.canConfigure)
        assertEquals("chosen", result.choreographyId)
        assertEquals("Выбранная хореография недоступна", result.planResult.error)
        assertNull(result.planResult.plan)
        assertNull(result.durationMs)
    }

    @Test
    fun selectedItemWithoutSnapshotStillExposesFormWithoutBorrowedMusic() {
        val result = RehearsalSelectionResolver.resolve(
            "selected",
            listOf(item("other", playable()), item("selected", null, "Selected title")),
        )

        assertTrue(result.canConfigure)
        assertEquals("selected", result.choreographyId)
        assertEquals("Selected title", result.title)
        assertEquals("Для запуска добавьте музыку и сохраните эту хореографию", result.planResult.error)
        assertNull(result.planResult.plan)
        assertNull(result.durationMs)
    }

    @Test
    fun exactSelectedIdWinsDespiteDuplicateTitlesAndOtherPlayableItem() {
        val selectedSnapshot = playable("file:///selected.wav", durationMs = 7_250L)
        val result = RehearsalSelectionResolver.resolve(
            "selected",
            listOf(item("other", playable(), "Same"), item("selected", selectedSnapshot, "Same")),
        )

        val plan = assertNotNull(result.planResult.plan)
        assertTrue(result.canConfigure)
        assertEquals("selected", plan.choreographyId)
        assertEquals("file:///selected.wav", plan.musicClips.single().uri)
        assertEquals(7_250L, result.durationMs)
        assertEquals(RehearsalPlanBuilder.fromSnapshot("selected", selectedSnapshot), result.planResult)
    }

    @Test
    fun savedEmptyMusicIsAuthoritativeOverTrackDisplayMetadata() {
        val snapshot = ChoreographySnapshot(title = "Snapshot", style = "Style")
        val result = RehearsalSelectionResolver.resolve(
            "selected",
            listOf(item("other", playable()), item("selected", snapshot)),
        )

        assertTrue(result.canConfigure)
        assertEquals(RehearsalPlanBuilder.fromSnapshot("selected", snapshot), result.planResult)
        assertEquals("Добавьте музыку в постановку.", result.planResult.error)
        assertNull(result.durationMs)
    }

    @Test
    fun invalidSelectedAudioPreservesBuilderErrorWithoutFallback() {
        val snapshot = playable(uri = "")
        val result = RehearsalSelectionResolver.resolve(
            "selected",
            listOf(item("other", playable()), item("selected", snapshot)),
        )

        assertTrue(result.canConfigure)
        assertEquals(RehearsalPlanBuilder.fromSnapshot("selected", snapshot), result.planResult)
        assertNull(result.planResult.plan)
        assertNull(result.durationMs)
    }

    @Test
    fun subsequentResolutionUsesUpdatedSnapshotRatherThanPreviousPlan() {
        val initial = RehearsalSelectionResolver.resolve("selected", listOf(item("selected", playable())))
        val updated = RehearsalSelectionResolver.resolve("selected", listOf(item("selected", null)))

        assertNotNull(initial.planResult.plan)
        assertTrue(updated.canConfigure)
        assertNull(updated.planResult.plan)
        assertNull(updated.durationMs)
    }

    private fun playable(uri: String = "file:///other.wav", durationMs: Long = 4_000L) =
        ChoreographySnapshot(
            title = "Snapshot title",
            style = "Style",
            musicClips = listOf(EditorClip("Saved music", durationMs, uri, sourceStartMs = 500L)),
        )

    private fun item(id: String, snapshot: ChoreographySnapshot?, title: String = "Title") =
        ChoreographyVaultItem(
            id = id,
            title = title,
            status = VaultStatus.Ready,
            style = "Style",
            bpm = 120,
            durationLabel = "99:59",
            trackName = "Unrelated display track",
            chrId = "unrelated-legacy-id",
            snapshot = snapshot,
        )
}
