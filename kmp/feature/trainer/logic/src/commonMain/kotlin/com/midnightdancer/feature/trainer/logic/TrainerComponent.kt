package com.midnightdancer.feature.trainer.logic

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.midnightdancer.core.tts.SpeakOutcome
import com.midnightdancer.core.tts.TrainerTtsController
import com.midnightdancer.core.tts.TrainerVoice
import com.midnightdancer.core.tts.TtsBackend
import com.midnightdancer.core.tts.TtsEngine
import com.midnightdancer.core.tts.createPlatformTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

interface TrainerSessionStubComponent {
    val uiState: StateFlow<TrainerSessionUiState>
    fun onIntent(intent: TrainerSessionIntent)
}

class DefaultTrainerSessionStubComponent(
    componentContext: ComponentContext,
) : TrainerSessionStubComponent, ComponentContext by componentContext {
    private val _uiState = MutableStateFlow(
        TrainerSessionUiState().let { base ->
            val cues = TrainerReady.seedCueSchedule(base.runway)
            base.copy(
                title = "Тренировка",
                moveSubtitle = "",
                playheadFraction = 0f,
                cueSchedule = cues,
                moveTitle = cues.firstOrNull()?.label ?: base.moveTitle,
            )
        },
    )
    override val uiState: StateFlow<TrainerSessionUiState> = _uiState.asStateFlow()

    override fun onIntent(intent: TrainerSessionIntent) {
        when (intent) {
            TrainerSessionIntent.TogglePause ->
                _uiState.update { it.copy(isPaused = !it.isPaused) }
            is TrainerSessionIntent.SetTrackVol ->
                _uiState.update { it.copy(trackVol = intent.value.coerceIn(0f, 1f)) }
            is TrainerSessionIntent.SetVoiceVol ->
                _uiState.update { it.copy(voiceVol = intent.value.coerceIn(0f, 1f)) }
            is TrainerSessionIntent.HoldFinishTick ->
                _uiState.update { it.copy(holdProgress = intent.progress.coerceIn(0f, 1f)) }
            TrainerSessionIntent.ResetHold ->
                _uiState.update { it.copy(holdProgress = 0f) }
            is TrainerSessionIntent.Seek ->
                _uiState.update { TrainerReady.applySeek(it, intent.fraction) }
        }
    }
}

interface TrainerHubComponent {
    val uiState: StateFlow<TrainerUiState>
    fun onIntent(intent: TrainerIntent)
}

