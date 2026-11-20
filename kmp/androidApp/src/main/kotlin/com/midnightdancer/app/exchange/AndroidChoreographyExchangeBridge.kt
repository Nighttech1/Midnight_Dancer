package com.midnightdancer.app.exchange

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.midnightdancer.core.domain.exchange.CatalogPickItem
import com.midnightdancer.core.domain.exchange.ChoreographyExchangeBridge
import com.midnightdancer.core.domain.exchange.EditorExchangeMedia
import com.midnightdancer.core.domain.exchange.EditorExchangePayload
import com.midnightdancer.core.domain.exchange.PreparedChoreographyImport
import com.midnightdancer.core.domain.exchange.PackageExportLimits
import com.midnightdancer.core.domain.settings.CreationAccessPolicy
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultItem
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultStore
import com.midnightdancer.feature.choreography.logic.ChoreographySnapshot
import com.midnightdancer.feature.choreography.logic.VaultStatus
import com.midnightdancer.feature.choreography.logic.EditorClip
import com.midnightdancer.feature.choreography.logic.ownChoreographyCount
import com.midnightdancer.feature.choreography.logic.decodeVaultItems
import com.midnightdancer.feature.choreography.logic.encodeVaultItems
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Android composition root connects portable exchange to the existing editor's exact persisted model. */
class AndroidChoreographyExchangeBridge(
    private val context: Context,
    private val creationAccessPolicy: CreationAccessPolicy? = null,
    private val mediaReferenceResolver: (String) -> String = { it },
) : ChoreographyExchangeBridge {
    private val vault = AtomicFile(File(context.filesDir, "choreography_vault_v1.json"))

    private fun readItems(): List<ChoreographyVaultItem> =
        if (vault.baseFile.exists() || File(vault.baseFile.path + ".bak").exists()) {
            vault.openRead().bufferedReader().use { decodeVaultItems(it.readText()) }
        } else ChoreographyVaultStore.all()

    override fun catalog(): List<CatalogPickItem> = readItems().map { item ->
        val media = item.snapshot?.let { it.elementClips + it.musicClips }.orEmpty()
            .mapNotNull { it.mediaUri }.distinct()
        CatalogPickItem(
            id = item.id,
            title = item.title,
            subtitle = item.style,
            styleId = item.style,
            approximateBytes = media.sumOf { mediaSize(it) },
        )
    }

    override fun export(ids: Set<String>): EditorExchangePayload {
        val selected = readItems().filter { it.id in ids }
        require(selected.map { it.id }.toSet() == ids) { "editor_choreography_not_found" }
        val media = linkedMapOf<String, String>()
        val hashes = mutableMapOf<String, String>()
        fun portable(clip: EditorClip, lane: String): EditorClip {
            val source = clip.mediaUri?.takeIf { it.isNotBlank() } ?: return clip.copy(mediaUri = null)
            val digest = hashes.getOrPut(source) { mediaDigest(source) }
            val ext = Uri.parse(source).lastPathSegment?.substringAfterLast('.', "")
                ?.lowercase()?.takeIf { it in setOf("mp4", "mov", "mp3", "m4a", "wav", "aac", "ogg", "flac") }
                ?: if (lane == "videos") "mp4" else "mp3"
            val relative = "$lane/editor-$digest.$ext"
            media[relative] = source
            return clip.copy(mediaUri = relative)
        }
        val portable = selected.map { item ->
            item.copy(snapshot = item.snapshot?.let { snapshot ->
                snapshot.copy(
                    elementClips = snapshot.elementClips.map { portable(it, "videos") },
                    musicClips = snapshot.musicClips.map { portable(it, "music") },
                )
            })
        }
        return EditorExchangePayload(encodeVaultItems(portable), media.map { EditorExchangeMedia(it.key, it.value) })
    }

    override fun prepareImport(payloadJson: String, resolvedMedia: Map<String, String>, styleRedirect: Map<String, String>, legacyData: AppData): PreparedChoreographyImport {
        if (payloadJson.isNotBlank()) require(JSONObject(payloadJson).optInt("version", 1) == 1) { "unsupported_editor_vault_schema" }
        val exactEditorItems = if (payloadJson.isBlank()) emptyList() else decodeVaultItems(payloadJson)
        val incoming = exactEditorItems + legacyItems(legacyData, exactEditorItems.map { it.id }.toSet())
        require(incoming.size <= 999) { "too_many_editor_choreographies" }
        fun resolved(clip: EditorClip): EditorClip {
            require(clip.durationMs >= 0L && clip.sourceStartMs >= 0L && clip.speed.isFinite() && clip.speed in 0.1..10.0) {
                "invalid_editor_clip_timing"
            }
            val ref = clip.mediaUri?.takeIf { it.isNotBlank() } ?: return clip.copy(mediaUri = null)
            require(ref.matches(Regex("(videos|music)/[a-zA-Z0-9_.-]+\\.(mp4|mov|mp3|m4a|wav|aac|ogg|flac)")) &&
                ref.split('/').none { it == ".." || it.startsWith('.') }) {
                "unsafe_editor_media_reference"
            }
            val local = requireNotNull(resolvedMedia[ref]) { "editor_media_missing:$ref" }
            return clip.copy(mediaUri = local)
        }
        val beforePrepared = readItems()
        // Hash on the IO caller, never while holding the main-thread vault owner.
        val identicalIds = incoming.mapNotNull { item ->
            val previous = beforePrepared.firstOrNull { it.id == item.id } ?: return@mapNotNull null
            val portablePrevious = runCatching { decodeVaultItems(export(setOf(previous.id)).payloadJson).single() }.getOrNull()
            if (portablePrevious?.snapshot == item.snapshot && previous.title == item.title) item.id else null
        }.toMutableSet()
        val resolvedIncoming = incoming.map { item ->
            item.copy(style = styleRedirect[item.style] ?: item.style, snapshot = item.snapshot?.let { snapshot ->
                require(snapshot.songPlaybackSpeed.isFinite() && snapshot.songPlaybackSpeed in 0.1..10.0) {
                    "invalid_editor_song_speed"
                }
                snapshot.copy(
                    style = styleRedirect[snapshot.style] ?: snapshot.style,
                    elementClips = snapshot.elementClips.map(::resolved),
                    musicClips = snapshot.musicClips.map(::resolved),
                )
            })
        }
        resolvedIncoming.forEach { item ->
            val previous = beforePrepared.firstOrNull { it.id == item.id }
            if (previous?.snapshot == item.snapshot && previous?.title == item.title) identicalIds += item.id
        }
        fun mergeInto(current: List<ChoreographyVaultItem>): Pair<List<ChoreographyVaultItem>, Set<String>> {
            val used = current.map { it.id }.toMutableSet()
            val added = resolvedIncoming.mapNotNull { item ->
                val previous = current.firstOrNull { it.id == item.id }
                val samePrepared = previous == beforePrepared.firstOrNull { it.id == item.id }
                if (item.id in identicalIds && previous != null && samePrepared) return@mapNotNull null
                var id = item.id
                if (id.isBlank() || !used.add(id)) {
                    id = "imported-editor-${UUID.randomUUID()}"
                    used.add(id)
                }
                item.copy(id = id)
            }
            val ownCount = ownChoreographyCount(current)
            require(added.isEmpty() || creationAccessPolicy?.canCreate(ownCount + added.size - 1) != false) {
                "Для импорта новых постановок сверх лимита Free требуется Pro. Существующие данные сохранены"
            }
            return (current + added) to added.map { it.id }.toSet()
        }
        mergeInto(beforePrepared) // Fail before any media/catalog writes when already over quota.
        return object : PreparedChoreographyImport {
            private var addedIds: Set<String> = emptySet()
            override fun commit() {
                runBlocking(Dispatchers.Main.immediate) {
                    val (next, added) = mergeInto(readItems())
                    writeItems(next)
                    addedIds = added
                    ChoreographyVaultStore.replaceAll(next)
                }
            }
            override fun rollback() {
                runBlocking(Dispatchers.Main.immediate) {
                    if (addedIds.isNotEmpty()) {
                        val restored = readItems().filterNot { it.id in addedIds }
                        writeItems(restored)
                        ChoreographyVaultStore.replaceAll(restored)
                        addedIds = emptySet()
                    }
                }
            }
        }
    }

    private fun writeItems(items: List<ChoreographyVaultItem>) {
        val out = vault.startWrite()
        try {
            out.write(encodeVaultItems(items).toByteArray(Charsets.UTF_8))
            vault.finishWrite(out)
        } catch (error: Throwable) {
            vault.failWrite(out)
            throw error
        }
    }

    private fun mediaSize(rawRef: String): Long = runCatching {
        val ref = mediaReferenceResolver(rawRef)
        val uri = Uri.parse(ref)
        if (uri.scheme == "content") {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length.coerceAtLeast(0L) } ?: 0L
        } else File(if (uri.scheme == "file") uri.path.orEmpty() else ref).length()
    }.getOrDefault(0L)

    private fun mediaDigest(rawRef: String): String {
        val ref = mediaReferenceResolver(rawRef)
        val uri = Uri.parse(ref)
        val input = if (uri.scheme == "content") {
            requireNotNull(context.contentResolver.openInputStream(uri)) { "cannot_read_editor_media" }
        } else {
            require(uri.scheme == null || uri.scheme == "file") { "unsupported_editor_media_uri" }
            File(if (uri.scheme == "file") uri.path.orEmpty() else ref).inputStream()
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                bytes += count
                require(bytes <= PackageExportLimits.MAX_PACKAGE_BYTES) { "editor_media_exceeds_package_limit" }
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Legacy event times are song-absolute. Preserve explicit boundaries, never infer video source trims. */
    private fun legacyItems(data: AppData, alreadyExact: Set<String>): List<ChoreographyVaultItem> =
        data.choreographies.filterNot { it.id in alreadyExact }.map { choreography ->
            require(choreography.startTime.isFinite() && choreography.endTime.isFinite() &&
                choreography.startTime >= 0.0 && choreography.endTime > choreography.startTime) { "legacy_choreography_invalid_range" }
            require(choreography.songPlaybackSpeed.isFinite() && choreography.songPlaybackSpeed in 0.1..10.0) { "legacy_choreography_invalid_speed" }
            val start = (choreography.startTime * 1000).toLong()
            val end = (choreography.endTime * 1000).toLong()
            val events = choreography.timeline.entries.sortedBy { it.key }
            require(events.all { it.key.isFinite() && it.key >= 0.0 }) { "legacy_invalid_event_time" }
            val clipped = events.filter { it.key >= choreography.startTime && it.key < choreography.endTime }
                .map { (time, ref) -> (time * 1000).toLong() to ref }.toMutableList()
            val activeAtStart = events.lastOrNull { it.key < choreography.startTime }
            if (clipped.firstOrNull()?.first != start && activeAtStart != null) clipped.add(0, start to activeAtStart.value)
            val elementClips = mutableListOf<EditorClip>()
            if (clipped.firstOrNull()?.first?.let { it > start } == true) {
                // A silent gap represents the lack of an explicit event, not an invented movement.
                elementClips += EditorClip("", clipped.first().first - start, speed = choreography.songPlaybackSpeed)
            }
            clipped.forEachIndexed { index, (time, ref) ->
                val moveId = ref.substringAfter("::", ref)
                val styleId = ref.substringBefore("::", "")
                val moves = if (styleId.isBlank()) data.danceStyles.flatMap { it.moves }
                    else data.danceStyles.filter { it.id == styleId }.flatMap { it.moves }
                val move = requireNotNull(moves.firstOrNull { it.id == moveId || it.name == moveId }) { "legacy_move_not_found:$ref" }
                val until = clipped.getOrNull(index + 1)?.first ?: end
                val video = move.videoUri?.takeIf { it.isNotBlank() }?.let { "videos/${move.id}.mp4" }
                elementClips += EditorClip(move.name, until - time, video, speed = choreography.songPlaybackSpeed)
            }
            val song = data.songs.firstOrNull { it.id == choreography.songId }
            val music = if (song == null) {
                require(choreography.songId.isBlank()) { "legacy_song_not_found:${choreography.songId}" }
                emptyList()
            } else {
                val ext = song.fileName.substringAfterLast('.', "mp3").lowercase()
                require(ext in setOf("mp3", "m4a", "wav", "aac", "ogg", "flac")) { "legacy_unsupported_song_format" }
                listOf(EditorClip(song.title, end - start, "music/${song.id}.$ext", sourceStartMs = start))
            }
            val style = data.danceStyles.firstOrNull { it.id == choreography.styleId }?.name ?: choreography.styleId
            val durationSeconds = ((end - start) / choreography.songPlaybackSpeed / 1000).toInt()
            ChoreographyVaultItem(
                id = choreography.id, title = choreography.name, status = VaultStatus.Draft, style = style,
                bpm = 0, durationLabel = "%02d:%02d".format(durationSeconds / 60, durationSeconds % 60),
                trackName = "${song?.title ?: "Без музыки"} · старый формат: проверьте видеоклипы",
                chrId = choreography.id, songPlaybackSpeed = choreography.songPlaybackSpeed,
                snapshot = ChoreographySnapshot(choreography.name, style, elementClips, music, choreography.songPlaybackSpeed),
            )
        }
}
