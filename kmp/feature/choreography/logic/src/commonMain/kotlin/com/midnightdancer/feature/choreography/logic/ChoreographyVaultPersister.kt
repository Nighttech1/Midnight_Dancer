package com.midnightdancer.feature.choreography.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * C-301 / Wave D: survive force-stop — vault was process-memory only (D1.3 FAIL).
 * Platform supplies [read]/]/write] (Android filesDir JSON).
 */
interface ChoreographyVaultPersister {
    fun readRaw(): String?
    fun writeRaw(json: String)
    /** Refresh only style references in the existing document, preserving unknown fields. */
    fun refreshStyles() { readRaw()?.let(::writeRaw) }
}

@Serializable
data class PersistedEditorClip(
    val label: String,
    val durationMs: Long,
    val mediaUri: String? = null,
    val speed: Double = 1.0,
    val sourceStartMs: Long = 0L,
)

@Serializable
data class PersistedSnapshot(
    val title: String,
    val style: String,
    val elementClips: List<PersistedEditorClip> = emptyList(),
    val musicClips: List<PersistedEditorClip> = emptyList(),
    val songPlaybackSpeed: Double = 1.0,
    val musicVolume: Float = 1f,
    val tag: String = "",
)

@Serializable
data class PersistedVaultItem(
    val id: String,
    val title: String,
    val status: String,
    val style: String,
    val tags: List<String> = emptyList(),
    val bpm: Int = 120,
    val durationLabel: String = "00:00",
    val trackName: String = "—",
    val chrId: String = "",
    val songPlaybackSpeed: Double = 1.0,
    val snapshot: PersistedSnapshot? = null,
)

@Serializable
data class PersistedVaultFile(
    val version: Int = 1,
    val items: List<PersistedVaultItem> = emptyList(),
)

private val vaultJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun encodeVaultItems(items: List<ChoreographyVaultItem>): String =
    vaultJson.encodeToString(
        PersistedVaultFile(
            items = items.map { it.toPersisted() },
        ),
    )

fun decodeVaultItems(raw: String): List<ChoreographyVaultItem> =
    vaultJson.decodeFromString<PersistedVaultFile>(raw).items.map { it.toDomain() }

private fun ChoreographyVaultItem.toPersisted() = PersistedVaultItem(
    id = id,
    title = title,
    status = status.name,
    style = style,
    tags = tags,
    bpm = bpm,
    durationLabel = durationLabel,
    trackName = trackName,
    chrId = chrId,
    songPlaybackSpeed = songPlaybackSpeed,
    snapshot = snapshot?.let { s ->
        PersistedSnapshot(
            title = s.title,
            style = s.style,
            elementClips = s.elementClips.map { it.toPersisted() },
            musicClips = s.musicClips.map { it.toPersisted() },
            songPlaybackSpeed = s.songPlaybackSpeed,
            musicVolume = s.musicVolume,
            tag = s.tag,
        )
    },
)

private fun EditorClip.toPersisted() = PersistedEditorClip(
    label = label,
    durationMs = durationMs,
    mediaUri = mediaUri,
    speed = speed,
    sourceStartMs = sourceStartMs,
)

private fun PersistedVaultItem.toDomain() = ChoreographyVaultItem(
    id = id,
    title = title,
    status = runCatching { VaultStatus.valueOf(status) }.getOrDefault(VaultStatus.Draft),
    style = style,
    tags = tags,
    bpm = bpm,
    durationLabel = durationLabel,
    trackName = trackName,
    chrId = chrId,
    songPlaybackSpeed = songPlaybackSpeed,
    snapshot = snapshot?.let { s ->
        ChoreographySnapshot(
            title = s.title,
            style = s.style,
            elementClips = s.elementClips.map { it.toDomain() },
            musicClips = s.musicClips.map { it.toDomain() },
            songPlaybackSpeed = s.songPlaybackSpeed,
            musicVolume = s.musicVolume,
            tag = s.tag,
        )
    },
)

private fun PersistedEditorClip.toDomain() = EditorClip(
    label = label,
    durationMs = durationMs,
    mediaUri = mediaUri,
    speed = speed,
    sourceStartMs = sourceStartMs,
)
