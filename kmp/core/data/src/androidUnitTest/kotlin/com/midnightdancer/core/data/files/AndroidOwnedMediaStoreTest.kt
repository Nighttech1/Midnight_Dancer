package com.midnightdancer.core.data.files

import android.content.Context
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.midnightdancer.core.domain.media.*
import com.midnightdancer.core.domain.storage.DeviceStorage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows

/** IO contracts use injected format validation; actual codec validation/playback is in OwnedMediaDeviceTest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AndroidOwnedMediaStoreTest {
    private lateinit var directory: File
    private lateinit var context: Context
    private var free = 10L * 1024 * 1024 * 1024
    private val space = object : DeviceStorage {
        override fun freeBytes() = free
        override fun totalBytes() = 10L * 1024 * 1024 * 1024
    }
    private val validator = OwnedMediaValidator { file, _ ->
        require(file.length() > 4)
        if (!file.inputStream().use { it.readNBytes(4) }.contentEquals("RIFF".encodeToByteArray()))
            throw OwnedMediaException(OwnedMediaFailure.Unsupported)
        "wav"
    }
    @Before fun setup() {
        directory = Files.createTempDirectory("owned-media-unit-").toFile()
        val base = ApplicationProvider.getApplicationContext<Context>()
        context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(directory, "private").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
        }
        free = space.totalBytes()
    }
    @After fun cleanup() { check(directory.name.startsWith("owned-media-unit-")); directory.deleteRecursively() }
    private fun store() = AndroidOwnedMediaStore(context, space, minFreeBytes = 1024, validator = validator)
    private fun bytes(seed: Int = 1, size: Int = 500000) = ByteArray(size) { (it * 13 + seed).toByte() }.also {
        "RIFF".encodeToByteArray().copyInto(it)
    }
    private fun source(name: String, bytes: ByteArray) = File(directory, name).apply { writeBytes(bytes) }
    private fun assertNoStaging() = assertTrue(File(context.filesDir, "MidnightDancer/owned_media/v1/_staging").listFiles().orEmpty().isEmpty())

    @Test fun equal_bytes_different_names_extensions_and_concurrent_imports_have_one_blob_without_source_deletion() = runBlocking {
        val original = bytes(); val a = source("A.mp3", original); val b = source("B.wav", original)
        val first = store(); val otherInstance = store()
        val results = coroutineScope { listOf(async { first.retain(a.path, OwnedMediaKind.Audio) }, async { otherInstance.retain(b.path, OwnedMediaKind.Audio) }).awaitAll() }
        assertEquals(1, results.map { it.reference }.distinct().size)
        assertEquals(1, results.count { !it.reused })
        assertContentEquals(original, File(results.first().reference).readBytes())
        val stats = first.inspect(); assertEquals(1, stats.poolFiles); assertEquals(original.size.toLong(), stats.poolBytes)
        assertContentEquals(original, a.readBytes()); assertContentEquals(original, b.readBytes())
        val changed = source("C.wav", bytes(2)); val new = first.retain(changed.path, OwnedMediaKind.Audio)
        assertNotEquals(results.first().sha256, new.sha256); assertEquals(2, first.inspect().poolFiles); assertNoStaging()
    }

    @Test fun successful_new_retain_also_protects_legacy_snapshot_ref_before_original_deletion() = runBlocking {
        val file = source("existing-element.wav", bytes()); val s = store()
        val saved = s.retain(file.path, OwnedMediaKind.Audio)
        assertEquals(saved.reference, s.resolveReference(file.path))
        assertTrue(file.delete())
        val restarted = store(); restarted.initialize()
        assertEquals(saved.reference, restarted.resolveReference(file.path))
        assertContentEquals(bytes(), File(restarted.resolveReference(file.path)).readBytes())
        assertEquals(1, restarted.inspect().poolFiles); assertEquals(1, restarted.inspect().aliasCount)
    }

    @Test fun adopted_alias_survives_source_deletion_restart_and_uri_reuse_but_new_retain_reads_current_bytes() = runBlocking {
        val file = source("legacy.wav", bytes(1)); val original = file.readBytes(); val first = store()
        val protected = first.adopt(file.path, OwnedMediaKind.Audio)
        assertEquals(protected.reference, first.resolveReference(file.path))
        file.writeBytes(bytes(2))
        val currentPick = first.retain(file.path, OwnedMediaKind.Audio)
        assertNotEquals(protected.sha256, currentPick.sha256)
        assertEquals(protected.reference, first.adopt(file.path, OwnedMediaKind.Audio).reference)
        assertEquals(protected.reference, first.resolveReference(file.path))
        assertTrue(file.delete())
        val restarted = store(); restarted.initialize()
        assertEquals(protected.reference, restarted.resolveReference(file.path))
        assertContentEquals(original, File(protected.reference).readBytes())
        assertEquals(1, restarted.inspect().aliasCount); assertEquals(2, restarted.inspect().poolFiles)
        assertNoStaging()
    }

    @Test fun same_content_uri_new_pick_reads_new_bytes_but_legacy_adoption_remains_first_version() = runBlocking {
        val uri = Uri.parse("content://owned.test/reused-uri")
        val oldBytes = bytes(1); val newBytes = bytes(2); val s = store()
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(oldBytes))
        val old = s.adopt(uri.toString(), OwnedMediaKind.Audio)
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(newBytes))
        val new = s.retain(uri.toString(), OwnedMediaKind.Audio)
        assertNotEquals(old.reference, new.reference)
        assertContentEquals(newBytes, File(new.reference).readBytes())
        assertEquals(old.reference, s.resolveReference(uri.toString()))
        assertContentEquals(oldBytes, File(s.resolveReference(uri.toString())).readBytes())
        assertEquals(2, s.inspect().poolFiles); assertNoStaging()
    }

    @Test fun mapped_missing_blob_never_falls_back_to_changed_original_and_existing_durable_import_is_not_copied() = runBlocking {
        val external = source("original.wav", bytes()); val s = store(); val owned = s.adopt(external.path, OwnedMediaKind.Audio)
        assertTrue(File(owned.reference).delete()); external.writeBytes(bytes(5))
        assertEquals(owned.reference, s.resolveReference(external.path))
        val failure = runCatching { s.adopt(external.path, OwnedMediaKind.Audio) }.exceptionOrNull()
        assertIs<OwnedMediaException>(failure); assertEquals(OwnedMediaFailure.Unavailable, failure.reason)
        val imported = File(context.filesDir, "MidnightDancer/exchange/imported_media/token/music/old.wav").apply { parentFile!!.mkdirs(); writeBytes(bytes(8)) }
        val retained = s.retain(imported.path, OwnedMediaKind.Audio)
        assertEquals(imported.canonicalPath, retained.reference); assertTrue(retained.reused)
        assertTrue(s.isDurableOwnedReference(imported.path)); assertFalse(s.isDurableOwnedReference(external.path))
        val cache = File(context.cacheDir, "preview.wav").apply { writeBytes(bytes(9)) }
        assertFalse(s.isDurableOwnedReference(cache.path)); assertNoStaging()
    }

    @Test fun unknown_size_content_source_is_copied_and_read_failure_cleans_only_its_temporary() = runBlocking {
        val data = bytes(); val uri = Uri.parse("content://owned.test/unknown")
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(data))
        val s = store(); val owned = s.retain(uri.toString(), OwnedMediaKind.Audio)
        assertContentEquals(data, File(owned.reference).readBytes())
        val failureUri = Uri.parse("content://owned.test/failure")
        val input = object : ByteArrayInputStream(bytes(2)) {
            var reads = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (++reads == 3) throw IOException("synthetic provider read failure")
                return super.read(buffer, offset, length)
            }
        }
        Shadows.shadowOf(context.contentResolver).registerInputStream(failureUri, input)
        assertIs<OwnedMediaException>(runCatching { s.retain(failureUri.toString(), OwnedMediaKind.Audio) }.exceptionOrNull())
        assertEquals(1, s.inspect().poolFiles); assertContentEquals(data, File(owned.reference).readBytes()); assertNoStaging()
    }

    @Test fun known_content_length_early_eof_is_rejected_before_blob_alias_or_metadata_success() = runBlocking {
        val original = bytes(size = 400000); val truncated = source("truncated.wav", original.copyOf(200000))
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "audio/wav"
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(truncated, ParcelFileDescriptor.MODE_READ_ONLY)
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): MatrixCursor =
                MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply { addRow(arrayOf(original.size.toLong())) }
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        provider.attachInfo(context, ProviderInfo().apply { authority = "owned.earlyeof" })
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("owned.earlyeof", provider)
        val s = store(); val reference = "content://owned.earlyeof/truncated"
        val failure = assertIs<OwnedMediaException>(runCatching { s.retain(reference, OwnedMediaKind.Audio) }.exceptionOrNull())
        assertEquals(OwnedMediaFailure.Unavailable, failure.reason)
        assertEquals(0, s.inspect().poolFiles); assertEquals(0, s.inspect().aliasCount)
        assertEquals(reference, s.resolveReference(reference))
        assertContentEquals(original.copyOf(200000), truncated.readBytes()); assertNoStaging()
    }

    @Test fun transient_files_dir_media_are_copied_while_permanent_imports_are_reused() = runBlocking {
        val s = store()
        for ((index, relative) in listOf("MidnightDancer/exchange/staging/token/music/staged.wav",
            "MidnightDancer/exchange/inspection/temporary.wav", "cache/preview.wav").withIndex()) {
            val transient = File(context.filesDir, relative).apply { parentFile!!.mkdirs(); writeBytes(bytes(index)) }
            assertFalse(s.isDurableOwnedReference(transient.path))
            val saved = s.retain(transient.path, OwnedMediaKind.Audio)
            assertNotEquals(transient.canonicalPath, saved.reference)
            assertTrue(transient.delete()); assertContentEquals(bytes(index), File(saved.reference).readBytes())
        }
        val permanent = File(context.filesDir, "MidnightDancer/music/imported.wav").apply { parentFile!!.mkdirs(); writeBytes(bytes(7)) }
        assertTrue(s.isDurableOwnedReference(permanent.path))
        val result = s.retain(permanent.path, OwnedMediaKind.Audio)
        assertEquals(permanent.canonicalPath, result.reference); assertTrue(result.reused)
        assertEquals(3, s.inspect().poolFiles); assertNoStaging()
    }

    @Test fun cancellation_and_rolling_low_space_do_not_publish_partial_or_delete_a_successful_blob() = runBlocking {
        val file = source("large.wav", bytes(size = 2 * 1024 * 1024)); val s = store()
        val cancelled = async {
            val job = currentCoroutineContext().job
            s.retain(file.path, OwnedMediaKind.Audio) { if (it.copiedBytes > 0) job.cancel() }
        }
        assertIs<CancellationException>(runCatching { cancelled.await() }.exceptionOrNull())
        assertEquals(0, s.inspect().poolFiles); assertTrue(file.exists()); assertNoStaging()
        val failure = runCatching { s.retain(file.path, OwnedMediaKind.Audio) { if (it.copiedBytes > 0) free = 0 } }.exceptionOrNull()
        assertIs<OwnedMediaException>(failure); assertEquals(OwnedMediaFailure.NoSpace, failure.reason)
        assertEquals(0, s.inspect().poolFiles); assertNoStaging()
        free = space.totalBytes(); val good = s.retain(file.path, OwnedMediaKind.Audio)
        assertEquals(file.length(), File(good.reference).length()); assertEquals(1, s.inspect().poolFiles)
    }

    @Test fun corrupt_existing_digest_is_not_overwritten_or_accepted_as_a_duplicate() = runBlocking {
        val file = source("source.wav", bytes()); val s = store(); val owned = s.retain(file.path, OwnedMediaKind.Audio)
        val blob = File(owned.reference); val changed = bytes(8); blob.writeBytes(changed)
        val direct = runCatching { s.retain(blob.path, OwnedMediaKind.Audio) }.exceptionOrNull()
        assertEquals(OwnedMediaFailure.CorruptOwned, assertIs<OwnedMediaException>(direct).reason)
        val duplicate = runCatching { s.retain(file.path, OwnedMediaKind.Audio) }.exceptionOrNull()
        assertEquals(OwnedMediaFailure.CorruptOwned, assertIs<OwnedMediaException>(duplicate).reason)
        assertContentEquals(changed, blob.readBytes()); assertContentEquals(bytes(), file.readBytes()); assertNoStaging()
    }

    @Test fun alias_commit_low_space_preserves_previous_atomic_map_and_loaded_aliases() = runBlocking {
        val a = source("a.wav", bytes(1)); val b = source("b.wav", bytes(2)); val s = store()
        val first = s.adopt(a.path, OwnedMediaKind.Audio)
        val registry = File(context.filesDir, "owned_media_aliases_v1.json"); val previous = registry.readBytes()
        val failure = runCatching { s.adopt(b.path, OwnedMediaKind.Audio) { if (it.phase == OwnedMediaPhase.Publishing) free = 0 } }.exceptionOrNull()
        assertEquals(OwnedMediaFailure.NoSpace, assertIs<OwnedMediaException>(failure).reason)
        assertContentEquals(previous, registry.readBytes()); assertEquals(first.reference, s.resolveReference(a.path))
        assertEquals(b.path, s.resolveReference(b.path)); assertTrue(a.exists() && b.exists()); assertNoStaging()
        free = space.totalBytes(); val restarted = store(); restarted.initialize()
        assertEquals(first.reference, restarted.resolveReference(a.path)); assertEquals(1, restarted.inspect().aliasCount)
    }

    @Test fun corrupt_alias_registry_fails_closed_without_resetting_metadata() = runBlocking {
        val registry = File(context.filesDir, "owned_media_aliases_v1.json").apply { writeText("{\"version\":99,\"aliases\":{}}") }
        val previous = registry.readBytes(); val bad = store()
        val failure = assertIs<OwnedMediaException>(runCatching { bad.initialize() }.exceptionOrNull())
        assertEquals(OwnedMediaFailure.CorruptOwned, failure.reason); assertContentEquals(previous, registry.readBytes())
        assertContentEquals(previous, registry.readBytes())
    }
}
