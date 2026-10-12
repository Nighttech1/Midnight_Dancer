package com.midnightdancer.core.tts

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Trainer-facing façade: prepare → speak with ducking option; serializes calls.
 * Default sample phrase when UI sends empty text.
 */
class TrainerTtsController(
    private val engine: TtsEngine,
) {
    private val mutex = Mutex()

    val currentVoice: TrainerVoice? get() = engine.currentVoice
    val currentBackend: TtsBackend? get() = engine.currentBackend

    suspend fun selectAndSpeak(
        voice: TrainerVoice,
        text: String = DEFAULT_SPEAK_TEXT,
        options: SpeakOptions = SpeakOptions(),
    ): SpeakOutcome =
        mutex.withLock {
            val prepared = engine.prepare(voice)
            if (prepared is PrepareResult.Failed) {
                return@withLock SpeakOutcome.Failed(prepared.reason)
            }
            val body = text.trim().ifEmpty { defaultPhraseFor(voice) }
            engine.speak(
                SpeakRequest(
                    text = body,
                    voice = voice,
                    options = options,
                ),
            )
        }

    suspend fun stop() = mutex.withLock { engine.stop() }

    fun dispose() = engine.dispose()

    companion object {
        const val DEFAULT_SPEAK_TEXT: String = ""

        fun defaultPhraseFor(voice: TrainerVoice): String =
            when (voice) {
                TrainerVoice.Ruslan -> "Привет! Я Руслан, голос тренера Midnight Dancer."
                TrainerVoice.Irina -> "Привет! Я Ирина, голос тренера Midnight Dancer."
                TrainerVoice.Kamila -> "Hi! I'm Kamila, your Midnight Dancer coach voice."
            }

        fun speakLabel(
            voice: TrainerVoice,
            backend: TtsBackend?,
            speaking: Boolean,
        ): String {
            val backendPart =
                when (backend) {
                    TtsBackend.PiperOffline -> "Piper"
                    TtsBackend.SystemFallback -> "System TTS"
                    null -> "…"
                }
            return if (speaking) {
                "Speaking · ${voice.displayNameRu} · $backendPart"
            } else {
                "Speak · ${voice.displayNameRu} · $backendPart"
            }
        }
    }
}
