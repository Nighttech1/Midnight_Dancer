package com.midnightdancer.core.tts

/** Which engine produced (or will produce) audio. */
enum class TtsBackend {
    /** Piper VITS via Sherpa-ONNX (offline). */
    PiperOffline,

    /** Platform system TTS (Android TextToSpeech / iOS AVSpeech). */
    SystemFallback,
}

data class SpeakOptions(
    val speed: Float = 0.5f,
    val pitch: Float = 1.0f,
    val volume: Float = 1.0f,
    /** Best-effort ducking under music (AudioFocus / AVAudioSession). */
    val duckUnderMusic: Boolean = true,
)

data class SpeakRequest(
    val text: String,
    val voice: TrainerVoice,
    val options: SpeakOptions = SpeakOptions(),
)

sealed interface PrepareResult {
    data class Ready(
        val voice: TrainerVoice,
        val backend: TtsBackend,
        /** True when ONNX was found but Piper engine could not load (OOM / missing JNI). */
        val piperAttempted: Boolean = false,
        val note: String = "",
    ) : PrepareResult

    data class Failed(
        val voice: TrainerVoice,
        val reason: String,
    ) : PrepareResult
}

/**
 * On-demand packaging policy (C-33).
 * Three medium Piper models ≈ 63 + 63 + 78 MB ≈ **205 MB** + espeak-ng-data.
 * Do **not** ship all three inside the base APK/IPA by default.
 */
object VoiceAssetPolicy {
    const val APPROX_TOTAL_ONNX_MB: Int = 205
    const val FLAVOR_FOLDER: String = "full"
    const val ON_DEMAND_RELATIVE_ROOT: String = "voices/full"

    /** Expected ONNX filenames per voice (Piper VITS). */
    fun onnxFileName(voice: TrainerVoice): String =
        when (voice) {
            TrainerVoice.Ruslan -> "ru_RU-ruslan-medium.onnx"
            TrainerVoice.Irina -> "ru_RU-irina-medium.onnx"
            TrainerVoice.Kamila -> "en_US-libritts_r-medium.onnx"
        }

    const val TOKENS_FILE: String = "tokens.txt"
    const val MODEL_ALIAS: String = "model.onnx"

    const val COPY_SCRIPT: String = "scripts/copy_voices_from_old_app.ps1"

    const val LATENCY_NOTE: String =
        "Piper first speak = model load (seconds, one voice at a time) + synth + PCM play; " +
            "system fallback speak ≈ 100–400 ms after prepare. " +
            "Dispose previous OfflineTts before switching voices to avoid OOM."
}
