package com.midnightdancer.core.data.exchange

import com.midnightdancer.core.data.files.AppFileLayout
import com.midnightdancer.core.data.files.MediaFileKind
import com.midnightdancer.core.data.files.MediaStorage
import com.midnightdancer.core.data.migrate.catalog.CatalogExporter
import com.midnightdancer.core.data.zip.AndroidZipMetadataReader
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.exchange.CatalogPickItem
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
import kotlinx.coroutines.withContext
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
) : ExchangeZipUseCase {

    override suspend fun export(selection: ExportPackageSelection?): ExchangeZipResult =
        withContext(Dispatchers.IO) {
            runCatching { exportBlocking(selection) }.getOrElse { e ->
                ExchangeZipResult.Failed(
                    message = "Export failed",
                    reasons = listOf(e.message ?: e::class.simpleName ?: "export_error"),
                )
            }
        }

    override suspend fun import(
        preferredPath: String?,
        contentFilter: ImportContentFilter,
        stylePlan: StyleImportPlan,
    ): ExchangeZipResult =
        withContext(Dispatchers.IO) {
            runCatching { importBlocking(preferredPath, contentFilter, stylePlan) }.getOrElse { e ->
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
            val elements =
                data.danceStyles.flatMap { style ->
                    style.moves.map { m ->
                        CatalogPickItem(
                            id = m.id,
                            title = m.name.ifBlank { m.id },
                            subtitle = style.name,
                            styleId = style.id,
                        )
                    }
                }
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
                    )
                }
            val choreos =
                data.choreographies.map { c ->
                    CatalogPickItem(
                        id = c.id,
                        title = c.name.ifBlank { c.id },
                        subtitle = styleNameById[c.styleId] ?: c.styleId,
                        styleId = c.styleId,
                    )
                }
            ExportCatalogSnapshot(
                styles = styles,
                elements = elements,
                songs = songs,
                choreographies = choreos,
            )
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
        val sel =
            selection
                ?: ExportPackageSelection.autoCap(snapshotFromAppData(full))
        val invalid = sel.validate()
        if (invalid.isNotEmpty()) {
            return ExchangeZipResult.Failed(
                message = "Export rejected · лимит пакета",
                reasons = invalid,
            )
        }
        val appData = ExportPackageSubset.apply(full, sel)

        val outFile = File(exchangeDir, ExchangeZipLimits.EXPORT_FILE_NAME)
        val zipTmp = File(exchangeDir, "debug_export.inner.zip.tmp")
        if (zipTmp.exists()) zipTmp.delete()

        val packedMoveIds = linkedSetOf<String>()
        var mediaBytes = 0L
        var mediaCount = 0

        ZipOutputStream(FileOutputStream(zipTmp)).use { zos ->
            zos.putUtf8(
                "manifest.json",
                """{"format":"${ExchangeZipLimits.FORMAT_ID}","version":${ExchangeZipLimits.FORMAT_VERSION}}""",
            )

            val videoSources = collectVideoSources(appData)
            for ((moveId, src) in videoSources) {
                if (mediaBytes >= ExchangeZipLimits.MAX_MEDIA_BYTES) break
                val len = src.length()
                if (len <= 0L) continue
                if (mediaBytes + len > ExchangeZipLimits.MAX_MEDIA_BYTES) continue
                zos.putFile("videos/$moveId.mp4", src)
                mediaBytes += len
                mediaCount++
                packedMoveIds += moveId
            }

            for (song in appData.songs) {
                if (mediaBytes >= ExchangeZipLimits.MAX_MEDIA_BYTES) break
                val ext = extensionOf(song.fileName)
                val src =
                    runCatching {
                        File(mediaStorage.mediaFilePath(song.id, MediaFileKind.MUSIC, ext))
                    }.getOrNull() ?: continue
                if (!src.isFile) continue
                val len = src.length()
                if (len <= 0L) continue
                if (mediaBytes + len > ExchangeZipLimits.MAX_MEDIA_BYTES) continue
                zos.putFile("music/${song.id}.$ext", src)
                mediaBytes += len
                mediaCount++
            }

            val exportData = rewritePackedVideoUris(appData, packedMoveIds)
            zos.putUtf8("metadata.json", DomainJson.encodeAppData(exportData))
        }

        check(zipTmp.isFile) { "cannot_finalize_inner_zip" }
        MdanceContainer.wrapZipFileToMdance(zipTmp, outFile, packageName)
        zipTmp.delete()
        check(outFile.isFile) { "cannot_finalize_mdance" }

        val size = outFile.length()
        if (size > PackageExportLimits.MAX_PACKAGE_BYTES) {
            outFile.delete()
            return ExchangeZipResult.Failed(
                message = "Export rejected · пакет слишком большой",
                reasons = listOf("package_too_large:$size"),
            )
        }

        return ExchangeZipResult.Ok(
            message = "Export OK · ${outFile.name} · ${formatMb(size)} · media=$mediaCount",
            archivePath = outFile.absolutePath,
            sizeBytes = size,
            mediaFilesPacked = mediaCount,
        )
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
                val decrypted = File(exchangeDirectory(), "import_decrypt.inner.zip.tmp")
                runCatching {
                    MdanceContainer.unwrapMdanceToZipFile(archive, decrypted, packageName)
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
        return try {
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
                    saveAppData.save(result.data)
                    ExchangeZipResult.Ok(
                        message = "Import OK · ${archive.name} · ${formatMb(size)}",
                        archivePath = archive.absolutePath,
                        sizeBytes = size,
                    )
                }
            }
        } finally {
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
            val zip =
                if (MdanceContainer.isMdance(archive)) {
                    val decrypted = File(exchangeDirectory(), "peek_decrypt.inner.zip.tmp")
                    MdanceContainer.unwrapMdanceToZipFile(archive, decrypted, packageName)
                    zipForPeek = decrypted
                    decrypted
                } else {
                    archive
                }
            val raw = metadataReader.readMetadataJson(zip.absolutePath)
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
        }
    }

    private fun snapshotFromAppData(data: AppData): ExportCatalogSnapshot {
        val styles =
            data.danceStyles.map { StyleRef(id = it.id, name = it.name.ifBlank { it.id }) }
        val elements =
            data.danceStyles.flatMap { style ->
                style.moves.map { m ->
                    CatalogPickItem(
                        id = m.id,
                        title = m.name,
                        subtitle = style.name,
                        styleId = style.id,
                    )
                }
            }
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
                    )
                },
            choreographies =
                data.choreographies.map {
                    CatalogPickItem(
                        id = it.id,
                        title = it.name,
                        subtitle = it.styleId,
                        styleId = it.styleId,
                    )
                },
        )
    }

    private fun resolveImportArchive(preferredPath: String?): File? {
        if (!preferredPath.isNullOrBlank()) {
            val f = File(preferredPath)
            if (f.isFile) return f
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

    private fun collectVideoSources(appData: AppData): List<Pair<String, File>> {
        val out = ArrayList<Pair<String, File>>()
        for (style in appData.danceStyles) {
            for (move in style.moves) {
                val src = resolveVideoFile(move) ?: continue
                out += move.id to src
            }
        }
        return out
    }

    private fun resolveVideoFile(move: Move): File? {
        val uri = move.videoUri?.trim().orEmpty()
        if (uri.startsWith("/") || uri.contains(":\\") || uri.contains(":/")) {
            if (uri.startsWith("content:", ignoreCase = true)) return null
            val f = File(uri)
            if (f.isFile) return f
        }
        if (uri.isNotEmpty() && !uri.contains('/') && !uri.contains('\\')) {
            val byId =
                runCatching {
                    File(mediaStorage.mediaFilePath(uri.substringBeforeLast('.'), MediaFileKind.VIDEO))
                }.getOrNull()
            if (byId != null && byId.isFile) return byId
        }
        val byMoveId =
            runCatching {
                File(mediaStorage.mediaFilePath(move.id, MediaFileKind.VIDEO))
            }.getOrNull()
        return byMoveId?.takeIf { it.isFile }
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
        val i = fileName.lastIndexOf('.')
        val ext =
            if (i >= 0 && i < fileName.length - 1) {
                fileName.substring(i + 1).lowercase()
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
