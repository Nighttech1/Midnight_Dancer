package com.midnightdancer.core.data.exchange

import com.midnightdancer.core.data.zip.*
import com.midnightdancer.core.domain.entity.*
import com.midnightdancer.core.domain.exchange.*
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.usecase.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.*
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.*

/** Real ZIP/common merge/file ownership, explicit fault-injected transaction port; not a real-Room integration claim. */
@OptIn(ExperimentalForeignApi::class)
class IosArchiveImportTransactionTest {
    private val fs = FileSystem.SYSTEM
    private val storage = object : DeviceStorage { override fun freeBytes() = 4L * 1024 * 1024 * 1024; override fun totalBytes() = freeBytes() }
    private val noCrypto = object : IosArchiveContainerCodec {
        override fun decryptToPrivateZip(containerPath: String, destinationPath: String, bundleId: String, shouldCancel: () -> Boolean): Unit = error("Plain ZIP fixture must not call crypto")
        override fun encryptPrivateZip(zipPath: String, destinationPath: String, bundleId: String, shouldCancel: () -> Boolean): Unit = error("Not an export fixture")
    }
    private val before = AppData(danceStyles = listOf(DanceStyle("old-style", "Existing style", listOf(Move("old-move", "Existing move", "1")))),
        songs = listOf(Song("old-song", "Existing song", "old-style", "1", "existing.wav", 1.0, 1)),
        settings = JsonObject(mapOf("local-preference" to JsonPrimitive("keep"), "editorVaultV1" to JsonPrimitive("old opaque Room editor document"))))
    private suspend fun fixture(block: suspend (Path, Path) -> Unit) {
        val root = NSTemporaryDirectory().toPath() / ("ios-import-test-" + NSUUID().UUIDString)
        fs.createDirectory(root, mustCreate = true)
        val recipient = root / "recipient"; fs.createDirectory(recipient, mustCreate = true)
        try { block(root, recipient) } finally { fs.deleteRecursively(root) }
    }
    private fun write(path: Path, bytes: ByteArray) { fs.sink(path).buffer().use { it.write(bytes) } }
    private fun read(path: Path) = fs.source(path).buffer().use { it.readByteArray() }
    private fun packageZip(root: Path, editor: Boolean = false): Path {
        val payload = """{"version":1,"items":[{"id":"editor","title":"Imported editor","style":"Sensual","snapshot":null}]}"""
        val incoming = AppData(danceStyles = listOf(DanceStyle("sensual", "Sensual", listOf(Move("video", "Incoming move", "1", videoUri = "video")))),
            songs = listOf(Song("audio", "Incoming song", "sensual", "1", "audio.wav", 1.0, 5)),
            settings = if (editor) JsonObject(mapOf("editorVaultV1" to JsonPrimitive(payload))) else JsonObject(emptyMap()))
        val metadata = root / "metadata-source"; val video = root / "video-source"; val audio = root / "audio-source"
        write(metadata, DomainJson.encodeAppData(incoming).encodeToByteArray()); write(video, byteArrayOf(1, 2, 3, 4)); write(audio, byteArrayOf(8, 7, 6, 5, 4))
        return (root / "source.zip").also { IosSecureZipArchive(root.toString()).writeStoredArchive(it.toString(), listOf(
            IosZipSource("metadata.json", metadata.toString()), IosZipSource("videos/video.mp4", video.toString()), IosZipSource("music/audio.wav", audio.toString()))) }
    }
    private class Access(initial: AppData) : IosImportLibraryAccess {
        var data = initial; var revision = 41
        val receipts = mutableMapOf<String, String>()
        var commits = 0; var rollbacks = 0
        var failCommitOnce = false; var failRollback = false; var cancelAfterCommit = false
        private val mutex = Mutex()
        private data class Snapshot(override val data: AppData, val revision: Int) : IosImportCatalogSnapshot
        override suspend fun acquireExclusiveWrite(): IosImportLibrarySession {
            mutex.lock()
            return object : IosImportLibrarySession {
                var closed = false
                override suspend fun snapshot(): IosImportCatalogSnapshot = Snapshot(data, revision)
                override suspend fun receipt(key: String) = receipts[key]
                override suspend fun commitCatalogWithPendingReceipt(data: AppData, key: String, pendingReceipt: String) {
                    commits++; this@Access.data = data; revision++; receipts[key] = pendingReceipt
                    if (cancelAfterCommit) currentCoroutineContext().cancel()
                    if (failCommitOnce) { failCommitOnce = false; error("after-catalog-write") }
                }
                override suspend fun writeReceipt(key: String, receipt: String) { receipts[key] = receipt }
                override suspend fun rollback(snapshot: IosImportCatalogSnapshot, key: String) {
                    assertTrue(currentCoroutineContext().isActive, "Rollback must run in NonCancellable")
                    rollbacks++
                    if (failRollback) error("fixture-rollback-unavailable")
                    this@Access.data = snapshot.data; revision = (snapshot as Snapshot).revision; receipts.remove(key)
                }
                override fun close() { if (!closed) { closed = true; mutex.unlock() } }
            }
        }
    }
    private class Vault : ChoreographyExchangeBridge {
        var items = 0; var failCommit = false; var paths = emptyMap<String, String>(); var redirects = emptyMap<String, String>()
        override fun catalog() = emptyList<CatalogPickItem>()
        override fun export(ids: Set<String>): EditorExchangePayload = error("not export")
        override fun prepareImport(payloadJson: String, resolvedMedia: Map<String, String>, styleRedirect: Map<String, String>, legacyData: AppData): PreparedChoreographyImport {
            redirects = styleRedirect
            val oldCount = items; val oldPaths = paths
            return object : PreparedChoreographyImport {
                override fun commit() { items++; paths = resolvedMedia; if (failCommit) error("after-vault-write") }
                override fun rollback() { items = oldCount; paths = oldPaths }
            }
        }
    }
    private fun transaction(access: Access, recipient: Path, bridge: ChoreographyExchangeBridge? = null) =
        IosArchiveImportTransaction(access, storage, noCrypto, recipient.toString(), bridge = bridge)
    private fun importedDirectories(recipient: Path): List<Path> {
        val path = recipient / "exchange" / "imports"
        return if (fs.exists(path)) fs.list(path) else emptyList()
    }

