package com.midnightdancer.shared

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.Choreography
import com.midnightdancer.core.domain.styles.projectVaultStyleJson
import com.midnightdancer.core.domain.timeline.ChoreographyTimelineRef
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultItem
import com.midnightdancer.feature.choreography.logic.ChoreographySnapshot
import com.midnightdancer.feature.choreography.logic.VaultStatus
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.*
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSUUID
import kotlin.math.roundToLong
import com.midnightdancer.core.domain.exchange.CatalogPickItem
import com.midnightdancer.core.domain.exchange.ChoreographyExchangeBridge
import com.midnightdancer.core.domain.exchange.EditorExchangeMedia
import com.midnightdancer.core.domain.exchange.EditorExchangePayload
import com.midnightdancer.core.domain.exchange.PreparedChoreographyImport
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultPersister
import com.midnightdancer.feature.choreography.logic.EditorClip
import com.midnightdancer.feature.choreography.logic.decodeVaultItems
import com.midnightdancer.feature.choreography.logic.encodeVaultItems

@OptIn(ExperimentalForeignApi::class)
internal class IosChoreographyExportBridge(
    private val vault: ChoreographyVaultPersister,
    private val archive: IosArchiveDriver,
) : ChoreographyExchangeBridge {
    private fun items() = vault.readRaw()?.let(::decodeVaultItems).orEmpty()

    override fun catalog(): List<CatalogPickItem> = items().map { item ->
        val references = item.snapshot?.let { it.elementClips + it.musicClips }.orEmpty()
            .mapNotNull { it.mediaUri }.distinct()
        val bytes = references.sumOf { reference ->
            archive.resolveMedia(reference)?.let(archive::mediaSize) ?: 0L
        }
        CatalogPickItem(item.id, item.title, item.style, item.style, bytes)
    }

    override fun export(ids: Set<String>): EditorExchangePayload {
        val selected = items().filter { it.id in ids }
        require(selected.map { it.id }.toSet() == ids) { "editor_choreography_not_found" }
        val locations = linkedMapOf<String, String>()
        val hashes = mutableMapOf<String, String>()
        fun portable(clip: EditorClip, lane: String): EditorClip {
            val reference = clip.mediaUri?.takeIf { it.isNotBlank() } ?: return clip.copy(mediaUri = null)
            val path = archive.resolveMedia(reference) ?: error("editor_media_unavailable")
            val digest = hashes.getOrPut(path) { archive.mediaSha256(path) }
            require(digest.matches(Regex("[0-9a-f]{64}"))) { "editor_media_invalid_digest" }
            val extension = path.substringAfterLast('.', "").lowercase()
            val allowed = if (lane == "videos") setOf("mp4", "mov") else setOf("mp3", "m4a", "wav", "aac", "ogg", "flac")
            require(extension in allowed) { "unsupported_media_extension" }
            val destination = "$lane/editor-$digest.$extension"
            locations[destination] = reference
            return clip.copy(mediaUri = destination)
        }
        val portable = selected.map { item ->
            item.copy(snapshot = item.snapshot?.let { snapshot ->
                snapshot.copy(
                    elementClips = snapshot.elementClips.map { portable(it, "videos") },
                    musicClips = snapshot.musicClips.map { portable(it, "music") },
                )
            })
        }
        return EditorExchangePayload(encodeVaultItems(portable),
            locations.map { (target, source) -> EditorExchangeMedia(target, source) })
    }

    override fun prepareImport(
        payloadJson: String,
        resolvedMedia: Map<String, String>,
        styleRedirect: Map<String, String>,
        legacyData: AppData,
    ): PreparedChoreographyImport {
        require(payloadJson.encodeToByteArray().size <= 2 * 1024 * 1024) { "editor_payload_too_large" }
        val exact = if (payloadJson.isBlank()) emptyList() else {
            projectVaultStyleJson(payloadJson) // Strict version/shape/identity check, never tolerant empty fallback.
            decodeVaultItems(payloadJson)
        }
        val exactIds = exact.map { it.id }.toSet()
        val incoming = exact + legacyItems(legacyData, exactIds, resolvedMedia)
        require(incoming.size <= 999 && incoming.map { it.id }.distinct().size == incoming.size) { "invalid_editor_import_count_or_ids" }
        fun style(value: String): String = styleRedirect[value]?.also { require(it.isNotBlank()) { "empty_style_redirect" } } ?: value
        fun clip(value: EditorClip, lane: String): EditorClip {
            require(value.durationMs >= 0 && value.sourceStartMs >= 0 &&
                value.durationMs <= Long.MAX_VALUE - value.sourceStartMs &&
                value.speed.isFinite() && value.speed in 0.1..10.0) { "invalid_editor_clip_timing" }
            val reference = value.mediaUri?.takeIf { it.isNotBlank() } ?: return value.copy(mediaUri = null)
            require(safeMedia(reference, lane)) { "unsafe_editor_media_reference" }
            val mapped = resolvedMedia[reference] ?: error("editor_media_missing")
            val local = archive.resolveMedia(mapped) ?: error("editor_media_unavailable")
            require(local.startsWith('/') && '\u0000' !in local && archive.mediaSize(local) > 0) { "editor_media_unavailable" }
            return value.copy(mediaUri = local)
        }
        val resolved = incoming.map { item ->
            require(item.id.isNotBlank() && item.songPlaybackSpeed.isFinite() && item.songPlaybackSpeed in 0.1..10.0) { "invalid_editor_item" }
            item.copy(style = style(item.style), snapshot = item.snapshot?.let { snapshot ->
                require(snapshot.songPlaybackSpeed.isFinite() && snapshot.songPlaybackSpeed in 0.1..10.0 && snapshot.musicVolume.isFinite()) { "invalid_editor_song_settings" }
                snapshot.copy(style = style(snapshot.style),
                    elementClips = snapshot.elementClips.map { clip(it, "videos") },
                    musicClips = snapshot.musicClips.map { clip(it, "music") })
            })
        }
        // Validate the current file now; commit rereads latest state rather than replacing this snapshot.
        document(vault.readRaw())
        return object : PreparedChoreographyImport {
            private var written = emptyMap<String, JsonObject>()
            private var committed = false
            private var rolledBack = false

            override fun commit() = withImportLock {
                check(!rolledBack) { "editor_import_already_rolled_back" }
                if (committed) return@withImportLock
                val beforeRaw = vault.readRaw()
                val before = document(beforeRaw)
                val previous = before.getValue("items").jsonArray
                val used = previous.map { it.jsonObject.getValue("id").jsonPrimitive.content }.toMutableSet()
                val added = resolved.map { item ->
                    var id = item.id
                    if (!used.add(id)) {
                        do { id = "imported-editor-${NSUUID().UUIDString}" } while (!used.add(id))
                    }
                    item.copy(id = id, chrId = if (id == item.id) item.chrId else id)
                }
                if (added.isEmpty()) { committed = true; return@withImportLock }
                val rows = Json.parseToJsonElement(encodeVaultItems(added)).jsonObject.getValue("items").jsonArray
                val next = JsonObject(before.toMutableMap().apply { put("items", JsonArray(previous + rows)) }).toString()
                require(vault.readRaw() == beforeRaw) { "editor_import_concurrent_write" }
                // Track exact intended rows before writing: an atomic writer may commit then report failure.
                written = rows.associate { row -> row.jsonObject.getValue("id").jsonPrimitive.content to row.jsonObject }
                vault.writeRaw(next)
                committed = true
                // No off-Main store mutation or blocking Main dispatch. The future writer adapter must
                // publish committed data on its actor after its cross-store transaction succeeds.
            }

            override fun rollback() = withImportLock {
                if (rolledBack) return@withImportLock
                if (written.isEmpty()) { rolledBack = true; return@withImportLock }
                val beforeRaw = vault.readRaw()
                val before = document(beforeRaw)
                var conflict = false
                val kept = before.getValue("items").jsonArray.filter { element ->
                    val row = element.jsonObject
                    val expected = written[row.getValue("id").jsonPrimitive.content]
                    when {
                        expected == null -> true
                        row == expected -> false
                        else -> { conflict = true; true } // Never remove an edited/replaced row, even at an imported ID.
                    }
                }
                val next = JsonObject(before.toMutableMap().apply { put("items", JsonArray(kept)) }).toString()
                require(vault.readRaw() == beforeRaw) { "editor_rollback_concurrent_write" }
                if (kept.size != before.getValue("items").jsonArray.size) vault.writeRaw(next)
                rolledBack = true
                require(!conflict) { "editor_rollback_preserved_modified_import" }
            }
        }
    }

    private fun document(raw: String?): JsonObject {
        val value = raw ?: encodeVaultItems(emptyList())
        projectVaultStyleJson(value)
        decodeVaultItems(value) // Also validate numeric clip fields against the exact persistence schema.
        return Json.parseToJsonElement(value).jsonObject
    }

    private fun safeMedia(reference: String, lane: String): Boolean {
        val extensions = if (lane == "videos") "mp4|mov" else "mp3|m4a|wav|aac|ogg|flac"
        return reference.matches(Regex("$lane/[a-zA-Z0-9_][a-zA-Z0-9_.-]*\\.($extensions)")) &&
            reference.substringAfter('/').let { it != "." && it != ".." }
    }

    private fun legacyItems(data: AppData, exact: Set<String>, media: Map<String, String>): List<ChoreographyVaultItem> =
        data.choreographies.filterNot { it.id in exact }.map { legacy ->
            require(legacy.songPlaybackSpeed.isFinite() && legacy.songPlaybackSpeed in 0.1..10.0) { "invalid_legacy_song_speed" }
            val start = milliseconds(legacy.startTime)
            val end = milliseconds(legacy.endTime)
            require(end > start) { "invalid_legacy_choreography_range" }
            val song = data.songs.singleOrNull { it.id == legacy.songId }
            require(legacy.songId.isBlank() || song != null) { "legacy_song_not_found" }
            val music = song?.let {
                val exactPath = it.fileName.takeIf { path -> safeMedia(path, "music") && path in media }
                val candidates = media.keys.filter { path -> safeMedia(path, "music") && path.substringAfter('/').substringBeforeLast('.') == it.id }
                val reference = exactPath ?: candidates.singleOrNull() ?: error("legacy_song_media_missing_or_ambiguous")
                listOf(EditorClip(it.title, end - start, reference, sourceStartMs = start))
            }.orEmpty()
            val points = legacy.timeline.entries.map { milliseconds(it.key) to it.value }.sortedBy { it.first }
            require(points.map { it.first }.distinct().size == points.size) { "legacy_timeline_precision_not_supported" }
            val active = points.indexOfLast { it.first <= start }
            val events = buildList {
                if (active >= 0) add(points[active].copy(first = start))
                addAll(points.filter { it.first > start && it.first < end })
            }
            val elements = buildList {
                if (events.firstOrNull()?.first?.let { it > start } == true) add(EditorClip("", events.first().first - start, speed = legacy.songPlaybackSpeed))
                events.forEachIndexed { index, (time, reference) ->
                    val until = events.getOrNull(index + 1)?.first ?: end
                    val scoped = ChoreographyTimelineRef.decode(reference)
                    val own = data.danceStyles.singleOrNull { it.id == legacy.styleId }?.moves.orEmpty()
                    val all = data.danceStyles.flatMap { it.moves }
                    val move = when {
                        reference.isBlank() -> null
                        scoped != null -> data.danceStyles.singleOrNull { it.id == scoped.styleId }?.moves?.singleOrNull { it.id == scoped.moveId }
                        "::" in reference -> null
                        else -> own.singleOrNull { it.id == reference } ?: own.singleOrNull { it.name == reference }
                            ?: all.singleOrNull { it.id == reference }
                    }
                    require(reference.isBlank() || move != null) { "legacy_move_missing_or_ambiguous" }
                    val video = move?.let {
                        val exactPath = it.videoUri?.takeIf { path -> safeMedia(path, "videos") && path in media }
                        val candidates = media.keys.filter { path -> safeMedia(path, "videos") && path.substringAfter('/').substringBeforeLast('.') == it.id }
                        exactPath ?: candidates.singleOrNull().also { candidate ->
                            require(it.videoUri.isNullOrBlank() || candidate != null) { "legacy_move_media_missing_or_ambiguous" }
                        }
                    }
                    val originalStart = if (index == 0 && active >= 0) points[active].first else time
                    add(EditorClip(move?.name.orEmpty(), until - time, video, speed = legacy.songPlaybackSpeed,
                        sourceStartMs = time - originalStart))
                }
            }
            val effective = ((end - start).toDouble() / legacy.songPlaybackSpeed).toLong()
            ChoreographyVaultItem(legacy.id, legacy.name, if (music.isEmpty()) VaultStatus.Draft else VaultStatus.Ready,
                legacy.styleId, bpm = 120, durationLabel = "${effective / 60000}:${((effective / 1000) % 60).toString().padStart(2, '0')}",
                trackName = song?.title.orEmpty(), chrId = legacy.id, songPlaybackSpeed = legacy.songPlaybackSpeed,
                snapshot = ChoreographySnapshot(legacy.name, legacy.styleId, elements, music, legacy.songPlaybackSpeed))
        }

    private fun milliseconds(seconds: Double): Long {
        val value = seconds * 1000
        require(value.isFinite() && value >= 0 && value < Long.MAX_VALUE.toDouble()) { "invalid_legacy_timeline_time" }
        return value.roundToLong()
    }

}


/** Serializes this bridge's prepared operations, not unrelated editor writers: this is NOT a CAS. */
@OptIn(ExperimentalForeignApi::class)
private val iosVaultImportLock = NSRecursiveLock()

@OptIn(ExperimentalForeignApi::class)
private inline fun <T> withImportLock(block: () -> T): T {
    iosVaultImportLock.lock()
    return try { block() } finally { iosVaultImportLock.unlock() }
}
