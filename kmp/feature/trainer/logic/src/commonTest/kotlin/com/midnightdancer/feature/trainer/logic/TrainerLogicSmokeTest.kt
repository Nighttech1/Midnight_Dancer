package com.midnightdancer.feature.trainer.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.midnightdancer.core.tts.RecordingTtsEngine
import com.midnightdancer.core.tts.TrainerVoice
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class TrainerLogicSmokeTest {

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
    fun intent_requestSpeak_incrementsAndUpdatesLabel() = runTest {
        val lifecycle = LifecycleRegistry()
        val engine = RecordingTtsEngine()
        val component =
            DefaultTrainerComponent(
                DefaultComponentContext(lifecycle = lifecycle),
                ttsEngine = engine,
            )
        lifecycle.resume()

        assertEquals("Тренировка", component.uiState.value.title)
        assertEquals(0, component.uiState.value.readyPercent)
        assertEquals(0, component.uiState.value.speakRequestedCount)
        assertEquals(TrainerVoice.Ruslan, component.uiState.value.selectedVoice)
        assertEquals(TrainerVoice.Ruslan.displayNameRu, component.uiState.value.voiceName)

        component.onIntent(TrainerIntent.SelectVoice(TrainerVoice.Irina))
        assertEquals(TrainerVoice.Irina, component.uiState.value.selectedVoice)
        assertEquals(1, component.uiState.value.selectedVoiceIndex)
        assertEquals("Ирина", component.uiState.value.voiceName)

        component.onIntent(TrainerIntent.RequestSpeak())
        assertEquals(1, component.uiState.value.speakRequestedCount)
        assertEquals(1, engine.speakCount)
        assertTrue(component.uiState.value.speakLabel.contains("Ирина"))
        assertTrue(!component.uiState.value.speakLabel.contains("stub", ignoreCase = true))
    }

    @Test
    fun intent_openSessionStub_pushesNestedChild_whenReady() {
        val lifecycle = LifecycleRegistry()
        val component =
            DefaultTrainerComponent(
                DefaultComponentContext(lifecycle = lifecycle),
                ttsEngine = RecordingTtsEngine(),
            )
        lifecycle.resume()

        // Not ready yet — ignore
        component.onIntent(TrainerIntent.OpenSessionStub)
        assertTrue(component.stack.value.active.instance is TrainerComponent.Child.Hub)

        fillFreestyleReady(component)
        component.onIntent(TrainerIntent.OpenSessionStub)
        assertTrue(component.stack.value.active.instance is TrainerComponent.Child.Session)
        val session =
            (component.stack.value.active.instance as TrainerComponent.Child.Session).component
        assertEquals("Тренировка", session.uiState.value.title)
        assertEquals("", session.uiState.value.moveSubtitle)
        assertTrue(session.uiState.value.cueSchedule.isNotEmpty())
        assertTrue(component.onBack())
        assertTrue(component.stack.value.active.instance is TrainerComponent.Child.Hub)
    }

    @Test
    fun intent_startDance_ignoredUntilReady_thenPushesSession() {
        val lifecycle = LifecycleRegistry()
        val component =
            DefaultTrainerComponent(
                DefaultComponentContext(lifecycle = lifecycle),
                ttsEngine = RecordingTtsEngine(),
            )
        lifecycle.resume()

        component.onIntent(TrainerIntent.StartDance)
        assertTrue(component.stack.value.active.instance is TrainerComponent.Child.Hub)

        fillFreestyleReady(component)
        assertEquals(100, component.uiState.value.readyPercent)
        component.onIntent(TrainerIntent.StartDance)
        assertTrue(component.stack.value.active.instance is TrainerComponent.Child.Session)
        assertTrue(component.onBack())
    }

    @Test
    fun intent_setModeAndExpandedStep_updatesHubState() {
        val lifecycle = LifecycleRegistry()
        val component =
            DefaultTrainerComponent(
                DefaultComponentContext(lifecycle = lifecycle),
                ttsEngine = RecordingTtsEngine(),
            )
        lifecycle.resume()

        assertEquals(4, component.uiState.value.storylineSteps.size)

        component.onIntent(TrainerIntent.SetMode(TrainerMode.Choreography))
        assertEquals(TrainerMode.Choreography, component.uiState.value.mode)
        assertEquals(2, component.uiState.value.storylineSteps.size)
        // Choreography: mode + default voice → 100
        assertEquals(100, component.uiState.value.readyPercent)

        component.onIntent(TrainerIntent.SetExpandedStep(2))
        assertEquals(2, component.uiState.value.expandedStep)

        component.onIntent(TrainerIntent.SetMode(TrainerMode.Freestyle))
        assertEquals(4, component.uiState.value.storylineSteps.size)
        assertEquals(0, component.uiState.value.readyPercent)
    }

    @Test
    fun session_intents_updateVolumesHoldAndSeek() {
        val lifecycle = LifecycleRegistry()
        val component =
            DefaultTrainerComponent(
                DefaultComponentContext(lifecycle = lifecycle),
                ttsEngine = RecordingTtsEngine(),
            )
        lifecycle.resume()
        fillFreestyleReady(component)
        component.onIntent(TrainerIntent.OpenSessionStub)
        val session =
            (component.stack.value.active.instance as TrainerComponent.Child.Session).component

        session.onIntent(TrainerSessionIntent.TogglePause)
        assertTrue(session.uiState.value.isPaused)
        session.onIntent(TrainerSessionIntent.SetTrackVol(0.4f))
        assertEquals(0.4f, session.uiState.value.trackVol)
        session.onIntent(TrainerSessionIntent.HoldFinishTick(0.5f))
        assertEquals(0.5f, session.uiState.value.holdProgress)
        session.onIntent(TrainerSessionIntent.ResetHold)
        assertEquals(0f, session.uiState.value.holdProgress)

        session.onIntent(TrainerSessionIntent.Seek(0.5f))
        assertEquals(0.5f, session.uiState.value.playheadFraction)
        assertTrue(session.uiState.value.moveTitle.isNotBlank())
    }

    private fun fillFreestyleReady(component: DefaultTrainerComponent) {
        component.onIntent(TrainerIntent.SetStyle("Бачата Sensual"))
        component.onIntent(TrainerIntent.SetTrack("Zacarias F. — Asesina"))
        component.onIntent(TrainerIntent.SetElementTag("Все уровни"))
        component.onIntent(TrainerIntent.SetInterval(5f))
        component.onIntent(TrainerIntent.SelectVoice(TrainerVoice.Ruslan))
    }
}
