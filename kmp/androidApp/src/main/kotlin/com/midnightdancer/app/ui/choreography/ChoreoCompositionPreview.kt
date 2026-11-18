package com.midnightdancer.app.ui.choreography

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.midnightdancer.app.ui.elements.ScrubVideoDecoder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.elements.EditorScrubFrameCache
import com.midnightdancer.app.ui.elements.ElementThumbCache
import com.midnightdancer.app.ui.elements.ProxyMediaCache
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.feature.choreography.logic.EditorClip
import com.midnightdancer.feature.choreography.logic.EditorTrackDurations
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val SurfaceLowest = Color(0xFF0D0E12)
private const val SEEK_SCRUB_END_MS = 40L
/** Emulator + multi-URI reorder: goldfish decode often buffers 2–6s; 1.5s caused false pause. */
private const val BUFFERING_GIVE_UP_MS = 10_000L
private const val BIND_READY_BUDGET_MS = 10_000L

/**
 * C-293 / Wave E: single [CompositionPlayer] timeline.
 * - Seek = composition presentation ms (`seekTo(timelineMs)`). Media3 ignores mediaItemIndex
 *   in CompositionPlayer.handleSeek — `seekTo(index, offset)` wrongly jumps to [offset] on the
 *   whole tape. Clip index via [EditorTrackDurations.compositionSeekTarget] is for logs/overlay
 *   and follows current `elementClips` order after reorder.
 * - While playing: player owns decode clock — no per-tick seek, no 250ms play() hammer
 * - Scrub: strip bitmap until first frame after scrub-end seek
 */
