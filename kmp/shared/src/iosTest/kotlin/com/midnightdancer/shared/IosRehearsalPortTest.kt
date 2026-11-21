package com.midnightdancer.shared

import com.midnightdancer.feature.choreography.logic.*
import com.midnightdancer.feature.trainer.logic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class IosRehearsalPortTest {
    @Test fun preparationWaitsForDriverAndPropagatesActualDuration() = runTest {
        val driver = Driver()
        val port = IosRehearsalPlaybackPort(driver)
        val result = async { port.prepare(plan()) }
        runCurrent()
        assertEquals(1, driver.prepares)
        assertFalse(result.isCompleted)
        driver.prepared = true
        advanceTimeBy(10); runCurrent()
        assertEquals(5_000L, result.await())
    }

    @Test fun cancelDuringPrepareStopsDriverBeforeAnyPlayback() = runTest {
        val driver = Driver()
        val work = launch { IosRehearsalPlaybackPort(driver).prepare(plan()) }
        runCurrent()
        work.cancelAndJoin()
        assertEquals(1, driver.stops)
        assertEquals(0, driver.plays)
    }

    @Test fun playWaitsForActualStartAndCancellationPausesThenCanRetry() = runTest {
        val driver = Driver()
        val port = IosRehearsalPlaybackPort(driver)
        val first = launch { port.playAndAwaitStarted() }
        runCurrent()
        assertEquals(1, driver.plays)
        assertFalse(first.isCompleted)
        first.cancelAndJoin()
        assertEquals(1, driver.pauses)
        val resumed = launch { port.playAndAwaitStarted() }
        runCurrent()
        assertEquals(2, driver.plays)
        assertFalse(resumed.isCompleted)
        driver.playing = true
        advanceTimeBy(10); runCurrent()
        resumed.join()
    }

    @Test fun reportedDriverFailureDoesNotBecomeSuccessfulPreparation() = runTest {
        val driver = Driver().apply { failure = "Missing exact source" }
        assertFailsWith<IllegalStateException> { IosRehearsalPlaybackPort(driver).prepare(plan()) }
        assertEquals(0, driver.plays)
    }

    @Test fun exactIdSelectionKeepsDuplicateNamesDistinctAndNeverBorrowsSnapshot() {
        val before = ChoreographyVaultStore.all()
        try {
            val snapshot = ChoreographySnapshot("Duplicate", "style", musicClips = listOf(EditorClip("Exact", 5000, "owned-b", sourceStartMs = 300)))
            ChoreographyVaultStore.replaceAll(listOf(item("a", null), item("b", snapshot)))
            val choices = IosTrainerChoreographies.choices()
            assertNotEquals(choices[0].displayTitle, choices[1].displayTitle)
            assertNotNull(IosTrainerChoreographies.resolve("a").error)
            assertEquals(snapshot, IosTrainerChoreographies.resolve("b").snapshot)
            assertNull(IosTrainerChoreographies.resolve("missing").snapshot)
            val rehearsal = RehearsalSelectionResolver.resolve("b", ChoreographyVaultStore.all()).planResult.plan!!
            assertEquals("owned-b", rehearsal.musicClips.single().uri)
            assertEquals(300L, rehearsal.musicClips.single().sourceStartMs)
        } finally { ChoreographyVaultStore.replaceAll(before) }
    }

    @Test fun unconfiguredAccessIsUnavailableAndFreeOnlyLimitsNewCreation() = runTest {
        assertEquals(RehearsalAccess.Unavailable, IosUnavailableRehearsalAccess.access())
        val free = IosCreationPolicy(legacyProtected = false)
        val legacy = IosCreationPolicy(legacyProtected = true)
        assertTrue(free.canCreate(0))
        assertFalse(free.canCreate(1))
        assertFalse(free.canCreate(20))
        assertTrue(legacy.canCreate(20))
    }

    private fun plan() = RehearsalPlan("id", "revision", 5000,
        listOf(RehearsalMusicClip("exact", 0, 300, 5000)), emptyList())
    private fun item(id: String, snapshot: ChoreographySnapshot?) = ChoreographyVaultItem(id, "Duplicate", VaultStatus.Ready,
        "style", bpm = 120, durationLabel = "00:05", trackName = "", chrId = id, snapshot = snapshot)
    private class Driver : IosRehearsalAudioDriver {
        var prepared = false; var playing = false; var failure: String? = null
        var prepares = 0; var plays = 0; var pauses = 0; var stops = 0
        override fun beginPrepare(plan: RehearsalPlan) { prepares++ }
        override fun isPrepared() = prepared
        override fun preparedDurationMs() = 5000L
        override fun errorMessage() = failure
        override fun takeInterruption() = false
        override fun seek(positionMs: Long) = Unit
        override fun isReady() = prepared
        override fun play() { plays++ }
        override fun isPlaying() = playing
        override fun positionMs() = 0L
        override fun pause() { pauses++; playing = false }
        override fun stop() { stops++; playing = false }
    }
}
