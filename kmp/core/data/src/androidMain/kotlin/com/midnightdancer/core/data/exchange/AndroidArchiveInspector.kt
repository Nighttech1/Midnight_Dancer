package com.midnightdancer.core.data.exchange

import android.content.ContentResolver
import com.midnightdancer.core.data.zip.SecureZipLimitsDefaults
import com.midnightdancer.core.data.zip.SecureZipMetadataLimits
import com.midnightdancer.core.data.zip.SecureZipSandbox
import com.midnightdancer.core.data.zip.SecureZipWhitelist
import com.midnightdancer.core.domain.exchange.CatalogPickItem
import com.midnightdancer.core.domain.exchange.ChoreographyExchangeBridge
import com.midnightdancer.core.domain.exchange.ExportCatalogSnapshot
import com.midnightdancer.core.domain.exchange.StyleRef
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.storage.DeviceStorage
import com.midnightdancer.core.domain.storage.DiskSpaceGate
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.serialization.json.*

/** Metadata projection only: never resolves media URIs, imports, or reads the user's catalog. */
internal class AndroidArchiveInspector(
    private val root: File,
    private val storage: DeviceStorage,
    private val resolver: ContentResolver?,
    private val packageName: String,
) {
    fun inspect(path: String, checkCancellation: () -> Unit = {}): ExportCatalogSnapshot {
        require(path.isNotBlank()) { "archive_missing" }
        checkCancellation()
        var picked: File? = null
        var temporary: File? = null
        try {
            val source = if (path.startsWith("content://", true)) {
                AndroidExchangeArchiveFiles(root, storage, resolver).copyContentArchive(path).also { picked = it }
            } else {
                require(!path.contains("://")) { "unsupported_archive_path" }
                File(path).also { require(it.isAbsolute) { "unsupported_archive_path" } }
            }
            require(source.isFile && source.canRead()) { "archive_missing" }
            require(source.length() in 1..(ExchangeZipLimits.MAX_ARCHIVE_BYTES + 36L)) { "invalid_archive_size" }
            val zip = if (MdanceContainer.isMdance(source)) {
                require(DiskSpaceGate.canImport(storage.freeBytes(), source.length())) { "insufficient_free_space" }
                val dir = File(root, "exchange/inspection").apply { check(mkdirs() || isDirectory) }
                File(dir, "${UUID.randomUUID()}.zip").also {
                    temporary = it
                    MdanceContainer.unwrapMdanceToZipFile(source, it, packageName, checkCancellation = checkCancellation)
                }
            } else source
            checkCancellation()
            val raw = metadata(zip, checkCancellation)
            checkCancellation()
            return project(raw).also { checkCancellation() }
        } finally {
            temporary?.delete()
            picked?.delete()
        }
    }

    private fun metadata(file: File, checkCancellation: () -> Unit): String = ZipFile(file).use { zip ->
        val paths = mutableSetOf<String>()
        var metadata: java.util.zip.ZipEntry? = null
        var count = 0
        var advertised = 0L
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            checkCancellation()
            val entry = entries.nextElement()
            require(++count <= SecureZipLimitsDefaults.MAX_TOTAL_ENTRIES) { "too_many_entries" }
            if (entry.isDirectory) continue
            val path = entry.name.replace('\\', '/').trim()
            SecureZipSandbox.assertExtractTargetAllowed(root.absolutePath, path, SecureZipWhitelist.MidnightDancerFullBackup)
            require(paths.add(path)) { "duplicate_archive_entry" }
            require(entry.method == 0 || entry.method == 8) { "unsupported_compression" }
            require(entry.size in 0..ExchangeZipLimits.MAX_MEDIA_BYTES) { "invalid_entry_size" }
            advertised += entry.size
            require(advertised <= ExchangeZipLimits.MAX_MEDIA_BYTES) { "uncompressed_package_too_large" }
            if (path == "metadata.json") metadata = entry
        }
        val entry = requireNotNull(metadata) { "archive_metadata_missing" }
        require(entry.size in 0..SecureZipMetadataLimits.MAX_METADATA_JSON_BYTES.toLong()) { "metadata_too_large" }
        val output = ByteArrayOutputStream()
        zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                checkCancellation()
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= SecureZipMetadataLimits.MAX_METADATA_JSON_BYTES && total <= entry.size) { "metadata_too_large" }
                output.write(buffer, 0, read)
            }
            require(total == entry.size) { "metadata_size_mismatch" }
        }
        output.toString(Charsets.UTF_8.name())
    }

    private fun project(raw: String): ExportCatalogSnapshot {
        val data = DomainJson.decodeAppData(raw)
        val styles = data.danceStyles.map { StyleRef(it.id, it.name.ifBlank { it.id }) }
        fun styleName(id: String) = styles.firstOrNull { it.id == id || it.name.equals(id, true) }?.name ?: id
        val elements = data.danceStyles.flatMapIndexed { styleIndex, style ->
            style.moves.mapIndexed { index, move ->
                CatalogPickItem("element:$styleIndex:$index:${move.id}", move.name.ifBlank { move.id }, style.name, style.id)
            }
        }
        val songs = data.songs.mapIndexed { index, song ->
            CatalogPickItem("song:$index:${song.id}", song.title.ifBlank { song.id }, styleName(song.danceStyle), song.danceStyle)
        }
        val legacy = data.choreographies.mapIndexed { index, choreo ->
            CatalogPickItem("legacy:$index:${choreo.id}", choreo.name.ifBlank { choreo.id }, styleName(choreo.styleId), choreo.styleId)
        }
        val editor = data.settings[ChoreographyExchangeBridge.METADATA_KEY]?.let { value ->
            val payload = value as? JsonPrimitive
            require(payload != null && payload.isString) { "invalid_editor_vault_schema" }
            val document = Json.parseToJsonElement(payload.content) as? JsonObject
                ?: error("invalid_editor_vault_schema")
            require((document["version"] as? JsonPrimitive)?.intOrNull == 1) { "unsupported_editor_vault_schema" }
            val items = document["items"] as? JsonArray ?: error("invalid_editor_vault_schema")
            items.mapIndexed { index, item ->
                val obj = item as? JsonObject ?: error("invalid_editor_vault_schema")
                fun text(key: String): String {
                    val field = obj[key] as? JsonPrimitive
                    require(field != null && field.isString) { "invalid_editor_vault_schema" }
                    return field.content
                }
                val id = text("id")
                val title = text("title")
                val style = text("style")
                val snapshot = obj["snapshot"]
                require(snapshot == null || snapshot == JsonNull || snapshot is JsonObject) { "invalid_editor_vault_schema" }
                CatalogPickItem("editor:$index:$id", title.ifBlank { id }, styleName(style), style)
            }
        }.orEmpty()
        return ExportCatalogSnapshot(styles, elements, songs, legacy + editor)
    }
}
