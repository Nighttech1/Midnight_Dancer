package com.midnightdancer.app.ui.splash

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.PaintDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.net.Uri
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.midnightdancer.app.R
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Boot video + Skip in the same Android View tree (C-53 / C-54 / C-56 / C-59).
 *
 * Full-bleed contract:
 * - PlayerView ZOOM + TextureView center-crop matrix (no FIT letterbox shelf)
 * - Skip is a TextView floating over video — never Material Button / painted bar
 * - Never pad/margin the video host for nav insets (only Skip/loading float up)
 */
@Composable
fun SplashBootHost(
    skipVisible: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val skipAction = remember { SkipAction() }
    skipAction.block = onSkip
    val session = remember { PlayerSession() }
    val shineHost = remember { ShineHost() }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.fromUri(
                    Uri.parse("android.resource://${context.packageName}/${R.raw.splash_boot}"),
                ),
            )
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
            session.player = this
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                session.videoWidth = videoSize.width
                session.videoHeight = videoSize.height
                session.applyCenterCrop()
            }

            override fun onRenderedFirstFrame() {
                session.applyCenterCrop()
                session.hideShutter()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            SplashSkipBridge.detachVideo = null
            shineHost.stop()
            session.releaseOnce()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val root = LayoutInflater.from(ctx).inflate(R.layout.splash_boot_host, null) as FrameLayout
            root.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            root.setBackgroundColor(Color.TRANSPARENT)
            root.setPadding(0, 0, 0, 0)
            // Root does not clip — Skip may sit near edges. PlayerView clips ZOOM overflow.
            root.clipToPadding = false
            root.clipChildren = false
            root.contentDescription = "splash_boot_host"

            val playerView = root.findViewById<PlayerView>(R.id.splash_boot_player)
            session.playerView = playerView
            configureFullBleedPlayerView(playerView)
            playerView.player = player
            // Re-assert ZOOM after player bind (Media3 may reset content frame).
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            forceContentFrameZoom(playerView)
            session.bindTextureView(playerView)
            session.applyCenterCrop()

            SplashSkipBridge.detachVideo = { session.detachSurface() }

            val skip = root.findViewById<TextView>(R.id.splash_skip)
            val loading = root.findViewById<TextView>(R.id.splash_loading)
            // Elevation casts a dark ambient shadow that Client reads as a black shelf.
            skip.elevation = 0f
            skip.translationZ = 0f
            skip.outlineAmbientShadowColor = Color.TRANSPARENT
            skip.outlineSpotShadowColor = Color.TRANSPARENT
            skip.typeface = androidx.core.content.res.ResourcesCompat.getFont(
                root.context,
                R.font.noto_serif_bold,
            ) ?: Typeface.create(Typeface.SERIF, Typeface.BOLD)
            loading.typeface = androidx.core.content.res.ResourcesCompat.getFont(
                root.context,
                R.font.noto_serif_regular,
            ) ?: Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            shineHost.attach(root, skip)
            skip.bringToFront()
            skip.setOnClickListener {
                session.detachSurface()
                skipAction.block()
            }
            skip.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                v.systemGestureExclusionRects = listOf(
                    Rect(0, 0, v.width.coerceAtLeast(1), v.height.coerceAtLeast(1)),
                )
            }

            // Float Skip above nav gesture area; NEVER pad the video host (that creates a shelf).
            ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
                val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                val density = root.resources.displayMetrics.density
                val floatGap = (56f * density).toInt()
                val bottom = nav.bottom + floatGap
                applyBottomMargin(skip, bottom)
                val loading = root.findViewById<TextView>(R.id.splash_loading)
                applyBottomMargin(loading, bottom + (92f * density).toInt())
                // Video must ignore insets — re-assert full-bleed after inset pass.
                forceContentFrameZoom(playerView)
                session.applyCenterCrop()
                insets
            }
            ViewCompat.requestApplyInsets(root)

            root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                session.applyCenterCrop()
            }

            root
        },
        update = { root ->
            val playerView = root.findViewById<PlayerView>(R.id.splash_boot_player)
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            forceContentFrameZoom(playerView)
            session.bindTextureView(playerView)
            session.applyCenterCrop()

            val loading = root.findViewById<TextView>(R.id.splash_loading)
            val skip = root.findViewById<TextView>(R.id.splash_skip)
            skip.elevation = 0f
            skip.translationZ = 0f
            skip.outlineAmbientShadowColor = Color.TRANSPARENT
            skip.outlineSpotShadowColor = Color.TRANSPARENT
            skip.typeface = androidx.core.content.res.ResourcesCompat.getFont(
                root.context,
                R.font.noto_serif_bold,
            ) ?: Typeface.create(Typeface.SERIF, Typeface.BOLD)
            loading.typeface = androidx.core.content.res.ResourcesCompat.getFont(
                root.context,
                R.font.noto_serif_regular,
            ) ?: Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            loading.visibility = if (skipVisible) View.GONE else View.VISIBLE
            if (skipVisible) {
                if (skip.visibility != View.VISIBLE) {
                    skip.alpha = 0f
                    skip.visibility = View.VISIBLE
                    skip.animate()
                        .alpha(1f)
                        .setDuration(700L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                    shineHost.start()
                }
            } else {
                skip.animate().cancel()
                skip.visibility = View.GONE
                shineHost.stop()
            }
        },
    )
}

