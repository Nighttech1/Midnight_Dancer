package com.midnightdancer.app.ui.elements

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * C-291: scrub preview frames for Pro Editor — nearest ~500ms bucket via MMR,
 * so ExoPlayer is not seek-stormed while the user drags the playhead.
 *
 * W-2026-11-01 #A (real-device, MediaTek Redmi): the editor crashed mid-scrub with
 *   FATAL: FinalizerWatchdogDaemon — MediaMetadataRetriever.finalize() timed out after 60s.
 * Cause: warmStrip swept the WHOLE clip in 500ms buckets and did so for ±1 neighbours, so a single
 * cross-seam drag spun up HUNDREDS of MediaMetadataRetriever instances. Each `.use {}` closes its
 * MMR, but the native finalize still runs on GC, and under a saturated hardware decoder one of those
 * native finalizes blocked 60s → the watchdog killed the app. Fix: never create an MMR per frame.
 * A tiny LRU POOL of reusable retrievers (one per URI, current + its two neighbours) is kept open and
 * RELEASED EXPLICITLY on eviction/clear, so at most [MAX_RETRIEVERS] instances ever exist and nothing
 * is left for the finalizer queue. All native MMR access is serialized on one thread (MMR is not
 * thread-safe), mirroring the known-good single-pipeline design of [ScrubVideoDecoder].
 */
object EditorScrubFrameCache {
    const val BUCKET_MS = 500L
    private const val MAX_ENTRIES = 240
    private const val MAX_RETRIEVERS = 3

    private val frames = ConcurrentHashMap<String, Bitmap>()

    // One thread: serialize every native MMR call AND its explicit release so instances never pile
    // onto the finalizer queue (the 60s finalize-timeout crash).
    private val mmrExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "md-scrub-mmr") }
    private val mmrDispatcher = mmrExecutor.asCoroutineDispatcher()
    // LRU (accessOrder) pool keyed by URI. Touched ONLY on mmrExecutor.
    private val retrievers = object : LinkedHashMap<String, MediaMetadataRetriever>(4, 0.75f, true) {}

    private fun key(uri: String, sourceStartMs: Long, bucketMs: Long): String =
        "$uri|$sourceStartMs|$bucketMs"

    fun bucketOf(localOffsetMs: Long): Long =
        (localOffsetMs.coerceAtLeast(0L) / BUCKET_MS) * BUCKET_MS

    fun get(uri: String, sourceStartMs: Long, localOffsetMs: Long): Bitmap? {
        val effective = runCatching { com.midnightdancer.core.data.files.OwnedMediaRuntime.resolveReference(uri) }.getOrNull() ?: return null
        val b = bucketOf(localOffsetMs)
        return frames[key(effective, sourceStartMs, b)]
            ?: frames[key(effective, sourceStartMs, (b - BUCKET_MS).coerceAtLeast(0L))]
            ?: frames[key(effective, sourceStartMs, b + BUCKET_MS)]
    }

    /** Must run on [mmrExecutor]. Reuses an open retriever for [uri] or opens one, evicting LRU. */
    private fun retrieverFor(context: Context, uri: String): MediaMetadataRetriever? {
        retrievers[uri]?.let { return it }
        val mmr = MediaMetadataRetriever()
        val ok = runCatching { mmr.setDataSource(context, Uri.parse(uri)) }.isSuccess
        if (!ok) {
            runCatching { mmr.release() } // explicit even on open failure — no finalizer reliance
            return null
        }
        retrievers[uri] = mmr
        while (retrievers.size > MAX_RETRIEVERS) {
            val oldestKey = retrievers.keys.firstOrNull() ?: break
            val old = retrievers.remove(oldestKey)
            runCatching { old?.release() } // explicit — never leave it for native_finalize
        }
        return mmr
    }

    suspend fun getOrFetch(
        context: Context,
        uri: String,
        sourceStartMs: Long,
        localOffsetMs: Long,
    ): Bitmap? {
        val effective = runCatching { com.midnightdancer.core.data.files.OwnedMediaRuntime.resolveReference(uri) }.getOrNull() ?: return null
        get(effective, sourceStartMs, localOffsetMs)?.let { return it }
        val bucket = bucketOf(localOffsetMs)
        return withContext(mmrDispatcher) {
            get(effective, sourceStartMs, localOffsetMs)?.let { return@withContext it }
            val absUs = (sourceStartMs + bucket) * 1000L
            val mmr = retrieverFor(context, effective) ?: return@withContext null
            val bmp = runCatching {
                mmr.getFrameAtTime(absUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }.getOrNull()
            if (bmp != null) {
                trimIfNeeded()
                frames[key(effective, sourceStartMs, bucket)] = bmp
            }
            bmp
        }
    }

    /**
     * Prefetch strip across [0, durationMs] for snappy scrub. [shouldPause] lets a background warm
     * YIELD the shared MMR thread the moment the user starts dragging, so the live on-demand frame
     * fetch is never stuck behind a long sweep (W-2026-11-01 #A, the ~once-per-5s "frozen" scrub).
     */
    suspend fun warmStrip(
        context: Context,
        uri: String,
        sourceStartMs: Long,
        durationMs: Long,
        stepMs: Long = BUCKET_MS,
        shouldPause: () -> Boolean = { false },
    ) {
        if (uri.isBlank() || durationMs <= 0L) return
        var t = 0L
        val end = durationMs.coerceAtLeast(1L)
        while (t <= end) {
            while (shouldPause()) kotlinx.coroutines.delay(150L)
            getOrFetch(context, uri, sourceStartMs, t)
            t += stepMs
        }
    }

    /** Release all pooled retrievers explicitly (call on editor dispose). */
    fun release() {
        mmrExecutor.execute {
            val it = retrievers.values.iterator()
            while (it.hasNext()) {
                runCatching { it.next().release() }
                it.remove()
            }
        }
    }

    private fun trimIfNeeded() {
        if (frames.size <= MAX_ENTRIES) return
        val drop = frames.size - MAX_ENTRIES + 40
        frames.keys.take(drop).forEach { frames.remove(it) }
    }
}
