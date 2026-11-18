package com.midnightdancer.core.data.exchange

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import android.util.Log

import com.midnightdancer.core.data.files.AppFileLayout
import com.midnightdancer.core.data.files.MediaFileKind
import com.midnightdancer.core.data.files.MediaStorage
import com.midnightdancer.core.data.migrate.catalog.CatalogExporter
import com.midnightdancer.core.data.zip.AndroidZipMetadataReader
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.entity.Song
import com.midnightdancer.core.domain.exchange.CatalogPickItem
import com.midnightdancer.core.domain.exchange.ChoreographyExchangeBridge
import com.midnightdancer.core.domain.exchange.ExportCatalogSnapshot
import com.midnightdancer.core.domain.exchange.ExportPackageSelection
import com.midnightdancer.core.domain.exchange.ImportPeek
import com.midnightdancer.core.domain.exchange.PackageExportLimits
import com.midnightdancer.core.domain.exchange.StyleImportPlan
import com.midnightdancer.core.domain.exchange.StyleRef
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.usecase.ExchangeZipResult
import com.midnightdancer.core.domain.usecase.ExchangeZipUseCase
import com.midnightdancer.core.domain.usecase.ImportContentFilter
import com.midnightdancer.core.domain.usecase.ImportSecureZip
import com.midnightdancer.core.domain.usecase.ImportSecureZipResult
import com.midnightdancer.core.domain.usecase.SaveAppData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Real exchange under app files — Android (C-107 / C-114 / C-115).
 */