/** Continuous white translucent shine sweep over Skip (C-68). */
private class ShineHost {
    private var overlay: View? = null
    private var animator: ValueAnimator? = null

    fun attach(root: FrameLayout, skip: TextView) {
        if (overlay != null) return
        val shine = View(root.context).apply {
            elevation = 0f
            translationZ = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
            visibility = View.GONE
            alpha = 0.55f
        }
        val lp = FrameLayout.LayoutParams(1, 1)
        root.addView(shine, lp)
        overlay = shine
        skip.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            syncOverlayToSkip(v)
        }
        syncOverlayToSkip(skip)
    }

    private fun syncOverlayToSkip(skip: View) {
        val shine = overlay ?: return
        val lp = shine.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.width = (skip.width * 0.28f).toInt().coerceAtLeast(1)
        lp.height = skip.height.coerceAtLeast(1)
        lp.gravity = android.view.Gravity.NO_GRAVITY
        lp.leftMargin = skip.left
        lp.topMargin = skip.top
        shine.layoutParams = lp
        shine.background = shineGradientDrawable()
        shine.clipToOutline = false
    }

    private fun shineGradientDrawable(): PaintDrawable {
        val pd = PaintDrawable()
        pd.shape = RectShape()
        pd.shaderFactory = object : ShapeDrawable.ShaderFactory() {
            override fun resize(width: Int, height: Int): Shader {
                return LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    0f,
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.argb(140, 255, 255, 255),
                        Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
        }
        return pd
    }

    fun start() {
        val shine = overlay ?: return
        shine.visibility = View.VISIBLE
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { anim ->
                val skip = (shine.parent as? ViewGroup)?.findViewById<TextView>(R.id.splash_skip)
                    ?: return@addUpdateListener
                val travel = (skip.width - shine.width).coerceAtLeast(0).toFloat()
                shine.translationX = travel * (anim.animatedValue as Float)
                shine.translationY = 0f
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
        overlay?.visibility = View.GONE
        overlay?.translationX = 0f
    }
}

private fun configureFullBleedPlayerView(playerView: PlayerView) {
    playerView.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    playerView.setShutterBackgroundColor(Color.TRANSPARENT)
    playerView.setBackgroundColor(Color.TRANSPARENT)
    playerView.useController = false
    playerView.setKeepContentOnPlayerReset(true)
    // Clip ZOOM overflow so cropped edges don't paint outside; still covers full view.
    playerView.clipChildren = true
    playerView.clipToPadding = true
    forceContentFrameZoom(playerView)
}

/**
 * AspectRatioFrameLayout FIT letterboxes; ZOOM crops.
 * Keep content frame MATCH_PARENT + ZOOM so the surface covers the window
 * including under system bars / Skip. TextureView center-crop matrix is the belt.
 */
private fun forceContentFrameZoom(playerView: PlayerView) {
    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    val contentFrame = playerView.findViewById<AspectRatioFrameLayout>(
        androidx.media3.ui.R.id.exo_content_frame,
    )
    if (contentFrame != null) {
        contentFrame.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        contentFrame.layoutParams = contentFrame.layoutParams?.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        } ?: FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        contentFrame.setBackgroundColor(Color.TRANSPARENT)
        contentFrame.clipChildren = true
    }
    val shutter = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_shutter)
    shutter?.setBackgroundColor(Color.TRANSPARENT)
    shutter?.visibility = View.GONE
}

