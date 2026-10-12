package com.midnightdancer.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Android TTS actual:
 * 1. Probe on-demand Piper ONNX (one voice) — if present, note Piper-ready;
 *    Sherpa JNI AAR is **not** vendored in this wave → fall through to system TTS
 *    (documented bridge; avoids OOM from loading 60–78 MB without native engine).
 * 2. System [TextToSpeech] with locale/voice hints per [TrainerVoice].
 * 3. Best-effort music ducking via [AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK].
 */
class AndroidTtsEngine(
    private val context: Context,
) : TtsEngine {

    override var currentVoice: TrainerVoice? = null
        private set
    override var currentBackend: TtsBackend? = null
        private set

    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private var focusRequest: AudioFocusRequest? = null
    private var lastPiperPaths: PiperModelProbe.ModelPaths? = null

    override suspend fun prepare(voice: TrainerVoice): PrepareResult {
        // File probe off Main so splash + ExoPlayer first frames are not starved (C-53).
        lastPiperPaths = null
        val filesDir = context.applicationContext.filesDir
        val piper = withContext(Dispatchers.Default) {
            PiperModelProbe.find(voice, filesDir)
        }
        lastPiperPaths = piper

        val piperNote =
            if (piper != null) {
                "ONNX found (${PiperModelProbe.approxSizeMb(piper)} MB) at ${piper.modelDir}; " +
                    "Sherpa JNI not linked in KMP yet → System TTS. " +
                    VoiceAssetPolicy.LATENCY_NOTE
            } else {
                "No on-demand ONNX under filesDir/${VoiceAssetPolicy.ON_DEMAND_RELATIVE_ROOT}/${voice.id}. " +
                    "Run ${VoiceAssetPolicy.COPY_SCRIPT} then copy models to device filesDir (≈${VoiceAssetPolicy.APPROX_TOTAL_ONNX_MB} MB for all three). " +
                    "Using System TTS."
            }

        return withContext(Dispatchers.Main) {
            ensureSystemTts()
            val ok = ready.get()
            if (!ok) {
                return@withContext PrepareResult.Failed(voice, "TextToSpeech init failed")
            }

            applyVoiceHints(voice)
            currentVoice = voice
            currentBackend = TtsBackend.SystemFallback
            PrepareResult.Ready(
                voice = voice,
                backend = TtsBackend.SystemFallback,
                piperAttempted = piper != null,
                note = piperNote,
            )
        }
    }

    override suspend fun speak(request: SpeakRequest): SpeakOutcome =
        withContext(Dispatchers.Main) {
            val prep = prepare(request.voice)
            if (prep is PrepareResult.Failed) {
                return@withContext SpeakOutcome.Failed(prep.reason)
            }
            val engine = tts ?: return@withContext SpeakOutcome.Failed("TTS null")
            val text = request.text.trim()
            if (text.isEmpty()) {
                return@withContext SpeakOutcome.Failed("empty text")
            }

            if (request.options.duckUnderMusic) {
                requestDuckFocus()
            }

            val rate = (0.5f + request.options.speed.coerceIn(0f, 1f)).coerceIn(0.5f, 1.5f)
            val pitch = request.options.pitch.coerceIn(0.5f, 2f)
            engine.setSpeechRate(rate)
            engine.setPitch(pitch)

            val utteranceId = UUID.randomUUID().toString()
            val spoken =
                suspendCancellableCoroutine { cont ->
                    engine.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit

                            override fun onDone(utteranceId: String?) {
                                abandonDuckFocus()
                                if (cont.isActive) {
                                    cont.resume(true)
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                abandonDuckFocus()
                                if (cont.isActive) {
                                    cont.resume(false)
                                }
                            }

                            override fun onError(utteranceId: String?, errorCode: Int) {
                                abandonDuckFocus()
                                if (cont.isActive) {
                                    cont.resume(false)
                                }
                            }
                        },
                    )
                    cont.invokeOnCancellation {
                        engine.stop()
                        abandonDuckFocus()
                    }
                    val params = Bundle()
                    val code =
                        engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                    if (code != TextToSpeech.SUCCESS && cont.isActive) {
                        abandonDuckFocus()
                        cont.resume(false)
                    }
                }

            if (!spoken) {
                return@withContext SpeakOutcome.Failed("speak() failed")
            }
            SpeakOutcome.Spoken(
                voice = request.voice,
                backend = TtsBackend.SystemFallback,
                textPreview = text.take(48),
            )
        }

    override suspend fun stop() =
        withContext(Dispatchers.Main) {
            tts?.stop()
            abandonDuckFocus()
        }

    override fun dispose() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (t: Throwable) {
            Log.w(TAG, "dispose: $t")
        }
        tts = null
        ready.set(false)
        abandonDuckFocus()
        lastPiperPaths = null
        currentVoice = null
        currentBackend = null
    }

    private suspend fun ensureSystemTts() {
        if (tts != null && ready.get()) return
        tts?.shutdown()
        ready.set(false)
        val initialized =
            suspendCancellableCoroutine { cont ->
                lateinit var engine: TextToSpeech
                engine =
                    TextToSpeech(context.applicationContext) { status ->
                        ready.set(status == TextToSpeech.SUCCESS)
                        if (cont.isActive) {
                            cont.resume(engine)
                        }
                    }
            }
        tts = initialized
    }

    private fun applyVoiceHints(voice: TrainerVoice) {
        val engine = tts ?: return
        val locale = Locale.forLanguageTag(voice.systemLocaleTag)
        engine.language = locale
        // Prefer a distinct installed voice when the OEM exposes several for the locale.
        val voices = engine.voices ?: emptySet()
        val matched =
            when (voice) {
                TrainerVoice.Ruslan ->
                    voices.firstOrNull {
                        it.locale.language == "ru" &&
                            (it.name.contains("male", true) || it.name.contains("ru-ru-x-lmd", true))
                    } ?: voices.firstOrNull { it.locale.language == "ru" }
                TrainerVoice.Irina ->
                    voices.firstOrNull {
                        it.locale.language == "ru" &&
                            (it.name.contains("female", true) || it.name.contains("ru-ru-x-dfc", true))
                    } ?: voices.filter { it.locale.language == "ru" }.getOrNull(1)
                        ?: voices.firstOrNull { it.locale.language == "ru" }
                TrainerVoice.Kamila ->
                    voices.firstOrNull {
                        it.locale.language == "en" &&
                            (it.name.contains("female", true) || it.locale.country.equals("US", true))
                    } ?: voices.firstOrNull { it.locale.language == "en" }
            }
        if (matched != null) {
            engine.voice = matched
        }
    }

    private fun requestDuckFocus() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
            focusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
    }

    private fun abandonDuckFocus() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    companion object {
        private const val TAG = "AndroidTtsEngine"
    }
}

actual fun createPlatformTtsEngine(): TtsEngine =
    AndroidTtsEngine(requireTtsContext())
