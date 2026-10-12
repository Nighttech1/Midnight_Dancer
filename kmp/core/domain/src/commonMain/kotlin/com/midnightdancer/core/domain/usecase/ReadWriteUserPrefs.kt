package com.midnightdancer.core.domain.usecase

import com.midnightdancer.core.domain.settings.DanceReminderConfig
import com.midnightdancer.core.domain.settings.DanceStyleConfig
import com.midnightdancer.core.domain.settings.ElementCategoryConfig
import com.midnightdancer.core.domain.settings.LastImportManifest
import com.midnightdancer.core.domain.settings.UiLanguage

/**
 * Read/write user prefs (language, legal, reminders, last import, categories, styles).
 * Implementation: DataStore (+ AppData.settings mirror) in C-11.
 */
interface ReadWriteUserPrefs {
    suspend fun readUiLanguage(): UiLanguage

    suspend fun writeUiLanguage(language: UiLanguage)

    suspend fun readLegalTermsAccepted(): Boolean

    suspend fun writeLegalTermsAccepted(accepted: Boolean)

    suspend fun readDanceReminder(): DanceReminderConfig

    suspend fun writeDanceReminder(config: DanceReminderConfig)

    suspend fun readLastImportManifest(): LastImportManifest

    suspend fun writeLastImportManifest(manifest: LastImportManifest)

    suspend fun readElementCategories(): ElementCategoryConfig

    suspend fun writeElementCategories(config: ElementCategoryConfig)

    suspend fun readDanceStyles(): DanceStyleConfig

    suspend fun writeDanceStyles(config: DanceStyleConfig)

    /** C-110: free-space threshold for low-disk banner (bytes). */
    suspend fun readLowDiskWarnBytes(): Long

    suspend fun writeLowDiskWarnBytes(bytes: Long)

    /** C-111: soft plaque on/off (hard disk fail always shows). */
    suspend fun readLowDiskWarnEnabled(): Boolean

    suspend fun writeLowDiskWarnEnabled(enabled: Boolean)
}
