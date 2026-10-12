package com.midnightdancer.core.domain.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Root catalog document — 1:1 with Flutter `lib/data/models/app_data.dart`.
 *
 * Persisted as `MidnightDancer/metadata.json`. Typed prefs live as keys inside [settings]
 * (`ui_language`, `legalTermsAccepted`, `danceReminder*`, `last_import_manifest`).
 */
@Serializable
data class AppData(
    val danceStyles: List<DanceStyle> = emptyList(),
    val songs: List<Song> = emptyList(),
    val choreographies: List<Choreography> = emptyList(),
    val settings: JsonObject = JsonObject(emptyMap()),
)
