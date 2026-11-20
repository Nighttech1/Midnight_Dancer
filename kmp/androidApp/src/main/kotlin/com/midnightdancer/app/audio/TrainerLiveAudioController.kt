package com.midnightdancer.app.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer

/**
 * C-256: process-scoped Live training music — survives tab leave / Compose dispose /
 * Activity ON_STOP so the session keeps playing until Finish.
 * C-276: supports multi-clip playlist from selected choreography music blocks.
 */
object TrainerLiveAudioController {
    @Volatile
    private var player: ExoPlayer? = null

    /** Single URI or joined playlist key (`uri1|uri2|…`). */
    @Volatile
    var activeUri: String? = null
        private set

    fun obtain(context: Context): ExoPlayer {
        player?.let { return it }
        return synchronized(this) {
            player ?: ExoPlayer.Builder(context.applicationContext).setMediaSourceFactory(com.midnightdancer.app.audio.ownedMediaSourceFactory(context)).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    /* handleAudioFocus = */ false,
                )
            }.also { player = it }
        }
    }

    fun current(): ExoPlayer? = player

    fun ensureUri(context: Context, uri: String?): ExoPlayer {
        val exo = obtain(context)
        if (uri.isNullOrBlank()) {
            exo.pause()
            exo.clearMediaItems()
            activeUri = null
            return exo
        }
        if (activeUri != uri || exo.mediaItemCount == 0) {
            activeUri = uri
            exo.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            exo.prepare()
        }
        return exo
    }

    /** C-276: load ordered music blocks; replaces any previous single-URI freestyle bind. */
    fun ensurePlaylist(context: Context, uris: List<String>): ExoPlayer {
        val exo = obtain(context)
        val clean = uris.map { it.trim() }.filter { it.isNotBlank() }
        if (clean.isEmpty()) {
            exo.pause()
            exo.clearMediaItems()
            activeUri = null
            return exo
        }
        if (clean.size == 1) {
            return ensureUri(context, clean.first())
        }
        val key = clean.joinToString("|")
        if (activeUri != key || exo.mediaItemCount != clean.size) {
            activeUri = key
            exo.setMediaItems(clean.map { MediaItem.fromUri(Uri.parse(it)) })
            exo.prepare()
        }
        return exo
    }

    /** Absolute timeline position across concatenated media items (ms). */
    fun absolutePositionMs(exo: ExoPlayer): Long {
        val timeline = exo.currentTimeline
        if (timeline.isEmpty) return exo.currentPosition.coerceAtLeast(0L)
        val window = Timeline.Window()
        var elapsed = 0L
        val idx = exo.currentMediaItemIndex.coerceAtLeast(0)
        for (i in 0 until idx) {
            timeline.getWindow(i, window)
            val d = window.durationMs
            if (d > 0L) elapsed += d
        }
        return elapsed + exo.currentPosition.coerceAtLeast(0L)
    }

    /** Sum of window durations when known; else [fallbackMs]. */
    fun absoluteDurationMs(exo: ExoPlayer, fallbackMs: Long): Long {
        val timeline = exo.currentTimeline
        if (timeline.isEmpty) return fallbackMs.coerceAtLeast(1L)
        val window = Timeline.Window()
        var total = 0L
        for (i in 0 until timeline.windowCount) {
            timeline.getWindow(i, window)
            val d = window.durationMs
            if (d <= 0L) return fallbackMs.coerceAtLeast(1L)
            total += d
        }
        return total.coerceAtLeast(1L)
    }

    /**
     * Seek by absolute ms across the playlist.
     * Prefers ExoPlayer [Timeline] window durations when known (C-278) so vault
     * snapshot lengths cannot disagree with real media and snap the Live ring back.
     */
    fun seekAbsolute(exo: ExoPlayer, absoluteMs: Long, clipDurationsMs: List<Long>) {
        if (exo.mediaItemCount <= 1) {
            val dur = exo.duration.takeIf { it > 0L }
                ?: clipDurationsMs.firstOrNull()
                ?: 1L
            exo.seekTo(absoluteMs.coerceIn(0L, (dur - 1L).coerceAtLeast(0L)))
            return
        }
        val timeline = exo.currentTimeline
        val window = Timeline.Window()
        val durations = ArrayList<Long>(exo.mediaItemCount)
        var allKnown = !timeline.isEmpty && timeline.windowCount == exo.mediaItemCount
        if (allKnown) {
            for (i in 0 until timeline.windowCount) {
                timeline.getWindow(i, window)
                val d = window.durationMs
                if (d <= 0L) {
                    allKnown = false
                    break
                }
                durations += d
            }
        }
        if (!allKnown) {
            durations.clear()
            if (clipDurationsMs.size >= exo.mediaItemCount) {
                repeat(exo.mediaItemCount) { durations += clipDurationsMs[it].coerceAtLeast(1L) }
            } else {
                // Fallback: equal split of absolute target space.
                val fallback = clipDurationsMs.firstOrNull()?.coerceAtLeast(1L) ?: 1_000L
                repeat(exo.mediaItemCount) { durations += fallback }
            }
        }
        var remaining = absoluteMs.coerceAtLeast(0L)
        for (i in durations.indices) {
            val d = durations[i].coerceAtLeast(1L)
            val last = i == durations.lastIndex
            if (remaining < d || last) {
                exo.seekTo(i, remaining.coerceIn(0L, (d - 1L).coerceAtLeast(0L)))
                return
            }
            remaining -= d
        }
    }

    fun forceStop() {
        activeUri = null
        player?.let {
            it.pause()
            it.stop()
            it.clearMediaItems()
        }
    }
}