class AndroidDebugExchangeZipUseCase(
    private val catalogExporter: CatalogExporter,
    private val mediaStorage: MediaStorage,
    private val importSecureZip: ImportSecureZip,
    private val saveAppData: SaveAppData,
    private val packageName: String,
    private val deviceStorage: com.midnightdancer.core.domain.storage.DeviceStorage,
    private val metadataReader: AndroidZipMetadataReader = AndroidZipMetadataReader(),
    private val contentResolver: ContentResolver? = null,
    private val choreographyBridge: ChoreographyExchangeBridge? = null,
    private val debugDiagnostics: Boolean = false,
    private val mediaReferenceResolver: (String) -> String = { it },
) : ExchangeZipUseCase {
    private val exportMutex = Mutex()

    override suspend fun export(selection: ExportPackageSelection?): ExchangeZipResult =
        withContext(Dispatchers.IO) {
            exportMutex.withLock {
                try {
                    exportBlocking(selection)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    if (debugDiagnostics) Log.e("MdExchangeQA", "export-failed type=${e.javaClass.simpleName}")
                    ExchangeZipResult.Failed(
                        message = "Не удалось отправить сет",
                        reasons = listOf(e.message ?: e::class.simpleName ?: "export_error"),
                    )
                } finally {
                    // Also reclaim an interrupted archive left by an older app version.
                    runCatching { File(exchangeDirectory(), "debug_export.inner.zip.tmp").delete() }
                }
            }
        }

    override suspend fun import(
        preferredPath: String?,
        contentFilter: ImportContentFilter,
        stylePlan: StyleImportPlan,
    ): ExchangeZipResult =
        withContext(Dispatchers.IO) {
            runCatching { importBlocking(preferredPath, contentFilter, stylePlan) }.getOrElse { e ->
                if (e is CancellationException) throw e
                ExchangeZipResult.Failed(
                    message = "Import failed",
                    reasons = listOf(e.message ?: e::class.simpleName ?: "import_error"),
                )
            }
        }

    override suspend fun peekImport(preferredPath: String?): ImportPeek? =
        withContext(Dispatchers.IO) {
            runCatching { peekBlocking(preferredPath) }.getOrNull()
        }

    override suspend fun loadExportCatalog(): ExportCatalogSnapshot =
        withContext(Dispatchers.IO) {
            val data = catalogExporter.exportAppData()
            val catalogStyles =
                data.danceStyles.map { StyleRef(id = it.id, name = it.name.ifBlank { it.id }) }
            // Ensure dropdown shows several styles even if catalog is sparse (C-118).
            val defaults =
                com.midnightdancer.core.domain.settings.DanceStyleConfig.DEFAULT_NAMES.map { name ->
                    val id =
                        catalogStyles.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                            ?: name.lowercase().replace(' ', '-')
                    StyleRef(id = id, name = name)
                }
            val styles =
                (catalogStyles + defaults)
                    .distinctBy { it.id.lowercase() }
                    .distinctBy { it.name.lowercase() }
            val styleNameById = styles.associate { it.id to it.name }
            val songBytesById = data.songs.associate { it.id to actualSongBytes(it) }
            val elements =
                data.danceStyles.flatMap { style ->
                    style.moves.map { m ->
                        CatalogPickItem(
                            id = m.id,
                            title = m.name.ifBlank { m.id },
                            subtitle = style.name,
                            styleId = style.id,
                            approximateBytes = actualMoveBytes(m),
                        )
                    }
                }
            val moveBytesById = elements.associate { it.id to it.approximateBytes }
            val songs =
                data.songs.map { s ->
                    val styleId =
                        data.danceStyles
                            .firstOrNull {
                                it.id == s.danceStyle ||
                                    it.name.equals(s.danceStyle, ignoreCase = true)
                            }?.id
                            ?: s.danceStyle
                    CatalogPickItem(
                        id = s.id,
                        title = s.title.ifBlank { s.id },
                        subtitle = styleNameById[styleId] ?: s.danceStyle,
                        styleId = styleId,
                        approximateBytes = songBytesById[s.id] ?: 0L,
                    )
                }
            val legacyChoreos =
                data.choreographies.map { c ->
                    val songPart = songBytesById[c.songId] ?: 0L
                    val movePart =
                        c.timeline.values
                            .map { ref -> ref.substringAfter("::", ref) }
                            .distinct()
                            .sumOf { moveBytesById[it] ?: 0L }
                    CatalogPickItem(
                        id = c.id,
                        title = c.name.ifBlank { c.id },
                        subtitle = styleNameById[c.styleId] ?: c.styleId,
                        styleId = c.styleId,
                        approximateBytes = songPart + movePart,
                    )
                }
            // Editor snapshots store display names ("Sensual"), while the catalog may use
            // stable IDs ("sensual"). Normalize picker rows only; the bridge payload stays exact.
            val allStyles = styles.toMutableList()
            val editorChoreos = choreographyBridge?.catalog().orEmpty().map { row ->
                val style = row.styleId.trim()
                val existing = allStyles.firstOrNull {
                    it.id.equals(style, ignoreCase = true) || it.name.equals(style, ignoreCase = true)
                }
                val canonical = existing ?: StyleRef(style, style).also {
                    if (style.isNotEmpty()) allStyles += it
                }
                row.copy(styleId = canonical.id)
            }
            ExportCatalogSnapshot(
                styles = allStyles,
                elements = elements,
                songs = songs,
                choreographies = (editorChoreos + legacyChoreos).distinctBy { it.id },
            )
        }

    override suspend fun inspectArchive(archivePath: String): ExportCatalogSnapshot =
        withContext(Dispatchers.IO) {
            val context = currentCoroutineContext()
            AndroidArchiveInspector(File(mediaStorage.rootDirectory()), deviceStorage, contentResolver, packageName)
                .inspect(archivePath) { context.ensureActive() }
        }

    private suspend fun exportBlocking(selection: ExportPackageSelection?): ExchangeZipResult {
        mediaStorage.ensureLayout()
        val exchangeDir = exchangeDirectory()
        if (!exchangeDir.exists()) {
            check(exchangeDir.mkdirs() || exchangeDir.isDirectory) {
                "cannot_create_exchange_dir"
            }
        }
        val full = catalogExporter.exportAppData()
        val editorCatalog = choreographyBridge?.catalog().orEmpty()
        val sel = selection ?: ExportPackageSelection.autoCap(snapshotFromAppData(full).let { snapshot ->
            snapshot.copy(choreographies = (editorCatalog + snapshot.choreographies).distinctBy { it.id })
        })
        val invalid = sel.validate()
        if (invalid.isNotEmpty()) {
            return ExchangeZipResult.Failed(
                message = "Export rejected · лимит сета",
                reasons = invalid,
            )
        }
        val editorIds = editorCatalog.map { it.id }.toSet()
        val selectedEditorIds = sel.choreographyIds.filter { it in editorIds }.toSet()
        val editorPayload = if (selectedEditorIds.isNotEmpty()) choreographyBridge?.export(selectedEditorIds) else null
        val dependencyChoreos = full.choreographies.filter { it.id in sel.choreographyIds && it.id !in editorIds }
        val dependencyMoveIds = dependencyChoreos.flatMap { it.timeline.values }
            .map { it.substringAfter("::", it) }.toSet()
        val expanded = sel.copy(
            moveIds = (sel.moveIds + dependencyMoveIds).distinct(),
            songIds = (sel.songIds + dependencyChoreos.map { it.songId }.filter { it.isNotBlank() }).distinct(),
        )
        val subset = ExportPackageSubset.apply(full, expanded)
        val settings = subset.settings.toMutableMap().apply {
            remove(ChoreographyExchangeBridge.METADATA_KEY)
            editorPayload?.let { put(ChoreographyExchangeBridge.METADATA_KEY, JsonPrimitive(it.payloadJson)) }
        }
        val editorStyles = editorCatalog.filter { it.id in selectedEditorIds }.map { it.styleId }
            .filter { it.isNotBlank() }.distinct()
            .filter { style -> subset.danceStyles.none { it.id == style } }
            .map { com.midnightdancer.core.domain.entity.DanceStyle(it, it) }
        val appData = subset.copy(settings = JsonObject(settings), danceStyles = subset.danceStyles + editorStyles)

        val outFile = File(exchangeDir, ExchangeZipLimits.EXPORT_FILE_NAME)
        val zipTmp = File(exchangeDir, "debug_export.inner.zip.tmp")
        val candidate = File(exchangeDir, "export-${java.util.UUID.randomUUID()}.candidate.dance")
        if (zipTmp.exists()) zipTmp.delete()

        return try {
        // Resolve every selected source before spending time packing a large video.
        val videoSources = collectVideoSources(appData)
        val songSources = appData.songs.associate { it.id to resolveSongSource(it) }
        (videoSources.map { it.second } + songSources.values).distinct().forEach { source ->
            openMediaSource(source).use { /* verify persisted SAF permission / readability */ }
        }

        val packedMoveIds = linkedSetOf<String>()
        var mediaBytes = 0L
        var mediaCount = 0

        ZipOutputStream(FileOutputStream(zipTmp)).use { zos ->
            zos.putUtf8(
                "manifest.json",
                """{"format":"${ExchangeZipLimits.FORMAT_ID}","version":${ExchangeZipLimits.FORMAT_VERSION}}""",
            )

            for (media in editorPayload?.media.orEmpty()) {
                val bytes = putMediaSource(zos, media.archivePath, media.sourceUri, mediaBytes)
                mediaBytes += bytes
                mediaCount++
            }

            for ((moveId, src) in videoSources) {
                val len = putMediaSource(zos, "videos/$moveId.mp4", src, mediaBytes)
                mediaBytes += len
                mediaCount++
                packedMoveIds += moveId
            }

            for (song in appData.songs) {
                val ext = extensionOf(song.fileName)
                val len = putMediaSource(zos, "music/${song.id}.$ext", songSources.getValue(song.id), mediaBytes)
                mediaBytes += len
                mediaCount++
            }

            val portableSongs = appData.songs.map { song -> song.copy(fileName = "${song.id}.${extensionOf(song.fileName)}") }
            val exportData = rewritePackedVideoUris(appData.copy(songs = portableSongs), packedMoveIds)
            zos.putUtf8("metadata.json", DomainJson.encodeAppData(exportData))
        }

        check(zipTmp.isFile) { "cannot_finalize_inner_zip" }
        MdanceContainer.wrapZipFileToMdance(zipTmp, candidate, packageName, ::containerDiagnostic)
        check(candidate.isFile) { "cannot_finalize_mdance" }

        val size = candidate.length()
        if (size > PackageExportLimits.MAX_PACKAGE_BYTES) {
            return ExchangeZipResult.Failed(
                message = "Export rejected · сет слишком большой",
                reasons = listOf("package_too_large:$size"),
            )
        }

        // Preserve the previous successful archive until the full replacement is durable.
        val atomic = AtomicFile(outFile)
        val output = atomic.startWrite()
        try {
            candidate.inputStream().use { it.copyTo(output) }
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }

        ExchangeZipResult.Ok(
            message = "Export OK · ${outFile.name} · ${formatMb(size)} · media=$mediaCount",
            archivePath = outFile.absolutePath,
            sizeBytes = size,
            mediaFilesPacked = mediaCount,
        )
        } finally {
            zipTmp.delete()
            candidate.delete()
            File(candidate.parentFile, candidate.name + ".tmp").delete()
        }
    }

    private suspend fun importBlocking(
        preferredPath: String?,
        contentFilter: ImportContentFilter,
        stylePlan: StyleImportPlan,
    ): ExchangeZipResult {
        mediaStorage.ensureLayout()
        val archive =
            resolveImportArchive(preferredPath)
                ?: return ExchangeZipResult.Failed(
                    message = "Import failed",
                    reasons = listOf("no_archive_found"),
                )

        if (!archive.isFile) {
            return ExchangeZipResult.Failed(
                message = "Import failed",
                reasons = listOf("archive_not_file:${archive.absolutePath}"),
            )
        }
        val size = archive.length()
        if (size <= 0L) {
            return ExchangeZipResult.Failed(
                message = "Import rejected",
                reasons = listOf("empty_archive_file"),
            )
        }
        if (size > ExchangeZipLimits.MAX_ARCHIVE_BYTES) {
            return ExchangeZipResult.Failed(
                message = "Import rejected",
                reasons = listOf("archive_too_large:$size"),
            )
        }

        val free = deviceStorage.freeBytes()
        val need =
            com.midnightdancer.core.domain.storage.DiskSpaceGate.requiredWithBuffer(size)
        if (!com.midnightdancer.core.domain.storage.DiskSpaceGate.canImport(free, size)) {
            return ExchangeZipResult.Failed(
                message = "Import rejected · мало места",
                reasons =
                    listOf(
                        "insufficient_free_space:free=$free:need=$need",
                    ),
            )
        }

        val zipForImport: File =
            if (MdanceContainer.isMdance(archive)) {
                val decrypted = File(exchangeDirectory(), "${java.util.UUID.randomUUID()}-import.inner.zip.tmp")
                runCatching {
                    MdanceContainer.unwrapMdanceToZipFile(archive, decrypted, packageName, ::containerDiagnostic)
                }.getOrElse { e ->
                    return ExchangeZipResult.Failed(
                        message = "Import rejected",
                        reasons = listOf("mdance_decrypt_failed:${e.message}"),
                    )
                }
                decrypted
            } else {
                archive
            }

        val into = catalogExporter.exportAppData()
        var staged: StagedMedia? = null
        return try {
            val preparedMedia = AndroidExchangeArchiveFiles(File(mediaStorage.rootDirectory()), deviceStorage, contentResolver).stage(zipForImport)
            staged = preparedMedia
            when (
                val result =
                    importSecureZip.import(
                        zipForImport.absolutePath,
                        into,
                        contentFilter,
                        stylePlan,
                    )
            ) {
                is ImportSecureZipResult.Failed ->
                    ExchangeZipResult.Failed(
                        message = "Import rejected",
                        reasons = result.reasons,
                    )
                is ImportSecureZipResult.Success -> {
                    val rawImported = requireNotNull(metadataReader.readMetadataJson(zipForImport.absolutePath))
                    val importedData = DomainJson.decodeAppData(rawImported)
                    val editorRaw = importedData.settings[ChoreographyExchangeBridge.METADATA_KEY]?.jsonPrimitive?.content
                    val styleRedirect = stylePlan.archiveStyleToLocalId.mapValues { (_, localId) ->
                        result.data.danceStyles.firstOrNull { it.id == localId }?.name ?: localId
                    }
                    val preparedVault = if (contentFilter.importChoreography && (editorRaw != null || importedData.choreographies.isNotEmpty())) {
                        if (editorRaw != null) requireNotNull(choreographyBridge) { "editor_import_bridge_unavailable" }
                        choreographyBridge?.prepareImport(editorRaw.orEmpty(), preparedMedia.resolvedPaths, styleRedirect, importedData)
                    } else null
                    val playableData = rebindNewCatalogMedia(result.data, into, preparedMedia.resolvedPaths)
                    val cleanData = playableData.copy(settings = JsonObject(playableData.settings - ChoreographyExchangeBridge.METADATA_KEY))
                    var catalogWriteAttempted = false
                    try {
                        preparedMedia.commit()
                        catalogWriteAttempted = true
                        saveAppData.save(cleanData)
                        currentCoroutineContext().ensureActive()
                        preparedVault?.commit()
                        currentCoroutineContext().ensureActive()
                    } catch (failure: Throwable) {
                        val recoveryErrors = mutableListOf<Throwable>()
                        withContext(NonCancellable) {
                            runCatching { preparedVault?.rollback() }.exceptionOrNull()?.let { recoveryErrors += it }
                            if (catalogWriteAttempted) {
                                runCatching { saveAppData.save(into) }.exceptionOrNull()?.let { recoveryErrors += it }
                            }
                            if (recoveryErrors.isEmpty()) preparedMedia.rollback()
                        }
                        if (recoveryErrors.isNotEmpty()) {
                            // The surviving catalog or vault may still reference committed files.
                            // Keep them for recovery rather than turning a rollback error into data loss.
                            val recovery = IllegalStateException("import_recovery_required: committed media retained", failure)
                            recoveryErrors.forEach { recovery.addSuppressed(it) }
                            throw recovery
                        }
                        throw failure
                    }
                    ExchangeZipResult.Ok(
                        message = "Import OK · ${archive.name} · ${formatMb(size)}",
                        archivePath = archive.absolutePath,
                        sizeBytes = size,
                    )
                }
            }
        } finally {
            staged?.close()
            if (zipForImport.name.endsWith(".inner.zip.tmp") && zipForImport.exists()) {
                zipForImport.delete()
            }
        }
    }

    private suspend fun peekBlocking(preferredPath: String?): ImportPeek {
        mediaStorage.ensureLayout()
        val localData = catalogExporter.exportAppData()
        val local =
            localData.danceStyles.map { StyleRef(id = it.id, name = it.name.ifBlank { it.id }) }
        val archive = resolveImportArchive(preferredPath)
        if (archive == null || !archive.isFile) {
            return ImportPeek(packageStyles = emptyList(), localStyles = local)
        }
        var zipForPeek: File? = null
        return try {
            require(archive.length() in 1..ExchangeZipLimits.MAX_ARCHIVE_BYTES) { "invalid_archive_size" }
            val zip =
                if (MdanceContainer.isMdance(archive)) {
                    val decrypted = File(exchangeDirectory(), "${java.util.UUID.randomUUID()}-peek.inner.zip.tmp")
                    MdanceContainer.unwrapMdanceToZipFile(archive, decrypted, packageName, ::containerDiagnostic)
                    zipForPeek = decrypted
                    decrypted
                } else {
                    archive
                }
            val raw = AndroidExchangeArchiveFiles(File(mediaStorage.rootDirectory()), deviceStorage, contentResolver)
                .readMetadataForPeek(zip)
            if (raw.isNullOrBlank()) {
                return ImportPeek(packageStyles = emptyList(), localStyles = local)
            }
            val decoded =
                runCatching { DomainJson.decodeAppData(raw) }.getOrNull()
                    ?: return ImportPeek(packageStyles = emptyList(), localStyles = local)
            val packageStyles =
                decoded.danceStyles.map {
                    StyleRef(id = it.id, name = it.name.ifBlank { it.id })
                }
            ImportPeek(packageStyles = packageStyles, localStyles = local)
        } catch (_: Throwable) {
            ImportPeek(packageStyles = emptyList(), localStyles = local)
        } finally {
            zipForPeek?.takeIf { it.exists() }?.delete()
            if (preferredPath?.startsWith("content://", ignoreCase = true) == true) archive.delete()
        }
    }

    private fun snapshotFromAppData(data: AppData): ExportCatalogSnapshot {
        val styles =
            data.danceStyles.map { StyleRef(id = it.id, name = it.name.ifBlank { it.id }) }
        val songBytesById = data.songs.associate { it.id to actualSongBytes(it) }
        val elements =
            data.danceStyles.flatMap { style ->
                style.moves.map { m ->
                    CatalogPickItem(
                        id = m.id,
                        title = m.name,
                        subtitle = style.name,
                        styleId = style.id,
                        approximateBytes = actualMoveBytes(m),
                    )
                }
            }
        val moveBytesById = elements.associate { it.id to it.approximateBytes }
        return ExportCatalogSnapshot(
            styles = styles,
            elements = elements,
            songs =
                data.songs.map {
                    CatalogPickItem(
                        id = it.id,
                        title = it.title,
                        subtitle = it.danceStyle,
                        styleId = it.danceStyle,
                        approximateBytes = songBytesById[it.id] ?: 0L,
                    )
                },
            choreographies =
                data.choreographies.map { c ->
                    val songPart = songBytesById[c.songId] ?: 0L
                    val movePart =
                        c.timeline.values
                            .map { ref -> ref.substringAfter("::", ref) }
                            .distinct()
                            .sumOf { moveBytesById[it] ?: 0L }
                    CatalogPickItem(
                        id = c.id,
                        title = c.name,
                        subtitle = c.styleId,
                        styleId = c.styleId,
                        approximateBytes = songPart + movePart,
                    )
                },
        )
    }

    private fun resolveImportArchive(preferredPath: String?): File? {
        if (!preferredPath.isNullOrBlank()) {
            if (preferredPath.startsWith("content://", ignoreCase = true)) {
                return AndroidExchangeArchiveFiles(File(mediaStorage.rootDirectory()), deviceStorage, contentResolver)
                    .copyContentArchive(preferredPath)
            }
            val uri = Uri.parse(preferredPath)
            val f = File(if (uri.scheme == "file") uri.path.orEmpty() else preferredPath)
            require(f.isFile) { "selected_archive_not_found" }
            return f
        }
        val dir = exchangeDirectory()
        val candidates =
            listOf(
                File(dir, ExchangeZipLimits.IMPORT_INBOX_FILE_NAME),
                File(dir, ExchangeZipLimits.IMPORT_INBOX_ZIP_NAME),
                File(dir, ExchangeZipLimits.EXPORT_FILE_NAME),
                File(dir, ExchangeZipLimits.LEGACY_EXPORT_ZIP_NAME),
            )
        return candidates.firstOrNull { it.isFile }
    }

    private fun exchangeDirectory(): File =
        File(mediaStorage.rootDirectory(), ExchangeZipLimits.EXCHANGE_DIR)

    private fun containerDiagnostic(message: String) {
        if (debugDiagnostics) Log.i("MdExchangeQA", message)
    }

    private fun openMediaSource(rawSource: String): java.io.InputStream {
        val source = mediaReferenceResolver(rawSource)
        // Robolectric runs on Windows too: a drive letter is a local path, not a URI provider.
        val direct = File(source)
        if (direct.isAbsolute && direct.isFile) return direct.inputStream()
        val uri = Uri.parse(source)
        return if (uri.scheme == "content") {
            requireNotNull(contentResolver?.openInputStream(uri)) { "cannot_open_editor_media" }
        } else {
            require(uri.scheme == null || uri.scheme == "file") { "unsupported_editor_media_uri" }
            File(if (uri.scheme == "file") uri.path.orEmpty() else source).inputStream()
        }
    }

    private fun collectVideoSources(appData: AppData): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (style in appData.danceStyles) {
            for (move in style.moves) {
                val source = resolveMoveSource(move) ?: continue
                out += move.id to source
            }
        }
        return out
    }

    private fun putMediaSource(zip: ZipOutputStream, path: String, source: String, packedBytes: Long): Long {
        var copied = 0L
        openMediaSource(source).use { input ->
            zip.putNextEntry(ZipEntry(path))
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                copied += count
                require(packedBytes + copied <= ExchangeZipLimits.MAX_MEDIA_BYTES) { "selected_media_exceeds_package_limit" }
                zip.write(buffer, 0, count)
            }
            zip.closeEntry()
        }
        require(copied > 0L) { "selected_media_empty:$path" }
        return copied
    }

    private fun resolveSongSource(song: Song): String {
        val reference = mediaReferenceResolver(song.fileName.trim())
        explicitSource(reference, "selected_song_media_missing:${song.id}")?.let { return it }
        val basename = safeMediaBasename(reference, "music")
        val ext = extensionOf(reference)
        val names = listOfNotNull(basename, "${song.id}.$ext").distinct()
        return knownMediaFiles("music", names).firstOrNull { it.isFile }?.absolutePath
            ?: error("selected_song_media_missing:${song.id}")
    }

    private fun resolveMoveSource(move: Move): String? {
        val reference = mediaReferenceResolver(move.videoUri?.trim().orEmpty())
        explicitSource(reference, "selected_move_media_missing:${move.id}")?.let { return it }
        val basename = safeMediaBasename(reference, "videos")
        val explicitName = basename?.let { if (it.contains('.')) it else "$it.mp4" }
        val names = listOfNotNull(explicitName, "${move.id}.mp4").distinct()
        val found = knownMediaFiles("videos", names).firstOrNull { it.isFile }
        require(found != null || reference.isBlank()) { "selected_move_media_missing:${move.id}" }
        return found?.absolutePath
    }

    /** A stored SAF/file URI is authoritative. Never substitute another track when it is unreadable. */
    private fun explicitSource(reference: String, missingReason: String): String? {
        if (reference.isBlank()) return null
        if (reference.startsWith("content://", ignoreCase = true)) return reference
        val direct = File(reference)
        if (direct.isAbsolute) {
            require(direct.isFile) { missingReason }
            return direct.absolutePath
        }
        val uri = Uri.parse(reference)
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val file = File(uri.path.orEmpty())
            require(file.isFile) { missingReason }
            return file.absolutePath
        }
        require(uri.scheme == null) { "unsupported_selected_media_uri" }
        return null
    }

    private fun safeMediaBasename(reference: String, kind: String): String? {
        if (reference.isBlank()) return null
        val name = reference.removePrefix("$kind/")
        require(name.isNotBlank() && '/' !in name && '\\' !in name && ':' !in name && ".." !in name) {
            "unsafe_selected_media_reference"
        }
        return name
    }

    private fun knownMediaFiles(kind: String, names: List<String>): List<File> {
        val root = File(mediaStorage.rootDirectory())
        val applicationRoot = root.parentFile?.parentFile
        val roots = listOfNotNull(
            File(root, kind),
            applicationRoot?.let { File(it, "app_flutter/MidnightDancer/$kind") },
        )
        // Exact recorded basename first, ID-based layout only for old metadata without a URI.
        return names.flatMap { name ->
            require(name.isNotBlank() && '/' !in name && '\\' !in name && ':' !in name && ".." !in name) {
                "unsafe_selected_media_id"
            }
            roots.map { File(it, name) }
        }
    }

    private fun actualSongBytes(song: Song): Long =
        runCatching { sourceSize(resolveSongSource(song)) }.getOrNull()
            ?: song.sizeBytes.toLong().coerceAtLeast(0L)

    /** Only newly imported entities get trusted local paths; pre-existing catalog references stay intact. */
    private fun rebindNewCatalogMedia(data: AppData, before: AppData, mediaPaths: Map<String, String>): AppData {
        val existingSongs = before.songs.map { it.id }.toSet()
        val existingMoves = before.danceStyles.flatMap { it.moves }.map { it.id }.toSet()
        return data.copy(
            songs = data.songs.map { song ->
                if (song.id in existingSongs) song else {
                    val path = requireNotNull(mediaPaths["music/${song.id}.${extensionOf(song.fileName)}"]) {
                        "imported_song_media_missing:${song.id}"
                    }
                    song.copy(fileName = path)
                }
            },
            danceStyles = data.danceStyles.map { style ->
                style.copy(moves = style.moves.map { move ->
                    val videoReference = move.videoUri
                    if (move.id in existingMoves || videoReference.isNullOrBlank()) move else {
                        val ext = if (videoReference.endsWith(".mov", ignoreCase = true)) "mov" else "mp4"
                        val path = requireNotNull(mediaPaths["videos/${move.id}.$ext"]) {
                            "imported_move_media_missing:${move.id}"
                        }
                        move.copy(videoUri = path)
                    }
                })
            },
        )
    }

    private fun actualMoveBytes(move: Move): Long =
        runCatching { resolveMoveSource(move)?.let(::sourceSize) }.getOrNull() ?: 0L

    private fun sourceSize(rawSource: String): Long? {
        val source = mediaReferenceResolver(rawSource)
        val file = File(source)
        if (file.isAbsolute && file.isFile) return file.length()
        val uri = Uri.parse(source)
        if (uri.scheme == "file") return File(uri.path.orEmpty()).takeIf { it.isFile }?.length()
        if (uri.scheme != "content") return null
        val descriptorSize = runCatching {
            contentResolver?.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { size -> size >= 0L } }
        }.getOrNull()
        if (descriptorSize != null) return descriptorSize
        return runCatching {
            contentResolver?.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it >= 0L } else null
            }
        }.getOrNull()
    }

    private fun rewritePackedVideoUris(
        data: AppData,
        packedMoveIds: Set<String>,
    ): AppData {
        val styles =
            data.danceStyles.map { style ->
                style.copy(
                    moves =
                        style.moves.map { m ->
                            if (m.id in packedMoveIds) m.copy(videoUri = m.id) else m.copy(videoUri = null)
                        },
                )
            }
        return data.copy(danceStyles = styles)
    }

    private fun extensionOf(fileName: String): String {
        val leaf = Uri.parse(fileName).lastPathSegment ?: fileName.substringBefore('?').substringBefore('#')
        val i = leaf.lastIndexOf('.')
        val ext =
            if (i >= 0 && i < leaf.length - 1) {
                leaf.substring(i + 1).lowercase()
            } else {
                AppFileLayout.DEFAULT_MUSIC_EXTENSION
            }
        return if (ext in setOf("mp3", "m4a", "wav", "aac", "ogg", "flac")) ext else "mp3"
    }

    private fun formatMb(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1fMB", mb)
    }

    private fun ZipOutputStream.putUtf8(
        entryName: String,
        text: String,
    ) {
        putNextEntry(ZipEntry(entryName))
        write(text.encodeToByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.putFile(
        entryName: String,
        file: File,
    ) {
        putNextEntry(ZipEntry(entryName))
        BufferedInputStream(FileInputStream(file)).use { input ->
            input.copyTo(this)
        }
        closeEntry()
    }
}
