@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.midnightdancer.shared

import com.midnightdancer.core.data.files.IosDurableTextFile
import com.midnightdancer.core.domain.styles.ManageDanceStyles
import com.midnightdancer.core.domain.styles.projectSavedSetStyleJson
import com.midnightdancer.core.domain.styles.projectVaultStyleJson
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultPersister
import com.midnightdancer.feature.exchange.logic.SavedSet
import com.midnightdancer.feature.exchange.logic.SavedSetOrigin
import com.midnightdancer.feature.exchange.logic.SavedSetsPersister
import com.midnightdancer.feature.exchange.logic.normalizeSavedSetStyles
import kotlinx.serialization.json.*
import platform.Foundation.NSRecursiveLock

/** Same v1 raw contract as Android, in iOS Documents. Never catches a read/write error as empty. */
internal class IosChoreographyVaultPersister(private val styles: ManageDanceStyles) : ChoreographyVaultPersister {
    private val file = IosDurableTextFile("choreography_vault_v1.json")
    private val lock = NSRecursiveLock()
    override fun readRaw(): String? = withLibraryLock(lock) { file.readOrNull()?.let { projectVaultStyleJson(it, styles.state.value) } }
    override fun writeRaw(json: String) { withLibraryLock(lock) { file.write(projectVaultStyleJson(json, styles.state.value)) } }
    override fun refreshStyles() { withLibraryLock(lock) { file.readOrNull()?.let { writeRaw(it) } } }
}

internal class IosSavedSetsPersister(private val styles: ManageDanceStyles) : SavedSetsPersister {
    private val file = IosDurableTextFile("saved_sets_v1.json")
    private val lock = NSRecursiveLock()

    override fun read(): List<SavedSet>? = withLibraryLock(lock) { file.readOrNull()?.let { raw ->
        val root = Json.parseToJsonElement(projectSavedSetStyleJson(raw, styles.state.value)).jsonObject
        root.getValue("sets").jsonArray.map { item ->
            val row = item.jsonObject
            SavedSet(
                id = row.text("id") ?: error("missing_set_id"),
                name = row.text("name").orEmpty(),
                styleId = row.text("styleId").orEmpty(),
                styleName = row.text("styleName").orEmpty(),
                moveIds = row.strings("moveIds"), songIds = row.strings("songIds"), choreoIds = row.strings("choreoIds"),
                estimatedBytes = row.number("estimatedBytes"), updatedAt = row.number("updatedAt"),
                origin = SavedSetOrigin.valueOf(row.text("origin") ?: "Mine"),
                tags = row.strings("tags"), archivePath = row.text("archivePath")?.let(::resolveArchivePath),
            )
        }
    } }

    override fun write(sets: List<SavedSet>) {
        withLibraryLock(lock) {
        val normalized = styles.state.value?.let { normalizeSavedSetStyles(sets, it) } ?: sets
        val previous = file.readOrNull()?.let {
            Json.parseToJsonElement(projectSavedSetStyleJson(it, styles.state.value)).jsonObject
        }
        val previousRows = previous?.get("sets")?.jsonArray?.associate { row -> row.jsonObject.getValue("id").jsonPrimitive.content to row.jsonObject }.orEmpty()
        val rows = normalized.map { set ->
            JsonObject(previousRows[set.id].orEmpty().toMutableMap().apply {
                put("id", JsonPrimitive(set.id)); put("name", JsonPrimitive(set.name))
                put("styleId", JsonPrimitive(set.styleId)); put("styleName", JsonPrimitive(set.styleName))
                put("moveIds", set.moveIds.json()); put("songIds", set.songIds.json()); put("choreoIds", set.choreoIds.json())
                put("estimatedBytes", JsonPrimitive(set.estimatedBytes)); put("updatedAt", JsonPrimitive(set.updatedAt))
                put("origin", JsonPrimitive(set.origin.name)); put("tags", set.tags.json())
                val archivePath = set.archivePath
                if (archivePath == null) remove("archivePath")
                else put("archivePath", JsonPrimitive(portableArchivePath(archivePath)))
            })
        }
        val root = JsonObject(previous.orEmpty().toMutableMap().apply {
            put("schemaVersion", JsonPrimitive(1)); put("sets", JsonArray(rows))
        })
        file.write(projectSavedSetStyleJson(root.toString(), styles.state.value))
        // No archive GC: imported cards/copies may still own these immutable payloads.
        }
    }

    override fun refreshStyles() {
        withLibraryLock(lock) { file.readOrNull()?.let { file.write(projectSavedSetStyleJson(it, styles.state.value)) } }
    }

    override fun retainArchive(sourcePath: String, setId: String): String {
        require(setId.matches(Regex("[a-zA-Z0-9_-]+"))) { "invalid_set_id" }
        val extension = if (sourcePath.substringAfterLast('.').equals("zip", ignoreCase = true)) "zip" else "dance"
        return IosDurableTextFile("saved_set_archives/$setId.$extension").retainCopy(sourcePath)
    }

    // Older versions stored an absolute path inside Documents. Convert only our owned archive names.
    private fun portableArchivePath(path: String): String {
        val name = path.substringAfterLast('/')
        if (!name.matches(Regex("[a-zA-Z0-9_-]+\\.(dance|zip)"))) return path
        return if (path == "saved_set_archives/$name" ||
            path.startsWith('/') && path.endsWith("/Documents/saved_set_archives/$name")) "saved_set_archives/$name"
        else path
    }

    private fun resolveArchivePath(path: String): String {
        val stored = portableArchivePath(path)
        return if (stored.startsWith("saved_set_archives/") &&
            stored.substringAfter('/').matches(Regex("[a-zA-Z0-9_-]+\\.(dance|zip)"))) IosDurableTextFile(stored).absolutePath
        else stored
    }

    private fun JsonObject.text(key: String): String? {
        val value = get(key) ?: return null
        if (value == JsonNull) return null
        require(value is JsonPrimitive && value.isString) { "invalid_set_$key" }
        return value.content
    }
    private fun JsonObject.number(key: String): Long {
        val value = get(key) ?: return 0L
        return (value as? JsonPrimitive)?.longOrNull ?: error("invalid_set_$key")
    }
    private fun JsonObject.strings(key: String): List<String> = get(key)?.jsonArray?.map {
        require(it is JsonPrimitive && it.isString) { "invalid_set_$key" }; it.content
    }.orEmpty()
    private fun List<String>.json() = JsonArray(map { JsonPrimitive(it) })
}

private fun <T> withLibraryLock(lock: NSRecursiveLock, action: () -> T): T {
    lock.lock()
    try { return action() } finally { lock.unlock() }
}
