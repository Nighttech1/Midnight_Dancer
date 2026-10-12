package com.midnightdancer.core.data.migrate

/**
 * Where Flutter AppData JSON was read from for this migration run.
 */
enum class LegacyPrefsSource {
    FilesystemMetadataJson,
    HiveBoxMetadata,
    None,
}

/**
 * Result of one-shot hot-prefs migration into DataStore.
 *
 * [catalogHandOff] is the full metadata JSON for **C-13 Room** — C-11 does not write Room.
 */
data class PrefsMigrationResult(
    val prefsApplied: Boolean,
    val skippedAlreadyMigrated: Boolean,
    val source: LegacyPrefsSource,
    val catalogHandOff: CatalogMigrationHandOff?,
)

/**
 * Contract hand-off to C-13: catalog entities stay in AppData JSON until Room migrator runs.
 * Stable entity ids — Room import must be idempotent (not handled here).
 */
data class CatalogMigrationHandOff(
    /** Full Flutter `metadata.json` / Hive metadata payload (AppData). */
    val appDataJson: String,
    val source: LegacyPrefsSource,
)
