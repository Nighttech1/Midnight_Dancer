package com.midnightdancer.app.exchange

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.midnightdancer.core.data.db.CatalogDatabase
import com.midnightdancer.core.data.db.RoomCatalogRepository
import com.midnightdancer.core.data.db.getCatalogDatabase
import com.midnightdancer.core.data.exchange.AndroidDebugExchangeZipUseCase
import com.midnightdancer.core.data.exchange.MdanceContainer
import com.midnightdancer.core.data.files.DefaultMediaStorage
import com.midnightdancer.core.data.migrate.catalog.CatalogExporter
import com.midnightdancer.core.domain.entity.*
import com.midnightdancer.core.domain.exchange.*
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.usecase.*
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic cache + in-memory database only. Never uses application DI, global context binding or user stores. */
@RunWith(AndroidJUnit4::class)
class ArchiveInspectionDeviceTest {
    private lateinit var cache: File
    private lateinit var directory: File
    private lateinit var db: CatalogDatabase
    private lateinit var repository: RoomCatalogRepository
    private lateinit var exchange: ExchangeZipUseCase
    private val packageName = "com.midnightdancer.app"
    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        cache = context.cacheDir.canonicalFile
        directory = File(cache, "archive-inspection-device-${UUID.randomUUID()}").apply { check(mkdirs()) }
        db = getCatalogDatabase(Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java))
        repository = RoomCatalogRepository(db)
        exchange = AndroidDebugExchangeZipUseCase(
            CatalogExporter(repository), DefaultMediaStorage(directory.path),
            object : ImportSecureZip {
                override suspend fun import(archivePath: String, into: AppData, contentFilter: ImportContentFilter, stylePlan: StyleImportPlan): ImportSecureZipResult =
                    error("Inspection must never invoke import")
            }, repository, packageName,
            object : DeviceStorage {
                override fun freeBytes() = directory.usableSpace
                override fun totalBytes() = directory.totalSpace
            },
        )
    }
    @After fun cleanup() {
        db.close()
        check(directory.canonicalFile.parentFile == cache && directory.name.startsWith("archive-inspection-device-"))
        directory.deleteRecursively()
    }
    private fun archive(name: String, data: AppData): File {
        val zip = File(directory, "$name.zip")
        ZipOutputStream(zip.outputStream()).use {
            it.putNextEntry(ZipEntry("metadata.json")); it.write(DomainJson.encodeAppData(data).encodeToByteArray()); it.closeEntry()
        }
        return File(directory, "$name.dance").also { MdanceContainer.wrapZipFileToMdance(zip, it, packageName) }
    }
    private fun assertNoPlaintextTemps() {
        val inspectDir = File(directory, "MidnightDancer/exchange/inspection")
        assertTrue(inspectDir.listFiles().orEmpty().isEmpty())
    }
    @Test fun public_port_views_all_three_archive_lists_including_colliding_choreographies_without_changing_local_data() = runBlocking {
        val local = AppData(danceStyles = listOf(DanceStyle("s", "LOCAL", listOf(Move("move", "LOCAL element", "1")))))
        repository.save(local)
        val vault = File(directory, "choreography_vault_v1.json").apply { writeText("LOCAL vault sentinel") }
        val data = AppData(
            danceStyles = listOf(DanceStyle("s", "Archive style", listOf(Move("move", "Archive element", "1")))),
            songs = listOf(Song("song", "Archive song", "s", "1", "content://never-open-this", 1.0, 0)),
            choreographies = listOf(Choreography("same", "Archive legacy", "song", "s", endTime = 1.0)),
            settings = JsonObject(mapOf(ChoreographyExchangeBridge.METADATA_KEY to JsonPrimitive(
                """{"version":1,"items":[{"id":"same","title":"Archive editor","style":"s","snapshot":null}]}"""))),
        )
        val a = archive("A", data); val before = a.readBytes()
        val snapshot = exchange.inspectArchive(a.path)
        assertEquals(listOf("Archive element"), snapshot.elements.map { it.title })
        assertEquals(listOf("Archive song"), snapshot.songs.map { it.title })
        assertEquals(listOf("Archive legacy", "Archive editor"), snapshot.choreographies.map { it.title })
        assertEquals(2, snapshot.choreographies.map { it.id }.distinct().size)
        assertEquals(listOf("Archive style", "Archive style"), snapshot.choreographies.map { it.subtitle })
        val b = archive("B", AppData())
        val second = exchange.inspectArchive(b.path)
        assertTrue(second.elements.isEmpty() && second.songs.isEmpty() && second.choreographies.isEmpty())
        assertEquals(local, repository.load()); assertEquals("LOCAL vault sentinel", vault.readText())
        assertArrayEquals(before, a.readBytes()); assertNoPlaintextTemps()
    }
    @Test fun corrupt_missing_and_unsupported_editor_archives_are_errors_and_leave_no_plaintext_or_catalog_changes() = runBlocking {
        val before = repository.load()
        val unsupported = archive("unsupported", AppData(settings = JsonObject(mapOf(
            ChoreographyExchangeBridge.METADATA_KEY to JsonPrimitive("""{"version":2,"items":[]}""")))))
        assertTrue(runCatching { exchange.inspectArchive(unsupported.path) }.isFailure)
        val corrupted = archive("corrupt", AppData())
        val bytes = corrupted.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        corrupted.writeBytes(bytes)
        assertTrue(runCatching { exchange.inspectArchive(corrupted.path) }.isFailure)
        assertTrue(runCatching { exchange.inspectArchive(File(directory, "missing.dance").path) }.isFailure)
        assertArrayEquals(bytes, corrupted.readBytes()); assertEquals(before, repository.load()); assertNoPlaintextTemps()
    }
}
