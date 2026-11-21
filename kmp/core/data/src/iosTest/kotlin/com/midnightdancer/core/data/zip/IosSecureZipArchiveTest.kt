package com.midnightdancer.core.data.zip

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.exchange.StyleImportPlan
import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.usecase.ImportContentFilter
import com.midnightdancer.core.domain.usecase.ImportSecureZipResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.decodeBase64
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.*

@OptIn(ExperimentalForeignApi::class)
class IosSecureZipArchiveTest {
    private val fs = FileSystem.SYSTEM
    private val storage = object : DeviceStorage { override fun freeBytes() = 2L * 1024 * 1024 * 1024; override fun totalBytes() = freeBytes() }
    private val metadata = """{"danceStyles":[],"songs":[],"choreographies":[],"settings":{}}"""
    private fun fixture(block: (IosSecureZipArchive, Path) -> Unit) {
        val root = NSTemporaryDirectory().toPath() / ("md-ios-zip-" + NSUUID().UUIDString)
        fs.createDirectory(root, mustCreate = true)
        try { block(IosSecureZipArchive(root.toString()), root) } finally { fs.deleteRecursively(root) }
    }
    private fun write(path: Path, bytes: ByteArray) { fs.sink(path).buffer().use { it.write(bytes) } }
    private fun read(path: Path) = fs.source(path).buffer().use { it.readByteArray() }

