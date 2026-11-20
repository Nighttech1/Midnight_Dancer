package com.midnightdancer.app.ui.media

import android.content.Context
import android.util.AtomicFile
import com.midnightdancer.core.domain.exchange.ChoreographyExchangeBridge
import com.midnightdancer.core.domain.media.OwnedMediaKind
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.usecase.LoadLibrary
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultItem
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultStore
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONObject

/** A unique reference+role; titles are UI-only, never sent to logs. */
data class MediaAdoptionReference(val reference: String, val kind: OwnedMediaKind, val title: String)
data class MediaAdoptionCollection(val references: List<MediaAdoptionReference>, val warnings: List<String> = emptyList())

/** Union of durable catalog, disk vault, live snapshots, and legacy Room editorVaultV1. No mutation. */
class OwnedMediaAdoptionReferences(
    private val context: Context,
    private val library: LoadLibrary,
    private val liveSnapshots: () -> List<ChoreographyVaultItem> = ChoreographyVaultStore::all,
) {
    suspend fun collect(): MediaAdoptionCollection = withContext(Dispatchers.IO) {
        val references = linkedMapOf<Pair<String, OwnedMediaKind>, MediaAdoptionReference>()
        val warnings = mutableListOf<String>()
        fun add(reference: String?, kind: OwnedMediaKind, title: String) {
            if (!reference.isNullOrBlank()) references.putIfAbsent(reference to kind,
                MediaAdoptionReference(reference, kind, title.ifBlank { if (kind == OwnedMediaKind.Video) "Видео" else "Музыка" }))
        }
        fun snapshots(items: List<ChoreographyVaultItem>) {
            items.forEach { item ->
                item.snapshot?.elementClips.orEmpty().forEach { add(it.mediaUri, OwnedMediaKind.Video, it.label.ifBlank { item.title }) }
                item.snapshot?.musicClips.orEmpty().forEach { add(it.mediaUri, OwnedMediaKind.Audio, it.label.ifBlank { item.title }) }
            }
        }
        fun readVault(raw: String, warning: String) {
            try {
                val document = JSONObject(raw)
                require(document.getInt("version") == 1)
                val items = document.getJSONArray("items")
                for (index in 0 until items.length()) {
                    try {
                        val item = items.getJSONObject(index)
                        if (item.isNull("snapshot") || !item.has("snapshot")) continue
                        val snapshot = item.getJSONObject("snapshot")
                        for ((lane, kind) in listOf("elementClips" to OwnedMediaKind.Video, "musicClips" to OwnedMediaKind.Audio)) {
                            if (!snapshot.has(lane) || snapshot.isNull(lane)) continue
                            val clips = snapshot.getJSONArray(lane)
                            for (clipIndex in 0 until clips.length()) {
                                val clip = clips.getJSONObject(clipIndex)
                                val reference = clip.opt("mediaUri")
                                if (reference == null || reference == JSONObject.NULL) continue
                                require(reference is String)
                                add(reference, kind, clip.optString("label").ifBlank { item.optString("title") })
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { warnings += warning }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { warnings += warning }
        }

        try {
            val data = library.load()
            data.danceStyles.forEach { style -> style.moves.forEach { add(it.videoUri, OwnedMediaKind.Video, it.name) } }
            data.songs.forEach { add(it.fileName, OwnedMediaKind.Audio, it.title) }
            // Serialize through the public domain boundary; the UI never edits raw settings.
            val settings = JSONObject(DomainJson.encodeAppData(data)).optJSONObject("settings")
            if (settings?.has(ChoreographyExchangeBridge.METADATA_KEY) == true) {
                val raw = settings.opt(ChoreographyExchangeBridge.METADATA_KEY)
                if (raw is String) readVault(raw, "Не удалось прочитать часть постановок из библиотеки. Защита может быть неполной.")
                else warnings += "Не удалось прочитать часть постановок из библиотеки. Защита может быть неполной."
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { warnings += "Не удалось прочитать библиотеку. Её материалы пока не проверены." }
        currentCoroutineContext().ensureActive()
        val vault = File(context.filesDir, "choreography_vault_v1.json")
        if (vault.exists() || File(vault.path + ".bak").exists()) {
            try {
                require(vault.length() <= MAX_VAULT_BYTES)
                val output = java.io.ByteArrayOutputStream()
                AtomicFile(vault).openRead().use { input ->
                    val block = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(block)
                        if (read < 0) break
                        require(output.size() + read <= MAX_VAULT_BYTES)
                        output.write(block, 0, read)
                    }
                }
                readVault(output.toString(Charsets.UTF_8.name()), "Не удалось прочитать сохранённые постановки. Защита может быть неполной.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { warnings += "Не удалось прочитать сохранённые постановки. Защита может быть неполной." }
        }
        try { snapshots(liveSnapshots()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { warnings += "Не удалось проверить открытые постановки. Защита может быть неполной." }
        MediaAdoptionCollection(references.values.toList(), warnings.distinct())
    }
    companion object { private const val MAX_VAULT_BYTES = 4 * 1024 * 1024 }
}
