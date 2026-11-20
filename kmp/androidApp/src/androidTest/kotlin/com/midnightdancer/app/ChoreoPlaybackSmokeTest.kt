package com.midnightdancer.app

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Matrix group E (playback stress) — connected smoke test, headless, no human.
 *
 * Plays a 5-clip ExoPlayer playlist (the editor's engine) into an offscreen [ImageReader] surface
 * and asserts the DECODER actually rendered many frames and crossed clip seams, with no error.
 * renderedOutputBufferCount comes from the decoder, so it proves real playback regardless of the
 * emulator's display FPS — a freeze would show as the count not advancing.
 *
 * Run: ./gradlew :androidApp:connectedDebugAndroidTest (needs a device/emulator).
 */
@RunWith(AndroidJUnit4::class)
class ChoreoPlaybackSmokeTest {

    @OptIn(UnstableApi::class)
    @Test
    fun multiClipPlayback_decodesFramesAcrossSeams_noFreeze() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("android.resource://${ctx.packageName}/raw/splash_boot")

        val ht = HandlerThread("e-reader").apply { start() }
        val reader = ImageReader.newInstance(
            1280, 720, ImageFormat.PRIVATE, 4, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
        )
        // Drain frames so the decoder pipeline keeps flowing (no back-pressure stall).
        reader.setOnImageAvailableListener({ r -> runCatching { r.acquireLatestImage()?.close() } }, Handler(ht.looper))

        var player: ExoPlayer? = null
        var error: PlaybackException? = null
        instr.runOnMainSync {
            val p = ExoPlayer.Builder(ctx).build()
            p.setVideoSurface(reader.surface)
            p.volume = 0f
            p.setMediaItems((1..5).map { MediaItem.fromUri(uri) })
            p.addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) { error = e }
            })
            p.prepare()
            p.playWhenReady = true
            player = p
        }

        var maxRendered = 0L
        var maxIndex = 0
        // 24 * 500ms = 12s window — comfortably longer than one clip, so a seam is always crossed
        // even with slow startup buffering on a software-GPU emulator.
        repeat(24) {
            Thread.sleep(500)
            instr.runOnMainSync {
                val p = player ?: return@runOnMainSync
                val c = p.videoDecoderCounters
                c?.ensureUpdated()
                val r = (c?.renderedOutputBufferCount ?: 0).toLong()
                if (r > maxRendered) maxRendered = r
                if (p.currentMediaItemIndex > maxIndex) maxIndex = p.currentMediaItemIndex
            }
        }

        instr.runOnMainSync { player?.release() }
        reader.close()
        ht.quitSafely()

        android.util.Log.i("MdPlaybackQA", "androidTest E rendered=$maxRendered maxIndex=$maxIndex error=$error")
        assertTrue("playback error: $error", error == null)
        assertTrue("decoder rendered too few frames ($maxRendered) — likely frozen", maxRendered > 30)
        assertTrue("playlist never advanced past clip 0 — seam transition failed", maxIndex >= 1)
    }
}
