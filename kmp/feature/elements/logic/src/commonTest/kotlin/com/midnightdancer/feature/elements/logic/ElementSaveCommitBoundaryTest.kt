package com.midnightdancer.feature.elements.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.midnightdancer.core.domain.entity.*
import com.midnightdancer.core.domain.usecase.SaveAppData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*

/** A completed file copy is not a completed catalog save. */
class ElementSaveCommitBoundaryTest {
    @Test fun newOwnedElementIsPublishedOnlyAfterCatalogCommit() = runBlocking {
        checkSave(fail = false)
    }

    @Test fun failedCatalogCommitKeepsOwnedDraftAndPreviousCatalogForRetry() = runBlocking {
        checkSave(fail = true)
    }

    private suspend fun checkSave(fail: Boolean) {
        val lifecycle = LifecycleRegistry()
        val save = DeferredElementSave()
        val component = DefaultElementsComponent(DefaultComponentContext(lifecycle), saveAppData = save)
        try {
            val before = component.uiState.value.catalogCards
            component.openEditor(null)
            val draft = component.uiState.value.editor!!.copy(
                title = "QA owned clip", videoUri = "/owned/sha256-video.mp4", durationMs = 4150,
            )
            component.onIntent(ElementsIntent.UpdateEditorDraft(draft))
            component.saveElement()
            val persisted = withTimeout(3000) { save.entered.await() }
            val pending = component.uiState.value
            assertTrue(pending.saving)
            assertEquals(draft, pending.editor)
            assertEquals(before, pending.catalogCards)
            assertEquals(draft.videoUri, persisted.videoUri)
            component.saveElement() // Busy intent must not enqueue a second commit.
            assertEquals(1, save.calls)
            if (fail) save.release.completeExceptionally(IllegalStateException("disk write failed"))
            else save.release.complete(Unit)
            val completed = withTimeout(3000) { component.uiState.first { !it.saving } }
            if (fail) {
                assertNotNull(completed.saveError)
                assertEquals(draft, completed.editor)
                assertEquals(before, completed.catalogCards)
            } else {
                assertNull(completed.editor)
                assertNull(completed.saveError)
                val card = completed.catalogCards.single { it.title == draft.title }
                assertEquals(draft.videoUri, card.videoUri)
                assertEquals(persisted.id, card.id)
            }
        } finally {
            save.release.complete(Unit)
            lifecycle.destroy()
        }
    }

    private class DeferredElementSave : SaveAppData {
        val entered = CompletableDeferred<Move>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        override suspend fun upsertMove(styleId: String, move: Move) {
            calls++
            entered.complete(move)
            release.await()
        }
        override suspend fun ensureDanceStyle(id: String, name: String, currentMoveId: String?) = Unit
        override suspend fun save(data: AppData) = error("Unexpected full save")
        override suspend fun upsertSong(song: Song) = error("Unexpected song")
        override suspend fun upsertStyle(style: DanceStyle) = error("Unexpected style replacement")
        override suspend fun upsertChoreography(choreography: Choreography) = error("Unexpected choreography")
        override suspend fun deleteMove(id: String) = error("Unexpected delete")
        override suspend fun deleteSong(id: String) = error("Unexpected delete")
    }
}
