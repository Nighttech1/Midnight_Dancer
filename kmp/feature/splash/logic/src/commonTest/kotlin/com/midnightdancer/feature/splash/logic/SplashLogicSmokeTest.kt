package com.midnightdancer.feature.splash.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.midnightdancer.core.tts.PrepareResult
import com.midnightdancer.core.tts.RecordingTtsEngine
import com.midnightdancer.core.tts.SpeakOutcome
import com.midnightdancer.core.tts.SpeakRequest
import com.midnightdancer.core.tts.TtsBackend
import com.midnightdancer.core.tts.TtsEngine
import com.midnightdancer.core.tts.TrainerVoice
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SplashLogicSmokeTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun intent_continue_hidesSplashAndFinishes() {
        var finished = false
        val lifecycle = LifecycleRegistry()
        val component = DefaultSplashComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            finishSplash = { finished = true },
            autoFinish = false,
            ttsEngine = RecordingTtsEngine(),
        )
        lifecycle.resume()

        assertTrue(component.uiState.value.show)
        assertTrue(component.uiState.value.bootReady)
        assertTrue(component.uiState.value.skipVisible)
        component.onIntent(SplashIntent.Continue)
        assertFalse(component.uiState.value.show)
        assertTrue(finished)
    }

    @Test
    fun intent_setLegalStub_updatesFlag() {
        val lifecycle = LifecycleRegistry()
        val component = DefaultSplashComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            finishSplash = {},
            autoFinish = false,
            ttsEngine = RecordingTtsEngine(),
        )
        lifecycle.resume()

        assertFalse(component.uiState.value.legalAcceptedStub)
        component.onIntent(SplashIntent.SetLegalStub(accepted = true))
        assertTrue(component.uiState.value.legalAcceptedStub)
    }

    @Test
    fun continue_beforePrepare_doesNotFinish() {
        var finished = false
        val latch = LatchTtsEngine()
        val lifecycle = LifecycleRegistry()
        val component = DefaultSplashComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            finishSplash = { finished = true },
            autoFinish = false,
            ttsEngine = latch,
        )
        lifecycle.resume()

        assertFalse(component.uiState.value.bootReady)
        assertFalse(component.uiState.value.skipVisible)
        component.onIntent(SplashIntent.Continue)
        assertTrue(component.uiState.value.show)
        assertFalse(finished)

        latch.release()
        assertTrue(component.uiState.value.bootReady)
        component.onIntent(SplashIntent.Continue)
        assertFalse(component.uiState.value.show)
        assertTrue(finished)
    }

    @Test
    fun prepareTimeout_stillBootReady() {
        var finished = false
        val latch = LatchTtsEngine()
        val lifecycle = LifecycleRegistry()
        val component = DefaultSplashComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            finishSplash = { finished = true },
            autoFinish = false,
            ttsEngine = latch,
        )
        lifecycle.resume()

        assertFalse(component.uiState.value.bootReady)
        dispatcher.scheduler.advanceTimeBy(DefaultSplashComponent.PREPARE_TIMEOUT_MS)
        dispatcher.scheduler.runCurrent()
        assertTrue(component.uiState.value.bootReady)
        assertTrue(component.uiState.value.prepareFailed)
        component.onIntent(SplashIntent.Continue)
        assertTrue(finished)
    }

    @Test
    fun prepareFailed_stillBootReady() {
        var finished = false
        val lifecycle = LifecycleRegistry()
        val component = DefaultSplashComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            finishSplash = { finished = true },
            autoFinish = false,
            ttsEngine = FailingTtsEngine(),
        )
        lifecycle.resume()

        assertTrue(component.uiState.value.bootReady)
        assertTrue(component.uiState.value.prepareFailed)
        component.onIntent(SplashIntent.Continue)
        assertTrue(finished)
    }
}

private class LatchTtsEngine : TtsEngine {
    private val gate = CompletableDeferred<Unit>()
    override var currentVoice: TrainerVoice? = null
        private set
    override var currentBackend: TtsBackend? = null
        private set

    fun release() {
        gate.complete(Unit)
    }

    override suspend fun prepare(voice: TrainerVoice): PrepareResult {
        gate.await()
        currentVoice = voice
        currentBackend = TtsBackend.SystemFallback
        return PrepareResult.Ready(
            voice = voice,
            backend = TtsBackend.SystemFallback,
            note = "latch",
        )
    }

    override suspend fun speak(request: SpeakRequest): SpeakOutcome =
        SpeakOutcome.Failed("unused")

    override suspend fun stop() = Unit

    override fun dispose() = Unit
}

private class FailingTtsEngine : TtsEngine {
    override val currentVoice: TrainerVoice? = null
    override val currentBackend: TtsBackend? = null

    override suspend fun prepare(voice: TrainerVoice): PrepareResult =
        PrepareResult.Failed(voice = voice, reason = "test-fail")

    override suspend fun speak(request: SpeakRequest): SpeakOutcome =
        SpeakOutcome.Failed("unused")

    override suspend fun stop() = Unit

    override fun dispose() = Unit
}
