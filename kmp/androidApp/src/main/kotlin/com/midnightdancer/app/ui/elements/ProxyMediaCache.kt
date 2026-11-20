package com.midnightdancer.app.ui.elements

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * W-2026-11-01 (#A, "как в CapCut"): low-res PREVIEW PROXIES for the scrub engine.
 *
 * The 5s "video loads for ~5 seconds when I swipe the timeline fast" is a COLD MediaCodec start on
 * the ORIGINAL file — a 1080p/4K/HEVC source takes a long time to configure+seek the first frame on
 * every clip switch. CapCut (and OpenShot) hide this the same way: edit against a small downscaled
 * proxy, render the original only on export. A ~480p H.264 proxy configures and seeks near-instantly.
 *
 * This is additive and safe: until a clip's proxy is ready, callers use the original (today's
 * behaviour). Proxies are generated once, in the background, with Media3 Transformer (already a
 * dependency — no paid service), cached under cacheDir/scrub_proxies keyed by the source, and reused
 * across sessions. Audio is dropped (preview is muted) to make the transcode faster and the file
 * smaller. Duration and presentation timestamps are preserved, so a source-timebase seek maps 1:1.
 */
@OptIn(UnstableApi::class)
object ProxyMediaCache {

    /**
     * DISABLED (W-2026-11-01 cycle 2, 0.4.24). Generating proxies with Media3 Transformer grabbed the
     * phone's single hardware video encoder and ran on the main Looper, starving the scrub decoder's
     * codec and freezing the picture on the first scrub (audio kept playing on ExoPlayer's own threads
     * — the "only music, frozen video" bug). The IDEA is right (small proxy = fast cold start), but it
     * MUST run off-thread on a dedicated codec pool and never while playback/scrub holds a codec. Until
     * that is built (Фаза 2), this is a hard no-op: every caller falls back to the original (= 0.4.22
     * scrub behaviour). Code kept for the re-implementation; flip ENABLED when the pool exists.
     */
    private const val ENABLED = false

    /** Preview height; width follows the source aspect. 480p keeps decode cheap yet readable. */
    private const val TARGET_HEIGHT = 480
    private const val DIR = "scrub_proxies"

    /** sourceUri -> ready proxy file:// uri. */
    private val ready = ConcurrentHashMap<String, String>()

    /** sourceUri currently transcoding (dedupe concurrent requests for the same clip). */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /** Proxy file:// uri if the proxy for [sourceUri] is ready on disk, else null. */
    fun proxyUriIfReady(context: Context, sourceUri: String): String? {
        if (!ENABLED) return null
        if (sourceUri.isBlank()) return null
        ready[sourceUri]?.let { return it }
        // Survive process restarts: a finished proxy may already sit in the cache dir.
        val f = fileFor(context, sourceUri)
        if (f.exists() && f.length() > 0L) {
            val uri = Uri.fromFile(f).toString()
            ready[sourceUri] = uri
            return uri
        }
        return null
    }

    /**
     * Kick off proxy generation for [sourceUri] if it is not ready and not already running. Returns
     * immediately; the transcode runs in the background. No-op once a proxy exists.
     */
    fun ensureProxy(context: Context, sourceUri: String) {
        if (!ENABLED) return
        if (sourceUri.isBlank()) return
        if (proxyUriIfReady(context, sourceUri) != null) return
        if (!inFlight.add(sourceUri)) return

        val appCtx = context.applicationContext
        val outFile = fileFor(appCtx, sourceUri)
        // Stale/partial leftover from a killed transcode — start clean.
        runCatching { if (outFile.exists()) outFile.delete() }

        // Transformer must be built and started on a thread with a Looper, and reports back on it.
        Handler(Looper.getMainLooper()).post {
            val started = runCatching {
                val source = MediaItem.fromUri(Uri.parse(com.midnightdancer.core.data.files.OwnedMediaRuntime.resolveReference(sourceUri)))
                val videoEffects: List<Effect> = listOf(Presentation.createForHeight(TARGET_HEIGHT))
                val edited = EditedMediaItem.Builder(source)
                    .setRemoveAudio(true)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()
                val transformer = Transformer.Builder(appCtx)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            ready[sourceUri] = Uri.fromFile(outFile).toString()
                            inFlight.remove(sourceUri)
                            android.util.Log.i(
                                "MdPlaybackQA",
                                "proxy-ready src=${shortId(sourceUri)} bytes=${outFile.length()}",
                            )
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException,
                        ) {
                            runCatching { outFile.delete() }
                            inFlight.remove(sourceUri)
                            android.util.Log.w(
                                "MdPlaybackQA",
                                "proxy-fail src=${shortId(sourceUri)} err=${exception.errorCode}",
                            )
                        }
                    })
                    .build()
                transformer.start(edited, outFile.absolutePath)
                android.util.Log.i("MdPlaybackQA", "proxy-start src=${shortId(sourceUri)}")
            }
            if (started.isFailure) {
                inFlight.remove(sourceUri)
                android.util.Log.w("MdPlaybackQA", "proxy-start-fail src=${shortId(sourceUri)}")
            }
        }
    }

    private fun fileFor(context: Context, sourceUri: String): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        return File(dir, "${shortId(sourceUri)}_h$TARGET_HEIGHT.mp4")
    }

    private fun shortId(uri: String): String = Integer.toHexString(uri.hashCode())
}
