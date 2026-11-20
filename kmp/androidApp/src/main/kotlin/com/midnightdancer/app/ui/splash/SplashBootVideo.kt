package com.midnightdancer.app.ui.splash

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.midnightdancer.app.R
import java.util.concurrent.atomic.AtomicBoolean

internal const val SplashBootAspectRatio = 720f / 1280f

/**
 * C-292: cold-boot muted video (`splash_boot.mp4` = first half of user gemini clip).
 * C-302: display size ÷1.5 (≈66% width, FIT letterbox) — was edge-bleeding ZOOM fill.
 * No Skip. Plays once from 0; on end / error → [onFinished] after surface detach (C-53).
 */
@Composable
fun SplashBootHost(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val finishedOnce = remember { AtomicBoolean(false) }
    val session = remember { PlayerSession() }

    fun finishSafely() {
        if (!finishedOnce.compareAndSet(false, true)) return
        session.detachSurface()
        onFinished()
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.fromUri(
                    Uri.parse("android.resource://${context.packageName}/${R.raw.splash_boot}"),
                ),
            )
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f
            session.player = this
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                session.revealWhenSized()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    finishSafely()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                finishSafely()
            }
        }
        player.addListener(listener)
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            session.releaseOnce()
        }
    }

    // Size the actual video surface before its first frame. Scaling a full-screen SurfaceView
    // with graphicsLayer briefly used the wrong height while Media3 learned the source aspect.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val videoWidth = (maxWidth / 1.5f).coerceAtMost(maxHeight * SplashBootAspectRatio)
        AndroidView(
            modifier = Modifier.width(videoWidth).height(videoWidth / SplashBootAspectRatio),
            factory = { ctx ->
                val root = LayoutInflater.from(ctx).inflate(R.layout.splash_boot_host, null) as FrameLayout
                root.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                root.contentDescription = "splash_boot_host"

                val playerView = root.findViewById<PlayerView>(R.id.splash_boot_player)
                session.playerView = playerView
                configureInsetPlayerView(playerView)
                playerView.player = player

                root.findViewById<TextView>(R.id.splash_skip).visibility = View.GONE
                root.findViewById<TextView>(R.id.splash_loading).visibility = View.GONE

                root
            },
        )
    }
}

private fun configureInsetPlayerView(playerView: PlayerView) {
    playerView.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    playerView.alpha = 0f
    playerView.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
    playerView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    playerView.useController = false
    playerView.setKeepContentOnPlayerReset(true)
    val contentFrame = playerView.findViewById<AspectRatioFrameLayout>(
        androidx.media3.ui.R.id.exo_content_frame,
    )
    contentFrame?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    contentFrame?.setAspectRatio(SplashBootAspectRatio)
    val shutter = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_shutter)
    shutter?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    shutter?.visibility = View.GONE
}

private class PlayerSession {
    var player: ExoPlayer? = null
    var playerView: PlayerView? = null
    private val released = AtomicBoolean(false)

    fun revealWhenSized() {
        val view = playerView ?: return
        view.postOnAnimation {
            if (released.get() || view !== playerView) return@postOnAnimation
            if (view.width > 0 && view.height > 0 &&
                kotlin.math.abs(view.width.toFloat() / view.height - SplashBootAspectRatio) < 0.01f) {
                view.alpha = 1f
            } else {
                // A first codec frame may precede the final AndroidView layout; keep it black.
                view.postOnAnimation { if (!released.get()) revealWhenSized() }
            }
        }
    }

    fun detachSurface() {
        val p = player
        val view = playerView
        runCatching {
            p?.playWhenReady = false
            view?.player = null
        }
        view?.visibility = View.GONE
    }

    fun releaseOnce() {
        if (!released.compareAndSet(false, true)) return
        detachSurface()
        runCatching { player?.release() }
        player = null
        playerView = null
    }
}