@OptIn(UnstableApi::class)
@Composable
fun ChoreoCompositionPreview(
    isPlaying: Boolean,
    playPulse: Float,
    isEmpty: Boolean,
    currentElementLabel: String?,
    elementClips: List<EditorClip>,
    /** Playhead ms along speed-adjusted element lane (0 … elementsDuration). */
    playheadInElementsMs: Long,
    scrubbing: Boolean,
    glow: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoClips = remember(elementClips) {
        elementClips.filter { !it.mediaUri.isNullOrBlank() }
    }
    val compositionKey = remember(videoClips) {
        videoClips.joinToString(";") {
            "${it.mediaUri}|${it.sourceStartMs}|${it.durationMs}|${it.speed}"
        }
    }
    val elementsDurationMs = remember(videoClips) {
        videoClips.sumOf {
            EditorTrackDurations.effectiveMs(it.durationMs, it.speed.takeIf { s -> s > 0.0 } ?: 1.0)
        }.coerceAtLeast(1L)
    }
    val targetMs = playheadInElementsMs.coerceIn(0L, elementsDurationMs)
    val scrubClip = remember(videoClips, targetMs) {
        clipAtTimeline(videoClips, targetMs)
    }
    // W-2026-11-01 #A: index of the clip under the playhead. The warm-strip effect keys on THIS, not
    // on targetMs, so dragging within one clip does not relaunch the MMR warm burst on every tick
    // (that re-fired a full-clip MediaMetadataRetriever sweep per scrub pixel and churned HW decoder
    // instances). It re-warms only when the playhead crosses into a new clip neighbourhood.
    val warmIndex = remember(videoClips, targetMs) {
        clipIndexAtTimeline(videoClips, targetMs).coerceIn(0, (videoClips.size - 1).coerceAtLeast(0))
    }

    // C-314: ExoPlayer playlist replaces CompositionPlayer for playback. CompositionPlayer
    // (UnstableApi) could not decode real content in real time — it buffered 2–6s, leaving the
    // preview frozen ~68% of a real session while the editor clock ran on. ExoPlayer decodes in
    // real time and transitions a playlist gaplessly within ONE surface, so the seam stays clean.
    // Per-clip speed is applied on each item transition. Scrub still uses the MediaCodec decoder.
    val player = remember {
        // C-326: a 2nd video renderer prewarmed the NEXT element's decoder to smooth the first-pass
        // seam. W-2026-11-01 #A (real-device QA, MediaTek Redmi): that prewarm keeps TWO hardware
        // AVC decoders alive for the whole session (confirmed in logcat: the prewarmed one reports
        // Render:0/Drop:all — it decodes every frame and throws them away). MediaTek phones allow
        // only a handful of concurrent HW video decoders, so during a scrub (ExoPlayer held + the
        // live ScrubVideoDecoder) the pool hit 5–6 instances, the scrub decoder was starved, and the
        // picture froze while the audio track kept playing — the user's exact symptom. Prewarming OFF
        // trades a slightly softer first seam for one playback decoder, freeing the pool for scrub.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 60_000, 1_000, 2_000)
            .build()
        val renderers = DefaultRenderersFactory(context.applicationContext)
        ExoPlayer.Builder(context.applicationContext, renderers).setMediaSourceFactory(com.midnightdancer.app.audio.ownedMediaSourceFactory(context))
            .setLoadControl(loadControl)
            .build().apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var surfaceEpoch by remember { mutableStateOf(0) }
    var appliedSurfaceEpoch by remember { mutableStateOf(-1) }
    // C-312: use-after-release guard. CompositionPlayer is UnstableApi; effects/listeners can
    // outlive dispose on fast back-navigation and crash the editor. Gate every player touch.
    var released by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        onDispose {
            released = true
            runCatching { player.release() }
        }
    }
    // C-313: frame-accurate live-scrub decoder (renders the exact frame under the finger).
    val scrubDecoder = remember { ScrubVideoDecoder() }
    DisposableEffect(scrubDecoder) {
        onDispose {
            runCatching { scrubDecoder.release() }
            // W-2026-11-01 #A: free the pooled scrub-still retrievers when the editor closes.
            runCatching { EditorScrubFrameCache.release() }
        }
    }

    var boundKey by remember { mutableStateOf<String?>(null) }
    var scrubBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var holdScrubOverlay by remember { mutableStateOf(false) }
    var wasScrubbing by remember { mutableStateOf(false) }
    // C-327: index of the clip whose frame is ACTUALLY on screen (player's real item / last scrub
    // render), so the overlay label matches the picture — not the ticker, which raced ahead of the
    // decoder on fast switching and showed e.g. "element 3" while element 10 was on screen.
    var renderedClipIndex by remember { mutableStateOf(0) }
    val latestPlaying by rememberUpdatedState(isPlaying)
    val latestScrubbing by rememberUpdatedState(scrubbing)
    val latestTargetMs by rememberUpdatedState(targetMs)
    val latestClips by rememberUpdatedState(videoClips)
    val holdOverlayRef = rememberUpdatedState(holdScrubOverlay)

    fun seekComposition(reason: String, timelineMs: Long = latestTargetMs) {
        if (released) return
        val clips = latestClips
        if (clips.isEmpty()) return
        val dur = clips.sumOf {
            EditorTrackDurations.effectiveMs(it.durationMs, it.speed.takeIf { s -> s > 0.0 } ?: 1.0)
        }.coerceAtLeast(1L)
        val want = timelineMs.coerceIn(0L, dur)
        val mapped = EditorTrackDurations.compositionSeekTarget(clips, want) ?: return
        val clip = clips.getOrNull(mapped.first)
        val speed = clip?.speed?.takeIf { it > 0.0 } ?: 1.0
        // C-314: ExoPlayer playlist — seek by (clipIndex, source-local ms). Presentation ms is
        // speed-adjusted, so source ms = presentationMs * speed. Guarded (never crashes the editor).
        val localSourceMs = (mapped.second * speed).toLong().coerceAtLeast(0L)
        if (player.playbackState == Player.STATE_IDLE) {
            runCatching { player.prepare() }
        }
        runCatching { player.seekTo(mapped.first, localSourceMs) }
        android.util.Log.d(
            "MdAvDebug",
            "exo seek reason=$reason want=$want idx=${mapped.first} local=$localSourceMs " +
                "→ pos=${player.currentPosition} state=${player.playbackState}",
        )
    }

    fun ensurePlayingIfWanted() {
        if (released || !latestPlaying || latestScrubbing) return
        runCatching {
            player.playWhenReady = true
            when (player.playbackState) {
                Player.STATE_READY, Player.STATE_BUFFERING -> player.play()
                Player.STATE_IDLE -> runCatching { player.prepare() }
                else -> Unit
            }
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_START -> {
                    surfaceEpoch += 1
                    val view = playerViewRef
                    if (view != null) {
                        view.player = null
                        view.player = player
                    }
                    ensurePlayingIfWanted()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    // Wave E: leave tab must not leave seek/play hammer running.
                    player.playWhenReady = false
                    player.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    ensurePlayingIfWanted()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // C-314: apply this element's speed as the playlist advances across a seam.
                val idx = player.currentMediaItemIndex
                val sp = (latestClips.getOrNull(idx)?.speed?.takeIf { it > 0.0 } ?: 1.0).toFloat()
                runCatching { player.playbackParameters = PlaybackParameters(sp) }
                renderedClipIndex = idx // C-327: label follows the clip actually playing
            }

            override fun onRenderedFirstFrame() {
                renderedClipIndex = player.currentMediaItemIndex // C-327
                if (holdOverlayRef.value) {
                    holdScrubOverlay = false
                    scrubBitmap = null
                    android.util.Log.d("MdAvDebug", "composition first-frame cleared scrub overlay")
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(compositionKey) {
        if (videoClips.isEmpty()) {
            runCatching { player.stop(); player.clearMediaItems() }
            boundKey = null
            return@LaunchedEffect
        }
        // C-314: one ClippingMediaItem per element — ExoPlayer plays the playlist gaplessly in a
        // single surface (no dual-player seam, no CompositionPlayer buffering stall).
        val items = videoClips.mapNotNull { clip ->
            val uri = clip.mediaUri?.trim()
            if (uri.isNullOrEmpty()) return@mapNotNull null
            val start = clip.sourceStartMs.coerceAtLeast(0L)
            val end = start + clip.durationMs.coerceAtLeast(1L)
            MediaItem.Builder()
                .setUri(Uri.parse(uri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(start)
                        .setEndPositionMs(end)
                        .build(),
                )
                .build()
        }
        playerViewRef?.let { view -> if (view.player !== player) view.player = player }
        runCatching {
            player.setMediaItems(items)
            player.prepare()
        }
        // Initial element speed; transitions update it per clip.
        val firstSpeed = (videoClips.firstOrNull()?.speed?.takeIf { it > 0.0 } ?: 1.0).toFloat()
        runCatching { player.playbackParameters = PlaybackParameters(firstSpeed) }
        seekComposition("bind", latestTargetMs)
        boundKey = compositionKey
        android.util.Log.d(
            "MdAvDebug",
            "exo bind clips=${items.size} state=${player.playbackState} pos=${player.currentPosition}",
        )
        if (latestPlaying && !latestScrubbing) {
            player.playWhenReady = true
            ensurePlayingIfWanted()
        } else {
            player.playWhenReady = false
            player.pause()
        }
    }

    // C-321: playback telemetry for Client auto-QA (no screen recording, no human). Logs the
    // DECODER's real rendered/dropped frame counts via ExoPlayer.videoDecoderCounters — these are
    // immune to the emulator's ~1fps display, so "frozen" = decoder stops producing frames while
    // playing, which the log parser (scripts/qa/analyze_playback_logs.py) asserts on. tag MdPlaybackQA.
    LaunchedEffect(boundKey, isPlaying) {
        if (boundKey == null || !isPlaying) return@LaunchedEffect
        var lastRendered = -1L
        var lastNanos = System.nanoTime()
        var bufferingMs = 0L
        android.util.Log.i("MdPlaybackQA", "play-begin clips=${videoClips.size} dur=${elementsDurationMs}")
        while (isActive && latestPlaying && !latestScrubbing) {
            val counters = runCatching { player.videoDecoderCounters }.getOrNull()
            runCatching { counters?.ensureUpdated() }
            val rendered = counters?.renderedOutputBufferCount?.toLong() ?: -1L
            val dropped = counters?.droppedBufferCount?.toLong() ?: -1L
            val now = System.nanoTime()
            val dtMs = (now - lastNanos) / 1_000_000L
            val dRendered = if (lastRendered >= 0 && rendered >= 0) rendered - lastRendered else 0L
            val fps = if (dtMs > 0) dRendered * 1000.0 / dtMs else 0.0
            val st = player.playbackState
            if (st == Player.STATE_BUFFERING) bufferingMs += dtMs
            android.util.Log.i(
                "MdPlaybackQA",
                "sample state=$st playing=${player.isPlaying} pos=${player.currentPosition} " +
                    "idx=${player.currentMediaItemIndex} rendered=$rendered dRendered=$dRendered " +
                    "fps=${"%.1f".format(fps)} dropped=$dropped bufferingMsTotal=$bufferingMs",
            )
            lastRendered = rendered
            lastNanos = now
            delay(500L)
        }
        android.util.Log.i("MdPlaybackQA", "play-end pos=${player.currentPosition} state=${player.playbackState}")
    }

    // Warm strip ±1 neighbors for scrub. W-2026-11-01 #A: keyed on warmIndex (clip boundary), not
    // targetMs, so it fires once per clip entered instead of on every scrub tick. It also no longer
    // runs WHILE scrubbing: the warm sweep and the live backdrop fetch share one MMR thread, so a
    // full-clip sweep queued ahead of the current-frame fetch left the preview updating ~once per
    // 5s drag (looked frozen). Warming now happens only when idle (pre-cache for the next drag), and
    // during the drag the on-demand getOrFetch owns the thread → the still backdrop tracks the finger.
    // The step is also coarser (every 1.5s) so even the idle sweep cannot clog the thread for long;
    // on-demand fetches still fill the exact 500ms bucket under the finger.
    LaunchedEffect(compositionKey, warmIndex, scrubbing) {
        if (videoClips.isEmpty() || scrubbing) return@LaunchedEffect
        val idx = warmIndex.coerceIn(0, videoClips.lastIndex)
        val from = (idx - 1).coerceAtLeast(0)
        val to = (idx + 1).coerceAtMost(videoClips.lastIndex)
        for (i in from..to) {
            val c = videoClips[i]
            val uri = c.mediaUri ?: continue
            EditorScrubFrameCache.warmStrip(
                context,
                uri,
                c.sourceStartMs.coerceAtLeast(0L),
                c.durationMs.coerceAtLeast(1L),
                stepMs = 1_500L,
            )
            // W-2026-11-01 #A: build the low-res scrub proxy for this clip and its neighbors so a
            // switch onto them decodes the small file (fast), not a cold 1080p/4K original (~5s).
            ProxyMediaCache.ensureProxy(context, uri)
        }
    }

    // W-2026-11-01 #A: pre-warm the still FILMSTRIP for EVERY clip on open, in the background. A fast
    // flick across the whole timeline then finds a cached still at each position and shows it instantly
    // — EditorScrubFrameCache.get() is a pure map hit that bypasses the MMR thread entirely — instead
    // of waiting on a cold full-res decode, which is what left a hard scrub frozen for seconds. Coarse
    // step + spacing keeps it light, and it YIELDS the MMR thread whenever the user is actively dragging
    // (the live on-demand fetch owns it then). Cache hits make return visits free. (ProxyMediaCache is
    // a no-op until the off-thread proxy pool exists; the still filmstrip is the smoothness lever now.)
    LaunchedEffect(compositionKey) {
        if (videoClips.isEmpty()) return@LaunchedEffect
        delay(500L) // let the current clip's ±1 neighbour warm start first
        for (c in videoClips) {
            val uri = c.mediaUri ?: continue
            EditorScrubFrameCache.warmStrip(
                context,
                uri,
                c.sourceStartMs.coerceAtLeast(0L),
                c.durationMs.coerceAtLeast(1L),
                stepMs = 1_000L,
                shouldPause = { latestScrubbing },
            )
            ProxyMediaCache.ensureProxy(context, uri)
            delay(250L) // spacing so UI/scrub stay responsive during the background sweep
        }
    }

    LaunchedEffect(scrubbing, targetMs, scrubClip?.mediaUri) {
        if (!scrubbing) return@LaunchedEffect
        holdScrubOverlay = true
        val clip = scrubClip
        val uri = clip?.mediaUri
        if (clip == null || uri.isNullOrBlank()) {
            scrubBitmap = null
            return@LaunchedEffect
        }
        val local = localMsInClip(videoClips, targetMs, clip)
        // C-313: live frame under the finger — absolute source microseconds into this clip's file.
        val absSourceUs = (clip.sourceStartMs.coerceAtLeast(0L) + local) * 1000L
        // W-2026-11-01 #A: decode the small proxy when it is ready (near-instant cold start), else
        // the original (today's behaviour). The proxy keeps the source timebase, so absSourceUs maps 1:1.
        val decodeUri = ProxyMediaCache.proxyUriIfReady(context, uri) ?: uri
        scrubDecoder.requestFrame(context, decodeUri, absSourceUs)
        // Still from the MMR cache remains as an instant backdrop until the decoded frame lands.
        val bmp = EditorScrubFrameCache.getOrFetch(
            context,
            uri,
            clip.sourceStartMs.coerceAtLeast(0L),
            local,
        ) ?: ElementThumbCache.get(uri) ?: ElementThumbCache.loadOrFetch(context, uri)
        scrubBitmap = bmp?.asImageBitmap()
    }

    // Wave E: play edge — one seek + wait READY once; no per-tick seek, no 250ms hammer.
    LaunchedEffect(isPlaying, scrubbing, boundKey, surfaceEpoch) {
        if (boundKey == null || videoClips.isEmpty()) return@LaunchedEffect
        if (scrubbing) {
            player.playWhenReady = false
            player.pause()
            return@LaunchedEffect
        }
        if (isPlaying) {
            seekComposition("play-edge")
            player.playWhenReady = true
            ensurePlayingIfWanted()
            val started = System.currentTimeMillis()
            var slowBufferLogged = false
            while (isActive && latestPlaying && !latestScrubbing) {
                when (player.playbackState) {
                    Player.STATE_READY -> {
                        if (!player.isPlaying) {
                            player.playWhenReady = true
                            player.play()
                        }
                        // Free-run: editor ticker advances playhead; player owns decode.
                        break
                    }
                    Player.STATE_BUFFERING -> {
                        // Never pause on slow buffer (Wave E reorder + multi-URI on emulator).
                        if (!slowBufferLogged &&
                            System.currentTimeMillis() - started > BUFFERING_GIVE_UP_MS
                        ) {
                            slowBufferLogged = true
                            android.util.Log.w(
                                "MdAvDebug",
                                "composition buffering >${BUFFERING_GIVE_UP_MS}ms — keep playWhenReady",
                            )
                        }
                        player.playWhenReady = true
                        ensurePlayingIfWanted()
                        delay(50L)
                    }
                    Player.STATE_IDLE -> {
                        runCatching { player.prepare() }
                        player.playWhenReady = true
                        delay(50L)
                    }
                    else -> delay(50L)
                }
            }
        } else {
            player.playWhenReady = false
            player.pause()
            seekComposition("pause-edge")
        }
    }

    // Scrub-end only (true→false). Do not seek on first composition bind.
    LaunchedEffect(scrubbing, boundKey) {
        if (boundKey == null || videoClips.isEmpty()) return@LaunchedEffect
        if (scrubbing) {
            wasScrubbing = true
            player.pause()
            return@LaunchedEffect
        }
        if (!wasScrubbing) return@LaunchedEffect
        wasScrubbing = false
        delay(SEEK_SCRUB_END_MS)
        holdScrubOverlay = true
        seekComposition("scrub-end")
        if (latestPlaying) {
            player.playWhenReady = true
            ensurePlayingIfWanted()
        }
        // C-312: was 800ms — the frozen scrub still lingered after release, reading as "video stops".
        // Shorter fallback; onRenderedFirstFrame still clears it as soon as live catches up.
        delay(450L)
        if (holdScrubOverlay) {
            holdScrubOverlay = false
            scrubBitmap = null
        }
    }

    // SeekStart / SeekTo / SeekEnd while paused: playhead moves but isPlaying stays false,
    // so play-edge/pause-edge never fire — must seek CompositionPlayer to absolute ms.
    // Never chase targetMs while playing (ticker free-runs; player owns the clock).
    LaunchedEffect(targetMs, isPlaying, scrubbing, boundKey) {
        if (boundKey == null || videoClips.isEmpty()) return@LaunchedEffect
        if (isPlaying || scrubbing) return@LaunchedEffect
        holdScrubOverlay = true
        seekComposition("idle-seek")
        delay(450L)
        if (holdScrubOverlay) {
            holdScrubOverlay = false
            scrubBitmap = null
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val legacyWidth = minOf(maxWidth * 0.72f, maxHeight * (9f / 16f))
        val frameWidth = ((maxWidth + legacyWidth) / 2).coerceAtMost(maxWidth)
        val frameHeight = minOf(maxHeight, legacyWidth * (16f / 9f))
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .width(frameWidth)
                .height(frameHeight)
                .clip(shape)
                .background(SurfaceLowest)
                .then(
                    if (isEmpty) {
                        Modifier
                    } else {
                        Modifier.border(
                            width = 1.dp,
                            color = AtelierColors.Flame.copy(alpha = 0.15f + playPulse * glow * 0.45f),
                            shape = shape,
                        )
                    },
                ),
        ) {
            if (isEmpty) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Начнем творить!",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MdFonts.Display,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Загрузи твой первый элемент",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        fontFamily = MdFonts.Display,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                if (videoClips.isNotEmpty()) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                // C-312: FIT, not ZOOM. ZOOM re-crops to each clip's own size, so a
                                // seam between two elements of different resolution/aspect read as a
                                // sudden zoom "explosion". FIT holds one stable canvas across the
                                // seam (CapCut-like). Letterbox on the lowest surface is acceptable.
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                this.player = player
                                setKeepContentOnPlayerReset(true)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                playerViewRef = this
                            }
                        },
                        update = { view ->
                            playerViewRef = view
                            if (appliedSurfaceEpoch != surfaceEpoch) {
                                appliedSurfaceEpoch = surfaceEpoch
                                view.player = null
                                view.player = player
                            } else if (view.player !== player) {
                                view.player = player
                            }
                            view.setKeepContentOnPlayerReset(true)
                        },
                        modifier = Modifier.fillMaxSize().zIndex(0f),
                    )
                }
                // C-313: MMR still as an instant backdrop (z=1) under the live decoded frame.
                if ((scrubbing || holdScrubOverlay) && scrubBitmap != null) {
                    Image(
                        bitmap = scrubBitmap!!,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().zIndex(1f),
                    )
                }
                // C-313 / W-2026-11-01 #A: live-scrub surface — ScrubVideoDecoder renders the exact
                // frame here (z=2). It is shown only AFTER the finger lifts (holdScrubOverlay && not
                // scrubbing), never DURING an active drag. On a mid MediaTek the full-res frame decode
                // can't keep up with a fast flick, so the last decoded frame stuck on top of the fresh
                // cached-still backdrop and read as "frozen". CapCut-style: during the drag the viewer
                // sees the instant filmstrip still (z=1, cache hit) that tracks the finger; the exact
                // frame settles in on release (here, and via the ExoPlayer scrub-end seek). This also
                // frees the scrub decoder during the drag (no surface → no decode → no pool pressure).
                if (holdScrubOverlay && !scrubbing) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                isOpaque = false
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                        scrubDecoder.attach(Surface(st))
                                    }
                                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        scrubDecoder.attach(null)
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize().zIndex(2f),
                    )
                }
                // C-327: label = clip ACTUALLY on screen. Scrub → the clip under the finger; play →
                // the player's real current item; paused → the settled state label.
                val liveLabel = when {
                    scrubbing || holdScrubOverlay -> scrubClip?.label ?: currentElementLabel
                    isPlaying -> videoClips.getOrNull(renderedClipIndex)?.label ?: currentElementLabel
                    else -> currentElementLabel
                }
                if (!liveLabel.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .background(Color(0xFF0A0A0C), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .zIndex(3f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = liveLabel,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = MdFonts.Display,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private fun clipAtTimeline(clips: List<EditorClip>, timelineMs: Long): EditorClip? {
    val idx = clipIndexAtTimeline(clips, timelineMs)
    return clips.getOrNull(idx) ?: clips.lastOrNull()
}

private fun clipIndexAtTimeline(clips: List<EditorClip>, timelineMs: Long): Int {
    var start = 0L
    for (i in clips.indices) {
        val clip = clips[i]
        val speed = clip.speed.takeIf { it > 0.0 } ?: 1.0
        val eff = EditorTrackDurations.effectiveMs(clip.durationMs, speed)
        val end = start + eff
        if (timelineMs < end) return i
        start = end
    }
    return clips.lastIndex.coerceAtLeast(0)
}

/** Source-local ms inside the media file (for strip cache), not Composition presentation. */
private fun localMsInClip(clips: List<EditorClip>, timelineMs: Long, clip: EditorClip): Long {
    var start = 0L
    for (c in clips) {
        val speed = c.speed.takeIf { it > 0.0 } ?: 1.0
        val eff = EditorTrackDurations.effectiveMs(c.durationMs, speed)
        val end = start + eff
        if (c === clip || (c.mediaUri == clip.mediaUri && c.sourceStartMs == clip.sourceStartMs && c.durationMs == clip.durationMs)) {
            val localTimeline = (timelineMs - start).coerceAtLeast(0L)
            return (localTimeline * speed).toLong().coerceAtLeast(0L)
        }
        start = end
    }
    return 0L
}
