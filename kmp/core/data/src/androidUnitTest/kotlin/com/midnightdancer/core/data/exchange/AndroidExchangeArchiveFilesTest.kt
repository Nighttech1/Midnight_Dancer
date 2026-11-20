package com.midnightdancer.core.data.exchange

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.midnightdancer.core.domain.storage.DeviceStorage
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AndroidExchangeArchiveFilesTest {
    private lateinit var root: File
    private val space = object : DeviceStorage {
        override fun freeBytes() = 10L * 1024 * 1024 * 1024
        override fun totalBytes() = freeBytes()
    }
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = File(context.cacheDir, "archive-test-${UUID.randomUUID()}").apply { mkdirs() }
    }
    @After fun cleanup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator))
        root.deleteRecursively()
    }
    private fun archive(vararg entries: Pair<String, ByteArray>): File = File(root, "input.zip").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    @Test fun staged_media_does_not_touch_existing_files_and_rolls_back_only_new_files() {
        val old = File(root, "music/existing.mp3").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        val input = archive("music/existing.mp3" to byteArrayOf(1), "videos/new.mp4" to byteArrayOf(2, 3))
        val staged = AndroidExchangeArchiveFiles(root, space, null).stage(input)
        assertFalse(File(root, "videos/new.mp4").exists())
        staged.commit()
        assertArrayEquals(byteArrayOf(1), old.readBytes())
        assertArrayEquals(byteArrayOf(2, 3), File(root, "videos/new.mp4").readBytes())
        staged.rollback()
        staged.close()
        assertFalse(File(root, "videos/new.mp4").exists())
        assertArrayEquals(byteArrayOf(1), old.readBytes())
    }

    @Test fun traversal_is_rejected_without_writing_outside_sandbox() {
        val input = archive("../escape.mp4" to byteArrayOf(1, 2))
        assertTrue(runCatching { AndroidExchangeArchiveFiles(root, space, null).stage(input) }.isFailure)
        assertFalse(File(root.parentFile, "escape.mp4").exists())
    }

    @Test fun excessive_metadata_rejected_before_decoding_or_catalog_writes() {
        val oversized = ByteArray(2 * 1024 * 1024 + 1) { 32 }
        val input = archive("metadata.json" to oversized)
        assertTrue(runCatching { AndroidExchangeArchiveFiles(root, space, null).stage(input) }.isFailure)
        assertTrue(runCatching { AndroidExchangeArchiveFiles(root, space, null).readMetadataForPeek(input) }.isFailure)
    }

    @Test fun forged_editor_digest_is_rejected_before_any_commit() {
        val falseHash = "0".repeat(64)
        val input = archive("videos/editor-$falseHash.mp4" to byteArrayOf(1, 2, 3))
        val failure = runCatching { AndroidExchangeArchiveFiles(root, space, null).stage(input) }.exceptionOrNull()
        assertTrue(failure?.toString() ?: "stage unexpectedly succeeded", failure?.message.orEmpty().contains("editor_media_hash_mismatch"))
        assertFalse(File(root, "videos/editor-$falseHash.mp4").exists())
    }

    @Test fun same_id_with_different_bytes_rejects_whole_import_preserving_original_media() {
        val old = File(root, "music/shared-id.mp3").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2)) }
        val input = archive("music/shared-id.mp3" to byteArrayOf(9, 8), "videos/new.mp4" to byteArrayOf(3))
        val failure = runCatching { AndroidExchangeArchiveFiles(root, space, null).stage(input) }.exceptionOrNull()
        assertTrue(failure?.toString() ?: "stage unexpectedly succeeded", failure?.message.orEmpty().contains("media_conflict"))
        assertArrayEquals(byteArrayOf(1, 2), old.readBytes())
        assertFalse(File(root, "videos/new.mp4").exists())
    }
}
