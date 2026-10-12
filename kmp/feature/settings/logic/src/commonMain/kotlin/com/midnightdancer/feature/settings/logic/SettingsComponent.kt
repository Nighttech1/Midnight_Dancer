package com.midnightdancer.feature.settings.logic

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.midnightdancer.core.domain.settings.DanceStyleConfig
import com.midnightdancer.core.domain.settings.ElementCategoryConfig
import com.midnightdancer.core.domain.settings.UiLanguage
import com.midnightdancer.core.domain.storage.LowDiskDefaults
import com.midnightdancer.core.domain.usecase.ReadWriteUserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface SettingsComponent {
    val uiState: StateFlow<SettingsUiState>
    fun onIntent(intent: SettingsIntent)

    /** SKIE-friendly aliases for SwiftUI bridge (C-124 / C-127). */
    fun setLanguage(code: String) = onIntent(SettingsIntent.SetLanguageStub(code))
    fun setReminderEnabled(enabled: Boolean) =
        onIntent(SettingsIntent.SetReminderEnabledStub(enabled))
    fun setStyleDraft(text: String) = onIntent(SettingsIntent.SetStyleDraft(text))
    fun addStyle() = onIntent(SettingsIntent.AddStyle)
    fun removeStyle(name: String) = onIntent(SettingsIntent.RemoveStyle(name))
    fun setCategoryDraft(text: String) = onIntent(SettingsIntent.SetCategoryDraft(text))
    fun addCategory() = onIntent(SettingsIntent.AddCategory)
    fun removeCategory(name: String) = onIntent(SettingsIntent.RemoveCategory(name))
    fun setLowDiskWarnGib(gib: Int) = onIntent(SettingsIntent.SetLowDiskWarnGib(gib))
    fun setLowDiskWarnEnabled(enabled: Boolean) =
        onIntent(SettingsIntent.SetLowDiskWarnEnabled(enabled))
}

