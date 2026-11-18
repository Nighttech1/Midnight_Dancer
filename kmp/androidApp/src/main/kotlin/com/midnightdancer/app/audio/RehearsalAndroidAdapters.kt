package com.midnightdancer.app.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.ToneGenerator
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.midnightdancer.core.tts.*
import com.midnightdancer.feature.trainer.logic.*
import java.io.File
import kotlinx.coroutines.*

/** Own player: no competing Live ticker or background autoplay may own this session. */
class AndroidRehearsalPlayback(private val context: Context) : RehearsalPlaybackPort {
    private val player = ExoPlayer.Builder(context.applicationContext).setMediaSourceFactory(com.midnightdancer.app.audio.ownedMediaSourceFactory(context)).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        setHandleAudioBecomingNoisy(true)
        setSeekParameters(SeekParameters.EXACT)
        repeatMode = Player.REPEAT_MODE_OFF
    }
    private var plan: RehearsalPlan? = null
    var onInterrupted: () -> Unit = {}

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) onInterrupted()
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) onInterrupted()
            }
        })
    }

    override suspend fun prepare(plan: RehearsalPlan): Long? {
        TrainerLiveAudioController.forceStop()
        // Validate original material bounds before reporting a usable D. No synthetic fallback.
        withContext(Dispatchers.IO) {
            plan.musicClips.forEach { clip ->
                val retriever = MediaMetadataRetriever()
                try {
                    val uri = mediaUri(clip.uri)
                    retriever.setDataSource(context, uri)
                    val sourceMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        ?: error("Не удалось определить длительность музыкального файла")
                    require(clip.sourceStartMs >= 0 && clip.durationMs > 0 &&
                        clip.sourceStartMs <= sourceMs - clip.durationMs) {
                        "Музыкальный участок выходит за границы исходного файла"
                    }
                } finally { retriever.release() }
            }
        }
        this.plan = plan
        player.volume = plan.musicVolume
        player.setMediaItems(plan.musicClips.map { clip ->
            MediaItem.Builder().setUri(mediaUri(clip.uri)).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.sourceStartMs)
                    .setEndPositionMs(clip.sourceStartMs + clip.durationMs).build()
            ).build()
        })
        player.prepare()
        awaitReady()
        return plan.durationMs
    }

    override suspend fun seekTo(positionMs: Long) {
        val current = requireNotNull(plan)
        val (index, sourceMs) = current.sourcePositionAt(positionMs)
            ?: error("Участок недоступен в музыкальной шкале")
        player.pause()
        player.seekTo(index, sourceMs - current.musicClips[index].sourceStartMs)
        // Let ExoPlayer process the seek before readiness is evaluated.
        yield()
        awaitReady()
    }

    override suspend fun awaitReady() {
        withTimeout(20_000L) {
            while (player.playbackState != Player.STATE_READY) {
                player.playerError?.let { throw it }
                check(player.playbackState != Player.STATE_ENDED) { "Музыка закончилась до начала участка" }
                delay(10)
            }
            player.playerError?.let { throw it }
        }
    }

    override suspend fun playAndAwaitStarted() {
        player.play()
        withTimeout(20_000L) {
            while (!player.isPlaying) {
                player.playerError?.let { throw it }
                delay(5)
            }
        }
        Log.i("MdRehearsalQA", "audio-start positionMs=${positionMs()}")
    }

    override fun positionMs(): Long {
        player.playerError?.let { throw it }
        val current = plan ?: return 0L
        if (player.playbackState == Player.STATE_ENDED) return current.durationMs
        val clip = current.musicClips.getOrNull(player.currentMediaItemIndex) ?: return 0L
        return clip.timelineStartMs + player.currentPosition.coerceAtLeast(0L)
    }

    override fun pause() { player.pause() }
    override fun stop() { player.pause(); player.stop(); player.clearMediaItems() }
    fun release() { player.release() }
    private fun mediaUri(reference: String): Uri {
        val value = com.midnightdancer.core.data.files.OwnedMediaRuntime.resolveReference(reference)
        return if (value.startsWith("/")) Uri.fromFile(File(value)) else Uri.parse(value)
    }
}

class AndroidRehearsalVoice(
    private val scope: CoroutineScope,
    private val engine: TtsEngine,
    private val voice: TrainerVoice,
) : RehearsalVoicePort {
    private var speaking: Job? = null
    private var generation = 0L
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 35)
    var onFailure: (String) -> Unit = {}

    override suspend fun prepare() {
        when (val prepared = engine.prepare(voice)) {
            is PrepareResult.Failed -> error(prepared.reason)
            else -> Unit
        }
    }

    override fun speak(label: String) {
        stop()
        val token = generation
        speaking = scope.launch {
            engine.stop()
            if (token != generation) return@launch
            when (val result = engine.speak(SpeakRequest(label, voice, SpeakOptions(duckUnderMusic = false)))) {
                is SpeakOutcome.Failed -> {
                    Log.w("MdRehearsalQA", "voice-failed ${result.reason}")
                    if (token == generation) onFailure(result.reason)
                }
                else -> Unit
            }
        }
    }

    override fun stop() {
        generation++
        speaking?.cancel()
        speaking = null
        tone.stopTone()
        // Cancellation of the active engine speak stops its utterance immediately.
        // Main-immediate scope also stops the engine before any subsequent cue is scheduled.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { engine.stop() }
    }

    override fun countdown(seconds: Int) { if (seconds > 0) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90) }
    fun release() { stop(); engine.dispose(); tone.release() }
}
