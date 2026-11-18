package com.midnightdancer.core.data.exchange

import com.midnightdancer.core.domain.entity.*
import com.midnightdancer.core.domain.exchange.ChoreographyExchangeBridge
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.storage.DeviceStorage
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class AndroidArchiveInspectorTest {
    private lateinit var root: File
    private val packageName = "com.midnightdancer.app"
    private val storage = object : DeviceStorage {
        override fun freeBytes() = 10L * 1024 * 1024 * 1024
        override fun totalBytes() = freeBytes()
    }
    @Before fun setup() { root = Files.createTempDirectory("archive-inspection-unit-").toFile() }
    @After fun cleanup() { check(root.name.startsWith("archive-inspection-unit-")); root.deleteRecursively() }
    private fun inspector() = AndroidArchiveInspector(root, storage, null, packageName)
    private fun data(editor: String? = """{"version":1,"items":[{"id":"same","title":"Editor exact","style":"Archive sensual","snapshot":{"elementClips":[{"label":"Nested clip"}],"musicClips":[]}}]}""") = AppData(
        danceStyles = listOf(DanceStyle("s", "Archive sensual", moves = listOf(Move("duplicate", "Archive element", "Beginner"))),
            DanceStyle("b", "Archive bachata", moves = listOf(Move("duplicate", "Other style element", "Beginner")))),
        songs = listOf(Song("song", "Archive song", "s", "Beginner", "content://never-read-private-source", 1.0, 0)),
        choreographies = listOf(Choreography("same", "Legacy exact", "song", "b", endTime = 1.0)),
        settings = JsonObject(editor?.let { mapOf(ChoreographyExchangeBridge.METADATA_KEY to JsonPrimitive(it)) }.orEmpty()),
    )
    private fun zip(name: String, metadata: String, mediaBytes: Int = 0): File = File(root, name).also { file ->
        ZipOutputStream(file.outputStream()).use { out ->
            out.setLevel(0)
            out.putNextEntry(ZipEntry("metadata.json")); out.write(metadata.encodeToByteArray()); out.closeEntry()
            if (mediaBytes > 0) {
                out.putNextEntry(ZipEntry("videos/synthetic.mp4"))
                val block = ByteArray(64 * 1024) { (it * 17).toByte() }
                repeat(mediaBytes / block.size) { out.write(block) }; out.closeEntry()
            }
        }
    }
    private fun seal(source: File) = File(root, source.name + ".dance").also { MdanceContainer.wrapZipFileToMdance(source, it, packageName) }
    private fun assertClean() { assertTrue(File(root, "exchange/inspection").listFiles().orEmpty().isEmpty(), "Plaintext inspection temp leaked") }

    @Test fun encrypted_exact_projection_keeps_all_three_types_archive_styles_and_collision_documents_without_catalog_mutation() {
        val local = File(root, "metadata.json").apply { writeText("LOCAL sentinel with conflicting titles") }
        val vault = File(root, "choreography_vault_v1.json").apply { writeText("LOCAL vault sentinel") }
        val source = seal(zip("A.zip", DomainJson.encodeAppData(data())))
        val bytes = source.readBytes()
        val snapshot = inspector().inspect(source.absolutePath)
        assertEquals(listOf("Archive element", "Other style element"), snapshot.elements.map { it.title })
        assertEquals(listOf("Archive song"), snapshot.songs.map { it.title })
        assertEquals(listOf("Legacy exact", "Editor exact"), snapshot.choreographies.map { it.title })
        assertEquals(listOf("Archive bachata", "Archive sensual"), snapshot.choreographies.map { it.subtitle })
        assertEquals(2, snapshot.choreographies.map { it.id }.distinct().size)
        assertEquals(2, snapshot.elements.map { it.id }.distinct().size)
        assertFalse(snapshot.elements.any { it.title == "Nested clip" })
        assertEquals("LOCAL sentinel with conflicting titles", local.readText()); assertEquals("LOCAL vault sentinel", vault.readText())
        assertContentEquals(bytes, source.readBytes()); assertClean()
    }

    @Test fun successive_A_B_archives_and_editor_only_document_never_inherit_previous_or_local_rows() {
        val a = zip("A.zip", DomainJson.encodeAppData(data(editor = null)))
        val editorOnly = data().copy(danceStyles = emptyList(), songs = emptyList(), choreographies = emptyList())
        val b = seal(zip("B.zip", DomainJson.encodeAppData(editorOnly)))
        assertEquals(2, inspector().inspect(a.absolutePath).elements.size)
        val second = inspector().inspect(b.absolutePath)
        assertTrue(second.elements.isEmpty() && second.songs.isEmpty())
        assertEquals(listOf("Editor exact"), second.choreographies.map { it.title }); assertClean()
    }

    @Test fun missing_path_corrupt_container_and_unsupported_or_malformed_editor_payload_fail_honestly() {
        for (path in listOf("", File(root, "missing.dance").path, "relative.dance")) {
            assertTrue(runCatching { inspector().inspect(path) }.isFailure, path)
        }
        for (editor in listOf("""{"version":2,"items":[]}""", """{"version":1,"items":[{}]}""", "not json")) {
            val source = seal(zip("bad.zip", DomainJson.encodeAppData(data(editor))))
            assertTrue(runCatching { inspector().inspect(source.path) }.isFailure, editor); assertClean()
        }
        val valid = seal(zip("valid.zip", DomainJson.encodeAppData(data())))
        val bytes = valid.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        val corrupt = File(root, "corrupt.dance").apply { writeBytes(bytes) }
        assertTrue(runCatching { inspector().inspect(corrupt.path) }.isFailure); assertClean()
        assertContentEquals(bytes, corrupt.readBytes())
    }

    @Test fun metadata_bomb_duplicate_normalized_path_and_missing_metadata_are_rejected_without_media_extraction() {
        val oversized = zip("oversized.zip", " ".repeat(2 * 1024 * 1024 + 1))
        assertTrue(runCatching { inspector().inspect(oversized.path) }.exceptionOrNull()?.message.orEmpty().contains("metadata_too_large"))
        val duplicate = File(root, "duplicate.zip")
        ZipOutputStream(duplicate.outputStream()).use { out ->
            for (path in listOf("metadata.json", " metadata.json ")) {
                out.putNextEntry(ZipEntry(path)); out.write("{}".encodeToByteArray()); out.closeEntry()
            }
        }
        assertTrue(runCatching { inspector().inspect(duplicate.path) }.exceptionOrNull()?.message.orEmpty().contains("duplicate_archive_entry"))
        val missing = File(root, "empty.zip").also { ZipOutputStream(it.outputStream()).close() }
        assertTrue(runCatching { inspector().inspect(missing.path) }.isFailure)
        assertFalse(File(root, "videos").exists()); assertFalse(File(root, "music").exists()); assertClean()
    }

    @Test fun cancellation_during_authenticated_decrypt_propagates_and_cleans_all_private_plaintext() {
        val source = seal(zip("large.zip", DomainJson.encodeAppData(data()), mediaBytes = 2 * 1024 * 1024))
        val before = source.readBytes(); var checks = 0
        val failure = runCatching { inspector().inspect(source.path) {
            if (++checks == 10) throw CancellationException("synthetic cancellation")
        } }.exceptionOrNull()
        assertIs<CancellationException>(failure)
        assertEquals(10, checks); assertContentEquals(before, source.readBytes()); assertClean()
        assertEquals(2, inspector().inspect(source.path).choreographies.size); assertClean()
    }
}
