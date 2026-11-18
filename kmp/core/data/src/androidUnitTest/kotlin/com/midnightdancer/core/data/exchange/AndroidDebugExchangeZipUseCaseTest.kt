package com.midnightdancer.core.data.exchange

import android.content.Context
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import com.midnightdancer.core.data.db.bindCatalogDatabaseContext
import com.midnightdancer.core.data.db.CatalogDatabase
import com.midnightdancer.core.data.db.inMemoryCatalogDatabaseBuilder
import com.midnightdancer.core.data.db.di.inMemoryCatalogDatabaseModule
import com.midnightdancer.core.data.files.MediaStorage
import com.midnightdancer.core.data.files.bindMediaFilesContext
import com.midnightdancer.core.data.files.di.mediaFilesKoinModule
import com.midnightdancer.core.data.migrate.catalog.CatalogExporter
import com.midnightdancer.core.data.zip.AndroidZipMetadataReader
import com.midnightdancer.core.data.zip.SecureZipWhitelist
import com.midnightdancer.core.data.zip.di.secureZipKoinModule
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.entity.Song
import com.midnightdancer.core.domain.entity.Choreography
import com.midnightdancer.core.domain.exchange.ExportPackageSelection
import com.midnightdancer.core.domain.exchange.CatalogPickItem
import com.midnightdancer.core.domain.exchange.ChoreographyExchangeBridge
import com.midnightdancer.core.domain.exchange.EditorExchangePayload
import com.midnightdancer.core.domain.exchange.PreparedChoreographyImport
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.usecase.ExchangeZipResult
import com.midnightdancer.core.domain.usecase.ExchangeZipUseCase
import com.midnightdancer.core.domain.usecase.SaveAppData
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AndroidDebugExchangeZipUseCaseTest : KoinTest {

    private lateinit var database: CatalogDatabase
    private lateinit var roomJob: CompletableJob
    private lateinit var roomExecutor: ExecutorService
    private lateinit var roomDispatcher: ExecutorCoroutineDispatcher

    private val ampleStorage =
        object : DeviceStorage {
            override fun freeBytes(): Long = 50L * 1024L * 1024L * 1024L
            override fun totalBytes(): Long = 128L * 1024L * 1024L * 1024L
        }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        bindCatalogDatabaseContext(context)
        bindMediaFilesContext(context)
        // Own Room's async invalidation work as well as its connection. Otherwise stopKoin()
        // leaves refresh jobs running into the next Robolectric sandbox's SQLite reset.
        roomJob = SupervisorJob()
        roomExecutor = Executors.newSingleThreadExecutor()
        roomDispatcher = roomExecutor.asCoroutineDispatcher()
        database = inMemoryCatalogDatabaseBuilder("exchange_zip_test.db")
            .setQueryCoroutineContext(roomDispatcher + roomJob)
            .build()
        startKoin {
            modules(
                inMemoryCatalogDatabaseModule("exchange_zip_test.db"),
                mediaFilesKoinModule(),
                secureZipKoinModule(
                    metadataReader = AndroidZipMetadataReader(),
                    whitelist = SecureZipWhitelist.MidnightDancerFullBackup,
                ),
                module {
                    // Override only the fixture DB; production repository/use-case bindings stay real.
                    single<CatalogDatabase> { database }
                    single<DeviceStorage> { ampleStorage }
                    single<ExchangeZipUseCase> {
                        AndroidDebugExchangeZipUseCase(
                            catalogExporter = get(),
                            mediaStorage = get(),
                            importSecureZip = get(),
                            saveAppData = get(),
                            packageName = context.packageName,
                            deviceStorage = get(),
                            contentResolver = context.contentResolver,
                        )
                    }
                },
            )
        }
    }

    @After
    fun tearDown() {
        try {
            // Room creates its scope as a child of the supplied Job. Join its cleanup BEFORE
            // closing SQLite, rather than racing an in-flight refresh transaction with close().
            runBlocking { roomJob.cancelAndJoin() }
        } finally {
            try {
                database.close()
            } finally {
                stopKoin()
                roomDispatcher.close()
                assertTrue(roomExecutor.awaitTermination(10, TimeUnit.SECONDS), "Room fixture executor did not terminate")
            }
        }
    }

    @Test
    fun export_creates_real_zip_and_import_roundtrip() =
        runTest {
            val useCase = get<ExchangeZipUseCase>()
            val exporter = get<CatalogExporter>()
            val storage = get<MediaStorage>()
            storage.ensureLayout()
            val video = File(storage.videosDirectory(), "roundtrip-move.mp4").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val audio = File(storage.musicDirectory(), "roundtrip-song.mp3").apply { writeBytes(byteArrayOf(5, 6, 7)) }
            val data = AppData(
                danceStyles = listOf(DanceStyle("style", "Style", listOf(Move("roundtrip-move", "Move", "1", videoUri = video.path)))),
                songs = listOf(Song("roundtrip-song", "Song", "style", "1", "roundtrip-song.mp3", 20.0, 3)),
                choreographies = listOf(Choreography("roundtrip-choreo", "Dance", "roundtrip-song", "style", mapOf(0.0 to "style::roundtrip-move"), endTime = 20.0)),
            )
            get<SaveAppData>().save(data)

            // A choreography-only choice includes its referenced catalog media.
            val exported = useCase.export(ExportPackageSelection(choreographyIds = listOf("roundtrip-choreo")))
            val ok = assertIs<ExchangeZipResult.Ok>(exported, exported.toString())
            assertTrue(ok.archivePath.endsWith("debug_export.dance"), ok.archivePath)
            assertTrue(File(ok.archivePath).isFile)
            assertTrue(File(ok.archivePath).length() > 0L)
            assertTrue(MdanceContainer.isMdance(File(ok.archivePath)))

            video.delete()
            audio.delete()
            get<SaveAppData>().save(AppData())
            val imported = useCase.import(ok.archivePath)
            assertIs<ExchangeZipResult.Ok>(imported, imported.toString())
            val restored = exporter.exportAppData()
            assertEquals("roundtrip-choreo", restored.choreographies.single().id)
            assertEquals("roundtrip-song", restored.songs.single().id)
            assertEquals("roundtrip-move", restored.danceStyles.single().moves.single().id)
            assertContentEquals(byteArrayOf(1, 2, 3, 4), video.readBytes())
            assertContentEquals(byteArrayOf(5, 6, 7), audio.readBytes())
        }

    @Test
    fun explicit_missing_path_does_not_fall_back_to_previous_export() = runTest {
        val useCase = get<ExchangeZipUseCase>()
        val before = get<CatalogExporter>().exportAppData()
        val result = useCase.import("/missing/user-selected.dance")
        assertIs<ExchangeZipResult.Failed>(result)
        assertTrue(result.reasons.any { it.contains("selected_archive_not_found") })
        assertEquals(before, get<CatalogExporter>().exportAppData())
    }

    @Test
    fun editor_choreography_display_style_maps_to_catalog_id_without_duplicate_styles_or_payload_changes() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        get<SaveAppData>().save(AppData(danceStyles = listOf(DanceStyle("sensual", "Sensual"))))
        val originalRows = listOf(
            CatalogPickItem("editor-1", "Dance one", styleId = "Sensual"),
            CatalogPickItem("editor-2", "Dance two", styleId = "sEnSuAl"),
        )
        val originalPayload = """{"originalStyle":"Sensual"}"""
        val bridge = object : ChoreographyExchangeBridge {
            override fun catalog() = originalRows
            override fun export(ids: Set<String>) = EditorExchangePayload(originalPayload)
            override fun prepareImport(payloadJson: String, resolvedMedia: Map<String, String>, styleRedirect: Map<String, String>, legacyData: AppData): PreparedChoreographyImport = error("not used")
        }
        val useCase = AndroidDebugExchangeZipUseCase(get(), get(), get(), get(), context.packageName, ampleStorage,
            choreographyBridge = bridge)
        val picker = useCase.loadExportCatalog()
        assertEquals(listOf("editor-1", "editor-2"), picker.choreosForStyle("sensual").map { it.id })
        assertEquals(1, picker.styles.count { it.name.equals("Sensual", ignoreCase = true) })
        assertEquals("sensual", picker.styles.single { it.name.equals("Sensual", ignoreCase = true) }.id)
        assertEquals("Sensual", originalRows.first().styleId)
        assertEquals(originalPayload, bridge.export(setOf("editor-1")).payloadJson)
    }

    @Test
    fun selected_saf_song_and_video_export_exact_bytes_and_import_as_playable_new_local_refs() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = get<MediaStorage>().also { it.ensureLayout() }
        val songBytes = byteArrayOf(8, 6, 4, 2)
        val videoBytes = byteArrayOf(1, 3, 5)
        val songFile = File(context.cacheDir, "saf-selected-song.mp3").apply { writeBytes(songBytes) }
        val videoFile = File(context.cacheDir, "saf-selected-video.mp4").apply { writeBytes(videoBytes) }
        registerSelectedMediaProvider(context, mapOf(songFile.name to songFile, videoFile.name to videoFile))
        val songUri = "content://exchange.selected/${songFile.name}"
        val videoUri = "content://exchange.selected/${videoFile.name}"
        // A canonical same-ID file exists but is not the material that this catalog row plays.
        val wrongCanonical = File(storage.musicDirectory(), "picked-song.mp3").apply { writeBytes(byteArrayOf(99)) }
        val original = AppData(
            danceStyles = listOf(DanceStyle("s", "Style", listOf(Move("picked-move", "Chosen move", "1", videoUri = videoUri)))),
            songs = listOf(Song("picked-song", "Chosen song", "s", "1", songUri, 20.0, 0, playbackSpeed = 1.0477)),
        )
        get<SaveAppData>().save(original)
        val useCase = get<ExchangeZipUseCase>()
        val catalog = useCase.loadExportCatalog()
        assertEquals(songBytes.size.toLong(), catalog.songs.single().approximateBytes)
        assertEquals(videoBytes.size.toLong(), catalog.elements.single().approximateBytes)
        val result = useCase.export(ExportPackageSelection(moveIds = listOf("picked-move"), songIds = listOf("picked-song")))
        val ok = assertIs<ExchangeZipResult.Ok>(result, result.toString())
        assertEquals(2, ok.mediaFilesPacked)
        val inner = File(context.cacheDir, "selected-inspect.zip")
        MdanceContainer.unwrapMdanceToZipFile(File(ok.archivePath), inner, context.packageName)
        ZipFile(inner).use { zip ->
            assertContentEquals(songBytes, zip.getInputStream(zip.getEntry("music/picked-song.mp3")).use { it.readBytes() })
            assertContentEquals(videoBytes, zip.getInputStream(zip.getEntry("videos/picked-move.mp4")).use { it.readBytes() })
            val metadata = zip.getInputStream(zip.getEntry("metadata.json")).bufferedReader().use { DomainJson.decodeAppData(it.readText()) }
            assertEquals("picked-song.mp3", metadata.songs.single().fileName)
            assertEquals(1.0477, metadata.songs.single().playbackSpeed)
            assertTrue(!metadata.songs.single().fileName.startsWith("content:"))
        }
        assertEquals(original, get<CatalogExporter>().exportAppData()) // Export never rewrites the source catalog.
        get<SaveAppData>().save(AppData())
        wrongCanonical.delete() // Test-owned collision fixture; no production/user file is touched.
        val imported = useCase.import(ok.archivePath)
        assertIs<ExchangeZipResult.Ok>(imported, imported.toString())
        val restored = get<CatalogExporter>().exportAppData()
        assertContentEquals(songBytes, File(restored.songs.single().fileName).readBytes())
        assertContentEquals(videoBytes, File(restored.danceStyles.single().moves.single().videoUri!!).readBytes())
        assertContentEquals(songBytes, songFile.readBytes())
        assertContentEquals(videoBytes, videoFile.readBytes())
        inner.delete()
    }

    @Test
    fun legacy_basename_media_in_app_flutter_exports_without_moving_originals() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = get<MediaStorage>()
        val appRoot = File(storage.rootDirectory()).parentFile!!.parentFile!!
        val legacyDir = File(appRoot, "app_flutter/MidnightDancer/music").apply { mkdirs() }
        val originalName = "legacy-${java.util.UUID.randomUUID()}.mp3"
        val source = File(legacyDir, originalName).apply { writeBytes(byteArrayOf(7, 5, 3)) }
        get<SaveAppData>().save(AppData(songs = listOf(Song("legacy-source", "Legacy", "s", "1", originalName, 2.0, 0))))
        val result = get<ExchangeZipUseCase>().export(ExportPackageSelection(songIds = listOf("legacy-source")))
        val ok = assertIs<ExchangeZipResult.Ok>(result, result.toString())
        val inner = File(context.cacheDir, "legacy-inspect.zip")
        MdanceContainer.unwrapMdanceToZipFile(File(ok.archivePath), inner, context.packageName)
        ZipFile(inner).use { zip ->
            assertContentEquals(byteArrayOf(7, 5, 3), zip.getInputStream(zip.getEntry("music/legacy-source.mp3")).use { it.readBytes() })
        }
        assertContentEquals(byteArrayOf(7, 5, 3), source.readBytes())
        source.delete()
        inner.delete()
    }

    @Test
    fun export_failure_cleans_partial_files_and_preserves_previous_successful_archive() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val good = File(context.cacheDir, "export-good.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val empty = File(context.cacheDir, "export-empty.mp3").apply { writeBytes(byteArrayOf()) }
        val song = Song("cleanup-song", "Keep last good export", "s", "1", good.path, 2.0, 3)
        get<SaveAppData>().save(AppData(songs = listOf(song)))
        val useCase = get<ExchangeZipUseCase>()
        val first = useCase.export(ExportPackageSelection(songIds = listOf(song.id)))
        val success = assertIs<ExchangeZipResult.Ok>(first, first.toString())
        val previousBytes = File(success.archivePath).readBytes()
        get<SaveAppData>().save(AppData(songs = listOf(song.copy(fileName = empty.path))))
        val result = assertIs<ExchangeZipResult.Failed>(useCase.export(ExportPackageSelection(songIds = listOf(song.id))))
        assertTrue(result.reasons.any { it.contains("selected_media_empty") })
        assertContentEquals(previousBytes, File(success.archivePath).readBytes())
        val exchange = File(success.archivePath).parentFile!!
        assertTrue(!File(exchange, "debug_export.inner.zip.tmp").exists())
        assertTrue(exchange.listFiles().orEmpty().none { it.name.contains(".candidate.dance") })
    }

    @Test
    fun supported_aac_ogg_flac_archive_roundtrip_preserves_extension_and_bytes() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val useCase = get<ExchangeZipUseCase>()
        for (ext in listOf("aac", "ogg", "flac")) {
            val id = "format-${java.util.UUID.randomUUID()}"
            val bytes = byteArrayOf(4, 3, 2, 1)
            val source = File(context.cacheDir, "$id.$ext").apply { writeBytes(bytes) }
            get<SaveAppData>().save(AppData(songs = listOf(Song(id, "Format $ext", "s", "1", source.path, 2.0, bytes.size))))
            val result = useCase.export(ExportPackageSelection(songIds = listOf(id)))
            val ok = assertIs<ExchangeZipResult.Ok>(result, result.toString())
            get<SaveAppData>().save(AppData())
            val imported = useCase.import(ok.archivePath)
            assertIs<ExchangeZipResult.Ok>(imported, imported.toString())
            val restored = get<CatalogExporter>().exportAppData().songs.single()
            assertTrue(restored.fileName.endsWith(".$ext"))
            assertContentEquals(bytes, File(restored.fileName).readBytes())
            assertContentEquals(bytes, source.readBytes())
        }
    }

    private fun registerSelectedMediaProvider(context: Context, sources: Map<String, File>) {
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri): String = if (uri.lastPathSegment.orEmpty().endsWith("mp4")) "video/mp4" else "audio/mpeg"
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(
                requireNotNull(sources[uri.lastPathSegment]), ParcelFileDescriptor.MODE_READ_ONLY,
            )
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): MatrixCursor {
                val file = requireNotNull(sources[uri.lastPathSegment])
                val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
                return MatrixCursor(columns).apply {
                    addRow(columns.map { if (it == OpenableColumns.SIZE) file.length() else file.name })
                }
            }
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        provider.attachInfo(context, ProviderInfo().apply { authority = "exchange.selected" })
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("exchange.selected", provider)
    }

    @Test
    fun saf_selected_archive_is_imported_instead_of_a_stale_fallback() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = android.net.Uri.parse("content://test.sets/selected.dance")
        val metadata = DomainJson.encodeAppData(AppData(danceStyles = listOf(DanceStyle("saf-style", "Selected SAF"))))
        val bytes = java.io.ByteArrayOutputStream().also { buffer ->
            ZipOutputStream(buffer).use { zip ->
                zip.putNextEntry(ZipEntry("metadata.json"))
                zip.write(metadata.encodeToByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(uri, java.io.ByteArrayInputStream(bytes))
        get<SaveAppData>().save(AppData())
        val result = get<ExchangeZipUseCase>().import(uri.toString())
        assertIs<ExchangeZipResult.Ok>(result)
        assertEquals("Selected SAF", get<CatalogExporter>().exportAppData().danceStyles.single().name)
    }

    @Test
    fun failing_vault_commit_rolls_back_catalog_and_new_media() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = get<MediaStorage>()
        storage.ensureLayout()
        val old = AppData(danceStyles = listOf(DanceStyle("keep", "Keep me")))
        get<SaveAppData>().save(old)
        val mediaName = "rollback-${java.util.UUID.randomUUID()}.mp3"
        val finalMedia = File(storage.musicDirectory(), mediaName)
        val incoming = AppData(
            songs = listOf(Song(mediaName.substringBeforeLast('.'), "New", "s", "1", mediaName, 2.0, 3)),
            settings = kotlinx.serialization.json.JsonObject(mapOf(ChoreographyExchangeBridge.METADATA_KEY to kotlinx.serialization.json.JsonPrimitive("{}"))),
        )
        val archive = File(context.cacheDir, "rollback.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("metadata.json")); zip.write(DomainJson.encodeAppData(incoming).encodeToByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("music/$mediaName")); zip.write(byteArrayOf(7, 8, 9)); zip.closeEntry()
        }
        var rolledBack = false
        val bridge = object : ChoreographyExchangeBridge {
            override fun catalog() = emptyList<CatalogPickItem>()
            override fun export(ids: Set<String>) = EditorExchangePayload("{}")
            override fun prepareImport(payloadJson: String, resolvedMedia: Map<String, String>, styleRedirect: Map<String, String>, legacyData: AppData) = object : PreparedChoreographyImport {
                override fun commit(): Unit = error("vault write failed")
                override fun rollback() { rolledBack = true }
            }
        }
        val useCase = AndroidDebugExchangeZipUseCase(get(), storage, get(), get(), context.packageName, ampleStorage,
            contentResolver = context.contentResolver, choreographyBridge = bridge)
        assertIs<ExchangeZipResult.Failed>(useCase.import(archive.absolutePath))
        assertEquals(old, get<CatalogExporter>().exportAppData())
        assertTrue(rolledBack)
        assertTrue(!finalMedia.exists())
    }

    @Test
    fun cancellation_immediately_after_catalog_save_restores_metadata_before_removing_media() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = get<MediaStorage>()
        storage.ensureLayout()
        val before = AppData(danceStyles = listOf(DanceStyle("keep-cancel", "Keep before cancellation")))
        val actualSave = get<SaveAppData>()
        actualSave.save(before)
        val songId = "cancel-${java.util.UUID.randomUUID()}"
        val incoming = AppData(songs = listOf(Song(songId, "New", "s", "1", "$songId.mp3", 2.0, 2)))
        val archive = File(context.cacheDir, "cancel-import.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(DomainJson.encodeAppData(incoming).encodeToByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("music/$songId.mp3"))
            zip.write(byteArrayOf(1, 2))
            zip.closeEntry()
        }
        var cancelOnce = true
        val cancellingSave = object : SaveAppData by actualSave {
            override suspend fun save(data: AppData) {
                actualSave.save(data)
                if (cancelOnce && data.songs.any { it.id == songId }) {
                    cancelOnce = false
                    currentCoroutineContext().cancel()
                }
            }
        }
        val useCase = AndroidDebugExchangeZipUseCase(get(), storage, get(), cancellingSave, context.packageName, ampleStorage)
        val importJob = launch { useCase.import(archive.absolutePath) }
        importJob.join()
        assertTrue(importJob.isCancelled)
        assertEquals(before, get<CatalogExporter>().exportAppData())
        assertTrue(!File(storage.musicDirectory(), "$songId.mp3").exists())
    }

    @Test
    fun failed_catalog_rollback_keeps_committed_media_for_recovery() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = get<MediaStorage>()
        storage.ensureLayout()
        val before = AppData()
        val actualSave = get<SaveAppData>()
        actualSave.save(before)
        val id = "recovery-${java.util.UUID.randomUUID()}"
        val incoming = AppData(
            songs = listOf(Song(id, "Recovery", "s", "1", "$id.mp3", 2.0, 2)),
            settings = kotlinx.serialization.json.JsonObject(mapOf(ChoreographyExchangeBridge.METADATA_KEY to kotlinx.serialization.json.JsonPrimitive("{}"))),
        )
        val archive = File(context.cacheDir, "recovery-import.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("metadata.json")); zip.write(DomainJson.encodeAppData(incoming).encodeToByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("music/$id.mp3")); zip.write(byteArrayOf(1, 2)); zip.closeEntry()
        }
        val failingRollbackSave = object : SaveAppData by actualSave {
            override suspend fun save(data: AppData) {
                if (data == before) error("catalog recovery unavailable")
                actualSave.save(data)
            }
        }
        val bridge = object : ChoreographyExchangeBridge {
            override fun catalog() = emptyList<CatalogPickItem>()
            override fun export(ids: Set<String>) = EditorExchangePayload("{}")
            override fun prepareImport(payloadJson: String, resolvedMedia: Map<String, String>, styleRedirect: Map<String, String>, legacyData: AppData) = object : PreparedChoreographyImport {
                override fun commit(): Unit = error("vault unavailable")
                override fun rollback() = Unit
            }
        }
        val useCase = AndroidDebugExchangeZipUseCase(get(), storage, get(), failingRollbackSave, context.packageName, ampleStorage,
            choreographyBridge = bridge)
        val result = assertIs<ExchangeZipResult.Failed>(useCase.import(archive.absolutePath))
        assertTrue(result.reasons.any { it.contains("import_recovery_required") })
        assertTrue(get<CatalogExporter>().exportAppData().songs.any { it.id == id })
        assertContentEquals(byteArrayOf(1, 2), File(storage.musicDirectory(), "$id.mp3").readBytes())
    }

    @Test
    fun import_corrupt_zip_rejects_without_throw() =
        runTest {
            val useCase = get<ExchangeZipUseCase>()
            get<SaveAppData>().save(AppData())

            val exchangeDir =
                File(get<MediaStorage>().rootDirectory(), ExchangeZipLimits.EXCHANGE_DIR)
            exchangeDir.mkdirs()
            val corrupt = File(exchangeDir, ExchangeZipLimits.IMPORT_INBOX_FILE_NAME)
            corrupt.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04))

            val result = useCase.import(corrupt.absolutePath)
            val failed = assertIs<ExchangeZipResult.Failed>(result)
            assertTrue(
                failed.reasons.any { it.contains("cannot_list_archive") } ||
                    failed.message.contains("reject", ignoreCase = true) ||
                    failed.message.contains("fail", ignoreCase = true),
                failed.toString(),
            )
        }

    @Test
    fun import_zip_missing_metadata_rejected() =
        runTest {
            val useCase = get<ExchangeZipUseCase>()
            get<SaveAppData>().save(AppData())
            val exchangeDir =
                File(get<MediaStorage>().rootDirectory(), ExchangeZipLimits.EXCHANGE_DIR)
            exchangeDir.mkdirs()
            val bad = File(exchangeDir, "no_meta.zip")
            ZipOutputStream(bad.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("readme.txt"))
                zos.write("nope".encodeToByteArray())
                zos.closeEntry()
            }

            val result = useCase.import(bad.absolutePath)
            val failed = assertIs<ExchangeZipResult.Failed>(result)
            assertTrue(failed.reasons.isNotEmpty(), failed.toString())
        }
}
