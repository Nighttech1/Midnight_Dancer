package com.midnightdancer.app.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Process-scoped Media3 player for the Music library (C-178).
 * Survives tab composition dispose and Activity ON_STOP so playback continues
 * across dock tabs and when the app is backgrounded.
 * Call [forceStop] when Trainer Live or Pro Editor playback starts.
 */
object LibraryAudioController {
    @Volatile
    private var player: ExoPlayer? = null

    /** Track id last started from the Music deck — survives ChildPages DESTROY. */
    @Volatile
    var activeTrackId: String? = null
        private set

    /** Media URI currently loaded into the player (C-182 resume without restart). */
    @Volatile
    var activeMediaUri: String? = null
        private set

    fun obtain(context: Context): ExoPlayer {
        player?.let { return it }
        return synchronized(this) {
            player ?: ExoPlayer.Builder(context.applicationContext).setMediaSourceFactory(com.midnightdancer.app.audio.ownedMediaSourceFactory(context)).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    /* handleAudioFocus = */ true,
                )
            }.also { player = it }
        }
    }

    fun current(): ExoPlayer? = player

    fun pause() {
        player?.pause()
    }

    fun markPlaying(trackId: String, mediaUri: String? = null) {
        activeTrackId = trackId
        if (mediaUri != null) activeMediaUri = mediaUri
    }

    fun clearPlayingMark() {
        activeTrackId = null
        activeMediaUri = null
    }

    /** Same track still loaded — do not setMediaItem / seek(0) on tab remount. */
    fun isContinuing(trackId: String, mediaUri: String): Boolean {
        val exo = player ?: return false
        return activeTrackId == trackId &&
            activeMediaUri == mediaUri &&
            exo.mediaItemCount > 0 &&
            (exo.isPlaying || exo.playWhenReady)
    }

    fun forceStop() {
        activeTrackId = null
        activeMediaUri = null
        player?.let {
            it.pause()
            it.stop()
            it.clearMediaItems()
        }
    }

    fun knownDurationMs(): Long? =
        player?.duration?.takeIf { it != C.TIME_UNSET && it > 0L }

    fun isActuallyPlaying(): Boolean = player?.isPlaying == true
}