    @Test fun stored_export_is_real_zip_and_stage_is_crc_checked_and_temporary() = fixture { archive, root ->
        write(root / "metadata-source", metadata.encodeToByteArray()); write(root / "media-source", byteArrayOf(1, 2, 3, 4))
        val zip = root / "transfer.zip"
        archive.writeStoredArchive(zip.toString(), listOf(IosZipSource("metadata.json", (root / "metadata-source").toString()), IosZipSource("videos/a.mp4", (root / "media-source").toString())))
        assertEquals(listOf("metadata.json", "videos/a.mp4"), archive.inspect(zip.toString()).map { it.name })
        assertEquals(metadata, archive.metadata(zip.toString()))
        val staged = archive.extractToNewDirectory(zip.toString(), storage)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), read(staged.resolvedPaths.getValue("videos/a.mp4").toPath()))
        val directory = staged.directory.toPath(); staged.close(); staged.close()
        assertFalse(fs.exists(directory)); assertTrue(fs.exists(zip)); assertTrue(fs.exists(root / "media-source"))
    }

    @Test fun android_style_deflate_descriptor_fixture_reuses_real_common_importer() = fixture { archive, root ->
        val zip = root / "android.zip"; write(zip, DEFLATE_DESCRIPTOR.decodeBase64()!!.toByteArray())
        assertTrue(archive.inspect(zip.toString()).all { it.flags and 8 != 0 })
        val importer = DefaultImportSecureZip(DefaultValidateSecureZip(IosZipArchiveInspector(archive), SecureZipWhitelist.MidnightDancerFullBackup), metadataReader = IosZipMetadataReader(archive))
        val result = runBlocking { importer.import(zip.toString(), AppData(), ImportContentFilter.All, StyleImportPlan.Empty) }
        assertIs<ImportSecureZipResult.Success>(result)
        val stage = archive.extractToNewDirectory(zip.toString(), storage)
        try { assertEquals("fixture-media-byte-content", read(stage.resolvedPaths.getValue("videos/a.mp4").toPath()).decodeToString()) } finally { stage.close() }
    }

    @Test fun traversal_and_symlink_archives_fail_before_any_extraction() = fixture { archive, root ->
        for ((name, bytes) in listOf("slip.zip" to ZIP_SLIP, "link.zip" to SYMLINK)) {
            val zip = root / name; write(zip, bytes.decodeBase64()!!.toByteArray())
            assertFails { archive.extractToNewDirectory(zip.toString(), storage) }
        }
        assertFalse(fs.exists(root / "exchange")); assertFalse(fs.exists(root / "escape.mp4"))
    }

    @Test fun cancellation_and_space_failure_leave_existing_archive_and_sources_intact() = fixture { archive, root ->
        val source = root / "source"; write(source, metadata.encodeToByteArray())
        val destination = root / "prior.zip"; write(destination, "previous-valid-output-sentinel".encodeToByteArray())
        val sources = listOf(IosZipSource("metadata.json", source.toString()))
        assertFails { archive.writeStoredArchive(destination.toString(), sources) { throw IllegalStateException("cancel-fixture") } }
        assertEquals("previous-valid-output-sentinel", read(destination).decodeToString())
        assertEquals(metadata, read(source).decodeToString())
        assertFalse(fs.list(root).any { it.name.startsWith(".zip-") })
        val zip = root / "android.zip"; write(zip, DEFLATE_DESCRIPTOR.decodeBase64()!!.toByteArray())
        val noSpace = object : DeviceStorage { override fun freeBytes() = 0L; override fun totalBytes() = 0L }
        assertFails { archive.extractToNewDirectory(zip.toString(), noSpace) }
        assertFalse(fs.exists(root / "exchange"))
        var calls = 0
        assertFails { archive.extractToNewDirectory(zip.toString(), storage) { if (++calls == 2) error("cancel-after-first-member") } }
        assertTrue(fs.list(root / "exchange" / "staging").isEmpty())
    }

    @Test fun crc_corruption_cleans_stage_and_plain_zip_cannot_masquerade_as_mdance() = fixture { archive, root ->
        val source = root / "source"; write(source, metadata.encodeToByteArray())
        assertFails { archive.writeStoredArchive((root / "bad.dance").toString(), listOf(IosZipSource("metadata.json", source.toString()))) }
        val zip = root / "corrupt.zip"
        archive.writeStoredArchive(zip.toString(), listOf(IosZipSource("metadata.json", source.toString())))
        val bytes = read(zip); bytes[30 + "metadata.json".length] = 'x'.code.toByte(); write(zip, bytes)
        assertFails { archive.extractToNewDirectory(zip.toString(), storage) }
        assertTrue(fs.list(root / "exchange" / "staging").isEmpty())
        assertEquals(metadata, read(source).decodeToString())
    }

    @Test fun advertised_bomb_and_local_central_name_disagreement_are_rejected_before_writes() = fixture { archive, root ->
        val fixture = DEFLATE_DESCRIPTOR.decodeBase64()!!.toByteArray()
        val oversized = fixture.copyOf()
        val central = (0..oversized.size - 46).first { at ->
            (oversized[at].toInt() and 255) == 0x50 && (oversized[at + 1].toInt() and 255) == 0x4b &&
                (oversized[at + 2].toInt() and 255) == 1 && (oversized[at + 3].toInt() and 255) == 2
        }
        val claimed = IosSecureZipArchive.MAX_ARCHIVE_BYTES + 1
        repeat(4) { oversized[central + 24 + it] = (claimed ushr (it * 8)).toByte() }
        val mismatch = fixture.copyOf().also { it[30] = 'x'.code.toByte() }
        for ((index, bytes) in listOf(oversized, mismatch).withIndex()) {
            val path = root / "rejected-$index.zip"; write(path, bytes)
            assertFails { archive.extractToNewDirectory(path.toString(), storage) }
        }
        assertFalse(fs.exists(root / "exchange"))
    }

    @Test fun canonical_equivalent_root_alias_can_write_and_cannot_overwrite_its_own_source() = fixture { _, root ->
        val actual = root / "private-directory"; fs.createDirectory(actual, mustCreate = true)
        val alias = root / "root-alias"; fs.createSymlink(alias, actual)
        val archive = IosSecureZipArchive(actual.toString())
        val metadataSource = root / "metadata-source"; write(metadataSource, metadata.encodeToByteArray())
        val output = alias / "export.zip"
        archive.writeStoredArchive(output.toString(), listOf(IosZipSource("metadata.json", metadataSource.toString())))
        assertEquals(metadata, archive.metadata((actual / "export.zip").toString()))
        assertEquals(metadata, read(metadataSource).decodeToString())
        val sourceAndOutput = actual / "same.zip"; write(sourceAndOutput, metadata.encodeToByteArray())
        assertFails { archive.writeStoredArchive((alias / "same.zip").toString(), listOf(IosZipSource("metadata.json", sourceAndOutput.toString()))) }
        assertEquals(metadata, read(sourceAndOutput).decodeToString())
    }

    @Test fun canonical_ancestor_outside_private_root_still_fails_before_creating_any_directory() = fixture { _, root ->
        val actual = root / "private-directory"; fs.createDirectory(actual, mustCreate = true)
        val outside = root / "outside-directory"; fs.createDirectory(outside, mustCreate = true)
        fs.createSymlink(actual / "escape", outside)
        val source = root / "metadata-source"; write(source, metadata.encodeToByteArray())
        val requested = actual / "escape" / "must-not-be-created" / "export.zip"
        assertFails { IosSecureZipArchive(actual.toString()).writeStoredArchive(requested.toString(), listOf(IosZipSource("metadata.json", source.toString()))) }
        assertFalse(fs.exists(outside / "must-not-be-created"))
        assertEquals(metadata, read(source).decodeToString())
    }

    private companion object {
        const val DEFLATE_DESCRIPTOR = "UEsDBBQACAAIAPKMdV0AAAAAAAAAAAAAAAANAAAAbWV0YWRhdGEuanNvbqtWSknMS04NLqnMSS1WsoqO1VEqzs9LhzKTM/KLUvPTixILMjLh0qklJZlgFdW1tQBQSwcI0DQABDEAAAA/AAAAUEsDBBQACAAIAPKMdV0AAAAAAAAAAAAAAAAMAAAAdmlkZW9zL2EubXA0S8usKCktStXNTU3JTNRNqixJ1U3OzytJzSsBAFBLBwh7K0lVHAAAABoAAABQSwECFAAUAAgACADyjHVd0DQABDEAAAA/AAAADQAAAAAAAAAAAAAAgAEAAAAAbWV0YWRhdGEuanNvblBLAQIUABQACAAIAPKMdV17K0lVHAAAABoAAAAMAAAAAAAAAAAAAACAAWwAAAB2aWRlb3MvYS5tcDRQSwUGAAAAAAIAAgB1AAAAwgAAAAAA"
        const val ZIP_SLIP = "UEsDBBQACAAIAPKMdV0AAAAAAAAAAAAAAAANAAAAbWV0YWRhdGEuanNvbqtWSknMS04NLqnMSS1WsoqO1VEqzs9LhzKTM/KLUvPTixILMjLh0qklJZlgFdW1tQBQSwcI0DQABDEAAAA/AAAAUEsDBBQACAAIAPKMdV0AAAAAAAAAAAAAAAANAAAALi4vZXNjYXBlLm1wNEvLrCgpLUrVzU1NyUzUTaosSdVNzs8rSc0rAQBQSwcIeytJVRwAAAAaAAAAUEsBAhQAFAAIAAgA8ox1XdA0AAQxAAAAPwAAAA0AAAAAAAAAAAAAAIABAAAAAG1ldGFkYXRhLmpzb25QSwECFAAUAAgACADyjHVdeytJVRwAAAAaAAAADQAAAAAAAAAAAAAAgAFsAAAALi4vZXNjYXBlLm1wNFBLBQYAAAAAAgACAHYAAADDAAAAAAA="
        const val SYMLINK = "UEsDBBQACAAIAPKMdV0AAAAAAAAAAAAAAAANAAAAbWV0YWRhdGEuanNvbqtWSknMS04NLqnMSS1WsoqO1VEqzs9LhzKTM/KLUvPTixILMjLh0qklJZlgFdW1tQBQSwcI0DQABDEAAAA/AAAAUEsDBBQACAAAAAAAIQAAAAAAAAAAAAAAAAAPAAAAdmlkZW9zL2xpbmsubXA0b3V0c2lkZVBLBwim/eq1BwAAAAcAAABQSwECFAAUAAgACADyjHVd0DQABDEAAAA/AAAADQAAAAAAAAAAAAAAgAEAAAAAbWV0YWRhdGEuanNvblBLAQIUAxQACAAAAAAAIQCm/eq1BwAAAAcAAAAPAAAAAAAAAAAAAAD/oWwAAAB2aWRlb3MvbGluay5tcDRQSwUGAAAAAAIAAgB4AAAAsAAAAAAA"
    }
}
