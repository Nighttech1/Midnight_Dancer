package com.midnightdancer.feature.trainer.logic

import com.midnightdancer.feature.choreography.logic.ChoreographySnapshot
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultItem
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultStore
import com.midnightdancer.feature.choreography.logic.EditorClip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** C-276: Live session must carry all choreography music blocks as a playlist. */
class TrainerSessionMusicPlaylistTest {

    @Test
    fun choreographyMode_buildsPlaylistFromAllMusicClips() {
        ChoreographyVaultStore.replaceAll(
            listOf(
                ChoreographyVaultItem(
                    id = "c1",
                    title = "My Set",
                    status = com.midnightdancer.feature.choreography.logic.VaultStatus.Ready,
                    style = "Bachata",
                    tags = listOf("База"),
                    bpm = 128,
                    durationLabel = "0:45",
                    trackName = "Song A",
                    chrId = "chr-c1",
                    snapshot = ChoreographySnapshot(
                        title = "My Set",
                        style = "Bachata",
                        elementClips = listOf(
                            EditorClip("El1", 5_000L, "file://e1.mp4"),
                        ),
                        musicClips = listOf(
                            EditorClip("Song A", 10_000L, "file://a.mp3"),
                            EditorClip("Song B", 20_000L, "file://b.mp3"),
                            EditorClip("Song C", 15_000L, "file://c.mp3"),
                        ),
                    ),
                ),
            ),
        )
        val session = TrainerSessionFactory.buildFromSelectedChoreography(
            hub = TrainerUiState(
                mode = TrainerMode.Choreography,
                selectedChoreographyId = "c1",
                selectedChoreographyTitle = "My Set",
            ),
            data = null,
        )
        assertEquals(3, session.musicPlaylist.size)
        assertEquals(
            listOf("file://a.mp3", "file://b.mp3", "file://c.mp3"),
            session.musicPlaylist.map { it.uri },
        )
        assertEquals(45_000L, session.trackDurationMs)
        assertEquals("file://a.mp3", session.trackMediaUri)
        assertTrue(session.cueSchedule.isNotEmpty())
    }
}