class DefaultSettingsComponent(
    componentContext: ComponentContext,
    private val userPrefs: ReadWriteUserPrefs? = null,
) : SettingsComponent, ComponentContext by componentContext {

    // Default (not Main): unit tests / commonTest have no Android Main dispatcher.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(SettingsUiState())
    override val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        if (userPrefs != null) {
            loadPrefs()
        } else {
            _uiState.update { it.copy(prefsLoaded = true) }
        }
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SetLanguageStub ->
                _uiState.update { it.copy(languageCode = intent.languageCode) }
            is SettingsIntent.SetReminderEnabledStub ->
                _uiState.update { it.copy(reminderEnabled = intent.enabled) }
            SettingsIntent.ReloadFromPrefs -> loadPrefs()
            is SettingsIntent.SetStyleDraft ->
                _uiState.update {
                    it.copy(newStyleDraft = intent.text.take(DanceStyleConfig.MAX_NAME_LENGTH))
                }
            SettingsIntent.AddStyle -> performAddStyle()
            is SettingsIntent.RemoveStyle ->
                persistStyles(_uiState.value.danceStyles.filterNot { it == intent.name })
            is SettingsIntent.RenameStyle -> {
                val next = _uiState.value.danceStyles.map {
                    if (it == intent.oldName) {
                        DanceStyleConfig.sanitizeName(intent.newName)
                    } else {
                        it
                    }
                }
                persistStyles(next)
            }
            is SettingsIntent.SetCategoryDraft ->
                _uiState.update {
                    it.copy(
                        newCategoryDraft = intent.text.take(ElementCategoryConfig.MAX_NAME_LENGTH),
                    )
                }
            SettingsIntent.AddCategory -> performAddCategory()
            is SettingsIntent.RemoveCategory ->
                persistCategories(
                    _uiState.value.elementCategories.filterNot { it == intent.name },
                )
            is SettingsIntent.RenameCategory -> {
                val next = _uiState.value.elementCategories.map {
                    if (it == intent.oldName) {
                        ElementCategoryConfig.sanitizeName(intent.newName)
                    } else {
                        it
                    }
                }
                persistCategories(next)
            }
            is SettingsIntent.SetLowDiskWarnGib -> persistLowDiskWarnGib(intent.gib)
            is SettingsIntent.SetLowDiskWarnEnabled -> persistLowDiskWarnEnabled(intent.enabled)
        }
    }

    private fun performAddStyle() {
        val draft = DanceStyleConfig.sanitizeName(_uiState.value.newStyleDraft)
        val existing = _uiState.value.danceStyles
        val name = draft.ifEmpty { uniqueNewName(existing, DEFAULT_NEW_STYLE_NAME) }
        if (name.isEmpty()) return
        val merged = DanceStyleConfig.sanitizeList(listOf(name) + existing)
        persistStyles(merged)
        _uiState.update { it.copy(newStyleDraft = "") }
    }

    private fun performAddCategory() {
        val draft = ElementCategoryConfig.sanitizeName(_uiState.value.newCategoryDraft)
        val existing = _uiState.value.elementCategories
        val name = draft.ifEmpty { uniqueNewName(existing, DEFAULT_NEW_TAG_NAME) }
        if (name.isEmpty()) return
        // New tag appears at the top of the list (C-61).
        val merged = ElementCategoryConfig.sanitizeList(listOf(name) + existing)
        persistCategories(merged)
        _uiState.update { it.copy(newCategoryDraft = "") }
    }

    private fun uniqueNewName(existing: List<String>, base: String): String {
        if (existing.none { it.equals(base, ignoreCase = true) }) return base
        var n = 2
        while (n < 100) {
            val candidate = "$base $n"
            if (existing.none { it.equals(candidate, ignoreCase = true) }) return candidate
            n++
        }
        return "$base ${existing.size + 1}"
    }

    companion object {
        /** Default language stub when DataStore is unavailable. */
        val DefaultLanguage: UiLanguage = UiLanguage.RU

        const val DEFAULT_NEW_TAG_NAME: String = "новый тег"
        const val DEFAULT_NEW_STYLE_NAME: String = "новый стиль"
    }

    private fun persistStyles(names: List<String>) {
        val clean = DanceStyleConfig.sanitizeList(names)
        _uiState.update { it.copy(danceStyles = clean) }
        val prefs = userPrefs ?: return
        scope.launch {
            prefs.writeDanceStyles(DanceStyleConfig(names = clean))
        }
    }

    private fun persistCategories(names: List<String>) {
        val clean = ElementCategoryConfig.sanitizeList(names)
        _uiState.update { it.copy(elementCategories = clean) }
        val prefs = userPrefs ?: return
        scope.launch {
            prefs.writeElementCategories(ElementCategoryConfig(names = clean))
        }
    }

    private fun persistLowDiskWarnGib(gib: Int) {
        val allowed = LowDiskDefaults.PRESET_GIB
        val pick = if (gib in allowed) gib else 5
        val bytes = LowDiskDefaults.bytesForGib(pick)
        _uiState.update {
            it.copy(lowDiskWarnGib = pick, lowDiskWarnBytes = bytes)
        }
        val prefs = userPrefs ?: return
        scope.launch {
            prefs.writeLowDiskWarnBytes(bytes)
        }
    }

    private fun persistLowDiskWarnEnabled(enabled: Boolean) {
        _uiState.update { it.copy(lowDiskWarnEnabled = enabled) }
        val prefs = userPrefs ?: return
        scope.launch {
            prefs.writeLowDiskWarnEnabled(enabled)
        }
    }

    private fun loadPrefs() {
        val prefs = userPrefs ?: run {
            _uiState.update { it.copy(prefsLoaded = true) }
            return
        }
        scope.launch {
            val snap =
                withContext(Dispatchers.Default) {
                    val language = prefs.readUiLanguage()
                    val reminder = prefs.readDanceReminder()
                    val categories = prefs.readElementCategories()
                    val styles = prefs.readDanceStyles()
                    val warnBytes = prefs.readLowDiskWarnBytes()
                    val warnEnabled = prefs.readLowDiskWarnEnabled()
                    val warnGib =
                        LowDiskDefaults.PRESET_GIB.firstOrNull {
                            LowDiskDefaults.bytesForGib(it) == warnBytes
                        } ?: ((warnBytes / (1024L * 1024L * 1024L)).toInt().coerceIn(1, 10))
                    SettingsUiState(
                        languageCode = language.code,
                        reminderEnabled = reminder.enabled,
                        reminderHour = reminder.hour,
                        reminderMinute = reminder.minute,
                        prefsLoaded = true,
                        danceStyles = styles.names,
                        styleNameMaxLength = DanceStyleConfig.MAX_NAME_LENGTH,
                        elementCategories = categories.names,
                        categoryNameMaxLength = ElementCategoryConfig.MAX_NAME_LENGTH,
                        lowDiskWarnBytes = warnBytes,
                        lowDiskWarnGib = warnGib,
                        lowDiskWarnEnabled = warnEnabled,
                    )
                }
            _uiState.value = snap
        }
    }
}
