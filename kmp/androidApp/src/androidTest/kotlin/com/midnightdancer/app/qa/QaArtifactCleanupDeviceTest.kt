package com.midnightdancer.app.qa

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.midnightdancer.app.BuildConfig
import com.midnightdancer.core.domain.usecase.LoadLibrary
import com.midnightdancer.core.domain.usecase.SaveAppData
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** One explicit cleanup of this wave's journaled disposable row, never a product-facing delete API. */
@RunWith(AndroidJUnit4::class)
class QaArtifactCleanupDeviceTest {
    @Test fun removeOnlyExactJournaledDisposableElementThroughProductionUseCase() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val id = arguments.getString("qa_cleanup_element_id")
        val expectedHash = arguments.getString("qa_cleanup_sha256")
        // A normal suite cannot delete anything; this utility requires an explicit owner invocation.
        assumeTrue(id != null && expectedHash != null)
        check(BuildConfig.DEBUG && id == "new-element-15")
        check(expectedHash!!.matches(Regex("[0-9a-f]{64}")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            val stages = listOf(Stage.PRE_ON_CREATE, Stage.CREATED, Stage.STARTED, Stage.RESUMED,
                Stage.PAUSED, Stage.STOPPED, Stage.RESTARTED)
            check(stages.all { monitor.getActivitiesInStage(it).isEmpty() }) { "cleanup_requires_cold_process" }
        }
        withContext(Dispatchers.IO) {
            val load = GlobalContext.get().get<LoadLibrary>()
            val save = GlobalContext.get().get<SaveAppData>()
            val before = load.load()
            val moves = before.danceStyles.flatMap { it.moves }
            val target = moves.singleOrNull { it.id == id } ?: return@withContext
            check(target.name == "MD-QA-16-video") { "refuse_non_fixture_title" }
            val reference = requireNotNull(target.videoUri)
            val uri = Uri.parse(reference)
            check(uri.scheme == null || uri.scheme == "file")
            val file = File(if (uri.scheme == "file") requireNotNull(uri.path) else reference).canonicalFile
            val pool = File(instrumentation.targetContext.filesDir, "MidnightDancer/owned_media").canonicalFile
            check(file.path.startsWith(pool.path + File.separator) && file.isFile) { "refuse_non_owned_fixture" }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val block = ByteArray(64 * 1024)
                while (true) { val n = input.read(block); if (n < 0) break; digest.update(block, 0, n) }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == expectedHash) { "refuse_changed_fixture_bytes" }
            save.deleteMove(requireNotNull(id))
            val after = load.load()
            val remaining = after.danceStyles.flatMap { it.moves }
            assertFalse(remaining.any { it.id == id })
            assertEquals(moves.filterNot { it.id == id }.associateBy { it.id }, remaining.associateBy { it.id })
            assertEquals(before.songs, after.songs)
            assertEquals(before.choreographies, after.choreographies)
            assertTrue(file.isFile) // Shared blobs, snapshots and archive bytes are never removed.
        }
        Unit
    }
}
