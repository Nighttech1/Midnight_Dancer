package com.midnightdancer.core.data.exchange

import com.midnightdancer.core.data.zip.*
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.exchange.*
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.storage.DiskSpaceGate
import com.midnightdancer.core.domain.usecase.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okio.Buffer
import okio.HashingSource
import okio.Path
import okio.Path.Companion.toPath
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import okio.use
import platform.Foundation.NSUUID

/** Exception/cancellation-safe additive transaction. A pending crash receipt fails closed for explicit recovery. */
@OptIn(ExperimentalForeignApi::class)
class IosArchiveImportTransaction(
    private val access: IosImportLibraryAccess,
    private val storage: DeviceStorage,
    private val codec: IosArchiveContainerCodec,
    private val privateRoot: String,
    private val bundleId: String = "com.midnightdancer.app",
    private val bridge: ChoreographyExchangeBridge? = null,
    private val resolveMedia: (String) -> String = { it },
) {
    suspend fun importArchive(path: String, filter: ImportContentFilter, stylePlan: StyleImportPlan): ExchangeZipResult = withContext(Dispatchers.IO) {
        // The shared ghost/scoped-ID merge needs a separate source-ID map; never silently misbind it.
        if (filter != ImportContentFilter.All) return@withContext ExchangeZipResult.Failed("Пока поддерживается импорт полного содержимого архива", listOf("ios_import_filter_not_supported"))
        val context = currentCoroutineContext()
        val workspace = IosArchiveWorkspace(privateRoot, storage)
        var stage: IosPreparedZip? = null
        var permanent: Path? = null
        var retainPermanent = false
        var session: IosImportLibrarySession? = null
        try {
            val source = workspace.copyImmutable(path, "source.archive", IosSecureZipArchive.MAX_ARCHIVE_BYTES + 36) { context.ensureActive() }
            val sourceHash = hash(workspace, source) { context.ensureActive() }
            val mapping = JsonObject(stylePlan.archiveStyleToLocalId.entries.sortedBy { it.key }.associate { it.key to JsonPrimitive(it.value) }).toString()
            val key = "ios-import-v1-" + (sourceHash + "|all|" + mapping).encodeUtf8().sha256().hex()
            val prefix = workspace.fs.source(source).buffer().use { it.readByteArray(8) }
            val encrypted = prefix.take(7).toByteArray().decodeToString() == "MDANCE1"
            val zip = if (encrypted) (workspace.directory / "verified.zip").also {
                codec.decryptToPrivateZip(source.toString(), it.toString(), bundleId) { !context.isActive }
            } else {
                require(path.substringAfterLast('.').equals("zip", true) && prefix[0] == 0x50.toByte() && prefix[1] == 0x4b.toByte()) { "unsupported_archive_format" }
                source
            }
            val archive = IosSecureZipArchive(privateRoot)
            val raw = archive.metadata(zip.toString())
            IosArchiveInspector.project(raw)
            val incoming = DomainJson.decodeAppData(raw)
            requireUniqueIds(incoming)
            context.ensureActive()
            val owner = access.acquireExclusiveWrite()
            session = owner
            owner.receipt(key)?.let { receipt ->
                val result = completedReceipt(workspace, receipt, sourceHash) { context.ensureActive() }
                return@withContext result
            }
            val snapshot = owner.snapshot()
            val before = snapshot.data
            val importer = DefaultImportSecureZip(DefaultValidateSecureZip(IosZipArchiveInspector(archive), SecureZipWhitelist.MidnightDancerFullBackup), metadataReader = IosZipMetadataReader(archive))
            val merge = importer.import(zip.toString(), before, filter, stylePlan)
            if (merge is ImportSecureZipResult.Failed) return@withContext ExchangeZipResult.Failed("Архив отклонён: существующая библиотека сохранена", listOf("ios_import_merge_rejected"))
            val merged = (merge as ImportSecureZipResult.Success).data
            requireUniqueIds(merged)
            val expandedBytes = archive.inspect(zip.toString()).sumOf { it.size }
            require(DiskSpaceGate.canImport(storage.freeBytes(), expandedBytes * 2 + (workspace.fs.metadata(source).size ?: 0L) + 8L * 1024 * 1024)) { "insufficient_free_space" }
            val staged = archive.extractToNewDirectory(zip.toString(), storage) { context.ensureActive() }
            stage = staged
            val parent = workspace.root / "exchange" / "imports"
            workspace.privateDirectory(parent)
            val directory = parent / NSUUID().UUIDString
            workspace.fs.createDirectory(directory, mustCreate = true)
            permanent = directory
            val resolved = linkedMapOf<String, String>()
            val hashes = linkedMapOf<String, String>()
            for ((relative, stagedPath) in staged.resolvedPaths.filterKeys { it.startsWith("videos/") || it.startsWith("music/") }) {
                context.ensureActive()
                SecureZipSandbox.assertExtractTargetAllowed(directory.toString(), relative, SecureZipWhitelist.MidnightDancerFullBackup)
                val stagedSource = stagedPath.toPath()
                val digest = hash(workspace, stagedSource) { context.ensureActive() }
                val originalName = relative.substringAfterLast('/').substringBeforeLast('.')
                if (originalName.startsWith("editor-")) require(originalName.removePrefix("editor-").matches(Regex("[0-9a-f]{64}")) && digest == originalName.removePrefix("editor-")) { "editor_media_digest_mismatch" }
                val kind = if (relative.startsWith("videos/")) "v" else "a"
                val extension = relative.substringAfterLast('.').lowercase()
                val target = directory / ("$kind-$digest.$extension")
                if (workspace.fs.exists(target)) require(hash(workspace, target) { context.ensureActive() } == digest) { "catalog_media_conflict" }
                else copyChecked(workspace, stagedSource, target) { context.ensureActive() }
                resolved[relative] = IosImportedMediaReference.create(directory.name, target.name)
                hashes[target.name] = digest
            }
            val retained = directory / if (encrypted) "archive.dance" else "archive.zip"
            copyChecked(workspace, source, retained) { context.ensureActive() }
            hashes[retained.name] = sourceHash
            val playable = rebindAndCheckCollisions(workspace, before, incoming, merged, stylePlan, resolved) { context.ensureActive() }
            val editorRaw = incoming.settings[ChoreographyExchangeBridge.METADATA_KEY]?.jsonPrimitive?.content
            val prepared = if (editorRaw != null || incoming.choreographies.isNotEmpty()) {
                val adapter = requireNotNull(bridge) { "ios_choreography_import_bridge_unavailable" }
                // The iOS vault persists canonical style IDs; never replace the caller's ID with a display alias.
                adapter.prepareImport(editorRaw.orEmpty(), resolved, stylePlan.archiveStyleToLocalId, incoming)
            } else null
            val settings = playable.settings.toMutableMap().apply {
                remove(ChoreographyExchangeBridge.METADATA_KEY)
                // The user's older Room-only editor document must never disappear as an import side effect.
                before.settings[ChoreographyExchangeBridge.METADATA_KEY]?.let { put(ChoreographyExchangeBridge.METADATA_KEY, it) }
            }
            val clean = playable.copy(settings = JsonObject(settings))
            assertExistingPreserved(before, clean)
            val pending = receipt("pending", directory, retained, sourceHash, hashes)
            var catalogAttempted = false
            try {
                context.ensureActive()
                catalogAttempted = true
                owner.commitCatalogWithPendingReceipt(clean, key, pending)
                context.ensureActive()
                prepared?.commit()
                context.ensureActive()
                owner.writeReceipt(key, receipt("completed", directory, retained, sourceHash, hashes))
                context.ensureActive()
                retainPermanent = true
                ExchangeZipResult.Ok("Содержимое архива импортировано", retained.toString(), workspace.fs.metadata(retained).size ?: 0L)
            } catch (failure: Throwable) {
                val recoveryErrors = mutableListOf<Throwable>()
                withContext(NonCancellable) {
                    runCatching { prepared?.rollback() }.exceptionOrNull()?.let { recoveryErrors += it }
                    if (catalogAttempted) runCatching { owner.rollback(snapshot, key) }.exceptionOrNull()?.let { recoveryErrors += it }
                    if (recoveryErrors.isNotEmpty()) {
                        retainPermanent = true // A surviving catalog/vault may still reference these exact files.
                        runCatching { owner.writeReceipt(key, receipt("recovery_required", directory, retained, sourceHash, hashes)) }
                    }
                }
                if (recoveryErrors.isNotEmpty()) error("ios_import_recovery_required")
                throw failure
            }
        } catch (failure: Throwable) {
            context.ensureActive()
            if (failure is CancellationException) throw failure
            val allowed = setOf("insufficient_free_space", "catalog_media_conflict", "catalog_id_conflict", "editor_media_digest_mismatch",
                "ios_choreography_import_bridge_unavailable", "ios_import_recovery_required", "ios_import_receipt_invalid")
            val reason = failure.message?.takeIf { it in allowed } ?: "ios_import_failed"
            ExchangeZipResult.Failed(if (reason == "ios_import_recovery_required") "Импорт требует восстановления; файлы сохранены" else "Импорт не завершён: существующие данные сохранены", listOf(reason))
        } finally {
            session?.close()
            stage?.close()
            if (!retainPermanent) permanent?.let { directory ->
                require(directory.parent == workspace.root / "exchange" / "imports")
                require(workspace.fs.canonicalize(directory).toString().startsWith(workspace.root.toString() + "/"))
                workspace.fs.deleteRecursively(directory)
            }
            workspace.close()
        }
    }

    private fun rebindAndCheckCollisions(workspace: IosArchiveWorkspace, before: AppData, incoming: AppData, merged: AppData,
        styles: StyleImportPlan, media: Map<String, String>, check: () -> Unit): AppData {
        fun movePath(id: String, ref: String?): String? {
            if (ref.isNullOrBlank()) return null
            // The original Android archive records an exact bare move ID for its MP4 member.
            val extension = if (ref == id) "mp4" else ref.substringAfterLast('.', "").lowercase()
            require(extension in setOf("mp4", "mov")) { "import_video_media_missing" }
            return requireNotNull(media["videos/$id.$extension"]) { "import_video_media_missing" }
        }
        fun songPath(id: String, ref: String): String {
            val extension = ref.substringAfterLast('.', "").lowercase()
            require(extension in setOf("mp3", "m4a", "wav", "aac", "ogg", "flac")) { "import_song_media_missing" }
            return requireNotNull(media["music/$id.$extension"]) { "import_song_media_missing" }
        }
        val oldMoves = before.danceStyles.flatMap { style -> style.moves.map { it.id to (style.id to it) } }.toMap()
        val oldSongs = before.songs.associateBy { it.id }
        for (style in incoming.danceStyles) for (move in style.moves) {
            oldMoves[move.id]?.let { (oldStyle, old) ->
                require(oldStyle == (styles.archiveStyleToLocalId[style.id] ?: style.id)) { "catalog_id_conflict" }
                val incomingPath = movePath(move.id, move.videoUri)
                if (incomingPath != null) {
                    val oldReference = old.videoUri?.takeIf { it.isNotBlank() } ?: error("catalog_media_conflict")
                    require(hash(workspace, workspace.source(resolveMedia(oldReference)), check) == hash(workspace, workspace.source(incomingPath), check)) { "catalog_media_conflict" }
                }
            }
        }
        for (song in incoming.songs) oldSongs[song.id]?.let { old ->
            require(hash(workspace, workspace.source(resolveMedia(old.fileName)), check) == hash(workspace, workspace.source(songPath(song.id, song.fileName)), check)) { "catalog_media_conflict" }
        }
        return merged.copy(
            danceStyles = merged.danceStyles.map { style -> style.copy(moves = style.moves.map { move ->
                if (move.id in oldMoves) move else move.copy(videoUri = movePath(move.id, move.videoUri))
            }) },
            songs = merged.songs.map { song -> if (song.id in oldSongs) song else song.copy(fileName = songPath(song.id, song.fileName)) })
    }
    private fun requireUniqueIds(data: AppData) {
        require(data.danceStyles.map { it.id }.distinct().size == data.danceStyles.size &&
            data.danceStyles.flatMap { it.moves }.map { it.id }.distinct().size == data.danceStyles.sumOf { it.moves.size } &&
            data.songs.map { it.id }.distinct().size == data.songs.size && data.choreographies.map { it.id }.distinct().size == data.choreographies.size) { "catalog_id_conflict" }
    }
    private fun assertExistingPreserved(before: AppData, after: AppData) {
        require(before.songs.all { it in after.songs } && before.choreographies.all { it in after.choreographies }) { "catalog_id_conflict" }
        for (style in before.danceStyles) {
            val next = after.danceStyles.singleOrNull { it.id == style.id }
            require(next != null && next.copy(moves = style.moves) == style && style.moves.all { it in next.moves }) { "catalog_id_conflict" }
        }
    }
    private fun receipt(phase: String, directory: Path, archive: Path, sourceHash: String, hashes: Map<String, String>): String =
        buildJsonObject {
            put("version", 2); put("phase", phase); put("directory", IosImportedMediaReference.directory(directory.name)); put("archive", IosImportedMediaReference.create(directory.name, archive.name)); put("sourceHash", sourceHash)
            put("files", JsonObject(hashes.mapValues { JsonPrimitive(it.value) }))
        }.toString()
    private fun completedReceipt(workspace: IosArchiveWorkspace, raw: String, sourceHash: String, check: () -> Unit): ExchangeZipResult {
        val receipt = Json.parseToJsonElement(raw).jsonObject
        require(receipt["version"]?.jsonPrimitive?.intOrNull == 2 && receipt["sourceHash"]?.jsonPrimitive?.content == sourceHash) { "ios_import_receipt_invalid" }
        require(receipt["phase"]?.jsonPrimitive?.content == "completed") { "ios_import_recovery_required" }
        val directory = IosImportedMediaReference.resolveDirectory(workspace.root.toString(), receipt.getValue("directory").jsonPrimitive.content).toPath()
        require(directory.parent == workspace.root / "exchange" / "imports") { "ios_import_receipt_invalid" }
        val files = receipt.getValue("files").jsonObject
        for ((relative, digest) in files) {
            require(IosImportedMediaReference.create(directory.name, relative).substringAfterLast('/') == relative) { "ios_import_receipt_invalid" }
            val file = directory / relative
            require(workspace.fs.exists(file) && workspace.fs.canonicalize(file).toString().startsWith(directory.toString() + "/")) { "ios_import_recovery_required" }
            require(hash(workspace, file, check) == digest.jsonPrimitive.content) { "ios_import_recovery_required" }
        }
        val archive = IosImportedMediaReference.resolve(workspace.root.toString(), receipt.getValue("archive").jsonPrimitive.content).toPath()
        require(archive.parent == directory && archive.name in files) { "ios_import_receipt_invalid" }
        return ExchangeZipResult.Ok("Этот архив уже импортирован", archive.toString(), workspace.fs.metadata(archive).size ?: 0L)
    }
    private fun copyChecked(workspace: IosArchiveWorkspace, from: Path, to: Path, check: () -> Unit) {
        workspace.fs.openReadWrite(to, mustCreate = true).use { handle ->
            handle.sink().buffer().use { output -> workspace.fs.source(from).buffer().use { input ->
                val buffer = ByteArray(65536); var total = 0L
                while (true) { check(); val n = input.read(buffer); if (n == -1) break; total += n; require(total <= IosSecureZipArchive.MAX_ARCHIVE_BYTES + 36); output.write(buffer, 0, n) }
            } }
            handle.flush()
        }
        require(hash(workspace, from, check) == hash(workspace, to, check)) { "import_copy_verification_failed" }
    }
    private fun hash(workspace: IosArchiveWorkspace, path: Path, check: () -> Unit): String {
        val hashing = HashingSource.sha256(workspace.fs.source(path))
        hashing.buffer().use { input -> val buffer = Buffer(); while (true) { check(); if (input.read(buffer, 65536) == -1L) break; buffer.clear() } }
        return hashing.hash.hex()
    }
}
