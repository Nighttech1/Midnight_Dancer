package com.midnightdancer.feature.settings.logic

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsLogicSmokeTest {

    @Test
    fun intent_languageAndReminderStubs_updateUiState() {
        val lifecycle = LifecycleRegistry()
        val component = DefaultSettingsComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        assertEquals("Настройки", component.uiState.value.title)
        assertEquals("ru", component.uiState.value.languageCode)
        assertTrue(component.uiState.value.prefsLoaded)

        component.onIntent(SettingsIntent.SetLanguageStub("en"))
        assertEquals("en", component.uiState.value.languageCode)

        component.onIntent(SettingsIntent.SetReminderEnabledStub(true))
        assertTrue(component.uiState.value.reminderEnabled)
    }

    @Test
    fun addCategory_emptyDraft_insertsNewTagAtTop() {
        val lifecycle = LifecycleRegistry()
        val component = DefaultSettingsComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        val before = component.uiState.value.elementCategories
        component.onIntent(SettingsIntent.AddCategory)
        val after = component.uiState.value.elementCategories

        assertEquals(DefaultSettingsComponent.DEFAULT_NEW_TAG_NAME, after.first())
        assertEquals(before.size + 1, after.size)
    }

    @Test
    fun renameCategory_updatesName() {
        val lifecycle = LifecycleRegistry()
        val component = DefaultSettingsComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        component.onIntent(SettingsIntent.AddCategory)
        component.onIntent(
            SettingsIntent.RenameCategory(
                oldName = DefaultSettingsComponent.DEFAULT_NEW_TAG_NAME,
                newName = "латина",
            ),
        )
        assertTrue(component.uiState.value.elementCategories.contains("латина"))
        assertTrue(
            component.uiState.value.elementCategories.none {
                it == DefaultSettingsComponent.DEFAULT_NEW_TAG_NAME
            },
        )
    }

    @Test
    fun setLowDiskWarnGib_updatesState() {
        val lifecycle = LifecycleRegistry()
        val component = DefaultSettingsComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()

        assertEquals(5, component.uiState.value.lowDiskWarnGib)
        component.onIntent(SettingsIntent.SetLowDiskWarnGib(2))
        assertEquals(2, component.uiState.value.lowDiskWarnGib)
        assertEquals(2L * 1024L * 1024L * 1024L, component.uiState.value.lowDiskWarnBytes)
    }
}
