package com.midnightdancer.core.data.exchange

import com.midnightdancer.core.data.files.platformDocumentsDirectoryPath
import com.midnightdancer.core.data.zip.IosSecureZipArchive
import com.midnightdancer.core.data.zip.IosZipSource
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.DanceStyle
import com.midnightdancer.core.domain.exchange.*
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.storage.DiskSpaceGate
import com.midnightdancer.core.domain.usecase.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okio.buffer
import okio.use
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSUUID

/** Operational export/inspection only. Import is fail-closed until a catalog+vault+media transaction is supplied. */
@OptIn(ExperimentalForeignApi::class)
class IosExchangeZipUseCase(
    private val library: LoadLibrary,
    private val storage: DeviceStorage,
    private val containerCodec: IosArchiveContainerCodec,
    private val choreographyBridge: ChoreographyExchangeBridge? = null,
    private val mediaReferenceResolver: (String) -> String = { it },
    private val privateRoot: String = platformDocumentsDirectoryPath(),
    private val bundleId: String = "com.midnightdancer.app",
) : ExchangeZipUseCase {
    private val mutex = Mutex()
    private val inspector = IosArchiveInspector(storage, containerCodec, privateRoot, bundleId)
    override suspend fun inspectArchive(archivePath: String) = inspector.inspect(archivePath)

    override suspend fun loadExportCatalog(): ExportCatalogSnapshot = withContext(Dispatchers.IO) {
        catalog(library.load())
    }
    override suspend fun export(selection: ExportPackageSelection?): ExchangeZipResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { exportSelected(selection) }
            catch (failure: Throwable) {
                currentCoroutineContext().ensureActive()
                if (failure is CancellationException) throw failure
                val safeReasons = setOf("invalid_export_selection", "selected_material_missing", "choreography_dependency_missing",
                    "selected_video_missing", "unsupported_media_extension", "insufficient_free_space", "package_too_large",
                    "selected_media_exceeds_package_limit", "metadata_size_limit", "archive_size_limit", "archive_source_changed",
                    "editor_media_not_packed", "export_verification_mismatch")
                val reason = failure.message?.takeIf { it in safeReasons } ?: "ios_export_failed"
                val message = when (reason) {
                    "insufficient_free_space" -> "Недостаточно места для безопасной подготовки архива"
                    "package_too_large", "selected_media_exceeds_package_limit" -> "Максимальный размер сета — 300 МБ"
                    else -> "Не удалось подготовить архив сета"
                }
                ExchangeZipResult.Failed(message, listOf(reason))
            }
        }
    }
    override suspend fun import(preferredPath: String?, contentFilter: ImportContentFilter, stylePlan: StyleImportPlan): ExchangeZipResult =
        ExchangeZipResult.Failed("Импорт на iOS ещё не подключён: существующие данные сохранены", listOf("ios_import_transaction_unavailable"))

    override suspend fun peekImport(preferredPath: String?): ImportPeek? = withContext(Dispatchers.IO) {
        if (preferredPath.isNullOrBlank()) return@withContext null
        val archive = inspector.inspect(preferredPath)
        ImportPeek(archive.styles, library.load().danceStyles.map { StyleRef(it.id, it.name) })
    }

    private fun catalog(data: AppData): ExportCatalogSnapshot {
        val styles = data.danceStyles.map { StyleRef(it.id, it.name.ifBlank { it.id }) }.toMutableList()
        fun canonical(value: String): String = styles.firstOrNull { it.id == value || it.id.equals(value, true) || it.name.equals(value, true) }?.id ?: value
        fun bytes(reference: String): Long = runCatching {
            val raw = mediaReferenceResolver(reference)
            require(raw.startsWith('/') && !raw.contains("://"))
            FileSystem.SYSTEM.metadata(raw.toPath()).size ?: 0L
        }.getOrDefault(0L)
        val elements = data.danceStyles.flatMap { style -> style.moves.map { CatalogPickItem(it.id, it.name.ifBlank { it.id }, style.name, style.id, it.videoUri?.let(::bytes) ?: 0L) } }
        val songs = data.songs.map { CatalogPickItem(it.id, it.title.ifBlank { it.id }, it.danceStyle, canonical(it.danceStyle), bytes(it.fileName)) }
        val moveSizes = elements.associate { it.id to it.approximateBytes }
        val songSizes = songs.associate { it.id to it.approximateBytes }
        val legacy = data.choreographies.map { item ->
            val size = (songSizes[item.songId] ?: 0L) + item.timeline.values.map { it.substringAfter("::", it) }.distinct().sumOf { moveSizes[it] ?: 0L }
            CatalogPickItem(item.id, item.name.ifBlank { item.id }, item.styleId, canonical(item.styleId), size)
        }
        val editor = choreographyBridge?.catalog().orEmpty().map { row ->
            val id = canonical(row.styleId)
            if (id.isNotBlank() && styles.none { it.id == id }) styles += StyleRef(id, row.styleId)
            row.copy(styleId = id)
        }
        return ExportCatalogSnapshot(styles, elements, songs, (editor + legacy).distinctBy { it.id })
    }

    private suspend fun exportSelected(selection: ExportPackageSelection?): ExchangeZipResult {
        val context = currentCoroutineContext()
        val full = library.load()
        val available = catalog(full)
        val selected = selection ?: ExportPackageSelection.autoCap(available)
        require(selected.validate().isEmpty()) { "invalid_export_selection" }
        require(selected.moveIds.all { id -> available.elements.any { it.id == id } } &&
            selected.songIds.all { id -> available.songs.any { it.id == id } } &&
            selected.choreographyIds.all { id -> available.choreographies.any { it.id == id } }) { "selected_material_missing" }
        val editorIds = choreographyBridge?.catalog().orEmpty().map { it.id }.toSet()
        val selectedEditor = selected.choreographyIds.filter { it in editorIds }.toSet()
        val payload = if (selectedEditor.isNotEmpty()) requireNotNull(choreographyBridge).export(selectedEditor) else null
        val dependencies = full.choreographies.filter { it.id in selected.choreographyIds && it.id !in editorIds }
        val expanded = selected.copy(
            moveIds = (selected.moveIds + dependencies.flatMap { it.timeline.values }.map { it.substringAfter("::", it) }).distinct(),
            songIds = (selected.songIds + dependencies.map { it.songId }.filter { it.isNotBlank() }).distinct())
        require(expanded.moveIds.all { id -> available.elements.any { it.id == id } } && expanded.songIds.all { id -> available.songs.any { it.id == id } }) { "choreography_dependency_missing" }
        val subset = ExportPackageSubset.apply(full, expanded)
        val workspace = IosArchiveWorkspace(privateRoot, storage)
        val archive = IosSecureZipArchive(privateRoot)
        var published: okio.Path? = null
        var success = false
        try {
            val sources = linkedMapOf<String, String>()
            fun add(name: String, raw: String) {
                require(name !in sources) { "duplicate_export_member" }
                sources[name] = mediaReferenceResolver(raw)
            }
            payload?.media.orEmpty().forEach { add(it.archivePath, it.sourceUri) }
            val moves = subset.danceStyles.flatMap { it.moves }
            moves.forEach { move -> add("videos/${move.id}.${extension(move.videoUri.orEmpty(), true)}", requireNotNull(move.videoUri?.takeIf { it.isNotBlank() }) { "selected_video_missing" }) }
            subset.songs.forEach { add("music/${it.id}.${extension(it.fileName, false)}", it.fileName) }
            payload?.let { validateEditorMedia(it.payloadJson, sources.keys) }
            val packedBytes = sources.values.sumOf { path -> workspace.fs.metadata(workspace.source(path)).size ?: error("media_unknown_size") }
            require(packedBytes <= PackageExportLimits.MAX_PACKAGE_BYTES) { "selected_media_exceeds_package_limit" }
            // Media copy + inner ZIP + encrypted output + verification ZIP all need private space.
            require(DiskSpaceGate.canImport(storage.freeBytes(), packedBytes * 4 + 8L * 1024 * 1024)) { "insufficient_free_space" }
            val staged = mutableMapOf<String, okio.Path>()
            val mediaSources = sources.entries.mapIndexed { index, (name, path) ->
                val copy = staged.getOrPut(path) { workspace.copyImmutable(path, "media-$index", PackageExportLimits.MAX_PACKAGE_BYTES) { context.ensureActive() } }
                IosZipSource(name, copy.toString())
            }
            val settings = subset.settings.toMutableMap().apply {
                remove(ChoreographyExchangeBridge.METADATA_KEY)
                payload?.let { put(ChoreographyExchangeBridge.METADATA_KEY, JsonPrimitive(it.payloadJson)) }
            }
            val portable = subset.copy(
                danceStyles = subset.danceStyles.map { style -> style.copy(moves = style.moves.map { move -> move.copy(videoUri = "videos/${move.id}.${extension(move.videoUri.orEmpty(), true)}") }) },
                songs = subset.songs.map { it.copy(fileName = "${it.id}.${extension(it.fileName, false)}") }, settings = JsonObject(settings))
            val editorStyles = available.choreographies.filter { it.id in selectedEditor }.map { it.styleId }.distinct().filter { it.isNotBlank() && portable.danceStyles.none { style -> style.id == it } }
            val metadata = DomainJson.encodeAppData(portable.copy(danceStyles = portable.danceStyles + editorStyles.map { id -> DanceStyle(id, available.styles.firstOrNull { it.id == id }?.name ?: id) }))
            require(metadata.encodeToByteArray().size <= 2 * 1024 * 1024) { "metadata_size_limit" }
            // Validate exact archive editor envelope before any ciphertext can be returned.
            IosArchiveInspector.project(metadata)
            val metadataPath = workspace.text("metadata.json", metadata)
            val manifest = workspace.text("manifest.json", """{"format":"midnight-dancer-full-backup","version":1}""")
            val zip = workspace.directory / "package.zip"
            archive.writeStoredArchive(zip.toString(), mediaSources + listOf(IosZipSource("metadata.json", metadataPath.toString()), IosZipSource("manifest.json", manifest.toString()))) { context.ensureActive() }
            val outputDirectory = workspace.root / "exchange" / "archives"
            workspace.privateDirectory(outputDirectory)
            val output = outputDirectory / (NSUUID().UUIDString + ".dance")
            published = output
            containerCodec.encryptPrivateZip(zip.toString(), output.toString(), bundleId) { !context.isActive }
            context.ensureActive()
            require((workspace.fs.metadata(output).size ?: Long.MAX_VALUE) <= PackageExportLimits.MAX_PACKAGE_BYTES) { "package_too_large" }
            val verified = workspace.directory / "verified.zip"
            containerCodec.decryptToPrivateZip(output.toString(), verified.toString(), bundleId) { !context.isActive }
            context.ensureActive()
            require(workspace.fs.metadata(zip).size == workspace.fs.metadata(verified).size) { "export_verification_mismatch" }
            workspace.fs.source(zip).buffer().use { expected ->
                workspace.fs.source(verified).buffer().use { actual ->
                    var remaining = workspace.fs.metadata(zip).size ?: error("export_unknown_size")
                    while (remaining > 0) {
                        context.ensureActive()
                        val count = minOf(65536L, remaining)
                        require(expected.readByteArray(count).contentEquals(actual.readByteArray(count))) { "export_verification_mismatch" }
                        remaining -= count
                    }
                }
            }
            require(archive.metadata(verified.toString()) == metadata) { "export_verification_mismatch" }
            success = true
            return ExchangeZipResult.Ok("Архив сета подготовлен", output.toString(), workspace.fs.metadata(output).size ?: 0L, sources.size)
        } finally {
            if (!success) published?.let { workspace.fs.delete(it, mustExist = false) }
            workspace.close()
        }
    }
    private fun validateEditorMedia(raw: String, packed: Set<String>) {
        val document = Json.parseToJsonElement(raw).jsonObject
        for (item in document.getValue("items").jsonArray) {
            val snapshot = item.jsonObject["snapshot"]
            if (snapshot == null || snapshot == JsonNull) continue
            require(snapshot is JsonObject) { "invalid_editor_snapshot" }
            for (lane in listOf("elementClips", "musicClips")) {
                val clips = snapshot[lane] ?: continue
                require(clips is JsonArray) { "invalid_editor_clips" }
                for (clip in clips) {
                    require(clip is JsonObject) { "invalid_editor_clip" }
                    val reference = clip["mediaUri"] ?: continue
                    if (reference == JsonNull) continue
                    require(reference is JsonPrimitive && reference.isString) { "invalid_editor_media_reference" }
                    if (reference.content.isNotBlank()) require(reference.content in packed) { "editor_media_not_packed" }
                }
            }
        }
    }
    private fun extension(reference: String, video: Boolean): String {
        val extension = reference.substringAfterLast('.', "").lowercase()
        val supported = if (video) setOf("mp4", "mov") else setOf("mp3", "m4a", "wav", "aac", "ogg", "flac")
        require(extension in supported) { "unsupported_media_extension" }
        return extension
    }
}
