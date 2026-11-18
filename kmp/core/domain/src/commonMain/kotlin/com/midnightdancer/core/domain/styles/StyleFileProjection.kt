package com.midnightdancer.core.domain.styles

import kotlinx.serialization.json.*

/** Strict validation without model roundtrip. A null catalog validates and returns the original bytes. */
fun projectVaultStyleJson(raw: String, catalog: DanceStyleCatalogSnapshot? = null): String {
    val root = Json.parseToJsonElement(raw) as? JsonObject ?: error("invalid_vault_document")
    root.requireVersion("version")
    val items = root["items"] as? JsonArray ?: error("invalid_vault_items")
    val ids = mutableSetOf<String>()
    val next = items.map { element ->
        val item = element as? JsonObject ?: error("invalid_vault_item")
        require(ids.add(item.requiredString("id").also { require(it.isNotBlank()) })) { "duplicate_vault_id" }
        item.requiredString("title"); item.requiredString("status")
        val style = item.requiredString("style")
        val fields = item.toMutableMap()
        if (catalog != null) fields["style"] = JsonPrimitive(catalog.canonicalId(style))
        val snapshot = item["snapshot"]
        if (snapshot != null && snapshot != JsonNull) {
            require(snapshot is JsonObject) { "invalid_vault_snapshot" }
            snapshot.requiredString("title")
            val snapshotStyle = snapshot.requiredString("style")
            listOf("elementClips", "musicClips").forEach { lane ->
                snapshot[lane]?.let { value ->
                    require(value is JsonArray) { "invalid_vault_clips" }
                    value.forEach { clipValue ->
                        val clip = clipValue as? JsonObject ?: error("invalid_vault_clip")
                        clip.requiredString("label")
                        require((clip["durationMs"] as? JsonPrimitive)?.longOrNull != null) { "invalid_vault_clip_duration" }
                        clip["mediaUri"]?.takeIf { it != JsonNull }?.let { require(it is JsonPrimitive && it.isString) }
                    }
                }
            }
            if (catalog != null) fields["snapshot"] = JsonObject(snapshot.toMutableMap().apply {
                put("style", JsonPrimitive(catalog.canonicalId(snapshotStyle)))
            })
        }
        JsonObject(fields)
    }
    return if (catalog == null) raw else JsonObject(root.toMutableMap().apply { put("items", JsonArray(next)) }).toString()
}

fun projectSavedSetStyleJson(raw: String, catalog: DanceStyleCatalogSnapshot? = null): String {
    val root = Json.parseToJsonElement(raw) as? JsonObject ?: error("invalid_sets_document")
    root.requireVersion("schemaVersion")
    val sets = root["sets"] as? JsonArray ?: error("invalid_sets_items")
    val ids = mutableSetOf<String>()
    val next = sets.map { element ->
        val set = element as? JsonObject ?: error("invalid_set_item")
        require(ids.add(set.requiredString("id").also { require(it.isNotBlank()) })) { "duplicate_set_id" }
        val origin = set.optionalString("origin") ?: "Mine"
        require(origin == "Mine" || origin == "Imported") { "unsupported_set_origin" }
        val id = set.optionalString("styleId").orEmpty()
        val name = set.optionalString("styleName").orEmpty()
        set.optionalString("name")
        listOf("moveIds", "songIds", "choreoIds", "tags").forEach { field ->
            set[field]?.let { value -> require(value is JsonArray && value.all { it is JsonPrimitive && it.isString }) { "invalid_set_selection" } }
        }
        if (catalog == null || origin == "Imported" || id.isBlank()) set
        else {
            val canonical = catalog.resolveId(id) ?: catalog.canonicalId(name)
            JsonObject(set.toMutableMap().apply {
                put("styleId", JsonPrimitive(canonical)); put("styleName", JsonPrimitive(catalog.displayName(canonical)))
            })
        }
    }
    return if (catalog == null) raw else JsonObject(root.toMutableMap().apply { put("sets", JsonArray(next)) }).toString()
}

private fun JsonObject.requiredString(key: String): String = optionalString(key) ?: error("missing_$key")
private fun JsonObject.requireVersion(key: String) {
    val version = get(key) ?: return // Original v1 readers allowed an omitted version.
    require(version is JsonPrimitive && !version.isString && version.intOrNull == 1) { "unsupported_$key" }
}
private fun JsonObject.optionalString(key: String): String? {
    val value = get(key) ?: return null
    require(value is JsonPrimitive && value.isString) { "invalid_$key" }
    return value.content
}
