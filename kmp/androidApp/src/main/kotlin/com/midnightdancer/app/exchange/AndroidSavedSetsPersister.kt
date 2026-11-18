package com.midnightdancer.app.exchange

import android.content.Context
import android.util.AtomicFile
import com.midnightdancer.feature.exchange.logic.SavedSet
import com.midnightdancer.feature.exchange.logic.SavedSetOrigin
import com.midnightdancer.feature.exchange.logic.SavedSetsPersister
import com.midnightdancer.feature.exchange.logic.normalizeSavedSetStyles
import com.midnightdancer.core.domain.styles.ManageDanceStyles
import com.midnightdancer.core.domain.styles.projectSavedSetStyleJson
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Private durable card metadata; archives are copied so later imports cannot change an older card. */
class AndroidSavedSetsPersister(context: Context, private val danceStyles: ManageDanceStyles? = null) : SavedSetsPersister {
    private val metadata = AtomicFile(File(context.filesDir, "saved_sets_v1.json"))
    private val archives = File(context.filesDir, "saved_set_archives")
    private val pickedInbox = File(context.filesDir, "MidnightDancer/exchange/picked")

    @Synchronized
    override fun read(): List<SavedSet>? {
        if (!metadata.baseFile.exists() && !File(metadata.baseFile.path + ".bak").exists()) return null
        val raw = metadata.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(projectSavedSetStyleJson(raw, danceStyles?.state?.value))
        require(root.optInt("schemaVersion", 1) == 1) { "unsupported_saved_sets_schema" }
        val rows = root.getJSONArray("sets")
        val sets = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            SavedSet(
                id = row.getString("id"),
                name = row.optString("name"),
                styleId = row.optString("styleId"),
                styleName = row.optString("styleName"),
                moveIds = row.stringList("moveIds"),
                songIds = row.stringList("songIds"),
                choreoIds = row.stringList("choreoIds"),
                estimatedBytes = row.optLong("estimatedBytes"),
                updatedAt = row.optLong("updatedAt"),
                origin = runCatching { SavedSetOrigin.valueOf(row.optString("origin", "Mine")) }
                    .getOrDefault(SavedSetOrigin.Mine),
                tags = row.stringList("tags"),
                archivePath = row.optString("archivePath").takeIf { it.isNotBlank() },
            )
        }
        return normalize(sets)
    }

    private fun normalize(sets: List<SavedSet>) = danceStyles?.state?.value?.let { normalizeSavedSetStyles(sets, it) } ?: sets

    /** Startup projection must not collect/rewrite any archived payload. */
    @Synchronized
    override fun refreshStyles() {
        if (!metadata.baseFile.exists() && !File(metadata.baseFile.path + ".bak").exists()) return
        val raw = metadata.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        val next = projectSavedSetStyleJson(raw, danceStyles?.state?.value).toByteArray(Charsets.UTF_8)
        val output = metadata.startWrite()
        try { output.write(next); metadata.finishWrite(output)
        } catch (error: Throwable) { metadata.failWrite(output); throw error }
    }

    @Synchronized
    override fun write(sets: List<SavedSet>) {
        val sets = normalize(sets)
        writeMetadata(sets)
        // Only app-owned archive copies may be reclaimed; copied cards can share an archive.
        val referenced = sets.mapNotNull { it.archivePath }.toSet()
        archives.listFiles()?.filter { it.isFile && it.extension == "dance" && it.absolutePath !in referenced }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun writeMetadata(sets: List<SavedSet>) {
        val rows = JSONArray()
        sets.forEach { set ->
            rows.put(JSONObject().apply {
                put("id", set.id)
                put("name", set.name)
                put("styleId", set.styleId)
                put("styleName", set.styleName)
                put("moveIds", JSONArray(set.moveIds))
                put("songIds", JSONArray(set.songIds))
                put("choreoIds", JSONArray(set.choreoIds))
                put("estimatedBytes", set.estimatedBytes)
                put("updatedAt", set.updatedAt)
                put("origin", set.origin.name)
                put("tags", JSONArray(set.tags))
                set.archivePath?.let { put("archivePath", it) }
            })
        }
        val json = JSONObject().put("schemaVersion", 1).put("sets", rows).toString()
        val output = metadata.startWrite()
        try {
            output.write(json.toByteArray(Charsets.UTF_8))
            metadata.finishWrite(output)
        } catch (error: Throwable) {
            metadata.failWrite(output)
            throw error
        }
    }

    @Synchronized
    override fun retainArchive(sourcePath: String, setId: String): String {
        require(setId.matches(Regex("[a-zA-Z0-9_-]+"))) { "invalid_set_id" }
        val source = File(sourcePath)
        require(source.isFile) { "import_archive_missing" }
        check(archives.mkdirs() || archives.isDirectory) { "cannot_create_archive_storage" }
        val target = AtomicFile(File(archives, "$setId.dance"))
        val output = target.startWrite()
        try {
            source.inputStream().use { it.copyTo(output) }
            target.finishWrite(output)
        } catch (error: Throwable) {
            target.failWrite(output)
            throw error
        }
        if (source.parentFile?.canonicalFile == pickedInbox.canonicalFile &&
            source.name.matches(Regex("[a-fA-F0-9-]{36}\\.dance"))) {
            source.delete() // The generated SAF staging copy is now retained under the card's identity.
        }
        return target.baseFile.absolutePath
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val list = optJSONArray(key) ?: return emptyList()
        return (0 until list.length()).map { list.getString(it) }
    }
}
