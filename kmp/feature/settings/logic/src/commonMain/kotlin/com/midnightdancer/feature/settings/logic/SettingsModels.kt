package com.midnightdancer.feature.settings.logic

import com.midnightdancer.core.domain.settings.DanceStyleConfig
import com.midnightdancer.core.domain.settings.ElementCategoryConfig
import com.midnightdancer.core.domain.storage.LowDiskDefaults

/**
 * Settings shell — language / reminders / dance styles / element tags / low-disk (C-110).
 */
data class SettingsUiState(
    val title: String = "Настройки",
    val languageCode: String = "ru",
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val prefsLoaded: Boolean = false,
    val danceStyles: List<String> = DanceStyleConfig.DEFAULT_NAMES,
    val newStyleDraft: String = "",
    val styleNameMaxLength: Int = DanceStyleConfig.MAX_NAME_LENGTH,
    val elementCategories: List<String> = ElementCategoryConfig.DEFAULT_NAMES,
    val newCategoryDraft: String = "",
    val categoryNameMaxLength: Int = ElementCategoryConfig.MAX_NAME_LENGTH,
    /** C-110/C-111: warn when free space ≤ this many bytes. */
    val lowDiskWarnBytes: Long = LowDiskDefaults.DEFAULT_WARN_BYTES,
    val lowDiskWarnGib: Int = 5,
    val lowDiskPresetsGib: List<Int> = LowDiskDefaults.PRESET_GIB,
    val lowDiskWarnEnabled: Boolean = LowDiskDefaults.DEFAULT_WARN_ENABLED,
)

sealed interface SettingsIntent {
    data class SetLanguageStub(val languageCode: String) : SettingsIntent
    data class SetReminderEnabledStub(val enabled: Boolean) : SettingsIntent
    data object ReloadFromPrefs : SettingsIntent
    data class SetStyleDraft(val text: String) : SettingsIntent
    data object AddStyle : SettingsIntent
    data class RemoveStyle(val name: String) : SettingsIntent
    data class RenameStyle(val oldName: String, val newName: String) : SettingsIntent
    data class SetCategoryDraft(val text: String) : SettingsIntent
    data object AddCategory : SettingsIntent
    data class RemoveCategory(val name: String) : SettingsIntent
    data class RenameCategory(val oldName: String, val newName: String) : SettingsIntent
    /** C-110: pick threshold in GiB (1 / 2 / 5 / 10). */
    data class SetLowDiskWarnGib(val gib: Int) : SettingsIntent
    /** C-111: soft plaque on/off. */
    data class SetLowDiskWarnEnabled(val enabled: Boolean) : SettingsIntent
}
