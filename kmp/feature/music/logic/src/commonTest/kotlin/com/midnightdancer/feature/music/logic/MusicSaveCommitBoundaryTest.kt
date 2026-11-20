package com.midnightdancer.feature.music.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.midnightdancer.core.domain.entity.*
import com.midnightdancer.core.domain.usecase.LoadLibrary
import com.midnightdancer.core.domain.usecase.SaveAppData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class MusicSaveCommitBoundaryTest {
    @Test fun pickedOwnedMusicIsPublishedOnlyAfterCatalogCommit() = runBlocking { checkUpload(false) }
    @Test fun failedPickedMusicCommitDoesNotPublishPhantomTrack() = runBlocking { checkUpload(true) }
    @Test fun editingMusicWaitsForCommitBeforeClosingOrChangingCatalog() = runBlocking { checkEdit(false) }
    @Test fun failedMusicEditKeepsDialogAndPreviousCatalogForRetry() = runBlocking { checkEdit(true) }

    private suspend fun checkUpload(fail: Boolean) {
        val lifecycle = LifecycleRegistry()
        val save = DeferredMusicSave()
        val component = DefaultMusicComponent(DefaultComponentContext(lifecycle), saveAppData = save)
        try {
            val before = component.uiState.value.tracks
            component.onIntent(MusicIntent.AddPickedTrack("QA owned music", "/owned/sha256-music.wav"))
            val song = withTimeout(3000) { save.entered.await() }
            assertTrue(component.uiState.value.saving)
            assertEquals(before, component.uiState.value.tracks)
            assertNull(component.uiState.value.editingTrackId)
            assertEquals("/owned/sha256-music.wav", song.fileName)
            component.onIntent(MusicIntent.AddPickedTrack("Duplicate", "/owned/other.wav"))
            assertEquals(1, save.calls)
            finish(save, fail)
            val done = withTimeout(3000) { component.uiState.first { !it.saving } }
            if (fail) {
                assertNotNull(done.operationError)
                assertEquals(before, done.tracks)
                assertNull(done.editingTrackId)
            } else {
                val track = done.tracks.single { it.id == song.id }
                assertEquals(song.fileName, track.mediaUri)
                assertEquals(track.id, done.editingTrackId)
                assertNull(done.operationError)
            }
        } finally { save.release.complete(Unit); lifecycle.destroy() }
    }

    private suspend fun checkEdit(fail: Boolean) {
        val lifecycle = LifecycleRegistry()
        val save = DeferredMusicSave()
        val data = AppData(songs = listOf(Song("old-song", "Original", "sensual", "", "/owned/old.wav", 3.0, 120)))
        val load = object : LoadLibrary {
            override suspend fun load() = data
            override fun observe(): Flow<AppData> = flowOf(data)
        }
        val component = DefaultMusicComponent(DefaultComponentContext(lifecycle), loadLibrary = load, saveAppData = save)
        try {
            withTimeout(3000) { component.uiState.first { it.tracks.any { row -> row.id == "old-song" } } }
            component.onIntent(MusicIntent.EditTrack("old-song"))
            val before = component.uiState.value.tracks
            component.onIntent(MusicIntent.SaveTrackEdit("old-song", "Edited", "sensual", emptyList(), 1.25))
            val song = withTimeout(3000) { save.entered.await() }
            assertEquals("Edited", song.title)
            assertEquals("/owned/old.wav", song.fileName)
            assertTrue(component.uiState.value.saving)
            assertEquals(before, component.uiState.value.tracks)
            assertEquals("old-song", component.uiState.value.editingTrackId)
            component.onIntent(MusicIntent.CloseEdit)
            assertEquals("old-song", component.uiState.value.editingTrackId)
            component.onIntent(MusicIntent.SaveTrackEdit("old-song", "Second tap", "sensual", emptyList(), 1.0))
            assertEquals(before, component.uiState.value.tracks)
            finish(save, fail)
            val done = withTimeout(3000) { component.uiState.first { !it.saving } }
            if (fail) {
                assertEquals(before, done.tracks)
                assertEquals("old-song", done.editingTrackId)
                assertNotNull(done.operationError)
            } else {
                assertNull(done.editingTrackId)
                assertEquals("Edited", done.tracks.single().title)
                assertEquals("/owned/old.wav", done.tracks.single().mediaUri)
            }
        } finally { save.release.complete(Unit); lifecycle.destroy() }
    }

    private fun finish(save: DeferredMusicSave, fail: Boolean) {
        if (fail) save.release.completeExceptionally(IllegalStateException("catalog unavailable"))
        else save.release.complete(Unit)
    }

    private class DeferredMusicSave : SaveAppData {
        val entered = CompletableDeferred<Song>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        override suspend fun upsertSong(song: Song) { calls++; entered.complete(song); release.await() }
        override suspend fun save(data: AppData) = error("Unexpected full save")
        override suspend fun upsertMove(styleId: String, move: Move) = error("Unexpected move")
        override suspend fun upsertStyle(style: DanceStyle) = error("Unexpected style replacement")
        override suspend fun upsertChoreography(choreography: Choreography) = error("Unexpected choreography")
        override suspend fun ensureDanceStyle(id: String, name: String, currentMoveId: String?) = Unit
        override suspend fun deleteMove(id: String) = error("Unexpected delete")
        override suspend fun deleteSong(id: String) = error("Unexpected delete")
    }
}
