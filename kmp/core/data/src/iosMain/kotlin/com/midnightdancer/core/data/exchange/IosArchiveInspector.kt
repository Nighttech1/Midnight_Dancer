package com.midnightdancer.core.data.exchange

import com.midnightdancer.core.data.files.platformDocumentsDirectoryPath
import com.midnightdancer.core.data.zip.IosSecureZipArchive
import com.midnightdancer.core.domain.exchange.*
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.storage.DeviceStorage
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okio.buffer
import okio.use

/** Reads only an immutable copy of the supplied archive. Never loads/imports the live catalog. */
class IosArchiveInspector(
    private val storage: DeviceStorage,
    private val containerCodec: IosArchiveContainerCodec,
    private val privateRoot: String = platformDocumentsDirectoryPath(),
    private val bundleId: String = "com.midnightdancer.app",
) {
    suspend fun inspect(archivePath: String): ExportCatalogSnapshot = withContext(Dispatchers.IO) {
        project(metadata(archivePath))
    }
    internal suspend fun metadata(archivePath: String): String {
        val context = currentCoroutineContext()
        val workspace = IosArchiveWorkspace(privateRoot, storage)
        try {
            val source = workspace.copyImmutable(archivePath, "source.archive", IosSecureZipArchive.MAX_ARCHIVE_BYTES + 36) { context.ensureActive() }
            val prefix = workspace.fs.source(source).buffer().use { it.readByteArray(8) }
            val encrypted = prefix.take(7).toByteArray().decodeToString() == "MDANCE1"
            val zip = if (encrypted) {
                (workspace.directory / "verified.zip").also {
                    containerCodec.decryptToPrivateZip(source.toString(), it.toString(), bundleId) { !context.isActive }
                }
            } else {
                require(prefix[0] == 0x50.toByte() && prefix[1] == 0x4b.toByte()) { "unsupported_archive_format" }
                // Plain ZIP stays explicitly ZIP; an encrypted-looking suffix cannot become an unauthenticated fallback.
                require(archivePath.substringAfterLast('.').equals("zip", true)) { "plain_archive_requires_zip_extension" }
                source
            }
            context.ensureActive()
            return IosSecureZipArchive(privateRoot).metadata(zip.toString()).also { context.ensureActive() }
        } catch (failure: Throwable) {
            context.ensureActive() // Swift cancellation errors become real coroutine cancellation here.
            throw failure
        } finally { workspace.close() }
    }

    companion object {
        /** Exact archive-only projection; preserve same-ID legacy/editor documents as separate rows. */
        fun project(raw: String): ExportCatalogSnapshot {
            val data = DomainJson.decodeAppData(raw)
            val styles = data.danceStyles.map { StyleRef(it.id, it.name.ifBlank { it.id }) }.toMutableList()
            fun styleName(id: String) = styles.firstOrNull { it.id == id || it.name.equals(id, true) }?.name ?: id
            val elements = data.danceStyles.flatMapIndexed { styleIndex, style ->
                style.moves.mapIndexed { index, move -> CatalogPickItem("element:$styleIndex:$index:${move.id}", move.name.ifBlank { move.id }, style.name, style.id) }
            }
            val songs = data.songs.mapIndexed { index, song -> CatalogPickItem("song:$index:${song.id}", song.title.ifBlank { song.id }, styleName(song.danceStyle), song.danceStyle) }
            val legacy = data.choreographies.mapIndexed { index, choreo -> CatalogPickItem("legacy:$index:${choreo.id}", choreo.name.ifBlank { choreo.id }, styleName(choreo.styleId), choreo.styleId) }
            val editor = data.settings[ChoreographyExchangeBridge.METADATA_KEY]?.let { value ->
                val payload = value as? JsonPrimitive
                require(payload != null && payload.isString) { "invalid_editor_vault_schema" }
                val document = Json.parseToJsonElement(payload.content) as? JsonObject ?: error("invalid_editor_vault_schema")
                require((document["version"] as? JsonPrimitive)?.intOrNull == 1) { "unsupported_editor_vault_schema" }
                val items = document["items"] as? JsonArray ?: error("invalid_editor_vault_schema")
                require(items.size <= 999) { "too_many_editor_choreographies" }
                items.mapIndexed { index, item ->
                    val obj = item as? JsonObject ?: error("invalid_editor_vault_schema")
                    fun text(key: String): String {
                        val field = obj[key] as? JsonPrimitive
                        require(field != null && field.isString) { "invalid_editor_vault_schema" }
                        return field.content
                    }
                    val id = text("id"); val title = text("title"); val style = text("style")
                    val snapshot = obj["snapshot"]
                    require(snapshot == null || snapshot == JsonNull || snapshot is JsonObject) { "invalid_editor_vault_schema" }
                    if (style.isNotBlank() && styles.none { it.id == style || it.name.equals(style, true) }) styles += StyleRef(style, style)
                    CatalogPickItem("editor:$index:$id", title.ifBlank { id }, styleName(style), style)
                }
            }.orEmpty()
            return ExportCatalogSnapshot(styles, elements, songs, legacy + editor)
        }
    }
}
