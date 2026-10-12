package com.midnightdancer.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.settings.DanceReminderConfig
import com.midnightdancer.core.domain.settings.DanceReminderMode
import com.midnightdancer.core.domain.settings.DanceStyleConfig
import com.midnightdancer.core.domain.settings.ElementCategoryConfig
import com.midnightdancer.core.domain.settings.LastImportManifest
import com.midnightdancer.core.domain.settings.UiLanguage
import com.midnightdancer.core.domain.storage.LowDiskDefaults
import com.midnightdancer.core.domain.usecase.ReadWriteUserPrefs
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * [ReadWriteUserPrefs] backed by DataStore Preferences Core.
 * Keys match Flutter `AppData.settings` exactly.
 */
class DataStoreReadWriteUserPrefs(
    private val dataStore: DataStore<Preferences>,
) : ReadWriteUserPrefs {
    override suspend fun readUiLanguage(): UiLanguage {
        val raw = dataStore.data.first()[PrefsPreferenceKeys.uiLanguage]
        return UiLanguage.fromCode(raw)
    }

    override suspend fun writeUiLanguage(language: UiLanguage) {
        dataStore.edit { prefs ->
            prefs[PrefsPreferenceKeys.uiLanguage] = language.code
        }
    }

    override suspend fun readLegalTermsAccepted(): Boolean =
        dataStore.data.first()[PrefsPreferenceKeys.legalTermsAccepted] == true

    override suspend fun writeLegalTermsAccepted(accepted: Boolean) {
        dataStore.edit { prefs ->
            prefs[PrefsPreferenceKeys.legalTermsAccepted] = accepted
        }
    }

    override suspend fun readDanceReminder(): DanceReminderConfig {
        val snap = dataStore.data.first()
        val modeStr = snap[PrefsPreferenceKeys.danceReminderMode] ?: "daily"
        val mode =
            when (modeStr) {
                "weekdays" -> DanceReminderMode.weekdays
                "weekly" -> DanceReminderMode.weekly
                else -> DanceReminderMode.daily
            }
        return DanceReminderConfig(
            enabled = snap[PrefsPreferenceKeys.danceReminderEnabled] == true,
            hour = (snap[PrefsPreferenceKeys.danceReminderHour] ?: 19).coerceIn(0, 23),
            minute = (snap[PrefsPreferenceKeys.danceReminderMinute] ?: 0).coerceIn(0, 59),
            mode = mode,
            weekday = (snap[PrefsPreferenceKeys.danceReminderWeekday] ?: 6).coerceIn(1, 7),
        )
    }

    override suspend fun writeDanceReminder(config: DanceReminderConfig) {
        dataStore.edit { prefs ->
            prefs[PrefsPreferenceKeys.danceReminderEnabled] = config.enabled
            prefs[PrefsPreferenceKeys.danceReminderHour] = config.hour.coerceIn(0, 23)
            prefs[PrefsPreferenceKeys.danceReminderMinute] = config.minute.coerceIn(0, 59)
            prefs[PrefsPreferenceKeys.danceReminderMode] = config.mode.name
            prefs[PrefsPreferenceKeys.danceReminderWeekday] = config.weekday.coerceIn(1, 7)
        }
    }

    override suspend fun readLastImportManifest(): LastImportManifest {
        val raw = dataStore.data.first()[PrefsPreferenceKeys.lastImportManifest]
        if (raw.isNullOrBlank()) return LastImportManifest.empty()
        return runCatching {
            DomainJson.format.decodeFromString(LastImportManifest.serializer(), raw)
        }.getOrElse { LastImportManifest.empty() }
    }

    override suspend fun writeLastImportManifest(manifest: LastImportManifest) {
        dataStore.edit { prefs ->
            if (manifest.isEmpty) {
                prefs.remove(PrefsPreferenceKeys.lastImportManifest)
            } else {
                prefs[PrefsPreferenceKeys.lastImportManifest] =
                    DomainJson.format.encodeToString(LastImportManifest.serializer(), manifest)
            }
        }
    }

    override suspend fun readElementCategories(): ElementCategoryConfig {
        val raw = dataStore.data.first()[PrefsPreferenceKeys.elementCategories]
        if (raw.isNullOrBlank()) return ElementCategoryConfig()
        val names = runCatching {
            DomainJson.format.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrElse { ElementCategoryConfig.DEFAULT_NAMES }
        return ElementCategoryConfig(names = ElementCategoryConfig.sanitizeList(names))
    }

    override suspend fun writeElementCategories(config: ElementCategoryConfig) {
        val names = ElementCategoryConfig.sanitizeList(config.names)
        dataStore.edit { prefs ->
            prefs[PrefsPreferenceKeys.elementCategories] =
                DomainJson.format.encodeToString(ListSerializer(String.serializer()), names)
        }
    }

    override suspend fun readDanceStyles(): DanceStyleConfig {
        val raw = dataStore.data.first()[PrefsPreferenceKeys.danceStyles]
        if (raw.isNullOrBlank()) return DanceStyleConfig()
        val names = runCatching {
            DomainJson.format.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrElse { DanceStyleConfig.DEFAULT_NAMES }
        return DanceStyleConfig(names = DanceStyleConfig.sanitizeList(names))
    }

    override suspend fun writeDanceStyles(config: DanceStyleConfig) {
        val names = DanceStyleConfig.sanitizeList(config.names)
        dataStore.edit { prefs ->
            prefs[PrefsPreferenceKeys.danceStyles] =
                DomainJson.format.encodeToString(ListSerializer(String.serializer()), names)
        }
    }

    override suspend fun readLowDiskWarnBytes(): Long =
        dataStore.data.first()[PrefsPreferenceKeys.lowDiskWarnBytes]
            ?: LowDiskDefaults.DEFAULT_WARN_BYTES

    override suspend fun writeLowDiskWarnBytes(bytes: Long) {
        val clamped = bytes.coerceAtLeast(LowDiskDefaults.bytesForGib(1))
        dataStore.edit { prefs ->
            prefs[PrefsPreferenceKeys.lowDiskWarnBytes] = clamped
        }
    }

    override suspend fun readLowDiskWarnEnabled(): Boolean =
        dataStore.data.first()[PrefsPreferenceKeys.lowDiskWarnEnabled]
            ?: LowDiskDefaults.DEFAULT_WARN_ENABLED

    override suspend fun writeLowDiskWarnEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PrefsPreferenceKeys.lowDiskWarnEnabled] = enabled
        }
    }

    internal suspend fun isPrefsMigrationDone(): Boolean =
        dataStore.data.first()[PrefsPreferenceKeys.prefsMigratedFromFlutterV1] == true

    internal suspend fun markPrefsMigrationDone() {
        dataStore.edit { prefs ->
            prefs[PrefsPreferenceKeys.prefsMigratedFromFlutterV1] = true
        }
    }
}
