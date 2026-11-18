package com.midnightdancer.feature.choreography.logic

import com.midnightdancer.core.domain.entity.*
import com.midnightdancer.core.domain.styles.*
import kotlin.test.*

class CanonicalVaultStylesTest {
    private val snapshot = DanceStyleCatalogSnapshot(2,
        listOf(DanceStyleEntry(ReservedDanceStyle.ID, ReservedDanceStyle.NAME, true), DanceStyleEntry("b", "Beta")),
        mapOf("a" to ReservedDanceStyle.ID), mapOf("alpha" to ReservedDanceStyle.ID))

    @Test fun vaultAndSnapshotAreReassignedWithoutChangingAnyClipField() {
        val clip = EditorClip("Own clip", 4150, "owned.mp4", 0.75, 250)
        val before = ChoreographyVaultItem("c", "Own", VaultStatus.Ready, "a", bpm = 90,
            durationLabel = "4:15", trackName = "Song", chrId = "c", snapshot = ChoreographySnapshot("Own", "Alpha", listOf(clip)))
        val expected = before.copy(style = ReservedDanceStyle.ID, snapshot = before.snapshot!!.copy(style = ReservedDanceStyle.ID))
        assertEquals(listOf(expected), normalizeVaultStyles(listOf(before), snapshot))
        assertEquals(listOf(expected), normalizeVaultStyles(normalizeVaultStyles(listOf(before), snapshot), snapshot))
        assertEquals(listOf(clip), expected.snapshot!!.elementClips)
    }

    @Test fun noMatchingStyleNeverOffersForeignMedia() {
        ChoreoCatalog.replaceStyleCatalog(snapshot)
        try {
        ChoreoCatalog.replaceFromAppData(AppData(
            danceStyles = listOf(DanceStyle("b", "Beta", listOf(Move("m", "Foreign", "", videoUri = "foreign.mp4")))),
            songs = listOf(Song("s", "Foreign song", "b", "", "foreign.wav", 4.0, 20))))
        assertTrue(ChoreoCatalog.elementsForStyle(ReservedDanceStyle.ID).isEmpty())
        assertTrue(ChoreoCatalog.elementsForStyle("").isEmpty())
        assertTrue(ChoreoCatalog.tracksForStyle(ReservedDanceStyle.ID).isEmpty())
        assertTrue(ChoreoCatalog.tracksForStyle("").isEmpty())
        assertEquals("m", ChoreoCatalog.elementsForStyle("b").single().id)
        assertEquals("s", ChoreoCatalog.tracksForStyle("b").single().id)
        } finally { ChoreoCatalog.replaceStyleCatalog(null) }
    }
}
