package com.midnightdancer.core.data.migrate

import com.midnightdancer.core.data.prefs.DataStoreReadWriteUserPrefs
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.settings.DanceReminderConfig
import com.midnightdancer.core.domain.settings.LastImportManifest
import com.midnightdancer.core.domain.settings.LegalTermsConsent
import com.midnightdancer.core.domain.settings.UiLanguage
import com.midnightdancer.core.domain.usecase.ReadWriteUserPrefs

/**
 * One-shot migrator: Flutter metadata / Hive → DataStore hot prefs.
 *
 * Order: filesystem `metadata.json` first; if absent, Hive `midnight_dancer` / `metadata`.
 * Writes language, legal, reminders, optional `last_import_manifest`.
 * Full catalog JSON is returned as [CatalogMigrationHandOff] for C-13 (not persisted to Room here).
 */
class PrefsMigrator(
    private val legacyReader: LegacyMetadataReader,
    private val userPrefs: ReadWriteUserPrefs,
) {
    /**
     * Idempotent: if DataStore already has migration marker, skips overwrite and returns
     * [PrefsMigrationResult.skippedAlreadyMigrated] = true.
     */
    suspend fun migrateOnce(): PrefsMigrationResult {
        val store = userPrefs as? DataStoreReadWriteUserPrefs
        if (store != null && store.isPrefsMigrationDone()) {
            return PrefsMigrationResult(
                prefsApplied = false,
                skippedAlreadyMigrated = true,
                source = LegacyPrefsSource.None,
                catalogHandOff = null,
            )
        }

        val resolved = resolveLegacyJson()
        val rawJson = resolved.first
        val source = resolved.second
        if (rawJson.isNullOrBlank()) {
            store?.markPrefsMigrationDone()
            return PrefsMigrationResult(
                prefsApplied = false,
                skippedAlreadyMigrated = false,
                source = LegacyPrefsSource.None,
                catalogHandOff = null,
            )
        }

        val appData =
            try {
                DomainJson.decodeAppData(rawJson)
            } catch (_: Exception) {
                store?.markPrefsMigrationDone()
                return PrefsMigrationResult(
                    prefsApplied = false,
                    skippedAlreadyMigrated = false,
                    source = source,
                    catalogHandOff = null,
                )
            }

        val settings = appData.settings
        userPrefs.writeUiLanguage(UiLanguage.fromSettings(settings))
        userPrefs.writeLegalTermsAccepted(LegalTermsConsent.isAccepted(settings))
        userPrefs.writeDanceReminder(DanceReminderConfig.fromSettings(settings))
        userPrefs.writeLastImportManifest(LastImportManifest.fromSettings(settings))
        store?.markPrefsMigrationDone()

        return PrefsMigrationResult(
            prefsApplied = true,
            skippedAlreadyMigrated = false,
            source = source,
            catalogHandOff =
                CatalogMigrationHandOff(
                    appDataJson = rawJson,
                    source = source,
                ),
        )
    }

    private suspend fun resolveLegacyJson(): Pair<String?, LegacyPrefsSource> {
        val fs = legacyReader.readFilesystemMetadataJson()
        if (!fs.isNullOrBlank()) {
            return fs to LegacyPrefsSource.FilesystemMetadataJson
        }
        val hive = legacyReader.readHiveMetadataJson()
        if (!hive.isNullOrBlank()) {
            return hive to LegacyPrefsSource.HiveBoxMetadata
        }
        return null to LegacyPrefsSource.None
    }
}
