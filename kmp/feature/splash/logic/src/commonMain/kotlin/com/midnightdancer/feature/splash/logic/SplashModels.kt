package com.midnightdancer.feature.splash.logic

/**
 * Splash shell state — SKIE-friendly immutable data.
 * [show] true while splash is active; [legalAcceptedStub] is a Stage 3 legal-consent stub flag.
 * [bootReady] / [skipVisible] become true after TTS [com.midnightdancer.core.tts.TtsEngine.prepare]
 * finishes (Ready or Failed). [prepareFailed] is informational; skip still appears.
 */
data class SplashUiState(
    val show: Boolean = true,
    val legalAcceptedStub: Boolean = false,
    val bootReady: Boolean = false,
    val prepareFailed: Boolean = false,
) {
    /** Alias for chrome: «Пропустить» only when [bootReady]. */
    val skipVisible: Boolean
        get() = bootReady
}

sealed interface SplashIntent {
    data object Continue : SplashIntent
    data class SetLegalStub(val accepted: Boolean) : SplashIntent
}
