package com.midnightdancer.feature.music.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.midnightdancer.core.domain.entity.*
import com.midnightdancer.core.domain.usecase.LoadLibrary
import com.midnightdancer.core.domain.usecase.SaveAppData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import kotlin.test.*

class MusicSaveCommitBoundaryTest {
    @Test fun pickedOwnedMusicIsPublishedOnlyAfterCatalogCommit() = runBlocking { checkUpload(false) }
    @Test fun failedPickedMusicCommitDoesNotPublishPhantomTrack() = runBlocking { checkUpload(true) }
    @Test fun editingMusicWaitsForCommitBeforeClosingOrChangingCatalog() = runBlocking { checkEdit(false) }
    @Test fun failedMusicEditKeepsDialogAndPreviousCatalogForRetry() = runBlocking { checkEdit(true) }

    @Test fun measuredAddEditAndDeleteUseOneGuardedWriteAtATime() = runBlocking {
        val lifecycle = LifecycleRegistry()
        val writes = Channel<MusicWrite>(Channel.UNLIMITED)
        val held = mutableListOf<MusicWrite>()
        val persisted = mutableMapOf<String, Song>()
        val save = object : SaveAppData by DeferredMusicSave() {
            override suspend fun upsertSong(song: Song) {
                val request = MusicWrite(song.id, song)
                writes.send(request)
                request.release.await()
                persisted[song.id] = song
            }
            override suspend fun deleteSong(id: String) {
                val request = MusicWrite(id, null)
                writes.send(request)
                request.release.await()
                persisted.remove(id)
            }
        }
        val component = DefaultMusicComponent(DefaultComponentContext(lifecycle), saveAppData = save)
        try {
            component.onIntent(MusicIntent.AddPickedTrack("Measured", "/owned/measured.wav", 12_345L))
            val add = withTimeout(3000) { writes.receive() }.also(held::add)
            assertEquals(12.345, add.song?.duration)
            assertTrue(component.uiState.value.tracks.isEmpty())
            add.release.complete(Unit)
            val committed = withTimeout(3000) { component.uiState.first { !it.saving && it.tracks.isNotEmpty() } }
            val id = committed.tracks.single().id
            assertEquals(12_345L, committed.tracks.single().durationMs)

            component.onIntent(MusicIntent.SaveTrackEdit(id, "Renamed", "", emptyList(), 1.25))
            val edit = withTimeout(3000) { writes.receive() }.also(held::add)
            assertEquals("Renamed", edit.song?.title)
            assertEquals(12.345, edit.song?.duration)
            // A late player metadata callback cannot queue a stale upsert behind the edit.
            component.onIntent(MusicIntent.UpdateTrackDuration(id, 999_000L))
            assertEquals(12_345L, component.uiState.value.tracks.single().durationMs)
            edit.release.complete(Unit)
            withTimeout(3000) { component.uiState.first { !it.saving && it.tracks.single().title == "Renamed" } }

            component.onIntent(MusicIntent.SelectTrack(id))
            component.onIntent(MusicIntent.DeleteTrack(id))
            val delete = withTimeout(3000) { writes.receive() }.also(held::add)
            assertNull(delete.song)
            assertEquals("Renamed", component.uiState.value.tracks.single().title)
            assertFalse(component.uiState.value.tracks.single().isPlaying)
            assertTrue(component.uiState.value.saving)
            component.onIntent(MusicIntent.UpdateTrackDuration(id, 999_000L))
            component.onIntent(MusicIntent.SaveTrackEdit(id, "Resurrected", "", emptyList(), 1.0))
            assertTrue(writes.tryReceive().isFailure)
            delete.release.complete(Unit)
            withTimeout(3000) { component.uiState.first { !it.saving && it.tracks.isEmpty() } }
            assertTrue(persisted.isEmpty())
            assertTrue(writes.tryReceive().isFailure)
        } finally {
            // Release outstanding writes even when a regression assertion fails.
            held.forEach { it.release.complete(Unit) }
            while (true) { (writes.tryReceive().getOrNull() ?: break).release.complete(Unit) }
            lifecycle.destroy()
        }
        Unit
    }

    @Test fun failedDeleteKeepsCatalogAndEditorForRetry() = runBlocking {
        val lifecycle = LifecycleRegistry()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val song = Song("keep", "Keep me", "", "", "/owned/keep.wav", 12.345, 120)
        val load = object : LoadLibrary {
            override suspend fun load() = AppData(songs = listOf(song))
            override fun observe(): Flow<AppData> = flowOf(AppData(songs = listOf(song)))
        }
        val save = object : SaveAppData by DeferredMusicSave() {
            override suspend fun deleteSong(id: String) {
                started.complete(Unit)
                finish.await()
                error("disk failure")
            }
        }
        val component = DefaultMusicComponent(DefaultComponentContext(lifecycle), loadLibrary = load, saveAppData = save)
        try {
            withTimeout(3000) { component.uiState.first { it.tracks.isNotEmpty() } }
            component.onIntent(MusicIntent.EditTrack("keep"))
            component.onIntent(MusicIntent.DeleteTrack("keep"))
            withTimeout(3000) { started.await() }
            assertEquals("keep", component.uiState.value.tracks.single().id)
            assertTrue(component.uiState.value.saving)
            finish.complete(Unit)
            val failed = withTimeout(3000) { component.uiState.first { !it.saving } }
            assertEquals("keep", failed.tracks.single().id)
            assertEquals("keep", failed.editingTrackId)
            assertNotNull(failed.operationError)
        } finally { finish.complete(Unit); lifecycle.destroy() }
        Unit
    }

    private data class MusicWrite(val id: String, val song: Song?, val release: CompletableDeferred<Unit> = CompletableDeferred())

    private suspend fun checkUpload(fail: Boolean) {
        val lifecycle = LifecycleRegistry()
        val save = DeferredMusicSave()
        val component = DefaultMusicComponent(DefaultComponentContext(lifecycle), saveAppData = save)
        try {
            val before = component.uiState.value.tracks
            component.onIntent(MusicIntent.AddPickedTrack("QA owned music", "/owned/sha256-music.wav", durationMs = 12_345L))
            val song = withTimeout(3000) { save.entered.await() }
            assertTrue(component.uiState.value.saving)
            assertEquals(before, component.uiState.value.tracks)
            assertNull(component.uiState.value.editingTrackId)
            assertEquals("/owned/sha256-music.wav", song.fileName)
            assertEquals(12.345, song.duration)
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
                assertEquals(12_345L, track.durationMs)
                assertEquals("00:12", track.durationLabel)
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