    @Test fun successful_import_owns_target_media_retains_exact_archive_and_completed_retry_is_idempotent() = runBlocking { fixture { root, recipient ->
        val source = packageZip(root); val original = read(source); val access = Access(before)
        val operation = transaction(access, recipient)
        val first = assertIs<ExchangeZipResult.Ok>(operation.importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty))
        val video = access.data.danceStyles.flatMap { it.moves }.single { it.id == "video" }.videoUri!!
        val audio = access.data.songs.single { it.id == "audio" }.fileName
        assertTrue(video.startsWith(IosImportedMediaReference.PREFIX)); assertTrue(audio.startsWith(IosImportedMediaReference.PREFIX))
        assertContentEquals(byteArrayOf(1, 2, 3, 4), read(IosImportedMediaReference.resolve(recipient.toString(), video).toPath())); assertContentEquals(byteArrayOf(8, 7, 6, 5, 4), read(IosImportedMediaReference.resolve(recipient.toString(), audio).toPath()))
        assertContentEquals(original, read(first.archivePath.toPath())); assertContentEquals(original, read(source))
        assertEquals(before.settings["editorVaultV1"], access.data.settings["editorVaultV1"])
        val once = access.data
        val second = assertIs<ExchangeZipResult.Ok>(operation.importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty))
        assertEquals(first.archivePath, second.archivePath); assertEquals(once, access.data); assertEquals(1, access.commits)
        assertEquals(1, importedDirectories(recipient).size)
    } }

    @Test fun after_catalog_write_exception_restores_exact_snapshot_and_retry_can_commit() = runBlocking { fixture { root, recipient ->
        val source = packageZip(root); val access = Access(before).apply { failCommitOnce = true }
        val operation = transaction(access, recipient)
        assertIs<ExchangeZipResult.Failed>(operation.importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty))
        assertEquals(before, access.data); assertEquals(41, access.revision); assertTrue(access.receipts.isEmpty())
        assertTrue(importedDirectories(recipient).isEmpty()); assertTrue(fs.exists(source))
        assertIs<ExchangeZipResult.Ok>(operation.importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty))
    } }

    @Test fun vault_partial_failure_rolls_back_both_consumers_before_deleting_only_new_media() = runBlocking { fixture { root, recipient ->
        val source = packageZip(root, editor = true); val access = Access(before); val vault = Vault().apply { failCommit = true }
        val result = transaction(access, recipient, vault).importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty)
        assertIs<ExchangeZipResult.Failed>(result); assertEquals(before, access.data); assertEquals(0, vault.items)
        assertTrue(vault.paths.isEmpty()); assertTrue(access.receipts.isEmpty()); assertTrue(importedDirectories(recipient).isEmpty())
    } }

    @Test fun cancellation_immediately_after_catalog_write_has_non_cancellable_rollback() = runBlocking { fixture { root, recipient ->
        val source = packageZip(root); val access = Access(before).apply { cancelAfterCommit = true }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val task = scope.async { transaction(access, recipient).importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty) }
            assertFailsWith<CancellationException> { task.await() }
            assertEquals(before, access.data); assertEquals(1, access.rollbacks); assertTrue(access.receipts.isEmpty())
            assertTrue(importedDirectories(recipient).isEmpty()); assertTrue(fs.exists(source))
        } finally { scope.cancel() }
    } }

    @Test fun failed_rollback_keeps_referenced_media_and_receipt_blocks_unsafe_retry() = runBlocking { fixture { root, recipient ->
        val source = packageZip(root); val access = Access(before).apply { failCommitOnce = true; failRollback = true }
        val operation = transaction(access, recipient)
        val failed = assertIs<ExchangeZipResult.Failed>(operation.importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty))
        assertTrue("ios_import_recovery_required" in failed.reasons)
        val video = access.data.danceStyles.flatMap { it.moves }.single { it.id == "video" }.videoUri!!
        assertTrue(fs.exists(IosImportedMediaReference.resolve(recipient.toString(), video).toPath())); assertTrue(access.receipts.values.single().contains("recovery_required"))
        val repeated = assertIs<ExchangeZipResult.Failed>(operation.importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty))
        assertTrue("ios_import_recovery_required" in repeated.reasons); assertEquals(1, access.commits)
        assertEquals(1, importedDirectories(recipient).size); assertTrue(fs.exists(source))
    } }

    @Test fun same_id_different_bytes_cannot_bind_imported_content_to_old_local_media() = runBlocking { fixture { root, recipient ->
        val source = packageZip(root); val localVideo = root / "existing.mp4"; write(localVideo, byteArrayOf(99, 98))
        val local = before.copy(danceStyles = before.danceStyles + DanceStyle("sensual", "Sensual", listOf(Move("video", "Local same ID", "1", videoUri = localVideo.toString()))))
        val access = Access(local)
        val result = assertIs<ExchangeZipResult.Failed>(transaction(access, recipient).importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty))
        assertTrue("catalog_media_conflict" in result.reasons); assertEquals(local, access.data); assertEquals(0, access.commits)
        assertContentEquals(byteArrayOf(99, 98), read(localVideo)); assertTrue(importedDirectories(recipient).isEmpty())
    } }

    @Test fun missing_vault_bridge_and_unsupported_subset_filters_fail_without_catalog_mutation() = runBlocking { fixture { root, recipient ->
        val source = packageZip(root, editor = true); val access = Access(before)
        val operation = transaction(access, recipient)
        val noBridge = assertIs<ExchangeZipResult.Failed>(operation.importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty))
        assertTrue("ios_choreography_import_bridge_unavailable" in noBridge.reasons)
        val subset = assertIs<ExchangeZipResult.Failed>(operation.importArchive(source.toString(), ImportContentFilter(false, false, true), StyleImportPlan.Empty))
        assertTrue("ios_import_filter_not_supported" in subset.reasons)
        assertEquals(before, access.data); assertEquals(0, access.commits); assertTrue(importedDirectories(recipient).isEmpty())
    } }
    @Test fun absent_exclusive_writer_access_fails_closed_before_reading_or_writing_files() = runBlocking { fixture { root, recipient ->
        val library = object : LoadLibrary {
            override suspend fun load(): AppData = error("Must not load without transaction integration")
            override fun observe() = flowOf(before)
        }
        val operation = IosExchangeZipUseCase(library, storage, noCrypto, privateRoot = recipient.toString())
        val result = assertIs<ExchangeZipResult.Failed>(operation.import((root / "not-read.zip").toString()))
        assertTrue("ios_import_transaction_unavailable" in result.reasons)
        assertFalse(fs.exists(recipient / "exchange"))
    } }

    @Test fun concurrent_retry_uses_one_completed_receipt_and_creates_one_owned_import() = runBlocking { fixture { root, recipient ->
        val source = packageZip(root); val access = Access(before)
        val results = coroutineScope {
            listOf(async { transaction(access, recipient).importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty) },
                async { transaction(access, recipient).importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty) }).awaitAll()
        }
        val paths = results.map { assertIs<ExchangeZipResult.Ok>(it).archivePath }
        assertEquals(1, paths.distinct().size); assertEquals(1, access.commits)
        assertEquals(1, importedDirectories(recipient).size)
    } }

    @Test fun interrupted_pending_receipt_is_not_misreported_as_completed_or_duplicated() = runBlocking { fixture { root, recipient ->
        val source = packageZip(root); val access = Access(before); val operation = transaction(access, recipient)
        val first = assertIs<ExchangeZipResult.Ok>(operation.importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty))
        val key = access.receipts.keys.single()
        val receipt = Json.parseToJsonElement(access.receipts.getValue(key)).jsonObject.toMutableMap()
        receipt["phase"] = JsonPrimitive("pending"); access.receipts[key] = JsonObject(receipt).toString()
        val after = access.data
        val result = assertIs<ExchangeZipResult.Failed>(operation.importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty))
        assertTrue("ios_import_recovery_required" in result.reasons)
        assertEquals(after, access.data); assertEquals(1, access.commits)
        assertTrue(fs.exists(first.archivePath.toPath())); assertEquals(1, importedDirectories(recipient).size)
    } }

    @Test fun relocating_documents_preserves_media_vault_refs_receipt_retry_and_exact_archive_inspection() = runBlocking { fixture { root, recipient ->
        val source = packageZip(root, editor = true)
        val access = Access(before); val vault = Vault()
        val first = assertIs<ExchangeZipResult.Ok>(transaction(access, recipient, vault).importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty))
        val catalogBeforeMove = access.data
        val video = access.data.danceStyles.flatMap { it.moves }.single { it.id == "video" }.videoUri!!
        val audio = access.data.songs.single { it.id == "audio" }.fileName
        assertTrue(video.startsWith(IosImportedMediaReference.PREFIX)); assertTrue(audio.startsWith(IosImportedMediaReference.PREFIX))
        assertTrue(vault.paths.values.all { it.startsWith(IosImportedMediaReference.PREFIX) && it.split('/').size == 3 })
        val receiptRaw = access.receipts.values.single()
        assertFalse(receiptRaw.contains(recipient.toString())); assertFalse(receiptRaw.contains(fs.canonicalize(recipient).toString()))
        assertFalse(DomainJson.encodeAppData(access.data).contains(recipient.toString()))
        val receipt = Json.parseToJsonElement(receiptRaw).jsonObject
        assertEquals(2, receipt.getValue("version").jsonPrimitive.int)
        val archiveReference = receipt.getValue("archive").jsonPrimitive.content
        assertTrue(receipt.getValue("directory").jsonPrimitive.content.startsWith(IosImportedMediaReference.PREFIX))
        assertTrue(receipt.getValue("files").jsonObject.keys.all { '/' !in it })
        val movedParent = root / "new-install-container"; fs.createDirectory(movedParent, mustCreate = true)
        val movedDocuments = movedParent / "Documents"
        fs.atomicMove(recipient, movedDocuments)
        assertFalse(fs.exists(recipient)); assertFalse(fs.exists(first.archivePath.toPath()))
        assertContentEquals(byteArrayOf(1, 2, 3, 4), read(IosImportedMediaReference.resolve(movedDocuments.toString(), video).toPath()))
        assertContentEquals(byteArrayOf(8, 7, 6, 5, 4), read(IosImportedMediaReference.resolve(movedDocuments.toString(), audio).toPath()))
        for (reference in vault.paths.values) assertTrue(fs.exists(IosImportedMediaReference.resolve(movedDocuments.toString(), reference).toPath()))
        val viewed = IosArchiveInspector(storage, noCrypto, movedDocuments.toString()).inspect(archiveReference)
        assertEquals(listOf("Incoming move"), viewed.elements.map { it.title })
        assertEquals(listOf("Incoming song"), viewed.songs.map { it.title })
        assertEquals(listOf("Imported editor"), viewed.choreographies.map { it.title })
        val repeated = assertIs<ExchangeZipResult.Ok>(transaction(access, movedDocuments, vault).importArchive(source.toString(), ImportContentFilter.All, StyleImportPlan.Empty))
        assertTrue(repeated.archivePath.startsWith(fs.canonicalize(movedDocuments).toString() + "/exchange/imports/"))
        assertEquals(catalogBeforeMove, access.data); assertEquals(1, access.commits); assertEquals(1, vault.items)
        assertEquals(1, importedDirectories(movedDocuments).size)
        assertContentEquals(read(source), read(repeated.archivePath.toPath()))
    } }

    @Test fun portable_reference_rejects_nested_paths_escapes_and_encoded_separators() = runBlocking { fixture { _, recipient ->
        val id = "11111111-2222-3333-4444-555555555555"
        for (reference in listOf("md-import-v1/$id/../outside.mp4", "md-import-v1/$id/a/b.mp4", "md-import-v1/$id/%2Foutside.mp4",
            "md-import-v1/../outside.mp4", "md-import-v1/$id/..", "md-import-v1/$id/\\outside.mp4")) {
            assertFails { IosImportedMediaReference.resolve(recipient.toString(), reference) }
        }
        val valid = IosImportedMediaReference.create(id, "v-digest.mp4")
        assertEquals((fs.canonicalize(recipient) / "exchange" / "imports" / id / "v-digest.mp4").toString(), IosImportedMediaReference.resolve(recipient.toString(), valid))
    } }

    @Test fun choreography_redirects_keep_canonical_local_ids_instead_of_display_names() = runBlocking { fixture { root, recipient ->
        val source = packageZip(root, editor = true)
        val local = before.copy(danceStyles = before.danceStyles + DanceStyle("local-canonical", "Display alias"))
        val access = Access(local); val vault = Vault()
        val plan = StyleImportPlan(mapOf("sensual" to "local-canonical"))
        assertIs<ExchangeZipResult.Ok>(transaction(access, recipient, vault).importArchive(source.toString(), ImportContentFilter.All, plan))
        assertEquals(mapOf("sensual" to "local-canonical"), vault.redirects)
        assertFalse(vault.redirects.values.contains("Display alias"))
    } }

}
