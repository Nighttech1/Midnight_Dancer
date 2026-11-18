package com.midnightdancer.app.media

import android.content.Context
import android.content.ContextWrapper
import android.graphics.ImageFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.midnightdancer.app.exchange.AndroidChoreographyExchangeBridge
import com.midnightdancer.app.ui.media.*
import com.midnightdancer.app.audio.ownedMediaSourceFactory
import com.midnightdancer.core.data.db.CatalogDatabase
import com.midnightdancer.core.data.db.RoomCatalogRepository
import com.midnightdancer.core.data.db.getCatalogDatabase
import com.midnightdancer.core.data.exchange.AndroidDebugExchangeZipUseCase
import com.midnightdancer.core.data.files.AndroidOwnedMediaStore
import com.midnightdancer.core.data.files.DefaultMediaStorage
import com.midnightdancer.core.data.zip.*
import com.midnightdancer.core.data.migrate.catalog.CatalogExporter
import com.midnightdancer.core.domain.entity.*
import com.midnightdancer.core.domain.exchange.*
import com.midnightdancer.core.domain.media.*
import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.usecase.*
import com.midnightdancer.feature.choreography.logic.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** All original deletion is restricted to synthetic copies in this test's unique cache sandbox. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class OwnedMediaDeviceTest {
    private lateinit var base: Context
    private lateinit var sandbox: File
    private lateinit var isolated: Context
    private val storage = object : DeviceStorage {
        override fun freeBytes() = sandbox.usableSpace
        override fun totalBytes() = sandbox.totalSpace
    }
    @Before fun setup() {
        base = InstrumentationRegistry.getInstrumentation().targetContext
        sandbox = File(base.cacheDir, "owned-media-device-${UUID.randomUUID()}").apply { check(mkdirs()) }
        isolated = object : ContextWrapper(base) {
            override fun getFilesDir() = File(sandbox, "files").apply { mkdirs() }
            override fun getCacheDir() = File(sandbox, "cache").apply { mkdirs() }
        }
    }
    @After fun cleanup() {
        check(sandbox.canonicalFile.parentFile == base.cacheDir.canonicalFile && sandbox.name.startsWith("owned-media-device-"))
        sandbox.deleteRecursively()
    }
    private fun videoSource(): File {
        val resource = base.resources.getIdentifier("splash_boot", "raw", base.packageName)
        check(resource != 0) { "Bundled MP4 test fixture missing" }
        return File(sandbox, "synthetic-original.mp4").also { file ->
            base.resources.openRawResource(resource).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
    }
    private fun audioSource(): File {
        val frames = 16000
        val buffer = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + frames * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(frames * 2)
        repeat(frames) { buffer.putShort(((it % 100) * 12).toShort()) }
        return File(sandbox, "synthetic-original.wav").apply { writeBytes(buffer.array()) }
    }
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val block = ByteArray(65536); while (true) { val n = input.read(block); if (n < 0) break; digest.update(block, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private suspend fun playTen(reference: String, video: Boolean,
        resolver: ((String) -> String)? = null, beforePlaying: (suspend () -> Unit)? = null) {
        val thread = if (video) HandlerThread("owned-media-fixture-surface").apply { start() } else null
        val reader = if (video) ImageReader.newInstance(640, 360, ImageFormat.PRIVATE, 4, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE) else null
        reader?.setOnImageAvailableListener({ r -> runCatching { r.acquireLatestImage()?.close() } }, Handler(thread!!.looper))
        var error: PlaybackException? = null
        var rendered = 0
        var player: ExoPlayer? = null
        // Capture every old file:// URI before adoption, but attach later repeats only after deletion
        // in the dynamic resolver test so a fully prebuffered playlist cannot fake the result.
        val items = List(10) { MediaItem.Builder().setUri(Uri.fromFile(File(reference)))
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0).setEndPositionMs(200).build()).build() }
        try {
            withContext(Dispatchers.Main) {
                val builder = ExoPlayer.Builder(isolated)
                if (resolver != null) builder.setMediaSourceFactory(ownedMediaSourceFactory(isolated, resolver))
                player = builder.build().also { p ->
                    reader?.let { p.setVideoSurface(it.surface) }
                    p.volume = 0f
                    p.setMediaItems(if (beforePlaying != null) items.take(1) else items)
                    p.addListener(object : Player.Listener { override fun onPlayerError(e: PlaybackException) { error = e } })
                    p.prepare(); p.playWhenReady = beforePlaying == null
                }
            }
            if (beforePlaying != null) {
                withTimeout(15000) {
                    while (!withContext(Dispatchers.Main) { error?.let { throw AssertionError("Fixture preparation failed", it) }; player!!.playbackState == Player.STATE_READY }) delay(20)
                }
                beforePlaying()
                withContext(Dispatchers.Main) { player!!.addMediaItems(items.drop(1)); player!!.play() }
            }
            withTimeout(30000) {
                while (true) {
                    val finished = withContext(Dispatchers.Main) {
                        error?.let { throw AssertionError("Synthetic owned playback failed", it) }
                        val p = player!!
                        p.videoDecoderCounters?.let { it.ensureUpdated(); rendered = maxOf(rendered, it.renderedOutputBufferCount) }
                        p.currentMediaItemIndex == 9 && p.playbackState == Player.STATE_ENDED
                    }
                    if (finished) break
                    delay(30)
                }
            }
            if (video) assertTrue("Video must actually decode frames after original deletion", rendered > 10)
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { player?.release() }
            reader?.close(); thread?.quitSafely()
        }
    }

    @Test fun deleted_synthetic_video_and_audio_survive_restart_ten_timeline_refs_native_decode_and_exchange() = runBlocking {
        val video = videoSource(); val audio = audioSource()
        val expectedVideo = hash(video); val expectedAudio = hash(audio)
        val first = AndroidOwnedMediaStore(isolated, storage)
        val ownedVideo = first.adopt(video.path, OwnedMediaKind.Video)
        val ownedAudio = first.adopt(audio.path, OwnedMediaKind.Audio)
        assertEquals(expectedVideo, ownedVideo.sha256); assertEquals(expectedAudio, ownedAudio.sha256)
        assertTrue(video.delete()); assertTrue(audio.delete()) // These are our sandbox copies, not phone originals.
        val reopened = AndroidOwnedMediaStore(isolated, storage); reopened.initialize()
        assertEquals(ownedVideo.reference, reopened.resolveReference(video.path))
        assertEquals(ownedAudio.reference, reopened.resolveReference(audio.path))
        assertEquals(expectedVideo, hash(File(ownedVideo.reference))); assertEquals(expectedAudio, hash(File(ownedAudio.reference)))
        val elements = List(10) { EditorClip("Repeated video $it", 200, video.path) }
        val music = List(10) { EditorClip("Repeated music $it", 200, audio.path) }
        val item = ChoreographyVaultItem("qa-owned", "Owned repeated fixture", VaultStatus.Ready, "Owned style",
            bpm = 120, durationLabel = "00:02", trackName = "Owned audio", chrId = "qa-owned",
            snapshot = ChoreographySnapshot("Owned repeated fixture", "Owned style", elements, music))
        val restored = decodeVaultItems(encodeVaultItems(listOf(item))).single()
        assertEquals(1, restored.snapshot!!.elementClips.map { reopened.resolveReference(it.mediaUri!!) }.distinct().size)
        assertEquals(1, restored.snapshot!!.musicClips.map { reopened.resolveReference(it.mediaUri!!) }.distinct().size)
        val copy = restored.copy(id = "copy", snapshot = restored.snapshot!!.copy(title = "Copy"))
        assertEquals(restored.snapshot!!.elementClips.map { it.mediaUri }, copy.snapshot!!.elementClips.map { it.mediaUri })
        assertEquals(2, reopened.inspect().poolFiles)
        playTen(ownedVideo.reference, video = true); playTen(ownedAudio.reference, video = false)
        assertEquals(2, reopened.inspect().poolFiles)
        File(isolated.filesDir, "choreography_vault_v1.json").writeText(encodeVaultItems(listOf(item)))
        val database = getCatalogDatabase(Room.inMemoryDatabaseBuilder(base, CatalogDatabase::class.java))
        try {
            val repository = RoomCatalogRepository(database)
            repository.save(AppData(
                danceStyles = listOf(DanceStyle("owned", "Owned style", listOf(Move("video", "Owned video", "1", videoUri = video.path)))),
                songs = listOf(Song("audio", "Owned audio", "owned", "1", audio.path, 1.0, 32044)),
            ))
            val useCase = AndroidDebugExchangeZipUseCase(CatalogExporter(repository), DefaultMediaStorage(isolated.filesDir.path),
                object : ImportSecureZip {
                    override suspend fun import(archivePath: String, into: AppData, contentFilter: ImportContentFilter, stylePlan: StyleImportPlan): ImportSecureZipResult = error("No import in ownership fixture")
                }, repository, base.packageName, storage,
                choreographyBridge = AndroidChoreographyExchangeBridge(isolated, mediaReferenceResolver = reopened::resolveReference),
                mediaReferenceResolver = reopened::resolveReference,
            )
            val result = useCase.export(ExportPackageSelection(listOf("video"), listOf("audio"), listOf("qa-owned")))
            assertTrue(result.toString(), result is ExchangeZipResult.Ok)
            val snapshot = useCase.inspectArchive((result as ExchangeZipResult.Ok).archivePath)
            assertEquals(listOf("Owned video"), snapshot.elements.map { it.title })
            assertEquals(listOf("Owned audio"), snapshot.songs.map { it.title })
            assertEquals(listOf("Owned repeated fixture"), snapshot.choreographies.map { it.title })
            assertFalse(video.exists() || audio.exists()); assertEquals(2, reopened.inspect().poolFiles)
        } finally { database.close() }
    }

    @Test fun repeated_new_picks_deduplicate_real_formats_and_low_space_never_publishes_or_deletes_source() = runBlocking {
        val video = videoSource(); val audio = audioSource(); val store = AndroidOwnedMediaStore(isolated, storage)
        val first = store.retain(video.path, OwnedMediaKind.Video)
        val duplicate = File(sandbox, "different-name.bin").apply { video.inputStream().use { input -> outputStream().use { input.copyTo(it) } } }
        val second = store.retain(duplicate.path, OwnedMediaKind.Video)
        assertEquals(first.reference, second.reference); assertTrue(second.reused)
        val failure = runCatching {
            AndroidOwnedMediaStore(isolated, object : DeviceStorage { override fun freeBytes() = 0L; override fun totalBytes() = 0L })
                .retain(audio.path, OwnedMediaKind.Audio)
        }.exceptionOrNull()
        assertTrue(failure is OwnedMediaException && failure.reason == OwnedMediaFailure.NoSpace)
        assertTrue(video.exists() && duplicate.exists() && audio.exists()); assertEquals(1, store.inspect().poolFiles)
        assertTrue(File(isolated.filesDir, "MidnightDancer/owned_media/v1/_staging").listFiles().orEmpty().isEmpty())
    }

    @Test fun settings_adoption_unions_catalog_disk_live_and_room_snapshots_without_metadata_edits_or_per_clip_copies() = runBlocking {
        val video = videoSource(); val libraryAudio = audioSource()
        fun duplicate(name: String) = File(sandbox, name).apply { writeBytes(libraryAudio.readBytes()) }
        val diskOnly = duplicate("disk-only.wav"); val roomOnly = duplicate("room-only.wav"); val liveOnly = duplicate("live-only.wav")
        fun item(id: String, audio: File) = ChoreographyVaultItem(id, id, VaultStatus.Ready, "Owned style",
            bpm = 120, durationLabel = "00:01", trackName = id, chrId = id,
            snapshot = ChoreographySnapshot(id, "Owned style", elementClips = List(10) { EditorClip("Repeat video", 100, video.path) },
                musicClips = List(10) { EditorClip("Repeat audio", 100, audio.path) }))
        val vault = File(isolated.filesDir, "choreography_vault_v1.json").apply { writeText(encodeVaultItems(listOf(item("disk", diskOnly)))) }
        val vaultBefore = vault.readBytes()
        // Simulate the prior committed AtomicFile backup after an interrupted writer.
        check(vault.renameTo(File(vault.path + ".bak")))
        val data = AppData(
            danceStyles = listOf(DanceStyle("owned", "Owned style", listOf(Move("v", "Video", "1", videoUri = video.path)))),
            songs = listOf(Song("a", "Audio", "owned", "1", libraryAudio.path, 1.0, 32044)),
            settings = JsonObject(mapOf(ChoreographyExchangeBridge.METADATA_KEY to JsonPrimitive(encodeVaultItems(listOf(item("room", roomOnly)))))),
        )
        val library = object : LoadLibrary { override suspend fun load() = data; override fun observe() = flowOf(data) }
        val references = OwnedMediaAdoptionReferences(isolated, library) { listOf(item("disk-again", diskOnly), item("live", liveOnly)) }
        val collection = references.collect()
        assertTrue(collection.warnings.isEmpty()); assertEquals(5, collection.references.size)
        assertEquals(setOf(video.path, libraryAudio.path, diskOnly.path, roomOnly.path, liveOnly.path), collection.references.map { it.reference }.toSet())
        val store = AndroidOwnedMediaStore(isolated, storage)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val coordinator = OwnedMediaAdoptionCoordinator(store, scope, references::collect)
            coordinator.start()
            withTimeout(15000) { while (!coordinator.state.value.finished) { check(coordinator.state.value.error == null); delay(10) } }
            val result = coordinator.state.value
            assertEquals(5, result.protectedCount); assertEquals(0, result.failed); assertEquals(5, result.processed)
            assertEquals(2, result.stats!!.poolFiles) // Repeated refs and equal audio bytes still own exactly two blobs.
            assertArrayEquals(vaultBefore, vault.readBytes())
            for (source in listOf(video, libraryAudio, diskOnly, roomOnly, liveOnly)) {
                assertTrue(source.exists()); assertTrue(File(store.resolveReference(source.path)).isFile)
            }
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }

    @Test fun settings_partial_parse_missing_source_and_cancel_never_report_complete_or_lose_completed_protection() = runBlocking {
        val audio = audioSource()
        val vault = File(isolated.filesDir, "choreography_vault_v1.json").apply { writeText("invalid vault json") }
        val data = AppData(songs = listOf(Song("a", "Audio", "", "1", audio.path, 1.0, 32044)))
        val library = object : LoadLibrary { override suspend fun load() = data; override fun observe() = flowOf(data) }
        val collection = OwnedMediaAdoptionReferences(isolated, library) { emptyList() }.collect()
        assertEquals(1, collection.references.size); assertFalse(collection.warnings.isEmpty())
        val missing = MediaAdoptionReference(File(sandbox, "missing.wav").path, OwnedMediaKind.Audio, "Missing")
        val store = AndroidOwnedMediaStore(isolated, storage)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val partial = OwnedMediaAdoptionCoordinator(store, scope) { collection.copy(references = collection.references + missing) }
            partial.start()
            withTimeout(10000) { while (!partial.state.value.finished) { check(partial.state.value.error == null); delay(10) } }
            assertEquals(1, partial.state.value.protectedCount); assertEquals(1, partial.state.value.failed)
            assertFalse(partial.state.value.warnings.isEmpty()); assertEquals("invalid vault json", vault.readText())
            val entered = CompletableDeferred<Unit>()
            val delayed = object : OwnedMediaStore by store {
                override suspend fun adopt(reference: String, kind: OwnedMediaKind, onProgress: ((OwnedMediaProgress) -> Unit)?): OwnedMedia {
                    if (reference == "blocked") { entered.complete(Unit); awaitCancellation() }
                    return store.adopt(reference, kind, onProgress)
                }
            }
            val cancelled = OwnedMediaAdoptionCoordinator(delayed, scope) { MediaAdoptionCollection(collection.references + MediaAdoptionReference("blocked", OwnedMediaKind.Audio, "Blocked")) }
            cancelled.start(); withTimeout(10000) { entered.await() }; cancelled.cancel()
            assertTrue(cancelled.state.value.cancelled); assertFalse(cancelled.state.value.finished); assertFalse(cancelled.state.value.running)
            assertEquals(1, cancelled.state.value.protectedCount)
            assertTrue(File(store.resolveReference(audio.path)).isFile); assertEquals("invalid vault json", vault.readText())
        } finally { scope.coroutineContext.job.cancelAndJoin() }
    }


    @Test fun dynamic_prepared_file_uri_playlist_survives_adoption_source_deletion_and_later_repeat_opens() = runBlocking {
        val store = AndroidOwnedMediaStore(isolated, storage); store.initialize()
        val video = videoSource(); val audio = audioSource()
        for ((original, kind) in listOf(video to OwnedMediaKind.Video, audio to OwnedMediaKind.Audio)) {
            val ownedPathOpens = AtomicInteger()
            var owned: OwnedMedia? = null
            val resolve: (String) -> String = { value ->
                store.resolveReference(value).also { effective ->
                    if (value == original.path && effective != value) ownedPathOpens.incrementAndGet()
                }
            }
            // MediaItem file:///... is prepared with the external absolute-path reference still unprotected.
            playTen(original.path, video = kind == OwnedMediaKind.Video, resolver = resolve, beforePlaying = {
                assertTrue(original.exists())
                owned = store.adopt(original.path, kind)
                assertTrue(original.delete()) // Only the synthetic source in this sandbox.
                assertEquals(owned!!.reference, store.resolveReference(original.path))
            })
            assertFalse(original.exists())
            assertNotNull(owned)
            assertTrue(File(owned!!.reference).isFile)
            assertTrue("A later repeat must actually open via the file-URI path alias, not a preloaded original descriptor", ownedPathOpens.get() > 0)
        }
        assertEquals(2, store.inspect().poolFiles)
    }

    @Test fun owned_export_import_into_second_sandbox_remaps_all_refs_and_decodes_without_source_pool() = runBlocking {
        // Run this method alone after force-stop. The real bridge publishes to the process store;
        // an application Activity/RootComponent autosave collector must never observe our fixture.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            val stages = listOf(Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.RESTARTED, Stage.PRE_ON_CREATE)
            assertTrue("Isolated import requires a cold instrumentation process with no live Activities",
                stages.all { monitor.getActivitiesInStage(it).isEmpty() })
        }
        val video = videoSource(); val audio = audioSource()
        val videoHash = hash(video); val audioHash = hash(audio)
        val sourceStore = AndroidOwnedMediaStore(isolated, storage)
        val ownedVideo = sourceStore.retain(video.path, OwnedMediaKind.Video)
        val ownedAudio = sourceStore.retain(audio.path, OwnedMediaKind.Audio)
        assertTrue(video.delete()); assertTrue(audio.delete()) // Synthetic originals only.
        val sourceItem = ChoreographyVaultItem("transfer-owned", "Transfer repeated owned", VaultStatus.Ready, "Transfer style",
            bpm = 120, durationLabel = "00:02", trackName = "Transfer audio", chrId = "transfer-owned",
            snapshot = ChoreographySnapshot("Transfer repeated owned", "Transfer style",
                elementClips = List(10) { EditorClip("Video repeat $it", 200, ownedVideo.reference) },
                musicClips = List(10) { EditorClip("Audio repeat $it", 200, ownedAudio.reference) }))
        File(isolated.filesDir, "choreography_vault_v1.json").writeText(encodeVaultItems(listOf(sourceItem)))
        val targetRoot = File(sandbox, "recipient").apply { check(mkdirs()) }
        val target = object : ContextWrapper(base) {
            override fun getFilesDir() = File(targetRoot, "files").apply { mkdirs() }
            override fun getCacheDir() = File(targetRoot, "cache").apply { mkdirs() }
        }
        // Prevent bridge.readItems() falling back to the application's live process store.
        val targetVault = File(target.filesDir, "choreography_vault_v1.json")
        targetVault.writeText(encodeVaultItems(emptyList()))
        val targetStore = AndroidOwnedMediaStore(target, storage); targetStore.initialize()
        val sourceDb = getCatalogDatabase(Room.inMemoryDatabaseBuilder(base, CatalogDatabase::class.java))
        val targetDb = getCatalogDatabase(Room.inMemoryDatabaseBuilder(base, CatalogDatabase::class.java))
        val liveBefore = ChoreographyVaultStore.all().toList()
        try {
            val sourceRepository = RoomCatalogRepository(sourceDb)
            val targetRepository = RoomCatalogRepository(targetDb)
            sourceRepository.save(AppData(
                danceStyles = listOf(DanceStyle("transfer", "Transfer style",
                    listOf(Move("transfer-video", "Transfer video", "1", videoUri = ownedVideo.reference)))),
                songs = listOf(Song("transfer-audio", "Transfer audio", "transfer", "1", ownedAudio.reference, 1.0, 32044))))
            targetRepository.save(AppData())
            fun exchange(context: Context, repository: RoomCatalogRepository, store: OwnedMediaStore) =
                AndroidDebugExchangeZipUseCase(CatalogExporter(repository), DefaultMediaStorage(context.filesDir.path),
                    DefaultImportSecureZip(DefaultValidateSecureZip(AndroidZipArchiveInspector(), SecureZipWhitelist.MidnightDancerFullBackup),
                        metadataReader = AndroidZipMetadataReader()),
                    repository, base.packageName, storage,
                    choreographyBridge = AndroidChoreographyExchangeBridge(context, mediaReferenceResolver = store::resolveReference),
                    mediaReferenceResolver = store::resolveReference)
            val exported = exchange(isolated, sourceRepository, sourceStore).export(
                ExportPackageSelection(listOf("transfer-video"), listOf("transfer-audio"), listOf("transfer-owned")))
            assertTrue(exported.toString(), exported is ExchangeZipResult.Ok)
            val received = File(target.cacheDir, "received.dance")
            File((exported as ExchangeZipResult.Ok).archivePath).copyTo(received)
            // Only our isolated source pool is disabled; no source file can accidentally satisfy target playback.
            val sourcePool = File(isolated.filesDir, "MidnightDancer/owned_media")
            assertTrue(sourcePool.isDirectory)
            assertTrue(sourcePool.renameTo(File(isolated.filesDir, "MidnightDancer/unavailable-owned-media")))
            assertFalse(File(ownedVideo.reference).exists()); assertFalse(File(ownedAudio.reference).exists())
            val imported = exchange(target, targetRepository, targetStore).import(received.path)
            assertTrue(imported.toString(), imported is ExchangeZipResult.Ok)
            val catalog = CatalogExporter(targetRepository).exportAppData()
            val importedMove = catalog.danceStyles.flatMap { it.moves }.single()
            val importedSong = catalog.songs.single()
            val importedItem = decodeVaultItems(targetVault.readText()).single()
            val snapshot = requireNotNull(importedItem.snapshot)
            assertEquals(10, snapshot.elementClips.size); assertEquals(10, snapshot.musicClips.size)
            assertEquals(sourceItem.snapshot!!.elementClips.map { it.durationMs }, snapshot.elementClips.map { it.durationMs })
            assertEquals(sourceItem.snapshot!!.musicClips.map { it.label }, snapshot.musicClips.map { it.label })
            val targetMedia = File(DefaultMediaStorage(target.filesDir.path).rootDirectory()).canonicalFile
            fun assertTarget(ref: String?, expected: String): String {
                val file = File(requireNotNull(ref)).canonicalFile
                val relative = file.relativeTo(targetMedia).invariantSeparatorsPath
                assertTrue("Imported ref must be target permanent media: $relative",
                    relative.startsWith("music/") || relative.startsWith("videos/") || relative.startsWith("exchange/imported_media/"))
                assertTrue(file.path.startsWith(targetMedia.path + File.separator))
                assertFalse(file.path.startsWith(isolated.filesDir.canonicalPath + File.separator))
                assertTrue(file.isFile); assertEquals(expected, hash(file))
                return file.path
            }
            assertTarget(importedMove.videoUri, videoHash); assertTarget(importedSong.fileName, audioHash)
            val videos = snapshot.elementClips.map { assertTarget(it.mediaUri, videoHash) }.distinct()
            val audios = snapshot.musicClips.map { assertTarget(it.mediaUri, audioHash) }.distinct()
            assertEquals(1, videos.size); assertEquals(1, audios.size)
            // Explicit recipient resolver, no app runtime binding/Koin mutation; native decoder must finish all ten clips.
            playTen(videos.single(), video = true, resolver = targetStore::resolveReference)
            playTen(audios.single(), video = false, resolver = targetStore::resolveReference)
            assertFalse(File(ownedVideo.reference).exists()); assertFalse(File(ownedAudio.reference).exists())
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { ChoreographyVaultStore.replaceAll(liveBefore) }
            sourceDb.close(); targetDb.close()
        }
    }

}
