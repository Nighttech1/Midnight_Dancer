package com.midnightdancer.core.tts

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.setActive

/**
 * iOS TTS actual: AVSpeechSynthesizer with language hints for Ruslan / Irina / Kamila.
 * Piper ONNX remains Android/Flutter-primary this wave; iOS = system voices + duckOthers.
 * True Piper on iOS = future Sherpa XCFramework (documented bridge in C-33 notes).
 */
@OptIn(ExperimentalForeignApi::class)
class IosAvSpeechTtsEngine : TtsEngine {

    override var currentVoice: TrainerVoice? = null
        private set
    override var currentBackend: TtsBackend? = null
        private set

    private val synthesizer = AVSpeechSynthesizer()

    override suspend fun prepare(voice: TrainerVoice): PrepareResult =
        withContext(Dispatchers.Main) {
            currentVoice = voice
            currentBackend = TtsBackend.SystemFallback
            PrepareResult.Ready(
                voice = voice,
                backend = TtsBackend.SystemFallback,
                piperAttempted = false,
                note = "iOS AVSpeech system fallback. Piper/Sherpa XCFramework not linked; " +
                    VoiceAssetPolicy.LATENCY_NOTE,
            )
        }

    override suspend fun speak(request: SpeakRequest): SpeakOutcome =
        withContext(Dispatchers.Main) {
            prepare(request.voice)
            val text = request.text.trim()
            if (text.isEmpty()) {
                return@withContext SpeakOutcome.Failed("empty text")
            }

            if (request.options.duckUnderMusic) {
                runCatching {
                    val session = AVAudioSession.sharedInstance()
                    session.setCategory(
                        category = AVAudioSessionCategoryPlayback,
                        withOptions = AVAudioSessionCategoryOptionDuckOthers,
                        error = null,
                    )
                    session.setActive(true, error = null)
                }
            }

            val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
            // ~0.4–0.6 maps UI speed 0..1 onto a natural AVSpeech range.
            utterance.rate = (0.4f + request.options.speed.coerceIn(0f, 1f) * 0.25f)
            utterance.pitchMultiplier = request.options.pitch.coerceIn(0.5f, 2f)
            utterance.volume = request.options.volume.coerceIn(0f, 1f)
            utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage(request.voice.systemLocaleTag)

            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
            synthesizer.speakUtterance(utterance)

            SpeakOutcome.Spoken(
                voice = request.voice,
                backend = TtsBackend.SystemFallback,
                textPreview = text.take(48),
            )
        }

    override suspend fun stop() =
        withContext(Dispatchers.Main) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }

    override fun dispose() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        currentVoice = null
        currentBackend = null
    }
}

actual fun createPlatformTtsEngine(): TtsEngine = IosAvSpeechTtsEngine()
