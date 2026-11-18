package com.midnightdancer.feature.trainer.logic

import com.midnightdancer.core.domain.styles.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.settings.ElementCategoryConfig
import com.midnightdancer.core.domain.usecase.LoadLibrary
import com.midnightdancer.core.domain.usecase.ReadWriteUserPrefs
import com.midnightdancer.core.tts.SpeakOutcome
import com.midnightdancer.core.tts.TrainerTtsController
import com.midnightdancer.core.tts.TrainerVoice
import com.midnightdancer.core.tts.TtsBackend
import com.midnightdancer.core.tts.TtsEngine
import com.midnightdancer.core.tts.createPlatformTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    initial: TrainerSessionUiState,
    private val ttsController: TrainerTtsController,
    private val scope: CoroutineScope,
    private val isRestored: Boolean = false,
) : TrainerSessionStubComponent, ComponentContext by componentContext {
    private val _uiState = MutableStateFlow(initial)
    override val uiState: StateFlow<TrainerSessionUiState> = _uiState.asStateFlow()
    private var lastSpokenLabel: String? = if (isRestored) initial.moveTitle else null
    /** C-243: cancel+reschedule so long scrub does not speak every crossed cue. */
    private var pendingSpeakJob: Job? = null

    init {
        TrainerLiveSessionStore.begin(initial)
        // Announce first cue only on fresh open — restored sessions keep lastSpoken (C-268).
        if (!isRestored) {
            val first = initial.moveTitle
            if (first.isNotBlank()) {
                speakCue(first, initial.selectedVoice)
            }
        }
    }

    override fun onIntent(intent: TrainerSessionIntent) {
        when (intent) {
            TrainerSessionIntent.TogglePause ->
                _uiState.update {
                    it.copy(isPaused = !it.isPaused).also(TrainerLiveSessionStore::update)
                }
            is TrainerSessionIntent.SetTrackVol ->
                _uiState.update {
                    it.copy(trackVol = intent.value.coerceIn(0f, 1f))
                        .also(TrainerLiveSessionStore::update)
                }
            is TrainerSessionIntent.SetVoiceVol ->
                _uiState.update {
                    it.copy(voiceVol = intent.value.coerceIn(0f, 1f))
                        .also(TrainerLiveSessionStore::update)
                }
            is TrainerSessionIntent.HoldFinishTick ->
                _uiState.update {
                    it.copy(holdProgress = intent.progress.coerceIn(0f, 1f))
                        .also(TrainerLiveSessionStore::update)
                }
            TrainerSessionIntent.ResetHold ->
                _uiState.update {
                    it.copy(holdProgress = 0f).also(TrainerLiveSessionStore::update)
                }
            is TrainerSessionIntent.Seek ->
                _uiState.update { prev ->
                    val next = TrainerReady.applySeek(prev, intent.fraction)
                    if (intent.fromUserScrub) {
                        // C-280: scrub → only upcoming cue (sec > t); never in-progress moveTitle.
                        val upcoming = TrainerReady.nextCueAfter(next)
                        if (upcoming != null &&
                            upcoming.label.isNotBlank() &&
                            !next.isPaused
                        ) {
                            scheduleSettledSpeak(upcoming.label, next.selectedVoice)
                        } else {
                            pendingSpeakJob?.cancel()
                        }
                    } else if (next.moveTitle.isNotBlank() &&
                        next.moveTitle != prev.moveTitle &&
                        !next.isPaused
                    ) {
                        // C-255/C-267: auto playhead — speak when cue starts (highlight first).
                        pendingSpeakJob?.cancel()
                        if (next.moveTitle != lastSpokenLabel) {
                            speakCue(next.moveTitle, next.selectedVoice)
                        }
                    }
                    next.also(TrainerLiveSessionStore::update)
                }
        }
    }

    private fun scheduleSettledSpeak(label: String, voice: TrainerVoice) {
        pendingSpeakJob?.cancel()
        pendingSpeakJob = scope.launch {
            delay(SCRUB_SPEAK_SETTLE_MS)
            val cur = _uiState.value
            if (cur.isPaused) return@launch
            if (cur.moveTitle != label) return@launch
            if (label == lastSpokenLabel) return@launch
            speakCue(label, voice)
        }
    }

    private fun speakCue(label: String, voice: TrainerVoice) {
        lastSpokenLabel = label
        scope.launch {
            // C-267: yield so Compose paints cue highlight before TTS audio starts.
            kotlinx.coroutines.yield()
            ttsController.selectAndSpeak(voice, text = label)
        }
    }

    companion object {
        /** After scrub/playhead stops changing cue for this long → speak current only (C-243). */
        const val SCRUB_SPEAK_SETTLE_MS = 1_000L
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
    private val sharedState: MutableStateFlow<TrainerUiState>,
    private val onStyleSelected: ((String) -> Unit)? = null,
) : TrainerHubComponent, ComponentContext by componentContext {

    override val uiState: StateFlow<TrainerUiState> = sharedState.asStateFlow()

    override fun onIntent(intent: TrainerIntent) {
        when (intent) {
            TrainerIntent.OpenSessionStub,
            TrainerIntent.StartDance,
            -> {
                if (sharedState.value.readyPercent >= 100) {
                    onOpenSession()
                }
            }
            is TrainerIntent.SetMode ->
                sharedState.update { state ->
                    val steps = TrainerReady.storylineFor(intent.mode)
                    val next = TrainerSessionFactory.withChoreographyOptions(
                        state.copy(
                            mode = intent.mode,
                            storylineSteps = steps,
                            expandedStep = state.expandedStep.coerceIn(0, steps.size),
                        ),
                    )
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetExpandedStep ->
                sharedState.update {
                    val max = it.storylineSteps.size.coerceAtLeast(1)
                    val target = if (it.expandedStep == intent.step) 0 else intent.step
                    it.copy(expandedStep = target.coerceIn(0, max))
                }
            is TrainerIntent.SetStyle -> {
                val select = onStyleSelected
                if (select != null) select(intent.style)
                else sharedState.update { state ->
                    val next = state.copy(styleName = intent.style, selectedStyleId = "")
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            }
            is TrainerIntent.SetTrack ->
                sharedState.update { state ->
                    val next = state.copy(trackTitle = intent.track)
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetElementTag ->
                sharedState.update { state ->
                    val next = state.copy(
                        elementTagLabel = intent.tag,
                        levelLabel = intent.tag,
                    )
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetInterval ->
                sharedState.update { state ->
                    val next = state.copy(intervalSec = intent.seconds.coerceAtLeast(0f))
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetUseElementTiming ->
                sharedState.update { state ->
                    val interval =
                        if (intent.enabled && state.intervalSec <= 0f) 8f else state.intervalSec
                    val next = state.copy(
                        useElementTiming = intent.enabled,
                        intervalSec = interval,
                    )
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SelectVoice ->
                sharedState.update { state ->
                    val next = state.copy(
                        selectedVoice = intent.voice,
                        selectedVoiceIndex = intent.voice.ordinal,
                        voiceName = intent.voice.displayNameRu,
                        speakLabel = TrainerTtsLabels.idle(intent.voice, state.speakBackend),
                    )
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetChoreographyTag ->
                sharedState.update { state ->
                    val next = TrainerSessionFactory.withChoreographyOptions(
                        state.copy(choreographyTag = intent.tag),
                    )
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetSelectedChoreography ->
                sharedState.update { state ->
                    val id = state.choreographyIdsByTitle[intent.title].orEmpty()
                    val next = state.copy(
                        selectedChoreographyTitle = intent.title,
                        selectedChoreographyId = id,
                    )
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.RequestSpeak ->
                speak(intent.voice ?: sharedState.value.selectedVoice)
            TrainerIntent.RefreshPlaceholders ->
                sharedState.update { it.copy(placeholderItems = TrainerUiState().placeholderItems) }
        }
    }

    private fun speak(voice: TrainerVoice) {
        sharedState.update {
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
            sharedState.update { state ->
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

/** Nested stack: Hub → TrainerSession; speak owned by Voice TTS (C-33 / C-192). */
interface TrainerComponent {
    val uiState: StateFlow<TrainerUiState>
    val stack: Value<ChildStack<*, Child>>
    fun onIntent(intent: TrainerIntent)
    fun openTrainerSession()
    fun onBack(): Boolean

    fun selectVoiceByIndex(index: Int) {
        TrainerVoice.fullSet.getOrNull(index)?.let { onIntent(TrainerIntent.SelectVoice(it)) }
    }

    fun requestSpeak() {
        onIntent(TrainerIntent.RequestSpeak())
    }

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

    fun setUseElementTiming(enabled: Boolean) {
        onIntent(TrainerIntent.SetUseElementTiming(enabled))
    }

    sealed class Child {
        class Hub(val component: TrainerHubComponent) : Child()
        class Session(val component: TrainerSessionStubComponent) : Child()
    }
}

class DefaultTrainerComponent(
    componentContext: ComponentContext,
    ttsEngine: TtsEngine? = null,
    private val loadLibrary: LoadLibrary? = null,
    private val userPrefs: ReadWriteUserPrefs? = null,
    private val danceStyles: ManageDanceStyles? = null,
) : TrainerComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine: TtsEngine = ttsEngine ?: runCatching { createPlatformTtsEngine() }.getOrElse {
        com.midnightdancer.core.tts.RecordingTtsEngine()
    }
    private val ttsController = TrainerTtsController(engine)

    private val navigation = StackNavigation<Config>()

    private val _uiState = MutableStateFlow(
        TrainerUiState().copy(readyPercent = TrainerReady.compute(TrainerUiState())),
    )
    override val uiState: StateFlow<TrainerUiState> = _uiState.asStateFlow()

    @Volatile
    private var cachedAppData: AppData? = null

    override val stack: Value<ChildStack<*, TrainerComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        // C-268: if Live was active when tab remounted, reopen Session from process store.
        initialConfiguration = if (TrainerLiveSessionStore.active) Config.Session else Config.Hub,
        handleBackButton = true,
        childFactory = ::child,
    )

    init {
        hydrateCatalogs()
        lifecycle.doOnDestroy {
            // Do NOT end Live store here — tab DESTROY must not finish the session (C-268).
            scope.cancel()
            ttsController.dispose()
        }
    }

    private fun hydrateCatalogs() {
        val load = loadLibrary ?: return
        scope.launch {
            val catalogFlow = danceStyles?.let { manager -> combine(load.observe(), manager.state.filterNotNull()) { data, _ -> data } } ?: load.observe()
            catalogFlow.collect { data ->
            cachedAppData = data
            val categories = userPrefs?.let {
                runCatching { it.readElementCategories().names }.getOrNull()
            }.orEmpty().ifEmpty { ElementCategoryConfig.DEFAULT_NAMES }
            _uiState.update { state ->
                val hydrated = TrainerSessionFactory.hydrateHubCatalogs(data, categories)(state)
                val catalog = danceStyles?.state?.value
                if (catalog == null) hydrated else {
                    val id = if (state.selectedStyleId.isNotBlank()) catalog.canonicalId(state.selectedStyleId)
                        else state.styleName.takeIf { it.isNotBlank() }?.let(catalog::canonicalId).orEmpty()
                    hydrated.copy(styleOptions = catalog.entries.map { it.name }, selectedStyleId = id,
                        styleName = if (id.isBlank()) "" else catalog.displayName(id))
                }
            }
            }
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
                    val next = TrainerSessionFactory.withChoreographyOptions(
                        state.copy(
                            mode = intent.mode,
                            storylineSteps = steps,
                            expandedStep = state.expandedStep.coerceIn(0, steps.size),
                        ),
                    )
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetExpandedStep ->
                _uiState.update {
                    val max = it.storylineSteps.size.coerceAtLeast(1)
                    val target = if (it.expandedStep == intent.step) 0 else intent.step
                    it.copy(expandedStep = target.coerceIn(0, max))
                }
            is TrainerIntent.SetStyle ->
                _uiState.update { state ->
                    val catalog = danceStyles?.state?.value
                    val id = if (intent.style.isBlank()) "" else catalog?.choiceId(intent.style)
                        ?: cachedAppData?.danceStyles?.singleOrNull { it.id == intent.style || it.name.equals(intent.style, true) }?.id.orEmpty()
                    val next = state.copy(selectedStyleId = id,
                        styleName = if (id.isNotBlank() && catalog != null) catalog.displayName(id) else intent.style)
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
            is TrainerIntent.SetUseElementTiming ->
                _uiState.update { state ->
                    val interval =
                        if (intent.enabled && state.intervalSec <= 0f) 8f else state.intervalSec
                    val next = state.copy(
                        useElementTiming = intent.enabled,
                        intervalSec = interval,
                    )
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
            is TrainerIntent.SetChoreographyTag ->
                _uiState.update { state ->
                    val next = TrainerSessionFactory.withChoreographyOptions(
                        state.copy(choreographyTag = intent.tag),
                    )
                    next.copy(readyPercent = TrainerReady.compute(next))
                }
            is TrainerIntent.SetSelectedChoreography ->
                _uiState.update { state ->
                    val id = state.choreographyIdsByTitle[intent.title].orEmpty()
                    val next = state.copy(
                        selectedChoreographyTitle = intent.title,
                        selectedChoreographyId = id,
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
        // Refresh catalog once more so Live sees latest Room rows.
        scope.launch {
            val load = loadLibrary
            if (load != null) {
                runCatching { load.load() }.getOrNull()?.let { cachedAppData = it }
            }
            navigation.pushNew(Config.Session)
        }
    }

    override fun onBack(): Boolean {
        // C-268: Finish / back from Live ends the process-scoped session.
        TrainerLiveSessionStore.end()
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
                        sharedState = _uiState,
                        onStyleSelected = { onIntent(TrainerIntent.SetStyle(it)) },
                    ),
                )
            Config.Session -> {
                val restored = TrainerLiveSessionStore.snapshot?.takeIf { TrainerLiveSessionStore.active }
                val sessionState = restored
                    ?: TrainerSessionFactory.build(_uiState.value, cachedAppData)
                TrainerComponent.Child.Session(
                    DefaultTrainerSessionStubComponent(
                        componentContext = context,
                        initial = sessionState,
                        ttsController = ttsController,
                        scope = scope,
                        isRestored = restored != null,
                    ),
                )
            }
        }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Hub : Config

        @Serializable
        data object Session : Config
    }
}
