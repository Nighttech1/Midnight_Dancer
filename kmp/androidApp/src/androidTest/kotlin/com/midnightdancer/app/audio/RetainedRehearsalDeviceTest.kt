package com.midnightdancer.app.audio

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midnightdancer.app.access.ProductAccessRuntime
import com.midnightdancer.app.ui.trainer.*
import com.midnightdancer.core.tts.TrainerVoice
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

/** Real player + Android ViewModelStore; isolated fixture and in-memory settings only. */
@RunWith(AndroidJUnit4::class)
class RetainedRehearsalDeviceTest {
    @Test fun recreatedUiOwnerKeepsPausedAudioUntilManualResumeAndSourceReplacementClosesOldSession() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rate = 8000; val count = rate * 10
        val bytes = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + count * 2).put("WAVEfmt ".toByteArray())
        bytes.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(count * 2)
        repeat(count) { bytes.putShort((sin(it * 2 * Math.PI * 440 / rate) * 1000).toInt().toShort()) }
        val file = File.createTempFile("retained-rehearsal-qa-", ".wav", context.cacheDir).apply { writeBytes(bytes.array()) }
        try {
            withContext(Dispatchers.Main) {
                val previousGrant = ProductAccessRuntime.state.value.testPro
                ProductAccessRuntime.setTestPro(true)
                val store = ViewModelStore()
                val writeEntered = CompletableDeferred<Unit>()
                val writePermit = CompletableDeferred<Unit>()
                var writes = 0
                var persisted: RehearsalSettings? = null
                val repository = object : RehearsalSettingsRepository {
                    override suspend fun read(choreographyId: String): RehearsalSettings? = persisted
                    override suspend fun write(settings: RehearsalSettings) {
                        writeEntered.complete(Unit)
                        writePermit.await()
                        persisted = settings
                        writes++
                    }
                    override suspend fun delete(choreographyId: String) = Unit
                }
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = RetainedRehearsalViewModel(context, repository) as T
                }
                fun owner() = object : ViewModelStoreOwner { override val viewModelStore = store }
                val plan = requireNotNull(RehearsalPlanBuilder.fromSnapshot("qa-retained", ChoreographySnapshot(
                    "QA", "Test", musicClips = listOf(EditorClip("tone", 10000, file.absolutePath)), musicVolume = 0.02f,
                )).plan)
                val key = RehearsalRuntimeKey(plan.choreographyId, RehearsalPlanResult(plan), TrainerVoice.Ruslan)
                val settings = RehearsalSettings(plan.choreographyId, plan.revision, 1000, 9000, 2, 0, 0, false)
                persisted = settings.copy(endMs = 7000, totalPasses = 3)
                suspend fun playing(session: RetainedRehearsalSession) {
                    withTimeout(15000) {
                        while (session.controller.state.value.phase != RehearsalPhase.Playing || session.controller.state.value.isWaitingForPlayer) {
                            check(session.controller.state.value.phase != RehearsalPhase.Error) { session.controller.state.value.error.orEmpty() }
                            delay(10)
                        }
                    }
                }
                var latest: RetainedRehearsalSession? = null
                try {
                    val firstModel = ViewModelProvider(owner(), factory).get("trainer-part-session", RetainedRehearsalViewModel::class.java)
                    val first = firstModel.obtain(key); latest = first
                    first.start(settings, plan)
                    withTimeout(15000) { writeEntered.await() }
                    first.pauseForUiLeave()
                    assertEquals(RehearsalPhase.Paused, first.controller.state.value.phase)
                    val position = first.controller.state.value.positionMs
                    // A new UI owner/provider models pager composition destruction and recreation.
                    val nextModel = ViewModelProvider(owner(), factory).get("trainer-part-session", RetainedRehearsalViewModel::class.java)
                    assertSame(firstModel, nextModel)
                    val returned = nextModel.obtain(key)
                    assertSame(first, returned)
                    assertEquals(0, writes) // UI recreated before asynchronous persistence completes.
                    assertEquals(settings, returned.configuration)
                    assertNotEquals(repository.read(plan.choreographyId), returned.configuration)
                    writePermit.complete(Unit)
                    delay(300)
                    assertEquals(RehearsalPhase.Paused, returned.controller.state.value.phase)
                    assertEquals(position, returned.controller.state.value.positionMs)
                    returned.controller.resume(); playing(returned)
                    withTimeout(3000) { while (returned.controller.state.value.positionMs <= position + 100) delay(10) }
                    // Same ID/revision but changed playable result must replace, not reuse old volume/source.
                    val changedPlan = plan.copy(musicVolume = 0.03f)
                    val changed = nextModel.obtain(key.copy(result = RehearsalPlanResult(changedPlan))); latest = changed
                    assertNotSame(first, changed)
                    assertEquals(RehearsalPhase.Ready, first.controller.state.value.phase)
                    assertNull(changed.configuration)
                    changed.start(settings, changedPlan); playing(changed)
                    first.pauseForUiLeave() // Late disposal of old UI must never pause the new handle.
                    delay(100)
                    assertEquals(RehearsalPhase.Playing, changed.controller.state.value.phase)
                    store.clear()
                    assertEquals(RehearsalPhase.Ready, changed.controller.state.value.phase)
                    changed.pauseForUiLeave() // Closed handle is safe and never restarts.
                } finally {
                    store.clear()
                    latest?.pauseForUiLeave()
                    ProductAccessRuntime.setTestPro(previousGrant)
                }
            }
        } finally { check(file.canonicalFile.parentFile == context.cacheDir.canonicalFile); file.delete() }
    }
}
