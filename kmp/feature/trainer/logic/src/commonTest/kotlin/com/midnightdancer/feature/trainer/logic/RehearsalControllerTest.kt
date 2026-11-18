package com.midnightdancer.feature.trainer.logic

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RehearsalControllerTest {
    private val plan = RehearsalPlan(
        "c1", "r1", 90_000L,
        listOf(RehearsalMusicClip("file://music", 0L, 0L, 90_000L)),
        listOf(
            RehearsalElement(0L, 40_000L, "X", 0),
            RehearsalElement(40_000L, 55_000L, "Y", 1),
            RehearsalElement(55_000L, 90_000L, "Z", 2),
        ),
    )
    private val example = RehearsalSettings("c1", "r1", 35_000L, 55_000L, 5, 8_000L, 3_000L)

    @Test
    fun fivePassExample_completesAfter135SecondsWithFourRestsAndNoCueAtB() = runTest {
        val f = fixture()
        f.controller.start(example, plan)
        runCurrent()
        assertEquals(RehearsalPhase.Preparing, f.controller.state.value.phase)
        assertEquals(0, f.controller.state.value.pass)
        advanceTimeBy(134_999L)
        runCurrent()
        assertEquals(RehearsalPhase.Playing, f.controller.state.value.phase)
        advanceTimeBy(21L)
        runCurrent()
        assertEquals(RehearsalPhase.Completed, f.controller.state.value.phase)
        assertEquals(5, f.controller.state.value.pass)
        assertEquals(5, f.player.starts)
        assertEquals(listOf("X", "Y", "X", "Y", "X", "Y", "X", "Y", "X", "Y"), f.voice.labels)
        assertFalse(f.player.running)
        assertEquals(1, f.repository.writes)
        assertEquals(1, f.accessChecks()) // Expiry is checked next session, never mid-session.
        f.controller.stop()
    }

    @Test
    fun singlePass_hasNoRestAndZeroPreparationHasNoCountdown() = runTest {
        val f = fixture()
        f.controller.start(example.copy(totalPasses = 1, preparationMs = 0L), plan)
        runCurrent()
        assertEquals(RehearsalPhase.Playing, f.controller.state.value.phase)
        advanceTimeBy(20_020L)
        runCurrent()
        assertEquals(RehearsalPhase.Completed, f.controller.state.value.phase)
        assertEquals(1, f.player.starts)
        assertTrue(f.voice.beeps.isEmpty())
        f.controller.stop()
    }

    @Test
    fun zeroRest_hasExactlyNStartsWithoutExtraPassAfterCompletion() = runTest {
        val f = fixture()
        val s = example.copy(startMs = 0L, endMs = 1_000L, totalPasses = 5, restMs = 0L, preparationMs = 0L)
        f.controller.start(s, plan)
        advanceTimeBy(5_020L)
        runCurrent()
        assertEquals(RehearsalPhase.Completed, f.controller.state.value.phase)
        assertEquals(5, f.player.starts)
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(5, f.player.starts)
        f.controller.stop()
    }

    @Test
    fun freeAndUnavailable_neverPrepareOrSpeakAndRemainDistinct() = runTest {
        for (access in listOf(RehearsalAccess.Free, RehearsalAccess.Unavailable)) {
            val f = fixture(access)
            f.controller.start(example, plan)
            runCurrent()
            assertEquals(access, f.controller.state.value.access)
            assertEquals(RehearsalPhase.Ready, f.controller.state.value.phase)
            assertEquals(0, f.player.prepares)
            assertTrue(f.voice.labels.isEmpty())
            assertTrue(f.voice.beeps.isEmpty())
            f.controller.stop()
        }
    }

    @Test
    fun invalidOrStaleParameters_areRejectedBeforeRightsOrPlayer() = runTest {
        val f = fixture()
        f.controller.start(example.copy(choreographyRevision = "old"), plan)
        runCurrent()
        assertEquals(RehearsalPhase.Error, f.controller.state.value.phase)
        assertEquals(0, f.player.prepares)
        assertEquals(0, f.accessChecks())
        f.controller.stop()
    }

    @Test
    fun unknownConfirmedDuration_isErrorAndDoesNotStartOrCount() = runTest {
        val f = fixture()
        f.player.confirmedDuration = null
        f.controller.start(example, plan)
        runCurrent()
        assertEquals(RehearsalPhase.Error, f.controller.state.value.phase)
        assertEquals(0, f.player.starts)
        assertEquals(0, f.controller.state.value.pass)
        assertTrue(f.voice.labels.isEmpty())
        f.controller.stop()
    }

    @Test
    fun pausePreparation_freezesRemainingAndDoesNotRestartPreparation() = runTest {
        val f = fixture()
        f.controller.start(example, plan)
        advanceTimeBy(1_000L)
        runCurrent()
        f.controller.pause()
        assertEquals(2_000L, f.controller.state.value.remainingMs)
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(RehearsalPhase.Paused, f.controller.state.value.phase)
        assertEquals(0, f.player.starts)
        f.controller.resume()
        advanceTimeBy(2_020L)
        runCurrent()
        assertEquals(RehearsalPhase.Playing, f.controller.state.value.phase)
        assertEquals(1, f.controller.state.value.pass)
        f.controller.stop()
    }

    @Test
    fun pausePlaying_preservesPositionPassAndDoesNotRepeatAlreadySpokenCue() = runTest {
        val f = fixture()
        f.controller.start(example.copy(preparationMs = 0L), plan)
        advanceTimeBy(1_000L)
        runCurrent()
        f.controller.pause()
        val pausedPosition = f.player.positionMs()
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(pausedPosition, f.player.positionMs())
        f.controller.resume()
        advanceTimeBy(100L)
        runCurrent()
        assertEquals(1, f.controller.state.value.pass)
        assertEquals(listOf("X"), f.voice.labels)
        assertTrue(f.player.running)
        f.controller.stop()
    }

    @Test
    fun pauseAndImmediateResumeBetweenTicks_stillRestartsAudioWithoutNewPass() = runTest {
        val f = fixture()
        f.controller.start(example.copy(preparationMs = 0L), plan)
        runCurrent()
        f.controller.pause()
        f.controller.resume()
        advanceTimeBy(40L)
        runCurrent()
        assertTrue(f.player.running)
        assertEquals(1, f.controller.state.value.pass)
        f.controller.stop()
    }

    @Test
    fun restPause_preservesRemainingAndNoMovementVoiceDuringRest() = runTest {
        val f = fixture()
        f.controller.start(example.copy(startMs = 0L, endMs = 1_000L, totalPasses = 2, preparationMs = 0L), plan)
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(RehearsalPhase.Resting, f.controller.state.value.phase)
        f.controller.pause()
        val remaining = f.controller.state.value.remainingMs
        val labels = f.voice.labels.toList()
        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(remaining, f.controller.state.value.remainingMs)
        assertEquals(labels, f.voice.labels)
        f.controller.resume()
        advanceTimeBy(remaining + 40L)
        runCurrent()
        assertEquals(RehearsalPhase.Playing, f.controller.state.value.phase)
        assertEquals(2, f.controller.state.value.pass)
        f.controller.stop()
    }

    @Test
    fun awaitingActualPlaybackStart_doesNotCountBeforeAdapterConfirmation() = runTest {
        val f = fixture()
        f.player.startDelayMs = 2_000L
        f.controller.start(example.copy(preparationMs = 0L), plan)
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(0, f.controller.state.value.pass)
        assertTrue(f.controller.state.value.isWaitingForPlayer)
        advanceTimeBy(1_020L)
        runCurrent()
        assertEquals(1, f.controller.state.value.pass)
        f.controller.stop()
    }

    @Test
    fun backgroundPauseWhilePrepareSuspends_cannotAutoplayOrStartCountdownUntilResume() = runTest {
        val f = fixture()
        f.player.prepareDelayMs = 5_000L
        f.controller.start(example, plan)
        runCurrent()
        assertEquals(RehearsalPhase.Preparing, f.controller.state.value.phase)
        assertTrue(f.controller.state.value.isWaitingForPlayer)
        f.controller.pause() // Activity ON_STOP before media preparation completes.
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(RehearsalPhase.Paused, f.controller.state.value.phase)
        assertEquals(0, f.player.starts)
        assertTrue(f.voice.labels.isEmpty())
        assertTrue(f.voice.beeps.isEmpty())
        f.controller.resume()
        advanceTimeBy(3_040L)
        runCurrent()
        assertEquals(RehearsalPhase.Playing, f.controller.state.value.phase)
        assertEquals(1, f.controller.state.value.pass)
        assertEquals(listOf(3, 2, 1), f.voice.beeps)
        f.controller.stop()
    }

    @Test
    fun pauseWhileVoiceInitializes_doesNotAutoplayOrCountAndResumesOnlyAfterReady() = runTest {
        val f = fixture()
        f.voice.prepareDelayMs = 5_000L
        f.controller.start(example, plan)
        runCurrent()
        assertEquals(1, f.voice.prepares)
        assertEquals(0, f.player.starts)
        f.controller.pause()
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(RehearsalPhase.Paused, f.controller.state.value.phase)
        assertEquals(0, f.controller.state.value.pass)
        assertEquals(0, f.player.starts)
        assertTrue(f.voice.labels.isEmpty())
        assertTrue(f.voice.beeps.isEmpty())
        f.controller.resume()
        advanceTimeBy(3_040L)
        runCurrent()
        assertEquals(RehearsalPhase.Playing, f.controller.state.value.phase)
        assertEquals(1, f.controller.state.value.pass)
        assertEquals(1, f.voice.prepares)
        assertEquals(listOf("X"), f.voice.labels)
        f.controller.stop()
    }

    @Test
    fun pauseImmediatelyAfterStartBeforeAccessCheck_isRememberedBeforeFirstSuspend() = runTest {
        val f = fixture()
        f.controller.start(example, plan)
        f.controller.pause() // Before even the launched access check has executed.
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(RehearsalPhase.Paused, f.controller.state.value.phase)
        assertEquals(0, f.player.prepares)
        assertEquals(0, f.player.starts)
        assertTrue(f.voice.beeps.isEmpty())
        f.controller.resume()
        advanceTimeBy(3_040L)
        runCurrent()
        assertEquals(1, f.controller.state.value.pass)
        f.controller.stop()
    }

    @Test
    fun pauseDuringActualStartHandshake_cancelsWaitAndResumeRequestsNewStartWithoutTimeout() = runTest {
        val f = fixture()
        f.player.waitForPlaybackSignal = true
        f.controller.start(example.copy(preparationMs = 0L), plan)
        runCurrent()
        assertEquals(1, f.player.startRequests)
        assertEquals(0, f.controller.state.value.pass)
        f.controller.pause() // player.play requested, but actual isPlaying has not arrived.
        runCurrent()
        advanceTimeBy(40_000L) // Longer than the start operation timeout.
        runCurrent()
        assertEquals(RehearsalPhase.Paused, f.controller.state.value.phase)
        assertEquals(0, f.player.starts)
        f.player.allowPlaybackSignal = true
        f.controller.resume()
        advanceTimeBy(60L)
        runCurrent()
        assertEquals(RehearsalPhase.Playing, f.controller.state.value.phase)
        assertEquals(1, f.controller.state.value.pass)
        assertEquals(2, f.player.startRequests)
        assertEquals(1, f.player.starts)
        assertTrue(f.voice.beeps.isEmpty())
        f.controller.stop()
    }

    @Test
    fun pauseAgainDuringResumeHandshake_keepsSamePassAndDoesNotRepeatCue() = runTest {
        val f = fixture()
        f.controller.start(example.copy(preparationMs = 0L), plan)
        runCurrent()
        assertEquals(listOf("X"), f.voice.labels)
        f.controller.pause()
        f.player.waitForPlaybackSignal = true
        f.controller.resume()
        advanceTimeBy(40L)
        runCurrent()
        assertTrue(f.controller.state.value.isWaitingForPlayer)
        f.controller.pause()
        runCurrent()
        advanceTimeBy(40_000L)
        runCurrent()
        assertEquals(RehearsalPhase.Paused, f.controller.state.value.phase)
        f.player.allowPlaybackSignal = true
        f.controller.resume()
        advanceTimeBy(60L)
        runCurrent()
        assertEquals(1, f.controller.state.value.pass)
        assertEquals(listOf("X"), f.voice.labels)
        assertTrue(f.player.running)
        f.controller.stop()
    }

    @Test
    fun stopDuringSeek_cancelsPendingStartAndRepeatedStartUsesNewGeneration() = runTest {
        val f = fixture()
        f.player.seekDelayMs = 1_000L
        f.controller.start(example.copy(preparationMs = 0L), plan)
        runCurrent()
        f.controller.stop()
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(0, f.player.starts)
        assertEquals(RehearsalPhase.Ready, f.controller.state.value.phase)
        f.controller.start(example.copy(preparationMs = 0L), plan)
        runCurrent()
        f.player.seekDelayMs = 0L
        f.controller.start(example.copy(startMs = 40_000L, preparationMs = 0L), plan)
        runCurrent()
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(1, f.player.starts)
        assertEquals(listOf("Y"), f.voice.labels)
        f.controller.stop()
    }

    @Test
    fun disabledVoice_keepsVisualMovementAndPlayback() = runTest {
        val f = fixture()
        f.controller.start(example.copy(preparationMs = 0L, voiceEnabled = false), plan)
        runCurrent()
        assertEquals("X", f.controller.state.value.currentLabel)
        assertTrue(f.voice.labels.isEmpty())
        assertTrue(f.player.running)
        assertEquals(0, f.voice.prepares)
        f.controller.stop()
    }

    @Test
    fun stoppedMusic_neverAdvancesTimeArtificiallyAndReportsStall() = runTest {
        val f = fixture()
        f.player.frozen = true
        f.controller.start(example.copy(preparationMs = 0L), plan)
        advanceTimeBy(15_020L)
        runCurrent()
        assertEquals(RehearsalPhase.Error, f.controller.state.value.phase)
        assertEquals(1, f.controller.state.value.pass)
        assertEquals(35_000L, f.controller.state.value.positionMs)
        f.controller.stop()
    }

    private fun TestScope.fixture(access: RehearsalAccess = RehearsalAccess.Granted): Fixture {
        val player = FakePlayer(testScheduler)
        val voice = FakeVoice()
        val repository = FakeRepository()
        var checks = 0
        val controller = RehearsalController(
            this, player, voice, RehearsalEntitlementPort { checks++; access }, repository,
            RehearsalClock { testScheduler.currentTime },
        )
        return Fixture(controller, player, voice, repository) { checks }
    }

    private data class Fixture(
        val controller: RehearsalController,
        val player: FakePlayer,
        val voice: FakeVoice,
        val repository: FakeRepository,
        val accessChecks: () -> Int,
    )

    private class FakePlayer(private val scheduler: TestCoroutineScheduler) : RehearsalPlaybackPort {
        var running = false
        var starts = 0
        var prepares = 0
        var confirmedDuration: Long? = 90_000L
        var startDelayMs = 0L
        var seekDelayMs = 0L
        var prepareDelayMs = 0L
        var startRequests = 0
        var waitForPlaybackSignal = false
        var allowPlaybackSignal = false
        private var playRequested = false
        var frozen = false
        private var position = 0L
        private var startedAt = 0L

        override suspend fun prepare(plan: RehearsalPlan): Long? {
            prepares++
            delay(prepareDelayMs)
            return confirmedDuration
        }
        override suspend fun seekTo(positionMs: Long) { delay(seekDelayMs); position = positionMs; startedAt = scheduler.currentTime }
        override suspend fun awaitReady() = Unit
        override suspend fun playAndAwaitStarted() {
            startRequests++
            playRequested = true
            if (waitForPlaybackSignal) {
                while (!playRequested || !allowPlaybackSignal) delay(10L)
            }
            delay(startDelayMs)
            startedAt = scheduler.currentTime
            running = true
            starts++
        }
        override fun positionMs(): Long = position + if (running && !frozen) scheduler.currentTime - startedAt else 0L
        override fun pause() { position = positionMs(); running = false; playRequested = false }
        override fun stop() { pause(); position = 0L }
    }

    private class FakeVoice : RehearsalVoicePort {
        val labels = mutableListOf<String>()
        val beeps = mutableListOf<Int>()
        var prepares = 0
        var prepareDelayMs = 0L
        override suspend fun prepare() { prepares++; delay(prepareDelayMs) }
        override fun speak(label: String) { labels += label }
        override fun stop() = Unit
        override fun countdown(seconds: Int) { beeps += seconds }
    }

    private class FakeRepository : RehearsalSettingsRepository {
        var writes = 0
        private var saved: RehearsalSettings? = null
        override suspend fun read(choreographyId: String) = saved?.takeIf { it.choreographyId == choreographyId }
        override suspend fun write(settings: RehearsalSettings) { writes++; saved = settings }
        override suspend fun delete(choreographyId: String) { saved = null }
    }
}