private fun applyBottomMargin(view: View, bottomPx: Int) {
    val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return
    if (lp.bottomMargin != bottomPx) {
        lp.bottomMargin = bottomPx
        view.layoutParams = lp
    }
}

/**
 * True center-crop on TextureView: scale uniformly so both axes cover the view,
 * then translate to center. Eliminates FIT-style letterbox bars that read as a shelf.
 */
private fun applyTextureViewCenterCrop(
    textureView: TextureView,
    videoWidth: Int,
    videoHeight: Int,
) {
    val viewWidth = textureView.width
    val viewHeight = textureView.height
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return

    val scale = max(
        viewWidth.toFloat() / videoWidth.toFloat(),
        viewHeight.toFloat() / videoHeight.toFloat(),
    )
    val scaledW = videoWidth * scale
    val scaledH = videoHeight * scale
    val dx = (viewWidth - scaledW) / 2f
    val dy = (viewHeight - scaledH) / 2f

    val matrix = Matrix()
    // TextureView transform is relative to the view's own size.
    matrix.setScale(scaledW / viewWidth, scaledH / viewHeight)
    matrix.postTranslate(dx, dy)
    textureView.setTransform(matrix)
}

private fun findTextureView(root: View): TextureView? {
    if (root is TextureView) return root
    if (root is ViewGroup) {
        for (i in 0 until root.childCount) {
            val found = findTextureView(root.getChildAt(i))
            if (found != null) return found
        }
    }
    return null
}

private class SkipAction {
    var block: () -> Unit = {}
}

private class PlayerSession {
    var player: ExoPlayer? = null
    var playerView: PlayerView? = null
    var textureView: TextureView? = null
    var videoWidth: Int = 0
    var videoHeight: Int = 0
    private val released = AtomicBoolean(false)
    private var textureLayoutBound: TextureView? = null
    private val textureLayoutListener =
        View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyCenterCrop() }

    fun bindTextureView(playerView: PlayerView) {
        val tv = findTextureView(playerView) ?: return
        textureView = tv
        // TextureView forbids setBackground* — crash: "doesn't support displaying a background drawable".
        // Fill the content frame; crop is done via Matrix (and Media3 ZOOM).
        tv.layoutParams = tv.layoutParams?.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        if (textureLayoutBound !== tv) {
            textureLayoutBound?.removeOnLayoutChangeListener(textureLayoutListener)
            tv.addOnLayoutChangeListener(textureLayoutListener)
            textureLayoutBound = tv
        }
    }

    fun applyCenterCrop() {
        val tv = textureView ?: playerView?.let { findTextureView(it) } ?: return
        textureView = tv
        applyTextureViewCenterCrop(tv, videoWidth, videoHeight)
    }

    fun hideShutter() {
        val shutter = playerView?.findViewById<View>(androidx.media3.ui.R.id.exo_shutter)
        shutter?.visibility = View.GONE
        shutter?.setBackgroundColor(Color.TRANSPARENT)
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
        textureLayoutBound?.removeOnLayoutChangeListener(textureLayoutListener)
        textureLayoutBound = null
        detachSurface()
        runCatching { player?.release() }
        player = null
        playerView = null
        textureView = null
    }
}

internal object SplashSkipBridge {
    @Volatile
    var detachVideo: (() -> Unit)? = null
}
