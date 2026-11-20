package com.midnightdancer.app.audio

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midnightdancer.feature.choreography.logic.ChoreographySnapshot
import com.midnightdancer.feature.choreography.logic.EditorClip
import com.midnightdancer.feature.trainer.logic.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sin

/** Real Media3/audio-device checks; uses disposable fixtures, never edits the user's library. */
@RunWith(AndroidJUnit4::class)
class RehearsalPlaybackDeviceTest {
    private fun tone(context: Context): File {
        val rate = 8_000
        val count = rate * 6
        val b = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + count * 2).put("WAVEfmt ".toByteArray())
        b.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(count * 2)
        repeat(count) { b.putShort((sin(it * 2 * Math.PI * 440 / rate) * 1200).toInt().toShort()) }
        return File.createTempFile("rehearsal-qa-", ".wav", context.cacheDir).apply { writeBytes(b.array()) }
    }

    @Test fun twentyRealAudioTransitionsRespectBoundsAndNeverAddExtraRest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = tone(context)
        try {
            withContext(Dispatchers.Main) {
                val actual = AndroidRehearsalPlayback(context)
                val starts = mutableListOf<Long>()
                val boundaries = mutableListOf<Long>()
                var measuring = false
                val playback = object : RehearsalPlaybackPort by actual {
                    override suspend fun playAndAwaitStarted() {
                        actual.playAndAwaitStarted()
                        starts += actual.positionMs()
                        measuring = true
                    }
                    override fun pause() {
                        if (measuring) {
                            boundaries += actual.positionMs()
                            measuring = false
                        }
                        actual.pause()
                    }
                }
                val labels = mutableListOf<String>()
                val voice = object : RehearsalVoicePort {
                    override fun speak(label: String) { labels += label }
                    override fun stop() = Unit
                }
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                val plan = requireNotNull(RehearsalPlanBuilder.fromSnapshot("qa", ChoreographySnapshot(
                    "QA", "Test", elementClips = listOf(EditorClip("X", 2000), EditorClip("Y", 4000)),
                    musicClips = listOf(EditorClip("tone", 6000, file.absolutePath)), musicVolume = 0.05f,
                )).plan)
                val controller = RehearsalController(scope, playback, voice, RehearsalEntitlementPort { RehearsalAccess.Granted })
                try {
                    controller.start(RehearsalSettings("qa", plan.revision, 1000, 2000, 21, 0, 0), plan)
                    withTimeout(70_000) {
                        while (controller.state.value.phase !in setOf(RehearsalPhase.Completed, RehearsalPhase.Error)) delay(20)
                    }
                    assertEquals(controller.state.value.error, RehearsalPhase.Completed, controller.state.value.phase)
                    assertEquals(21, starts.size)
                    assertEquals(21, boundaries.size)
                    assertEquals(21, labels.size)
                    assertTrue(labels.all { it == "X" }) // Y begins on exclusive B and must never speak.
                    val startErrors = starts.map { kotlin.math.abs(it - 1000) }
                    val endErrors = boundaries.map { kotlin.math.abs(it - 2000) }
                    Log.i("MdRehearsalQA", "DEVICE_21_PASSES startErrorsMs=$startErrors endErrorsMs=$endErrors")
                    assertTrue("Start errors: $startErrors", startErrors.max() <= 150)
                    assertTrue("End errors: $endErrors", endErrors.max() <= 150)
                } finally {
                    controller.stop(); scope.cancel(); actual.release()
                }
            }
        } finally { file.delete() }
    }

    @Test fun missingSourceFailsBeforeAnyAudioPass() = runBlocking {
        withContext(Dispatchers.Main) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val actual = AndroidRehearsalPlayback(context)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val plan = RehearsalPlan("missing", "v1", 2000,
                listOf(RehearsalMusicClip(File(context.cacheDir, "not-existing-qa.wav").absolutePath, 0, 0, 2000)), emptyList())
            val controller = RehearsalController(scope, actual, object : RehearsalVoicePort {
                override fun speak(label: String) { fail("No cue before material readiness") }
                override fun stop() = Unit
            }, RehearsalEntitlementPort { RehearsalAccess.Granted })
            try {
                controller.start(RehearsalSettings("missing", "v1", 0, 2000, 1, 0, 0, false), plan)
                withTimeout(10_000) { while (controller.state.value.phase != RehearsalPhase.Error) delay(20) }
                assertEquals(0, controller.state.value.pass)
            } finally { controller.stop(); scope.cancel(); actual.release() }
        }
    }

    @Test fun transientAudioFocusRequiresExplicitResume() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = tone(context)
        try {
            withContext(Dispatchers.Main) {
                val actual = AndroidRehearsalPlayback(context)
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                val plan = requireNotNull(RehearsalPlanBuilder.fromSnapshot("focus", ChoreographySnapshot(
                    "Focus", "Test", musicClips = listOf(EditorClip("tone", 6000, file.absolutePath)), musicVolume = 0.05f,
                )).plan)
                val controller = RehearsalController(scope, actual, object : RehearsalVoicePort {
                    override fun speak(label: String) = Unit
                    override fun stop() = Unit
                }, RehearsalEntitlementPort { RehearsalAccess.Granted })
                actual.onInterrupted = controller::pause
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION).build())
                    .setOnAudioFocusChangeListener { }.build()
                try {
                    controller.start(RehearsalSettings("focus", plan.revision, 0, 5000, 1, 0, 0, false), plan)
                    withTimeout(15_000) { while (controller.state.value.phase != RehearsalPhase.Playing) delay(20) }
                    assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audioManager.requestAudioFocus(request))
                    withTimeout(3000) { while (controller.state.value.phase != RehearsalPhase.Paused) delay(20) }
                    audioManager.abandonAudioFocusRequest(request)
                    delay(400)
                    assertEquals(RehearsalPhase.Paused, controller.state.value.phase)
                    assertEquals(1, controller.state.value.pass)
                    controller.resume()
                    withTimeout(3000) { while (controller.state.value.phase != RehearsalPhase.Playing || controller.state.value.isWaitingForPlayer) delay(20) }
                    assertEquals(1, controller.state.value.pass)
                } finally {
                    audioManager.abandonAudioFocusRequest(request)
                    controller.stop(); scope.cancel(); actual.release()
                }
            }
        } finally { file.delete() }
    }
}
