package com.midnightdancer.core.tts

/**
 * Offline-first TTS contract (SKIE-friendly).
 *
 * ARCH (C-33): Piper/Sherpa via platform actual when ONNX present + JNI available;
 * otherwise system TTS. Load **one** voice at a time (dispose previous) to avoid OOM.
 */
interface TtsEngine {
    val currentVoice: TrainerVoice?
    val currentBackend: TtsBackend?

    /**
     * Load one voice (dispose previous). Splash (C-49) treats **Ready and Failed**
     * as boot-ready so Skip is never stuck; missing ONNX / JNI → Ready + SystemFallback.
     */
    suspend fun prepare(voice: TrainerVoice): PrepareResult

    suspend fun speak(request: SpeakRequest): SpeakOutcome

    suspend fun stop()

    /** Release native OfflineTts / AudioTrack / AVSpeech resources. */
    fun dispose()
}

sealed interface SpeakOutcome {
    data class Spoken(
        val voice: TrainerVoice,
        val backend: TtsBackend,
        val textPreview: String,
    ) : SpeakOutcome

    data class Failed(
        val reason: String,
    ) : SpeakOutcome
}

/** Test / preview engine — records last speak, never crashes. */
class RecordingTtsEngine : TtsEngine {
    override var currentVoice: TrainerVoice? = null
        private set
    override var currentBackend: TtsBackend? = null
        private set

    var prepareCount: Int = 0
        private set
    var speakCount: Int = 0
        private set
    var lastText: String? = null
        private set
    var disposed: Boolean = false
        private set

    override suspend fun prepare(voice: TrainerVoice): PrepareResult {
        prepareCount++
        currentVoice = voice
        currentBackend = TtsBackend.SystemFallback
        return PrepareResult.Ready(
            voice = voice,
            backend = TtsBackend.SystemFallback,
            note = "recording",
        )
    }

    override suspend fun speak(request: SpeakRequest): SpeakOutcome {
        if (disposed) return SpeakOutcome.Failed("disposed")
        val prepared = prepare(request.voice)
        if (prepared is PrepareResult.Failed) {
            return SpeakOutcome.Failed(prepared.reason)
        }
        speakCount++
        lastText = request.text
        return SpeakOutcome.Spoken(
            voice = request.voice,
            backend = TtsBackend.SystemFallback,
            textPreview = request.text.take(48),
        )
    }

    override suspend fun stop() = Unit

    override fun dispose() {
        disposed = true
        currentVoice = null
        currentBackend = null
    }
}
