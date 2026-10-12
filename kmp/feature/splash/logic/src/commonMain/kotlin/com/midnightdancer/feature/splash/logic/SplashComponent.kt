package com.midnightdancer.feature.splash.logic

import com.arkivanov.decompose.ComponentContext
import com.midnightdancer.core.tts.PrepareResult
import com.midnightdancer.core.tts.TtsEngine
import com.midnightdancer.core.tts.TrainerVoice
import com.midnightdancer.core.tts.createPlatformTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

/**
 * Splash / boot — advances to Main via [finishSplash] (tap Continue).
 * Android plays looping muted `splash_boot.mp4`; [autoFinish] only for tests.
 *
 * Production Root passes [autoFinish]=false and waits for TTS prepare (C-49).
 * [RootComponentSmokeTest] relies on default [autoFinish]=true (immediate Main).
 */
interface SplashComponent {
    val uiState: StateFlow<SplashUiState>
    fun onIntent(intent: SplashIntent)

    /** Convenience for chrome that only needs continue. */
    fun onFinished()
}

class DefaultSplashComponent(
    componentContext: ComponentContext,
    private val finishSplash: () -> Unit,
    private val autoFinish: Boolean = true,
    ttsEngine: TtsEngine? = null,
    lastVoice: TrainerVoice = TrainerVoice.Ruslan,
) : SplashComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine: TtsEngine = ttsEngine ?: runCatching { createPlatformTtsEngine() }.getOrElse {
        // Unit tests / hosts without bindTtsAndroidContext — recording engine.
        com.midnightdancer.core.tts.RecordingTtsEngine()
    }
    private val voice: TrainerVoice = lastVoice

    private val _uiState = MutableStateFlow(SplashUiState())
    override val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        if (autoFinish) {
            advanceToMain()
        } else {
            scope.launch {
                // Yield so first splash frames (video + LOADING VOICE) are not starved
                // by Main.immediate running prepare before Compose draws (C-53 / C-106 ANR).
                yield()
                val result =
                    withTimeoutOrNull(PREPARE_TIMEOUT_MS) {
                        // Keep TTS prepare off UI thread — cold boot System UI ANR risk (C-106).
                        withContext(Dispatchers.Default) {
                            runCatching { engine.prepare(voice) }.getOrElse { error ->
                                PrepareResult.Failed(
                                    voice = voice,
                                    reason = error.message ?: error.toString(),
                                )
                            }
                        }
                    } ?: PrepareResult.Failed(
                        voice = voice,
                        reason = "TTS prepare timed out",
                    )
                val failed = result is PrepareResult.Failed
                _uiState.update { it.copy(bootReady = true, prepareFailed = failed) }
            }
        }
    }

    override fun onIntent(intent: SplashIntent) {
        when (intent) {
            SplashIntent.Continue -> {
                if (!autoFinish && !_uiState.value.bootReady) {
                    return
                }
                advanceToMain()
            }
            is SplashIntent.SetLegalStub ->
                _uiState.update { it.copy(legalAcceptedStub = intent.accepted) }
        }
    }

    override fun onFinished() {
        onIntent(SplashIntent.Continue)
    }

    private fun advanceToMain() {
        _uiState.update { it.copy(show = false) }
        finishSplash()
    }

    companion object {
        /** Skip must appear even if system TTS / file probe hangs on device (C-53). */
        const val PREPARE_TIMEOUT_MS = 2_500L
    }
}
