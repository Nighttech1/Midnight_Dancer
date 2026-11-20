package com.midnightdancer.shared

import com.midnightdancer.core.data.files.IosDurableTextFile
import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.Choreography
import com.midnightdancer.core.domain.entity.Move
import com.midnightdancer.core.domain.media.ElementDurationCodec
import com.midnightdancer.core.domain.styles.projectVaultStyleJson
import com.midnightdancer.core.domain.timeline.ChoreographyTimelineRef
import com.midnightdancer.feature.choreography.logic.*
import kotlinx.serialization.json.*
import kotlin.math.roundToLong

/** One-time local view projection. Room and original metadata remain intact and authoritative history. */
internal fun projectIosLegacyVault(data: AppData, vault: ChoreographyVaultPersister) {
    val marker = IosDurableTextFile("ios_legacy_vault_projection_v1.json")
    val existingMarker = marker.readOrNull()?.let { raw ->
        val root = Json.parseToJsonElement(raw).jsonObject
        val version = root["version"]
        require(version is JsonPrimitive && !version.isString && version.intOrNull == 1) { "unsupported_legacy_vault_projection" }
        root.getValue("handledIds").jsonArray.map {
            require(it is JsonPrimitive && it.isString) { "invalid_legacy_vault_projection" }; it.content
        }.toSet()
    }.orEmpty()

    var raw = vault.readRaw()
    // A real saved editor snapshot takes precedence over a legacy timeline with the same ID.
    if (raw == null) {
        data.settings["editorVaultV1"]?.let { editor ->
            require(editor is JsonPrimitive && editor.isString) { "invalid_editor_vault_metadata" }
            raw = projectVaultStyleJson(editor.content)
            decodeVaultItems(checkNotNull(raw)) // Full vault schema, not a tolerant empty projection.
            vault.writeRaw(checkNotNull(raw))
        }
    }
    val current = raw?.let(::decodeVaultItems).orEmpty()
    val existingIds = current.map { it.id }.toSet()
    val pending = data.choreographies.filter { it.id !in existingMarker && it.id !in existingIds }
    val projected = pending.map { legacy -> projectLegacyItem(data, legacy) }
    if (projected.isNotEmpty()) {
        val original = Json.parseToJsonElement(raw ?: encodeVaultItems(emptyList())).jsonObject
        val added = Json.parseToJsonElement(encodeVaultItems(projected)).jsonObject.getValue("items").jsonArray
        val combined = JsonObject(original.toMutableMap().apply {
            put("items", JsonArray(original.getValue("items").jsonArray + added))
        })
        vault.writeRaw(combined.toString())
    }
    val handled = existingMarker + data.choreographies.map { it.id }
    if (handled != existingMarker || existingMarker.isEmpty()) {
        marker.write(buildJsonObject {
            put("version", 1)
            put("handledIds", JsonArray(handled.sorted().map { JsonPrimitive(it) }))
        }.toString())
    }
}

private fun projectLegacyItem(data: AppData, legacy: Choreography): ChoreographyVaultItem {
    val song = data.songs.singleOrNull { it.id == legacy.songId }
    val start = milliseconds(legacy.startTime)
    val end = milliseconds(legacy.endTime)
    val usableRange = start != null && end != null && end > start
    val duration = if (usableRange) checkNotNull(end) - checkNotNull(start) else 0L
    val music = if (usableRange && !song?.fileName.isNullOrBlank()) listOf(
        EditorClip(song!!.title, duration, song.fileName, sourceStartMs = checkNotNull(start)),
    ) else emptyList()
    val elements = if (usableRange) projectElements(data, legacy, checkNotNull(start), checkNotNull(end)) else emptyList()
    val snapshot = ChoreographySnapshot(legacy.name, legacy.styleId,
        elementClips = elements, musicClips = music, songPlaybackSpeed = legacy.songPlaybackSpeed)
    return ChoreographyVaultItem(
        id = legacy.id, title = legacy.name,
        status = if (music.isNotEmpty()) VaultStatus.Ready else VaultStatus.Draft,
        style = legacy.styleId, bpm = 120,
        durationLabel = ElementDurationCodec.formatLabel(duration), trackName = song?.title.orEmpty(), chrId = legacy.id,
        songPlaybackSpeed = legacy.songPlaybackSpeed, snapshot = snapshot,
    )
}

private fun projectElements(data: AppData, legacy: Choreography, start: Long, end: Long): List<EditorClip> {
    // Legacy keys are absolute music seconds; the new sequential lane begins at the trimmed music A.
    val points = legacy.timeline.entries.map { (time, ref) ->
        (milliseconds(time) ?: error("invalid_legacy_timeline_time")) to ref
    }.sortedBy { it.first }
    require(points.map { it.first }.distinct().size == points.size) { "legacy_timeline_precision_not_supported" }
    if (points.isEmpty()) return emptyList()
    val active = points.indexOfLast { it.first <= start }
    val selected = buildList {
        if (active >= 0) add(points[active].copy(first = start))
        addAll(points.filter { it.first > start && it.first < end })
    }
    if (selected.isEmpty()) return emptyList()
    return buildList {
        if (selected.first().first > start) add(EditorClip("", selected.first().first - start))
        selected.forEachIndexed { index, (time, ref) ->
            val until = selected.getOrNull(index + 1)?.first ?: end
            if (until > time) {
                val move = resolveLegacyMove(data, legacy.styleId, ref)
                val label = move?.name ?: if (ref.isBlank()) "" else "Недоступный элемент"
                val originalStart = if (index == 0 && active >= 0) points[active].first else time
                add(EditorClip(label, until - time, move?.videoUri, sourceStartMs = time - originalStart))
            }
        }
    }
}

private fun resolveLegacyMove(data: AppData, styleId: String, reference: String): Move? {
    val scoped = ChoreographyTimelineRef.decode(reference)
    if (scoped != null) return data.danceStyles.singleOrNull { it.id == scoped.styleId }
        ?.moves?.singleOrNull { it.id == scoped.moveId }
    val own = data.danceStyles.singleOrNull { it.id == styleId }?.moves.orEmpty()
    own.singleOrNull { it.id == reference }?.let { return it }
    val ownNames = own.filter { it.name == reference }
    if (ownNames.isNotEmpty()) return ownNames.singleOrNull()
    val all = data.danceStyles.flatMap { it.moves }
    return all.singleOrNull { it.id == reference }
        ?: all.singleOrNull { it.name == reference }
}

private fun milliseconds(seconds: Double): Long? {
    val value = seconds * 1_000.0
    return if (!value.isFinite() || value < 0 || value >= Long.MAX_VALUE.toDouble()) null else value.roundToLong()
}
