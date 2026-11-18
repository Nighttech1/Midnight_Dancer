package com.midnightdancer.app.ui.elements

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.view.Surface
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * C-313 / live-scrub engine. Frame-accurate preview decoder for the Pro Editor playhead.
 *
 * C-325 THREAD SAFETY (the hard crash on scrub-back): a MediaCodec/MediaExtractor must never be
 * touched from two threads at once or used after release — that is a NATIVE SIGSEGV that no
 * try/catch can catch and that takes the whole app down. EVERYTHING here (render, surface change,
 * teardown) runs on ONE dedicated thread, so pipeline access is strictly serialized.
 *
 * Design: conflated requests (a drag floods targets; only the newest is decoded), one warm per-URI
 * pipeline, forward decode for targets ahead / re-seek for backward or new clip. Every native call
 * is wrapped; any failure returns and the caller falls back to the still cache.
 *
 * NOTE: a multi-clip warm cache (prefetch) was attempted for faster switching but regressed the
 * decoder (see W-2026-10-31-choreo-prefetch-desync) — reverted to this known-good single pipeline.
 */
class ScrubVideoDecoder {

    private data class Req(val context: Context, val uri: String, val targetUs: Long)

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "md-scrub-decoder") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val requests = Channel<Req>(Channel.CONFLATED)

    @Volatile private var released = false
    /** Request metadata only; native objects and pendingWithoutSurface remain decoder-thread-only. */
    @Volatile private var latestRequest: Req? = null

    // Touched ONLY on the single decoder thread.
    private var surface: Surface? = null
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var currentUri: String? = null
    private var lastRenderedUs: Long = -1L
    private var inputDone = false
    private var pendingWithoutSurface: Req? = null

    init {
        val worker = scope.launch {
            try {
                for (req in requests) {
                    if (released) break
                    runCatching { renderInternal(req) }
                }
            } finally {
                pendingWithoutSurface = null
                teardownPipeline()
                surface = null
            }
        }
        // Closing the channel completes the worker, not its SupervisorJob. Close only after
        // the worker's finally has released all native objects on the decoder thread.
        worker.invokeOnCompletion { runCatching { dispatcher.close() } }
    }

    fun attach(newSurface: Surface?) {
        if (released) return
        scope.launch {
            if (released) return@launch
            val changed = surface !== newSurface
            if (changed) {
                surface = newSurface
                teardownPipeline()
            }
            // The consumer can receive an already-buffered request before this queued attach
            // coroutine runs. Keep that request instead of requiring another user gesture.
            // A recreated surface also needs the latest still even if no new scrub gesture
            // has arrived since the preceding surface rendered successfully.
            if (newSurface != null && (changed || pendingWithoutSurface != null)) {
                pendingWithoutSurface = null
                latestRequest?.let { newest -> runCatching { renderInternal(newest) } }
            }
        }
    }

    fun requestFrame(context: Context, uri: String, targetUs: Long) {
        if (released || uri.isBlank()) return
        val req = Req(context.applicationContext, uri, targetUs.coerceAtLeast(0L))
        latestRequest = req
        requests.trySend(req)
    }

    fun release() {
        released = true
        latestRequest = null
        runCatching { requests.close() }
    }

    private fun renderInternal(req: Req) {
        if (released || latestRequest !== req) return
        val out = surface
        if (out == null) {
            pendingWithoutSurface = req
            android.util.Log.d("MdPlaybackQA", "scrub-pending-surface")
            return
        }
        pendingWithoutSurface = null
        // Phase-0 telemetry: switch→first-frame latency, split by cold (new file = MediaCodec
        // reconfigure, the ~5s pain) vs warm (same file, just a re-seek). Proven in MdPlaybackQA so
        // the proxy work can be measured, not eyeballed on a ~1fps emulator.
        val t0 = System.nanoTime()
        val wasCold = codec == null || currentUri != runCatching { com.midnightdancer.core.data.files.OwnedMediaRuntime.resolveReference(req.uri) }.getOrNull()
        ensurePipeline(req.context, req.uri, out) ?: return
        val ex = extractor ?: return
        val dec = codec ?: return
        val target = req.targetUs

        if (lastRenderedUs < 0L || target < lastRenderedUs) {
            runCatching { ex.seekTo(target, MediaExtractor.SEEK_TO_PREVIOUS_SYNC); dec.flush() }
            inputDone = false
        }

        val info = MediaCodec.BufferInfo()
        var rendered = false
        var guard = 0
        while (!rendered && guard++ < 600) {
            // Complete an in-flight frame for the same file so continuous fast drags cannot
            // starve rendering. A switch to another file invalidates the old image immediately.
            if (released || latestRequest?.uri != req.uri) return
            if (!inputDone) {
                val inIdx = runCatching { dec.dequeueInputBuffer(2_000L) }.getOrDefault(-1)
                if (inIdx >= 0) {
                    val buf = runCatching { dec.getInputBuffer(inIdx) }.getOrNull()
                    val size = if (buf != null) runCatching { ex.readSampleData(buf, 0) }.getOrDefault(-1) else -1
                    if (size < 0) {
                        runCatching { dec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM) }
                        inputDone = true
                    } else {
                        val pts = ex.sampleTime
                        runCatching { dec.queueInputBuffer(inIdx, 0, size, pts, 0) }
                        ex.advance()
                    }
                }
            }
            val outIdx = runCatching { dec.dequeueOutputBuffer(info, 2_000L) }.getOrDefault(-1)
            when {
                outIdx >= 0 -> {
                    val reachedTarget = info.presentationTimeUs >= target ||
                        (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    val renderToSurface = reachedTarget && !released && latestRequest?.uri == req.uri
                    runCatching { dec.releaseOutputBuffer(outIdx, renderToSurface) }
                    if (renderToSurface) { lastRenderedUs = info.presentationTimeUs; rendered = true }
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> if (inputDone) break
            }
        }
        if (rendered) {
            val ms = (System.nanoTime() - t0) / 1_000_000L
            android.util.Log.i(
                "MdPlaybackQA",
                "scrub-frame cold=$wasCold latencyMs=$ms uri=${Integer.toHexString(req.uri.hashCode())}",
            )
        }
    }

    private fun ensurePipeline(context: Context, reference: String, out: Surface): Unit? {
        val uri = runCatching { com.midnightdancer.core.data.files.OwnedMediaRuntime.resolveReference(reference) }.getOrNull() ?: return null
        if (codec != null && currentUri == uri) return Unit
        teardownPipeline()
        return runCatching {
            // Publish each native resource before the next fallible setup call. The common
            // failure teardown must own partially initialized extractors/codecs as well.
            val ex = MediaExtractor()
            extractor = ex
            ex.setDataSource(context, Uri.parse(uri), null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val fmt = ex.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { track = i; format = fmt; break }
            }
            if (track < 0 || format == null) { teardownPipeline(); return@runCatching null }
            ex.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val dec = MediaCodec.createDecoderByType(mime)
            codec = dec
            dec.configure(format, out, null, 0)
            dec.start()
            currentUri = uri
            lastRenderedUs = -1L
            inputDone = false
            Unit
        }.getOrElse { teardownPipeline(); null }
    }

    private fun teardownPipeline() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { extractor?.release() }
        codec = null
        extractor = null
        currentUri = null
        lastRenderedUs = -1L
        inputDone = false
    }
}
