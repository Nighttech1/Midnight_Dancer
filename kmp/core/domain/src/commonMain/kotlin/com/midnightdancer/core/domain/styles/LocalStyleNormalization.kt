package com.midnightdancer.core.domain.styles

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.entity.DanceStyle
import kotlinx.serialization.json.*

/** Local mutable data only. Archive inspectors must keep the original archive labels. */
fun AppData.normalizeLocalStyles(catalog: DanceStyleCatalogSnapshot): AppData {
    // Room Move identity is global, including internal choreography payloads. Even equal
    // bytes cannot have two owners: upserting either association would silently drop the other.
    require(danceStyles.flatMap { it.moves }.groupBy { it.id }.values.all { it.size == 1 }) {
        "style_move_id_collision"
    }
    val internal = danceStyles.filter { it.id == "__md_choreo_scoped__" }
    val grouped = danceStyles.filterNot { it in internal }.groupBy { catalog.canonicalId(it.id) }
    val normalized = catalog.entries.map { entry ->
        val sources = grouped[entry.id].orEmpty()
        val moves = sources.flatMap { it.moves }
        val unique = moves.distinctBy { it.id }
        DanceStyle(entry.id, entry.name, unique,
            sources.mapNotNull { it.currentMoveId }.firstOrNull { pin -> unique.any { it.id == pin } })
    }
    return copy(
        danceStyles = normalized + internal,
        songs = songs.map { it.copy(danceStyle = catalog.canonicalId(it.danceStyle)) },
        choreographies = choreographies.map { choreo ->
            choreo.copy(styleId = catalog.canonicalId(choreo.styleId), timeline = choreo.timeline.mapValues { (_, ref) ->
                val separator = ref.indexOf("::")
                if (separator < 0 || ref.substring(0, separator) == "__md_choreo_scoped__") ref
                else catalog.canonicalId(ref.substring(0, separator)) + ref.substring(separator)
            })
        },
        settings = normalizeEditorVaultStyles(settings, catalog),
    )
}

/** Updates only style fields; unknown fields, snapshots and media references survive. */
fun normalizeEditorVaultStyles(settings: JsonObject, catalog: DanceStyleCatalogSnapshot): JsonObject {
    val raw = settings["editorVaultV1"] ?: return settings
    val text = (raw as? JsonPrimitive)?.content ?: error("invalid_editor_vault_styles")
    val root = Json.parseToJsonElement(text) as? JsonObject ?: error("invalid_editor_vault_styles")
    // Historical Room metadata may contain partial projection items (no vault status/title),
    // but an explicit future schema or a non-object snapshot must never be rewritten.
    root["version"]?.let { version ->
        require(version is JsonPrimitive && !version.isString && version.intOrNull == 1) { "unsupported_editor_vault_version" }
    }
    val items = root["items"] as? JsonArray ?: error("invalid_editor_vault_styles")
    val next = JsonArray(items.map { element ->
        val item = element as? JsonObject ?: error("invalid_editor_vault_styles")
        val values = item.toMutableMap()
        item["style"]?.let {
            require(it is JsonPrimitive && it.isString) { "invalid_editor_vault_style" }
            values["style"] = JsonPrimitive(catalog.canonicalId(it.content))
        }
        val snapshotValue = item["snapshot"]
        require(snapshotValue == null || snapshotValue == JsonNull || snapshotValue is JsonObject) { "invalid_editor_vault_snapshot" }
        (snapshotValue as? JsonObject)?.let { snapshot ->
            values["snapshot"] = JsonObject(snapshot.toMutableMap().apply {
                snapshot["style"]?.let {
                    require(it is JsonPrimitive && it.isString) { "invalid_editor_vault_style" }
                    put("style", JsonPrimitive(catalog.canonicalId(it.content)))
                }
            })
        }
        JsonObject(values)
    })
    return JsonObject(settings.toMutableMap().apply {
        put("editorVaultV1", JsonPrimitive(JsonObject(root.toMutableMap().apply { put("items", next) }).toString()))
    })
}