class DefaultTrainerHubComponent(
    componentContext: ComponentContext,
    private val onOpenSession: () -> Unit,
    private val ttsController: TrainerTtsController,
    private val scope: CoroutineScope,
) : TrainerHubComponent, ComponentContext by componentContext {

    private val _uiState = MutableStateFlow(
        TrainerUiState().copy(readyPercent = TrainerReady.compute(TrainerUiState())),
    )
    override val uiState: StateFlow<TrainerUiState> = _uiState.asStateFlow()

    override fun onIntent(intent: TrainerIntent) {
        when (intent) {
            TrainerIntent.OpenSessionStub,
            TrainerIntent.StartDance,
            -> {
                if (_uiState.value.readyPercent >= 100) {
                    onOpenSession()
                }
            }
            is TrainerIntent.SetMode ->
                _uiState.update { state ->
                    val steps = TrainerReady.storylineFor(intent.mode)
                    val next = state.copy(
                        mode = intent.mode,
                        storylineSteps = steps,
                        expandedStep = state.expandedStep.coerceIn(1, steps.size),
                    )
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetExpandedStep ->
                _uiState.update {
                    val max = it.storylineSteps.size.coerceAtLeast(1)
                    it.copy(expandedStep = intent.step.coerceIn(1, max))
                }
            is TrainerIntent.SetStyle ->
                _uiState.update { state ->
                    val next = state.copy(styleName = intent.style)
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetTrack ->
                _uiState.update { state ->
                    val next = state.copy(trackTitle = intent.track)
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetElementTag ->
                _uiState.update { state ->
                    val next = state.copy(
                        elementTagLabel = intent.tag,
                        levelLabel = intent.tag,
                    )
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetInterval ->
                _uiState.update { state ->
                    val next = state.copy(intervalSec = intent.seconds.coerceAtLeast(0f))
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SelectVoice ->
                _uiState.update { state ->
                    val next = state.copy(
                        selectedVoice = intent.voice,
                        selectedVoiceIndex = intent.voice.ordinal,
                        voiceName = intent.voice.displayNameRu,
                        speakLabel = TrainerTtsLabels.idle(intent.voice, state.speakBackend),
                    )
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.RequestSpeak ->
                speak(intent.voice ?: _uiState.value.selectedVoice)
            TrainerIntent.RefreshPlaceholders ->
                _uiState.update { it.copy(placeholderItems = TrainerUiState().placeholderItems) }
        }
    }

    private fun speak(voice: TrainerVoice) {
        _uiState.update {
            it.copy(
                selectedVoice = voice,
                selectedVoiceIndex = voice.ordinal,
                voiceName = voice.displayNameRu,
                speakRequestedCount = it.speakRequestedCount + 1,
                isSpeaking = true,
                speakLabel = TrainerTtsLabels.speaking(voice, it.speakBackend),
            )
        }
        scope.launch {
            val outcome = ttsController.selectAndSpeak(voice)
            _uiState.update { state ->
                when (outcome) {
                    is SpeakOutcome.Spoken -> {
                        val next = state.copy(
                            isSpeaking = false,
                            speakBackend = outcome.backend,
                            selectedVoice = outcome.voice,
                            selectedVoiceIndex = outcome.voice.ordinal,
                            voiceName = outcome.voice.displayNameRu,
                            speakLabel = TrainerTtsLabels.idle(outcome.voice, outcome.backend),
                            lastSpeakNote = "ok · ${outcome.backend} · ${outcome.textPreview}",
                        )
                        next.copy(readyPercent = TrainerReady.compute(next))
                    }
                    is SpeakOutcome.Failed ->
                        state.copy(
                            isSpeaking = false,
                            speakBackend = TtsBackend.SystemFallback,
                            speakLabel = TrainerTtsLabels.idle(voice, TtsBackend.SystemFallback),
                            lastSpeakNote = "fail · ${outcome.reason}",
                        )
                }
            }
        }
    }
}

/** Nested stack: Hub → TrainerSession; speak owned by Voice TTS (C-33). */
interface TrainerComponent {
    val uiState: StateFlow<TrainerUiState>
    val stack: Value<ChildStack<*, Child>>
    fun onIntent(intent: TrainerIntent)
    fun openTrainerSession()
    fun onBack(): Boolean

    /** SKIE-friendly: 0=Ruslan, 1=Irina, 2=Kamila. */
    fun selectVoiceByIndex(index: Int) {
        TrainerVoice.fullSet.getOrNull(index)?.let { onIntent(TrainerIntent.SelectVoice(it)) }
    }

    /** SKIE-friendly speak trigger for SwiftUI. */
    fun requestSpeak() {
        onIntent(TrainerIntent.RequestSpeak())
    }

    /** SKIE-friendly Dance CTA (ignored unless readyPercent == 100). */
    fun startDance() {
        onIntent(TrainerIntent.StartDance)
    }

    fun setExpandedStep(step: Int) {
        onIntent(TrainerIntent.SetExpandedStep(step))
    }

    fun setStyle(style: String) {
        onIntent(TrainerIntent.SetStyle(style))
    }

    fun setTrack(track: String) {
        onIntent(TrainerIntent.SetTrack(track))
    }

    fun setElementTag(tag: String) {
        onIntent(TrainerIntent.SetElementTag(tag))
    }

    fun setInterval(seconds: Float) {
        onIntent(TrainerIntent.SetInterval(seconds))
    }

    sealed class Child {
        class Hub(val component: TrainerHubComponent) : Child()
        class Session(val component: TrainerSessionStubComponent) : Child()
    }
}

class DefaultTrainerComponent(
    componentContext: ComponentContext,
    ttsEngine: TtsEngine? = null,
) : TrainerComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine: TtsEngine = ttsEngine ?: runCatching { createPlatformTtsEngine() }.getOrElse {
        // Unit tests / hosts without bindTtsAndroidContext — recording engine.
        com.midnightdancer.core.tts.RecordingTtsEngine()
    }
    private val ttsController = TrainerTtsController(engine)

    private val navigation = StackNavigation<Config>()

    private val _uiState = MutableStateFlow(
        TrainerUiState().copy(readyPercent = TrainerReady.compute(TrainerUiState())),
    )
    override val uiState: StateFlow<TrainerUiState> = _uiState.asStateFlow()

    override val stack: Value<ChildStack<*, TrainerComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Hub,
        handleBackButton = true,
        childFactory = ::child,
    )

    init {
        lifecycle.doOnDestroy {
            scope.cancel()
            ttsController.dispose()
        }
    }

    override fun onIntent(intent: TrainerIntent) {
        when (intent) {
            TrainerIntent.OpenSessionStub,
            TrainerIntent.StartDance,
            -> {
                if (_uiState.value.readyPercent >= 100) {
                    openTrainerSession()
                }
            }
            is TrainerIntent.SetMode ->
                _uiState.update { state ->
                    val steps = TrainerReady.storylineFor(intent.mode)
                    val next = state.copy(
                        mode = intent.mode,
                        storylineSteps = steps,
                        expandedStep = state.expandedStep.coerceIn(1, steps.size),
                    )
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetExpandedStep ->
                _uiState.update {
                    val max = it.storylineSteps.size.coerceAtLeast(1)
                    it.copy(expandedStep = intent.step.coerceIn(1, max))
                }
            is TrainerIntent.SetStyle ->
                _uiState.update { state ->
                    val next = state.copy(styleName = intent.style)
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetTrack ->
                _uiState.update { state ->
                    val next = state.copy(trackTitle = intent.track)
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetElementTag ->
                _uiState.update { state ->
                    val next = state.copy(
                        elementTagLabel = intent.tag,
                        levelLabel = intent.tag,
                    )
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetInterval ->
                _uiState.update { state ->
                    val next = state.copy(intervalSec = intent.seconds.coerceAtLeast(0f))
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SelectVoice ->
                _uiState.update { state ->
                    val next = state.copy(
                        selectedVoice = intent.voice,
                        selectedVoiceIndex = intent.voice.ordinal,
                        voiceName = intent.voice.displayNameRu,
                        speakLabel = TrainerTtsLabels.idle(intent.voice, state.speakBackend),
                    )
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.RequestSpeak ->
                speak(intent.voice ?: _uiState.value.selectedVoice)
            TrainerIntent.RefreshPlaceholders ->
                _uiState.update { it.copy(placeholderItems = TrainerUiState().placeholderItems) }
        }
    }

    private fun speak(voice: TrainerVoice) {
        _uiState.update {
            it.copy(
                selectedVoice = voice,
                selectedVoiceIndex = voice.ordinal,
                voiceName = voice.displayNameRu,
                speakRequestedCount = it.speakRequestedCount + 1,
                isSpeaking = true,
                speakLabel = TrainerTtsLabels.speaking(voice, it.speakBackend),
            )
        }
        scope.launch {
            val outcome = ttsController.selectAndSpeak(voice)
            _uiState.update { state ->
                when (outcome) {
                    is SpeakOutcome.Spoken -> {
                        val next = state.copy(
                            isSpeaking = false,
                            speakBackend = outcome.backend,
                            selectedVoice = outcome.voice,
                            selectedVoiceIndex = outcome.voice.ordinal,
                            voiceName = outcome.voice.displayNameRu,
                            speakLabel = TrainerTtsLabels.idle(outcome.voice, outcome.backend),
                            lastSpeakNote = "ok · ${outcome.backend} · ${outcome.textPreview}",
                        )
                        next.copy(readyPercent = TrainerReady.compute(next))
                    }
                    is SpeakOutcome.Failed ->
                        state.copy(
                            isSpeaking = false,
                            speakBackend = TtsBackend.SystemFallback,
                            speakLabel = TrainerTtsLabels.idle(voice, TtsBackend.SystemFallback),
                            lastSpeakNote = "fail · ${outcome.reason}",
                        )
                }
            }
        }
    }

    override fun openTrainerSession() {
        navigation.pushNew(Config.Session)
    }

    override fun onBack(): Boolean {
        var handled = false
        navigation.pop { didPop -> handled = didPop }
        return handled
    }

    private fun child(
        config: Config,
        context: ComponentContext,
    ): TrainerComponent.Child =
        when (config) {
            Config.Hub ->
                TrainerComponent.Child.Hub(
                    DefaultTrainerHubComponent(
                        componentContext = context,
                        onOpenSession = ::openTrainerSession,
                        ttsController = ttsController,
                        scope = scope,
                    ),
                )
            Config.Session ->
                TrainerComponent.Child.Session(DefaultTrainerSessionStubComponent(context))
        }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Hub : Config

        @Serializable
        data object Session : Config
    }
}
